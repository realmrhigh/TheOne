package com.high.theone.model
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

// --- Sample and SampleMetadata models for drum/synth engine ---

@Serializable
data class Sample(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val filePath: String = "",
    val metadata: SampleMetadata = SampleMetadata()
)

@Serializable
data class SampleMetadata(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val filePath: String = "",
    val durationMs: Long = 0L,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val fileSizeBytes: Long = 0L,
    val format: String = "wav", // wav, mp3, flac, etc.
    val rootNote: Int = 60,
    val tags: List<String> = emptyList(),
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val projectId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val checksum: String? = null, // For file integrity verification
    val isTemporary: Boolean = false, // For temporary recordings
    val originalFilePath: String? = null // Path to original file if copied
) {
    /**
     * Get the effective duration considering trim settings
     */
    val effectiveDurationMs: Long
        get() = if (trimStartMs > 0 || trimEndMs > 0) {
            val endTime = if (trimEndMs > 0) trimEndMs else durationMs
            endTime - trimStartMs
        } else {
            durationMs
        }
    
    /**
     * Check if the sample has been trimmed
     */
    val isTrimmed: Boolean
        get() = trimStartMs > 0 || (trimEndMs > 0 && trimEndMs < durationMs)
    
    /**
     * Get formatted duration string
     */
    val formattedDuration: String
        get() {
            val totalSeconds = effectiveDurationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    
    /**
     * Get formatted file size string
     */
    val formattedFileSize: String
        get() {
            return when {
                fileSizeBytes < 1024 -> "${fileSizeBytes}B"
                fileSizeBytes < 1024 * 1024 -> "${fileSizeBytes / 1024}KB"
                else -> "${fileSizeBytes / (1024 * 1024)}MB"
            }
        }
    
    /**
     * Check if this is a valid sample
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && 
               filePath.isNotBlank() && 
               durationMs > 0 && 
               sampleRate > 0 && 
               channels > 0
    }
}

// SampleLayer for multi-layered drum pads
@Serializable
data class SampleLayer(
    val sample: Sample = Sample(),
    @Contextual val velocityRange: IntRange = 1..127,
    val gain: Float = 1.0f
)
