package com.example.theone.model
import kotlinx.serialization.Serializable // Ensure this import is present

@Serializable // Add this
data class SampleMetadata( // Placeholder definition
    val uri: String,
    val name: String,
    // Add other metadata properties if needed
)

@Serializable
data class Track(
    val id: String,
    var name: String,
    // Add other track-specific properties here, e.g., volume, pan, etc.
)

@Serializable
data class Project(
    val id: String,
    var name: String,
    val tracks: MutableList<Track> = mutableListOf(),
    val sequences: MutableList<com.example.theone.model.Sequence> = mutableListOf(),
    val samplePool: MutableList<SampleMetadata> = mutableListOf(),
)
