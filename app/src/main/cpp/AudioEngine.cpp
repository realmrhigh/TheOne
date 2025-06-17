#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm> // For std::max, std::min
#include <cmath>     // For M_PI, cosf, sinf, powf
#include <string.h>  // For memset in onAudioReady
#include <fcntl.h>   // For open
#include <unistd.h>  // For close

// Ensure M_PI and M_PI_2 are defined
#ifndef M_PI
    #define M_PI 3.14159265358979323846
#endif
#ifndef M_PI_2
    #define M_PI_2 (M_PI / 2.0)
#endif

// Define APP_NAME for logging, if not already globally available
#ifndef APP_NAME
#define APP_NAME "TheOneAudioEngine"
#endif

namespace theone {
namespace audio {

AudioEngine::AudioEngine() : oboeInitialized_(false), audioStreamSampleRate_(0), currentTickDurationMs_(0.0), timeAccumulatedForTick_(0.0) {
    // Constructor: Initialize members, e.g., random engine
    // Ensure padSettingsMap_, sampleMap_ etc. are ready.
    // The randomEngine_ is already initialized in the header.
}

AudioEngine::~AudioEngine() {
    shutdown(); // Ensure resources are released
}

bool AudioEngine::initialize() {
    if (oboeInitialized_.load()) {
        return true;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setSampleRate(oboe::kUnspecified) // Let Oboe pick optimal
            ->setCallback(this); // `this` AudioEngine instance is the callback

    oboe::Result result = builder.openManagedStream(outStream_);
    if (result == oboe::Result::OK) {
        audioStreamSampleRate_ = static_cast<uint32_t>(outStream_->getSampleRate());

        // Initialize metronome with sample rate
        {
            std::lock_guard<std::mutex> lock(metronomeStateMutex_);
            metronomeState_.audioStreamSampleRate = audioStreamSampleRate_;
            metronomeState_.updateSchedulingParameters();
        }

        // Start the stream
        result = outStream_->requestStart();
        if (result != oboe::Result::OK) {
            outStream_->close();
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::initialize - Failed to start output stream: %s", oboe::convertToText(result));
            return false;
        }
        oboeInitialized_.store(true);
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::initialize - Oboe output stream started. Sample Rate: %u", audioStreamSampleRate_.load());
        return true;
    }
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::initialize - Failed to open output stream: %s", oboe::convertToText(result));
    return false;
}

void AudioEngine::shutdown() {
    if (isRecording_.load()) {
        // Stop recording first if active (simplified)
        // Actual stopAudioRecording might need JNIEnv, so this is conceptual
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::shutdown - Stopping active recording.");
        // Call a non-JNI version of stop or set flag for onAudioReady to handle.
        isRecording_.store(false);
        if (mInputStream_) {
             mInputStream_->requestStop();
             mInputStream_->close();
        }
        if (wavWriterInitialized_) {
            drwav_uninit(&wavWriter_);
            wavWriterInitialized_ = false;
        }
        if (recordingFileDescriptor_ != -1) {
            // This FD was likely a dup, original managed by Kotlin.
            // If AudioEngine dupped it, it should close it.
            // For simplicity here, assume it's managed elsewhere or closed if dupped.
            recordingFileDescriptor_ = -1;
        }
    }

    if (outStream_) {
        outStream_->requestStop();
        outStream_->close();
    }
    oboeInitialized_.store(false);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::shutdown - Oboe streams closed.");
}

// --- Pad Settings Management ---
void AudioEngine::updatePadSettings(const std::string& padKey, const PadSettingsCpp& settings) {
    std::lock_guard<std::mutex> lock(padSettingsMutex_);
    padSettingsMap_[padKey] = settings;
    padSettingsMap_[padKey].currentCycleLayerIndex = 0; // Reset cycle index on update
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Updated PadSettings for key: %s", padKey.c_str());
}

void AudioEngine::setPadVolume(const std::string& padKey, float volume) {
    std::lock_guard<std::mutex> lock(padSettingsMutex_);
    auto it = padSettingsMap_.find(padKey);
    if (it != padSettingsMap_.end()) {
        float clampedVolume = std::max(0.0f, std::min(2.0f, volume)); // Example range 0-2
        it->second.volume = clampedVolume;
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Volume for pad '%s' set to %f (clamped: %f)", padKey.c_str(), volume, clampedVolume);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "setPadVolume: PadSettings not found for key '%s'", padKey.c_str());
    }
}

void AudioEngine::setPadPan(const std::string& padKey, float pan) {
    std::lock_guard<std::mutex> lock(padSettingsMutex_);
    auto it = padSettingsMap_.find(padKey);
    if (it != padSettingsMap_.end()) {
        float clampedPan = std::max(-1.0f, std::min(1.0f, pan));
        it->second.pan = clampedPan;
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Pan for pad '%s' set to %f (clamped: %f)", padKey.c_str(), pan, clampedPan);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "setPadPan: PadSettings not found for key '%s'", padKey.c_str());
    }
}

// --- Oboe Callbacks ---
oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

    // Minimalistic audio processing: silence
    // Full processing logic (metronome, sample playback, sequencer) would go here,
    // using class members (activeSounds_, sampleMap_, padSettingsMap_, etc.)
    // and their respective mutexes.

    float *outputBuffer = static_cast<float*>(audioData);
    memset(outputBuffer, 0, sizeof(float) * numFrames * oboeStream->getChannelCount());

    // --- Sequencer Clock Processing ---
    uint32_t currentSampleRate = this->audioStreamSampleRate_.load();
    double tickDuration = this->currentTickDurationMs_; // Read once per callback

    if (currentSampleRate > 0 && tickDuration > 0.0) {
        std::lock_guard<std::mutex> sequencerLock(this->sequencerMutex_);

        if (this->currentSequence_ && this->currentSequence_->isPlaying) {
            double singleFrameDurationMs = 1000.0 / static_cast<double>(currentSampleRate);
            this->timeAccumulatedForTick_ += static_cast<double>(numFrames) * singleFrameDurationMs;

            while (this->timeAccumulatedForTick_ >= tickDuration && tickDuration > 0.0) {
                this->timeAccumulatedForTick_ -= tickDuration;
                this->currentSequence_->currentPlayheadTicks++;

                long beatsPerBar = this->currentSequence_->timeSignatureNumerator;
                if (beatsPerBar <= 0) beatsPerBar = 4;

                long ticksPerBar = beatsPerBar * this->currentSequence_->ppqn;
                if (ticksPerBar <= 0 && this->currentSequence_->ppqn > 0) {
                   ticksPerBar = 4 * this->currentSequence_->ppqn;
                }

                long totalTicksInSequence = this->currentSequence_->barLength * ticksPerBar;

                if (totalTicksInSequence <= 0) {
                    if(this->currentSequence_->ppqn > 0) {
                       totalTicksInSequence = 4L * 4L * this->currentSequence_->ppqn;
                    } else {
                       totalTicksInSequence = -1L;
                    }
                }

                if (totalTicksInSequence > 0 && this->currentSequence_->currentPlayheadTicks >= totalTicksInSequence) {
                    this->currentSequence_->currentPlayheadTicks = 0;
                    __android_log_print(ANDROID_LOG_DEBUG, "TheOneAudioEngine",
                                        "Sequencer looped. Playhead reset to 0. Total ticks: %ld. Accum time: %f",
                                        totalTicksInSequence, this->timeAccumulatedForTick_);
                }

                // __android_log_print(ANDROID_LOG_VERBOSE, "TheOneAudioEngine", "Tick advanced to: %ld", this->currentSequence_->currentPlayheadTicks);

                // --- Trigger Events ---
                // Accessing currentSequence_ members is safe here due to the outer sequencerLock
                for (auto const& [trackId, track] : this->currentSequence_->tracks) {
                    for (const auto& event : track.events) {
                        if (event.startTimeTicks == this->currentSequence_->currentPlayheadTicks) {
                            if (event.type == theone::audio::EventTriggerTypeCpp::PAD_TRIGGER) {
                                std::string padKey = event.trackId + "_" + event.padTrigger.padId;

                                std::shared_ptr<theone::audio::PadSettingsCpp> padSettingsPtr;
                                { // Scope for padSettingsMutex_
                                    std::lock_guard<std::mutex> settingsLock(this->padSettingsMutex_);
                                    auto it = this->padSettingsMap_.find(padKey);
                                    if (it != this->padSettingsMap_.end()) {
                                        padSettingsPtr = std::make_shared<theone::audio::PadSettingsCpp>(it->second);
                                    }
                                } // padSettingsMutex_ released

                                if (padSettingsPtr) {
                                    const theone::audio::SampleLayerCpp* selectedLayer = nullptr;
                                    // Simplified layer selection: first enabled layer with a sampleId
                                    for (const auto& layer : padSettingsPtr->layers) {
                                        if (layer.enabled && !layer.sampleId.empty()) {
                                            selectedLayer = &layer;
                                            break;
                                        }
                                    }

                                    if (selectedLayer) {
                                        const theone::audio::LoadedSample* loadedSample = nullptr;
                                        { // Scope for sampleMapMutex_
                                            std::lock_guard<std::mutex> samplesLock(this->sampleMapMutex_);
                                            auto sampleIt = this->sampleMap_.find(selectedLayer->sampleId);
                                            if (sampleIt != this->sampleMap_.end()) {
                                                loadedSample = &(sampleIt->second);
                                            }
                                        } // sampleMapMutex_ released

                                        if (loadedSample && !loadedSample->audioData.empty()) {
                                            std::string noteInstanceId = "seq_" + event.id + "_" +
                                                                         std::to_string(this->currentSequence_->currentPlayheadTicks) + "_" +
                                                                         std::to_string(this->randomEngine_() % 10000);

                                            float velocityNormalized = static_cast<float>(event.padTrigger.velocity) / 127.0f;
                                            if (velocityNormalized < 0.0f) velocityNormalized = 0.0f;
                                            if (velocityNormalized > 1.0f) velocityNormalized = 1.0f;

                                            float baseVolume = padSettingsPtr->volume;
                                            float basePan = padSettingsPtr->pan;
                                            float layerVolumeOffsetGain = 1.0f;
                                            if (selectedLayer->volumeOffsetDb > -90.0f) {
                                                layerVolumeOffsetGain = powf(10.0f, selectedLayer->volumeOffsetDb / 20.0f);
                                            } else {
                                                layerVolumeOffsetGain = 0.0f;
                                            }
                                            float layerPanOffset = selectedLayer->panOffset;

                                            float finalVolume = baseVolume * layerVolumeOffsetGain * velocityNormalized;
                                            float finalPan = basePan + layerPanOffset;
                                            finalPan = std::max(-1.0f, std::min(1.0f, finalPan));

                                            theone::audio::PlayingSound soundToPlay(loadedSample, noteInstanceId, finalVolume, finalPan);
                                            soundToPlay.padSettings = padSettingsPtr;

                                            uint32_t currentSampleRateForModules = this->audioStreamSampleRate_.load();
                                            if (currentSampleRateForModules == 0) currentSampleRateForModules = 48000;

                                            soundToPlay.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                            soundToPlay.ampEnvelopeGen->configure(padSettingsPtr->ampEnvelope, static_cast<float>(currentSampleRateForModules), velocityNormalized);
                                            soundToPlay.ampEnvelopeGen->triggerOn(velocityNormalized);

                                            // Populate total tuning from Pad base + Layer offset
                                            if (soundToPlay.padSettings && selectedLayer) {
                                                soundToPlay.totalTuningCoarse_ = soundToPlay.padSettings->tuningCoarse + selectedLayer->tuningCoarseOffset;
                                                soundToPlay.totalTuningFine_ = soundToPlay.padSettings->tuningFine + selectedLayer->tuningFineOffset;
                                            } else if (soundToPlay.padSettings) { // Fallback if no specific layer (should ideally not happen for sequenced pad sounds)
                                                soundToPlay.totalTuningCoarse_ = soundToPlay.padSettings->tuningCoarse;
                                                soundToPlay.totalTuningFine_ = soundToPlay.padSettings->tuningFine;
                                            }
                                            // totalTuningCoarse_ and totalTuningFine_ remain 0 if no padSettings

                                            if (padSettingsPtr->hasPitchEnvelope) {
                                                soundToPlay.pitchEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                                soundToPlay.pitchEnvelopeGen->configure(padSettingsPtr->pitchEnvelope, static_cast<float>(currentSampleRateForModules), velocityNormalized);
                                                soundToPlay.pitchEnvelopeGen->triggerOn(velocityNormalized);
                                            }

                                            if (padSettingsPtr->hasFilterEnvelope) {
                                                soundToPlay.filterEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
                                                soundToPlay.filterEnvelopeGen->configure(padSettingsPtr->filterEnvelope, static_cast<float>(currentSampleRateForModules), velocityNormalized);
                                                soundToPlay.filterEnvelopeGen->triggerOn(velocityNormalized);
                                            }

                                            soundToPlay.lfoGens.clear();
                                            float currentTempo = this->currentSequence_ ? this->currentSequence_->bpm : 120.0f;
                                            if (currentTempo <= 0) currentTempo = 120.0f;

                                            for (const auto& lfoConfig : padSettingsPtr->lfos) {
                                                if (lfoConfig.isEnabled) {
                                                    auto lfo = std::make_unique<theone::audio::LfoGenerator>();
                                                    lfo->configure(lfoConfig, static_cast<float>(currentSampleRateForModules), currentTempo);
                                                    lfo->retrigger();
                                                    soundToPlay.lfoGens.push_back(std::move(lfo));
                                                }
                                            }

                                            // --- Filter Initialization for new sound ---
                                            if (soundToPlay.padSettings && soundToPlay.padSettings->filterSettings.enabled) {
                                                uint32_t currentSr = this->audioStreamSampleRate_.load();
                                                if (currentSr == 0) currentSr = 48000; // Fallback

                                                soundToPlay.filterL_ = std::make_unique<theone::audio::StateVariableFilter>();
                                                soundToPlay.filterR_ = std::make_unique<theone::audio::StateVariableFilter>();

                                                soundToPlay.filterL_->setSampleRate(static_cast<float>(currentSr));
                                                soundToPlay.filterR_->setSampleRate(static_cast<float>(currentSr));

                                                soundToPlay.filterL_->configure(
                                                    soundToPlay.padSettings->filterSettings.mode,
                                                    soundToPlay.padSettings->filterSettings.cutoffHz,
                                                    soundToPlay.padSettings->filterSettings.resonance
                                                );
                                                soundToPlay.filterR_->configure(
                                                    soundToPlay.padSettings->filterSettings.mode,
                                                    soundToPlay.padSettings->filterSettings.cutoffHz,
                                                    soundToPlay.padSettings->filterSettings.resonance
                                                );
                                            }
                                            // --- End Filter Initialization ---

                                            { // Scope for activeSoundsMutex_
                                                std::lock_guard<std::mutex> activeSoundsLock(this->activeSoundsMutex_);
                                                this->activeSounds_.push_back(std::move(soundToPlay));
                                            }
                                            __android_log_print(ANDROID_LOG_DEBUG, "TheOneAudioEngine",
                                                                "Sequencer triggered sound for event %s (pad %s), sample %s, vel %d at tick %ld. Active sounds: %zu",
                                                                event.id.c_str(), padKey.c_str(), selectedLayer->sampleId.c_str(),
                                                                event.padTrigger.velocity, this->currentSequence_->currentPlayheadTicks,
                                                                this->activeSounds_.size()); // Size access might need lock or be approx.
                                        } else {
                                            __android_log_print(ANDROID_LOG_WARN, "TheOneAudioEngine",
                                                                "Sequencer: Sample '%s' for layer in pad '%s' not found or empty.",
                                                                selectedLayer->sampleId.c_str(), padKey.c_str());
                                        }
                                    } else {
                                        __android_log_print(ANDROID_LOG_WARN, "TheOneAudioEngine",
                                                            "Sequencer: No suitable (enabled with sampleId) layer found for pad '%s'.", padKey.c_str());
                                    }
                                } else {
                                    __android_log_print(ANDROID_LOG_WARN, "TheOneAudioEngine",
                                                        "Sequencer: PadSettings not found for key '%s'.", padKey.c_str());
                                }
                            } // end if PAD_TRIGGER
                        } // end if event.startTimeTicks == currentPlayheadTicks
                    } // end for events in track
                } // end for tracks in sequence
                // --- End Trigger Events ---
            }
        }
    }
    // --- End Sequencer Clock Processing ---

    // Placeholder for where complex audio rendering would occur, similar to the one in native-lib.cpp
    // This would involve iterating activeSounds_, processing metronome, sequencer events,
    // applying envelopes, LFOs, and mixing into outputBuffer.
    // All accesses to shared data (padSettingsMap_, sampleMap_, activeSounds_, etc.)
    // must be protected by their respective mutexes (padSettingsMutex_, sampleMapMutex_, etc.).

    // --- Active Sounds Processing ---
    { // Scope for activeSoundsMutex_
        std::lock_guard<std::mutex> activeSoundsLock(this->activeSoundsMutex_);
        uint32_t streamChannels = static_cast<uint32_t>(oboeStream->getChannelCount());

        for (auto soundIt = this->activeSounds_.begin(); soundIt != this->activeSounds_.end(); /* no increment here */) {
            theone::audio::PlayingSound &sound = *soundIt;

            if (!sound.isActive.load()) {
                ++soundIt; // Still need to advance iterator if just checking inactive ones prior to erase-remove
                continue;
            }

            const theone::audio::LoadedSample* loadedSample = sound.loadedSamplePtr;
            if (!loadedSample || loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
                sound.isActive.store(false);
                ++soundIt;
                continue;
            }

            uint32_t sampleChannels = loadedSample->format.channels;
            float ampEnvValue = 1.0f;
            float pitchEnvValue = 0.0f; // Typically additive, centered at 0
            float filterEnvValue = 1.0f; // Typically multiplicative for cutoff modulation

            // Envelope Processing
            if (sound.padSettings) { // Pad-triggered sound
                if (sound.ampEnvelopeGen) {
                    ampEnvValue = sound.ampEnvelopeGen->process();
                    if (sound.ampEnvelopeGen->getCurrentStage() == theone::audio::EnvelopeStage::IDLE && ampEnvValue < 0.001f) {
                        sound.isActive.store(false);
                    }
                }
                if (sound.padSettings->hasPitchEnvelope && sound.pitchEnvelopeGen) {
                    pitchEnvValue = sound.pitchEnvelopeGen->process();
                     // Check if pitch env is done, though it doesn't make sound inactive
                    if (sound.pitchEnvelopeGen->getCurrentStage() == theone::audio::EnvelopeStage::IDLE) {
                        // Pitch envelope finished, could reset or hold last value
                    }
                }
                if (sound.padSettings->hasFilterEnvelope && sound.filterEnvelopeGen) {
                    filterEnvValue = sound.filterEnvelopeGen->process();
                    // Check if filter env is done
                    if (sound.filterEnvelopeGen->getCurrentStage() == theone::audio::EnvelopeStage::IDLE) {
                        // Filter envelope finished
                    }
                }
            } else { // Non-pad sound (e.g., metronome if it were routed here, or other simple triggers)
                if (sound.ampEnvelopeGen) { // Basic amp envelope for non-pad sounds
                    ampEnvValue = sound.ampEnvelopeGen->process();
                    if (sound.ampEnvelopeGen->getCurrentStage() == theone::audio::EnvelopeStage::IDLE && ampEnvValue < 0.001f) {
                        sound.isActive.store(false);
                    }
                }
            }

            // LFO Processing
            float lfoVolumeMod = 1.0f;  // Multiplicative
            float lfoPanMod = 0.0f;     // Additive
            float lfoPitchMod = 0.0f;   // Additive (semitones or cents)
            float lfoFilterMod = 0.0f;  // Additive (filter cutoff units)

            if (sound.padSettings && !sound.lfoGens.empty()) {
                for (size_t i = 0; i < sound.lfoGens.size() && i < sound.padSettings->lfos.size(); ++i) {
                    if (!sound.lfoGens[i] || !sound.padSettings->lfos[i].isEnabled) continue;

                    float lfoValue = sound.lfoGens[i]->process(); // Output typically -1 to 1 or 0 to 1
                    const auto& lfoConfig = sound.padSettings->lfos[i];

                    // Apply LFO based on its primary destination
                    // Note: The 'depth' controls how much the LFO affects the parameter.
                    // A bipolar LFO (-1 to 1) with depth D would modulate Param by +/- D.
                    // A unipolar LFO (0 to 1) with depth D would modulate Param by 0 to D.
                    // Assuming lfoValue is bipolar (-1 to 1) for these calculations.
                    // If LFO is unipolar, it should be mapped to bipolar, or calculations adjusted.
                    // For simplicity, assume lfoValue is already appropriately scaled by LFO generator itself if needed.

                    switch (lfoConfig.primaryDestination) {
                        case LfoDestinationCpp::VOLUME:
                            // lfoValue is -1 to 1. Depth is 0 to 1.
                            // Modulates volume multiplicatively. Example: (1 + lfoValue * depth)
                            // To ensure volume doesn't go negative: (1.0f + lfoValue * lfoConfig.depth)
                            // Or more commonly, map LFO to a factor: e.g. 0.5 to 1.5
                            // lfoVolumeMod *= (1.0f + lfoValue * lfoConfig.depth); // This can be problematic if depth is high
                                                        // A safer way: make LFO apply to a range.
                                                        // If LFO is -1 to 1, depth is 0.5, then range is 1.0 +/- 0.5 => 0.5 to 1.5
                                                        // Or, if LFO is 0 to 1 (e.g. for tremolo), depth 0.5, then range is 1.0 - (0 to 0.5) => 0.5 to 1.0
                                                        // Let's assume lfoValue is -1 to 1, depth is 0-1.
                                                        // Max reduction/boost is depth. So effective_mod = 1 + lfoValue * depth.
                            lfoVolumeMod *= (1.0f + lfoValue * (lfoConfig.depth / 2.0f)); // Assuming depth is full range, so halve for +/-
                            break;
                        case LfoDestinationCpp::PAN:
                            lfoPanMod += lfoValue * lfoConfig.depth; // Pan is -1 to 1. Depth is 0 to 1 (pan width)
                            break;
                        case LfoDestinationCpp::PITCH:
                            // Pitch mod is often in semitones. Depth could be # of semitones.
                            lfoPitchMod += lfoValue * lfoConfig.depth;
                            break;
                        case LfoDestinationCpp::FILTER_CUTOFF:
                            // Filter mod is often additive to a base cutoff. Depth relative to filter range.
                            lfoFilterMod += lfoValue * lfoConfig.depth;
                            break;
                        default: // NONE or other unhandled
                            break;
                    }
                }
                // Clamp LFO modulations to sensible ranges
                lfoVolumeMod = std::max(0.0f, std::min(2.0f, lfoVolumeMod)); // e.g. 0x to 2x volume
                lfoPanMod = std::max(-1.0f, std::min(1.0f, lfoPanMod));     // Clamp total pan mod contribution
            }
            // (Pitch and Filter LFOs would be applied during sample generation/DSP if those were here)

            // --- A. Calculate Effective Pitch Modulation ---
            // Combine coarse and fine tuning. Fine tuning is in cents (1/100th of a semitone).
            float soundSpecificBaseTuneSemitones = static_cast<float>(sound.totalTuningCoarse_) + (static_cast<float>(sound.totalTuningFine_) / 100.0f);

            float pitchEnvSemitones = 0.0f;
            // Assuming pitchEnvValue is the raw value from the envelope generator (0 to 1.0)
            // And lfoPitchMod is already in semitones.
            if (sound.pitchEnvelopeGen && sound.padSettings && sound.padSettings->hasPitchEnvelope) {
                // pitchEnvValue was already processed earlier in the "Envelope Processing" section.
                float pitchEnvDepth = 24.0f; // Default, consider making this configurable per pad/layer if needed
                                             // Example: pitchEnvDepth = sound.padSettings->pitchEnvelope.depthSemitones; // Assumes field exists
                pitchEnvSemitones = pitchEnvDepth * (pitchEnvValue - 0.5f) * 2.0f; // Assumes pitchEnvValue is 0-1
            }

            float lfoPitchSemitones = lfoPitchMod; // Assuming lfoPitchMod is already correctly scaled (e.g. in semitones)

            // Total pitch shift includes sound's base tuning, envelope, and LFO
            float totalPitchShiftSemitones = soundSpecificBaseTuneSemitones + pitchEnvSemitones + lfoPitchSemitones;
            float currentPitchFactor = powf(2.0f, totalPitchShiftSemitones / 12.0f);
            currentPitchFactor = std::max(0.1f, std::min(4.0f, currentPitchFactor)); // Sanity clamp pitch factor

            // --- B. Calculate Effective Filter Cutoff (Filter Modulation) ---
            // Note: The main filter sample rate update (setSampleRate) is now done at sound creation.
            // We might still need to update it here if the engine's sample rate can change dynamically
            // *during* a sound's lifetime, which is less common for typical audio engine designs using Oboe.
            // For now, assuming it's stable for the duration of a playing sound.

            if (sound.filterL_ && sound.filterR_ && sound.padSettings && sound.padSettings->filterSettings.enabled) {
                uint32_t currentSr = this->audioStreamSampleRate_.load();
                if (currentSr == 0) currentSr = 48000; // Fallback

                // Optional: Re-check and set sample rate if it could change dynamically.
                // if (sound.filterL_->getSampleRate() != static_cast<float>(currentSr)) {
                //     sound.filterL_->setSampleRate(static_cast<float>(currentSr));
                // }
                // if (sound.filterR_->getSampleRate() != static_cast<float>(currentSr)) {
                //     sound.filterR_->setSampleRate(static_cast<float>(currentSr));
                // }

                float baseCutoffHz = sound.padSettings->filterSettings.cutoffHz;
                float baseResonance = sound.padSettings->filterSettings.resonance;
                float modulatedCutoffHz = baseCutoffHz;

                if (sound.filterEnvelopeGen && sound.padSettings->hasFilterEnvelope) {
                    // filterEnvValue was already processed in "Envelope Processing" section
                    float filterEnvDepthOctaves = sound.padSettings->filterSettings.envAmount;
                    modulatedCutoffHz *= powf(2.0f, filterEnvDepthOctaves * (filterEnvValue - 0.5f) * 2.0f);
                }

                float lfoModOctaves = lfoFilterMod; // lfoFilterMod is assumed to be an octave offset
                modulatedCutoffHz *= powf(2.0f, lfoModOctaves);

                float nyquistLimit = static_cast<float>(currentSr) / 2.0f;
                modulatedCutoffHz = std::max(20.0f, std::min(modulatedCutoffHz, nyquistLimit - 100.0f));
                if (modulatedCutoffHz < 20.0f) modulatedCutoffHz = 20.0f;

                sound.filterL_->configure(sound.padSettings->filterSettings.mode, modulatedCutoffHz, baseResonance);
                sound.filterR_->configure(sound.padSettings->filterSettings.mode, modulatedCutoffHz, baseResonance);
            }

            // Panning and Gain Calculation
            float currentPan = sound.initialPan + lfoPanMod;
            currentPan = std::max(-1.0f, std::min(1.0f, currentPan)); // Clamp final pan

            // Convert pan (-1 to 1) to radians for stereo gain calculation (constant power panning)
            // M_PI_4 is pi/4. Pan value from -1 to 1 maps to 0 to pi/2.
            // Pan = -1 (Left)  => angle = 0      => cos(0)=1, sin(0)=0
            // Pan =  0 (Center) => angle = pi/4   => cos(pi/4)=sin(pi/4)=sqrt(2)/2
            // Pan =  1 (Right) => angle = pi/2   => cos(pi/2)=0, sin(pi/2)=1
            float panRad = (currentPan * 0.5f + 0.5f) * (float)M_PI_2; // M_PI_2 is pi/2

            float overallGain = sound.initialVolume * ampEnvValue * lfoVolumeMod;
            overallGain = std::max(0.0f, overallGain); // Ensure gain is not negative

            float finalGainL = overallGain * cosf(panRad);
            float finalGainR = overallGain * sinf(panRad);

            // --- B. Frame-by-Frame Mixing Loop (with resampling) ---
            for (int frame = 0; frame < numFrames; ++frame) {
                if (!sound.isActive.load()) {
                    break; // Sound became inactive during this frame block
                }

                size_t effEndFrame = sound.useSlicing ? sound.endFrame : loadedSample->frameCount;
                if (effEndFrame == 0 && loadedSample->frameCount > 0) effEndFrame = loadedSample->frameCount;


                // Determine integer indices and fraction for interpolation
                size_t index0 = static_cast<size_t>(sound.fractionalFramePosition);
                float fraction = static_cast<float>(sound.fractionalFramePosition - index0);
                size_t index1 = index0 + 1;

                // Boundary checks for index0 and index1 against sample/slice boundaries
                if (index0 >= effEndFrame) {
                    if (sound.isLooping && sound.loopStartFrame < sound.loopEndFrame && sound.loopEndFrame <= effEndFrame) {
                        double overshoot = sound.fractionalFramePosition - static_cast<double>(effEndFrame);
                        sound.fractionalFramePosition = static_cast<double>(sound.loopStartFrame) + overshoot;
                        // Recalculate indices and fraction
                        index0 = static_cast<size_t>(sound.fractionalFramePosition);
                        fraction = static_cast<float>(sound.fractionalFramePosition - index0);
                        index1 = index0 + 1;
                        // Ensure new index0 is not immediately causing another loop or end
                        if (index0 >= effEndFrame) {
                             sound.isActive.store(false); // Stuck in a loop or bad loop parameters
                             break;
                        }
                    } else {
                        sound.isActive.store(false);
                        break;
                    }
                }

                // Ensure index1 is within bounds for interpolation.
                // If index0 is the last valid frame, index1 would be out of bounds.
                // In this case, we can use sample0 for sample1 (zero-order hold for the last fraction).
                if (index1 >= loadedSample->frameCount) { // Check against the absolute sample frameCount
                    index1 = index0; // Use sample0 for sample1 if index1 is out of bounds
                    if (index0 >= loadedSample->frameCount) { // Should not happen if effEndFrame is correct
                        sound.isActive.store(false);
                        break;
                    }
                }
                 // Further check if index0 itself has gone past actual sample data (e.g. bad loop parameters)
                if (index0 >= loadedSample->frameCount) {
                    sound.isActive.store(false);
                    break;
                }


                // Fetch samples
                float sample0_L = 0.0f, sample0_R = 0.0f;
                float sample1_L = 0.0f, sample1_R = 0.0f;

                if (sampleChannels == 1) { // Mono sample
                    sample0_L = loadedSample->audioData[index0];
                    sample1_L = loadedSample->audioData[index1];
                    if (streamChannels == 2) {
                        sample0_R = sample0_L;
                        sample1_R = sample1_L;
                    }
                } else if (sampleChannels == 2) { // Stereo sample
                    sample0_L = loadedSample->audioData[index0 * 2];
                    sample0_R = loadedSample->audioData[index0 * 2 + 1];
                    sample1_L = loadedSample->audioData[index1 * 2];
                    sample1_R = loadedSample->audioData[index1 * 2 + 1];
                }

                // Linear Interpolation
                float interpolatedSample_L = sample0_L * (1.0f - fraction) + sample1_L * fraction;
                float interpolatedSample_R = (streamChannels == 2) ? (sample0_R * (1.0f - fraction) + sample1_R * fraction) : 0.0f;

                // --- C. Apply Filter ---
                float processedSample_L = interpolatedSample_L;
                float processedSample_R = interpolatedSample_R;

                if (sound.filterL_ && sound.filterR_ && sound.padSettings && sound.padSettings->filterSettings.enabled) {
                    processedSample_L = sound.filterL_->process(interpolatedSample_L);
                    if (streamChannels == 2) {
                        processedSample_R = sound.filterR_->process(interpolatedSample_R);
                    } else if (streamChannels == 1 && sampleChannels == 2) {
                        // If output is mono but sample was stereo, the R channel of the sample was already
                        // mixed into interpolatedSample_L if sampleChannels == 1 logic was hit earlier,
                        // or interpolatedSample_R contains the R channel data.
                        // For mono output, we only care about processedSample_L.
                        // If stereo sample to mono output needs R channel filtered too before summing,
                        // that logic would be more complex and happen before this point or during mixing.
                        // Current structure implies interpolatedSample_L is the one to use for mono output.
                    }
                }

                // Mix into output buffer
                if (streamChannels == 1) { // Mono output
                    outputBuffer[frame] += processedSample_L * overallGain; // Panning ignored for mono output
                } else if (streamChannels == 2) { // Stereo output
                    outputBuffer[frame * streamChannels] += processedSample_L * finalGainL;
                    outputBuffer[frame * streamChannels + 1] += processedSample_R * finalGainR;
                }
                // else: Handle other channel counts if necessary

                // Advance fractional frame position
                sound.fractionalFramePosition += currentPitchFactor;
                sound.currentFrame = static_cast<size_t>(sound.fractionalFramePosition); // Keep legacy currentFrame somewhat in sync

            } // End of frame-by-frame mixing loop for one sound

            if (!sound.isActive.load()) {
                // Sound became inactive, mark for removal.
                // The actual removal happens after the main loop.
            }
            ++soundIt; // Advance iterator for the next sound
        } // End of activeSounds_ loop

        // Remove inactive sounds using erase-remove-if idiom
        this->activeSounds_.erase(
            std::remove_if(this->activeSounds_.begin(), this->activeSounds_.end(),
                           [](const theone::audio::PlayingSound& s) {
                               return !s.isActive.load();
                           }),
            this->activeSounds_.end());

    } // End of scope for activeSoundsMutex_
    // --- End Active Sounds Processing ---


    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine ErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine ErrorAfterClose: %s", oboe::convertToText(error));
    // May need to recreate stream or enter a fallback state
    oboeInitialized_.store(false);
    // Consider attempting to re-initialize or notify UI
}

bool AudioEngine::isOboeInitialized() const {
    return oboeInitialized_.load() && outStream_ && outStream_->getState() != oboe::StreamState::Closed;
}

float AudioEngine::getOboeReportedLatencyMillis() const {
    if (isOboeInitialized()) {
        oboe::ResultWithValue<double> latency = outStream_->calculateLatencyMillis();
        if (latency) {
            return static_cast<jfloat>(latency.value());
        }
    }
    return -1.0f;
}

// Add a write proc for file descriptors
template<typename T>
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite) {
    int fd = static_cast<int>(reinterpret_cast<intptr_t>(pUserData));
    ssize_t written = write(fd, pData, bytesToWrite);
    return written < 0 ? 0 : static_cast<size_t>(written);
}

// --- Placeholder/Simplified Implementations for other methods from AudioEngine.h ---
// These would need full implementations similar to what was in native-lib.cpp,
// but adapted to use class members and appropriate mutexes.

bool AudioEngine::loadSampleToMemory(const std::string& sampleId, const std::string& filePath, long offset, long length) {
    if (filePath.empty() || length <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::loadSampleToMemory - Invalid filePath or length");
        return false;
    }
    std::lock_guard<std::mutex> lock(sampleMapMutex_);
    if (sampleMap_.count(sampleId) > 0) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "AudioEngine::loadSampleToMemory - Sample '%s' already loaded.", sampleId.c_str());
        return true;
    }
    FILE* file = fopen(filePath.c_str(), "rb");
    if (!file) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::loadSampleToMemory - Failed to open file %s", filePath.c_str());
        return false;
    }
    if (fseek(file, offset, SEEK_SET) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::loadSampleToMemory - Failed to fseek to offset %ld", offset);
        fclose(file);
        return false;
    }
    drwav wav;
    if (!drwav_init_file(&wav, filePath.c_str(), nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "AudioEngine::loadSampleToMemory - Failed to init drwav for file %s", filePath.c_str());
        fclose(file);
        return false;
    }
    size_t totalFrames = static_cast<size_t>(wav.totalPCMFrameCount);
    size_t numChannels = wav.channels;
    std::vector<float> audioData(totalFrames * numChannels);
    drwav_uint64 framesRead = drwav_read_pcm_frames_f32(&wav, wav.totalPCMFrameCount, audioData.data());
    if (framesRead != wav.totalPCMFrameCount) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "AudioEngine::loadSampleToMemory - Only read %llu/%llu frames", (unsigned long long)framesRead, (unsigned long long)wav.totalPCMFrameCount);
    }
    drwav_uninit(&wav);
    fclose(file);
    LoadedSample sample;
    sample.id = sampleId;
    sample.format.channels = static_cast<uint16_t>(numChannels);
    sample.format.sampleRate = static_cast<uint32_t>(wav.sampleRate);
    sample.format.bitDepth = 32; // We use float
    sample.audioData = std::move(audioData);
    sample.frameCount = static_cast<size_t>(framesRead);
    sampleMap_[sampleId] = std::move(sample);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::loadSampleToMemory - Loaded sample '%s' (%llu frames, %zu channels)", sampleId.c_str(), (unsigned long long)framesRead, numChannels);
    return true;
}

