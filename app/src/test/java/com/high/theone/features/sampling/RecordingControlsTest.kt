package com.high.theone.features.sampling

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.RecordingState
import com.high.theone.model.AudioInputSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * UI component tests for RecordingControls component.
 * Tests recording controls interaction, state updates, and level meter functionality.
 * 
 * Requirements: 1.2 (recording status), 1.3 (level monitoring)
 */
@RunWith(AndroidJUnit4::class)
class RecordingControlsTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `recording controls displays correct initial state`() {
        val initialState = RecordingState(isInitialized = true)
        var startRecordingCalled = false
        var stopRecordingCalled = false
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = initialState,
                onStartRecording = { startRecordingCalled = true },
                onStopRecording = { stopRecordingCalled = true }
            )
        }
        
        // Verify initial UI state
        composeTestRule.onNodeWithText("Ready to Record").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Start Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:00").assertDoesNotExist() // Duration not shown initially
        
        // Verify record button is enabled
        composeTestRule.onNodeWithContentDescription("Start Recording").assertIsEnabled()
    }
    
    @Test
    fun `record button triggers start recording callback`() {
        val initialState = RecordingState(isInitialized = true)
        var startRecordingCalled = false
        var stopRecordingCalled = false
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = initialState,
                onStartRecording = { startRecordingCalled = true },
                onStopRecording = { stopRecordingCalled = true }
            )
        }
        
        // Click record button
        composeTestRule.onNodeWithContentDescription("Start Recording").performClick()
        
        // Verify callback was called
        assertTrue("Start recording callback should be called", startRecordingCalled)
        assertFalse("Stop recording callback should not be called", stopRecordingCalled)
    }
    
    @Test
    fun `recording state shows correct UI elements`() {
        val recordingState = RecordingState(
            isInitialized = true,
            isRecording = true,
            durationMs = 5000L,
            peakLevel = 0.7f,
            averageLevel = 0.5f
        )
        var startRecordingCalled = false
        var stopRecordingCalled = false
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = recordingState,
                onStartRecording = { startRecordingCalled = true },
                onStopRecording = { stopRecordingCalled = true }
            )
        }
        
        // Verify recording UI state
        composeTestRule.onNodeWithText("Recording").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Stop Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("Input Level").assertIsDisplayed()
        
        // Verify level meter information
        composeTestRule.onNodeWithText("Peak: 70%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avg: 50%").assertIsDisplayed()
    }
    
    @Test
    fun `stop button triggers stop recording callback`() {
        val recordingState = RecordingState(
            isInitialized = true,
            isRecording = true,
            durationMs = 5000L
        )
        var startRecordingCalled = false
        var stopRecordingCalled = false
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = recordingState,
                onStartRecording = { startRecordingCalled = true },
                onStopRecording = { stopRecordingCalled = true }
            )
        }
        
        // Click stop button
        composeTestRule.onNodeWithContentDescription("Stop Recording").performClick()
        
        // Verify callback was called
        assertFalse("Start recording callback should not be called", startRecordingCalled)
        assertTrue("Stop recording callback should be called", stopRecordingCalled)
    }
    
    @Test
    fun `level meter displays correctly with different levels`() {
        val testCases = listOf(
            0.0f to 0.0f, // Silent
            0.3f to 0.2f, // Low level
            0.7f to 0.5f, // Medium level
            0.9f to 0.8f, // High level
            1.0f to 0.9f  // Clipping level
        )
        
        testCases.forEach { (peakLevel, averageLevel) ->
            val recordingState = RecordingState(
                isRecording = true,
                peakLevel = peakLevel,
                averageLevel = averageLevel
            )
            
            composeTestRule.setContent {
                RecordingControls(
                    recordingState = recordingState,
                    onStartRecording = { },
                    onStopRecording = { }
                )
            }
            
            // Verify level display
            val expectedPeak = (peakLevel * 100).toInt()
            val expectedAvg = (averageLevel * 100).toInt()
            
            composeTestRule.onNodeWithText("Peak: ${expectedPeak}%").assertIsDisplayed()
            composeTestRule.onNodeWithText("Avg: ${expectedAvg}%").assertIsDisplayed()
        }
    }
    
    @Test
    fun `error state displays error message`() {
        val errorState = RecordingState(
            isInitialized = true,
            error = "Microphone not available"
        )
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = errorState,
                onStartRecording = { },
                onStopRecording = { }
            )
        }
        
        // Verify error display
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Microphone not available").assertIsDisplayed()
        
        // Verify record button is disabled
        composeTestRule.onNodeWithContentDescription("Start Recording").assertIsNotEnabled()
    }
    
    @Test
    fun `processing state shows correct UI`() {
        val processingState = RecordingState(
            isInitialized = true,
            isProcessing = true,
            durationMs = 10000L
        )
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = processingState,
                onStartRecording = { },
                onStopRecording = { }
            )
        }
        
        // Verify processing UI state
        composeTestRule.onNodeWithText("Processing...").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:10").assertIsDisplayed()
        
        // Verify buttons are disabled during processing
        composeTestRule.onNodeWithContentDescription("Start Recording").assertIsNotEnabled()
    }
    
    @Test
    fun `near max duration shows warning indicators`() {
        val nearMaxState = RecordingState(
            isInitialized = true,
            isRecording = true,
            durationMs = 26000L, // 26 seconds
            maxDurationSeconds = 30
        )
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = nearMaxState,
                onStartRecording = { },
                onStopRecording = { }
            )
        }
        
        // Verify warning indicators
        composeTestRule.onNodeWithText("4s remaining").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:26").assertIsDisplayed()
    }
    
    @Test
    fun `duration display formats correctly for various durations`() {
        val testCases = mapOf(
            0L to "00:00",
            1000L to "00:01",
            30000L to "00:30",
            60000L to "01:00",
            90000L to "01:30",
            3661000L to "61:01" // Over an hour
        )
        
        testCases.forEach { (durationMs, expectedFormat) ->
            val state = RecordingState(
                isRecording = true,
                durationMs = durationMs
            )
            
            composeTestRule.setContent {
                RecordingControls(
                    recordingState = state,
                    onStartRecording = { },
                    onStopRecording = { }
                )
            }
            
            composeTestRule.onNodeWithText(expectedFormat).assertIsDisplayed()
        }
    }
    
    @Test
    fun `uninitialized state shows correct status`() {
        val uninitializedState = RecordingState(isInitialized = false)
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = uninitializedState,
                onStartRecording = { },
                onStopRecording = { }
            )
        }
        
        // Verify uninitialized UI state
        composeTestRule.onNodeWithText("Initializing...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Start Recording").assertIsNotEnabled()
    }
    
    @Test
    fun `paused state shows correct UI elements`() {
        val pausedState = RecordingState(
            isInitialized = true,
            isPaused = true,
            durationMs = 15000L
        )
        
        composeTestRule.setContent {
            RecordingControls(
                recordingState = pausedState,
                onStartRecording = { },
                onStopRecording = { }
            )
        }
        
        // Verify paused UI state
        composeTestRule.onNodeWithText("Paused").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:15").assertIsDisplayed()
    }
    
    // Unit tests for RecordingState data class logic
    @Test
    fun `recording state shows correct status text`() {
        // Test initial state
        val initialState = RecordingState()
        assertFalse(initialState.isRecording)
        assertTrue(initialState.canStartRecording)
        assertEquals("00:00", initialState.formattedDuration)
        
        // Test recording state
        val recordingState = initialState.copy(
            isRecording = true,
            durationMs = 5000L,
            peakLevel = 0.7f,
            averageLevel = 0.5f
        )
        assertTrue(recordingState.isRecording)
        assertFalse(recordingState.canStartRecording)
        assertTrue(recordingState.canStopRecording)
        assertEquals("00:05", recordingState.formattedDuration)
        
        // Test processing state
        val processingState = recordingState.copy(
            isRecording = false,
            isProcessing = true
        )
        assertFalse(processingState.isRecording)
        assertFalse(processingState.canStartRecording)
        assertFalse(processingState.canStopRecording)
        
        // Test error state
        val errorState = initialState.copy(
            error = "Microphone not available"
        )
        assertFalse(errorState.canStartRecording)
        assertEquals("Microphone not available", errorState.error)
    }
    
    @Test
    fun `recording duration formats correctly`() {
        val testCases = mapOf(
            0L to "00:00",
            1000L to "00:01",
            30000L to "00:30",
            60000L to "01:00",
            90000L to "01:30",
            3661000L to "61:01" // Over an hour
        )
        
        testCases.forEach { (durationMs, expected) ->
            val state = RecordingState(durationMs = durationMs)
            assertEquals("Duration $durationMs should format as $expected", 
                        expected, state.formattedDuration)
        }
    }
    
    @Test
    fun `recording state detects near max duration`() {
        val maxDuration = 30 // 30 seconds
        
        // Not near max duration
        val earlyState = RecordingState(
            durationMs = 20000L, // 20 seconds
            maxDurationSeconds = maxDuration
        )
        assertFalse(earlyState.isNearMaxDuration)
        
        // Near max duration (within 5 seconds)
        val nearMaxState = RecordingState(
            durationMs = 26000L, // 26 seconds (4 seconds remaining)
            maxDurationSeconds = maxDuration
        )
        assertTrue(nearMaxState.isNearMaxDuration)
        
        // At max duration
        val maxState = RecordingState(
            durationMs = 30000L, // 30 seconds
            maxDurationSeconds = maxDuration
        )
        assertTrue(maxState.isNearMaxDuration)
    }
    
    @Test
    fun `recording state validates input source`() {
        val state = RecordingState(inputSource = AudioInputSource.MICROPHONE)
        assertEquals(AudioInputSource.MICROPHONE, state.inputSource)
        
        val lineInState = state.copy(inputSource = AudioInputSource.LINE_IN)
        assertEquals(AudioInputSource.LINE_IN, lineInState.inputSource)
        
        val usbState = state.copy(inputSource = AudioInputSource.USB_AUDIO)
        assertEquals(AudioInputSource.USB_AUDIO, usbState.inputSource)
    }
    
    @Test
    fun `recording state handles audio quality settings`() {
        val state = RecordingState(
            sampleRate = 44100,
            channels = 1,
            bitDepth = 16
        )
        
        assertEquals(44100, state.sampleRate)
        assertEquals(1, state.channels)
        assertEquals(16, state.bitDepth)
        
        // Test stereo recording
        val stereoState = state.copy(channels = 2)
        assertEquals(2, stereoState.channels)
        
        // Test high sample rate
        val highQualityState = state.copy(sampleRate = 48000)
        assertEquals(48000, highQualityState.sampleRate)
    }
    
    @Test
    fun `recording state handles level monitoring`() {
        val state = RecordingState(
            peakLevel = 0.8f,
            averageLevel = 0.6f
        )
        
        assertEquals(0.8f, state.peakLevel, 0.001f)
        assertEquals(0.6f, state.averageLevel, 0.001f)
        
        // Test level bounds
        val clippingState = state.copy(peakLevel = 1.0f)
        assertEquals(1.0f, clippingState.peakLevel, 0.001f)
        
        val silentState = state.copy(peakLevel = 0.0f, averageLevel = 0.0f)
        assertEquals(0.0f, silentState.peakLevel, 0.001f)
        assertEquals(0.0f, silentState.averageLevel, 0.001f)
    }
}