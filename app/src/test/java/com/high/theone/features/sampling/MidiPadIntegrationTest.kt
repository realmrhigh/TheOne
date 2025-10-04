package com.high.theone.features.sampling

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MIDI pad integration functionality.
 * Tests MIDI-to-pad mapping, velocity conversion, and event generation.
 */
class MidiPadIntegrationTest {

    @Test
    fun `convertMidiVelocityToPadVelocity converts correctly with linear curve`() {
        // Test linear conversion
        val result1 = MidiPadIntegration.convertMidiVelocityToPadVelocity(0, 1.0f, VelocityCurve.LINEAR)
        assertEquals(0.0f, result1, 0.001f)
        
        val result2 = MidiPadIntegration.convertMidiVelocityToPadVelocity(127, 1.0f, VelocityCurve.LINEAR)
        assertEquals(1.0f, result2, 0.001f)
        
        val result3 = MidiPadIntegration.convertMidiVelocityToPadVelocity(64, 1.0f, VelocityCurve.LINEAR)
        assertEquals(0.504f, result3, 0.01f) // 64/127 ≈ 0.504
    }

    @Test
    fun `convertMidiVelocityToPadVelocity applies sensitivity multiplier`() {
        // Test with 2x sensitivity
        val result1 = MidiPadIntegration.convertMidiVelocityToPadVelocity(64, 2.0f, VelocityCurve.LINEAR)
        assertEquals(1.0f, result1, 0.001f) // Should be clamped to 1.0
        
        // Test with 0.5x sensitivity
        val result2 = MidiPadIntegration.convertMidiVelocityToPadVelocity(127, 0.5f, VelocityCurve.LINEAR)
        assertEquals(0.5f, result2, 0.001f)
    }

    @Test
    fun `convertMidiVelocityToPadVelocity handles exponential curve`() {
        val result = MidiPadIntegration.convertMidiVelocityToPadVelocity(64, 1.0f, VelocityCurve.EXPONENTIAL)
        val expected = (64f / 127f).let { it * it } // Square of normalized value
        assertEquals(expected, result, 0.01f)
    }

    @Test
    fun `findPadForMidiNote finds correct pad`() {
        val pads = listOf(
            PadState(index = 0, midiNote = 36, midiChannel = 9, hasAssignedSample = true, sampleId = "sample1"),
            PadState(index = 1, midiNote = 38, midiChannel = 9, hasAssignedSample = true, sampleId = "sample2"),
            PadState(index = 2, midiNote = 42, midiChannel = 9, hasAssignedSample = true, sampleId = "sample3")
        )
        
        val result1 = MidiPadIntegration.findPadForMidiNote(pads, 36, 9)
        assertEquals(0, result1)
        
        val result2 = MidiPadIntegration.findPadForMidiNote(pads, 38, 9)
        assertEquals(1, result2)
        
        val result3 = MidiPadIntegration.findPadForMidiNote(pads, 99, 9)
        assertNull(result3) // No pad mapped to note 99
        
        val result4 = MidiPadIntegration.findPadForMidiNote(pads, 36, 0)
        assertNull(result4) // Wrong channel
    }

    @Test
    fun `respondsToMidiNote works correctly`() {
        val pad = PadState(
            index = 0,
            midiNote = 36,
            midiChannel = 9,
            hasAssignedSample = true,
            sampleId = "sample1",
            acceptsAllChannels = false
        )
        
        assertTrue(pad.respondsToMidiNote(36, 9))
        assertFalse(pad.respondsToMidiNote(36, 0)) // Wrong channel
        assertFalse(pad.respondsToMidiNote(38, 9)) // Wrong note
        
        // Test with acceptsAllChannels = true
        val padAllChannels = pad.copy(acceptsAllChannels = true)
        assertTrue(padAllChannels.respondsToMidiNote(36, 0))
        assertTrue(padAllChannels.respondsToMidiNote(36, 15))
    }

    @Test
    fun `shouldTriggerPad works for NOTE_ON messages`() {
        val pad = PadState(
            index = 0,
            midiNote = 36,
            midiChannel = 9,
            hasAssignedSample = true,
            sampleId = "sample1"
        )
        
        val noteOnMessage = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 36,
            data2 = 100,
            timestamp = System.nanoTime()
        )
        
        assertTrue(MidiPadIntegration.shouldTriggerPad(noteOnMessage, pad))
        
        // Test with velocity 0 (should not trigger)
        val noteOnZeroVelocity = noteOnMessage.copy(data2 = 0)
        assertFalse(MidiPadIntegration.shouldTriggerPad(noteOnZeroVelocity, pad))
        
