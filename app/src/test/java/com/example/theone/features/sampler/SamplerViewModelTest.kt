package com.example.theone.features.sampler

import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.Sample
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SamplerViewModelTest {

    // Rule for Main dispatcher substitution (JUnit 4)
    // @get:Rule
    // val mainDispatcherRule = MainDispatcherRule() // See helper class below

    private lateinit var viewModel: SamplerViewModel
    private lateinit var mockProjectManager: ProjectManager
    private lateinit var mockAudioEngine: AudioEngine

    // TestDispatcher for controlling coroutine execution
    private val testDispatcher = StandardTestDispatcher()


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for viewModelScope

        mockProjectManager = mock()
        mockAudioEngine = mock()

        // Mock the samplePool StateFlow in ProjectManager
        val mockSamplePoolFlow = MutableStateFlow<List<SampleMetadata>>(emptyList())
        whenever(mockProjectManager.samplePool).thenReturn(mockSamplePoolFlow)

        viewModel = SamplerViewModel(mockAudioEngine, mockProjectManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher
    }

    @Test
    fun `loadSample successfully loads and registers sample`() = runTest(testDispatcher) {
        val testUri = "test://sample.wav"
        val dummyMetadata = SampleMetadata(uri = testUri, duration = 1000L, name = "Test Sample")
        val dummyAudioData = FloatArray(100)
        val dummySample = Sample(metadata = dummyMetadata, audioData = dummyAudioData)

        // Mock ProjectManager behavior
        whenever(mockProjectManager.loadWavFile(eq(testUri))).thenReturn(Result.success(dummySample))
        // Mock AudioEngine behavior
        whenever(mockAudioEngine.loadSampleToMemory(eq(dummySample.id), eq(testUri))).thenReturn(true)

        viewModel.loadSample(testUri)
        advanceUntilIdle() // Ensure coroutines launched in viewModelScope complete

        verify(mockProjectManager).loadWavFile(eq(testUri))
        verify(mockProjectManager).addSampleToPool(eq(dummyMetadata))
        verify(mockAudioEngine).loadSampleToMemory(eq(dummySample.id), eq(testUri))
        assertTrue(viewModel.saveSampleStatus.value?.contains("successfully loaded into AudioEngine") == true)
    }

    @Test
    fun `loadSample handles ProjectManager load failure`() = runTest(testDispatcher) {
        val testUri = "test://sample.wav"
        val errorMessage = "Failed to load WAV"
        whenever(mockProjectManager.loadWavFile(eq(testUri))).thenReturn(Result.failure(Error(errorMessage)))

        viewModel.loadSample(testUri)
        advanceUntilIdle()

        verify(mockProjectManager).loadWavFile(eq(testUri))
        verify(mockProjectManager, never()).addSampleToPool(any())
        verify(mockAudioEngine, never()).loadSampleToMemory(any(), any())
        assertTrue(viewModel.saveSampleStatus.value?.contains(errorMessage) == true)
    }

    @Test
    fun `loadSample handles AudioEngine load failure`() = runTest(testDispatcher) {
        val testUri = "test://sample.wav"
        val dummyMetadata = SampleMetadata(uri = testUri, duration = 1000L, name = "Test Sample")
        val dummyAudioData = FloatArray(100)
        val dummySample = Sample(metadata = dummyMetadata, audioData = dummyAudioData)

        whenever(mockProjectManager.loadWavFile(eq(testUri))).thenReturn(Result.success(dummySample))
        whenever(mockAudioEngine.loadSampleToMemory(eq(dummySample.id), eq(testUri))).thenReturn(false) // Simulate AudioEngine failure

        viewModel.loadSample(testUri)
        advanceUntilIdle()

        verify(mockProjectManager).loadWavFile(eq(testUri))
        verify(mockProjectManager).addSampleToPool(eq(dummyMetadata))
        verify(mockAudioEngine).loadSampleToMemory(eq(dummySample.id), eq(testUri))
        assertTrue(viewModel.saveSampleStatus.value?.contains("Error loading sample") == true && viewModel.saveSampleStatus.value?.contains("into audio engine") == true)
    }

    @Test
    fun `saveSample successfully calls ProjectManager`() = runTest(testDispatcher) {
        val testUri = "test://output.wav"
        val dummyMetadata = SampleMetadata(uri = "input_uri", duration = 1000L, name = "Test Sample")
        val dummyAudioData = FloatArray(100)
        val dummySample = Sample(metadata = dummyMetadata, audioData = dummyAudioData)

        whenever(mockProjectManager.saveWavFile(eq(dummySample), eq(testUri))).thenReturn(Result.success(Unit))

        viewModel.saveSample(dummySample, testUri)
        advanceUntilIdle()

        verify(mockProjectManager).saveWavFile(eq(dummySample), eq(testUri))
        assertTrue(viewModel.saveSampleStatus.value?.contains("saved successfully") == true)
    }

    @Test
    fun `saveSample handles ProjectManager save failure`() = runTest(testDispatcher) {
        val testUri = "test://output.wav"
        val dummyMetadata = SampleMetadata(uri = "input_uri", duration = 1000L, name = "Test Sample")
        val dummyAudioData = FloatArray(100)
        val dummySample = Sample(metadata = dummyMetadata, audioData = dummyAudioData)
        val errorMessage = "Failed to save WAV"

        whenever(mockProjectManager.saveWavFile(eq(dummySample), eq(testUri))).thenReturn(Result.failure(Error(errorMessage)))

        viewModel.saveSample(dummySample, testUri)
        advanceUntilIdle()

        verify(mockProjectManager).saveWavFile(eq(dummySample), eq(testUri))
        assertTrue(viewModel.saveSampleStatus.value?.contains(errorMessage) == true)
    }
}

// Helper class for JUnit 4 if MainDispatcherRule is needed and not using JUnit5 TestInstance per class
// For JUnit 5, TestCoroutineScheduler and Main.set/reset can be managed with extensions or lifecycle methods.
// @ExperimentalCoroutinesApi
// class MainDispatcherRule(
//    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
// ) : TestWatcher() {
//    override fun starting(description: Description) {
//        Dispatchers.setMain(testDispatcher)
//    }
//    override fun finished(description: Description) {
//        Dispatchers.resetMain()
//    }
// }
