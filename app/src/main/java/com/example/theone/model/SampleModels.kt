package com.example.theone.model

enum class LoopMode {
    NONE,
    FORWARD,
}

data class SampleMetadata(
    val id: String,
    val name: String,
    val filePathUri: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val detectedBpm: Float? = null,
    val detectedKey: String? = null,
    var userBpm: Float? = null,
    var userKey: String? = null,
    var rootNote: Int = 60, // MIDI C3
    var trimStartMs: Long = 0L,
    var trimEndMs: Long = durationMs, // Default to full duration
    var loopStartMs: Long? = null,
    var loopEndMs: Long? = null,
    var loopMode: LoopMode = LoopMode.NONE
) {
    init {
        // Ensure trimEndMs is initialized properly if it's 0 but duration is not
        if (this.trimEndMs == 0L && this.durationMs > 0L) {
            this.trimEndMs = this.durationMs
        }
        // Coerce trim points to be within bounds and logical
        if (this.trimStartMs < 0L) {
            this.trimStartMs = 0L
        }
        if (this.trimEndMs > this.durationMs) {
            this.trimEndMs = this.durationMs
        }
        if (this.trimStartMs > this.trimEndMs) {
            // If start is somehow after end, set start to end (or end to start, effectively a zero-length selection at end)
            this.trimStartMs = this.trimEndMs
        }
    }

    // Calculated property for the effective duration of the trimmed selection
    fun getEffectiveDuration(): Long = if (trimEndMs > trimStartMs) trimEndMs - trimStartMs else 0L
}
