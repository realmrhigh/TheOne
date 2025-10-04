#include "AudioEngine.h"
#include <android/log.h>
#include <android/asset_manager.h> // For AAssetManager
#include <algorithm> // For std::max, std::min
#include <cmath>     // For M_PI, cosf, sinf, powf
#include <string.h>  // For memset in onAudioReady
#include <fcntl.h>   // For open
#include <unistd.h>  // For close
#include <cinttypes> // For PRIu64 format specifier
#include <errno.h>   // For strerror
#include <thread>    // For recording thread
#include <sys/statvfs.h> // For storage space checking
#include <chrono>    // For high-precision timing

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

// dr_wav file descriptor write callback
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite) {
    int* pFD = static_cast<int*>(pUserData);
    if (!pFD || *pFD == -1) {
        return 0;
    }
    
    ssize_t bytesWritten = write(*pFD, pData, bytesToWrite);
    if (bytesWritten < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "WAV write error: %s", strerror(errno));
        return 0;
    }
    
    return static_cast<size_t>(bytesWritten);
}

// dr_wav file descriptor seek callback
static drwav_bool32 drwav_seek_proc_fd(void* pUserData, int offset, drwav_seek_origin origin) {
    int* pFD = static_cast<int*>(pUserData);
    if (!pFD || *pFD == -1) {
        return DRWAV_FALSE;
    }
    
    int whence;
    switch (origin) {
        case drwav_seek_origin_start:   whence = SEEK_SET; break;
        case drwav_seek_origin_current: whence = SEEK_CUR; break;
        default: return DRWAV_FALSE;
    }
    
    off_t result = lseek(*pFD, offset, whence);
    return (result != -1) ? DRWAV_TRUE : DRWAV_FALSE;
}

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

void theone::audio::AudioEngine::shutdown() {
    if (isRecording_.load()) {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::shutdown - Stopping active recording.");
        
        // Signal recording thread to stop
        shouldStopRecording_.store(true);
        isRecording_.store(false);
        
        // Wait for recording thread to finish
        if (recordingThread_.joinable()) {
            recordingThread_.join();
        }
        
        // Stop input stream
        if (mInputStream_) {
            mInputStream_->requestStop();
            mInputStream_->close();
        }
        
        // Cleanup WAV writer
        {
            std::lock_guard<std::mutex> lock(recordingStateMutex_);
            if (wavWriterInitialized_) {
                drwav_uninit(&wavWriter_);
                wavWriterInitialized_ = false;
            }
            
            if (recordingFileDescriptor_ != -1) {
                close(recordingFileDescriptor_);
                recordingFileDescriptor_ = -1;
            }
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
    
    // 🎵 SEQUENCER TRIGGER PROCESSING
    processScheduledTriggers(numFrames);
    
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

void AudioEngine::triggerDrumPad(int padIndex, float velocity) {
    if (padIndex < 0 || padIndex >= 16) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid drum pad index: %d", padIndex);
        return;
    }
    
    // Create pad key
    std::string padKey = "pad_" + std::to_string(padIndex);
    
    // Get pad settings
    std::lock_guard<std::mutex> padLock(padSettingsMutex_);
    auto it = padSettingsMap_.find(padKey);
    if (it == padSettingsMap_.end() || it->second.layers.empty()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "No sample assigned to drum pad %d", padIndex);
        return;
    }
    
    const PadSettingsCpp& padSettings = it->second;
    
    // Get the first enabled layer's sample ID
    std::string sampleId;
    for (const auto& layer : padSettings.layers) {
        if (layer.enabled) {
            sampleId = layer.sampleId;
            break;
        }
    }
    
    if (sampleId.empty()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "No enabled sample layer for drum pad %d", padIndex);
        return;
    }
    
    // Trigger the sample with pad settings
    float finalVolume = velocity * padSettings.volume;
    triggerSample(sampleId, finalVolume, padSettings.pan);
    
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                       "🥁 Triggered drum pad %d: sample=%s, velocity=%f, volume=%f", 
                       padIndex, sampleId.c_str(), velocity, finalVolume);
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

    // Check if this is an asset:// path and redirect to asset loading
    if (filePath.find("asset://") == 0) {
        std::string assetPath = filePath.substr(8); // Remove "asset://" prefix
        return loadSampleFromAsset(sampleId, assetPath);
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
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Offset %ld exceeds total frames %" PRIu64, offset, totalFrames);
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
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to seek to frame %" PRIu64 " in %s", startFrame, filePath.c_str());
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
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Expected to read %" PRIu64 " frames, but read %" PRIu64 " frames",
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
                        "Successfully loaded sample %s: %" PRIu64 " frames, %u channels, %u Hz, %zu total samples",
                        sampleId.c_str(), (uint64_t)framesToLoad, wav.channels, wav.sampleRate, totalSamples);

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

