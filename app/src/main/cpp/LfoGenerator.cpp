#include "LfoGenerator.h"
#include <cstdlib> // For rand()
#include <algorithm> // For std::min, std::max
#include <ctime>   // For time() in srand initialization
#include <cmath>   // For sin, cos, M_PI

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace theone {
namespace audio {

LfoGenerator::LfoGenerator()
    : sampleRate(48000.0f), // Match Oboe's sample rate
      currentTempoBpm(120.0f),
      phase(0.0),
      phaseIncrement(0.0),
      lastRandomValue(0.0f),
      nextRandomValue(0.0f), // For smooth random later
      samplesUntilNextRandomStep(0) {
    // settings will be default constructed
    // Seed random number generator once
    // Consider a more robust random solution if high quality is needed
    std::srand(static_cast<unsigned int>(std::time(nullptr)));
}

void LfoGenerator::configure(const LfoSettingsCpp& newSettings, float sr, float tempoBpm) {
    settings = newSettings;
    sampleRate = sr > 0 ? sr : 48000.0f;
    currentTempoBpm = tempoBpm > 0 ? tempoBpm : 120.0f;
    phase = 0.0; // Reset phase on new configuration
    calculatePhaseIncrement();
    
    if (settings.waveform == LfoWaveformCpp::RANDOM_STEP || settings.waveform == LfoWaveformCpp::RANDOM_SMOOTH) {
        lastRandomValue = (static_cast<float>(std::rand()) / static_cast<float>(RAND_MAX)) * 2.0f - 1.0f;
        nextRandomValue = (static_cast<float>(std::rand()) / static_cast<float>(RAND_MAX)) * 2.0f - 1.0f;
        samplesUntilNextRandomStep = 0; // Trigger immediate new value on first process
    }
}

void LfoGenerator::calculatePhaseIncrement() {
    if (settings.syncToTempo) {
        if (currentTempoBpm > 0 && sampleRate > 0 && settings.tempoDivision != TimeDivisionCpp::NONE) {
            double beatsPerMinute = static_cast<double>(currentTempoBpm);
            double beatsPerSecond = beatsPerMinute / 60.0;

            double noteValue = 1.0; // Relative to a whole note (4 beats in 4/4)
                                    // This interpretation means:
                                    // Quarter = 1 beat
                                    // Half = 2 beats
                                    // Whole = 4 beats (an LFO cycle spanning a whole measure in 4/4)
            switch (settings.tempoDivision) {
                // Basic divisions
                case TimeDivisionCpp::WHOLE: noteValue = 4.0; break;
                case TimeDivisionCpp::HALF: noteValue = 2.0; break;
                case TimeDivisionCpp::QUARTER: noteValue = 1.0; break;
                case TimeDivisionCpp::EIGHTH: noteValue = 0.5; break;
                case TimeDivisionCpp::SIXTEENTH: noteValue = 0.25; break;
                case TimeDivisionCpp::THIRTY_SECOND: noteValue = 0.125; break;
                case TimeDivisionCpp::SIXTY_FOURTH: noteValue = 0.0625; break;
                // Dotted (add 50% to the duration)
                case TimeDivisionCpp::DOTTED_HALF: noteValue = 2.0 * 1.5; break;
                case TimeDivisionCpp::DOTTED_QUARTER: noteValue = 1.0 * 1.5; break;
                case TimeDivisionCpp::DOTTED_EIGHTH: noteValue = 0.5 * 1.5; break;
                case TimeDivisionCpp::DOTTED_SIXTEENTH: noteValue = 0.25 * 1.5; break;
                // Triplets (3 notes in the space of 2 of the base note value)
                // So, each triplet note is 2/3 the duration of the base note value.
                case TimeDivisionCpp::TRIPLET_WHOLE: noteValue = (4.0 * 2.0 / 3.0); break; // 3 whole note triplets in 2 whole notes
                case TimeDivisionCpp::TRIPLET_HALF: noteValue = (2.0 * 2.0 / 3.0); break;
                case TimeDivisionCpp::TRIPLET_QUARTER: noteValue = (1.0 * 2.0 / 3.0); break;
                case TimeDivisionCpp::TRIPLET_EIGHTH: noteValue = (0.5 * 2.0 / 3.0); break;
                case TimeDivisionCpp::TRIPLET_SIXTEENTH: noteValue = (0.25 * 2.0 / 3.0); break;
                default: noteValue = 1.0; break; // Default to quarter if unknown
            }
            
            double lfoCycleDurationSeconds = noteValue / beatsPerSecond; // Duration of one LFO cycle in seconds
            
            if (lfoCycleDurationSeconds > 0.00001) { // Avoid division by zero or extremely small values
                phaseIncrement = 1.0 / (lfoCycleDurationSeconds * static_cast<double>(sampleRate));
            } else {
                phaseIncrement = 0.0;
            }
        } else {
            phaseIncrement = 0.0; // Cannot calculate if tempo, SR or division is invalid
        }
    } else { // Rate in Hz
        if (sampleRate > 0) {
            phaseIncrement = static_cast<double>(settings.rateHz) / static_cast<double>(sampleRate);
        } else {
            phaseIncrement = 0.0;
        }
    }
}

void LfoGenerator::resetPhase() {
    phase = 0.0;
    
    if (settings.waveform == LfoWaveformCpp::RANDOM_STEP || settings.waveform == LfoWaveformCpp::RANDOM_SMOOTH) {
        // Re-randomize on phase reset for these types
        lastRandomValue = (static_cast<float>(std::rand()) / static_cast<float>(RAND_MAX)) * 2.0f - 1.0f;
        nextRandomValue = (static_cast<float>(std::rand()) / static_cast<float>(RAND_MAX)) * 2.0f - 1.0f;
        samplesUntilNextRandomStep = 0;
    }
}

void LfoGenerator::retrigger() {
    resetPhase(); // Default retrigger action is to reset phase
}

float LfoGenerator::process() {
    float value = 0.0f;
    switch (settings.waveform) {
        case LfoWaveformCpp::SINE:        value = generateSine();       break;
        case LfoWaveformCpp::TRIANGLE:    value = generateTriangle();   break;
        case LfoWaveformCpp::SQUARE:      value = generateSquare();     break;
        case LfoWaveformCpp::SAW_UP:      value = generateSawUp();      break;
        case LfoWaveformCpp::SAW_DOWN:    value = generateSawDown();    break;
        case LfoWaveformCpp::RANDOM_STEP: value = generateRandomStep(); break;
        case LfoWaveformCpp::RANDOM_SMOOTH:
            value = generateRandomStep(); // Fallback to RANDOM_STEP
            break;
        default: value = 0.0f; break;
    }

    phase += phaseIncrement;
    if (phase >= 1.0) {
        phase -= 1.0;        // For RANDOM_STEP, update the value when the phase wraps (completes a cycle)
        if (settings.waveform == LfoWaveformCpp::RANDOM_STEP || settings.waveform == LfoWaveformCpp::RANDOM_SMOOTH) {
             lastRandomValue = nextRandomValue;
             nextRandomValue = (static_cast<float>(std::rand()) / static_cast<float>(RAND_MAX)) * 2.0f - 1.0f;
        }
    }
    return value;
}

// Waveform Implementations
float LfoGenerator::generateSine() {
    return std::sin(static_cast<float>(phase * 2.0 * M_PI));
}

float LfoGenerator::generateTriangle() {
    float p = static_cast<float>(phase);
    if (p < 0.25f) {
        // Ramping up from 0 to 1 (phase 0->0.25 maps to output 0->1)
        return p * 4.0f;
    } else if (p < 0.75f) {
        // Ramping down from 1 to -1 (phase 0.25->0.75 maps to output 1->-1)
        return 1.0f - (p - 0.25f) * 4.0f;
    } else {
        // Ramping up from -1 to 0 (phase 0.75->1.0 maps to output -1->0)
        return -1.0f + (p - 0.75f) * 4.0f;
    }
}


float LfoGenerator::generateSquare() {
    return (phase < 0.5) ? 1.0f : -1.0f;
}

float LfoGenerator::generateSawUp() { // Ramp up from -1 to 1
    return (static_cast<float>(phase) * 2.0f) - 1.0f;
}

float LfoGenerator::generateSawDown() { // Ramp down from 1 to -1
    return 1.0f - (static_cast<float>(phase) * 2.0f);
}

float LfoGenerator::generateRandomStep() {
    // Value changes when phase wraps, as implemented in process()
    return lastRandomValue;
}

} // namespace audio
} // namespace theone
