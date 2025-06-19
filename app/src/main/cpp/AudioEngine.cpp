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

    float *outputBuffer = static_cast<float*>(audioData);
    int32_t channelCount = oboeStream->getChannelCount();
    
    // Clear the buffer first
    memset(outputBuffer, 0, sizeof(float) * numFrames * channelCount);
    
    // 🎵 MASTER VOLUME CONTROL
    float masterVolume = masterVolume_.load();
    if (masterVolume <= 0.0f) {
        return oboe::DataCallbackResult::Continue; // Silent when muted
    }
    
    // 🔥 SAMPLE PLAYBACK ENGINE 
    processSamplePlayback(outputBuffer, numFrames, channelCount);
    
    // 🎛️ TEST TONE GENERATOR (for initial testing)
    if (testToneEnabled_.load()) {
        generateTestTone(outputBuffer, numFrames, channelCount);
    }
      // 🥁 METRONOME (if enabled)
    processMetronome(outputBuffer, numFrames, channelCount);
    
    // �️ AVST PLUGIN PROCESSING
    processPlugins(outputBuffer, numFrames, channelCount);
    
    // �🎹 APPLY MASTER VOLUME & LIMITING
    applyMasterProcessing(outputBuffer, numFrames, channelCount, masterVolume);return oboe::DataCallbackResult::Continue;
}

// 🎵 EPIC SAMPLE PLAYBACK ENGINE
void AudioEngine::processSamplePlayback(float* outputBuffer, int32_t numFrames, int32_t channelCount) {
    std::lock_guard<std::mutex> activeSoundsLock(activeSoundsMutex_);
    
    for (auto it = activeSounds_.begin(); it != activeSounds_.end();) {
        ActiveSound& sound = *it;
        
        // Get sample data
        std::shared_ptr<SampleDataCpp> sampleData;
        {
            std::lock_guard<std::mutex> sampleLock(sampleMutex_);
            auto sampleIt = sampleMap_.find(sound.sampleKey);
            if (sampleIt == sampleMap_.end()) {
                it = activeSounds_.erase(it);
                continue;
            }
            sampleData = sampleIt->second;
        }
        
        bool soundFinished = false;
          // Process each frame
        for (int32_t frame = 0; frame < numFrames; ++frame) {
            // Check bounds based on frame count, not sample count
            size_t currentFrame = static_cast<size_t>(sound.currentSampleIndex);
            size_t totalFrames = sampleData->sampleCount / sampleData->channels;
            
            if (currentFrame >= totalFrames) {
                soundFinished = true;
                break;
            }
            
            // Get sample value with bounds checking
            float sampleValue = 0.0f;
            if (sampleData->channels == 1) {
                // Mono sample
                sampleValue = sampleData->samples[currentFrame];
            } else if (sampleData->channels == 2) {
                // Stereo sample - mix to mono for simplicity, or we could do proper stereo positioning
                size_t leftIndex = currentFrame * 2;
                size_t rightIndex = leftIndex + 1;
                if (rightIndex < sampleData->sampleCount) {
                    sampleValue = (sampleData->samples[leftIndex] + sampleData->samples[rightIndex]) * 0.5f;
                } else {
                    sampleValue = sampleData->samples[leftIndex];
                }
            }
              // Apply envelope
            float envelopeValue = sound.envelope.process();
            float finalSample = sampleValue * envelopeValue * sound.volume;
            
            // Mix to output (stereo)
            if (channelCount == 2) {
                float leftGain = (1.0f - std::max(0.0f, sound.pan)) * 0.707f; // -3dB pan law
                float rightGain = (1.0f + std::min(0.0f, sound.pan)) * 0.707f;
                
                outputBuffer[frame * 2] += finalSample * leftGain;
                outputBuffer[frame * 2 + 1] += finalSample * rightGain;
            } else {
                // Mono output
                outputBuffer[frame] += finalSample;
            }
            
            // Advance sample position
            sound.currentSampleIndex += sound.playbackSpeed;
            
            // Check if envelope finished
            if (!sound.envelope.isActive()) {
                soundFinished = true;
                break;
            }
        }
        
        if (soundFinished) {
            it = activeSounds_.erase(it);
        } else {
            ++it;
        }
    }
}

