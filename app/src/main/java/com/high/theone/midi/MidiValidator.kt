package com.high.theone.midi

import com.high.theone.midi.model.*

/**
 * MIDI validation utilities for ensuring data integrity
 */
object MidiValidator {
    
    /**
     * Validate MIDI device information
     */
    fun validateDeviceInfo(deviceInfo: MidiDeviceInfo): Result<Unit> {
        return try {
            require(deviceInfo.id.isNotBlank()) { "Device ID cannot be blank" }
            require(deviceInfo.name.isNotBlank()) { "Device name cannot be blank" }
            require(deviceInfo.inputPortCount >= 0) { "Input port count cannot be negative" }
            require(deviceInfo.outputPortCount >= 0) { "Output port count cannot be negative" }
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiError.InvalidMessage(e.message?.toByteArray() ?: byteArrayOf()))
        }
    }
    
    /**
     * Validate MIDI mapping configuration
     */
    fun validateMapping(mapping: MidiMapping): Result<Unit> {
        return try {
            require(mapping.id.isNotBlank()) { "Mapping ID cannot be blank" }
            require(mapping.name.isNotBlank()) { "Mapping name cannot be blank" }
            
            // Validate each parameter mapping
            mapping.mappings.forEach { paramMapping ->
                validateParameterMapping(paramMapping).getOrThrow()
            }
            
            // Check for duplicate mappings
            val duplicates = mapping.mappings
                .groupBy { Triple(it.midiType, it.midiChannel, it.midiController) }
                .filter { it.value.size > 1 }
            
            if (duplicates.isNotEmpty()) {
                val conflictingMappings = duplicates.keys.map { 
                    "${it.first}:${it.second}:${it.third}" 
                }
                return Result.failure(MidiError.MappingConflict(conflictingMappings))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiException("Mapping validation failed", e))
        }
    }
    
    /**
     * Validate individual parameter mapping
     */
    fun validateParameterMapping(mapping: MidiParameterMapping): Result<Unit> {
        return try {
            require(mapping.midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
            require(mapping.midiController in 0..127) { "MIDI controller must be between 0 and 127" }
            require(mapping.targetId.isNotBlank()) { "Target ID cannot be blank" }
            require(mapping.minValue <= mapping.maxValue) { "Min value must be <= max value" }
            
            // Validate target type specific constraints
            when (mapping.targetType) {
                MidiTargetType.PAD_TRIGGER -> {
                    // Pad triggers should typically use Note On/Off
                    require(mapping.midiType in listOf(MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF)) {
                        "Pad triggers should use Note On/Off messages"
                    }
                }
                MidiTargetType.PAD_VOLUME, MidiTargetType.PAD_PAN, MidiTargetType.MASTER_VOLUME -> {
                    // Volume/pan controls should use CC or aftertouch
                    require(mapping.midiType in listOf(MidiMessageType.CONTROL_CHANGE, MidiMessageType.AFTERTOUCH)) {
                        "Volume/pan controls should use Control Change or Aftertouch"
                    }
                    require(mapping.minValue >= 0.0f && mapping.maxValue <= 1.0f) {
                        "Volume/pan values should be between 0.0 and 1.0"
                    }
                }
                MidiTargetType.SEQUENCER_TEMPO -> {
                    require(mapping.midiType == MidiMessageType.CONTROL_CHANGE) {
                        "Tempo control should use Control Change"
                    }
                    require(mapping.minValue >= 60.0f && mapping.maxValue <= 200.0f) {
                        "Tempo should be between 60 and 200 BPM"
                    }
                }
                MidiTargetType.TRANSPORT_CONTROL -> {
                    require(mapping.midiType in listOf(
                        MidiMessageType.NOTE_ON, 
                        MidiMessageType.CONTROL_CHANGE,
                        MidiMessageType.START,
                        MidiMessageType.STOP,
                        MidiMessageType.CONTINUE
                    )) {
                        "Transport control should use appropriate message types"
                    }
                }
                MidiTargetType.EFFECT_PARAMETER -> {
                    require(mapping.midiType in listOf(MidiMessageType.CONTROL_CHANGE, MidiMessageType.AFTERTOUCH)) {
                        "Effect parameters should use Control Change or Aftertouch"
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiException("Parameter mapping validation failed: ${e.message}", e))
        }
    }
    
    /**
     * Validate MIDI device configuration
     */
    fun validateDeviceConfiguration(config: MidiDeviceConfiguration): Result<Unit> {
        return try {
            require(config.deviceId.isNotBlank()) { "Device ID cannot be blank" }
            require(config.inputLatencyMs >= 0) { "Input latency cannot be negative" }
            require(config.outputLatencyMs >= 0) { "Output latency cannot be negative" }
            require(config.inputLatencyMs <= 1000) { "Input latency should not exceed 1000ms" }
            require(config.outputLatencyMs <= 1000) { "Output latency should not exceed 1000ms" }
            
            config.channelFilter?.forEach { channel ->
                require(channel in 0..15) { "MIDI channel must be between 0 and 15" }
            }
            
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiException("Device configuration validation failed: ${e.message}", e))
        }
    }
    
    /**
     * Validate MIDI global settings
     */
    fun validateGlobalSettings(settings: MidiGlobalSettings): Result<Unit> {
        return try {
            require(settings.velocitySensitivity in 0.0f..2.0f) { 
                "Velocity sensitivity must be between 0.0 and 2.0" 
            }
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiException("Global settings validation failed: ${e.message}", e))
        }
    }
    
    /**
     * Validate MIDI clock settings
     */
    fun validateClockSettings(settings: MidiClockSettings): Result<Unit> {
        return try {
            require(settings.clockDivision > 0) { "Clock division must be positive" }
            require(settings.clockDivision <= 32) { "Clock division should not exceed 32" }
            
            // Validate clock source compatibility
            if (settings.receiveClock && settings.clockSource == MidiClockSource.INTERNAL) {
                return Result.failure(MidiException("Cannot receive clock when using internal clock source"))
            }
            
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiException("Clock settings validation failed: ${e.message}", e))
        }
    }
    
    /**
     * Validate complete MIDI configuration
     */
    fun validateConfiguration(config: MidiConfiguration): Result<Unit> {
        return try {
            // Validate global settings
            validateGlobalSettings(config.globalSettings).getOrThrow()
            
            // Validate clock settings
            validateClockSettings(config.clockSettings).getOrThrow()
            
            // Validate device configurations
            config.deviceConfigurations.values.forEach { deviceConfig ->
                validateDeviceConfiguration(deviceConfig).getOrThrow()
            }
            
            // Validate active mapping ID exists if set
            config.activeMappingId?.let { mappingId ->
                require(mappingId.isNotBlank()) { "Active mapping ID cannot be blank" }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiException("Configuration validation failed", e))
        }
    }
    
    /**
     * Check for mapping conflicts between multiple mappings
     */
    fun checkMappingConflicts(mappings: List<MidiMapping>): List<String> {
        val conflicts = mutableListOf<String>()
        val allParameterMappings = mappings.flatMap { mapping ->
            mapping.mappings.map { paramMapping ->
                Triple(paramMapping.midiType, paramMapping.midiChannel, paramMapping.midiController) to mapping.id
            }
        }
        
        val duplicates = allParameterMappings
            .groupBy { it.first }
            .filter { it.value.size > 1 }
        
        duplicates.forEach { (key, mappingIds) ->
            conflicts.add("MIDI ${key.first} on channel ${key.second} controller ${key.third} " +
                         "is mapped in multiple profiles: ${mappingIds.map { it.second }.distinct().joinToString()}")
        }
        
        return conflicts
    }
    
    /**
     * Validate MIDI learn target
     */
    fun validateLearnTarget(target: MidiLearnTarget): Result<Unit> {
        return try {
            require(target.targetId.isNotBlank()) { "Target ID cannot be blank" }
            require(target.startTime > 0) { "Start time must be positive" }
            
            val currentTime = System.currentTimeMillis()
            require(target.startTime <= currentTime) { "Start time cannot be in the future" }
            
            // Check if learn session hasn't timed out (5 minutes)
            val timeoutMs = 5 * 60 * 1000
            require(currentTime - target.startTime <= timeoutMs) { "MIDI learn session has timed out" }
            
            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            Result.failure(MidiException("Learn target validation failed: ${e.message}", e))
        }
    }
}