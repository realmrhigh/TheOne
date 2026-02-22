#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <oboe/Oboe.h>
#include <map> // For std::map
#include <cinttypes> // For PRIu64 format specifier
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
    //                 // This would require a Java callback interface and cached jobject/global ref.
    //                 // For now, this is a placeholder for future implementation.
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
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback: Failed to write all PCM frames. Wrote %" PRIu64 "/%d", (uint64_t)framesWritten, numFrames);
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

// --- MIGRATED JNI: com.high.theone.audio.AudioEngine ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1initialize(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) audioEngineInstance = std::make_unique<theone::audio::AudioEngine>();
    return audioEngineInstance && audioEngineInstance->initialize() ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1shutdown(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) audioEngineInstance->shutdown();
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setMetronomeState(
    JNIEnv* env, jobject /* thiz */, jboolean isEnabled, jfloat bpm, jint timeSignatureNum, jint timeSignatureDen, jstring soundPrimaryUri, jstring soundSecondaryUri) {
    if (audioEngineInstance) audioEngineInstance->setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, JStringToString(env, soundPrimaryUri), JStringToString(env, soundSecondaryUri));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1loadSampleToMemory(
    JNIEnv* env, jobject /* thiz */, jstring sampleId, jstring filePath, jlong offset, jlong length) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->loadSampleToMemory(JStringToString(env, sampleId), JStringToString(env, filePath), offset, length) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1unloadSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleId) {
    if (audioEngineInstance) audioEngineInstance->unloadSample(JStringToString(env, sampleId));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1playPadSample(
    JNIEnv* env, jobject /* thiz */, jstring noteInstanceId, jstring trackId, jstring padId,
    jstring sampleId, jfloat velocity, jfloat coarseTune, jfloat fineTune, jfloat pan, jfloat volume,
    jint playbackModeOrdinal, jfloat ampEnvAttackMs, jfloat ampEnvDecayMs, jfloat ampEnvSustainLevel, jfloat ampEnvReleaseMs) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->playPadSample(
        JStringToString(env, noteInstanceId),
        JStringToString(env, trackId),
        JStringToString(env, padId),
        JStringToString(env, sampleId),
        velocity, coarseTune, fineTune, pan, volume,
        playbackModeOrdinal, ampEnvAttackMs, ampEnvDecayMs, ampEnvSustainLevel, ampEnvReleaseMs
    ) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1stopNote(
    JNIEnv* env, jobject /* thiz */, jstring noteInstanceId, jfloat releaseTimeMs) {
    if (audioEngineInstance) audioEngineInstance->stopNote(JStringToString(env, noteInstanceId), releaseTimeMs);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1stopAllNotes(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jboolean immediate) {
    if (audioEngineInstance) audioEngineInstance->stopAllNotes(JStringToString(env, trackId), immediate);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1startAudioRecording(
    JNIEnv* env, jobject thiz, jstring filePathUri, jstring inputDeviceId) {
    if (!audioEngineInstance) return JNI_FALSE;
    // TODO: Use inputDeviceId if needed. For now, pass default sampleRate and channels.
    const std::string filePath = JStringToString(env, filePathUri);
    int sampleRate = 44100; // Default/stub value
    int channels = 2;       // Default/stub value
    return audioEngineInstance->startAudioRecording(env, thiz, filePath, sampleRate, channels) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_high_theone_audio_AudioEngine_native_1stopAudioRecording(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return nullptr;
    return audioEngineInstance->stopAudioRecording(env);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setTrackVolume(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jfloat volume) {
    if (audioEngineInstance) audioEngineInstance->setTrackVolume(JStringToString(env, trackId), volume);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setTrackPan(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jfloat pan) {
    if (audioEngineInstance) audioEngineInstance->setTrackPan(JStringToString(env, trackId), pan);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1addTrackEffect(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jobject effectInstance) {
    // TODO: Convert effectInstance from Java to C++
    // This requires a C++ Effect class and a Java-to-C++ mapping. For now, log and return false.
    if (!audioEngineInstance) return JNI_FALSE;
    return JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1removeTrackEffect(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jstring effectInstanceId) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->removeTrackEffect(JStringToString(env, trackId), JStringToString(env, effectInstanceId)) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setTransportBpm(
    JNIEnv* env, jobject /* thiz */, jfloat bpm) {
    if (audioEngineInstance) audioEngineInstance->setTransportBpm(bpm);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setSampleEnvelope(
    JNIEnv* env, jobject /* thiz */, jstring sampleId, jobject envelopeObj) {
    if (!audioEngineInstance) return;
    const std::string sampleIdStr = JStringToString(env, sampleId);
    // TODO: Convert envelopeObj (EnvelopeSettings) from Java to C++ struct
    // For now, just log and return
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_setSampleEnvelope called for %s", sampleIdStr.c_str());
    // audioEngineInstance->setSampleEnvelope(sampleIdStr, envelopeSettingsCpp);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setSampleLFO(
    JNIEnv* env, jobject /* thiz */, jstring sampleId, jobject lfoObj) {
    if (!audioEngineInstance) return;
    const std::string sampleIdStr = JStringToString(env, sampleId);
    // TODO: Convert lfoObj (LFOSettings) from Java to C++ struct
    // For now, just log and return
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_setSampleLFO called for %s", sampleIdStr.c_str());
    // audioEngineInstance->setSampleLFO(sampleIdStr, lfoSettingsCpp);
}
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setEffectParameter(
    JNIEnv* env, jobject /* thiz */, jstring effectId, jstring parameter, jfloat value) {
    if (!audioEngineInstance) return;
    const std::string effectIdStr = JStringToString(env, effectId);
    const std::string parameterStr = JStringToString(env, parameter);
    audioEngineInstance->setEffectParameter(effectIdStr, parameterStr, value);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1addInsertEffect(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jstring effectType, jobject parametersMap) {
    if (!audioEngineInstance) return JNI_FALSE;
    const std::string trackIdStr = JStringToString(env, trackId);
    const std::string effectTypeStr = JStringToString(env, effectType);
    // TODO: Convert parametersMap (Java Map) to C++ map
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_addInsertEffect: trackId=%s, effectType=%s", trackIdStr.c_str(), effectTypeStr.c_str());
    // return audioEngineInstance->addInsertEffect(trackIdStr, effectTypeStr, cppParams) ? JNI_TRUE : JNI_FALSE;
    return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1removeInsertEffect(
    JNIEnv* env, jobject /* thiz */, jstring trackId, jstring effectId) {
    if (!audioEngineInstance) return JNI_FALSE;
    const std::string trackIdStr = JStringToString(env, trackId);
    const std::string effectIdStr = JStringToString(env, effectId);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_removeInsertEffect: trackId=%s, effectId=%s", trackIdStr.c_str(), effectIdStr.c_str());
    // return audioEngineInstance->removeInsertEffect(trackIdStr, effectIdStr) ? JNI_TRUE : JNI_FALSE;
    return JNI_TRUE;
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_high_theone_audio_AudioEngine_native_1getAudioLevels(
    JNIEnv* env, jobject /* thiz */, jstring trackId) {
    if (!audioEngineInstance) return nullptr;
    const std::string trackIdStr = JStringToString(env, trackId);
    // For now, return dummy levels (L, R)
    jfloatArray result = env->NewFloatArray(2);
    float levels[2] = {0.0f, 0.0f};
    // TODO: Look up actual levels for the track
    env->SetFloatArrayRegion(result, 0, 2, levels);
    return result;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_high_theone_audio_AudioEngine_native_1getReportedLatencyMillis(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return -1.0f;
    return audioEngineInstance->getOboeReportedLatencyMillis();
}

// 🔥 EPIC SAMPLE TRIGGERING JNI FUNCTIONS
extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1triggerSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleKey, jfloat volume, jfloat pan) {
    if (!audioEngineInstance) return;
    audioEngineInstance->triggerSample(JStringToString(env, sampleKey), volume, pan);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1stopAllSamples(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) audioEngineInstance->stopAllSamples();
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1loadTestSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleKey) {
    if (!audioEngineInstance) return;
    audioEngineInstance->loadTestSample(JStringToString(env, sampleKey));
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setMasterVolume(
    JNIEnv* env, jobject /* thiz */, jfloat volume) {
    if (audioEngineInstance) audioEngineInstance->setMasterVolume(volume);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_high_theone_audio_AudioEngine_native_1getMasterVolume(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return 0.0f;
    return audioEngineInstance->getMasterVolume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setTestToneEnabled(
    JNIEnv* env, jobject /* thiz */, jboolean enabled) {
    if (audioEngineInstance) audioEngineInstance->setTestToneEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1isTestToneEnabled(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->isTestToneEnabled() ? JNI_TRUE : JNI_FALSE;
}

// --- END MIGRATED JNI ---

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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1createAndTriggerTestSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleKey, jfloat volume, jfloat pan) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->createAndTriggerTestSample(JStringToString(env, sampleKey), volume, pan);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 🎛️ ===== AVST PLUGIN SYSTEM JNI FUNCTIONS ===== 🎛️

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1loadPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring pluginName) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->loadPlugin(
        JStringToString(env, pluginId), 
        JStringToString(env, pluginName)
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1unloadPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->unloadPlugin(JStringToString(env, pluginId));
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_high_theone_audio_AudioEngine_native_1getLoadedPlugins(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }
    
    auto pluginIds = audioEngineInstance->getLoadedPlugins();
    jobjectArray result = env->NewObjectArray(
        pluginIds.size(), 
        env->FindClass("java/lang/String"), 
        nullptr
    );
    
    for (size_t i = 0; i < pluginIds.size(); ++i) {
        jstring str = env->NewStringUTF(pluginIds[i].c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }
    
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setPluginParameter(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring paramId, jdouble value) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->setPluginParameter(
        JStringToString(env, pluginId),
        JStringToString(env, paramId),
        value
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_high_theone_audio_AudioEngine_native_1getPluginParameter(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring paramId) {
    if (!audioEngineInstance) return 0.0;
    return audioEngineInstance->getPluginParameter(
        JStringToString(env, pluginId),
        JStringToString(env, paramId)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1noteOnToPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jint note, jint velocity) {
    if (!audioEngineInstance) return;
    audioEngineInstance->noteOnToPlugin(
        JStringToString(env, pluginId),
        static_cast<uint8_t>(note),
        static_cast<uint8_t>(velocity)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1noteOffToPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jint note, jint velocity) {
    if (!audioEngineInstance) return;
    audioEngineInstance->noteOffToPlugin(
        JStringToString(env, pluginId),
        static_cast<uint8_t>(note),
        static_cast<uint8_t>(velocity)
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1savePluginPreset(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring presetName, jstring filePath) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->savePluginPreset(
        JStringToString(env, pluginId),
        JStringToString(env, presetName),
        JStringToString(env, filePath)
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngine_native_1loadPluginPreset(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring filePath) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->loadPluginPreset(
        JStringToString(env, pluginId),
        JStringToString(env, filePath)
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngine_native_1setAssetManager(
    JNIEnv* env, jobject /* thiz */, jobject assetManager) {
    if (audioEngineInstance && assetManager) {
        AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
        audioEngineInstance->setAssetManager(nativeAssetManager);
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Asset manager set for AudioEngine");
    }
}

// --- JNI METHODS FOR AudioEngineImpl ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1initialize(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) audioEngineInstance = std::make_unique<theone::audio::AudioEngine>();
    return audioEngineInstance && audioEngineInstance->initialize() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1shutdown(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) audioEngineInstance->shutdown();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setAssetManager(
    JNIEnv* env, jobject /* thiz */, jobject assetManager) {
    if (audioEngineInstance && assetManager) {
        AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
        audioEngineInstance->setAssetManager(nativeAssetManager);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1createAndTriggerTestSample(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->createAndTriggerTestSample("test_sample", 1.0f, 0.0f);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1loadTestSample(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return JNI_FALSE;
    audioEngineInstance->loadTestSample("test_sample");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1triggerTestPadSample(
    JNIEnv* env, jobject /* thiz */, jint padIndex) {
    if (!audioEngineInstance) return JNI_FALSE;
    // For now, just trigger the test sample
    bool result = audioEngineInstance->createAndTriggerTestSample("test_pad_" + std::to_string(padIndex), 1.0f, 0.0f);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getOboeReportedLatencyMillis(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return -1.0f;
    return audioEngineInstance->getOboeReportedLatencyMillis();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1loadSampleToMemory(
    JNIEnv* env, jobject /* thiz */, jstring sampleId, jstring filePath) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->loadSampleToMemory(JStringToString(env, sampleId), JStringToString(env, filePath), 0, 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1unloadSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleId) {
    if (audioEngineInstance) audioEngineInstance->unloadSample(JStringToString(env, sampleId));
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1triggerSample(
    JNIEnv* env, jobject /* thiz */, jstring sampleKey, jfloat volume, jfloat pan) {
    if (!audioEngineInstance) return;
    audioEngineInstance->triggerSample(JStringToString(env, sampleKey), volume, pan);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1stopAllSamples(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) audioEngineInstance->stopAllSamples();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1loadSampleFromAsset(
    JNIEnv* env, jobject /* thiz */, jstring sampleId, jstring assetPath) {
    if (!audioEngineInstance) return JNI_FALSE;
    // Use the proper loadSampleFromAsset method
    return audioEngineInstance->loadSampleFromAsset(JStringToString(env, sampleId), JStringToString(env, assetPath)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1initializeDrumEngine(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return JNI_FALSE;
    // For now, just return true as the main engine handles drum functionality
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1triggerDrumPad(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jfloat velocity) {
    if (!audioEngineInstance) return;
    // For now, trigger a test sample for the drum pad
    audioEngineInstance->createAndTriggerTestSample("drum_pad_" + std::to_string(padIndex), velocity, 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1releaseDrumPad(
    JNIEnv* env, jobject /* thiz */, jint padIndex) {
    if (!audioEngineInstance) return;
    // For now, just log the release
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Drum pad %d released", padIndex);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1loadDrumSample(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jstring samplePath) {
    if (!audioEngineInstance) return JNI_FALSE;
    std::string sampleId = "drum_pad_" + std::to_string(padIndex);
    return audioEngineInstance->loadSampleToMemory(sampleId, JStringToString(env, samplePath), 0, 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setDrumPadVolume(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jfloat volume) {
    if (!audioEngineInstance) return;
    // For now, just log the volume change
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Drum pad %d volume set to %f", padIndex, volume);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setDrumPadPan(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jfloat pan) {
    if (!audioEngineInstance) return;
    // For now, just log the pan change
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Drum pad %d pan set to %f", padIndex, pan);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setDrumPadMode(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jint playbackMode) {
    if (!audioEngineInstance) return;
    // For now, just log the mode change
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Drum pad %d mode set to %d", padIndex, playbackMode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setDrumMasterVolume(
    JNIEnv* env, jobject /* thiz */, jfloat volume) {
    if (audioEngineInstance) audioEngineInstance->setMasterVolume(volume);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getDrumActiveVoices(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return 0;
    // For now, return a dummy value
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1clearDrumVoices(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) audioEngineInstance->stopAllSamples();
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1debugPrintDrumEngineState(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return;
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Drum engine state: AudioEngine initialized");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getDrumEngineLoadedSamples(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return 0;
    // For now, return a dummy value
    return 0;
}

// 🎛️ ===== AVST PLUGIN SYSTEM JNI FUNCTIONS FOR AudioEngineImpl ===== 🎛️

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1loadPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring pluginName) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->loadPlugin(
        JStringToString(env, pluginId),
        JStringToString(env, pluginName)
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1unloadPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->unloadPlugin(JStringToString(env, pluginId));
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getLoadedPlugins(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }

    auto pluginIds = audioEngineInstance->getLoadedPlugins();
    jobjectArray result = env->NewObjectArray(
        pluginIds.size(),
        env->FindClass("java/lang/String"),
        nullptr
    );

    for (size_t i = 0; i < pluginIds.size(); ++i) {
        jstring str = env->NewStringUTF(pluginIds[i].c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }

    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setPluginParameter(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jstring paramId, jdouble value) {
    if (!audioEngineInstance) return JNI_FALSE;
    bool result = audioEngineInstance->setPluginParameter(
        JStringToString(env, pluginId),
        JStringToString(env, paramId),
        static_cast<double>(value)
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1noteOnToPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jint note, jint velocity) {
    if (!audioEngineInstance) return;
    audioEngineInstance->noteOnToPlugin(
        JStringToString(env, pluginId),
        static_cast<uint8_t>(note),
        static_cast<uint8_t>(velocity)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1noteOffToPlugin(
    JNIEnv* env, jobject /* thiz */, jstring pluginId, jint note, jint velocity) {
    if (!audioEngineInstance) return;
    audioEngineInstance->noteOffToPlugin(
        JStringToString(env, pluginId),
        static_cast<uint8_t>(note),
        static_cast<uint8_t>(velocity)
    );
}

// 🎵 SEQUENCER INTEGRATION JNI METHODS

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1scheduleStepTrigger(
    JNIEnv* env, jobject /* thiz */, jint padIndex, jfloat velocity, jlong timestamp) {
    if (!audioEngineInstance) return JNI_FALSE;
    return audioEngineInstance->scheduleStepTrigger(
        static_cast<int>(padIndex),
        velocity,
        static_cast<int64_t>(timestamp)
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setSequencerTempo(
    JNIEnv* env, jobject /* thiz */, jfloat bpm) {
    if (audioEngineInstance) {
        audioEngineInstance->setSequencerTempo(bpm);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getAudioLatencyMicros(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return 0L;
    return static_cast<jlong>(audioEngineInstance->getAudioLatencyMicros());
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1setHighPrecisionMode(
    JNIEnv* env, jobject /* thiz */, jboolean enabled) {
    if (audioEngineInstance) {
        audioEngineInstance->setHighPrecisionMode(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1preloadSequencerSamples(
    JNIEnv* env, jobject /* thiz */, jintArray padIndices) {
    if (!audioEngineInstance || !padIndices) return JNI_FALSE;
    
    jsize length = env->GetArrayLength(padIndices);
    jint* indices = env->GetIntArrayElements(padIndices, nullptr);
    
    if (!indices) return JNI_FALSE;
    
    bool result = audioEngineInstance->preloadSequencerSamples(indices, static_cast<int>(length));
    
    env->ReleaseIntArrayElements(padIndices, indices, JNI_ABORT);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1clearScheduledEvents(
    JNIEnv* env, jobject /* thiz */) {
    if (audioEngineInstance) {
        audioEngineInstance->clearScheduledEvents();
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_high_theone_audio_AudioEngineImpl_native_1getTimingStatistics(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return nullptr;
    
    auto stats = audioEngineInstance->getTimingStatistics();
    
    // Create a HashMap to return the statistics
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    if (!hashMapClass) return nullptr;
    
    jmethodID hashMapConstructor = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    
    if (!hashMapConstructor || !putMethod) return nullptr;
    
    jobject hashMap = env->NewObject(hashMapClass, hashMapConstructor);
    if (!hashMap) return nullptr;
    
    // Add statistics to the map
    for (const auto& pair : stats) {
        jstring key = env->NewStringUTF(pair.first.c_str());
        
        // Convert double to Double object
        jclass doubleClass = env->FindClass("java/lang/Double");
        jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");
        jobject value = env->NewObject(doubleClass, doubleConstructor, pair.second);
        
        if (key && value) {
            env->CallObjectMethod(hashMap, putMethod, key, value);
        }
        
        if (key) env->DeleteLocalRef(key);
        if (value) env->DeleteLocalRef(value);
    }
    
    return hashMap;
}

// 🎹 ===== MIDI PROCESSING JNI FUNCTIONS ===== 🎹

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1processMidiMessage(
    JNIEnv* env, jobject /* thiz */, jint type, jint channel, jint data1, jint data2, jlong timestamp) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->processMidiMessage(
        static_cast<uint8_t>(type),
        static_cast<uint8_t>(channel),
        static_cast<uint8_t>(data1),
        static_cast<uint8_t>(data2),
        static_cast<int64_t>(timestamp)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1scheduleMidiEvent(
    JNIEnv* env, jobject /* thiz */, jint type, jint channel, jint data1, jint data2, jlong timestamp) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->scheduleMidiEvent(
        static_cast<uint8_t>(type),
        static_cast<uint8_t>(channel),
        static_cast<uint8_t>(data1),
        static_cast<uint8_t>(data2),
        static_cast<int64_t>(timestamp)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setMidiNoteMapping(
    JNIEnv* env, jobject /* thiz */, jint midiNote, jint midiChannel, jint padIndex) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setMidiNoteMapping(
        static_cast<uint8_t>(midiNote),
        static_cast<uint8_t>(midiChannel),
        padIndex
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1removeMidiNoteMapping(
    JNIEnv* env, jobject /* thiz */, jint midiNote, jint midiChannel) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->removeMidiNoteMapping(
        static_cast<uint8_t>(midiNote),
        static_cast<uint8_t>(midiChannel)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setMidiVelocityCurve(
    JNIEnv* env, jobject /* thiz */, jint curveType, jfloat sensitivity) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setMidiVelocityCurve(curveType, sensitivity);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1applyMidiVelocityCurve(
    JNIEnv* env, jobject /* thiz */, jint velocity) {
    if (!audioEngineInstance) return 0.0f;
    
    return audioEngineInstance->applyMidiVelocityCurve(static_cast<uint8_t>(velocity));
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setMidiClockSyncEnabled(
    JNIEnv* env, jobject /* thiz */, jboolean enabled) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setMidiClockSyncEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1processMidiClockPulse(
    JNIEnv* env, jobject /* thiz */, jlong timestamp, jfloat bpm) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->processMidiClockPulse(static_cast<int64_t>(timestamp), bpm);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1handleMidiTransport(
    JNIEnv* env, jobject /* thiz */, jint transportType) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->handleMidiTransport(transportType);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setMidiInputLatency(
    JNIEnv* env, jobject /* thiz */, jlong latencyMicros) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setMidiInputLatency(static_cast<int64_t>(latencyMicros));
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1getMidiStatistics(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return nullptr;
    
    auto stats = audioEngineInstance->getMidiStatistics();
    
    // Create a HashMap to return the statistics
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapConstructor = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    
    jobject hashMap = env->NewObject(hashMapClass, hashMapConstructor);
    
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
    
    for (const auto& pair : stats) {
        jstring key = env->NewStringUTF(pair.first.c_str());
        jobject value = env->NewObject(longClass, longConstructor, static_cast<jlong>(pair.second));
        
        env->CallObjectMethod(hashMap, putMethod, key, value);
        
        env->DeleteLocalRef(key);
        env->DeleteLocalRef(value);
    }
    
    return hashMap;
}

// ===== MIDI CLOCK SYNCHRONIZATION JNI FUNCTIONS =====

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setExternalClockEnabled(
    JNIEnv* env, jobject /* thiz */, jboolean useExternal) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setExternalClockEnabled(useExternal == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1setClockSmoothingFactor(
    JNIEnv* env, jobject /* thiz */, jfloat factor) {
    if (!audioEngineInstance) return;
    
    audioEngineInstance->setClockSmoothingFactor(factor);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1getCurrentBpm(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return 120.0f;
    
    return audioEngineInstance->getCurrentBpm();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_high_theone_midi_integration_MidiAudioEngineAdapterImpl_native_1isClockStable(
    JNIEnv* env, jobject /* thiz */) {
    if (!audioEngineInstance) return JNI_FALSE;
    
    return audioEngineInstance->isClockStable() ? JNI_TRUE : JNI_FALSE;
}