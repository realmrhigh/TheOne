#pragma once

#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cmath>
#include <unordered_map>
#include <string>

namespace theone {
namespace synth {

// Forward declarations
class Oscillator;
class Filter;
class LFO;
class Envelope;

/**
 * Oscillator waveform types
 */
enum class WaveformType {
    SINE = 0,
    SAW = 1,
    SQUARE = 2,
    TRIANGLE = 3,
    NOISE = 4
};

/**
 * Filter types
 */
enum class FilterType {
    LOW_PASS = 0,
    HIGH_PASS = 1,
    BAND_PASS = 2,
    NOTCH = 3
};

/**
 * LFO modulation targets
 */
enum class ModulationTarget {
    PITCH = 0,
    VOLUME = 1,
    PAN = 2,
    FILTER_CUTOFF = 3,
    FILTER_RESONANCE = 4
};

/**
 * Single oscillator with basic waveforms
 */
class Oscillator {
public:
    Oscillator(float sampleRate);
    ~Oscillator() = default;

    void setWaveform(WaveformType type) { waveformType_ = type; }
    void setFrequency(float frequency) { frequency_ = frequency; }
    void setAmplitude(float amplitude) { amplitude_ = amplitude; }
    void reset() { phase_ = 0.0f; }
    
    float process();

private:
    WaveformType waveformType_ = WaveformType::SINE;
    float sampleRate_;
    float frequency_ = 440.0f;
    float amplitude_ = 1.0f;
    float phase_ = 0.0f;
    float phaseIncrement_ = 0.0f;
    
    // Noise generator state
    uint32_t noiseState_ = 1;
    
    void updatePhaseIncrement();
    float generateNoise();
};

/**
 * Simple but effective filter (12dB/octave)
 */
class Filter {
public:
    Filter(float sampleRate);
    ~Filter() = default;

    void setType(FilterType type) { filterType_ = type; }
    void setCutoff(float cutoff);
    void setResonance(float resonance);
    void reset();
    
    float process(float input);

private:
    FilterType filterType_ = FilterType::LOW_PASS;
    float sampleRate_;
    float cutoff_ = 1000.0f;
    float resonance_ = 0.1f;
    
    // Biquad filter coefficients
    float a0_, a1_, a2_, b1_, b2_;
    float z1_ = 0.0f, z2_ = 0.0f;
    
    void updateCoefficients();
};

/**
 * Low Frequency Oscillator for modulation
 */
class LFO {
public:
    LFO(float sampleRate);
    ~LFO() = default;

    void setWaveform(WaveformType type) { waveformType_ = type; }
    void setFrequency(float frequency) { frequency_ = frequency; }
    void setAmount(float amount) { amount_ = amount; }
    void reset() { phase_ = 0.0f; }
    
    float process(); // Returns modulation value (-amount to +amount)

private:
    WaveformType waveformType_ = WaveformType::SINE;
    float sampleRate_;
    float frequency_ = 2.0f; // 2 Hz default
    float amount_ = 0.0f;
    float phase_ = 0.0f;
    float phaseIncrement_ = 0.0f;
    
    void updatePhaseIncrement();
};

/**
 * ADSR Envelope generator
 */
class Envelope {
public:
    enum class Stage {
        IDLE,
        ATTACK,
        DECAY,
        SUSTAIN,
        RELEASE
    };

    Envelope(float sampleRate);
    ~Envelope() = default;

    void setAttack(float attackTime) { attackTime_ = attackTime; }
    void setDecay(float decayTime) { decayTime_ = decayTime; }
    void setSustain(float sustainLevel) { sustainLevel_ = sustainLevel; }
    void setRelease(float releaseTime) { releaseTime_ = releaseTime; }
    
    void noteOn();
    void noteOff();
    void reset();
    
    float process(); // Returns envelope value (0.0 to 1.0)
    bool isActive() const { return stage_ != Stage::IDLE; }

private:
    float sampleRate_;
    Stage stage_ = Stage::IDLE;
    float currentLevel_ = 0.0f;
    
    // ADSR parameters (in seconds)
    float attackTime_ = 0.01f;
    float decayTime_ = 0.3f;
    float sustainLevel_ = 0.7f;
    float releaseTime_ = 0.5f;
    
