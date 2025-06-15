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
    INTERNAL_LOOPBACK,
    EXTERNAL_USB
}
