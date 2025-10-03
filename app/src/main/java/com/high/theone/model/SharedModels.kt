package com.high.theone.model

// Define PlaybackMode enum
enum class PlaybackMode {
    ONE_SHOT,
    LOOP,
    GATE,
    NOTE_ON_OFF
}

// EnvelopeSettings is now defined in com.high.theone.model.SynthModels

enum class AudioInputSource {
    MICROPHONE,
    LINE_IN,
    USB_AUDIO
}

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
    val type: String = "",
    val params: Map<String, Float> = emptyMap()
)
