package com.example.theone.features.drumprogrameditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings

class DrumProgramEditViewModelFactory(
    private val padSettingsId: String, // ID of the pad being edited
    private val initialPadSettings: PadSettings?, // Can be null if creating new
    private val projectManager: ProjectManager,
    private val audioEngine: AudioEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DrumProgramEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DrumProgramEditViewModel(
                padSettingsIdToEdit = padSettingsId,
                initialPadSettings = initialPadSettings,
                projectManager = projectManager,
                audioEngine = audioEngine
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
