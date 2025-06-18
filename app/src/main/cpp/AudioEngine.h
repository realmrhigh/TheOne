#ifndef THEONE_AUDIOENGINE_H
#define THEONE_AUDIOENGINE_H

#include "dr_wav.h"
#include <random>

#include <string>
#include <vector>
#include <map>
#include <mutex>
#include "PadSettings.h" // Assuming PadSettingsCpp is defined here
#include "audio_sample.h" // For LoadedSample, PlayingSound etc. if managed by AudioEngine
#include <oboe/Oboe.h>   // For Oboe types if AudioEngine manages stream
#include <jni.h> // Use the real JNI definition
struct _jobject;
typedef _jobject* jobject;

#include "SequenceCpp.h"

namespace theone {
namespace audio {

class AudioEngine : public oboe::AudioStreamCallback { // Assuming it's also the callback
public:
    AudioEngine();
    ~AudioEngine();

    bool initialize(); // Simplified initialization method
    void shutdown();   // Simplified shutdown method

    // Sample management (example, might be more complex)
    // Update declaration to match new implementation
    bool loadSampleToMemory(const std::string& sampleId, const std::string& filePath, long offset, long length);
    bool isSampleLoaded(const std::string& sampleId);
    void unloadSample(const std::string& sampleId);
    int getSampleRate(const std::string& sampleId);

    // Playback (example, might be more complex)
    bool playPadSample(
        const std::string& noteInstanceId, const std::string& trackId, const std::string& padId,
        const std::string& sampleId,
        float velocity, float coarseTune, float fineTune, float pan, float volume,
        int playbackModeOrdinal, float ampEnvAttackMs, float ampEnvDecayMs,
        float ampEnvSustainLevel, float ampEnvReleaseMs
    );
    // Add playSampleSlice if it's managed here

    // Pad settings management
    void updatePadSettings(const std::string& padKey, const PadSettingsCpp& settings);
    void setPadVolume(const std::string& padKey, float volume);
    void setPadPan(const std::string& padKey, float pan); // <-- New method for pan

    // Metronome
    void setMetronomeState(bool isEnabled, float bpm, int timeSigNum, int timeSigDen,
                           const std::string& primarySoundSampleId, const std::string& secondarySoundSampleId);
    void setMetronomeVolume(float volume);

    // Recording
    bool startAudioRecording(JNIEnv* env, jobject context, const std::string& filePathUri, int sampleRate, int channels);
    jobjectArray stopAudioRecording(JNIEnv* env); // Assuming returns metadata like in native-lib
    bool isRecordingActive();
    float getRecordingLevelPeak();


    // Envelope and LFO settings
    void setSampleEnvelope(const std::string& sampleId, const EnvelopeSettingsCpp& envelope);
    void setSampleLFO(const std::string& sampleId, const LfoSettingsCpp& lfo);

    // Oboe AudioStreamCallback methods
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

    // Added for consistency with native-lib.cpp's global state
    bool isOboeInitialized() const;
    float getOboeReportedLatencyMillis() const;

    // --- Test Helpers ---
    // These methods are primarily for facilitating unit testing.
    int getActiveSoundsCountForTest() const;
    void addPlayingSoundForTest(PlayingSound sound);
    // Allow test to set sample rate if Oboe isn't initialized
    void setAudioStreamSampleRateForTest(uint32_t sampleRate);

    void loadSequenceData(const SequenceCpp& sequence);


    // --- JNI/Native-lib required stubs ---
    // These are not implemented, but are required for JNI linkage.
    void stopNote(const std::string& noteInstanceId, float releaseTimeMs) {}
    void stopAllNotes(const std::string& trackId, bool immediate) {}
    void setTrackVolume(const std::string& trackId, float volume) {}
    void setTrackPan(const std::string& trackId, float pan) {}
    bool removeTrackEffect(const std::string& trackId, const std::string& effectInstanceId) { return false; }
    void setTransportBpm(float bpm) {}
    void setEffectParameter(const std::string& effectId, const std::string& parameter, float value) {}

    // 🎛️ Audio Processing Functions
    void processSamplePlayback(float* outputBuffer, int32_t numFrames, int32_t channelCount);
    void generateTestTone(float* outputBuffer, int32_t numFrames, int32_t channelCount);
    void processMetronome(float* outputBuffer, int32_t numFrames, int32_t channelCount);
    void applyMasterProcessing(float* outputBuffer, int32_t numFrames, int32_t channelCount, float masterVolume);

    // 🎵 Audio Control
    void setMasterVolume(float volume) { masterVolume_.store(volume); }
    float getMasterVolume() const { return masterVolume_.load(); }
    void setTestToneEnabled(bool enabled) { testToneEnabled_.store(enabled); }
    bool isTestToneEnabled() const { return testToneEnabled_.load(); }

    // 🔥 EPIC SAMPLE TRIGGERING
    void triggerSample(const std::string& sampleKey, float volume = 1.0f, float pan = 0.0f);
    void stopAllSamples();
    void loadTestSample(const std::string& sampleKey); // For quick testing

    // 🎯 CONVENIENCE FUNCTIONS FOR TESTING
    bool createAndTriggerTestSample(const std::string& sampleKey, float volume = 1.0f, float pan = 0.0f);

private:
    // Oboe Stream
    oboe::ManagedStream outStream_;
    oboe::ManagedStream mInputStream_; // For recording    // Sample map (updated for new audio engine)
    std::map<std::string, std::shared_ptr<SampleDataCpp>> sampleMap_;
    std::mutex sampleMutex_;

    // Pad settings map
    std::map<std::string, PadSettingsCpp> padSettingsMap_;
    std::mutex padSettingsMutex_;

    // Active sounds (updated for new audio engine)
    mutable std::mutex activeSoundsMutex_;
    std::vector<ActiveSound> activeSounds_;

    // 🎛️ Audio Control Variables
    std::atomic<float> masterVolume_{0.7f};      // Default comfortable volume
    std::atomic<bool> testToneEnabled_{false};   // Test tone for debugging

    // Metronome
    MetronomeState metronomeState_;
    std::mutex metronomeStateMutex_;

    // Recording state
    std::atomic<bool> isRecording_ {false};
    std::atomic<float> peakRecordingLevel_ {0.0f};
    int recordingFileDescriptor_ = -1;
    drwav wavWriter_;
    bool wavWriterInitialized_ = false;
    std::string currentRecordingFilePath_ = "";
    std::mutex recordingStateMutex_;


    std::atomic<bool> oboeInitialized_ {false};

    // Sequencer related members (if AudioEngine is to manage this)
    std::unique_ptr<SequenceCpp> currentSequence_;
    std::mutex sequencerMutex_;
    double currentTickDurationMs_ = 0.0; // Read by audio thread, written by other threads under sequencerMutex_
    double timeAccumulatedForTick_ = 0.0; // Accessed only by audio thread or under sequencerMutex_
    std::atomic<uint32_t> audioStreamSampleRate_ {0}; // To be updated when Oboe stream starts, accessed by audio thread

private:
    void RecalculateTickDurationInternal(); // No lock
    void RecalculateTickDuration();         // Locks, then calls internal

    // Random engine for layer triggering
    std::mt19937 randomEngine_ {std::random_device{}()};
};

} // namespace audio
} // namespace theone

#endif //THEONE_AUDIOENGINE_H
