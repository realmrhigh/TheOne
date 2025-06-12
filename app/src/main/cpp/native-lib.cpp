#include <jni.h>
#include <string>
#include <android/log.h>
#include <oboe/Oboe.h>
#include <map> // For std::map
#include "audio_sample.h" // Include the new header
#include <unistd.h> // For lseek, read, close
#include <fcntl.h>  // For open (though we get fd from Kotlin)
#include <sys/stat.h> // For fstat
#include <errno.h> // For strerror

// Define this in exactly one .c or .cpp file
#define DR_WAV_IMPLEMENTATION
// In a .h or .cpp file
#define APP_NAME "TheOne" // Existing app name
#ifndef NATIVE_LIB_APP_NAME
#define NATIVE_LIB_APP_NAME "TheOneNativeLib" // For JNI specific logs
#endif

#include "dr_wav.h" // Include the dr_wav header
#include <vector>   // For std::vector
#include <mutex>    // For std::mutex
#include <cmath>    // For cosf, sinf for panning
#include <cstdint>  // For uint types
#include <memory>   // For std::unique_ptr, std::make_unique (used by audio_sample.h)
#include <random>   // For std::mt19937, std::random_device
#include <iterator> // For std::make_move_iterator
#include <algorithm> // For std::max and std::min

#include "EnvelopeGenerator.h"
#include "LfoGenerator.h"
#include "PadSettings.h"
#include "AudioEngine.h" // Include the new AudioEngine header


#ifndef M_PI // Define M_PI if not defined by cmath (common on some compilers)
#define M_PI (3.14159265358979323846f)
#endif

// Helper function for dr_wav to write to a file descriptor
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite) {
    int fd = (int)(intptr_t)pUserData;
    ssize_t bytesWritten = write(fd, pData, bytesToWrite);
    if (bytesWritten < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_write_proc_fd: Failed to write: %s", strerror(errno));
        return 0;
    }
    return (size_t)bytesWritten;
}

// Helper function for dr_wav to seek in a file descriptor
static drwav_bool32 drwav_seek_proc_fd(void* pUserData, int offset, drwav_seek_origin origin) {
    int fd = (int)(intptr_t)pUserData;
    int whence;
    switch (origin) {
        case drwav_seek_origin_start:
            whence = SEEK_SET;
            break;
        case drwav_seek_origin_current:
            whence = SEEK_CUR;
            break;
        default: // Should not happen with dr_wav's current usage, but cover it.
             __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_seek_proc_fd: Unknown seek origin: %d", origin);
            return DRWAV_FALSE;
    }
    if (lseek(fd, (off_t)offset, whence) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_seek_proc_fd: Failed to seek: %s", strerror(errno));
        return DRWAV_FALSE;
    }
    return DRWAV_TRUE;
}

// Add these two callback functions somewhere before your JNI function
static size_t on_read_c(void* pUserData, void* pBufferOut, size_t bytesToRead) {
    return read((int)(intptr_t)pUserData, pBufferOut, bytesToRead);
}

static drwav_bool32 on_seek_c(void* pUserData, int offset, drwav_seek_origin origin) {
    int whence = (origin == drwav_seek_origin_current) ? SEEK_CUR : SEEK_SET;
    // lseek returns -1 on error, so we check for that.
    // The function should return DRWAV_TRUE on success and DRWAV_FALSE on failure.
    if (lseek((int)(intptr_t)pUserData, offset, whence) != -1) {
        return DRWAV_TRUE;
    }
    // Log an error if lseek fails
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "on_seek_c: Failed to lseek: %s", strerror(errno));
    return DRWAV_FALSE;
}

// Define a type for our sample map
using SampleId = std::string;
using SampleMap = std::map<SampleId, theone::audio::LoadedSample>;

// Declare a global instance of the sample map
static SampleMap gSampleMap;

// Map to store PadSettingsCpp for each pad, keyed by a string (e.g., "Track1_Pad1")
// This will be populated by a JNI function.
// The PadSettingsCpp struct is now defined in PadSettings.h
static std::map<std::string, theone::audio::PadSettingsCpp> gPadSettingsMap;
static std::mutex gPadSettingsMutex; // Mutex for gPadSettingsMap

// Random number generator for layer selection
static std::mt19937 gRandomEngine{std::random_device{}()}; // Reinstated for native-lib layer logic

// Global AudioEngine instance
static std::unique_ptr<theone::audio::AudioEngine> audioEngineInstance;


// --- C++ Sequencer Data Structures (Potentially moved to AudioEngine or remain global if engine doesn't manage them directly) ---
// For now, assuming these might still be global or passed to AudioEngine methods if not fully encapsulated.
// If AudioEngine fully encapsulates sequencer, these statics would be removed or wrapped.
enum class EventTriggerTypeCpp {
    PAD_TRIGGER // Initially, only pad triggers
};

struct PadTriggerEventCpp {
    std::string padId; // Relative to the track, e.g. "Pad1", "Pad2"
    int velocity;
    long durationTicks; // Optional, if events have duration
};

struct EventCpp {
    std::string id; // Unique ID for this event instance
    std::string trackId; // Identifies which track this event belongs to (e.g., "Track1")
    long startTimeTicks;
    EventTriggerTypeCpp type;
    PadTriggerEventCpp padTrigger; // Assuming only pad triggers for now
    // Could use std::variant if more event types are added later
};

struct TrackCpp {
    std::string id; // e.g., "Track1"
    std::vector<EventCpp> events;
};

struct SequenceCpp {
    std::string id; // Unique ID for this sequence
    std::string name;
    float bpm = 120.0f;
    int timeSignatureNumerator = 4;
    int timeSignatureDenominator = 4; // Currently not used for tick calculation directly, but good to have
    long barLength = 4; // Length in bars
    long ppqn = 96; // Pulses per quarter note, configurable per sequence
    std::map<std::string, TrackCpp> tracks; // Map of trackId to TrackCpp
    long currentPlayheadTicks = 0;
    bool isPlaying = false;
};

// --- Global Sequencer Variables ---
static std::unique_ptr<SequenceCpp> gCurrentSequence;
static std::mutex gSequencerMutex; // To protect access to gCurrentSequence and its state
static double gCurrentTickDurationMs = 0.0; // Calculated based on BPM and PPQN
static double gTimeAccumulatedForTick = 0.0; // Accumulator for precise tick advancement
static uint32_t gAudioStreamSampleRate = 0; // To be updated when Oboe stream starts


// --- Helper Function to Recalculate Tick Duration ---
void RecalculateTickDuration() {
    // This function might be called when gCurrentSequence members (bpm, ppqn) change,
    // or when gCurrentSequence itself is replaced.
    // A lock is appropriate here to ensure consistent reads of gCurrentSequence members
    // and write to gCurrentTickDurationMs.
    std::lock_guard<std::mutex> lock(gSequencerMutex);
    if (gCurrentSequence && gCurrentSequence->bpm > 0 && gCurrentSequence->ppqn > 0) {
        double msPerMinute = 60000.0;
        double beatsPerMinute = gCurrentSequence->bpm;
        double msPerBeat = msPerMinute / beatsPerMinute;
        gCurrentTickDurationMs = msPerBeat / gCurrentSequence->ppqn;
    } else {
        gCurrentTickDurationMs = 0.0;
    }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Recalculated tick duration: %f ms (BPM: %f, PPQN: %ld)",
                        gCurrentTickDurationMs,
                        gCurrentSequence ? gCurrentSequence->bpm : 0.0f,
                        gCurrentSequence ? gCurrentSequence->ppqn : 0l);
}


// Helper function to convert jstring to std::string
std::string JStringToString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "JStringToString: jstr is null");
        return "";
    }
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (chars == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "JStringToString: GetStringUTFChars failed");
        env->ExceptionClear(); // Clear any pending exceptions
        return "";
    }
    std::string str = chars;
    env->ReleaseStringUTFChars(jstr, chars);
    return str;
}

// Helper function to get enum ordinal
int getEnumOrdinal(JNIEnv* env, jobject enumObj) {
    if (enumObj == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "getEnumOrdinal: enumObj is null");
        return -1; // Or handle error as appropriate
    }
    jclass enumClass = env->GetObjectClass(enumObj);
    if (enumClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "getEnumOrdinal: GetObjectClass failed for enumObj");
        env->ExceptionClear();
        env->DeleteLocalRef(enumObj); // Release the object reference
        return -1;
    }
    jmethodID ordinalMethod = env->GetMethodID(enumClass, "ordinal", "()I");
    if (ordinalMethod == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "getEnumOrdinal: GetMethodID for ordinal failed");
        env->DeleteLocalRef(enumClass);
        env->DeleteLocalRef(enumObj); // Release the object reference
        env->ExceptionClear();
        return -1;
    }
    int ordinal = env->CallIntMethod(enumObj, ordinalMethod);
    if (env->ExceptionCheck()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "getEnumOrdinal: CallIntMethod for ordinal failed");
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(enumClass);
        env->DeleteLocalRef(enumObj); // Release the object reference
        return -1;
    }
    env->DeleteLocalRef(enumClass);
    // Note: enumObj is not deleted here if it was passed as an argument to this function,
    // its lifecycle should be managed by the caller. If it was locally created (e.g. GetObjectField),
    // then it should be deleted by the caller after this function returns.
    return ordinal;
}


// Placeholder for logging
//const char* APP_NAME = "TheOneNative";

// List for active playing sounds and its mutex
static std::vector<theone::audio::PlayingSound> gActiveSounds;
static std::mutex gActiveSoundsMutex; // Mutex to protect gActiveSounds

// Metronome state object and its mutex
static theone::audio::MetronomeState gMetronomeState;
static std::mutex gMetronomeStateMutex; // Mutex to protect gMetronomeState

// Recording State Variables
static oboe::ManagedStream mInputStream; // Oboe Input Stream
static std::atomic<bool> mIsRecording {false};
static std::atomic<float> mPeakRecordingLevel {0.0f};
static int mRecordingFileDescriptor = -1;    // FD for the output WAV file
static drwav mWavWriter;                     // dr_wav instance for writing
static bool mWavWriterInitialized = false;   // Flag to check if mWavWriter is initialized
static std::string mCurrentRecordingFilePath = ""; // Store the path for metadata later
static std::mutex gRecordingStateMutex; // Mutex to protect recording state, file descriptor, and drwav writer access


// Basic Oboe audio callback
class MyAudioCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override {

        float *outputBuffer = static_cast<float*>(audioData);
        memset(outputBuffer, 0, sizeof(float) * numFrames * oboeStream->getChannelCount());

        // Metronome Beat Generation
        std::vector<theone::audio::PlayingSound> newMetronomeSounds;

        { // Scope for gMetronomeStateMutex
            std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
            if (gMetronomeState.enabled.load() && gMetronomeState.framesPerBeat > 0) {
                if (gMetronomeState.audioStreamSampleRate == 0) {
                    // __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Metronome: audioStreamSampleRate is 0!");
                }

                for (int i = 0; i < numFrames; ++i) {
                    if (gMetronomeState.samplesUntilNextBeat == 0) {
                        gMetronomeState.currentBeatInBar++;
                        if (gMetronomeState.currentBeatInBar > gMetronomeState.timeSignatureNum.load() || gMetronomeState.timeSignatureNum.load() == 0) {
                            gMetronomeState.currentBeatInBar = 1;
                        }

                        const theone::audio::LoadedSample* soundToPlay = nullptr;
                        bool isPrimary = (gMetronomeState.currentBeatInBar == 1);

                        if (isPrimary && gMetronomeState.primaryBeatSample) {
                            soundToPlay = gMetronomeState.primaryBeatSample;
                        } else if (!isPrimary && gMetronomeState.secondaryBeatSample) {
                            soundToPlay = gMetronomeState.secondaryBeatSample;
                        } else if (gMetronomeState.primaryBeatSample) { // Fallback to primary if secondary is null but it's not beat 1
                            soundToPlay = gMetronomeState.primaryBeatSample;
                        }


                        if (soundToPlay) {
                            std::string instanceId = "m_tick_" + std::to_string(gMetronomeState.currentBeatInBar);
                            newMetronomeSounds.emplace_back(soundToPlay, instanceId, gMetronomeState.volume.load(), 0.0f /*center pan*/);
                        }
                        gMetronomeState.samplesUntilNextBeat = gMetronomeState.framesPerBeat;
                    }
                    gMetronomeState.samplesUntilNextBeat--;
                }
            }
        } // gMetronomeStateMutex released


