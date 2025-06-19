#include "SynthEngine.h"
#include <android/log.h>
#include <algorithm>
#include <random>

#define APP_NAME "TheOne"

namespace theone {
namespace synth {

// ============================================================================
// Oscillator Implementation
// ============================================================================

Oscillator::Oscillator(float sampleRate) : sampleRate_(sampleRate) {
    updatePhaseIncrement();
}

void Oscillator::updatePhaseIncrement() {
    phaseIncrement_ = (2.0f * M_PI * frequency_) / sampleRate_;
}

float Oscillator::process() {
    float output = 0.0f;
    
    switch (waveformType_) {
        case WaveformType::SINE:
            output = sinf(phase_) * amplitude_;
            break;
            
        case WaveformType::SAW:
            output = (2.0f * (phase_ / (2.0f * M_PI)) - 1.0f) * amplitude_;
            break;
            
        case WaveformType::SQUARE:
            output = (phase_ < M_PI ? 1.0f : -1.0f) * amplitude_;
            break;
            
        case WaveformType::TRIANGLE:
            if (phase_ < M_PI) {
                output = (2.0f * phase_ / M_PI - 1.0f) * amplitude_;
            } else {
                output = (3.0f - 2.0f * phase_ / M_PI) * amplitude_;
            }
            break;
            
        case WaveformType::NOISE:
            output = generateNoise() * amplitude_;
            break;
    }
    
    // Update phase
    phase_ += phaseIncrement_;
    if (phase_ >= 2.0f * M_PI) {
        phase_ -= 2.0f * M_PI;
    }
    
    updatePhaseIncrement(); // In case frequency was modulated
    
    return output;
}

float Oscillator::generateNoise() {
    // Simple white noise using linear congruential generator
    noiseState_ = noiseState_ * 1664525u + 1013904223u;
    return static_cast<float>(static_cast<int32_t>(noiseState_)) / static_cast<float>(INT32_MAX);
}

// ============================================================================
// Filter Implementation
// ============================================================================

Filter::Filter(float sampleRate) : sampleRate_(sampleRate) {
    updateCoefficients();
}

void Filter::setCutoff(float cutoff) {
    cutoff_ = std::clamp(cutoff, 20.0f, sampleRate_ * 0.45f);
    updateCoefficients();
}

void Filter::setResonance(float resonance) {
    resonance_ = std::clamp(resonance, 0.1f, 10.0f);
    updateCoefficients();
}

void Filter::reset() {
    z1_ = z2_ = 0.0f;
}

void Filter::updateCoefficients() {
    float omega = 2.0f * M_PI * cutoff_ / sampleRate_;
    float cos_omega = cosf(omega);
    float sin_omega = sinf(omega);
    float alpha = sin_omega / (2.0f * resonance_);
    
    switch (filterType_) {
        case FilterType::LOW_PASS:
            b1_ = 1.0f - cos_omega;
            b2_ = b1_ * 0.5f;
            a0_ = b1_ + b2_ + 1.0f + alpha;
            a1_ = -2.0f * cos_omega;
            a2_ = 1.0f - alpha;
            break;
            
        case FilterType::HIGH_PASS:
            b1_ = -(1.0f + cos_omega);
            b2_ = -b1_ * 0.5f;
            a0_ = -b1_ + b2_ + 1.0f + alpha;
            a1_ = 2.0f * cos_omega;
            a2_ = 1.0f - alpha;
            break;
            
        case FilterType::BAND_PASS:
            b1_ = 0.0f;
            b2_ = -alpha;
            a0_ = alpha + 1.0f + alpha;
            a1_ = -2.0f * cos_omega;
            a2_ = 1.0f - alpha;
            break;
            
        case FilterType::NOTCH:
            b1_ = -2.0f * cos_omega;
            b2_ = 1.0f;
            a0_ = 1.0f + alpha + 1.0f;
            a1_ = -2.0f * cos_omega;
            a2_ = 1.0f - alpha;
            break;
    }
    
    // Normalize coefficients
    float norm = 1.0f / a0_;
    a1_ *= norm;
    a2_ *= norm;
    b1_ *= norm;
    b2_ *= norm;
}

float Filter::process(float input) {
    float output = input + a1_ * z1_ + a2_ * z2_;
    
    // Update delay line
    z2_ = z1_;
    z1_ = output;
    
    return output * 0.5f; // Scale down to prevent clipping
}

// ============================================================================
// LFO Implementation
// ============================================================================

LFO::LFO(float sampleRate) : sampleRate_(sampleRate) {
    updatePhaseIncrement();
}

void LFO::updatePhaseIncrement() {
    phaseIncrement_ = (2.0f * M_PI * frequency_) / sampleRate_;
}

float LFO::process() {
    float lfoValue = 0.0f;
    
    switch (waveformType_) {
        case WaveformType::SINE:
            lfoValue = sinf(phase_);
            break;
            
        case WaveformType::SAW:
            lfoValue = 2.0f * (phase_ / (2.0f * M_PI)) - 1.0f;
            break;
            
        case WaveformType::SQUARE:
            lfoValue = phase_ < M_PI ? 1.0f : -1.0f;
            break;
            
        case WaveformType::TRIANGLE:
            if (phase_ < M_PI) {
                lfoValue = 2.0f * phase_ / M_PI - 1.0f;
            } else {
                lfoValue = 3.0f - 2.0f * phase_ / M_PI;
            }
            break;
            
        case WaveformType::NOISE:
            static std::random_device rd;
            static std::mt19937 gen(rd());
            static std::uniform_real_distribution<float> dis(-1.0f, 1.0f);
            lfoValue = dis(gen);
            break;
    }
    
    // Update phase
    phase_ += phaseIncrement_;
    if (phase_ >= 2.0f * M_PI) {
        phase_ -= 2.0f * M_PI;
    }
    
    updatePhaseIncrement();
    
    return lfoValue * amount_;
}

// ============================================================================
// Envelope Implementation
// ============================================================================

Envelope::Envelope(float sampleRate) : sampleRate_(sampleRate) {
    updateRates();
}

void Envelope::updateRates() {
    attackRate_ = 1.0f / (attackTime_ * sampleRate_);
    decayRate_ = (1.0f - sustainLevel_) / (decayTime_ * sampleRate_);
    releaseRate_ = sustainLevel_ / (releaseTime_ * sampleRate_);
}

void Envelope::noteOn() {
    stage_ = Stage::ATTACK;
    updateRates();
}

void Envelope::noteOff() {
    if (stage_ != Stage::IDLE) {
        stage_ = Stage::RELEASE;
        updateRates();
    }
}

void Envelope::reset() {
    stage_ = Stage::IDLE;
    currentLevel_ = 0.0f;
}

float Envelope::process() {
    switch (stage_) {
        case Stage::IDLE:
            currentLevel_ = 0.0f;
            break;
            
        case Stage::ATTACK:
            currentLevel_ += attackRate_;
            if (currentLevel_ >= 1.0f) {
                currentLevel_ = 1.0f;
                stage_ = Stage::DECAY;
            }
            break;
            
        case Stage::DECAY:
            currentLevel_ -= decayRate_;
            if (currentLevel_ <= sustainLevel_) {
                currentLevel_ = sustainLevel_;
                stage_ = Stage::SUSTAIN;
            }
            break;
            
        case Stage::SUSTAIN:
            currentLevel_ = sustainLevel_;
            break;
            
        case Stage::RELEASE:
            currentLevel_ -= releaseRate_;
            if (currentLevel_ <= 0.0f) {
                currentLevel_ = 0.0f;
                stage_ = Stage::IDLE;
            }
            break;
    }
    
    return currentLevel_;
}

// ============================================================================
// SynthVoice Implementation
// ============================================================================

SynthVoice::SynthVoice(int voiceId, float sampleRate) 
    : voiceId_(voiceId), sampleRate_(sampleRate) {
    
    oscillator_ = std::make_unique<Oscillator>(sampleRate);
    filter_ = std::make_unique<Filter>(sampleRate);
    envelope_ = std::make_unique<Envelope>(sampleRate);
    lfo1_ = std::make_unique<LFO>(sampleRate);
    lfo2_ = std::make_unique<LFO>(sampleRate);
}

void SynthVoice::noteOn(float frequency, float velocity) {
    baseFrequency_ = frequency;
    velocity_ = velocity;
    
    oscillator_->setFrequency(frequency);
    oscillator_->reset();
    envelope_->noteOn();
    lfo1_->reset();
    lfo2_->reset();
    filter_->reset();
}

void SynthVoice::noteOff() {
    envelope_->noteOff();
}

void SynthVoice::reset() {
    oscillator_->reset();
    envelope_->reset();
    lfo1_->reset();
    lfo2_->reset();
    filter_->reset();
}

bool SynthVoice::isActive() const {
    return envelope_->isActive();
}

void SynthVoice::setOscillatorWaveform(WaveformType type) {
    oscillator_->setWaveform(type);
}

void SynthVoice::setOscillatorAmplitude(float amplitude) {
    oscillator_->setAmplitude(amplitude);
}

void SynthVoice::setFilterType(FilterType type) {
    filter_->setType(type);
}

void SynthVoice::setFilterCutoff(float cutoff) {
    filter_->setCutoff(cutoff);
}

void SynthVoice::setFilterResonance(float resonance) {
    filter_->setResonance(resonance);
}

void SynthVoice::setLFO1(WaveformType waveform, float frequency, float amount, ModulationTarget target) {
    lfo1_->setWaveform(waveform);
    lfo1_->setFrequency(frequency);
    lfo1_->setAmount(amount);
    lfo1Target_ = target;
}

void SynthVoice::setLFO2(WaveformType waveform, float frequency, float amount, ModulationTarget target) {
    lfo2_->setWaveform(waveform);
    lfo2_->setFrequency(frequency);
    lfo2_->setAmount(amount);
    lfo2Target_ = target;
}

void SynthVoice::setEnvelope(float attack, float decay, float sustain, float release) {
    envelope_->setAttack(attack);
    envelope_->setDecay(decay);
    envelope_->setSustain(sustain);
    envelope_->setRelease(release);
}

float SynthVoice::process() {
    if (!envelope_->isActive()) {
        return 0.0f;
    }
    
    // Process modulation
    applyModulation();
    
    // Generate oscillator output
    float oscOutput = oscillator_->process();
    
    // Apply filter
    float filteredOutput = filter_->process(oscOutput);
    
    // Apply envelope
    float envelopeLevel = envelope_->process();
    float finalOutput = filteredOutput * envelopeLevel * velocity_;
    
    return finalOutput;
}

void SynthVoice::applyModulation() {
    float lfo1Value = lfo1_->process();
    float lfo2Value = lfo2_->process();
    
    // Apply LFO1 modulation
    switch (lfo1Target_) {
        case ModulationTarget::PITCH:
            oscillator_->setFrequency(baseFrequency_ * (1.0f + lfo1Value * 0.1f)); // ±10% pitch mod
            break;
        case ModulationTarget::VOLUME:
            oscillator_->setAmplitude(1.0f + lfo1Value * 0.5f); // ±50% volume mod
            break;
        case ModulationTarget::FILTER_CUTOFF:
            filter_->setCutoff(1000.0f * (1.0f + lfo1Value)); // Modulate around 1kHz
            break;
        default:
            break;
    }
    
    // Apply LFO2 modulation
    switch (lfo2Target_) {
        case ModulationTarget::PITCH:
            oscillator_->setFrequency(baseFrequency_ * (1.0f + lfo2Value * 0.1f));
            break;
        case ModulationTarget::VOLUME:
            oscillator_->setAmplitude(1.0f + lfo2Value * 0.5f);
            break;
        case ModulationTarget::FILTER_CUTOFF:
            filter_->setCutoff(1000.0f * (1.0f + lfo2Value));
            break;
        default:
            break;
    }
}

// ============================================================================
// SynthEngine Implementation
// ============================================================================

SynthEngine::SynthEngine(float sampleRate, int maxVoices) 
    : sampleRate_(sampleRate), maxVoices_(maxVoices) {
    
    // Initialize voice pool
    voices_.reserve(maxVoices);
    for (int i = 0; i < maxVoices; ++i) {
        voices_.push_back(std::make_unique<SynthVoice>(i, sampleRate));
    }
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, 
                        "SynthEngine initialized: %d voices, %.0f Hz", maxVoices, sampleRate);
}

void SynthEngine::noteOn(const std::string& noteId, float frequency, float velocity) {
    std::lock_guard<std::mutex> lock(voicesMutex_);
    
    // Check if this note is already playing
    if (noteToVoiceMap_.find(noteId) != noteToVoiceMap_.end()) {
        return; // Note already playing
    }
    
    // Allocate a voice
    SynthVoice* voice = allocateVoice();
    if (!voice) {
        __android_log_print(ANDROID_LOG_WARN, APP_NAME, "No available voices for note %s", noteId.c_str());
        return;
    }
    
    // Apply current preset to the voice
    applyPresetToVoice(voice);
    
    // Start the note
    voice->noteOn(frequency, velocity);
    noteToVoiceMap_[noteId] = voice->getVoiceId();
    
    __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, 
                        "Note ON: %s (%.2f Hz, vel %.2f) -> Voice %d", 
                        noteId.c_str(), frequency, velocity, voice->getVoiceId());
}

