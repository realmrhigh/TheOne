package com.high.theone.features.sampler

import com.high.theone.audio.AudioEngine
import com.high.theone.domain.ProjectManager
import com.high.theone.model.AudioInputSource
import com.high.theone.model.SampleMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// --- Test Doubles ---

class FakeAudioEngine : AudioEngine() {
    var startAudioRecordingCalledWith: Pair<AudioInputSource, String>? = null
    var stopCurrentRecordingCalled = false
    var playSampleSliceCalledWith: Triple<String, Long, Long>? = null

    var nextSampleMetadataToReturn: SampleMetadata = SampleMetadata(id = "default_id", name = "Default Sample", uri = "default_uri", duration = 1000L)

    override fun startAudioRecording(audioInputSource: AudioInputSource, tempFilePath: String): SampleMetadata {
        startAudioRecordingCalledWith = Pair(audioInputSource, tempFilePath)
        // Simulate some delay or processing if needed by tests, but for now, direct return
        return nextSampleMetadataToReturn
    }

    override fun stopCurrentRecording() {
        stopCurrentRecordingCalled = true
    }

    override fun playSampleSlice(audioUri: String, startMs: Long, endMs: Long) {
        playSampleSliceCalledWith = Triple(audioUri, startMs, endMs)
    }
    // Other methods can be overridden if needed by tests, returning default/empty values.
}

class FakeProjectManager : ProjectManager {
    val samples = mutableListOf<SampleMetadata>()
    var addSampleToPoolCalledWith: SampleMetadata? = null
    var updateSampleMetadataCalledWith: SampleMetadata? = null

    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        addSampleToPoolCalledWith = sampleMetadata
        // Simulate actual add/replace logic for getSamplesFromPool testing
        val existingIndex = samples.indexOfFirst { it.uri == sampleMetadata.uri }
        if (existingIndex != -1) {
            samples[existingIndex] = sampleMetadata
        } else {
            samples.add(sampleMetadata)
        }
    }

    override fun getSamplesFromPool(): List<SampleMetadata> {
        return samples.toList()
    }

    // This is the non-suspend version used by SampleEditViewModel
    fun updateSampleMetadataNonSuspend(updatedSampleMetadata: SampleMetadata) {
        updateSampleMetadataCalledWith = updatedSampleMetadata
        val index = samples.indexOfFirst { it.uri == updatedSampleMetadata.uri }
        if (index != -1) {
            samples[index] = updatedSampleMetadata
        }
    }

    // Interface methods that need to be implemented
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val sample = SampleMetadata(id = "pm-${name}-${System.currentTimeMillis()}", name = name, uri = sourceFileUri, duration = 0L)
        addSampleToPool(sample) // Call the other version
        return sample
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        updateSampleMetadataNonSuspend(sample)
        return true
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? {
        return samples.find { it.id == sampleId } // Search by ID
    }
}


