package com.high.theone.features.midi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.midi.device.MidiDeviceManager
import com.high.theone.midi.model.MidiDeviceInfo
import com.high.theone.midi.model.MidiStatistics
import com.high.theone.midi.MidiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MIDI Settings Screen.
 * Manages device discovery, connection state, and configuration.
 */
@HiltViewModel
class MidiSettingsViewModel @Inject constructor(
    private val midiDeviceManager: MidiDeviceManager
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(MidiSettingsUiState())
    val uiState: StateFlow<MidiSettingsUiState> = _uiState.asStateFlow()
    
    // Device states from manager
    val availableDevices: StateFlow<List<MidiDeviceInfo>> = midiDeviceManager.availableDevices
    val connectedDevices: StateFlow<Map<String, MidiDeviceInfo>> = midiDeviceManager.connectedDevices
    
    // Device configurations
    private val _deviceConfigurations = MutableStateFlow<Map<String, MidiDeviceConfiguration>>(emptyMap())
    val deviceConfigurations: StateFlow<Map<String, MidiDeviceConfiguration>> = _deviceConfigurations.asStateFlow()
    
    init {
        initializeMidiSystem()
        observeDeviceChanges()
    }
    
    private fun initializeMidiSystem() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val initialized = midiDeviceManager.initialize()
                if (initialized) {
                    _uiState.value = _uiState.value.copy(
                        midiEnabled = true,
                        isLoading = false,
                        errorMessage = null
                    )
                    refreshDevices()
                } else {
                    _uiState.value = _uiState.value.copy(
                        midiEnabled = false,
                        isLoading = false,
                        permissionRequired = true,
                        errorMessage = "MIDI system initialization failed"
                    )
                }
            } catch (e: MidiError.MidiNotSupported) {
                _uiState.value = _uiState.value.copy(
                    midiEnabled = false,
                    isLoading = false,
                    errorMessage = "MIDI is not supported on this device"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    midiEnabled = false,
                    isLoading = false,
                    errorMessage = "Failed to initialize MIDI: ${e.message}"
                )
            }
        }
    }
    
    private fun observeDeviceChanges() {
        viewModelScope.launch {
            // Observe connected devices and update statistics
            connectedDevices.collect { devices ->
                if (devices.isNotEmpty()) {
                    updateStatistics()
                }
            }
        }
    }
    
    fun refreshDevices() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                midiDeviceManager.scanForDevices()
                
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to scan devices: ${e.message}"
                )
            }
        }
    }
    
    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(errorMessage = null)
                
                val success = midiDeviceManager.connectDevice(deviceId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to connect to device"
                    )
                }
            } catch (e: MidiError.DeviceNotFound) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Device not found: ${e.deviceId}"
                )
            } catch (e: MidiError.ConnectionFailed) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Connection failed: ${e.reason}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }
    
    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(errorMessage = null)
                
                val success = midiDeviceManager.disconnectDevice(deviceId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to disconnect device"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to disconnect: ${e.message}"
                )
            }
        }
    }
    
    fun updateDeviceConfiguration(deviceId: String, configuration: MidiDeviceConfiguration) {
        viewModelScope.launch {
            try {
                // Store configuration
                val currentConfigs = _deviceConfigurations.value.toMutableMap()
                currentConfigs[deviceId] = configuration
                _deviceConfigurations.value = currentConfigs
                
                // Apply configuration to device manager if needed
                applyDeviceConfiguration(deviceId, configuration)
                
                _uiState.value = _uiState.value.copy(errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update configuration: ${e.message}"
                )
            }
        }
    }
    
    fun requestMidiPermission() {
        // This would typically trigger a permission request
        // For now, we'll just try to reinitialize
        initializeMidiSystem()
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    private suspend fun updateStatistics() {
        try {
            // Create mock statistics for now - in real implementation this would come from MIDI manager
            val statistics = MidiStatistics(
                inputMessageCount = 0L,
                outputMessageCount = 0L,
                averageInputLatency = 0f,
                droppedMessageCount = 0L,
                lastErrorMessage = null
            )
            
            _uiState.value = _uiState.value.copy(statistics = statistics)
        } catch (e: Exception) {
            // Log error but don't update UI state
        }
    }
    
    private suspend fun applyDeviceConfiguration(deviceId: String, configuration: MidiDeviceConfiguration) {
        // Apply configuration to the actual device manager
        // This would involve setting latency compensation, velocity curves, etc.
        // Implementation depends on the specific device manager capabilities
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup is handled by the device manager's lifecycle
    }
}

/**
 * UI State for MIDI Settings Screen
 */
data class MidiSettingsUiState(
    val midiEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val permissionRequired: Boolean = false,
    val errorMessage: String? = null,
    val statistics: MidiStatistics? = null
)