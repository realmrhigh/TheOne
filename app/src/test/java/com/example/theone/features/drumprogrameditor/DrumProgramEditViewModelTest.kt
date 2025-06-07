package com.example.theone.features.drumprogrameditor

import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.*
import com.example.theone.features.drumtrack.model.PadSettings // For PadSettings itself
import com.example.theone.features.drumtrack.model.PlaybackMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// --- Test Doubles (Ideally from a shared test util file) ---

class FakeAudioEngine : AudioEngine() {
    var triggerPadCalledWith: Triple<String, PadSettings, Int>? = null
    var startAudioRecordingCalledWith: Pair<AudioInputSource, String>? = null
    var stopCurrentRecordingCalled = false
    var playSampleSliceCalledWith: Triple<String, Long, Long>? = null
    var nextSampleMetadataToReturn: SampleMetadata = SampleMetadata("default_uri", 1000L, "Default Sample")

    override fun triggerPad(padSettingsId: String, padSettings: PadSettings, velocity: Int) {
        triggerPadCalledWith = Triple(padSettingsId, padSettings, velocity)
    }
    override fun startAudioRecording(audioInputSource: AudioInputSource, tempFilePath: String): SampleMetadata {
        startAudioRecordingCalledWith = Pair(audioInputSource, tempFilePath)
        return nextSampleMetadataToReturn
    }
    override fun stopCurrentRecording() { stopCurrentRecordingCalled = true }
    override fun playSampleSlice(audioUri: String, startMs: Long, endMs: Long) {
        playSampleSliceCalledWith = Triple(audioUri, startMs, endMs)
    }
    override fun playPadSample(padSettings: PadSettings) { /* Deprecated */ }
}

class FakeProjectManager : ProjectManager {
    val samplesInPool = mutableListOf<SampleMetadata>()
    val padSettingsStore = mutableMapOf<String, PadSettings>()
    var addSampleToPoolCalledWith: SampleMetadata? = null
    var updateSampleMetadataCalledWith: SampleMetadata? = null
    var savedPadSettings: PadSettings? = null

    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        addSampleToPoolCalledWith = sampleMetadata
        samplesInPool.add(sampleMetadata) // Simplified for this fake
    }
    override fun getSamplesFromPool(): List<SampleMetadata> = samplesInPool.toList()
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val sample = SampleMetadata(uri = sourceFileUri, duration = 0L, name = name)
        addSampleToPool(sample)
        return sample
    }
    fun updateSampleMetadataNonSuspend(updatedSampleMetadata: SampleMetadata) {
        updateSampleMetadataCalledWith = updatedSampleMetadata
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        updateSampleMetadataNonSuspend(sample)
        return true
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? = samplesInPool.find { it.uri == sampleId }

    override suspend fun savePadSettings(padSettings: PadSettings) {
        savedPadSettings = padSettings
        padSettingsStore[padSettings.padSettingsId] = padSettings
    }
    override suspend fun loadPadSettings(padSettingsId: String): PadSettings? = padSettingsStore[padSettingsId]
    override suspend fun getAllPadSettings(): Map<String, PadSettings> = padSettingsStore.toMap()
}