// 🎛️ TEST TONE GENERATOR
void AudioEngine::generateTestTone(float* outputBuffer, int32_t numFrames, int32_t channelCount) {
    static float phase = 0.0f;
    float frequency = 440.0f; // A4 note
    float amplitude = 0.1f;   // Gentle volume
    float phaseIncrement = 2.0f * M_PI * frequency / audioStreamSampleRate_.load();
    
    for (int32_t frame = 0; frame < numFrames; ++frame) {
        float sampleValue = sinf(phase) * amplitude;
        
        if (channelCount == 2) {
            outputBuffer[frame * 2] += sampleValue;     // Left
            outputBuffer[frame * 2 + 1] += sampleValue; // Right
        } else {
            outputBuffer[frame] += sampleValue;
        }
        
        phase += phaseIncrement;
        if (phase > 2.0f * M_PI) {
            phase -= 2.0f * M_PI;
        }
    }
}

// 🥁 METRONOME PROCESSOR
void AudioEngine::processMetronome(float* outputBuffer, int32_t numFrames, int32_t channelCount) {
    std::lock_guard<std::mutex> lock(metronomeStateMutex_);
    
    if (!metronomeState_.enabled.load()) return;
    
    // Generate metronome clicks based on current timing
    // This is a simplified version - full implementation would sync with sequencer
    static int clickCounter = 0;
    
    if (clickCounter <= 0) {
        // Generate click sound (short burst of high frequency)
        float clickAmplitude = 0.3f;
        float clickFreq = 800.0f;
        int clickDuration = audioStreamSampleRate_.load() * 0.01f; // 10ms click
        
        for (int32_t frame = 0; frame < std::min(numFrames, clickDuration); ++frame) {
            float clickValue = sinf(2.0f * M_PI * clickFreq * frame / audioStreamSampleRate_.load()) * clickAmplitude;
            clickValue *= (1.0f - (float)frame / clickDuration); // Fade out
            
            if (channelCount == 2) {
                outputBuffer[frame * 2] += clickValue;
                outputBuffer[frame * 2 + 1] += clickValue;
            } else {
                outputBuffer[frame] += clickValue;
            }
        }
        
        // Reset counter based on BPM (simplified)
        clickCounter = audioStreamSampleRate_.load() * 60 / 120; // 120 BPM
    }
    
    clickCounter -= numFrames;
}

// 🎹 MASTER PROCESSING & LIMITING
void AudioEngine::applyMasterProcessing(float* outputBuffer, int32_t numFrames, int32_t channelCount, float masterVolume) {
    for (int32_t frame = 0; frame < numFrames; ++frame) {
        for (int32_t channel = 0; channel < channelCount; ++channel) {
            int32_t index = frame * channelCount + channel;
            
            // Apply master volume
            outputBuffer[index] *= masterVolume;
            
            // Soft limiting to prevent clipping
            if (outputBuffer[index] > 0.95f) {
                outputBuffer[index] = 0.95f;
            } else if (outputBuffer[index] < -0.95f) {
                outputBuffer[index] = -0.95f;            }
        }
    }
}

void AudioEngine::setSampleEnvelope(const std::string& sampleId, const EnvelopeSettingsCpp& envelope) {
    // TODO: Implement envelope assignment logic
}

// 🔥 EPIC SAMPLE TRIGGERING IMPLEMENTATION
void AudioEngine::triggerSample(const std::string& sampleKey, float volume, float pan) {
    std::lock_guard<std::mutex> sampleLock(sampleMutex_);
    
    // Check if sample exists
    auto it = sampleMap_.find(sampleKey);
    if (it == sampleMap_.end()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "triggerSample: Sample not found: %s", sampleKey.c_str());
        return;
    }
    
    // Create new active sound
    ActiveSound newSound(sampleKey, volume, pan);
    
    // Add to active sounds
    {
        std::lock_guard<std::mutex> activeLock(activeSoundsMutex_);
        activeSounds_.emplace_back(std::move(newSound));
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎵 Sample triggered: %s (vol: %.2f, pan: %.2f)", 
                       sampleKey.c_str(), volume, pan);
}

