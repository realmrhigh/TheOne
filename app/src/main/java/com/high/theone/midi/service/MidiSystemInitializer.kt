package com.high.theone.midi.service

import com.high.theone.midi.MidiManagerControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates MIDI system initialization with proper dependency ordering.
 * Ensures permissions are checked, lifecycle is managed, and system is ready.
 */
@Singleton
class MidiSystemInitializer @Inject constructor(
    private val midiManager: MidiManagerControl,
    private val lifecycleManager: MidiLifecycleManager,
    private val permissionManager: MidiPermissionManager
) {
    
    private val initializerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _initializationState = MutableStateFlow(MidiInitializationState.NOT_STARTED)
    val initializationState: StateFlow<MidiInitializationState> = _initializationState.asStateFlow()
    
    private val _initializationProgress = MutableStateFlow(MidiInitializationProgress())
    val initializationProgress: StateFlow<MidiInitializationProgress> = _initializationProgress.asStateFlow()
    
    /**
     * Initialize the complete MIDI system with proper coordination
     */
    suspend fun initializeSystem(): Result<Unit> {
        return try {
            _initializationState.value = MidiInitializationState.IN_PROGRESS
            updateProgress("Starting MIDI system initialization...", 0.1f)
            
            // Step 1: Check device support
            updateProgress("Checking MIDI device support...", 0.2f)
            if (!permissionManager.hasMidiSupport()) {
                _initializationState.value = MidiInitializationState.FAILED
                return Result.failure(Exception("MIDI not supported on this device"))
            }
            
            // Step 2: Check permissions
            updateProgress("Checking MIDI permissions...", 0.3f)
            permissionManager.updatePermissionState()
            
            if (!permissionManager.hasAllMidiPermissions()) {
                _initializationState.value = MidiInitializationState.WAITING_FOR_PERMISSIONS
                updateProgress("Waiting for MIDI permissions...", 0.4f)
                // Don't fail here - let the UI handle permission requests
                // Return success but system won't be fully ready until permissions granted
                return Result.success(Unit)
            }
            
            // Step 3: Initialize lifecycle manager
            updateProgress("Initializing MIDI lifecycle manager...", 0.5f)
            val lifecycleResult = lifecycleManager.initializeMidiSystem()
            if (lifecycleResult.isFailure) {
                _initializationState.value = MidiInitializationState.FAILED
                return lifecycleResult
            }
            
            // Step 4: Wait for MIDI manager to be ready
            updateProgress("Starting MIDI manager...", 0.7f)
            
            // Monitor MIDI manager state
            val midiManagerReady = withTimeoutOrNull(10000) { // 10 second timeout
                midiManager.isInitialized.first { it }
            }
            
            if (midiManagerReady != true) {
                _initializationState.value = MidiInitializationState.FAILED
                return Result.failure(Exception("MIDI manager initialization timeout"))
            }
            
            // Step 5: Final verification
            updateProgress("Verifying MIDI system readiness...", 0.9f)
            if (!lifecycleManager.isMidiSystemReady()) {
                _initializationState.value = MidiInitializationState.FAILED
                return Result.failure(Exception("MIDI system not ready after initialization"))
            }
            
            // Success!
            updateProgress("MIDI system ready", 1.0f)
            _initializationState.value = MidiInitializationState.COMPLETED
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            _initializationState.value = MidiInitializationState.FAILED
            updateProgress("MIDI initialization failed: ${e.message}", 0.0f)
            Result.failure(e)
        }
    }
    
    /**
     * Shutdown the MIDI system in proper order
     */
    suspend fun shutdownSystem(): Result<Unit> {
        return try {
            _initializationState.value = MidiInitializationState.SHUTTING_DOWN
            updateProgress("Shutting down MIDI system...", 0.5f)
            
            // Shutdown in reverse order
            val result = lifecycleManager.shutdownMidiSystem()
            
            if (result.isSuccess) {
                _initializationState.value = MidiInitializationState.SHUTDOWN
                updateProgress("MIDI system shutdown complete", 0.0f)
            } else {
                _initializationState.value = MidiInitializationState.FAILED
                updateProgress("MIDI shutdown failed", 0.0f)
            }
            
            result
        } catch (e: Exception) {
            _initializationState.value = MidiInitializationState.FAILED
            updateProgress("MIDI shutdown error: ${e.message}", 0.0f)
            Result.failure(e)
        }
    }
    
    /**
     * Restart the entire MIDI system
     */
    suspend fun restartSystem(): Result<Unit> {
        shutdownSystem()
        delay(1000) // Brief pause between shutdown and restart
        return initializeSystem()
    }
    
    /**
     * Check if MIDI system is fully ready for use
     */
    fun isSystemReady(): Boolean {
        return _initializationState.value == MidiInitializationState.COMPLETED &&
                permissionManager.hasAllMidiPermissions() &&
                lifecycleManager.isMidiSystemReady()
    }
    
    /**
     * Get current system status for UI display
     */
    fun getSystemStatus(): MidiSystemStatus {
        return MidiSystemStatus(
            initializationState = _initializationState.value,
            progress = _initializationProgress.value,
            permissionState = permissionManager.permissionState.value,
            lifecycleState = lifecycleManager.lifecycleState.value,
            isReady = isSystemReady()
        )
    }
    
    /**
     * Handle permission grant result
     */
    suspend fun onPermissionsGranted() {
        permissionManager.updatePermissionState()
        
        if (_initializationState.value == MidiInitializationState.WAITING_FOR_PERMISSIONS) {
            // Continue initialization now that permissions are granted
            initializeSystem()
        }
    }
    
    private fun updateProgress(message: String, progress: Float) {
        _initializationProgress.value = MidiInitializationProgress(
            message = message,
            progress = progress,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Represents the state of MIDI system initialization
 */
enum class MidiInitializationState {
    NOT_STARTED,
    IN_PROGRESS,
    WAITING_FOR_PERMISSIONS,
    COMPLETED,
    SHUTTING_DOWN,
    SHUTDOWN,
    FAILED
}

/**
 * Progress information for MIDI initialization
 */
data class MidiInitializationProgress(
    val message: String = "",
    val progress: Float = 0.0f,
    val timestamp: Long = 0L
)

/**
 * Complete system status for UI display
 */
data class MidiSystemStatus(
    val initializationState: MidiInitializationState,
    val progress: MidiInitializationProgress,
    val permissionState: MidiPermissionState,
    val lifecycleState: MidiLifecycleState,
    val isReady: Boolean
)