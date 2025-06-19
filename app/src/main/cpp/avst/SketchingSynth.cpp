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

// === Oscillator Implementation ===
void Oscillator::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    updatePhaseIncrement();
}

void Oscillator::setFrequency(float frequency) {
    frequency_ = frequency;
    updatePhaseIncrement();
}

void Oscillator::setType(OscillatorType type) {
    type_ = type;
}

void Oscillator::reset() {
    phase_ = 0.0f;
}

float Oscillator::process() {
    float output = 0.0f;
    
    switch (type_) {
        case OscillatorType::SINE:
            output = std::sin(2.0f * M_PI * phase_);
            break;
            
        case OscillatorType::SAW:
            output = 2.0f * phase_ - 1.0f;
            break;
    }
    
    // Advance phase
    phase_ += phaseIncrement_;
    if (phase_ >= 1.0f) {
        phase_ -= 1.0f;
    }
    
    return output;
}

void Oscillator::updatePhaseIncrement() {
    phaseIncrement_ = frequency_ / sampleRate_;
}

// === LFO Implementation ===
void SimpleLFO::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    updatePhaseIncrement();
}

void SimpleLFO::setFrequency(float frequency) {
    frequency_ = frequency;
    updatePhaseIncrement();
}

void SimpleLFO::setDepth(float depth) {
    depth_ = std::clamp(depth, 0.0f, 1.0f);
}

void SimpleLFO::reset() {
    phase_ = 0.0f;
}

float SimpleLFO::process() {
    // Always sine wave for LFO
    float lfoValue = std::sin(2.0f * M_PI * phase_);
    
    // Advance phase
    phase_ += phaseIncrement_;
    if (phase_ >= 1.0f) {
        phase_ -= 1.0f;
    }
    
    return lfoValue * depth_;
}

void SimpleLFO::updatePhaseIncrement() {
    phaseIncrement_ = frequency_ / sampleRate_;
}

// === Filter Implementation ===
void SimpleFilter::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    updateCoefficient();
}

void SimpleFilter::setCutoff(float cutoff) {
    cutoff_ = std::clamp(cutoff, 20.0f, sampleRate_ * 0.45f);
    updateCoefficient();
}

void SimpleFilter::setResonance(float resonance) {
    resonance_ = std::clamp(resonance, 0.0f, 0.95f);
    updateCoefficient();
}

void SimpleFilter::setType(Type type) {
    type_ = type;
}

void SimpleFilter::reset() {
    lastOutput_ = 0.0f;
}

float SimpleFilter::process(float input) {
    if (type_ == LOWPASS) {
        lastOutput_ = lastOutput_ + coefficient_ * (input - lastOutput_);
        // Add slight gain compensation for low-pass filtering
        return lastOutput_ * (1.0f + coefficient_ * 0.3f);
    } else { // HIGHPASS
        lastOutput_ = lastOutput_ + coefficient_ * (input - lastOutput_);
        float highpass = input - lastOutput_;
        // Add gain compensation for high-pass filtering
        return highpass * (1.0f + coefficient_ * 0.2f);
    }
}

void SimpleFilter::updateCoefficient() {
    // Simple one-pole coefficient calculation
    float frequency = cutoff_ * (1.0f + resonance_ * 0.5f);
    coefficient_ = 1.0f - std::exp(-2.0f * M_PI * frequency / sampleRate_);
    coefficient_ = std::clamp(coefficient_, 0.0f, 0.99f);
}

// === SketchingSynth Implementation ===
SketchingSynth::SketchingSynth() {
    // Create audio processing components
    oscillator_ = std::make_unique<Oscillator>();
    filter_ = std::make_unique<SimpleFilter>();
    pitchLFO_ = std::make_unique<SimpleLFO>();
    volumeLFO_ = std::make_unique<SimpleLFO>();
    panLFO_ = std::make_unique<SimpleLFO>();
    filterLFO_ = std::make_unique<SimpleLFO>();
    
    setupParameters();
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎹 SketchingSynth created - World's first AVST plugin!");
}

