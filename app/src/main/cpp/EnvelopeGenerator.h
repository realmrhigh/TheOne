#ifndef THEONE_ENVELOPE_GENERATOR_H
#define THEONE_ENVELOPE_GENERATOR_H

#include <cstdint> // For float

namespace theone {
namespace audio {

// Forward declare if SynthModels.h is created later, or define here
// For now, defining a C++ version of EnvelopeSettings and EnvelopeType
enum class EnvelopeStage {
    IDLE,
    ATTACK,
    DECAY,
    SUSTAIN,
    RELEASE
};

// Add C++ enum for envelope type to match test and settings usage
enum class ModelEnvelopeTypeInternalCpp {
    ADSR,
    AHDSR,
    AR,
    CUSTOM
};

struct EnvelopeSettingsCpp {
    float attackMs = 5.0f;
    float decayMs = 150.0f;
    float sustainLevel = 1.0f; // 0.0 to 1.0, relevant for envelopes with sustain
    bool hasSustain = true;
    float releaseMs = 100.0f;
    float holdMs = 0.0f; // Add hold for AHDSR
    ModelEnvelopeTypeInternalCpp type = ModelEnvelopeTypeInternalCpp::ADSR;

    // Default constructor
    EnvelopeSettingsCpp() = default;

    // Parameterized constructor (useful for JNI mapping)
    EnvelopeSettingsCpp(float atk, float dec, float sus, bool hasSus, float rel)
        : attackMs(atk), decayMs(dec), sustainLevel(sus), hasSustain(hasSus), releaseMs(rel), holdMs(0.0f), type(ModelEnvelopeTypeInternalCpp::ADSR) {}
};

class EnvelopeGenerator {
public:
    EnvelopeGenerator(); // Default constructor

    void configure(const EnvelopeSettingsCpp& settings, float sampleRate, float triggerVelocity = 1.0f);
    void triggerOn(float triggerVelocity = 1.0f); // Velocity 0.0 to 1.0
    void triggerOff();
    float process(); // Returns current envelope value (0.0 to 1.0)
    void reset();
    bool isActive() const;
    EnvelopeStage getCurrentStage() const;

private:
    EnvelopeSettingsCpp settings;
    float sampleRate;
    float currentValue;
    EnvelopeStage currentStage;
    float attackRate;  // Rate of change per sample
    float decayRate;   // Rate of change per sample
    float releaseRate; // Rate of change per sample

    void calculateRates(float triggerVelocity);
};

} // namespace audio
} // namespace theone

#endif // THEONE_ENVELOPE_GENERATOR_H
