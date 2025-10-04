package com.high.theone.midi

import com.high.theone.midi.model.*
import org.junit.Test
import org.junit.Assert.*

class MidiValidatorTest {
    
    @Test
    fun `validateDeviceInfo should pass for valid device info`() {
        val deviceInfo = MidiDeviceInfo(
            id = "device1",
            name = "Test Device",
            manufacturer = "Test Manufacturer",
            type = MidiDeviceType.KEYBOARD,
            inputPortCount = 1,
            outputPortCount = 1,
            isConnected = true
        )
        
        val result = MidiValidator.validateDeviceInfo(deviceInfo)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateDeviceInfo should fail for blank device ID`() {
        val deviceInfo = MidiDeviceInfo(
            id = "",
            name = "Test Device",
            manufacturer = "Test Manufacturer",
            type = MidiDeviceType.KEYBOARD,
            inputPortCount = 1,
            outputPortCount = 1,
            isConnected = true
        )
        
        val result = MidiValidator.validateDeviceInfo(deviceInfo)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateParameterMapping should pass for valid pad trigger mapping`() {
        val mapping = MidiParameterMapping(
            midiType = MidiMessageType.NOTE_ON,
            midiChannel = 0,
            midiController = 64,
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad1",
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
        
        val result = MidiValidator.validateParameterMapping(mapping)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateParameterMapping should fail for invalid channel`() {
        val mapping = MidiParameterMapping(
            midiType = MidiMessageType.NOTE_ON,
            midiChannel = 16, // Invalid channel
            midiController = 64,
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad1",
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
        
        val result = MidiValidator.validateParameterMapping(mapping)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateParameterMapping should fail for pad trigger with wrong message type`() {
        val mapping = MidiParameterMapping(
            midiType = MidiMessageType.CONTROL_CHANGE, // Wrong type for pad trigger
            midiChannel = 0,
            midiController = 64,
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad1",
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
        
        val result = MidiValidator.validateParameterMapping(mapping)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateParameterMapping should fail for volume control with invalid range`() {
        val mapping = MidiParameterMapping(
            midiType = MidiMessageType.CONTROL_CHANGE,
            midiChannel = 0,
            midiController = 7,
            targetType = MidiTargetType.PAD_VOLUME,
            targetId = "pad1",
            minValue = 0.0f,
            maxValue = 2.0f, // Invalid range for volume
            curve = MidiCurve.LINEAR
        )
        
        val result = MidiValidator.validateParameterMapping(mapping)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateMapping should detect conflicts`() {
        val conflictingMappings = listOf(
            MidiParameterMapping(
                midiType = MidiMessageType.CONTROL_CHANGE,
                midiChannel = 0,
                midiController = 7,
                targetType = MidiTargetType.PAD_VOLUME,
                targetId = "pad1",
                minValue = 0.0f,
                maxValue = 1.0f,
                curve = MidiCurve.LINEAR
            ),
            MidiParameterMapping(
                midiType = MidiMessageType.CONTROL_CHANGE,
                midiChannel = 0,
                midiController = 7, // Same controller as above
                targetType = MidiTargetType.MASTER_VOLUME,
                targetId = "master",
                minValue = 0.0f,
                maxValue = 1.0f,
                curve = MidiCurve.LINEAR
            )
        )
        
        val mapping = MidiMapping(
            id = "test_mapping",
            name = "Test Mapping",
            deviceId = "device1",
            mappings = conflictingMappings,
            isActive = true
        )
        
        val result = MidiValidator.validateMapping(mapping)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MidiError.MappingConflict)
    }
    
    @Test
    fun `validateDeviceConfiguration should pass for valid configuration`() {
        val config = MidiDeviceConfiguration(
            deviceId = "device1",
            isInputEnabled = true,
            isOutputEnabled = false,
            inputLatencyMs = 10.0f,
            outputLatencyMs = 5.0f,
            velocityCurve = MidiCurve.LINEAR,
            channelFilter = setOf(0, 1, 2)
        )
        
        val result = MidiValidator.validateDeviceConfiguration(config)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateDeviceConfiguration should fail for excessive latency`() {
        val config = MidiDeviceConfiguration(
            deviceId = "device1",
            isInputEnabled = true,
            isOutputEnabled = false,
            inputLatencyMs = 1500.0f, // Excessive latency
            outputLatencyMs = 5.0f,
            velocityCurve = MidiCurve.LINEAR,
            channelFilter = null
        )
        
        val result = MidiValidator.validateDeviceConfiguration(config)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateGlobalSettings should pass for valid settings`() {
        val settings = MidiGlobalSettings(
            midiThru = false,
            velocitySensitivity = 1.0f,
            panicOnStop = true,
            omniMode = false
        )
        
        val result = MidiValidator.validateGlobalSettings(settings)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateGlobalSettings should fail for invalid velocity sensitivity`() {
        val settings = MidiGlobalSettings(
            midiThru = false,
            velocitySensitivity = 3.0f, // Invalid range
            panicOnStop = true,
            omniMode = false
        )
        
        val result = MidiValidator.validateGlobalSettings(settings)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `validateClockSettings should pass for valid settings`() {
        val settings = MidiClockSettings(
            clockSource = MidiClockSource.EXTERNAL_AUTO,
            sendClock = false,
            receiveClock = true,
            clockDivision = 24,
            syncToFirstClock = true
        )
        
        val result = MidiValidator.validateClockSettings(settings)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateClockSettings should fail for conflicting clock configuration`() {
        val settings = MidiClockSettings(
            clockSource = MidiClockSource.INTERNAL,
            sendClock = true,
            receiveClock = true, // Cannot receive when using internal clock
            clockDivision = 24,
            syncToFirstClock = false
        )
        
        val result = MidiValidator.validateClockSettings(settings)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `checkMappingConflicts should detect conflicts across multiple mappings`() {
        val mapping1 = MidiMapping(
            id = "mapping1",
            name = "Mapping 1",
            deviceId = "device1",
            mappings = listOf(
                MidiParameterMapping(
                    midiType = MidiMessageType.CONTROL_CHANGE,
                    midiChannel = 0,
                    midiController = 7,
                    targetType = MidiTargetType.PAD_VOLUME,
                    targetId = "pad1",
                    minValue = 0.0f,
                    maxValue = 1.0f,
                    curve = MidiCurve.LINEAR
                )
            ),
            isActive = true
        )
        
        val mapping2 = MidiMapping(
            id = "mapping2",
            name = "Mapping 2",
            deviceId = "device2",
            mappings = listOf(
                MidiParameterMapping(
                    midiType = MidiMessageType.CONTROL_CHANGE,
                    midiChannel = 0,
                    midiController = 7, // Same as mapping1
                    targetType = MidiTargetType.MASTER_VOLUME,
                    targetId = "master",
                    minValue = 0.0f,
                    maxValue = 1.0f,
                    curve = MidiCurve.LINEAR
                )
            ),
            isActive = true
        )
        
        val conflicts = MidiValidator.checkMappingConflicts(listOf(mapping1, mapping2))
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts[0].contains("CONTROL_CHANGE"))
        assertTrue(conflicts[0].contains("mapping1"))
        assertTrue(conflicts[0].contains("mapping2"))
    }
    
    @Test
    fun `validateLearnTarget should pass for valid target`() {
        val target = MidiLearnTarget(
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad1",
            startTime = System.currentTimeMillis() - 1000 // 1 second ago
        )
        
        val result = MidiValidator.validateLearnTarget(target)
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `validateLearnTarget should fail for timed out session`() {
        val target = MidiLearnTarget(
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad1",
            startTime = System.currentTimeMillis() - (6 * 60 * 1000) // 6 minutes ago (timed out)
        )
        
        val result = MidiValidator.validateLearnTarget(target)
        assertTrue(result.isFailure)
    }
}