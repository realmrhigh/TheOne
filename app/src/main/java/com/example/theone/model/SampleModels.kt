package com.example.theone.model

import java.util.UUID // For generating unique IDs

// Existing SampleMetadata class
data class SampleMetadata(
    val uri: String, // Can be the original file URI or a content URI
    val duration: Long, // in milliseconds
    var name: String? = null, // User-defined name
    var trimStartMs: Long = 0,
    var trimEndMs: Long = 0,
    // Fields from WAV format itself, good for metadata
    var sampleRate: Int = 44100,
    var channels: Int = 1, // Mono/Stereo
    var bitDepth: Int = 16 // e.g., 16-bit, 24-bit
) {
    init {
        if (trimEndMs == 0L && duration > 0L) {
            trimEndMs = duration
        }
    }
}

// New Sample data class
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
