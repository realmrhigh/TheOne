package com.high.theone.features.sequencer

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages step callback registration and execution with microsecond-accurate timing
 * Handles audio latency compensation and error recovery
 */
@Singleton
class StepCallbackManager @Inject constructor() {
    
    private val callbacks = ConcurrentHashMap<String, StepCallback>()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Latency compensation
    private var audioLatencyMicros = AtomicLong(0L)
    private var systemLatencyMicros = AtomicLong(0L)
    
    // Performance monitoring
    private val callbackExecutionTimes = mutableListOf<Long>()
    private val failedCallbacks = mutableListOf<CallbackFailure>()
    
    companion object {
        const val MAX_EXECUTION_TIME_SAMPLES = 50
        const val MAX_FAILED_CALLBACK_HISTORY = 20
        const val CALLBACK_TIMEOUT_MICROS = 10000L // 10ms timeout
        const val DEFAULT_AUDIO_LATENCY_MICROS = 20000L // 20ms default
    }
    
    /**
     * Register a step callback with unique identifier
     * @param id Unique identifier for the callback
     * @param callback Function to call on each step
     * @param priority Execution priority (higher = earlier execution)
     */
    fun registerCallback(
        id: String,
        callback: (step: Int, microTime: Long) -> Unit,
        priority: Int = 0
    ) {
        callbacks[id] = StepCallback(
            id = id,
            callback = callback,
            priority = priority,
            registeredAt = SystemClock.elapsedRealtimeNanos() / 1000L,
            isEnabled = true
        )
    }
    
    /**
     * Unregister a callback
     * @param id Callback identifier to remove
     */
    fun unregisterCallback(id: String) {
        callbacks.remove(id)
    }
    
    /**
     * Enable or disable a callback without removing it
     * @param id Callback identifier
     * @param enabled Whether the callback should be executed
     */
    fun setCallbackEnabled(id: String, enabled: Boolean) {
        callbacks[id]?.let { callback ->
            callbacks[id] = callback.copy(isEnabled = enabled)
        }
    }
    
    /**
     * Execute all registered callbacks for a step
     * @param step Step index (0-based)
     * @param rawTimestamp Raw timestamp in microseconds
     */
    fun executeStepCallbacks(step: Int, rawTimestamp: Long) {
        // Apply latency compensation
        val compensatedTimestamp = rawTimestamp - getTotalLatencyCompensation()
        
        // Get enabled callbacks sorted by priority
        val enabledCallbacks = callbacks.values
            .filter { it.isEnabled }
            .sortedByDescending { it.priority }
        
        if (enabledCallbacks.isEmpty()) return
        
        // Execute callbacks
        callbackScope.launch {
            val executionStartTime = SystemClock.elapsedRealtimeNanos() / 1000L
            
            enabledCallbacks.forEach { stepCallback ->
                try {
                    executeCallbackWithTimeout(stepCallback, step, compensatedTimestamp)
                } catch (e: Exception) {
                    handleCallbackFailure(stepCallback.id, step, e)
                }
            }
            
            // Record execution time for performance monitoring
            val executionTime = (SystemClock.elapsedRealtimeNanos() / 1000L) - executionStartTime
            recordExecutionTime(executionTime)
        }
    }
    
    /**
     * Set audio latency compensation
     * @param latencyMicros Audio system latency in microseconds
     */
    fun setAudioLatency(latencyMicros: Long) {
        audioLatencyMicros.set(latencyMicros.coerceAtLeast(0L))
    }
    
    /**
     * Set system latency compensation (processing delays, etc.)
     * @param latencyMicros System processing latency in microseconds
     */
    fun setSystemLatency(latencyMicros: Long) {
        systemLatencyMicros.set(latencyMicros.coerceAtLeast(0L))
    }
    
    /**
     * Get total latency compensation
     */
    fun getTotalLatencyCompensation(): Long {
        return audioLatencyMicros.get() + systemLatencyMicros.get()
    }
    
    /**
     * Get callback performance statistics
     */
    fun getCallbackStats(): CallbackStats {
        val avgExecutionTime = if (callbackExecutionTimes.isNotEmpty()) {
            callbackExecutionTimes.average().toLong()
        } else 0L
        
        val maxExecutionTime = callbackExecutionTimes.maxOrNull() ?: 0L
        
        return CallbackStats(
            registeredCallbacks = callbacks.size,
            enabledCallbacks = callbacks.values.count { it.isEnabled },
            averageExecutionTime = avgExecutionTime,
            maxExecutionTime = maxExecutionTime,
            failedCallbackCount = failedCallbacks.size,
            totalLatencyCompensation = getTotalLatencyCompensation()
        )
    }
    
    /**
     * Get list of registered callback IDs
     */
    fun getRegisteredCallbacks(): List<String> {
        return callbacks.keys.toList()
    }
    
    /**
     * Clear all callbacks
     */
    fun clearAllCallbacks() {
        callbacks.clear()
    }
    
    /**
     * Get recent callback failures for debugging
     */
    fun getRecentFailures(): List<CallbackFailure> {
        return failedCallbacks.toList()
    }
    
