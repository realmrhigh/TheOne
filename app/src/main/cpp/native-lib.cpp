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
#include "SequenceCpp.h" // Include the new SequenceCpp header
#include "AudioEngine.h" // Ensure AudioEngine is included

// --- dr_wav file descriptor callbacks ---
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

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

// --- GLOBAL STATE: Move all global variables to the top for visibility ---
// Remove all global audio state and MyAudioCallback
// static theone::audio::MetronomeState gMetronomeState;
// static std::mutex gMetronomeStateMutex;
// static std::map<std::string, theone::audio::PadSettingsCpp> gPadSettingsMap;
// static std::mutex gPadSettingsMutex;
// static std::map<std::string, theone::audio::LoadedSample> gSampleMap;
// static std::vector<theone::audio::PlayingSound> gActiveSounds;
// static std::mutex gActiveSoundsMutex;
// static std::mutex gSequencerMutex;
// static std::unique_ptr<theone::audio::SequenceCpp> gCurrentSequence;
// static double gCurrentTickDurationMs = 0.0;
// static int gAudioStreamSampleRate = 0;
// static double gTimeAccumulatedForTick = 0.0;

// Recording State Variables
static oboe::ManagedStream mInputStream; // Oboe Input Stream
static std::atomic<bool> mIsRecording {false};
static std::atomic<float> mPeakRecordingLevel {0.0f};
static int mRecordingFileDescriptor = -1;    // FD for the output WAV file
static drwav mWavWriter;                     // dr_wav instance for writing
static bool mWavWriterInitialized = false;   // Flag to check if mWavWriter is initialized
static std::string mCurrentRecordingFilePath = ""; // Store the path for metadata later
static std::mutex gRecordingStateMutex; // Mutex to protect recording state, file descriptor, and drwav writer access

// Add with other globals:
static std::mt19937 gRandomEngine;

// Ensure these are declared before any use:
static size_t drwav_read_proc_fd(void* pUserData, void* pBufferOut, size_t bytesToRead);
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite);
static drwav_bool32 drwav_seek_proc_fd(void* pUserData, int offset, drwav_seek_origin origin);

// Helper to convert jstring to std::string
static std::string JStringToString(JNIEnv* env, jstring jStr) {
    if (!jStr) return {};
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string result(chars ? chars : "");
    env->ReleaseStringUTFChars(jStr, chars);
    return result;
}

