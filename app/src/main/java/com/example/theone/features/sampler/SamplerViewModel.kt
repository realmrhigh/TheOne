package com.example.theone.features.sampler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.AudioInputSource
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Placeholder for AudioEngineControl interface (C1) //TODO: Remove this comment, using AudioEngine now
// Placeholder for ProjectManager interface (C3) //TODO: Remove this comment
// Local Placeholder for SampleMetadata. //TODO: Remove this comment
// Ideally, this would be in a shared 'core.model' module as per README.
// Ensure this definition is present if not already.

enum class RecordingState {
    IDLE,
    ARMED,
    RECORDING,
    REVIEWING
}

class SamplerViewModel(
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordedSamplesQueue = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val recordedSamplesQueue: StateFlow<List<SampleMetadata>> = _recordedSamplesQueue.asStateFlow()

    private val MAX_RECORDINGS = 3

    fun armSampler() {
        _recordedSamplesQueue.value = emptyList()
        _recordingState.value = RecordingState.ARMED
    }

    fun startRecording(audioInputSource: AudioInputSource) {
        if (_recordingState.value == RecordingState.ARMED) {
            _recordingState.value = RecordingState.RECORDING
            viewModelScope.launch {
                // TODO: Define how tempFilePath is generated or passed
                val tempFilePath = "path/to/temp/sample.wav" // Placeholder
                val newSample = audioEngine.startAudioRecording(audioInputSource, tempFilePath)
                onRecordingFinished(newSample)
            }
        }
    }

    fun stopRecordingAndFinalize() { // User presses Stop during RECORDING
        if (_recordingState.value == RecordingState.RECORDING) {
            audioEngine.stopCurrentRecording() // Assume AudioEngine has this method
            // Actual state transition to ARMED (or REVIEWING) should be handled by onRecordingFinished,
            // which is assumed to be called by AudioEngine after recording finalization.
            // If AudioEngine doesn't call back, manual state change would be needed here.
        }
    }

    fun onRecordingFinished(newSample: SampleMetadata) { // Called by AudioEngine or after startRecording completes
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        if (currentQueue.size == MAX_RECORDINGS) {
            currentQueue.removeAt(0) // Remove the oldest if queue is full
        }
        currentQueue.add(newSample)
        _recordedSamplesQueue.value = currentQueue
        _recordingState.value = RecordingState.ARMED // As per spec, return to ARMED
    }

    fun disarmOrFinishSession() { // User presses "Stop" button when ARMED (to finish session) or "Done/Back" from REVIEWING
        if (_recordingState.value == RecordingState.ARMED) {
            if (_recordedSamplesQueue.value.isNotEmpty()) {
                _recordingState.value = RecordingState.REVIEWING
            } else {
                _recordingState.value = RecordingState.IDLE
            }
        } else if (_recordingState.value == RecordingState.REVIEWING) {
            _recordingState.value = RecordingState.IDLE
            _recordedSamplesQueue.value = emptyList() // Clear queue after review session is done
        }
    }

    fun auditionSample(sample: SampleMetadata) {
        // Ensure sample.uri is valid and accessible
        audioEngine.playSampleSlice(sample.uri, sample.trimStartMs, sample.trimEndMs)
        // Note: spec says sampleId for playSampleSlice, but in review, we might only have a URI from a temp file.
        // Adjusting to use URI for now. If it must be an ID, it means samples are added to pool before review.
    }

    fun saveSample(sample: SampleMetadata, name: String) {
        val namedSample = sample.copy(name = name)
        projectManager.addSampleToPool(namedSample)
        // Potentially remove from queue or update UI to show it's saved
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        currentQueue.remove(sample)
        _recordedSamplesQueue.value = currentQueue
        // If queue is empty after saving the last sample, transition to IDLE
        if (_recordedSamplesQueue.value.isEmpty()) {
            _recordingState.value = RecordingState.IDLE
        }
    }

    fun discardSample(sample: SampleMetadata) {
        // Delete physical file if it's temporary and this ViewModel is responsible for it
        // For now, just remove from queue
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        currentQueue.remove(sample)
        _recordedSamplesQueue.value = currentQueue
        // If queue is empty after discarding the last sample, transition to IDLE
        if (_recordedSamplesQueue.value.isEmpty()) {
            _recordingState.value = RecordingState.IDLE
        }
    }
}