        // --- C++ Sequencer Processing ---
        // Check if sequencer is initialized and has a valid tick duration
        if (gAudioStreamSampleRate > 0 && gCurrentTickDurationMs > 0.0) {
            std::lock_guard<std::mutex> sequencerLock(gSequencerMutex); // Lock for gCurrentSequence access

            if (gCurrentSequence && gCurrentSequence->isPlaying) {
                double frameDurationMs = 1000.0 / static_cast<double>(gAudioStreamSampleRate);
                gTimeAccumulatedForTick += static_cast<double>(numFrames) * frameDurationMs;

                // Process all ticks that should have occurred in this audio buffer
                while (gTimeAccumulatedForTick >= gCurrentTickDurationMs && gCurrentTickDurationMs > 0.0) {
                    gTimeAccumulatedForTick -= gCurrentTickDurationMs;
                    gCurrentSequence->currentPlayheadTicks++;

                    // --- Handle Sequence Looping ---
                    // Assuming PPQN is based on quarter notes. Denominator adjustment needed for other bases.
                    long beatsPerBar = gCurrentSequence->timeSignatureNumerator;
                    // If timeSignatureDenominator is 8, a "beat" (quarter note) is half as long in ticks.
                    // This basic calculation assumes denominator is 4. A more robust solution would use it.
                    long ticksPerBar = beatsPerBar * gCurrentSequence->ppqn;
                    long totalTicksInSequence = gCurrentSequence->barLength * ticksPerBar;

                    if (totalTicksInSequence > 0 && gCurrentSequence->currentPlayheadTicks >= totalTicksInSequence) {
                        gCurrentSequence->currentPlayheadTicks = 0; // Loop back
                        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Sequencer looped. Playhead reset. Total ticks: %ld", totalTicksInSequence);
                    }

                    // --- Trigger Events ---
                    for (auto const& [trackId, track] : gCurrentSequence->tracks) {
                        for (const auto& event : track.events) {
                            if (event.startTimeTicks == gCurrentSequence->currentPlayheadTicks) {
                                if (event.type == EventTriggerTypeCpp::PAD_TRIGGER) {
                                    std::string padKey = event.trackId + "_" + event.padTrigger.padId;
                                    std::shared_ptr<theone::audio::PadSettingsCpp> padSettingsPtr;

                                    { // Scope for gPadSettingsMutex
                                        std::lock_guard<std::mutex> padSettingsLocker(gPadSettingsMutex);
                                        auto it = gPadSettingsMap.find(padKey);
                                        if (it != gPadSettingsMap.end()) {
                                            // Create a shared_ptr to the found settings.
                                            // Copying (it->second) ensures thread safety if PadSettingsCpp is complex,
                                            // or if PlayingSound needs its own lifecycle for it.
                                            padSettingsPtr = std::make_shared<theone::audio::PadSettingsCpp>(it->second);
                                        }
                                    }

                                    if (padSettingsPtr) {
                                        const theone::audio::SampleLayerCpp* selectedLayer = nullptr;
                                        // Simplified layer selection: first enabled layer
                                        for (const auto& layer : padSettingsPtr->layers) {
                                            if (layer.enabled && !layer.sampleId.empty()) {
                                                selectedLayer = &layer;
                                                break;
                                            }
                                        }

                                        if (selectedLayer) {
                                            auto sampleIt = gSampleMap.find(selectedLayer->sampleId);
                                            if (sampleIt != gSampleMap.end()) {
                                                const theone::audio::LoadedSample* loadedSample = &(sampleIt->second);
                                                if (loadedSample && !loadedSample->audioData.empty()) {
                                                    std::string noteInstanceId = "seq_" + event.id + "_" + std::to_string(gCurrentSequence->currentPlayheadTicks) + "_" + std::to_string(std::rand());
                                                    float velocityNormalized = static_cast<float>(event.padTrigger.velocity) / 127.0f;

                                                    // Calculate volume and pan from pad, layer, and event velocity
                                                    float baseVolume = padSettingsPtr->volume;
                                                    float basePan = padSettingsPtr->pan;
                                                    float layerVolumeOffsetGain = 1.0f;
                                                    if (selectedLayer->volumeOffsetDb > -90.0f) { // Avoid large negative for powf
                                                        layerVolumeOffsetGain = powf(10.0f, selectedLayer->volumeOffsetDb / 20.0f);
                                                    } else {
                                                        layerVolumeOffsetGain = 0.0f; // Effective silence
                                                    }
                                                    // Apply velocity to the gain derived from base and layer offset
                                                    float finalVolume = baseVolume * layerVolumeOffsetGain * velocityNormalized;
                                                    float finalPan = basePan + selectedLayer->panOffset;
                                                    finalPan = std::max(-1.0f, std::min(1.0f, finalPan)); // Clamp pan

                                                    theone::audio::PlayingSound soundToPlay(loadedSample, noteInstanceId, finalVolume, finalPan);
                                                    soundToPlay.padSettings = padSettingsPtr; // Assign the settings

                                                    float sr = static_cast<float>(gAudioStreamSampleRate); // Already checked gAudioStreamSampleRate > 0
                                                                                                        // but good to have a local copy for safety if sr calculation is complex

                                                    // Configure audio modules based on padSettings
                                                    if (soundToPlay.padSettings) {
                                                        // Amp Envelope
                                                        soundToPlay.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                                        soundToPlay.ampEnvelopeGen->configure(soundToPlay.padSettings->ampEnvelope, sr, velocityNormalized);
                                                        soundToPlay.ampEnvelopeGen->triggerOn(velocityNormalized);

                                                        // Pitch Envelope
                                                        if (soundToPlay.padSettings->hasPitchEnvelope) {
                                                            soundToPlay.pitchEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                                            soundToPlay.pitchEnvelopeGen->configure(soundToPlay.padSettings->pitchEnvelope, sr, velocityNormalized);
                                                            soundToPlay.pitchEnvelopeGen->triggerOn(velocityNormalized);
                                                        }

                                                        // Filter Envelope
                                                        if (soundToPlay.padSettings->hasFilterEnvelope) {
                                                            soundToPlay.filterEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                                            soundToPlay.filterEnvelopeGen->configure(soundToPlay.padSettings->filterEnvelope, sr, velocityNormalized);
                                                            soundToPlay.filterEnvelopeGen->triggerOn(velocityNormalized);
                                                        }

                                                        // LFOs
                                                        soundToPlay.lfoGens.clear();
                                                        float currentTempo = gCurrentSequence->bpm; // Use sequence's BPM
                                                        if (currentTempo <= 0) currentTempo = 120.0f; // Safety

                                                        for (const auto& lfoConfig : soundToPlay.padSettings->lfos) {
                                                            if (lfoConfig.isEnabled) {
                                                                auto lfo = std::make_unique<theone::audio::LfoGenerator>();
                                                                lfo->configure(lfoConfig, sr, currentTempo);
                                                                lfo->retrigger();
                                                                soundToPlay.lfoGens.push_back(std::move(lfo));
                                                            }
                                                        }
                                                    }

                                                    // Add to active sounds
                                                    {
                                                        std::lock_guard<std::mutex> activeSoundsLocker(gActiveSoundsMutex);
                                                        gActiveSounds.push_back(std::move(soundToPlay));
                                                    }
                                                     __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Sequencer triggered sound for event %s (pad %s), sample %s, vel %d at tick %ld",
                                                                         event.id.c_str(), padKey.c_str(), selectedLayer->sampleId.c_str(), event.padTrigger.velocity, gCurrentSequence->currentPlayheadTicks);
                                                }
                                            } else {
                                                 __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: Sample '%s' for layer in pad '%s' not found in gSampleMap.",
                                                                     selectedLayer->sampleId.c_str(), padKey.c_str());
                                            }
                                        } else {
                                             __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: No suitable (enabled with sampleId) layer found for pad '%s'.", padKey.c_str());
                                        }
                                    } else {
                                         __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: PadSettings not found for key '%s'.", padKey.c_str());
                                    }
                                } // end if PAD_TRIGGER
                            } // end if event.startTimeTicks == currentPlayheadTicks
                        } // end for events
                    } // end for tracks
                    // TODO: Report playhead position back to Kotlin if needed (e.g., via a JNI callback)
                } // end while ticks to process
            } // end if gCurrentSequence && isPlaying
        } // end if gAudioStreamSampleRate > 0 && gCurrentTickDurationMs > 0
        // --- End C++ Sequencer Processing ---

        // Mixing and main audio processing
        std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);

        if (!newMetronomeSounds.empty()) {
            gActiveSounds.insert(gActiveSounds.end(),
                               std::make_move_iterator(newMetronomeSounds.begin()),
                               std::make_move_iterator(newMetronomeSounds.end()));
            newMetronomeSounds.clear(); // Clear the source vector as its elements are now moved.
        }

        for (auto soundIt = gActiveSounds.begin(); soundIt != gActiveSounds.end(); /* no increment here */) {
            if (!soundIt->isActive.load()) {
                ++soundIt;
                continue;
            }
            theone::audio::PlayingSound &sound = *soundIt;
            const theone::audio::LoadedSample* loadedSample = sound.loadedSamplePtr;

            if (!loadedSample || loadedSample->audioData.empty()) {
                sound.isActive.store(false);
                ++soundIt;
                continue;
            }

            int channels = oboeStream->getChannelCount();
            int sampleChannels = loadedSample->format.channels;

            // --- Envelope and LFO Processing (per sound, before per-frame loop) ---
            float ampEnvValue = 1.0f;
            float pitchEnvValue = 0.0f; // For pitch modulation in semitones or other units
            float filterEnvValue = 1.0f; // For filter modulation (e.g. cutoff multiplier)

            if (sound.padSettings) { // Check if padSettings are available
                if (sound.ampEnvelopeGen) {
                    ampEnvValue = sound.ampEnvelopeGen->process();
                    if (!sound.ampEnvelopeGen->isActive() && ampEnvValue < 0.001f) {
                        sound.isActive.store(false);
                    }
                }
                if (sound.pitchEnvelopeGen && sound.padSettings->hasPitchEnvelope) {
                    pitchEnvValue = sound.pitchEnvelopeGen->process();
                    // Actual application of pitchEnvValue would modify playback rate or resampling
                }
                if (sound.filterEnvelopeGen && sound.padSettings->hasFilterEnvelope) {
                    filterEnvValue = sound.filterEnvelopeGen->process();
                    // Actual application of filterEnvValue would modify filter parameters
                }
            } else { // Fallback for sounds without padSettings (e.g., metronome ticks)
                 if (sound.ampEnvelopeGen) { // Metronome might have a simple amp envelope
                    ampEnvValue = sound.ampEnvelopeGen->process();
                    if (!sound.ampEnvelopeGen->isActive() && ampEnvValue < 0.001f) {
                        sound.isActive.store(false);
                    }
                }
            }

            float lfoVolumeMod = 1.0f; // Multiplicative
            float lfoPanMod = 0.0f;    // Additive
            float lfoPitchMod = 0.0f;  // Additive (e.g. semitones)
            float lfoFilterMod = 0.0f; // Additive or Multiplicative, depending on filter design

            if (sound.padSettings && !sound.lfoGens.empty()) {
                for (size_t i = 0; i < sound.lfoGens.size(); ++i) {
                    auto& lfoGen = sound.lfoGens[i];
                    if (lfoGen && i < sound.padSettings->lfos.size()) {
                        float lfoValue = lfoGen->process();
                        const auto& lfoConfig = sound.padSettings->lfos[i];

                        switch (lfoConfig.primaryDestination) {
                            case theone::audio::LfoDestinationCpp::VOLUME:
                                // Assuming LFO outputs -1 to 1, map to e.g. 0.5 to 1.5 for volume modulation
                                lfoVolumeMod *= (1.0f + lfoValue * lfoConfig.depth * 0.5f);
                                break;
                            case theone::audio::LfoDestinationCpp::PAN:
                                lfoPanMod += lfoValue * lfoConfig.depth; // Depth could be max pan swing
                                break;
                            case theone::audio::LfoDestinationCpp::PITCH:
                                lfoPitchMod += lfoValue * lfoConfig.depth; // Depth in semitones
                                break;
                            case theone::audio::LfoDestinationCpp::FILTER_CUTOFF:
                                lfoFilterMod += lfoValue * lfoConfig.depth; // Depth for filter cutoff
                                break;
                            case theone::audio::LfoDestinationCpp::NONE:
                            default:
                                break;
                        }
                    }
                }
            }
            // Clamp LFO modulations if necessary
            lfoPanMod = std::max(-1.0f, std::min(1.0f, lfoPanMod));
            lfoVolumeMod = std::max(0.0f, lfoVolumeMod); // Ensure volume doesn't go negative

            // Apply pitch modulation (conceptual - actual implementation is complex)
            // float effectivePitchMod = pitchEnvValue + lfoPitchMod;
            // Resampling or playback rate adjustment would happen based on effectivePitchMod

            // Apply filter modulation (conceptual - actual implementation is complex)
            // float effectiveFilterCutoffMod = filterEnvValue * (or +) lfoFilterMod;
            // Filter parameters would be updated here

            float currentPan = sound.initialPan + lfoPanMod;
            currentPan = std::max(-1.0f, std::min(1.0f, currentPan)); // Clamp final pan
            float panRad = (currentPan * 0.5f + 0.5f) * (static_cast<float>(M_PI) / 2.0f);

            float overallGain = sound.initialVolume * ampEnvValue * lfoVolumeMod; // Apply LFO volume mod
            float finalGainL = overallGain * cosf(panRad);
            float finalGainR = overallGain * sinf(panRad);
            // --- End Envelope and LFO Processing ---

            for (int i = 0; i < numFrames; ++i) {
                if (!sound.isActive.load()) {
                    break;
                }

                // === START NEW SLICING/LOOPING LOGIC ===
                if (sound.useSlicing) {
                    if (sound.currentFrame >= sound.endFrame) {
                        if (sound.isLooping && sound.loopStartFrame < sound.loopEndFrame && sound.loopEndFrame <= sound.endFrame && sound.loopStartFrame < sound.endFrame) {
                            sound.currentFrame = sound.loopStartFrame;
                        } else {
                            sound.isActive.store(false);
                        }
                    }
                } else { // Original logic for non-sliced sounds
                    if (sound.currentFrame >= loadedSample->frameCount) {
                        sound.isActive.store(false);
                    }
                }

                if (!sound.isActive.load()) {
                     break;
                }
                if (sound.currentFrame >= loadedSample->frameCount) {
                     sound.isActive.store(false);
                     break;
                }
                // === END NEW SLICING/LOOPING LOGIC ===

                float leftSampleValue = 0.0f;
                float rightSampleValue = 0.0f;

                if (sampleChannels == 1) { // Mono sample
                    float sampleValue = loadedSample->audioData[sound.currentFrame];
                    leftSampleValue = sampleValue * finalGainL; // Apply final calculated gain
                    rightSampleValue = sampleValue * finalGainR;
                } else { // Stereo sample
                    float L = loadedSample->audioData[sound.currentFrame * sampleChannels];
                    float R = loadedSample->audioData[sound.currentFrame * sampleChannels + 1];
                    leftSampleValue = L * finalGainL; // Apply final calculated gain
                    rightSampleValue = R * finalGainR;
                }

                if (channels == 2) { // Stereo output
                    outputBuffer[i * 2]     += leftSampleValue;
                    outputBuffer[i * 2 + 1] += rightSampleValue;
                } else if (channels == 1) { // Mono output
                    outputBuffer[i] += (leftSampleValue + rightSampleValue) * 0.5f;
                }
                sound.currentFrame++;
            }
            ++soundIt;
        }

        gActiveSounds.erase(
                std::remove_if(gActiveSounds.begin(), gActiveSounds.end(),
                               [](const theone::audio::PlayingSound& s) {
                                   return !s.isActive.load();
                               }),
                gActiveSounds.end());

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorBeforeClose: %s", oboe::convertToText(error));
    }

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorAfterClose: %s", oboe::convertToText(error));
    }
};

