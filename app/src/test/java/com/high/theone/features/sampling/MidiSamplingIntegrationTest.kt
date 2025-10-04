package com.high.theone.features.sampling

import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Integration tests for the complete MIDI-to-pad workflow.
 * Tests the interaction between MidiSamplingAdapter and pad triggering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidiSamplingIntegrationTest {

    @Mock
    private lateinit var mockMidiInputProcessor: MidiInputProcessor

    private lateinit var midiSamplingAdapter: MidiSamplingAdapter

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        midiSamplingAdapter = MidiSamplingAdapter(mockMidiInputProcessor)
    }

    @Test
    fun `MIDI adapter initializes and registers handler`() {
        midiSamplingAdapter.initialize()
        
        verify(mockMidiInputProcessor).registerMessageHandler(any(), any())
        verify(mockMidiInputProcessor).startProcessing()
    }

    @Test
    fun `MIDI adapter shuts down properly`() {
        midiSamplingAdapter.initialize()
        midiSamplingAdapter.shutdown()
        
        verify(mockMidiInputProcessor).unregisterMessageHandler(any())
        verify(mockMidiInputProcessor).stopProcessing()
    }

    @Test
    fun `pad configuration update works correctly`() {
        val pads = listOf(
            PadState(
                index = 0,
                midiNote = 36,
                midiChannel = 9,
                hasAssignedSample = true,
                sampleId = "kick"
            ),
            PadState(
                index = 1,
                midiNote = 38,
                midiChannel = 9,
                hasAssignedSample = true,
                sampleId = "snare"
            )
        )
        
        midiSamplingAdapter.updatePadConfiguration(pads)
        
        val stats = midiSamplingAdapter.getProcessingStatistics()
        assertEquals(2, stats.mappedPads)
        assertEquals(2, stats.totalPads)
    }

    @Test
    fun `test pad mapping functionality`() = runTest {
        val pads = listOf(
            PadState(
                index = 0,
                midiNote = 36,
                midiChannel = 9,
                hasAssignedSample = true,
                sampleId = "kick"
            )
        )
        
        midiSamplingAdapter.updatePadConfiguration(pads)
        midiSamplingAdapter.testPadMapping(0, 100)
        
        // The test should trigger the pad mapping logic
        // In a real test, we would verify the event was emitted
    }

    @Test
    fun `complete MIDI workflow integration test`() {
        // Test the complete workflow from MIDI message to pad trigger event
        val pads = listOf(
            PadState(
                index = 0,
                midiNote = 36,
                midiChannel = 9,
                hasAssignedSample = true,
                sampleId = "kick",
                midiVelocitySensitivity = 1.0f
            ),
            PadState(
                index = 1,
                midiNote = 38,
                midiChannel = 9,
                hasAssignedSample = true,
                sampleId = "snare",
                midiVelocitySensitivity = 1.5f
            )
        )
        
        // Verify pad configuration
        pads.forEach { pad ->
            assertTrue("Pad ${pad.index} should be triggerable from MIDI", pad.canTriggerFromMidi)
            assertTrue("Pad ${pad.index} should respond to its MIDI note", 
                pad.respondsToMidiNote(pad.midiNote!!, pad.midiChannel))
        }
        
        // Test MIDI message processing
        val kickMessage = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 36,
            data2 = 100,
            timestamp = System.nanoTime()
        )
        
        val snareMessage = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 38,
            data2 = 80,
            timestamp = System.nanoTime()
        )
        
        // Verify trigger events can be created
        val kickEvent = MidiPadIntegration.createPadTriggerEvent(kickMessage, pads[0])
        assertNotNull("Kick trigger event should be created", kickEvent)
        assertEquals(0, kickEvent!!.padIndex)
        assertEquals(36, kickEvent.midiNote)
        
        val snareEvent = MidiPadIntegration.createPadTriggerEvent(snareMessage, pads[1])
        assertNotNull("Snare trigger event should be created", snareEvent)
        assertEquals(1, snareEvent!!.padIndex)
        assertEquals(38, snareEvent.midiNote)
        
        // Verify velocity conversion with sensitivity
        val expectedSnareVelocity = MidiPadIntegration.convertMidiVelocityToPadVelocity(
            80, 1.5f, VelocityCurve.LINEAR
        )
        assertEquals(expectedSnareVelocity, snareEvent.velocity, 0.001f)
    }

    @Test
    fun `MIDI note off handling works correctly`() {
        val pad = PadState(
            index = 0,
            midiNote = 36,
            midiChannel = 9,
            hasAssignedSample = true,
            sampleId = "kick",
            playbackMode = PlaybackMode.NOTE_ON_OFF
        )
        
        val noteOffMessage = MidiMessage(
            type = MidiMessageType.NOTE_OFF,
            channel = 9,
            data1 = 36,
            data2 = 0,
            timestamp = System.nanoTime()
        )
        
        assertTrue("Pad should respond to note off", 
            MidiPadIntegration.shouldStopPad(noteOffMessage, pad))
        
        // Test NOTE_ON with velocity 0 (equivalent to NOTE_OFF)
        val noteOnZeroVel = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = 9,
            data1 = 36,
            data2 = 0,
            timestamp = System.nanoTime()
        )
        
        assertTrue("Pad should respond to NOTE_ON with velocity 0", 
            MidiPadIntegration.shouldStopPad(noteOnZeroVel, pad))
    }

    @Test
    fun `auto-assign MIDI notes creates proper mapping`() {
        val pads = List(16) { index ->
            PadState(
                index = index,
                hasAssignedSample = true,
                sampleId = "sample$index"
            )
        }
        
        val assignedPads = MidiPadIntegration.autoAssignMidiNotes(pads, channel = 9)
        
        // Verify all pads have MIDI notes
        assignedPads.forEach { pad ->
            assertNotNull("Pad ${pad.index} should have MIDI note", pad.midiNote)
            assertEquals("Pad ${pad.index} should be on channel 9", 9, pad.midiChannel)
            assertTrue("Pad ${pad.index} should be triggerable from MIDI", pad.canTriggerFromMidi)
        }
        
        // Verify standard drum mapping
        assertEquals(36, assignedPads[0].midiNote) // Kick
        assertEquals(38, assignedPads[1].midiNote) // Snare
        assertEquals(42, assignedPads[2].midiNote) // Closed Hi-Hat
        assertEquals(46, assignedPads[3].midiNote) // Open Hi-Hat
    }

    @Test
    fun `velocity curves work correctly`() {
        val testVelocity = 64 // Mid-range velocity
        val normalizedInput = testVelocity / 127f
        
        // Linear curve
        val linear = MidiPadIntegration.convertMidiVelocityToPadVelocity(
            testVelocity, 1.0f, VelocityCurve.LINEAR
        )
        assertEquals(normalizedInput, linear, 0.01f)
        
        // Exponential curve (should be lower for mid-range)
        val exponential = MidiPadIntegration.convertMidiVelocityToPadVelocity(
            testVelocity, 1.0f, VelocityCurve.EXPONENTIAL
        )
        assertTrue("Exponential curve should be lower than linear for mid-range", exponential < linear)
        
        // Logarithmic curve (should be higher for mid-range)
        val logarithmic = MidiPadIntegration.convertMidiVelocityToPadVelocity(
            testVelocity, 1.0f, VelocityCurve.LOGARITHMIC
        )
        assertTrue("Logarithmic curve should be higher than linear for mid-range", logarithmic > linear)
    }
}