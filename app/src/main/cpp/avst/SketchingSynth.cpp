#include "SketchingSynth.h"
#include <cmath>
#include <algorithm>
#include <android/log.h>
#include <cstring>

#define APP_NAME "SketchingSynth"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace avst {

// ─── VoiceOsc::process ────────────────────────────────────────────────────────
float VoiceOsc::process(OscWaveform waveform) {
    float output = 0.0f;

    switch (waveform) {
        case OscWaveform::SINE:
            output = std::sin(2.0f * static_cast<float>(M_PI) * phase);
            break;

        case OscWaveform::SAW: {
            output = 2.0f * phase - 1.0f;
            // PolyBLEP anti-aliasing
            if (phaseIncrement > 0.0f) {
                if (phase < phaseIncrement) {
                    float t = phase / phaseIncrement;
                    output -= t * t - 2.0f * t + 1.0f;
                } else if (phase > 1.0f - phaseIncrement) {
                    float t = (phase - 1.0f) / phaseIncrement;
                    output -= t * t + 2.0f * t + 1.0f;
                }
            }
            break;
        }

        case OscWaveform::SQUARE: {
            output = phase < 0.5f ? 1.0f : -1.0f;
            // PolyBLEP anti-aliasing (rising edge at 0, falling edge at 0.5)
            if (phaseIncrement > 0.0f) {
                if (phase < phaseIncrement) {
                    float t = phase / phaseIncrement;
                    output += (t + t - t * t - 1.0f);
                } else if (phase > 1.0f - phaseIncrement) {
                    float t = (phase - 1.0f) / phaseIncrement;
                    output += (t * t + t + t + 1.0f);
                }
                if (phase > 0.5f - phaseIncrement && phase < 0.5f) {
                    float t = (phase - 0.5f) / phaseIncrement;
                    output -= (t + t - t * t - 1.0f);
                } else if (phase > 0.5f && phase < 0.5f + phaseIncrement) {
                    float t = (phase - 0.5f) / phaseIncrement;
                    output -= (t * t + t + t + 1.0f);
                }
            }
            break;
        }

        case OscWaveform::TRIANGLE:
            output = phase < 0.5f
                ? (4.0f * phase - 1.0f)
                : (3.0f - 4.0f * phase);
            break;

        case OscWaveform::NOISE:
            // Noise is generated externally via SketchingSynth::generateNoiseSample()
            output = 0.0f;
            break;
    }

    phase += phaseIncrement;
    if (phase >= 1.0f) phase -= 1.0f;

    return output;
}

// ─── SketchingSynth ───────────────────────────────────────────────────────────
SketchingSynth::SketchingSynth() {
    setupParameters();
    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
        "SketchingSynth v2.0 created - Full Featured Polyphonic Synthesizer");
}

PluginInfo SketchingSynth::getPluginInfo() const {
    PluginInfo info;
    info.id              = "com.high.theone.sketchingsynth";
    info.name            = "Sketching Synth";
    info.vendor          = "The One Audio";
    info.version         = "2.0.0";
    info.type            = PluginType::INSTRUMENT;
    info.category        = PluginCategory::SYNTHESIZER;
    info.hasUI           = true;
    info.isSynth         = true;
    info.acceptsMidi     = true;
    info.producesMidi    = false;
    info.cpuUsageEstimate = 40;
    info.memoryUsageKB   = 2048;
    info.supportsBackground = true;
    return info;
}

bool SketchingSynth::initialize(const AudioIOConfig& config) {
    audioConfig_ = config;

    for (auto& v : voices_) {
        v.active      = false;
        v.releasing   = false;
        v.sustainHeld = false;
        v.portamentoRate = 1.0f;
        v.ampEnv.reset();
        v.filterEnv.reset();
        v.filter.setSampleRate(config.sampleRate);
        v.filter.reset();
        v.osc1.reset();
        v.osc2.reset();
        v.subOsc.reset();
    }

    pitchBendNorm_.store(0.0f);
    sustainPedal_.store(false);
    modWheel_.store(0.0f);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
        "SketchingSynth initialized: %.0f Hz, %d channels",
        config.sampleRate, config.currentOutputChannels);
    return true;
}

