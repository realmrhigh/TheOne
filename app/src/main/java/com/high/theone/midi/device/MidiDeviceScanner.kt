package com.high.theone.midi.device

import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.high.theone.midi.model.MidiDeviceInfo as AppMidiDeviceInfo
import com.high.theone.midi.MidiError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time MIDI device scanner that handles device discovery callbacks,
 * automatic reconnection, and device capability validation.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class MidiDeviceScanner @Inject constructor(
    private val context: Context,
    private val midiDeviceManager: MidiDeviceManager
) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val scannerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Device discovery events
    private val _deviceEvents = MutableSharedFlow<DeviceEvent>()
    val deviceEvents: SharedFlow<DeviceEvent> = _deviceEvents.asSharedFlow()
    
    // Scanner state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Reconnection management
    private val reconnectionJobs = mutableMapOf<String, Job>()
    private val reconnectionAttempts = mutableMapOf<String, Int>()
    private val maxReconnectionAttempts = 5
    private val baseReconnectionDelay = 1000L // 1 second
    
    // Device validation cache
    private val deviceCapabilityCache = mutableMapOf<String, DeviceCapabilities>()
    
    // Device callback for real-time detection
    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            scannerScope.launch {
                handleDeviceAdded(device)
            }
        }
        
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            scannerScope.launch {
                handleDeviceRemoved(device)
            }
        }
        
        override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
            scannerScope.launch {
                handleDeviceStatusChanged(status)
            }
        }
    }
    
    /**
     * Start real-time device scanning
     */
    suspend fun startScanning(): Boolean {
        return try {
            if (midiManager == null) {
                throw MidiError.MidiNotSupported
            }
            
            if (_isScanning.value) {
                return true // Already scanning
            }
            
            // Register device callback
            midiManager.registerDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            
            // Perform initial device discovery
            performInitialScan()
            
            _isScanning.value = true
            
            // Emit scanning started event
            _deviceEvents.emit(DeviceEvent.ScanningStarted)
            
            true
        } catch (e: Exception) {
            throw MidiError.ConnectionFailed("scanner", "Failed to start scanning: ${e.message}")
        }
    }
    
    /**
     * Stop real-time device scanning
     */
    suspend fun stopScanning() {
        try {
            if (!_isScanning.value) {
                return // Not scanning
            }
            
            // Unregister device callback
            midiManager?.unregisterDeviceCallback(deviceCallback)
            
            // Cancel all reconnection jobs
            reconnectionJobs.values.forEach { it.cancel() }
            reconnectionJobs.clear()
            reconnectionAttempts.clear()
            
            _isScanning.value = false
            
            // Emit scanning stopped event
            _deviceEvents.emit(DeviceEvent.ScanningStopped)
        } catch (e: Exception) {
            // Log error but don't throw during shutdown
        }
    }
    
    /**
     * Validate device capabilities and suitability
     */
    suspend fun validateDevice(deviceId: String): DeviceValidationResult {
        return try {
            // Check cache first
            deviceCapabilityCache[deviceId]?.let { capabilities ->
                return DeviceValidationResult.Valid(capabilities)
            }
            
            // Get device info from manager
            val capabilities = midiDeviceManager.getDeviceCapabilities(deviceId)
                ?: return DeviceValidationResult.Invalid("Device not found")
            
            // Validate device capabilities
            val validationErrors = mutableListOf<String>()
            
            if (!capabilities.hasInputPorts && !capabilities.hasOutputPorts) {
                validationErrors.add("Device has no input or output ports")
            }
            
            if (!capabilities.supportsRealTime) {
                validationErrors.add("Device does not support real-time MIDI")
            }
            
            // Cache the capabilities
            deviceCapabilityCache[deviceId] = capabilities
            
            if (validationErrors.isEmpty()) {
                DeviceValidationResult.Valid(capabilities)
            } else {
                DeviceValidationResult.Invalid(validationErrors.joinToString(", "))
            }
        } catch (e: Exception) {
            DeviceValidationResult.Invalid("Validation failed: ${e.message}")
        }
    }
    
    /**
     * Enable automatic reconnection for a device
     */
    fun enableAutoReconnection(deviceId: String) {
        // Reset reconnection attempts for this device
        reconnectionAttempts[deviceId] = 0
    }
    
    /**
     * Disable automatic reconnection for a device
     */
    fun disableAutoReconnection(deviceId: String) {
        reconnectionJobs[deviceId]?.cancel()
        reconnectionJobs.remove(deviceId)
        reconnectionAttempts.remove(deviceId)
    }
    
    /**
     * Force a device rescan
     */
    suspend fun rescanDevices() {
        try {
            if (midiManager == null) {
                throw MidiError.MidiNotSupported
            }
            
            // Clear capability cache to force re-validation
            deviceCapabilityCache.clear()
            
            // Perform fresh scan
            performInitialScan()
            
            _deviceEvents.emit(DeviceEvent.RescanCompleted)
        } catch (e: Exception) {
            _deviceEvents.emit(DeviceEvent.ScanError(e.message ?: "Unknown error"))
        }
    }
    
    // Private helper methods
    
    private suspend fun performInitialScan() {
        try {
            val devices = midiManager?.devices ?: emptyArray()
            
            for (device in devices) {
                val appDeviceInfo = convertToAppDeviceInfo(device)
                val validationResult = validateDevice(appDeviceInfo.id)
                
                when (validationResult) {
                    is DeviceValidationResult.Valid -> {
                        _deviceEvents.emit(DeviceEvent.DeviceDiscovered(appDeviceInfo, validationResult.capabilities))
                    }
                    is DeviceValidationResult.Invalid -> {
                        _deviceEvents.emit(DeviceEvent.DeviceValidationFailed(appDeviceInfo, validationResult.reason))
                    }
                }
            }
        } catch (e: Exception) {
            _deviceEvents.emit(DeviceEvent.ScanError("Initial scan failed: ${e.message}"))
        }
    }
    
    private suspend fun handleDeviceAdded(device: MidiDeviceInfo) {
        try {
            val appDeviceInfo = convertToAppDeviceInfo(device)
            val validationResult = validateDevice(appDeviceInfo.id)
            
            when (validationResult) {
                is DeviceValidationResult.Valid -> {
                    _deviceEvents.emit(DeviceEvent.DeviceDiscovered(appDeviceInfo, validationResult.capabilities))
                    
                    // Check if this device should auto-reconnect
                    if (reconnectionAttempts.containsKey(appDeviceInfo.id)) {
                        attemptAutoReconnection(appDeviceInfo.id)
                    }
                }
                is DeviceValidationResult.Invalid -> {
                    _deviceEvents.emit(DeviceEvent.DeviceValidationFailed(appDeviceInfo, validationResult.reason))
                }
            }
        } catch (e: Exception) {
            _deviceEvents.emit(DeviceEvent.ScanError("Device added handling failed: ${e.message}"))
        }
    }
    
    private suspend fun handleDeviceRemoved(device: MidiDeviceInfo) {
        try {
            val deviceId = device.id.toString()
            val appDeviceInfo = convertToAppDeviceInfo(device)
            
            // Remove from capability cache
            deviceCapabilityCache.remove(deviceId)
            
            _deviceEvents.emit(DeviceEvent.DeviceLost(appDeviceInfo))
            
            // Start auto-reconnection if enabled
            if (reconnectionAttempts.containsKey(deviceId)) {
                scheduleReconnectionAttempt(deviceId)
            }
        } catch (e: Exception) {
            _deviceEvents.emit(DeviceEvent.ScanError("Device removed handling failed: ${e.message}"))
        }
    }
    
    private suspend fun handleDeviceStatusChanged(status: MidiDeviceStatus) {
        try {
            val deviceInfo = status.deviceInfo
            val appDeviceInfo = convertToAppDeviceInfo(deviceInfo)
            
            _deviceEvents.emit(DeviceEvent.DeviceStatusChanged(appDeviceInfo, status.isInputPortOpen(0)))
        } catch (e: Exception) {
            _deviceEvents.emit(DeviceEvent.ScanError("Device status change handling failed: ${e.message}"))
        }
    }
    
    private fun attemptAutoReconnection(deviceId: String) {
        val currentAttempts = reconnectionAttempts[deviceId] ?: 0
        
        if (currentAttempts >= maxReconnectionAttempts) {
            // Max attempts reached, give up
            reconnectionAttempts.remove(deviceId)
            scannerScope.launch {
                _deviceEvents.emit(DeviceEvent.ReconnectionFailed(deviceId, "Max attempts reached"))
            }
            return
        }
        
        reconnectionJobs[deviceId] = scannerScope.launch {
            try {
                delay(calculateReconnectionDelay(currentAttempts))
                
                val success = midiDeviceManager.connectDevice(deviceId)
                if (success) {
                    // Reconnection successful
                    reconnectionAttempts.remove(deviceId)
                    reconnectionJobs.remove(deviceId)
                    _deviceEvents.emit(DeviceEvent.DeviceReconnected(deviceId))
                } else {
                    // Reconnection failed, increment attempts
                    reconnectionAttempts[deviceId] = currentAttempts + 1
                    _deviceEvents.emit(DeviceEvent.ReconnectionAttemptFailed(deviceId, currentAttempts + 1))
                }
            } catch (e: Exception) {
                reconnectionAttempts[deviceId] = currentAttempts + 1
                _deviceEvents.emit(DeviceEvent.ReconnectionAttemptFailed(deviceId, currentAttempts + 1))
            }
        }
    }
    
    private fun scheduleReconnectionAttempt(deviceId: String) {
        reconnectionJobs[deviceId] = scannerScope.launch {
            delay(baseReconnectionDelay)
            attemptAutoReconnection(deviceId)
        }
    }
    
    private fun calculateReconnectionDelay(attemptNumber: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        return baseReconnectionDelay * (1L shl attemptNumber)
    }
    
    private fun convertToAppDeviceInfo(deviceInfo: MidiDeviceInfo): AppMidiDeviceInfo {
        val bundle = deviceInfo.properties
        val manufacturer = bundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Unknown"
        val name = bundle.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Device ${deviceInfo.id}"
        
        return AppMidiDeviceInfo(
            id = deviceInfo.id.toString(),
            name = name,
            manufacturer = manufacturer,
            type = midiDeviceManager.getDeviceCapabilities(deviceInfo.id.toString())?.deviceType 
                ?: com.high.theone.midi.model.MidiDeviceType.OTHER,
            inputPortCount = deviceInfo.inputPortCount,
            outputPortCount = deviceInfo.outputPortCount,
            isConnected = midiDeviceManager.isDeviceConnected(deviceInfo.id.toString())
        )
    }
    
    /**
     * Clean up resources when scanner is no longer needed
     */
    fun cleanup() {
        scannerScope.cancel()
        deviceCapabilityCache.clear()
    }
}