@ExperimentalCoroutinesApi
class DrumProgramEditViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: DrumProgramEditViewModel
    private lateinit var fakeAudioEngine: FakeAudioEngine
    private lateinit var fakeProjectManager: FakeProjectManager

    private val sampleMeta1 = SampleMetadata("uri1", 1000L, "SampleKick")
    private val existingPadSettingsId = "existingPadId_123"
    private val newPadSettingsId = "newPadId_456"

    private fun createInitialPadSettings(): PadSettings {
        return PadSettings(
            padSettingsId = existingPadSettingsId,
            volume = 0.8f,
            layers = mutableListOf(SampleLayer(sampleId = "initial_layer_sample_uri"))
        )
    }

    @Before
    fun setUp() {
        fakeAudioEngine = FakeAudioEngine()
        fakeProjectManager = FakeProjectManager()
    }

    private fun setupViewModel(initialSettings: PadSettings?, idToEdit: String) {
         viewModel = DrumProgramEditViewModel(
            padSettingsIdToEdit = idToEdit,
            initialPadSettings = initialSettings,
            projectManager = fakeProjectManager,
            audioEngine = fakeAudioEngine
        )
    }


    @Test
    fun `initialization_withNullInitialSettings_createsDefaultPadSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val currentSettings = viewModel.currentPadSettings.value
        assertEquals(newPadSettingsId, currentSettings.padSettingsId)
        assertTrue(currentSettings.layers.isEmpty())
        assertEquals(1.0f, currentSettings.volume) // Default volume
    }

    @Test
    fun `initialization_withExistingInitialSettings_usesThem`() = runTest {
        val initial = createInitialPadSettings()
        setupViewModel(initial, existingPadSettingsId)
        assertEquals(initial, viewModel.currentPadSettings.value)
        assertEquals(0.8f, viewModel.currentPadSettings.value.volume)
        assertEquals(1, viewModel.currentPadSettings.value.layers.size)
    }

    @Test
    fun `addLayer_addsLayerToCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val initialLayerCount = viewModel.currentPadSettings.value.layers.size

        val addedLayer = viewModel.addLayer(sampleMeta1)

        val currentSettings = viewModel.currentPadSettings.value
        assertEquals(initialLayerCount + 1, currentSettings.layers.size)
        assertTrue(currentSettings.layers.contains(addedLayer))
        assertEquals(sampleMeta1.uri, addedLayer.sampleId)
    }

    @Test
    fun `removeLayer_removesLayerFromCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val layer1 = viewModel.addLayer(sampleMeta1)
        val layer2 = viewModel.addLayer(SampleMetadata("uri2", 200L, "Snare"))
        assertEquals(2, viewModel.currentPadSettings.value.layers.size)

        viewModel.removeLayer(layer1.id)

        val currentSettings = viewModel.currentPadSettings.value
        assertEquals(1, currentSettings.layers.size)
        assertFalse(currentSettings.layers.any { it.id == layer1.id })
        assertTrue(currentSettings.layers.contains(layer2))
    }

    @Test
    fun `updateLayer_updatesLayerInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val layerToUpdate = viewModel.addLayer(sampleMeta1)
        val modifiedLayer = layerToUpdate.copy(volumeOffsetDb = -3f)

        viewModel.updateLayer(modifiedLayer)

        val currentSettings = viewModel.currentPadSettings.value
        val updatedLayerFromVM = currentSettings.layers.find { it.id == layerToUpdate.id }
        assertNotNull(updatedLayerFromVM)
        assertEquals(-3f, updatedLayerFromVM!!.volumeOffsetDb)
    }

    @Test
    fun `updateLayerTriggerRule_updatesRuleInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        viewModel.updateLayerTriggerRule(LayerTriggerRule.CYCLE)
        assertEquals(LayerTriggerRule.CYCLE, viewModel.currentPadSettings.value.layerTriggerRule)
    }

    @Test
    fun `updateEnvelope_updatesCorrectEnvelope`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val newAmpEnv = EnvelopeSettings(attackMs = 50f)
        viewModel.updateEnvelope("amp", newAmpEnv)
        assertEquals(newAmpEnv, viewModel.currentPadSettings.value.ampEnvelope)

        val newFilterEnv = EnvelopeSettings(decayMs = 200f)
        viewModel.updateEnvelope("filter", newFilterEnv)
        assertEquals(newFilterEnv, viewModel.currentPadSettings.value.filterEnvelope)

        val newPitchEnv = EnvelopeSettings(sustainLevel = 0.1f)
        viewModel.updateEnvelope("pitch", newPitchEnv)
        assertEquals(newPitchEnv, viewModel.currentPadSettings.value.pitchEnvelope)

        // Test invalid type
        val originalSettings = viewModel.currentPadSettings.value
        viewModel.updateEnvelope("invalid_type", EnvelopeSettings(attackMs = 999f))
        assertEquals(originalSettings, viewModel.currentPadSettings.value)
    }

    @Test
    fun `addLfo_addsLfoToList_returnsNewLfo`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val newLfo = viewModel.addLfo()
        assertEquals(1, viewModel.currentPadSettings.value.lfos.size)
        assertEquals(newLfo, viewModel.currentPadSettings.value.lfos[0])
    }

    @Test
    fun `removeLfo_removesLfoFromList`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val lfo1 = viewModel.addLfo()
        viewModel.removeLfo(lfo1.id)
        assertTrue(viewModel.currentPadSettings.value.lfos.isEmpty())
    }

    @Test
    fun `updateLfo_updatesCorrectLfoInList`() = runTest {
        setupViewModel(null, newPadSettingsId)
        val lfoToUpdate = viewModel.addLfo()
        val modifiedLfo = lfoToUpdate.copy(rateHz = 5.0f)
        viewModel.updateLfo(modifiedLfo)
        assertEquals(5.0f, viewModel.currentPadSettings.value.lfos.find { it.id == lfoToUpdate.id }?.rateHz)
    }

    @Test
    fun `updateBaseVolume_updatesVolumeInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        viewModel.updateBaseVolume(0.75f)
        assertEquals(0.75f, viewModel.currentPadSettings.value.volume)
    }

    @Test
    fun `updateBasePan_updatesPanInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        viewModel.updateBasePan(-0.5f)
        assertEquals(-0.5f, viewModel.currentPadSettings.value.pan)
    }

    @Test
    fun `updateBaseCoarseTune_updatesCoarseTuneInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        viewModel.updateBaseCoarseTune(12)
        assertEquals(12, viewModel.currentPadSettings.value.tuningCoarse)
    }

    @Test
    fun `updateBaseFineTune_updatesFineTuneInCurrentSettings`() = runTest {
        setupViewModel(null, newPadSettingsId)
        viewModel.updateBaseFineTune(-50)
        assertEquals(-50, viewModel.currentPadSettings.value.tuningFine)
    }


    @Test
    fun `saveChanges_callsProjectManagerSavePadSettings`() = runTest {
        setupViewModel(createInitialPadSettings(), existingPadSettingsId)
        val settingsToSave = viewModel.currentPadSettings.value // get current state

        viewModel.saveChanges()
        advanceUntilIdle() // For viewModelScope.launch

        assertNotNull(fakeProjectManager.savedPadSettings)
        assertEquals(settingsToSave, fakeProjectManager.savedPadSettings)
    }

    @Test
    fun `auditionPad_callsAudioEngineTriggerPad`() = runTest {
        setupViewModel(createInitialPadSettings(), existingPadSettingsId)
        val velocity = 100
        val currentSettings = viewModel.currentPadSettings.value

        viewModel.auditionPad(velocity)

        assertNotNull(fakeAudioEngine.triggerPadCalledWith)
        assertEquals(existingPadSettingsId, fakeAudioEngine.triggerPadCalledWith!!.first)
        assertEquals(currentSettings, fakeAudioEngine.triggerPadCalledWith!!.second)
        assertEquals(velocity, fakeAudioEngine.triggerPadCalledWith!!.third)
    }
}

@ExperimentalCoroutinesApi
class MainCoroutineRule(val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()) : TestWatcher() {
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
fun MainCoroutineRule.advanceUntilIdle() = this.testDispatcher.scheduler.advanceUntilIdle()
