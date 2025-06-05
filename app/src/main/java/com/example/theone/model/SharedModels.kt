package com.example.theone.model

enum class PlaybackMode {
    ONE_SHOT,
    NOTE_ON_OFF
}

data class EnvelopeSettings(
    val attackMs: Float,
    val decayMs: Float,
    val sustainLevel: Float,
    val releaseMs: Float
)
