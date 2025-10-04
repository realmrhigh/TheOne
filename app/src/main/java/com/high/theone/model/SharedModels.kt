package com.high.theone.model

// EnvelopeSettings is now defined in com.high.theone.model.SynthModels

// Minimal stub for LayerTriggerRule (expand as needed)
enum class LayerTriggerRule {
    VELOCITY, ROUND_ROBIN, RANDOM, FIRST, LAST
}

// Minimal stub for ModelEnvelopeTypeInternal (expand as needed)
enum class ModelEnvelopeTypeInternal {
    ADSR, AHDSR, AR, CUSTOM
}

// Minimal stub for PadItem (expand as needed)
data class PadItem(
    val id: Int = 0,
    val name: String = "Pad"
)

// Minimal stub for LoopMode (expand as needed)
enum class LoopMode {
    NONE, FORWARD, PINGPONG, REVERSE
}

// Minimal stub for EffectInstance (expand as needed)
data class EffectInstance(
    val id: String = "",
    val type: String = "",
    val params: Map<String, Float> = emptyMap()
)
