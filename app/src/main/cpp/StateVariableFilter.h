// app/src/main/cpp/StateVariableFilter.h
#ifndef STATE_VARIABLE_FILTER_H
#define STATE_VARIABLE_FILTER_H
#include <cmath> // For M_PI, tanf, etc.

// Ensure M_PI is defined (often in cmath, but can be guarded)
#ifndef M_PI
    #define M_PI 3.14159265358979323846
#endif

namespace theone {
namespace audio {

enum class SVF_Mode {
    LOW_PASS,
    BAND_PASS,
    HIGH_PASS
};

class StateVariableFilter {
public:
    StateVariableFilter();
    void setSampleRate(float sr);
    void configure(SVF_Mode mode, float cutoffHz, float resonanceQ); // resonanceQ typically 0.5 (min) to ~20+
    float process(float inputSample);
    void reset();
    // Ordinals match Kotlin FilterMode enum: LOW_PASS=0, BAND_PASS=1, HIGH_PASS=2
    SVF_Mode getCurrentMode() const { return currentMode_; }

private:
    float sampleRate_;
    SVF_Mode currentMode_;
    // Internal states
    float s1_, s2_; // State variables for integrators
    // Coefficients pre-calculated by configure()
    float g_, R2_, h_; // Parameters for a common SVF variant

    void calculateCoefficients(float cutoffHz, float resonanceQ);
};

} // namespace audio
} // namespace theone

#endif // STATE_VARIABLE_FILTER_H
