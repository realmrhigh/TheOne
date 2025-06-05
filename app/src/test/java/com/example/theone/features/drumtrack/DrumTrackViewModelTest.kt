package com.example.theone.features.drumtrack

import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.SampleMetadata
import com.example.theone.audio.AudioEngineControl // Using the one from sampler package
import com.example.theone.domain.ProjectManager     // Using the one from sampler package
import com.example.theone.model.EnvelopeSettings // For SamplerViewModel.EnvelopeSettings
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
import org.junit.rules.TestWatcher // Explicit import

@ExperimentalCoroutinesApi
class DrumTrackViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule() // Reusing from SamplerViewModelTest, ensure it's accessible or redefine

    private lateinit var viewModel: DrumTrackViewModel
    private lateinit var mockAudioEngine: AudioEngineControl
    private lateinit var mockProjectManager: ProjectManager // Although not directly used in these tests, it's a dependency

    private val testSample1 = SampleMetadata(id = "s1", name = "Kick", filePathUri = "uri/kick.wav")
    private val testSample2 = SampleMetadata(id = "s2", name = "Snare", filePathUri = "uri/snare.wav")

    @Before
    fun setUp() {
        mockAudioEngine = mockk(relaxed = true)
        mockProjectManager = mockk(relaxed = true) // Relaxed as it's not the focus here
        viewModel = DrumTrackViewModel(mockAudioEngine, mockProjectManager)

        // Simulate fetching available samples as DrumTrackViewModel's init calls it
        // This uses the internal _availableSamples.value which is not ideal for testing,
        // but given the current implementation of fetchAvailableSamples, we mock its effect.
        // A better approach would be to make fetchAvailableSamples use ProjectManager
        // and mock that behavior. For now, we bypass it by setting the flow directly if possible,
        // or just acknowledge it runs. The tests below focus on assignment and playback.
        // For this test suite, we'll ensure `availableSamples` has items if needed for assignment tests.
        coEvery { mockProjectManager.addSampleToPool(any(), any(), any()) } returns null // Default mock
        // No direct way to set _availableSamples from outside, so tests will rely on assignSampleToPad
        // which doesn't depend on _availableSamples state.
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial drumTrack has 16 pads`() = runTest {
        val drumTrack = viewModel.drumTrack.first()
        assertEquals(16, drumTrack.pads.size)
        assertTrue(drumTrack.pads.all { it.sampleId == null })
    }

    @Test
    fun `assignSampleToPad updates the correct pad`() = runTest {
        val padToAssign = "Pad5"
        viewModel.assignSampleToPad(padToAssign, testSample1)

        val drumTrack = viewModel.drumTrack.first()
        val targetPad = drumTrack.pads.find { it.id == padToAssign }
        assertNotNull(targetPad)
        assertEquals(testSample1.id, targetPad?.sampleId)
        assertEquals(testSample1.name, targetPad?.sampleName)
        assertEquals("Sample '${testSample1.name}' assigned to Pad $padToAssign.", viewModel.userMessage.first())
    }

    @Test
    fun `clearSampleFromPad clears the sample from the pad`() = runTest {
        val padToClear = "Pad3"
        // Assign first
        viewModel.assignSampleToPad(padToClear, testSample1)
        var drumTrack = viewModel.drumTrack.first()
        assertNotNull(drumTrack.pads.find { it.id == padToClear }?.sampleId)

        // Clear
        viewModel.clearSampleFromPad(padToClear)
        drumTrack = viewModel.drumTrack.first()
        val targetPad = drumTrack.pads.find { it.id == padToClear }
        assertNotNull(targetPad)
        assertNull(targetPad?.sampleId)
        assertNull(targetPad?.sampleName)
        assertEquals("Sample cleared from Pad $padToClear.", viewModel.userMessage.first())
    }

    @Test
    fun `onPadTriggered with no sample assigned does not call audioEngine`() = runTest {
        val padId = "Pad1"
        // Ensure no sample is assigned (default state)
        viewModel.onPadTriggered(padId)

        coVerify(exactly = 0) { mockAudioEngine.playPadSample(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals("Pad $padId has no sample assigned.", viewModel.userMessage.first())
    }

    @Test
    fun `onPadTriggered with assigned sample calls audioEngine playPadSample`() = runTest {
        val padId = "Pad1"
        val trackId = viewModel.drumTrack.first().id

        // Assign sample
        viewModel.assignSampleToPad(padId, testSample1)
        // Capture the PadSettings after assignment to check parameters
        val assignedPadSettings = viewModel.drumTrack.first().pads.find { it.id == padId }!!

        coEvery { mockAudioEngine.playPadSample(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        viewModel.onPadTriggered(padId)

        coVerify {
            mockAudioEngine.playPadSample(
                noteInstanceId = any(), // Can't easily predict System.currentTimeMillis
                trackId = trackId,
                padId = padId,
                sampleId = testSample1.id,
                sliceId = null,
                velocity = 1.0f,
                playbackMode = com.example.theone.model.PlaybackMode.ONE_SHOT, // Expected mapped value
                coarseTune = assignedPadSettings.tuningCoarse,
                fineTune = assignedPadSettings.tuningFine,
                pan = assignedPadSettings.pan,
                volume = assignedPadSettings.volume,
                ampEnv = any<EnvelopeSettings>(), // Check type, specific values if necessary
                filterEnv = null,
                pitchEnv = null,
                lfos = emptyList()
            )
        }
        // Message will be "Playing Pad..."
        assertTrue(viewModel.userMessage.first()?.startsWith("Playing Pad $padId") ?: false)
    }

    @Test
    fun `onPadTriggered with assigned sample and audioEngine fails sets error message`() = runTest {
        val padId = "Pad1"
        viewModel.assignSampleToPad(padId, testSample1)

        coEvery { mockAudioEngine.playPadSample(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns false // Simulate playback failure

        viewModel.onPadTriggered(padId)

        assertEquals("Playback failed for Pad $padId.", viewModel.userMessage.first())
    }

    @Test
    fun `onPadTriggered with non-existent padId sets error message`() = runTest {
        val nonExistentPadId = "Pad99"
        viewModel.onPadTriggered(nonExistentPadId)
        assertEquals("Error: Pad $nonExistentPadId not found.", viewModel.userMessage.first())
        coVerify(exactly = 0) { mockAudioEngine.playPadSample(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}

// Helper class for managing CoroutineDispatchers in tests
// Ensure this is defined or accessible. Copied from SamplerViewModelTest for completeness if run standalone.
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