PluginInfo SketchingSynth::getPluginInfo() const {
    PluginInfo info;
    info.id = "com.high.theone.sketchingsynth";
    info.name = "Sketching Synth";
    info.vendor = "The One Audio";
    info.version = "1.0.0";
    info.type = PluginType::INSTRUMENT;
    info.category = PluginCategory::SYNTHESIZER;
    info.hasUI = true;
    info.isSynth = true;
    info.acceptsMidi = true;
    info.producesMidi = false;
    info.cpuUsageEstimate = 25; // Lightweight sketching synth
    info.memoryUsageKB = 512;
    info.supportsBackground = true;
    
    return info;
}

bool SketchingSynth::initialize(const AudioIOConfig& config) {
    audioConfig_ = config;
    
    // Initialize audio components
    oscillator_->setSampleRate(config.sampleRate);
    filter_->setSampleRate(config.sampleRate);
    pitchLFO_->setSampleRate(config.sampleRate);
    volumeLFO_->setSampleRate(config.sampleRate);
    panLFO_->setSampleRate(config.sampleRate);
    filterLFO_->setSampleRate(config.sampleRate);
    
    // Reset all voices
    for (auto& voice : voices_) {
        voice.active = false;
        voice.envelope = 0.0f;
    }
    
    activeVoiceCount_.store(0);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
                       "🎹 SketchingSynth initialized: %.0f Hz, %d channels", 
                       config.sampleRate, config.currentOutputChannels);
    
    return true;
}

void SketchingSynth::shutdown() {
    allNotesOff();
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎹 SketchingSynth shutdown");
}

bool SketchingSynth::setAudioIOConfig(const AudioIOConfig& config) {
    return initialize(config);
}

void SketchingSynth::processAudio(ProcessContext& context) {
    // Update parameters if changed
    updateFromParameters();
    
    // Process LFOs
    float pitchMod = pitchLFO_->process();
    float volumeMod = volumeLFO_->process();
    float panMod = panLFO_->process();
    float filterMod = filterLFO_->process();
    
    // Process each frame
    for (uint32_t frame = 0; frame < context.frameCount; ++frame) {
        float mixedOutput = 0.0f;
        
        // Process all active voices
        for (auto& voice : voices_) {
            if (!voice.active) continue;
            
            // Calculate modulated frequency
            float modFreq = voice.frequency * (1.0f + pitchMod * 0.1f); // ±10% pitch mod
            oscillator_->setFrequency(modFreq);
            
            // Generate oscillator output
            float oscOutput = oscillator_->process();
            
            // Apply envelope
            voice.envelope += voice.envelopeRate;
            voice.envelope = std::clamp(voice.envelope, 0.0f, voice.envelopeTarget);
            
            // Check if voice should be released
            if (voice.envelopeTarget == 0.0f && voice.envelope <= 0.01f) {
                voice.active = false;
                activeVoiceCount_.fetch_sub(1);
                continue;
            }
            
            // Apply velocity and envelope
            float voiceOutput = oscOutput * voice.velocity * voice.envelope;
            
            mixedOutput += voiceOutput;
        }
          // Apply filter with LFO modulation
        float baseCutoff = getParameterValue<float>("filter_cutoff", 1000.0f);
        float modCutoff = baseCutoff * (1.0f + filterMod * 0.5f);
        filter_->setCutoff(modCutoff);
        
        float filteredOutput = filter_->process(mixedOutput);
        
        // Add filter makeup gain to compensate for attenuation
        // Lower cutoff = more gain needed
        float cutoffNormalized = std::clamp((baseCutoff - 20.0f) / (8000.0f - 20.0f), 0.0f, 1.0f);
        float makeupGain = 1.0f + (1.0f - cutoffNormalized) * 0.6f; // Up to 60% more gain at low cutoff
        filteredOutput *= makeupGain;
        
        // Apply master volume with LFO modulation
        float masterVol = getParameterValue<float>("master_volume", 0.5f);
        float modVolume = masterVol * (1.0f + volumeMod * 0.2f);
        
        filteredOutput *= modVolume;
        
        // Apply panning
        float pan = getParameterValue<float>("pan", 0.0f) + panMod * 0.3f;
        pan = std::clamp(pan, -1.0f, 1.0f);
        
        float leftGain = std::sqrt((1.0f - pan) * 0.5f);
        float rightGain = std::sqrt((1.0f + pan) * 0.5f);
        
        // Output to stereo channels
        if (context.numOutputs >= 1) {
            context.outputs[0][frame] = filteredOutput * leftGain;
        }
        if (context.numOutputs >= 2) {
            context.outputs[1][frame] = filteredOutput * rightGain;
        }
    }
}

