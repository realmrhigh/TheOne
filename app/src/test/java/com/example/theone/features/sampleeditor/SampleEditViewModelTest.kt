package com.example.theone.features.sampleeditor

import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.AudioInputSource // For FakeAudioEngine consistency
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

// Mock implementations for testing
class MockTestAudioEngineControl : AudioEngineControl {
    var playSampleCalledWith: Triple<String, String, Float>? = null // sampleId, noteInstanceId, volume
    var playSampleShouldSucceed: Boolean = true

    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean = true
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean = true
    override suspend fun loadSampleToMemory(context: android.content.Context, sampleId: String, filePathUri: String): Boolean = true
    override suspend fun unloadSample(sampleId: String) {}
    override fun isSampleLoaded(sampleId: String): Boolean = true
    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean {
        playSampleCalledWith = Triple(sampleId, noteInstanceId, volume)
        return playSampleShouldSucceed
    }
    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, primarySoundSampleId: String, secondarySoundSampleId: String?) {}
    override suspend fun setMetronomeVolume(volume: Float) {}
    override suspend fun startAudioRecording(context: android.content.Context, filePathUri: String, sampleRate: Int, channels: Int, inputDeviceId: String?): Boolean = true
    override suspend fun stopAudioRecording(): SampleMetadata? = null
    override fun isRecordingActive(): Boolean = false
    override fun getRecordingLevelPeak(): Float = 0.0f
    override suspend fun shutdown() {}
    override fun isInitialized(): Boolean = true
    override fun getReportedLatencyMillis(): Float = 0.0f
}

class MockTestProjectManager : ProjectManager {
    var updatedSampleMetadata: SampleMetadata? = null
    var updateShouldSucceed: Boolean = true
    var samples = mutableMapOf<String, SampleMetadata>()

    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val id = UUID.randomUUID().toString()
        val meta = SampleMetadata(id, name, sourceFileUri, 1000L, 44100, 1)
        samples[id] = meta
        return meta
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        updatedSampleMetadata = sample
        if (updateShouldSucceed) samples[sample.id] = sample
        return updateShouldSucceed
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? = samples[sampleId]
}

