package com.example.theone.features.drumtrack.model

data class PadSettings(
    val sampleId: String? = null,
    val sampleName: String? = null,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // Center
    val tuning: Float = 0.0f // No change in pitch
)
