package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.theone.audio.AudioEngineControl // C1
import com.example.theone.domain.ProjectManager     // C3
import com.example.theone.model.SampleMetadata

class SampleEditViewModelFactory(
    private val audioEngine: AudioEngineControl,
    private val projectManager: ProjectManager,
    private val sampleMetadata: SampleMetadata
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SampleEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SampleEditViewModel(audioEngine, projectManager, sampleMetadata) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