void AudioEngine::stopAllSamples() {
    std::lock_guard<std::mutex> activeLock(activeSoundsMutex_);
    
    // Trigger release on all active sounds
    for (auto& sound : activeSounds_) {
        sound.envelope.triggerOff();
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🛑 All samples stopped");
}

void AudioEngine::loadTestSample(const std::string& sampleKey) {
    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        
        // Check if test sample is already loaded
        if (sampleMap_.find(sampleKey) != sampleMap_.end()) {
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Test sample %s already loaded", sampleKey.c_str());
            return;
        }
    }
    
    // Create test sample - a nice drum hit simulation
    uint32_t sampleRate = 44100;
    uint16_t channels = 1;
    size_t durationFrames = 22050; // 0.5 seconds
    size_t totalSamples = durationFrames * channels;
    
    std::vector<float> audioData(totalSamples);
    
    // Generate a punchy drum-like sound (kick drum simulation)
    for (size_t i = 0; i < durationFrames; ++i) {
        float t = static_cast<float>(i) / static_cast<float>(sampleRate);
        
        // Low frequency sine wave (kick fundamental)
        float kick = sinf(2.0f * M_PI * 60.0f * t) * expf(-t * 8.0f);
        
        // Click component (attack)
        float click = sinf(2.0f * M_PI * 300.0f * t) * expf(-t * 30.0f);
        
        // Combine and shape
        float sample = (kick * 0.8f + click * 0.3f) * 0.7f;
        
        // Apply a gentle limiter to prevent clipping
        if (sample > 0.95f) sample = 0.95f;
        if (sample < -0.95f) sample = -0.95f;
        
        audioData[i * channels] = sample;
    }
    
    // Create SampleDataCpp object
    auto sampleData = std::make_shared<SampleDataCpp>(
        sampleKey,
        std::move(audioData),
        totalSamples,
        sampleRate,
        channels
    );
    
    // Store in sample map
    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        sampleMap_[sampleKey] = sampleData;
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🥁 Test sample loaded: %s (%zu samples, %u Hz, %u channels)", 
                        sampleKey.c_str(), totalSamples, sampleRate, channels);
}

void AudioEngine::setSampleLFO(const std::string& sampleId, const LfoSettingsCpp& lfo) {
    // TODO: Implement LFO assignment logic
}

// 🎯 CONVENIENCE FUNCTIONS FOR TESTING

bool AudioEngine::createAndTriggerTestSample(const std::string& sampleKey, float volume, float pan) {
    // Load test sample if not already loaded
    loadTestSample(sampleKey);
    
    // Trigger the sample
    triggerSample(sampleKey, volume, pan);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🚀 Created and triggered test sample: %s", sampleKey.c_str());
    return true;
}

// --- Missing Function Implementations ---

void AudioEngine::setMetronomeState(bool isEnabled, float bpm, int timeSigNum, int timeSigDen,
                                   const std::string& primarySoundSampleId, const std::string& secondarySoundSampleId) {
    std::lock_guard<std::mutex> lock(metronomeStateMutex_);
    metronomeState_.enabled.store(isEnabled);
    metronomeState_.bpm.store(bpm);
    metronomeState_.timeSignatureNum.store(timeSigNum);
    metronomeState_.timeSignatureDen.store(timeSigDen);
    // TODO: Store sample IDs when implementing actual metronome playback
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome state set: enabled=%d, bpm=%f", isEnabled, bpm);
}