bool AudioEngine::isSampleLoaded(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMapMutex_);
    return sampleMap_.count(sampleId) > 0;
}

void AudioEngine::unloadSample(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMapMutex_);
    sampleMap_.erase(sampleId);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::unloadSample for ID: %s", sampleId.c_str());
}

int AudioEngine::getSampleRate(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMapMutex_);
    auto it = sampleMap_.find(sampleId);
    if (it != sampleMap_.end()) {
        return it->second.format.sampleRate;
    }
    return 0;
}

bool AudioEngine::playPadSample(
    const std::string& noteInstanceId, const std::string& trackId, const std::string& padId,
    const std::string& sampleId,
    float velocity, float coarseTune, float fineTune, float pan, float volume,
    int playbackModeOrdinal, float ampEnvAttackMs, float ampEnvDecayMs,
    float ampEnvSustainLevel, float ampEnvReleaseMs
) {
    // Lookup sample
    const LoadedSample* samplePtr = nullptr;
    {
        std::lock_guard<std::mutex> lock(sampleMapMutex_);
        auto it = sampleMap_.find(sampleId);
        if (it == sampleMap_.end()) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "playPadSample: Sample '%s' not loaded.", sampleId.c_str());
            return false;
        }
        samplePtr = &it->second;
    }
    // Lookup pad settings
    std::shared_ptr<PadSettingsCpp> padSettingsPtr;
    {
        std::lock_guard<std::mutex> lock(padSettingsMutex_);
        std::string padKey = trackId + "_" + padId;
        auto it = padSettingsMap_.find(padKey);
        if (it != padSettingsMap_.end()) {
            padSettingsPtr = std::make_shared<PadSettingsCpp>(it->second);
        }
    }
    // Calculate effective volume and pan
    float effectiveVolume = std::max(0.0f, std::min(2.0f, volume * velocity));
    float effectivePan = std::max(-1.0f, std::min(1.0f, pan));
    // Create PlayingSound
    PlayingSound sound(samplePtr, noteInstanceId, effectiveVolume, effectivePan);
    sound.padSettings = padSettingsPtr;
    sound.isActive.store(true);
    // Set tuning
    sound.totalTuningCoarse_ = static_cast<int>(coarseTune);
    sound.totalTuningFine_ = static_cast<int>(fineTune);
    // Set envelope (simplified, real impl would use EnvelopeGenerator)
    // sound.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>(ampEnvAttackMs, ampEnvDecayMs, ampEnvSustainLevel, ampEnvReleaseMs);
    // Set playback mode
    sound.isLooping = (playbackModeOrdinal == static_cast<int>(PlaybackModeCpp::LOOP));
    // Add to active sounds
    {
        std::lock_guard<std::mutex> lock(activeSoundsMutex_);
        activeSounds_.push_back(std::move(sound));
    }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "playPadSample: Triggered sample '%s' on pad '%s' (noteInstanceId: %s)", sampleId.c_str(), padId.c_str(), noteInstanceId.c_str());
    return true;
}

