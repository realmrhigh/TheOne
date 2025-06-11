package com.example.theone.features.drumtrack.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

// This is the custom ViewModelProvider.Factory
class DrumProgramEditViewModelFactory @AssistedInject constructor(
    private val audioEngine: AudioEngineControl,
    private val projectManager: ProjectManager,
    @Assisted("padSettings") private val padSettings: PadSettings
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrumProgramEditViewModel::class.java)) {
            return DrumProgramEditViewModel(audioEngine, projectManager, padSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// This is the Hilt factory that creates the above ViewModelProvider.Factory
@AssistedFactory
interface DrumProgramEditViewModelAssistedFactory {
    fun create(@Assisted("padSettings") padSettings: PadSettings): DrumProgramEditViewModelFactory
}