bool AudioEngine::loadSampleFromAsset(const std::string& sampleId, const std::string& assetPath) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Loading sample from asset: %s from %s",
                        sampleId.c_str(), assetPath.c_str());

    // Check if asset manager is available
    if (!assetManager_) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Asset manager not set for loading asset: %s", assetPath.c_str());
        return false;
    }

    // Check if sample is already loaded
    {
        std::lock_guard<std::mutex> lock(sampleMutex_);
        if (sampleMap_.find(sampleId) != sampleMap_.end()) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample %s already loaded, skipping", sampleId.c_str());
            return true;
        }
    }

    // Open the asset
    AAsset* asset = AAssetManager_open(assetManager_, assetPath.c_str(), AASSET_MODE_UNKNOWN);
    if (!asset) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open asset: %s", assetPath.c_str());
        return false;
    }

    // Get asset size and read data
    off_t assetSize = AAsset_getLength(asset);
    if (assetSize <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid asset size for: %s", assetPath.c_str());
        AAsset_close(asset);
        return false;
    }

    // Read asset data into buffer
    std::vector<uint8_t> assetData(assetSize);
    int bytesRead = AAsset_read(asset, assetData.data(), assetSize);
    AAsset_close(asset);

    if (bytesRead != assetSize) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to read complete asset data: %s (read %d of %ld bytes)",
                            assetPath.c_str(), bytesRead, assetSize);
        return false;
    }

    // Initialize dr_wav with memory buffer
    drwav wav;
    if (!drwav_init_memory(&wav, assetData.data(), assetSize, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to initialize WAV from asset: %s", assetPath.c_str());
        return false;
    }

    // Calculate frames to load (same logic as file loading)
    uint64_t totalFrames = wav.totalPCMFrameCount;
    uint64_t framesToLoad = totalFrames;

    if (framesToLoad == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "No frames to load for asset sample %s", sampleId.c_str());
        drwav_uninit(&wav);
        return false;
    }

    // Allocate buffer for audio data
    size_t totalSamples = static_cast<size_t>(framesToLoad * wav.channels);
    std::vector<float> audioData(totalSamples);

    // Read the audio data as float samples
    uint64_t samplesRead = drwav_read_pcm_frames_f32(&wav, framesToLoad, audioData.data());

    if (samplesRead != framesToLoad) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Expected to read %" PRIu64 " frames, but read %" PRIu64 " frames",
                            framesToLoad, samplesRead);
        // Adjust the vector size to actual samples read
        audioData.resize(samplesRead * wav.channels);
        framesToLoad = samplesRead;
    }

    // Close the WAV
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
                        "Successfully loaded asset sample %s: %" PRIu64 " frames, %u channels, %u Hz, %zu total samples",
                        sampleId.c_str(), (uint64_t)framesToLoad, wav.channels, wav.sampleRate, totalSamples);

    return true;
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
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "startAudioRecording called with path: %s, sampleRate: %d, channels: %d", 
                        filePathUri.c_str(), sampleRate, channels);
    
    std::lock_guard<std::mutex> lock(recordingStateMutex_);
    
    // Check if already recording
    if (isRecording_.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Recording already in progress");
        return false;
    }
    
    // Validate parameters
    if (channels < 1 || channels > 2) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid channel count: %d (must be 1 or 2)", channels);
        return false;
    }
    
    if (sampleRate < 8000 || sampleRate > 192000) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid sample rate: %d", sampleRate);
        return false;
    }
    
    // Store recording parameters
    currentRecordingFilePath_ = filePathUri;
    
    // Initialize input stream with Oboe
    oboe::AudioStreamBuilder inputBuilder;
    inputBuilder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(channels)
                ->setSampleRate(sampleRate)
                ->setCallback(nullptr); // We'll use blocking read for recording
    
    oboe::Result result = inputBuilder.openManagedStream(mInputStream_);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open input stream: %s", oboe::convertToText(result));
        return false;
    }
    
    // Get actual stream parameters
    int32_t actualSampleRate = mInputStream_->getSampleRate();
    int32_t actualChannels = mInputStream_->getChannelCount();
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Input stream opened - Requested: %dHz %dch, Actual: %dHz %dch", 
                        sampleRate, channels, actualSampleRate, actualChannels);
    
    // Check available storage space (estimate 10MB minimum for recording)
    struct statvfs stat;
    if (statvfs(filePathUri.c_str(), &stat) == 0) {
        unsigned long long availableBytes = stat.f_bavail * stat.f_frsize;
        const unsigned long long minRequiredBytes = 10 * 1024 * 1024; // 10MB
        
        if (availableBytes < minRequiredBytes) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Insufficient storage space: %llu bytes available, %llu required", 
                                availableBytes, minRequiredBytes);
            mInputStream_->close();
            return false;
        }
        
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Storage check passed: %llu bytes available", availableBytes);
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Could not check storage space: %s", strerror(errno));
    }
    
    // Open file for writing (convert URI to file descriptor if needed)
    // For now, assume filePathUri is a direct file path
    recordingFileDescriptor_ = open(filePathUri.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (recordingFileDescriptor_ == -1) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open recording file: %s, error: %s", 
                            filePathUri.c_str(), strerror(errno));
        mInputStream_->close();
        return false;
    }
    
    // Verify file descriptor is valid and writable
    if (fcntl(recordingFileDescriptor_, F_GETFL) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid file descriptor: %s", strerror(errno));
        close(recordingFileDescriptor_);
        recordingFileDescriptor_ = -1;
        mInputStream_->close();
        return false;
    }
    
    // Initialize WAV writer with metadata
    drwav_data_format format;
    format.container = drwav_container_riff;
    format.format = DR_WAVE_FORMAT_IEEE_FLOAT;
    format.channels = actualChannels;
    format.sampleRate = actualSampleRate;
    format.bitsPerSample = 32; // 32-bit float
    
    if (!drwav_init_write_sequential(&wavWriter_, &format, 0, // totalSampleCount unknown
                                     drwav_write_proc_fd, &recordingFileDescriptor_, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to initialize WAV writer");
        close(recordingFileDescriptor_);
        recordingFileDescriptor_ = -1;
        mInputStream_->close();
        return false;
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "WAV writer initialized: %dHz, %d channels, 32-bit float", 
                        actualSampleRate, actualChannels);
    
    wavWriterInitialized_ = true;
    
    // Start the input stream
    result = mInputStream_->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to start input stream: %s", oboe::convertToText(result));
        drwav_uninit(&wavWriter_);
        wavWriterInitialized_ = false;
        close(recordingFileDescriptor_);
        recordingFileDescriptor_ = -1;
        mInputStream_->close();
        return false;
    }
    
    // Reset level monitoring and set recording flag
    peakRecordingLevel_.store(0.0f);
    rmsRecordingLevel_.store(0.0f);
    currentGain_.store(1.0f);
    shouldStopRecording_.store(false);
    isRecording_.store(true);
    
    // Start recording thread
    recordingThread_ = std::thread(&AudioEngine::recordingThreadFunction, this);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording started successfully");
    return true;
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
    
    if (!isRecording_.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "No recording in progress");
        return nullptr;
    }
    
    // Signal recording thread to stop
    shouldStopRecording_.store(true);
    isRecording_.store(false);
    
    // Wait for recording thread to finish
    if (recordingThread_.joinable()) {
        recordingThread_.join();
    }
    
    // Stop input stream and cleanup
    if (mInputStream_) {
        mInputStream_->requestStop();
        mInputStream_->close();
    }
    
    // Finalize WAV file and get metadata
    drwav_uint64 totalFramesWritten = 0;
    float durationSeconds = 0.0f;
    int32_t sampleRate = 44100;
    int32_t channels = 1;
    
    {
        std::lock_guard<std::mutex> lock(recordingStateMutex_);
        if (wavWriterInitialized_) {
            totalFramesWritten = wavWriter_.totalPCMFrameCount;
            sampleRate = wavWriter_.sampleRate;
            channels = wavWriter_.channels;
            durationSeconds = static_cast<float>(totalFramesWritten) / static_cast<float>(sampleRate);
            
            drwav_uninit(&wavWriter_);
            wavWriterInitialized_ = false;
        }
        
        if (recordingFileDescriptor_ != -1) {
            close(recordingFileDescriptor_);
            recordingFileDescriptor_ = -1;
        }
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Recording stopped. Duration: %.2fs, Frames: %llu, Sample Rate: %d, Channels: %d", 
                        durationSeconds, (unsigned long long)totalFramesWritten, sampleRate, channels);
    
    // Create metadata array to return to Kotlin
    // Format: [filePath, duration, sampleRate, channels, frameCount]
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray metadataArray = env->NewObjectArray(5, stringClass, nullptr);
    
    if (metadataArray) {
        // File path
        jstring jFilePath = env->NewStringUTF(currentRecordingFilePath_.c_str());
        env->SetObjectArrayElement(metadataArray, 0, jFilePath);
        env->DeleteLocalRef(jFilePath);
        
        // Duration
        jstring jDuration = env->NewStringUTF(std::to_string(durationSeconds).c_str());
        env->SetObjectArrayElement(metadataArray, 1, jDuration);
        env->DeleteLocalRef(jDuration);
        
        // Sample rate
        jstring jSampleRate = env->NewStringUTF(std::to_string(sampleRate).c_str());
        env->SetObjectArrayElement(metadataArray, 2, jSampleRate);
        env->DeleteLocalRef(jSampleRate);
        
        // Channels
        jstring jChannels = env->NewStringUTF(std::to_string(channels).c_str());
        env->SetObjectArrayElement(metadataArray, 3, jChannels);
        env->DeleteLocalRef(jChannels);
        
        // Frame count
        jstring jFrameCount = env->NewStringUTF(std::to_string(totalFramesWritten).c_str());
        env->SetObjectArrayElement(metadataArray, 4, jFrameCount);
        env->DeleteLocalRef(jFrameCount);
    }
    
    // Validate the recorded file
    if (totalFramesWritten > 0) {
        // Quick validation by trying to open the file with dr_wav
        drwav testWav;
        if (drwav_init_file(&testWav, currentRecordingFilePath_.c_str(), nullptr)) {
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Recording validation successful: %llu frames, %dHz, %d channels", 
                                (unsigned long long)testWav.totalPCMFrameCount, testWav.sampleRate, testWav.channels);
            drwav_uninit(&testWav);
        } else {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Recording validation failed - file may be corrupted");
        }
    }
    
    // Reset recording state
    currentRecordingFilePath_.clear();
    peakRecordingLevel_.store(0.0f);
    rmsRecordingLevel_.store(0.0f);
    currentGain_.store(1.0f);
    
    return metadataArray;
}

