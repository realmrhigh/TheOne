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
data class Sequence(
    val id: String,
    var name: String,
    var trackId: String,
    // Add other sequence-specific properties here, e.g., notes, automation, etc.
)

@Serializable
data class Project(
    val id: String,
    var name: String,
    val tracks: MutableList<Track> = mutableListOf(),
    val sequences: MutableList<Sequence> = mutableListOf(),
    val samplePool: MutableList<SampleMetadata> = mutableListOf(),
)
