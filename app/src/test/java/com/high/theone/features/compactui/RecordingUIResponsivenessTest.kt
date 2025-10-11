package com.high.theone.features.compactui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Tests for UI responsiveness during recording operations
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RecordingUIResponsivenessTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testButtonResponseTimesDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(false)
        val responseTimes = mutableListOf<Long>()

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = if (isRecording) 5000L else 0L,
                        peakLevel = if (isRecording) 0.7f else 0f
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

        // Test button responsiveness during recording
        repeat(10) {
            val responseTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Pad 1")
                    .performClick()
                
                composeTestRule.waitForIdle()
            }
            
            responseTimes.add(responseTime)
            
            // Each button press should respond within 50ms
            assertTrue(
                responseTime < 50,
                "Button response time too high during recording: ${responseTime}ms"
            )
            
            delay(100) // Small delay between tests
        }

        val averageResponseTime = responseTimes.average()
        assertTrue(
            averageResponseTime < 30,
            "Average button response time too high: ${averageResponseTime}ms"
        )
    }

    @Test
    fun testScrollingPerformanceDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        val scrollTimes = mutableListOf<Long>()

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = 10000L,
                        peakLevel = 0.6f
                    ),
                    // Add scrollable content
                    padStates = (0..15).map { index ->
                        PadState(
                            id = index.toString(),
                            sampleName = "Sample $index",
                            isLoaded = true,
                            isPlaying = false
                        )
                    }
                ),
                onRecordingStart = {},
                onRecordingStop = { isRecording = false },
                onPadAssignment = {}
            )
        }

        // Test scrolling performance
        repeat(5) {
            val scrollTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Pad Grid")
                    .performTouchInput {
                        swipeUp(
                            startY = centerY + 200,
                            endY = centerY - 200,
                            durationMillis = 300
                        )
                    }
                
                composeTestRule.waitForIdle()
            }
            
            scrollTimes.add(scrollTime)
            
            // Scrolling should complete smoothly within 400ms
            assertTrue(
                scrollTime < 400,
                "Scroll time too high during recording: ${scrollTime}ms"
            )
        }

        val averageScrollTime = scrollTimes.average()
        assertTrue(
            averageScrollTime < 350,
            "Average scroll time too high during recording: ${averageScrollTime}ms"
        )
    }

    @Test
    fun testAnimationSmoothnessDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(false)
        var recordingLevel by mutableStateOf(0f)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = if (isRecording) 8000L else 0L,
                        peakLevel = recordingLevel,
                        averageLevel = recordingLevel * 0.7f
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

        // Test level meter animation smoothness
        val animationFrameTimes = mutableListOf<Long>()
        
        repeat(30) { frame ->
            recordingLevel = (frame % 10) / 10f // Animate level 0.0 to 1.0
            
            val frameTime = measureTimeMillis {
                composeTestRule.waitForIdle()
                
                // Verify level meter updates
                composeTestRule.onNodeWithContentDescription("Recording Level Meter")
                    .assertIsDisplayed()
            }
            
            animationFrameTimes.add(frameTime)
            
            // Each animation frame should render within 16.67ms (60fps)
            assertTrue(
                frameTime < 17,
                "Animation frame time too high: ${frameTime}ms (frame $frame)"
            )
        }

        val averageFrameTime = animationFrameTimes.average()
        assertTrue(
            averageFrameTime < 12,
            "Average animation frame time too high: ${averageFrameTime}ms"
        )
    }

    @Test
    fun testTextInputResponsivenessDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        var showMetadataEditor by mutableStateOf(false)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        recordedSampleId = if (!isRecording) "sample_123" else null
                    ),
                    showSampleMetadataEditor = showMetadataEditor
                ),
                onRecordingStart = {},
                onRecordingStop = { 
                    isRecording = false
                    showMetadataEditor = true
                },
                onPadAssignment = {}
            )
        }

        // Stop recording to show metadata editor
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .performClick()

        // Test text input responsiveness
        val inputTimes = mutableListOf<Long>()
        
        repeat(10) { char ->
            val inputTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Sample Name Input")
                    .performTextInput(char.toString())
                
                composeTestRule.waitForIdle()
            }
            
            inputTimes.add(inputTime)
            
            // Text input should respond within 100ms
            assertTrue(
                inputTime < 100,
                "Text input time too high: ${inputTime}ms"
            )
        }

        val averageInputTime = inputTimes.average()
        assertTrue(
            averageInputTime < 50,
            "Average text input time too high: ${averageInputTime}ms"
        )
    }

    @Test
    fun testNavigationResponsivenessDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        var currentScreen by mutableStateOf("main")

        composeTestRule.setContent {
            when (currentScreen) {
                "main" -> CompactMainScreen(
                    state = CompactUIState(
                        recordingState = IntegratedRecordingState(
                            isRecording = isRecording,
                            durationMs = 15000L
                        )
                    ),
                    onRecordingStart = {},
                    onRecordingStop = { isRecording = false },
                    onPadAssignment = {},
                    onNavigateToSettings = { currentScreen = "settings" }
                )
                "settings" -> {
                    // Mock settings screen
                }
            }
        }

        // Test navigation during recording
        val navigationTimes = mutableListOf<Long>()
        
        repeat(3) {
            val navigationTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Settings")
                    .performClick()
                
                composeTestRule.waitUntil(timeoutMillis = 1000) {
                    currentScreen == "settings"
                }
                
                // Navigate back
                currentScreen = "main"
                composeTestRule.waitForIdle()
            }
            
            navigationTimes.add(navigationTime)
            
            // Navigation should complete within 500ms even during recording
            assertTrue(
                navigationTime < 500,
                "Navigation time too high during recording: ${navigationTime}ms"
            )
        }

        val averageNavigationTime = navigationTimes.average()
        assertTrue(
            averageNavigationTime < 300,
            "Average navigation time too high: ${averageNavigationTime}ms"
        )
    }

    @Test
    fun testMultiTouchResponsivenessDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        val touchTimes = mutableListOf<Long>()

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = 12000L,
                        peakLevel = 0.8f
                    ),
                    padStates = (0..7).map { index ->
                        PadState(
                            id = index.toString(),
                            sampleName = "Pad $index",
                            isLoaded = true,
                            isPlaying = false
                        )
                    }
                ),
                onRecordingStart = {},
                onRecordingStop = { isRecording = false },
                onPadAssignment = {},
                onPadTrigger = { padId -> 
                    // Handle pad trigger
                }
            )
        }

        // Test multi-touch (simultaneous pad presses)
        repeat(5) {
            val multiTouchTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Pad Grid")
                    .performTouchInput {
                        // Simulate pressing multiple pads simultaneously
                        down(1, center)
                        down(2, Offset(centerX + 100, centerY))
                        down(3, Offset(centerX - 100, centerY))
                        
                        // Hold for a moment
                        advanceEventTime(50)
                        
                        // Release all
                        up(1)
                        up(2)
                        up(3)
                    }
                
                composeTestRule.waitForIdle()
            }
            
            touchTimes.add(multiTouchTime)
            
            // Multi-touch should be handled within 100ms
            assertTrue(
                multiTouchTime < 100,
                "Multi-touch time too high during recording: ${multiTouchTime}ms"
            )
        }

        val averageMultiTouchTime = touchTimes.average()
        assertTrue(
            averageMultiTouchTime < 75,
            "Average multi-touch time too high: ${averageMultiTouchTime}ms"
        )
    }

    @Test
    fun testUIUpdateFrequencyDuringRecording() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        var recordingDuration by mutableStateOf(0L)
        val updateTimes = mutableListOf<Long>()

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = recordingDuration,
                        peakLevel = (recordingDuration % 1000) / 1000f
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = { isRecording = false },
                onPadAssignment = {}
            )
        }

        // Test UI update frequency (should be smooth 60fps)
        repeat(60) { frame ->
            recordingDuration = frame * 100L // Increment by 100ms each frame
            
            val updateTime = measureTimeMillis {
                composeTestRule.waitForIdle()
                
                // Verify duration display updates
                val expectedTime = String.format("%02d:%02d", 
                    (recordingDuration / 1000) / 60,
                    (recordingDuration / 1000) % 60
                )
                
                composeTestRule.onNodeWithText(expectedTime)
                    .assertIsDisplayed()
            }
            
            updateTimes.add(updateTime)
            
            // Each UI update should complete within one frame (16.67ms)
            assertTrue(
                updateTime < 17,
                "UI update time too high: ${updateTime}ms (frame $frame)"
            )
        }

        val averageUpdateTime = updateTimes.average()
        assertTrue(
            averageUpdateTime < 10,
            "Average UI update time too high: ${averageUpdateTime}ms"
        )
    }

    @Test
    fun testMemoryPressureUIResponsiveness() = runBlockingTest {
        var isRecording by mutableStateOf(true)
        var memoryPressure by mutableStateOf(false)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = isRecording,
                        durationMs = 20000L
                    ),
                    performanceState = PerformanceState(
                        memoryPressure = memoryPressure,
                        activeOptimizations = if (memoryPressure) 
                            setOf(OptimizationType.REDUCE_MEMORY_USAGE) 
                        else emptySet()
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = { isRecording = false },
                onPadAssignment = {}
            )
        }

        // Simulate memory pressure
        memoryPressure = true

        // Test UI responsiveness under memory pressure
        val responseTimes = mutableListOf<Long>()
        
        repeat(10) {
            val responseTime = measureTimeMillis {
                composeTestRule.onNodeWithContentDescription("Pad 1")
                    .performClick()
                
                composeTestRule.waitForIdle()
            }
            
            responseTimes.add(responseTime)
            
            // UI should remain responsive even under memory pressure
            assertTrue(
                responseTime < 100,
                "UI response time too high under memory pressure: ${responseTime}ms"
            )
        }

        val averageResponseTime = responseTimes.average()
        assertTrue(
            averageResponseTime < 75,
            "Average response time too high under memory pressure: ${averageResponseTime}ms"
        )
    }
}