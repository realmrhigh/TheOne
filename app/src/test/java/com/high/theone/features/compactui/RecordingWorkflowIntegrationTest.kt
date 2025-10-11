package com.high.theone.features.compactui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.audio.AudioEngineControl
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
 * Integration tests for the complete recording workflow from UI to audio engine
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RecordingWorkflowIntegrationTest {

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

    private lateinit var viewModel: CompactMainViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Setup mock sampling view model
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState())
        )
        
        viewModel = CompactMainViewModel(
            audioEngine = mockAudioEngine,
            samplingViewModel = mockSamplingViewModel,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun testCompleteRecordingWorkflow() = runBlockingTest {
        // Setup initial state
        val initialState = CompactUIState(
            recordingState = IntegratedRecordingState(canStartRecording = true)
        )
        
        composeTestRule.setContent {
            CompactMainScreen(
                state = initialState,
                onRecordingStart = { viewModel.startRecording() },
                onRecordingStop = { viewModel.stopRecording() },
                onPadAssignment = { padId -> viewModel.assignRecordedSampleToPad(padId) }
            )
        }

        // Step 1: Start recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertIsDisplayed()
            .performClick()

        // Verify recording started
        verify(mockSamplingViewModel).startRecording()
        
        // Step 2: Simulate recording in progress
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState(
                isRecording = true,
                durationMs = 1500L,
                peakLevel = 0.7f,
                averageLevel = 0.4f
            ))
        )

        // Verify UI shows recording state
        composeTestRule.onNodeWithText("00:01")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription("Recording Level Meter")
            .assertIsDisplayed()

        // Step 3: Stop recording
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .performClick()

        verify(mockSamplingViewModel).stopRecording()

        // Step 4: Simulate recording completed with sample
        val sampleId = "test_sample_123"
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState(
                isRecording = false,
                recordedSampleId = sampleId,
                isProcessing = false
            ))
        )

        // Step 5: Verify pad assignment UI appears
        composeTestRule.onNodeWithText("Assign to Pad")
            .assertIsDisplayed()

        // Step 6: Assign to pad
        composeTestRule.onNodeWithContentDescription("Pad 1")
            .performClick()

        // Verify assignment
        verify(mockAudioEngine).assignSampleToPad(0, sampleId)
        
        // Step 7: Verify UI returns to normal state
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertIsDisplayed()
    }

    @Test
    fun testRecordingWithLevelMeters() = runBlockingTest {
        val recordingState = MutableStateFlow(RecordingState(
            isRecording = true,
            durationMs = 2000L,
            peakLevel = 0.8f,
            averageLevel = 0.5f
        ))
        
        `when`(mockSamplingViewModel.recordingState).thenReturn(recordingState)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        isRecording = true,
                        durationMs = 2000L,
                        peakLevel = 0.8f,
                        averageLevel = 0.5f
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = {}
            )
        }

        // Verify level meters are displayed and updating
        composeTestRule.onNodeWithContentDescription("Peak Level: 80%")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription("Average Level: 50%")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithText("00:02")
            .assertIsDisplayed()
    }

    @Test
    fun testRecordingDurationDisplay() = runBlockingTest {
        val durations = listOf(0L, 1000L, 5000L, 30000L, 60000L)
        val expectedFormats = listOf("00:00", "00:01", "00:05", "00:30", "01:00")

        durations.forEachIndexed { index, duration ->
            val recordingState = MutableStateFlow(RecordingState(
                isRecording = true,
                durationMs = duration
            ))
            
            `when`(mockSamplingViewModel.recordingState).thenReturn(recordingState)

            composeTestRule.setContent {
                CompactMainScreen(
                    state = CompactUIState(
                        recordingState = IntegratedRecordingState(
                            isRecording = true,
                            durationMs = duration
                        )
                    ),
                    onRecordingStart = {},
                    onRecordingStop = {},
                    onPadAssignment = {}
                )
            }

            composeTestRule.onNodeWithText(expectedFormats[index])
                .assertIsDisplayed()
        }
    }

    @Test
    fun testQuickPadAssignmentFlow() = runBlockingTest {
        val sampleId = "recorded_sample_456"
        
        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(
                    recordingState = IntegratedRecordingState(
                        recordedSampleId = sampleId,
                        availablePadsForAssignment = listOf("0", "1", "2", "3"),
                        isAssignmentMode = true
                    )
                ),
                onRecordingStart = {},
                onRecordingStop = {},
                onPadAssignment = { padId -> 
                    verify(mockAudioEngine).assignSampleToPad(padId.toInt(), sampleId)
                }
            )
        }

        // Verify available pads are highlighted
        composeTestRule.onNodeWithContentDescription("Available Pad 0")
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithContentDescription("Available Pad 1")
            .assertIsDisplayed()

        // Test assignment
        composeTestRule.onNodeWithContentDescription("Available Pad 0")
            .performClick()

        // Verify assignment completed
        composeTestRule.onNodeWithText("Sample assigned to Pad 1")
            .assertIsDisplayed()
    }

    @Test
    fun testRecordingStateTransitions() = runBlockingTest {
        val stateFlow = MutableStateFlow(RecordingState())
        `when`(mockSamplingViewModel.recordingState).thenReturn(stateFlow)

        composeTestRule.setContent {
            CompactMainScreen(
                state = CompactUIState(),
                onRecordingStart = { 
                    stateFlow.value = RecordingState(isRecording = true)
                },
                onRecordingStop = { 
                    stateFlow.value = RecordingState(
                        isRecording = false,
                        isProcessing = true
                    )
                },
                onPadAssignment = {}
            )
        }

        // Initial state - ready to record
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .assertIsDisplayed()

        // Start recording
        composeTestRule.onNodeWithContentDescription("Start Recording")
            .performClick()

        // Recording state
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .assertIsDisplayed()

        // Stop recording
        composeTestRule.onNodeWithContentDescription("Stop Recording")
            .performClick()

        // Processing state
        composeTestRule.onNodeWithContentDescription("Processing Recording")
            .assertIsDisplayed()

        // Complete processing
        stateFlow.value = RecordingState(
            isRecording = false,
            isProcessing = false,
            recordedSampleId = "sample_789"
        )

        // Assignment mode
        composeTestRule.onNodeWithText("Assign to Pad")
            .assertIsDisplayed()
    }
}