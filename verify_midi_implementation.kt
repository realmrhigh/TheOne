// Simple verification script for MIDI implementation
// This would be run in a Kotlin REPL or as a standalone script

import com.high.theone.midi.model.*
import com.high.theone.midi.*

fun main() {
    println("=== MIDI Implementation Verification ===")
    
    // Test 1: Create and validate MIDI message
    println("\n1. Testing MIDI Message Creation:")
    try {
        val noteOnMessage = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 0,
            data1 = 64, // Middle C
            data2 = 127, // Max velocity
            timestamp = System.nanoTime()
        )
        println("✓ Created Note On message: $noteOnMessage")
        
        val isValid = MidiMessageParser.validateMessage(noteOnMessage)
        println("✓ Message validation: $isValid")
        
    } catch (e: Exception) {
        println("✗ Error creating MIDI message: ${e.message}")
    }
    
    // Test 2: Parse raw MIDI bytes
    println("\n2. Testing MIDI Message Parsing:")
    try {
        val rawBytes = byteArrayOf(0x90.toByte(), 0x40, 0x7F) // Note On, channel 0, note 64, velocity 127
        val parsedMessage = MidiMessageParser.parseMessage(rawBytes)
        println("✓ Parsed message: $parsedMessage")
        
        val backToBytes = MidiMessageParser.messageToBytes(parsedMessage)
        println("✓ Converted back to bytes: ${backToBytes.joinToString { "%02X".format(it) }}")
        
    } catch (e: Exception) {
        println("✗ Error parsing MIDI bytes: ${e.message}")
    }
    
    // Test 3: Create device info
    println("\n3. Testing MIDI Device Info:")
    try {
        val deviceInfo = MidiDeviceInfo(
            id = "test_device_1",
            name = "Test MIDI Keyboard",
            manufacturer = "Test Corp",
            type = MidiDeviceType.KEYBOARD,
            inputPortCount = 1,
            outputPortCount = 1,
            isConnected = true
        )
        println("✓ Created device info: $deviceInfo")
        
        val validation = MidiValidator.validateDeviceInfo(deviceInfo)
        println("✓ Device validation: ${validation.isSuccess}")
        
    } catch (e: Exception) {
        println("✗ Error creating device info: ${e.message}")
    }
    
    // Test 4: Create MIDI mapping
    println("\n4. Testing MIDI Mapping:")
    try {
        val parameterMapping = MidiParameterMapping(
            midiType = MidiMessageType.NOTE_ON,
            midiChannel = 0,
            midiController = 64,
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = "pad_1",
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
        
        val mapping = MidiMapping(
            id = "test_mapping_1",
            name = "Test Mapping",
            deviceId = "test_device_1",
            mappings = listOf(parameterMapping),
            isActive = true
        )
        println("✓ Created MIDI mapping: $mapping")
        
        val validation = MidiValidator.validateMapping(mapping)
        println("✓ Mapping validation: ${validation.isSuccess}")
        
    } catch (e: Exception) {
        println("✗ Error creating MIDI mapping: ${e.message}")
    }
    
    // Test 5: Test error handling
    println("\n5. Testing Error Handling:")
    try {
        // Test invalid message parsing
        val invalidBytes = byteArrayOf() // Empty array should fail
        MidiMessageParser.parseMessage(invalidBytes)
        println("✗ Should have thrown an error for empty bytes")
    } catch (e: MidiError.InvalidMessage) {
        println("✓ Correctly caught InvalidMessage error: ${e.message}")
    } catch (e: Exception) {
        println("✗ Unexpected error type: ${e.javaClass.simpleName}")
    }
    
    // Test 6: Test message type detection
    println("\n6. Testing Message Type Detection:")
    try {
        val noteOnMsg = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 0L)
        val noteOffMsg = MidiMessage(MidiMessageType.NOTE_OFF, 0, 64, 0, 0L)
        val ccMsg = MidiMessage(MidiMessageType.CONTROL_CHANGE, 0, 7, 100, 0L)
        
        println("✓ Note On detection: ${MidiMessageParser.isNoteOn(noteOnMsg)}")
        println("✓ Note Off detection: ${MidiMessageParser.isNoteOff(noteOffMsg)}")
        println("✓ Controller number: ${MidiMessageParser.getControllerNumber(ccMsg)}")
        println("✓ Controller value: ${MidiMessageParser.getControllerValue(ccMsg)}")
        
    } catch (e: Exception) {
        println("✗ Error in message type detection: ${e.message}")
    }
    
    println("\n=== Verification Complete ===")
}