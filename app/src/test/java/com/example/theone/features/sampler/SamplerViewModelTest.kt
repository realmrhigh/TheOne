package com.example.theone.features.sampler

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher // Explicit import for TestWatcher

@ExperimentalCoroutinesApi
class SamplerViewModelTest {

    // Rule for JUnit to use TestCoroutineDispatcher
    // This helps in controlling the execution of coroutines in tests
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule() // See MainCoroutineRule definition below

    private lateinit var viewModel: SamplerViewModel
    private lateinit var mockAudioEngine: AudioEngineControl
    private lateinit var mockProjectManager: ProjectManager

    // Mocked SampleMetadata for consistent testing
    private val fakeSampleMetadata = SamplerViewModel.SampleMetadata(
        id = "test_id",
        name = "test_sample",
        filePathUri = "fake/path/to/sample.wav"
    )

    @Before
    fun setUp() {
        // Create mocks for the dependencies
        mockAudioEngine = mockk(relaxed = true) // relaxed = true allows skipping `every { ... } returns ...` for all methods
        mockProjectManager = mockk(relaxed = true)

        // Create an instance of the ViewModel with the mocked dependencies
        viewModel = SamplerViewModel(mockAudioEngine, mockProjectManager)
    }

    @After
    fun tearDown() {
        unmockkAll() // Clear all mocks after each test
    }

    @Test
    fun `initial state is IDLE`() = runTest {
        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
    }

    @Test
    fun `startRecordingPressed without threshold success`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true

        viewModel.startRecordingPressed()

        assertEquals(RecordingState.RECORDING, viewModel.recordingState.first())
        coVerify { mockAudioEngine.startAudioRecording(any(), null) }
    }

    @Test
    fun `startRecordingPressed without threshold failure`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns false

        viewModel.startRecordingPressed()

        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
        assertEquals("Failed to start recording.", viewModel.userMessage.first())
    }

    @Test
    fun `startRecordingPressed with threshold enabled arms the viewModel`() = runTest {
        viewModel.toggleThresholdRecording(true)
        viewModel.startRecordingPressed()
        assertEquals(RecordingState.ARMED, viewModel.recordingState.first())
        assertEquals("Armed for threshold recording. Make some noise!", viewModel.userMessage.first())
    }

    @Test
    fun `stopRecordingPressed while recording success`() = runTest {
        // Start recording first
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed() // Puts state to RECORDING

        coEvery { mockAudioEngine.stopAudioRecording() } returns fakeSampleMetadata

        viewModel.stopRecordingPressed()

        assertEquals(RecordingState.REVIEWING, viewModel.recordingState.first())
        assertEquals("Recording stopped. Review your sample.", viewModel.userMessage.first())
        coVerify { mockAudioEngine.stopAudioRecording() }
    }

    @Test
    fun `stopRecordingPressed while recording but no metadata returned`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed()

        coEvery { mockAudioEngine.stopAudioRecording() } returns null // Simulate failure or no data

        viewModel.stopRecordingPressed()

        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
        assertEquals("Recording stopped or no audio data.", viewModel.userMessage.first())
    }


    @Test
    fun `saveRecording success`() = runTest {
        // Go to REVIEWING state first
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed()
        coEvery { mockAudioEngine.stopAudioRecording() } returns fakeSampleMetadata
        viewModel.stopRecordingPressed() // Now in REVIEWING with lastRecordedSampleMetadata set

        val newSampleName = "My New Sample"
        val expectedSavedMetadata = fakeSampleMetadata.copy(name = newSampleName)
        coEvery { mockProjectManager.addSampleToPool(newSampleName, fakeSampleMetadata.filePathUri, true) } returns expectedSavedMetadata

        viewModel.saveRecording(newSampleName, null)

        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
        assertEquals("Sample '${expectedSavedMetadata.name}' saved successfully!", viewModel.userMessage.first())
        coVerify { mockProjectManager.addSampleToPool(newSampleName, fakeSampleMetadata.filePathUri, true) }
    }

    @Test
    fun `saveRecording success with pad assignment (placeholder verification)`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed()
        coEvery { mockAudioEngine.stopAudioRecording() } returns fakeSampleMetadata
        viewModel.stopRecordingPressed()

        val newSampleName = "My Sample For Pad"
        val padToAssign = "Pad5"
        val expectedSavedMetadata = fakeSampleMetadata.copy(name = newSampleName)
        coEvery { mockProjectManager.addSampleToPool(newSampleName, fakeSampleMetadata.filePathUri, true) } returns expectedSavedMetadata

        viewModel.saveRecording(newSampleName, padToAssign)

        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
        assertEquals("Sample '${expectedSavedMetadata.name}' saved and assigned to $padToAssign (Placeholder).", viewModel.userMessage.first())
    }


    @Test
    fun `saveRecording failure`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed()
        coEvery { mockAudioEngine.stopAudioRecording() } returns fakeSampleMetadata
        viewModel.stopRecordingPressed()

        val newSampleName = "Failed Sample"
        coEvery { mockProjectManager.addSampleToPool(any(), any(), any()) } returns null // Simulate failure

        viewModel.saveRecording(newSampleName, null)

        assertEquals(RecordingState.REVIEWING, viewModel.recordingState.first()) // Should remain in reviewing
        assertEquals("Failed to save sample.", viewModel.userMessage.first())
    }

    @Test
    fun `discardRecording success`() = runTest {
        coEvery { mockAudioEngine.startAudioRecording(any(), any()) } returns true
        viewModel.startRecordingPressed()
        coEvery { mockAudioEngine.stopAudioRecording() } returns fakeSampleMetadata
        viewModel.stopRecordingPressed() // State is REVIEWING

        viewModel.discardRecording()

        assertEquals(RecordingState.IDLE, viewModel.recordingState.first())
        assertEquals("Recording discarded.", viewModel.userMessage.first())
    }
}

// Helper class for managing CoroutineDispatchers in tests
// Standard MainCoroutineRule from kotlinx-coroutines-test documentation
@ExperimentalCoroutinesApi
class MainCoroutineRule(
    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher() { // TestWatcher is from JUnit

    override fun starting(description: org.junit.runner.Description?) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: org.junit.runner.Description?) {
        super.finished(description)
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }
}

// If TestCoroutineDispatcher is not available (older kotlinx-coroutines-test)
// you might need a slightly different setup for MainCoroutineRule, or use runTest directly.
// For example, with kotlinx-coroutines-test >= 1.6.0, TestCoroutineDispatcher is deprecated.
// You'd use something like:
// val testDispatcher = StandardTestDispatcher()
// or
// val testDispatcher = UnconfinedTestDispatcher()

// For simplicity with the current environment, the provided MainCoroutineRule with TestCoroutineDispatcher is common.
// If issues arise, it might be due to library versions. The subtask should still create the file.
// The key is that `runTest` from kotlinx-coroutines-test is used for coroutine tests.
