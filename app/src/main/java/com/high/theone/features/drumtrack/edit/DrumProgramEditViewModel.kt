package com.example.theone.features.drumtrack.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.domain.ProjectManager
import com.high.theone.domain.model.DrumPad
import com.high.theone.domain.model.DrumProgram
import com.high.theone.domain.model.PadSettings
import com.high.theone.domain.model.SampleMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrumProgramEditViewModel @Inject constructor(
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrumProgramEditUiState())
    val uiState: StateFlow<DrumProgramEditUiState> = _uiState.asStateFlow()

    // Loads a DrumProgram for editing
    fun loadDrumProgram(programId: String) {
        viewModelScope.launch {
            // TODO: Replace with real loading logic
            val drumProgram = DrumProgram(
                id = programId,
                name = "Default Program",
                pads = List(16) { index ->
                    DrumPad(
                        id = "pad$index",
                        name = "Pad $index",
                        sample = null,
                        settings = PadSettings()
                    )
                }
            )
            _uiState.value = _uiState.value.copy(
                drumProgram = drumProgram,
                selectedPadId = drumProgram.pads.firstOrNull()?.id
            )
        }
    }

    fun selectPad(padId: String) {
        _uiState.value = _uiState.value.copy(selectedPadId = padId)
    }

    fun onPadVolumeChange(padId: String, volume: Float) {
        updatePadSettings(padId) { it.copy(volume = volume) }
    }

    fun onPadPanChange(padId: String, pan: Float) {
        updatePadSettings(padId) { it.copy(pan = pan) }
    }

    fun onPadSampleChange(padId: String, sample: SampleMetadata) {
        val program = _uiState.value.drumProgram ?: return
        val updatedPads = program.pads.map { pad ->
            if (pad.id == padId) pad.copy(sample = sample) else pad
        }
        _uiState.value = _uiState.value.copy(
            drumProgram = program.copy(pads = updatedPads)
        )
    }

    private fun updatePadSettings(padId: String, update: (PadSettings) -> PadSettings) {
        val program = _uiState.value.drumProgram ?: return
        val updatedPads = program.pads.map { pad ->
            if (pad.id == padId) pad.copy(settings = update(pad.settings)) else pad
        }
        _uiState.value = _uiState.value.copy(
            drumProgram = program.copy(pads = updatedPads)
        )
    }

    fun saveDrumProgram() {
        viewModelScope.launch {
            // TODO: Implement saving logic using projectManager
        }
    }

    // Add more event handlers as needed for envelopes, LFOs, etc.
}

// UI State data class
data class DrumProgramEditUiState(
    val drumProgram: DrumProgram? = null,
    val selectedPadId: String? = null,
    // Add more UI state fields as needed
)