package com.high.theone.midi

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import org.junit.Test
import org.junit.Assert.*

class MidiMessageParserTest {
    
    @Test
    fun `parseMessage should parse Note On correctly`() {
        val data = byteArrayOf(0x90.toByte(), 0x40, 0x7F) // Note On, channel 0, note 64, velocity 127
        val message = MidiMessageParser.parseMessage(data, 1000L)
        
        assertEquals(MidiMessageType.NOTE_ON, message.type)
        assertEquals(0, message.channel)
        assertEquals(64, message.data1) // note number
        assertEquals(127, message.data2) // velocity
        assertEquals(1000L, message.timestamp)
    }
    
    @Test
    fun `parseMessage should parse Note Off correctly`() {
        val data = byteArrayOf(0x81.toByte(), 0x40, 0x00) // Note Off, channel 1, note 64, velocity 0
        val message = MidiMessageParser.parseMessage(data, 2000L)
        
        assertEquals(MidiMessageType.NOTE_OFF, message.type)
        assertEquals(1, message.channel)
        assertEquals(64, message.data1)
        assertEquals(0, message.data2)
        assertEquals(2000L, message.timestamp)
    }
    
    @Test
    fun `parseMessage should parse Control Change correctly`() {
        val data = byteArrayOf(0xB2.toByte(), 0x07, 0x64) // CC, channel 2, controller 7, value 100
        val message = MidiMessageParser.parseMessage(data, 3000L)
        
        assertEquals(MidiMessageType.CONTROL_CHANGE, message.type)
        assertEquals(2, message.channel)
        assertEquals(7, message.data1) // controller number
        assertEquals(100, message.data2) // controller value
    }
    
    @Test
    fun `parseMessage should parse Program Change correctly`() {
        val data = byteArrayOf(0xC3.toByte(), 0x10) // Program Change, channel 3, program 16
        val message = MidiMessageParser.parseMessage(data, 4000L)
        
        assertEquals(MidiMessageType.PROGRAM_CHANGE, message.type)
        assertEquals(3, message.channel)
        assertEquals(16, message.data1) // program number
        assertEquals(0, message.data2) // unused for program change
    }
    
    @Test
    fun `parseMessage should parse system real-time messages correctly`() {
        val clockData = byteArrayOf(0xF8.toByte())
        val clockMessage = MidiMessageParser.parseMessage(clockData, 5000L)
        
        assertEquals(MidiMessageType.CLOCK, clockMessage.type)
        assertEquals(0, clockMessage.channel)
        assertEquals(0, clockMessage.data1)
        assertEquals(0, clockMessage.data2)
        
        val startData = byteArrayOf(0xFA.toByte())
        val startMessage = MidiMessageParser.parseMessage(startData, 6000L)
        assertEquals(MidiMessageType.START, startMessage.type)
    }
    
    @Test(expected = MidiError.InvalidMessage::class)
    fun `parseMessage should throw on empty data`() {
        MidiMessageParser.parseMessage(byteArrayOf())
    }
    
    @Test(expected = MidiError.InvalidMessage::class)
    fun `parseMessage should throw on invalid status byte`() {
        val data = byteArrayOf(0x70, 0x40, 0x7F) // Invalid status byte
        MidiMessageParser.parseMessage(data)
    }
    
    @Test(expected = MidiError.InvalidMessage::class)
    fun `parseMessage should throw on insufficient data for Note On`() {
        val data = byteArrayOf(0x90.toByte(), 0x40) // Missing velocity byte
        MidiMessageParser.parseMessage(data)
    }
    
    @Test
    fun `messageToBytes should convert Note On correctly`() {
        val message = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        val bytes = MidiMessageParser.messageToBytes(message)
        
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x40, 0x7F), bytes)
    }
    
    @Test
    fun `messageToBytes should convert Control Change correctly`() {
        val message = MidiMessage(MidiMessageType.CONTROL_CHANGE, 2, 7, 100, 2000L)
        val bytes = MidiMessageParser.messageToBytes(message)
        
        assertArrayEquals(byteArrayOf(0xB2.toByte(), 0x07, 0x64), bytes)
    }
    
    @Test
    fun `validateMessage should return true for valid messages`() {
        val validMessage = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        assertTrue(MidiMessageParser.validateMessage(validMessage))
    }
    
    @Test
    fun `validateMessage should return false for invalid channel`() {
        val invalidMessage = MidiMessage(MidiMessageType.NOTE_ON, 16, 64, 127, 1000L)
        assertFalse(MidiMessageParser.validateMessage(invalidMessage))
    }
    
    @Test
    fun `validateMessage should return false for invalid data values`() {
        val invalidMessage = MidiMessage(MidiMessageType.NOTE_ON, 0, 128, 127, 1000L)
        assertFalse(MidiMessageParser.validateMessage(invalidMessage))
    }
    
    @Test
    fun `isNoteOn should identify Note On messages correctly`() {
        val noteOn = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        val noteOnZeroVel = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 0, 1000L)
        val noteOff = MidiMessage(MidiMessageType.NOTE_OFF, 0, 64, 0, 1000L)
        
        assertTrue(MidiMessageParser.isNoteOn(noteOn))
        assertFalse(MidiMessageParser.isNoteOn(noteOnZeroVel)) // Note On with velocity 0 is treated as Note Off
        assertFalse(MidiMessageParser.isNoteOn(noteOff))
    }
    
    @Test
    fun `isNoteOff should identify Note Off messages correctly`() {
        val noteOn = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        val noteOnZeroVel = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 0, 1000L)
        val noteOff = MidiMessage(MidiMessageType.NOTE_OFF, 0, 64, 0, 1000L)
        
        assertFalse(MidiMessageParser.isNoteOff(noteOn))
        assertTrue(MidiMessageParser.isNoteOff(noteOnZeroVel)) // Note On with velocity 0 is treated as Note Off
        assertTrue(MidiMessageParser.isNoteOff(noteOff))
    }
    
    @Test
    fun `getNoteNumber should extract note number correctly`() {
        val noteMessage = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        val ccMessage = MidiMessage(MidiMessageType.CONTROL_CHANGE, 0, 7, 100, 1000L)
        
        assertEquals(64, MidiMessageParser.getNoteNumber(noteMessage))
        assertNull(MidiMessageParser.getNoteNumber(ccMessage))
    }
    
    @Test
    fun `getControllerNumber should extract controller number correctly`() {
        val ccMessage = MidiMessage(MidiMessageType.CONTROL_CHANGE, 0, 7, 100, 1000L)
        val noteMessage = MidiMessage(MidiMessageType.NOTE_ON, 0, 64, 127, 1000L)
        
        assertEquals(7, MidiMessageParser.getControllerNumber(ccMessage))
        assertNull(MidiMessageParser.getControllerNumber(noteMessage))
    }
    
    @Test
    fun `sanitizeMessage should clean malformed data`() {
        val malformedData = byteArrayOf(0x90.toByte(), 0xFF.toByte(), 0x80.toByte())
        val sanitized = MidiMessageParser.sanitizeMessage(malformedData)
        
        // Status byte should remain unchanged, data bytes should be masked to 7 bits
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x7F, 0x00), sanitized)
    }
}