        // Test with wrong note
        val wrongNote = noteOnMessage.copy(data1 = 38)
        assertFalse(MidiPadIntegration.shouldTriggerPad(wrongNote, pad))
    }

    @Test
    fun `shouldStopPad works for NOTE_OFF messages`() {
        val pad = PadState(
            index = 0,
            midiNote = 36,
            midiChannel = 9,
            hasAssignedSample = true,
            sampleId = "sample1"
        )
        
        val noteOffMessage = MidiMessage(
            type = MidiMessageType.NOTE_OFF,
            channel = 9,
            data1 = 36,
            data2 = 0,
            timestamp = System.nanoTime()
        )
        
        assertTrue(MidiPadIntegration.shouldStopPad(noteOffMessage, pad))
        
        // Test NOTE_ON with velocity 0 (equivalent to NOTE_OFF)
        val noteOnZeroVelocity = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 36,
            data2 = 0,
            timestamp = System.nanoTime()
        )
        
        assertTrue(MidiPadIntegration.shouldStopPad(noteOnZeroVelocity, pad))
    }

    @Test
    fun `createPadTriggerEvent creates correct event`() {
        val pad = PadState(
            index = 5,
            midiNote = 42,
            midiChannel = 9,
            hasAssignedSample = true,
            sampleId = "sample1",
            midiVelocitySensitivity = 1.5f
        )
        
        val midiMessage = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 42,
            data2 = 80,
            timestamp = 12345L
        )
        
        val event = MidiPadIntegration.createPadTriggerEvent(midiMessage, pad)
        
        assertNotNull(event)
        assertEquals(5, event!!.padIndex)
        assertEquals(42, event.midiNote)
        assertEquals(9, event.midiChannel)
        assertEquals(80, event.midiVelocity)
        assertEquals(12345L, event.timestamp)
        
        // Velocity should be converted with sensitivity
        val expectedVelocity = MidiPadIntegration.convertMidiVelocityToPadVelocity(80, 1.5f)
        assertEquals(expectedVelocity, event.velocity, 0.001f)
    }

    @Test
    fun `getStandardDrumMidiNote returns correct notes`() {
        assertEquals(36, MidiPadIntegration.getStandardDrumMidiNote(0)) // Kick
        assertEquals(38, MidiPadIntegration.getStandardDrumMidiNote(1)) // Snare
        assertEquals(42, MidiPadIntegration.getStandardDrumMidiNote(2)) // Closed Hi-Hat
        assertEquals(46, MidiPadIntegration.getStandardDrumMidiNote(3)) // Open Hi-Hat
    }

    @Test
    fun `autoAssignMidiNotes assigns notes to all pads`() {
        val pads = List(16) { index ->
            PadState(index = index, hasAssignedSample = true, sampleId = "sample$index")
        }
        
        val assignedPads = MidiPadIntegration.autoAssignMidiNotes(pads, channel = 9)
        
        assertEquals(16, assignedPads.size)
        
        // Check that all pads have MIDI notes assigned
        assignedPads.forEach { pad ->
            assertNotNull("Pad ${pad.index} should have MIDI note assigned", pad.midiNote)
            assertEquals("Pad ${pad.index} should be on channel 9", 9, pad.midiChannel)
            assertFalse("Pad ${pad.index} should not accept all channels", pad.acceptsAllChannels)
        }
        
        // Check specific assignments
        assertEquals(36, assignedPads[0].midiNote) // Kick
        assertEquals(38, assignedPads[1].midiNote) // Snare
        assertEquals(42, assignedPads[2].midiNote) // Closed Hi-Hat
    }

    @Test
    fun `canTriggerFromMidi property works correctly`() {
        // Pad with sample and MIDI note should be triggerable
        val triggerablePad = PadState(
            index = 0,
            hasAssignedSample = true,
            sampleId = "sample1",
            midiNote = 36,
            isEnabled = true,
            isLoading = false
        )
        assertTrue(triggerablePad.canTriggerFromMidi)
        
        // Pad without MIDI note should not be triggerable from MIDI
        val noMidiPad = triggerablePad.copy(midiNote = null)
        assertFalse(noMidiPad.canTriggerFromMidi)
        
        // Pad without sample should not be triggerable
        val noSamplePad = triggerablePad.copy(hasAssignedSample = false, sampleId = null)
        assertFalse(noSamplePad.canTriggerFromMidi)
        
        // Disabled pad should not be triggerable
        val disabledPad = triggerablePad.copy(isEnabled = false)
        assertFalse(disabledPad.canTriggerFromMidi)
        
        // Loading pad should not be triggerable
        val loadingPad = triggerablePad.copy(isLoading = true)
        assertFalse(loadingPad.canTriggerFromMidi)
    }
}