void SketchingSynth::shutdown() {
    allNotesOff();
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "SketchingSynth shutdown");
}

bool SketchingSynth::setAudioIOConfig(const AudioIOConfig& config) {
    return initialize(config);
}

// ─── Parameter read helper ────────────────────────────────────────────────────
void SketchingSynth::readParams(CachedParams& p) const {
    // OSC 1
    p.osc1Wave   = static_cast<int>(getParameterValue<float>("osc1_wave",   1.0f));
    p.osc1Octave = getParameterValue<float>("osc1_octave", 0.0f);
    p.osc1Semi   = getParameterValue<float>("osc1_semi",   0.0f);
    p.osc1Fine   = getParameterValue<float>("osc1_fine",   0.0f);
    p.osc1Level  = getParameterValue<float>("osc1_level",  1.0f);
    // OSC 2
    p.osc2Wave   = static_cast<int>(getParameterValue<float>("osc2_wave",   0.0f));
    p.osc2Octave = getParameterValue<float>("osc2_octave", 0.0f);
    p.osc2Semi   = getParameterValue<float>("osc2_semi",   0.0f);
    p.osc2Fine   = getParameterValue<float>("osc2_fine",   5.0f);
    p.osc2Level  = getParameterValue<float>("osc2_level",  0.0f);
    // Sub / Noise
    p.subLevel   = getParameterValue<float>("sub_level",   0.0f);
    p.noiseLevel = getParameterValue<float>("noise_level", 0.0f);
    // Amp envelope
    p.ampAttack  = getParameterValue<float>("amp_attack",  10.0f);
    p.ampDecay   = getParameterValue<float>("amp_decay",   150.0f);
    p.ampSustain = getParameterValue<float>("amp_sustain", 1.0f);
    p.ampRelease = getParameterValue<float>("amp_release", 200.0f);
    // Filter
    p.filterType      = static_cast<int>(getParameterValue<float>("filter_type",     0.0f));
    p.filterCutoff    = getParameterValue<float>("filter_cutoff",    8000.0f);
    p.filterResonance = getParameterValue<float>("filter_resonance", 0.707f);
    p.filterEnvAmt    = getParameterValue<float>("filter_env_amt",   0.0f);
    p.filterKeyTrack  = getParameterValue<float>("filter_key_track", 0.0f);
    p.filterVelSens   = getParameterValue<float>("filter_vel_sens",  0.0f);
    // Filter envelope
    p.filtAttack  = getParameterValue<float>("filt_attack",  10.0f);
    p.filtDecay   = getParameterValue<float>("filt_decay",   150.0f);
    p.filtSustain = getParameterValue<float>("filt_sustain", 0.5f);
    p.filtRelease = getParameterValue<float>("filt_release", 200.0f);
    // LFO 1
    p.lfo1Rate  = getParameterValue<float>("lfo1_rate",  2.0f);
    p.lfo1Depth = getParameterValue<float>("lfo1_depth", 0.0f);
    p.lfo1Shape = static_cast<int>(getParameterValue<float>("lfo1_shape", 0.0f));
    p.lfo1Dest  = static_cast<int>(getParameterValue<float>("lfo1_dest",  1.0f));
    // LFO 2
    p.lfo2Rate  = getParameterValue<float>("lfo2_rate",  1.0f);
    p.lfo2Depth = getParameterValue<float>("lfo2_depth", 0.0f);
    p.lfo2Shape = static_cast<int>(getParameterValue<float>("lfo2_shape", 0.0f));
    p.lfo2Dest  = static_cast<int>(getParameterValue<float>("lfo2_dest",  3.0f));
    // Master
    p.masterVolume   = getParameterValue<float>("master_volume",    0.7f);
    p.pan            = getParameterValue<float>("pan",              0.0f);
    p.portamento     = getParameterValue<float>("portamento",       0.0f);
    p.pitchBendRange = getParameterValue<float>("pitch_bend_range", 2.0f);
}

