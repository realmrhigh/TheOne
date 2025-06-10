package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SampleEditViewModel(
    private val initialSampleMetadata: SampleMetadata,
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _editableSampleMetadata = MutableStateFlow(initialSampleMetadata)
    val editableSampleMetadata: StateFlow<SampleMetadata> = _editableSampleMetadata.asStateFlow()

    private val _trimStartMs = MutableStateFlow(initialSampleMetadata.trimStartMs)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(initialSampleMetadata.trimEndMs)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    init {
        val correctedTrimEndMs = if (initialSampleMetadata.trimEndMs == 0L && initialSampleMetadata.duration > 0L) {
            initialSampleMetadata.duration
        } else {
            initialSampleMetadata.trimEndMs
        }

        if (initialSampleMetadata.trimEndMs != correctedTrimEndMs) {
            _editableSampleMetadata.value = initialSampleMetadata.copy(trimEndMs = correctedTrimEndMs)
        } else {
            _editableSampleMetadata.value = initialSampleMetadata
        }

        _trimStartMs.value = _editableSampleMetadata.value.trimStartMs
        _trimEndMs.value = _editableSampleMetadata.value.trimEndMs
    }

    fun updateTrimPoints(startMs: Long, endMs: Long) {
        val currentSample = _editableSampleMetadata.value
        val validStartMs = startMs.coerceIn(0, currentSample.duration)
        val validEndMs = endMs.coerceIn(validStartMs, currentSample.duration)

        _trimStartMs.value = validStartMs
        _trimEndMs.value = validEndMs
        _editableSampleMetadata.value = currentSample.copy(trimStartMs = validStartMs, trimEndMs = validEndMs)
    }

    fun auditionSlice() {
        viewModelScope.launch {
            val currentSample = _editableSampleMetadata.value
            audioEngine.playSampleSlice(
                sampleId = currentSample.id,
                noteInstanceId = UUID.randomUUID().toString(),
                volume = 1.0f,
                pan = 0.0f,
                trimStartMs = _trimStartMs.value,
                trimEndMs = _trimEndMs.value,
                loopStartMs = null,
                loopEndMs = null,
                isLooping = false
            )
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            projectManager.updateSampleMetadata(_editableSampleMetadata.value)
        }
    }
}