bool AudioEngine::isRecordingActive() {
    return isRecording_.load();
}

float AudioEngine::getRecordingLevelPeak() {
    return peakRecordingLevel_.load();
}

float AudioEngine::getRecordingLevelRMS() {
    return rmsRecordingLevel_.load();
}

void AudioEngine::setAutoGainControlEnabled(bool enabled) {
    autoGainControlEnabled_.store(enabled);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Auto Gain Control %s", enabled ? "enabled" : "disabled");
}

bool AudioEngine::isAutoGainControlEnabled() {
    return autoGainControlEnabled_.load();
}

void AudioEngine::setTargetRecordingLevel(float level) {
    float clampedLevel = std::max(0.1f, std::min(0.9f, level));
    targetRecordingLevel_.store(clampedLevel);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Target recording level set to %.2f", clampedLevel);
}

float AudioEngine::getTargetRecordingLevel() {
    return targetRecordingLevel_.load();
}

float AudioEngine::getCurrentRecordingGain() {
    return currentGain_.load();
}

void AudioEngine::recordingThreadFunction() {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Recording thread started");
    
    const int32_t bufferSizeFrames = 256; // Small buffer for low latency
    const int32_t channelCount = mInputStream_->getChannelCount();
    const int32_t bufferSizeSamples = bufferSizeFrames * channelCount;
    
    std::vector<float> audioBuffer(bufferSizeSamples);
    
    while (isRecording_.load() && !shouldStopRecording_.load()) {
        // Read audio data from input stream
        oboe::ResultWithValue<int32_t> result = mInputStream_->read(audioBuffer.data(), bufferSizeFrames, 0);
        
        if (result != oboe::Result::OK) {
            if (result.error() == oboe::Result::ErrorTimeout) {
                // Timeout is normal, continue
                continue;
            } else {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Recording read error: %s", oboe::convertToText(result.error()));
                break;
            }
        }
        
        int32_t framesRead = result.value();
        if (framesRead <= 0) {
            continue;
        }
        
        // Calculate peak and RMS levels for monitoring
        float currentPeak = 0.0f;
        float sumSquares = 0.0f;
        int32_t totalSamples = framesRead * channelCount;
        
        for (int32_t i = 0; i < totalSamples; ++i) {
            float sample = audioBuffer[i];
            float absSample = std::abs(sample);
            
            if (absSample > currentPeak) {
                currentPeak = absSample;
            }
            
            sumSquares += sample * sample;
        }
        
        float currentRMS = std::sqrt(sumSquares / totalSamples);
        
        // Apply automatic gain control if enabled
        if (autoGainControlEnabled_.load() && currentRMS > 0.001f) {
            float targetLevel = targetRecordingLevel_.load();
            float currentGain = currentGain_.load();
            
            // Calculate desired gain adjustment
            float desiredGain = targetLevel / currentRMS;
            
            // Smooth gain changes to avoid artifacts (attack/release)
            float gainSmoothingFactor = (desiredGain > currentGain) ? 0.01f : 0.05f; // Slower attack, faster release
            float newGain = currentGain + (desiredGain - currentGain) * gainSmoothingFactor;
            
            // Limit gain range to prevent extreme adjustments
            newGain = std::max(0.1f, std::min(10.0f, newGain));
            currentGain_.store(newGain);
            
            // Apply gain to audio buffer
            for (int32_t i = 0; i < totalSamples; ++i) {
                audioBuffer[i] *= newGain;
            }
            
            // Recalculate levels after gain adjustment
            currentPeak = 0.0f;
            sumSquares = 0.0f;
            for (int32_t i = 0; i < totalSamples; ++i) {
                float sample = audioBuffer[i];
                float absSample = std::abs(sample);
                
                if (absSample > currentPeak) {
                    currentPeak = absSample;
                }
                
                sumSquares += sample * sample;
            }
            currentRMS = std::sqrt(sumSquares / totalSamples);
        }
        
        // Update levels with smoothing for UI display
        float previousPeak = peakRecordingLevel_.load();
        float previousRMS = rmsRecordingLevel_.load();
        
        // Different smoothing factors for peak (faster) and RMS (slower)
        float peakSmoothingFactor = 0.3f;  // Faster response for peaks
        float rmsSmoothingFactor = 0.1f;   // Slower response for RMS
        
        float smoothedPeak = previousPeak * (1.0f - peakSmoothingFactor) + currentPeak * peakSmoothingFactor;
        float smoothedRMS = previousRMS * (1.0f - rmsSmoothingFactor) + currentRMS * rmsSmoothingFactor;
        
        peakRecordingLevel_.store(smoothedPeak);
        rmsRecordingLevel_.store(smoothedRMS);
        
        // Periodic logging for debugging (every ~1 second)
        static int logCounter = 0;
        if (++logCounter >= (mInputStream_->getSampleRate() / bufferSizeFrames)) {
            logCounter = 0;
            __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Recording levels - Peak: %.3f, RMS: %.3f, Gain: %.2f", 
                                smoothedPeak, smoothedRMS, currentGain_.load());
        }
        
        // Write to WAV file with error handling
        {
            std::lock_guard<std::mutex> lock(recordingStateMutex_);
            if (wavWriterInitialized_) {
                drwav_uint64 samplesWritten = drwav_write_pcm_frames(&wavWriter_, framesRead, audioBuffer.data());
                if (samplesWritten != static_cast<drwav_uint64>(framesRead)) {
                    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "WAV write error: expected %d frames, wrote %llu", 
                                        framesRead, (unsigned long long)samplesWritten);
                    
                    // Check if disk is full
                    struct statvfs stat;
                    if (statvfs(currentRecordingFilePath_.c_str(), &stat) == 0) {
                        unsigned long long availableBytes = stat.f_bavail * stat.f_frsize;
                        if (availableBytes < 1024 * 1024) { // Less than 1MB
                            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Storage space critically low: %llu bytes", availableBytes);
                            shouldStopRecording_.store(true);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Recording thread finished");
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
    config.maxBlockSize = 512; // Reasonable default
    config.currentInputChannels = 0; // Synth doesn't need input
    config.currentOutputChannels = 2; // Stereo output
    
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

// 🎵 SEQUENCER TRIGGER PROCESSING
void AudioEngine::processScheduledTriggers(int32_t numFrames) {
    if (scheduledTriggers_.empty()) {
        return;
    }
    
    // Get current time in microseconds
    int64_t currentTime = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    
    std::lock_guard<std::mutex> lock(scheduledTriggersMutex_);
    
    // Process triggers that are due
    for (auto it = scheduledTriggers_.begin(); it != scheduledTriggers_.end();) {
        if (!it->processed && it->timestamp <= currentTime) {
            // Trigger the pad
            triggerDrumPad(it->padIndex, it->velocity);
            
            // Mark as processed
            it->processed = true;
            
            // Update performance metrics
            {
                std::lock_guard<std::mutex> metricsLock(performanceMetricsMutex_);
                performanceMetrics_.totalTriggers++;
                
                int64_t latency = currentTime - it->timestamp;
                performanceMetrics_.totalLatency += latency;
                performanceMetrics_.maxLatency = std::max(performanceMetrics_.maxLatency, latency);
                performanceMetrics_.minLatency = std::min(performanceMetrics_.minLatency, latency);
            }
            
            __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                               "Triggered scheduled pad %d at timestamp %" PRId64 " (latency: %" PRId64 " μs)", 
                               it->padIndex, it->timestamp, currentTime - it->timestamp);
            
            // Remove processed trigger
            it = scheduledTriggers_.erase(it);
        } else if (it->timestamp < currentTime - 100000) { // 100ms old
            // Remove very old unprocessed triggers (missed)
            {
                std::lock_guard<std::mutex> metricsLock(performanceMetricsMutex_);
                performanceMetrics_.missedTriggers++;
            }
            
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, 
                               "Missed trigger for pad %d (timestamp %" PRId64 " vs current %" PRId64 ")", 
                               it->padIndex, it->timestamp, currentTime);
            
            it = scheduledTriggers_.erase(it);
        } else {
            ++it;
        }
    }
}

// 🎵 SEQUENCER INTEGRATION IMPLEMENTATION

bool AudioEngine::scheduleStepTrigger(int padIndex, float velocity, int64_t timestamp) {
    if (padIndex < 0 || padIndex >= 16) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid pad index: %d", padIndex);
        return false;
    }
    
    if (velocity < 0.0f || velocity > 1.0f) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Invalid velocity: %f", velocity);
        return false;
    }
    
    std::lock_guard<std::mutex> lock(scheduledTriggersMutex_);
    
    // Add the trigger to the scheduled list
    scheduledTriggers_.emplace_back(padIndex, velocity, timestamp);
    
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                       "Scheduled trigger: pad=%d, velocity=%f, timestamp=%" PRId64, 
                       padIndex, velocity, timestamp);
    
    return true;
}

