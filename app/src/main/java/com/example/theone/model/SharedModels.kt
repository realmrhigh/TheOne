package com.example.theone.model

// Define PlaybackMode enum
enum class PlaybackMode {
    ONE_SHOT,
    LOOP,
    GATE,
    NOTE_ON_OFF
}

// EnvelopeSettings is now defined in com.example.theone.model.SynthModels

enum class AudioInputSource {
    MICROPHONE,
    INTERNAL_LOOPBACK,
    EXTERNAL_USB
}