bool AudioEngine::loadSampleToMemory(const std::string& sampleId, const std::string& filePath, long offset, long length) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Loading sample: %s from %s (offset: %ld, length: %ld)", 
                        sampleId.c_str(), filePath.c_str(), offset, length);

    // Check if sample is already loaded
    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        if (sampleMap_.find(sampleId) != sampleMap_.end()) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample %s already loaded, skipping", sampleId.c_str());
            return true;
        }
    }

    // Load the WAV file using dr_wav
    drwav wav;
    if (!drwav_init_file(&wav, filePath.c_str(), nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open WAV file: %s", filePath.c_str());
        return false;
    }

    // Validate the WAV file
    if (wav.channels == 0 || wav.channels > 2) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Unsupported channel count: %u (must be 1 or 2)", wav.channels);
        drwav_uninit(&wav);
        return false;
    }

    if (wav.sampleRate == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid sample rate: %u", wav.sampleRate);
        drwav_uninit(&wav);
        return false;
    }

    // Calculate the actual frames to load (considering offset and length)
    uint64_t totalFrames = wav.totalPCMFrameCount;
    uint64_t startFrame = (offset > 0) ? static_cast<uint64_t>(offset) : 0;
    uint64_t framesToLoad = totalFrames;

    if (startFrame >= totalFrames) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Offset %ld exceeds total frames %llu", offset, totalFrames);
        drwav_uninit(&wav);
        return false;
    }

    if (length > 0) {
        uint64_t requestedLength = static_cast<uint64_t>(length);
        framesToLoad = std::min(requestedLength, totalFrames - startFrame);
    } else {
        framesToLoad = totalFrames - startFrame;
    }

    if (framesToLoad == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "No frames to load for sample %s", sampleId.c_str());
        drwav_uninit(&wav);
        return false;
    }

    // Seek to the start frame if offset is specified
    if (startFrame > 0) {
        if (!drwav_seek_to_pcm_frame(&wav, startFrame)) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to seek to frame %llu in %s", startFrame, filePath.c_str());
            drwav_uninit(&wav);
            return false;
        }
    }

    // Allocate buffer for audio data
    size_t totalSamples = static_cast<size_t>(framesToLoad * wav.channels);
    std::vector<float> audioData(totalSamples);

    // Read the audio data as float samples
    uint64_t samplesRead = drwav_read_pcm_frames_f32(&wav, framesToLoad, audioData.data());
    
    if (samplesRead != framesToLoad) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Expected to read %llu frames, but read %llu frames", 
                            framesToLoad, samplesRead);
        // Adjust the vector size to actual samples read
        audioData.resize(samplesRead * wav.channels);
        framesToLoad = samplesRead;
    }

    // Close the WAV file
    drwav_uninit(&wav);

    // Create SampleDataCpp object
    auto sampleData = std::make_shared<SampleDataCpp>(
        sampleId,
        std::move(audioData),
        static_cast<size_t>(framesToLoad * wav.channels), // Total samples
        wav.sampleRate,
        static_cast<uint16_t>(wav.channels)
    );

    // Store in sample map
    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        sampleMap_[sampleId] = sampleData;
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
                        "Successfully loaded sample %s: %llu frames, %u channels, %u Hz, %zu total samples", 
                        sampleId.c_str(), framesToLoad, wav.channels, wav.sampleRate, totalSamples);

    return true;
}

void AudioEngine::unloadSample(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMutex_);
    auto it = sampleMap_.find(sampleId);
    if (it != sampleMap_.end()) {
        sampleMap_.erase(it);
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample unloaded: %s", sampleId.c_str());
    }
}

bool AudioEngine::playPadSample(
    const std::string& noteInstanceId, const std::string& trackId, const std::string& padId,
    const std::string& sampleId,
    float velocity, float coarseTune, float fineTune, float pan, float volume,
    int playbackModeOrdinal, float ampEnvAttackMs, float ampEnvDecayMs,
    float ampEnvSustainLevel, float ampEnvReleaseMs) {
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "playPadSample called: %s", sampleId.c_str());
    // TODO: Implement actual pad sample playback
    return true;
}

bool AudioEngine::startAudioRecording(JNIEnv* env, jobject context, const std::string& filePathUri, int sampleRate, int channels) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "startAudioRecording called");
    // TODO: Implement audio recording
    return false;
}

float AudioEngine::getOboeReportedLatencyMillis() const {
    if (outStream_ && oboeInitialized_.load()) {
        auto latency = outStream_->calculateLatencyMillis();
        if (latency) {
            return static_cast<float>(latency.value());
        }
    }
    return 0.0f;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe error after close: %s", oboe::convertToText(error));
}

// --- Additional missing method implementations ---

bool AudioEngine::isOboeInitialized() const {
    return oboeInitialized_.load();
}

jobjectArray AudioEngine::stopAudioRecording(JNIEnv* env) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "stopAudioRecording called");
    isRecording_.store(false);
    // TODO: Return proper metadata array
    return nullptr;
}

bool AudioEngine::isRecordingActive() {
    return isRecording_.load();
}

float AudioEngine::getRecordingLevelPeak() {
    return peakRecordingLevel_.load();
}

bool AudioEngine::isSampleLoaded(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMutex_);
    return sampleMap_.find(sampleId) != sampleMap_.end();
}

int AudioEngine::getSampleRate(const std::string& sampleId) {
    std::lock_guard<std::mutex> lock(sampleMutex_);
    auto it = sampleMap_.find(sampleId);
    if (it != sampleMap_.end()) {
        return static_cast<int>(it->second->sampleRate);
    }
    return 0;
}

