#ifndef THEONE_AUDIOENGINE_H
#define THEONE_AUDIOENGINE_H

#include "dr_wav.h"
#include <random>

#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <android/asset_manager.h>
#include "PadSettings.h" // Assuming PadSettingsCpp is defined here
#include "audio_sample.h" // For LoadedSample, PlayingSound etc. if managed by AudioEngine
#include <oboe/Oboe.h>   // For Oboe types if AudioEngine manages stream
#include <jni.h> // Use the real JNI definition
#include <thread> // For recording thread
struct _jobject;
typedef _jobject* jobject;

#include "SequenceCpp.h"

// AVST Plugin System Integration
#include "avst/IAvstPlugin.h"
#include "avst/SketchingSynth.h"

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
    void setPadPan(const std::string& padKey, float pan);

    /**
     * Set per-pad SVF filter. sampleKey matches pad ID used in loadSampleToMemory/triggerSample.
     * modeOrdinal: 0=LOW_PASS, 1=BAND_PASS, 2=HIGH_PASS (must match SVF_Mode and Kotlin FilterMode ordinals).
     */
    void setPadFilter(const std::string& sampleKey,
                      bool enabled, int modeOrdinal,
                      float cutoffHz, float resonance);

    // Metronome
    void setMetronomeState(bool isEnabled, float bpm, int timeSigNum, int timeSigDen,
                           const std::string& primarySoundSampleId, const std::string& secondarySoundSampleId);
    void setMetronomeVolume(float volume);

    // Recording
    bool startAudioRecording(JNIEnv* env, jobject context, const std::string& filePathUri, int sampleRate, int channels);
    jobjectArray stopAudioRecording(JNIEnv* env); // Assuming returns metadata like in native-lib
    bool isRecordingActive();
    float getRecordingLevelPeak();
    float getRecordingLevelRMS();
    void setAutoGainControlEnabled(bool enabled);
    bool isAutoGainControlEnabled();
    void setTargetRecordingLevel(float level);
    float getTargetRecordingLevel();
    float getCurrentRecordingGain();


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


    // --- JNI/Native-lib required functions ---
    void stopNote(const std::string& noteInstanceId, float releaseTimeMs);
    void stopAllNotes(const std::string& trackId, bool immediate);
    /** Fill outL/outR with the latest peak output levels (0.0–1.0). */
    void getOutputLevels(float& outL, float& outR) const {
        outL = outputPeakL_.load();
        outR = outputPeakR_.load();
    }
    void setTrackVolume(const std::string& trackId, float volume) {}
    void setTrackPan(const std::string& trackId, float pan) {}
    bool removeTrackEffect(const std::string& trackId, const std::string& effectInstanceId) { return false; }
    void setTransportBpm(float bpm) {}
    void setEffectParameter(const std::string& effectId, const std::string& parameter, float value) {}

    // 🎛️ Audio Processing Functions
    void processSamplePlayback(float* outputBuffer, int32_t numFrames, int32_t channelCount);
    void processScheduledTriggers(int32_t numFrames);
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
    void triggerDrumPad(int padIndex, float velocity);
    void stopAllSamples();
    void loadTestSample(const std::string& sampleKey); // For quick testing    // 🎯 CONVENIENCE FUNCTIONS FOR TESTING
    bool createAndTriggerTestSample(const std::string& sampleKey, float volume = 1.0f, float pan = 0.0f);

    // 🎛️ AVST PLUGIN SYSTEM
    bool loadPlugin(const std::string& pluginId, const std::string& pluginName);
    bool unloadPlugin(const std::string& pluginId);
    std::vector<std::string> getLoadedPlugins() const;
    
    // Plugin parameter control
    bool setPluginParameter(const std::string& pluginId, const std::string& paramId, double value);
    double getPluginParameter(const std::string& pluginId, const std::string& paramId) const;
    std::vector<avst::ParameterInfo> getPluginParameters(const std::string& pluginId) const;
    
    // Plugin MIDI control
    void sendMidiToPlugin(const std::string& pluginId, uint8_t status, uint8_t data1, uint8_t data2);
    void noteOnToPlugin(const std::string& pluginId, uint8_t note, uint8_t velocity);
    void noteOffToPlugin(const std::string& pluginId, uint8_t note, uint8_t velocity);
    
    // Plugin preset management
    bool savePluginPreset(const std::string& pluginId, const std::string& presetName, const std::string& filePath);
    bool loadPluginPreset(const std::string& pluginId, const std::string& filePath);
    std::vector<std::string> getPluginPresets(const std::string& pluginId) const;    // Asset management
    void setAssetManager(AAssetManager* assetManager) {
        assetManager_ = assetManager;
    }
    bool loadSampleFromAsset(const std::string& sampleId, const std::string& assetPath);
    
    float getSampleRate() const { return globalSampleRate; }
    
    // 🎵 SEQUENCER INTEGRATION METHODS
    
    /**
     * Schedule a sample trigger at a precise timestamp for sequencer playback
     * @param padIndex The pad index to trigger (0-15)
     * @param velocity Velocity (0.0-1.0)
     * @param timestamp Precise timestamp in microseconds when to trigger
     * @return True if successfully scheduled
     */
    bool scheduleStepTrigger(int padIndex, float velocity, int64_t timestamp);
    
    /**
     * Set the sequencer tempo for timing compensation
     * @param bpm Beats per minute (60-200)
     */
    void setSequencerTempo(float bpm);
    
    /**
     * Get the current audio latency for timing compensation
     * @return Audio latency in microseconds
     */
    int64_t getAudioLatencyMicros() const;
    
    /**
     * Enable/disable high-precision timing mode for sequencer
     * @param enabled True to enable high-precision mode
     */
    void setHighPrecisionMode(bool enabled);
    
    /**
     * Pre-load samples for sequencer playback to minimize latency
     * @param padIndices Array of pad indices to pre-load
     * @param count Number of pad indices
     */
    bool preloadSequencerSamples(const int* padIndices, int count);
    
    /**
     * Clear all scheduled sequencer events
     */
    void clearScheduledEvents();
    
    /**
     * Get timing statistics for performance monitoring
     * @return Map-like structure of timing statistics
     */
    std::map<std::string, double> getTimingStatistics() const;
    
    // 🎹 MIDI PROCESSING METHODS
    
    /**
     * Process a MIDI message in the native audio thread
     * @param type MIDI message type (0x80-0xFF)
     * @param channel MIDI channel (0-15)
     * @param data1 First data byte (0-127)
     * @param data2 Second data byte (0-127)
     * @param timestamp Timestamp in microseconds
     */
    void processMidiMessage(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2, int64_t timestamp);
    
    /**
     * Schedule a MIDI event for sample-accurate timing
     * @param type MIDI message type
     * @param channel MIDI channel (0-15)
     * @param data1 First data byte
     * @param data2 Second data byte
     * @param timestamp Target timestamp in microseconds
     */
    void scheduleMidiEvent(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2, int64_t timestamp);
    
    /**
     * Set MIDI note to pad mapping
     * @param midiNote MIDI note number (0-127)
     * @param midiChannel MIDI channel (0-15)
     * @param padIndex Target pad index (0-15)
     */
    void setMidiNoteMapping(uint8_t midiNote, uint8_t midiChannel, int padIndex);
    
    /**
     * Remove MIDI note mapping
     * @param midiNote MIDI note number (0-127)
     * @param midiChannel MIDI channel (0-15)
     */
    void removeMidiNoteMapping(uint8_t midiNote, uint8_t midiChannel);
    
    /**
     * Set MIDI velocity curve parameters
     * @param curveType Curve type (0=linear, 1=exponential, 2=logarithmic, 3=s-curve)
     * @param sensitivity Velocity sensitivity (0.1-2.0)
     */
    void setMidiVelocityCurve(int curveType, float sensitivity);
    
    /**
     * Apply velocity curve to MIDI velocity
     * @param velocity Raw MIDI velocity (0-127)
     * @return Processed velocity (0.0-1.0)
     */
    float applyMidiVelocityCurve(uint8_t velocity);
    
    /**
     * Enable/disable MIDI clock synchronization
     * @param enabled True to enable external clock sync
     */
    void setMidiClockSyncEnabled(bool enabled);
    
    /**
     * Process MIDI clock pulse
     * @param timestamp Clock pulse timestamp in microseconds
     * @param bpm Current BPM from clock analysis
     */
    void processMidiClockPulse(int64_t timestamp, float bpm);
    
    /**
     * Handle MIDI transport messages
     * @param transportType Transport message type (0=start, 1=stop, 2=continue)
     */
    void handleMidiTransport(int transportType);
    
    /**
     * Set MIDI input latency compensation
     * @param latencyMicros Latency compensation in microseconds
     */
    void setMidiInputLatency(int64_t latencyMicros);
    
    /**
     * Get MIDI processing statistics
     * @return Map of MIDI statistics
     */
    std::map<std::string, int64_t> getMidiStatistics() const;
    
    /**
     * Enable/disable external clock source
     * @param useExternal True to use external clock, false for internal
     */
    void setExternalClockEnabled(bool useExternal);
    
    /**
     * Set clock smoothing factor for tempo detection
     * @param factor Smoothing factor (0.0-1.0, lower = more smoothing)
     */
    void setClockSmoothingFactor(float factor);
    
    /**
     * Get current detected BPM from external clock
     * @return Detected BPM or internal BPM if no external clock
     */
    float getCurrentBpm() const;
    
    /**
     * Check if external clock is stable and synchronized
     * @return True if clock is stable
     */
    bool isClockStable() const;
    
