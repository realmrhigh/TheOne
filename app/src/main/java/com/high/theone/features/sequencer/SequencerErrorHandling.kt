package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Comprehensive error handling system for sequencer operations
 */
class SequencerErrorHandler {
    
    private val _errors = MutableStateFlow<List<SequencerError>>(emptyList())
    val errors: StateFlow<List<SequencerError>> = _errors.asStateFlow()
    
    private val _warnings = MutableStateFlow<List<SequencerWarning>>(emptyList())
    val warnings: StateFlow<List<SequencerWarning>> = _warnings.asStateFlow()
    
    /**
     * Reports an error to the error handler
     */
    fun reportError(error: SequencerError) {
        val currentErrors = _errors.value.toMutableList()
        currentErrors.add(error)
        
        // Keep only the last 50 errors to prevent memory issues
        if (currentErrors.size > 50) {
            currentErrors.removeAt(0)
        }
        
        _errors.value = currentErrors
    }
    
    /**
     * Reports a warning to the error handler
     */
    fun reportWarning(warning: SequencerWarning) {
        val currentWarnings = _warnings.value.toMutableList()
        currentWarnings.add(warning)
        
        // Keep only the last 50 warnings
        if (currentWarnings.size > 50) {
            currentWarnings.removeAt(0)
        }
        
        _warnings.value = currentWarnings
    }
    
    /**
     * Clears all errors
     */
    fun clearErrors() {
        _errors.value = emptyList()
    }
    
    /**
     * Clears all warnings
     */
    fun clearWarnings() {
        _warnings.value = emptyList()
    }
    
    /**
     * Clears a specific error by ID
     */
    fun clearError(errorId: String) {
        _errors.value = _errors.value.filter { it.id != errorId }
    }
    
    /**
     * Clears a specific warning by ID
     */
    fun clearWarning(warningId: String) {
        _warnings.value = _warnings.value.filter { it.id != warningId }
    }
    
    /**
     * Gets the most recent error of a specific type
     */
    fun getLatestError(type: SequencerErrorType): SequencerError? {
        return _errors.value.filter { it.type == type }.maxByOrNull { it.timestamp }
    }
    
    /**
     * Gets all errors of a specific type
     */
    fun getErrorsOfType(type: SequencerErrorType): List<SequencerError> {
        return _errors.value.filter { it.type == type }
    }
    
    /**
     * Returns true if there are any critical errors
     */
    fun hasCriticalErrors(): Boolean {
        return _errors.value.any { it.severity == ErrorSeverity.CRITICAL }
    }
}

/**
 * Represents a sequencer error with context and recovery information
 */
data class SequencerError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SequencerErrorType,
    val severity: ErrorSeverity,
    val message: String,
    val details: String? = null,
    val context: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val recoveryActions: List<RecoveryAction> = emptyList(),
    val cause: Throwable? = null
)

/**
 * Represents a sequencer warning
 */
data class SequencerWarning(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SequencerWarningType,
    val message: String,
    val details: String? = null,
    val context: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val dismissible: Boolean = true
)

/**
 * Types of sequencer errors
 */
enum class SequencerErrorType(val displayName: String) {
    PATTERN_VALIDATION("Pattern Validation"),
    TIMING_ENGINE("Timing Engine"),
    AUDIO_ENGINE("Audio Engine"),
    PATTERN_LOADING("Pattern Loading"),
    PATTERN_SAVING("Pattern Saving"),
    PLAYBACK("Playback"),
    RECORDING("Recording"),
    SONG_MODE("Song Mode"),
    MEMORY("Memory"),
    PERFORMANCE("Performance"),
    CONFIGURATION("Configuration"),
    UNKNOWN("Unknown")
}

/**
 * Types of sequencer warnings
 */
enum class SequencerWarningType(val displayName: String) {
    PERFORMANCE("Performance"),
    PATTERN_COMPLEXITY("Pattern Complexity"),
    TIMING_ACCURACY("Timing Accuracy"),
    MEMORY_USAGE("Memory Usage"),
    CONFIGURATION("Configuration"),
    USER_ACTION("User Action")
}

