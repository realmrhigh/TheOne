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

#include <vector>   // For std::vector
#include <mutex>    // For std::mutex
#include <cmath>    // For cosf, sinf for panning
#include <cstdint>  // For uint types

#ifndef M_PI // Define M_PI if not defined by cmath (common on some compilers)
#define M_PI (3.14159265358979323846f)
#endif

// Helper function for dr_wav to write to a file descriptor
static size_t drwav_write_proc_fd(void* pUserData, const void* pData, size_t bytesToWrite) {
    int fd = (int)(intptr_t)pUserData;
    ssize_t bytesWritten = write(fd, pData, bytesToWrite);
    if (bytesWritten < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_write_proc_fd: Failed to write: %s", strerror(errno));
        return 0;
    }
    return (size_t)bytesWritten;
}

// Helper function for dr_wav to seek in a file descriptor
static drwav_bool32 drwav_seek_proc_fd(void* pUserData, int offset, drwav_seek_origin origin) {
    int fd = (int)(intptr_t)pUserData;
    int whence;
    switch (origin) {
        case drwav_seek_origin_start:
            whence = SEEK_SET;
            break;
        case drwav_seek_origin_current:
            whence = SEEK_CUR;
            break;
        default: // Should not happen with dr_wav's current usage, but cover it.
             __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_seek_proc_fd: Unknown seek origin: %d", origin);
            return DRWAV_FALSE;
    }
    if (lseek(fd, (off_t)offset, whence) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "drwav_seek_proc_fd: Failed to seek: %s", strerror(errno));
        return DRWAV_FALSE;
    }
    return DRWAV_TRUE;
}

// Add these two callback functions somewhere before your JNI function
static size_t on_read_c(void* pUserData, void* pBufferOut, size_t bytesToRead) {
    return read((int)(intptr_t)pUserData, pBufferOut, bytesToRead);
}

static drwav_bool32 on_seek_c(void* pUserData, int offset, drwav_seek_origin origin) {
    int whence = (origin == drwav_seek_origin_current) ? SEEK_CUR : SEEK_SET;
    // lseek returns -1 on error, so we check for that.
    // The function should return DRWAV_TRUE on success and DRWAV_FALSE on failure.
    if (lseek((int)(intptr_t)pUserData, offset, whence) != -1) {
        return DRWAV_TRUE;
    }
    // Log an error if lseek fails
    __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "on_seek_c: Failed to lseek: %s", strerror(errno));
    return DRWAV_FALSE;
}

// Define a type for our sample map
using SampleId = std::string;
using SampleMap = std::map<SampleId, theone::audio::LoadedSample>;

// Declare a global instance of the sample map
static SampleMap gSampleMap;

// Placeholder for logging
const char* APP_NAME = "TheOneNative";

// List for active playing sounds and its mutex
static std::vector<theone::audio::PlayingSound> gActiveSounds;
static std::mutex gActiveSoundsMutex; // Mutex to protect gActiveSounds

// Metronome state object and its mutex
static theone::audio::MetronomeState gMetronomeState;
static std::mutex gMetronomeStateMutex; // Mutex to protect gMetronomeState

// Recording State Variables
static oboe::ManagedStream mInputStream; // Oboe Input Stream
static std::atomic<bool> mIsRecording {false};
static std::atomic<float> mPeakRecordingLevel {0.0f};
static int mRecordingFileDescriptor = -1;    // FD for the output WAV file
static drwav mWavWriter;                     // dr_wav instance for writing
static bool mWavWriterInitialized = false;   // Flag to check if mWavWriter is initialized
static std::string mCurrentRecordingFilePath = ""; // Store the path for metadata later
static std::mutex gRecordingStateMutex; // Mutex to protect recording state, file descriptor, and drwav writer access


// Basic Oboe audio callback
class MyAudioCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override {

        float *outputBuffer = static_cast<float*>(audioData);
        memset(outputBuffer, 0, sizeof(float) * numFrames * oboeStream->getChannelCount());

        // Metronome Beat Generation
        std::vector<theone::audio::PlayingSound> newMetronomeSounds;

