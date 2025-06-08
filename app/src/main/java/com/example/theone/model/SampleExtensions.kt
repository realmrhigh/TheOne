package com.example.theone.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
// No specific new imports needed for reverse

// Existing trim function...
fun Sample.trim(startMs: Long, endMs: Long): Sample {
    val originalMetadata = this.metadata
    val originalAudioData = this.audioData // Corrected to this.audioData

    val validatedStartMs = startMs.coerceIn(0, originalMetadata.duration)
    val validatedEndMs = endMs.coerceIn(validatedStartMs, originalMetadata.duration)

    val sampleRate = originalMetadata.sampleRate.toFloat()
    if (sampleRate <= 0f) {
        throw IllegalArgumentException("Sample rate must be positive. Found: $sampleRate")
    }

    // Ensure startFrame and endFrame calculations are safe for empty audioData
    val startFrame = (validatedStartMs / 1000.0f * sampleRate).roundToInt()
        .coerceIn(0, if (originalAudioData.isNotEmpty()) originalAudioData.size else 0)
    val endFrame = (validatedEndMs / 1000.0f * sampleRate).roundToInt()
        .coerceIn(startFrame, if (originalAudioData.isNotEmpty()) originalAudioData.size else 0)

    val newAudioData = if (originalAudioData.isNotEmpty() && startFrame < endFrame) {
        originalAudioData.copyOfRange(startFrame, endFrame)
    } else {
        FloatArray(0)
    }

    val newDurationMs = if (sampleRate > 0f && newAudioData.isNotEmpty()) {
        ((newAudioData.size / sampleRate) * 1000.0f).toLong()
    } else {
        0L
    }

    val newMetadata = originalMetadata.copy(
        name = (originalMetadata.name ?: "Sample") + " (Trimmed)",
        duration = newDurationMs,
        trimStartMs = 0,
        trimEndMs = newDurationMs,
    )

    return Sample(
        id = UUID.randomUUID().toString(),
        metadata = newMetadata,
        audioData = newAudioData
    )
}

// Existing normalize function...
fun Sample.normalize(targetPeakDb: Float = 0.0f): Sample {
    val originalAudioData = this.audioData
    if (originalAudioData.isEmpty()) {
        val newMetadata = this.metadata.copy(
            name = (this.metadata.name ?: "Sample") + " (Normalized - Empty)"
        )
        return this.copy(id = UUID.randomUUID().toString(), metadata = newMetadata)
    }

    var maxAbsoluteAmplitude = 0.0f
    for (sampleValue in originalAudioData) {
        val currentAbs = abs(sampleValue)
        if (currentAbs > maxAbsoluteAmplitude) {
            maxAbsoluteAmplitude = currentAbs
        }
    }

    if (maxAbsoluteAmplitude <= 1e-6f) { // Threshold for effective silence
         val newMetadata = this.metadata.copy(
            name = (this.metadata.name ?: "Sample") + " (Normalized - Silent)"
        )
        return this.copy(id = UUID.randomUUID().toString(), metadata = newMetadata, audioData = originalAudioData.copyOf())
    }

    val targetLinearPeak = 10.0.pow(targetPeakDb / 20.0).toFloat()
    val scalingFactor = targetLinearPeak / maxAbsoluteAmplitude
    val newAudioData = FloatArray(originalAudioData.size) { i ->
        (originalAudioData[i] * scalingFactor).coerceIn(-1.0f, 1.0f)
    }

    val newMetadata = this.metadata.copy(
        name = (this.metadata.name ?: "Sample") + " (Normalized)"
    )

    return Sample(
        id = UUID.randomUUID().toString(),
        metadata = newMetadata,
        audioData = newAudioData
    )
}

fun Sample.reverse(): Sample {
    val originalAudioData = this.audioData
    if (originalAudioData.isEmpty()) {
        // Return a new instance with a new ID, but otherwise identical
        val newMetadata = this.metadata.copy(
            name = (this.metadata.name ?: "Sample") + " (Reversed - Empty)"
        )
        return this.copy(id = UUID.randomUUID().toString(), metadata = newMetadata)
    }

    val newAudioData = originalAudioData.reversedArray() // Creates a new reversed copy

    val newMetadata = this.metadata.copy(
        name = (this.metadata.name ?: "Sample") + " (Reversed)"
        // Duration, sampleRate, channels, bitDepth, trim points remain the same relative to the new audio data.
        // If trim points were relative to original, they'd need recalculating,
        // but since a new sample is created from 'whole' audio, its trim points are 0 and duration.
    )

    return Sample(
        id = UUID.randomUUID().toString(), // New unique ID
        metadata = newMetadata,
        audioData = newAudioData
    )
}