@ExperimentalCoroutinesApi
class SamplerViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SamplerViewModel
    private lateinit var fakeAudioEngine: FakeAudioEngine
    private lateinit var fakeProjectManager: FakeProjectManager

    private val testSample1 = SampleMetadata(id = "id1", name = "Sample1", uri = "uri1", duration = 1000L)
    private val testSample2 = SampleMetadata(id = "id2", name = "Sample2", uri = "uri2", duration = 2000L)
    private val testSample3 = SampleMetadata(id = "id3", name = "Sample3", uri = "uri3", duration = 3000L)
    private val testSample4 = SampleMetadata(id = "id4", name = "Sample4", uri = "uri4", duration = 4000L)


    @Before
    fun setUp() {
        fakeAudioEngine = FakeAudioEngine()
        fakeProjectManager = FakeProjectManager()
        viewModel = SamplerViewModel(fakeAudioEngine, fakeProjectManager)
    }

    @After
    fun tearDown() {
        // Nothing specific to tear down with fakes unless they hold global state
    }

    @Test
    fun `initialState_isIdleAndQueueEmpty`() = runTest {
        assertEquals(RecordingState.IDLE, viewModel.recordingState.value)
        assertTrue(viewModel.recordedSamplesQueue.value.isEmpty())
    }

    @Test
    fun `armSampler_clearsQueueAndSetsStateToArmed`() = runTest {
        // Set initial state with some samples in queue
        viewModel.onRecordingFinished(testSample1) // Manually add to queue for test setup
        assertEquals(1, viewModel.recordedSamplesQueue.value.size)

        viewModel.armSampler()

        assertTrue(viewModel.recordedSamplesQueue.value.isEmpty())
        assertEquals(RecordingState.ARMED, viewModel.recordingState.value)
    }

    @Test
    fun `startRecording_fromArmed_transitionsToRecordingAndCallsAudioEngine`() = runTest {
        fakeAudioEngine.nextSampleMetadataToReturn = testSample1

        viewModel.armSampler() // State is ARMED
        assertEquals(RecordingState.ARMED, viewModel.recordingState.value)

        viewModel.startRecording(AudioInputSource.MICROPHONE) // This is suspend due to viewModelScope.launch

        // viewModelScope.launch is async. We need to wait for it.
        // Since startAudioRecording is not suspend in Fake, and onRecordingFinished is not suspend,
        // the state changes should happen quickly within the launch block.
        // However, runTest should handle this by advancing the dispatcher.

        assertEquals(RecordingState.RECORDING, viewModel.recordingState.value) // Check intermediate state

        // Advance dispatcher to ensure coroutine in startRecording completes
        advanceUntilIdle()

        assertEquals(AudioInputSource.MICROPHONE, fakeAudioEngine.startAudioRecordingCalledWith?.first)
        assertEquals(testSample1, viewModel.recordedSamplesQueue.value.first())
        assertEquals(RecordingState.ARMED, viewModel.recordingState.value) // After onRecordingFinished
    }


    @Test
    fun `onRecordingFinished_addsToQueueAndManagesMaxSize`() = runTest {
        viewModel.armSampler()

        viewModel.onRecordingFinished(testSample1)
        assertEquals(listOf(testSample1), viewModel.recordedSamplesQueue.value)
        assertEquals(RecordingState.ARMED, viewModel.recordingState.value)

        viewModel.onRecordingFinished(testSample2)
        assertEquals(listOf(testSample1, testSample2), viewModel.recordedSamplesQueue.value)

        viewModel.onRecordingFinished(testSample3)
        assertEquals(listOf(testSample1, testSample2, testSample3), viewModel.recordedSamplesQueue.value)
        assertEquals(3, viewModel.recordedSamplesQueue.value.size) // MAX_RECORDINGS = 3

        viewModel.onRecordingFinished(testSample4) // This should push out testSample1
        assertEquals(listOf(testSample2, testSample3, testSample4), viewModel.recordedSamplesQueue.value)
        assertEquals(3, viewModel.recordedSamplesQueue.value.size)
        assertEquals(RecordingState.ARMED, viewModel.recordingState.value)
    }

    @Test
    fun `onRecordingFinished_updatesQueueCorrectly`() = runTest {
         viewModel.armSampler() // Ensure state is ARMED and queue is empty

        // Add first sample
        viewModel.onRecordingFinished(testSample1)
        assertEquals(listOf(testSample1), viewModel.recordedSamplesQueue.value)

        // Add second sample
        viewModel.onRecordingFinished(testSample2)
        assertEquals(listOf(testSample1, testSample2), viewModel.recordedSamplesQueue.value)

        // Add third sample
        viewModel.onRecordingFinished(testSample3)
        assertEquals(listOf(testSample1, testSample2, testSample3), viewModel.recordedSamplesQueue.value)

        // Add fourth sample (should evict testSample1 as MAX_RECORDINGS = 3)
        viewModel.onRecordingFinished(testSample4)
        assertEquals(listOf(testSample2, testSample3, testSample4), viewModel.recordedSamplesQueue.value)
    }


    @Test
    fun `disarmOrFinishSession_fromArmed_whenQueueNotEmpty_transitionsToReviewing`() = runTest {
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1) // Add a sample to queue

        viewModel.disarmOrFinishSession()
        assertEquals(RecordingState.REVIEWING, viewModel.recordingState.value)
    }

    @Test
    fun `disarmOrFinishSession_fromArmed_whenQueueEmpty_transitionsToIdle`() = runTest {
        viewModel.armSampler() // Queue is empty

        viewModel.disarmOrFinishSession()
        assertEquals(RecordingState.IDLE, viewModel.recordingState.value)
    }

    @Test
    fun `disarmOrFinishSession_fromReviewing_transitionsToIdleAndClearsQueue`() = runTest {
        // Setup: ARMED -> add sample -> REVIEWING
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1)
        viewModel.disarmOrFinishSession() // Now in REVIEWING
        assertEquals(RecordingState.REVIEWING, viewModel.recordingState.value)
        assertFalse(viewModel.recordedSamplesQueue.value.isEmpty())

        viewModel.disarmOrFinishSession() // Call again from REVIEWING

        assertEquals(RecordingState.IDLE, viewModel.recordingState.value)
        assertTrue(viewModel.recordedSamplesQueue.value.isEmpty())
    }

    @Test
    fun `saveSample_callsProjectManagerAndRemovesFromQueue`() = runTest {
        // Setup: ARMED -> add sample -> REVIEWING
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1)
        viewModel.disarmOrFinishSession() // Now in REVIEWING
        assertEquals(RecordingState.REVIEWING, viewModel.recordingState.value)
        assertTrue(viewModel.recordedSamplesQueue.value.contains(testSample1))

        val sampleToSave = viewModel.recordedSamplesQueue.value.first()
        viewModel.saveSample(sampleToSave, "Saved ${sampleToSave.name}")

        assertEquals(sampleToSave.copy(name = "Saved ${sampleToSave.name}"), fakeProjectManager.addSampleToPoolCalledWith)
        assertFalse(viewModel.recordedSamplesQueue.value.contains(sampleToSave))
    }

    @Test
    fun `saveSample_transitionsToIdleIfQueueBecomesEmpty`() = runTest {
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1)
        viewModel.disarmOrFinishSession() // REVIEWING with 1 sample

        val sampleToSave = viewModel.recordedSamplesQueue.value.first()
        viewModel.saveSample(sampleToSave, "Saved Sample")

        assertTrue(viewModel.recordedSamplesQueue.value.isEmpty())
        assertEquals(RecordingState.IDLE, viewModel.recordingState.value)
    }

    @Test
    fun `discardSample_removesFromQueue`() = runTest {
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1)
        viewModel.onRecordingFinished(testSample2)
        viewModel.disarmOrFinishSession() // REVIEWING with 2 samples

        assertTrue(viewModel.recordedSamplesQueue.value.contains(testSample1))
        val initialQueueSize = viewModel.recordedSamplesQueue.value.size

        viewModel.discardSample(testSample1)

        assertFalse(viewModel.recordedSamplesQueue.value.contains(testSample1))
        assertEquals(initialQueueSize - 1, viewModel.recordedSamplesQueue.value.size)
    }

    @Test
    fun `discardSample_transitionsToIdleIfQueueBecomesEmpty`() = runTest {
        viewModel.armSampler()
        viewModel.onRecordingFinished(testSample1)
        viewModel.disarmOrFinishSession() // REVIEWING with 1 sample

        viewModel.discardSample(testSample1)

        assertTrue(viewModel.recordedSamplesQueue.value.isEmpty())
        assertEquals(RecordingState.IDLE, viewModel.recordingState.value)
    }

    @Test
    fun `auditionSample_callsAudioEnginePlaySampleSlice`() = runTest {
        val sampleToAudition = testSample1.copy(trimStartMs = 100, trimEndMs = 500)
        // No specific state needed for auditionSample itself, just that a sample is passed

        viewModel.auditionSample(sampleToAudition)

        assertNotNull(fakeAudioEngine.playSampleSliceCalledWith)
        assertEquals(sampleToAudition.uri, fakeAudioEngine.playSampleSliceCalledWith!!.first)
        assertEquals(sampleToAudition.trimStartMs, fakeAudioEngine.playSampleSliceCalledWith!!.second)
        assertEquals(sampleToAudition.trimEndMs, fakeAudioEngine.playSampleSliceCalledWith!!.third)
    }
}


@ExperimentalCoroutinesApi
class MainCoroutineRule(private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()) : TestWatcher() {
    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        super.finished(description)
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }
}
