package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.SampleMetadata

class SampleEditViewModelFactory(
    private val initialSampleMetadata: SampleMetadata, // Name and order changed
    private val audioEngine: AudioEngine, // Type and order changed
    private val projectManager: ProjectManager // Order changed
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SampleEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Parameters now match SampleEditViewModel constructor
            return SampleEditViewModel(initialSampleMetadata, audioEngine, projectManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