// ─── processAudio ─────────────────────────────────────────────────────────────
void SketchingSynth::processAudio(ProcessContext& context) {
    CachedParams p;
    readParams(p);

    const float sr          = audioConfig_.sampleRate;
    const float pitchBend   = pitchBendNorm_.load();
    const float modWheelVal = modWheel_.load();

    // Pitch bend multiplier
    const float pbMult = std::pow(2.0f, pitchBend * p.pitchBendRange / 12.0f);

    // LFO destinations: 0=None 1=Pitch 2=Volume 3=FilterCutoff 4=Pan

    for (uint32_t frame = 0; frame < context.frameCount; ++frame) {
        float leftOut  = 0.0f;
        float rightOut = 0.0f;

        for (auto& v : voices_) {
            if (!v.active) continue;

            // ── Portamento ───────────────────────────────────────────────────
            if (v.portamentoRate != 1.0f) {
                v.currentFrequency *= v.portamentoRate;
                bool overshot = (v.portamentoRate > 1.0f)
                    ? (v.currentFrequency >= v.baseFrequency)
                    : (v.currentFrequency <= v.baseFrequency);
                if (overshot) {
                    v.currentFrequency = v.baseFrequency;
                    v.portamentoRate   = 1.0f;
                }
            }

            // ── LFO ─────────────────────────────────────────────────────────
            float lfo1Raw = v.lfo1.process();
            float lfo2Raw = v.lfo2.process();
            float lfo1Out = lfo1Raw * p.lfo1Depth * (1.0f + modWheelVal * 2.0f);
            float lfo2Out = lfo2Raw * p.lfo2Depth;

            // ── Frequency with pitch modulation ──────────────────────────────
            float pitchMod = 0.0f;
            if (p.lfo1Dest == 1) pitchMod += lfo1Out * 0.05f;  // ±5 %
            if (p.lfo2Dest == 1) pitchMod += lfo2Out * 0.05f;

            float baseFreq = v.currentFrequency * pbMult * (1.0f + pitchMod);

            float freq1 = baseFreq * std::pow(2.0f,
                (p.osc1Octave * 12.0f + p.osc1Semi + p.osc1Fine * 0.01f) / 12.0f);
            float freq2 = baseFreq * std::pow(2.0f,
                (p.osc2Octave * 12.0f + p.osc2Semi + p.osc2Fine * 0.01f) / 12.0f);
            float freqSub = baseFreq * 0.5f;

            v.osc1.setFrequency(freq1,   sr);
            v.osc2.setFrequency(freq2,   sr);
            v.subOsc.setFrequency(freqSub, sr);

            // ── Oscillator mix ────────────────────────────────────────────────
            OscWaveform wave1 = static_cast<OscWaveform>(std::clamp(p.osc1Wave, 0, 4));
            OscWaveform wave2 = static_cast<OscWaveform>(std::clamp(p.osc2Wave, 0, 4));

            float osc1Out  = (wave1 == OscWaveform::NOISE)
                ? generateNoiseSample() : v.osc1.process(wave1);
            float osc2Out  = (wave2 == OscWaveform::NOISE)
                ? generateNoiseSample() : v.osc2.process(wave2);
            float subOut   = v.subOsc.process(OscWaveform::SINE);
            float noiseOut = generateNoiseSample();

            float oscMix = osc1Out * p.osc1Level
                         + osc2Out * p.osc2Level
                         + subOut  * p.subLevel
                         + noiseOut * p.noiseLevel;

            // Soft clip oscillator mix before filter
            oscMix = std::tanh(oscMix * 0.8f);

            // ── Amp envelope ──────────────────────────────────────────────────
            float ampEnvVal = v.ampEnv.process();

            // Deactivate voice when amp envelope finishes
            if (!v.ampEnv.isActive() && v.releasing) {
                v.active    = false;
                v.releasing = false;
                // Still advance oscillators but don't contribute to output
                v.filterEnv.process();
                continue;
            }

            // ── Filter envelope ───────────────────────────────────────────────
            float filtEnvVal = v.filterEnv.process();

            // ── Filter cutoff calculation ─────────────────────────────────────
            float lfoFilterMod = 0.0f;
            if (p.lfo1Dest == 3) lfoFilterMod += lfo1Out;
            if (p.lfo2Dest == 3) lfoFilterMod += lfo2Out;

            // Key tracking: note 60 = no offset; each octave shifts cutoff by filterKeyTrack octaves
            float keyOctaves = (static_cast<float>(v.midiNote) - 60.0f) / 12.0f;
            float keyMult    = std::pow(2.0f, keyOctaves * p.filterKeyTrack);

            // Velocity sensitivity: higher velocity opens filter
            float velMult = 1.0f + (v.velocity - 0.5f) * p.filterVelSens * 2.0f;

            // Envelope amount: ±4 octaves at maximum
            float envMult = std::pow(2.0f, p.filterEnvAmt * filtEnvVal * 4.0f);

            // LFO modulation: ±2 octaves
            float lfoMult = std::pow(2.0f, lfoFilterMod * 2.0f);

            float modCutoff = std::clamp(
                p.filterCutoff * keyMult * velMult * envMult * lfoMult,
                20.0f, 20000.0f);

            theone::audio::SVF_Mode svfMode =
                static_cast<theone::audio::SVF_Mode>(std::clamp(p.filterType, 0, 2));
            v.filter.configure(svfMode, modCutoff, p.filterResonance);
            float filtered = v.filter.process(oscMix);

            // ── Volume LFO ────────────────────────────────────────────────────
            float volMod = 1.0f;
            if (p.lfo1Dest == 2) volMod *= (1.0f + lfo1Out * 0.5f);
            if (p.lfo2Dest == 2) volMod *= (1.0f + lfo2Out * 0.5f);

            // ── Pan LFO ───────────────────────────────────────────────────────
            float voicePan = p.pan;
            if (p.lfo1Dest == 4) voicePan += lfo1Out * 0.3f;
            if (p.lfo2Dest == 4) voicePan += lfo2Out * 0.3f;
            voicePan = std::clamp(voicePan, -1.0f, 1.0f);

            // ── Voice output ──────────────────────────────────────────────────
            float voiceOut = filtered * ampEnvVal * v.velocity * volMod;

            float leftGain  = std::sqrt(0.5f * (1.0f - voicePan));
            float rightGain = std::sqrt(0.5f * (1.0f + voicePan));

            leftOut  += voiceOut * leftGain;
            rightOut += voiceOut * rightGain;
        }

        // ── Master volume + soft clip ─────────────────────────────────────────
        leftOut  *= p.masterVolume;
        rightOut *= p.masterVolume;
        leftOut  = std::tanh(leftOut  * 0.7f);
        rightOut = std::tanh(rightOut * 0.7f);

        if (context.numOutputs >= 1) context.outputs[0][frame] = leftOut;
        if (context.numOutputs >= 2) context.outputs[1][frame] = rightOut;
    }
}

