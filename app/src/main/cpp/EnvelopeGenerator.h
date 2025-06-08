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
    HOLD, // Added for AHDS
    DECAY,
    SUSTAIN,
    RELEASE
};

enum class ModelEnvelopeTypeInternalCpp { // Renamed to avoid conflict if Kotlin enum is bridged directly
    AD,
    AHDS,
    ADSR
};

struct EnvelopeSettingsCpp {
    ModelEnvelopeTypeInternalCpp type = ModelEnvelopeTypeInternalCpp::ADSR;
    float attackMs = 5.0f;
    float holdMs = 0.0f;    // Relevant for AHDS, ADSR (though ADSR sustain level makes hold phase brief)
    float decayMs = 150.0f;
    float sustainLevel = 1.0f; // 0.0 to 1.0, relevant for ADSR, AHDS
    float releaseMs = 100.0f;

    // Velocity sensitivities (0.0 to 1.0 range, affect time or level)
    // For simplicity, these are not yet implemented in the generator's process method
    // but are included for future expansion and API compatibility.
    float velocityToAttack = 0.0f; // e.g., 1.0 means max velocity makes attack time 0
    float velocityToLevel = 0.0f;  // e.g., 1.0 means velocity fully scales output level

    // Default constructor
    EnvelopeSettingsCpp() = default;

    // Parameterized constructor (useful for JNI mapping)
    EnvelopeSettingsCpp(ModelEnvelopeTypeInternalCpp t, float atk, float hld, float dec, float sus, float rel, float velAtk, float velLvl)
        : type(t), attackMs(atk), holdMs(hld), decayMs(dec), sustainLevel(sus), releaseMs(rel),
          velocityToAttack(velAtk), velocityToLevel(velLvl) {}
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
    float holdTimeSamples; // Hold time in samples
    float holdSamplesRemaining;

    void calculateRates(float triggerVelocity);
};

} // namespace audio
} // namespace theone

#endif // THEONE_ENVELOPE_GENERATOR_H
