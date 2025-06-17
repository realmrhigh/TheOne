package com.high.theone.model
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

// --- Sample and SampleMetadata models for drum/synth engine ---

@Serializable
data class Sample(
    @Contextual val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val filePath: String = "",
    val metadata: SampleMetadata = SampleMetadata()
)

@Serializable
data class SampleMetadata(
    val durationMs: Long = 0L,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val rootNote: Int = 60,
    val tags: List<String> = emptyList(),
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L
)

// SampleLayer for multi-layered drum pads
@Serializable
data class SampleLayer(
    val sample: Sample = Sample(),
    @Contextual val velocityRange: IntRange = 1..127,
    val gain: Float = 1.0f
)
