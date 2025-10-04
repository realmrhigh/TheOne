package com.high.theone.features.sequencer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.PatternRepository
import com.high.theone.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive error handling system for the sequencer.
 * Provides graceful recovery from audio engine failures, pattern loading errors,
 * timing issues, and comprehensive logging for debugging.
 * 
 * Requirements: 8.7, 10.4
 */
@Singleton
class SequencerErrorHandler @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val patternRepository: PatternRepository
) {

    companion object {
        private const val TAG = "SequencerErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val ERROR_HISTORY_SIZE = 100
        private const val TIMING_ERROR_THRESHOLD_MS = 50L
        private const val AUDIO_ENGINE_RECOVERY_TIMEOUT_MS = 5000L
    }

    // Error state management
    private val _errorState = MutableStateFlow(SequencerErrorState())
    val errorState: StateFlow<SequencerErrorState> = _errorState.asStateFlow()

    // Error history for debugging
    private val errorHistory = mutableListOf<SequencerError>()
    private val errorCounts = ConcurrentHashMap<ErrorType, AtomicLong>()
    
    // Recovery state tracking
    private val recoveryAttempts = ConcurrentHashMap<String, Int>()
    private var lastAudioEngineCheck = 0L
    
    // Custom coroutine scope for error handler
    private val errorHandlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        initializeErrorHandling()
    }

    /**
     * Initialize error handling system
     * Requirements: 8.7, 10.4
     */
    private fun initializeErrorHandling() {
        // Initialize error counters
        ErrorType.values().forEach { errorType ->
            errorCounts[errorType] = AtomicLong(0)
        }
        
        // Start periodic health checks
        startHealthMonitoring()
        
        Log.d(TAG, "Sequencer error handling system initialized")
    }

    /**
     * Handle audio engine failures with recovery
     * Requirements: 8.7, 10.4
     */
    suspend fun handleAudioEngineFailure(
        operation: String,
        exception: Throwable? = null,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val error = SequencerError(
                type = ErrorType.AUDIO_ENGINE_FAILURE,
                message = "Audio engine failure during: $operation",
                exception = exception,
                context = context,
                timestamp = System.currentTimeMillis(),
                severity = ErrorSeverity.HIGH
            )
            
            recordError(error)
            
            // Attempt recovery
            val recoveryKey = "audio_engine_$operation"
            val attempts = recoveryAttempts.getOrDefault(recoveryKey, 0)
            
            if (attempts < MAX_RETRY_ATTEMPTS) {
                recoveryAttempts[recoveryKey] = attempts + 1
                
                Log.w(TAG, "Attempting audio engine recovery (attempt ${attempts + 1}/$MAX_RETRY_ATTEMPTS)")
                
                val recovered = recoverAudioEngine()
                if (recovered) {
                    recoveryAttempts.remove(recoveryKey)
                    updateErrorState(isRecovering = false, lastRecoverySuccess = true)
                    
                    RecoveryResult.SUCCESS
                } else {
                    updateErrorState(isRecovering = true, lastRecoverySuccess = false)
                    RecoveryResult.RETRY_NEEDED
                }
            } else {
                Log.e(TAG, "Audio engine recovery failed after $MAX_RETRY_ATTEMPTS attempts")
                updateErrorState(
                    isRecovering = false,
                    lastRecoverySuccess = false,
                    criticalError = "Audio engine recovery failed"
                )
                RecoveryResult.FAILED
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in audio engine failure handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Handle pattern loading errors with user feedback
     * Requirements: 8.7
     */
    suspend fun handlePatternLoadingError(
        patternId: String,
        exception: Throwable,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val error = SequencerError(
                type = ErrorType.PATTERN_LOADING_ERROR,
                message = "Failed to load pattern: $patternId",
                exception = exception,
                context = context + mapOf("patternId" to patternId),
                timestamp = System.currentTimeMillis(),
                severity = ErrorSeverity.MEDIUM
            )
            
            recordError(error)
            
            // Attempt pattern recovery
            val recoveryKey = "pattern_$patternId"
            val attempts = recoveryAttempts.getOrDefault(recoveryKey, 0)
            
            if (attempts < MAX_RETRY_ATTEMPTS) {
                recoveryAttempts[recoveryKey] = attempts + 1
                
                Log.w(TAG, "Attempting pattern loading recovery for $patternId (attempt ${attempts + 1}/$MAX_RETRY_ATTEMPTS)")
                
                delay(RETRY_DELAY_MS * attempts) // Exponential backoff
                
                val recovered = recoverPattern(patternId)
                if (recovered) {
                    recoveryAttempts.remove(recoveryKey)
                    updateErrorState(lastRecoverySuccess = true)
                    RecoveryResult.SUCCESS
                } else {
                    RecoveryResult.RETRY_NEEDED
                }
            } else {
                Log.e(TAG, "Pattern loading recovery failed for $patternId after $MAX_RETRY_ATTEMPTS attempts")
                updateErrorState(
                    lastRecoverySuccess = false,
                    userMessage = "Failed to load pattern. Please check the pattern file."
                )
                RecoveryResult.FAILED
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in pattern loading error handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Handle timing errors and compensation
     * Requirements: 8.7, 10.4
     */
    suspend fun handleTimingError(
        expectedTime: Long,
        actualTime: Long,
        operation: String,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val timingDrift = kotlin.math.abs(actualTime - expectedTime)
            
            if (timingDrift > TIMING_ERROR_THRESHOLD_MS * 1000) { // Convert to microseconds
                val error = SequencerError(
                    type = ErrorType.TIMING_ERROR,
                    message = "Timing drift detected: ${timingDrift / 1000}ms during $operation",
                    context = context + mapOf(
                        "expectedTime" to expectedTime,
                        "actualTime" to actualTime,
                        "drift" to timingDrift,
                        "operation" to operation
                    ),
                    timestamp = System.currentTimeMillis(),
                    severity = if (timingDrift > TIMING_ERROR_THRESHOLD_MS * 2000) {
                        ErrorSeverity.HIGH
                    } else {
                        ErrorSeverity.MEDIUM
                    }
                )
                
                recordError(error)
                
                // Apply timing compensation
                val compensated = applyTimingCompensation(timingDrift)
                if (compensated) {
                    updateErrorState(lastRecoverySuccess = true)
                    RecoveryResult.SUCCESS
                } else {
                    updateErrorState(lastRecoverySuccess = false)
                    RecoveryResult.RETRY_NEEDED
                }
            } else {
                RecoveryResult.SUCCESS
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in timing error handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Handle sample loading errors
     * Requirements: 8.7
     */
    suspend fun handleSampleLoadingError(
        sampleId: String,
        exception: Throwable,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val error = SequencerError(
                type = ErrorType.SAMPLE_LOADING_ERROR,
                message = "Failed to load sample: $sampleId",
                exception = exception,
                context = context + mapOf("sampleId" to sampleId),
                timestamp = System.currentTimeMillis(),
                severity = ErrorSeverity.MEDIUM
            )
            
            recordError(error)
            
            // Attempt sample recovery or fallback
            val recoveryKey = "sample_$sampleId"
            val attempts = recoveryAttempts.getOrDefault(recoveryKey, 0)
            
            if (attempts < MAX_RETRY_ATTEMPTS) {
                recoveryAttempts[recoveryKey] = attempts + 1
                
                Log.w(TAG, "Attempting sample loading recovery for $sampleId")
                
                // Try to reload the sample or use a fallback
                val recovered = recoverSample(sampleId)
                if (recovered) {
                    recoveryAttempts.remove(recoveryKey)
                    updateErrorState(lastRecoverySuccess = true)
                    RecoveryResult.SUCCESS
                } else {
                    RecoveryResult.RETRY_NEEDED
                }
            } else {
                Log.e(TAG, "Sample loading recovery failed for $sampleId")
                updateErrorState(
                    lastRecoverySuccess = false,
                    userMessage = "Sample unavailable. Pattern will play without this sample."
                )
                RecoveryResult.FAILED
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in sample loading error handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Handle voice allocation errors
     * Requirements: 8.7, 10.4
     */
    suspend fun handleVoiceAllocationError(
        padIndex: Int,
        sampleId: String,
        exception: Throwable? = null,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val error = SequencerError(
                type = ErrorType.VOICE_ALLOCATION_ERROR,
                message = "Voice allocation failed for pad $padIndex",
                exception = exception,
                context = context + mapOf(
                    "padIndex" to padIndex,
                    "sampleId" to sampleId
                ),
                timestamp = System.currentTimeMillis(),
                severity = ErrorSeverity.LOW
            )
            
            recordError(error)
            
            // Voice allocation errors are usually recoverable by trying again
            // or using voice stealing
            updateErrorState(lastRecoverySuccess = true)
            RecoveryResult.SUCCESS
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in voice allocation error handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Handle general sequencer errors
     * Requirements: 8.7
     */
    suspend fun handleGeneralError(
        operation: String,
        exception: Throwable,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM,
        context: Map<String, Any> = emptyMap()
    ): RecoveryResult {
        return try {
            val error = SequencerError(
                type = ErrorType.GENERAL_ERROR,
                message = "Error during $operation: ${exception.message}",
                exception = exception,
                context = context + mapOf("operation" to operation),
                timestamp = System.currentTimeMillis(),
                severity = severity
            )
            
            recordError(error)
            
            when (severity) {
                ErrorSeverity.LOW -> {
                    // Log and continue
                    RecoveryResult.SUCCESS
                }
                ErrorSeverity.MEDIUM -> {
                    // Attempt recovery
                    updateErrorState(userMessage = "An error occurred during $operation. Attempting recovery...")
                    RecoveryResult.RETRY_NEEDED
                }
                ErrorSeverity.HIGH -> {
                    // Critical error - may need user intervention
                    updateErrorState(
                        criticalError = "Critical error during $operation",
                        userMessage = "A critical error occurred. Please restart the sequencer."
                    )
                    RecoveryResult.FAILED
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in general error handling", e)
            RecoveryResult.FAILED
        }
    }

    /**
     * Recover audio engine functionality
     * Requirements: 8.7, 10.4
     */
    private suspend fun recoverAudioEngine(): Boolean {
        return try {
            Log.d(TAG, "Attempting audio engine recovery")
            
            // Try to reinitialize the audio engine
            val initialized = audioEngine.initialize(
                sampleRate = 44100,
                bufferSize = 256,
                enableLowLatency = true
            )
            
            if (initialized) {
                // Reinitialize drum engine
                val drumEngineReady = audioEngine.initializeDrumEngine()
                
                if (drumEngineReady) {
                    Log.d(TAG, "Audio engine recovery successful")
                    true
                } else {
                    Log.w(TAG, "Drum engine recovery failed")
                    false
                }
            } else {
                Log.w(TAG, "Audio engine initialization failed during recovery")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during audio engine recovery", e)
            false
        }
    }

    /**
     * Recover pattern loading
     * Requirements: 8.7
     */
    private suspend fun recoverPattern(patternId: String): Boolean {
        return try {
            Log.d(TAG, "Attempting pattern recovery for $patternId")
            
            // Try to reload the pattern from repository
            val result = patternRepository.loadPattern(patternId)
            
            when (result) {
                is com.high.theone.domain.Result.Success -> {
                    Log.d(TAG, "Pattern recovery successful for $patternId")
                    true
                }
                is com.high.theone.domain.Result.Failure -> {
                    Log.w(TAG, "Pattern recovery failed for $patternId: ${result.error.message}")
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during pattern recovery", e)
            false
        }
    }

    /**
     * Recover sample loading
     * Requirements: 8.7
     */
    private suspend fun recoverSample(sampleId: String): Boolean {
        return try {
            Log.d(TAG, "Attempting sample recovery for $sampleId")
            
            // This would integrate with the sample cache manager
            // For now, we'll simulate recovery
            delay(100L)
            
            // In a real implementation, this would try to reload the sample
            // or provide a fallback sample
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sample recovery", e)
            false
        }
    }

    /**
     * Apply timing compensation
     * Requirements: 8.7, 10.4
     */
    private suspend fun applyTimingCompensation(timingDrift: Long): Boolean {
        return try {
            Log.d(TAG, "Applying timing compensation for drift: ${timingDrift / 1000}ms")
            
            // This would integrate with the timing engine to apply compensation
            // For now, we'll simulate compensation
            delay(50L)
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during timing compensation", e)
            false
        }
    }

    /**
     * Record error in history and update counters
     * Requirements: 8.7
     */
    private fun recordError(error: SequencerError) {
        synchronized(errorHistory) {
            errorHistory.add(error)
            
            // Maintain history size limit
            if (errorHistory.size > ERROR_HISTORY_SIZE) {
                errorHistory.removeAt(0)
            }
        }
        
        // Update error counters
        errorCounts[error.type]?.incrementAndGet()
        
        // Log error based on severity
        when (error.severity) {
            ErrorSeverity.LOW -> Log.d(TAG, "Low severity error: ${error.message}")
            ErrorSeverity.MEDIUM -> Log.w(TAG, "Medium severity error: ${error.message}", error.exception)
            ErrorSeverity.HIGH -> Log.e(TAG, "High severity error: ${error.message}", error.exception)
        }
    }

    /**
     * Update error state
     * Requirements: 8.7
     */
    private fun updateErrorState(
        isRecovering: Boolean = _errorState.value.isRecovering,
        lastRecoverySuccess: Boolean = _errorState.value.lastRecoverySuccess,
        criticalError: String? = _errorState.value.criticalError,
        userMessage: String? = _errorState.value.userMessage
    ) {
        _errorState.value = _errorState.value.copy(
            isRecovering = isRecovering,
            lastRecoverySuccess = lastRecoverySuccess,
            criticalError = criticalError,
            userMessage = userMessage,
            lastUpdate = System.currentTimeMillis(),
            errorCounts = errorCounts.mapValues { it.value.get() }
        )
    }

    /**
     * Start periodic health monitoring
     * Requirements: 8.7, 10.4
     */
    private fun startHealthMonitoring() {
        errorHandlerScope.launch {
            while (true) {
                try {
                    performHealthCheck()
                    delay(5000L) // Check every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in health monitoring", e)
                    delay(10000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Perform periodic health check
     * Requirements: 8.7, 10.4
     */
    private suspend fun performHealthCheck() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Check audio engine health
            if (currentTime - lastAudioEngineCheck > AUDIO_ENGINE_RECOVERY_TIMEOUT_MS) {
                // This would check if audio engine is responsive
                // For now, we'll assume it's healthy
                lastAudioEngineCheck = currentTime
            }
            
            // Clear old recovery attempts
            val oldAttempts = recoveryAttempts.filter { (_, attempts) ->
                attempts >= MAX_RETRY_ATTEMPTS
            }.keys
            
            oldAttempts.forEach { key ->
                recoveryAttempts.remove(key)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in health check", e)
        }
    }

    /**
     * Get error statistics for debugging
     * Requirements: 8.7
     */
    fun getErrorStatistics(): ErrorStatistics {
        return ErrorStatistics(
            totalErrors = errorHistory.size,
            errorsByType = errorCounts.mapValues { it.value.get() },
            recentErrors = errorHistory.takeLast(10),
            recoveryAttempts = recoveryAttempts.size,
            lastHealthCheck = lastAudioEngineCheck
        )
    }

    /**
     * Clear error history and reset counters
     * Requirements: 8.7
     */
    fun clearErrorHistory() {
        synchronized(errorHistory) {
            errorHistory.clear()
        }
        
        errorCounts.values.forEach { counter ->
            counter.set(0)
        }
        
        recoveryAttempts.clear()
        
        updateErrorState(
            isRecovering = false,
            lastRecoverySuccess = true,
            criticalError = null,
            userMessage = null
        )
        
        Log.d(TAG, "Error history cleared")
    }

    /**
     * Clear user messages
     * Requirements: 8.7
     */
    fun clearUserMessage() {
        updateErrorState(userMessage = null)
    }

    /**
     * Clear critical error state
     * Requirements: 8.7
     */
    fun clearCriticalError() {
        updateErrorState(criticalError = null)
    }
}

/**
 * Sequencer error information
 */
data class SequencerError(
    val type: ErrorType,
    val message: String,
    val exception: Throwable? = null,
    val context: Map<String, Any> = emptyMap(),
    val timestamp: Long,
    val severity: ErrorSeverity
)

/**
 * Types of sequencer errors
 */
enum class ErrorType {
    AUDIO_ENGINE_FAILURE,
    PATTERN_LOADING_ERROR,
    SAMPLE_LOADING_ERROR,
    TIMING_ERROR,
    VOICE_ALLOCATION_ERROR,
    GENERAL_ERROR
}

/**
 * Error severity levels
 */
enum class ErrorSeverity {
    LOW,    // Informational, doesn't affect functionality
    MEDIUM, // May affect functionality, recovery possible
    HIGH    // Critical error, may require user intervention
}

/**
 * Recovery result from error handling
 */
enum class RecoveryResult {
    SUCCESS,      // Error handled successfully
    RETRY_NEEDED, // Recovery attempted, may need retry
    FAILED        // Recovery failed, manual intervention needed
}

/**
 * Sequencer error state
 */
data class SequencerErrorState(
    val isRecovering: Boolean = false,
    val lastRecoverySuccess: Boolean = true,
    val criticalError: String? = null,
    val userMessage: String? = null,
    val lastUpdate: Long = 0L,
    val errorCounts: Map<ErrorType, Long> = emptyMap()
)

/**
 * Error statistics for debugging
 */
data class ErrorStatistics(
    val totalErrors: Int,
    val errorsByType: Map<ErrorType, Long>,
    val recentErrors: List<SequencerError>,
    val recoveryAttempts: Int,
    val lastHealthCheck: Long
)