void SynthEngine::noteOff(const std::string& noteId) {
    std::lock_guard<std::mutex> lock(voicesMutex_);
    
    auto it = noteToVoiceMap_.find(noteId);
    if (it != noteToVoiceMap_.end()) {
        int voiceId = it->second;
        voices_[voiceId]->noteOff();
        noteToVoiceMap_.erase(it);
        
        __android_log_print(ANDROID_LOG_DEBUG, APP_NAME, "Note OFF: %s -> Voice %d", noteId.c_str(), voiceId);
    }
}

void SynthEngine::allNotesOff() {
    std::lock_guard<std::mutex> lock(voicesMutex_);
    
    for (auto& voice : voices_) {
        voice->noteOff();
    }
    noteToVoiceMap_.clear();
    
    __android_log_print(ANDROID_LOG_INFO, APP_NAME, "All notes OFF");
}

SynthVoice* SynthEngine::allocateVoice() {
    // First, try to find an inactive voice
    for (auto& voice : voices_) {
        if (!voice->isActive()) {
            return voice.get();
        }
    }
    
    // If no inactive voice, steal the oldest one (voice 0)
    voices_[0]->reset();
    return voices_[0].get();
}

void SynthEngine::releaseVoice(int voiceId) {
    if (voiceId >= 0 && voiceId < voices_.size()) {
        voices_[voiceId]->reset();
    }
}

