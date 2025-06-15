package com.example.theone.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SampleMetadata(
    val id: String, // Unique ID for the sample
    var name: String, // User-defined name
    val uri: String, // URI to the actual audio file (merged from filePathUri and uri)
    val duration: Long, // in milliseconds (merged from durationMs and duration)
    var sampleRate: Int = 44100,
    var channels: Int = 1, // Mono/Stereo
    var bitDepth: Int = 16, // e.g., 16-bit, 24-bit
    val detectedBpm: Float? = null,
    val detectedKey: String? = null,
    var userBpm: Float? = null,
    var userKey: String? = null,
    var rootNote: Int = 60, // MIDI C3, default root note
    var trimStartMs: Long = 0,
    var trimEndMs: Long = 0 // Will be set in init block if 0 and duration > 0
) {
    val durationMs: String
        get() {
            TODO()
        }

    init {
        if (trimEndMs == 0L && duration > 0L) {
            trimEndMs = duration
        }
        // Ensure name is not empty, provide a default if necessary
        if (name.isBlank()) {
            name = "Sample" // Or generate a name based on URI/ID
        }
    }
}

// Existing Sample data class (ensure it uses the updated SampleMetadata)
data class Sample(
    val id: String = UUID.randomUUID().toString(), // Unique identifier for this loaded sample instance
    val metadata: SampleMetadata,
    val audioData: FloatArray // Raw audio data, normalized to [-1.0, 1.0]
) {
    // Override equals and hashCode if audioData comparison is too heavy or not needed for equality.
    // For now, default behavior is fine. Consider if ID alone should define equality.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample

        if (id != other.id) return false
        if (metadata != other.metadata) return false
        // Optionally skip audioData for equality if it's too slow or IDs are truly unique and define identity
        // if (!audioData.contentEquals(other.audioData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + metadata.hashCode()
        // result = 31 * result + audioData.contentHashCode() // Optional
        return result
    }
}
