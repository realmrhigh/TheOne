package com.high.theone.features.sampling

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the PadGrid composable functionality.
 * Verifies pad rendering, touch handling, and visual feedback.
 * 
 * Requirements: 2.1 (pad management), 3.1 (touch handling), 6.1 (visual feedback)
 */
@RunWith(AndroidJUnit4::class)
class PadGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun padGrid_displaysCorrectNumberOfPads() {
        // Given: A list of pad states
        val pads = List(16) { index ->
            PadState(
                index = index,
                hasAssignedSample = index % 3 == 0, // Some pads have samples
                sampleName = if (index % 3 == 0) "Sample $index" else null
            )
        }
        
        var tapCount = 0
        var longPressCount = 0
        
        // When: PadGrid is displayed
        composeTestRule.setContent {
            PadGrid(
                pads = pads,
                onPadTap = { _, _ -> tapCount++ },
                onPadLongPress = { longPressCount++ }
            )
        }
        
        // Then: All 16 pads should be visible
        for (i in 1..16) {
            composeTestRule.onNodeWithText("$i").assertExists()
        }
    }
    
    @Test
    fun padGrid_showsCorrectPadStates() {
        // Given: Pads with different states
        val pads = listOf(
            PadState(index = 0, hasAssignedSample = false), // Empty pad
            PadState(index = 1, hasAssignedSample = true, sampleName = "Kick", isPlaying = false), // Loaded pad
            PadState(index = 2, hasAssignedSample = true, sampleName = "Snare", isPlaying = true), // Playing pad
            PadState(index = 3, isLoading = true) // Loading pad
        ) + List(12) { index -> PadState(index = index + 4) } // Fill remaining pads
        
        // When: PadGrid is displayed
        composeTestRule.setContent {
            PadGrid(
                pads = pads,
                onPadTap = { _, _ -> },
                onPadLongPress = { }
            )
        }
        
        // Then: Pad states should be displayed correctly
        composeTestRule.onNodeWithText("Empty").assertExists()
        composeTestRule.onNodeWithText("Kick").assertExists()
        composeTestRule.onNodeWithText("Snare").assertExists()
        composeTestRule.onNodeWithText("Loading...").assertExists()
    }
    
    @Test
    fun padGrid_handlesDisabledState() {
        // Given: A pad grid that is disabled
        val pads = List(16) { index ->
            PadState(
                index = index,
                hasAssignedSample = true,
                sampleName = "Sample $index"
            )
        }
        
        var tapCount = 0
        
        // When: PadGrid is displayed as disabled
        composeTestRule.setContent {
            PadGrid(
                pads = pads,
                onPadTap = { _, _ -> tapCount++ },
                onPadLongPress = { },
                enabled = false
            )
        }
        
        // Then: Pads should still be visible but interactions should be disabled
        composeTestRule.onNodeWithText("1").assertExists()
        composeTestRule.onNodeWithText("Sample 0").assertExists()
        
        // Tap should not trigger callback when disabled
        composeTestRule.onNodeWithText("1").performClick()
        assert(tapCount == 0) { "Tap should not be handled when disabled" }
    }
    
    @Test
    fun padGrid_handlesEmptyPadsList() {
        // Given: An empty pads list
        val pads = emptyList<PadState>()
        
        // When: PadGrid is displayed
        composeTestRule.setContent {
            PadGrid(
                pads = pads,
                onPadTap = { _, _ -> },
                onPadLongPress = { }
            )
        }
        
        // Then: Should still display 16 pads with default states
        for (i in 1..16) {
            composeTestRule.onNodeWithText("$i").assertExists()
        }
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(16)
    }
    
    @Test
    fun padGrid_handlesPartialPadsList() {
        // Given: A partial pads list (less than 16)
        val pads = List(8) { index ->
            PadState(
                index = index,
                hasAssignedSample = true,
                sampleName = "Sample $index"
            )
        }
        
        // When: PadGrid is displayed
        composeTestRule.setContent {
            PadGrid(
                pads = pads,
                onPadTap = { _, _ -> },
                onPadLongPress = { }
            )
        }
        
        // Then: Should display 16 pads total (8 with samples, 8 empty)
        for (i in 1..16) {
            composeTestRule.onNodeWithText("$i").assertExists()
        }
        
        // First 8 should have samples
        for (i in 0..7) {
            composeTestRule.onNodeWithText("Sample $i").assertExists()
        }
        
        // Remaining should be empty
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(8)
    }
}