void SketchingSynth::processMidiMessage(const MidiMessage& message) {
    uint8_t status = message.status & 0xF0;
    uint8_t channel = message.status & 0x0F;
    
    switch (status) {
        case 0x90: // Note On
            if (message.data2 > 0) { // Velocity > 0
                noteOn(message.data1, message.data2 / 127.0f);
            } else {
                noteOff(message.data1);
            }
            break;
            
        case 0x80: // Note Off
            noteOff(message.data1);
            break;
            
        case 0xB0: // Control Change
            // Handle CC messages if needed
            break;
    }
}

std::vector<uint8_t> SketchingSynth::saveState() const {
    // Simple state saving - just parameter values
    std::vector<uint8_t> state;
    auto paramValues = getParameters().getParameterValues();
    
    // Convert to bytes (simplified)
    for (const auto& [id, value] : paramValues) {
        // In a real implementation, you'd use proper serialization
        float floatVal = static_cast<float>(value);
        uint8_t* bytes = reinterpret_cast<uint8_t*>(&floatVal);
        state.insert(state.end(), bytes, bytes + sizeof(float));
    }
    
    return state;
}

bool SketchingSynth::loadState(const std::vector<uint8_t>& state) {
    // Simple state loading
    // In a real implementation, you'd properly deserialize
    return true;
}

void SketchingSynth::onLowMemory() {
    __android_log_print(ANDROID_LOG_WARN, APP_NAME, "🎹 Low memory warning - optimizing");
    allNotesOff();
}