void AudioEngine::setSequencerTempo(float bpm) {
    if (bpm < 60.0f || bpm > 200.0f) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Tempo out of range: %f BPM", bpm);
        bpm = std::max(60.0f, std::min(200.0f, bpm));
    }
    
    sequencerTempo_.store(bpm);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sequencer tempo set to %f BPM", bpm);
}

int64_t AudioEngine::getAudioLatencyMicros() const {
    if (outStream_) {
        // Get latency from Oboe and convert to microseconds
        auto latencyResult = outStream_->calculateLatencyMillis();
        if (latencyResult) {
            int64_t latencyMicros = static_cast<int64_t>(latencyResult.value() * 1000.0);
            return latencyMicros;
        }
    }
    
    // Return cached value if Oboe query fails
    return audioLatencyMicros_.load();
}

void AudioEngine::setHighPrecisionMode(bool enabled) {
    highPrecisionMode_.store(enabled);
    
    if (enabled) {
        // Enable optimizations for high precision timing
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "High precision mode enabled");
    } else {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "High precision mode disabled");
    }
}

bool AudioEngine::preloadSequencerSamples(const int* padIndices, int count) {
    if (!padIndices || count <= 0) {
        return false;
    }
    
    int successCount = 0;
    
    for (int i = 0; i < count; ++i) {
        int padIndex = padIndices[i];
        if (padIndex >= 0 && padIndex < 16) {
            // Check if pad has a sample assigned
            std::string padKey = "pad_" + std::to_string(padIndex);
            
            std::lock_guard<std::mutex> lock(padSettingsMutex_);
            auto it = padSettingsMap_.find(padKey);
            if (it != padSettingsMap_.end() && !it->second.layers.empty() && !it->second.layers[0].sampleId.empty()) {
                // Sample is already loaded if it's in the pad settings
                successCount++;
                __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                                   "Pad %d sample preloaded: %s", padIndex, it->second.layers[0].sampleId.c_str());
            }
        }
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
                       "Preloaded %d/%d sequencer samples", successCount, count);
    
    return successCount == count;
}