/**
 * Error severity levels
 */
enum class ErrorSeverity(val displayName: String, val priority: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4)
}

/**
 * Recovery actions that can be taken for errors
 */
data class RecoveryAction(
    val id: String,
    val displayName: String,
    val description: String,
    val action: () -> Unit
)

/**
 * Utility functions for creating common sequencer errors
 */
object SequencerErrors {
    
    fun patternValidationError(
        message: String,
        patternId: String? = null,
        validationErrors: List<String> = emptyList()
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.PATTERN_VALIDATION,
            severity = ErrorSeverity.MEDIUM,
            message = message,
            details = if (validationErrors.isNotEmpty()) {
                "Validation errors: ${validationErrors.joinToString(", ")}"
            } else null,
            context = mapOf(
                "patternId" to (patternId ?: "unknown"),
                "validationErrors" to validationErrors
            )
        )
    }
    
    fun timingEngineError(
        message: String,
        cause: Throwable? = null,
        recoveryActions: List<RecoveryAction> = emptyList()
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.TIMING_ENGINE,
            severity = ErrorSeverity.HIGH,
            message = message,
            cause = cause,
            recoveryActions = recoveryActions
        )
    }
    
    fun audioEngineError(
        message: String,
        cause: Throwable? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.AUDIO_ENGINE,
            severity = ErrorSeverity.CRITICAL,
            message = message,
            cause = cause,
            recoveryActions = listOf(
                RecoveryAction(
                    id = "restart_audio",
                    displayName = "Restart Audio Engine",
                    description = "Attempt to restart the audio engine"
                ) { /* Implementation would restart audio engine */ }
            )
        )
    }
    
    fun patternLoadingError(
        message: String,
        patternId: String,
        cause: Throwable? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.PATTERN_LOADING,
            severity = ErrorSeverity.MEDIUM,
            message = message,
            context = mapOf("patternId" to patternId),
            cause = cause,
            recoveryActions = listOf(
                RecoveryAction(
                    id = "retry_load",
                    displayName = "Retry Loading",
                    description = "Attempt to load the pattern again"
                ) { /* Implementation would retry loading */ },
                RecoveryAction(
                    id = "create_empty",
                    displayName = "Create Empty Pattern",
                    description = "Create a new empty pattern instead"
                ) { /* Implementation would create empty pattern */ }
            )
        )
    }
    
    fun patternSavingError(
        message: String,
        patternId: String,
        cause: Throwable? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.PATTERN_SAVING,
            severity = ErrorSeverity.HIGH,
            message = message,
            context = mapOf("patternId" to patternId),
            cause = cause,
            recoveryActions = listOf(
                RecoveryAction(
                    id = "retry_save",
                    displayName = "Retry Saving",
                    description = "Attempt to save the pattern again"
                ) { /* Implementation would retry saving */ },
                RecoveryAction(
                    id = "save_as_new",
                    displayName = "Save As New Pattern",
                    description = "Save with a new name and ID"
                ) { /* Implementation would save as new */ }
            )
        )
    }
    
    fun playbackError(
        message: String,
        cause: Throwable? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.PLAYBACK,
            severity = ErrorSeverity.HIGH,
            message = message,
            cause = cause,
            recoveryActions = listOf(
                RecoveryAction(
                    id = "stop_playback",
                    displayName = "Stop Playback",
                    description = "Stop current playback and reset"
                ) { /* Implementation would stop playback */ },
                RecoveryAction(
                    id = "restart_playback",
                    displayName = "Restart Playback",
                    description = "Stop and restart playback from beginning"
                ) { /* Implementation would restart playback */ }
            )
        )
    }
    
    fun recordingError(
        message: String,
        cause: Throwable? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.RECORDING,
            severity = ErrorSeverity.MEDIUM,
            message = message,
            cause = cause,
            recoveryActions = listOf(
                RecoveryAction(
                    id = "stop_recording",
                    displayName = "Stop Recording",
                    description = "Stop current recording session"
                ) { /* Implementation would stop recording */ }
            )
        )
    }
    
    fun memoryError(
        message: String,
        memoryUsage: Long? = null
    ): SequencerError {
        return SequencerError(
            type = SequencerErrorType.MEMORY,
            severity = ErrorSeverity.HIGH,
            message = message,
            context = memoryUsage?.let { mapOf("memoryUsage" to it) } ?: emptyMap(),
            recoveryActions = listOf(
                RecoveryAction(
                    id = "clear_cache",
                    displayName = "Clear Cache",
                    description = "Clear pattern cache to free memory"
                ) { /* Implementation would clear cache */ },
                RecoveryAction(
                    id = "reduce_complexity",
                    displayName = "Reduce Complexity",
                    description = "Simplify current patterns to reduce memory usage"
                ) { /* Implementation would reduce complexity */ }
            )
        )
    }
}

