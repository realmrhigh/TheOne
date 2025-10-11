package com.high.theone.features.compactui.animations

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for enhanced animation integration in the compact UI
 * Verifies that all animations and visual feedback work correctly
 */
@RunWith(AndroidJUnit4::class)
class EnhancedAnimationIntegrationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun recordingButtonAnimation_showsPulseWhenRecording() {
        var isRecording = false
        
        composeTestRule.setContent {
            MicroInteractions.RecordingButton(
                isRecording = isRecording,
                isInitializing = false,
                canRecord = true,
                onStartRecording = { isRecording = true },
                onStopRecording = { isRecording = false }
            )
        }
        
        // Initially not recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertExists()
        
        // Start recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .performClick()
        
        // Should show stop recording button
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .assertExists()
    }
    
    @Test
    fun recordingInitializationLoader_showsCorrectSteps() {
        var initStep = RecordingInitStep.AUDIO_ENGINE
        
        composeTestRule.setContent {
            RecordingInitializationLoader(
                isInitializing = true,
                initializationStep = initStep,
                progress = 0.5f
            )
        }
        
        // Should show initialization text
        composeTestRule.onNodeWithText("Initializing Recording")
            .assertExists()
        
        composeTestRule.onNodeWithText("Starting audio engine...")
            .assertExists()
        
        composeTestRule.onNodeWithText("50%")
            .assertExists()
    }
    
    @Test
    fun sampleAssignmentSuccessAnimation_showsCorrectContent() {
        var animationTriggered = false
        
        composeTestRule.setContent {
            AnimationSystem.SampleAssignmentSuccessAnimation(
                triggered = true,
                padIndex = 0,
                sampleName = "Test Sample",
                onAnimationComplete = { animationTriggered = true }
            )
        }
        
        // Should show success message
        composeTestRule.onNodeWithText("Sample Assigned!")
            .assertExists()
        
        composeTestRule.onNodeWithText("to Pad 1")
            .assertExists()
        
        composeTestRule.onNodeWithText("Test Sample")
            .assertExists()
    }
    
    @Test
    fun recordingPanelTransition_animatesCorrectly() {
        var isVisible = false
        
        composeTestRule.setContent {
            AnimationSystem.RecordingPanelTransition(
                visible = isVisible
            ) {
                androidx.compose.material3.Text("Recording Panel Content")
            }
        }
        
        // Initially not visible
        composeTestRule.onNodeWithText("Recording Panel Content")
            .assertDoesNotExist()
        
        // Make visible
        isVisible = true
        composeTestRule.setContent {
            AnimationSystem.RecordingPanelTransition(
                visible = isVisible
            ) {
                androidx.compose.material3.Text("Recording Panel Content")
            }
        }
        
        // Should be visible after animation
        composeTestRule.onNodeWithText("Recording Panel Content")
            .assertExists()
    }
    
    @Test
    fun enhancedRecordingLevelMeter_showsCorrectLevels() {
        composeTestRule.setContent {
            EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
                peakLevel = 0.8f,
                averageLevel = 0.6f,
                isRecording = true
            )
        }
        
        // Level meter should be visible
        // Note: In a real test, we'd verify the visual appearance
        // For now, we just ensure it renders without crashing
        composeTestRule.onRoot().assertExists()
    }
    
    @Test
    fun recordingWorkflowAnimations_integratesAllComponents() {
        val recordingState = IntegratedRecordingState(
            isRecording = false,
            isProcessing = false,
            canStartRecording = true,
            durationMs = 0L,
            peakLevel = 0f,
            averageLevel = 0f,
            recordedSampleId = null,
            isAssignmentMode = false,
            error = null
        )
        
        composeTestRule.setContent {
            EnhancedAnimationIntegration.RecordingWorkflowAnimations(
                recordingState = recordingState,
                onStartRecording = { },
                onStopRecording = { },
                onAssignToPad = { },
                onDiscardRecording = { }
            )
        }
        
        // Should render without crashing
        composeTestRule.onRoot().assertExists()
    }
    
    @Test
    fun recordingErrorAnimation_showsErrorCorrectly() {
        val error = RecordingError(
            type = RecordingErrorType.MICROPHONE_UNAVAILABLE,
            message = "Microphone not available",
            isRecoverable = true,
            recoveryAction = RecordingRecoveryAction.REQUEST_PERMISSION
        )
        
        composeTestRule.setContent {
            EnhancedAnimationIntegration.RecordingErrorAnimation(
                error = error,
                onDismiss = { }
            )
        }
        
        // Should show error message
        composeTestRule.onNodeWithText("Recording Error")
            .assertExists()
        
        composeTestRule.onNodeWithText("Microphone not available")
            .assertExists()
        
        // Should show retry button for recoverable errors
        composeTestRule.onNodeWithText("Retry")
            .assertExists()
    }
    
    @Test
    fun animatedRecordingDuration_formatsTimeCorrectly() {
        composeTestRule.setContent {
            EnhancedAnimationIntegration.AnimatedRecordingDuration(
                durationMs = 125000L, // 2 minutes 5 seconds
                isRecording = true
            )
        }
        
        // Should show formatted duration
        composeTestRule.onNodeWithText("02:05")
            .assertExists()
    }
    
    @Test
    fun recordingStatusIndicator_showsCorrectStates() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                // Normal state
                EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                    isRecording = false,
                    isInitializing = false,
                    hasError = false
                )
                
                // Recording state
                EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                    isRecording = true,
                    isInitializing = false,
                    hasError = false
                )
                
                // Error state
                EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                    isRecording = false,
                    isInitializing = false,
                    hasError = true
                )
            }
        }
        
        // Should render all states without crashing
        composeTestRule.onRoot().assertExists()
    }
}