package com.high.theone.features.sequencer

import com.high.theone.audio.AudioEngineControl
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SequencerAudioEngineIntegrationTest {
    
    private lateinit var mockAudioEngine: AudioEngineControl
    private lateinit var sequencerAudioEngine: SequencerAudioEngineAdapter
    
    @Before
    fun setup() {
        mockAudioEngine = mockk<AudioEngineControl>(relaxed = true)
        sequencerAudioEngine = SequencerAudioEngineAdapter(mockAudioEngine)
    }
    
    @Test
    fun `scheduleStepTrigger should delegate to audio engine`() = runTest {
        // Arrange
        val padIndex = 0
        val velocity = 0.8f
        val timestamp = 1000000L // 1 second in microseconds
        
        coEvery { mockAudioEngine.scheduleStepTrigger(any(), any(), any()) } returns true
        
        // Act
        val result = sequencerAudioEngine.scheduleStepTrigger(padIndex, velocity, timestamp)
        
        // Assert
        assertTrue(result)
        coVerify { mockAudioEngine.scheduleStepTrigger(padIndex, velocity, timestamp) }
    }
    
    @Test
    fun `setSequencerTempo should delegate to audio engine`() = runTest {
        // Arrange
        val bpm = 140f
        
        // Act
        sequencerAudioEngine.setSequencerTempo(bpm)
        
        // Assert
        coVerify { mockAudioEngine.setSequencerTempo(bpm) }
    }
    
    @Test
    fun `schedulePatternTriggers should clear events and schedule all triggers`() = runTest {
        // Arrange
        val triggers = listOf(
            StepTrigger(0, 0.8f, 1000000L, 0, "pattern1"),
            StepTrigger(1, 0.6f, 1500000L, 4, "pattern1"),
            StepTrigger(2, 1.0f, 2000000L, 8, "pattern1")
        )
        
        coEvery { mockAudioEngine.scheduleStepTrigger(any(), any(), any()) } returns true
        
        // Act
        val result = sequencerAudioEngine.schedulePatternTriggers(triggers)
        
        // Assert
        assertTrue(result)
        coVerify { mockAudioEngine.clearScheduledEvents() }
        coVerify(exactly = 3) { mockAudioEngine.scheduleStepTrigger(any(), any(), any()) }
    }
    
    @Test
    fun `calculateTriggerTime should compensate for latency`() = runTest {
        // Arrange
        val stepTime = 1000000L
        val lookahead = 5000L
        val latency = 10000L
        
        coEvery { mockAudioEngine.getAudioLatencyMicros() } returns latency
        
        // Act
        val result = sequencerAudioEngine.calculateTriggerTime(stepTime, lookahead)
        
        // Assert
        val expected = stepTime - latency + lookahead
        assertEquals(expected, result)
    }
    
    @Test
    fun `setSequencerMode should enable high precision when enabled`() = runTest {
        // Act
        sequencerAudioEngine.setSequencerMode(true)
        
        // Assert
        coVerify { mockAudioEngine.setHighPrecisionMode(true) }
    }
    
    @Test
    fun `setSequencerMode should clear events when disabled`() = runTest {
        // Act
        sequencerAudioEngine.setSequencerMode(false)
        
        // Assert
        coVerify { mockAudioEngine.clearScheduledEvents() }
    }
    
    @Test
    fun `getSequencerPerformanceMetrics should return metrics from audio engine`() = runTest {
        // Arrange
        val mockStats = mapOf(
            "averageLatency" to 5000.0,
            "maxLatency" to 10000.0,
            "minLatency" to 2000.0,
            "jitter" to 8000.0,
            "missedTriggers" to 2.0,
            "scheduledTriggers" to 16.0,
            "cpuUsage" to 25.5,
            "memoryUsage" to 1024000.0,
            "bufferUnderruns" to 0.0
        )
        
        coEvery { mockAudioEngine.getTimingStatistics() } returns mockStats
        
        // Act
        val result = sequencerAudioEngine.getSequencerPerformanceMetrics()
        
        // Assert
        assertEquals(5000L, result.averageLatency)
        assertEquals(10000L, result.maxLatency)
        assertEquals(2000L, result.minLatency)
        assertEquals(8000L, result.jitter)
        assertEquals(2, result.missedTriggers)
        assertEquals(16, result.scheduledTriggers)
        assertEquals(25.5f, result.cpuUsage)
        assertEquals(1024000L, result.memoryUsage)
        assertEquals(0, result.bufferUnderruns)
    }
}