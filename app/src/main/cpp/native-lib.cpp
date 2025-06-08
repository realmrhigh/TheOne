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
// In a .h or .cpp file
#define APP_NAME "TheOne"

#include "dr_wav.h" // Include the dr_wav header
#include <vector>   // For std::vector
#include <mutex>    // For std::mutex
#include <cmath>    // For cosf, sinf for panning
#include <cstdint>  // For uint types
#include <memory>   // For std::unique_ptr, std::make_unique (used by audio_sample.h)
#include <random>   // For std::mt19937, std::random_device

#include "EnvelopeGenerator.h"
#include "LfoGenerator.h"


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

// --- C++ Data Structures Mirroring Kotlin Models (for JNI transfer) ---
// These will be populated from Kotlin objects passed via JNI.

struct SampleLayerCpp {
    std::string id;
    std::string sampleId; // ID of the sample from the SamplePool
    bool enabled = true;
    int velocityRangeMin = 0;
    int velocityRangeMax = 127;
    int tuningCoarseOffset = 0; // Semitones
    int tuningFineOffset = 0;   // Cents
    float volumeOffsetDb = 0.0f; // in Decibels
    float panOffset = 0.0f;     // -1.0 to 1.0

    // Default constructor
    SampleLayerCpp() = default;

    // Basic constructor for easier testing/setup later
    SampleLayerCpp(std::string sid, std::string sampId, int velMin, int velMax)
        : id(std::move(sid)), sampleId(std::move(sampId)),
          velocityRangeMin(velMin), velocityRangeMax(velMax) {}
};

enum class LayerTriggerRuleCpp {
    VELOCITY,
    CYCLE,
    RANDOM
};

// PlaybackModeCpp might also be needed if it affects C++ logic directly
enum class PlaybackModeCpp {
    ONE_SHOT,
    LOOP, // Assuming LOOP is for the whole pad, not individual sample looping here
    GATE
};


struct PadSettingsCpp {
    std::string id; // e.g., "Pad1"

    std::vector<SampleLayerCpp> layers;
    LayerTriggerRuleCpp layerTriggerRule = LayerTriggerRuleCpp::VELOCITY;
    int currentCycleLayerIndex = 0; // State for CYCLE trigger rule

    PlaybackModeCpp playbackMode = PlaybackModeCpp::ONE_SHOT;
    int tuningCoarse = 0;
    int tuningFine = 0;
    float volume = 1.0f; // Base volume
    float pan = 0.0f;    // Base pan
    int muteGroup = 0;
    int polyphony = 16; // Max active sounds for this pad

    // Envelope and LFO settings (using the Cpp versions defined in their respective headers)
    theone::audio::EnvelopeSettingsCpp ampEnvelope;
    // Optional envelopes: use a flag or a different approach if std::optional is not used
    bool hasFilterEnvelope = false;
    theone::audio::EnvelopeSettingsCpp filterEnvelope;
    bool hasPitchEnvelope = false;
    theone::audio::EnvelopeSettingsCpp pitchEnvelope;
    std::vector<theone::audio::LfoSettingsCpp> lfos;

    // Default constructor to initialize with some sensible defaults
    PadSettingsCpp() {
        // Example: Initialize ampEnvelope with default constructor of EnvelopeSettingsCpp
        ampEnvelope = theone::audio::EnvelopeSettingsCpp();
        // layers might be empty by default or pre-populated for testing
    }
};

// Map to store PadSettingsCpp for each pad, keyed by a string (e.g., "Track1_Pad1")
// This will be populated by a JNI function.
static std::map<std::string, PadSettingsCpp> gPadSettingsMap;
static std::mutex gPadSettingsMutex; // Mutex for gPadSettingsMap

// Random number generator for layer selection
static std::mt19937 gRandomEngine{std::random_device{}()};