// Basic Oboe audio callback
// class MyAudioCallback : public oboe::AudioStreamCallback {
    // oboe::DataCallbackResult onAudioReady(
    //         oboe::AudioStream *oboeStream,
    //         void *audioData,
    //         int32_t numFrames) override {

    //     float *outputBuffer = static_cast<float*>(audioData);
    //     memset(outputBuffer, 0, sizeof(float) * numFrames * oboeStream->getChannelCount());

    //     // Metronome Beat Generation
    //     std::vector<theone::audio::PlayingSound> newMetronomeSounds;

    //     { // Scope for gMetronomeStateMutex
    //         std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
    //         if (gMetronomeState.enabled.load() && gMetronomeState.framesPerBeat > 0) {
    //             if (gMetronomeState.audioStreamSampleRate == 0) {
    //                 // __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Metronome: audioStreamSampleRate is 0!");
    //             }

    //             for (int i = 0; i < numFrames; ++i) {
    //                 if (gMetronomeState.samplesUntilNextBeat == 0) {
    //                     gMetronomeState.currentBeatInBar++;
    //                     if (gMetronomeState.currentBeatInBar > gMetronomeState.timeSignatureNum.load() || gMetronomeState.timeSignatureNum.load() == 0) {
    //                         gMetronomeState.currentBeatInBar = 1;
    //                     }

    //                     const theone::audio::LoadedSample* soundToPlay = nullptr;
    //                     bool isPrimary = (gMetronomeState.currentBeatInBar == 1);

    //                     if (isPrimary && gMetronomeState.primaryBeatSample) {
    //                         soundToPlay = gMetronomeState.primaryBeatSample;
    //                     } else if (!isPrimary && gMetronomeState.secondaryBeatSample) {
    //                         soundToPlay = gMetronomeState.secondaryBeatSample;
    //                     } else if (gMetronomeState.primaryBeatSample) { // Fallback to primary if secondary is null but it's not beat 1
    //                         soundToPlay = gMetronomeState.primaryBeatSample;
    //                     }


    //                     if (soundToPlay) {
    //                         std::string instanceId = "m_tick_" + std::to_string(gMetronomeState.currentBeatInBar);
    //                         newMetronomeSounds.emplace_back(soundToPlay, instanceId, gMetronomeState.volume.load(), 0.0f /*center pan*/);
    //                     }
    //                     gMetronomeState.samplesUntilNextBeat = gMetronomeState.framesPerBeat;
    //                 }
    //                 gMetronomeState.samplesUntilNextBeat--;
    //             }
    //         }
    //     } // gMetronomeStateMutex released


    //     // --- C++ Sequencer Processing ---
    //     // Check if sequencer is initialized and has a valid tick duration
    //     if (gAudioStreamSampleRate > 0 && gCurrentTickDurationMs > 0.0) {
    //         std::lock_guard<std::mutex> sequencerLock(gSequencerMutex); // Lock for gCurrentSequence access

    //         if (gCurrentSequence && gCurrentSequence->isPlaying) {
    //             double frameDurationMs = 1000.0 / static_cast<double>(gAudioStreamSampleRate);
    //             gTimeAccumulatedForTick += static_cast<double>(numFrames) * frameDurationMs;

    //             // Process all ticks that should have occurred in this audio buffer
    //             while (gTimeAccumulatedForTick >= gCurrentTickDurationMs && gCurrentTickDurationMs > 0.0) {
    //                 gTimeAccumulatedForTick -= gCurrentTickDurationMs;
    //                 gCurrentSequence->currentPlayheadTicks++;

    //                 // --- Handle Sequence Looping ---
    //                 // Assuming PPQN is based on quarter notes. Denominator adjustment needed for other bases.
    //                 long beatsPerBar = gCurrentSequence->timeSignatureNumerator;
    //                 // If timeSignatureDenominator is 8, a "beat" (quarter note) is half as long in ticks.
    //                 // This basic calculation assumes denominator is 4. A more robust solution would use it.
    //                 long ticksPerBar = beatsPerBar * gCurrentSequence->ppqn;
    //                 long totalTicksInSequence = gCurrentSequence->barLength * ticksPerBar;

    //                 if (totalTicksInSequence > 0 && gCurrentSequence->currentPlayheadTicks >= totalTicksInSequence) {
    //                     gCurrentSequence->currentPlayheadTicks = 0; // Loop back
    //                     __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Sequencer looped. Playhead reset. Total ticks: %ld", totalTicksInSequence);
    //                 }

    //                 // --- Trigger Events ---
    //                 for (auto const& [trackId, track] : gCurrentSequence->tracks) {
    //                     for (const auto& event : track.events) {
    //                         if (event.startTimeTicks == gCurrentSequence->currentPlayheadTicks) {
    //                             if (event.type == theone::audio::EventTriggerTypeCpp::PAD_TRIGGER) {
    //                                 std::string padKey = event.trackId + "_" + event.padTrigger.padId;
    //                                 std::shared_ptr<theone::audio::PadSettingsCpp> padSettingsPtr;

    //                                 { // Scope for gPadSettingsMutex
    //                                     std::lock_guard<std::mutex> padSettingsLocker(gPadSettingsMutex);
    //                                     auto it = gPadSettingsMap.find(padKey);
    //                                     if (it != gPadSettingsMap.end()) {
    //                                         // Create a shared_ptr to the found settings.
    //                                         // Copying (it->second) ensures thread safety if PadSettingsCpp is complex,
    //                                         // or if PlayingSound needs its own lifecycle for it.
    //                                         padSettingsPtr = std::make_shared<theone::audio::PadSettingsCpp>(it->second);
    //                                     }
    //                                 }

    //                                 if (padSettingsPtr) {
    //                                     const theone::audio::SampleLayerCpp* selectedLayer = nullptr;
    //                                     // Simplified layer selection: first enabled layer
    //                                     for (const auto& layer : padSettingsPtr->layers) {
    //                                         if (layer.enabled && !layer.sampleId.empty()) {
    //                                             selectedLayer = &layer;
    //                                             break;
    //                                         }
    //                                     }

    //                                     if (selectedLayer) {
    //                                         auto sampleIt = gSampleMap.find(selectedLayer->sampleId);
    //                                         if (sampleIt != gSampleMap.end()) {
    //                                             const theone::audio::LoadedSample* loadedSample = &(sampleIt->second);
    //                                             if (loadedSample && !loadedSample->audioData.empty()) {
    //                                                 std::string noteInstanceId = "seq_" + event.id + "_" + std::to_string(gCurrentSequence->currentPlayheadTicks) + "_" + std::to_string(std::rand());
    //                                                 float velocityNormalized = static_cast<float>(event.padTrigger.velocity) / 127.0f;

    //                                                 // Calculate volume and pan from pad, layer, and event velocity
    //                                                 float baseVolume = padSettingsPtr->volume;
    //                                                 float basePan = padSettingsPtr->pan;
    //                                                 float layerVolumeOffsetGain = 1.0f;
    //                                                 if (selectedLayer->volumeOffsetDb > -90.0f) // Avoid large negative for powf
    //                                                     layerVolumeOffsetGain = powf(10.0f, selectedLayer->volumeOffsetDb / 20.0f);
    //                                                 else
    //                                                     layerVolumeOffsetGain = 0.0f; // Effective silence
    //                                                 // Apply velocity to the gain derived from base and layer offset
    //                                                 float finalVolume = baseVolume * layerVolumeOffsetGain * velocityNormalized;
    //                                                 float finalPan = basePan + selectedLayer->panOffset;
    //                                                 finalPan = std::max(-1.0f, std::min(1.0f, finalPan)); // Clamp pan

    //                                                 theone::audio::PlayingSound soundToPlay(loadedSample, noteInstanceId, finalVolume, finalPan);
    //                                                 soundToPlay.padSettings = padSettingsPtr; // Assign the settings

    //                                                 float sr = static_cast<float>(gAudioStreamSampleRate); // Already checked gAudioStreamSampleRate > 0
    //                                                                                                         // but good to have a local copy for safety if sr calculation is complex

    //                                                 // Configure audio modules based on padSettings
    //                                                 if (soundToPlay.padSettings) {
    //                                                     // Amp Envelope
    //                                                     soundToPlay.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
    //                                                     soundToPlay.ampEnvelopeGen->configure(soundToPlay.padSettings->ampEnvelope, sr, velocityNormalized);
    //                                                     soundToPlay.ampEnvelopeGen->triggerOn(velocityNormalized);

    //                                                     // Pitch Envelope
    //                                                     if (soundToPlay.padSettings->hasPitchEnvelope) {
    //                                                         soundToPlay.pitchEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
    //                                                         soundToPlay.pitchEnvelopeGen->configure(soundToPlay.padSettings->pitchEnvelope, sr, velocityNormalized);
    //                                                         soundToPlay.pitchEnvelopeGen->triggerOn(velocityNormalized);
    //                                                     }

    //                                                     // Filter Envelope
    //                                                     if (soundToPlay.padSettings->hasFilterEnvelope) {
    //                                                         soundToPlay.filterEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
    //                                                         soundToPlay.filterEnvelopeGen->configure(soundToPlay.padSettings->filterEnvelope, sr, velocityNormalized);
    //                                                         soundToPlay.filterEnvelopeGen->triggerOn(velocityNormalized);
    //                                                     }

    //                                                     // LFOs
    //                                                     soundToPlay.lfoGens.clear();
    //                                                     float currentTempo = gCurrentSequence->bpm; // Use sequence's BPM
    //                                                     if (currentTempo <= 0) currentTempo = 120.0f; // Safety

    //                                                     for (const auto& lfoConfig : soundToPlay.padSettings->lfos) {
    //                                                         if (lfoConfig.isEnabled) {
    //                                                             auto lfo = std::make_unique<theone::audio::LfoGenerator>();
    //                                                             lfo->configure(lfoConfig, sr, currentTempo);
    //                                                             lfo->retrigger();
    //                                                             soundToPlay.lfoGens.push_back(std::move(lfo));
    //                                                         }
    //                                                     }
    //                                                 }

    //                                                 // Add to active sounds
    //                                                 {
    //                                                     std::lock_guard<std::mutex> activeSoundsLocker(gActiveSoundsMutex);
    //                                                     gActiveSounds.push_back(std::move(soundToPlay));
    //                                                 }
    //                                                  __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Sequencer triggered sound for event %s (pad %s), sample %s, vel %d at tick %ld",
    //                                                                      event.id.c_str(), padKey.c_str(), selectedLayer->sampleId.c_str(), event.padTrigger.velocity, gCurrentSequence->currentPlayheadTicks);
    //                                             }
    //                                         } else {
    //                                                  __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: Sample '%s' for layer in pad '%s' not found in gSampleMap.",
    //                                                                      selectedLayer->sampleId.c_str(), padKey.c_str());
    //                                         }
    //                                     } else {
    //                                          __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: No suitable (enabled with sampleId) layer found for pad '%s'.", padKey.c_str());
    //                                     }
    //                                 } else {
    //                                      __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sequencer: PadSettings not found for key '%s'.", padKey.c_str());
    //                                 }
    //                             } // end if PAD_TRIGGER
    //                         } // end if event.startTimeTicks == currentPlayheadTicks
    //                     } // end for events
    //                 } // end for tracks
    //                 // TODO: Report playhead position back to Kotlin if needed (e.g., via a JNI callback)
    //             } // end while ticks to process
    //         } // end if gCurrentSequence && isPlaying
    //     } // end if gAudioStreamSampleRate > 0 && gCurrentTickDurationMs > 0
    //     // --- End C++ Sequencer Processing ---

    //     // Mixing and main audio processing
    //     std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);

    //     if (!newMetronomeSounds.empty()) {
    //         gActiveSounds.insert(gActiveSounds.end(),
    //                            std::make_move_iterator(newMetronomeSounds.begin()),
    //                            std::make_move_iterator(newMetronomeSounds.end()));
    //         newMetronomeSounds.clear(); // Clear the source vector as its elements are now moved.
    //     }

    //     for (auto soundIt = gActiveSounds.begin(); soundIt != gActiveSounds.end(); /* no increment here */) {
    //         if (!soundIt->isActive.load()) {
    //             ++soundIt;
    //             continue;
    //         }
    //         theone::audio::PlayingSound &sound = *soundIt;
    //         const theone::audio::LoadedSample* loadedSample = sound.loadedSamplePtr;

    //         if (!loadedSample || loadedSample->audioData.empty()) {
    //             sound.isActive.store(false);
    //             ++soundIt;
    //             continue;
    //         }

    //         int channels = oboeStream->getChannelCount();
    //         int sampleChannels = loadedSample->format.channels;

    //         // --- Envelope and LFO Processing (per sound, before per-frame loop) ---
    //         float ampEnvValue = 1.0f;
    //         float pitchEnvValue = 0.0f; // For pitch modulation in semitones or other units
    //         float filterEnvValue = 1.0f; // For filter modulation (e.g. cutoff multiplier)

    //         if (sound.padSettings) { // Check if padSettings are available
    //             if (sound.ampEnvelopeGen) {
    //                 ampEnvValue = sound.ampEnvelopeGen->process();
    //                 if (!sound.ampEnvelopeGen->isActive() && ampEnvValue < 0.001f) {
    //                     sound.isActive.store(false);
    //                 }
    //             }
    //             if (sound.pitchEnvelopeGen && sound.padSettings->hasPitchEnvelope) {
    //                 pitchEnvValue = sound.pitchEnvelopeGen->process();
    //                 // Actual application of pitchEnvValue would modify playback rate or resampling
    //             }
    //             if (sound.filterEnvelopeGen && sound.padSettings->hasFilterEnvelope) {
    //                 filterEnvValue = sound.filterEnvelopeGen->process();
    //                 // Actual application of filterEnvValue would modify filter parameters
    //             }
    //         } else { // Fallback for sounds without padSettings (e.g., metronome ticks)
    //              if (sound.ampEnvelopeGen) { // Metronome might have a simple amp envelope
    //                 ampEnvValue = sound.ampEnvelopeGen->process();
    //                 if (!sound.ampEnvelopeGen->isActive() && ampEnvValue < 0.001f) {
    //                     sound.isActive.store(false);
    //                 }
    //             }
    //         }

    //         float lfoVolumeMod = 1.0f; // Multiplicative
    //         float lfoPanMod = 0.0f;    // Additive
    //         float lfoPitchMod = 0.0f;  // Additive (e.g. semitones)
    //         float lfoFilterMod = 0.0f; // Additive or Multiplicative, depending on filter design

    //         if (sound.padSettings && !sound.lfoGens.empty()) {
    //             for (size_t i = 0; i < sound.lfoGens.size(); ++i) {
    //                 auto& lfoGen = sound.lfoGens[i];
    //                 if (lfoGen && i < sound.padSettings->lfos.size()) {
    //                     float lfoValue = lfoGen->process();
    //                     const auto& lfoConfig = sound.padSettings->lfos[i];

    //                     switch (lfoConfig.primaryDestination) {
    //                         case theone::audio::LfoDestinationCpp::VOLUME:
    //                             // Assuming LFO outputs -1 to 1, map to e.g. 0.5 to 1.5 for volume modulation
    //                             lfoVolumeMod *= (1.0f + lfoValue * lfoConfig.depth * 0.5f);
    //                             break;
    //                         case theone::audio::LfoDestinationCpp::PAN:
    //                             lfoPanMod += lfoValue * lfoConfig.depth; // Depth could be max pan swing
    //                             break;
    //                         case theone::audio::LfoDestinationCpp::PITCH:
    //                             lfoPitchMod += lfoValue * lfoConfig.depth; // Depth in semitones
    //                             break;
    //                         case theone::audio::LfoDestinationCpp::FILTER_CUTOFF:
    //                             lfoFilterMod += lfoValue * lfoConfig.depth; // Depth for filter cutoff
    //                             break;
    //                         case theone::audio::LfoDestinationCpp::NONE:
    //                         default:
    //                             break;
    //                     }
    //                 }
    //             }
    //         }
    //         // Clamp LFO modulations if necessary
    //         lfoPanMod = std::max(-1.0f, std::min(1.0f, lfoPanMod));
    //         lfoVolumeMod = std::max(0.0f, lfoVolumeMod); // Ensure volume doesn't go negative

    //         // Apply pitch modulation (conceptual - actual implementation is complex)
    //         // float effectivePitchMod = pitchEnvValue + lfoPitchMod;
    //         // Resampling or playback rate adjustment would happen based on effectivePitchMod

    //         // Apply filter modulation (conceptual - actual implementation is complex)
    //         // float effectiveFilterCutoffMod = filterEnvValue * (or +) lfoFilterMod;
    //         // Filter parameters would be updated here

    //         float currentPan = sound.initialPan + lfoPanMod;
    //         currentPan = std::max(-1.0f, std::min(1.0f, currentPan)); // Clamp final pan
    //         float panRad = (currentPan * 0.5f + 0.5f) * (static_cast<float>(M_PI) / 2.0f);

    //         float overallGain = sound.initialVolume * ampEnvValue * lfoVolumeMod; // Apply LFO volume mod
    //         float finalGainL = overallGain * cosf(panRad);
    //         float finalGainR = overallGain * sinf(panRad);
    //         // --- End Envelope and LFO Processing ---

    //         for (int i = 0; i < numFrames; ++i) {
    //             if (!sound.isActive.load()) {
    //                 break;
    //             }

    //             // === START NEW SLICING/LOOPING LOGIC ===
    //             if (sound.useSlicing) {
    //                 if (sound.currentFrame >= sound.endFrame) {
    //                     if (sound.isLooping && sound.loopStartFrame < sound.loopEndFrame && sound.loopEndFrame <= sound.endFrame && sound.loopStartFrame < sound.endFrame) {
    //                         sound.currentFrame = sound.loopStartFrame;
    //                     } else {
    //                         sound.isActive.store(false);
    //                     }
    //                 }
    //             } else { // Original logic for non-sliced sounds
    //                 if (sound.currentFrame >= loadedSample->frameCount) {
    //                     sound.isActive.store(false);
    //                 }
    //             }

    //             if (!sound.isActive.load()) {
    //                  break;
    //             }
    //             if (sound.currentFrame >= loadedSample->frameCount) {
    //                  sound.isActive.store(false);
    //                  break;
    //             }
    //             // === END NEW SLICING/LOOPING LOGIC ===

    //             float leftSampleValue = 0.0f;
    //             float rightSampleValue = 0.0f;

    //             if (sampleChannels == 1) { // Mono sample
    //                 float sampleValue = loadedSample->audioData[sound.currentFrame];
    //                 leftSampleValue = sampleValue * finalGainL; // Apply final calculated gain
    //                 rightSampleValue = sampleValue * finalGainR;
    //             } else { // Stereo sample
    //                 float L = loadedSample->audioData[sound.currentFrame * sampleChannels];
    //                 float R = loadedSample->audioData[sound.currentFrame * sampleChannels + 1];
    //                 leftSampleValue = L * finalGainL; // Apply final calculated gain
    //                 rightSampleValue = R * finalGainR;
    //             }

    //             if (channels == 2) { // Stereo output
    //                 outputBuffer[i * 2]     += leftSampleValue;
    //                 outputBuffer[i * 2 + 1] += rightSampleValue;
    //             } else if (channels == 1) { // Mono output
    //                 outputBuffer[i] += (leftSampleValue + rightSampleValue) * 0.5f;
    //             }
    //             sound.currentFrame++;
    //         }
    //         ++soundIt;
    //     }

    //     gActiveSounds.erase(
    //             std::remove_if(gActiveSounds.begin(), gActiveSounds.end(),
    //                            [](const theone::audio::PlayingSound& s) {
    //                                return !s.isActive.load();
    //                            }),
    //             gActiveSounds.end());

    //     return oboe::DataCallbackResult::Continue;
    // }

    // void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
    //     __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorBeforeClose: %s", oboe::convertToText(error));
    // }

    // void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
    //     __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorAfterClose: %s", oboe::convertToText(error));
    // }
