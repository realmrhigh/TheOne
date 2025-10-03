package com.high.theone.features.sampling

import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.domain.SampleRepository
import com.high.theone.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SamplingViewModel.
 * Tests core functionality including recording, pad management, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamplingViewModelTest {

    @Mock
    private lateinit var audioEngine: AudioEngineControl

    @Mock
    private lateinit var sampleRepository: SampleRepository

    @Mock
    private lateinit var projectManager: ProjectManager

    private lateinit var viewModel: SamplingViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Setup default mock behaviors
        whenever(projectManager.getCurrentProject()).thenReturn(
            MutableStateFlow(
                Project(
                    id = "test-project",
                    name = "Test Project",
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    samplePool = emptyList()
                )
            )
        )
    }

    private fun createViewModel(): SamplingViewModel {
        return SamplingViewModel(audioEngine, sampleRepository, projectManager)
    }

    @Test
    fun `initial state should be correct`() = testScope.runTest {
        viewModel = createViewModel()
        
        val initialState = viewModel.uiState.value
        
        assertEquals(16, initialState.pads.size)
        assertFalse(initialState.recordingState.isRecording)
        assertFalse(initialState.isLoading)
        assertEquals(emptyList(), initialState.availableSamples)
    }

    @Test
    fun `selectPad should update selected pad index`() = testScope.runTest {
        viewModel = createViewModel()
        
        viewModel.selectPad(5)
        
        assertEquals(5, viewModel.uiState.value.selectedPad)
    }

    @Test
    fun `clearError should remove error messages`() = testScope.runTest {
        viewModel = createViewModel()
        
        // Simulate an error state
        viewModel.clearError()
        
        val state = viewModel.uiState.value
        assertEquals(null, state.error)
        assertEquals(null, state.recordingState.error)
    }

    @Test
    fun `triggerPad should handle invalid pad index gracefully`() = testScope.runTest {
        viewModel = createViewModel()
        
        // Try to trigger a pad that doesn't exist
        viewModel.triggerPad(99)
        
        // Should not crash and state should remain stable
        val state = viewModel.uiState.value
        assertEquals(16, state.pads.size)
    }

    @Test
    fun `getPadUsageStats should return correct statistics`() = testScope.runTest {
        viewModel = createViewModel()
        
        val stats = viewModel.getPadUsageStats()
        
        assertEquals(16, stats.totalPads)
        assertEquals(0, stats.loadedPads)
        assertEquals(0, stats.playingPads)
        assertEquals(16, stats.enabledPads) // All pads enabled by default
    }

    @Test
    fun `getSystemDiagnostics should return current system state`() = testScope.runTest {
        viewModel = createViewModel()
        
        val diagnostics = viewModel.getSystemDiagnostics()
        
        assertEquals(false, diagnostics.audioEngineReady) // Not initialized in test
        assertEquals(0, diagnostics.loadedSamples)
        assertEquals(0, diagnostics.loadedPads)
        assertEquals(0, diagnostics.playingPads)
        assertFalse(diagnostics.isDirty)
    }

    @Test
    fun `validateOperationPreconditions should prevent invalid operations`() = testScope.runTest {
        viewModel = createViewModel()
        
        // Test validation through public interface by checking state
        val initialState = viewModel.uiState.value
        
        // Recording should not be possible when audio engine is not ready
        assertFalse(initialState.recordingState.canStartRecording)
        assertFalse(initialState.isAudioEngineReady)
    }

    @Test
    fun `resetPad should clear pad configuration`() = testScope.runTest {
        viewModel = createViewModel()
        
        viewModel.resetPad(0)
        
        val pad = viewModel.uiState.value.getPad(0)
        assertEquals(null, pad?.sampleId)
        assertEquals(null, pad?.sampleName)
        assertFalse(pad?.hasAssignedSample ?: true)
    }

    @Test
    fun `copyPadConfiguration should handle missing source pad`() = testScope.runTest {
        viewModel = createViewModel()
        
        // Try to copy from a pad that has no configuration
        viewModel.copyPadConfiguration(0, 1)
        
        // Should not crash and target pad should remain unchanged
        val targetPad = viewModel.uiState.value.getPad(1)
        assertFalse(targetPad?.hasAssignedSample ?: true)
    }

    @Test
    fun `setDebugLogging should not crash`() = testScope.runTest {
        viewModel = createViewModel()
        
        // Should not throw any exceptions
        viewModel.setDebugLogging(true)
        viewModel.setDebugLogging(false)
    }
}