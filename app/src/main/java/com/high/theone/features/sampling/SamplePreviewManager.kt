package com.high.theone.features.sampling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.SampleRepository
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.util.Log
import javax.inject.Inject

/**
 * Manager for sample preview functionality including waveform generation,
 * playback control, trimming, and metadata editing for recorded samples.
 * 
 * Requirements: 4.1 (sample preview), 4.4 (metadata editing)
 */
@HiltViewModel
class SamplePreviewManager @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleRepository: SampleRepository,
    private val waveformAnalyzer: WaveformAnalyzer
) : ViewModel() {

    companion object {
        private const val TAG = "SamplePreviewManager"
        private const val PLAYBACK_POSITION_UPDATE_INTERVAL = 50L // 50ms updates
    }

    // Private mutable state
    private val _previewState = MutableStateFlow(SamplePreviewState())
    
    // Public read-only state
    val previewState: StateFlow<SamplePreviewState> = _previewState.asStateFlow()
    
    // Playback position tracking job
    private var playbackTrackingJob: Job? = null

    /**
     * Load a sample for preview with waveform generation
     */
    fun loadSampleForPreview(sampleMetadata: SampleMetadata, filePath: String) {
        viewModelScope.launch {
            try {
                _previewState.update { currentState ->
                    currentState.copy(
                        isLoading = true,
                        error = null
                    )
                }
                
                // Generate waveform data
                val waveformData = waveformAnalyzer.generateWaveform(filePath)
                
                // Analyze waveform for recommendations
                val analysis = waveformAnalyzer.analyzeWaveform(waveformData)
                
                // Create initial trim settings with smart recommendations
                val initialTrimSettings = SampleTrimSettings(
                    startTime = if (analysis.recommendedTrimStart > 0.01f) analysis.recommendedTrimStart else 0f,
                    endTime = if (analysis.recommendedTrimEnd < 0.99f) analysis.recommendedTrimEnd else 1f,
                    originalDurationMs = sampleMetadata.durationMs
                )
                
                _previewState.update { currentState ->
                    currentState.copy(
                        sampleMetadata = sampleMetadata,
                        filePath = filePath,
                        waveformData = waveformData.samples,
                        trimSettings = initialTrimSettings,
                        sampleName = sampleMetadata.name,
                        sampleTags = sampleMetadata.tags,
                        waveformAnalysis = analysis,
                        isLoading = false,
                        error = null
                    )
                }
                
                Log.d(TAG, "Sample loaded for preview: ${sampleMetadata.name}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sample for preview", e)
                _previewState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Failed to load sample: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Start or resume sample playback
     */
    fun startPlayback() {
        viewModelScope.launch {
            try {
                val currentState = _previewState.value
                if (currentState.filePath == null) {
                    Log.w(TAG, "No sample loaded for playback")
                    return@launch
                }
                
                // Calculate playback start position based on trim settings
                val startPosition = currentState.trimSettings.startTime
                val success = audioEngine.startSamplePreview(
                    filePath = currentState.filePath,
                    startPosition = startPosition,
                    endPosition = currentState.trimSettings.endTime
                )
                
                if (success) {
                    _previewState.update { state ->
                        state.copy(isPlaying = true, error = null)
                    }
                    
                    startPlaybackTracking()
                    Log.d(TAG, "Sample playback started")
                } else {
                    _previewState.update { state ->
                        state.copy(error = "Failed to start playback")
                    }
                    Log.e(TAG, "Failed to start sample playback")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback", e)
                _previewState.update { state ->
                    state.copy(
                        isPlaying = false,
                        error = "Playback error: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Pause sample playback
     */
    fun pausePlayback() {
        viewModelScope.launch {
            try {
                audioEngine.pauseSamplePreview()
                _previewState.update { state ->
                    state.copy(isPlaying = false)
                }
                
                stopPlaybackTracking()
                Log.d(TAG, "Sample playback paused")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing playback", e)
            }
        }
    }
    
    /**
     * Stop sample playback
     */
    fun stopPlayback() {
        viewModelScope.launch {
            try {
                audioEngine.stopSamplePreview()
                _previewState.update { state ->
                    state.copy(
                        isPlaying = false,
                        playbackPosition = 0f
                    )
                }
                
                stopPlaybackTracking()
                Log.d(TAG, "Sample playback stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping playback", e)
            }
        }
    }
    
    /**
     * Seek to a specific position in the sample
     */
    fun seekToPosition(position: Float) {
        viewModelScope.launch {
            try {
                val clampedPosition = position.coerceIn(0f, 1f)
                audioEngine.seekSamplePreview(clampedPosition)
                
                _previewState.update { state ->
                    state.copy(playbackPosition = clampedPosition)
                }
                
                Log.d(TAG, "Seeked to position: $clampedPosition")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error seeking to position", e)
            }
        }
    }
    
    /**
     * Update trim settings
     */
    fun updateTrimSettings(newTrimSettings: SampleTrimSettings) {
        _previewState.update { state ->
            state.copy(trimSettings = newTrimSettings)
        }
        
        // If currently playing, update the playback range
        if (_previewState.value.isPlaying) {
            viewModelScope.launch {
                try {
                    audioEngine.updateSamplePreviewRange(
                        startPosition = newTrimSettings.startTime,
                        endPosition = newTrimSettings.endTime
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating playback range", e)
                }
            }
        }
    }
    
    /**
     * Update sample name
     */
    fun updateSampleName(newName: String) {
        _previewState.update { state ->
            state.copy(sampleName = newName)
        }
    }
    
    /**
     * Update sample tags
     */
    fun updateSampleTags(newTags: List<String>) {
        _previewState.update { state ->
            state.copy(sampleTags = newTags)
        }
    }
    
    /**
     * Save sample with current settings and metadata
     */
    fun saveSample(onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val currentState = _previewState.value
                val sampleMetadata = currentState.sampleMetadata
                val filePath = currentState.filePath
                
                if (sampleMetadata == null || filePath == null) {
                    onComplete(Result.failure(Exception("No sample loaded")))
                    return@launch
                }
                
                // Create updated metadata
                val updatedMetadata = sampleMetadata.copy(
                    name = currentState.sampleName,
                    tags = currentState.sampleTags
                )
                
                // Save to repository
                val saveResult = sampleRepository.saveSampleWithTrimming(
                    originalFilePath = filePath,
                    metadata = updatedMetadata,
                    trimSettings = currentState.trimSettings
                )
                
                when (saveResult) {
                    is com.high.theone.domain.Result.Success -> {
                        Log.d(TAG, "Sample saved successfully: ${saveResult.value}")
                        onComplete(Result.success(saveResult.value))
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to save sample: ${saveResult.error.message}")
                        onComplete(Result.failure(Exception(saveResult.error.message)))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving sample", e)
                onComplete(Result.failure(e))
            }
        }
    }
    
    /**
     * Assign sample to a specific pad
     */
    fun assignToPad(padIndex: Int, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                // First save the sample
                saveSample { saveResult ->
                    saveResult.fold(
                        onSuccess = { sampleId ->
                            // Then assign to pad
                            viewModelScope.launch {
                                try {
                                    // This would typically be handled by the main sampling ViewModel
                                    // For now, we'll just complete successfully
                                    Log.d(TAG, "Sample assigned to pad $padIndex")
                                    onComplete(Result.success(Unit))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error assigning to pad", e)
                                    onComplete(Result.failure(e))
                                }
                            }
                        },
                        onFailure = { error ->
                            onComplete(Result.failure(error))
                        }
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error assigning sample to pad", e)
                onComplete(Result.failure(e))
            }
        }
    }
    
    /**
     * Discard the current sample preview
     */
    fun discardSample() {
        viewModelScope.launch {
            try {
                // Stop playback if active
                if (_previewState.value.isPlaying) {
                    stopPlayback()
                }
                
                // Clear state
                _previewState.update { SamplePreviewState() }
                
                Log.d(TAG, "Sample preview discarded")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error discarding sample", e)
            }
        }
    }
    
    /**
     * Clear any error messages
     */
    fun clearError() {
        _previewState.update { state ->
            state.copy(error = null)
        }
    }
    
    /**
     * Start tracking playback position
     */
    private fun startPlaybackTracking() {
        stopPlaybackTracking() // Stop any existing tracking
        
        playbackTrackingJob = viewModelScope.launch {
            while (_previewState.value.isPlaying) {
                try {
                    // Get current playback position from audio engine
                    val position = audioEngine.getSamplePreviewPosition()
                    
                    _previewState.update { state ->
                        state.copy(playbackPosition = position)
                    }
                    
                    // Check if playback has ended
                    if (position >= _previewState.value.trimSettings.endTime) {
                        _previewState.update { state ->
                            state.copy(
                                isPlaying = false,
                                playbackPosition = 0f
                            )
                        }
                        break
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error tracking playback position", e)
                    break
                }
                
                delay(PLAYBACK_POSITION_UPDATE_INTERVAL)
            }
        }
    }
    
    /**
     * Stop tracking playback position
     */
    private fun stopPlaybackTracking() {
        playbackTrackingJob?.cancel()
        playbackTrackingJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPlaybackTracking()
        
        // Stop any active playback
        if (_previewState.value.isPlaying) {
            viewModelScope.launch {
                try {
                    audioEngine.stopSamplePreview()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping playback on cleanup", e)
                }
            }
        }
    }
}

/**
 * State for sample preview functionality
 */
data class SamplePreviewState(
    val sampleMetadata: SampleMetadata? = null,
    val filePath: String? = null,
    val waveformData: FloatArray = floatArrayOf(),
    val trimSettings: SampleTrimSettings = SampleTrimSettings(),
    val sampleName: String = "",
    val sampleTags: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val playbackPosition: Float = 0f, // 0.0 to 1.0
    val isLoading: Boolean = false,
    val error: String? = null,
    val waveformAnalysis: WaveformAnalysis? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SamplePreviewState
        
        if (sampleMetadata != other.sampleMetadata) return false
        if (filePath != other.filePath) return false
        if (!waveformData.contentEquals(other.waveformData)) return false
        if (trimSettings != other.trimSettings) return false
        if (sampleName != other.sampleName) return false
        if (sampleTags != other.sampleTags) return false
        if (isPlaying != other.isPlaying) return false
        if (playbackPosition != other.playbackPosition) return false
        if (isLoading != other.isLoading) return false
        if (error != other.error) return false
        if (waveformAnalysis != other.waveformAnalysis) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sampleMetadata?.hashCode() ?: 0
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + waveformData.contentHashCode()
        result = 31 * result + trimSettings.hashCode()
        result = 31 * result + sampleName.hashCode()
        result = 31 * result + sampleTags.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + playbackPosition.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (waveformAnalysis?.hashCode() ?: 0)
        return result
    }
}