void AudioEngine::setMetronomeState(bool isEnabled, float bpm, int timeSigNum, int timeSigDen,
                                  const std::string& primarySoundSampleId, const std::string& secondarySoundSampleId) {
    std::lock_guard<std::mutex> lock(metronomeStateMutex_);
    metronomeState_.enabled.store(isEnabled);
    metronomeState_.bpm.store(bpm);
    metronomeState_.timeSignatureNum.store(timeSigNum);
    metronomeState_.timeSignatureDen.store(timeSigDen);

    // Simplified sample lookup from main sampleMap_
    // In a full impl, metronome samples might be distinct or specially handled.
    {
        std::lock_guard<std::mutex> sampleLock(sampleMapMutex_);
        auto primaryIt = sampleMap_.find(primarySoundSampleId);
        metronomeState_.primaryBeatSample = (primaryIt != sampleMap_.end()) ? &(primaryIt->second) : nullptr;
        if (!secondarySoundSampleId.empty()) {
            auto secondaryIt = sampleMap_.find(secondarySoundSampleId);
            metronomeState_.secondaryBeatSample = (secondaryIt != sampleMap_.end()) ? &(secondaryIt->second) : nullptr;
        } else {
            metronomeState_.secondaryBeatSample = nullptr;
        }
    }
    metronomeState_.audioStreamSampleRate = audioStreamSampleRate_.load(); // Ensure it has the current rate
    metronomeState_.updateSchedulingParameters();
    if (metronomeState_.enabled.load()) {
        metronomeState_.samplesUntilNextBeat = 0; // Reset beat count for immediate start
        metronomeState_.currentBeatInBar = metronomeState_.timeSignatureNum.load(); // Start at the last beat to trigger first beat on next cycle
    }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::setMetronomeState updated.");
}

