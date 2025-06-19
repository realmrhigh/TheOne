#pragma once

#include "IAvstPlugin.h"
#include <memory>
#include <array>
#include <atomic>

namespace avst {

// Oscillator types
enum class OscillatorType {
    SINE,
    SAW
};

// Simple oscillator class
class Oscillator {
public:
    void setSampleRate(float sampleRate);
    void setFrequency(float frequency);
    void setType(OscillatorType type);
    void reset();
    
    float process(); // Generate next sample
    
private:
    float sampleRate_ = 44100.0f;
    float frequency_ = 440.0f;
    OscillatorType type_ = OscillatorType::SINE;
    float phase_ = 0.0f;
    float phaseIncrement_ = 0.0f;
    
    void updatePhaseIncrement();
};

// Simple LFO (Low Frequency Oscillator)
class SimpleLFO {
public:
    void setSampleRate(float sampleRate);
    void setFrequency(float frequency);
    void setDepth(float depth); // 0.0 to 1.0
    void reset();
    
    float process(); // Generate next modulation value
    
private:
    float sampleRate_ = 44100.0f;
    float frequency_ = 1.0f;
    float depth_ = 0.0f;
    float phase_ = 0.0f;
    float phaseIncrement_ = 0.0f;
    
    void updatePhaseIncrement();
};

// Simple filter (high/low pass)
class SimpleFilter {
public:
    enum Type { LOWPASS, HIGHPASS };
    
    void setSampleRate(float sampleRate);
    void setCutoff(float cutoff); // Hz
    void setResonance(float resonance); // 0.0 to 1.0
    void setType(Type type);
    void reset();
    
    float process(float input);
    
private:
    float sampleRate_ = 44100.0f;
    float cutoff_ = 1000.0f;
    float resonance_ = 0.0f;
    Type type_ = LOWPASS;
    
    // Simple one-pole filter state
    float lastOutput_ = 0.0f;
    float coefficient_ = 0.0f;
    
    void updateCoefficient();
};

// The world's first AVST plugin! 🎹🔥
class SketchingSynth : public IAvstPlugin {
public:
    SketchingSynth();
    virtual ~SketchingSynth() = default;
    
    // === Plugin Information ===
    PluginInfo getPluginInfo() const override;
    AvstParameterContainer& getParameters() override { return parameters_; }
    const AvstParameterContainer& getParameters() const override { return parameters_; }
    
    // === Audio Processing Setup ===
    bool initialize(const AudioIOConfig& config) override;
    void shutdown() override;
    bool setAudioIOConfig(const AudioIOConfig& config) override;
    AudioIOConfig getAudioIOConfig() const override { return audioConfig_; }
    
    // === Real-time Audio Processing ===
    void processAudio(ProcessContext& context) override;
    
    // === MIDI Support ===
    void processMidiMessage(const MidiMessage& message) override;
    
    // === State Management ===
    std::vector<uint8_t> saveState() const override;
    bool loadState(const std::vector<uint8_t>& state) override;
    
    // === Mobile-specific ===
    void onAppBackground() override {}
    void onAppForeground() override {}
    void onLowMemory() override;
    
private:
    // Audio configuration
    AudioIOConfig audioConfig_;
    
    // Parameters container
    AvstParameterContainer parameters_;
    
    // Audio processing components
    std::unique_ptr<Oscillator> oscillator_;
    std::unique_ptr<SimpleFilter> filter_;
    std::unique_ptr<SimpleLFO> pitchLFO_;
    std::unique_ptr<SimpleLFO> volumeLFO_;
    std::unique_ptr<SimpleLFO> panLFO_;
    std::unique_ptr<SimpleLFO> filterLFO_;
    
    // Voice state
    struct Voice {
        bool active = false;
        float velocity = 0.0f;
        float frequency = 440.0f;
        uint8_t midiNote = 60;
        
        // Simple envelope
        float envelope = 0.0f;
        float envelopeTarget = 0.0f;
        float envelopeRate = 0.0f;
    };
    
    static constexpr int MAX_VOICES = 8;
    std::array<Voice, MAX_VOICES> voices_;
    std::atomic<int> activeVoiceCount_{0};
    
    // Helper methods
    void setupParameters();
    void updateFromParameters();
    float noteToFrequency(uint8_t midiNote);
    Voice* findFreeVoice();
    Voice* findVoiceByNote(uint8_t midiNote);
    void noteOn(uint8_t note, float velocity);
    void noteOff(uint8_t note);
    void allNotesOff();
};

} // namespace avst
