package com.high.theone.midi.mapping

import com.high.theone.midi.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core engine for managing MIDI parameter mappings.
 * Handles mapping logic, conflict detection, and profile management.
 */
@Singleton
class MidiMappingEngine @Inject constructor() {
    
    private val _activeMappings = MutableStateFlow<List<MidiMapping>>(emptyList())
    val activeMappings: StateFlow<List<MidiMapping>> = _activeMappings.asStateFlow()
    
    private val _currentProfile = MutableStateFlow<MidiMapping?>(null)
    val currentProfile: StateFlow<MidiMapping?> = _currentProfile.asStateFlow()
    
    private val _mappingConflicts = MutableStateFlow<List<MidiMappingConflict>>(emptyList())
    val mappingConflicts: StateFlow<List<MidiMappingConflict>> = _mappingConflicts.asStateFlow()
    
    private val mappingMutex = Mutex()
    private val mappingProfiles = mutableMapOf<String, MidiMapping>()
    
    /**
     * Processes a MIDI message and returns the mapped parameter changes
     */
    suspend fun processMidiMessage(message: MidiMessage): List<MidiParameterChange> {
        val currentMappings = _activeMappings.value
        val parameterChanges = mutableListOf<MidiParameterChange>()
        
        for (mapping in currentMappings) {
            if (!mapping.isActive) continue
            
            for (parameterMapping in mapping.mappings) {
                if (isMessageMatchingMapping(message, parameterMapping)) {
                    val parameterChange = createParameterChange(message, parameterMapping)
                    parameterChanges.add(parameterChange)
                }
            }
        }
        
        return parameterChanges
    }
    
    /**
     * Adds a new mapping profile
     */
    suspend fun addMappingProfile(mapping: MidiMapping): Result<Unit> = mappingMutex.withLock {
        return try {
            // Validate mapping
            validateMapping(mapping)
            
            // Store the mapping
            mappingProfiles[mapping.id] = mapping
            
            // Update active mappings if this mapping is active
            if (mapping.isActive) {
                updateActiveMappings()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiMappingException("Failed to add mapping profile: ${e.message}", e))
        }
    }
    
    /**
     * Removes a mapping profile
     */
    suspend fun removeMappingProfile(mappingId: String): Result<Unit> = mappingMutex.withLock {
        return try {
            val mapping = mappingProfiles.remove(mappingId)
                ?: return Result.failure(MidiMappingException("Mapping profile not found: $mappingId"))
            
            // Update active mappings
            updateActiveMappings()
            
            // Clear current profile if it was removed
            if (_currentProfile.value?.id == mappingId) {
                _currentProfile.value = null
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiMappingException("Failed to remove mapping profile: ${e.message}", e))
        }
    }
    