void AudioEngine::setMetronomeVolume(float volume) {
    std::lock_guard<std::mutex> lock(metronomeStateMutex_);
    metronomeState_.volume.store(volume);
}

int AudioEngine::getActiveSoundsCountForTest() const {
    std::lock_guard<std::mutex> lock(activeSoundsMutex_);
    return static_cast<int>(activeSounds_.size());
}

void AudioEngine::addPlayingSoundForTest(PlayingSound sound) {
    // This would need conversion from PlayingSound to ActiveSound
    // For now, just log the call
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "addPlayingSoundForTest called");
}

void AudioEngine::setAudioStreamSampleRateForTest(uint32_t sampleRate) {
    audioStreamSampleRate_.store(sampleRate);
}

void AudioEngine::loadSequenceData(const SequenceCpp& sequence) {
    std::lock_guard<std::mutex> lock(sequencerMutex_);
    currentSequence_ = std::make_unique<SequenceCpp>(sequence);
}

// RecalculateTickDuration functions
void AudioEngine::RecalculateTickDurationInternal() {
    if (currentSequence_ && currentSequence_->bpm > 0.0f && currentSequence_->ppqn > 0) {
        double beatsPerMinute = static_cast<double>(currentSequence_->bpm);
        double ticksPerBeat = static_cast<double>(currentSequence_->ppqn);
        currentTickDurationMs_ = (60.0 * 1000.0) / (beatsPerMinute * ticksPerBeat);
    } else {
        currentTickDurationMs_ = 0.0;
    }
}

void AudioEngine::RecalculateTickDuration() {
    std::lock_guard<std::mutex> lock(sequencerMutex_);
    RecalculateTickDurationInternal();
}

// 🎛️ ===== AVST PLUGIN SYSTEM IMPLEMENTATION ===== 🎛️

bool AudioEngine::loadPlugin(const std::string& pluginId, const std::string& pluginName) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    // Check if plugin is already loaded
    if (loadedPlugins_.find(pluginId) != loadedPlugins_.end()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Plugin %s already loaded", pluginId.c_str());
        return true;
    }
    
    std::unique_ptr<avst::IAvstPlugin> plugin;
    
    // For now, we only support SketchingSynth - later we can add a factory system
    if (pluginName == "SketchingSynth" || pluginId == "com.high.theone.sketchingsynth") {
        plugin = std::make_unique<avst::SketchingSynth>();
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Unknown plugin: %s", pluginName.c_str());
        return false;
    }
    
    // Initialize plugin with current audio config
    avst::AudioIOConfig config;
    config.sampleRate = static_cast<float>(audioStreamSampleRate_.load());
    config.maxBufferSize = 512; // Reasonable default
    config.inputChannelCount = 0; // Synth doesn't need input
    config.outputChannelCount = 2; // Stereo output
    
    if (!plugin->initialize(config)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to initialize plugin: %s", pluginId.c_str());
        return false;
    }
    
    loadedPlugins_[pluginId] = std::move(plugin);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎹 Plugin loaded: %s", pluginId.c_str());
    return true;
}

bool AudioEngine::unloadPlugin(const std::string& pluginId) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return false;
    }
    
    it->second->shutdown();
    loadedPlugins_.erase(it);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎹 Plugin unloaded: %s", pluginId.c_str());
    return true;
}

std::vector<std::string> AudioEngine::getLoadedPlugins() const {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    std::vector<std::string> pluginIds;
    for (const auto& pair : loadedPlugins_) {
        pluginIds.push_back(pair.first);
    }
    return pluginIds;
}

bool AudioEngine::setPluginParameter(const std::string& pluginId, const std::string& paramId, double value) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return false;
    }
    
    auto* param = it->second->getParameters().getParameter(paramId);
    if (!param) {
        return false;
    }
    
    param->setValue(value);
    return true;
}

double AudioEngine::getPluginParameter(const std::string& pluginId, const std::string& paramId) const {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return 0.0;
    }
    
    const auto* param = it->second->getParameters().getParameter(paramId);
    if (!param) {
        return 0.0;
    }
    
    return param->getValue();
}

std::vector<avst::ParameterInfo> AudioEngine::getPluginParameters(const std::string& pluginId) const {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return {};
    }
    
    return it->second->getParameters().getAllParameterInfo();
}

