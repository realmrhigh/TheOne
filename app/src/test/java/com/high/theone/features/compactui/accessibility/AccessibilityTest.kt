package com.high.theone.features.compactui.accessibility

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.high.theone.features.compactui.*
import com.high.theone.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive accessibility tests for compact UI components.
 * Tests screen reader compatibility, touch target sizes, contrast ratios, and keyboard navigation.
 * 
 * Requirements: 9.1 (accessibility validation), 9.2 (minimum touch targets), 
 *              9.3 (high contrast), 9.5 (keyboard navigation)
 */
class AccessibilityTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private val accessibilityTestSuite = AccessibilityTestSuite()
    private val colorContrastChecker = ColorContrastChecker()
    
    @Test
    fun transportControlBar_meetsAccessibilityRequirements() {
        val transportState = TransportState(
            isPlaying = false,
            isRecording = false,
            bpm = 120,
            midiSyncStatus = MidiSyncStatus.DISCONNECTED,
            audioLevels = AudioLevels()
        )
        
        composeTestRule.setContent {
            MaterialTheme {
                TransportControlBar(
                    transportState = transportState,
                    onPlayPause = {},
                    onStop = {},
                    onRecord = {},
                    onBpmChange = {}
                )
            }
        }
        
        // Test content descriptions
        composeTestRule.onNodeWithContentDescription("Start playback")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithContentDescription("Stop playback")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
        
        composeTestRule.onNodeWithContentDescription("Start recording")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
        
        // Test minimum touch target sizes
        composeTestRule.onNodeWithContentDescription("Start playback")
            .assertMinimumTouchTargetSize()
        
        composeTestRule.onNodeWithContentDescription("Stop playback")
            .assertMinimumTouchTargetSize()
        
        composeTestRule.onNodeWithContentDescription("Start recording")
            .assertMinimumTouchTargetSize()
        
        // Test BPM controls
        composeTestRule.onNodeWithContentDescription("Decrease tempo, current 120 BPM")
            .assertExists()
            .assertMinimumTouchTargetSize()
        
        composeTestRule.onNodeWithContentDescription("Increase tempo, current 120 BPM")
            .assertExists()
            .assertMinimumTouchTargetSize()
        
        // Run comprehensive accessibility test
        val report = accessibilityTestSuite.runFullAccessibilityTest(composeTestRule)
        assertTrue("Transport control bar should pass basic accessibility", report.passesBasicAccessibility)
    }
    
    @Test
    fun compactDrumPadGrid_meetsAccessibilityRequirements() {
        val pads = List(16) { index ->
            PadState(
                index = index,
                hasAssignedSample = index < 8,
                sampleName = if (index < 8) "Sample $index" else null,
                canTrigger = index < 8
            )
        }
        
        val screenConfig = ScreenConfiguration(
            screenWidth = 400.dp,
            screenHeight = 800.dp,
            orientation = androidx.compose.ui.unit.Orientation.Portrait,
            densityDpi = 160,
            isTablet = false
        )
        
        composeTestRule.setContent {
            MaterialTheme {
                CompactDrumPadGrid(
                    pads = pads,
                    onPadTap = { _, _ -> },
                    onPadLongPress = {},
                    screenConfiguration = screenConfig
                )
            }
        }
        
        // Test that all pads have proper content descriptions
        for (i in 0 until 16) {
            val expectedDescription = if (i < 8) {
                "Drum pad ${i + 1}, Sample $i assigned"
            } else {
                "Drum pad ${i + 1}, empty pad"
            }
            
            composeTestRule.onNodeWithContentDescription(expectedDescription, substring = true)
                .assertExists()
        }
        
        // Test minimum touch target sizes for all pads
        for (i in 0 until 16) {
            composeTestRule.onAllNodesWithContentDescription("Drum pad ${i + 1}", substring = true)[0]
                .assertMinimumTouchTargetSize()
        }
        
        // Run comprehensive accessibility test
        val report = accessibilityTestSuite.runFullAccessibilityTest(composeTestRule)
        assertTrue("Drum pad grid should pass basic accessibility", report.passesBasicAccessibility)
    }
    
    @Test
    fun inlineSequencer_meetsAccessibilityRequirements() {
        val pattern = Pattern(
            id = "test",
            name = "Test Pattern",
            length = 16,
            tempo = 120f,
            steps = mapOf(
                0 to listOf(Step(position = 0, velocity = 127), Step(position = 4, velocity = 100))
            )
        )
        
        val tracks = listOf(
            SequencerPadInfo(0, "kick", "Kick", true),
            SequencerPadInfo(1, "snare", "Snare", true)
        )
        
        composeTestRule.setContent {
            MaterialTheme {
                InlineSequencer(
                    sequencerState = SequencerState(selectedPads = setOf(0)),
                    currentPattern = pattern,
                    availablePatterns = listOf(pattern),
                    availableTracks = tracks,
                    selectedTracks = setOf(0),
                    muteSoloState = TrackMuteSoloState(),
                    onStepToggle = { _, _ -> },
                    onStepLongPress = { _, _ -> },
                    onTrackSelect = {},
                    onTrackMute = {},
                    onTrackSolo = {},
                    onPatternSelect = {},
                    onPatternCreate = { _, _ -> },
                    onPatternDuplicate = {},
                    onPatternDelete = {},
                    onPatternRename = { _, _ -> },
                    onSelectAllTracks = {},
                    onSelectAssignedTracks = {},
                    onClearTrackSelection = {}
                )
            }
        }
        
        // Test step accessibility
        composeTestRule.onNodeWithContentDescription("Step 1", substring = true)
            .assertExists()
            .assertMinimumTouchTargetSize()
        
        // Run comprehensive accessibility test
        val report = accessibilityTestSuite.runFullAccessibilityTest(composeTestRule)
        assertTrue("Inline sequencer should pass basic accessibility", report.passesBasicAccessibility)
    }
    
    @Test
    fun colorContrast_meetsWCAGStandards() {
        // Test standard Material 3 colors
        val lightBackground = Color.White
        val darkText = Color.Black
        val lightText = Color.White
        val darkBackground = Color.Black
        
        // Test WCAG AA compliance
        assertTrue(
            "Dark text on light background should meet WCAG AA",
            colorContrastChecker.meetsWCAG_AA(darkText, lightBackground)
        )
        
        assertTrue(
            "Light text on dark background should meet WCAG AA",
            colorContrastChecker.meetsWCAG_AA(lightText, darkBackground)
        )
        
        // Test high contrast colors
        val highContrastColors = HighContrastColors.drumPadColors(darkTheme = false)
        
        assertTrue(
            "High contrast assigned pad should meet WCAG AAA",
            colorContrastChecker.meetsWCAG_AAA(
                highContrastColors.text,
                highContrastColors.assigned
            )
        )
        
        assertTrue(
            "High contrast playing pad should meet WCAG AAA",
            colorContrastChecker.meetsWCAG_AAA(
                highContrastColors.text,
                highContrastColors.playing
            )
        )
    }
    
    @Test
    fun highContrastMode_providesImprovedContrast() {
        composeTestRule.setContent {
            HighContrastModeProvider(enabled = true) {
                MaterialTheme {
                    TransportControlBar(
                        transportState = TransportState(),
                        onPlayPause = {},
                        onStop = {},
                        onRecord = {},
                        onBpmChange = {}
                    )
                }
            }
        }
        
        // Verify high contrast mode is applied
        assertTrue("High contrast mode should be enabled", isHighContrastModeEnabled())
        
        // Test that components still meet accessibility requirements
        val report = accessibilityTestSuite.runFullAccessibilityTest(composeTestRule)
        assertTrue("High contrast mode should maintain accessibility", report.passesBasicAccessibility)
    }
    
    @Test
    fun keyboardNavigation_worksCorrectly() {
        val transportState = TransportState()
        var playPressed = false
        var stopPressed = false
        var recordPressed = false
        var bpmIncreased = false
        var bpmDecreased = false
        
        composeTestRule.setContent {
            MaterialTheme {
                KeyboardNavigationProvider {
                    TransportControlBar(
                        transportState = transportState,
                        onPlayPause = { playPressed = true },
                        onStop = { stopPressed = true },
                        onRecord = { recordPressed = true },
                        onBpmChange = { if (it > transportState.bpm) bpmIncreased = true else bpmDecreased = true }
                    )
                }
            }
        }
        
        // Test keyboard shortcuts
        composeTestRule.onRoot().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.Spacebar)
            keyUp(androidx.compose.ui.input.key.Key.Spacebar)
        }
        assertTrue("Space should trigger play", playPressed)
        
        composeTestRule.onRoot().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.CtrlLeft)
            keyDown(androidx.compose.ui.input.key.Key.S)
            keyUp(androidx.compose.ui.input.key.Key.S)
            keyUp(androidx.compose.ui.input.key.Key.CtrlLeft)
        }
        assertTrue("Ctrl+S should trigger stop", stopPressed)
    }
    
    @Test
    fun accessibilityValidator_detectsIssues() {
        val validator = AccessibilityValidator()
        
        // Create a mock node with accessibility issues
        composeTestRule.setContent {
            MaterialTheme {
                // Button without content description (should fail)
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier.size(20.dp) // Too small (should fail)
                ) {
                    androidx.compose.material3.Text("Test")
                }
            }
        }
        
        val nodes = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val result = validator.validateAccessibility(nodes)
        
        assertTrue("Validator should detect issues", result is AccessibilityValidationResult.Invalid)
        
        if (result is AccessibilityValidationResult.Invalid) {
            assertTrue("Should detect touch target size issue", 
                result.issues.any { it.type == AccessibilityIssueType.TOUCH_TARGET_TOO_SMALL })
        }
    }
    
    @Test
    fun accessibilityTestSuite_generatesComprehensiveReport() {
        composeTestRule.setContent {
            MaterialTheme {
                TransportControlBar(
                    transportState = TransportState(),
                    onPlayPause = {},
                    onStop = {},
                    onRecord = {},
                    onBpmChange = {}
                )
            }
        }
        
        val report = accessibilityTestSuite.runFullAccessibilityTest(composeTestRule)
        
        assertNotNull("Report should be generated", report)
        assertTrue("Report should have tested nodes", report.totalNodes > 0)
        
        val reportText = report.generateReport()
        assertTrue("Report should contain summary", reportText.contains("Accessibility Test Report"))
        assertTrue("Report should contain node count", reportText.contains("Total nodes tested:"))
        
        if (report.hasIssues) {
            assertTrue("Report should list issues", reportText.contains("Issues by severity:"))
        } else {
            assertTrue("Report should show success", reportText.contains("All accessibility tests passed!"))
        }
    }
    
    @Test
    fun contrastChecker_providesAccurateMeasurements() {
        val checker = ColorContrastChecker()
        
        // Test known contrast ratios
        val whiteOnBlack = checker.calculateContrastRatio(Color.White, Color.Black)
        assertEquals("White on black should have maximum contrast", 21f, whiteOnBlack, 0.1f)
        
        val blackOnWhite = checker.calculateContrastRatio(Color.Black, Color.White)
        assertEquals("Black on white should have maximum contrast", 21f, blackOnWhite, 0.1f)
        
        val grayOnWhite = checker.calculateContrastRatio(Color.Gray, Color.White)
        assertTrue("Gray on white should have moderate contrast", grayOnWhite > 3f && grayOnWhite < 10f)
        
        // Test WCAG compliance
        assertTrue("White on black meets WCAG AAA", checker.meetsWCAG_AAA(Color.White, Color.Black))
        assertTrue("Black on white meets WCAG AAA", checker.meetsWCAG_AAA(Color.Black, Color.White))
        
        // Test validation results
        val validation = checker.validateContrast(Color.White, Color.Black, targetLevel = ContrastLevel.AAA)
        assertTrue("Validation should pass", validation.passes)
        assertEquals("Should achieve AAA level", ContrastLevel.AAA, validation.level)
    }
    
    @Test
    fun keyboardNavigationManager_handlesAllShortcuts() {
        val manager = KeyboardNavigationManager()
        var actionTriggered = ""
        
        // Test transport shortcuts
        val spaceEvent = androidx.compose.ui.input.key.KeyEvent(
            androidx.compose.ui.input.key.Key.Spacebar,
            androidx.compose.ui.input.key.KeyEventType.KeyDown
        )
        
        val handled = manager.handleTransportKeyEvents(
            keyEvent = spaceEvent,
            onPlay = { actionTriggered = "play" },
            onStop = { actionTriggered = "stop" },
            onRecord = { actionTriggered = "record" },
            onBpmIncrease = { actionTriggered = "bpm_up" },
            onBpmDecrease = { actionTriggered = "bpm_down" }
        )
        
        assertTrue("Space key should be handled", handled)
        assertEquals("Play action should be triggered", "play", actionTriggered)
        
        // Test drum pad navigation
        val arrowUpEvent = androidx.compose.ui.input.key.KeyEvent(
            androidx.compose.ui.input.key.Key.DirectionUp,
            androidx.compose.ui.input.key.KeyEventType.KeyDown
        )
        
        var selectedPad = -1
        val padHandled = manager.handleDrumPadKeyEvents(
            keyEvent = arrowUpEvent,
            currentPadIndex = 5,
            onPadSelect = { selectedPad = it },
            onPadTrigger = {},
            onPadOptions = {}
        )
        
        assertTrue("Arrow up should be handled", padHandled)
        assertEquals("Should select pad above", 1, selectedPad)
    }
}