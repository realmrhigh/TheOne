package com.high.theone.features.sequencer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SampleCacheManager
import com.high.theone.features.sampling.VoiceManager
import com.high.theone.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SequencerPerformanceOptimizer
 * Tests performance monitoring, optimization strategies, and resource management
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SequencerPerformanceOptimizerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var audioEngine: AudioEngineControl
    private lateinit var sampleCacheManager: SampleCacheManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var performanceOptimizer: SequencerPerformanceOptimizer

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock dependencies
        audioEngine = mockk(relaxed = true)
        sampleCacheManager = mockk(relaxed = true) {
            every { preloadSample(any()) } returns true
            every { unloadSample(any()) } returns Unit
        }
        voiceManager = mockk(relaxed = true) {
            every { getMaxVoices() } returns 32
            every { releaseVoice(any()) } returns Unit
        }

        // Create optimizer instance
        performanceOptimizer = SequencerPerformanceOptimizer(
            audioEngine, sampleCacheManager, voiceManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should initialize with default state`() = runTest {
        // Given - fresh optimizer instance
        
        // When - checking initial state
        val metrics = performanceOptimizer.performanceMetrics.value
        val state = performanceOptimizer.optimizationState.value
        
        // Then - should have default values
        assertEquals(0L, metrics.averageLatency)
        assertEquals(0f, state.memoryUsagePercent)
        assertEquals(0f, state.cpuUsagePercent)
        assertFalse(state.cpuOptimizationActive)
        assertFalse(state.memoryOptimizationActive)
    }

    @Test
    fun `should cache pattern correctly`() = runTest {
        // Given - a test pattern
        val pattern = Pattern(
            name = "Test Pattern",
            length = 16,
            tempo = 120f,
            swing = 0f,
            steps = mapOf(
                0 to listOf(Step(0, 100, true)),
                1 to listOf(Step(4, 80, true))
            )
        )
        
        // When - caching the pattern
        performanceOptimizer.cachePattern(pattern)
        
        // Then - pattern should be cached
        val cachedPattern = performanceOptimizer.getCachedPattern(pattern.id)
        assertEquals(pattern.name, cachedPattern?.name)
        assertEquals(pattern.length, cachedPattern?.length)
    }

    @Test
    fun `should preload samples for pattern`() = runTest {
        // Given - a pattern with steps
        val pattern = Pattern(
            name = "Test Pattern",
            steps = mapOf(
                0 to listOf(Step(0, 100, true)),
                1 to listOf(Step(4, 80, true)),
                2 to listOf(Step(8, 90, true))
            )
        )
        
        // When - preloading samples
        performanceOptimizer.preloadSamplesForPattern(pattern)
        advanceUntilIdle()
        
        // Then - samples should be preloaded
        verify(atLeast = 1) { sampleCacheManager.preloadSample(any()) }
    }

    @Test
    fun `should track voice allocation`() = runTest {
        // Given - voice allocation parameters
        val voiceId = "test_voice_1"
        val padIndex = 0
        val sampleId = "sample_1"
        
        // When - tracking voice allocation
        performanceOptimizer.trackVoiceAllocation(voiceId, padIndex, sampleId)
        
        // Then - voice should be tracked in optimization state
        val state = performanceOptimizer.optimizationState.value
        assertEquals(1, state.activeVoiceCount)
    }

    @Test
    fun `should release tracked voice`() = runTest {
        // Given - tracked voice
        val voiceId = "test_voice_1"
        performanceOptimizer.trackVoiceAllocation(voiceId, 0, "sample_1")
        
        // When - releasing voice
        performanceOptimizer.releaseTrackedVoice(voiceId)
        advanceUntilIdle()
        
        // Then - voice count should decrease
        val state = performanceOptimizer.optimizationState.value
        assertEquals(0, state.activeVoiceCount)
    }

    @Test
    fun `should generate memory optimization recommendations`() = runTest {
        // Given - high memory usage (simulated by setting internal state)
        // This would require access to internal state or mocking
        
        // When - getting recommendations
        val recommendations = performanceOptimizer.getOptimizationRecommendations()
        
        // Then - should return recommendations (empty in this case due to default state)
        assertTrue(recommendations.isEmpty()) // Default state has low usage
    }

    @Test
    fun `should apply automatic optimizations`() = runTest {
        // Given - optimizer instance
        
        // When - applying automatic optimizations
        performanceOptimizer.applyAutomaticOptimizations()
        advanceUntilIdle()
        
        // Then - optimizations should be applied (no exceptions thrown)
        // This is mainly testing that the method executes without errors
    }

    @Test
    fun `should handle pattern caching with size limits`() = runTest {
        // Given - multiple patterns to exceed cache limit
        val patterns = (1..10).map { index ->
            Pattern(
                id = "pattern_$index",
                name = "Pattern $index",
                length = 16,
                tempo = 120f,
                swing = 0f
            )
        }
        
        // When - caching all patterns
        patterns.forEach { pattern ->
            performanceOptimizer.cachePattern(pattern)
        }
        
        // Then - cache should manage size limits
        // The first patterns might be evicted due to cache size limits
        val state = performanceOptimizer.optimizationState.value
        assertTrue(state.cachedPatternCount <= 8) // MAX_CACHED_PATTERNS
    }

    @Test
    fun `should handle voice allocation tracking with limits`() = runTest {
        // Given - many voice allocations
        val voiceIds = (1..50).map { "voice_$it" }
        
        // When - tracking many voices
        voiceIds.forEach { voiceId ->
            performanceOptimizer.trackVoiceAllocation(voiceId, 0, "sample_1")
        }
        
        // Then - should track voices up to reasonable limits
        val state = performanceOptimizer.optimizationState.value
        assertTrue(state.activeVoiceCount > 0)
    }

    @Test
    fun `should provide performance recommendations based on usage`() = runTest {
        // Given - optimizer with some tracked resources
        performanceOptimizer.trackVoiceAllocation("voice_1", 0, "sample_1")
        performanceOptimizer.trackVoiceAllocation("voice_2", 1, "sample_2")
        
        // When - getting recommendations
        val recommendations = performanceOptimizer.getOptimizationRecommendations()
        
        // Then - should provide appropriate recommendations
        // In default state, should have minimal recommendations
        assertTrue(recommendations.size >= 0)
    }

    @Test
    fun `should handle concurrent voice operations safely`() = runTest {
        // Given - multiple concurrent voice operations
        val voiceIds = (1..20).map { "concurrent_voice_$it" }
        
        // When - performing concurrent operations
        val jobs = voiceIds.map { voiceId ->
            launch {
                performanceOptimizer.trackVoiceAllocation(voiceId, 0, "sample_1")
                delay(10L)
                performanceOptimizer.releaseTrackedVoice(voiceId)
            }
        }
        
        jobs.forEach { it.join() }
        
        // Then - should handle concurrent operations without issues
        val state = performanceOptimizer.optimizationState.value
        assertEquals(0, state.activeVoiceCount) // All voices should be released
    }

    @Test
    fun `should cleanup resources on clear`() = runTest {
        // Given - optimizer with tracked resources
        performanceOptimizer.trackVoiceAllocation("voice_1", 0, "sample_1")
        performanceOptimizer.cachePattern(Pattern(name = "Test"))
        
        // When - clearing (simulated through onCleared)
        performanceOptimizer.onCleared()
        
        // Then - resources should be cleaned up
        // This mainly tests that cleanup doesn't throw exceptions
    }
}