void AudioEngine::sendMidiToPlugin(const std::string& pluginId, uint8_t status, uint8_t data1, uint8_t data2) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return;
    }
    
    avst::MidiMessage message;
    message.status = status;
    message.data1 = data1;
    message.data2 = data2;
    message.sampleOffset = 0; // Immediate
    
    it->second->processMidiMessage(message);
}

void AudioEngine::noteOnToPlugin(const std::string& pluginId, uint8_t note, uint8_t velocity) {
    sendMidiToPlugin(pluginId, 0x90, note, velocity); // Note On, channel 0
}

void AudioEngine::noteOffToPlugin(const std::string& pluginId, uint8_t note, uint8_t velocity) {
    sendMidiToPlugin(pluginId, 0x80, note, velocity); // Note Off, channel 0
}

bool AudioEngine::savePluginPreset(const std::string& pluginId, const std::string& presetName, const std::string& filePath) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return false;
    }
    
    return it->second->savePreset(presetName, filePath);
}

bool AudioEngine::loadPluginPreset(const std::string& pluginId, const std::string& filePath) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return false;
    }
    
    return it->second->loadPreset(filePath);
}

std::vector<std::string> AudioEngine::getPluginPresets(const std::string& pluginId) const {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    auto it = loadedPlugins_.find(pluginId);
    if (it == loadedPlugins_.end()) {
        return {};
    }
    
    return it->second->getPresetList();
}

void AudioEngine::processPlugins(float* outputBuffer, int32_t numFrames, int32_t channelCount) {
    std::lock_guard<std::mutex> lock(pluginsMutex_);
    
    if (loadedPlugins_.empty()) {
        return; // No plugins to process
    }
    
    // Ensure our plugin buffers are the right size
    ensurePluginBuffersSize(numFrames, channelCount);
    
    // Process each loaded plugin
    for (auto& pair : loadedPlugins_) {
        const std::string& pluginId = pair.first;
        auto& plugin = pair.second;
        
        // Clear plugin output buffers
        for (int ch = 0; ch < channelCount; ++ch) {
            std::fill(pluginOutputBuffers_[ch].begin(), 
                     pluginOutputBuffers_[ch].begin() + numFrames, 0.0f);
        }
        
        // Set up process context
        avst::ProcessContext context;
        context.inputs = nullptr; // Synths don't need input
        context.numInputs = 0;
        
        // Setup output pointers
        std::vector<float*> outputPtrs(channelCount);
        for (int ch = 0; ch < channelCount; ++ch) {
            outputPtrs[ch] = pluginOutputBuffers_[ch].data();
        }
        context.outputs = outputPtrs.data();
        context.numOutputs = channelCount;
        context.frameCount = numFrames;
        
        context.sampleRate = static_cast<float>(audioStreamSampleRate_.load());
        context.tempo = 120.0; // TODO: Get from transport
        context.timePosition = 0.0; // TODO: Get from transport
        context.isPlaying = true; // TODO: Get from transport state
        
        // Process the plugin
        try {
            plugin->processAudio(context);
            
            // Mix plugin output into main output buffer
            for (int32_t frame = 0; frame < numFrames; ++frame) {
                for (int ch = 0; ch < channelCount; ++ch) {
                    outputBuffer[frame * channelCount + ch] += 
                        pluginOutputBuffers_[ch][frame] * 0.5f; // Mix at 50% for now
                }
            }
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, 
                               "Plugin %s processing error: %s", pluginId.c_str(), e.what());
        }
    }
}

void AudioEngine::ensurePluginBuffersSize(int32_t numFrames, int32_t channelCount) {
    // Resize buffer vectors if needed
    if (pluginInputBuffers_.size() != static_cast<size_t>(channelCount)) {
        pluginInputBuffers_.resize(channelCount);
    }
    if (pluginOutputBuffers_.size() != static_cast<size_t>(channelCount)) {
        pluginOutputBuffers_.resize(channelCount);
    }
    
    // Resize each channel buffer if needed
    for (int ch = 0; ch < channelCount; ++ch) {
        if (pluginInputBuffers_[ch].size() < static_cast<size_t>(numFrames)) {
            pluginInputBuffers_[ch].resize(numFrames);
        }
        if (pluginOutputBuffers_[ch].size() < static_cast<size_t>(numFrames)) {
            pluginOutputBuffers_[ch].resize(numFrames);
        }
    }
}

} // namespace audio
} // namespace theone