// ─── processMidiMessage ───────────────────────────────────────────────────────
void SketchingSynth::processMidiMessage(const MidiMessage& message) {
    const uint8_t status = message.status & 0xF0;

    switch (status) {
        case 0x90: // Note On
            if (message.data2 > 0)
                noteOn(message.data1, message.data2 / 127.0f);
            else
                noteOff(message.data1);
            break;

        case 0x80: // Note Off
            noteOff(message.data1);
            break;

        case 0xB0: // Control Change
            switch (message.data1) {
                case 1:  // Mod wheel
                    modWheel_.store(message.data2 / 127.0f);
                    break;
                case 64: // Sustain pedal
                    if (message.data2 >= 64) {
                        sustainPedal_.store(true);
                    } else {
                        sustainPedal_.store(false);
                        // Release notes that were held by the pedal
                        for (auto& v : voices_) {
                            if (v.active && v.sustainHeld) {
                                v.sustainHeld = false;
                                v.releasing   = true;
                                v.ampEnv.triggerOff();
                                v.filterEnv.triggerOff();
                            }
                        }
                    }
                    break;
                case 120: case 123: // All sound/notes off
                    allNotesOff();
                    break;
            }
            break;

        case 0xE0: { // Pitch bend  (data2=MSB, data1=LSB)
            int raw = ((int)message.data2 << 7) | (int)message.data1;
            float norm = std::clamp((raw - 8192) / 8192.0f, -1.0f, 1.0f);
            pitchBendNorm_.store(norm);
            break;
        }
    }
}