    /**
     * Sets the active mapping profile
     */
    suspend fun setActiveMappingProfile(mappingId: String): Result<Unit> = mappingMutex.withLock {
        return try {
            val mapping = mappingProfiles[mappingId]
                ?: return Result.failure(MidiMappingException("Mapping profile not found: $mappingId"))
            
            // Deactivate current profile
            _currentProfile.value?.let { current ->
                mappingProfiles[current.id] = current.copy(isActive = false)
            }
            
            // Activate new profile
            val activeMapping = mapping.copy(isActive = true)
            mappingProfiles[mappingId] = activeMapping
            _currentProfile.value = activeMapping
            
            updateActiveMappings()
            detectMappingConflicts()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiMappingException("Failed to set active mapping profile: ${e.message}", e))
        }
    }
    
    /**
     * Gets all available mapping profiles
     */
    fun getAllMappingProfiles(): List<MidiMapping> {
        return mappingProfiles.values.toList()
    }
    
    /**
     * Gets a specific mapping profile by ID
     */
    fun getMappingProfile(mappingId: String): MidiMapping? {
        return mappingProfiles[mappingId]
    }
    
    /**
     * Updates a mapping profile
     */
    suspend fun updateMappingProfile(mapping: MidiMapping): Result<Unit> = mappingMutex.withLock {
        return try {
            validateMapping(mapping)
            
            mappingProfiles[mapping.id] = mapping
            
            if (mapping.isActive) {
                updateActiveMappings()
                detectMappingConflicts()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiMappingException("Failed to update mapping profile: ${e.message}", e))
        }
    }
    
    /**
     * Detects and resolves mapping conflicts
     */
    suspend fun detectMappingConflicts(): List<MidiMappingConflict> {
        val conflicts = mutableListOf<MidiMappingConflict>()
        val activeMappings = _activeMappings.value
        
        for (i in activeMappings.indices) {
            for (j in i + 1 until activeMappings.size) {
                val mapping1 = activeMappings[i]
                val mapping2 = activeMappings[j]
                
                val conflictingMappings = findConflictingMappings(mapping1, mapping2)
                conflicts.addAll(conflictingMappings)
            }
        }
        
        _mappingConflicts.value = conflicts
        return conflicts
    }
    
    /**
     * Resolves a mapping conflict by applying the specified resolution
     */
    suspend fun resolveMappingConflict(
        conflict: MidiMappingConflict,
        resolution: MidiConflictResolution
    ): Result<Unit> = mappingMutex.withLock {
        return try {
            when (resolution.type) {
                MidiConflictResolutionType.KEEP_FIRST -> {
                    // Remove conflicting mapping from second profile
                    val updatedMapping = removeConflictingMapping(
                        conflict.mapping2,
                        conflict.conflictingParameter2
                    )
                    mappingProfiles[updatedMapping.id] = updatedMapping
                }
                MidiConflictResolutionType.KEEP_SECOND -> {
                    // Remove conflicting mapping from first profile
                    val updatedMapping = removeConflictingMapping(
                        conflict.mapping1,
                        conflict.conflictingParameter1
                    )
                    mappingProfiles[updatedMapping.id] = updatedMapping
                }
                MidiConflictResolutionType.DISABLE_BOTH -> {
                    // Disable both conflicting mappings
                    val updatedMapping1 = removeConflictingMapping(
                        conflict.mapping1,
                        conflict.conflictingParameter1
                    )
                    val updatedMapping2 = removeConflictingMapping(
                        conflict.mapping2,
                        conflict.conflictingParameter2
                    )
                    mappingProfiles[updatedMapping1.id] = updatedMapping1
                    mappingProfiles[updatedMapping2.id] = updatedMapping2
                }
                MidiConflictResolutionType.REASSIGN -> {
                    // Reassign one of the mappings to a different MIDI controller
                    resolution.newMidiController?.let { newController ->
                        val updatedParameter = conflict.conflictingParameter2.copy(
                            midiController = newController
                        )
                        val updatedMappings = conflict.mapping2.mappings.map { mapping ->
                            if (mapping == conflict.conflictingParameter2) updatedParameter else mapping
                        }
                        val updatedMapping = conflict.mapping2.copy(mappings = updatedMappings)
                        mappingProfiles[updatedMapping.id] = updatedMapping
                    }
                }
                MidiConflictResolutionType.REPLACE_ALL -> {
                    // Replace all conflicting mappings with the selected one
                    resolution.selectedMapping?.let { selected ->
                        val updatedMapping1 = removeConflictingMapping(conflict.mapping1, conflict.conflictingParameter1)
                        val updatedMapping2 = removeConflictingMapping(conflict.mapping2, conflict.conflictingParameter2)
                        val newMappings = updatedMapping1.mappings + selected
                        val finalMapping = updatedMapping1.copy(mappings = newMappings)
                        mappingProfiles[finalMapping.id] = finalMapping
                        mappingProfiles[updatedMapping2.id] = updatedMapping2
                    }
                }
                MidiConflictResolutionType.REMOVE_ALL -> {
                    // Remove both conflicting mappings
                    val updatedMapping1 = removeConflictingMapping(conflict.mapping1, conflict.conflictingParameter1)
                    val updatedMapping2 = removeConflictingMapping(conflict.mapping2, conflict.conflictingParameter2)
                    mappingProfiles[updatedMapping1.id] = updatedMapping1
                    mappingProfiles[updatedMapping2.id] = updatedMapping2
                }
            }
            
            updateActiveMappings()
            detectMappingConflicts()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiMappingException("Failed to resolve mapping conflict: ${e.message}", e))
        }
    }
    
    private fun isMessageMatchingMapping(
        message: MidiMessage,
        mapping: MidiParameterMapping
    ): Boolean {
        return message.type == mapping.midiType &&
                message.channel == mapping.midiChannel &&
                (mapping.midiType != MidiMessageType.CONTROL_CHANGE || 
                 message.data1 == mapping.midiController)
    }
    
    private fun createParameterChange(
        message: MidiMessage,
        mapping: MidiParameterMapping
    ): MidiParameterChange {
        val rawValue = when (mapping.midiType) {
            MidiMessageType.CONTROL_CHANGE -> message.data2
            MidiMessageType.NOTE_ON -> message.data2 // velocity
            MidiMessageType.PITCH_BEND -> {
                // Combine data1 and data2 for 14-bit pitch bend
                (message.data2 shl 7) or message.data1
            }
            else -> message.data1
        }
        
        val normalizedValue = rawValue / 127.0f
        val scaledValue = applyCurveAndScale(normalizedValue, mapping)
        
        return MidiParameterChange(
            targetType = mapping.targetType,
            targetId = mapping.targetId,
            value = scaledValue,
            midiMessage = message
        )
    }
    
    private fun applyCurveAndScale(
        normalizedValue: Float,
        mapping: MidiParameterMapping
    ): Float {
        val curvedValue = when (mapping.curve) {
            MidiCurve.LINEAR -> normalizedValue
            MidiCurve.EXPONENTIAL -> normalizedValue * normalizedValue
            MidiCurve.LOGARITHMIC -> kotlin.math.sqrt(normalizedValue)
            MidiCurve.S_CURVE -> {
                // Smooth S-curve using sigmoid-like function
                val x = (normalizedValue - 0.5f) * 6f // Scale to -3 to 3
                1f / (1f + kotlin.math.exp(-x))
            }
        }
        
        return mapping.minValue + curvedValue * (mapping.maxValue - mapping.minValue)
    }
    
    private fun validateMapping(mapping: MidiMapping) {
        require(mapping.id.isNotBlank()) { "Mapping ID cannot be blank" }
        require(mapping.name.isNotBlank()) { "Mapping name cannot be blank" }
        
        for (paramMapping in mapping.mappings) {
            require(paramMapping.midiChannel in 0..15) { 
                "MIDI channel must be between 0 and 15" 
            }
            require(paramMapping.midiController in 0..127) { 
                "MIDI controller must be between 0 and 127" 
            }
            require(paramMapping.targetId.isNotBlank()) { 
                "Target ID cannot be blank" 
            }
            require(paramMapping.minValue <= paramMapping.maxValue) { 
                "Min value must be less than or equal to max value" 
            }
        }
    }
    
    private fun updateActiveMappings() {
        val active = mappingProfiles.values.filter { it.isActive }
        _activeMappings.value = active
    }
    
    private fun findConflictingMappings(
        mapping1: MidiMapping,
        mapping2: MidiMapping
    ): List<MidiMappingConflict> {
        val conflicts = mutableListOf<MidiMappingConflict>()
        
        for (param1 in mapping1.mappings) {
            for (param2 in mapping2.mappings) {
                if (isMappingConflict(param1, param2)) {
                    conflicts.add(
                        MidiMappingConflict(
                            mapping1 = mapping1,
                            mapping2 = mapping2,
                            conflictingParameter1 = param1,
                            conflictingParameter2 = param2,
                            conflictType = MidiConflictType.DUPLICATE_MIDI_CONTROLLER
                        )
                    )
                }
            }
        }
        
        return conflicts
    }
    
    private fun isMappingConflict(
        param1: MidiParameterMapping,
        param2: MidiParameterMapping
    ): Boolean {
        return param1.midiType == param2.midiType &&
                param1.midiChannel == param2.midiChannel &&
                param1.midiController == param2.midiController
    }
    
    private fun removeConflictingMapping(
        mapping: MidiMapping,
        conflictingParameter: MidiParameterMapping
    ): MidiMapping {
        val updatedMappings = mapping.mappings.filter { it != conflictingParameter }
        return mapping.copy(mappings = updatedMappings)
    }
}