// static MyAudioCallback myCallback; // Replaced by AudioEngine instance as callback
// static oboe::ManagedStream outStream; // Managed by AudioEngine instance
// static bool oboeInitialized = false; // Managed by AudioEngine instance

// Define the Audio Input Callback class (if not part of AudioEngine)
// If AudioEngine handles input callback as well, this might be removed or adapted.
// For this step, assuming AudioEngine might use a separate input callback or this remains for now.
class MyAudioInputCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *inputStream,
            void *audioData,
            int32_t numFrames) override {

        if (mIsRecording.load() && mWavWriterInitialized) {
            std::lock_guard<std::mutex> lock(gRecordingStateMutex);
            if (!mIsRecording.load() || !mWavWriterInitialized) {
                return oboe::DataCallbackResult::Continue;
            }
            const float* inputBuffer = static_cast<const float*>(audioData);
            drwav_uint64 framesWritten = drwav_write_pcm_frames(&mWavWriter, numFrames, inputBuffer);
            if (framesWritten != static_cast<drwav_uint64>(numFrames)) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback: Failed to write all PCM frames. Wrote %llu/%d", (unsigned long long)framesWritten, numFrames);
            }
            float currentMaxAbs = 0.0f;
            int numSamplesInBlock = numFrames * inputStream->getChannelCount();
            for (int i = 0; i < numSamplesInBlock; ++i) {
                float absValue = std::abs(inputBuffer[i]);
                if (absValue > currentMaxAbs) {
                    currentMaxAbs = absValue;
                }
            }
            float previousPeak = mPeakRecordingLevel.load();
            if (currentMaxAbs > previousPeak) {
                mPeakRecordingLevel.store(currentMaxAbs);
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorBeforeClose: %s", oboe::convertToText(error));
    }

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorAfterClose: %s", oboe::convertToText(error));
    }
};
static MyAudioInputCallback myInputCallback;


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1startAudioRecording(
        JNIEnv *env,
        jobject /* thiz */,
        jint jFd,
        jstring jStoragePathForMetadata,
        jint jSampleRate,
        jint jChannels) {

    std::lock_guard<std::mutex> lock(gRecordingStateMutex);

    if (mIsRecording.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "startAudioRecording: Already recording.");
        if (jFd >= 0) close(jFd);
        return JNI_FALSE;
    }

    const char* pathChars = env->GetStringUTFChars(jStoragePathForMetadata, nullptr);
    if (pathChars) {
        mCurrentRecordingFilePath = pathChars;
        env->ReleaseStringUTFChars(jStoragePathForMetadata, pathChars);
    } else {
        mCurrentRecordingFilePath = "";
    }

    if (jFd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Invalid file descriptor provided (%d).", jFd);
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mRecordingFileDescriptor = jFd; // Store the original FD for closing by Kotlin

    drwav_data_format wavFormat;
    wavFormat.container = drwav_container_riff;
    wavFormat.format = DR_WAVE_FORMAT_IEEE_FLOAT;
    wavFormat.channels = static_cast<uint32_t>(jChannels);
    wavFormat.sampleRate = static_cast<uint32_t>(jSampleRate);
    wavFormat.bitsPerSample = 32;

    // Use drwav_init_write_sequential_pcm_frames with custom write and seek procedures
    // We pass the original jFd (cast to void*) as pUserData to our callbacks.
    // dr_wav will not close this FD; Kotlin side is responsible.
    // We need to dup the fd because drwav_init_write_sequential_pcm_frames will call lseek, which will modify the file offset of the fd.
    // If we don't dup the fd, then the original fd's offset will be modified, which could cause problems for other code that uses the original fd.
    int recordingFdDup = dup(mRecordingFileDescriptor);
    if (recordingFdDup < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to dup file descriptor: %s", strerror(errno));
        mCurrentRecordingFilePath = "";
        // No need to close mRecordingFileDescriptor here, Kotlin will do it if jFd was valid.
        // If dup failed, mRecordingFileDescriptor is still the original one from Kotlin.
        return JNI_FALSE;
    }

    if (!drwav_init_write(&mWavWriter, &wavFormat, drwav_write_proc_fd, drwav_seek_proc_fd, (void*)(intptr_t)recordingFdDup, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to initialize drwav writer with procs for FD: %d (dup: %d)", mRecordingFileDescriptor, recordingFdDup);
        close(recordingFdDup); // Close the duplicated FD as init failed
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mWavWriterInitialized = true;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer initialized with procs for FD: %d (dup: %d)", mRecordingFileDescriptor, recordingFdDup);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(static_cast<oboe::ChannelCount>(jChannels))
            ->setSampleRate(static_cast<int32_t>(jSampleRate))
            ->setInputPreset(oboe::InputPreset::VoiceRecognition)
            ->setCallback(&myInputCallback);

    oboe::Result result = builder.openManagedStream(mInputStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to open Oboe input stream: %s", oboe::convertToText(result));
        drwav_uninit(&mWavWriter); // Uninit writer if Oboe fails
        mWavWriterInitialized = false;
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }

    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to start Oboe input stream: %s", oboe::convertToText(result));
        mInputStream->close();
        drwav_uninit(&mWavWriter);
        mWavWriterInitialized = false;
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }

    mIsRecording.store(true);
    mPeakRecordingLevel.store(0.0f);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording started successfully to path: %s", mCurrentRecordingFilePath.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_theone_audio_AudioEngine_native_1stopAudioRecording(
        JNIEnv *env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gRecordingStateMutex);

    if (!mIsRecording.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Not recording.");
        return nullptr;
    }
    mIsRecording.store(false);

    if (mInputStream) {
        mInputStream->requestStop();
        mInputStream->close();
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream stopped and closed.");
    }

    drwav_uint64 totalFramesWritten = 0;
    std::string tempRecordingPath = mCurrentRecordingFilePath;

    if (mWavWriterInitialized) {
        totalFramesWritten = mWavWriter.totalPCMFrameCount;
        if (!drwav_uninit(&mWavWriter)) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to uninitialize drwav writer.");
        } else {
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer uninitialized. Total frames: %llu", (unsigned long long)totalFramesWritten);
        }
        mWavWriterInitialized = false;
    }

    // Kotlin is responsible for closing the mRecordingFileDescriptor
    mRecordingFileDescriptor = -1;
    mCurrentRecordingFilePath = "";

    jstring jRecordedPath = env->NewStringUTF(tempRecordingPath.c_str());
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
    jobject jTotalFrames = env->NewObject(longClass, longConstructor, static_cast<jlong>(totalFramesWritten));
    jclass objectClass = env->FindClass("java/lang/Object");
    jobjectArray resultArray = env->NewObjectArray(2, objectClass, nullptr);
    env->SetObjectArrayElement(resultArray, 0, jRecordedPath);
    env->SetObjectArrayElement(resultArray, 1, jTotalFrames);

    env->DeleteLocalRef(jRecordedPath);
    env->DeleteLocalRef(jTotalFrames);
    env->DeleteLocalRef(longClass);
    env->DeleteLocalRef(objectClass);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording stopped. Path: %s, Frames: %llu",
                        tempRecordingPath.c_str(), (unsigned long long)totalFramesWritten);
    return resultArray;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1initOboe(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngineInstance && audioEngineInstance->isOboeInitialized()) {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine already initialized.");
        return JNI_TRUE;
    }
    audioEngineInstance = std::make_unique<theone::audio::AudioEngine>();
    if (audioEngineInstance && audioEngineInstance->initialize()) {
        // If global gAudioStreamSampleRate is still used by parts of native-lib.cpp not yet refactored:
        // gAudioStreamSampleRate = static_cast<uint32_t>(audioEngineInstance->getOboeReportedSampleRate()); // You'd need a getter in AudioEngine
        // Same for gMetronomeState if it's still global and needs the sample rate.
        // For now, assuming AudioEngine internally manages what it needs.
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine initialized via native_initOboe.");
        return JNI_TRUE;
    }
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to initialize AudioEngine via native_initOboe.");
    audioEngineInstance.reset(); // Ensure cleanup if init failed
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1loadSampleToMemory(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId,
        jint fd,
        jlong offset,
        jlong length) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_loadSampleToMemory for ID: %s, FD: %d, Offset: %lld, Length: %lld",
                        sampleIdStr.c_str(), fd, (long long)offset, (long long)length);

    if (gSampleMap.count(sampleIdStr)) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample ID '%s' already loaded.", sampleIdStr.c_str());
        return JNI_TRUE;
    }

    drwav wav;
    // For reading, dr_wav_init_fd is appropriate.
    // We must duplicate the file descriptor if dr_wav_init_fd is going to close it
    // or if we want to manage the original fd's lifecycle independently.
    // According to dr_wav.h, drwav_init_fd does *not* take ownership of the fd,
    // so duping is not strictly necessary for dr_wav itself, but good practice if multiple
    // systems might use the fd or if its lifetime is complex. Kotlin side will close original.
    // However, it's safer to work with a duplicated FD if we are doing seeks etc.
    // For simplicity, and if Kotlin guarantees closure, we can use the original fd.
    // Let's assume Kotlin passes a valid, readable FD that it will close.
    // If lseek is needed for the offset:
    if (offset > 0) {
        if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) == -1) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to lseek to offset %lld for FD %d: %s", (long long)offset, fd, strerror(errno));
            return JNI_FALSE;
        }
    }
    // Now fd is positioned at the correct offset.

    if (!drwav_init(&wav, on_read_c, on_seek_c, (void*)(intptr_t)fd, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to init drwav with callbacks for sample %s", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    unsigned int channels = wav.channels;
    unsigned int sampleRate = wav.sampleRate;
    drwav_uint64 totalFrameCount = wav.totalPCMFrameCount;

    // Allocate buffer for all PCM frames
    // Using malloc as new(std::nothrow) might not be universally available/configured in all NDK toolchains without C++ exceptions
    float* pSampleData = (float*)malloc(totalFrameCount * channels * sizeof(float));
    if (pSampleData == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to allocate memory for sample %s. Size: %llu frames, %u channels.",
                            sampleIdStr.c_str(), (unsigned long long)totalFrameCount, channels);
        drwav_uninit(&wav);
        return JNI_FALSE;
    }

    // Read all PCM frames
    drwav_uint64 framesRead = drwav_read_pcm_frames_f32(&wav, totalFrameCount, pSampleData);

    if (framesRead != totalFrameCount) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to read all frames for sample %s. Read %llu of %llu.",
                            sampleIdStr.c_str(), (unsigned long long)framesRead, (unsigned long long)totalFrameCount);
        free(pSampleData);
        drwav_uninit(&wav);
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Successfully decoded WAV: %u Ch, %u Hz, %llu Frames",
                        channels, sampleRate, (unsigned long long)totalFrameCount);

    theone::audio::LoadedSample loadedSampleInstance; // Correctly declare instance
    loadedSampleInstance.id = sampleIdStr;
    loadedSampleInstance.format.channels = channels;
    loadedSampleInstance.format.sampleRate = sampleRate;
    loadedSampleInstance.format.bitDepth = wav.bitsPerSample; // This will be 32 for f32
    loadedSampleInstance.frameCount = totalFrameCount;
    loadedSampleInstance.audioData.assign(pSampleData, pSampleData + (totalFrameCount * channels));

    free(pSampleData); // Free the buffer allocated by malloc
    drwav_uninit(&wav);   // Uninitialize dr_wav, this does NOT close the original fd.

    gSampleMap[sampleIdStr] = loadedSampleInstance;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' loaded. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size());

    return JNI_TRUE;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playPadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jNoteInstanceId,
        jstring jTrackId,
        jstring jPadId,
        jstring jSampleId, // Fallback if PadSettings not found or layer has no sampleId
        jstring jSliceId, // Can be null, currently not used with full PadSettings path
        jfloat velocity,  // Note velocity (0.0 to 1.0)
        jint coarseTune,  // Fallback
        jint fineTune,    // Fallback
        jfloat pan,       // Fallback
        jfloat volume,    // Fallback
        // New parameters matching AudioEngine.kt
        jint jPlaybackModeOrdinal,
        jfloat jAmpEnvAttackMs,
        jfloat jAmpEnvDecayMs,
        jfloat jAmpEnvSustainLevel,
        jfloat jAmpEnvReleaseMs
        // TODO: Add JNI params for filterEnv, pitchEnv, LFOs later
) {
    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    const char *nativeTrackId = env->GetStringUTFChars(jTrackId, nullptr);
    const char *nativePadId = env->GetStringUTFChars(jPadId, nullptr);
    std::string padKey = std::string(nativeTrackId) + "_" + std::string(nativePadId);
    env->ReleaseStringUTFChars(jTrackId, nativeTrackId);
    env->ReleaseStringUTFChars(jPadId, nativePadId);

    std::shared_ptr<theone::audio::PadSettingsCpp> padSettingsPtr;
    {
        std::lock_guard<std::mutex> lock(gPadSettingsMutex);
        auto it = gPadSettingsMap.find(padKey);
        if (it != gPadSettingsMap.end()) {
            padSettingsPtr = std::make_shared<theone::audio::PadSettingsCpp>(it->second);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "playPadSample: PadSettings not found for key %s. Playing direct sample (if provided) or failing.", padKey.c_str());
            // Fallback to direct sample playback if jSampleId is valid
            // This part can be removed if strict PadSettings usage is enforced.
            const char *fallbackSampleIdChars = env->GetStringUTFChars(jSampleId, nullptr);
            if (!fallbackSampleIdChars || strlen(fallbackSampleIdChars) == 0) {
                 env->ReleaseStringUTFChars(jSampleId, fallbackSampleIdChars);
                 return JNI_FALSE; // No settings, no fallback sample ID
            }
            SampleId fallbackSampleIdStr(fallbackSampleIdChars);
            env->ReleaseStringUTFChars(jSampleId, fallbackSampleIdChars);

            auto mapIt = gSampleMap.find(fallbackSampleIdStr);
            if (mapIt == gSampleMap.end()) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample (fallback): Sample ID '%s' not found.", fallbackSampleIdStr.c_str());
                return JNI_FALSE;
            }
            const theone::audio::LoadedSample* loadedSample = &(mapIt->second);
            if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample (fallback): Sample ID '%s' has no audio data.", fallbackSampleIdStr.c_str());
                return JNI_FALSE;
            }
            std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);
            gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan); // Using JNI params for vol/pan
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (fallback) added. Active sounds: %zu", fallbackSampleIdStr.c_str(), gActiveSounds.size());
            return JNI_TRUE;
        }
    }

    // If padSettingsPtr is valid, proceed with layer logic.
    if (!padSettingsPtr) {
         __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: padSettingsPtr is null after lock, should not happen if logic is correct.");
         return JNI_FALSE; // Should have been handled by return JNI_FALSE in the map lookup.
    }

    const theone::audio::SampleLayerCpp* selectedLayer = nullptr;
    if (padSettingsPtr->layers.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Pad %s has no layers defined.", padKey.c_str());
        return JNI_FALSE;
    }

    std::vector<const theone::audio::SampleLayerCpp*> enabledLayers;
    for (const auto& layer : padSettingsPtr->layers) {
        if (layer.enabled) {
            enabledLayers.push_back(&layer);
        }
    }

    if (enabledLayers.empty()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "playPadSample: Pad %s has no ENABLED layers.", padKey.c_str());
        return JNI_FALSE;
    }

    float noteVelocity = velocity; // from JNI parameter (0.0 to 1.0)

    switch (padSettingsPtr->layerTriggerRule) {
        case theone::audio::LayerTriggerRuleCpp::VELOCITY: {
            int intVelocity = static_cast<int>(noteVelocity * 127.0f);
            for (const auto& layerPtr : enabledLayers) {
                if (intVelocity >= layerPtr->velocityRangeMin && intVelocity <= layerPtr->velocityRangeMax) {
                    selectedLayer = layerPtr;
                    break;
                }
            }
            if (!selectedLayer && !enabledLayers.empty()) {
                selectedLayer = enabledLayers.front();
                __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Velocity fallback: no layer matched velocity %d, picked first enabled.", intVelocity);
            }
            break;
        }
        case theone::audio::LayerTriggerRuleCpp::CYCLE: {
            std::lock_guard<std::mutex> cycleLock(gPadSettingsMutex);
            auto mapEntryIt = gPadSettingsMap.find(padKey);
            if (mapEntryIt != gPadSettingsMap.end()) {
                theone::audio::PadSettingsCpp& originalPadSettings = mapEntryIt->second;
                // Re-filter enabled layers from originalPadSettings in case they changed.
                // This is a bit complex; for now, assume enabledLayers from the shared_ptr copy is sufficient for indexing.
                // A truly robust solution might need to re-evaluate enabledLayers based on originalPadSettings.layers here.
                if (!enabledLayers.empty()) { // Use the previously filtered list from padSettingsPtr for selection.
                                              // The index is applied to this list.
                    originalPadSettings.currentCycleLayerIndex %= enabledLayers.size();
                    selectedLayer = enabledLayers[originalPadSettings.currentCycleLayerIndex];
                    originalPadSettings.currentCycleLayerIndex = (originalPadSettings.currentCycleLayerIndex + 1) % enabledLayers.size();
                }
            }
            break;
        }
        case theone::audio::LayerTriggerRuleCpp::RANDOM: {
            if (!enabledLayers.empty()) {
                std::uniform_int_distribution<> distrib(0, static_cast<int>(enabledLayers.size() - 1));
                selectedLayer = enabledLayers[distrib(gRandomEngine)]; // Ensure gRandomEngine is used
            }
            break;
        }
    }

    if (!selectedLayer) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Could not select a layer for pad %s.", padKey.c_str());
        return JNI_FALSE;
    }

    SampleId resolvedSampleIdStr = selectedLayer->sampleId;
    if (resolvedSampleIdStr.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Selected layer for pad %s has an empty sampleId.", padKey.c_str());
        return JNI_FALSE;
    }

    auto mapIt = gSampleMap.find(resolvedSampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' (from layer) not found.", resolvedSampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' (from layer) has no audio data.", resolvedSampleIdStr.c_str());
        return JNI_FALSE;
    }

    float baseVolume = padSettingsPtr->volume;
    float basePan = padSettingsPtr->pan;
    float volumeOffsetGain = powf(10.0f, selectedLayer->volumeOffsetDb / 20.0f);
    float finalVolume = baseVolume * volumeOffsetGain;
    float finalPan = basePan + selectedLayer->panOffset;
    finalPan = std::max(-1.0f, std::min(1.0f, finalPan));

    // TODO: Apply coarse/fine tuning (padSettingsPtr->tuningCoarse/Fine + selectedLayer->tuningCoarse/FineOffset)
    // This is complex and involves changing playback speed or resampling. Deferred for now.

    theone::audio::PlayingSound soundToMove(loadedSample, noteInstanceIdStr, finalVolume, finalPan);

    // Assign the retrieved padSettingsPtr to the sound
    soundToMove.padSettings = padSettingsPtr;

    float sr = outStream ? static_cast<float>(outStream->getSampleRate()) : 48000.0f;
    if (sr <= 0) sr = 48000.0f; // Safety net for sample rate

    // Prioritize PadSettingsCpp for envelope configuration
    if (soundToMove.padSettings) {
        // Amp Envelope
        soundToMove.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
        soundToMove.ampEnvelopeGen->configure(soundToMove.padSettings->ampEnvelope, sr, noteVelocity);
        soundToMove.ampEnvelopeGen->triggerOn(noteVelocity);

        // Pitch Envelope
        if (soundToMove.padSettings->hasPitchEnvelope) {
            soundToMove.pitchEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
            soundToMove.pitchEnvelopeGen->configure(soundToMove.padSettings->pitchEnvelope, sr, noteVelocity);
            soundToMove.pitchEnvelopeGen->triggerOn(noteVelocity);
        }

        // Filter Envelope
        if (soundToMove.padSettings->hasFilterEnvelope) {
            soundToMove.filterEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
            soundToMove.filterEnvelopeGen->configure(soundToMove.padSettings->filterEnvelope, sr, noteVelocity);
            soundToMove.filterEnvelopeGen->triggerOn(noteVelocity);
        }

        // LFOs
        soundToMove.lfoGens.clear(); // Clear any prior LFOs
        float tempo = 120.0f; // Default tempo
        // Consider getting tempo from a more reliable source if sequencer is active
        // For now, use metronome BPM as a proxy if available
        {
            std::lock_guard<std::mutex> metroLock(gMetronomeStateMutex);
            if (gMetronomeState.bpm.load() > 0) { // Check if BPM is valid
                tempo = gMetronomeState.bpm.load();
            }
        }
         if (gCurrentSequence) { // Prefer sequencer tempo if available
            std::lock_guard<std::mutex> seqLock(gSequencerMutex);
            if (gCurrentSequence && gCurrentSequence->bpm > 0) {
                tempo = gCurrentSequence->bpm;
            }
        }


        for (const auto& lfoConfig : soundToMove.padSettings->lfos) {
            if (lfoConfig.isEnabled) {
                auto lfo = std::make_unique<theone::audio::LfoGenerator>();
                lfo->configure(lfoConfig, sr, tempo);
                lfo->retrigger(); // Retrigger based on LFO config (e.g. if note-on retrigger is set)
                soundToMove.lfoGens.push_back(std::move(lfo));
            }
        }

    } else {
        // Fallback to JNI parameters if padSettingsPtr was not available (though current logic implies it should be)
        // This block would typically be hit if the initial gPadSettingsMap.find() failed AND a fallback path was taken,
        // but the current structure ensures padSettingsPtr is valid if we reach here, or returns early.
        // For robustness, or if the JNI params are meant as an override/alternative path:
        theone::audio::EnvelopeSettingsCpp ampEnvelopeFromParams;
        ampEnvelopeFromParams.type = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR; // Default type
        ampEnvelopeFromParams.attackMs = jAmpEnvAttackMs;
        ampEnvelopeFromParams.holdMs = 0; // JNI doesn't pass hold for amp
        ampEnvelopeFromParams.decayMs = jAmpEnvDecayMs;
        ampEnvelopeFromParams.sustainLevel = jAmpEnvSustainLevel;
        ampEnvelopeFromParams.releaseMs = jAmpEnvReleaseMs;

        soundToMove.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
        soundToMove.ampEnvelopeGen->configure(ampEnvelopeFromParams, sr, noteVelocity);
        soundToMove.ampEnvelopeGen->triggerOn(noteVelocity);
        // No LFOs or other envelopes in this fallback path from JNI params
    }

    // Slicing parameters (currently not set by PadSettings, but PlayingSound supports them)
    // If PadSettings were to include slice points for the selected layer's sample, they'd be applied here.
    // For now, it will play the full sample as per LoadedSample.
    // soundToMove.startFrame = ...; soundToMove.endFrame = ...; soundToMove.isLooping = ...; etc.

    std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);
    gActiveSounds.push_back(std::move(soundToMove));
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (Pad: %s, Layer: %s) added. Active sounds: %zu",
                        resolvedSampleIdStr.c_str(), padKey.c_str(), selectedLayer->id.c_str(), gActiveSounds.size());

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playSampleSlice(
        JNIEnv *env, jobject,
        jstring jSampleId,
        jstring jNoteInstanceId,
        jfloat volume,
        jfloat pan,
        jint jSampleRate, // Sample rate passed from Kotlin (potentially for calculation if metadata not available)
        jlong jTrimStartMs,
        jlong jTrimEndMs,
        jlong jLoopStartMs,
        jlong jLoopEndMs,
        jboolean jIsLooping
) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    // 1. Retrieve LoadedSample and Actual Sample Rate
    auto mapIt = gSampleMap.find(sampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' not found.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' has no audio data or zero frames.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    uint32_t actualSampleRate = loadedSample->format.sampleRate;
    if (actualSampleRate == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' has an actual sample rate of 0. Cannot proceed.", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    if (static_cast<uint32_t>(jSampleRate) != actualSampleRate) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                            "playSampleSlice: Kotlin sampleRate (%d) differs from actual sampleRate (%u) for ID '%s'. Using actual rate for calculations.",
                            jSampleRate, actualSampleRate, sampleIdStr.c_str());
    }

    // 2. Convert Milliseconds to Frames
    size_t startFrame = (jTrimStartMs * actualSampleRate) / 1000;
    size_t endFrame = (jTrimEndMs * actualSampleRate) / 1000; // If jTrimEndMs is 0, endFrame becomes 0. Constructor handles this.
    size_t loopStartFrame = 0;
    size_t loopEndFrame = 0;

    if (jIsLooping == JNI_TRUE) {
        loopStartFrame = (jLoopStartMs * actualSampleRate) / 1000;
        loopEndFrame = (jLoopEndMs * actualSampleRate) / 1000; // If jLoopEndMs is 0, loopEndFrame becomes 0. Constructor handles this.
    }

    // 5. Logging (Initial values)
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "playSampleSlice: ID='%s', SR_kotlin=%d, SR_actual=%u, Vol=%.2f, Pan=%.2f, TrimMs:[%lld-%lld], LoopMs:[%lld-%lld], Loop:%d",
                        sampleIdStr.c_str(), jSampleRate, actualSampleRate, volume, pan,
                        (long long)jTrimStartMs, (long long)jTrimEndMs,
                        (long long)jLoopStartMs, (long long)jLoopEndMs, jIsLooping == JNI_TRUE);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Calculated initial frames: Start=%zu, End=%zu, LoopStart=%zu, LoopEnd=%zu (before constructor adjustment for 0 end/loopEnd)",
                        startFrame, endFrame, loopStartFrame, loopEndFrame);


    // 3. Validate Frame Indices
    // Note: PlayingSound constructor also does some validation, especially for endFrame=0
    if (startFrame >= loadedSample->frameCount) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                            "playSampleSlice: Calculated startFrame (%zu) is out of bounds for sample '%s' (frameCount: %zu).",
                            startFrame, sampleIdStr.c_str(), loadedSample->frameCount);
        return JNI_FALSE;
    }

    // If endFrame was calculated from a non-zero jTrimEndMs, and it's beyond frameCount.
    // If endFrame is 0 (from jTrimEndMs = 0), constructor will set it to frameCount.
    if (endFrame != 0 && endFrame > loadedSample->frameCount) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                            "playSampleSlice: Calculated endFrame (%zu) exceeds sample frameCount (%zu) for '%s'. Clamping to frameCount.",
                            endFrame, loadedSample->frameCount, sampleIdStr.c_str());
        endFrame = loadedSample->frameCount;
    }
     if (startFrame >= endFrame && endFrame != 0) { // endFrame == 0 means play to end, handled by constructor
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                            "playSampleSlice: startFrame (%zu) must be less than endFrame (%zu) for '%s'.",
                            startFrame, endFrame, sampleIdStr.c_str());
        return JNI_FALSE;
    }


    if (jIsLooping == JNI_TRUE) {
        if (loopStartFrame >= (loopEndFrame == 0 ? loadedSample->frameCount : loopEndFrame) ) {
             __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                                "playSampleSlice: loopStartFrame (%zu) must be less than loopEndFrame (%zu, effective: %zu) for '%s'.",
                                loopStartFrame, loopEndFrame, (loopEndFrame == 0 ? loadedSample->frameCount : loopEndFrame), sampleIdStr.c_str());
            return JNI_FALSE;
        }
        // Loop points should be within the main trim region (or sample bounds if endFrame is 0)
        size_t effectiveEndFrame = (endFrame == 0) ? loadedSample->frameCount : endFrame;
        if (loopStartFrame < startFrame || loopEndFrame > effectiveEndFrame) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                                "playSampleSlice: Loop region [%zu-%zu] is outside effective play region [%zu-%zu] for '%s'. Adjusting or error might occur.",
                                loopStartFrame, loopEndFrame, startFrame, effectiveEndFrame, sampleIdStr.c_str());
            // Depending on desired behavior, either clamp loop points or return false.
            // For now, we'll let the PlayingSound constructor handle potential inconsistencies,
            // but it's good to log.
        }
         if (loopEndFrame != 0 && loopEndFrame > loadedSample->frameCount) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                                "playSampleSlice: Calculated loopEndFrame (%zu) exceeds sample frameCount (%zu) for '%s'. Clamping in constructor.",
                                loopEndFrame, loadedSample->frameCount, sampleIdStr.c_str());
            // Constructor will clamp this.
        }
    }

    // 4. Instantiate PlayingSound
    std::lock_guard<std::mutex> lock(gActiveSoundsMutex);
    gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan,
                               startFrame, endFrame,
                               loopStartFrame, loopEndFrame,
                               jIsLooping == JNI_TRUE);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Sample '%s' (slice) added. Active sounds: %zu. Effective playback frames after constructor: current=%zu, start=%zu, end=%zu, loopStart=%zu, loopEnd=%zu, looping=%d, useSlicing=%d",
                        sampleIdStr.c_str(), gActiveSounds.size(),
                        gActiveSounds.back().currentFrame, gActiveSounds.back().startFrame, gActiveSounds.back().endFrame,
                        gActiveSounds.back().loopStartFrame, gActiveSounds.back().loopEndFrame,
                        gActiveSounds.back().isLooping, gActiveSounds.back().useSlicing);

    return JNI_TRUE;
}


