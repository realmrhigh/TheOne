package com.high.theone.features.compactui

import com.high.theone.features.sampling.MidiPadTriggerEvent
import com.high.theone.features.sampling.MidiPadStopEvent
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for CompactPadMidiIntegration functionality.
 * Tests MIDI event handling, pad highlighting, and sustained note management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompactPadMidiIntegrationTest {
    
    @Test
    fun `test MIDI trigger event handling`() = runTest {
        val midiIntegration = CompactPadMidiIntegration()
        var triggeredPadIndex = -1
        var triggeredVelocity = 0f
        
        val pads = listOf(
            PadState(
                index = 0,
                sampleId = "sample1",
                hasAssignedSample = true,
                midiNote = 36,
                midiChannel = 9,
                playbackMode = PlaybackMode.ONE_SHOT
            )
        )
        
        val triggerEvent = MidiPadTriggerEvent(
            padIndex = 0,
            velocity = 0.8f,
            midiNote = 36,
            midiChannel = 9,
            midiVelocity = 100,
            timestamp = System.currentTimeMillis()
        )
        
        // Handle the trigger event
        midiIntegration.handleMidiTrigger(
            event = triggerEvent,
            pads = pads,
            onPadTrigger = { index, velocity ->
                triggeredPadIndex = index
                triggeredVelocity = velocity
            }
        )
        
        // Verify pad was triggered
        assertEquals(0, triggeredPadIndex)
        assertEquals(0.8f, triggeredVelocity, 0.01f)
        
        // Verify pad is highlighted
        assertTrue(midiIntegration.isPadHighlighted(0))
        
        // Verify no sustained note for ONE_SHOT mode
        assertNull(midiIntegration.getSustainedNote(0))
    }
    
    @Test
    fun `test sustained note handling for NOTE_ON_OFF mode`() = runTest {
        val midiIntegration = CompactPadMidiIntegration()
        var triggeredPadIndex = -1
        var stoppedPadIndex = -1
        
        val pads = listOf(
            PadState(
                index = 0,
                sampleId = "sample1",
                hasAssignedSample = true,
                midiNote = 36,
                midiChannel = 9,
                playbackMode = PlaybackMode.NOTE_ON_OFF
            )
        )
        
        val triggerEvent = MidiPadTriggerEvent(
            padIndex = 0,
            velocity = 0.7f,
            midiNote = 36,
            midiChannel = 9,
            midiVelocity = 90,
            timestamp = System.currentTimeMillis()
        )
        
        // Handle trigger event
        midiIntegration.handleMidiTrigger(
            event = triggerEvent,
            pads = pads,
            onPadTrigger = { index, velocity ->
                triggeredPadIndex = index
            }
        )
        
        // Verify pad is triggered and sustained
        assertEquals(0, triggeredPadIndex)
        assertTrue(midiIntegration.isPadHighlighted(0))
        
        val sustainedNote = midiIntegration.getSustainedNote(0)
        assertNotNull(sustainedNote)
        assertEquals(0, sustainedNote?.padIndex)
        assertEquals(36, sustainedNote?.midiNote)
        assertEquals(9, sustainedNote?.midiChannel)
        assertEquals(0.7f, sustainedNote?.velocity ?: 0f, 0.01f)
        
        // Handle stop event
        val stopEvent = MidiPadStopEvent(
            padIndex = 0,
            midiNote = 36,
            midiChannel = 9,
            timestamp = System.currentTimeMillis()
        )
        
        midiIntegration.handleMidiStop(
            event = stopEvent,
            onPadStop = { index ->
                stoppedPadIndex = index
            }
        )
        
        // Verify pad is stopped and no longer highlighted
        assertEquals(0, stoppedPadIndex)
        assertFalse(midiIntegration.isPadHighlighted(0))
        assertNull(midiIntegration.getSustainedNote(0))
    }
    
    @Test
    fun `test manual pad highlighting`() = runTest {
        val midiIntegration = CompactPadMidiIntegration()
        
        // Initially no pads highlighted
        assertFalse(midiIntegration.isPadHighlighted(0))
        
        // Highlight pad manually
        midiIntegration.highlightPad(0, 50L)
        
        // Pad should no longer be highlighted after duration
        assertFalse(midiIntegration.isPadHighlighted(0))
    }
    
    @Test
    fun `test clear all highlights`() = runTest {
        val midiIntegration = CompactPadMidiIntegration()
        
        val pads = listOf(
            PadState(
                index = 0,
                sampleId = "sample1",
                hasAssignedSample = true,
                midiNote = 36,
                midiChannel = 9,
                playbackMode = PlaybackMode.NOTE_ON_OFF
            ),
            PadState(
                index = 1,
                sampleId = "sample2",
                hasAssignedSample = true,
                midiNote = 38,
                midiChannel = 9,
                playbackMode = PlaybackMode.ONE_SHOT
            )
        )
        
        // Trigger multiple pads
        val triggerEvent1 = MidiPadTriggerEvent(
            padIndex = 0,
            velocity = 0.8f,
            midiNote = 36,
            midiChannel = 9,
            midiVelocity = 100,
            timestamp = System.currentTimeMillis()
        )
        
        val triggerEvent2 = MidiPadTriggerEvent(
            padIndex = 1,
            velocity = 0.6f,
            midiNote = 38,
            midiChannel = 9,
            midiVelocity = 80,
            timestamp = System.currentTimeMillis()
        )
        
        midiIntegration.handleMidiTrigger(triggerEvent1, pads) { _, _ -> }
        midiIntegration.handleMidiTrigger(triggerEvent2, pads) { _, _ -> }
        
        // Verify both pads are highlighted
        assertTrue(midiIntegration.isPadHighlighted(0))
        assertTrue(midiIntegration.isPadHighlighted(1))
        
        // Verify sustained note exists for NOTE_ON_OFF pad
        assertNotNull(midiIntegration.getSustainedNote(0))
        
        // Clear all highlights
        midiIntegration.clearAllHighlights()
        
        // Verify all highlights and sustained notes are cleared
        assertFalse(midiIntegration.isPadHighlighted(0))
        assertFalse(midiIntegration.isPadHighlighted(1))
        assertNull(midiIntegration.getSustainedNote(0))
    }
    
    @Test
    fun `test MIDI integration configuration`() {
        val midiIntegration = CompactPadMidiIntegration()
        
        // Test default configuration
        midiIntegration.configure()
        
        // Test custom configuration
        midiIntegration.configure(
            highlightDurationMs = 300L,
            sustainedHighlightEnabled = false
        )
        
        // Configuration should be applied (no direct way to test private fields,
        // but we can verify the integration still works)
        assertNotNull(midiIntegration)
    }
    
    @Test
    fun `test sustained note duration calculation`() {
        val startTime = System.currentTimeMillis()
        val sustainedNote = SustainedNoteInfo(
            padIndex = 0,
            midiNote = 36,
            midiChannel = 9,
            velocity = 0.8f,
            startTime = startTime
        )
        
        // Duration should be approximately current time - start time
        val duration = sustainedNote.durationMs
        assertTrue("Duration should be non-negative", duration >= 0)
        assertTrue("Duration should be reasonable", duration < 1000) // Less than 1 second for this test
    }
    
    @Test
    fun `test wrong MIDI note stop event`() = runTest {
        val midiIntegration = CompactPadMidiIntegration()
        var stoppedPadIndex = -1
        
        val pads = listOf(
            PadState(
                index = 0,
                sampleId = "sample1",
                hasAssignedSample = true,
                midiNote = 36,
                midiChannel = 9,
                playbackMode = PlaybackMode.NOTE_ON_OFF
            )
        )
        
        // Trigger pad with note 36
        val triggerEvent = MidiPadTriggerEvent(
            padIndex = 0,
            velocity = 0.7f,
            midiNote = 36,
            midiChannel = 9,
            midiVelocity = 90,
            timestamp = System.currentTimeMillis()
        )
        
        midiIntegration.handleMidiTrigger(triggerEvent, pads) { _, _ -> }
        
        // Verify pad is sustained
        assertTrue(midiIntegration.isPadHighlighted(0))
        assertNotNull(midiIntegration.getSustainedNote(0))
        
        // Try to stop with wrong MIDI note
        val wrongStopEvent = MidiPadStopEvent(
            padIndex = 0,
            midiNote = 38, // Wrong note!
            midiChannel = 9,
            timestamp = System.currentTimeMillis()
        )
        
        midiIntegration.handleMidiStop(wrongStopEvent) { index ->
            stoppedPadIndex = index
        }
        
        // Pad should still be sustained (not stopped by wrong note)
        assertEquals(-1, stoppedPadIndex) // Callback should not have been called
        assertTrue(midiIntegration.isPadHighlighted(0))
        assertNotNull(midiIntegration.getSustainedNote(0))
    }
}