SynthVoice* SynthEngine::findVoiceByNoteId(const std::string& noteId) {
    auto it = noteToVoiceMap_.find(noteId);
    if (it != noteToVoiceMap_.end()) {
        return voices_[it->second].get();
    }
    return nullptr;
}

void SynthEngine::applyPresetToVoice(SynthVoice* voice) {
    voice->setOscillatorWaveform(currentPreset_.oscillatorWaveform);
    voice->setFilterType(currentPreset_.filterType);
    voice->setFilterCutoff(currentPreset_.filterCutoff);
    voice->setFilterResonance(currentPreset_.filterResonance);
    
    voice->setLFO1(currentPreset_.lfo1.waveform, 
                   currentPreset_.lfo1.frequency, 
                   currentPreset_.lfo1.amount, 
                   currentPreset_.lfo1.target);
    
    voice->setLFO2(currentPreset_.lfo2.waveform, 
                   currentPreset_.lfo2.frequency, 
                   currentPreset_.lfo2.amount, 
                   currentPreset_.lfo2.target);
    
    voice->setEnvelope(currentPreset_.envelope.attack,
                       currentPreset_.envelope.decay,
                       currentPreset_.envelope.sustain,
                       currentPreset_.envelope.release);
}

// Preset management methods
void SynthEngine::setOscillatorWaveform(WaveformType type) {
    currentPreset_.oscillatorWaveform = type;
}

