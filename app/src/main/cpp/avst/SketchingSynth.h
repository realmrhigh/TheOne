#pragma once

#include "IAvstPlugin.h"
#include "../EnvelopeGenerator.h"
#include "../StateVariableFilter.h"
#include "../LfoGenerator.h"
#include <memory>
#include <array>
#include <atomic>
#include <cmath>
#include <random>

namespace avst {

// ─── Waveform types ──────────────────────────────────────────────────────────
enum class OscWaveform : int {
    SINE     = 0,
    SAW      = 1,
    SQUARE   = 2,
    TRIANGLE = 3,
    NOISE    = 4
};

// ─── Per-voice oscillator state ───────────────────────────────────────────────
struct VoiceOsc {
    float phase          = 0.0f;
    float phaseIncrement = 0.0f;

    void setFrequency(float freq, float sampleRate) {
        phaseIncrement = (sampleRate > 0.0f) ? (freq / sampleRate) : 0.0f;
    }

    float process(OscWaveform waveform);
    void  reset() { phase = 0.0f; }
};

// ─── Full synthesiser voice ───────────────────────────────────────────────────
struct SynthVoice {
    bool    active       = false;
    bool    releasing    = false;   // In release phase (not yet silent)
    bool    sustainHeld  = false;   // Held open by the sustain pedal
    uint8_t midiNote     = 60;
    float   velocity     = 1.0f;
    float   baseFrequency    = 440.0f;  // Target (after portamento converges)
    float   currentFrequency = 440.0f;  // Actual frequency used each sample
    float   portamentoRate   = 1.0f;    // Per-sample multiplier (1.0 = off)
    uint64_t noteOnTime      = 0;       // For voice-stealing (steal oldest)

    VoiceOsc osc1;
    VoiceOsc osc2;
    VoiceOsc subOsc;  // One octave below osc1

    theone::audio::EnvelopeGenerator ampEnv;
    theone::audio::EnvelopeGenerator filterEnv;
    theone::audio::StateVariableFilter filter;
    theone::audio::LfoGenerator lfo1;
    theone::audio::LfoGenerator lfo2;
};

// ─── Full-featured SketchingSynth ─────────────────────────────────────────────
class SketchingSynth : public IAvstPlugin {
public:
    SketchingSynth();
    virtual ~SketchingSynth() = default;

    // === Plugin Information ===
    PluginInfo getPluginInfo() const override;
    AvstParameterContainer&       getParameters()       override { return parameters_; }
    const AvstParameterContainer& getParameters() const override { return parameters_; }

    // === Audio Processing Setup ===
    bool initialize(const AudioIOConfig& config) override;
    void shutdown() override;
    bool setAudioIOConfig(const AudioIOConfig& config) override;
    AudioIOConfig getAudioIOConfig() const override { return audioConfig_; }

    // === Real-time Audio Processing ===
    void processAudio(ProcessContext& context) override;

    // === MIDI ===
    void processMidiMessage(const MidiMessage& message) override;

    // === State Management ===
    std::vector<uint8_t> saveState() const override;
    bool loadState(const std::vector<uint8_t>& state) override;

    // === Mobile ===
    void onAppBackground() override {}
    void onAppForeground() override {}
    void onLowMemory() override;

private:
    AudioIOConfig        audioConfig_;
    AvstParameterContainer parameters_;

    static constexpr int MAX_VOICES = 8;
    std::array<SynthVoice, MAX_VOICES> voices_;
    std::atomic<uint64_t> globalAge_{0};

    // Global MIDI state (set from processMidiMessage, read from audio thread)
    std::atomic<float> pitchBendNorm_{0.0f};  // -1..+1
    std::atomic<bool>  sustainPedal_{false};
    std::atomic<float> modWheel_{0.0f};       //  0..1

    // Noise generation
    std::mt19937 rng_{12345u};  // Fixed seed for determinism
    std::uniform_real_distribution<float> noiseDist_{-1.0f, 1.0f};

    // ── Parameter cache (read once per processAudio buffer) ──────────────────
    struct CachedParams {
        // OSC 1
        int   osc1Wave;
        float osc1Octave, osc1Semi, osc1Fine, osc1Level;
        // OSC 2
        int   osc2Wave;
        float osc2Octave, osc2Semi, osc2Fine, osc2Level;
        // Sub / Noise
        float subLevel, noiseLevel;
        // Amp envelope
        float ampAttack, ampDecay, ampSustain, ampRelease;
        // Filter
        int   filterType;
        float filterCutoff, filterResonance, filterEnvAmt;
        float filterKeyTrack, filterVelSens;
        // Filter envelope
        float filtAttack, filtDecay, filtSustain, filtRelease;
        // LFO 1
        float lfo1Rate, lfo1Depth;
        int   lfo1Shape, lfo1Dest;
        // LFO 2
        float lfo2Rate, lfo2Depth;
        int   lfo2Shape, lfo2Dest;
        // Master
        float masterVolume, pan, portamento, pitchBendRange;
    };

    void readParams(CachedParams& p) const;

    // ── Helpers ──────────────────────────────────────────────────────────────
    void setupParameters();
    void noteOn(uint8_t note, float velocity);
    void noteOff(uint8_t note);
    void allNotesOff();
    float noteToFrequency(uint8_t midiNote) const;
    SynthVoice* findFreeVoice();
    SynthVoice* findVoiceByNote(uint8_t midiNote);
    void configureVoice(SynthVoice& voice, float freq, float velocity, bool freshVoice);
    float generateNoiseSample();
};

} // namespace avst