void AudioEngine::clearScheduledEvents() {
    std::lock_guard<std::mutex> lock(scheduledTriggersMutex_);
    scheduledTriggers_.clear();
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Cleared all scheduled events");
}

std::map<std::string, double> AudioEngine::getTimingStatistics() const {
    std::map<std::string, double> stats;
    
    std::lock_guard<std::mutex> lock(performanceMetricsMutex_);
    
    stats["totalTriggers"] = static_cast<double>(performanceMetrics_.totalTriggers);
    stats["missedTriggers"] = static_cast<double>(performanceMetrics_.missedTriggers);
    stats["scheduledTriggers"] = static_cast<double>(scheduledTriggers_.size());
    
    if (performanceMetrics_.totalTriggers > 0) {
        stats["averageLatency"] = static_cast<double>(performanceMetrics_.totalLatency) / 
                                 static_cast<double>(performanceMetrics_.totalTriggers);
    } else {
        stats["averageLatency"] = 0.0;
    }
    
    stats["maxLatency"] = static_cast<double>(performanceMetrics_.maxLatency);
    stats["minLatency"] = performanceMetrics_.minLatency == INT64_MAX ? 0.0 : 
                         static_cast<double>(performanceMetrics_.minLatency);
    stats["bufferUnderruns"] = static_cast<double>(performanceMetrics_.bufferUnderruns);
    stats["jitter"] = stats["maxLatency"] - stats["minLatency"];
    
    // Add system info
    stats["cpuUsage"] = 0.0; // TODO: Implement CPU monitoring
    stats["memoryUsage"] = 0.0; // TODO: Implement memory monitoring
    stats["isRealTimeMode"] = highPrecisionMode_.load() ? 1.0 : 0.0;
    
    return stats;
}

} // namespace audio
} // namespace theone

// MIDI PROCESSING IMPLEMENTATION

void theone::audio::AudioEngine::processMidiMessage(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2, int64_t timestamp) {
    auto startTime = std::chrono::high_resolution_clock::now();
    
    try {
        // Apply input latency compensation
        int64_t compensatedTimestamp = timestamp + midiInputLatencyMicros_.load();
        
        // Determine if this should be processed immediately or scheduled
        auto currentTime = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::high_resolution_clock::now().time_since_epoch()).count();
        
        if (compensatedTimestamp <= currentTime + 1000) { // Within 1ms, process immediately
            processMidiMessageImmediate(type, channel, data1, data2);
        } else {
            // Schedule for later processing
            scheduleMidiEvent(type, channel, data1, data2, compensatedTimestamp);
        }
        
        // Update statistics
        {
            std::lock_guard<std::mutex> lock(midiStatsMutex_);
            midiStats_.messagesProcessed++;
            
            auto endTime = std::chrono::high_resolution_clock::now();
            auto processingTime = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime).count();
            midiStats_.totalProcessingTime += processingTime;
            midiStats_.maxProcessingTime = std::max(midiStats_.maxProcessingTime, static_cast<int64_t>(processingTime));
        }
        
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "MIDI processing error: %s", e.what());
        std::lock_guard<std::mutex> lock(midiStatsMutex_);
        midiStats_.eventsDropped++;
    }
}

