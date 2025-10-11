package com.high.theone.features.compactui.error

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.high.theone.model.RecordingError
import com.high.theone.model.RecordingErrorType
import com.high.theone.model.RecordingRecoveryAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive error handling system for recording and audio operations
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5 (comprehensive error handling)
 */
@Singleton
class ErrorHandlingSystem @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val MIN_STORAGE_SPACE_MB = 100L
        private const val AUDIO_ENGINE_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    private val _currentError = MutableStateFlow<RecordingError?>(null)
    val currentError: StateFlow<RecordingError?> = _currentError.asStateFlow()
    
    private val _isRecovering = MutableStateFlow(false)
    val isRecovering: StateFlow<Boolean> = _isRecovering.asStateFlow()
    
    private val _retryAttempts = MutableStateFlow(0)
    val retryAttempts: StateFlow<Int> = _retryAttempts.asStateFlow()
    
    /**
     * Handle recording errors with appropriate recovery actions
     */
    fun handleRecordingError(throwable: Throwable): RecordingError {
        val error = when {
            throwable.message?.contains("permission", ignoreCase = true) == true -> {
                createPermissionError()
            }
            throwable.message?.contains("audio", ignoreCase = true) == true -> {
                createAudioEngineError()
            }
            throwable.message?.contains("storage", ignoreCase = true) == true -> {
                createStorageError()
            }
            throwable.message?.contains("microphone", ignoreCase = true) == true -> {
                createMicrophoneError()
            }
            else -> {
                createSystemOverloadError()
            }
        }
        
        _currentError.value = error
        return error
    }
    
    /**
     * Handle specific error types with contextual information
     */
    fun handleSpecificError(errorType: RecordingErrorType, message: String? = null): RecordingError {
        val error = when (errorType) {
            RecordingErrorType.PERMISSION_DENIED -> createPermissionError(message)
            RecordingErrorType.AUDIO_ENGINE_FAILURE -> createAudioEngineError(message)
            RecordingErrorType.STORAGE_FAILURE -> createStorageError(message)
            RecordingErrorType.MICROPHONE_UNAVAILABLE -> createMicrophoneError(message)
            RecordingErrorType.SYSTEM_OVERLOAD -> createSystemOverloadError(message)
        }
        
        _currentError.value = error
        return error
    }
    
    /**
     * Check microphone permission status
     */
    fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check available storage space
     */
    fun checkStorageSpace(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            val availableBytes = stat.availableBytes
            availableBytes / (1024 * 1024) // Convert to MB
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Check if storage space is sufficient for recording
     */
    fun hasEnoughStorageSpace(): Boolean {
        return checkStorageSpace() >= MIN_STORAGE_SPACE_MB
    }
    
    /**
     * Clear current error state
     */
    fun clearError() {
        _currentError.value = null
        _retryAttempts.value = 0
    }
    
    /**
     * Set recovery state
     */
    fun setRecovering(isRecovering: Boolean) {
        _isRecovering.value = isRecovering
    }
    
    /**
     * Increment retry attempts
     */
    fun incrementRetryAttempts(): Boolean {
        val current = _retryAttempts.value
        if (current < MAX_RETRY_ATTEMPTS) {
            _retryAttempts.value = current + 1
            return true
        }
        return false
    }
    
    /**
     * Reset retry attempts
     */
    fun resetRetryAttempts() {
        _retryAttempts.value = 0
    }
    
    /**
     * Check if more retries are available
     */
    fun canRetry(): Boolean {
        return _retryAttempts.value < MAX_RETRY_ATTEMPTS
    }
    
    private fun createPermissionError(customMessage: String? = null): RecordingError {
        return RecordingError(
            type = RecordingErrorType.PERMISSION_DENIED,
            message = customMessage ?: "Microphone permission is required to record audio. Please grant permission in app settings.",
            isRecoverable = true,
            recoveryAction = RecordingRecoveryAction.REQUEST_PERMISSION
        )
    }
    
    private fun createAudioEngineError(customMessage: String? = null): RecordingError {
        return RecordingError(
            type = RecordingErrorType.AUDIO_ENGINE_FAILURE,
            message = customMessage ?: "Audio engine failed to initialize. This may be due to another app using the microphone or audio system issues.",
            isRecoverable = true,
            recoveryAction = RecordingRecoveryAction.RESTART_AUDIO_ENGINE
        )
    }
    
    private fun createStorageError(customMessage: String? = null): RecordingError {
        val availableSpace = checkStorageSpace()
        return RecordingError(
            type = RecordingErrorType.STORAGE_FAILURE,
            message = customMessage ?: "Insufficient storage space for recording. Available: ${availableSpace}MB, Required: ${MIN_STORAGE_SPACE_MB}MB",
            isRecoverable = availableSpace > 0,
            recoveryAction = if (availableSpace > 0) RecordingRecoveryAction.FREE_STORAGE_SPACE else null
        )
    }
    
    private fun createMicrophoneError(customMessage: String? = null): RecordingError {
        return RecordingError(
            type = RecordingErrorType.MICROPHONE_UNAVAILABLE,
            message = customMessage ?: "Microphone is not available. It may be in use by another app or disconnected.",
            isRecoverable = true,
            recoveryAction = RecordingRecoveryAction.RETRY_RECORDING
        )
    }
    
    private fun createSystemOverloadError(customMessage: String? = null): RecordingError {
        return RecordingError(
            type = RecordingErrorType.SYSTEM_OVERLOAD,
            message = customMessage ?: "System is overloaded. Try closing other apps or reducing recording quality.",
            isRecoverable = true,
            recoveryAction = RecordingRecoveryAction.REDUCE_QUALITY
        )
    }
}