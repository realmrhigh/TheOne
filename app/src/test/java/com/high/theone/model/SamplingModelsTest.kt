package com.high.theone.model

import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Unit tests for the sampling data models to verify functionality,
 * serialization, and business logic.
 */
class SamplingModelsTest {

    @Test
    fun `PadState should have correct default values`() {
        val padState = PadState(index = 5)
        
        assertEquals(5, padState.index)
        assertNull(padState.sampleId)
        assertNull(padState.sampleName)
        assertFalse(padState.isPlaying)
        assertEquals(1.0f, padState.volume, 0.001f)
        assertEquals(0.0f, padState.pan, 0.001f)
        assertEquals(PlaybackMode.ONE_SHOT, padState.playbackMode)
        assertFalse(padState.hasAssignedSample)
        assertFalse(padState.canTrigger)
        assertEquals("Pad 6", padState.displayName) // index + 1
    }

    @Test
    fun `PadState canTrigger should work correctly`() {
        val emptyPad = PadState(index = 0)
        assertFalse(emptyPad.canTrigger)
        
        val loadedPad = PadState(
            index = 0,
            sampleId = "sample123",
            hasAssignedSample = true
        )
        assertTrue(loadedPad.canTrigger)
        
        val disabledPad = loadedPad.copy(isEnabled = false)
        assertFalse(disabledPad.canTrigger)
        
        val loadingPad = loadedPad.copy(isLoading = true)
        assertFalse(loadingPad.canTrigger)
    }

    @Test
    fun `RecordingState should have correct default values`() {
        val recordingState = RecordingState()
        
        assertFalse(recordingState.isRecording)
        assertFalse(recordingState.isPaused)
        assertEquals(0L, recordingState.durationMs)
        assertEquals(0.0f, recordingState.peakLevel, 0.001f)
        assertFalse(recordingState.isInitialized)
        assertNull(recordingState.error)
        assertEquals(AudioInputSource.MICROPHONE, recordingState.inputSource)
        assertEquals(44100, recordingState.sampleRate)
        assertEquals(1, recordingState.channels)
        assertEquals(30, recordingState.maxDurationSeconds)
        assertFalse(recordingState.canStartRecording) // Not initialized
        assertFalse(recordingState.canStopRecording) // Not recording
    }

    @Test
    fun `RecordingState formattedDuration should format correctly`() {
        val state1 = RecordingState(durationMs = 0L)
        assertEquals("00:00", state1.formattedDuration)
        
        val state2 = RecordingState(durationMs = 30000L) // 30 seconds
        assertEquals("00:30", state2.formattedDuration)
        
        val state3 = RecordingState(durationMs = 125000L) // 2:05
        assertEquals("02:05", state3.formattedDuration)
    }

    @Test
    fun `RecordingState should handle recording workflow states`() {
        val initialState = RecordingState(isInitialized = true)
        assertTrue(initialState.canStartRecording)
        assertFalse(initialState.canStopRecording)
        
        val recordingState = initialState.copy(isRecording = true)
        assertFalse(recordingState.canStartRecording)
        assertTrue(recordingState.canStopRecording)
        
        val processingState = recordingState.copy(isProcessing = true)
        assertFalse(processingState.canStartRecording)
        assertFalse(processingState.canStopRecording)
    }

    @Test
    fun `SampleTrimSettings should have correct default values`() {
        val trimSettings = SampleTrimSettings()
        
        assertEquals(0.0f, trimSettings.startTime, 0.001f)
        assertEquals(1.0f, trimSettings.endTime, 0.001f)
        assertEquals(0.0f, trimSettings.fadeInMs, 0.001f)
        assertEquals(0.0f, trimSettings.fadeOutMs, 0.001f)
        assertFalse(trimSettings.normalize)
        assertFalse(trimSettings.reverse)
        assertEquals(1.0f, trimSettings.gain, 0.001f)
        assertEquals(0L, trimSettings.originalDurationMs)
        assertTrue(trimSettings.preserveOriginal)
        assertFalse(trimSettings.isTrimmed)
        assertFalse(trimSettings.hasProcessing)
        assertTrue(trimSettings.isValid())
    }

