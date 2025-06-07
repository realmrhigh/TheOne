package com.example.theone.model

data class SampleMetadata(
    val uri: String,
    val duration: Long, // in milliseconds
    val name: String? = null,
    val trimStartMs: Long = 0,
    var trimEndMs: Long = 0
) {
    init {
        if (trimEndMs == 0L) {
            trimEndMs = duration
        }
    }
}
