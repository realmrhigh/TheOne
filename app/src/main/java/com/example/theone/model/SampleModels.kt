package com.example.theone.model

import android.net.Uri

enum class LoopMode {
    ONE_SHOT,
    LOOP,
    PING_PONG
}

data class SampleMetadata(
    val id: String,
    val name: String,
    val uri: Uri,
    val durationMs: Long,
    var trimStartMs: Float = 0f,
    var trimEndMs: Float = durationMs.toFloat(),
    var loopStartMs: Float = 0f,
    var loopEndMs: Float = durationMs.toFloat(),
    var loopMode: LoopMode = LoopMode.ONE_SHOT
) {
    fun getEffectiveDuration(): Long {
        return (trimEndMs - trimStartMs).toLong()
    }
}