void AudioEngine::setMetronomeVolume(float volume) {
    std::lock_guard<std::mutex> lock(metronomeStateMutex_);
    metronomeState_.volume.store(std::max(0.0f, std::min(1.0f, volume)));
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::setMetronomeVolume updated.");
}

// --- Recording (Simplified Placeholders) ---
bool AudioEngine::startAudioRecording(JNIEnv* env, jobject context, const std::string& filePathUri, int sampleRate, int channels) {
    std::lock_guard<std::mutex> lock(recordingStateMutex_);
    if (isRecording_.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "startAudioRecording: Already recording.");
        return false;
    }
    // Open file for writing
    int fd = open(filePathUri.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to open file %s", filePathUri.c_str());
        return false;
    }
    recordingFileDescriptor_ = fd;
    // Setup dr_wav writer
    FILE* file = fdopen(fd, "wb");
    if (!file) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to fdopen for fd %d", fd);
        close(fd);
        recordingFileDescriptor_ = -1;
        return false;
    }
    drwav_data_format wavFormat;
    wavFormat.container = drwav_container_riff;
    wavFormat.format = DR_WAVE_FORMAT_IEEE_FLOAT;
    wavFormat.channels = static_cast<uint32_t>(channels);
    wavFormat.sampleRate = static_cast<uint32_t>(sampleRate);
    wavFormat.bitsPerSample = 32;
    if (!drwav_init_write_sequential_pcm_frames(&wavWriter_, &wavFormat, 0, drwav_write_proc_fd<void>, (void*)(intptr_t)fd, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to initialize drwav writer");
        close(fd);
        recordingFileDescriptor_ = -1;
        return false;
    }
    wavWriterInitialized_ = true;
    // Setup Oboe input stream
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(static_cast<oboe::ChannelCount>(channels))
           ->setSampleRate(sampleRate)
           ->setInputPreset(oboe::InputPreset::VoiceRecognition)
           ->setCallback(this);
    oboe::Result result = builder.openManagedStream(mInputStream_);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to open Oboe input stream: %s", oboe::convertToText(result));
        drwav_uninit(&wavWriter_);
        wavWriterInitialized_ = false;
        close(fd);
        recordingFileDescriptor_ = -1;
        return false;
    }
    result = mInputStream_->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to start Oboe input stream: %s", oboe::convertToText(result));
        mInputStream_->close();
        drwav_uninit(&wavWriter_);
        wavWriterInitialized_ = false;
        close(fd);
        recordingFileDescriptor_ = -1;
        return false;
    }
    isRecording_.store(true);
    peakRecordingLevel_.store(0.0f);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording started: %s", filePathUri.c_str());
    return true;
}