// Implement other JNI functions (isSampleLoaded, unloadSample, setMetronomeState, etc.) as before

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isSampleLoaded(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    return static_cast<jboolean>(gSampleMap.count(sampleIdStr) > 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1unloadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    gSampleMap.erase(sampleIdStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setMetronomeState(
        JNIEnv *env,
        jobject /* thiz */,
        jboolean jIsEnabled,
        jfloat jBpm,
        jint jTimeSignatureNum,
        jint jTimeSignatureDen,
        jstring jPrimarySoundSampleId,
        jstring jSecondarySoundSampleId) {

    const char *primarySampleIdChars = env->GetStringUTFChars(jPrimarySoundSampleId, nullptr);
    SampleId primarySampleIdStr = primarySampleIdChars ? primarySampleIdChars : "";
    if (primarySampleIdChars) env->ReleaseStringUTFChars(jPrimarySoundSampleId, primarySampleIdChars);

    SampleId secondarySampleIdStr = "";
    if (jSecondarySoundSampleId != nullptr) {
        const char *secondarySampleIdChars = env->GetStringUTFChars(jSecondarySoundSampleId, nullptr);
        secondarySampleIdStr = secondarySampleIdChars ? secondarySampleIdChars : "";
        if (secondarySampleIdChars) env->ReleaseStringUTFChars(jSecondarySoundSampleId, secondarySampleIdChars);
    }

    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.enabled.store(jIsEnabled == JNI_TRUE);
    gMetronomeState.bpm.store(jBpm);
    gMetronomeState.timeSignatureNum.store(jTimeSignatureNum);
    gMetronomeState.timeSignatureDen.store(jTimeSignatureDen);

    auto primaryIt = gSampleMap.find(primarySampleIdStr);
    gMetronomeState.primaryBeatSample = (primaryIt != gSampleMap.end()) ? &(primaryIt->second) : nullptr;

    if (!secondarySampleIdStr.empty()) {
        auto secondaryIt = gSampleMap.find(secondarySampleIdStr);
        gMetronomeState.secondaryBeatSample = (secondaryIt != gSampleMap.end()) ? &(secondaryIt->second) : nullptr;
    } else {
        gMetronomeState.secondaryBeatSample = nullptr;
    }

    if (gMetronomeState.audioStreamSampleRate == 0 && oboeInitialized && outStream) {
        gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate());
    }
    gMetronomeState.updateSchedulingParameters();
    if (gMetronomeState.enabled.load()) {
        gMetronomeState.samplesUntilNextBeat = 0;
        gMetronomeState.currentBeatInBar = gMetronomeState.timeSignatureNum.load();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setMetronomeVolume(
        JNIEnv *env,
        jobject /* thiz */,
        jfloat jVolume) {
    float volumeValue = static_cast<float>(jVolume);
    if (volumeValue < 0.0f) volumeValue = 0.0f;
    if (volumeValue > 1.0f) volumeValue = 1.0f;
    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.volume.store(volumeValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1shutdownOboe(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngineInstance) {
        audioEngineInstance->shutdown();
        audioEngineInstance.reset(); // Release the instance
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine shutdown complete via native_shutdownOboe.");
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_shutdownOboe: audioEngineInstance is null.");
    }
    // Clear any remaining global state if it's being phased out.
    // oboeInitialized = false; // This global would be removed if fully refactored.
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isOboeInitialized(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngineInstance) {
        return static_cast<jboolean>(audioEngineInstance->isOboeInitialized());
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getOboeReportedLatencyMillis(
        JNIEnv* env,
        jobject /* this */) {
    if (audioEngineInstance) {
        return audioEngineInstance->getOboeReportedLatencyMillis();
    }
    return -1.0f;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_theone_audio_AudioEngine_native_1stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    // std::string hello = "Hello from C++ (AudioEngine)"; // This is just a test JNI
    // return env->NewStringUTF(hello.c_str());
    if (audioEngineInstance) {
         // Example: if AudioEngine had a similar test method
         // return env->NewStringUTF(audioEngineInstance->getTestString().c_str());
    }
    return env->NewStringUTF("Hello from C++ (AudioEngine - instance not fully integrated yet)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isRecordingActive(
        JNIEnv *env,
        jobject /* thiz */) {
    // return static_cast<jboolean>(mIsRecording.load()); // Old global way
    if (audioEngineInstance) {
        return static_cast<jboolean>(audioEngineInstance->isRecordingActive());
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getRecordingLevelPeak(
        JNIEnv *env,
        jobject /* thiz */) {
    // return mPeakRecordingLevel.exchange(0.0f); // Old global way
    if (audioEngineInstance) {
        return audioEngineInstance->getRecordingLevelPeak();
    }
    return 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getSampleRate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {
    // const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr); // Old global way
    // SampleId sampleIdStr(nativeSampleId);
    // env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    // auto it = gSampleMap.find(sampleIdStr);
    // ... (old global logic)
    if (audioEngineInstance) {
        std::string sampleIdStr = JStringToString(env, jSampleId);
        return audioEngineInstance->getSampleRate(sampleIdStr);
    }
    return 0;
}

// ... Any other JNI functions needed ...
// TODO: Refactor native_loadSampleToMemory, native_isSampleLoaded, native_unloadSample,
// native_playPadSample, native_playSampleSlice, native_setMetronomeState, native_setMetronomeVolume,
// native_startAudioRecording, native_stopAudioRecording, etc. to use audioEngineInstance


// --- JNI Sequence Conversion Helper Functions ---

static theone::audio::PadTriggerEventCpp ConvertKotlinPadTriggerEvent(JNIEnv* env, jobject kotlinPadTriggerEvent) {
    theone::audio::PadTriggerEventCpp cppPadTrigger;
    if (!kotlinPadTriggerEvent) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinPadTriggerEvent: kotlinPadTriggerEvent is null");
        return cppPadTrigger; // Return empty
    }

    jclass padTriggerClass = env->GetObjectClass(kotlinPadTriggerEvent);
    if (!padTriggerClass) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinPadTriggerEvent: GetObjectClass failed for PadTrigger event object.");
        if(env->ExceptionCheck()) env->ExceptionClear();
        return cppPadTrigger;
    }

    jfieldID padIdFid = env->GetFieldID(padTriggerClass, "padId", "Ljava/lang/String;");
    jfieldID velocityFid = env->GetFieldID(padTriggerClass, "velocity", "I");
    jfieldID durationTicksFid = env->GetFieldID(padTriggerClass, "durationTicks", "J");

    if (!padIdFid || !velocityFid || !durationTicksFid) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinPadTriggerEvent: Failed to get one or more field IDs for EventType.PadTrigger");
        env->DeleteLocalRef(padTriggerClass);
        if(env->ExceptionCheck()) env->ExceptionClear();
        return cppPadTrigger;
    }

    jstring jPadId = (jstring)env->GetObjectField(kotlinPadTriggerEvent, padIdFid);
    cppPadTrigger.padId = JStringToString(env, jPadId); // JStringToString should handle null jPadId
    if (jPadId) env->DeleteLocalRef(jPadId);

    cppPadTrigger.velocity = env->GetIntField(kotlinPadTriggerEvent, velocityFid);
    cppPadTrigger.durationTicks = env->GetLongField(kotlinPadTriggerEvent, durationTicksFid);

    env->DeleteLocalRef(padTriggerClass);
    return cppPadTrigger;
}

static theone::audio::EventCpp ConvertKotlinEvent(JNIEnv* env, jobject kotlinEvent) {
    theone::audio::EventCpp cppEvent;
    if (!kotlinEvent) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: kotlinEvent is null");
        return cppEvent;
    }

    jclass eventClass = env->GetObjectClass(kotlinEvent);
    if (!eventClass) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: GetObjectClass failed for Event object.");
        if(env->ExceptionCheck()) env->ExceptionClear();
        return cppEvent;
    }

    jfieldID idFid = env->GetFieldID(eventClass, "id", "Ljava/lang/String;");
    jfieldID trackIdFid = env->GetFieldID(eventClass, "trackId", "Ljava/lang/String;");
    jfieldID startTimeTicksFid = env->GetFieldID(eventClass, "startTimeTicks", "J");
    jfieldID typeFid = env->GetFieldID(eventClass, "type", "Lcom/example/theone/model/EventType;");

    if (!idFid || !trackIdFid || !startTimeTicksFid || !typeFid) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: Failed to get one or more common field IDs for Event");
        env->DeleteLocalRef(eventClass);
        if(env->ExceptionCheck()) env->ExceptionClear();
        return cppEvent;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinEvent, idFid);
    cppEvent.id = JStringToString(env, jId);
    if (jId) env->DeleteLocalRef(jId);

    jstring jEventTrackId = (jstring)env->GetObjectField(kotlinEvent, trackIdFid);
    cppEvent.trackId = JStringToString(env, jEventTrackId);
    if (jEventTrackId) env->DeleteLocalRef(jEventTrackId);

    cppEvent.startTimeTicks = env->GetLongField(kotlinEvent, startTimeTicksFid);

    jobject kotlinEventTypeObj = env->GetObjectField(kotlinEvent, typeFid);
    if (kotlinEventTypeObj) {
        jclass padTriggerEventTypeClass = env->FindClass("com/example/theone/model/EventType$PadTrigger");
        if (padTriggerEventTypeClass) {
            if (env->IsInstanceOf(kotlinEventTypeObj, padTriggerEventTypeClass)) {
                cppEvent.type = theone::audio::EventTriggerTypeCpp::PAD_TRIGGER;
                cppEvent.padTrigger = ConvertKotlinPadTriggerEvent(env, kotlinEventTypeObj);
            } else {
                __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: EventType is not PadTrigger.");
            }
            env->DeleteLocalRef(padTriggerEventTypeClass);
        } else {
             __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: Could not find EventType$PadTrigger class. Check path.");
             if(env->ExceptionCheck()) env->ExceptionClear();
        }
        env->DeleteLocalRef(kotlinEventTypeObj);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinEvent: Event 'type' field is null");
    }

    env->DeleteLocalRef(eventClass);
    return cppEvent;
}

static theone::audio::TrackCpp ConvertKotlinTrack(JNIEnv* env, jobject kotlinTrack) {
    theone::audio::TrackCpp cppTrack;
    if (!kotlinTrack) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: kotlinTrack is null"); return cppTrack; }

    jclass trackClass = env->GetObjectClass(kotlinTrack);
    if (!trackClass) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: GetObjectClass failed."); if(env->ExceptionCheck()) env->ExceptionClear(); return cppTrack; }

    jfieldID idFid = env->GetFieldID(trackClass, "id", "Ljava/lang/String;");
    jfieldID eventsListFid = env->GetFieldID(trackClass, "events", "Ljava/util/List;");

    if (!idFid || !eventsListFid) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: GetFieldID failed."); env->DeleteLocalRef(trackClass); if(env->ExceptionCheck()) env->ExceptionClear(); return cppTrack; }

    jstring jId = (jstring)env->GetObjectField(kotlinTrack, idFid);
    cppTrack.id = JStringToString(env, jId);
    if(jId) env->DeleteLocalRef(jId);

    jobject kotlinEventsList = env->GetObjectField(kotlinTrack, eventsListFid);
    if (kotlinEventsList) {
        jclass listClass = env->FindClass("java/util/List");
        if(!listClass) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: FindClass java/util/List failed."); env->DeleteLocalRef(kotlinEventsList); env->DeleteLocalRef(trackClass); if(env->ExceptionCheck()) env->ExceptionClear(); return cppTrack;}

        jmethodID listSizeMid = env->GetMethodID(listClass, "size", "()I");
        jmethodID listGetMid = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

        if(!listSizeMid || !listGetMid) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: GetMethodID for List size/get failed."); env->DeleteLocalRef(listClass); env->DeleteLocalRef(kotlinEventsList); env->DeleteLocalRef(trackClass); if(env->ExceptionCheck()) env->ExceptionClear(); return cppTrack;}

        int listSize = env->CallIntMethod(kotlinEventsList, listSizeMid);
        cppTrack.events.reserve(listSize);
        for (int i = 0; i < listSize; ++i) {
            jobject kotlinEventObj = env->CallObjectMethod(kotlinEventsList, listGetMid, i);
            if (kotlinEventObj) {
                cppTrack.events.push_back(ConvertKotlinEvent(env, kotlinEventObj));
                env->DeleteLocalRef(kotlinEventObj);
            }
        }
        env->DeleteLocalRef(listClass);
        env->DeleteLocalRef(kotlinEventsList);
    }
    env->DeleteLocalRef(trackClass);
    return cppTrack;
}

static theone::audio::SequenceCpp ConvertKotlinSequence(JNIEnv* env, jobject kotlinSequence) {
    theone::audio::SequenceCpp cppSequence;
    if (!kotlinSequence) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: kotlinSequence is null."); return cppSequence; }

    jclass sequenceClass = env->GetObjectClass(kotlinSequence);
    if (!sequenceClass) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: GetObjectClass failed."); if(env->ExceptionCheck()) env->ExceptionClear(); return cppSequence; }

    jfieldID idFid = env->GetFieldID(sequenceClass, "id", "Ljava/lang/String;");
    jfieldID nameFid = env->GetFieldID(sequenceClass, "name", "Ljava/lang/String;");
    jfieldID bpmFid = env->GetFieldID(sequenceClass, "bpm", "F");
    jfieldID timeSigNumFid = env->GetFieldID(sequenceClass, "timeSignatureNumerator", "I");
    jfieldID timeSigDenFid = env->GetFieldID(sequenceClass, "timeSignatureDenominator", "I");
    jfieldID barLengthFid = env->GetFieldID(sequenceClass, "barLength", "J");
    jfieldID ppqnFid = env->GetFieldID(sequenceClass, "ppqn", "J");
    jfieldID tracksMapFid = env->GetFieldID(sequenceClass, "tracks", "Ljava/util/Map;");

    if (!idFid || !nameFid || !bpmFid || !timeSigNumFid || !timeSigDenFid || !barLengthFid || !ppqnFid || !tracksMapFid) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: Failed to get one or more field IDs for Sequence");
        env->DeleteLocalRef(sequenceClass); if(env->ExceptionCheck()) env->ExceptionClear(); return cppSequence;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinSequence, idFid);
    cppSequence.id = JStringToString(env, jId);
    if(jId) env->DeleteLocalRef(jId);

    jstring jName = (jstring)env->GetObjectField(kotlinSequence, nameFid);
    cppSequence.name = JStringToString(env, jName);
    if(jName) env->DeleteLocalRef(jName);

    cppSequence.bpm = env->GetFloatField(kotlinSequence, bpmFid);
    cppSequence.timeSignatureNumerator = env->GetIntField(kotlinSequence, timeSigNumFid);
    cppSequence.timeSignatureDenominator = env->GetIntField(kotlinSequence, timeSigDenFid);
    cppSequence.barLength = env->GetLongField(kotlinSequence, barLengthFid);
    cppSequence.ppqn = env->GetLongField(kotlinSequence, ppqnFid);

    jobject kotlinTracksMap = env->GetObjectField(kotlinSequence, tracksMapFid);
    if (kotlinTracksMap) {
        jclass mapClass = env->FindClass("java/util/Map");
        jclass setClass = env->FindClass("java/util/Set");
        jclass iteratorClass = env->FindClass("java/util/Iterator");
        jclass entryClass = env->FindClass("java/util/Map$Entry");

        if(!mapClass || !setClass || !iteratorClass || !entryClass) {
            __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: FindClass failed for Map iteration classes.");
            if(env->ExceptionCheck()) env->ExceptionClear(); goto cleanup_seq_map_error;
        }

        jmethodID entrySetMid = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jmethodID iteratorMid = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jmethodID hasNextMid = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMid = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");
        jmethodID getKeyMid = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMid = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        if(!entrySetMid || !iteratorMid || !hasNextMid || !nextMid || !getKeyMid || !getValueMid) {
            __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: GetMethodID failed for Map iteration methods.");
            if(env->ExceptionCheck()) env->ExceptionClear(); goto cleanup_seq_map_error;
        }

        jobject entrySet = env->CallObjectMethod(kotlinTracksMap, entrySetMid);
        if (!entrySet) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: entrySet is null."); if(env->ExceptionCheck()) env->ExceptionClear(); goto cleanup_seq_map_error; }

        jobject iterator = env->CallObjectMethod(entrySet, iteratorMid);
        if (!iterator) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: iterator is null."); env->DeleteLocalRef(entrySet); if(env->ExceptionCheck()) env->ExceptionClear(); goto cleanup_seq_map_error; }

        while (env->CallBooleanMethod(iterator, hasNextMid)) {
            jobject entry = env->CallObjectMethod(iterator, nextMid);
            if (!entry) { __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: got null entry from map iterator."); continue; }

            jstring jTrackMapKey = (jstring)env->CallObjectMethod(entry, getKeyMid);
            jobject kotlinTrackObj = env->CallObjectMethod(entry, getValueMid);

            std::string trackMapKeyStr = JStringToString(env, jTrackMapKey);
            if (!trackMapKeyStr.empty() && kotlinTrackObj) {
                cppSequence.tracks[trackMapKeyStr] = ConvertKotlinTrack(env, kotlinTrackObj);
            } else {
                __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: null/empty track key or null track object in map.");
            }

            if (jTrackMapKey) env->DeleteLocalRef(jTrackMapKey);
            if (kotlinTrackObj) env->DeleteLocalRef(kotlinTrackObj);
            env->DeleteLocalRef(entry);
        }
        env->DeleteLocalRef(iterator);
        env->DeleteLocalRef(entrySet);

cleanup_seq_map_error:
        if (mapClass) env->DeleteLocalRef(mapClass);
        if (setClass) env->DeleteLocalRef(setClass);
        if (iteratorClass) env->DeleteLocalRef(iteratorClass);
        if (entryClass) env->DeleteLocalRef(entryClass);
        env->DeleteLocalRef(kotlinTracksMap);
    }

    env->DeleteLocalRef(sequenceClass);
    return cppSequence;
}

// Helper function to convert Kotlin SampleLayer to SampleLayerCpp
theone::audio::SampleLayerCpp ConvertKotlinSampleLayer(JNIEnv* env, jobject kotlinLayer) {
    theone::audio::SampleLayerCpp cppLayer;
    if (kotlinLayer == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinSampleLayer: kotlinLayer is null");
        return cppLayer; // Return default-constructed layer
    }

    jclass sampleLayerClass = env->GetObjectClass(kotlinLayer);
    if (sampleLayerClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinSampleLayer: GetObjectClass failed for SampleLayer");
        env->ExceptionClear();
        return cppLayer;
    }

    jfieldID idFid = env->GetFieldID(sampleLayerClass, "id", "Ljava/lang/String;");
    jfieldID sampleIdFid = env->GetFieldID(sampleLayerClass, "sampleId", "Ljava/lang/String;");
    jfieldID enabledFid = env->GetFieldID(sampleLayerClass, "enabled", "Z");
    jfieldID velocityRangeMinFid = env->GetFieldID(sampleLayerClass, "velocityRangeMin", "I");
    jfieldID velocityRangeMaxFid = env->GetFieldID(sampleLayerClass, "velocityRangeMax", "I");
    jfieldID tuningCoarseOffsetFid = env->GetFieldID(sampleLayerClass, "tuningCoarseOffset", "I");
    jfieldID tuningFineOffsetFid = env->GetFieldID(sampleLayerClass, "tuningFineOffset", "F"); // Assuming Kotlin is Float, C++ is int in struct, but problem says float
    jfieldID volumeOffsetDbFid = env->GetFieldID(sampleLayerClass, "volumeOffsetDb", "F");
    jfieldID panOffsetFid = env->GetFieldID(sampleLayerClass, "panOffset", "F");

    if (idFid == nullptr || sampleIdFid == nullptr || enabledFid == nullptr ||
        velocityRangeMinFid == nullptr || velocityRangeMaxFid == nullptr ||
        tuningCoarseOffsetFid == nullptr || tuningFineOffsetFid == nullptr ||
        volumeOffsetDbFid == nullptr || panOffsetFid == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinSampleLayer: Failed to get one or more field IDs for SampleLayer");
        env->DeleteLocalRef(sampleLayerClass);
        env->ExceptionClear();
        return cppLayer;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinLayer, idFid);
    cppLayer.id = JStringToString(env, jId);
    env->DeleteLocalRef(jId);

    jstring jSampleId = (jstring)env->GetObjectField(kotlinLayer, sampleIdFid);
    cppLayer.sampleId = JStringToString(env, jSampleId);
    env->DeleteLocalRef(jSampleId);

    cppLayer.enabled = env->GetBooleanField(kotlinLayer, enabledFid);
    cppLayer.velocityRangeMin = env->GetIntField(kotlinLayer, velocityRangeMinFid);
    cppLayer.velocityRangeMax = env->GetIntField(kotlinLayer, velocityRangeMaxFid);
    cppLayer.tuningCoarseOffset = env->GetIntField(kotlinLayer, tuningCoarseOffsetFid);
    // The existing SampleLayerCpp has tuningFineOffset as int, but instructions say float.
    // For now, I will assume the C++ struct needs to be float or I cast.
    // The provided struct has `int tuningFineOffset;` - this seems like a mismatch with instructions.
    // Sticking to the struct for now, will retrieve as float and cast.
    // UPDATE: The problem description for SampleLayerCpp says "float tuningFineOffset", but the C++ struct in the file says "int tuningFineOffset".
    // I will proceed assuming the problem description is the target, which means I should be reading a float.
    // However, the existing struct `SampleLayerCpp` has `int tuningFineOffset;`. This is a conflict.
    // For now, I will retrieve as float and assign, assuming the C++ struct might be changed or this is an oversight.
    // If SampleLayerCpp.tuningFineOffset is indeed an int, then `env->GetFloatField` should be `env->GetIntField` or a cast is needed.
    // Given the problem states "float tuningFineOffset" for SampleLayerCpp, I will use GetFloatField.
    // This implies the struct in native-lib.cpp for SampleLayerCpp might need adjustment if it's truly int.
    // For now, let's assume it's a float field in C++ as per step 2 "Populate a SampleLayerCpp object" which lists "tuningFineOffset".
    // The C++ struct provided in the file has `int tuningFineOffset;`
    // The problem description for step 2 lists `tuningFineOffset` (implying float based on context of other float fields)
    // Let's assume the problem description is the source of truth for the *target* SampleLayerCpp.
    // If the struct in the file is fixed, this will be fine. If not, it's a type mismatch.
    // For now, reading as float from Kotlin.
    // **Correction based on existing struct:** `tuningFineOffset` is `int` in the C++ struct.
    // The instruction "Populate a SampleLayerCpp object" lists it without type, but other similar fields are float.
    // The specific instruction "Get SampleLayer class and field IDs for ... tuningFineOffset" doesn't specify type.
    // Given the C++ struct `SampleLayerCpp { ... int tuningFineOffset; ...}` I must read it as an Int or convert.
    // Kotlin side is `Float`. This is a mismatch.
    // Assuming Kotlin `tuningFineOffset` is `Float` and C++ is `int`.
    // I will get it as float and cast to int for cppLayer.tuningFineOffset.
    cppLayer.tuningFineOffset = static_cast<int>(env->GetFloatField(kotlinLayer, tuningFineOffsetFid)); // Cast to int
    cppLayer.volumeOffsetDb = env->GetFloatField(kotlinLayer, volumeOffsetDbFid);
    cppLayer.panOffset = env->GetFloatField(kotlinLayer, panOffsetFid);

    env->DeleteLocalRef(sampleLayerClass);
    return cppLayer;
}


// Helper function to convert Kotlin EnvelopeSettings to theone::audio::EnvelopeSettingsCpp
theone::audio::EnvelopeSettingsCpp ConvertKotlinEnvelopeSettings(JNIEnv* env, jobject kotlinEnvelopeSettings) {
    theone::audio::EnvelopeSettingsCpp cppSettings; // Default constructor
    if (kotlinEnvelopeSettings == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEnvelopeSettings: kotlinEnvelopeSettings is null");
        return cppSettings; // Return default
    }

    jclass envSettingsClass = env->GetObjectClass(kotlinEnvelopeSettings);
    if (envSettingsClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEnvelopeSettings: GetObjectClass failed for EnvelopeSettings");
        env->ExceptionClear();
        return cppSettings;
    }

    jfieldID typeFid = env->GetFieldID(envSettingsClass, "type", "Lcom/example/theone/model/audioEngine/ModelEnvelopeTypeInternal;");
    jfieldID attackMsFid = env->GetFieldID(envSettingsClass, "attackMs", "F");
    jfieldID holdMsFid = env->GetFieldID(envSettingsClass, "holdMs", "Ljava/lang/Float;"); // Nullable Float
    jfieldID decayMsFid = env->GetFieldID(envSettingsClass, "decayMs", "F");
    jfieldID sustainLevelFid = env->GetFieldID(envSettingsClass, "sustainLevel", "F");
    jfieldID releaseMsFid = env->GetFieldID(envSettingsClass, "releaseMs", "F");

    if (typeFid == nullptr || attackMsFid == nullptr || holdMsFid == nullptr || decayMsFid == nullptr ||
        sustainLevelFid == nullptr || releaseMsFid == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEnvelopeSettings: Failed to get one or more field IDs for EnvelopeSettings");
        env->DeleteLocalRef(envSettingsClass);
        env->ExceptionClear();
        return cppSettings;
    }

    // Type (Enum)
    jobject kotlinTypeEnum = env->GetObjectField(kotlinEnvelopeSettings, typeFid);
    if (kotlinTypeEnum != nullptr) {
        int ordinal = getEnumOrdinal(env, kotlinTypeEnum);
        if (ordinal == 0) cppSettings.type = theone::audio::ModelEnvelopeTypeInternalCpp::AD;
        else if (ordinal == 1) cppSettings.type = theone::audio::ModelEnvelopeTypeInternalCpp::AHDSR;
        else if (ordinal == 2) cppSettings.type = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR;
        else __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinEnvelopeSettings: Unknown ModelEnvelopeTypeInternal ordinal: %d", ordinal);
        env->DeleteLocalRef(kotlinTypeEnum);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinEnvelopeSettings: EnvelopeSettings.type is null, using default");
        // cppSettings.type will use its default from constructor
    }


    cppSettings.attackMs = env->GetFloatField(kotlinEnvelopeSettings, attackMsFid);

    // HoldMs (Nullable Float)
    jobject kotlinHoldMsObj = env->GetObjectField(kotlinEnvelopeSettings, holdMsFid);
    if (kotlinHoldMsObj != nullptr) {
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID floatValueMid = env->GetMethodID(floatClass, "floatValue", "()F");
        cppSettings.holdMs = env->CallFloatMethod(kotlinHoldMsObj, floatValueMid);
        env->DeleteLocalRef(floatClass);
        env->DeleteLocalRef(kotlinHoldMsObj);
    } else {
        cppSettings.holdMs = 0.0f; // Default if null
    }

    cppSettings.decayMs = env->GetFloatField(kotlinEnvelopeSettings, decayMsFid);
    cppSettings.sustainLevel = env->GetFloatField(kotlinEnvelopeSettings, sustainLevelFid);
    cppSettings.releaseMs = env->GetFloatField(kotlinEnvelopeSettings, releaseMsFid);

    env->DeleteLocalRef(envSettingsClass);
    return cppSettings;
}

// Helper function to convert Kotlin LFOSettings to theone::audio::LfoSettingsCpp
theone::audio::LfoSettingsCpp ConvertKotlinLfoSettings(JNIEnv* env, jobject kotlinLfoSettings) {
    theone::audio::LfoSettingsCpp cppSettings; // Default constructor
    if (kotlinLfoSettings == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinLfoSettings: kotlinLfoSettings is null");
        return cppSettings;
    }

    jclass lfoSettingsClass = env->GetObjectClass(kotlinLfoSettings);
    if (lfoSettingsClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinLfoSettings: GetObjectClass failed for LFOSettings");
        env->ExceptionClear();
        return cppSettings;
    }

    jfieldID isEnabledFid = env->GetFieldID(lfoSettingsClass, "isEnabled", "Z");
    jfieldID waveformFid = env->GetFieldID(lfoSettingsClass, "waveform", "Lcom/example/theone/model/ audioEngine/LfoWaveform;");
    jfieldID rateHzFid = env->GetFieldID(lfoSettingsClass, "rateHz", "F");
    jfieldID syncToTempoFid = env->GetFieldID(lfoSettingsClass, "syncToTempo", "Z");
    jfieldID tempoDivisionFid = env->GetFieldID(lfoSettingsClass, "tempoDivision", "Lcom/example/theone/model/ audioEngine/TimeDivision;");
    jfieldID depthFid = env->GetFieldID(lfoSettingsClass, "depth", "F");
    jfieldID primaryDestinationFid = env->GetFieldID(lfoSettingsClass, "primaryDestination", "Lcom/example/theone/model/ audioEngine/LfoDestination;");

    if (isEnabledFid == nullptr || waveformFid == nullptr || rateHzFid == nullptr || syncToTempoFid == nullptr ||
        tempoDivisionFid == nullptr || depthFid == nullptr || primaryDestinationFid == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinLfoSettings: Failed to get one or more field IDs for LFOSettings");
        env->DeleteLocalRef(lfoSettingsClass);
        env->ExceptionClear();
        return cppSettings;
    }

    cppSettings.isEnabled = env->GetBooleanField(kotlinLfoSettings, isEnabledFid);

    // Waveform (Enum)
    jobject kotlinWaveformEnum = env->GetObjectField(kotlinLfoSettings, waveformFid);
    if (kotlinWaveformEnum != nullptr) {
        int ordinal = getEnumOrdinal(env, kotlinWaveformEnum);
        // Assuming LfoWaveformCpp enum values match ordinals: SINE=0, TRIANGLE=1, etc.
        // This requires LfoWaveformCpp to be defined in LfoGenerator.h and its values known.
        // Example mapping:
        if (ordinal >= 0 && ordinal < static_cast<int>(theone::audio::LfoWaveformCpp::NUM_LFO_WAVEFORMS)) { // Boundary check
             cppSettings.waveform = static_cast<theone::audio::LfoWaveformCpp>(ordinal);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: Unknown LfoWaveform ordinal: %d", ordinal);
            // cppSettings.waveform will use its default
        }
        env->DeleteLocalRef(kotlinWaveformEnum);
    } else {
         __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: LFOSettings.waveform is null, using default");
    }


    cppSettings.rateHz = env->GetFloatField(kotlinLfoSettings, rateHzFid);
    cppSettings.syncToTempo = env->GetBooleanField(kotlinLfoSettings, syncToTempoFid);

    // TempoDivision (Enum)
    jobject kotlinTempoDivisionEnum = env->GetObjectField(kotlinLfoSettings, tempoDivisionFid);
    if (kotlinTempoDivisionEnum != nullptr) {
        int ordinal = getEnumOrdinal(env, kotlinTempoDivisionEnum);
        // Assuming TimeDivisionCpp enum values match ordinals.
        // This requires TimeDivisionCpp to be defined (likely in LfoGenerator.h or a common types header)
        // Example mapping:
         if (ordinal >= 0 && ordinal < static_cast<int>(theone::audio::TimeDivisionCpp::NUM_TIME_DIVISIONS)) { // Boundary check
            cppSettings.tempoDivision = static_cast<theone::audio::TimeDivisionCpp>(ordinal);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: Unknown TimeDivision ordinal: %d", ordinal);
        }
        env->DeleteLocalRef(kotlinTempoDivisionEnum);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: LFOSettings.tempoDivision is null, using default");
    }


    cppSettings.depth = env->GetFloatField(kotlinLfoSettings, depthFid);

    // PrimaryDestination (Enum)
    jobject kotlinPrimaryDestEnum = env->GetObjectField(kotlinLfoSettings, primaryDestinationFid);
    if (kotlinPrimaryDestEnum != nullptr) {
        int ordinal = getEnumOrdinal(env, kotlinPrimaryDestEnum);
        // Assuming LfoDestinationCpp enum values match ordinals.
        // This requires LfoDestinationCpp to be defined (likely in LfoGenerator.h or a common types header)
        // Example mapping:
        if (ordinal >= 0 && ordinal < static_cast<int>(theone::audio::LfoDestinationCpp::NUM_LFO_DESTINATIONS)) { // Boundary check
            cppSettings.primaryDestination = static_cast<theone::audio::LfoDestinationCpp>(ordinal);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: Unknown LfoDestination ordinal: %d", ordinal);
        }
        env->DeleteLocalRef(kotlinPrimaryDestEnum);
    } else {
         __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinLfoSettings: LFOSettings.primaryDestination is null, using default");
    }


    env->DeleteLocalRef(lfoSettingsClass);
    return cppSettings;
}


// Main JNI Function: native_updatePadSettings
extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1updatePadSettings(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jTrackId,
        jstring jPadId,
        jobject jPadSettings) { // PadSettings Kotlin object

    if (jTrackId == nullptr || jPadId == nullptr || jPadSettings == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Null argument provided (trackId, padId, or padSettings)");
        return;
    }

    std::string trackIdStr = JStringToString(env, jTrackId);
    std::string padIdStr = JStringToString(env, jPadId);
    std::string padKey = trackIdStr + "_" + padIdStr;

    // __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_updatePadSettings: Called for padKey: %s", padKey.c_str()); // Original Log

    theone::audio::PadSettingsCpp cppSettings; // Create a new C++ settings object to convert Kotlin object

    // Get PadSettings Kotlin class and field IDs
    jclass padSettingsClass = env->GetObjectClass(jPadSettings);
    if (padSettingsClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: GetObjectClass failed for PadSettings");
        env->ExceptionClear();
        return;
    }

    // --- Populate PadSettingsCpp from jPadSettings ---

    // id (String)
    jfieldID idFid = env->GetFieldID(padSettingsClass, "id", "Ljava/lang/String;");
    if (idFid != nullptr) {
        jstring jId = (jstring)env->GetObjectField(jPadSettings, idFid);
        cppSettings.id = JStringToString(env, jId);
        env->DeleteLocalRef(jId);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'id'");
        env->ExceptionClear();
    }


    // layers (MutableList<SampleLayer>)
    jfieldID layersFid = env->GetFieldID(padSettingsClass, "layers", "Ljava/util/List;"); // Kotlin MutableList is java.util.List
    if (layersFid != nullptr) {
        jobject kotlinLayersList = env->GetObjectField(jPadSettings, layersFid);
        if (kotlinLayersList != nullptr) {
            jclass listClass = env->FindClass("java/util/List");
            jmethodID listSizeMid = env->GetMethodID(listClass, "size", "()I");
            jmethodID listGetMid = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

            int listSize = env->CallIntMethod(kotlinLayersList, listSizeMid);
            for (int i = 0; i < listSize; ++i) {
                jobject kotlinLayerObj = env->CallObjectMethod(kotlinLayersList, listGetMid, i);
                if (kotlinLayerObj != nullptr) {
                    cppSettings.layers.push_back(ConvertKotlinSampleLayer(env, kotlinLayerObj));
                    env->DeleteLocalRef(kotlinLayerObj);
                } else {
                     __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: Null SampleLayer object at index %d", i);
                }
            }
            env->DeleteLocalRef(listClass);
            env->DeleteLocalRef(kotlinLayersList);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.layers list is null");
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'layers'");
        env->ExceptionClear();
    }


    // layerTriggerRule (Enum LayerTriggerRule)
    jfieldID layerTriggerRuleFid = env->GetFieldID(padSettingsClass, "layerTriggerRule", "Lcom/example/theone/model/ audioEngine/LayerTriggerRule;");
    if (layerTriggerRuleFid != nullptr) {
        jobject kotlinLayerTriggerRuleEnum = env->GetObjectField(jPadSettings, layerTriggerRuleFid);
        if (kotlinLayerTriggerRuleEnum != nullptr) {
            int ordinal = getEnumOrdinal(env, kotlinLayerTriggerRuleEnum);
            if (ordinal == 0) cppSettings.layerTriggerRule = theone::audio::LayerTriggerRuleCpp::VELOCITY;
            else if (ordinal == 1) cppSettings.layerTriggerRule = theone::audio::LayerTriggerRuleCpp::CYCLE;
            else if (ordinal == 2) cppSettings.layerTriggerRule = theone::audio::LayerTriggerRuleCpp::RANDOM;
            else __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: Unknown LayerTriggerRule ordinal: %d", ordinal);
            env->DeleteLocalRef(kotlinLayerTriggerRuleEnum);
        } else {
             __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.layerTriggerRule is null");
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'layerTriggerRule'");
        env->ExceptionClear();
    }


    // playbackMode (Enum PlaybackMode)
    jfieldID playbackModeFid = env->GetFieldID(padSettingsClass, "playbackMode", "Lcom/example/theone/model/ audioEngine/PlaybackMode;");
    if (playbackModeFid != nullptr) {
        jobject kotlinPlaybackModeEnum = env->GetObjectField(jPadSettings, playbackModeFid);
        if (kotlinPlaybackModeEnum != nullptr) {
            int ordinal = getEnumOrdinal(env, kotlinPlaybackModeEnum);
            if (ordinal == 0) cppSettings.playbackMode = theone::audio::PlaybackModeCpp::ONE_SHOT;
            else if (ordinal == 1) cppSettings.playbackMode = theone::audio::PlaybackModeCpp::LOOP;
            else if (ordinal == 2) cppSettings.playbackMode = theone::audio::PlaybackModeCpp::GATE;
            else __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: Unknown PlaybackMode ordinal: %d", ordinal);
            env->DeleteLocalRef(kotlinPlaybackModeEnum);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.playbackMode is null");
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'playbackMode'");
        env->ExceptionClear();
    }


    // Basic fields: tuningCoarse, tuningFine, volume, pan, muteGroup, polyphony
    jfieldID tuningCoarseFid = env->GetFieldID(padSettingsClass, "tuningCoarse", "I");
    jfieldID tuningFineFid = env->GetFieldID(padSettingsClass, "tuningFine", "I"); // Assuming C++ PadSettingsCpp.tuningFine is int
    jfieldID volumeFid = env->GetFieldID(padSettingsClass, "volume", "F");
    jfieldID panFid = env->GetFieldID(padSettingsClass, "pan", "F");
    jfieldID muteGroupFid = env->GetFieldID(padSettingsClass, "muteGroup", "I");
    jfieldID polyphonyFid = env->GetFieldID(padSettingsClass, "polyphony", "I");

    if (tuningCoarseFid) cppSettings.tuningCoarse = env->GetIntField(jPadSettings, tuningCoarseFid);
    if (tuningFineFid) cppSettings.tuningFine = env->GetIntField(jPadSettings, tuningFineFid);
    if (volumeFid) cppSettings.volume = env->GetFloatField(jPadSettings, volumeFid);
    if (panFid) cppSettings.pan = env->GetFloatField(jPadSettings, panFid);
    if (muteGroupFid) cppSettings.muteGroup = env->GetIntField(jPadSettings, muteGroupFid);
    if (polyphonyFid) cppSettings.polyphony = env->GetIntField(jPadSettings, polyphonyFid);


    // ampEnvelope (EnvelopeSettings)
    jfieldID ampEnvelopeFid = env->GetFieldID(padSettingsClass, "ampEnvelope", "Lcom/example/theone/model/ audioEngine/EnvelopeSettings;");
    if (ampEnvelopeFid != nullptr) {
        jobject kotlinAmpEnv = env->GetObjectField(jPadSettings, ampEnvelopeFid);
        if (kotlinAmpEnv != nullptr) {
            cppSettings.ampEnvelope = ConvertKotlinEnvelopeSettings(env, kotlinAmpEnv);
            env->DeleteLocalRef(kotlinAmpEnv);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.ampEnvelope is null");
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'ampEnvelope'");
        env->ExceptionClear();
    }


    // pitchEnvelope (EnvelopeSettings) - Non-nullable in Kotlin, always convert
    jfieldID pitchEnvelopeFid = env->GetFieldID(padSettingsClass, "pitchEnvelope", "Lcom/example/theone/model/ audioEngine/EnvelopeSettings;");
    if (pitchEnvelopeFid != nullptr) {
        jobject kotlinPitchEnv = env->GetObjectField(jPadSettings, pitchEnvelopeFid);
        if (kotlinPitchEnv != nullptr) {
            cppSettings.pitchEnvelope = ConvertKotlinEnvelopeSettings(env, kotlinPitchEnv);
            cppSettings.hasPitchEnvelope = true; // As per requirement
            env->DeleteLocalRef(kotlinPitchEnv);
        } else {
            // This case should ideally not happen if Kotlin side guarantees non-null.
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.pitchEnvelope is unexpectedly null. Setting hasPitchEnvelope=false.");
            cppSettings.hasPitchEnvelope = false;
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'pitchEnvelope'");
        cppSettings.hasPitchEnvelope = false; // Safety
        env->ExceptionClear();
    }

    // filterEnvelope (EnvelopeSettings) - Non-nullable in Kotlin, always convert
    jfieldID filterEnvelopeFid = env->GetFieldID(padSettingsClass, "filterEnvelope", "Lcom/example/theone/model/ audioEngine/EnvelopeSettings;");
    if (filterEnvelopeFid != nullptr) {
        jobject kotlinFilterEnv = env->GetObjectField(jPadSettings, filterEnvelopeFid);
        if (kotlinFilterEnv != nullptr) {
            cppSettings.filterEnvelope = ConvertKotlinEnvelopeSettings(env, kotlinFilterEnv);
            cppSettings.hasFilterEnvelope = true; // As per requirement
            env->DeleteLocalRef(kotlinFilterEnv);
        } else {
            // This case should ideally not happen if Kotlin side guarantees non-null.
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.filterEnvelope is unexpectedly null. Setting hasFilterEnvelope=false.");
            cppSettings.hasFilterEnvelope = false;
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'filterEnvelope'");
        cppSettings.hasFilterEnvelope = false; // Safety
        env->ExceptionClear();
    }


    // lfos (MutableList<LFOSettings>)
    jfieldID lfosFid = env->GetFieldID(padSettingsClass, "lfos", "Ljava/util/List;");
    if (lfosFid != nullptr) {
        jobject kotlinLfosList = env->GetObjectField(jPadSettings, lfosFid);
        if (kotlinLfosList != nullptr) {
            jclass listClass = env->FindClass("java/util/List"); // Re-find or use a global ref
            jmethodID listSizeMid = env->GetMethodID(listClass, "size", "()I");
            jmethodID listGetMid = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

            int lfosListSize = env->CallIntMethod(kotlinLfosList, listSizeMid);
            for (int i = 0; i < lfosListSize; ++i) {
                jobject kotlinLfoObj = env->CallObjectMethod(kotlinLfosList, listGetMid, i);
                if (kotlinLfoObj != nullptr) {
                    cppSettings.lfos.push_back(ConvertKotlinLfoSettings(env, kotlinLfoObj));
                    env->DeleteLocalRef(kotlinLfoObj);
                } else {
                     __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: Null LFOSettings object at index %d", i);
                }
            }
            env->DeleteLocalRef(listClass); // Delete local ref to List class
            env->DeleteLocalRef(kotlinLfosList);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_updatePadSettings: PadSettings.lfos list is null");
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: Failed to get field ID for 'lfos'");
        env->ExceptionClear();
    }

    // currentCycleLayerIndex should be reset/initialized.
    cppSettings.currentCycleLayerIndex = 0;


    // Clean up PadSettings class reference
    env->DeleteLocalRef(padSettingsClass);

    // --- Update Map --- // This part is now handled by AudioEngine class
    // {
    //     std::lock_guard<std::mutex> lock(gPadSettingsMutex);
    //     gPadSettingsMap[padKey] = std::move(cppSettings); // Move constructed CppSettings into map
    //     __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_updatePadSettings: Successfully updated PadSettings for key: %s. Map size: %zu", padKey.c_str(), gPadSettingsMap.size());
    // }

    if (audioEngineInstance) {
        audioEngineInstance->updatePadSettings(padKey, cppSettings);
         __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_updatePadSettings: Delegated to AudioEngine for key: %s", padKey.c_str());
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_updatePadSettings: audioEngineInstance is null for key: %s", padKey.c_str());
    }

    // JNI resources like jTrackId, jPadId, jPadSettings are managed by JNI (caller releases them or they are arguments)
    // Local refs created inside this function (GetObjectClass, GetObjectField, FindClass etc.) have been deleted.
}

// --- JNI Functions for Sequencer ---

// Forward declaration
EventCpp ConvertKotlinEvent(JNIEnv* env, jobject kotlinEvent);
TrackCpp ConvertKotlinTrack(JNIEnv* env, jobject kotlinTrackObject);


EventCpp ConvertKotlinEvent(JNIEnv* env, jobject kotlinEvent) {
    EventCpp cppEvent;
    if (kotlinEvent == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEvent: kotlinEvent is null");
        return cppEvent; // Return default-constructed event
    }

    jclass eventClass = env->GetObjectClass(kotlinEvent);
    if (!eventClass) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEvent: GetObjectClass failed for Event");
        env->ExceptionClear();
        return cppEvent;
    }

    // Get common fields
    jfieldID idFid = env->GetFieldID(eventClass, "id", "Ljava/lang/String;");
    jfieldID trackIdFid = env->GetFieldID(eventClass, "trackId", "Ljava/lang/String;");
    jfieldID startTimeTicksFid = env->GetFieldID(eventClass, "startTimeTicks", "J");
    jfieldID typeFid = env->GetFieldID(eventClass, "type", "Lcom/example/theone/model/EventType;");

    if (!idFid || !trackIdFid || !startTimeTicksFid || !typeFid) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEvent: Failed to get common field IDs for Event");
        env->DeleteLocalRef(eventClass);
        env->ExceptionClear();
        return cppEvent;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinEvent, idFid);
    cppEvent.id = JStringToString(env, jId);
    env->DeleteLocalRef(jId);

    jstring jTrackId = (jstring)env->GetObjectField(kotlinEvent, trackIdFid);
    cppEvent.trackId = JStringToString(env, jTrackId); // This is event's own trackId, usually same as parent Track's ID.
    env->DeleteLocalRef(jTrackId);

    cppEvent.startTimeTicks = env->GetLongField(kotlinEvent, startTimeTicksFid);

    // Determine EventType
    jobject eventTypeObj = env->GetObjectField(kotlinEvent, typeFid);
    if (eventTypeObj) {
        jclass eventTypeClass = env->GetObjectClass(eventTypeObj);
        // Assuming EventType.PadTrigger is "com/example/theone/model/EventType$PadTrigger"
        // This needs to be exact. A safer way might be to have an int/enum field in EventType itself.
        jclass padTriggerEventTypeClass = env->FindClass("com/example/theone/model/EventType$PadTrigger");

        if (padTriggerEventTypeClass && env->IsInstanceOf(eventTypeObj, padTriggerEventTypeClass)) {
            cppEvent.type = EventTriggerTypeCpp::PAD_TRIGGER;
            jfieldID padIdFid = env->GetFieldID(padTriggerEventTypeClass, "padId", "Ljava/lang/String;");
            jfieldID velocityFid = env->GetFieldID(padTriggerEventTypeClass, "velocity", "I");
            jfieldID durationTicksFid = env->GetFieldID(padTriggerEventTypeClass, "durationTicks", "J");

            if (padIdFid && velocityFid && durationTicksFid) {
                jstring jPadId = (jstring)env->GetObjectField(eventTypeObj, padIdFid);
                cppEvent.padTrigger.padId = JStringToString(env, jPadId);
                env->DeleteLocalRef(jPadId);
                cppEvent.padTrigger.velocity = env->GetIntField(eventTypeObj, velocityFid);
                cppEvent.padTrigger.durationTicks = env->GetLongField(eventTypeObj, durationTicksFid);
            } else {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEvent: Failed to get field IDs for EventType.PadTrigger");
                env->ExceptionClear();
            }
        } else {
            // Handle other event types or log unknown
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinEvent: Unknown or unhandled EventType instance");
        }
        if (padTriggerEventTypeClass) env->DeleteLocalRef(padTriggerEventTypeClass);
        if (eventTypeClass) env->DeleteLocalRef(eventTypeClass);
        env->DeleteLocalRef(eventTypeObj);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinEvent: Event 'type' field is null");
    }

    env->DeleteLocalRef(eventClass);
    return cppEvent;
}

TrackCpp ConvertKotlinTrack(JNIEnv* env, jobject kotlinTrackObject) {
    TrackCpp cppTrack;
    if (kotlinTrackObject == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinTrack: kotlinTrackObject is null");
        return cppTrack;
    }

    jclass trackClass = env->GetObjectClass(kotlinTrackObject);
    if (!trackClass) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinTrack: GetObjectClass failed for Track");
        env->ExceptionClear();
        return cppTrack;
    }

    jfieldID idFid = env->GetFieldID(trackClass, "id", "Ljava/lang/String;");
    jfieldID eventsListFid = env->GetFieldID(trackClass, "events", "Ljava/util/List;");

    if (!idFid || !eventsListFid) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinTrack: Failed to get field IDs for Track (id or events)");
        env->DeleteLocalRef(trackClass);
        env->ExceptionClear();
        return cppTrack;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinTrackObject, idFid);
    cppTrack.id = JStringToString(env, jId);
    env->DeleteLocalRef(jId);

    jobject eventsListObj = env->GetObjectField(kotlinTrackObject, eventsListFid);
    if (eventsListObj) {
        jclass listClass = env->FindClass("java/util/List");
        jmethodID listSizeMid = env->GetMethodID(listClass, "size", "()I");
        jmethodID listGetMid = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

        if (listClass && listSizeMid && listGetMid) {
            int listSize = env->CallIntMethod(eventsListObj, listSizeMid);
            for (int i = 0; i < listSize; ++i) {
                jobject kotlinEventObj = env->CallObjectMethod(eventsListObj, listGetMid, i);
                if (kotlinEventObj) {
                    cppTrack.events.push_back(ConvertKotlinEvent(env, kotlinEventObj));
                    env->DeleteLocalRef(kotlinEventObj);
                }
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "ConvertKotlinTrack: Failed to get List methods.");
            env->ExceptionClear();
        }
        if (listClass) env->DeleteLocalRef(listClass);
        env->DeleteLocalRef(eventsListObj);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "ConvertKotlinTrack: Track '%s' has null events list", cppTrack.id.c_str());
    }

    env->DeleteLocalRef(trackClass);
    return cppTrack;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1loadSequenceData(
        JNIEnv *env,
        jobject /* thiz */,
        jobject jSequence_kotlin) {
    if (!audioEngineInstance) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "native_loadSequenceData: audioEngineInstance is null!");
        return;
    }
    if (jSequence_kotlin == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "native_loadSequenceData: Kotlin Sequence object is null. Clearing current sequence in AudioEngine.");
        theone::audio::SequenceCpp emptySequence;
        // Set a distinct ID or flag if AudioEngine needs to differentiate from a truly empty loaded sequence.
        emptySequence.id = "__EMPTY_SEQUENCE_FOR_CLEARING__";
        audioEngineInstance->loadSequenceData(emptySequence);
        return;
    }

    __android_log_print(ANDROID_LOG_INFO, NATIVE_LIB_APP_NAME, "native_loadSequenceData: Starting conversion of Kotlin Sequence.");
    theone::audio::SequenceCpp sequenceCpp = ConvertKotlinSequence(env, jSequence_kotlin);

    // It's good practice to check if the conversion was successful, e.g. by checking if id is populated
    if (sequenceCpp.id.empty() && jSequence_kotlin != nullptr) { // Check id only if original object was not null
         __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "native_loadSequenceData: Conversion from Kotlin Sequence might have failed (resulting ID is empty).");
         // Depending on requirements, may not load this potentially corrupt/empty sequence.
         // For now, we proceed to load whatever was converted.
    }

    audioEngineInstance->loadSequenceData(sequenceCpp);
    __android_log_print(ANDROID_LOG_INFO, NATIVE_LIB_APP_NAME, "native_loadSequenceData: Kotlin Sequence (ID: %s) converted and loaded into AudioEngine.", sequenceCpp.id.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playSequence(
        JNIEnv *env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gSequencerMutex);
    if (gCurrentSequence) {
        gCurrentSequence->isPlaying = true;
        gCurrentSequence->currentPlayheadTicks = 0;
        gTimeAccumulatedForTick = 0.0; // Reset accumulator for immediate start
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_playSequence: Playback started for sequence ID '%s'. Playhead reset.", gCurrentSequence->id.c_str());
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_playSequence: No sequence loaded.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1stopSequence(
        JNIEnv *env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gSequencerMutex);
    if (gCurrentSequence) {
        gCurrentSequence->isPlaying = false;
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_stopSequence: Playback stopped for sequence ID '%s'.", gCurrentSequence->id.c_str());
        // Playhead position is maintained, reset on play.
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_stopSequence: No sequence loaded.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setSequencerBpm(
        JNIEnv *env,
        jobject /* thiz */,
        jfloat bpm) {
    std::lock_guard<std::mutex> lock(gSequencerMutex);
    if (gCurrentSequence) {
        if (bpm > 0.0f) {
            gCurrentSequence->bpm = bpm;
            RecalculateTickDuration();
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_setSequencerBpm: BPM for sequence ID '%s' updated to %f. New tick duration: %f ms",
                                gCurrentSequence->id.c_str(), gCurrentSequence->bpm, gCurrentTickDurationMs);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_setSequencerBpm: Invalid BPM value %f provided.", bpm);
        }
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_setSequencerBpm: No sequence loaded. Cannot set BPM.");
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getSequencerPlayheadPosition(
        JNIEnv *env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gSequencerMutex);
    if (gCurrentSequence) {
        return static_cast<jlong>(gCurrentSequence->currentPlayheadTicks);
    }
    return 0L;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setPadPan(
        JNIEnv *env,
        jobject /* this */,
        jstring jTrackId,
        jstring jPadId,
        jfloat pan) {
    std::string trackIdStr = JStringToString(env, jTrackId);
    std::string padIdStr = JStringToString(env, jPadId);

    if (trackIdStr.empty() || padIdStr.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_setPadPan JNI: trackId or padId is empty");
        return;
    }
    std::string padKey = trackIdStr + "_" + padIdStr;

    if (audioEngineInstance) {
        audioEngineInstance->setPadPan(padKey, static_cast<float>(pan));
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_setPadPan JNI: audioEngineInstance is null for padKey '%s'", padKey.c_str());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setPadVolume(
        JNIEnv *env,
        jobject /* this */,
        jstring jTrackId,
        jstring jPadId,
        jfloat volume) {
    std::string trackIdStr = JStringToString(env, jTrackId);
    std::string padIdStr = JStringToString(env, jPadId);

    if (trackIdStr.empty() || padIdStr.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_setPadVolume JNI: trackId or padId is empty");
        return;
    }
    std::string padKey = trackIdStr + "_" + padIdStr;

    if (audioEngineInstance) {
        audioEngineInstance->setPadVolume(padKey, static_cast<float>(volume));
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "native_setPadVolume JNI: audioEngineInstance is null for padKey '%s'", padKey.c_str());
    }
}
