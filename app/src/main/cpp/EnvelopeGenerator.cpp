#include "EnvelopeGenerator.h"
#include <algorithm> // For std::max, std::min
#include <cmath>     // For powf

namespace theone {
namespace audio {

EnvelopeGenerator::EnvelopeGenerator()
    : sampleRate(44100.0f), // Default, should be configured
      currentValue(0.0f),
      currentStage(EnvelopeStage::IDLE),
      attackRate(0.0f),
      decayRate(0.0f),
      releaseRate(0.0f),
      holdTimeSamples(0.0f),
      holdSamplesRemaining(0.0f) {
    // settings will be default constructed
}

void EnvelopeGenerator::configure(const EnvelopeSettingsCpp& newSettings, float sr, float triggerVelocity) {
    settings = newSettings;
    sampleRate = sr > 0 ? sr : 44100.0f; // Ensure sampleRate is positive
    reset(); // Reset state with new settings
    // Initial calculation of rates, can be updated by triggerVelocity in triggerOn
    calculateRates(triggerVelocity);
}

void EnvelopeGenerator::calculateRates(float triggerVelocity) {
    // Apply velocity to attack time (example: higher velocity = shorter attack)
    // This is a simple linear scaling. More complex curves could be used.
    // velocityToAttack is 0-1. If 1, max velocity makes attack instant. If 0, no effect.
    float actualAttackMs = settings.attackMs;
    if (settings.velocityToAttack > 0.0f) {
        actualAttackMs = settings.attackMs * (1.0f - (triggerVelocity * settings.velocityToAttack));
        actualAttackMs = std::max(0.0f, actualAttackMs); // Ensure non-negative
    }

    if (actualAttackMs > 0.0f) {
        attackRate = 1.0f / (actualAttackMs / 1000.0f * sampleRate);
    } else {
        attackRate = 1.0f; // Effectively instant attack if time is zero
    }

    if (settings.decayMs > 0.0f) {
        // Decay from 1.0 to sustainLevel
        decayRate = (1.0f - settings.sustainLevel) / (settings.decayMs / 1000.0f * sampleRate);
    } else {
        decayRate = 1.0f; // Effectively instant decay
    }

    if (settings.releaseMs > 0.0f) {
        // Release from current value (or sustainLevel if not AD) to 0
        // For ADSR/AHDS, releaseRate is based on sustainLevel typically, but can be from current value.
        // Here, using sustainLevel for ADSR/AHDS, currentValue for AD.
        float releaseFromLevel = (settings.type == ModelEnvelopeTypeInternalCpp::AD) ? currentValue : settings.sustainLevel;
        if (settings.type == ModelEnvelopeTypeInternalCpp::AD && currentStage == EnvelopeStage::ATTACK) {
             // If AD and still in attack, release from current value
             releaseFromLevel = currentValue;
        } else if (settings.type == ModelEnvelopeTypeInternalCpp::AD && currentStage == EnvelopeStage::DECAY) {
            // AD envelope has no sustain, decay goes to 0. Release is from current value if triggered early.
             releaseFromLevel = currentValue;
        }


        if (releaseFromLevel > 0.0f) { // Check to prevent division by zero if sustain is 0
             releaseRate = releaseFromLevel / (settings.releaseMs / 1000.0f * sampleRate);
        } else {
             releaseRate = 1.0f; // Instant release if starting from zero
        }

    } else {
        releaseRate = 1.0f; // Effectively instant release
    }

    if (settings.holdMs > 0.0f) {
        holdTimeSamples = settings.holdMs / 1000.0f * sampleRate;
    } else {
        holdTimeSamples = 0.0f;
    }
}

void EnvelopeGenerator::triggerOn(float triggerVelocity) {
    // Recalculate rates based on this specific trigger's velocity
    // This allows velocity to affect envelope times dynamically per note-on.
    calculateRates(triggerVelocity);

    currentValue = 0.0f;
    currentStage = EnvelopeStage::ATTACK;
    holdSamplesRemaining = holdTimeSamples;

    if (settings.type == ModelEnvelopeTypeInternalCpp::AD && settings.attackMs <= 0.0f) { // AD type, zero attack time
        currentValue = 1.0f; // Go straight to peak
        currentStage = EnvelopeStage::DECAY; // Then start decaying
    } else if (settings.attackMs <= 0.0f) { // Non-AD type, zero attack time
        currentValue = 1.0f;
        if (settings.type == ModelEnvelopeTypeInternalCpp::AHDS || settings.type == ModelEnvelopeTypeInternalCpp::ADSR) {
            if (holdTimeSamples > 0) {
                currentStage = EnvelopeStage::HOLD;
            } else {
                currentStage = EnvelopeStage::DECAY;
            }
        } else { // AD (already handled) or future types
            currentStage = EnvelopeStage::DECAY;
        }
    }
    // Note: Velocity to Level is not implemented in this `process` method yet.
    // It would typically scale `currentValue` or the target levels of stages.
}

void EnvelopeGenerator::triggerOff() {
    if (currentStage != EnvelopeStage::IDLE) {
        currentStage = EnvelopeStage::RELEASE;
        // Recalculate release rate based on current value if it's not fixed to sustain level
        // This is particularly important for AD envelopes or if release is triggered during attack/decay of ADSR/AHDS
        if (settings.releaseMs > 0.0f) {
            // Ensure releaseRate is calculated based on the value when release starts
            // Avoid division by zero if currentValue is already 0
            if (currentValue > 0.0f) {
                releaseRate = currentValue / (settings.releaseMs / 1000.0f * sampleRate);
            } else {
                releaseRate = 1.0f; // Effectively instant if starting at or below zero
            }
        } else {
            releaseRate = 1.0f; // Instant release
        }
    }
}

float EnvelopeGenerator::process() {
    switch (currentStage) {
        case EnvelopeStage::IDLE:
            currentValue = 0.0f;
            break;
        case EnvelopeStage::ATTACK:
            currentValue += attackRate;
            if (currentValue >= 1.0f) {
                currentValue = 1.0f;
                if (settings.type == ModelEnvelopeTypeInternalCpp::AHDS || (settings.type == ModelEnvelopeTypeInternalCpp::ADSR && settings.holdMs > 0.0f)) {
                    if (holdTimeSamples > 0) {
                        currentStage = EnvelopeStage::HOLD;
                        holdSamplesRemaining = holdTimeSamples; // Reset for this new hold phase
                    } else {
                         currentStage = EnvelopeStage::DECAY;
                    }
                } else { // AD or ADSR with no hold
                    currentStage = EnvelopeStage::DECAY;
                }
            }
            break;
        case EnvelopeStage::HOLD:
            currentValue = 1.0f; // Sustain at peak during hold
            holdSamplesRemaining--;
            if (holdSamplesRemaining <= 0) {
                currentStage = EnvelopeStage::DECAY;
            }
            break;
        case EnvelopeStage::DECAY:
            if (settings.type == ModelEnvelopeTypeInternalCpp::AD) { // AD envelope decays to zero
                // For AD, sustainLevel is effectively 0. decayRate should be calculated towards 0.
                // This recalculation should ideally be more robustly handled in calculateRates or by
                // ensuring settings.sustainLevel is treated as 0 for AD type during rate calculation.
                // Quick fix for AD decay rate calculation if not already set up to target 0:
                float targetSustainForAD = 0.0f;
                float tempDecayRate;
                 if (settings.decayMs > 0.0f) {
                    tempDecayRate = (1.0f - targetSustainForAD) / (settings.decayMs / 1000.0f * sampleRate);
                } else {
                    tempDecayRate = 1.0f; // Instant decay
                }
                currentValue -= tempDecayRate;

            } else { // AHDS, ADSR decay to sustainLevel
                currentValue -= decayRate;
            }

            if (settings.type != ModelEnvelopeTypeInternalCpp::AD && currentValue <= settings.sustainLevel) {
                currentValue = settings.sustainLevel;
                currentStage = EnvelopeStage::SUSTAIN;
            } else if (settings.type == ModelEnvelopeTypeInternalCpp::AD && currentValue <= 0.0f) {
                currentValue = 0.0f;
                currentStage = EnvelopeStage::IDLE; // AD envelope finishes after decay
            }
            break;
        case EnvelopeStage::SUSTAIN:
            // For ADSR/AHDS, sustainLevel is held. AD shouldn't reach here.
            currentValue = settings.sustainLevel;
            if (settings.sustainLevel <= 0.0f && settings.type != ModelEnvelopeTypeInternalCpp::AD) { // If sustain is zero (for ADSR/AHDS), effectively ends
                currentStage = EnvelopeStage::IDLE;
            }
            break;
        case EnvelopeStage::RELEASE:
            currentValue -= releaseRate;
            if (currentValue <= 0.0f) {
                currentValue = 0.0f;
                currentStage = EnvelopeStage::IDLE;
            }
            break;
    }
    return currentValue;
}

void EnvelopeGenerator::reset() {
    currentValue = 0.0f;
    currentStage = EnvelopeStage::IDLE;
    holdSamplesRemaining = 0.0f; // Reset any ongoing hold
}

bool EnvelopeGenerator::isActive() const {
    return currentStage != EnvelopeStage::IDLE;
}

EnvelopeStage EnvelopeGenerator::getCurrentStage() const {
    return currentStage;
}

} // namespace audio
} // namespace theone