// Placeholder for logging
//const char* APP_NAME = "TheOneNative";

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

            // --- Envelope and LFO Processing (per sound, before per-frame loop) ---
            float ampEnvValue = 1.0f;
            if (sound.ampEnvelopeGen) {
                ampEnvValue = sound.ampEnvelopeGen->process();
                if (!sound.ampEnvelopeGen->isActive() && ampEnvValue < 0.001f) { // Envelope finished and value is negligible
                    sound.isActive.store(false);
                }
            }

            float lfoPanMod = 0.0f;
            if (!sound.lfoGens.empty()) {
                for (auto& lfoGen : sound.lfoGens) {
                    if (lfoGen) {
                        float lfoValue = lfoGen->process();
                        // Example: First LFO modulates Pan. Real routing would be more complex.
                        if (&lfoGen == &sound.lfoGens.front()) {
                            lfoPanMod = lfoValue * 0.5f; // LFO swings pan by +/- 0.5
                        }
                    }
                }
            }

            float currentPan = sound.initialPan + lfoPanMod;
            currentPan = std::max(-1.0f, std::min(1.0f, currentPan));
            float panRad = (currentPan * 0.5f + 0.5f) * (static_cast<float>(M_PI) / 2.0f);

            float overallGain = sound.initialVolume * ampEnvValue;
            float finalGainL = overallGain * cosf(panRad);
            float finalGainR = overallGain * sinf(panRad);
            // --- End Envelope and LFO Processing ---

            for (int i = 0; i < numFrames; ++i) {
                if (!sound.isActive.load()) {
                    break;
                }

                // === START NEW SLICING/LOOPING LOGIC ===
                if (sound.useSlicing) {
                    if (sound.currentFrame >= sound.endFrame) {
                        if (sound.isLooping && sound.loopStartFrame < sound.loopEndFrame && sound.loopEndFrame <= sound.endFrame && sound.loopStartFrame < sound.endFrame) {
                            sound.currentFrame = sound.loopStartFrame;
                        } else {
                            sound.isActive.store(false);
                        }
                    }
                } else { // Original logic for non-sliced sounds
                    if (sound.currentFrame >= loadedSample->frameCount) {
                        sound.isActive.store(false);
                    }
                }

                if (!sound.isActive.load()) {
                     break;
                }
                if (sound.currentFrame >= loadedSample->frameCount) {
                     sound.isActive.store(false);
                     break;
                }
                // === END NEW SLICING/LOOPING LOGIC ===

                float leftSampleValue = 0.0f;
                float rightSampleValue = 0.0f;

                if (sampleChannels == 1) { // Mono sample
                    float sampleValue = loadedSample->audioData[sound.currentFrame];
                    leftSampleValue = sampleValue * finalGainL; // Apply final calculated gain
                    rightSampleValue = sampleValue * finalGainR;
                } else { // Stereo sample
                    float L = loadedSample->audioData[sound.currentFrame * sampleChannels];
                    float R = loadedSample->audioData[sound.currentFrame * sampleChannels + 1];
                    leftSampleValue = L * finalGainL; // Apply final calculated gain
                    rightSampleValue = R * finalGainR;
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
        jstring jSampleId, // Fallback if PadSettings not found or layer has no sampleId
        jstring jSliceId, // Can be null, currently not used with full PadSettings path
        jfloat velocity,  // Note velocity (0.0 to 1.0)
        jint coarseTune,  // Fallback
        jint fineTune,    // Fallback
        jfloat pan,       // Fallback
        jfloat volume,    // Fallback
        // New parameters matching AudioEngine.kt
        jint jPlaybackModeOrdinal,
        jfloat jAmpEnvAttackMs,
        jfloat jAmpEnvDecayMs,
        jfloat jAmpEnvSustainLevel,
        jfloat jAmpEnvReleaseMs
        // TODO: Add JNI params for filterEnv, pitchEnv, LFOs later
) {
    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    const char *nativeTrackId = env->GetStringUTFChars(jTrackId, nullptr);
    const char *nativePadId = env->GetStringUTFChars(jPadId, nullptr);
    std::string padKey = std::string(nativeTrackId) + "_" + std::string(nativePadId);
    env->ReleaseStringUTFChars(jTrackId, nativeTrackId);
    env->ReleaseStringUTFChars(jPadId, nativePadId);

    std::shared_ptr<PadSettingsCpp> padSettingsPtr;
    {
        std::lock_guard<std::mutex> lock(gPadSettingsMutex);
        auto it = gPadSettingsMap.find(padKey);
        if (it != gPadSettingsMap.end()) {
            padSettingsPtr = std::make_shared<PadSettingsCpp>(it->second);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "playPadSample: PadSettings not found for key %s. Playing direct sample (if provided) or failing.", padKey.c_str());
            // Fallback to direct sample playback if jSampleId is valid
            // This part can be removed if strict PadSettings usage is enforced.
            const char *fallbackSampleIdChars = env->GetStringUTFChars(jSampleId, nullptr);
            if (!fallbackSampleIdChars || strlen(fallbackSampleIdChars) == 0) {
                 env->ReleaseStringUTFChars(jSampleId, fallbackSampleIdChars);
                 return JNI_FALSE; // No settings, no fallback sample ID
            }
            SampleId fallbackSampleIdStr(fallbackSampleIdChars);
            env->ReleaseStringUTFChars(jSampleId, fallbackSampleIdChars);

            auto mapIt = gSampleMap.find(fallbackSampleIdStr);
            if (mapIt == gSampleMap.end()) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample (fallback): Sample ID '%s' not found.", fallbackSampleIdStr.c_str());
                return JNI_FALSE;
            }
            const theone::audio::LoadedSample* loadedSample = &(mapIt->second);
            if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
                __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample (fallback): Sample ID '%s' has no audio data.", fallbackSampleIdStr.c_str());
                return JNI_FALSE;
            }
            std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);
            gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan); // Using JNI params for vol/pan
            __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (fallback) added. Active sounds: %zu", fallbackSampleIdStr.c_str(), gActiveSounds.size());
            return JNI_TRUE;
        }
    }

    // If padSettingsPtr is valid, proceed with layer logic.
    if (!padSettingsPtr) {
         __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: padSettingsPtr is null after lock, should not happen if logic is correct.");
         return JNI_FALSE; // Should have been handled by return JNI_FALSE in the map lookup.
    }

    const SampleLayerCpp* selectedLayer = nullptr;
    if (padSettingsPtr->layers.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Pad %s has no layers defined.", padKey.c_str());
        return JNI_FALSE;
    }

    std::vector<const SampleLayerCpp*> enabledLayers;
    for (const auto& layer : padSettingsPtr->layers) {
        if (layer.enabled) {
            enabledLayers.push_back(&layer);
        }
    }

    if (enabledLayers.empty()) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "playPadSample: Pad %s has no ENABLED layers.", padKey.c_str());
        return JNI_FALSE;
    }

    float noteVelocity = velocity; // from JNI parameter (0.0 to 1.0)

    switch (padSettingsPtr->layerTriggerRule) {
        case LayerTriggerRuleCpp::VELOCITY: {
            int intVelocity = static_cast<int>(noteVelocity * 127.0f);
            for (const auto& layerPtr : enabledLayers) {
                if (intVelocity >= layerPtr->velocityRangeMin && intVelocity <= layerPtr->velocityRangeMax) {
                    selectedLayer = layerPtr;
                    break;
                }
            }
            if (!selectedLayer && !enabledLayers.empty()) {
                selectedLayer = enabledLayers.front();
                __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Velocity fallback: no layer matched velocity %d, picked first enabled.", intVelocity);
            }
            break;
        }
        case LayerTriggerRuleCpp::CYCLE: {
            std::lock_guard<std::mutex> cycleLock(gPadSettingsMutex);
            auto mapEntryIt = gPadSettingsMap.find(padKey);
            if (mapEntryIt != gPadSettingsMap.end()) {
                PadSettingsCpp& originalPadSettings = mapEntryIt->second;
                // Re-filter enabled layers from originalPadSettings in case they changed.
                // This is a bit complex; for now, assume enabledLayers from the shared_ptr copy is sufficient for indexing.
                // A truly robust solution might need to re-evaluate enabledLayers based on originalPadSettings.layers here.
                if (!enabledLayers.empty()) { // Use the previously filtered list from padSettingsPtr for selection.
                                              // The index is applied to this list.
                    originalPadSettings.currentCycleLayerIndex %= enabledLayers.size();
                    selectedLayer = enabledLayers[originalPadSettings.currentCycleLayerIndex];
                    originalPadSettings.currentCycleLayerIndex = (originalPadSettings.currentCycleLayerIndex + 1) % enabledLayers.size();
                }
            }
            break;
        }
        case LayerTriggerRuleCpp::RANDOM: {
            if (!enabledLayers.empty()) {
                std::uniform_int_distribution<> distrib(0, static_cast<int>(enabledLayers.size() - 1));
                selectedLayer = enabledLayers[distrib(gRandomEngine)];
            }
            break;
        }
    }

    if (!selectedLayer) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Could not select a layer for pad %s.", padKey.c_str());
        return JNI_FALSE;
    }

    SampleId resolvedSampleIdStr = selectedLayer->sampleId;
    if (resolvedSampleIdStr.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Selected layer for pad %s has an empty sampleId.", padKey.c_str());
        return JNI_FALSE;
    }

    auto mapIt = gSampleMap.find(resolvedSampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' (from layer) not found.", resolvedSampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' (from layer) has no audio data.", resolvedSampleIdStr.c_str());
        return JNI_FALSE;
    }

    float baseVolume = padSettingsPtr->volume;
    float basePan = padSettingsPtr->pan;
    float volumeOffsetGain = powf(10.0f, selectedLayer->volumeOffsetDb / 20.0f);
    float finalVolume = baseVolume * volumeOffsetGain;
    float finalPan = basePan + selectedLayer->panOffset;
    finalPan = std::max(-1.0f, std::min(1.0f, finalPan));

    // TODO: Apply coarse/fine tuning (padSettingsPtr->tuningCoarse/Fine + selectedLayer->tuningCoarse/FineOffset)
    // This is complex and involves changing playback speed or resampling. Deferred for now.

    theone::audio::PlayingSound soundToMove(loadedSample, noteInstanceIdStr, finalVolume, finalPan);

    float sr = outStream ? static_cast<float>(outStream->getSampleRate()) : 48000.0f;
    if (sr <= 0) sr = 48000.0f; // Safety net for sample rate

    // Construct ampEnvelopeFromParams using new JNI arguments
    theone::audio::EnvelopeSettingsCpp ampEnvelopeFromParams;
    // Preserve type and holdMs from PadSettings for now, as they are not passed via JNI yet.
    // If PadSettingsCpp is not found, these would need default values or be part of JNI args.
    // This path assumes padSettingsPtr is valid.
    ampEnvelopeFromParams.type = padSettingsPtr->ampEnvelope.type;
    ampEnvelopeFromParams.attackMs = jAmpEnvAttackMs;
    ampEnvelopeFromParams.holdMs = padSettingsPtr->ampEnvelope.holdMs;
    ampEnvelopeFromParams.decayMs = jAmpEnvDecayMs;
    ampEnvelopeFromParams.sustainLevel = jAmpEnvSustainLevel;
    ampEnvelopeFromParams.releaseMs = jAmpEnvReleaseMs;

    soundToMove.ampEnvelopeGen = std::make_unique<theone::audio::EnvelopeGenerator>();
    soundToMove.ampEnvelopeGen->configure(ampEnvelopeFromParams, sr, noteVelocity); // Use ampEnvelopeFromParams
    soundToMove.ampEnvelopeGen->triggerOn(noteVelocity);

    // TODO: Configure filterEnvelopeGen and pitchEnvelopeGen if padSettingsPtr->hasFilterEnvelope etc.

    for (const auto& lfoConfig : padSettingsPtr->lfos) {
        auto lfo = std::make_unique<theone::audio::LfoGenerator>();
        float tempo = 120.0f; // Default tempo
        // Lock gMetronomeStateMutex only if accessing gMetronomeState directly
        // Better to pass tempo as a parameter if possible, or have a shared tempo provider
        {
            std::lock_guard<std::mutex> metroLock(gMetronomeStateMutex); // Assuming direct access for now
            tempo = gMetronomeState.bpm.load();
        }
        if (tempo <= 0) tempo = 120.0f; // Safety net for tempo

        lfo->configure(lfoConfig, sr, tempo);
        lfo->retrigger();
        soundToMove.lfoGens.push_back(std::move(lfo));
    }

    // Slicing parameters (currently not set by PadSettings, but PlayingSound supports them)
    // If PadSettings were to include slice points for the selected layer's sample, they'd be applied here.
    // For now, it will play the full sample as per LoadedSample.
    // soundToMove.startFrame = ...; soundToMove.endFrame = ...; soundToMove.isLooping = ...; etc.

    std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);
    gActiveSounds.push_back(std::move(soundToMove));
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (Pad: %s, Layer: %s) added. Active sounds: %zu",
                        resolvedSampleIdStr.c_str(), padKey.c_str(), selectedLayer->id.c_str(), gActiveSounds.size());

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_theone_audio_AudioEngine_native_1playSampleSlice(
        JNIEnv *env, jobject,
        jstring jSampleId,
        jstring jNoteInstanceId,
        jfloat volume,
        jfloat pan,
        jint jSampleRate, // Sample rate passed from Kotlin (potentially for calculation if metadata not available)
        jlong jTrimStartMs,
        jlong jTrimEndMs,
        jlong jLoopStartMs,
        jlong jLoopEndMs,
        jboolean jIsLooping
) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr);
    std::string noteInstanceIdStr(nativeNoteInstanceId);
    env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);

    // 1. Retrieve LoadedSample and Actual Sample Rate
    auto mapIt = gSampleMap.find(sampleIdStr);
    if (mapIt == gSampleMap.end()) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' not found.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second);

    if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' has no audio data or zero frames.", sampleIdStr.c_str());
        return JNI_FALSE;
    }
    uint32_t actualSampleRate = loadedSample->format.sampleRate;
    if (actualSampleRate == 0) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playSampleSlice: Sample ID '%s' has an actual sample rate of 0. Cannot proceed.", sampleIdStr.c_str());
        return JNI_FALSE;
    }

    if (static_cast<uint32_t>(jSampleRate) != actualSampleRate) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                            "playSampleSlice: Kotlin sampleRate (%d) differs from actual sampleRate (%u) for ID '%s'. Using actual rate for calculations.",
                            jSampleRate, actualSampleRate, sampleIdStr.c_str());
    }

    // 2. Convert Milliseconds to Frames
    size_t startFrame = (jTrimStartMs * actualSampleRate) / 1000;
    size_t endFrame = (jTrimEndMs * actualSampleRate) / 1000; // If jTrimEndMs is 0, endFrame becomes 0. Constructor handles this.
    size_t loopStartFrame = 0;
    size_t loopEndFrame = 0;

    if (jIsLooping == JNI_TRUE) {
        loopStartFrame = (jLoopStartMs * actualSampleRate) / 1000;
        loopEndFrame = (jLoopEndMs * actualSampleRate) / 1000; // If jLoopEndMs is 0, loopEndFrame becomes 0. Constructor handles this.
    }

    // 5. Logging (Initial values)
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "playSampleSlice: ID='%s', SR_kotlin=%d, SR_actual=%u, Vol=%.2f, Pan=%.2f, TrimMs:[%lld-%lld], LoopMs:[%lld-%lld], Loop:%d",
                        sampleIdStr.c_str(), jSampleRate, actualSampleRate, volume, pan,
                        (long long)jTrimStartMs, (long long)jTrimEndMs,
                        (long long)jLoopStartMs, (long long)jLoopEndMs, jIsLooping == JNI_TRUE);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Calculated initial frames: Start=%zu, End=%zu, LoopStart=%zu, LoopEnd=%zu (before constructor adjustment for 0 end/loopEnd)",
                        startFrame, endFrame, loopStartFrame, loopEndFrame);


    // 3. Validate Frame Indices
    // Note: PlayingSound constructor also does some validation, especially for endFrame=0
    if (startFrame >= loadedSample->frameCount) {
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                            "playSampleSlice: Calculated startFrame (%zu) is out of bounds for sample '%s' (frameCount: %zu).",
                            startFrame, sampleIdStr.c_str(), loadedSample->frameCount);
        return JNI_FALSE;
    }

    // If endFrame was calculated from a non-zero jTrimEndMs, and it's beyond frameCount.
    // If endFrame is 0 (from jTrimEndMs = 0), constructor will set it to frameCount.
    if (endFrame != 0 && endFrame > loadedSample->frameCount) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                            "playSampleSlice: Calculated endFrame (%zu) exceeds sample frameCount (%zu) for '%s'. Clamping to frameCount.",
                            endFrame, loadedSample->frameCount, sampleIdStr.c_str());
        endFrame = loadedSample->frameCount;
    }
     if (startFrame >= endFrame && endFrame != 0) { // endFrame == 0 means play to end, handled by constructor
        __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                            "playSampleSlice: startFrame (%zu) must be less than endFrame (%zu) for '%s'.",
                            startFrame, endFrame, sampleIdStr.c_str());
        return JNI_FALSE;
    }


    if (jIsLooping == JNI_TRUE) {
        if (loopStartFrame >= (loopEndFrame == 0 ? loadedSample->frameCount : loopEndFrame) ) {
             __android_log_print(ANDROID_LOG_ERROR, APP_NAME,
                                "playSampleSlice: loopStartFrame (%zu) must be less than loopEndFrame (%zu, effective: %zu) for '%s'.",
                                loopStartFrame, loopEndFrame, (loopEndFrame == 0 ? loadedSample->frameCount : loopEndFrame), sampleIdStr.c_str());
            return JNI_FALSE;
        }
        // Loop points should be within the main trim region (or sample bounds if endFrame is 0)
        size_t effectiveEndFrame = (endFrame == 0) ? loadedSample->frameCount : endFrame;
        if (loopStartFrame < startFrame || loopEndFrame > effectiveEndFrame) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                                "playSampleSlice: Loop region [%zu-%zu] is outside effective play region [%zu-%zu] for '%s'. Adjusting or error might occur.",
                                loopStartFrame, loopEndFrame, startFrame, effectiveEndFrame, sampleIdStr.c_str());
            // Depending on desired behavior, either clamp loop points or return false.
            // For now, we'll let the PlayingSound constructor handle potential inconsistencies,
            // but it's good to log.
        }
         if (loopEndFrame != 0 && loopEndFrame > loadedSample->frameCount) {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME,
                                "playSampleSlice: Calculated loopEndFrame (%zu) exceeds sample frameCount (%zu) for '%s'. Clamping in constructor.",
                                loopEndFrame, loadedSample->frameCount, sampleIdStr.c_str());
            // Constructor will clamp this.
        }
    }

    // 4. Instantiate PlayingSound
    std::lock_guard<std::mutex> lock(gActiveSoundsMutex);
    gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan,
                               startFrame, endFrame,
                               loopStartFrame, loopEndFrame,
                               jIsLooping == JNI_TRUE);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
                        "Sample '%s' (slice) added. Active sounds: %zu. Effective playback frames after constructor: current=%zu, start=%zu, end=%zu, loopStart=%zu, loopEnd=%zu, looping=%d, useSlicing=%d",
                        sampleIdStr.c_str(), gActiveSounds.size(),
                        gActiveSounds.back().currentFrame, gActiveSounds.back().startFrame, gActiveSounds.back().endFrame,
                        gActiveSounds.back().loopStartFrame, gActiveSounds.back().loopEndFrame,
                        gActiveSounds.back().isLooping, gActiveSounds.back().useSlicing);

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

