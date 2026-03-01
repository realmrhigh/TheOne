package com.high.theone.model

// --- Enums to be used by editor and model ---

// Envelope type for synths and samplers
enum class EnvelopeType {
    ADSR, AHDSR, AR, CUSTOM
}

// Envelope settings for amplitude, filter, pitch, etc.
data class EnvelopeSettings(
    val attackMs: Float = 10f,
    val decayMs: Float = 100f,
    val sustainLevel: Float = 1.0f,
    val releaseMs: Float = 200f,
    val holdMs: Float = 0f,
    val type: EnvelopeType = EnvelopeType.ADSR,
    val hasSustain: Boolean = true
)

// Effect settings for a pad or track (extensible)
data class EffectSetting(
    val type: String = "",
    val amount: Float = 0.0f,
    val params: Map<String, Float> = emptyMap()
)

// LFO (Low Frequency Oscillator) settings
data class LFOSettings(
    val rate: Float = 1.0f,
    val depth: Float = 0.5f,
    val shape: String = "sine",
    val destination: String = "pitch"
)

// Modulation routing (source -> destination)
data class ModulationRouting(
    val source: String = "",
    val destination: String = "",
    val amount: Float = 0.0f
)

// --- SVF Filter ---

/**
 * State Variable Filter mode.
 * Ordinals MUST match SVF_Mode in StateVariableFilter.h and PadSettings.h:
 *   LOW_PASS=0, BAND_PASS=1, HIGH_PASS=2
 */
enum class FilterMode {
    LOW_PASS,   // ordinal 0
    BAND_PASS,  // ordinal 1
    HIGH_PASS   // ordinal 2
}

/**
 * Per-pad SVF filter settings.
 * Passed to AudioEngineControl.setPadFilter() and ultimately to C++ setPadFilter().
 */
data class FilterSettings(
    val enabled: Boolean = false,
    val mode: FilterMode = FilterMode.LOW_PASS,
    val cutoffHz: Float = 20000f,   // 20 Hz – 20 kHz; default = fully open
    val resonance: Float = 0.707f   // 0.5 – 25; 0.707 = Butterworth (no resonance peak)
)

// ─── Full Synth State ─────────────────────────────────────────────────────────

enum class OscWaveformKt(val index: Int, val label: String) {
    SINE(0, "Sine"), SAW(1, "Saw"), SQUARE(2, "Sqr"), TRIANGLE(3, "Tri"), NOISE(4, "Noise")
}

enum class LfoShapeKt(val index: Int, val label: String) {
    SINE(0, "Sine"), TRIANGLE(1, "Tri"), SQUARE(2, "Sqr"),
    SAW_UP(3, "SawU"), SAW_DOWN(4, "SawD"), RANDOM_STEP(5, "Step"), RANDOM_SMOOTH(6, "Rand")
}

enum class LfoDestKt(val index: Int, val label: String) {
    NONE(0, "None"), PITCH(1, "Pitch"), VOLUME(2, "Vol"), FILTER(3, "Filter"), PAN(4, "Pan")
}

data class SynthState(
    // OSC 1
    val osc1Wave: Int     = 1,     // SAW
    val osc1Octave: Int   = 0,
    val osc1Semi: Int     = 0,
    val osc1Fine: Float   = 0f,
    val osc1Level: Float  = 1.0f,
    // OSC 2
    val osc2Wave: Int     = 0,     // SINE
    val osc2Octave: Int   = 0,
    val osc2Semi: Int     = 0,
    val osc2Fine: Float   = 5f,
    val osc2Level: Float  = 0.0f,
    // Sub / Noise
    val subLevel: Float   = 0f,
    val noiseLevel: Float = 0f,
    // Amp Envelope
    val ampAttack: Float  = 10f,
    val ampDecay: Float   = 150f,
    val ampSustain: Float = 1.0f,
    val ampRelease: Float = 200f,
    // Filter
    val filterType: Int          = 0,      // 0=LP 1=BP 2=HP
    val filterCutoff: Float      = 8000f,
    val filterResonance: Float   = 0.707f,
    val filterEnvAmt: Float      = 0f,
    val filterKeyTrack: Float    = 0f,
    val filterVelSens: Float     = 0f,
    // Filter Envelope
    val filtAttack: Float  = 10f,
    val filtDecay: Float   = 150f,
    val filtSustain: Float = 0.5f,
    val filtRelease: Float = 200f,
    // LFO 1
    val lfo1Rate: Float  = 2f,
    val lfo1Depth: Float = 0f,
    val lfo1Shape: Int   = 0,
    val lfo1Dest: Int    = 1,    // Pitch
    // LFO 2
    val lfo2Rate: Float  = 1f,
    val lfo2Depth: Float = 0f,
    val lfo2Shape: Int   = 0,
    val lfo2Dest: Int    = 3,    // Filter
    // Master
    val masterVolume: Float   = 0.7f,
    val pan: Float            = 0f,
    val portamento: Float     = 0f,
    val pitchBendRange: Float = 2f,
    // Keyboard
    val keyboardOctave: Int = 4
)

// Add more synth-related models as needed
