package com.example.theone.model

// Define PlaybackMode enum
enum class PlaybackMode {
    ONE_SHOT,
    NOTE_ON_OFF // Add other modes as needed
}

// Define EnvelopeSettings data class
data class EnvelopeSettings(
    val attackMs: Float,
    val decayMs: Float,
    val sustainLevel: Float,
    val releaseMs: Float
)
