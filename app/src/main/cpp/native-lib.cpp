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

// Basic Oboe audio callback
class MyAudioCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

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
                        } else if (gMetronomeState.primaryBeatSample) {
                            soundToPlay = gMetronomeState.primaryBeatSample;
                        }


                        if (soundToPlay) {
                            std::string instanceId = "m_tick_" + std::to_string(gMetronomeState.currentBeatInBar);
                            newMetronomeSounds.emplace_back(soundToPlay, instanceId, gMetronomeState.volume.load(), 0.0f /*center pan*/);
                            // __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Metronome: Queued tick, beat %d", gMetronomeState.currentBeatInBar);
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
            // __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Metronome: Added %zu ticks to gActiveSounds. Total: %zu", newMetronomeSounds.size(), gActiveSounds.size());
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

                if (sampleChannels == 1) {
                    float sampleValue = loadedSample->audioData[currentReadFrame];
                    leftSampleValue = sampleValue * sound.gainLeft;
                    rightSampleValue = sampleValue * sound.gainRight;
                } else {
                    float L = loadedSample->audioData[currentReadFrame * sampleChannels];
                    float R = loadedSample->audioData[currentReadFrame * sampleChannels + 1];
                    leftSampleValue = L * sound.gainLeft;
                    rightSampleValue = R * sound.gainRight;
                }

                if (channels == 2) {
                    outputBuffer[i * 2]     += leftSampleValue;
                    outputBuffer[i * 2 + 1] += rightSampleValue;
                } else if (channels == 1) {
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

    // Add onErrorBeforeClose and onErrorAfterClose for robustness
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

// Define the Audio Input Callback class
class MyAudioInputCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *inputStream, // This is the input stream
        void *audioData,    // Buffer with incoming audio data
        int32_t numFrames) override {

        // Only process if recording is active and writer is initialized
        if (mIsRecording.load() && mWavWriterInitialized) {
            std::lock_guard<std::mutex> lock(gRecordingStateMutex); // Protect mWavWriter and mPeakRecordingLevel update

            if (!mIsRecording.load() || !mWavWriterInitialized) { // Double check after lock
                return oboe::DataCallbackResult::Continue;
            }

            // Assuming input stream is float, matching drwav_write_pcm_frames expectation for float.
            // If input is int16_t, it needs conversion before writing as float, or use drwav_write_pcm_frames_s16.
            // For now, let's assume Oboe input is configured for float.
            const float* inputBuffer = static_cast<const float*>(audioData);

            // Write data to WAV file
            // drwav_write_pcm_frames takes the number of PCM frames, not samples.
            // If the stream is stereo, numFrames is frames_per_channel.
            // If mono, numFrames is total samples. dr_wav handles channels internally via init.
            drwav_uint64 framesWritten = drwav_write_pcm_frames(&mWavWriter, numFrames, inputBuffer);
            if (framesWritten != static_cast<drwav_uint64>(numFrames)) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback: Failed to write all PCM frames to WAV. Wrote %llu/%d",
                                    framesWritten, numFrames);
                // Consider stopping recording or flagging an error here.
                // For now, continue.
            }

            // Calculate peak level for this buffer
            float currentMaxAbs = 0.0f;
            int numSamplesInBlock = numFrames * inputStream->getChannelCount(); // Total samples in this block

            for (int i = 0; i < numSamplesInBlock; ++i) {
                float absValue = std::abs(inputBuffer[i]);
                if (absValue > currentMaxAbs) {
                    currentMaxAbs = absValue;
                }
            }

            // Update global peak level (simple max, could be smoothed)
            // Use a temporary variable for the atomic store to avoid direct read-modify-write on atomic
            float previousPeak = mPeakRecordingLevel.load();
            if (currentMaxAbs > previousPeak) {
                mPeakRecordingLevel.store(currentMaxAbs);
            }

        } else {
            // If not recording, or writer not ready, just consume data.
            // Or, if input stream is not running when not recording, this won't be called.
        }
        return oboe::DataCallbackResult::Continue; // Keep stream running
    }

    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorBeforeClose: %s", oboe::convertToText(error));
        // Potentially set a flag indicating the input stream had an error.
    }

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorAfterClose: %s", oboe::convertToText(error));
    }
};