void SynthEngine::setFilterType(FilterType type) {
    currentPreset_.filterType = type;
}

void SynthEngine::setFilterCutoff(float cutoff) {
    currentPreset_.filterCutoff = cutoff;
}

void SynthEngine::setFilterResonance(float resonance) {
    currentPreset_.filterResonance = resonance;
}

void SynthEngine::setLFO1(WaveformType waveform, float frequency, float amount, ModulationTarget target) {
    currentPreset_.lfo1.waveform = waveform;
    currentPreset_.lfo1.frequency = frequency;
    currentPreset_.lfo1.amount = amount;
    currentPreset_.lfo1.target = target;
}

void SynthEngine::setLFO2(WaveformType waveform, float frequency, float amount, ModulationTarget target) {
    currentPreset_.lfo2.waveform = waveform;
    currentPreset_.lfo2.frequency = frequency;
    currentPreset_.lfo2.amount = amount;
    currentPreset_.lfo2.target = target;
}

void SynthEngine::setEnvelope(float attack, float decay, float sustain, float release) {
    currentPreset_.envelope.attack = attack;
    currentPreset_.envelope.decay = decay;
    currentPreset_.envelope.sustain = sustain;
    currentPreset_.envelope.release = release;
}

void SynthEngine::process(float* outputBuffer, int frames, int channels) {
    std::lock_guard<std::mutex> lock(voicesMutex_);
    
    // Clear output buffer
    for (int i = 0; i < frames * channels; ++i) {
        outputBuffer[i] = 0.0f;
    }
    
    float masterVol = masterVolume_.load();
    float masterPanValue = masterPan_.load();
    
    // Process each active voice
    for (auto& voice : voices_) {
        if (voice->isActive()) {
            for (int frame = 0; frame < frames; ++frame) {
                float sample = voice->process() * masterVol;
                
                if (channels == 1) {
                    // Mono output
                    outputBuffer[frame] += sample;
                } else if (channels == 2) {
                    // Stereo output with panning
                    float leftGain = (1.0f - masterPanValue) * 0.5f + 0.5f;
                    float rightGain = (1.0f + masterPanValue) * 0.5f;
                    
                    outputBuffer[frame * 2] += sample * leftGain;     // Left
                    outputBuffer[frame * 2 + 1] += sample * rightGain; // Right
                }
            }
        }
    }
}

int SynthEngine::getActiveVoiceCount() const {
    std::lock_guard<std::mutex> lock(voicesMutex_);
    int count = 0;
    for (const auto& voice : voices_) {
        if (voice->isActive()) {
            count++;
        }
    }
    return count;
}

bool SynthEngine::hasActiveVoices() const {
    return getActiveVoiceCount() > 0;
}

} // namespace synth
} // namespace theone
