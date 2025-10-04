package com.high.theone.midi.device

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.high.theone.midi.model.MidiDeviceInfo as AppMidiDeviceInfo
import com.high.theone.midi.model.MidiDeviceType
import com.high.theone.midi.MidiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages MIDI device discovery, connection, and state tracking using Android MIDI API.
 * Handles device enumeration, filtering, and connection state management.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class MidiDeviceManager @Inject constructor(
    private val context: Context
) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    
    // Device state tracking
    private val _connectedDevices = MutableStateFlow<Map<String, AppMidiDeviceInfo>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, AppMidiDeviceInfo>> = _connectedDevices.asStateFlow()
    
    private val _availableDevices = MutableStateFlow<List<AppMidiDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<AppMidiDeviceInfo>> = _availableDevices.asStateFlow()
    
    // Active device connections
    private val activeConnections = mutableMapOf<String, MidiDevice>()
    
    // Device callback for monitoring device changes
    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            val appDeviceInfo = convertToAppDeviceInfo(device)
            updateAvailableDevices()
        }
        
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val deviceId = device.id.toString()
            
            // Remove from connected devices if present
            val currentConnected = _connectedDevices.value.toMutableMap()
            if (currentConnected.remove(deviceId) != null) {
                _connectedDevices.value = currentConnected
                
                // Close connection if active
                activeConnections[deviceId]?.close()
                activeConnections.remove(deviceId)
            }
            
            updateAvailableDevices()
        }
    }
    
    /**
     * Initialize the MIDI device manager and start device monitoring
     */
    suspend fun initialize(): Boolean {
        return try {
            if (midiManager == null) {
                throw MidiError.MidiNotSupported
            }
            
            // Register device callback for real-time monitoring
            midiManager.registerDeviceCallback(deviceCallback, null)
            
            // Initial device scan
            updateAvailableDevices()
            
            true
        } catch (e: Exception) {
            throw MidiError.ConnectionFailed("system", "Failed to initialize MIDI manager: ${e.message}")
        }
    }
    
    /**
     * Shutdown the MIDI device manager and clean up resources
     */
    fun shutdown() {
        try {
            // Close all active connections
            activeConnections.values.forEach { device ->
                try {
                    device.close()
                } catch (e: Exception) {
                    // Log but don't throw - we're shutting down
                }
            }
            activeConnections.clear()
            
            // Unregister callback
            midiManager?.unregisterDeviceCallback(deviceCallback)
            
            // Clear state
            _connectedDevices.value = emptyMap()
            _availableDevices.value = emptyList()
        } catch (e: Exception) {
            // Log error but don't throw during shutdown
        }
    }
    
    /**
     * Scan for available MIDI devices and update the available devices list
     */
    suspend fun scanForDevices(): List<AppMidiDeviceInfo> {
        return try {
            if (midiManager == null) {
                throw MidiError.MidiNotSupported
            }
            
            val devices = midiManager.devices
            val appDevices = devices.map { convertToAppDeviceInfo(it) }
                .filter { isValidDevice(it) }
            
            _availableDevices.value = appDevices
            appDevices
        } catch (e: Exception) {
            throw MidiError.DeviceNotFound("Failed to scan devices: ${e.message}")
        }
    }
    
    /**
     * Connect to a specific MIDI device
     */
    suspend fun connectDevice(deviceId: String): Boolean {
        return try {
            if (midiManager == null) {
                throw MidiError.MidiNotSupported
            }
            
            // Check if already connected
            if (activeConnections.containsKey(deviceId)) {
                return true
            }
            
            // Find device info
            val deviceInfo = midiManager.devices.find { it.id.toString() == deviceId }
                ?: throw MidiError.DeviceNotFound(deviceId)
            
            // Open device connection
            val device = openDevice(deviceInfo)
            activeConnections[deviceId] = device
            
            // Update connected devices state
            val appDeviceInfo = convertToAppDeviceInfo(deviceInfo).copy(isConnected = true)
            val currentConnected = _connectedDevices.value.toMutableMap()
            currentConnected[deviceId] = appDeviceInfo
            _connectedDevices.value = currentConnected
            
            true
        } catch (e: Exception) {
            when (e) {
                is MidiError -> throw e
                else -> throw MidiError.ConnectionFailed(deviceId, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Disconnect from a specific MIDI device
     */
    suspend fun disconnectDevice(deviceId: String): Boolean {
        return try {
            // Close connection if active
            activeConnections[deviceId]?.close()
            activeConnections.remove(deviceId)
            
            // Update connected devices state
            val currentConnected = _connectedDevices.value.toMutableMap()
            currentConnected.remove(deviceId)
            _connectedDevices.value = currentConnected
            
            true
        } catch (e: Exception) {
            throw MidiError.ConnectionFailed(deviceId, "Failed to disconnect: ${e.message}")
        }
    }
    
    /**
     * Get a connected MIDI device by ID
     */
    fun getConnectedDevice(deviceId: String): MidiDevice? {
        return activeConnections[deviceId]
    }
    
    /**
     * Check if a device is currently connected
     */
    fun isDeviceConnected(deviceId: String): Boolean {
        return activeConnections.containsKey(deviceId)
    }
    
    /**
     * Get device capabilities and validate device suitability
     */
    fun getDeviceCapabilities(deviceId: String): DeviceCapabilities? {
        val deviceInfo = midiManager?.devices?.find { it.id.toString() == deviceId }
            ?: return null
        
        return DeviceCapabilities(
            hasInputPorts = deviceInfo.inputPortCount > 0,
            hasOutputPorts = deviceInfo.outputPortCount > 0,
            inputPortCount = deviceInfo.inputPortCount,
            outputPortCount = deviceInfo.outputPortCount,
            supportsRealTime = true, // Android MIDI API supports real-time
            deviceType = determineDeviceType(deviceInfo)
        )
    }
    
    // Private helper methods
    
    private suspend fun openDevice(deviceInfo: MidiDeviceInfo): MidiDevice {
        return suspendCancellableCoroutine { continuation ->
            midiManager?.openDevice(deviceInfo, { device ->
                if (device != null) {
                    continuation.resume(device)
                } else {
                    continuation.resumeWithException(
                        MidiError.ConnectionFailed(deviceInfo.id.toString(), "Failed to open device")
                    )
                }
            }, null)
        }
    }
    
    private fun updateAvailableDevices() {
        try {
            if (midiManager == null) return
            
            val devices = midiManager.devices
            val appDevices = devices.map { deviceInfo ->
                val deviceId = deviceInfo.id.toString()
                val isConnected = activeConnections.containsKey(deviceId)
                convertToAppDeviceInfo(deviceInfo).copy(isConnected = isConnected)
            }.filter { isValidDevice(it) }
            
            _availableDevices.value = appDevices
        } catch (e: Exception) {
            // Log error but don't throw - this is called from callbacks
        }
    }
    
    private fun convertToAppDeviceInfo(deviceInfo: MidiDeviceInfo): AppMidiDeviceInfo {
        val bundle = deviceInfo.properties
        val manufacturer = bundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Unknown"
        val name = bundle.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Device ${deviceInfo.id}"
        
        return AppMidiDeviceInfo(
            id = deviceInfo.id.toString(),
            name = name,
            manufacturer = manufacturer,
            type = determineDeviceType(deviceInfo),
            inputPortCount = deviceInfo.inputPortCount,
            outputPortCount = deviceInfo.outputPortCount,
            isConnected = false // Will be updated based on actual connection state
        )
    }
    
    private fun determineDeviceType(deviceInfo: MidiDeviceInfo): MidiDeviceType {
        val bundle = deviceInfo.properties
        val name = bundle.getString(MidiDeviceInfo.PROPERTY_NAME)?.lowercase() ?: ""
        val manufacturer = bundle.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)?.lowercase() ?: ""
        
        return when {
            name.contains("keyboard") || name.contains("piano") -> MidiDeviceType.KEYBOARD
            name.contains("controller") || name.contains("control") -> MidiDeviceType.CONTROLLER
            name.contains("drum") || name.contains("pad") -> MidiDeviceType.DRUM_MACHINE
            name.contains("synth") || name.contains("synthesizer") -> MidiDeviceType.SYNTHESIZER
            name.contains("interface") || name.contains("usb") -> MidiDeviceType.INTERFACE
            else -> MidiDeviceType.OTHER
        }
    }
    
    private fun isValidDevice(deviceInfo: AppMidiDeviceInfo): Boolean {
        // Filter out devices that don't have any useful ports
        return deviceInfo.inputPortCount > 0 || deviceInfo.outputPortCount > 0
    }
}

/**
 * Device capabilities information
 */
data class DeviceCapabilities(
    val hasInputPorts: Boolean,
    val hasOutputPorts: Boolean,
    val inputPortCount: Int,
    val outputPortCount: Int,
    val supportsRealTime: Boolean,
    val deviceType: MidiDeviceType
)