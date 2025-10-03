package com.high.theone.features.sequencer

import com.high.theone.audio.AudioEngineControl

/**
 * Extended audio engine interface specifically for sequencer integration
 * Provides high-precision timing and scheduling capabilities
 */
interface SequencerAudioEngine : AudioEngineControl {
    
    /**
     * Schedule multiple step triggers for a pattern
     * @param triggers List of step triggers with timing information
     * @return True if all triggers were successfully scheduled
     */
    suspend fun schedulePatternTriggers(triggers: List<StepTrigger>): Boolean
    
    /**
     * Update scheduled triggers for real-time parameter changes
     * @param triggers Updated list of triggers
     */
    suspend fun updateScheduledTriggers(triggers: List<StepTrigger>)
    
    /**
     * Get the current audio thread timestamp for synchronization
     * @return Current timestamp in microseconds
     */
    suspend fun getCurrentAudioTimestamp(): Long
    
    /**
     * Calculate the exact trigger time for a step considering latency compensation
     * @param stepTime The intended step time
     * @param lookahead Additional lookahead time in microseconds
     * @return Compensated trigger time
     */
    suspend fun calculateTriggerTime(stepTime: Long, lookahead: Long = 0L): Long
    
    /**
     * Enable/disable sequencer mode optimizations
     * @param enabled True to enable sequencer optimizations
     */
    suspend fun setSequencerMode(enabled: Boolean)
    
    /**
     * Get performance metrics for sequencer playback
     * @return Performance metrics
     */
    suspend fun getSequencerPerformanceMetrics(): SequencerPerformanceMetrics
}

/**
 * Represents a scheduled step trigger with precise timing
 */
data class StepTrigger(
    val padIndex: Int,
    val velocity: Float,
    val timestamp: Long, // Microseconds
    val stepIndex: Int,
    val patternId: String
) {
    init {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(velocity in 0f..1f) { "Velocity must be between 0.0 and 1.0" }
        require(timestamp >= 0) { "Timestamp must be non-negative" }
        require(stepIndex >= 0) { "Step index must be non-negative" }
    }
}

/**
 * Performance metrics for sequencer playback monitoring
 */
data class SequencerPerformanceMetrics(
    val averageLatency: Long,        // Average trigger latency in microseconds
    val maxLatency: Long,            // Maximum latency observed
    val minLatency: Long,            // Minimum latency observed
    val jitter: Long,                // Timing jitter in microseconds
    val missedTriggers: Int,         // Number of missed triggers
    val scheduledTriggers: Int,      // Number of currently scheduled triggers
    val cpuUsage: Float,             // CPU usage percentage
    val memoryUsage: Long,           // Memory usage in bytes
    val isRealTimeMode: Boolean,     // Whether running in real-time mode
    val bufferUnderruns: Int         // Number of audio buffer underruns
)