jobjectArray AudioEngine::stopAudioRecording(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(recordingStateMutex_);
    if (!isRecording_.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Not recording.");
        return nullptr;
    }
    isRecording_.store(false);
    if (mInputStream_) {
        mInputStream_->requestStop();
        mInputStream_->close();
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream stopped and closed.");
    }
    drwav_uint64 totalFramesWritten = 0;
    if (wavWriterInitialized_) {
        totalFramesWritten = wavWriter_.totalPCMFrameCount;
        drwav_uninit(&wavWriter_);
        wavWriterInitialized_ = false;
    }
    if (recordingFileDescriptor_ != -1) {
        close(recordingFileDescriptor_);
        recordingFileDescriptor_ = -1;
    }
    // Prepare return: [filePath, totalFrames]
    jclass stringClass = env->FindClass("java/lang/String");
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
    jobject jTotalFrames = env->NewObject(longClass, longConstructor, (jlong)totalFramesWritten);
    jobjectArray resultArray = env->NewObjectArray(2, stringClass, nullptr);
    // For now, filePath is not stored; you may want to keep it as a member if needed
    env->SetObjectArrayElement(resultArray, 0, env->NewStringUTF(""));
    env->SetObjectArrayElement(resultArray, 1, jTotalFrames);
    env->DeleteLocalRef(jTotalFrames);
    env->DeleteLocalRef(longClass);
    env->DeleteLocalRef(stringClass);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording stopped. Frames: %llu", (unsigned long long)totalFramesWritten);
    return resultArray;
}