void theone::audio::AudioEngine::processMidiMessageImmediate(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2) {
    switch (type & 0xF0) {
        case 0x90: // Note On
            if (data2 > 0) { // Velocity > 0 means note on
                handleMidiNoteOn(channel, data1, data2);
            } else { // Velocity = 0 means note off
                handleMidiNoteOff(channel, data1, data2);
            }
            break;
            
        case 0x80: // Note Off
            handleMidiNoteOff(channel, data1, data2);
            break;
            
        case 0xB0: // Control Change
            handleMidiControlChange(channel, data1, data2);
            break;
            
        case 0xF8: // MIDI Clock
            if (midiClockSyncEnabled_.load()) {
                processMidiClockPulse(std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::high_resolution_clock::now().time_since_epoch()).count(), 
                    externalClockBpm_.load());
            }
            break;
            
        case 0xFA: // MIDI Start
            handleMidiTransport(0);
            break;
            
        case 0xFC: // MIDI Stop
            handleMidiTransport(1);
            break;
            
        case 0xFB: // MIDI Continue
            handleMidiTransport(2);
            break;
            
        default:
            // Ignore other message types
            break;
    }
}

void theone::audio::AudioEngine::handleMidiNoteOn(uint8_t channel, uint8_t note, uint8_t velocity) {
    // Look up pad mapping
    uint16_t mappingKey = (static_cast<uint16_t>(note) << 4) | channel;
    
    std::lock_guard<std::mutex> lock(midiNoteMappingsMutex_);
    auto it = midiNoteMappings_.find(mappingKey);
    if (it != midiNoteMappings_.end()) {
        int padIndex = it->second;
        
        // Apply velocity curve
        float processedVelocity = applyMidiVelocityCurve(velocity);
        
        // Trigger the drum pad
        triggerDrumPad(padIndex, processedVelocity);
        
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
            "MIDI Note On: note=%d, channel=%d, velocity=%d -> pad=%d, processedVel=%.3f", 
            note, channel, velocity, padIndex, processedVelocity);
    }
}

void theone::audio::AudioEngine::handleMidiNoteOff(uint8_t channel, uint8_t note, uint8_t velocity) {
    // Look up pad mapping
    uint16_t mappingKey = (static_cast<uint16_t>(note) << 4) | channel;
    
    std::lock_guard<std::mutex> lock(midiNoteMappingsMutex_);
    auto it = midiNoteMappings_.find(mappingKey);
    if (it != midiNoteMappings_.end()) {
        int padIndex = it->second;
        
        // Release the drum pad (if it supports note-off)
        // For now, we don't implement note-off for drum pads as they're typically one-shot
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
            "MIDI Note Off: note=%d, channel=%d -> pad=%d", 
            note, channel, padIndex);
    }
}

void theone::audio::AudioEngine::handleMidiControlChange(uint8_t channel, uint8_t controller, uint8_t value) {
    float normalizedValue = value / 127.0f;
    
    switch (controller) {
        case 7: // Volume
            setMasterVolume(normalizedValue);
            break;
            
        case 10: // Pan
            // Pan control - could be implemented for master pan if supported
            break;
            
        case 1: // Modulation wheel - could control effects
            break;
            
        default:
            // Handle other controllers as needed
            break;
    }
    
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
        "MIDI CC: channel=%d, controller=%d, value=%d (%.3f)", 
        channel, controller, value, normalizedValue);
}

void theone::audio::AudioEngine::scheduleMidiEvent(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2, int64_t timestamp) {
    std::lock_guard<std::mutex> lock(midiEventQueueMutex_);
    
    // Add to event queue
    midiEventQueue_.emplace_back(type, channel, data1, data2, timestamp);
    
    // Keep queue sorted by timestamp (insertion sort for small additions)
    auto it = midiEventQueue_.end() - 1;
    while (it != midiEventQueue_.begin() && (it-1)->timestamp > it->timestamp) {
        std::swap(*it, *(it-1));
        --it;
    }
    
    // Limit queue size to prevent memory issues
    if (midiEventQueue_.size() > 1000) {
        midiEventQueue_.erase(midiEventQueue_.begin());
        std::lock_guard<std::mutex> statsLock(midiStatsMutex_);
        midiStats_.eventsDropped++;
    } else {
        std::lock_guard<std::mutex> statsLock(midiStatsMutex_);
        midiStats_.eventsScheduled++;
    }
}

void theone::audio::AudioEngine::processScheduledMidiEvents() {
    auto currentTime = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::high_resolution_clock::now().time_since_epoch()).count();
    
    std::lock_guard<std::mutex> lock(midiEventQueueMutex_);
    
    // Process events that are due
    for (auto& event : midiEventQueue_) {
        if (!event.processed && event.timestamp <= currentTime) {
            processMidiMessageImmediate(event.type, event.channel, event.data1, event.data2);
            event.processed = true;
        }
    }
    
    // Remove processed events
    midiEventQueue_.erase(
        std::remove_if(midiEventQueue_.begin(), midiEventQueue_.end(),
            [](const MidiEvent& event) { return event.processed; }),
        midiEventQueue_.end());
}