    // Internal state
    float attackRate_ = 0.0f;
    float decayRate_ = 0.0f;
    float releaseRate_ = 0.0f;
    
    void updateRates();
};

/**
 * Voice - combines oscillator, filter, envelope, and LFOs
 */
class SynthVoice {
public:
    SynthVoice(int voiceId, float sampleRate);
    ~SynthVoice() = default;

    // Voice control
    void noteOn(float frequency, float velocity);
    void noteOff();
    void reset();
    bool isActive() const;
    
    // Oscillator control
    void setOscillatorWaveform(WaveformType type);
    void setOscillatorAmplitude(float amplitude);
    
    // Filter control
    void setFilterType(FilterType type);
    void setFilterCutoff(float cutoff);
    void setFilterResonance(float resonance);
    
    // LFO control
    void setLFO1(WaveformType waveform, float frequency, float amount, ModulationTarget target);
    void setLFO2(WaveformType waveform, float frequency, float amount, ModulationTarget target);
    
    // Envelope control
    void setEnvelope(float attack, float decay, float sustain, float release);
    
    // Audio processing
    float process();
    
    int getVoiceId() const { return voiceId_; }

private:
    int voiceId_;
    float sampleRate_;
    
    // Core components
    std::unique_ptr<Oscillator> oscillator_;
    std::unique_ptr<Filter> filter_;
    std::unique_ptr<Envelope> envelope_;
    std::unique_ptr<LFO> lfo1_;
    std::unique_ptr<LFO> lfo2_;
    
    // Voice state
    float baseFrequency_ = 440.0f;
    float velocity_ = 1.0f;
    
    // Modulation routing
    ModulationTarget lfo1Target_ = ModulationTarget::PITCH;
    ModulationTarget lfo2Target_ = ModulationTarget::VOLUME;
    
    void applyModulation();
};

/**
 * Main synthesizer engine
 */
class SynthEngine {
public:
    SynthEngine(float sampleRate, int maxVoices = 8);
    ~SynthEngine() = default;

    // Voice management
    void noteOn(const std::string& noteId, float frequency, float velocity);
    void noteOff(const std::string& noteId);
    void allNotesOff();
    
    // Global synth parameters
    void setMasterVolume(float volume) { masterVolume_.store(volume); }
    void setMasterPan(float pan) { masterPan_.store(pan); }
    
    // Preset management (all voices get these settings)
    void setOscillatorWaveform(WaveformType type);
    void setFilterType(FilterType type);
    void setFilterCutoff(float cutoff);
    void setFilterResonance(float resonance);
    void setLFO1(WaveformType waveform, float frequency, float amount, ModulationTarget target);
    void setLFO2(WaveformType waveform, float frequency, float amount, ModulationTarget target);
    void setEnvelope(float attack, float decay, float sustain, float release);
    
    // Audio processing
    void process(float* outputBuffer, int frames, int channels);
    
    // Info
    int getActiveVoiceCount() const;
    bool hasActiveVoices() const;

private:
    float sampleRate_;
    int maxVoices_;
    std::atomic<float> masterVolume_{0.8f};
    std::atomic<float> masterPan_{0.0f};
    
    // Voice management
    std::vector<std::unique_ptr<SynthVoice>> voices_;
    std::unordered_map<std::string, int> noteToVoiceMap_;
    std::mutex voicesMutex_;
    
    // Current preset settings
    struct PresetSettings {
        WaveformType oscillatorWaveform = WaveformType::SINE;
        FilterType filterType = FilterType::LOW_PASS;
        float filterCutoff = 1000.0f;
        float filterResonance = 0.1f;
        
        struct LFOSettings {
            WaveformType waveform = WaveformType::SINE;
            float frequency = 2.0f;
            float amount = 0.0f;
            ModulationTarget target = ModulationTarget::PITCH;
        } lfo1, lfo2;
        
        struct EnvelopeSettings {
            float attack = 0.01f;
            float decay = 0.3f;
            float sustain = 0.7f;
            float release = 0.5f;
        } envelope;
    } currentPreset_;
    
    SynthVoice* allocateVoice();
    void releaseVoice(int voiceId);
    SynthVoice* findVoiceByNoteId(const std::string& noteId);
    void applyPresetToVoice(SynthVoice* voice);
};

} // namespace synth
} // namespace theone
