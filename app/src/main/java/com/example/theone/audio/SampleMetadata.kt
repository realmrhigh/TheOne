package com.example.theone.audio

// Simplified for recording output initially:
data class SampleMetadata(
    val id: String, // Can be generated or use file name
    val name: String, // Can be same as ID or user-defined later
    val filePathUri: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int
    // Other fields like BPM, key, rootNote can be null or default if not determined during recording
)
