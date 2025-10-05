package com.high.theone.midi.repository

import android.content.Context
import com.high.theone.midi.model.MidiConfiguration
import com.high.theone.midi.model.MidiDeviceConfiguration
import com.high.theone.midi.model.MidiGlobalSettings
import com.high.theone.midi.model.MidiClockSettings
import com.high.theone.midi.model.MidiClockSource
import com.high.theone.midi.model.MidiCurve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MIDI configuration data persistence
 * Handles saving, loading, backup and restore of MIDI settings
 */
@Singleton
class MidiConfigurationRepository @Inject constructor(
    private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val configFile = File(context.filesDir, "midi_configuration.json")
    private val backupFile = File(context.filesDir, "midi_configuration_backup.json")
    
    private val _currentConfiguration = MutableStateFlow(getDefaultConfiguration())
    val currentConfiguration: Flow<MidiConfiguration> = _currentConfiguration.asStateFlow()
    
    /**
     * Load MIDI configuration from storage
     */
    suspend fun loadConfiguration(): Result<MidiConfiguration> = withContext(Dispatchers.IO) {
        try {
            if (!configFile.exists()) {
                val defaultConfig = getDefaultConfiguration()
                saveConfiguration(defaultConfig)
                return@withContext Result.success(defaultConfig)
            }
            
            val configJson = configFile.readText()
            val configuration = json.decodeFromString<MidiConfiguration>(configJson)
            _currentConfiguration.value = configuration
            Result.success(configuration)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to load MIDI configuration: ${e.message}", e))
        }
    }
    
    /**
     * Save MIDI configuration to storage
     */
    suspend fun saveConfiguration(configuration: MidiConfiguration): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Create backup of current configuration before saving new one
            if (configFile.exists()) {
                configFile.copyTo(backupFile, overwrite = true)
            }
            
            val configJson = json.encodeToString(configuration)
            configFile.writeText(configJson)
            _currentConfiguration.value = configuration
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to save MIDI configuration: ${e.message}", e))
        }
    }
    
    /**
     * Update device configuration for a specific device
     */
    suspend fun updateDeviceConfiguration(
        deviceId: String,
        deviceConfig: MidiDeviceConfiguration
    ): Result<Unit> {
        val currentConfig = _currentConfiguration.value
        val updatedConfig = currentConfig.copy(
            deviceConfigurations = currentConfig.deviceConfigurations + (deviceId to deviceConfig)
        )
        return saveConfiguration(updatedConfig)
    }
    
    /**
     * Remove device configuration
     */
    suspend fun removeDeviceConfiguration(deviceId: String): Result<Unit> {
        val currentConfig = _currentConfiguration.value
        val updatedConfig = currentConfig.copy(
            deviceConfigurations = currentConfig.deviceConfigurations - deviceId
        )
        return saveConfiguration(updatedConfig)
    }
    
    /**
     * Update global MIDI settings
     */
    suspend fun updateGlobalSettings(globalSettings: MidiGlobalSettings): Result<Unit> {
        val currentConfig = _currentConfiguration.value
        val updatedConfig = currentConfig.copy(globalSettings = globalSettings)
        return saveConfiguration(updatedConfig)
    }
    
    /**
     * Update MIDI clock settings
     */
    suspend fun updateClockSettings(clockSettings: MidiClockSettings): Result<Unit> {
        val currentConfig = _currentConfiguration.value
        val updatedConfig = currentConfig.copy(clockSettings = clockSettings)
        return saveConfiguration(updatedConfig)
    }
    
    /**
     * Set active mapping ID
     */
    suspend fun setActiveMappingId(mappingId: String?): Result<Unit> {
        val currentConfig = _currentConfiguration.value
        val updatedConfig = currentConfig.copy(activeMappingId = mappingId)
        return saveConfiguration(updatedConfig)
    }
    
    /**
     * Create backup of current configuration
     */
    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!configFile.exists()) {
                return@withContext Result.failure(IOException("No configuration file to backup"))
            }
            
            val timestamp = System.currentTimeMillis()
            val backupFileName = "midi_configuration_backup_$timestamp.json"
            val timestampedBackupFile = File(context.filesDir, backupFileName)
            
            configFile.copyTo(timestampedBackupFile, overwrite = true)
            Result.success(timestampedBackupFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to create backup: ${e.message}", e))
        }
    }
    
    /**
     * Restore configuration from backup
     */
    suspend fun restoreFromBackup(): Result<MidiConfiguration> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) {
                return@withContext Result.failure(IOException("No backup file found"))
            }
            
            val backupJson = backupFile.readText()
            val configuration = json.decodeFromString<MidiConfiguration>(backupJson)
            
            // Validate the backup configuration
            validateConfiguration(configuration)
            
            // Save the restored configuration as current
            saveConfiguration(configuration)
            Result.success(configuration)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to restore from backup: ${e.message}", e))
        }
    }
    
    /**
     * Export configuration to external file
     */
    suspend fun exportConfiguration(exportPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val exportFile = File(exportPath)
            if (!configFile.exists()) {
                return@withContext Result.failure(IOException("No configuration to export"))
            }
            
            configFile.copyTo(exportFile, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to export configuration: ${e.message}", e))
        }
    }
    
    /**
     * Import configuration from external file
     */
    suspend fun importConfiguration(importPath: String): Result<MidiConfiguration> = withContext(Dispatchers.IO) {
        try {
            val importFile = File(importPath)
            if (!importFile.exists()) {
                return@withContext Result.failure(IOException("Import file not found"))
            }
            
            val importJson = importFile.readText()
            val configuration = json.decodeFromString<MidiConfiguration>(importJson)
            
            // Validate the imported configuration
            validateConfiguration(configuration)
            
            // Save the imported configuration
            saveConfiguration(configuration)
            Result.success(configuration)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to import configuration: ${e.message}", e))
        }
    }
    
    /**
     * Reset to default configuration
     */
    suspend fun resetToDefault(): Result<MidiConfiguration> {
        val defaultConfig = getDefaultConfiguration()
        return saveConfiguration(defaultConfig).map { defaultConfig }
    }
    
    /**
     * Get device configuration for a specific device
     */
    fun getDeviceConfiguration(deviceId: String): MidiDeviceConfiguration? {
        return _currentConfiguration.value.deviceConfigurations[deviceId]
    }
    
    /**
     * Get all device configurations
     */
    fun getAllDeviceConfigurations(): Map<String, MidiDeviceConfiguration> {
        return _currentConfiguration.value.deviceConfigurations
    }
    
    /**
     * Get global settings
     */
    fun getGlobalSettings(): MidiGlobalSettings {
        return _currentConfiguration.value.globalSettings
    }
    
    /**
     * Get clock settings
     */
    fun getClockSettings(): MidiClockSettings {
        return _currentConfiguration.value.clockSettings
    }
    
    /**
     * Get active mapping ID
     */
    fun getActiveMappingId(): String? {
        return _currentConfiguration.value.activeMappingId
    }
    
    /**
     * Validate configuration data integrity
     */
    private fun validateConfiguration(configuration: MidiConfiguration) {
        // Validate device configurations
        configuration.deviceConfigurations.forEach { (deviceId, deviceConfig) ->
            require(deviceId.isNotBlank()) { "Device ID cannot be blank" }
            require(deviceConfig.deviceId == deviceId) { "Device configuration ID mismatch" }
        }
        
        // Validate global settings
        val globalSettings = configuration.globalSettings
        require(globalSettings.velocitySensitivity in 0.0f..2.0f) {
            "Invalid velocity sensitivity: ${globalSettings.velocitySensitivity}"
        }
        
        // Validate clock settings
        val clockSettings = configuration.clockSettings
        require(clockSettings.clockDivision > 0) {
            "Invalid clock division: ${clockSettings.clockDivision}"
        }
    }
    
    /**
     * Get default MIDI configuration
     */
    private fun getDefaultConfiguration(): MidiConfiguration {
        return MidiConfiguration(
            deviceConfigurations = emptyMap(),
            activeMappingId = null,
            globalSettings = MidiGlobalSettings(
                midiThru = false,
                velocitySensitivity = 1.0f,
                panicOnStop = true,
                omniMode = false
            ),
            clockSettings = MidiClockSettings(
                clockSource = MidiClockSource.INTERNAL,
                sendClock = false,
                receiveClock = false,
                clockDivision = 24,
                syncToFirstClock = true
            )
        )
    }
    
    /**
     * Get default device configuration
     */
    fun getDefaultDeviceConfiguration(deviceId: String): MidiDeviceConfiguration {
        return MidiDeviceConfiguration(
            deviceId = deviceId,
            isInputEnabled = true,
            isOutputEnabled = false,
            inputLatencyMs = 0.0f,
            outputLatencyMs = 0.0f,
            velocityCurve = MidiCurve.LINEAR,
            channelFilter = null // Accept all channels
        )
    }
}