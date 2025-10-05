package com.high.theone.midi.repository

import android.content.Context
import com.high.theone.midi.model.MidiMapping
import com.high.theone.midi.model.MidiParameterMapping
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.midi.model.MidiTargetType
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MIDI mapping profile management and storage
 * Handles mapping profiles, import/export, and default templates
 */
@Singleton
class MidiMappingRepository @Inject constructor(
    private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val mappingsDir = File(context.filesDir, "midi_mappings")
    private val templatesDir = File(context.filesDir, "midi_templates")
    
    private val _availableMappings = MutableStateFlow<List<MidiMapping>>(emptyList())
    val availableMappings: Flow<List<MidiMapping>> = _availableMappings.asStateFlow()
    
    init {
        // Ensure directories exist
        mappingsDir.mkdirs()
        templatesDir.mkdirs()
    }
    
    /**
     * Load all available MIDI mappings
     */
    suspend fun loadAllMappings(): Result<List<MidiMapping>> = withContext(Dispatchers.IO) {
        try {
            val mappings = mutableListOf<MidiMapping>()
            
            // Load user mappings
            mappingsDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val mappingJson = file.readText()
                    val mapping = json.decodeFromString<MidiMapping>(mappingJson)
                    mappings.add(mapping)
                } catch (e: Exception) {
                    // Log error but continue loading other mappings
                    println("Failed to load mapping from ${file.name}: ${e.message}")
                }
            }
            
            // Load default templates
            loadDefaultTemplates().forEach { template ->
                mappings.add(template)
            }
            
            _availableMappings.value = mappings.sortedBy { it.name }
            Result.success(mappings)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to load MIDI mappings: ${e.message}", e))
        }
    }
    
    /**
     * Save MIDI mapping profile
     */
    suspend fun saveMapping(mapping: MidiMapping): Result<MidiMapping> = withContext(Dispatchers.IO) {
        try {
            val mappingFile = File(mappingsDir, "${mapping.id}.json")
            val mappingJson = json.encodeToString(mapping)
            mappingFile.writeText(mappingJson)
            
            // Update in-memory list
            val currentMappings = _availableMappings.value.toMutableList()
            val existingIndex = currentMappings.indexOfFirst { it.id == mapping.id }
            if (existingIndex >= 0) {
                currentMappings[existingIndex] = mapping
            } else {
                currentMappings.add(mapping)
            }
            _availableMappings.value = currentMappings.sortedBy { it.name }
            
            Result.success(mapping)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to save MIDI mapping: ${e.message}", e))
        }
    }
    
    /**
     * Load specific MIDI mapping by ID
     */
    suspend fun loadMapping(mappingId: String): Result<MidiMapping> = withContext(Dispatchers.IO) {
        try {
            // Check in-memory first
            _availableMappings.value.find { it.id == mappingId }?.let {
                return@withContext Result.success(it)
            }
            
            // Load from file
            val mappingFile = File(mappingsDir, "$mappingId.json")
            if (!mappingFile.exists()) {
                return@withContext Result.failure(IOException("Mapping not found: $mappingId"))
            }
            
            val mappingJson = mappingFile.readText()
            val mapping = json.decodeFromString<MidiMapping>(mappingJson)
            Result.success(mapping)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to load MIDI mapping: ${e.message}", e))
        }
    }
    
    /**
     * Delete MIDI mapping
     */
    suspend fun deleteMapping(mappingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mappingFile = File(mappingsDir, "$mappingId.json")
            if (mappingFile.exists()) {
                mappingFile.delete()
            }
            
            // Update in-memory list
            val currentMappings = _availableMappings.value.toMutableList()
            currentMappings.removeAll { it.id == mappingId }
            _availableMappings.value = currentMappings
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to delete MIDI mapping: ${e.message}", e))
        }
    }
    
    /**
     * Create new mapping profile
     */
    suspend fun createMapping(
        name: String,
        deviceId: String? = null,
        mappings: List<MidiParameterMapping> = emptyList()
    ): Result<MidiMapping> {
        val newMapping = MidiMapping(
            id = UUID.randomUUID().toString(),
            name = name,
            deviceId = deviceId,
            mappings = mappings,
            isActive = false
        )
        return saveMapping(newMapping)
    }
    
    /**
     * Duplicate existing mapping
     */
    suspend fun duplicateMapping(mappingId: String, newName: String): Result<MidiMapping> {
        return loadMapping(mappingId).fold(
            onSuccess = { originalMapping ->
                val duplicatedMapping = originalMapping.copy(
                    id = UUID.randomUUID().toString(),
                    name = newName,
                    isActive = false
                )
                saveMapping(duplicatedMapping)
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    /**
     * Export mapping to external file
     */
    suspend fun exportMapping(mappingId: String, exportPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mapping = loadMapping(mappingId).getOrThrow()
            val exportFile = File(exportPath)
            val mappingJson = json.encodeToString(mapping)
            exportFile.writeText(mappingJson)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to export mapping: ${e.message}", e))
        }
    }
    
    /**
     * Import mapping from external file
     */
    suspend fun importMapping(importPath: String): Result<MidiMapping> = withContext(Dispatchers.IO) {
        try {
            val importFile = File(importPath)
            if (!importFile.exists()) {
                return@withContext Result.failure(IOException("Import file not found"))
            }
            
            val mappingJson = importFile.readText()
            val importedMapping = json.decodeFromString<MidiMapping>(mappingJson)
            
            // Generate new ID to avoid conflicts
            val newMapping = importedMapping.copy(
                id = UUID.randomUUID().toString(),
                isActive = false
            )
            
            // Validate the mapping
            validateMapping(newMapping)
            
            saveMapping(newMapping)
        } catch (e: Exception) {
            Result.failure(IOException("Failed to import mapping: ${e.message}", e))
        }
    }
    
    /**
     * Get mapping by name
     */
    fun getMappingByName(name: String): MidiMapping? {
        return _availableMappings.value.find { it.name == name }
    }
    
    /**
     * Get mappings for specific device
     */
    fun getMappingsForDevice(deviceId: String): List<MidiMapping> {
        return _availableMappings.value.filter { it.deviceId == deviceId || it.deviceId == null }
    }
    
    /**
     * Search mappings by name
     */
    fun searchMappings(query: String): List<MidiMapping> {
        return _availableMappings.value.filter { 
            it.name.contains(query, ignoreCase = true) ||
            it.deviceId?.contains(query, ignoreCase = true) == true
        }
    }
    
    /**
     * Load default mapping templates for common devices
     */
    private suspend fun loadDefaultTemplates(): List<MidiMapping> = withContext(Dispatchers.IO) {
        val templates = mutableListOf<MidiMapping>()
        
        // Create default templates if they don't exist
        val defaultTemplates = createDefaultTemplates()
        
        defaultTemplates.forEach { template ->
            val templateFile = File(templatesDir, "${template.id}.json")
            if (!templateFile.exists()) {
                try {
                    val templateJson = json.encodeToString(template)
                    templateFile.writeText(templateJson)
                } catch (e: Exception) {
                    println("Failed to save template ${template.name}: ${e.message}")
                }
            }
            templates.add(template)
        }
        
        templates
    }
    
    /**
     * Create default mapping templates for common MIDI devices
     */
    private fun createDefaultTemplates(): List<MidiMapping> {
        return listOf(
            // Generic MIDI Keyboard Template
            MidiMapping(
                id = "template_generic_keyboard",
                name = "Generic MIDI Keyboard",
                deviceId = null,
                mappings = listOf(
                    // Map MIDI notes 36-51 (C2-D#3) to pads 0-15
                    MidiParameterMapping(
                        midiType = MidiMessageType.NOTE_ON,
                        midiChannel = 0,
                        midiController = 36, // C2
                        targetType = MidiTargetType.PAD_TRIGGER,
                        targetId = "pad_0",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    ),
                    // Volume control via CC7
                    MidiParameterMapping(
                        midiType = MidiMessageType.CONTROL_CHANGE,
                        midiChannel = 0,
                        midiController = 7,
                        targetType = MidiTargetType.MASTER_VOLUME,
                        targetId = "master_volume",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    )
                ),
                isActive = false
            ),
            
            // Akai MPD Template
            MidiMapping(
                id = "template_akai_mpd",
                name = "Akai MPD Series",
                deviceId = null,
                mappings = listOf(
                    // Pad mappings for typical MPD layout
                    MidiParameterMapping(
                        midiType = MidiMessageType.NOTE_ON,
                        midiChannel = 9, // Drum channel
                        midiController = 36, // Kick
                        targetType = MidiTargetType.PAD_TRIGGER,
                        targetId = "pad_0",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    ),
                    MidiParameterMapping(
                        midiType = MidiMessageType.NOTE_ON,
                        midiChannel = 9,
                        midiController = 38, // Snare
                        targetType = MidiTargetType.PAD_TRIGGER,
                        targetId = "pad_1",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    ),
                    // Knob mappings
                    MidiParameterMapping(
                        midiType = MidiMessageType.CONTROL_CHANGE,
                        midiChannel = 0,
                        midiController = 1, // Mod wheel
                        targetType = MidiTargetType.EFFECT_PARAMETER,
                        targetId = "filter_cutoff",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    )
                ),
                isActive = false
            ),
            
            // Generic MIDI Controller Template
            MidiMapping(
                id = "template_generic_controller",
                name = "Generic MIDI Controller",
                deviceId = null,
                mappings = listOf(
                    // Transport controls
                    MidiParameterMapping(
                        midiType = MidiMessageType.CONTROL_CHANGE,
                        midiChannel = 0,
                        midiController = 64, // Sustain pedal for play/stop
                        targetType = MidiTargetType.TRANSPORT_CONTROL,
                        targetId = "play_stop",
                        minValue = 0.0f,
                        maxValue = 1.0f,
                        curve = MidiCurve.LINEAR
                    ),
                    // Tempo control
                    MidiParameterMapping(
                        midiType = MidiMessageType.CONTROL_CHANGE,
                        midiChannel = 0,
                        midiController = 2,
                        targetType = MidiTargetType.SEQUENCER_TEMPO,
                        targetId = "tempo",
                        minValue = 60.0f,
                        maxValue = 200.0f,
                        curve = MidiCurve.LINEAR
                    )
                ),
                isActive = false
            )
        )
    }
    
    /**
     * Validate mapping data integrity
     */
    private fun validateMapping(mapping: MidiMapping) {
        require(mapping.id.isNotBlank()) { "Mapping ID cannot be blank" }
        require(mapping.name.isNotBlank()) { "Mapping name cannot be blank" }
        
        mapping.mappings.forEach { paramMapping ->
            require(paramMapping.midiChannel in 0..15) { 
                "Invalid MIDI channel: ${paramMapping.midiChannel}" 
            }
            require(paramMapping.midiController in 0..127) { 
                "Invalid MIDI controller: ${paramMapping.midiController}" 
            }
            require(paramMapping.targetId.isNotBlank()) { 
                "Target ID cannot be blank" 
            }
            require(paramMapping.minValue <= paramMapping.maxValue) { 
                "Min value must be <= max value" 
            }
        }
    }
    
    /**
     * Get mapping statistics
     */
    fun getMappingStatistics(): Map<String, Any> {
        val mappings = _availableMappings.value
        return mapOf(
            "totalMappings" to mappings.size,
            "activeMappings" to mappings.count { it.isActive },
            "deviceSpecificMappings" to mappings.count { it.deviceId != null },
            "genericMappings" to mappings.count { it.deviceId == null },
            "averageMappingsPerProfile" to if (mappings.isNotEmpty()) {
                mappings.sumOf { it.mappings.size } / mappings.size
            } else 0
        )
    }
}