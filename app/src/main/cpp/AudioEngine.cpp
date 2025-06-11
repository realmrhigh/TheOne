#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm> // For std::max, std::min
#include <string.h> // For memset in onAudioReady

// Define APP_NAME for logging, if not already globally available
#ifndef APP_NAME
#define APP_NAME "TheOneAudioEngine"
#endif

namespace theone {
namespace audio {

AudioEngine::AudioEngine() : oboeInitialized_(false), audioStreamSampleRate_(0) {
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
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::initialize - Oboe output stream started. Sample Rate: %u", audioStreamSampleRate_);
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

    // Placeholder for where complex audio rendering would occur, similar to the one in native-lib.cpp
    // This would involve iterating activeSounds_, processing metronome, sequencer events,
    // applying envelopes, LFOs, and mixing into outputBuffer.
    // All accesses to shared data (padSettingsMap_, sampleMap_, activeSounds_, etc.)
    // must be protected by their respective mutexes (padSettingsMutex_, sampleMapMutex_, etc.).

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


// --- Placeholder/Simplified Implementations for other methods from AudioEngine.h ---
// These would need full implementations similar to what was in native-lib.cpp,
// but adapted to use class members and appropriate mutexes.

bool AudioEngine::loadSampleToMemory(const std::string& sampleId, int fd, long offset, long length) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::loadSampleToMemory (placeholder) for ID: %s", sampleId.c_str());
    // TODO: Implement using sampleMap_ and sampleMapMutex_
    // Similar logic to native-lib's native_loadSampleToMemory using dr_wav
    return false;
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

bool AudioEngine::playPadSample(/*...params...*/) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::playPadSample (placeholder)");
    // TODO: Implement using activeSounds_, activeSoundsMutex_, padSettingsMap_, sampleMap_
    // This would involve creating a PlayingSound object and adding to activeSounds_
    return false;
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
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::startAudioRecording (placeholder)");
    // TODO: Implement full recording logic with mInputStream_, recordingStateMutex_ etc.
    // This would involve setting up dr_wav with wavWriter_ and starting mInputStream_
    return false;
}

jobjectArray AudioEngine::stopAudioRecording(JNIEnv* env) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "AudioEngine::stopAudioRecording (placeholder)");
    // TODO: Implement full stop logic, uninit dr_wav, close stream, return metadata
    return nullptr;
}

bool AudioEngine::isRecordingActive() {
    return isRecording_.load();
}

float AudioEngine::getRecordingLevelPeak() {
    return peakRecordingLevel_.exchange(0.0f);
}

void AudioEngine::RecalculateTickDuration() {
    std::lock_guard<std::mutex> lock(sequencerMutex_);
    if (currentSequence_ && currentSequence_->bpm > 0 && currentSequence_->ppqn > 0) {
        double msPerMinute = 60000.0;
        double beatsPerMinute = currentSequence_->bpm;
        double msPerBeat = msPerMinute / beatsPerMinute;
        currentTickDurationMs_ = msPerBeat / currentSequence_->ppqn;
    } else {
        currentTickDurationMs_ = 0.0;
    }
}


} // namespace audio
} // namespace theone