/**
 * Device validation result
 */
sealed class DeviceValidationResult {
    data class Valid(val capabilities: DeviceCapabilities) : DeviceValidationResult()
    data class Invalid(val reason: String) : DeviceValidationResult()
}

/**
 * Device discovery and connection events
 */
sealed class DeviceEvent {
    object ScanningStarted : DeviceEvent()
    object ScanningStopped : DeviceEvent()
    object RescanCompleted : DeviceEvent()
    data class ScanError(val message: String) : DeviceEvent()
    
    data class DeviceDiscovered(
        val device: AppMidiDeviceInfo,
        val capabilities: DeviceCapabilities
    ) : DeviceEvent()
    
    data class DeviceLost(val device: AppMidiDeviceInfo) : DeviceEvent()
    
    data class DeviceValidationFailed(
        val device: AppMidiDeviceInfo,
        val reason: String
    ) : DeviceEvent()
    
    data class DeviceStatusChanged(
        val device: AppMidiDeviceInfo,
        val isActive: Boolean
    ) : DeviceEvent()
    
    data class DeviceReconnected(val deviceId: String) : DeviceEvent()
    
    data class ReconnectionFailed(
        val deviceId: String,
        val reason: String
    ) : DeviceEvent()
    
    data class ReconnectionAttemptFailed(
        val deviceId: String,
        val attemptNumber: Int
    ) : DeviceEvent()
}