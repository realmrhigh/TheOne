package com.high.theone.features.compactui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ResponsiveRecordingPanel to ensure proper responsive behavior
 * across different screen configurations and orientations.
 * 
 * Requirements: 3.3 (responsive design), 2.2 (accessibility)
 */
@RunWith(AndroidJUnit4::class)
class ResponsiveRecordingPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockRecordingState = IntegratedRecordingState(
        isRecording = false,
        isProcessing = false,
        durationMs = 0L,
        peakLevel = 0.5f,
        averageLevel = 0.3f,
        recordedSampleId = null,
        availablePadsForAssignment = emptyList(),
        isAssignmentMode = false,
        error = null,
        canStartRecording = true
    )

    private val mockDrumPadState = DrumPadState(
        padSettings = (0..15).associate { 
            it.toString() to PadSettings(
                padId = it.toString(),
                sampleId = if (it < 8) "sample_$it" else null,
                volume = 1.0f,
                pitch = 1.0f,
                isMuted = false,
                isSolo = false
            )
        },
        activePads = emptySet(),
        soloedPads = emptySet(),
        mutedPads = emptySet()
    )

    @Test
    fun testPortraitModeBottomSheetBehavior() {
        val portraitConfig = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = mockRecordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = portraitConfig,
                isVisible = true,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Verify bottom sheet elements are present
        composeTestRule.onNodeWithText("Recording").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hide Recording Panel").assertIsDisplayed()
        
        // Verify drag handle is present for bottom sheet
        composeTestRule.onNode(hasTestTag("drag_handle") or hasContentDescription("Drag Handle"))
            .assertExists()
    }

    @Test
    fun testLandscapeModesidePanelBehavior() {
        val landscapeConfig = ScreenConfiguration(
            screenWidth = 640.dp,
            screenHeight = 360.dp,
            orientation = Orientation.LANDSCAPE,
            densityDpi = 420,
            isTablet = false
        )

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = mockRecordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = landscapeConfig,
                isVisible = true,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Verify side panel elements are present
        composeTestRule.onNodeWithText("Recording Studio").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close Recording Panel").assertIsDisplayed()
        
        // Verify expanded controls are available in landscape
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
    }

    @Test
    fun testTabletModeProfessionalLayout() {
        val tabletConfig = ScreenConfiguration(
            screenWidth = 800.dp,
            screenHeight = 1200.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 320,
            isTablet = true
        )

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = mockRecordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = tabletConfig,
                isVisible = true,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Verify professional tablet layout elements
        composeTestRule.onNodeWithText("Professional Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recording Controls").assertIsDisplayed()
        
        // Verify professional quality indicators
        composeTestRule.onNodeWithText("48kHz / 24-bit").assertIsDisplayed()
    }

    @Test
    fun testRecordingStateVisibility() {
        val recordingState = mockRecordingState.copy(isRecording = true, peakLevel = 0.8f)

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = recordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = ScreenConfiguration(
                    screenWidth = 360.dp,
                    screenHeight = 640.dp,
                    orientation = Orientation.PORTRAIT,
                    densityDpi = 420,
                    isTablet = false
                ),
                isVisible = true,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Verify recording-specific elements are visible
        composeTestRule.onNodeWithText("Level").assertIsDisplayed()
        
        // Verify recording status is shown
        composeTestRule.onNode(
            hasText("Recording...") or hasContentDescription("Recording")
        ).assertExists()
    }

    @Test
    fun testSampleAssignmentVisibility() {
        val recordingState = mockRecordingState.copy(
            recordedSampleId = "test_sample",
            isRecording = false
        )

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = recordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = ScreenConfiguration(
                    screenWidth = 360.dp,
                    screenHeight = 640.dp,
                    orientation = Orientation.PORTRAIT,
                    densityDpi = 420,
                    isTablet = false
                ),
                isVisible = true,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Verify sample assignment UI is visible
        composeTestRule.onNodeWithText("Assign to Pad").assertIsDisplayed()
        
        // Verify pad grid is present (should have 16 pads)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(17) // 16 pads + close button
    }

    @Test
    fun testAccessibilityInAllModes() {
        val configs = listOf(
            ScreenConfiguration(360.dp, 640.dp, Orientation.PORTRAIT, 420, false),
            ScreenConfiguration(640.dp, 360.dp, Orientation.LANDSCAPE, 420, false),
            ScreenConfiguration(800.dp, 1200.dp, Orientation.PORTRAIT, 320, true)
        )

        configs.forEach { config ->
            composeTestRule.setContent {
                ResponsiveRecordingPanel(
                    recordingState = mockRecordingState,
                    drumPadState = mockDrumPadState,
                    screenConfiguration = config,
                    isVisible = true,
                    onStartRecording = {},
                    onStopRecording = {},
                    onAssignToPad = {},
                    onDiscardRecording = {},
                    onHidePanel = {}
                )
            }

            // Verify all interactive elements have content descriptions
            composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                assert(
                    node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) ||
                    node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ) {
                    "Interactive element missing accessibility description in ${config.layoutMode} mode"
                }
            }
        }
    }

    @Test
    fun testPanelVisibilityToggle() {
        var isVisible = true
        var hideCallCount = 0

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = mockRecordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = ScreenConfiguration(
                    screenWidth = 360.dp,
                    screenHeight = 640.dp,
                    orientation = Orientation.PORTRAIT,
                    densityDpi = 420,
                    isTablet = false
                ),
                isVisible = isVisible,
                onStartRecording = {},
                onStopRecording = {},
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = { hideCallCount++ }
            )
        }

        // Verify panel is visible
        composeTestRule.onNodeWithText("Recording").assertIsDisplayed()

        // Click hide button
        composeTestRule.onNodeWithContentDescription("Hide Recording Panel").performClick()

        // Verify hide callback was called
        assert(hideCallCount == 1) { "Hide panel callback should be called once" }
    }

    @Test
    fun testRecordingControlsInteraction() {
        var startCallCount = 0
        var stopCallCount = 0

        composeTestRule.setContent {
            ResponsiveRecordingPanel(
                recordingState = mockRecordingState,
                drumPadState = mockDrumPadState,
                screenConfiguration = ScreenConfiguration(
                    screenWidth = 360.dp,
                    screenHeight = 640.dp,
                    orientation = Orientation.PORTRAIT,
                    densityDpi = 420,
                    isTablet = false
                ),
                isVisible = true,
                onStartRecording = { startCallCount++ },
                onStopRecording = { stopCallCount++ },
                onAssignToPad = {},
                onDiscardRecording = {},
                onHidePanel = {}
            )
        }

        // Find and click the record button
        composeTestRule.onNode(
            hasContentDescription("Start Recording") or hasText("Record")
        ).performClick()

        // Verify start recording callback was called
        assert(startCallCount == 1) { "Start recording callback should be called once" }
    }
}