void theone::audio::AudioEngine::setMidiNoteMapping(uint8_t midiNote, uint8_t midiChannel, int padIndex) {
    if (midiNote > 127 || midiChannel > 15 || padIndex < 0 || padIndex > 15) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, 
            "Invalid MIDI mapping parameters: note=%d, channel=%d, pad=%d", 
            midiNote, midiChannel, padIndex);
        return;
    }
    
    uint16_t mappingKey = (static_cast<uint16_t>(midiNote) << 4) | midiChannel;
    
    std::lock_guard<std::mutex> lock(midiNoteMappingsMutex_);
    midiNoteMappings_[mappingKey] = padIndex;
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "MIDI mapping set: note=%d, channel=%d -> pad=%d", 
        midiNote, midiChannel, padIndex);
}

void theone::audio::AudioEngine::removeMidiNoteMapping(uint8_t midiNote, uint8_t midiChannel) {
    if (midiNote > 127 || midiChannel > 15) {
        return;
    }
    
    uint16_t mappingKey = (static_cast<uint16_t>(midiNote) << 4) | midiChannel;
    
    std::lock_guard<std::mutex> lock(midiNoteMappingsMutex_);
    midiNoteMappings_.erase(mappingKey);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "MIDI mapping removed: note=%d, channel=%d", 
        midiNote, midiChannel);
}

void theone::audio::AudioEngine::setMidiVelocityCurve(int curveType, float sensitivity) {
    if (curveType < 0 || curveType > 3 || sensitivity <= 0.0f || sensitivity > 2.0f) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, 
            "Invalid velocity curve parameters: type=%d, sensitivity=%.3f", 
            curveType, sensitivity);
        return;
    }
    
    midiVelocityCurveType_.store(curveType);
    midiVelocitySensitivity_.store(sensitivity);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "MIDI velocity curve set: type=%d, sensitivity=%.3f", 
        curveType, sensitivity);
}

float theone::audio::AudioEngine::applyMidiVelocityCurve(uint8_t velocity) {
    if (velocity == 0) return 0.0f;
    if (velocity >= 127) return 1.0f;
    
    float normalizedVel = velocity / 127.0f;
    float sensitivity = midiVelocitySensitivity_.load();
    int curveType = midiVelocityCurveType_.load();
    
    float result;
    switch (curveType) {
        case 1: // Exponential
            result = std::pow(normalizedVel, 2.0f / sensitivity);
            break;
            
        case 2: // Logarithmic
            result = std::log(1.0f + normalizedVel * (std::exp(sensitivity) - 1.0f)) / sensitivity;
            break;
            
        case 3: // S-Curve
            {
                float x = normalizedVel * 2.0f - 1.0f; // Map to -1..1
                float s = sensitivity;
                result = 0.5f + 0.5f * x / (1.0f + s * std::abs(x));
            }
            break;
            
        default: // Linear
            result = normalizedVel * sensitivity;
            break;
    }
    
    return std::max(0.0f, std::min(1.0f, result));
}

void theone::audio::AudioEngine::setMidiClockSyncEnabled(bool enabled) {
    midiClockSyncEnabled_.store(enabled);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "MIDI clock sync %s", enabled ? "enabled" : "disabled");
}





void theone::audio::AudioEngine::setMidiInputLatency(int64_t latencyMicros) {
    midiInputLatencyMicros_.store(latencyMicros);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "MIDI input latency set to %" PRId64 " microseconds", latencyMicros);
}

std::map<std::string, int64_t> theone::audio::AudioEngine::getMidiStatistics() const {
    std::lock_guard<std::mutex> lock(midiStatsMutex_);
    
    std::map<std::string, int64_t> stats;
    stats["messagesProcessed"] = midiStats_.messagesProcessed;
    stats["eventsScheduled"] = midiStats_.eventsScheduled;
    stats["eventsDropped"] = midiStats_.eventsDropped;
    stats["clockPulsesReceived"] = midiStats_.clockPulsesReceived;
    stats["totalProcessingTime"] = midiStats_.totalProcessingTime;
    stats["maxProcessingTime"] = midiStats_.maxProcessingTime;
    
    if (midiStats_.messagesProcessed > 0) {
        stats["averageProcessingTime"] = midiStats_.totalProcessingTime / midiStats_.messagesProcessed;
    } else {
        stats["averageProcessingTime"] = 0;
    }
    
    return stats;
}

// Initialize default MIDI note mappings in constructor
void theone::audio::AudioEngine::initializeDefaultMidiMappings() {
    std::lock_guard<std::mutex> lock(midiNoteMappingsMutex_);
    
    // Map MIDI notes 60-75 (C4-D#5) to pads 0-15 on channel 0
    for (int i = 0; i < 16; ++i) {
        uint8_t midiNote = 60 + i;
        uint8_t midiChannel = 0;
        uint16_t mappingKey = (static_cast<uint16_t>(midiNote) << 4) | midiChannel;
        midiNoteMappings_[mappingKey] = i;
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "Default MIDI mappings initialized: C4-D#5 -> pads 0-15");
}

// MIDI CLOCK SYNCHRONIZATION IMPLEMENTATION

void theone::audio::AudioEngine::processMidiClockPulse(int64_t timestamp, float bpm) {
    if (!midiClockSyncEnabled_.load()) return;
    
    // Update clock timing analysis
    updateClockTiming(timestamp);
    
    // If we have a stable external clock, use it
    if (useExternalClock_.load() && isClockTimingStable()) {
        std::lock_guard<std::mutex> lock(clockTimingMutex_);
        float detectedBpm = clockTiming_.detectedBpm;
        
        // Update sequencer tempo with smoothed BPM
        setSequencerTempo(detectedBpm);
        externalClockBpm_.store(detectedBpm);
        
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
            "MIDI Clock: BPM=%.2f, interval=%" PRId64 "μs, stable=%s", 
            detectedBpm, clockTiming_.clockInterval, 
            clockTiming_.isStable ? "yes" : "no");
    }
    
    std::lock_guard<std::mutex> lock(midiStatsMutex_);
    midiStats_.clockPulsesReceived++;
}