@ExperimentalCoroutinesApi
class SampleEditViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    private lateinit var viewModel: SampleEditViewModel
    private lateinit var mockAudioEngine: MockTestAudioEngineControl
    private lateinit var mockProjectManager: MockTestProjectManager

    private val initialSample = SampleMetadata(
        id = "testSample1",
        name = "Test Kick",
        filePathUri = "fake/path/kick.wav",
        durationMs = 1000L,
        sampleRate = 44100,
        channels = 1,
        trimStartMs = 0L,
        trimEndMs = 1000L, // default, will be set by init if 0L
        loopMode = LoopMode.NONE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAudioEngine = MockTestAudioEngineControl()
        mockProjectManager = MockTestProjectManager()
        mockProjectManager.samples[initialSample.id] = initialSample // Pre-populate for save
        viewModel = SampleEditViewModel(mockAudioEngine, mockProjectManager, initialSample)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization sets trimEndMs to durationMs if initially 0L`() {
        val sampleWithZeroEnd = initialSample.copy(trimEndMs = 0L)
        val vm = SampleEditViewModel(mockAudioEngine, mockProjectManager, sampleWithZeroEnd)
        assertEquals(initialSample.durationMs, vm.currentSample.value.trimEndMs)
    }

    @Test
    fun `initialization coerces out of bounds trim points`() {
        val sampleOOB = initialSample.copy(trimStartMs = -100L, trimEndMs = 2000L)
        val vm = SampleEditViewModel(mockAudioEngine, mockProjectManager, sampleOOB)
        assertEquals(0L, vm.currentSample.value.trimStartMs)
        assertEquals(initialSample.durationMs, vm.currentSample.value.trimEndMs)
    }

    @Test
    fun `initialization coerces trimStartMs if greater than trimEndMs`() {
        val sampleInvalidRange = initialSample.copy(trimStartMs = 500L, trimEndMs = 400L)
        val vm = SampleEditViewModel(mockAudioEngine, mockProjectManager, sampleInvalidRange)
        assertEquals(400L, vm.currentSample.value.trimStartMs) // Start becomes End
        assertEquals(400L, vm.currentSample.value.trimEndMs)
    }


    @Test
    fun `updateTrimPoints basic update`() = runTest {
        viewModel.updateTrimPoints(100L, 900L)
        val sample = viewModel.currentSample.value
        assertEquals(100L, sample.trimStartMs)
        assertEquals(900L, sample.trimEndMs)
    }

    @Test
    fun `updateTrimPoints coercion`() = runTest {
        viewModel.updateTrimPoints(-50L, 1100L)
        val sample = viewModel.currentSample.value
        assertEquals(0L, sample.trimStartMs)
        assertEquals(1000L, sample.trimEndMs)

        viewModel.updateTrimPoints(600L, 500L) // Start > End
        val sample2 = viewModel.currentSample.value
        assertEquals(500L, sample2.trimStartMs) // Start becomes End
        assertEquals(500L, sample2.trimEndMs)
    }

    @Test
    fun `updateTrimPoints invalidates loop points if they fall outside new region`() = runTest {
        // Setup initial loop
        viewModel.setLoopMode(LoopMode.FORWARD) // This will set loop to 0-1000
        viewModel.updateLoopPoints(200L, 800L)
        assertNotNull(viewModel.currentSample.value.loopStartMs)

        viewModel.updateTrimPoints(300L, 700L) // Loop points (200,800) are now outside
        val sample = viewModel.currentSample.value
        assertEquals(300L, sample.trimStartMs)
        assertEquals(700L, sample.trimEndMs)
        // Loop points should be reset because setLoopMode will re-evaluate them
        // based on the new trim if they were invalidated.
        // Or, if setLoopMode is not called again, they might be null.
        // The current SampleEditViewModel.updateTrimPoints clears them.
        // Then setLoopMode would re-initialize them if loop mode is active.
        // Let's check if they are null after trim, before setLoopMode might fix them.
        // The behavior is that updateTrimPoints itself ensures loop points are valid
        // or nulls them if they become invalid relative to new trim points.
        // Then setLoopMode, if called, might re-default them.
        // The SampleEditViewModel's updateTrimPoints ensures loop points are within the new trim or nullified.
        // If LoopMode is FORWARD, and loop points became null, then `setLoopMode` would re-initialize.
        // The `updateTrimPoints` logic now calls `ensureValid` which handles this.
        assertNull("Loop start should be null if outside new trim", sample.loopStartMs)
        assertNull("Loop end should be null if outside new trim", sample.loopEndMs)
    }


    @Test
    fun `updateLoopPoints basic update`() = runTest {
        viewModel.setLoopMode(LoopMode.FORWARD) // Activate loop mode
        viewModel.updateLoopPoints(200L, 800L)
        val sample = viewModel.currentSample.value
        assertEquals(200L, sample.loopStartMs)
        assertEquals(800L, sample.loopEndMs)
    }

    @Test
    fun `updateLoopPoints coercion`() = runTest {
        viewModel.setLoopMode(LoopMode.FORWARD)
        viewModel.updateTrimPoints(100L, 900L) // Set trim region first

        viewModel.updateLoopPoints(50L, 950L) // Loop outside trim
        val sample = viewModel.currentSample.value
        assertEquals(100L, sample.loopStartMs) // Coerced to trimStart
        assertEquals(900L, sample.loopEndMs)   // Coerced to trimEnd

        viewModel.updateLoopPoints(600L, 500L) // Loop Start > Loop End
        val sample2 = viewModel.currentSample.value
        assertNull(sample2.loopStartMs) // Invalidated
        assertNull(sample2.loopEndMs)
    }

    @Test
    fun `updateLoopPoints clears points if mode is NONE`() = runTest {
        viewModel.setLoopMode(LoopMode.FORWARD)
        viewModel.updateLoopPoints(100L, 200L)
        assertNotNull(viewModel.currentSample.value.loopStartMs)

        viewModel.setLoopMode(LoopMode.NONE) // This call itself should clear them
        // viewModel.updateLoopPoints(100L, 200L) // This call will also clear them because mode is NONE
        val sample = viewModel.currentSample.value
        assertNull(sample.loopStartMs)
        assertNull(sample.loopEndMs)
    }

    @Test
    fun `setLoopMode NONE clears loop points`() = runTest {
        viewModel.setLoopMode(LoopMode.FORWARD)
        viewModel.updateLoopPoints(100L, 200L) // Set some loop points

        viewModel.setLoopMode(LoopMode.NONE)
        val sample = viewModel.currentSample.value
        assertEquals(LoopMode.NONE, sample.loopMode)
        assertNull(sample.loopStartMs)
        assertNull(sample.loopEndMs)
    }

    @Test
    fun `setLoopMode FORWARD defaults loop points if null or invalid`() = runTest {
        viewModel.updateTrimPoints(100L, 900L) // Current trim region
        viewModel.setLoopMode(LoopMode.FORWARD) // Loop points should default to 100-900

        val sample = viewModel.currentSample.value
        assertEquals(LoopMode.FORWARD, sample.loopMode)
        assertEquals(100L, sample.loopStartMs)
        assertEquals(900L, sample.loopEndMs)

        // Manually set invalid loop points then change mode
        val vm2 = SampleEditViewModel(mockAudioEngine, mockProjectManager,
                        initialSample.copy(trimStartMs = 100L, trimEndMs = 900L, loopStartMs = 50L, loopEndMs = 5000L, loopMode = LoopMode.NONE))
        vm2.setLoopMode(LoopMode.FORWARD)
        val sample2 = vm2.currentSample.value
        assertEquals(100L, sample2.loopStartMs) // Defaulted to trim
        assertEquals(900L, sample2.loopEndMs)   // Defaulted to trim
    }

    @Test
    fun `auditionSelection calls audioEngine and sets message`() = runTest {
        viewModel.auditionSelection()
        assertEquals(initialSample.id, mockAudioEngine.playSampleCalledWith?.first)
        assertTrue(viewModel.userMessage.value?.contains("Auditioning FULL sample") == true)
    }

    @Test
    fun `saveChanges calls projectManager with correct data`() = runTest {
        viewModel.updateTrimPoints(100L, 800L)
        viewModel.setLoopMode(LoopMode.FORWARD)
        viewModel.updateLoopPoints(200L, 700L)

        viewModel.saveChanges()

        val saved = mockProjectManager.updatedSampleMetadata
        assertNotNull(saved)
        assertEquals(initialSample.id, saved?.id)
        assertEquals(100L, saved?.trimStartMs)
        assertEquals(800L, saved?.trimEndMs)
        assertEquals(LoopMode.FORWARD, saved?.loopMode)
        assertEquals(200L, saved?.loopStartMs)
        assertEquals(700L, saved?.loopEndMs)
        assertTrue(viewModel.userMessage.value?.contains("updated") == true)
    }

    @Test
    fun `saveChanges clears loop points if LoopMode is NONE`() = runTest {
        viewModel.setLoopMode(LoopMode.FORWARD)
        viewModel.updateLoopPoints(100L, 200L) // Set some loop points
        viewModel.setLoopMode(LoopMode.NONE) // Switch to NONE

        viewModel.saveChanges()

        val saved = mockProjectManager.updatedSampleMetadata
        assertNotNull(saved)
        assertEquals(LoopMode.NONE, saved?.loopMode)
        assertNull(saved?.loopStartMs)
        assertNull(saved?.loopEndMs)
    }
     @Test
    fun `saveChanges defaults loop points if LoopMode is active and points are invalid`() = runTest {
        viewModel.updateTrimPoints(50L, 950L)
        // Create a ViewModel state where loop mode is FORWARD but loop points are null
        val sampleWithActiveLoopNoPoints = initialSample.copy(
            trimStartMs = 50L,
            trimEndMs = 950L,
            loopMode = LoopMode.FORWARD,
            loopStartMs = null, // Invalid state for active loop
            loopEndMs = null
        )
        val localViewModel = SampleEditViewModel(mockAudioEngine, mockProjectManager, sampleWithActiveLoopNoPoints)

        localViewModel.saveChanges()

        val saved = mockProjectManager.updatedSampleMetadata
        assertNotNull(saved)
        assertEquals(LoopMode.FORWARD, saved?.loopMode)
        assertEquals(50L, saved?.loopStartMs) // Should default to trimStart
        assertEquals(950L, saved?.loopEndMs)   // Should default to trimEnd
    }
}
