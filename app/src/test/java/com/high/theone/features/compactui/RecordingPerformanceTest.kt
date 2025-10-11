package com.high.theone.features.compactui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.compactui.performance.*
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.*
import com.high.theone.ui.performance.PerformanceMonitor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Performance tests for recording operations
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RecordingPerformanceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockAudioEngine: AudioEngineControl

    @Mock
    private lateinit var mockSamplingViewModel: SamplingViewModel

    private val testDispatcher = TestCoroutineDispatcher()
    private lateinit var performanceMonitor: RecordingPerformanceMonitor
    private lateinit var frameRateMonitor: RecordingFrameRateMonitor
    private lateinit var memoryManager: RecordingMemoryManager

    @Before
    fun setup() {
        hiltRule.inject()
        
        performanceMonitor = RecordingPerformanceMonitor()
        frameRateMonitor = RecordingFrameRateMonitor()
        memoryManager = RecordingMemoryManager()
        
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState())
        )
    }

    @Test
    fun testRecordingStartLatency() = runBlockingTest {
        var recordingStarted = false
        val startLatencies = mutableListOf<Long>()

        repeat(10) { iteration ->
            composeTestRule.setContent {
                CompactMainScreen(
                    state = CompactUIState(
                        recordingState = IntegratedRecordingState(canStartRecording = true)
                    ),
                    onRecordingStart = {
                        val latency = measureTimeMillis {
                            recordingStarted = true
                            // Simulate recording start processing
                            Thread.sleep(5) // Minimal processing time
                        }
                        startLatencies.add(latency)
                    },
                    onRecordingStop = {},
                    onPadAssignment = {}
                )
            }

            // Measure time from button press to recording start
            val totalLatency = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Start Recording")
                    .performClick()
                
                composeTestRule.waitUntil(timeoutMillis = 1000) {
                    recordingStarted
                }
            }

            // Recording should start within 100ms
            assertTrue(
                totalLatency < 100,
                "Recording start latency too high: ${totalLatency}ms (iteration $iteration)"
            )
            
            recordingStarted = false
        }

        val averageLatency = startLatencies.average()
        assertTrue(
            averageLatency < 50,
            "Average recording start latency too high: ${averageLatency}ms"
        )
    }

    @Test
    fun testUIResponsivenessDuringRecording() = runBlockingTest {
        val frameRates = mutableListOf<Float>()
        var isRecording by mutableStateOf(false)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = if (isRecording) 5000L else 0L,
                        peakLevel = if (isRecording) 0.6f else 0f,
                        averageLevel = if (isRecording) 0.4f else 0f
                    )
                ),
                onRecordingStart = { isRecording = true },
                onRecordingStop = { isRecording = false },
                onPadAssignment = {}
            )
        }

        // Start recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .performClick()

        // Monitor frame rate during recording
        repeat(30) { // Monitor for 30 frames
            val frameTime = measureTimeMillis {
                composeTestRule.onNodeWithText("00:05")
                    .assertIsDisplayed()
                
                // Simulate frame rendering
                composeTestRule.waitForIdle()
            }
            
            val frameRate = 1000f / frameTime
            frameRates.add(frameRate)
            
            // Each frame should render in less than 16.67ms (60fps)
            assertTrue(
                frameTime < 17,
                "Frame time too high during recording: ${frameTime}ms"
            )
        }

        val averageFrameRate = frameRates.average()
        assertTrue(
            averageFrameRate > 50f,
            "Average frame rate too low during recording: ${averageFrameRate}fps"
        )
    }

    @Test
    fun testMemoryUsageDuringRecording() = runBlockingTest {
        val initialMemory = memoryManager.getCurrentMemoryUsage()
        var recordingMemory = 0L
        var peakMemory = 0L

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(canStartRecording = true)
                ),
                onRecordingStart = {
                    recordingMemory = memoryManager.getCurrentMemoryUsage()
                },
                onRecordingStop = {
                    peakMemory = memoryManager.getPeakMemoryUsage()
                },
                onPadAssignment = {}
            )
        }

        // Start recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .performClick()

        // Simulate recording for a period
        delay(2000)

        // Stop recording
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .performClick()

        // Memory increase should be reasonable (less than 50MB for recording)
        val memoryIncrease = recordingMemory - initialMemory
        assertTrue(
            memoryIncrease < 50 * 1024 * 1024,
            "Memory increase too high during recording: ${memoryIncrease / (1024 * 1024)}MB"
        )

        // Peak memory should not exceed 100MB above initial
        val peakIncrease = peakMemory - initialMemory
        assertTrue(
            peakIncrease < 100 * 1024 * 1024,
            "Peak memory increase too high: ${peakIncrease / (1024 * 1024)}MB"
        )
    }

    @Test
    fun testConcurrentOperationsPerformance() = runBlockingTest {
        var sequencerPlaying = false
        var recordingActive = false

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = recordingActive,
                        durationMs = if (recordingActive) 3000L else 0L
                    ),
                    sequencerState = SequencerState(
                        isPlaying = sequencerPlaying,
                        currentStep = if (sequencerPlaying) 4 else 0
                    )
                ),
                onRecordingStart = { recordingActive = true },
                onRecordingStop = { recordingActive = false },
                onPadAssignment = {},
                onSequencerPlay = { sequencerPlaying = true },
                onSequencerStop = { sequencerPlaying = false }
            )
        }

        // Start sequencer
        composeTestRule.onNodeWithContentDescription("Play Sequencer")
            .performClick()

        // Start recording while sequencer is playing
        val concurrentStartTime = measureTimeMillis {
            composeTestRule.onNodeWithContentDescription("Start Recording")
                .performClick()
            
            composeTestRule.waitUntil(timeoutMillis = 1000) {
                recordingActive
            }
        }

        // Concurrent operations should not significantly impact performance
        assertTrue(
            concurrentStartTime < 200,
            "Concurrent recording start too slow: ${concurrentStartTime}ms"
        )

        // Verify both operations are running
        composeTestRule.onNodeWithText("00:03")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription("Step 4 Active")
            .assertIsDisplayed()
    }

    @Test
    fun testPerformanceOptimizationTriggers() = runBlockingTest {
        val performanceState = MutableStateFlow(PerformanceState())
        
        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(isRecording = true),
                    performanceState = performanceState.value
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onPerformanceOptimization = { optimization ->
                    performanceState.value = performanceState.value.copy(
                        activeOptimizations = performanceState.value.activeOptimizations + optimization
                    )
                }
            )
        }

        // Simulate low frame rate
        performanceState.value = performanceState.value.copy(
            currentFrameRate = 45f,
            averageFrameRate = 47f
        )

        // Verify optimization warning appears
        composeTestRule.onNodeWithText("Performance Warning")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Frame rate below 50fps")
            .assertIsDisplayed()

        // Apply optimization
        composeTestRule.onNodeWithText("Optimize Performance")
            .performClick()

        // Verify optimization is active
        composeTestRule.onNodeWithText("Visual effects reduced")
            .assertIsDisplayed()
    }

    @Test
    fun testLongRecordingPerformance() = runBlockingTest {
        val frameRates = mutableListOf<Float>()
        val memoryUsages = mutableListOf<Long>()
        var recordingDuration = 0L

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = true,
                        durationMs = recordingDuration
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {}
            )
        }

        // Simulate 30-second recording
        repeat(30) { second ->
            recordingDuration = (second + 1) * 1000L
            
            val frameTime = measureTimeMillis {
                composeTestRule.waitForIdle()
            }
            
            val frameRate = 1000f / frameTime
            frameRates.add(frameRate)
            
            val memoryUsage = memoryManager.getCurrentMemoryUsage()
            memoryUsages.add(memoryUsage)
            
            delay(100) // Simulate time passing
        }

        // Performance should remain stable throughout long recording
        val frameRateVariance = frameRates.maxOrNull()!! - frameRates.minOrNull()!!
        assertTrue(
            frameRateVariance < 20f,
            "Frame rate variance too high during long recording: ${frameRateVariance}fps"
        )

        // Memory usage should not continuously increase (no memory leaks)
        val memoryGrowth = memoryUsages.last() - memoryUsages.first()
        assertTrue(
            memoryGrowth < 20 * 1024 * 1024,
            "Memory growth too high during long recording: ${memoryGrowth / (1024 * 1024)}MB"
        )
    }

    @Test
    fun testPerformanceRecoveryAfterOptimization() = runBlockingTest {
        var frameRate by mutableStateOf(45f) // Below threshold
        var optimizationsActive by mutableStateOf(false)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(isRecording = true),
                    performanceState = PerformanceState(
                        currentFrameRate = frameRate,
                        activeOptimizations = if (optimizationsActive) 
                            setOf(OptimizationType.REDUCE_VISUAL_EFFECTS) 
                        else emptySet()
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onPerformanceOptimization = { 
                    optimizationsActive = true
                    frameRate = 58f // Simulate improvement
                }
            )
        }

        // Verify performance warning
        composeTestRule.onNodeWithText("Performance Warning")
            .assertIsDisplayed()

        // Apply optimization
        composeTestRule.onNodeWithText("Optimize Performance")
            .performClick()

        // Verify performance improved
        composeTestRule.onNodeWithText("Performance Optimized")
            .assertIsDisplayed()

        // Simulate performance recovery
        frameRate = 60f
        optimizationsActive = false

        composeTestRule.onNodeWithText("Performance Normal")
            .assertIsDisplayed()
    }
}