package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.SampleMetadata

class SampleEditViewModelFactory(
    private val initialSampleMetadata: SampleMetadata,
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SampleEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SampleEditViewModel(initialSampleMetadata, audioEngine, projectManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}