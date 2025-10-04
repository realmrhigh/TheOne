package com.high.theone.midi

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.midi.model.MidiTargetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MIDI message parsing and validation utilities with enhanced message type detection,
 * routing logic, and malformed message filtering and sanitization.
 * 
 * Requirements: 1.3, 7.2
 */
@Singleton
class MidiMessageParser @Inject constructor() {
    
    /**
     * Parse raw MIDI bytes into a MidiMessage with enhanced validation and error handling
     */
    fun parseMessage(data: ByteArray, timestamp: Long = System.nanoTime()): ParseResult {
        return try {
            val sanitizedData = sanitizeMessage(data)
            val message = parseValidatedMessage(sanitizedData, timestamp)
            ParseResult.Success(message)
        } catch (e: MidiError.InvalidMessage) {
            ParseResult.Error(e, data)
        } catch (e: Exception) {
            ParseResult.Error(MidiError.InvalidMessage(data), data)
        }
    }
    
    /**
     * Internal parsing method for validated data
     */
    private fun parseValidatedMessage(data: ByteArray, timestamp: Long): MidiMessage {
        if (data.isEmpty()) {
            throw MidiError.InvalidMessage(data)
        }
        
        val statusByte = data[0].toInt() and 0xFF
        val messageType = MidiMessageType.fromStatusByte(statusByte)
            ?: throw MidiError.InvalidMessage(data)
        
        val channel = statusByte and 0x0F
        
        return when (messageType) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF, 
            MidiMessageType.CONTROL_CHANGE, MidiMessageType.AFTERTOUCH -> {
                if (data.size < 3) throw MidiError.InvalidMessage(data)
                val data1 = data[1].toInt() and 0x7F
                val data2 = data[2].toInt() and 0x7F
                MidiMessage(messageType, channel, data1, data2, timestamp)
            }
            
            MidiMessageType.PROGRAM_CHANGE -> {
                if (data.size < 2) throw MidiError.InvalidMessage(data)
                val data1 = data[1].toInt() and 0x7F
                MidiMessage(messageType, channel, data1, 0, timestamp)
            }
            
            MidiMessageType.PITCH_BEND -> {
                if (data.size < 3) throw MidiError.InvalidMessage(data)
                val lsb = data[1].toInt() and 0x7F
                val msb = data[2].toInt() and 0x7F
                val pitchValue = (msb shl 7) or lsb
                MidiMessage(messageType, channel, pitchValue and 0x7F, (pitchValue shr 7) and 0x7F, timestamp)
            }
            
            MidiMessageType.SYSTEM_EXCLUSIVE -> {
                // SysEx messages can be variable length
                if (data.size < 2 || data.last() != 0xF7.toByte()) {
                    throw MidiError.InvalidMessage(data)
                }
                MidiMessage(messageType, 0, data.size, 0, timestamp)
            }
            
            MidiMessageType.CLOCK, MidiMessageType.START, 
            MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                // System real-time messages are single byte
                MidiMessage(messageType, 0, 0, 0, timestamp)
            }
        }
    }
    
    /**
     * Convert MidiMessage back to raw bytes
     */
    fun messageToBytes(message: MidiMessage): ByteArray {
        return when (message.type) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF,
            MidiMessageType.CONTROL_CHANGE, MidiMessageType.AFTERTOUCH -> {
                byteArrayOf(
                    (message.type.statusByte or message.channel).toByte(),
                    message.data1.toByte(),
                    message.data2.toByte()
                )
            }
            
            MidiMessageType.PROGRAM_CHANGE -> {
                byteArrayOf(
                    (message.type.statusByte or message.channel).toByte(),
                    message.data1.toByte()
                )
            }
            
            MidiMessageType.PITCH_BEND -> {
                val pitchValue = (message.data2 shl 7) or message.data1
                byteArrayOf(
                    (message.type.statusByte or message.channel).toByte(),
                    (pitchValue and 0x7F).toByte(),
                    ((pitchValue shr 7) and 0x7F).toByte()
                )
            }
            
            MidiMessageType.CLOCK, MidiMessageType.START,
            MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                byteArrayOf(message.type.statusByte.toByte())
            }
            
            MidiMessageType.SYSTEM_EXCLUSIVE -> {
                // For SysEx, data1 contains the length
                ByteArray(message.data1) { 0xF0.toByte() } + byteArrayOf(0xF7.toByte())
            }
        }
    }
    
    /**
     * Validate MIDI message data
     */
    fun validateMessage(message: MidiMessage): Boolean {
        return try {
            // Check channel range
            if (message.channel !in 0..15) return false
            
            // Check data ranges based on message type
            when (message.type) {
                MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> {
                    message.data1 in 0..127 && message.data2 in 0..127
                }
                
                MidiMessageType.CONTROL_CHANGE -> {
                    message.data1 in 0..127 && message.data2 in 0..127
                }
                
                MidiMessageType.PROGRAM_CHANGE -> {
                    message.data1 in 0..127
                }
                
                MidiMessageType.PITCH_BEND -> {
                    val pitchValue = (message.data2 shl 7) or message.data1
                    pitchValue in 0..16383
                }
                
                MidiMessageType.AFTERTOUCH -> {
                    message.data1 in 0..127 && message.data2 in 0..127
                }
                
                MidiMessageType.SYSTEM_EXCLUSIVE -> {
                    message.data1 >= 0 // Length should be non-negative
                }
                
                MidiMessageType.CLOCK, MidiMessageType.START,
                MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                    true // System real-time messages don't have data constraints
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if a message is a Note On with velocity > 0
     */
    fun isNoteOn(message: MidiMessage): Boolean {
        return message.type == MidiMessageType.NOTE_ON && message.data2 > 0
    }
    
    /**
     * Check if a message is a Note Off or Note On with velocity 0
     */
    fun isNoteOff(message: MidiMessage): Boolean {
        return message.type == MidiMessageType.NOTE_OFF || 
               (message.type == MidiMessageType.NOTE_ON && message.data2 == 0)
    }
    
    /**
     * Extract note number from Note On/Off message
     */
    fun getNoteNumber(message: MidiMessage): Int? {
        return if (message.type == MidiMessageType.NOTE_ON || message.type == MidiMessageType.NOTE_OFF) {
            message.data1
        } else null
    }
    
    /**
     * Extract velocity from Note On/Off message
     */
    fun getVelocity(message: MidiMessage): Int? {
        return if (message.type == MidiMessageType.NOTE_ON || message.type == MidiMessageType.NOTE_OFF) {
            message.data2
        } else null
    }
    
    /**
     * Extract controller number from Control Change message
     */
    fun getControllerNumber(message: MidiMessage): Int? {
        return if (message.type == MidiMessageType.CONTROL_CHANGE) {
            message.data1
        } else null
    }
    
    /**
     * Extract controller value from Control Change message
     */
    fun getControllerValue(message: MidiMessage): Int? {
        return if (message.type == MidiMessageType.CONTROL_CHANGE) {
            message.data2
        } else null
    }
    
    /**
     * Check if message is a system real-time message
     */
    fun isSystemRealTime(message: MidiMessage): Boolean {
        return message.type in listOf(
            MidiMessageType.CLOCK,
            MidiMessageType.START,
            MidiMessageType.STOP,
            MidiMessageType.CONTINUE
        )
    }
    
    /**
     * Enhanced message type detection with routing information
     */
    fun detectMessageTypeAndTarget(message: MidiMessage): MessageRouting {
        val targetType = when (message.type) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> {
                // Route note messages to pad triggers
                MidiTargetType.PAD_TRIGGER
            }
            MidiMessageType.CONTROL_CHANGE -> {
                // Route CC messages based on controller number
                when (message.data1) {
                    7 -> MidiTargetType.PAD_VOLUME      // Volume CC
                    10 -> MidiTargetType.PAD_PAN        // Pan CC
                    in 16..23 -> MidiTargetType.PAD_TRIGGER  // Pad CCs
                    in 70..79 -> MidiTargetType.EFFECT_PARAMETER  // Effect CCs
                    else -> MidiTargetType.EFFECT_PARAMETER
                }
            }
            MidiMessageType.PROGRAM_CHANGE -> MidiTargetType.EFFECT_PARAMETER
            MidiMessageType.PITCH_BEND -> MidiTargetType.EFFECT_PARAMETER
            MidiMessageType.AFTERTOUCH -> MidiTargetType.PAD_VOLUME
            MidiMessageType.START, MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                MidiTargetType.TRANSPORT_CONTROL
            }
            MidiMessageType.CLOCK -> MidiTargetType.SEQUENCER_TEMPO
            MidiMessageType.SYSTEM_EXCLUSIVE -> MidiTargetType.EFFECT_PARAMETER
        }
        
        return MessageRouting(
            messageType = message.type,
            targetType = targetType,
            priority = getMessagePriority(message.type),
            requiresLowLatency = isLowLatencyMessage(message.type)
        )
    }
    
    /**
     * Advanced message filtering for malformed or invalid messages
     */
    fun filterMessage(data: ByteArray): FilterResult {
        if (data.isEmpty()) {
            return FilterResult.Rejected("Empty message data")
        }
        
        val statusByte = data[0].toInt() and 0xFF
        
        // Check for valid status byte
        if (statusByte < 0x80 && statusByte != 0xF0) {
            return FilterResult.Rejected("Invalid status byte: 0x${statusByte.toString(16)}")
        }
        
        // Check message length based on type
        val expectedLength = getExpectedMessageLength(statusByte)
        if (expectedLength > 0 && data.size < expectedLength) {
            return FilterResult.Rejected("Message too short: expected $expectedLength, got ${data.size}")
        }
        
        // Check for running status (not supported in this implementation)
        if (statusByte < 0x80) {
            return FilterResult.Rejected("Running status not supported")
        }
        
        // Validate data bytes
        for (i in 1 until data.size) {
            val dataByte = data[i].toInt() and 0xFF
            if (dataByte >= 0x80 && statusByte != 0xF0) { // Allow any bytes in SysEx
                return FilterResult.Rejected("Invalid data byte: 0x${dataByte.toString(16)} at position $i")
            }
        }
        
        return FilterResult.Accepted
    }
    
    /**
     * Sanitize potentially malformed message data with enhanced error recovery
     */
    fun sanitizeMessage(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val sanitized = mutableListOf<Byte>()
        
        for (i in data.indices) {
            val byte = data[i]
            val value = byte.toInt() and 0xFF
            
            when (i) {
                0 -> {
                    // Status byte - ensure it's valid
                    if (value >= 0x80 || value == 0xF0) {
                        sanitized.add(byte)
                    } else {
                        // Try to recover by setting MSB
                        sanitized.add((value or 0x80).toByte())
                    }
                }
                else -> {
                    // Data bytes - ensure they're in valid range (0-127) unless SysEx
                    val statusByte = sanitized[0].toInt() and 0xFF
                    if (statusByte == 0xF0) {
                        // SysEx - allow any byte except end with F7
                        sanitized.add(byte)
                    } else {
                        // Regular data byte - mask to 7 bits
                        sanitized.add((value and 0x7F).toByte())
                    }
                }
            }
        }
        
        return sanitized.toByteArray()
    }
    
    /**
     * Get expected message length based on status byte
     */
    private fun getExpectedMessageLength(statusByte: Int): Int {
        return when (statusByte and 0xF0) {
            0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 3  // Note Off/On, Aftertouch, CC, Pitch Bend
            0xC0, 0xD0 -> 2                      // Program Change, Channel Pressure
            0xF0 -> when (statusByte) {
                0xF0 -> -1  // SysEx - variable length
                0xF1 -> 2   // MTC Quarter Frame
                0xF2 -> 3   // Song Position
                0xF3 -> 2   // Song Select
                0xF6 -> 1   // Tune Request
                0xF8, 0xFA, 0xFB, 0xFC, 0xFE, 0xFF -> 1  // System Real-time
                else -> -1
            }
            else -> -1
        }
    }
    
    /**
     * Determine message priority for processing
     */
    private fun getMessagePriority(messageType: MidiMessageType): MessagePriority {
        return when (messageType) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> MessagePriority.HIGH
            MidiMessageType.CLOCK, MidiMessageType.START, MidiMessageType.STOP, MidiMessageType.CONTINUE -> MessagePriority.HIGH
            MidiMessageType.CONTROL_CHANGE, MidiMessageType.PITCH_BEND, MidiMessageType.AFTERTOUCH -> MessagePriority.NORMAL
            MidiMessageType.PROGRAM_CHANGE -> MessagePriority.NORMAL
            MidiMessageType.SYSTEM_EXCLUSIVE -> MessagePriority.LOW
        }
    }
    
    /**
     * Check if message type requires low-latency processing
     */
    private fun isLowLatencyMessage(messageType: MidiMessageType): Boolean {
        return when (messageType) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> true
            MidiMessageType.CLOCK -> true
            else -> false
        }
    }
}

/**
 * Result of message parsing operation
 */
sealed class ParseResult {
    data class Success(val message: MidiMessage) : ParseResult()
    data class Error(val error: MidiError.InvalidMessage, val originalData: ByteArray) : ParseResult()
}

/**
 * Result of message filtering operation
 */
sealed class FilterResult {
    object Accepted : FilterResult()
    data class Rejected(val reason: String) : FilterResult()
}

/**
 * Message routing information for processed messages
 */
data class MessageRouting(
    val messageType: MidiMessageType,
    val targetType: MidiTargetType,
    val priority: MessagePriority,
    val requiresLowLatency: Boolean
)

/**
 * Message priority levels
 */
enum class MessagePriority {
    HIGH,    // Note events, clock, transport
    NORMAL,  // Control changes, pitch bend
    LOW      // System exclusive, program changes
}