/**
 * Represents a parameter change resulting from MIDI input
 */
data class MidiParameterChange(
    val targetType: MidiTargetType,
    val targetId: String,
    val value: Float,
    val midiMessage: MidiMessage
)

/**
 * Represents a conflict between two MIDI mappings
 */
data class MidiMappingConflict(
    val mapping1: MidiMapping,
    val mapping2: MidiMapping,
    val conflictingParameter1: MidiParameterMapping,
    val conflictingParameter2: MidiParameterMapping,
    val conflictType: MidiConflictType
)

/**
 * Types of MIDI mapping conflicts
 */
enum class MidiConflictType {
    DUPLICATE_MIDI_CONTROLLER,
    DUPLICATE_TARGET_PARAMETER,
    INCOMPATIBLE_RANGES
}

/**
 * Resolution strategy for MIDI mapping conflicts
 */
data class MidiConflictResolution(
    val type: MidiConflictResolutionType,
    val selectedMapping: MidiParameterMapping? = null,
    val newMidiController: Int? = null
)

/**
 * Types of conflict resolution strategies
 */
enum class MidiConflictResolutionType {
    KEEP_FIRST,
    KEEP_SECOND,
    DISABLE_BOTH,
    REASSIGN,
    REPLACE_ALL,
    REMOVE_ALL
}

/**
 * Exception thrown when MIDI mapping operations fail
 */
class MidiMappingException(message: String, cause: Throwable? = null) : Exception(message, cause)