    @Test
    fun `SampleTrimSettings should calculate times correctly`() {
        val trimSettings = SampleTrimSettings(
            startTime = 0.25f, // 25%
            endTime = 0.75f,   // 75%
            originalDurationMs = 4000L // 4 seconds
        )
        
        assertEquals(1000L, trimSettings.startTimeMs) // 25% of 4000ms
        assertEquals(3000L, trimSettings.endTimeMs)   // 75% of 4000ms
        assertEquals(2000L, trimSettings.trimmedDurationMs) // 3000 - 1000
        assertTrue(trimSettings.isTrimmed)
        assertTrue(trimSettings.hasProcessing)
    }

    @Test
    fun `SampleTrimSettings validation should work correctly`() {
        val validSettings = SampleTrimSettings(
            startTime = 0.2f,
            endTime = 0.8f,
            fadeInMs = 10.0f,
            fadeOutMs = 10.0f,
            gain = 1.5f
        )
        assertTrue(validSettings.isValid())
        
        val invalidStart = validSettings.copy(startTime = -0.1f)
        assertFalse(invalidStart.isValid())
        
        val invalidEnd = validSettings.copy(endTime = 1.1f)
        assertFalse(invalidEnd.isValid())
        
        val invalidOrder = validSettings.copy(startTime = 0.8f, endTime = 0.2f)
        assertFalse(invalidOrder.isValid())
        
        val invalidGain = validSettings.copy(gain = 0.0f)
        assertFalse(invalidGain.isValid())
    }

    @Test
    fun `SamplingUiState should have correct default values`() {
        val uiState = SamplingUiState()
        
        assertEquals(RecordingState(), uiState.recordingState)
        assertEquals(16, uiState.pads.size)
        assertTrue(uiState.availableSamples.isEmpty())
        assertNull(uiState.selectedPad)
        assertFalse(uiState.isLoading)
        assertNull(uiState.error)
        assertFalse(uiState.isAudioEngineReady)
        assertNull(uiState.currentProject)
        assertFalse(uiState.isDirty)
        assertTrue(uiState.playingPads.isEmpty())
        assertTrue(uiState.loadedPads.isEmpty())
        assertFalse(uiState.isBusy)
    }

    @Test
    fun `SamplingUiState should handle pad queries correctly`() {
        val pads = listOf(
            PadState(0, hasAssignedSample = true, isPlaying = true),
            PadState(1, hasAssignedSample = true, isPlaying = false),
            PadState(2, hasAssignedSample = false, isPlaying = false)
        )
        val uiState = SamplingUiState(pads = pads)
        
        assertEquals(pads[0], uiState.getPad(0))
        assertNull(uiState.getPad(10))
        
        assertEquals(1, uiState.playingPads.size)
        assertEquals(pads[0], uiState.playingPads[0])
        
        assertEquals(2, uiState.loadedPads.size)
        assertTrue(uiState.loadedPads.contains(pads[0]))
        assertTrue(uiState.loadedPads.contains(pads[1]))
    }

    @Test
    fun `models should be serializable to JSON`() {
        val padState = PadState(
            index = 5,
            sampleId = "sample123",
            sampleName = "Kick Drum",
            volume = 0.8f,
            playbackMode = PlaybackMode.LOOP
        )
        
        val json = Json.encodeToString(padState)
        val decoded = Json.decodeFromString<PadState>(json)
        
        assertEquals(padState, decoded)
    }

    @Test
    fun `RecordingState should be serializable to JSON`() {
        val recordingState = RecordingState(
            isRecording = true,
            durationMs = 5000L,
            peakLevel = 0.7f,
            sampleRate = 48000,
            channels = 2
        )
        
        val json = Json.encodeToString(recordingState)
        val decoded = Json.decodeFromString<RecordingState>(json)
        
        assertEquals(recordingState, decoded)
    }

    @Test
    fun `SampleTrimSettings should be serializable to JSON`() {
        val trimSettings = SampleTrimSettings(
            startTime = 0.1f,
            endTime = 0.9f,
            fadeInMs = 50.0f,
            fadeOutMs = 100.0f,
            normalize = true,
            gain = 1.2f,
            originalDurationMs = 3000L
        )
        
        val json = Json.encodeToString(trimSettings)
        val decoded = Json.decodeFromString<SampleTrimSettings>(json)
        
        assertEquals(trimSettings, decoded)
    }
}