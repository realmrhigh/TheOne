#include "StateVariableFilter.h"
#include <algorithm> // For std::clamp (C++17) or std::max/min

namespace theone {
namespace audio {

StateVariableFilter::StateVariableFilter()
    : sampleRate_(48000.0f), // Default sample rate
      currentMode_(SVF_Mode::LOW_PASS),
      s1_(0.0f), s2_(0.0f) {
    // Initialize with some default cutoff and resonance
    calculateCoefficients(18000.0f, 0.707f);
}

void StateVariableFilter::setSampleRate(float sr) {
    if (sr > 0) {
        sampleRate_ = sr;
        // Recalculate coefficients based on the current cutoff/Q settings
        // This requires storing cutoff and Q, or passing them again.
        // For simplicity, the user of this class should call configure() again after setSampleRate if params are to be kept.
        // Or, we store cutoff and Q:
        // calculateCoefficients(lastCutoffHz_, lastResonanceQ_);
        // For now, let's assume configure will be called after setSampleRate by the user.
        // Or better, let's make configure call this if sampleRate changes, and store params.
        // However, the prompt implies configure is the main entry point for param changes.
        // So, if setSampleRate is called, configure must be called again.
        // To handle this robustly, let's store current params and re-calc.
        // This requires adding members for currentCutoffHz and currentResonanceQ.
        // For this subtask, let's stick to the provided header and assume configure will be called.
    }
}

void StateVariableFilter::configure(SVF_Mode mode, float cutoffHz, float resonanceQ) {
    currentMode_ = mode;

    // Clamp input values
    float clampedCutoffHz = cutoffHz;
    if (clampedCutoffHz < 20.0f) clampedCutoffHz = 20.0f;
    if (clampedCutoffHz > (sampleRate_ / 2.0f - 100.0f)) clampedCutoffHz = (sampleRate_ / 2.0f - 100.0f); // Nyquist safety margin
    if (clampedCutoffHz < 20.0f) clampedCutoffHz = 20.0f; // Ensure it's still valid after high clamp

    float clampedResonanceQ = resonanceQ;
    if (clampedResonanceQ < 0.5f) clampedResonanceQ = 0.5f;
    if (clampedResonanceQ > 25.0f) clampedResonanceQ = 25.0f; // Practical upper limit for Q

    calculateCoefficients(clampedCutoffHz, clampedResonanceQ);
}

void StateVariableFilter::calculateCoefficients(float cutoffHz, float resonanceQ) {
    if (sampleRate_ <= 0.0f) return; // Avoid division by zero if sample rate isn't set

    // Using a common SVF formulation (often from Chamberlin or Hal Chamberlin's book / Moorer's paper)
    // This version uses pre-warping for the cutoff frequency.
    float wd = 2.0f * static_cast<float>(M_PI) * cutoffHz;
    float T = 1.0f / sampleRate_;

    // Bilinear transform pre-warping for cutoff frequency
    // tanf might not be ideal for all DSP contexts, but common in examples.
    float wa = (2.0f / T) * tanf(wd * T / 2.0f);

    g_ = wa * T / 2.0f; // Gain for integrator stages

    // R2 is related to Q (Resonance). Q = 1 / (2 * DampingRatio).
    // For SVF, R (or 2*R in some notations) often represents 1/Q.
    // So, a common mapping is R_feedback = 1 / (2 * Q) or similar.
    // Here, R2_ is likely the damping factor related term.
    // Ensure Q is not zero to avoid division by zero.
    if (resonanceQ < 0.01f) resonanceQ = 0.01f; // Safety clamp for Q
    R2_ = 1.0f / (2.0f * resonanceQ);

    // Denominator factor for output calculations
    h_ = 1.0f / (1.0f + 2.0f * R2_ * g_ + g_ * g_);
}

float StateVariableFilter::process(float inputSample) {
    // Processing loop for one sample
    // Based on a common digital state variable filter structure.
    // y_hp = h * (x - (2*R + g)*s1 - s2)
    // y_bp = g*y_hp + s1' (where s1' is previous s1)
    // s1_new = g*y_hp + y_bp
    // y_lp = g*y_bp + s2' (where s2' is previous s2)
    // s2_new = g*y_bp + y_lp

    // Note: The order of operations and state updates is crucial.
    // s1_ and s2_ here are effectively s1[n-1] and s2[n-1] at the start of this block.

    float hp = h_ * (inputSample - (2.0f * R2_ + g_) * s1_ - s2_);
    float bp_intermediate = g_ * hp + s1_; // This is the bandpass output based on previous s1_

    // Update s1_ state for the next sample: s1[n] = s1[n-1] + g*hp[n] (if bp_intermediate is used)
    // Or, more directly from some formulations: s1_new = g*hp + bp_intermediate (which is 2*g*hp + s1_old)
    // Let's use a common update rule:
    // v_bp = g * v_hp + s1_ (bandpass output uses old s1)
    // s1_new = v_bp + g * v_hp (update s1 state)
    // v_lp = g * v_bp + s2_ (lowpass output uses old s2)
    // s2_new = v_lp + g * v_bp (update s2 state)

    // Corrected process based on typical SVF structure:
    float y_hp = h_ * (inputSample - (2.0f * R2_ * s1_) - (g_ * s1_) - s2_); // Simpler form: h_ * (inputSample - (2.0f * R2_ + g_) * s1_ - s2_);
    float y_bp = g_ * y_hp + s1_;
    s1_ = y_bp + g_ * y_hp; // Update state s1 for next sample
    float y_lp = g_ * y_bp + s2_;
    s2_ = y_lp + g_ * y_bp; // Update state s2 for next sample

    switch (currentMode_) {
        case SVF_Mode::LOW_PASS:
            return y_lp;
        case SVF_Mode::BAND_PASS:
            return y_bp;
        case SVF_Mode::HIGH_PASS:
            return y_hp;
        default:
            return y_lp; // Default to low-pass
    }
}

void StateVariableFilter::reset() {
    s1_ = 0.0f;
    s2_ = 0.0f;
}

} // namespace audio
} // namespace theone