        { // Scope for gMetronomeStateMutex
            std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
            if (gMetronomeState.enabled.load() && gMetronomeState.framesPerBeat > 0) {
                if (gMetronomeState.audioStreamSampleRate == 0) {
                    // __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Metronome: audioStreamSampleRate is 0!");
                }

                for (int i = 0; i < numFrames; ++i) {
                    if (gMetronomeState.samplesUntilNextBeat == 0) {
                        gMetronomeState.currentBeatInBar++;
                        if (gMetronomeState.currentBeatInBar > gMetronomeState.timeSignatureNum.load() || gMetronomeState.timeSignatureNum.load() == 0) {
                            gMetronomeState.currentBeatInBar = 1;
                        }

                        const theone::audio::LoadedSample* soundToPlay = nullptr;
                        bool isPrimary = (gMetronomeState.currentBeatInBar == 1);

                        if (isPrimary && gMetronomeState.primaryBeatSample) {
                            soundToPlay = gMetronomeState.primaryBeatSample;
                        } else if (!isPrimary && gMetronomeState.secondaryBeatSample) {
                            soundToPlay = gMetronomeState.secondaryBeatSample;
                        } else if (gMetronomeState.primaryBeatSample) { // Fallback to primary if secondary is null but it's not beat 1
                            soundToPlay = gMetronomeState.primaryBeatSample;
                        }


                        if (soundToPlay) {
                            std::string instanceId = "m_tick_" + std::to_string(gMetronomeState.currentBeatInBar);
                            newMetronomeSounds.emplace_back(soundToPlay, instanceId, gMetronomeState.volume.load(), 0.0f /*center pan*/);
                        }
                        gMetronomeState.samplesUntilNextBeat = gMetronomeState.framesPerBeat;
                    }
                    gMetronomeState.samplesUntilNextBeat--;
                }
            }
        } // gMetronomeStateMutex released


        // Mixing and main audio processing
        std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);

        if (!newMetronomeSounds.empty()) {
            for (const auto& metroSound : newMetronomeSounds) {
                gActiveSounds.push_back(metroSound);
            }
        }

        for (auto soundIt = gActiveSounds.begin(); soundIt != gActiveSounds.end(); /* no increment here */) {
            if (!soundIt->isActive.load()) {
                ++soundIt;
                continue;
            }
            theone::audio::PlayingSound &sound = *soundIt;
            const theone::audio::LoadedSample* loadedSample = sound.loadedSamplePtr;

            if (!loadedSample || loadedSample->audioData.empty()) {
                sound.isActive.store(false);
                ++soundIt;
                continue;
            }

            int channels = oboeStream->getChannelCount();
            int sampleChannels = loadedSample->format.channels;

            for (int i = 0; i < numFrames; ++i) {
                if (!sound.isActive.load()) {
                    break;
                }

                size_t currentReadFrame = sound.currentFrame;
                if (currentReadFrame >= loadedSample->frameCount) {
                    sound.isActive.store(false);
                    break;
                }

                float leftSampleValue = 0.0f;
                float rightSampleValue = 0.0f;

                if (sampleChannels == 1) { // Mono sample
                    float sampleValue = loadedSample->audioData[currentReadFrame];
                    leftSampleValue = sampleValue * sound.gainLeft;
                    rightSampleValue = sampleValue * sound.gainRight;
                } else { // Stereo sample
                    float L = loadedSample->audioData[currentReadFrame * sampleChannels];
                    float R = loadedSample->audioData[currentReadFrame * sampleChannels + 1];
                    leftSampleValue = L * sound.gainLeft;
                    rightSampleValue = R * sound.gainRight;
                }

                if (channels == 2) { // Stereo output
                    outputBuffer[i * 2]     += leftSampleValue;
                    outputBuffer[i * 2 + 1] += rightSampleValue;
                } else if (channels == 1) { // Mono output
                    outputBuffer[i] += (leftSampleValue + rightSampleValue) * 0.5f;
                }
                sound.currentFrame++;
            }
            ++soundIt;
        }

        gActiveSounds.erase(
                std::remove_if(gActiveSounds.begin(), gActiveSounds.end(),
                               [](const theone::audio::PlayingSound& s) {
                                   return !s.isActive.load();
                               }),
                gActiveSounds.end());

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorBeforeClose: %s", oboe::convertToText(error));
    }

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorAfterClose: %s", oboe::convertToText(error));
    }
};