bool AudioEngine::isRecordingActive() {
    return isRecording_.load();
}

float AudioEngine::getRecordingLevelPeak() {
    return peakRecordingLevel_.exchange(0.0f);
}

// Internal calculation without locking, assumes caller handles mutex
void AudioEngine::RecalculateTickDurationInternal() {
    if (currentSequence_ && currentSequence_->bpm > 0.0f && currentSequence_->ppqn > 0) {
        double msPerMinute = 60000.0;
        double beatsPerMinute = static_cast<double>(currentSequence_->bpm);
        double ppqn_double = static_cast<double>(currentSequence_->ppqn);
        double msPerBeat = msPerMinute / beatsPerMinute;
        currentTickDurationMs_ = msPerBeat / ppqn_double;
    } else {
        currentTickDurationMs_ = 0.0;
    }
}

// Public method that locks and then calls the internal calculation
void AudioEngine::RecalculateTickDuration() {
    std::lock_guard<std::mutex> lock(sequencerMutex_);
    RecalculateTickDurationInternal();
    __android_log_print(ANDROID_LOG_INFO, "TheOneAudioEngine",
                        "Recalculated tick duration (locked): %f ms (BPM: %f, PPQN: %ld)",
                        currentTickDurationMs_,
                        currentSequence_ ? currentSequence_->bpm : 0.0f,
                        currentSequence_ ? currentSequence_->ppqn : 0l);
}

