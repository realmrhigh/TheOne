package com.high.theone.features.sequencer

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the timing engine implementation
 */
class TimingEngineTest {
    
    private lateinit var timingCalculator: TimingCalculator
    private lateinit var swingCalculator: SwingCalculator
    private lateinit var callbackManager: StepCallbackManager
    private lateinit var timingEngine: PrecisionTimingEngine
    
    @Before
    fun setup() {
        timingCalculator = TimingCalculator()
        swingCalculator = SwingCalculator()
        callbackManager = StepCallbackManager()
        timingEngine = PrecisionTimingEngine(timingCalculator, swingCalculator, callbackManager)
    }
    
    @After
    fun tearDown() {
        timingEngine.release()
    }
    
    @Test
    fun `timing engine starts and stops correctly`() = runBlocking {
        // Initially not playing
        assertFalse(timingEngine.isPlaying.first())
        assertFalse(timingEngine.isPaused.first())
        assertEquals(0, timingEngine.getCurrentStep())
        
        // Start playback
        timingEngine.start(tempo = 120f, swing = 0f, patternLength = 16)
        
        // Should be playing
        assertTrue(timingEngine.isPlaying.first())
        assertFalse(timingEngine.isPaused.first())
        
        // Stop playback
        timingEngine.stop()
        
        // Should be stopped and reset
        assertFalse(timingEngine.isPlaying.first())
        assertFalse(timingEngine.isPaused.first())
        assertEquals(0, timingEngine.getCurrentStep())
    }
    
    @Test
    fun `tempo changes are applied correctly`() = runBlocking {
        timingEngine.start(tempo = 120f)
        
        // Change tempo
        timingEngine.setTempo(140f)
        
        // Tempo should be updated (we can't easily test the actual timing in unit tests)
        // But we can verify the engine is still running
        assertTrue(timingEngine.isPlaying.first())
    }
    
    @Test
    fun `swing changes are applied correctly`() = runBlocking {
        timingEngine.start(tempo = 120f, swing = 0f)
        
        // Change swing
        timingEngine.setSwing(0.25f)
        
        // Engine should still be running
        assertTrue(timingEngine.isPlaying.first())
    }
    
    @Test
    fun `pause and resume work correctly`() = runBlocking {
        timingEngine.start(tempo = 120f)
        assertTrue(timingEngine.isPlaying.first())
        
        // Pause
        timingEngine.pause()
        assertTrue(timingEngine.isPaused.first())
        
        // Resume
        timingEngine.resume()
        assertTrue(timingEngine.isPlaying.first())
        assertFalse(timingEngine.isPaused.first())
    }
    
    @Test
    fun `step callbacks are registered and executed`() = runBlocking {
        var callbackExecuted = false
        var receivedStep = -1
        var receivedTimestamp = 0L
        
        // Register callback
        timingEngine.scheduleStepCallback { step, microTime ->
            callbackExecuted = true
            receivedStep = step
            receivedTimestamp = microTime
        }
        
        // Start timing engine
        timingEngine.start(tempo = 120f)
        
        // Wait a bit for callback to execute
        delay(100)
        
        // Callback should have been executed
        assertTrue("Callback should have been executed", callbackExecuted)
        assertTrue("Step should be valid", receivedStep >= 0)
        assertTrue("Timestamp should be positive", receivedTimestamp > 0)
    }
    
    @Test
    fun `timing statistics are collected`() = runBlocking {
        timingEngine.start(tempo = 120f)
        
        // Let it run briefly
        delay(100)
        
        val stats = timingEngine.getTimingStats()
        
        // Stats should be initialized
        assertTrue("Average jitter should be non-negative", stats.averageJitter >= 0)
        assertTrue("Max jitter should be non-negative", stats.maxJitter >= 0)
        assertTrue("Missed callbacks should be non-negative", stats.missedCallbacks >= 0)
        assertTrue("CPU usage should be between 0 and 1", stats.cpuUsage in 0f..1f)
    }
}

/**
 * Unit tests for the tempo swing controller
 */
class TempoSwingControllerTest {
    
    private lateinit var controller: TempoSwingController
    
    @Before
    fun setup() {
        controller = TempoSwingController()
    }
    