// ─── State save / load ────────────────────────────────────────────────────────
std::vector<uint8_t> SketchingSynth::saveState() const {
    std::vector<uint8_t> state;
    auto params = getParameters().getParameterValues();
    for (const auto& [id, value] : params) {
        uint32_t idLen = static_cast<uint32_t>(id.size());
        const uint8_t* lb = reinterpret_cast<const uint8_t*>(&idLen);
        state.insert(state.end(), lb, lb + 4);
        state.insert(state.end(), id.begin(), id.end());
        float fv = static_cast<float>(value);
        const uint8_t* vb = reinterpret_cast<const uint8_t*>(&fv);
        state.insert(state.end(), vb, vb + 4);
    }
    return state;
}

bool SketchingSynth::loadState(const std::vector<uint8_t>& state) {
    if (state.empty()) return false;
    size_t offset = 0;
    while (offset + 8 <= state.size()) {
        uint32_t idLen;
        std::memcpy(&idLen, state.data() + offset, 4);
        offset += 4;
        if (offset + idLen + 4 > state.size()) break;
        std::string id(reinterpret_cast<const char*>(state.data() + offset), idLen);
        offset += idLen;
        float fv;
        std::memcpy(&fv, state.data() + offset, 4);
        offset += 4;
        setParameterValue(id, static_cast<double>(fv));
    }
    return true;
}

