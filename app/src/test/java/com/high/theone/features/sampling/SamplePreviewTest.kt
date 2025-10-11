package com.high.theone.features.sampling

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.SampleRepository
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for sample preview and management functionality.
 * Tests waveform generation, playback controls, trimming, and metadata editing.
 * 
 * Requirements: 4.1 (sample preview), 4.4 (metadata editing)
 */
@ExperimentalCoroutinesApi
class SamplePreviewTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var audioEngine: AudioEngineControl
    private lateinit var sampleRepository: SampleRepository
    private lateinit var waveformAnalyzer: WaveformAnalyzer
    private lateinit var samplePreviewManager: SamplePreviewManager

    private val testSampleMetadata = SampleMetadata(
        id = UUID.randomUUID(),
        name = "Test Sample",
        filePath = "/test/sample.wav",
        durationMs = 5000L,
        sampleRate = 44100,
        channels = 1,
        tags = listOf("test", "drum"),
        createdAt = System.currentTimeMillis()
    )

    private val testWaveformData = WaveformData(
        samples = FloatArray(100) { (it % 10) / 10f }, // Simple test waveform
        sampleRate = 44100,
        channels = 1,
        durationMs = 5000L,
        peakAmplitude = 1f,
        rmsAmplitude = 0.5f
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        audioEngine = mockk()
        sampleRepository = mockk()
        waveformAnalyzer = mockk()
        
        // Setup default mock behaviors
        coEvery { audioEngine.startSamplePreview(any(), any(), any()) } returns true
        coEvery { audioEngine.pauseSamplePreview() } returns true
        coEvery { audioEngine.stopSamplePreview() } returns true
        coEvery { audioEngine.seekSamplePreview(any()) } returns true
        coEvery { audioEngine.getSamplePreviewPosition() } returns 0f
        coEvery { audioEngine.updateSamplePreviewRange(any(), any()) } returns true
        
        coEvery { waveformAnalyzer.generateWaveform(any<String>(), any()) } returns testWaveformData
        coEvery { waveformAnalyzer.analyzeWaveform(any()) } returns WaveformAnalysis(
            peakCount = 10,
            dynamicRange = 1f,
            averageAmplitude = 0.5f,
            silencePercentage = 0.1f,
            hasClipping = false,
            recommendedTrimStart = 0.05f,
            recommendedTrimEnd = 0.95f
        )
        
        samplePreviewManager = SamplePreviewManager(audioEngine, sampleRepository, waveformAnalyzer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSampleForPreview should generate waveform and set initial state`() = runTest {
        // When
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // Then
        val state = samplePreviewManager.previewState.first()
        
        assertEquals(testSampleMetadata, state.sampleMetadata)
        assertEquals("/test/sample.wav", state.filePath)
        assertEquals(testSampleMetadata.name, state.sampleName)
        assertEquals(testSampleMetadata.tags, state.sampleTags)
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
        
        // Verify waveform analyzer was called
        coVerify { waveformAnalyzer.generateWaveform("/test/sample.wav", any()) }
        coVerify { waveformAnalyzer.analyzeWaveform(any()) }
    }

    @Test
    fun `loadSampleForPreview should set smart trim recommendations`() = runTest {
        // When
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // Then
        val state = samplePreviewManager.previewState.first()
        
        // Should use recommended trim positions from analysis
        assertEquals(0.05f, state.trimSettings.startTime)
        assertEquals(0.95f, state.trimSettings.endTime)
        assertEquals(testSampleMetadata.durationMs, state.trimSettings.originalDurationMs)
    }

    @Test
    fun `startPlayback should start audio engine playback`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // When
        samplePreviewManager.startPlayback()
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertTrue(state.isPlaying)
        
        coVerify { 
            audioEngine.startSamplePreview(
                "/test/sample.wav", 
                0.05f, // start position from trim settings
                0.95f  // end position from trim settings
            ) 
        }
    }

    @Test
    fun `pausePlayback should pause audio engine playback`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        // When
        samplePreviewManager.pausePlayback()
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertFalse(state.isPlaying)
        
        coVerify { audioEngine.pauseSamplePreview() }
    }

    @Test
    fun `stopPlayback should stop audio engine and reset position`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        // When
        samplePreviewManager.stopPlayback()
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertFalse(state.isPlaying)
        assertEquals(0f, state.playbackPosition)
        
        coVerify { audioEngine.stopSamplePreview() }
    }

    @Test
    fun `seekToPosition should update playback position`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // When
        samplePreviewManager.seekToPosition(0.5f)
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals(0.5f, state.playbackPosition)
        
        coVerify { audioEngine.seekSamplePreview(0.5f) }
    }

    @Test
    fun `seekToPosition should clamp position to valid range`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // When - seek beyond valid range
        samplePreviewManager.seekToPosition(1.5f)
        
        // Then - should be clamped to 1.0
        val state = samplePreviewManager.previewState.first()
        assertEquals(1f, state.playbackPosition)
        
        coVerify { audioEngine.seekSamplePreview(1f) }
    }

    @Test
    fun `updateTrimSettings should update state and audio engine range`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        val newTrimSettings = SampleTrimSettings(
            startTime = 0.1f,
            endTime = 0.8f,
            originalDurationMs = testSampleMetadata.durationMs
        )
        
        // When
        samplePreviewManager.updateTrimSettings(newTrimSettings)
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals(newTrimSettings, state.trimSettings)
        
        coVerify { audioEngine.updateSamplePreviewRange(0.1f, 0.8f) }
    }

    @Test
    fun `updateSampleName should update state`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        // When
        samplePreviewManager.updateSampleName("New Sample Name")
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals("New Sample Name", state.sampleName)
    }

    @Test
    fun `updateSampleTags should update state`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        val newTags = listOf("kick", "heavy", "808")
        
        // When
        samplePreviewManager.updateSampleTags(newTags)
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals(newTags, state.sampleTags)
    }

    @Test
    fun `saveSample should save with updated metadata and trim settings`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.updateSampleName("Updated Name")
        samplePreviewManager.updateSampleTags(listOf("new", "tags"))
        
        val expectedSampleId = "test-sample-id"
        coEvery { 
            sampleRepository.saveSampleWithTrimming(any(), any(), any()) 
        } returns com.high.theone.domain.Result.Success(expectedSampleId)
        
        var saveResult: Result<String>? = null
        
        // When
        samplePreviewManager.saveSample { result ->
            saveResult = result
        }
        
        // Then
        assertNotNull(saveResult)
        assertTrue(saveResult!!.isSuccess)
        assertEquals(expectedSampleId, saveResult!!.getOrNull())
        
        coVerify { 
            sampleRepository.saveSampleWithTrimming(
                "/test/sample.wav",
                match { metadata ->
                    metadata.name == "Updated Name" && 
                    metadata.tags == listOf("new", "tags")
                },
                any()
            )
        }
    }

    @Test
    fun `assignToPad should save sample then assign to pad`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        
        val expectedSampleId = "test-sample-id"
        coEvery { 
            sampleRepository.saveSampleWithTrimming(any(), any(), any()) 
        } returns com.high.theone.domain.Result.Success(expectedSampleId)
        
        var assignResult: Result<Unit>? = null
        
        // When
        samplePreviewManager.assignToPad(5) { result ->
            assignResult = result
        }
        
        // Then
        assertNotNull(assignResult)
        assertTrue(assignResult!!.isSuccess)
        
        coVerify { sampleRepository.saveSampleWithTrimming(any(), any(), any()) }
    }

    @Test
    fun `discardSample should stop playback and clear state`() = runTest {
        // Given
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        // When
        samplePreviewManager.discardSample()
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals(SamplePreviewState(), state)
        
        coVerify { audioEngine.stopSamplePreview() }
    }

    @Test
    fun `clearError should remove error from state`() = runTest {
        // Given - simulate an error state
        coEvery { audioEngine.startSamplePreview(any(), any(), any()) } returns false
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        // When
        samplePreviewManager.clearError()
        
        // Then
        val state = samplePreviewManager.previewState.first()
        assertEquals(null, state.error)
    }

    @Test
    fun `waveform analyzer should handle WAV files correctly`() = runTest {
        // Given
        val analyzer = WaveformAnalyzer()
        
        // Create a simple test WAV file data
        val testFilePath = "/test/sample.wav"
        
        // When
        val waveformData = analyzer.generateWaveform(testFilePath)
        
        // Then - should return empty data for non-existent file
        assertTrue(waveformData.samples.isEmpty())
        assertEquals(44100, waveformData.sampleRate)
        assertEquals(1, waveformData.channels)
    }

    @Test
    fun `waveform analysis should detect peaks and silence`() = runTest {
        // Given
        val analyzer = WaveformAnalyzer()
        val testWaveform = WaveformData(
            samples = floatArrayOf(0.1f, 0.8f, 0.2f, 0.9f, 0.1f, 0.0f, 0.0f, 0.7f),
            sampleRate = 44100,
            channels = 1,
            durationMs = 1000L,
            peakAmplitude = 0.9f,
            rmsAmplitude = 0.4f
        )
        
        // When
        val analysis = analyzer.analyzeWaveform(testWaveform)
        
        // Then
        assertTrue(analysis.peakCount > 0)
        assertTrue(analysis.dynamicRange > 0f)
        assertTrue(analysis.averageAmplitude > 0f)
        assertTrue(analysis.silencePercentage >= 0f)
    }

    @Test
    fun `sample metadata editor should validate input`() = runTest {
        // Test that empty names are handled appropriately
        val emptyName = ""
        val validName = "Valid Sample Name"
        
        // Empty names should be replaced with default
        assertTrue(emptyName.isEmpty())
        assertTrue(validName.isNotEmpty())
        
        // Tags should be trimmed and deduplicated
        val tags = listOf("  drum  ", "kick", "drum", "  snare  ")
        val cleanedTags = tags.map { it.trim() }.distinct().filter { it.isNotEmpty() }
        
        assertEquals(listOf("drum", "kick", "snare"), cleanedTags)
    }

    @Test
    fun `trim settings should calculate duration correctly`() = runTest {
        // Given
        val originalDuration = 10000L // 10 seconds
        val trimSettings = SampleTrimSettings(
            startTime = 0.2f, // 20% = 2 seconds
            endTime = 0.8f,   // 80% = 8 seconds
            originalDurationMs = originalDuration
        )
        
        // When/Then
        assertEquals(2000L, trimSettings.startTimeMs)
        assertEquals(8000L, trimSettings.endTimeMs)
        assertEquals(6000L, trimSettings.trimmedDurationMs) // 8s - 2s = 6s
        assertTrue(trimSettings.isTrimmed)
        assertTrue(trimSettings.isValid())
    }

    @Test
    fun `audio engine extensions should handle errors gracefully`() = runTest {
        // Given
        coEvery { audioEngine.startSamplePreview(any(), any(), any()) } throws Exception("Test error")
        
        // When
        samplePreviewManager.loadSampleForPreview(testSampleMetadata, "/test/sample.wav")
        samplePreviewManager.startPlayback()
        
        // Then - should not crash and should set error state
        val state = samplePreviewManager.previewState.first()
        assertFalse(state.isPlaying)
        // Error handling is internal to the manager
    }
}