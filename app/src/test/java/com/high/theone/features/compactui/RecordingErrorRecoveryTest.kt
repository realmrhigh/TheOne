package com.high.theone.features.compactui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.compactui.error.*
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import javax.inject.Inject

/**
 * Tests for recording error scenarios and recovery mechanisms
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RecordingErrorRecoveryTest {

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

    @Mock
    private lateinit var mockPermissionManager: PermissionManager

    @Mock
    private lateinit var mockAudioEngineRecovery: AudioEngineRecovery

    @Mock
    private lateinit var mockStorageManager: StorageManager

    private val testDispatcher = TestCoroutineDispatcher()
    private lateinit var errorHandlingSystem: ErrorHandlingSystem

    @Before
    fun setup() {
        hiltRule.inject()
        
        errorHandlingSystem = ErrorHandlingSystem(
            permissionManager = mockPermissionManager,
            audioEngineRecovery = mockAudioEngineRecovery,
            storageManager = mockStorageManager
        )
    }

    @Test
    fun testPermissionDeniedErrorAndRecovery() = runBlockingTest {
        // Setup permission denied error
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.PERMISSION_DENIED,
                message = "Microphone permission is required for recording",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.REQUEST_PERMISSION
            )
        )

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = errorState),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    when (action) {
                        RecordingRecoveryAction.REQUEST_PERMISSION -> {
                            // Simulate permission granted
                            `when`(mockPermissionManager.requestMicrophonePermission())
                                .thenReturn(true)
                        }
                        else -> {}
                    }
                }
            )
        }

        // Verify error is displayed
        composeTestRule.onNodeWithText("Microphone permission is required for recording")
            .assertIsDisplayed()

        // Verify recovery button is shown
        composeTestRule.onNodeWithText("Grant Permission")
            .assertIsDisplayed()

        // Test recovery action
        composeTestRule.onNodeWithText("Grant Permission")
            .performClick()

        // Verify permission request was triggered
        verify(mockPermissionManager).requestMicrophonePermission()

        // Simulate successful permission grant
        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(canStartRecording = true)
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {}
            )
        }

        // Verify recording is now available
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertIsDisplayed()
    }

    @Test
    fun testAudioEngineFailureAndRecovery() = runBlockingTest {
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.AUDIO_ENGINE_FAILURE,
                message = "Audio engine failed to initialize",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.RESTART_AUDIO_ENGINE
            )
        )

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = errorState),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    when (action) {
                        RecordingRecoveryAction.RESTART_AUDIO_ENGINE -> {
                            `when`(mockAudioEngineRecovery.restartEngine())
                                .thenReturn(true)
                        }
                        else -> {}
                    }
                }
            )
        }

        // Verify error message
        composeTestRule.onNodeWithText("Audio engine failed to initialize")
            .assertIsDisplayed()

        // Test recovery
        composeTestRule.onNodeWithText("Restart Audio Engine")
            .performClick()

        verify(mockAudioEngineRecovery).restartEngine()
    }

    @Test
    fun testStorageErrorAndRecovery() = runBlockingTest {
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.STORAGE_FAILURE,
                message = "Insufficient storage space for recording",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.FREE_STORAGE_SPACE
            )
        )

        `when`(mockStorageManager.getAvailableSpace()).thenReturn(50L * 1024 * 1024) // 50MB
        `when`(mockStorageManager.getRequiredSpace()).thenReturn(100L * 1024 * 1024) // 100MB

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = errorState),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    when (action) {
                        RecordingRecoveryAction.FREE_STORAGE_SPACE -> {
                            `when`(mockStorageManager.clearTemporaryFiles())
                                .thenReturn(true)
                        }
                        else -> {}
                    }
                }
            )
        }

        // Verify storage error details
        composeTestRule.onNodeWithText("Insufficient storage space for recording")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Available: 50 MB, Required: 100 MB")
            .assertIsDisplayed()

        // Test storage cleanup
        composeTestRule.onNodeWithText("Free Storage Space")
            .performClick()

        verify(mockStorageManager).clearTemporaryFiles()
    }

    @Test
    fun testMicrophoneUnavailableError() = runBlockingTest {
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.MICROPHONE_UNAVAILABLE,
                message = "Microphone is being used by another app",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.RETRY_RECORDING
            )
        )

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = errorState),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    when (action) {
                        RecordingRecoveryAction.RETRY_RECORDING -> {
                            // Simulate retry attempt
                        }
                        else -> {}
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Microphone is being used by another app")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Try Again")
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun testSystemOverloadErrorAndOptimization() = runBlockingTest {
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.SYSTEM_OVERLOAD,
                message = "System is under heavy load, recording may be affected",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.REDUCE_QUALITY
            )
        )

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = errorState),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    when (action) {
                        RecordingRecoveryAction.REDUCE_QUALITY -> {
                            // Apply performance optimizations
                        }
                        else -> {}
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("System is under heavy load, recording may be affected")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Optimize Performance")
            .performClick()
    }

    @Test
    fun testMultipleErrorRecoveryAttempts() = runBlockingTest {
        var attemptCount = 0
        
        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        error = RecordingError(
                            type = RecordingErrorType.AUDIO_ENGINE_FAILURE,
                            message = "Audio engine failed to initialize (Attempt ${attemptCount + 1})",
                            isRecoverable = attemptCount < 3,
                            recoveryAction = if (attemptCount < 3) 
                                RecordingRecoveryAction.RESTART_AUDIO_ENGINE 
                            else null
                        )
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorRecovery = { action ->
                    attemptCount++
                    `when`(mockAudioEngineRecovery.restartEngine())
                        .thenReturn(attemptCount >= 3)
                }
            )
        }

        // First attempt
        composeTestRule.onNodeWithText("Restart Audio Engine")
            .performClick()

        // Second attempt
        composeTestRule.onNodeWithText("Restart Audio Engine")
            .performClick()

        // Third attempt
        composeTestRule.onNodeWithText("Restart Audio Engine")
            .performClick()

        // After max attempts, should show different UI
        composeTestRule.onNodeWithText("Contact Support")
            .assertIsDisplayed()
    }

    @Test
    fun testErrorRecoveryWithUserGuidance() = runBlockingTest {
        val errorState = IntegratedRecordingState(
            error = RecordingError(
                type = RecordingErrorType.MICROPHONE_UNAVAILABLE,
                message = "Microphone access blocked",
                isRecoverable = true,
                recoveryAction = RecordingRecoveryAction.REQUEST_PERMISSION
            )
        )

        composeTestRule.setContent {
            ErrorRecoveryUI(
                error = errorState.error!!,
                onRecoveryAction = { action ->
                    // Handle recovery action
                },
                onDismiss = {}
            )
        }

        // Verify detailed guidance is shown
        composeTestRule.onNodeWithText("To fix this issue:")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("1. Go to Settings > Apps > TheOne")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("2. Enable Microphone permission")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("3. Return to the app and try again")
            .assertIsDisplayed()
    }

    @Test
    fun testErrorStateClearing() = runBlockingTest {
        val stateFlow = MutableStateFlow(
            IntegratedRecordingState(
                error = RecordingError(
                    type = RecordingErrorType.PERMISSION_DENIED,
                    message = "Permission denied",
                    isRecoverable = true,
                    recoveryAction = RecordingRecoveryAction.REQUEST_PERMISSION
                )
            )
        )

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(recordingState = stateFlow.value),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {},
                onErrorDismiss = {
                    stateFlow.value = IntegratedRecordingState(canStartRecording = true)
                }
            )
        }

        // Verify error is shown
        composeTestRule.onNodeWithText("Permission denied")
            .assertIsDisplayed()

        // Dismiss error
        composeTestRule.onNodeWithContentDescription("Dismiss Error")
            .performClick()

        // Verify error is cleared and normal UI is restored
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertIsDisplayed()
    }
}