void SketchingSynth::onLowMemory() {
    allNotesOff();
    __android_log_print(ANDROID_LOG_WARN, APP_NAME, "Low memory - all notes off");
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
float SketchingSynth::noteToFrequency(uint8_t midiNote) const {
    return 440.0f * std::pow(2.0f, (static_cast<float>(midiNote) - 69.0f) / 12.0f);
}

float SketchingSynth::generateNoiseSample() {
    return noiseDist_(rng_);
}

SynthVoice* SketchingSynth::findFreeVoice() {
    // Prefer a fully inactive voice
    for (auto& v : voices_) {
        if (!v.active) return &v;
    }
    // Then steal the oldest releasing voice
    SynthVoice* oldest = nullptr;
    uint64_t    oldAge  = UINT64_MAX;
    for (auto& v : voices_) {
        if (v.releasing && v.noteOnTime < oldAge) { oldest = &v; oldAge = v.noteOnTime; }
    }
    if (oldest) return oldest;
    // Last resort: steal the oldest active voice
    for (auto& v : voices_) {
        if (v.active && v.noteOnTime < oldAge) { oldest = &v; oldAge = v.noteOnTime; }
    }
    return oldest;
}

SynthVoice* SketchingSynth::findVoiceByNote(uint8_t midiNote) {
    for (auto& v : voices_) {
        if (v.active && v.midiNote == midiNote) return &v;
    }
    return nullptr;
}

void SketchingSynth::configureVoice(SynthVoice& voice,
                                     float freq, float velocity,
                                     bool freshVoice) {
    const float sr = audioConfig_.sampleRate;
    CachedParams p;
    readParams(p);

    voice.baseFrequency = freq;

    // Portamento: only slide if there's a previous frequency and portamento > 0
    if (p.portamento > 0.0f && !freshVoice && voice.currentFrequency > 0.0f
            && voice.currentFrequency != freq) {
        float portSamples = std::max(1.0f, p.portamento * 0.001f * sr);
        voice.portamentoRate = std::pow(freq / voice.currentFrequency, 1.0f / portSamples);
    } else {
        voice.currentFrequency = freq;
        voice.portamentoRate   = 1.0f;
    }

    // ── Amp envelope ─────────────────────────────────────────────────────────
    theone::audio::EnvelopeSettingsCpp ampSettings;
    ampSettings.attackMs    = std::max(1.0f, p.ampAttack);
    ampSettings.decayMs     = std::max(1.0f, p.ampDecay);
    ampSettings.sustainLevel = std::clamp(p.ampSustain, 0.0f, 1.0f);
    ampSettings.releaseMs   = std::max(1.0f, p.ampRelease);
    ampSettings.hasSustain  = true;
    ampSettings.type        = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR;
    voice.ampEnv.configure(ampSettings, sr, velocity);
    voice.ampEnv.triggerOn(velocity);

    // ── Filter envelope ───────────────────────────────────────────────────────
    theone::audio::EnvelopeSettingsCpp filtSettings;
    filtSettings.attackMs    = std::max(1.0f, p.filtAttack);
    filtSettings.decayMs     = std::max(1.0f, p.filtDecay);
    filtSettings.sustainLevel = std::clamp(p.filtSustain, 0.0f, 1.0f);
    filtSettings.releaseMs   = std::max(1.0f, p.filtRelease);
    filtSettings.hasSustain  = true;
    filtSettings.type        = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR;
    voice.filterEnv.configure(filtSettings, sr, velocity);
    voice.filterEnv.triggerOn(velocity);

    // ── Filter ────────────────────────────────────────────────────────────────
    voice.filter.setSampleRate(sr);
    voice.filter.reset();
    theone::audio::SVF_Mode svfMode =
        static_cast<theone::audio::SVF_Mode>(std::clamp(p.filterType, 0, 2));
    voice.filter.configure(svfMode,
        std::clamp(p.filterCutoff, 20.0f, 20000.0f),
        std::clamp(p.filterResonance, 0.5f, 20.0f));

    // ── LFO 1 ─────────────────────────────────────────────────────────────────
    theone::audio::LfoSettingsCpp lfo1s;
    lfo1s.id          = "lfo1";
    lfo1s.isEnabled   = true;
    lfo1s.rateHz      = std::max(0.01f, p.lfo1Rate);
    lfo1s.depth       = p.lfo1Depth;
    lfo1s.waveform    = static_cast<theone::audio::LfoWaveformCpp>(std::clamp(p.lfo1Shape, 0, 6));
    lfo1s.syncToTempo = false;
    voice.lfo1.configure(lfo1s, sr, 120.0f);
    voice.lfo1.retrigger();

    // ── LFO 2 ─────────────────────────────────────────────────────────────────
    theone::audio::LfoSettingsCpp lfo2s;
    lfo2s.id          = "lfo2";
    lfo2s.isEnabled   = true;
    lfo2s.rateHz      = std::max(0.01f, p.lfo2Rate);
    lfo2s.depth       = p.lfo2Depth;
    lfo2s.waveform    = static_cast<theone::audio::LfoWaveformCpp>(std::clamp(p.lfo2Shape, 0, 6));
    lfo2s.syncToTempo = false;
    voice.lfo2.configure(lfo2s, sr, 120.0f);
    voice.lfo2.retrigger();

    // Reset oscillator phases for a clean transient on fresh voices
    if (freshVoice) {
        voice.osc1.reset();
        voice.osc2.reset();
        voice.subOsc.reset();
    }
}

void SketchingSynth::noteOn(uint8_t note, float velocity) {
    // Reuse existing voice for the same note (legato retrigger)
    SynthVoice* voice = findVoiceByNote(note);
    bool fresh = (voice == nullptr);

    if (!voice) voice = findFreeVoice();
    if (!voice) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME,
            "No voice available for note %d", note);
        return;
    }

    voice->midiNote    = note;
    voice->velocity    = velocity;
    voice->active      = true;
    voice->releasing   = false;
    voice->sustainHeld = false;
    voice->noteOnTime  = globalAge_.fetch_add(1);

    configureVoice(*voice, noteToFrequency(note), velocity, fresh);

    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME,
        "Note ON: %d  freq=%.1f  vel=%.2f", note, voice->baseFrequency, velocity);
}

