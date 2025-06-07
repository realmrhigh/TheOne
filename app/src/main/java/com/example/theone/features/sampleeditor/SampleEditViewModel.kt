package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SampleEditViewModel(
    private val initialSampleMetadata: SampleMetadata, // Order changed
    private val audioEngine: AudioEngine, // Type changed
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _editableSampleMetadata = MutableStateFlow(initialSampleMetadata)
    val editableSampleMetadata: StateFlow<SampleMetadata> = _editableSampleMetadata.asStateFlow()

    // Expose trim points separately if UI binds to them directly
    private val _trimStartMs = MutableStateFlow(initialSampleMetadata.trimStartMs)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(initialSampleMetadata.trimEndMs)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    init {
        // The SampleMetadata from previous subtask has 'duration', not 'durationMs'.
        // And its constructor already handles setting trimEndMs to duration if it's 0.
        // So, we trust initialSampleMetadata is already correctly initialized.
        // If initialSampleMetadata.trimEndMs is 0, it implies it was meant to be full duration.
        val correctedTrimEndMs = if (initialSampleMetadata.trimEndMs == 0L && initialSampleMetadata.duration > 0L) {
            initialSampleMetadata.duration
        } else {
            initialSampleMetadata.trimEndMs
        }

        // If initialSampleMetadata itself needs correction based on its own duration property
        // (e.g. if it came from a source where trimEndMs might be 0 by mistake for a non-zero duration sample)
        // then update _editableSampleMetadata with a potentially corrected copy.
        if (initialSampleMetadata.trimEndMs != correctedTrimEndMs) {
            _editableSampleMetadata.value = initialSampleMetadata.copy(trimEndMs = correctedTrimEndMs)
        } else {
            _editableSampleMetadata.value = initialSampleMetadata
        }

        _trimStartMs.value = _editableSampleMetadata.value.trimStartMs // Use potentially corrected start
        _trimEndMs.value = _editableSampleMetadata.value.trimEndMs   // Use potentially corrected end
    }

    fun updateTrimPoints(startMs: Long, endMs: Long) {
        // Add validation: 0 <= startMs < endMs <= currentSample.duration
        val currentSample = _editableSampleMetadata.value
        val validStartMs = startMs.coerceIn(0, currentSample.duration)
        // Ensure endMs is not less than startMs, and not more than duration
        val validEndMs = endMs.coerceIn(validStartMs, currentSample.duration)

        _trimStartMs.value = validStartMs
        _trimEndMs.value = validEndMs
        _editableSampleMetadata.value = currentSample.copy(trimStartMs = validStartMs, trimEndMs = validEndMs)
    }

    fun auditionSlice() {
        val currentSample = _editableSampleMetadata.value
        // audioEngine.playSampleSlice expects a URI or resolvable ID.
        // currentSample.uri should be suitable here.
        audioEngine.playSampleSlice(currentSample.uri, _trimStartMs.value, _trimEndMs.value)
    }

    fun saveChanges() {
        // Persist the changes through ProjectManager
        projectManager.updateSampleMetadata(_editableSampleMetadata.value)
        // Optionally, have a way to signal completion/navigation back
        // This could be via a SharedFlow event or another StateFlow boolean.
    }
}
