package com.example.theone.features.drumtrack

import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.AudioInputSource // Not used by this VM, but keep for FakeAudioEngine consistency
import com.example.theone.model.SampleMetadata
import com.example.theone.features.drumtrack.model.PadSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// --- Test Doubles (Copied from SamplerViewModelTest - should be in a shared test util file) ---

class FakeAudioEngine : AudioEngine() {
    var startAudioRecordingCalledWith: Pair<AudioInputSource, String>? = null
    var stopCurrentRecordingCalled = false
    var playSampleSliceCalledWith: Triple<String, Long, Long>? = null
    var playPadSampleCalledWith: PadSettings? = null // Specific for DrumTrackViewModel

    var nextSampleMetadataToReturn: SampleMetadata = SampleMetadata("default_uri", 1000L, "Default Sample")

    override fun startAudioRecording(audioInputSource: AudioInputSource, tempFilePath: String): SampleMetadata {
        startAudioRecordingCalledWith = Pair(audioInputSource, tempFilePath)
        return nextSampleMetadataToReturn
    }

    override fun stopCurrentRecording() {
        stopCurrentRecordingCalled = true
    }

    override fun playSampleSlice(audioUri: String, startMs: Long, endMs: Long) {
        playSampleSliceCalledWith = Triple(audioUri, startMs, endMs)
    }

    override fun playPadSample(padSettings: PadSettings) { // Overridden for DrumTrackViewModel
        playPadSampleCalledWith = padSettings
    }
}

class FakeProjectManager : ProjectManager {
    val samplesInPool = mutableListOf<SampleMetadata>()
    var addSampleToPoolCalledWith: SampleMetadata? = null
    var updateSampleMetadataCalledWith: SampleMetadata? = null

    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        addSampleToPoolCalledWith = sampleMetadata
        val existingIndex = samplesInPool.indexOfFirst { it.uri == sampleMetadata.uri }
        if (existingIndex != -1) {
            samplesInPool[existingIndex] = sampleMetadata
        } else {
            samplesInPool.add(sampleMetadata)
        }
    }

    override fun getSamplesFromPool(): List<SampleMetadata> {
        return samplesInPool.toList()
    }

    fun updateSampleMetadataNonSuspend(updatedSampleMetadata: SampleMetadata) {
        updateSampleMetadataCalledWith = updatedSampleMetadata
        val index = samplesInPool.indexOfFirst { it.uri == updatedSampleMetadata.uri }
        if (index != -1) {
            samplesInPool[index] = updatedSampleMetadata
        }
    }

    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val sample = SampleMetadata(uri = sourceFileUri, duration = 0L, name = name)
        addSampleToPool(sample)
        return sample
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        updateSampleMetadataNonSuspend(sample)
        return true
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? {
        return samplesInPool.find { it.uri == sampleId || it.name == sampleId }
    }
}