/**
 * Utility functions for creating common sequencer warnings
 */
object SequencerWarnings {
    
    fun performanceWarning(
        message: String,
        details: String? = null,
        context: Map<String, Any> = emptyMap()
    ): SequencerWarning {
        return SequencerWarning(
            type = SequencerWarningType.PERFORMANCE,
            message = message,
            details = details,
            context = context
        )
    }
    
    fun patternComplexityWarning(
        message: String,
        patternId: String,
        complexity: Float
    ): SequencerWarning {
        return SequencerWarning(
            type = SequencerWarningType.PATTERN_COMPLEXITY,
            message = message,
            context = mapOf(
                "patternId" to patternId,
                "complexity" to complexity
            )
        )
    }
    
    fun timingAccuracyWarning(
        message: String,
        jitter: Float? = null
    ): SequencerWarning {
        return SequencerWarning(
            type = SequencerWarningType.TIMING_ACCURACY,
            message = message,
            context = jitter?.let { mapOf("jitter" to it) } ?: emptyMap()
        )
    }
    
    fun memoryUsageWarning(
        message: String,
        memoryUsage: Long,
        threshold: Long
    ): SequencerWarning {
        return SequencerWarning(
            type = SequencerWarningType.MEMORY_USAGE,
            message = message,
            context = mapOf(
                "memoryUsage" to memoryUsage,
                "threshold" to threshold,
                "percentage" to (memoryUsage.toFloat() / threshold * 100).toInt()
            )
        )
    }
}

/**
 * Extension functions for error handling
 */

/**
 * Safely executes a sequencer operation with error handling
 */
inline fun <T> SequencerErrorHandler.safeExecute(
    operation: () -> T,
    onError: (SequencerError) -> T
): T {
    return try {
        operation()
    } catch (e: PatternValidationException) {
        val error = SequencerErrors.patternValidationError(
            message = e.message ?: "Pattern validation failed",
            validationErrors = e.validationResult.errors.map { it.message }
        )
        reportError(error)
        onError(error)
    } catch (e: Exception) {
        val error = SequencerError(
            type = SequencerErrorType.UNKNOWN,
            severity = ErrorSeverity.MEDIUM,
            message = e.message ?: "Unknown error occurred",
            cause = e
        )
        reportError(error)
        onError(error)
    }
}

/**
 * Safely executes a suspending sequencer operation with error handling
 */
suspend inline fun <T> SequencerErrorHandler.safeExecuteSuspend(
    crossinline operation: suspend () -> T,
    crossinline onError: suspend (SequencerError) -> T
): T {
    return try {
        operation()
    } catch (e: PatternValidationException) {
        val error = SequencerErrors.patternValidationError(
            message = e.message ?: "Pattern validation failed",
            validationErrors = e.validationResult.errors.map { it.message }
        )
        reportError(error)
        onError(error)
    } catch (e: Exception) {
        val error = SequencerError(
            type = SequencerErrorType.UNKNOWN,
            severity = ErrorSeverity.MEDIUM,
            message = e.message ?: "Unknown error occurred",
            cause = e
        )
        reportError(error)
        onError(error)
    }
}