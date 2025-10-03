package com.high.theone.features.sequencer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for PadSystemIntegration
 * Tests pad state synchronization, mode switching, and integration functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PadSystemIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var audioEngine: AudioEngineControl
    private lateinit var samplingViewModel: SamplingViewModel
    private lateinit var padSystemIntegration: PadSystemIntegration

    // Mock sampling UI state
    private val mockSamplingUiState = MutableStateFlow(
        SamplingUiState(
            pads = List(16) { index ->
                PadState(
                    index = index,
                    sampleId = if (index < 4) "sample_$index" else null,
                    sampleName = if (index < 4) "Sample $index" else null,
                    hasAssignedSample = index < 4,
                    isEnabled = true,
                    volume = 1.0f,
                    pan = 0.0f,
                    playbackMode = com.high.theone.model.PlaybackMode.ONE_SHOT
                )
            },
            isAudioEngineReady = true
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock audio engine
        audioEngine = mockk(relaxed = true) {
            every { initialize(any(), any(), any()) } returns true
            every { initializeDrumEngine() } returns true
            every { setDrumPadVolume(any(), any()) } returns Unit
            every { setDrumPadPan(any(), any()) } returns Unit
            every { setDrumPadMode(any(), any()) } returns Unit
        }

        // Mock sampling view model
        samplingViewModel = mockk(relaxed = true) {
            every { uiState } returns mockSamplingUiState
            every { triggerPad(any(), any()) } returns Unit
        }

        // Create integration instance
        padSystemIntegration = PadSystemIntegration(audioEngine, samplingViewModel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should initialize with correct default state`() = runTest {
        // Given - fresh integration instance
        
        // When - checking initial state
        val currentMode = padSystemIntegration.currentMode.value
        val syncState = padSystemIntegration.padSyncState.value
        
        // Then - should have correct defaults
        assertEquals(PlaybackMode.LIVE, currentMode)
        assertFalse(syncState.isAudioEngineReady)
        assertEquals(0, syncState.totalPads)
    }

    @Test
    fun `should synchronize pad states from sampling system`() = runTest {
        // Given - sampling system with assigned pads
        val updatedState = mockSamplingUiState.value.copy(
            pads = List(16) { index ->
                PadState(
                    index = index,
                    sampleId = if (index < 8) "sample_$index" else null,
                    sampleName = if (index < 8) "Sample $index" else null,
                    hasAssignedSample = index < 8,
                    isEnabled = true
                )
            }
        )
        
        // When - updating sampling state
        mockSamplingUiState.value = updatedState
        advanceUntilIdle()
        
        // Then - sequencer pad state should be updated
        val sequencerPads = padSystemIntegration.sequencerPadState.value
        assertEquals(16, sequencerPads.size)
        assertEquals(8, sequencerPads.count { it.hasAssignedSample })
        
        val syncState = padSystemIntegration.padSyncState.value
        assertEquals(16, syncState.totalPads)
        assertEquals(8, syncState.assignedPads)
    }

    @Test
    fun `should switch to sequencer mode correctly`() = runTest {
        // Given - integration in live mode
        assertEquals(PlaybackMode.LIVE, padSystemIntegration.currentMode.value)
        
        // When - switching to sequencer mode
        padSystemIntegration.switchToMode(PlaybackMode.SEQUENCER)
        advanceUntilIdle()
        
        // Then - mode should be updated
        assertEquals(PlaybackMode.SEQUENCER, padSystemIntegration.currentMode.value)
        
        // And - sequencer mode should be enabled in audio engine
        verify { 
            if (audioEngine is SequencerAudioEngine) {
                (audioEngine as SequencerAudioEngine).setSequencerMode(true)
            }
        }
    }

    @Test
    fun `should switch to live mode correctly`() = runTest {
        // Given - integration in sequencer mode
        padSystemIntegration.switchToMode(PlaybackMode.SEQUENCER)
        advanceUntilIdle()
        
        // When - switching to live mode
        padSystemIntegration.switchToMode(PlaybackMode.LIVE)
        advanceUntilIdle()
        
        // Then - mode should be updated
        assertEquals(PlaybackMode.LIVE, padSystemIntegration.currentMode.value)
    }

    @Test
    fun `should synchronize pad configurations with audio engine`() = runTest {
        // Given - pads with different configurations
        val padsWithSettings = List(4) { index ->
            PadState(
                index = index,
                sampleId = "sample_$index",
                hasAssignedSample = true,
                volume = 0.5f + (index * 0.1f),
                pan = -0.5f + (index * 0.25f),
                playbackMode = com.high.theone.model.PlaybackMode.ONE_SHOT
            )
        }
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(pads = padsWithSettings + List(12) { PadState(it + 4) })
        
        // When - forcing pad resync
        padSystemIntegration.forcePadResync()
        advanceUntilIdle()
        
        // Then - audio engine should receive pad configurations
        verify(exactly = 4) { audioEngine.setDrumPadVolume(any(), any()) }
        verify(exactly = 4) { audioEngine.setDrumPadPan(any(), any()) }
        verify(exactly = 4) { audioEngine.setDrumPadMode(any(), any()) }
    }

    @Test
    fun `should detect pad assignments correctly`() = runTest {
        // Given - pads with assignments
        val assignedPads = List(6) { index ->
            PadState(
                index = index,
                sampleId = "sample_$index",
                sampleName = "Sample $index",
                hasAssignedSample = true,
                isEnabled = true
            )
        }
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(
            pads = assignedPads + List(10) { PadState(it + 6) }
        )
        
        // When - detecting assignments
        padSystemIntegration.detectPadAssignments()
        advanceUntilIdle()
        
        // Then - assignments should be detected
        val syncState = padSystemIntegration.padSyncState.value
        assertEquals(6, syncState.detectedAssignments.size)
        
        syncState.detectedAssignments.forEachIndexed { index, assignment ->
            assertEquals(index, assignment.padIndex)
            assertEquals("sample_$index", assignment.sampleId)
            assertEquals("Sample $index", assignment.sampleName)
            assertTrue(assignment.isActive)
        }
    }

    @Test
    fun `should trigger pad in live mode`() = runTest {
        // Given - integration in live mode
        padSystemIntegration.switchToMode(PlaybackMode.LIVE)
        advanceUntilIdle()
        
        // When - triggering pad
        padSystemIntegration.triggerPadLive(0, 0.8f)
        
        // Then - sampling view model should receive trigger
        verify { samplingViewModel.triggerPad(0, 0.8f) }
    }

    @Test
    fun `should not trigger pad in live mode when in sequencer mode`() = runTest {
        // Given - integration in sequencer mode
        padSystemIntegration.switchToMode(PlaybackMode.SEQUENCER)
        advanceUntilIdle()
        
        // When - attempting to trigger pad in live mode
        padSystemIntegration.triggerPadLive(0, 0.8f)
        
        // Then - sampling view model should not receive trigger
        verify(exactly = 0) { samplingViewModel.triggerPad(any(), any()) }
    }

    @Test
    fun `should get pad info correctly`() = runTest {
        // Given - pad with specific configuration
        val testPad = PadState(
            index = 2,
            sampleId = "test_sample",
            sampleName = "Test Sample",
            hasAssignedSample = true,
            volume = 0.7f,
            pan = 0.3f
        )
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(
            pads = mockSamplingUiState.value.pads.toMutableList().apply {
                set(2, testPad)
            }
        )
        advanceUntilIdle()
        
        // When - getting pad info
        val padInfo = padSystemIntegration.getPadInfo(2)
        
        // Then - should return correct info
        assertEquals(2, padInfo?.index)
        assertEquals("test_sample", padInfo?.sampleId)
        assertEquals("Test Sample", padInfo?.sampleName)
        assertTrue(padInfo?.hasAssignedSample == true)
        assertEquals(0.7f, padInfo?.volume)
        assertEquals(0.3f, padInfo?.pan)
    }

    @Test
    fun `should get assigned pads correctly`() = runTest {
        // Given - some assigned pads
        val assignedIndices = setOf(1, 3, 5, 7)
        val updatedPads = mockSamplingUiState.value.pads.mapIndexed { index, pad ->
            if (assignedIndices.contains(index)) {
                pad.copy(
                    sampleId = "sample_$index",
                    hasAssignedSample = true
                )
            } else {
                pad.copy(
                    sampleId = null,
                    hasAssignedSample = false
                )
            }
        }
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(pads = updatedPads)
        advanceUntilIdle()
        
        // When - getting assigned pads
        val assignedPads = padSystemIntegration.getAssignedPads()
        
        // Then - should return only assigned pads
        assertEquals(4, assignedPads.size)
        assignedPads.forEach { pad ->
            assertTrue(assignedIndices.contains(pad.index))
            assertTrue(pad.hasAssignedSample)
        }
    }

    @Test
    fun `should check pad sequencer readiness correctly`() = runTest {
        // Given - pads with different states
        val pads = listOf(
            PadState(0, sampleId = "sample_0", hasAssignedSample = true, isEnabled = true), // Ready
            PadState(1, sampleId = null, hasAssignedSample = false, isEnabled = true),      // No sample
            PadState(2, sampleId = "sample_2", hasAssignedSample = true, isEnabled = false), // Disabled
            PadState(3, sampleId = "sample_3", hasAssignedSample = true, isEnabled = true, isLoading = true) // Loading
        )
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(
            pads = pads + List(12) { PadState(it + 4) }
        )
        advanceUntilIdle()
        
        // When/Then - checking readiness
        assertTrue(padSystemIntegration.canUsePadInSequencer(0))  // Ready
        assertFalse(padSystemIntegration.canUsePadInSequencer(1)) // No sample
        assertFalse(padSystemIntegration.canUsePadInSequencer(2)) // Disabled
        assertFalse(padSystemIntegration.canUsePadInSequencer(3)) // Loading
    }

    @Test
    fun `should get pad usage statistics correctly`() = runTest {
        // Given - pads with different states
        val pads = List(16) { index ->
            PadState(
                index = index,
                hasAssignedSample = index < 8,
                isEnabled = index < 12,
                playbackMode = if (index % 2 == 0) {
                    com.high.theone.model.PlaybackMode.ONE_SHOT
                } else {
                    com.high.theone.model.PlaybackMode.LOOP
                }
            )
        }
        
        mockSamplingUiState.value = mockSamplingUiState.value.copy(pads = pads)
        advanceUntilIdle()
        
        // When - getting usage stats
        val stats = padSystemIntegration.getPadUsageStats()
        
        // Then - should return correct statistics
        assertEquals(16, stats.totalPads)
        assertEquals(8, stats.loadedPads)
        assertEquals(12, stats.enabledPads)
        assertEquals(8, stats.padsByPlaybackMode[com.high.theone.model.PlaybackMode.ONE_SHOT])
        assertEquals(8, stats.padsByPlaybackMode[com.high.theone.model.PlaybackMode.LOOP])
    }

    @Test
    fun `should handle audio engine initialization failure`() = runTest {
        // Given - audio engine that fails to initialize
        val failingAudioEngine = mockk<AudioEngineControl>(relaxed = true) {
            every { initialize(any(), any(), any()) } returns false
        }
        
        // When - creating integration with failing audio engine
        val integration = PadSystemIntegration(failingAudioEngine, samplingViewModel)
        advanceUntilIdle()
        
        // Then - sync state should reflect failure
        val syncState = integration.padSyncState.value
        assertFalse(syncState.isAudioEngineReady)
    }

    @Test
    fun `should get correct sync status`() = runTest {
        // Given - integration with no assignments
        mockSamplingUiState.value = mockSamplingUiState.value.copy(
            pads = List(16) { PadState(it, hasAssignedSample = false) }
        )
        advanceUntilIdle()
        
        // When - getting sync status
        val status = padSystemIntegration.getSyncStatus()
        
        // Then - should indicate no assignments
        assertEquals(PadSyncStatus.NO_ASSIGNMENTS, status)
    }
}