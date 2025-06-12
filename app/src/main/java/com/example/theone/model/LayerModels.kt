package com.example.theone.model

data class SampleLayer(
    val id: String, // Unique ID for the layer
    var sampleId: String, // ID of the sample from the SamplePool
    var enabled: Boolean = true,

    // Velocity mapping
    var velocityRangeMin: Int = 0,
    var velocityRangeMax: Int = 127,

    // Tuning and Volume offsets from the main PadSettings
    var tuningCoarseOffset: Int = 0, // Semitones (original field for offsets)
    var tuningFineOffset: Int = 0,   // Cents (original field for offsets)
    var volumeOffsetDb: Float = 0f, // in Decibels (original field for offsets)
    var panOffset: Float = 0f, // -1.0 to 1.0 (original field for offsets)

    // --- Fields added for compatibility with DrumProgramEditViewModel/Screen ---
    // Absolute fields (tuningSemi, tuningFine, volume, pan) were removed.
    // Offsets (tuningCoarseOffset, tuningFineOffset, volumeOffsetDb, panOffset) are primary.
    var sampleNameCache: String = "N/A", // For UI display
    var startPoint: Float = 0.0f, // Sample start position 0.0 to 1.0
    var endPoint: Float = 1.0f,   // Sample end position 0.0 to 1.0
    var loopPoint: Float = 0.0f,  // Loop point 0.0 to 1.0
    var loopEnabled: Boolean = false,
    var reverse: Boolean = false
)

enum class LayerTriggerRule {
    VELOCITY, // Play the layer whose velocity range matches the input
    CYCLE,    // Play the next layer in the list each time
    RANDOM    // Play a random layer
}
