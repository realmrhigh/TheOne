#ifndef THEONE_AUDIO_ENGINE_H
#define THEONE_AUDIO_ENGINE_H

#include <jni.h>
#include <string>
#include <oboe/Oboe.h>

// Forward declaration
class AudioEngine;

class AudioEngineCallback : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
    // ... (existing callback code)
    public:
        AudioEngineCallback(AudioEngine& parentEngine) : mParentEngine(parentEngine) {}
        oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
        void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
        void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    private:
        AudioEngine& mParentEngine;
};

extern "C" {
JNIEXPORT jstring JNICALL
Java_com_example_theone_audioengine_AudioEngineControlImpl_getEngineVersion(
        JNIEnv *env,
        jobject /* this */);

JNIEXPORT jboolean JNICALL
Java_com_example_theone_audioengine_AudioEngineControlImpl_initialize(
        JNIEnv *env,
        jobject /* this */,
        jint sampleRate,
        jint bufferSize,
        jboolean enableLowLatency);

JNIEXPORT void JNICALL // Added
Java_com_example_theone_audioengine_AudioEngineControlImpl_setMetronomeState(
        JNIEnv *env,
        jobject /* this */,
        jboolean isEnabled,
        jfloat bpm,
        jint timeSignatureNum,
        jint timeSignatureDen,
        jstring soundPrimaryUri, // jstring for URIs
        jstring soundSecondaryUri);
}

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();
    std::string getVersion();
    bool initialize(int sampleRate, int framesPerBurst, bool enableLowLatency);
    void stopStream();
    void setMetronomeState(bool isEnabled, float bpm, int tsNum, int tsDen, const std::string& primarySoundUri, const std::string& secondarySoundUri); // Added

friend class AudioEngineCallback;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    AudioEngineCallback* mCallback;
    std::atomic<bool> mIsStreamOpen{false};
    int32_t mSampleRate;
    int32_t mFramesPerBurst;
    int32_t mChannelCount = 2;

    // Metronome placeholder members
    bool mMetronomeEnabled = false;
    float mMetronomeBpm = 120.0f;
    // We'll add more detailed metronome state later (e.g., sample loading, tick generation logic)
};

#endif //THEONE_AUDIO_ENGINE_H