void theone::audio::AudioEngine::updateClockTiming(int64_t timestamp) {
    std::lock_guard<std::mutex> lock(clockTimingMutex_);
    
    if (clockTiming_.lastClockTime == 0) {
        // First clock pulse
        clockTiming_.lastClockTime = timestamp;
        clockTiming_.clockPulseCount = 1;
        return;
    }
    
    // Calculate interval since last clock pulse
    int64_t interval = timestamp - clockTiming_.lastClockTime;
    clockTiming_.lastClockTime = timestamp;
    clockTiming_.clockPulseCount++;
    
    // Ignore unrealistic intervals (< 1ms or > 2s)
    if (interval < 1000 || interval > 2000000) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, 
            "MIDI Clock: Ignoring unrealistic interval %" PRId64 "μs", interval);
        return;
    }
    
    // Store recent intervals for stability analysis
    clockTiming_.recentIntervals.push_back(interval);
    if (clockTiming_.recentIntervals.size() > 24) {
        clockTiming_.recentIntervals.erase(clockTiming_.recentIntervals.begin());
    }
    
    // Calculate BPM from interval
    float newBpm = calculateBpmFromInterval(interval);
    
    // Apply smoothing
    smoothClockTempo(newBpm);
    
    // Update stability
    clockTiming_.isStable = isClockTimingStable();
    clockTiming_.clockInterval = interval;
}

float theone::audio::AudioEngine::calculateBpmFromInterval(int64_t interval) {
    // MIDI clock sends 24 pulses per quarter note
    // BPM = (60 * 1000000) / (interval * 24)
    // where interval is in microseconds
    
    if (interval <= 0) return 120.0f;
    
    float bpm = (60.0f * 1000000.0f) / (static_cast<float>(interval) * 24.0f);
    
    // Clamp to reasonable BPM range
    return std::max(60.0f, std::min(200.0f, bpm));
}

void theone::audio::AudioEngine::smoothClockTempo(float newBpm) {
    float smoothingFactor = clockSmoothingFactor_.load();
    
    if (clockTiming_.detectedBpm == 0.0f) {
        // First BPM measurement
        clockTiming_.detectedBpm = newBpm;
    } else {
        // Apply exponential smoothing
        clockTiming_.detectedBpm = (1.0f - smoothingFactor) * clockTiming_.detectedBpm + 
                                   smoothingFactor * newBpm;
    }
}

bool theone::audio::AudioEngine::isClockTimingStable() const {
    if (clockTiming_.recentIntervals.size() < 8) {
        return false; // Need at least 8 intervals for stability analysis
    }
    
    // Calculate variance of recent intervals
    int64_t sum = 0;
    for (int64_t interval : clockTiming_.recentIntervals) {
        sum += interval;
    }
    
    double mean = static_cast<double>(sum) / clockTiming_.recentIntervals.size();
    
    double variance = 0.0;
    for (int64_t interval : clockTiming_.recentIntervals) {
        double diff = static_cast<double>(interval) - mean;
        variance += diff * diff;
    }
    variance /= clockTiming_.recentIntervals.size();
    
    double stdDev = std::sqrt(variance);
    double coefficientOfVariation = stdDev / mean;
    
    // Clock is stable if coefficient of variation is less than 5%
    return coefficientOfVariation < 0.05;
}

void theone::audio::AudioEngine::setExternalClockEnabled(bool useExternal) {
    useExternalClock_.store(useExternal);
    
    if (!useExternal) {
        // Reset to internal clock
        resetClockTiming();
        setSequencerTempo(120.0f); // Default internal tempo
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "External clock %s", useExternal ? "enabled" : "disabled");
}

void theone::audio::AudioEngine::setClockSmoothingFactor(float factor) {
    factor = std::max(0.0f, std::min(1.0f, factor));
    clockSmoothingFactor_.store(factor);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
        "Clock smoothing factor set to %.3f", factor);
}

float theone::audio::AudioEngine::getCurrentBpm() const {
    if (useExternalClock_.load() && midiClockSyncEnabled_.load()) {
        std::lock_guard<std::mutex> lock(clockTimingMutex_);
        return clockTiming_.isStable ? clockTiming_.detectedBpm : 120.0f;
    } else {
        return sequencerTempo_.load();
    }
}

bool theone::audio::AudioEngine::isClockStable() const {
    if (!useExternalClock_.load() || !midiClockSyncEnabled_.load()) {
        return true; // Internal clock is always "stable"
    }
    
    std::lock_guard<std::mutex> lock(clockTimingMutex_);
    return clockTiming_.isStable;
}

void theone::audio::AudioEngine::resetClockTiming() {
    std::lock_guard<std::mutex> lock(clockTimingMutex_);
    
    clockTiming_.lastClockTime = 0;
    clockTiming_.clockInterval = 0;
    clockTiming_.detectedBpm = 120.0f;
    clockTiming_.clockPulseCount = 0;
    clockTiming_.isStable = false;
    clockTiming_.recentIntervals.clear();
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Clock timing reset");
}

void theone::audio::AudioEngine::handleMidiTransport(int transportType) {
    switch (transportType) {
        case 0: // Start
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "MIDI Transport: Start");
            // Reset clock timing for new playback
            resetClockTiming();
            // Start sequencer playback if available
            break;
            
        case 1: // Stop
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "MIDI Transport: Stop");
            stopAllSamples();
            // Reset clock timing
            resetClockTiming();
            break;
            
        case 2: // Continue
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "MIDI Transport: Continue");
            // Continue sequencer playback if available
            // Don't reset clock timing for continue
            break;
            
        default:
            break;
    }
}