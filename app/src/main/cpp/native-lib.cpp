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
#include "dr_wav.h" // Include the dr_wav header

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

// Define a type for our sample map
using SampleId = std::string;
using SampleMap = std::map<SampleId, theone::audio::LoadedSample>;

// Declare a global instance of the sample map
static SampleMap gSampleMap;

// Placeholder for logging
const char* APP_NAME = "TheOneNative";


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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1loadSampleToMemory(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId,
        jint fd,
        jlong offset, // offset and length might be needed if reading a slice from a larger file (e.g. soundfont)
        jlong length) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    if (!nativeSampleId) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "loadSampleToMemory: Failed to get sample ID string.");
        return JNI_FALSE; // Out of memory or invalid string
    }
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "loadSampleToMemory called for ID: %s, FD: %d, Offset: %lld, Length: %lld",
                        sampleIdStr.c_str(), fd, offset, length);

    // Check if sample already loaded
    if (gSampleMap.count(sampleIdStr)) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample ID '%s' already loaded.", sampleIdStr.c_str());
        // Consider whether to reload or return true/false based on requirements
        return JNI_TRUE; // For now, assume if it's there, it's fine.
    }

    // The file descriptor (fd) received from Kotlin is already opened.
    // We need to dup it if we want to manage its lifecycle here (e.g. close it),
    // or ensure Kotlin closes it. Generally, Kotlin should own and close the FD.
    // For reading, we might need to seek if offset is used.
    // If using dr_wav with file descriptor, it might handle seeking internally.

    // It's good practice to dup the file descriptor if C++ is going to hold onto it
    // or if multiple C++ systems might access it concurrently.
    // However, if we are just going to read it sequentially here and now,
    // using the passed fd directly is fine, assuming Kotlin manages its lifecycle.
    // int new_fd = dup(fd); // If you need to own a copy of the FD
    // if (new_fd == -1) {
    //    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to dup file descriptor %d: %s", fd, strerror(errno));
    //    return JNI_FALSE;
    // }

    // Seek to the specified offset within the file if an offset is provided.
    // This is important if the audio data doesn't start at the beginning of the file descriptor.
    if (offset > 0) {
        if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) == -1) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to lseek to offset %lld for FD %d: %s", offset, fd, strerror(errno));
            // close(new_fd); // if duped
            return JNI_FALSE;
        }
    }

    // At this point, the file cursor for 'fd' is at the correct 'offset'.

    theone::audio::LoadedSample sample;
    sample.id = sampleIdStr;

    // The file descriptor 'fd' is already opened by Kotlin and positioned by lseek if offset > 0.
    // dr_wav can take the fd directly. Note: dr_wav will try to read from the current fd position.
    // If an offset was used with lseek, dr_wav will read from that offset.

    unsigned int channels;
    unsigned int sampleRate;
    drwav_uint64 totalFrameCount;
    float* pSampleData = nullptr; // Important to initialize to nullptr

    // If using a subrange (e.g. length is not the full file), drwav_init_file_descriptor_subrange
    // might be more appropriate, but requires more careful handling of drwav object.
    // For simplicity, assuming fd points to the start of a complete WAV file or segment
    // and length is the total length of this segment.
    // drwav_open_file_descriptor_and_read_pcm_frames_f32 reads the whole file/segment from current fd position.

    // Duplicate the file descriptor because dr_wav will close it if ownership is passed.
    // Alternatively, ensure Kotlin doesn't close it until after this JNI call if not duped,
    // but duping is safer if dr_wav internals might close it.
    // However, drwav_open_file_descriptor... does NOT take ownership or close the fd.
    // So, duping is not strictly necessary just for dr_wav. We duped it earlier if we wanted C++ to own it.
    // The `fd` from Kotlin is what we use.

    // If an offset was applied, lseek would have already positioned the fd.
    // dr_wav will read from the current position.
    // Using drwav_open_file_descriptor_and_read_pcm_frames_f32 with NULL for allocation callbacks to use defaults (malloc/free)
    pSampleData = drwav_open_file_descriptor_and_read_pcm_frames_f32(fd, &channels, &sampleRate, &totalFrameCount, NULL);

    if (pSampleData == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to decode WAV from FD %d. dr_wav error.", fd);
        // Note: If lseek was used, the fd is already positioned.
        // No need to close 'fd' here as it's owned by Kotlin.
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Successfully decoded WAV: %u Ch, %u Hz, %llu Frames", channels, sampleRate, totalFrameCount);

    sample.format.channels = static_cast<uint16_t>(channels);
    sample.format.sampleRate = static_cast<uint32_t>(sampleRate);
    sample.format.bitDepth = 32; // dr_wav converts to float 32-bit
    sample.frameCount = static_cast<size_t>(totalFrameCount);

    // Copy data into the sample's vector
    sample.audioData.assign(pSampleData, pSampleData + (totalFrameCount * channels));

    drwav_free(pSampleData, nullptr); // Free the buffer allocated by dr_wav

    gSampleMap[sampleIdStr] = sample;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' loaded and processed. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size());

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isSampleLoaded(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    if (!nativeSampleId) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "isSampleLoaded: Failed to get sample ID string.");
        return JNI_FALSE; // Should not happen if called from Kotlin with valid string
    }
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    if (gSampleMap.count(sampleIdStr)) {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "isSampleLoaded: Sample '%s' is loaded.", sampleIdStr.c_str());
        return JNI_TRUE;
    } else {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "isSampleLoaded: Sample '%s' is NOT loaded.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1unloadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    if (!nativeSampleId) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "unloadSample: Failed to get sample ID string.");
        return;
    }
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    auto it = gSampleMap.find(sampleIdStr);
    if (it != gSampleMap.end()) {
        // The LoadedSample struct uses std::vector for audioData,
        // so its memory will be automatically managed when the object is erased from the map.
        gSampleMap.erase(it);
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "unloadSample: Sample '%s' unloaded. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size());
    } else {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "unloadSample: Sample '%s' not found, cannot unload.", sampleIdStr.c_str());
    }
}