static MyAudioCallback myCallback;
static oboe::ManagedStream outStream;
static bool oboeInitialized = false;

// Define the Audio Input Callback class
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


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1startAudioRecording(
        JNIEnv *env,
        jobject /* thiz */,
        jint jFd,
        jstring jStoragePathForMetadata,
        jint jSampleRate,
        jint jChannels) {

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
    // We need to dup the fd because drwav_init_write_sequential_pcm_frames will call lseek, which will modify the file offset of the fd.
    // If we don't dup the fd, then the original fd's offset will be modified, which could cause problems for other code that uses the original fd.
    int recordingFdDup = dup(mRecordingFileDescriptor);
    if (recordingFdDup < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to dup file descriptor: %s", strerror(errno));
        mCurrentRecordingFilePath = "";
        // No need to close mRecordingFileDescriptor here, Kotlin will do it if jFd was valid.
        // If dup failed, mRecordingFileDescriptor is still the original one from Kotlin.
        return JNI_FALSE;
    }

    if (!drwav_init_write(&mWavWriter, &wavFormat, drwav_write_proc_fd, drwav_seek_proc_fd, (void*)(intptr_t)recordingFdDup, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to initialize drwav writer with procs for FD: %d (dup: %d)", mRecordingFileDescriptor, recordingFdDup);
        close(recordingFdDup); // Close the duplicated FD as init failed
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mWavWriterInitialized = true;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer initialized with procs for FD: %d (dup: %d)", mRecordingFileDescriptor, recordingFdDup);

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
    if (oboeInitialized) {
        return JNI_TRUE;
    }
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setSampleRate(oboe::kUnspecified)
            ->setCallback(&myCallback);

    oboe::Result result = builder.openManagedStream(outStream);
    if (result == oboe::Result::OK) {
        result = outStream->requestStart();
        if (result != oboe::Result::OK) {
            outStream->close();
            return JNI_FALSE;
        }
        oboeInitialized = true;
        std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
        gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate());
        gMetronomeState.updateSchedulingParameters();
        return JNI_TRUE;
    }
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
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_loadSampleToMemory for ID: %s, FD: %d, Offset: %lld, Length: %lld",
                        sampleIdStr.c_str(), fd, (long long)offset, (long long)length);

    if (gSampleMap.count(sampleIdStr)) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample ID '%s' already loaded.", sampleIdStr.c_str());
        return JNI_TRUE;
    }

    drwav wav;
    // For reading, dr_wav_init_fd is appropriate.
    // We must duplicate the file descriptor if dr_wav_init_fd is going to close it
    // or if we want to manage the original fd's lifecycle independently.
    // According to dr_wav.h, drwav_init_fd does *not* take ownership of the fd,
    // so duping is not strictly necessary for dr_wav itself, but good practice if multiple
    // systems might use the fd or if its lifetime is complex. Kotlin side will close original.
    // However, it's safer to work with a duplicated FD if we are doing seeks etc.
    // For simplicity, and if Kotlin guarantees closure, we can use the original fd.
    // Let's assume Kotlin passes a valid, readable FD that it will close.
    // If lseek is needed for the offset:
    if (offset > 0) {
        if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) == -1) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to lseek to offset %lld for FD %d: %s", (long long)offset, fd, strerror(errno));
            return JNI_FALSE;
        }
    }
    // Now fd is positioned at the correct offset.

    if (!drwav_init(&wav, on_read_c, on_seek_c, (void*)(intptr_t)fd, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to init drwav with callbacks for sample %s", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    unsigned int channels = wav.channels;
    unsigned int sampleRate = wav.sampleRate;
    drwav_uint64 totalFrameCount = wav.totalPCMFrameCount;

    // Allocate buffer for all PCM frames
    // Using malloc as new(std::nothrow) might not be universally available/configured in all NDK toolchains without C++ exceptions
    float* pSampleData = (float*)malloc(totalFrameCount * channels * sizeof(float));
    if (pSampleData == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to allocate memory for sample %s. Size: %llu frames, %u channels.",
                            sampleIdStr.c_str(), (unsigned long long)totalFrameCount, channels);
        drwav_uninit(&wav);
        return JNI_FALSE;
    }

    // Read all PCM frames
    drwav_uint64 framesRead = drwav_read_pcm_frames_f32(&wav, totalFrameCount, pSampleData);

    if (framesRead != totalFrameCount) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to read all frames for sample %s. Read %llu of %llu.",
                            sampleIdStr.c_str(), (unsigned long long)framesRead, (unsigned long long)totalFrameCount);
        free(pSampleData);
        drwav_uninit(&wav);
        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Successfully decoded WAV: %u Ch, %u Hz, %llu Frames",
                        channels, sampleRate, (unsigned long long)totalFrameCount);

    theone::audio::LoadedSample loadedSampleInstance; // Correctly declare instance
    loadedSampleInstance.id = sampleIdStr;
    loadedSampleInstance.format.channels = channels;
    loadedSampleInstance.format.sampleRate = sampleRate;
    loadedSampleInstance.format.bitDepth = wav.bitsPerSample; // This will be 32 for f32
    loadedSampleInstance.frameCount = totalFrameCount;
    loadedSampleInstance.audioData.assign(pSampleData, pSampleData + (totalFrameCount * channels));

    free(pSampleData); // Free the buffer allocated by malloc
    drwav_uninit(&wav);   // Uninitialize dr_wav, this does NOT close the original fd.

    gSampleMap[sampleIdStr] = loadedSampleInstance;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' loaded. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size());

    return JNI_TRUE;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playPadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jNoteInstanceId,
        jstring jTrackId,
        jstring jPadId,
        jstring jSampleId,
        jstring jSliceId, // Can be null
        jfloat velocity,
        // How to map PlaybackMode, EnvelopeSettings, LFOSettings?
        // For now, let's assume basic playback and ignore complex params from JNI side.
        // These would require complex jobject conversions.
        jint coarseTune,
        jint fineTune,
        jfloat pan,
        jfloat volume
        // jobject ampEnv, // EnvelopeSettings
        // jobject filterEnv, // EnvelopeSettings?
        // jobject pitchEnv, // EnvelopeSettings?
        // jobjectArray lfos // List<LFOSettings>
) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    // For this simplified JNI, we use the simpler play logic.
    // The full parameter list would require extensive JNI work to map complex Kotlin objects.
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "native_playPadSample (simplified) called: sampleID='%s', instanceID='%s', vol=%.2f, pan=%.2f",
                        sampleIdStr.c_str(), noteInstanceIdStr.c_str(), volume, pan);


    auto mapIt = gSampleMap.find(sampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' not found.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' has no audio data.", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gActiveSoundsMutex);
    gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' added. Active sounds: %zu", sampleIdStr.c_str(), gActiveSounds.size());

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playSampleSlice(
        JNIEnv *env, jobject,
        jstring jSampleId,
        jstring jNoteInstanceId,
        jfloat volume,
        jfloat pan,
        jint sampleRate, // actual sample rate of the audio data
        jlong trimStartMs,
        jlong trimEndMs,
        jlong loopStartMs, // Nullable, check if non-zero or special value
        jlong loopEndMs,   // Nullable
        jboolean isLooping
) {
    // This is a placeholder. True slice and loop playback is complex.
    // For now, it will play the whole sample like native_playPadSample.
    // TODO: Implement actual slice and loop logic.

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "native_playSampleSlice (STUB - plays whole sample) called: sampleID='%s', instanceID='%s', vol=%.2f, pan=%.2f",
                        sampleIdStr.c_str(), noteInstanceIdStr.c_str(), volume, pan);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Slice params: SR=%d, Trim: %lld-%lldms, Loop: %lld-%lldms, Looping: %d",
                        sampleRate, (long long)trimStartMs, (long long)trimEndMs, (long long)loopStartMs, (long long)loopEndMs, isLooping);


    auto mapIt = gSampleMap.find(sampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' not found.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' has no audio data.", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(gActiveSoundsMutex);
    gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan);
    // For true slice playback, you'd need to pass start/end frames (converted from ms)
    // to the PlayingSound constructor or modify its playback logic.
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (slice STUB) added. Active sounds: %zu", sampleIdStr.c_str(), gActiveSounds.size());

    return JNI_TRUE;
}


