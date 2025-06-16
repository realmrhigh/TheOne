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
                  releaseRate(0.0f) {
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
            float currentAttackMs = std::max(0.0f, settings.attackMs); // Ensure non-negative

            if (currentAttackMs > 0.0f) {
                attackRate = 1.0f / (currentAttackMs / 1000.0f * sampleRate);
            } else {
                attackRate = 1.0f; // Effectively instant attack if time is zero
            }

            // Decay Rate Calculation
            if (settings.decayMs > 0.0f) {
                if (settings.hasSustain) {
                    // Decay from 1.0 to sustainLevel
                    decayRate = (1.0f - settings.sustainLevel) / (settings.decayMs / 1000.0f * sampleRate);
                } else {
                    // AD envelope decays from peak (1.0) to zero (0.0)
                    decayRate = 1.0f / (settings.decayMs / 1000.0f * sampleRate); // Rate to go from 1 to 0
                }
            } else {
                decayRate = 1.0f; // Effectively instant decay
            }
            // Ensure decayRate is positive, especially if sustainLevel is 1.0 and decayMs is > 0
            if (decayRate <= 0.0f && settings.decayMs > 0.0f && settings.hasSustain && settings.sustainLevel >= 1.0f) {
                 // This case means decay to 1.0 or higher, effectively no decay phase or instant to sustain.
                 // Let's make it very fast to sustain level.
                 decayRate = 1.0f;
            }


            // Release Rate Calculation
            // triggerOff() will recalculate releaseRate based on currentValue when transitioning to RELEASE stage.
            // This sets a default based on sustainLevel or 0 if no sustain.
            if (settings.releaseMs > 0.0f) {
                 // If sustain is false, release is effectively from current value, but triggerOff handles actual current value.
                 // Here, for AD, if sustain is false, it will be 0.
                 // This calculation is more of a placeholder before triggerOff.
                 // However, if release happens from sustain stage, this rate is used.
                if (settings.hasSustain && settings.sustainLevel > 0.0f) {
                    releaseRate = settings.sustainLevel / (settings.releaseMs / 1000.0f * sampleRate);
                } else if (!settings.hasSustain) { // AD envelope - release from whatever current value is (handled by triggerOff)
                                                  // For initial setup, assume it would release from a nominal low level if sustain is false
                    releaseRate = 1.0f / (settings.releaseMs / 1000.0f * sampleRate); // Nominal rate to release from 1.0 to 0
                }
                else { // sustainLevel is 0.0f or less
                    releaseRate = 1.0f; // Instant release if sustain level is zero
                }
            } else {
                releaseRate = 1.0f; // Effectively instant release
            }
        }

        void EnvelopeGenerator::triggerOn(float triggerVelocity) {
            // Recalculate rates based on this specific trigger's velocity
            // This allows velocity to affect envelope times dynamically per note-on.
            calculateRates(triggerVelocity);

            currentValue = 0.0f;
            currentStage = EnvelopeStage::ATTACK;

            if (settings.attackMs <= 0.0f) {
                currentValue = 1.0f; // Go straight to peak
                currentStage = EnvelopeStage::DECAY; // Then start decaying (no HOLD stage)
            }
        }

        void EnvelopeGenerator::triggerOff() {
            if (currentStage != EnvelopeStage::IDLE) {
                currentStage = EnvelopeStage::RELEASE;
                // Recalculate release rate based on current value if it's not fixed to sustain level
                // This is particularly important for AD envelopes or if release is triggered during attack/decay of envelopes with sustain.
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
                    currentStage = EnvelopeStage::DECAY; // Transition directly to DECAY
                }
                break;
            case EnvelopeStage::DECAY:
                currentValue -= decayRate;
                if (settings.hasSustain) {
                    if (currentValue <= settings.sustainLevel) {
                        currentValue = settings.sustainLevel;
                        // Only go to sustain if sustainLevel is meaningfully positive.
                        // If sustainLevel is 0, it should go to IDLE from decay.
                        if (settings.sustainLevel > 0.0f) {
                            currentStage = EnvelopeStage::SUSTAIN;
                            } else {
                            currentStage = EnvelopeStage::IDLE;
                            }
                    }
                } else { // No sustain (AD envelope)
                    if (currentValue <= 0.0f) {
                        currentValue = 0.0f;
                        currentStage = EnvelopeStage::IDLE;
                        }
                    }
                // General fallback, if somehow decay rate is very high or sustain level is already < 0
                if (currentValue <= 0.0f && currentStage != EnvelopeStage::IDLE) {
                     currentValue = 0.0f;
                     currentStage = EnvelopeStage::IDLE;
                    }
                    break;
                case EnvelopeStage::SUSTAIN:
                // This stage should only be active if settings.hasSustain is true AND sustainLevel > 0.
                // If sustainLevel is <= 0, it should have transitioned to IDLE in DECAY stage.
                    currentValue = settings.sustainLevel;
                // It's possible to be in SUSTAIN with sustainLevel <= 0 if configured that way initially,
                // though DECAY stage logic tries to prevent this transition.
                if (!settings.hasSustain || settings.sustainLevel <= 0.0f) {
                    currentValue = 0.0f; // Ensure value is 0 if sustain is not really happening
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
        }

        bool EnvelopeGenerator::isActive() const {
            return currentStage != EnvelopeStage::IDLE;
        }

        EnvelopeStage EnvelopeGenerator::getCurrentStage() const {
            return currentStage;
        }

    } // namespace audio
} // namespace theone
