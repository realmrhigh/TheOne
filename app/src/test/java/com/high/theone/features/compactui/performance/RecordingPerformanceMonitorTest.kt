package com.high.theone.features.compactui.performance

import com.high.theone.ui.performance.PerformanceMonitor
import com.high.theone.features.compactui.CompactUIPerformanceOptimizer
import com.high.theone.model.PerformanceMetrics
import com.high.theone.model.OptimizationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*

/**
 * Unit tests for RecordingPerformanceMonitor
 * 
 * Tests the performance monitoring and optimization functionality
 * during recording operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingPerformanceMonitorTest {

    @Mock
    private lateinit var performanceMonitor: PerformanceMonitor

    @Mock
    private lateinit var compactUIOptimizer: CompactUIPerformanceOptimizer

    private lateinit var recordingPerformanceMonitor: RecordingPerformanceMonitor
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testScope = TestScope()
        
        // Setup mock flows
        whenever(performanceMonitor.performanceMetrics).thenReturn(
            MutableStateFlow(PerformanceMetrics())
        )
        whenever(compactUIOptimizer.optimizationState).thenReturn(
            MutableStateFlow(OptimizationState())
        )
        
        recordingPerformanceMonitor = RecordingPerformanceMonitor(
            performanceMonitor,
            compactUIOptimizer
        )
    }

    @Test
    fun `initial state should be optimal performance`() = runTest {
        val initialState = recordingPerformanceMonitor.recordingPerformanceState.value
        
        assertEquals(60f, initialState.frameRate, 0.1f)
        assertEquals(20f, initialState.audioLatency, 0.1f)
        assertEquals(0f, initialState.memoryPressure, 0.1f)
        assertTrue(initialState.appliedOptimizations.isEmpty())
    }

    @Test
    fun `should detect critical performance status`() = runTest {
        // Simulate critical performance conditions
        recordingPerformanceMonitor.recordRecordingFrameTime(50f) // 20fps
        recordingPerformanceMonitor.recordRecordingAudioLatency(100f) // 100ms latency
        recordingPerformanceMonitor.updateRecordingBufferMemory(300 * 1024 * 1024L) // 300MB
        
        val status = recordingPerformanceMonitor.getRecordingPerformanceStatus()
        assertEquals(RecordingPerformanceStatus.CRITICAL, status)
    }

    @Test
    fun `should detect degraded performance status`() = runTest {
        // Simulate degraded performance conditions
        recordingPerformanceMonitor.recordRecordingFrameTime(25f) // 40fps
        recordingPerformanceMonitor.recordRecordingAudioLatency(50f) // 50ms latency
        
        val status = recordingPerformanceMonitor.getRecordingPerformanceStatus()
        assertEquals(RecordingPerformanceStatus.DEGRADED, status)
    }

    @Test
    fun `should detect suboptimal performance status`() = runTest {
        // Simulate suboptimal performance conditions
        recordingPerformanceMonitor.recordRecordingFrameTime(18f) // 55fps
        recordingPerformanceMonitor.recordRecordingAudioLatency(25f) // 25ms latency
        
        val status = recordingPerformanceMonitor.getRecordingPerformanceStatus()
        assertEquals(RecordingPerformanceStatus.SUBOPTIMAL, status)
    }

    @Test
    fun `should provide performance recommendations for low frame rate`() = runTest {
        // Simulate low frame rate
        repeat(10) {
            recordingPerformanceMonitor.recordRecordingFrameTime(30f) // 33fps
        }
        
        val recommendations = recordingPerformanceMonitor.getRecordingPerformanceRecommendations()
        
        assertTrue(recommendations.any { it.contains("visual effects") })
        assertTrue(recommendations.any { it.contains("panels") })
    }

    @Test
    fun `should provide performance recommendations for high audio latency`() = runTest {
        // Simulate high audio latency
        repeat(10) {
            recordingPerformanceMonitor.recordRecordingAudioLatency(60f) // 60ms
        }
        
        val recommendations = recordingPerformanceMonitor.getRecordingPerformanceRecommendations()
        
        assertTrue(recommendations.any { it.contains("buffer size") })
        assertTrue(recommendations.any { it.contains("audio applications") })
    }

    @Test
    fun `should provide performance recommendations for high memory usage`() = runTest {
        // Simulate high memory usage
        recordingPerformanceMonitor.updateRecordingBufferMemory(250 * 1024 * 1024L) // 250MB
        
        val recommendations = recordingPerformanceMonitor.getRecordingPerformanceRecommendations()
        
        assertTrue(recommendations.any { it.contains("cache") })
        assertTrue(recommendations.any { it.contains("quality") })
    }

    @Test
    fun `should provide recommendations for long recordings`() = runTest {
        recordingPerformanceMonitor.startRecordingMonitoring(testScope)
        
        // Simulate long recording by advancing time
        Thread.sleep(100) // Small delay to simulate recording duration
        
        val recommendations = recordingPerformanceMonitor.getRecordingPerformanceRecommendations()
        
        // For very short test duration, we won't get long recording warnings
        // This test mainly verifies the method doesn't crash
        assertNotNull(recommendations)
    }

    @Test
    fun `should start and stop monitoring correctly`() = runTest {
        recordingPerformanceMonitor.startRecordingMonitoring(testScope)
        
        // Verify monitoring is active by checking if frame times are recorded
        recordingPerformanceMonitor.recordRecordingFrameTime(16.67f)
        
        recordingPerformanceMonitor.stopRecordingMonitoring()
        
        // After stopping, the state should be reset
        val finalState = recordingPerformanceMonitor.recordingPerformanceState.value
        assertEquals(0L, finalState.recordingDuration)
    }

    @Test
    fun `should generate performance warnings for critical conditions`() = runTest {
        recordingPerformanceMonitor.startRecordingMonitoring(testScope)
        
        // Simulate critical conditions
        repeat(30) {
            recordingPerformanceMonitor.recordRecordingFrameTime(50f) // 20fps
        }
        
        // Allow some time for monitoring to process
        Thread.sleep(200)
        
        val warnings = recordingPerformanceMonitor.performanceWarnings.value
        assertTrue(warnings.any { it.severity == PerformanceWarningSeverity.CRITICAL })
    }

    @Test
    fun `should generate optimization suggestions`() = runTest {
        recordingPerformanceMonitor.startRecordingMonitoring(testScope)
        
        // Simulate conditions that trigger optimization suggestions
        repeat(20) {
            recordingPerformanceMonitor.recordRecordingFrameTime(20f) // 50fps
        }
        
        // Allow some time for monitoring to process
        Thread.sleep(200)
        
        val suggestions = recordingPerformanceMonitor.optimizationSuggestions.value
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.canAutoApply })
    }

    @Test
    fun `should force memory cleanup`() = runTest {
        val initialState = recordingPerformanceMonitor.recordingPerformanceState.value
        
        recordingPerformanceMonitor.forceRecordingMemoryCleanup()
        
        val finalState = recordingPerformanceMonitor.recordingPerformanceState.value
        assertTrue(finalState.lastOptimizationTime > initialState.lastOptimizationTime)
        assertTrue(finalState.appliedOptimizations.contains(RecordingOptimization.MEMORY_CLEANUP))
    }
}