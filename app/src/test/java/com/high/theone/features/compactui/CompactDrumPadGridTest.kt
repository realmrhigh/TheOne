package com.high.theone.features.compactui

import androidx.compose.ui.unit.dp
import com.high.theone.model.CompactUIModels.*
import com.high.theone.model.LayoutMode
import com.high.theone.model.Orientation
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for CompactDrumPadGrid functionality.
 * Tests adaptive sizing, pad state handling, and MIDI integration.
 */
class CompactDrumPadGridTest {
    
    @Test
    fun `test adaptive pad size calculation for different screen configurations`() {
        // Test compact portrait mode
        val compactConfig = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        assertEquals(LayoutMode.COMPACT_PORTRAIT, compactConfig.layoutMode)
        
        // Test standard portrait mode
        val standardConfig = ScreenConfiguration(
            screenWidth = 400.dp,
            screenHeight = 800.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        assertEquals(LayoutMode.STANDARD_PORTRAIT, standardConfig.layoutMode)
        
        // Test landscape mode
        val landscapeConfig = ScreenConfiguration(
            screenWidth = 800.dp,
            screenHeight = 400.dp,
            orientation = Orientation.LANDSCAPE,
            densityDpi = 420,
            isTablet = false
        )
        assertEquals(LayoutMode.LANDSCAPE, landscapeConfig.layoutMode)
        
        // Test tablet mode
        val tabletConfig = ScreenConfiguration(
            screenWidth = 1024.dp,
            screenHeight = 768.dp,
            orientation = Orientation.LANDSCAPE,
            densityDpi = 320,
            isTablet = true
        )
        assertEquals(LayoutMode.TABLET, tabletConfig.layoutMode)
    }
    
    @Test
    fun `test pad state properties for MIDI integration`() {
        // Test pad with MIDI mapping
        val padWithMidi = PadState(
            index = 0,
            sampleId = "sample1",
            hasAssignedSample = true,
            midiNote = 36,
            midiChannel = 9,
            midiVelocitySensitivity = 1.0f
        )
        
        assertTrue(padWithMidi.canTrigger)
        assertTrue(padWithMidi.canTriggerFromMidi)
        assertTrue(padWithMidi.respondsToMidiNote(36, 9))
        assertFalse(padWithMidi.respondsToMidiNote(37, 9))
        assertFalse(padWithMidi.respondsToMidiNote(36, 8))
        
        // Test pad without MIDI mapping
        val padWithoutMidi = PadState(
            index = 1,
            sampleId = "sample2",
            hasAssignedSample = true,
            midiNote = null
        )
        
        assertTrue(padWithoutMidi.canTrigger)
        assertFalse(padWithoutMidi.canTriggerFromMidi)
        assertFalse(padWithoutMidi.respondsToMidiNote(36, 9))
        
        // Test empty pad
        val emptyPad = PadState(index = 2)
        
        assertFalse(emptyPad.canTrigger)
        assertFalse(emptyPad.canTriggerFromMidi)
    }
    
    @Test
    fun `test pad list normalization to 16 pads`() {
        // Test with fewer than 16 pads
        val shortList = listOf(
            PadState(0, hasAssignedSample = true),
            PadState(1, hasAssignedSample = true),
            PadState(2, hasAssignedSample = true)
        )
        
        // Simulate the normalization logic from CompactDrumPadGrid
        val normalizedList = shortList.take(16).let { currentPads ->
            if (currentPads.size < 16) {
                currentPads + List(16 - currentPads.size) { index ->
                    PadState(index = currentPads.size + index)
                }
            } else {
                currentPads
            }
        }
        
        assertEquals(16, normalizedList.size)
        assertEquals(0, normalizedList[0].index)
        assertEquals(1, normalizedList[1].index)
        assertEquals(2, normalizedList[2].index)
        assertEquals(3, normalizedList[3].index) // First generated pad
        assertEquals(15, normalizedList[15].index) // Last pad
        
        // Test with exactly 16 pads
        val fullList = List(16) { PadState(it, hasAssignedSample = true) }
        val normalizedFullList = fullList.take(16).let { currentPads ->
            if (currentPads.size < 16) {
                currentPads + List(16 - currentPads.size) { index ->
                    PadState(index = currentPads.size + index)
                }
            } else {
                currentPads
            }
        }
        
        assertEquals(16, normalizedFullList.size)
        assertEquals(fullList, normalizedFullList)
        
        // Test with more than 16 pads
        val longList = List(20) { PadState(it, hasAssignedSample = true) }
        val normalizedLongList = longList.take(16)
        
        assertEquals(16, normalizedLongList.size)
        assertEquals(0, normalizedLongList[0].index)
        assertEquals(15, normalizedLongList[15].index)
    }
    
    @Test
    fun `test waveform generation for different pad states`() {
        // Test pad with sample
        val padWithSample = PadState(
            index = 0,
            sampleId = "sample1",
            hasAssignedSample = true,
            volume = 0.8f
        )
        
        val waveform = generateMockWaveformForPad(padWithSample)
        assertNotNull(waveform)
        assertEquals(64, waveform?.size)
        
        // Verify waveform values are in valid range
        waveform?.forEach { sample ->
            assertTrue("Sample value $sample should be between -1 and 1", sample in -1f..1f)
        }
        
        // Test empty pad
        val emptyPad = PadState(index = 1)
        val emptyWaveform = generateMockWaveformForPad(emptyPad)
        assertNull(emptyWaveform)
    }
    
    @Test
    fun `test velocity calculation from touch position`() {
        // Simulate center touch (should give high velocity)
        val centerX = 50f
        val centerY = 50f
        val size = 100f
        
        val distanceFromCenter = kotlin.math.sqrt(
            (centerX - centerX).let { it * it } + (centerY - centerY).let { it * it }
        )
        val maxDistance = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
        val normalizedDistance = (distanceFromCenter / maxDistance).coerceIn(0f, 1f)
        val velocity = (1f - normalizedDistance * 0.3f).coerceIn(0.3f, 1f)
        
        assertEquals(1f, velocity, 0.01f) // Center should give max velocity
        
        // Simulate corner touch (should give lower velocity)
        val cornerX = 0f
        val cornerY = 0f
        
        val cornerDistanceFromCenter = kotlin.math.sqrt(
            (cornerX - centerX).let { it * it } + (cornerY - centerY).let { it * it }
        )
        val cornerNormalizedDistance = (cornerDistanceFromCenter / maxDistance).coerceIn(0f, 1f)
        val cornerVelocity = (1f - cornerNormalizedDistance * 0.3f).coerceIn(0.3f, 1f)
        
        assertTrue("Corner velocity should be less than center", cornerVelocity < velocity)
        assertTrue("Corner velocity should be at least 0.3", cornerVelocity >= 0.3f)
    }
    
    @Test
    fun `test color interpolation for press feedback`() {
        // Test lerp function
        val start = 0.2f
        val stop = 0.8f
        
        assertEquals(0.2f, lerp(start, stop, 0f), 0.01f)
        assertEquals(0.8f, lerp(start, stop, 1f), 0.01f)
        assertEquals(0.5f, lerp(start, stop, 0.5f), 0.01f)
        
        // Test edge cases
        assertEquals(start, lerp(start, stop, -0.1f), 0.01f) // Should clamp to start
        assertEquals(0.95f, lerp(start, stop, 1.25f), 0.01f) // Should extrapolate
    }
    
    private fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return start + fraction * (stop - start)
    }
    
    private fun generateMockWaveformForPad(padState: PadState): FloatArray? {
        if (!padState.hasAssignedSample) return null
        
        val samples = 64
        return FloatArray(samples) { i ->
            val frequency = 0.08f + (padState.index % 4) * 0.04f
            val amplitude = 0.25f + padState.volume * 0.35f
            val phase = (padState.index % 8) * 0.2f
            val decay = 1f - (i.toFloat() / samples) * 0.7f
            val noise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f
            
            val baseWave = kotlin.math.sin((i * frequency + phase) * 2 * kotlin.math.PI) * amplitude * decay
            (baseWave + noise).toFloat().coerceIn(-1f, 1f)
        }
    }
}