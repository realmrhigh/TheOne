// [ALL INCLUDES AS PROVIDED BY USER]
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
#include <vector>   // For std::vector
#include <mutex>    // For std::mutex
#include <atomic>   // For std::atomic
#include <cmath>    // For cosf, sinf for panning
#include <cstdint>  // For uint types
#include <algorithm> // Required for std::remove_if

// [DR_WAV and M_PI DEFINES AS PROVIDED BY USER]
#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"
#ifndef M_PI
#define M_PI (3.14159265358979323846f)
#endif

// Global constants and type aliases
const char* APP_NAME = "TheOneNative";
using SampleId = std::string;
using SampleMap = std::map<SampleId, theone::audio::LoadedSample>;

// All static global variable declarations
static oboe::ManagedStream outStream;
static bool oboeInitialized = false;
static SampleMap gSampleMap;
static std::vector<theone::audio::PlayingSound> gActiveSounds;
static std::mutex gActiveSoundsMutex;
static theone::audio::MetronomeState gMetronomeState;
static std::mutex gMetronomeStateMutex;
static oboe::ManagedStream mInputStream;
static std::atomic<bool> mIsRecording {false}; // Brace init for bool is fine
static std::atomic<float> mPeakRecordingLevel(0.0f); // Corrected initialization
static int mRecordingFileDescriptor = -1;
static drwav mWavWriter;
static bool mWavWriterInitialized = false;
static std::string mCurrentRecordingFilePath = "";
static std::mutex gRecordingStateMutex;

// Callback class definitions
class MyAudioCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {
        float *outputBuffer = static_cast<float*>(audioData);
        memset(outputBuffer, 0, sizeof(float) * numFrames * oboeStream->getChannelCount());
        std::vector<theone::audio::PlayingSound> newMetronomeSounds;
        {
            std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex);
            if (gMetronomeState.enabled.load() && gMetronomeState.framesPerBeat > 0) {
                if (gMetronomeState.audioStreamSampleRate == 0) { }
                for (int i = 0; i < numFrames; ++i) {
                    if (gMetronomeState.samplesUntilNextBeat == 0) {
                        gMetronomeState.currentBeatInBar++;
                        if (gMetronomeState.currentBeatInBar > gMetronomeState.timeSignatureNum.load() || gMetronomeState.timeSignatureNum.load() == 0) {
                            gMetronomeState.currentBeatInBar = 1;
                        }
                        const theone::audio::LoadedSample* soundToPlay = nullptr;
                        bool isPrimary = (gMetronomeState.currentBeatInBar == 1);
                        if (isPrimary && gMetronomeState.primaryBeatSample) { soundToPlay = gMetronomeState.primaryBeatSample; }
                        else if (!isPrimary && gMetronomeState.secondaryBeatSample) { soundToPlay = gMetronomeState.secondaryBeatSample; }
                        else if (gMetronomeState.primaryBeatSample) { soundToPlay = gMetronomeState.primaryBeatSample; }
                        if (soundToPlay) {
                            std::string instanceId = "m_tick_" + std::to_string(gMetronomeState.currentBeatInBar);
                            newMetronomeSounds.emplace_back(soundToPlay, instanceId, gMetronomeState.volume.load(), 0.0f );
                        }
                        gMetronomeState.samplesUntilNextBeat = gMetronomeState.framesPerBeat;
                    }
                    gMetronomeState.samplesUntilNextBeat--;
                }
            }
        }
        std::lock_guard<std::mutex> activeSoundsLock(gActiveSoundsMutex);
        if (!newMetronomeSounds.empty()) {
            for (const auto& metroSound : newMetronomeSounds) { gActiveSounds.push_back(metroSound); }
        }
        for (auto soundIt = gActiveSounds.begin(); soundIt != gActiveSounds.end(); ) {
            if (!soundIt->isActive) { // MODIFIED
                ++soundIt; continue;
            }
            theone::audio::PlayingSound &sound = *soundIt;
            const theone::audio::LoadedSample* loadedSample = sound.loadedSamplePtr;
            if (!loadedSample || loadedSample->audioData.empty()) {
                sound.isActive = false; // MODIFIED
                ++soundIt; continue;
            }
            int channels = oboeStream->getChannelCount();
            int sampleChannels = loadedSample->format.channels;
            for (int i = 0; i < numFrames; ++i) {
                if (!sound.isActive) { break; } // MODIFIED
                size_t currentReadFrame = sound.currentFrame;
                if (currentReadFrame >= loadedSample->frameCount) {
                    sound.isActive = false; break; } // MODIFIED
                float leftSampleValue = 0.0f; float rightSampleValue = 0.0f;
                if (sampleChannels == 1) {
                    float sampleValue = loadedSample->audioData[currentReadFrame];
                    leftSampleValue = sampleValue * sound.gainLeft; rightSampleValue = sampleValue * sound.gainRight;
                } else {
                    float L = loadedSample->audioData[currentReadFrame * sampleChannels];
                    float R = loadedSample->audioData[currentReadFrame * sampleChannels + 1];
                    leftSampleValue = L * sound.gainLeft; rightSampleValue = R * sound.gainRight;
                }
                if (channels == 2) {
                    outputBuffer[i * 2] += leftSampleValue; outputBuffer[i * 2 + 1] += rightSampleValue;
                } else if (channels == 1) {
                    outputBuffer[i] += (leftSampleValue + rightSampleValue) * 0.5f;
                }
                sound.currentFrame++;
            }
            ++soundIt;
        }
        gActiveSounds.erase( std::remove_if(gActiveSounds.begin(), gActiveSounds.end(), [](const theone::audio::PlayingSound& s) { return !s.isActive; }), gActiveSounds.end()); // MODIFIED
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorBeforeClose: %s", oboe::convertToText(error)); }
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe ErrorAfterClose: %s", oboe::convertToText(error)); }
};

