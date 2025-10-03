package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.StateFlow

/**
 * High-precision timing engine interface for step sequencer playback
 * Provides microsecond-accurate timing for musical applications
 */
interface TimingEngine {
    
    /**
     * Current playback state
     */
    val isPlaying: StateFlow<Boolean>
    val isPaused: StateFlow<Boolean>
    val currentStep: StateFlow<Int>
    val stepProgress: StateFlow<Float>
    
    /**
     * Start playback with specified tempo and swing
     * @param tempo BPM (60-200)
     * @param swing Swing amount (0.0-0.75)
     * @param patternLength Number of steps in pattern (8, 16, 24, 32)
     */
    fun start(tempo: Float, swing: Float = 0f, patternLength: Int = 16)
    
    /**
     * Stop playback and reset to beginning
     */
    fun stop()
    
    /**
     * Pause playback at current position
     */
    fun pause()
    
    /**
     * Resume playback from paused position
     */
    fun resume()
    
    /**
     * Change tempo in real-time without stopping playback
     * @param bpm New tempo (60-200 BPM)
     */
    fun setTempo(bpm: Float)
    
    /**
     * Change swing amount in real-time
     * @param amount Swing amount (0.0-0.75)
     */
    fun setSwing(amount: Float)
    
    /**
     * Get current step position (0-based)
     */
    fun getCurrentStep(): Int
    
    /**
     * Get progress within current step (0.0-1.0)
     */
    fun getStepProgress(): Float
    
    /**
     * Register callback for step trigger events
     * Callback receives step index and precise timestamp in microseconds
     */
    fun scheduleStepCallback(callback: (step: Int, microTime: Long) -> Unit)
    
    /**
     * Register callback for pattern completion events
     * Called when pattern reaches the end and loops back to beginning
     */
    fun schedulePatternCompleteCallback(callback: () -> Unit)
    
    /**
     * Remove step callback
     */
    fun removeStepCallback()
    
    /**
     * Remove pattern completion callback
     */
    fun removePatternCompleteCallback()
    
    /**
     * Get timing statistics for performance monitoring
     */
    fun getTimingStats(): TimingStats
    
    /**
     * Release resources and cleanup
     */
    fun release()
}

/**
 * Timing statistics for performance monitoring
 */
data class TimingStats(
    val averageJitter: Long,      // Average timing jitter in microseconds
    val maxJitter: Long,          // Maximum jitter observed
    val missedCallbacks: Int,     // Number of missed timing callbacks
    val cpuUsage: Float,          // Estimated CPU usage percentage
    val isRealTime: Boolean       // Whether running in real-time priority
)

/**
 * Transport actions for timing engine control
 */
sealed class TransportAction {
    object Play : TransportAction()
    object Stop : TransportAction()
    object Pause : TransportAction()
    object Resume : TransportAction()
    object ToggleRecord : TransportAction()
    data class SetTempo(val bpm: Float) : TransportAction()
    data class SetSwing(val amount: Float) : TransportAction()
    data class SetPatternLength(val length: Int) : TransportAction()
}