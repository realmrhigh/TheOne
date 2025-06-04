#include <jni.h>
#include <string>
#include <android/log.h>
#include <oboe/Oboe.h>

// Basic Oboe audio callback
class MyAudioCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {
        // Silence for now
        memset(audioData, 0, numFrames * oboeStream->getBytesPerFrame());
        return oboe::DataCallbackResult::Continue;
    }
};

static MyAudioCallback myCallback;
static oboe::ManagedStream outStream;
static bool oboeInitialized = false;


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1initOboe(
        JNIEnv* env,
        jobject /* this */) {
    __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "native_initOboe called");
    if (oboeInitialized) {
        __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "Oboe already initialized.");
        return JNI_TRUE;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(oboe::kUnspecified) // Use default sample rate for now
           ->setCallback(&myCallback);

    oboe::Result result = builder.openManagedStream(outStream);
    if (result == oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "Oboe stream opened successfully.");
        result = outStream->requestStart();
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, "TheOneNative", "Failed to start Oboe stream: %s", oboe::convertToText(result));
            outStream->close(); // Close if start failed
            oboeInitialized = false;
            return JNI_FALSE;
        }
        __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "Oboe stream started successfully. SampleRate: %d, BufferSize: %d, API: %s",
            outStream->getSampleRate(), outStream->getFramesPerBurst(), oboe::convertToText(outStream->getAudioApi()));
        oboeInitialized = true;
        return JNI_TRUE;
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "TheOneNative", "Failed to open Oboe stream: %s", oboe::convertToText(result));
        oboeInitialized = false;
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1shutdownOboe(
        JNIEnv* env,
        jobject /* this */) {
    __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "native_shutdownOboe called");
    if (outStream && oboeInitialized) {
        oboe::Result result = outStream->requestStop();
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, "TheOneNative", "Error stopping oboe stream: %s", oboe::convertToText(result));
        }
        result = outStream->close();
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, "TheOneNative", "Error closing oboe stream: %s", oboe::convertToText(result));
        }
        __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "Oboe stream stopped and closed.");
    }
    oboeInitialized = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isOboeInitialized(
        JNIEnv* env,
        jobject /* this */) {
    return static_cast<jboolean>(oboeInitialized && outStream && outStream->getState() != oboe::StreamState::Closed);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getOboeReportedLatencyMillis(
        JNIEnv* env,
        jobject /* this */) {
    if (oboeInitialized && outStream) {
        oboe::ResultWithValue<double> latency = outStream->calculateLatencyMillis();
        if (latency) {
            return static_cast<jfloat>(latency.value());
        } else {
             __android_log_print(ANDROID_LOG_ERROR, "TheOneNative", "Error calculating latency: %s", oboe::convertToText(latency.error()));
        }
    }
    return -1.0f;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_theone_audio_AudioEngine_native_1stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (AudioEngine)";
    __android_log_print(ANDROID_LOG_DEBUG, "TheOneNative", "%s", hello.c_str());

    oboe::AudioApi api = oboe::AudioApi::AAudio; // Default to AAudio for logging
    if (outStream && outStream->getAudioApi() != oboe::AudioApi::Unspecified) {
       api = outStream->getAudioApi();
    }
    __android_log_print(ANDROID_LOG_DEBUG, "TheOneNative", "Oboe AudioApi in use or default: %s", oboe::convertToText(api));

    return env->NewStringUTF(hello.c_str());
}