// };

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

static std::unique_ptr<theone::audio::AudioEngine> audioEngineInstance;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1startAudioRecording(
        JNIEnv *env,
        jobject /* thiz */,
        jint jFd,
        jstring jStoragePathForMetadata,
        jint jSampleRate,
        jint jChannels,
        jlong offset) {

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
    // We need to dup the fd because dr_wav_init_write_sequential_pcm_frames will call lseek, which will modify the file offset of the fd.
    // If we don't dup the fd, then the original fd's offset will be modified, which could cause problems for other code that uses the original fd.
    // For simplicity, and if Kotlin guarantees closure, we can use the original fd.
    // Let's assume Kotlin passes a valid, readable FD that it will close.
    // If lseek is needed for the offset:
    if (offset > 0) {
        if (lseek(jFd, static_cast<off_t>(offset), SEEK_SET) == -1) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to lseek to offset %lld for FD %d: %s", (long long)offset, jFd, strerror(errno));
            return JNI_FALSE;
        }
    }
    // Now jFd is positioned at the correct offset.

    if (!drwav_init_write_sequential_pcm_frames(&mWavWriter, &wavFormat, 0, drwav_write_proc_fd, (void*)(intptr_t)jFd, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to initialize drwav writer with procs for FD: %d", mRecordingFileDescriptor);
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mWavWriterInitialized = true;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer initialized with procs for FD: %d", mRecordingFileDescriptor);

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
    std::string sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    // Use audioEngineInstance for sample loading
    return audioEngineInstance && audioEngineInstance->loadSampleToMemory(sampleIdStr, fd, offset, length) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playPadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jNoteInstanceId,
        jstring jTrackId,
        jstring jPadId,
        jstring jSampleId,
        jstring jSliceId,
        jfloat velocity,
        jfloat coarseTune, // changed from jint to jfloat
        jfloat fineTune,   // changed from jint to jfloat
        jfloat pan,
        jfloat volume,
        jint jPlaybackModeOrdinal,
        jfloat jAmpEnvAttackMs,
        jfloat jAmpEnvDecayMs,
        jfloat jAmpEnvSustainLevel,
        jfloat jAmpEnvReleaseMs,
        jobject jFilterEnvSettings_kotlin,
        jobject jPitchEnvSettings_kotlin,
        jobjectArray jLfos_kotlin) {
    // Convert JNI strings to std::string
    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    const char *nativeTrackId = env->GetStringUTFChars(jTrackId, nullptr);
    const char *nativePadId = env->GetStringUTFChars(jPadId, nullptr);
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    std::string trackIdStr(nativeTrackId);
    std::string padIdStr(nativePadId);
    std::string sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jTrackId, nativeTrackId);
    env->ReleaseStringUTFChars(jPadId, nativePadId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    // Call AudioEngine's playPadSample with correct types
    return audioEngineInstance && audioEngineInstance->playPadSample(noteInstanceIdStr, trackIdStr, padIdStr, sampleIdStr, velocity, coarseTune, fineTune, pan, volume, jPlaybackModeOrdinal, jAmpEnvAttackMs, jAmpEnvDecayMs, jAmpEnvSustainLevel, jAmpEnvReleaseMs) ? JNI_TRUE : JNI_FALSE;
}

// Stub for ConvertKotlinEnvelopeSettings if missing
struct EnvelopeSettingsCpp {};
static EnvelopeSettingsCpp ConvertKotlinEnvelopeSettings(JNIEnv*, jobject) { return EnvelopeSettingsCpp(); }

// Stub for EventCpp if missing
namespace theone { namespace audio { struct EventCpp {}; } }

// --- JNI PadSettings Conversion Helper Functions ---

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

// Update ConvertKotlinEvent to use SequenceEventCpp
static theone::audio::SequenceEventCpp ConvertKotlinEvent(JNIEnv* env, jobject kotlinEvent) {
    theone::audio::SequenceEventCpp cppEvent;
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

// Update ConvertKotlinTrack to use SequenceTrackCpp
static theone::audio::SequenceTrackCpp ConvertKotlinTrack(JNIEnv* env, jobject kotlinTrack) {
    theone::audio::SequenceTrackCpp cppTrack;
    if (!kotlinTrack) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: kotlinTrack is null"); return cppTrack; }

    jclass trackClass = env->GetObjectClass(kotlinTrack);
    if (!trackClass) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: GetObjectClass failed for Track object."); return cppTrack; }

    jfieldID idFid = env->GetFieldID(trackClass, "id", "Ljava/lang/String;");
    jfieldID eventsFid = env->GetFieldID(trackClass, "events", "Ljava/util/List;");

    if (!idFid || !eventsFid) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: Failed to get one or more field IDs for Track");
        env->DeleteLocalRef(trackClass);
        return cppTrack;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinTrack, idFid);
    cppTrack.id = JStringToString(env, jId);
    if (jId) env->DeleteLocalRef(jId);

    // Convert events list
    jobject jEventsList = env->GetObjectField(kotlinTrack, eventsFid);
    if (jEventsList) {
        jclass listClass = env->GetObjectClass(jEventsList);
        jmethodID listSizeMethod = env->GetMethodID(listClass, "size", "()I");
        jmethodID listGetMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

        if (listSizeMethod && listGetMethod) {
            jint eventsCount = env->CallIntMethod(jEventsList, listSizeMethod);
            for (jint i = 0; i < eventsCount; ++i) {
                jobject jEvent = env->CallObjectMethod(jEventsList, listGetMethod, i);
                theone::audio::SequenceEventCpp cppEvent = ConvertKotlinEvent(env, jEvent);
                cppTrack.events.push_back(cppEvent);
                if (jEvent) env->DeleteLocalRef(jEvent);
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: Failed to get list methods for events");
        }
        env->DeleteLocalRef(listClass);
    } else {
        __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "ConvertKotlinTrack: events list is null");
    }

    env->DeleteLocalRef(trackClass);
    return cppTrack;
}

static theone::audio::SequenceCpp ConvertKotlinSequence(JNIEnv* env, jobject kotlinSequence) {
    theone::audio::SequenceCpp cppSequence;

    if (!kotlinSequence) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: kotlinSequence is null"); return cppSequence; }

    jclass sequenceClass = env->GetObjectClass(kotlinSequence);
    if (!sequenceClass) { __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: GetObjectClass failed for Sequence object."); return cppSequence; }

    jfieldID idFid = env->GetFieldID(sequenceClass, "id", "Ljava/lang/String;");
    jfieldID nameFid = env->GetFieldID(sequenceClass, "name", "Ljava/lang/String;");
    jfieldID bpmFid = env->GetFieldID(sequenceClass, "bpm", "F");
    jfieldID timeSignatureNumFid = env->GetFieldID(sequenceClass, "timeSignatureNumerator", "I");
    jfieldID timeSignatureDenFid = env->GetFieldID(sequenceClass, "timeSignatureDenominator", "I");
    jfieldID barLengthFid = env->GetFieldID(sequenceClass, "barLength", "J");
    jfieldID ppqnFid = env->GetFieldID(sequenceClass, "ppqn", "J");
    jfieldID tracksFid = env->GetFieldID(sequenceClass, "tracks", "Ljava/util/Map;");

    if (!idFid || !nameFid || !bpmFid || !timeSignatureNumFid || !timeSignatureDenFid || !barLengthFid || !ppqnFid || !tracksFid) {
        __android_log_print(ANDROID_LOG_ERROR, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: Failed to get one or more field IDs for Sequence");
        env->DeleteLocalRef(sequenceClass);
        return cppSequence;
    }

    jstring jId = (jstring)env->GetObjectField(kotlinSequence, idFid);
    cppSequence.id = JStringToString(env, jId);
    if (jId) env->DeleteLocalRef(jId);

    jstring jName = (jstring)env->GetObjectField(kotlinSequence, nameFid);
    cppSequence.name = JStringToString(env, jName);
    if (jName) env->DeleteLocalRef(jName);

    cppSequence.bpm = env->GetFloatField(kotlinSequence, bpmFid);
    cppSequence.timeSignatureNumerator = env->GetIntField(kotlinSequence, timeSignatureNumFid);
    cppSequence.timeSignatureDenominator = env->GetIntField(kotlinSequence, timeSignatureDenFid);
    cppSequence.barLength = env->GetLongField(kotlinSequence, barLengthFid);
    cppSequence.ppqn = env->GetLongField(kotlinSequence, ppqnFid);

    // Convert tracks map
    jobject jTracksMap = env->GetObjectField(kotlinSequence, tracksFid);
    if (jTracksMap) {
        jclass mapClass = env->GetObjectClass(jTracksMap);
        jmethodID mapEntrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject jSet = env->CallObjectMethod(jTracksMap, mapEntrySetMethod);
        jclass setClass = env->GetObjectClass(jSet);
        jmethodID setIteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject jIterator = env->CallObjectMethod(jSet, setIteratorMethod);
        jclass iteratorClass = env->GetObjectClass(jIterator);
        jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");
        jclass mapEntryClass = nullptr;
        jmethodID mapEntryGetKeyMethod = nullptr;
        jmethodID mapEntryGetValueMethod = nullptr;
        if (jIterator && hasNextMethod && nextMethod) {
            while (env->CallBooleanMethod(jIterator, hasNextMethod)) {
                jobject jMapEntry = env->CallObjectMethod(jIterator, nextMethod);
                if (!mapEntryClass) {
                    mapEntryClass = env->GetObjectClass(jMapEntry);
                    mapEntryGetKeyMethod = env->GetMethodID(mapEntryClass, "getKey", "()Ljava/lang/Object;");
                    mapEntryGetValueMethod = env->GetMethodID(mapEntryClass, "getValue", "()Ljava/lang/Object;");
                }
                jobject jTrackIdObj = env->CallObjectMethod(jMapEntry, mapEntryGetKeyMethod);
                jobject jTrackObj = env->CallObjectMethod(jMapEntry, mapEntryGetValueMethod);
                if (jTrackIdObj && jTrackObj) {
                    theone::audio::SequenceTrackCpp cppTrack = ConvertKotlinTrack(env, jTrackObj);
                    cppSequence.tracks.emplace(JStringToString(env, (jstring)jTrackIdObj), cppTrack);
                }
                if (jTrackIdObj) env->DeleteLocalRef(jTrackIdObj);
                if (jTrackObj) env->DeleteLocalRef(jTrackObj);
                if (jMapEntry) env->DeleteLocalRef(jMapEntry);
            }
        }
        if (jIterator) env->DeleteLocalRef(jIterator);
        if (jSet) env->DeleteLocalRef(jSet);
        if (setClass) env->DeleteLocalRef(setClass);
        if (iteratorClass) env->DeleteLocalRef(iteratorClass);
        if (mapEntryClass) env->DeleteLocalRef(mapEntryClass);
        env->DeleteLocalRef(mapClass);
    } else {
        __android_log_print(ANDROID_LOG_WARN, NATIVE_LIB_APP_NAME, "ConvertKotlinSequence: tracks map is null");
    }

    env->DeleteLocalRef(sequenceClass);
    return cppSequence;
}

// Implementation for drwav_write_proc_fd: writes data to a file descriptor
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite) {
    int fd = static_cast<int>(reinterpret_cast<intptr_t>(pUserData));
    if (fd < 0 || pData == nullptr || bytesToWrite == 0) return 0;
    ssize_t bytesWritten = write(fd, pData, bytesToWrite);
    if (bytesWritten < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_write_proc_fd: write failed: %s", strerror(errno));
        return 0;
    }
    return static_cast<size_t>(bytesWritten);
}