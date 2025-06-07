#include "audio_engine.h"
#include <string>
#include <android/log.h>

#define LOG_TAG "AudioEngineCPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper to convert jstring to std::string
std::string jstringToStdString(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string stdStr = chars;
    env->ReleaseStringUTFChars(jStr, chars);
    return stdStr;
}

// --- AudioEngineCallback Implementation ---
// ... (existing callback code from previous step)
oboe::DataCallbackResult AudioEngineCallback::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    // This is where audio processing would happen.
    // For now, let's just zero out the buffer.
    // Metronome tick generation will eventually go in here or be called from here.
    int16_t *outputBuffer = static_cast<int16_t *>(audioData);
    int numSamples = numFrames * oboeStream->getChannelCount();

    // Example: If metronome is enabled, mix a click (conceptual)
    // if (mParentEngine.mMetronomeEnabled) {
    //    // generate metronome click based on mParentEngine.mMetronomeBpm, numFrames, mParentEngine.mSampleRate
    //    // mix it into outputBuffer
    // }

    for (int i = 0; i < numSamples; ++i) {
        outputBuffer[i] = 0; // Keep it silent for now
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngineCallback::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
    mParentEngine.mIsStreamOpen = false;
}

void AudioEngineCallback::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
    mParentEngine.mIsStreamOpen = false;
}


// --- AudioEngine Class Implementation ---
// ... (constructor, destructor, getVersion, initialize, stopStream from previous step)
AudioEngine::AudioEngine() : mCallback(new AudioEngineCallback(*this)) {
    LOGI("AudioEngine instance created");
}

AudioEngine::~AudioEngine() {
    stopStream();
    delete mCallback;
    LOGI("AudioEngine instance destroyed");
}

std::string AudioEngine::getVersion() {
    return "0.0.1-alpha";
}

bool AudioEngine::initialize(int sampleRate, int framesPerBurst, bool enableLowLatency) {
    LOGI("AudioEngine::initialize called with SR: %d, FramesPerBurst: %d, LowLatency: %d", sampleRate, framesPerBurst, enableLowLatency);
    if (mIsStreamOpen) {
        LOGE("Stream already open. Call stopStream() first.");
        return false;
    }

    mSampleRate = sampleRate;
    mFramesPerBurst = framesPerBurst;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(enableLowLatency ? oboe::PerformanceMode::LowLatency : oboe::PerformanceMode::None)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(mChannelCount)
           ->setSampleRate(sampleRate)
           ->setDataCallback(mCallback)
           ->setErrorCallback(mCallback);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK || !mStream) {
        LOGE("Failed to create Oboe stream. Error: %s", oboe::convertToText(result));
        mStream.reset();
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream. Error: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }

    mIsStreamOpen = true;
    LOGI("Oboe stream started successfully. Sample Rate: %d, Channels: %d, LowLatency: %s",
         mStream->getSampleRate(), mStream->getChannelCount(), enableLowLatency ? "true" : "false");
    return true;
}

void AudioEngine::stopStream() {
    if (mStream && mIsStreamOpen) {
        LOGI("Stopping Oboe stream...");
        oboe::Result result = mStream->stop();
        if (result != oboe::Result::OK) LOGE("Error stopping Oboe stream: %s", oboe::convertToText(result));
        result = mStream->close();
        if (result != oboe::Result::OK) LOGE("Error closing Oboe stream: %s", oboe::convertToText(result));
        mStream.reset();
        mIsStreamOpen = false;
        LOGI("Oboe stream stopped and closed.");
    }
}

void AudioEngine::setMetronomeState(bool isEnabled, float bpm, int tsNum, int tsDen, const std::string& primarySoundUri, const std::string& secondarySoundUri) {
    LOGI("AudioEngine::setMetronomeState called:");
    LOGI("  isEnabled: %s", isEnabled ? "true" : "false");
    LOGI("  BPM: %.2f", bpm);
    LOGI("  Time Signature: %d/%d", tsNum, tsDen);
    LOGI("  Primary Sound URI: %s", primarySoundUri.c_str());
    LOGI("  Secondary Sound URI: %s", secondarySoundUri.c_str());

    mMetronomeEnabled = isEnabled;
    mMetronomeBpm = bpm;
    // Store other parameters as needed for future implementation
    // Actual sound loading and tick generation logic will be added later.
    // For now, this is just a placeholder to confirm the JNI bridge works.
}

static AudioEngine sEngineInstance;

// --- JNI Functions Implementation ---
// ... (getEngineVersion, initialize JNI functions from previous step)
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_theone_audioengine_AudioEngineControlImpl_getEngineVersion(
        JNIEnv *env,
        jobject /* this */) {
    std::string version = sEngineInstance.getVersion();
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audioengine_AudioEngineControlImpl_initialize(
        JNIEnv *env,
        jobject /* this */,
        jint sampleRate,
        jint bufferSize,
        jboolean enableLowLatency) {
    return static_cast<jboolean>(sEngineInstance.initialize(static_cast<int>(sampleRate),
                                                           static_cast<int>(bufferSize),
                                                           static_cast<bool>(enableLowLatency)));
}

extern "C" JNIEXPORT void JNICALL // Added
Java_com_example_theone_audioengine_AudioEngineControlImpl_setMetronomeState(
        JNIEnv *env,
        jobject /* this */,
        jboolean isEnabled,
        jfloat bpm,
        jint timeSignatureNum,
        jint timeSignatureDen,
        jstring soundPrimaryUri,
        jstring soundSecondaryUri) {

    std::string primaryUriStr = jstringToStdString(env, soundPrimaryUri);
    std::string secondaryUriStr = jstringToStdString(env, soundSecondaryUri);

    sEngineInstance.setMetronomeState(static_cast<bool>(isEnabled),
                                      static_cast<float>(bpm),
                                      static_cast<int>(timeSignatureNum),
                                      static_cast<int>(timeSignatureDen),
                                      primaryUriStr,
                                      secondaryUriStr);
}