// Declare an instance of the input callback
static MyAudioInputCallback myInputCallback;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1startAudioRecording(
        JNIEnv *env,
        jobject /* thiz */,
        jint jFd, // File descriptor for writing the WAV file
        jstring jStoragePathForMetadata, // Original URI/path string for metadata
        jint jSampleRate, // Desired sample rate for recording
        jint jChannels) { // Desired channel count for recording (1 for mono, 2 for stereo)

    std::lock_guard<std::mutex> lock(gRecordingStateMutex);

    if (mIsRecording.load()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "startAudioRecording: Already recording.");
        // It's important that Kotlin side closes the FD if it opened it and we are returning early.
        // Or, we take ownership and close it here. For now, assume Kotlin handles FD closure on error.
        // If FD was passed and we return false, Kotlin should close it.
        if (jFd >= 0) close(jFd); // Or expect Kotlin to close it. This is safer.
        return JNI_FALSE;
    }

    // Store metadata path
    const char* pathChars = env->GetStringUTFChars(jStoragePathForMetadata, nullptr);
    if (pathChars) {
        mCurrentRecordingFilePath = pathChars;
        env->ReleaseStringUTFChars(jStoragePathForMetadata, pathChars);
    } else {
        mCurrentRecordingFilePath = ""; // Should not happen if Kotlin sends valid string
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "native_startAudioRecording called. FD: %d, Path: %s, SR: %d, Ch: %d",
                        jFd, mCurrentRecordingFilePath.c_str(), jSampleRate, jChannels);

    if (jFd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Invalid file descriptor provided (%d).", jFd);
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mRecordingFileDescriptor = jFd;

    // Configure drwav format
    drwav_data_format wavFormat;
    wavFormat.container = drwav_container_riff; // Standard WAV
    wavFormat.format = DR_WAVE_FORMAT_IEEE_FLOAT; // Recording as float
    wavFormat.channels = static_cast<uint32_t>(jChannels);
    wavFormat.sampleRate = static_cast<uint32_t>(jSampleRate);
    wavFormat.bitsPerSample = 32; // 32-bit float

    // Initialize drwav writer
    // Using drwav_init_file_descriptor_write_sequential_pcm_frames which is more suitable for sequential writes
    // than drwav_init_file_descriptor_write. The pcm_frames variant assumes total frame count is known for header.
    // If total frame count is unknown, drwav_init_file_descriptor_write is better, then finalize with drwav_uninit.
    // For continuous recording, we don't know total frames upfront. So, drwav_init_file_descriptor_write is what we need.
    // The provided snippet used drwav_init_file_descriptor_write_sequential_pcm_frames.
    // Correcting to drwav_init_file_descriptor_write.
    // For sequential write without knowing total size, pass 0 for totalPCMFrameCount to _sequential_pcm_frames,
    // or use drwav_init_file_descriptor_write if not strictly sequential *in terms of dr_wav's definition*.
    // Let's use drwav_init_file_descriptor_write as it's more general for unknown length.
    // drwav_init_file_descriptor_write requires allocation callbacks to be null for default.
    if (!drwav_init_file_descriptor_write(&mWavWriter, &wavFormat, mRecordingFileDescriptor, nullptr)) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to initialize drwav writer for FD: %d", mRecordingFileDescriptor);
        close(mRecordingFileDescriptor); // Close the FD as we failed to use it
        mRecordingFileDescriptor = -1;
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    mWavWriterInitialized = true;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer initialized for FD: %d", mRecordingFileDescriptor);

    // Configure and open Oboe input stream
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive) // Recommended for recording
           ->setFormat(oboe::AudioFormat::Float)       // Match drwav format
           ->setChannelCount(static_cast<oboe::ChannelCount>(jChannels)) // Match drwav format
           ->setSampleRate(static_cast<int32_t>(jSampleRate))   // Match drwav format
           ->setInputPreset(oboe::InputPreset::VoiceRecognition) // Or Generic, Unprocessed
           ->setCallback(&myInputCallback);
           // ->setDeviceId(0); // Default input device. Later can use inputDeviceIdStr if passed.

    oboe::Result result = builder.openManagedStream(mInputStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to open Oboe input stream: %s", oboe::convertToText(result));
        drwav_uninit(&mWavWriter);
        mWavWriterInitialized = false;
        // FD was already closed by drwav_uninit if it took ownership, or we need to close it if drwav_init failed before taking ownership.
        // drwav_init_file_descriptor_write does not take ownership of FD, so we must close it.
        close(mRecordingFileDescriptor);
        mRecordingFileDescriptor = -1;
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream opened. SR: %d, Ch: %d, Format: %s",
        mInputStream->getSampleRate(), mInputStream->getChannelCount(), oboe::convertToText(mInputStream->getFormat()));

    if (mInputStream->getFormat() != oboe::AudioFormat::Float ||
        mInputStream->getSampleRate() != static_cast<int32_t>(jSampleRate) ||
        mInputStream->getChannelCount() != static_cast<oboe::ChannelCount>(jChannels)) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Oboe input stream format mismatch. Requested SR:%d, Ch:%d, Fmt:Float. Actual SR:%d, Ch:%d, Fmt:%s",
                            jSampleRate, jChannels,
                            mInputStream->getSampleRate(), mInputStream->getChannelCount(), oboe::convertToText(mInputStream->getFormat()));
        if (mInputStream->getFormat() != oboe::AudioFormat::Float) {
             __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe input stream NOT float. Cannot proceed with current dr_wav setup.");
             mInputStream->close();
             drwav_uninit(&mWavWriter);
             mWavWriterInitialized = false;
             close(mRecordingFileDescriptor);
             mRecordingFileDescriptor = -1;
             mCurrentRecordingFilePath = "";
             return JNI_FALSE;
        }
    }

    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to start Oboe input stream: %s", oboe::convertToText(result));
        mInputStream->close();
        drwav_uninit(&mWavWriter);
        mWavWriterInitialized = false;
        close(mRecordingFileDescriptor);
        mRecordingFileDescriptor = -1;
        mCurrentRecordingFilePath = "";
        return JNI_FALSE;
    }

    mIsRecording.store(true);
    mPeakRecordingLevel.store(0.0f);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording started successfully.");
    return JNI_TRUE;
}


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

        // Add this:
        {
            std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
            gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate());
            gMetronomeState.updateSchedulingParameters(); // Initial update based on default BPM
             __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: stream sample rate set to %u", gMetronomeState.audioStreamSampleRate);
        }
        return JNI_TRUE;
    } else {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open Oboe stream: %s", oboe::convertToText(result));
        oboeInitialized = false;
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playPadSample(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId,
        jstring jNoteInstanceId,
        jfloat volume,
        jfloat pan) {

    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    if (!nativeSampleId) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Failed to get sample ID string.");
        return JNI_FALSE;
    }
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    if (!nativeNoteInstanceId) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Failed to get note instance ID string.");
        return JNI_FALSE;
    }
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "playPadSample called: sampleID='%s', instanceID='%s', vol=%.2f, pan=%.2f",
                        sampleIdStr.c_str(), noteInstanceIdStr.c_str(), volume, pan);

    // Find the loaded sample
    auto mapIt = gSampleMap.find(sampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' not found in gSampleMap.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second); // Get pointer to the sample in the map

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
         __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' has no audio data or zero frames.", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    // Now add it to the active sounds list
    std::lock_guard<std::mutex> lock(gActiveSoundsMutex); // Lock the mutex

    // Optional: Check if a sound with the same noteInstanceId is already active.
    // This depends on desired behavior (e.g., retrigger, ignore, stop previous).
    // For now, we'll allow multiple instances with the same ID or different ones.
    // A more robust system might use noteInstanceId for explicit stop/modification later.

    gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Sample '%s' (instance '%s') added to active sounds. Active sounds count: %zu",
                        sampleIdStr.c_str(), noteInstanceIdStr.c_str(), gActiveSounds.size());

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

    mIsRecording.store(false); // Signal input callback to stop writing

    // Stop and close the input stream
    if (mInputStream) { // Check if stream was successfully opened
        oboe::Result closeResult = mInputStream->requestStop();
        if (closeResult != oboe::Result::OK) {
             __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Error stopping Oboe input stream: %s", oboe::convertToText(closeResult));
        }
        closeResult = mInputStream->close();
        if (closeResult != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Error closing Oboe input stream: %s", oboe::convertToText(closeResult));
        } else {
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream stopped and closed.");
        }
    }

    drwav_uint64 totalFramesWritten = 0;
    std::string tempRecordingPath = mCurrentRecordingFilePath; // Store before clearing

    if (mWavWriterInitialized) {
        totalFramesWritten = mWavWriter.totalPCMFrameCount; // Get before uninit
        if (!drwav_uninit(&mWavWriter)) {
            __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to uninitialize drwav writer.");
            // Continue anyway to return info, but WAV might be corrupt.
        } else {
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer uninitialized. Total frames: %llu", totalFramesWritten);
        }
        mWavWriterInitialized = false;
    }

    // The file descriptor mRecordingFileDescriptor is owned by Kotlin and should be closed there.
    // We closed it in startAudioRecording if drwav_init failed or oboe stream failed.
    // If it was successfully used by dr_wav, drwav_uninit should have handled flushing.
    // Kotlin is responsible for closing the FD it opened and passed to us.
    mRecordingFileDescriptor = -1; // Reset our reference.
    mCurrentRecordingFilePath = ""; // Clear after use

    // Prepare to return data to Kotlin
    jstring jRecordedPath = env->NewStringUTF(tempRecordingPath.c_str());

    // Create Long object for totalFramesWritten
    jclass longClass = env->FindClass("java/lang/Long");
    if (!longClass) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find java/lang/Long class.");
        env->DeleteLocalRef(jRecordedPath);
        return nullptr;
    }
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
    if (!longConstructor) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find Long constructor.");
        env->DeleteLocalRef(jRecordedPath);
        env->DeleteLocalRef(longClass);
        return nullptr;
    }
    jobject jTotalFrames = env->NewObject(longClass, longConstructor, static_cast<jlong>(totalFramesWritten));
    if (!jTotalFrames) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to create Long object for frames.");
        env->DeleteLocalRef(jRecordedPath);
        env->DeleteLocalRef(longClass);
        return nullptr;
    }

    // Create jobjectArray (Object[2])
    jclass objectClass = env->FindClass("java/lang/Object");
    if (!objectClass) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find java/lang/Object class.");
        env->DeleteLocalRef(jRecordedPath);
        env->DeleteLocalRef(jTotalFrames);
        env->DeleteLocalRef(longClass);
        return nullptr;
    }
    jobjectArray resultArray = env->NewObjectArray(2, objectClass, nullptr);
    if (!resultArray) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to create result Object array.");
        env->DeleteLocalRef(jRecordedPath);
        env->DeleteLocalRef(jTotalFrames);
        env->DeleteLocalRef(longClass);
        env->DeleteLocalRef(objectClass);
        return nullptr;
    }

    env->SetObjectArrayElement(resultArray, 0, jRecordedPath);
    env->SetObjectArrayElement(resultArray, 1, jTotalFrames);

    // Clean up local references created by JNI functions
    env->DeleteLocalRef(jRecordedPath);
    env->DeleteLocalRef(jTotalFrames);
    env->DeleteLocalRef(longClass);
    env->DeleteLocalRef(objectClass);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording stopped successfully. Path: %s, Frames: %llu",
                        tempRecordingPath.c_str(),
                        totalFramesWritten);

    return resultArray;
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
    // Atomically read the current peak and reset it to 0.0f
    return mPeakRecordingLevel.exchange(0.0f);
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

    // Secondary sound ID can be null or empty if not used.
    const char *secondarySampleIdChars = nullptr;
    SampleId secondarySampleIdStr = "";
    if (jSecondarySoundSampleId != nullptr) {
        secondarySampleIdChars = env->GetStringUTFChars(jSecondarySoundSampleId, nullptr);
        secondarySampleIdStr = secondarySampleIdChars ? secondarySampleIdChars : "";
        if (secondarySampleIdChars) env->ReleaseStringUTFChars(jSecondarySoundSampleId, secondarySampleIdChars);
    }

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "native_setMetronomeState called: enabled=%d, bpm=%.2f, timeSig=%d/%d, primaryID='%s', secondaryID='%s'",
                        jIsEnabled, jBpm, jTimeSignatureNum, jTimeSignatureDen, primarySampleIdStr.c_str(), secondarySampleIdStr.c_str());

    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);

    gMetronomeState.enabled.store(jIsEnabled == JNI_TRUE);
    gMetronomeState.bpm.store(jBpm);
    gMetronomeState.timeSignatureNum.store(jTimeSignatureNum);
    gMetronomeState.timeSignatureDen.store(jTimeSignatureDen); // Denominator currently not used in scheduling logic but stored

    // Look up samples
    auto primaryIt = gSampleMap.find(primarySampleIdStr);
    if (primaryIt != gSampleMap.end()) {
        gMetronomeState.primaryBeatSample = &(primaryIt->second);
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: Primary sound '%s' found.", primarySampleIdStr.c_str());
    } else {
        gMetronomeState.primaryBeatSample = nullptr;
        if (!primarySampleIdStr.empty()) { // Log error only if an ID was provided but not found
             __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: Primary sound '%s' NOT found.", primarySampleIdStr.c_str());
        }
    }

    if (!secondarySampleIdStr.empty()) {
        auto secondaryIt = gSampleMap.find(secondarySampleIdStr);
        if (secondaryIt != gSampleMap.end()) {
            gMetronomeState.secondaryBeatSample = &(secondaryIt->second);
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: Secondary sound '%s' found.", secondarySampleIdStr.c_str());
        } else {
            gMetronomeState.secondaryBeatSample = nullptr;
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: Secondary sound '%s' NOT found.", secondarySampleIdStr.c_str());
        }
    } else {
        gMetronomeState.secondaryBeatSample = nullptr; // No secondary ID provided
    }

    // Ensure audioStreamSampleRate is set (should be done on initOboe)
    if (gMetronomeState.audioStreamSampleRate == 0 && oboeInitialized && outStream) {
         gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate());
         __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: audioStreamSampleRate was 0, updated to %u", gMetronomeState.audioStreamSampleRate);
    }


    gMetronomeState.updateSchedulingParameters(); // Recalculate framesPerBeat etc.

    if (gMetronomeState.enabled.load()) {
        // Reset counters for a clean start/restart
        gMetronomeState.samplesUntilNextBeat = 0; // Trigger beat on next callback cycle
        gMetronomeState.currentBeatInBar = gMetronomeState.timeSignatureNum.load(); // So that the first beat triggered is '1'
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome enabled. framesPerBeat=%llu. First beat scheduled.", (long long unsigned)gMetronomeState.framesPerBeat);

    } else {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome disabled.");
        // No need to reset counters if disabled, they'll be reset when re-enabled.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1setMetronomeVolume(
        JNIEnv *env,
        jobject /* thiz */,
        jfloat jVolume) {

    float volumeValue = static_cast<float>(jVolume);
    // Clamp volume to a reasonable range, e.g., 0.0 to 1.0 (or higher if allowing boost)
    if (volumeValue < 0.0f) volumeValue = 0.0f;
    if (volumeValue > 1.0f) volumeValue = 1.0f; // Assuming max 1.0 for now

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "native_setMetronomeVolume called: volume=%.2f",
                        volumeValue);

    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.volume.store(volumeValue);
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
