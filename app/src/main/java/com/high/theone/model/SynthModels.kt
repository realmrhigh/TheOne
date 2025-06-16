package com.high.theone.model

// --- Enums to be used by editor and model ---

// Envelope type for synths and samplers
enum class EnvelopeType {
    ADSR, AHDSR, AR, CUSTOM
}

// Envelope settings for amplitude, filter, pitch, etc.
data class EnvelopeSettings(
    val attack: Float = 0.01f,
    val decay: Float = 0.1f,
    val sustain: Float = 1.0f,
    val release: Float = 0.2f,
    val hold: Float = 0.0f,
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

// Add more synth-related models as needed
