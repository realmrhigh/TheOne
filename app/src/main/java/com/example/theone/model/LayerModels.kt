package com.example.theone.model

import java.util.UUID

data class SampleLayer(
    val id: String = UUID.randomUUID().toString(), // Unique ID for the layer
    var sampleId: String, // ID of the sample from the SamplePool (e.g., URI or a persistent ID)
    var enabled: Boolean = true,

    // Velocity mapping
    var velocityRangeMin: Int = 0,
    var velocityRangeMax: Int = 127,

    // Tuning and Volume offsets from the main PadSettings
    var tuningCoarseOffset: Int = 0, // Semitones
    var tuningFineOffset: Int = 0,   // Cents
    var volumeOffsetDb: Float = 0f, // in Decibels
    var panOffset: Float = 0f // -1.0 (Left) to 1.0 (Right)
)

enum class LayerTriggerRule {
    VELOCITY, // Play the layer whose velocity range matches the input
    CYCLE,    // Play the next layer in the list each time
    RANDOM    // Play a random layer
}