void AudioEngine::loadSequenceData(const theone::audio::SequenceCpp& sequence) {
    std::lock_guard<std::mutex> lock(sequencerMutex_);

    currentSequence_ = std::make_unique<theone::audio::SequenceCpp>();

    currentSequence_->id = sequence.id;
    currentSequence_->name = sequence.name;
    currentSequence_->bpm = sequence.bpm;
    currentSequence_->timeSignatureNumerator = sequence.timeSignatureNumerator;
    currentSequence_->timeSignatureDenominator = sequence.timeSignatureDenominator;
    currentSequence_->barLength = sequence.barLength;
    currentSequence_->ppqn = sequence.ppqn;

    currentSequence_->tracks.clear();
    for (const auto& [trackId, trackData] : sequence.tracks) {
        theone::audio::SequenceTrackCpp newTrack;
        newTrack.id = trackData.id;
        newTrack.events.clear();
        for (const auto& eventData : trackData.events) {
            newTrack.events.push_back(eventData);
        }
        currentSequence_->tracks[trackId] = newTrack;
    }

    currentSequence_->currentPlayheadTicks = 0;
    currentSequence_->isPlaying = false;
    timeAccumulatedForTick_ = 0.0;

    RecalculateTickDurationInternal(); // Call internal version as lock is already held

    __android_log_print(ANDROID_LOG_INFO, "TheOneAudioEngine",
                        "Sequence loaded. ID: %s, Name: %s, BPM: %f, PPQN: %ld. Tracks: %zu. Playhead reset.",
                        currentSequence_->id.c_str(),
                        currentSequence_->name.c_str(),
                        currentSequence_->bpm,
                        currentSequence_->ppqn,
                        currentSequence_->tracks.size());
}

// NOTE: The following methods are stubs for future implementation.
// They log calls and ensure thread safety, but do not yet modify audio behavior.
void AudioEngine::setSampleEnvelope(const std::string& sampleId, const EnvelopeSettingsCpp& envelope) {
    std::lock_guard<std::mutex> lock(sampleMapMutex_);
    auto it = sampleMap_.find(sampleId);
    if (it != sampleMap_.end()) {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "setSampleEnvelope: Updated envelope for sample '%s' (stub)", sampleId.c_str());
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "setSampleEnvelope: Sample '%s' not found", sampleId.c_str());
    }
}
void AudioEngine::setSampleLFO(const std::string& sampleId, const LfoSettingsCpp& lfo) {
    // TODO: Implement LFO assignment logic
}

} // namespace audio
} // namespace theone