    @Test
    fun `tempo validation works correctly`() {
        assertTrue(controller.isValidTempo(120f))
        assertTrue(controller.isValidTempo(60f))
        assertTrue(controller.isValidTempo(200f))
        
        assertFalse(controller.isValidTempo(50f))
        assertFalse(controller.isValidTempo(250f))
    }
    
    @Test
    fun `swing validation works correctly`() {
        assertTrue(controller.isValidSwing(0f))
        assertTrue(controller.isValidSwing(0.5f))
        assertTrue(controller.isValidSwing(0.75f))
        
        assertFalse(controller.isValidSwing(-0.1f))
        assertFalse(controller.isValidSwing(1f))
    }
    
    @Test
    fun `tempo is clamped to valid range`() = runBlocking {
        controller.setTempo(50f) // Below minimum
        assertEquals(60f, controller.tempo.first())
        
        controller.setTempo(250f) // Above maximum
        assertEquals(200f, controller.tempo.first())
        
        controller.setTempo(120f) // Valid
        assertEquals(120f, controller.tempo.first())
    }
    
    @Test
    fun `swing is clamped to valid range`() = runBlocking {
        controller.setSwing(-0.1f) // Below minimum
        assertEquals(0f, controller.swing.first())
        
        controller.setSwing(1f) // Above maximum
        assertEquals(0.75f, controller.swing.first())
        
        controller.setSwing(0.25f) // Valid
        assertEquals(0.25f, controller.swing.first())
    }
    
    @Test
    fun `swing percentage conversion works correctly`() {
        controller.setSwingPercentage(50) // No swing
        assertEquals(0f, controller.swing.value, 0.01f)
        
        controller.setSwingPercentage(62) // Medium swing
        assertTrue(controller.swing.value > 0f)
        
        assertEquals(50, controller.getSwingPercentage())
    }
    
    @Test
    fun `groove presets are applied correctly`() {
        val presets = controller.getGroovePresets()
        assertTrue(presets.isNotEmpty())
        
        controller.applyGroovePreset("Medium")
        assertTrue(controller.swing.value > 0f)
        
        controller.applyGroovePreset("None")
        assertEquals(0f, controller.swing.value)
    }
}

/**
 * Unit tests for the step callback manager
 */
class StepCallbackManagerTest {
    
    private lateinit var callbackManager: StepCallbackManager
    
    @Before
    fun setup() {
        callbackManager = StepCallbackManager()
    }
    
    @Test
    fun `callbacks can be registered and unregistered`() {
        var callbackExecuted = false
        val callback: (Int, Long) -> Unit = { _, _ -> callbackExecuted = true }
        
        // Register callback
        callbackManager.registerCallback("test", callback)
        assertEquals(1, callbackManager.getRegisteredCallbacks().size)
        
        // Execute callbacks
        callbackManager.executeStepCallbacks(0, System.nanoTime() / 1000L)
        
        // Unregister callback
        callbackManager.unregisterCallback("test")
        assertEquals(0, callbackManager.getRegisteredCallbacks().size)
    }
    
    @Test
    fun `callback stats are tracked correctly`() {
        val callback: (Int, Long) -> Unit = { _, _ -> }
        
        callbackManager.registerCallback("test", callback)
        
        val stats = callbackManager.getCallbackStats()
        assertEquals(1, stats.registeredCallbacks)
        assertEquals(1, stats.enabledCallbacks)
        assertTrue(stats.averageExecutionTime >= 0)
    }
    
    @Test
    fun `latency compensation is applied`() {
        callbackManager.setAudioLatency(10000L) // 10ms
        callbackManager.setSystemLatency(5000L)  // 5ms
        
        assertEquals(15000L, callbackManager.getTotalLatencyCompensation())
    }
    
    @Test
    fun `callbacks can be enabled and disabled`() {
        val callback: (Int, Long) -> Unit = { _, _ -> }
        
        callbackManager.registerCallback("test", callback)
        
        var stats = callbackManager.getCallbackStats()
        assertEquals(1, stats.enabledCallbacks)
        
        callbackManager.setCallbackEnabled("test", false)
        
        stats = callbackManager.getCallbackStats()
        assertEquals(0, stats.enabledCallbacks)
    }
}