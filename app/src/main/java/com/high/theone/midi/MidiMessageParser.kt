package com.high.theone.midi

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType

/**
 * MIDI message parsing and validation utilities
 */
object MidiMessageParser {
    
    /**
     * Parse raw MIDI bytes into a MidiMessage
     */
    fun parseMessage(data: ByteArray, timestamp: Long = System.nanoTime()): MidiMessage {
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
     * Sanitize potentially malformed message data
     */
    fun sanitizeMessage(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        return data.map { byte ->
            val value = byte.toInt() and 0xFF
            when {
                // Status byte - keep as is
                value >= 0x80 -> byte
                // Data byte - ensure it's in valid range (0-127)
                else -> (value and 0x7F).toByte()
            }
        }.toByteArray()
    }
}