extern "C" JNIEXPORT jint JNICALL
Java_com_example_theone_audio_AudioEngine_native_1getSampleRate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jSampleId) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr);
    SampleId sampleIdStr(nativeSampleId);
    env->ReleaseStringUTFChars(jSampleId, nativeSampleId);

    auto it = gSampleMap.find(sampleIdStr);
    if (it != gSampleMap.end()) {
        const theone::audio::LoadedSample* loadedSample = &(it->second);
        if (loadedSample && loadedSample->format.sampleRate > 0) {
            return static_cast<jint>(loadedSample->format.sampleRate);
        } else {
            __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_getSampleRate: Sample '%s' found but has invalid sample rate: %u", sampleIdStr.c_str(), loadedSample ? loadedSample->format.sampleRate : 0);
            return 0; // Indicate error or invalid rate
        }
    }
    __android_log_print(ANDROID_LOG_WARN, APP_NAME, "native_getSampleRate: Sample ID '%s' not found.", sampleIdStr.c_str());
    return 0; // Indicate error (sample not found)
}

// ... Any other JNI functions needed ...

// Prototype for new JNI function (implementation deferred to Task 2.5)
extern "C" JNIEXPORT void JNICALL
Java_com_example_theone_audio_AudioEngine_native_1updatePadSettings(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jTrackId,
        jstring jPadId,
        jobject /* PadSettings Kotlin object */ jPadSettings) {
    // TODO: Implement JNI conversion from jPadSettings (Kotlin PadSettings object)
    //       to PadSettingsCpp, then update gPadSettingsMap.
    // Example structure:
    // const char *nativeTrackId = env->GetStringUTFChars(jTrackId, nullptr);
    // const char *nativePadId = env->GetStringUTFChars(jPadId, nullptr);
    // if (!nativeTrackId || !nativePadId) { /* error handling */ return; }
    // std::string key = std::string(nativeTrackId) + "_" + std::string(nativePadId);
    // env->ReleaseStringUTFChars(jTrackId, nativeTrackId);
    // env->ReleaseStringUTFChars(jPadId, nativePadId);
    //
    // PadSettingsCpp cppSettings;
    // // --- Populate cppSettings by calling JNI methods on jPadSettings ---
    // // This is the complex part that requires mapping each field.
    // // e.g., get layers, trigger rule, envelopes, LFOs from jPadSettings.
    // // This will involve:
    // //   env->GetObjectClass(jPadSettings)
    // //   env->GetFieldID(...) for various fields
    // //   env->GetObjectField(...) for nested objects (layers, envelopes, LFOs)
    // //   Iterating over Java Lists (for layers, LFOs) and converting each element.
    //
    // {
    //     std::lock_guard<std::mutex> lock(gPadSettingsMutex);
    //     gPadSettingsMap[key] = cppSettings; // Or use std::move if cppSettings is built efficiently
    //     __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Updated PadSettings for key: %s", key.c_str());
    // }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_updatePadSettings called (STUBBED) for %s_%s",
                        env->GetStringUTFChars(jTrackId, nullptr), env->GetStringUTFChars(jPadId, nullptr));
}
