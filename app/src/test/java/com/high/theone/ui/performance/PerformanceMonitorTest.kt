package com.high.theone.ui.performance

import com.high.theone.model.PerformanceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for PerformanceMonitor
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceMonitorTest {
    
    private lateinit var performanceMonitor: PerformanceMonitor
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        performanceMonitor = PerformanceMonitor()
    }
    
    @Test
    fun `initial performance mode is balanced`() {
        assertEquals(PerformanceMode.BALANCED, performanceMonitor.performanceMode.value)
    }
    
    @Test
    fun `setPerformanceMode updates mode correctly`() {
        performanceMonitor.setPerformanceMode(PerformanceMode.HIGH_PERFORMANCE)
        assertEquals(PerformanceMode.HIGH_PERFORMANCE, performanceMonitor.performanceMode.value)
        
        performanceMonitor.setPerformanceMode(PerformanceMode.BATTERY_SAVER)
        assertEquals(PerformanceMode.BATTERY_SAVER, performanceMonitor.performanceMode.value)
    }
    
    @Test
    fun `updateAudioLatency updates metrics correctly`() {
        val testLatency = 15.5f
        performanceMonitor.updateAudioLatency(testLatency)
        
        assertEquals(testLatency, performanceMonitor.performanceMetrics.value.audioLatency, 0.01f)
    }
    
    @Test
    fun `recordFrameTime can be called without errors`() {
        // Should not throw any exceptions
        performanceMonitor.recordFrameTime()
        performanceMonitor.recordFrameTime()
        performanceMonitor.recordFrameTime()
    }
    
    @Test
    fun `getPerformanceRecommendations returns empty list initially`() {
        val recommendations = performanceMonitor.getPerformanceRecommendations()
        assertTrue("Initial recommendations should be empty", recommendations.isEmpty())
    }
    
    @Test
    fun `startMonitoring and stopMonitoring work correctly`() = runTest {
        // Should not throw exceptions
        performanceMonitor.startMonitoring(this)
        performanceMonitor.stopMonitoring()
    }
}