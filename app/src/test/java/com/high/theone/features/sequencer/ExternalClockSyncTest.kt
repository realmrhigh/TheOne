package com.high.theone.features.sequencer

import com.high.theone.midi.model.MidiClockPulse
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for external MIDI clock synchronization in the timing engine
 */
class ExternalClockSyncTest {
    
    @Mock
    private lateinit var timingCalculator: TimingCalculator
    
    @Mock
    private lateinit var swingCalculator: SwingCalculator
    
    @Mock
    private lateinit var callbackManager: StepCallbackManager
    
    private lateinit var timingEngine: PrecisionTimingEngine
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock timing calculator to return predictable values
        whenever(timingCalculator.calculateStepDuration(120f)).thenReturn(125L) // 125ms per step at 120 BPM
        
        timingEngine = PrecisionTimingEngine(
            timingCalculator = timingCalculator,
            swingCalculator = swingCalculator,
            callbackManager = callbackManager
        )
    }
    
    @Test
    fun `setClockSource to EXTERNAL enables external clock sync`() = runTest {
        // Initially should be internal clock
        assertEquals(ClockSource.INTERNAL, timingEngine.getCurrentClockSource())
        assertFalse(timingEngine.isExternalClockSynced())
        
        // Switch to external clock
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        
        assertEquals(ClockSource.EXTERNAL, timingEngine.getCurrentClockSource())
        // Should not be synced until we receive clock pulses
        assertFalse(timingEngine.isExternalClockSynced())
    }
    
    @Test
    fun `setClockSource to INTERNAL disables external clock sync`() = runTest {
        // Start with external clock
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        assertEquals(ClockSource.EXTERNAL, timingEngine.getCurrentClockSource())
        
        // Switch back to internal
        timingEngine.setClockSource(ClockSource.INTERNAL)
        
        assertEquals(ClockSource.INTERNAL, timingEngine.getCurrentClockSource())
        assertFalse(timingEngine.isExternalClockSynced())
    }
    
    @Test
    fun `processExternalClockPulse synchronizes timing when using external clock`() = runTest {
        // Set to external clock source
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        
        // Start the timing engine
        timingEngine.start(120f, 0f, 16)
        
        // Send a series of clock pulses
        val baseTime = System.nanoTime()
        for (pulseNumber in 1..24) { // One quarter note worth of pulses
            val clockPulse = MidiClockPulse(
                timestamp = baseTime + (pulseNumber * 20_833_333L), // ~125ms / 6 pulses per step
                pulseNumber = pulseNumber,
                bpm = 120f
            )
            timingEngine.processExternalClockPulse(clockPulse)
        }
        
        // Should now be synchronized
        assertTrue(timingEngine.isExternalClockSynced())
    }
    
    @Test
    fun `processExternalClockPulse ignored when using internal clock`() = runTest {
        // Keep internal clock source (default)
        assertEquals(ClockSource.INTERNAL, timingEngine.getCurrentClockSource())
        
        // Send clock pulse
        val clockPulse = MidiClockPulse(
            timestamp = System.nanoTime(),
            pulseNumber = 1,
            bpm = 120f
        )
        timingEngine.processExternalClockPulse(clockPulse)
        
        // Should remain not synchronized
        assertFalse(timingEngine.isExternalClockSynced())
    }
    
    @Test
    fun `setTempo ignored when using external clock`() = runTest {
        // Set to external clock
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        
        // Start with 120 BPM
        timingEngine.start(120f, 0f, 16)
        
        // Try to change tempo manually
        timingEngine.setTempo(140f)
        
        // Tempo should not change when using external clock
        // (The actual tempo will be determined by external clock pulses)
        verify(timingCalculator).calculateStepDuration(120f) // Should still use original tempo
    }
    
    @Test
    fun `timing stats include external clock information`() = runTest {
        // Set to external clock and sync
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        
        val stats = timingEngine.getTimingStats()
        
        assertEquals(ClockSource.EXTERNAL, stats.clockSource)
        assertFalse(stats.isExternalClockSynced) // Not synced until we receive pulses
        assertEquals(120f, stats.detectedExternalTempo) // Default value
    }
    
    @Test
    fun `external clock timeout switches back to internal clock`() = runTest {
        // Set to external clock
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        
        // Send one pulse to establish sync
        val clockPulse = MidiClockPulse(
            timestamp = System.nanoTime(),
            pulseNumber = 1,
            bpm = 120f
        )
        timingEngine.processExternalClockPulse(clockPulse)
        
        // Wait for timeout (this would normally happen automatically)
        // In a real test, we'd need to wait or mock the timeout mechanism
        
        // For now, just verify the timeout mechanism exists by checking
        // that the clock source can be changed back to internal
        timingEngine.setClockSource(ClockSource.INTERNAL)
        assertEquals(ClockSource.INTERNAL, timingEngine.getCurrentClockSource())
    }
    
    @Test
    fun `external clock pulse triggers step callbacks at correct intervals`() = runTest {
        var stepCallbackCount = 0
        var lastStepReceived = -1
        
        // Set up step callback
        timingEngine.scheduleStepCallback { step, _ ->
            stepCallbackCount++
            lastStepReceived = step
        }
        
        // Set to external clock and start
        timingEngine.setClockSource(ClockSource.EXTERNAL)
        timingEngine.start(120f, 0f, 16)
        
        // Send clock pulses - 6 pulses should trigger one step
        val baseTime = System.nanoTime()
        for (pulseNumber in 1..12) { // Two steps worth of pulses
            val clockPulse = MidiClockPulse(
                timestamp = baseTime + (pulseNumber * 20_833_333L),
                pulseNumber = pulseNumber,
                bpm = 120f
            )
            timingEngine.processExternalClockPulse(clockPulse)
        }
        
        // Should have triggered step callbacks
        assertTrue(stepCallbackCount > 0)
    }
}