void SketchingSynth::noteOff(uint8_t note) {
    SynthVoice* voice = findVoiceByNote(note);
    if (!voice) return;

    if (sustainPedal_.load()) {
        voice->sustainHeld = true;  // Hold until pedal released
        return;
    }

    voice->releasing = true;
    voice->ampEnv.triggerOff();
    voice->filterEnv.triggerOff();

    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Note OFF: %d", note);
}

void SketchingSynth::allNotesOff() {
    for (auto& v : voices_) {
        v.active      = false;
        v.releasing   = false;
        v.sustainHeld = false;
        v.ampEnv.reset();
        v.filterEnv.reset();
    }
    pitchBendNorm_.store(0.0f);
    sustainPedal_.store(false);
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "All notes off");
}

// ─── setupParameters ──────────────────────────────────────────────────────────
void SketchingSynth::setupParameters() {
    using PT  = ParameterType;
    using PC  = ParameterCategory;
    using PH  = ParameterHint;

    const uint32_t AUTO = static_cast<uint32_t>(PH::AUTOMATABLE);
    const uint32_t LOG  = AUTO | static_cast<uint32_t>(PH::LOGARITHMIC);
    const uint32_t BIPO = AUTO | static_cast<uint32_t>(PH::BIPOLAR);

    auto add = [this](const std::string& id, const std::string& name,
                      const std::string& units, PT type, PC cat,
                      double min, double max, double def,
                      uint32_t hints, double step = 0.0) {
        ParameterInfo info;
        info.id           = id;
        info.displayName  = name;
        info.units        = units;
        info.type         = type;
        info.category     = cat;
        info.hints        = hints;
        info.minValue     = min;
        info.maxValue     = max;
        info.defaultValue = def;
        info.stepSize     = step;
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    };

    // OSC 1
    add("osc1_wave",   "OSC1 Wave",    "",    PT::CHOICE, PC::CONTROL,  0, 4, 1,    AUTO, 1.0);
    add("osc1_octave", "OSC1 Octave",  "oct", PT::INT,    PC::CONTROL, -2, 2, 0,    AUTO, 1.0);
    add("osc1_semi",   "OSC1 Semi",    "st",  PT::INT,    PC::CONTROL,-12,12, 0,    AUTO, 1.0);
    add("osc1_fine",   "OSC1 Fine",    "ct",  PT::FLOAT,  PC::CONTROL,-100,100,0,   BIPO);
    add("osc1_level",  "OSC1 Level",   "%",   PT::FLOAT,  PC::CONTROL,  0, 1, 1.0, AUTO);
    // OSC 2
    add("osc2_wave",   "OSC2 Wave",    "",    PT::CHOICE, PC::CONTROL,  0, 4, 0,    AUTO, 1.0);
    add("osc2_octave", "OSC2 Octave",  "oct", PT::INT,    PC::CONTROL, -2, 2, 0,    AUTO, 1.0);
    add("osc2_semi",   "OSC2 Semi",    "st",  PT::INT,    PC::CONTROL,-12,12, 0,    AUTO, 1.0);
    add("osc2_fine",   "OSC2 Fine",    "ct",  PT::FLOAT,  PC::CONTROL,-100,100,5,   BIPO);
    add("osc2_level",  "OSC2 Level",   "%",   PT::FLOAT,  PC::CONTROL,  0, 1, 0.0, AUTO);
    // Sub / Noise
    add("sub_level",   "Sub Level",    "%",   PT::FLOAT,  PC::CONTROL,  0, 1, 0.0, AUTO);
    add("noise_level", "Noise Level",  "%",   PT::FLOAT,  PC::CONTROL,  0, 1, 0.0, AUTO);
    // Amp envelope
    add("amp_attack",  "Amp Attack",   "ms",  PT::FLOAT,  PC::CONTROL,  1,10000, 10,  LOG);
    add("amp_decay",   "Amp Decay",    "ms",  PT::FLOAT,  PC::CONTROL,  1, 5000,150,  LOG);
    add("amp_sustain", "Amp Sustain",  "%",   PT::FLOAT,  PC::CONTROL,  0,    1, 1.0, AUTO);
    add("amp_release", "Amp Release",  "ms",  PT::FLOAT,  PC::CONTROL,  1,10000,200,  LOG);
    // Filter
    add("filter_type",      "Filter Type",     "",   PT::CHOICE, PC::CONTROL, 0,  2,    0, AUTO, 1.0);
    add("filter_cutoff",    "Filter Cutoff",   "Hz", PT::FLOAT,  PC::CONTROL,20,20000,8000, LOG);
    add("filter_resonance", "Filter Res",      "Q",  PT::FLOAT,  PC::CONTROL, 0.5,20,0.707,LOG);
    add("filter_env_amt",   "Filter Env Amt",  "%",  PT::FLOAT,  PC::CONTROL,-1,  1,    0, BIPO);
    add("filter_key_track", "Key Track",       "%",  PT::FLOAT,  PC::CONTROL, 0,  1,    0, AUTO);
    add("filter_vel_sens",  "Vel Sens",        "%",  PT::FLOAT,  PC::CONTROL, 0,  1,    0, AUTO);
    // Filter envelope
    add("filt_attack",  "Filt Attack",  "ms", PT::FLOAT,  PC::CONTROL,  1,10000, 10, LOG);
    add("filt_decay",   "Filt Decay",   "ms", PT::FLOAT,  PC::CONTROL,  1, 5000,150, LOG);
    add("filt_sustain", "Filt Sustain", "%",  PT::FLOAT,  PC::CONTROL,  0,    1,0.5, AUTO);
    add("filt_release", "Filt Release", "ms", PT::FLOAT,  PC::CONTROL,  1,10000,200, LOG);
    // LFO 1
    add("lfo1_rate",  "LFO1 Rate",  "Hz", PT::FLOAT,  PC::MODULATION,0.01,20, 2.0, LOG);
    add("lfo1_depth", "LFO1 Depth", "%",  PT::FLOAT,  PC::MODULATION,   0, 1, 0.0, AUTO);
    add("lfo1_shape", "LFO1 Shape", "",   PT::CHOICE, PC::MODULATION,   0, 6, 0,   AUTO, 1.0);
    add("lfo1_dest",  "LFO1 Dest",  "",   PT::CHOICE, PC::MODULATION,   0, 4, 1,   AUTO, 1.0);
    // LFO 2
    add("lfo2_rate",  "LFO2 Rate",  "Hz", PT::FLOAT,  PC::MODULATION,0.01,20, 1.0, LOG);
    add("lfo2_depth", "LFO2 Depth", "%",  PT::FLOAT,  PC::MODULATION,   0, 1, 0.0, AUTO);
    add("lfo2_shape", "LFO2 Shape", "",   PT::CHOICE, PC::MODULATION,   0, 6, 0,   AUTO, 1.0);
    add("lfo2_dest",  "LFO2 Dest",  "",   PT::CHOICE, PC::MODULATION,   0, 4, 3,   AUTO, 1.0);
    // Master
    add("master_volume",    "Master Volume", "%",  PT::FLOAT, PC::CONTROL, 0,  1, 0.7, AUTO);
    add("pan",              "Pan",           "",   PT::FLOAT, PC::CONTROL,-1,  1, 0.0, BIPO);
    add("portamento",       "Portamento",    "ms", PT::FLOAT, PC::CONTROL, 0,2000, 0,  LOG);
    add("pitch_bend_range", "PB Range",      "st", PT::FLOAT, PC::CONTROL, 0, 24, 2.0, AUTO);

    __android_log_print(ANDROID_LOG_INFO, APP_NAME,
        "Registered %zu parameters", parameters_.getParameterCount());
}

} // namespace avst

// Export the plugin
AVST_PLUGIN_EXPORT(avst::SketchingSynth)