void SketchingSynth::setupParameters() {
    // Oscillator parameters
    {
        ParameterInfo info;
        info.id = "osc_type";
        info.displayName = "Oscillator Type";
        info.type = ParameterType::CHOICE;
        info.category = ParameterCategory::CONTROL;
        info.hints = static_cast<uint32_t>(ParameterHint::AUTOMATABLE);
        info.minValue = 0.0;
        info.maxValue = 1.0;
        info.defaultValue = 0.0; // Sine
        info.stepSize = 1.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // Master Volume
    {
        ParameterInfo info;
        info.id = "master_volume";
        info.displayName = "Master Volume";
        info.units = "%";
        info.type = ParameterType::FLOAT;
        info.category = ParameterCategory::CONTROL;
        info.hints = static_cast<uint32_t>(ParameterHint::AUTOMATABLE);
        info.minValue = 0.0;
        info.maxValue = 1.0;
        info.defaultValue = 0.5;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // Pan
    {
        ParameterInfo info;
        info.id = "pan";
        info.displayName = "Pan";
        info.type = ParameterType::FLOAT;
        info.category = ParameterCategory::CONTROL;
        info.hints = static_cast<uint32_t>(ParameterHint::BIPOLAR | ParameterHint::AUTOMATABLE);
        info.minValue = -1.0;
        info.maxValue = 1.0;
        info.defaultValue = 0.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // Filter Cutoff
    {
        ParameterInfo info;
        info.id = "filter_cutoff";
        info.displayName = "Filter Cutoff";
        info.units = "Hz";
        info.type = ParameterType::FLOAT;
        info.category = ParameterCategory::CONTROL;
        info.hints = static_cast<uint32_t>(ParameterHint::LOGARITHMIC | ParameterHint::AUTOMATABLE);
        info.minValue = 20.0;
        info.maxValue = 8000.0;
        info.defaultValue = 1000.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // Filter Type
    {
        ParameterInfo info;
        info.id = "filter_type";
        info.displayName = "Filter Type";
        info.type = ParameterType::CHOICE;
        info.category = ParameterCategory::CONTROL;
        info.hints = static_cast<uint32_t>(ParameterHint::AUTOMATABLE);
        info.minValue = 0.0;
        info.maxValue = 1.0;
        info.defaultValue = 0.0; // Lowpass
        info.stepSize = 1.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // LFO Parameters - Pitch LFO
    {
        ParameterInfo info;
        info.id = "pitch_lfo_rate";
        info.displayName = "Pitch LFO Rate";
        info.units = "Hz";
        info.type = ParameterType::FLOAT;
        info.category = ParameterCategory::MODULATION;
        info.hints = static_cast<uint32_t>(ParameterHint::AUTOMATABLE);
        info.minValue = 0.1;
        info.maxValue = 10.0;
        info.defaultValue = 1.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    {
        ParameterInfo info;
        info.id = "pitch_lfo_depth";
        info.displayName = "Pitch LFO Depth";
        info.units = "%";
        info.type = ParameterType::FLOAT;
        info.category = ParameterCategory::MODULATION;
        info.hints = static_cast<uint32_t>(ParameterHint::AUTOMATABLE);
        info.minValue = 0.0;
        info.maxValue = 1.0;
        info.defaultValue = 0.0;
        
        parameters_.registerParameter(std::make_unique<AvstParameter>(info));
    }
    
    // More LFOs for volume, pan, filter... (similar pattern)
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🎛️ Registered %zu parameters", 
                       parameters_.getParameterCount());
}

void SketchingSynth::updateFromParameters() {
    // Update oscillator type
    int oscType = static_cast<int>(getParameterValue<float>("osc_type", 0.0f));
    oscillator_->setType(oscType == 0 ? OscillatorType::SINE : OscillatorType::SAW);
    
    // Update filter
    float filterType = getParameterValue<float>("filter_type", 0.0f);
    filter_->setType(filterType < 0.5f ? SimpleFilter::LOWPASS : SimpleFilter::HIGHPASS);
    
    // Update LFOs
    pitchLFO_->setFrequency(getParameterValue<float>("pitch_lfo_rate", 1.0f));
    pitchLFO_->setDepth(getParameterValue<float>("pitch_lfo_depth", 0.0f));
}

float SketchingSynth::noteToFrequency(uint8_t midiNote) {
    return 440.0f * std::pow(2.0f, (midiNote - 69) / 12.0f);
}

SketchingSynth::Voice* SketchingSynth::findFreeVoice() {
    for (auto& voice : voices_) {
        if (!voice.active) {
            return &voice;
        }
    }
    return nullptr; // All voices busy
}

SketchingSynth::Voice* SketchingSynth::findVoiceByNote(uint8_t midiNote) {
    for (auto& voice : voices_) {
        if (voice.active && voice.midiNote == midiNote) {
            return &voice;
        }
    }
    return nullptr;
}

void SketchingSynth::noteOn(uint8_t note, float velocity) {
    Voice* voice = findFreeVoice();
    if (voice) {
        voice->active = true;
        voice->midiNote = note;
        voice->frequency = noteToFrequency(note);
        voice->velocity = velocity;
        voice->envelope = 0.0f;
        voice->envelopeTarget = 1.0f;
        voice->envelopeRate = 0.001f; // Quick attack
        
        activeVoiceCount_.fetch_add(1);
        
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                           "🎵 Note ON: %d (%.1f Hz), vel=%.2f", 
                           note, voice->frequency, velocity);
    }
}

void SketchingSynth::noteOff(uint8_t note) {
    Voice* voice = findVoiceByNote(note);
    if (voice) {
        voice->envelopeTarget = 0.0f;
        voice->envelopeRate = -0.0005f; // Quick release
        
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "🎵 Note OFF: %d", note);
    }
}

void SketchingSynth::allNotesOff() {
    for (auto& voice : voices_) {
        voice.active = false;
        voice.envelope = 0.0f;
    }
    activeVoiceCount_.store(0);
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "🛑 All notes off");
}

} // namespace avst

// Register the plugin using our AVST macro
AVST_PLUGIN_EXPORT(avst::SketchingSynth)