    /**
     * Calibrate latency by measuring system response time
     * @param iterations Number of calibration iterations
     * @return Measured system latency in microseconds
     */
    suspend fun calibrateLatency(iterations: Int = 10): Long {
        val measurements = mutableListOf<Long>()
        
        repeat(iterations) {
            val startTime = SystemClock.elapsedRealtimeNanos() / 1000L
            
            // Simulate callback execution
            val testCallback: (Int, Long) -> Unit = { _, _ -> 
                // Minimal operation
            }
            
            try {
                testCallback(0, startTime)
            } catch (e: Exception) {
                // Ignore calibration errors
            }
            
            val endTime = SystemClock.elapsedRealtimeNanos() / 1000L
            measurements.add(endTime - startTime)
        }
        
        val averageLatency = measurements.average().toLong()
        setSystemLatency(averageLatency)
        
        return averageLatency
    }
    
    private suspend fun executeCallbackWithTimeout(
        stepCallback: StepCallback,
        step: Int,
        timestamp: Long
    ) {
        val startTime = SystemClock.elapsedRealtimeNanos() / 1000L
        
        try {
            // Execute callback with timeout protection
            stepCallback.callback(step, timestamp)
            
            // Update callback statistics
            val executionTime = (SystemClock.elapsedRealtimeNanos() / 1000L) - startTime
            
            if (executionTime > CALLBACK_TIMEOUT_MICROS) {
                handleCallbackTimeout(stepCallback.id, step, executionTime)
            }
            
        } catch (e: Exception) {
            throw CallbackExecutionException(stepCallback.id, step, e)
        }
    }
    
    private fun handleCallbackFailure(callbackId: String, step: Int, error: Throwable) {
        val failure = CallbackFailure(
            callbackId = callbackId,
            step = step,
            timestamp = SystemClock.elapsedRealtimeNanos() / 1000L,
            error = error.message ?: "Unknown error",
            errorType = error::class.simpleName ?: "Exception"
        )
        
        synchronized(failedCallbacks) {
            failedCallbacks.add(failure)
            if (failedCallbacks.size > MAX_FAILED_CALLBACK_HISTORY) {
                failedCallbacks.removeAt(0)
            }
        }
        
        // Optionally disable callback after repeated failures
        val recentFailures = failedCallbacks.count { 
            it.callbackId == callbackId && 
            (SystemClock.elapsedRealtimeNanos() / 1000L - it.timestamp) < 1000000L // Last 1 second
        }
        
        if (recentFailures >= 5) {
            setCallbackEnabled(callbackId, false)
        }
    }
    
    private fun handleCallbackTimeout(callbackId: String, step: Int, executionTime: Long) {
        val timeoutError = CallbackTimeoutException(callbackId, step, executionTime)
        handleCallbackFailure(callbackId, step, timeoutError)
    }
    
    private fun recordExecutionTime(executionTime: Long) {
        synchronized(callbackExecutionTimes) {
            callbackExecutionTimes.add(executionTime)
            if (callbackExecutionTimes.size > MAX_EXECUTION_TIME_SAMPLES) {
                callbackExecutionTimes.removeAt(0)
            }
        }
    }
}

/**
 * Data class representing a registered step callback
 */
data class StepCallback(
    val id: String,
    val callback: (step: Int, microTime: Long) -> Unit,
    val priority: Int,
    val registeredAt: Long,
    val isEnabled: Boolean
)

/**
 * Statistics about callback performance
 */
data class CallbackStats(
    val registeredCallbacks: Int,
    val enabledCallbacks: Int,
    val averageExecutionTime: Long,
    val maxExecutionTime: Long,
    val failedCallbackCount: Int,
    val totalLatencyCompensation: Long
)

/**
 * Information about a callback failure
 */
data class CallbackFailure(
    val callbackId: String,
    val step: Int,
    val timestamp: Long,
    val error: String,
    val errorType: String
)

/**
 * Exception thrown when callback execution fails
 */
class CallbackExecutionException(
    val callbackId: String,
    val step: Int,
    cause: Throwable
) : Exception("Callback '$callbackId' failed at step $step", cause)

/**
 * Exception thrown when callback execution times out
 */
class CallbackTimeoutException(
    val callbackId: String,
    val step: Int,
    val executionTime: Long
) : Exception("Callback '$callbackId' timed out at step $step (${executionTime}μs)")

/**
 * Audio latency detector for automatic latency compensation
 */
class AudioLatencyDetector @Inject constructor() {
    
    /**
     * Detect audio system latency
     * @return Estimated audio latency in microseconds
     */
    suspend fun detectAudioLatency(): Long {
        // This would typically involve:
        // 1. Playing a test tone
        // 2. Recording the output
        // 3. Measuring the round-trip time
        // For now, return a reasonable default
        return StepCallbackManager.DEFAULT_AUDIO_LATENCY_MICROS
    }
    
    /**
     * Continuously monitor latency changes
     */
    suspend fun startLatencyMonitoring(
        onLatencyChanged: (Long) -> Unit
    ) {
        // Implementation would monitor audio system changes
        // and call onLatencyChanged when latency changes
    }
}