@ExperimentalCoroutinesApi
class DrumTrackViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: DrumTrackViewModel
    private lateinit var fakeAudioEngine: FakeAudioEngine
    private lateinit var fakeProjectManager: FakeProjectManager

    private val testSample1 = SampleMetadata(uri = "uri/kick.wav", duration = 100L, name = "Kick")
    private val testSample2 = SampleMetadata(uri = "uri/snare.wav", duration = 150L, name = "Snare")

    @Before
    fun setUp() {
        fakeAudioEngine = FakeAudioEngine()
        fakeProjectManager = FakeProjectManager()
        // ViewModel's init block calls loadSamplesForAssignment, which calls projectManager.getSamplesFromPool()
        // So, pre-populate samples in fakeProjectManager if needed for initial state.
        fakeProjectManager.samplesInPool.addAll(listOf(testSample1, testSample2))
        viewModel = DrumTrackViewModel(fakeAudioEngine, fakeProjectManager)
    }

    @After
    fun tearDown() {
        // Clear fakes if they hold state across tests, though these are new instances each time.
    }

    @Test
    fun `initialState_hasDefaultPadSettingsAndLoadsAvailableSamples`() = runTest {
        // Check padSettingsMap
        val padSettings = viewModel.padSettingsMap.value
        assertEquals(16, padSettings.size) // NUM_PADS = 16
        assertTrue(padSettings.values.all { it.sampleId == null && it.sampleName == null })

        // Check availableSamples (called from init)
        val available = viewModel.availableSamples.value
        assertEquals(2, available.size)
        assertEquals(listOf(testSample1, testSample2), available)
    }

    @Test
    fun `loadSamplesForAssignment_fetchesFromProjectManager`() = runTest {
        // Clear initial samples and set new ones for this test
        fakeProjectManager.samplesInPool.clear()
        val newSamples = listOf(SampleMetadata("new_uri", 123L, "New Sample"))
        fakeProjectManager.samplesInPool.addAll(newSamples)

        viewModel.loadSamplesForAssignment() // Manually call again

        assertEquals(newSamples, viewModel.availableSamples.value)
    }

    @Test
    fun `assignSampleToPad_updatesPadSettingsMap`() = runTest {
        val padIdToAssign = 5 // Int ID
        viewModel.assignSampleToPad(padIdToAssign, testSample1)

        val currentPadSettings = viewModel.padSettingsMap.value[padIdToAssign]
        assertNotNull(currentPadSettings)
        assertEquals(testSample1.uri, currentPadSettings?.sampleId)
        assertEquals(testSample1.name, currentPadSettings?.sampleName)
    }

    @Test
    fun `onPadTriggered_withAssignedSample_callsAudioEngine`() = runTest {
        val padIdToTrigger = 3
        viewModel.assignSampleToPad(padIdToTrigger, testSample1)

        val expectedPadSettings = viewModel.padSettingsMap.value[padIdToTrigger]!!

        viewModel.onPadTriggered(padIdToTrigger)

        assertNotNull(fakeAudioEngine.playPadSampleCalledWith)
        assertEquals(expectedPadSettings, fakeAudioEngine.playPadSampleCalledWith)
    }

    @Test
    fun `onPadTriggered_withoutAssignedSample_doesNotCallAudioEngine`() = runTest {
        val padIdToTrigger = 10 // Assuming this pad has no sample (default state)

        viewModel.onPadTriggered(padIdToTrigger)

        assertNull(fakeAudioEngine.playPadSampleCalledWith)
    }

    @Test
    fun `clearSampleFromPad_clearsPadSetting`() = runTest {
        val padIdToClear = 7
        // Assign first
        viewModel.assignSampleToPad(padIdToClear, testSample2)
        assertNotNull(viewModel.padSettingsMap.value[padIdToClear]?.sampleId)

        // Clear
        viewModel.clearSampleFromPad(padIdToClear)
        val clearedPadSettings = viewModel.padSettingsMap.value[padIdToClear]
        assertNotNull(clearedPadSettings)
        assertNull(clearedPadSettings?.sampleId)
        assertNull(clearedPadSettings?.sampleName)
    }

    @Test
    fun `updatePadSetting_fullyUpdatesThePad`() = runTest {
        val padId = 0
        val initialSetting = viewModel.padSettingsMap.value[padId]!!
        assertEquals(1.0f, initialSetting.volume) // Default volume

        val newSetting = initialSetting.copy(
            sampleId = "testSampleUri",
            sampleName = "Test Name",
            volume = 0.5f,
            pan = 0.25f,
            tuning = 1.5f
        )
        viewModel.updatePadSetting(padId, newSetting)

        val updatedSetting = viewModel.padSettingsMap.value[padId]!!
        assertEquals("testSampleUri", updatedSetting.sampleId)
        assertEquals("Test Name", updatedSetting.sampleName)
        assertEquals(0.5f, updatedSetting.volume)
        assertEquals(0.25f, updatedSetting.pan)
        assertEquals(1.5f, updatedSetting.tuning)
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