// Implement other JNI functions (isSampleLoaded, unloadSample, setMetronomeState, etc.) as before

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isSampleLoaded(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    return static_cast<jboolean>(gSampleMap.count(sampleIdStr) > 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1unloadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    gSampleMap.erase(sampleIdStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setMetronomeState(
        JNIEnv *env,
        jobject /* thiz */,
        jboolean jIsEnabled,
        jfloat jBpm,
        jint jTimeSignatureNum,
        jint jTimeSignatureDen,
        jstring jPrimarySoundSampleId,
        jstring jSecondarySoundSampleId) {

    const char *primarySampleIdChars = env->GetStringUTFChars(jPrimarySoundSampleId, nullptr);
    SampleId primarySampleIdStr = primarySampleIdChars ? primarySampleIdChars : "";
    if (primarySampleIdChars) env->ReleaseStringUTFChars(jPrimarySoundSampleId, primarySampleIdChars);

    SampleId secondarySampleIdStr = "";
    if (jSecondarySoundSampleId != nullptr) {
        const char *secondarySampleIdChars = env->GetStringUTFChars(jSecondarySoundSampleId, nullptr);
        secondarySampleIdStr = secondarySampleIdChars ? secondarySampleIdChars : "";
        if (secondarySampleIdChars) env->ReleaseStringUTFChars(jSecondarySoundSampleId, secondarySampleIdChars);
    }

    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.enabled.store(jIsEnabled == JNI_TRUE);
    gMetronomeState.bpm.store(jBpm);
    gMetronomeState.timeSignatureNum.store(jTimeSignatureNum);
    gMetronomeState.timeSignatureDen.store(jTimeSignatureDen);

    auto primaryIt = gSampleMap.find(primarySampleIdStr);
    gMetronomeState.primaryBeatSample = (primaryIt != gSampleMap.end()) ? &(primaryIt->second) : nullptr;

    if (!secondarySampleIdStr.empty()) {
        auto secondaryIt = gSampleMap.find(secondarySampleIdStr);
        gMetronomeState.secondaryBeatSample = (secondaryIt != gSampleMap.end()) ? &(secondaryIt->second) : nullptr;
    } else {
        gMetronomeState.secondaryBeatSample = nullptr;
    }

    if (gMetronomeState.audioStreamSampleRate == 0 && oboeInitialized && outStream) {
        gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate());
    }
    gMetronomeState.updateSchedulingParameters();
    if (gMetronomeState.enabled.load()) {
        gMetronomeState.samplesUntilNextBeat = 0;
        gMetronomeState.currentBeatInBar = gMetronomeState.timeSignatureNum.load();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setMetronomeVolume(
        JNIEnv *env,
        jobject /* thiz */,
        jfloat jVolume) {
    float volumeValue = static_cast<float>(jVolume);
    if (volumeValue < 0.0f) volumeValue = 0.0f;
    if (volumeValue > 1.0f) volumeValue = 1.0f;
    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.volume.store(volumeValue);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1shutdownOboe(
        JNIEnv* env,
        jobject /* this */) {
    if (outStream && oboeInitialized) {
        outStream->requestStop();
        outStream->close();
    }
    if (mInputStream) { // Also close input stream if it was opened
        mInputStream->requestStop();
        mInputStream->close();
    }
    oboeInitialized = false;
    __android_log_print(ANDROID_LOG_INFO, "TheOneNative", "Oboe shutdown complete.");
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
        }
    }
    return -1.0f;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_theone_audio_AudioEngine_native_1stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (AudioEngine)";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1isRecordingActive(
        JNIEnv *env,
        jobject /* thiz */) {
    return static_cast<jboolean>(mIsRecording.load());
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getRecordingLevelPeak(
        JNIEnv *env,
        jobject /* thiz */) {
    return mPeakRecordingLevel.exchange(0.0f);
}

// ... Any other JNI functions needed ...
