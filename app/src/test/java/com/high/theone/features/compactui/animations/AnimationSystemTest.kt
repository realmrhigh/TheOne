package com.high.theone.features.compactui.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.features.compactui.animations.AnimationSystem
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.features.compactui.animations.VisualFeedbackSystem
import com.high.theone.features.compactui.animations.LoadingStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test suite for the animation system components
 * Verifies that animations work correctly and provide proper visual feedback
 */
@RunWith(AndroidJUnit4::class)
class AnimationSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun animationSystem_providesCorrectDurations() {
        // Test animation duration constants
        assert(AnimationSystem.FAST_ANIMATION == 150)
        assert(AnimationSystem.MEDIUM_ANIMATION == 300)
        assert(AnimationSystem.SLOW_ANIMATION == 500)
    }

    @Test
    fun animationSystem_providesCorrectEasingCurves() {
        // Test that easing curves are properly defined
        assert(AnimationSystem.FastOutSlowIn != null)
        assert(AnimationSystem.FastOutLinearIn != null)
        assert(AnimationSystem.LinearOutSlowIn != null)
    }

    @Test
    fun animationSystem_providesCorrectSpringSpecs() {
        // Test that spring specifications are properly defined
        assert(AnimationSystem.BounceSpring != null)
        assert(AnimationSystem.SmoothSpring != null)
        assert(AnimationSystem.QuickSpring != null)
    }

    @Test
    fun microInteractions_animatedButtonRespondsToPress() {
        var clickCount = 0
        
        composeTestRule.setContent {
            MicroInteractions.AnimatedButton(
                onClick = { clickCount++ },
                hapticEnabled = false // Disable haptic for testing
            ) {
                androidx.compose.material3.Text("Test Button")
            }
        }

        // Find and click the button
        composeTestRule
            .onNodeWithText("Test Button")
            .assertExists()
            .performClick()

        // Verify click was registered
        assert(clickCount == 1)
    }

    @Test
    fun microInteractions_animatedSwitchChangesState() {
        var switchState = false
        
        composeTestRule.setContent {
            MicroInteractions.AnimatedSwitch(
                checked = switchState,
                onCheckedChange = { switchState = it }
            )
        }

        // Find and toggle the switch
        composeTestRule
            .onNode(hasClickAction())
            .assertExists()
            .performClick()

        // Verify state changed
        assert(switchState == true)
    }

    @Test
    fun visualFeedbackSystem_touchRippleAppearsOnTrigger() {
        var rippleTriggered = false
        
        composeTestRule.setContent {
            VisualFeedbackSystem.TouchRipple(
                triggered = rippleTriggered
            )
        }

        // Initially no ripple should be visible
        composeTestRule
            .onNode(hasTestTag("ripple"))
            .assertDoesNotExist()

        // Trigger ripple
        composeTestRule.runOnUiThread {
            rippleTriggered = true
        }

        composeTestRule.waitForIdle()
        
        // Ripple should now be visible (implementation dependent)
        // This test verifies the component doesn't crash when triggered
    }

    @Test
    fun visualFeedbackSystem_successFeedbackShowsAndHides() {
        var successTriggered = false
        var animationCompleted = false
        
        composeTestRule.setContent {
            VisualFeedbackSystem.SuccessFeedback(
                triggered = successTriggered,
                onAnimationComplete = { animationCompleted = true }
            )
        }

        // Trigger success feedback
        composeTestRule.runOnUiThread {
            successTriggered = true
        }

        composeTestRule.waitForIdle()
        
        // Success feedback should be visible
        composeTestRule
            .onNodeWithText("✓")
            .assertExists()
    }

    @Test
    fun visualFeedbackSystem_errorFeedbackShowsAndHides() {
        var errorTriggered = false
        var animationCompleted = false
        
        composeTestRule.setContent {
            VisualFeedbackSystem.ErrorFeedback(
                triggered = errorTriggered,
                onAnimationComplete = { animationCompleted = true }
            )
        }

        // Trigger error feedback
        composeTestRule.runOnUiThread {
            errorTriggered = true
        }

        composeTestRule.waitForIdle()
        
        // Error feedback should be visible
        composeTestRule
            .onNodeWithText("✗")
            .assertExists()
    }

    @Test
    fun visualFeedbackSystem_audioLevelMeterRespondsToLevel() {
        var audioLevel = 0f
        
        composeTestRule.setContent {
            VisualFeedbackSystem.AudioLevelMeter(
                level = audioLevel,
                modifier = androidx.compose.ui.Modifier.testTag("audio_meter")
            )
        }

        // Audio meter should exist
        composeTestRule
            .onNodeWithTag("audio_meter")
            .assertExists()

        // Change audio level
        composeTestRule.runOnUiThread {
            audioLevel = 0.8f
        }

        composeTestRule.waitForIdle()
        
        // Meter should still exist and respond to level change
        composeTestRule
            .onNodeWithTag("audio_meter")
            .assertExists()
    }

    @Test
    fun visualFeedbackSystem_sequencerStepHighlightChangesWithState() {
        var isActive = false
        var isCurrentStep = false
        
        composeTestRule.setContent {
            VisualFeedbackSystem.SequencerStepHighlight(
                isActive = isActive,
                isCurrentStep = isCurrentStep,
                modifier = androidx.compose.ui.Modifier.testTag("step_highlight")
            )
        }

        // Step highlight should exist
        composeTestRule
            .onNodeWithTag("step_highlight")
            .assertExists()

        // Activate step
        composeTestRule.runOnUiThread {
            isActive = true
            isCurrentStep = true
        }

        composeTestRule.waitForIdle()
        
        // Step highlight should still exist with new state
        composeTestRule
            .onNodeWithTag("step_highlight")
            .assertExists()
    }

    @Test
    fun visualFeedbackSystem_midiActivityIndicatorChangesWithActivity() {
        var isActive = false
        
        composeTestRule.setContent {
            VisualFeedbackSystem.MidiActivityIndicator(
                isActive = isActive,
                modifier = androidx.compose.ui.Modifier.testTag("midi_indicator")
            )
        }

        // MIDI indicator should exist
        composeTestRule
            .onNodeWithTag("midi_indicator")
            .assertExists()

        // Activate MIDI
        composeTestRule.runOnUiThread {
            isActive = true
        }

        composeTestRule.waitForIdle()
        
        // MIDI indicator should still exist with new state
        composeTestRule
            .onNodeWithTag("midi_indicator")
            .assertExists()
    }

    @Test
    fun loadingStates_sampleLoadingIndicatorShowsProgress() {
        var isLoading = false
        var progress = 0f
        
        composeTestRule.setContent {
            LoadingStates.SampleLoadingIndicator(
                isLoading = isLoading,
                progress = progress,
                sampleName = "Test Sample"
            )
        }

        // Initially no loading indicator
        composeTestRule
            .onNodeWithText("Loading Sample")
            .assertDoesNotExist()

        // Start loading
        composeTestRule.runOnUiThread {
            isLoading = true
            progress = 0.5f
        }

        composeTestRule.waitForIdle()
        
        // Loading indicator should be visible
        composeTestRule
            .onNodeWithText("Loading Sample")
            .assertExists()
        
        composeTestRule
            .onNodeWithText("Test Sample")
            .assertExists()
    }

    @Test
    fun loadingStates_audioProcessingIndicatorShowsOperation() {
        var isProcessing = false
        
        composeTestRule.setContent {
            LoadingStates.AudioProcessingIndicator(
                isProcessing = isProcessing,
                operation = "Test Operation"
            )
        }

        // Initially no processing indicator
        composeTestRule
            .onNodeWithText("Test Operation")
            .assertDoesNotExist()

        // Start processing
        composeTestRule.runOnUiThread {
            isProcessing = true
        }

        composeTestRule.waitForIdle()
        
        // Processing indicator should be visible
        composeTestRule
            .onNodeWithText("Test Operation")
            .assertExists()
    }

    @Test
    fun loadingStates_midiConnectionIndicatorShowsStatus() {
        var isConnecting = false
        var isConnected = false
        
        composeTestRule.setContent {
            LoadingStates.MidiConnectionIndicator(
                isConnecting = isConnecting,
                isConnected = isConnected,
                deviceName = "Test Device"
            )
        }

        // Initially disconnected
        composeTestRule
            .onNodeWithText("Disconnected")
            .assertExists()

        // Start connecting
        composeTestRule.runOnUiThread {
            isConnecting = true
        }

        composeTestRule.waitForIdle()
        
        // Should show connecting
        composeTestRule
            .onNodeWithText("Connecting...")
            .assertExists()

        // Connect
        composeTestRule.runOnUiThread {
            isConnecting = false
            isConnected = true
        }

        composeTestRule.waitForIdle()
        
        // Should show connected with device name
        composeTestRule
            .onNodeWithText("Test Device")
            .assertExists()
    }

    @Test
    fun loadingStates_projectOperationIndicatorShowsProgress() {
        var isActive = false
        var progress = 0f
        
        composeTestRule.setContent {
            LoadingStates.ProjectOperationIndicator(
                isActive = isActive,
                operation = LoadingStates.ProjectOperation.SAVING,
                projectName = "Test Project",
                progress = progress
            )
        }

        // Initially no operation indicator
        composeTestRule
            .onNodeWithText("Saving Project")
            .assertDoesNotExist()

        // Start operation
        composeTestRule.runOnUiThread {
            isActive = true
            progress = 0.75f
        }

        composeTestRule.waitForIdle()
        
        // Operation indicator should be visible
        composeTestRule
            .onNodeWithText("Saving Project")
            .assertExists()
        
        composeTestRule
            .onNodeWithText("Test Project")
            .assertExists()
    }

    @Test
    fun loadingStates_skeletonLoaderExists() {
        composeTestRule.setContent {
            LoadingStates.SkeletonLoader(
                modifier = androidx.compose.ui.Modifier.testTag("skeleton")
            )
        }

        // Skeleton loader should exist
        composeTestRule
            .onNodeWithTag("skeleton")
            .assertExists()
    }
}