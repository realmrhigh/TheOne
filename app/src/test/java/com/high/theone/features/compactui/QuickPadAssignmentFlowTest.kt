package com.high.theone.features.compactui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the QuickPadAssignmentFlow component
 * 
 * Requirements: 2.1, 2.3, 4.1, 4.2 (post-recording UI, pad highlighting, one-tap assignment, visual confirmation)
 */
@RunWith(AndroidJUnit4::class)
class QuickPadAssignmentFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quickPadAssignmentFlow_showsWhenRecordingCompletes() {
        // Given a completed recording state
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = "test-sample-123",
            availablePadsForAssignment = listOf("0", "1", "2", "3"),
            isAssignmentMode = true
        )
        
        val drumPadState = DrumPadState(
            padSettings = mapOf(
                "0" to createEmptyPadSettings("0"),
                "1" to createEmptyPadSettings("1"),
                "2" to createEmptyPadSettings("2"),
                "3" to createEmptyPadSettings("3")
            )
        )
        
        val screenConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        var assignedPadId: String? = null
        var cancelled = false
        
        // When the component is displayed
        composeTestRule.setContent {
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { padId -> assignedPadId = padId },
                onCancel = { cancelled = true }
            )
        }
        
        // Then the assignment flow should be visible
        composeTestRule.onNodeWithText("Assign Sample to Pad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap an empty pad to assign your recorded sample").assertIsDisplayed()
        composeTestRule.onNodeWithText("Available Pads (4)").assertIsDisplayed()
        
        // And available pads should be shown
        composeTestRule.onNodeWithText("1").assertIsDisplayed() // Pad 0 (displayed as 1)
        composeTestRule.onNodeWithText("2").assertIsDisplayed() // Pad 1 (displayed as 2)
        composeTestRule.onNodeWithText("3").assertIsDisplayed() // Pad 2 (displayed as 3)
        composeTestRule.onNodeWithText("4").assertIsDisplayed() // Pad 3 (displayed as 4)
    }
    
    @Test
    fun quickPadAssignmentFlow_hidesWhenNoRecordedSample() {
        // Given a recording state without a recorded sample
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = null,
            availablePadsForAssignment = listOf("0", "1", "2", "3"),
            isAssignmentMode = false
        )
        
        val drumPadState = DrumPadState()
        val screenConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        // When the component is displayed
        composeTestRule.setContent {
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { },
                onCancel = { }
            )
        }
        
        // Then the assignment flow should not be visible
        composeTestRule.onNodeWithText("Assign Sample to Pad").assertDoesNotExist()
    }
    
    @Test
    fun quickPadAssignmentFlow_callsOnAssignToPadWhenPadTapped() {
        // Given a completed recording state
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = "test-sample-123",
            availablePadsForAssignment = listOf("0", "1"),
            isAssignmentMode = true
        )
        
        val drumPadState = DrumPadState(
            padSettings = mapOf(
                "0" to createEmptyPadSettings("0"),
                "1" to createEmptyPadSettings("1")
            )
        )
        
        val screenConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        var assignedPadId: String? = null
        
        // When the component is displayed
        composeTestRule.setContent {
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { padId -> assignedPadId = padId },
                onCancel = { }
            )
        }
        
        // When a pad is tapped
        composeTestRule.onNodeWithText("1").performClick() // Tap pad 0 (displayed as 1)
        
        // Then onAssignToPad should be called with the correct pad ID
        assert(assignedPadId == "0")
    }
    
    @Test
    fun quickPadAssignmentFlow_callsOnCancelWhenCancelTapped() {
        // Given a completed recording state
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = "test-sample-123",
            availablePadsForAssignment = listOf("0"),
            isAssignmentMode = true
        )
        
        val drumPadState = DrumPadState()
        val screenConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        var cancelled = false
        
        // When the component is displayed
        composeTestRule.setContent {
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { },
                onCancel = { cancelled = true }
            )
        }
        
        // When the cancel button is tapped
        composeTestRule.onNodeWithContentDescription("Cancel Assignment").performClick()
        
        // Then onCancel should be called
        assert(cancelled)
    }
    
    @Test
    fun quickPadAssignmentFlow_showsNoAvailablePadsMessage() {
        // Given a recording state with no available pads
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = "test-sample-123",
            availablePadsForAssignment = emptyList(),
            isAssignmentMode = true
        )
        
        val drumPadState = DrumPadState()
        val screenConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        // When the component is displayed
        composeTestRule.setContent {
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { },
                onCancel = { }
            )
        }
        
        // Then the no available pads message should be shown
        composeTestRule.onNodeWithText("No empty pads available. Clear a pad first to assign this sample.").assertIsDisplayed()
    }
    
    private fun createEmptyPadSettings(padId: String): com.high.theone.features.drumtrack.model.PadSettings {
        return com.high.theone.features.drumtrack.model.PadSettings(
            padId = padId,
            sampleId = null,
            sampleName = null,
            volume = 1.0f,
            pan = 0.0f,
            playbackMode = com.high.theone.model.PlaybackMode.ONE_SHOT,
            muteGroup = null
        )
    }
}