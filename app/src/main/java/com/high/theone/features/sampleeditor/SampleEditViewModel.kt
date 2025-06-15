package com.high.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.model.SampleMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SampleEditViewModel @AssistedInject constructor(
    @Assisted private val initialSampleMetadata: SampleMetadata, // Order changed
    private val audioEngine: AudioEngineControl, // Type changed
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
    }
}
// TODO: Complete implementation as needed
