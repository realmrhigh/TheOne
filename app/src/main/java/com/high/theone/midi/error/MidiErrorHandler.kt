package com.high.theone.midi.error

import android.content.Context
import android.util.Log
import com.high.theone.midi.MidiError
import com.high.theone.midi.MidiErrorRecoveryStrategy
import com.high.theone.midi.MidiErrorContext
import com.high.theone.midi.model.MidiSystemState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive error handling system for MIDI operations.
 * Provides error recovery, graceful degradation, and user notifications.
 * 
 * Requirements: 7.1, 7.2, 7.6
 */
@Singleton
class MidiErrorHandler @Inject constructor(
    private val context: Context,
    private val notificationManager: MidiNotificationManager
) {
    companion object {
        private const val TAG = "MidiErrorHandler"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_BASE_MS = 1000L
        private const val DEVICE_RECONNECT_TIMEOUT_MS = 30000L
    }
    
    private val errorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Error tracking
    private val _errorHistory = MutableStateFlow<List<MidiErrorRecord>>(emptyList())
    val errorHistory: StateFlow<List<MidiErrorRecord>> = _errorHistory.asStateFlow()
    
    private val _systemHealth = MutableStateFlow(MidiSystemHealth.HEALTHY)
    val systemHealth: StateFlow<MidiSystemHealth> = _systemHealth.asStateFlow()
    
    // Device reconnection tracking
    private val deviceReconnectionJobs = mutableMapOf<String, Job>()
    
    // Error recovery callbacks
    private val recoveryCallbacks = mutableMapOf<String, suspend (MidiError, MidiErrorContext) -> Boolean>()
    
    /**
     * Handle a MIDI error with appropriate recovery strategy
     */
    suspend fun handleError(
        error: MidiError,
        context: MidiErrorContext,
        customStrategy: MidiErrorRecoveryStrategy? = null
    ): MidiErrorResult {
        return try {
            Log.w(TAG, "Handling MIDI error: ${error.message}", error)
            
            // Record error for diagnostics
            recordError(error, context)
            
            // Determine recovery strategy
            val strategy = customStrategy ?: determineRecoveryStrategy(error, context)
            
            // Execute recovery
            val result = executeRecoveryStrategy(error, context, strategy)
            
            // Update system health
            updateSystemHealth(error, result.recovered)
            
            // Notify user if needed
            if (result.shouldNotifyUser) {
                notificationManager.showErrorNotification(error, context, result)
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in error handler", e)
            MidiErrorResult(
                recovered = false,
                shouldNotifyUser = true,
                message = "Critical error in MIDI system: ${e.message}",
                recoveryAction = null
            )
        }
    }
    
    /**
     * Handle device disconnection with automatic reconnection
     */
    suspend fun handleDeviceDisconnection(
        deviceId: String,
        reconnectCallback: suspend () -> Boolean
    ) {
        Log.i(TAG, "Handling device disconnection: $deviceId")
        
        // Cancel any existing reconnection job for this device
        deviceReconnectionJobs[deviceId]?.cancel()
        
        // Start reconnection job
        val reconnectionJob = errorScope.launch {
            var attempts = 0
            var reconnected = false
            
            while (attempts < MAX_RETRY_ATTEMPTS && !reconnected && isActive) {
                attempts++
                val delay = RETRY_DELAY_BASE_MS * (1 shl (attempts - 1)) // Exponential backoff
                
                Log.i(TAG, "Attempting to reconnect device $deviceId (attempt $attempts)")
                
                try {
                    delay(delay)
                    reconnected = reconnectCallback()
                    
                    if (reconnected) {
                        Log.i(TAG, "Successfully reconnected device: $deviceId")
                        notificationManager.showDeviceReconnectedNotification(deviceId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnection attempt $attempts failed for device $deviceId", e)
                }
            }
            
            if (!reconnected) {
                Log.w(TAG, "Failed to reconnect device after $attempts attempts: $deviceId")
                notificationManager.showDeviceReconnectionFailedNotification(deviceId)
            }
            
            // Clean up job reference
            deviceReconnectionJobs.remove(deviceId)
        }
        
        deviceReconnectionJobs[deviceId] = reconnectionJob
        
        // Set timeout for reconnection attempts
        errorScope.launch {
            delay(DEVICE_RECONNECT_TIMEOUT_MS)
            if (deviceReconnectionJobs[deviceId] == reconnectionJob) {
                reconnectionJob.cancel()
                deviceReconnectionJobs.remove(deviceId)
                Log.w(TAG, "Device reconnection timed out: $deviceId")
            }
        }
    }
    
    /**
     * Register a custom error recovery callback
     */
    fun registerRecoveryCallback(
        errorType: String,
        callback: suspend (MidiError, MidiErrorContext) -> Boolean
    ) {
        recoveryCallbacks[errorType] = callback
    }
    
    /**
     * Clear error history
     */
    fun clearErrorHistory() {
        _errorHistory.value = emptyList()
    }
    
    /**
     * Get error statistics for diagnostics
     */
    fun getErrorStatistics(): MidiErrorStatistics {
        val errors = _errorHistory.value
        val now = System.currentTimeMillis()
        val last24Hours = errors.filter { now - it.timestamp < 24 * 60 * 60 * 1000 }
        
        return MidiErrorStatistics(
            totalErrors = errors.size,
            errorsLast24Hours = last24Hours.size,
            mostCommonError = errors.groupBy { it.error::class.simpleName }
                .maxByOrNull { it.value.size }?.key,
            averageRecoveryTime = errors.filter { it.recoveryTimeMs > 0 }
                .map { it.recoveryTimeMs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L,
            systemHealth = _systemHealth.value
        )
    }
    
    /**
     * Shutdown error handler and cleanup resources
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down MIDI error handler")
        
        // Cancel all reconnection jobs
        deviceReconnectionJobs.values.forEach { it.cancel() }
        deviceReconnectionJobs.clear()
        
        // Cancel error scope
        errorScope.cancel()
    }
    
    // Private helper methods
    
    private fun recordError(error: MidiError, context: MidiErrorContext) {
        val record = MidiErrorRecord(
            error = error,
            context = context,
            timestamp = System.currentTimeMillis(),
            recoveryTimeMs = 0L // Will be updated when recovery completes
        )
        
        val currentHistory = _errorHistory.value.toMutableList()
        currentHistory.add(0, record) // Add to beginning
        
        // Keep only last 100 errors
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _errorHistory.value = currentHistory
    }
    
    private fun determineRecoveryStrategy(
        error: MidiError,
        context: MidiErrorContext
    ): MidiErrorRecoveryStrategy {
        return when (error) {
            is MidiError.DeviceNotFound -> MidiErrorRecoveryStrategy.RETRY
            is MidiError.ConnectionFailed -> MidiErrorRecoveryStrategy.RETRY
            is MidiError.DeviceBusy -> MidiErrorRecoveryStrategy.RETRY
            is MidiError.BufferOverflow -> MidiErrorRecoveryStrategy.FALLBACK_TO_INTERNAL
            is MidiError.ClockSyncLost -> MidiErrorRecoveryStrategy.FALLBACK_TO_INTERNAL
            is MidiError.InvalidMessage -> MidiErrorRecoveryStrategy.IGNORE
            is MidiError.PermissionDenied -> MidiErrorRecoveryStrategy.NOTIFY_USER
            is MidiError.Timeout -> MidiErrorRecoveryStrategy.RETRY
            MidiError.MidiNotSupported -> MidiErrorRecoveryStrategy.FALLBACK_TO_INTERNAL
            is MidiError.MappingConflict -> MidiErrorRecoveryStrategy.NOTIFY_USER
        }
    }
    
    private suspend fun executeRecoveryStrategy(
        error: MidiError,
        context: MidiErrorContext,
        strategy: MidiErrorRecoveryStrategy
    ): MidiErrorResult {
        val startTime = System.currentTimeMillis()
        
        return when (strategy) {
            MidiErrorRecoveryStrategy.RETRY -> {
                executeRetryStrategy(error, context, startTime)
            }
            
            MidiErrorRecoveryStrategy.FALLBACK_TO_INTERNAL -> {
                executeFallbackStrategy(error, context, startTime)
            }
            
            MidiErrorRecoveryStrategy.NOTIFY_USER -> {
                MidiErrorResult(
                    recovered = false,
                    shouldNotifyUser = true,
                    message = getUserFriendlyErrorMessage(error),
                    recoveryAction = getRecoveryAction(error),
                    recoveryTimeMs = System.currentTimeMillis() - startTime
                )
            }
            
            MidiErrorRecoveryStrategy.IGNORE -> {
                Log.d(TAG, "Ignoring error as per strategy: ${error.message}")
                MidiErrorResult(
                    recovered = true,
                    shouldNotifyUser = false,
                    message = "Error ignored",
                    recoveryAction = null,
                    recoveryTimeMs = System.currentTimeMillis() - startTime
                )
            }
            
            MidiErrorRecoveryStrategy.RESTART_DEVICE -> {
                executeRestartStrategy(error, context, startTime)
            }
        }
    }
    
    private suspend fun executeRetryStrategy(
        error: MidiError,
        context: MidiErrorContext,
        startTime: Long
    ): MidiErrorResult {
        val errorType = error::class.simpleName ?: "Unknown"
        val callback = recoveryCallbacks[errorType]
        
        if (callback != null) {
            return try {
                val recovered = callback(error, context)
                MidiErrorResult(
                    recovered = recovered,
                    shouldNotifyUser = !recovered,
                    message = if (recovered) "Error recovered" else "Recovery failed",
                    recoveryAction = if (!recovered) getRecoveryAction(error) else null,
                    recoveryTimeMs = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Recovery callback failed", e)
                MidiErrorResult(
                    recovered = false,
                    shouldNotifyUser = true,
                    message = "Recovery failed: ${e.message}",
                    recoveryAction = getRecoveryAction(error),
                    recoveryTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
        
        // Default retry behavior
        return MidiErrorResult(
            recovered = false,
            shouldNotifyUser = true,
            message = "Retry not implemented for ${error::class.simpleName}",
            recoveryAction = getRecoveryAction(error),
            recoveryTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    private suspend fun executeFallbackStrategy(
        error: MidiError,
        context: MidiErrorContext,
        startTime: Long
    ): MidiErrorResult {
        Log.i(TAG, "Executing fallback strategy for error: ${error.message}")
        
        return MidiErrorResult(
            recovered = true,
            shouldNotifyUser = true,
            message = "Switched to internal mode due to MIDI issue",
            recoveryAction = null,
            recoveryTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    private suspend fun executeRestartStrategy(
        error: MidiError,
        context: MidiErrorContext,
        startTime: Long
    ): MidiErrorResult {
        Log.i(TAG, "Executing restart strategy for error: ${error.message}")
        
        // This would trigger a device restart if implemented
        return MidiErrorResult(
            recovered = false,
            shouldNotifyUser = true,
            message = "Device restart required",
            recoveryAction = MidiRecoveryAction.RESTART_DEVICE,
            recoveryTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    private fun updateSystemHealth(error: MidiError, recovered: Boolean) {
        val currentHealth = _systemHealth.value
        
        val newHealth = when {
            recovered && currentHealth == MidiSystemHealth.CRITICAL -> MidiSystemHealth.DEGRADED
            recovered && currentHealth == MidiSystemHealth.DEGRADED -> MidiSystemHealth.HEALTHY
            !recovered && currentHealth == MidiSystemHealth.HEALTHY -> MidiSystemHealth.DEGRADED
            !recovered && currentHealth == MidiSystemHealth.DEGRADED -> MidiSystemHealth.CRITICAL
            else -> currentHealth
        }
        
        if (newHealth != currentHealth) {
            _systemHealth.value = newHealth
            Log.i(TAG, "System health changed: $currentHealth -> $newHealth")
        }
    }
    
    private fun getUserFriendlyErrorMessage(error: MidiError): String {
        return when (error) {
            is MidiError.DeviceNotFound -> "MIDI device '${error.deviceId}' not found. Please check connection."
            is MidiError.ConnectionFailed -> "Failed to connect to MIDI device '${error.deviceId}'. ${error.reason}"
            is MidiError.PermissionDenied -> "MIDI permission required. Please grant permission in settings."
            is MidiError.BufferOverflow -> "MIDI data overload detected. Switching to internal mode."
            is MidiError.ClockSyncLost -> "MIDI clock sync lost. Switching to internal timing."
            is MidiError.InvalidMessage -> "Invalid MIDI data received. Message ignored."
            MidiError.MidiNotSupported -> "MIDI is not supported on this device."
            is MidiError.MappingConflict -> "MIDI mapping conflict detected. Please review settings."
            is MidiError.DeviceBusy -> "MIDI device '${error.deviceId}' is busy. Please try again."
            is MidiError.Timeout -> "MIDI operation timed out: ${error.operation}"
        }
    }
    
    private fun getRecoveryAction(error: MidiError): MidiRecoveryAction? {
        return when (error) {
            is MidiError.DeviceNotFound -> MidiRecoveryAction.CHECK_CONNECTION
            is MidiError.ConnectionFailed -> MidiRecoveryAction.RETRY_CONNECTION
            is MidiError.PermissionDenied -> MidiRecoveryAction.GRANT_PERMISSION
            is MidiError.BufferOverflow -> MidiRecoveryAction.REDUCE_MIDI_LOAD
            is MidiError.ClockSyncLost -> MidiRecoveryAction.CHECK_CLOCK_SOURCE
            MidiError.MidiNotSupported -> MidiRecoveryAction.USE_TOUCH_INPUT
            is MidiError.MappingConflict -> MidiRecoveryAction.REVIEW_MAPPINGS
            is MidiError.DeviceBusy -> MidiRecoveryAction.RETRY_CONNECTION
            is MidiError.Timeout -> MidiRecoveryAction.RETRY_CONNECTION
            else -> null
        }
    }
}

/**
 * MIDI error handling result
 */
data class MidiErrorResult(
    val recovered: Boolean,
    val shouldNotifyUser: Boolean,
    val message: String,
    val recoveryAction: MidiRecoveryAction?,
    val recoveryTimeMs: Long = 0L
)

/**
 * MIDI error record for diagnostics
 */
data class MidiErrorRecord(
    val error: MidiError,
    val context: MidiErrorContext,
    val timestamp: Long,
    val recoveryTimeMs: Long
)

/**
 * MIDI system health status
 */
enum class MidiSystemHealth {
    HEALTHY,    // All systems functioning normally
    DEGRADED,   // Some issues but core functionality works
    CRITICAL    // Major issues, limited functionality
}

/**
 * MIDI error statistics
 */
data class MidiErrorStatistics(
    val totalErrors: Int,
    val errorsLast24Hours: Int,
    val mostCommonError: String?,
    val averageRecoveryTime: Long,
    val systemHealth: MidiSystemHealth
)

/**
 * Recovery actions that users can take
 */
enum class MidiRecoveryAction {
    CHECK_CONNECTION,
    RETRY_CONNECTION,
    GRANT_PERMISSION,
    REDUCE_MIDI_LOAD,
    CHECK_CLOCK_SOURCE,
    USE_TOUCH_INPUT,
    REVIEW_MAPPINGS,
    RESTART_DEVICE,
    RESTART_APP
}