class MyAudioInputCallback : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady( oboe::AudioStream *inputStream, void *audioData, int32_t numFrames) override {
        if (mIsRecording.load() && mWavWriterInitialized) {
            std::lock_guard<std::mutex> lock(gRecordingStateMutex);
            if (!mIsRecording.load() || !mWavWriterInitialized) { return oboe::DataCallbackResult::Continue; }
            const float* inputBuffer = static_cast<const float*>(audioData);
            drwav_uint64 framesWritten = drwav_write_pcm_frames(&mWavWriter, numFrames, inputBuffer);
            if (framesWritten != static_cast<drwav_uint64>(numFrames)) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback: Failed to write all PCM frames to WAV. Wrote %llu/%d", framesWritten, numFrames); }
            float currentMaxAbs = 0.0f;
            int numSamplesInBlock = numFrames * inputStream->getChannelCount();
            for (int i = 0; i < numSamplesInBlock; ++i) {
                float absValue = std::abs(inputBuffer[i]);
                if (absValue > currentMaxAbs) { currentMaxAbs = absValue; }
            }
            float previousPeak = mPeakRecordingLevel.load();
            if (currentMaxAbs > previousPeak) { mPeakRecordingLevel.store(currentMaxAbs); }
        }
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorBeforeClose: %s", oboe::convertToText(error)); }
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "InputCallback ErrorAfterClose: %s", oboe::convertToText(error)); }
};

static MyAudioCallback myCallback;
static MyAudioInputCallback myInputCallback;

extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1initOboe(JNIEnv* env, jobject /* thiz */) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_initOboe called");
    if (oboeInitialized) { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe already initialized."); return JNI_TRUE; }
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)->setFormat(oboe::AudioFormat::Float)->setChannelCount(oboe::ChannelCount::Stereo)->setSampleRate(oboe::kUnspecified)->setCallback(&myCallback);
    oboe::Result result = builder.openManagedStream(outStream);
    if (result == oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe stream opened successfully.");
        result = outStream->requestStart();
        if (result != oboe::Result::OK) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to start Oboe stream: %s", oboe::convertToText(result)); outStream->close(); oboeInitialized = false; return JNI_FALSE; }
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe stream started successfully. SampleRate: %d, BufferSize: %d, API: %s", outStream->getSampleRate(), outStream->getFramesPerBurst(), oboe::convertToText(outStream->getAudioApi()));
        oboeInitialized = true;
        { std::lock_guard<std::mutex> metronomeLock(gMetronomeStateMutex); gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate()); gMetronomeState.updateSchedulingParameters(); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: stream sample rate set to %u", gMetronomeState.audioStreamSampleRate); }
        return JNI_TRUE;
    } else { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to open Oboe stream: %s", oboe::convertToText(result)); oboeInitialized = false; return JNI_FALSE; }
}
extern "C" JNIEXPORT void JNICALL Java_com_example_theone_audio_AudioEngine_native_1shutdownOboe(JNIEnv* env, jobject /* thiz */) {
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_shutdownOboe called");
    if (outStream && oboeInitialized) {
        oboe::Result result = outStream->requestStop(); if (result != oboe::Result::OK) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Error stopping oboe stream: %s", oboe::convertToText(result)); }
        result = outStream->close(); if (result != oboe::Result::OK) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Error closing oboe stream: %s", oboe::convertToText(result)); }
        __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe stream stopped and closed.");
    }
    oboeInitialized = false;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1isOboeInitialized(JNIEnv* env, jobject /* thiz */) {
    return static_cast<jboolean>(oboeInitialized && outStream && outStream->getState() != oboe::StreamState::Closed);
}
extern "C" JNIEXPORT jfloat JNICALL Java_com_example_theone_audio_AudioEngine_native_1getOboeReportedLatencyMillis(JNIEnv* env, jobject /* thiz */) {
    if (oboeInitialized && outStream) { oboe::ResultWithValue<double> latency = outStream->calculateLatencyMillis(); if (latency) { return static_cast<jfloat>(latency.value()); } else { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Error calculating latency: %s", oboe::convertToText(latency.error())); } } return -1.0f;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_theone_audio_AudioEngine_native_1stringFromJNI(JNIEnv* env, jobject /* thiz */) {
    std::string hello = "Hello from C++ (AudioEngine)"; __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "%s", hello.c_str());
    oboe::AudioApi api = oboe::AudioApi::AAudio; if (outStream && outStream->getAudioApi() != oboe::AudioApi::Unspecified) { api = outStream->getAudioApi(); } __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Oboe AudioApi in use or default: %s", oboe::convertToText(api)); return env->NewStringUTF(hello.c_str());
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1loadSampleToMemory( JNIEnv *env, jobject /* thiz */, jstring jSampleId, jint fd, jlong offset, jlong length) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr); if (!nativeSampleId) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "loadSampleToMemory: Failed to get sample ID string."); return JNI_FALSE; }
    SampleId sampleIdStr(nativeSampleId); env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "loadSampleToMemory called for ID: %s, FD: %d, Offset: %lld, Length: %lld", sampleIdStr.c_str(), fd, offset, length);
    if (gSampleMap.count(sampleIdStr)) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Sample ID '%s' already loaded.", sampleIdStr.c_str()); return JNI_TRUE; }
    if (offset > 0) { if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) == -1) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to lseek to offset %lld for FD %d: %s", offset, fd, strerror(errno)); return JNI_FALSE; } }
    theone::audio::LoadedSample sample; sample.id = sampleIdStr;
    unsigned int channels; unsigned int sampleRate; drwav_uint64 totalFrameCount; float* pSampleData = nullptr;
    pSampleData = drwav_open_file_descriptor_and_read_pcm_frames_f32(fd, &channels, &sampleRate, &totalFrameCount, NULL);
    if (pSampleData == nullptr) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Failed to decode WAV from FD %d. dr_wav error.", fd); return JNI_FALSE; }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Successfully decoded WAV: %u Ch, %u Hz, %llu Frames", channels, sampleRate, totalFrameCount);
    sample.format.channels = static_cast<uint16_t>(channels); sample.format.sampleRate = static_cast<uint32_t>(sampleRate); sample.format.bitDepth = 32; sample.frameCount = static_cast<size_t>(totalFrameCount);
    sample.audioData.assign(pSampleData, pSampleData + (totalFrameCount * channels)); drwav_free(pSampleData, nullptr);
    gSampleMap[sampleIdStr] = sample; __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' loaded and processed. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size()); return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1isSampleLoaded( JNIEnv *env, jobject /* thiz */, jstring jSampleId) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr); if (!nativeSampleId) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "isSampleLoaded: Failed to get sample ID string."); return JNI_FALSE; }
    SampleId sampleIdStr(nativeSampleId); env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    if (gSampleMap.count(sampleIdStr)) { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "isSampleLoaded: Sample '%s' is loaded.", sampleIdStr.c_str()); return JNI_TRUE; } else { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "isSampleLoaded: Sample '%s' is NOT loaded.", sampleIdStr.c_str()); return JNI_FALSE; }
}
extern "C" JNIEXPORT void JNICALL Java_com_example_theone_audio_AudioEngine_native_1unloadSample( JNIEnv *env, jobject /* thiz */, jstring jSampleId) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr); if (!nativeSampleId) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "unloadSample: Failed to get sample ID string."); return; }
    SampleId sampleIdStr(nativeSampleId); env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    auto it = gSampleMap.find(sampleIdStr); if (it != gSampleMap.end()) { gSampleMap.erase(it); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "unloadSample: Sample '%s' unloaded. Map size: %zu", sampleIdStr.c_str(), gSampleMap.size()); } else { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "unloadSample: Sample '%s' not found, cannot unload.", sampleIdStr.c_str()); }
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1playPadSample( JNIEnv *env, jobject /* thiz */, jstring jSampleId, jstring jNoteInstanceId, jfloat volume, jfloat pan) {
    const char *nativeSampleId = env->GetStringUTFChars(jSampleId, nullptr); if (!nativeSampleId) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Failed to get sample ID string."); return JNI_FALSE; } SampleId sampleIdStr(nativeSampleId); env->ReleaseStringUTFChars(jSampleId, nativeSampleId);
    const char *nativeNoteInstanceId = env->GetStringUTFChars(jNoteInstanceId, nullptr); if (!nativeNoteInstanceId) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Failed to get note instance ID string."); return JNI_FALSE; } std::string noteInstanceIdStr(nativeNoteInstanceId); env->ReleaseStringUTFChars(jNoteInstanceId, nativeNoteInstanceId);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "playPadSample called: sampleID='%s', instanceID='%s', vol=%.2f, pan=%.2f", sampleIdStr.c_str(), noteInstanceIdStr.c_str(), volume, pan);
    auto mapIt = gSampleMap.find(sampleIdStr); if (mapIt == gSampleMap.end()) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' not found in gSampleMap.", sampleIdStr.c_str()); return JNI_FALSE; }
    const theone::audio::LoadedSample* loadedSample = &(mapIt->second); if (loadedSample->audioData.empty() || loadedSample->frameCount == 0) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "playPadSample: Sample ID '%s' has no audio data or zero frames.", sampleIdStr.c_str()); return JNI_FALSE; }
    std::lock_guard<std::mutex> lock(gActiveSoundsMutex); gActiveSounds.emplace_back(loadedSample, noteInstanceIdStr, volume, pan);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Sample '%s' (instance '%s') added to active sounds. Active sounds count: %zu", sampleIdStr.c_str(), noteInstanceIdStr.c_str(), gActiveSounds.size()); return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_com_example_theone_audio_AudioEngine_native_1setMetronomeState( JNIEnv *env, jobject /* thiz */, jboolean jIsEnabled, jfloat jBpm, jint jTimeSignatureNum, jint jTimeSignatureDen, jstring jPrimarySoundSampleId, jstring jSecondarySoundSampleId) {
    const char *primarySampleIdChars = env->GetStringUTFChars(jPrimarySoundSampleId, nullptr); SampleId primarySampleIdStr = primarySampleIdChars ? primarySampleIdChars : ""; if (primarySampleIdChars) env->ReleaseStringUTFChars(jPrimarySoundSampleId, primarySampleIdChars);
    const char *secondarySampleIdChars = nullptr; SampleId secondarySampleIdStr = ""; if (jSecondarySoundSampleId != nullptr) { secondarySampleIdChars = env->GetStringUTFChars(jSecondarySoundSampleId, nullptr); secondarySampleIdStr = secondarySampleIdChars ? secondarySampleIdChars : ""; if (secondarySampleIdChars) env->ReleaseStringUTFChars(jSecondarySoundSampleId, secondarySampleIdChars); }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_setMetronomeState called: enabled=%d, bpm=%.2f, timeSig=%d/%d, primaryID='%s', secondaryID='%s'", jIsEnabled, jBpm, jTimeSignatureNum, jTimeSignatureDen, primarySampleIdStr.c_str(), secondarySampleIdStr.c_str());
    std::lock_guard<std::mutex> lock(gMetronomeStateMutex);
    gMetronomeState.enabled.store(jIsEnabled == JNI_TRUE); gMetronomeState.bpm.store(jBpm); gMetronomeState.timeSignatureNum.store(jTimeSignatureNum); gMetronomeState.timeSignatureDen.store(jTimeSignatureDen);
    auto primaryIt = gSampleMap.find(primarySampleIdStr); if (primaryIt != gSampleMap.end()) { gMetronomeState.primaryBeatSample = &(primaryIt->second); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: Primary sound '%s' found.", primarySampleIdStr.c_str()); } else { gMetronomeState.primaryBeatSample = nullptr; if (!primarySampleIdStr.empty()) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: Primary sound '%s' NOT found.", primarySampleIdStr.c_str()); } }
    if (!secondarySampleIdStr.empty()) { auto secondaryIt = gSampleMap.find(secondarySampleIdStr); if (secondaryIt != gSampleMap.end()) { gMetronomeState.secondaryBeatSample = &(secondaryIt->second); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome: Secondary sound '%s' found.", secondarySampleIdStr.c_str()); } else { gMetronomeState.secondaryBeatSample = nullptr; __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: Secondary sound '%s' NOT found.", secondarySampleIdStr.c_str()); } } else { gMetronomeState.secondaryBeatSample = nullptr; }
    if (gMetronomeState.audioStreamSampleRate == 0 && oboeInitialized && outStream) { gMetronomeState.audioStreamSampleRate = static_cast<uint32_t>(outStream->getSampleRate()); __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Metronome: audioStreamSampleRate was 0, updated to %u", gMetronomeState.audioStreamSampleRate); }
    gMetronomeState.updateSchedulingParameters();
    if (gMetronomeState.enabled.load()) { gMetronomeState.samplesUntilNextBeat = 0; gMetronomeState.currentBeatInBar = gMetronomeState.timeSignatureNum.load(); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome enabled. framesPerBeat=%llu. First beat scheduled.", (long long unsigned)gMetronomeState.framesPerBeat); } else { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Metronome disabled."); }
}
extern "C" JNIEXPORT void JNICALL Java_com_example_theone_audio_AudioEngine_native_1setMetronomeVolume( JNIEnv *env, jobject /* thiz */, jfloat jVolume) {
    float volumeValue = static_cast<float>(jVolume); if (volumeValue < 0.0f) volumeValue = 0.0f; if (volumeValue > 1.0f) volumeValue = 1.0f;
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_setMetronomeVolume called: volume=%.2f", volumeValue);
    std::lock_guard<std::mutex> lock(gMetronomeStateMutex); gMetronomeState.volume.store(volumeValue);
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1startAudioRecording( JNIEnv *env, jobject /* thiz */, jint jFd, jstring jStoragePathForMetadata, jint jSampleRate, jint jChannels) {
    std::lock_guard<std::mutex> lock(gRecordingStateMutex);
    if (mIsRecording.load()) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "startAudioRecording: Already recording."); if (jFd >= 0) close(jFd); return JNI_FALSE; }
    const char* pathChars = env->GetStringUTFChars(jStoragePathForMetadata, nullptr); if (pathChars) { mCurrentRecordingFilePath = pathChars; env->ReleaseStringUTFChars(jStoragePathForMetadata, pathChars); } else { mCurrentRecordingFilePath = ""; }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "native_startAudioRecording called. FD: %d, Path: %s, SR: %d, Ch: %d", jFd, mCurrentRecordingFilePath.c_str(), jSampleRate, jChannels);
    if (jFd < 0) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Invalid file descriptor provided (%d).", jFd); mCurrentRecordingFilePath = ""; return JNI_FALSE; }
    mRecordingFileDescriptor = jFd;
    drwav_data_format wavFormat; wavFormat.container = drwav_container_riff; wavFormat.format = DR_WAVE_FORMAT_IEEE_FLOAT; wavFormat.channels = static_cast<uint32_t>(jChannels); wavFormat.sampleRate = static_cast<uint32_t>(jSampleRate); wavFormat.bitsPerSample = 32;
    if (!drwav_init_file_descriptor_write(&mWavWriter, &wavFormat, mRecordingFileDescriptor, nullptr)) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to initialize drwav writer for FD: %d", mRecordingFileDescriptor); close(mRecordingFileDescriptor); mRecordingFileDescriptor = -1; mCurrentRecordingFilePath = ""; return JNI_FALSE; }
    mWavWriterInitialized = true; __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer initialized for FD: %d", mRecordingFileDescriptor);
    oboe::AudioStreamBuilder builder; builder.setDirection(oboe::Direction::Input)->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)->setFormat(oboe::AudioFormat::Float)->setChannelCount(static_cast<oboe::ChannelCount>(jChannels))->setSampleRate(static_cast<int32_t>(jSampleRate))->setInputPreset(oboe::InputPreset::VoiceRecognition)->setCallback(&myInputCallback);
    oboe::Result result = builder.openManagedStream(mInputStream);
    if (result != oboe::Result::OK) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to open Oboe input stream: %s", oboe::convertToText(result)); drwav_uninit(&mWavWriter); mWavWriterInitialized = false; close(mRecordingFileDescriptor); mRecordingFileDescriptor = -1; mCurrentRecordingFilePath = ""; return JNI_FALSE; }
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream opened. SR: %d, Ch: %d, Format: %s", mInputStream->getSampleRate(), mInputStream->getChannelCount(), oboe::convertToText(mInputStream->getFormat()));
    if (mInputStream->getFormat() != oboe::AudioFormat::Float || mInputStream->getSampleRate() != static_cast<int32_t>(jSampleRate) || mInputStream->getChannelCount() != static_cast<oboe::ChannelCount>(jChannels)) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Oboe input stream format mismatch. Requested SR:%d, Ch:%d, Fmt:Float. Actual SR:%d, Ch:%d, Fmt:%s", jSampleRate, jChannels, mInputStream->getSampleRate(), mInputStream->getChannelCount(), oboe::convertToText(mInputStream->getFormat())); if (mInputStream->getFormat() != oboe::AudioFormat::Float) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Oboe input stream NOT float. Cannot proceed with current dr_wav setup."); mInputStream->close(); drwav_uninit(&mWavWriter); mWavWriterInitialized = false; close(mRecordingFileDescriptor); mRecordingFileDescriptor = -1; mCurrentRecordingFilePath = ""; return JNI_FALSE; } }
    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "startAudioRecording: Failed to start Oboe input stream: %s", oboe::convertToText(result)); mInputStream->close(); drwav_uninit(&mWavWriter); mWavWriterInitialized = false; close(mRecordingFileDescriptor); mRecordingFileDescriptor = -1; mCurrentRecordingFilePath = ""; return JNI_FALSE; }
    mIsRecording.store(true); mPeakRecordingLevel.store(0.0f); __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording started successfully."); return JNI_TRUE;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_example_theone_audio_AudioEngine_native_1stopAudioRecording( JNIEnv *env, jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gRecordingStateMutex);
    if (!mIsRecording.load()) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Not recording."); return nullptr; }
    mIsRecording.store(false);
    if (mInputStream) { oboe::Result closeResult = mInputStream->requestStop(); if (closeResult != oboe::Result::OK) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Error stopping Oboe input stream: %s", oboe::convertToText(closeResult)); } closeResult = mInputStream->close(); if (closeResult != oboe::Result::OK) { __android_log_print(ANDROID_LOG_WARN, APP_NAME, "stopAudioRecording: Error closing Oboe input stream: %s", oboe::convertToText(closeResult)); } else { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Oboe input stream stopped and closed."); } }
    drwav_uint64 totalFramesWritten = 0; std::string tempRecordingPath = mCurrentRecordingFilePath;
    if (mWavWriterInitialized) { totalFramesWritten = mWavWriter.totalPCMFrameCount; if (!drwav_uninit(&mWavWriter)) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to uninitialize drwav writer."); } else { __android_log_print(ANDROID_LOG_INFO, APP_NAME, "dr_wav writer uninitialized. Total frames: %llu", totalFramesWritten); } mWavWriterInitialized = false; }
    mRecordingFileDescriptor = -1; mCurrentRecordingFilePath = "";
    jstring jRecordedPath = env->NewStringUTF(tempRecordingPath.c_str());
    jclass longClass = env->FindClass("java/lang/Long"); if (!longClass) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find java/lang/Long class."); env->DeleteLocalRef(jRecordedPath); return nullptr; }
    jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V"); if (!longConstructor) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find Long constructor."); env->DeleteLocalRef(jRecordedPath); env->DeleteLocalRef(longClass); return nullptr; }
    jobject jTotalFrames = env->NewObject(longClass, longConstructor, static_cast<jlong>(totalFramesWritten)); if (!jTotalFrames) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to create Long object for frames."); env->DeleteLocalRef(jRecordedPath); env->DeleteLocalRef(longClass); return nullptr; }
    jclass objectClass = env->FindClass("java/lang/Object"); if (!objectClass) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to find java/lang/Object class."); env->DeleteLocalRef(jRecordedPath); env->DeleteLocalRef(jTotalFrames); env->DeleteLocalRef(longClass); return nullptr; }
    jobjectArray resultArray = env->NewObjectArray(2, objectClass, nullptr); if (!resultArray) { __android_log_print(ANDROID_LOG_ERROR, APP_NAME, "stopAudioRecording: Failed to create result Object array."); env->DeleteLocalRef(jRecordedPath); env->DeleteLocalRef(jTotalFrames); env->DeleteLocalRef(longClass); env->DeleteLocalRef(objectClass); return nullptr; }
    env->SetObjectArrayElement(resultArray, 0, jRecordedPath); env->SetObjectArrayElement(resultArray, 1, jTotalFrames);
    env->DeleteLocalRef(jRecordedPath); env->DeleteLocalRef(jTotalFrames); env->DeleteLocalRef(longClass); env->DeleteLocalRef(objectClass);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "Audio recording stopped successfully. Path: %s, Frames: %llu", tempRecordingPath.c_str(), totalFramesWritten);
    return resultArray;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_theone_audio_AudioEngine_native_1isRecordingActive( JNIEnv *env, jobject /* thiz */) {
    return static_cast<jboolean>(mIsRecording.load());
}
extern "C" JNIEXPORT jfloat JNICALL Java_com_example_theone_audio_AudioEngine_native_1getRecordingLevelPeak( JNIEnv *env, jobject /* thiz */) {
    return mPeakRecordingLevel.exchange(0.0f);
}