private:
    AAssetManager* assetManager_ = nullptr;
    
    // Global sample rate - single source of truth from Oboe
    float globalSampleRate = 48000.0f;

    // Oboe Stream
    oboe::ManagedStream outStream_;
    oboe::ManagedStream mInputStream_; // For recording// Sample map (updated for new audio engine)
    std::map<std::string, std::shared_ptr<SampleDataCpp>> sampleMap_;
    std::mutex sampleMutex_;

    // Pad settings map
    std::map<std::string, PadSettingsCpp> padSettingsMap_;
    std::mutex padSettingsMutex_;

    // Per-pad filter settings (separate map to avoid lock-order issues with padSettingsMutex_)
    // Lock order when acquiring multiple: padSettingsMutex_ → sampleMutex_ → padFilterMutex_ → activeSoundsMutex_
    std::map<std::string, FilterSettingsCpp> padFilterSettings_;
    std::mutex padFilterMutex_;

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
    std::atomic<float> rmsRecordingLevel_ {0.0f};
    std::atomic<bool> autoGainControlEnabled_ {false};
    std::atomic<float> targetRecordingLevel_ {0.7f};
    std::atomic<float> currentGain_ {1.0f};

    // Output level metering (peak L/R, updated each audio callback, decays toward 0)
    std::atomic<float> outputPeakL_ {0.0f};
    std::atomic<float> outputPeakR_ {0.0f};

    int recordingFileDescriptor_ = -1;
    drwav wavWriter_;
    bool wavWriterInitialized_ = false;
    std::string currentRecordingFilePath_ = "";
    std::mutex recordingStateMutex_;
    std::thread recordingThread_;
    std::atomic<bool> shouldStopRecording_ {false};


    std::atomic<bool> oboeInitialized_ {false};

    // Sequencer related members (if AudioEngine is to manage this)
    std::unique_ptr<SequenceCpp> currentSequence_;
    std::mutex sequencerMutex_;
    double currentTickDurationMs_ = 0.0; // Read by audio thread, written by other threads under sequencerMutex_
    double timeAccumulatedForTick_ = 0.0; // Accessed only by audio thread or under sequencerMutex_
    std::atomic<uint32_t> audioStreamSampleRate_ {0}; // To be updated when Oboe stream starts, accessed by audio thread
    
    // 🎵 SEQUENCER TIMING AND SCHEDULING
    struct ScheduledTrigger {
        int padIndex;
        float velocity;
        int64_t timestamp;
        bool processed;
        
        ScheduledTrigger(int pad, float vel, int64_t time) 
            : padIndex(pad), velocity(vel), timestamp(time), processed(false) {}
    };
    
    std::vector<ScheduledTrigger> scheduledTriggers_;
    mutable std::mutex scheduledTriggersMutex_;
    
    std::atomic<float> sequencerTempo_ {120.0f};
    std::atomic<bool> highPrecisionMode_ {false};
    std::atomic<int64_t> audioLatencyMicros_ {10000}; // Default 10ms
    
    // Performance monitoring
    mutable std::mutex performanceMetricsMutex_;
    struct PerformanceMetrics {
        int64_t totalTriggers = 0;
        int64_t missedTriggers = 0;
        int64_t totalLatency = 0;
        int64_t maxLatency = 0;
        int64_t minLatency = INT64_MAX;
        int bufferUnderruns = 0;
    } performanceMetrics_;
    
    // 🎹 MIDI PROCESSING MEMBERS
    
    // MIDI event queue for sample-accurate timing
    struct MidiEvent {
        uint8_t type;
        uint8_t channel;
        uint8_t data1;
        uint8_t data2;
        int64_t timestamp;
        bool processed;
        
        MidiEvent(uint8_t t, uint8_t c, uint8_t d1, uint8_t d2, int64_t ts)
            : type(t), channel(c), data1(d1), data2(d2), timestamp(ts), processed(false) {}
    };
    
    std::vector<MidiEvent> midiEventQueue_;
    mutable std::mutex midiEventQueueMutex_;
    
    // MIDI note mappings: (note << 4 | channel) -> padIndex
    std::map<uint16_t, int> midiNoteMappings_;
    mutable std::mutex midiNoteMappingsMutex_;
    
    // MIDI velocity curve settings
    std::atomic<int> midiVelocityCurveType_ {0}; // 0=linear, 1=exp, 2=log, 3=s-curve
    std::atomic<float> midiVelocitySensitivity_ {1.0f};
    
    // MIDI clock synchronization
    std::atomic<bool> midiClockSyncEnabled_ {false};
    std::atomic<int64_t> midiInputLatencyMicros_ {0};
    std::atomic<float> externalClockBpm_ {120.0f};
    
    // Clock timing and tempo detection
    struct ClockTiming {
        int64_t lastClockTime = 0;
        int64_t clockInterval = 0;
        float detectedBpm = 120.0f;
        int clockPulseCount = 0;
        bool isStable = false;
        std::vector<int64_t> recentIntervals;
        
        ClockTiming() {
            recentIntervals.reserve(24); // Store last 24 intervals (1 beat at 24 PPQN)
        }
    } clockTiming_;
    
    mutable std::mutex clockTimingMutex_;
    std::atomic<bool> useExternalClock_ {false};
    std::atomic<float> clockSmoothingFactor_ {0.1f};
    
    // MIDI statistics
    mutable std::mutex midiStatsMutex_;
    struct MidiStatistics {
        int64_t messagesProcessed = 0;
        int64_t eventsScheduled = 0;
        int64_t eventsDropped = 0;
        int64_t clockPulsesReceived = 0;
        int64_t totalProcessingTime = 0;
        int64_t maxProcessingTime = 0;
    } midiStats_;

private:
    void RecalculateTickDurationInternal(); // No lock
    void RecalculateTickDuration();         // Locks, then calls internal
    void recordingThreadFunction();         // Recording thread function
    
    // MIDI processing helper methods
    void processMidiMessageImmediate(uint8_t type, uint8_t channel, uint8_t data1, uint8_t data2);
    void handleMidiNoteOn(uint8_t channel, uint8_t note, uint8_t velocity);
    void handleMidiNoteOff(uint8_t channel, uint8_t note, uint8_t velocity);
    void handleMidiControlChange(uint8_t channel, uint8_t controller, uint8_t value);
    void processScheduledMidiEvents();
    void initializeDefaultMidiMappings();
    
    // Clock synchronization helper methods
    void updateClockTiming(int64_t timestamp);
    float calculateBpmFromInterval(int64_t interval);
    void smoothClockTempo(float newBpm);
    void resetClockTiming();
    bool isClockTimingStable() const;
    
    // Random engine for layer triggering
    std::mt19937 randomEngine_ {std::random_device{}()};    // 🎛️ AVST Plugin Management
    std::map<std::string, std::unique_ptr<avst::IAvstPlugin>> loadedPlugins_;
    mutable std::mutex pluginsMutex_;
    
    // Plugin audio buffers (for multi-channel processing)
    std::vector<std::vector<float>> pluginInputBuffers_;
    std::vector<std::vector<float>> pluginOutputBuffers_;
    
    // Helper methods for plugin processing
    void processPlugins(float* outputBuffer, int32_t numFrames, int32_t channelCount);
    void ensurePluginBuffersSize(int32_t numFrames, int32_t channelCount);
};

} // namespace audio
} // namespace theone

#endif //THEONE_AUDIOENGINE_H
