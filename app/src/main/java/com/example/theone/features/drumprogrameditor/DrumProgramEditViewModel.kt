package com.example.theone.features.drumprogrameditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.EnvelopeSettings
import com.example.theone.model.LFOSettings
import com.example.theone.model.LayerTriggerRule
import com.example.theone.model.SampleLayer
import com.example.theone.model.SampleMetadata // For addLayer from SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch // For saving

class DrumProgramEditViewModel(
    val padSettingsIdToEdit: String, // ID of the pad config being edited
    initialPadSettings: PadSettings?, // Initial settings, could be null for a new pad
    private val projectManager: ProjectManager,
    private val audioEngine: AudioEngine
) : ViewModel() {

    private val _currentPadSettings = MutableStateFlow(
        initialPadSettings ?: PadSettings(padSettingsId = padSettingsIdToEdit)
    )
    val currentPadSettings: StateFlow<PadSettings> = _currentPadSettings.asStateFlow()

    // --- Layer Management ---
    fun addLayer(sample: SampleMetadata): SampleLayer {
        val newLayer = SampleLayer(sampleId = sample.uri) // Use URI as sampleId for the layer
        _currentPadSettings.value = _currentPadSettings.value.copy(
            layers = (_currentPadSettings.value.layers + newLayer).toMutableList()
        )
        return newLayer
    }

    fun removeLayer(layerId: String) {
        _currentPadSettings.value = _currentPadSettings.value.copy(
            layers = _currentPadSettings.value.layers.filterNot { it.id == layerId }.toMutableList()
        )
    }

    fun updateLayer(updatedLayer: SampleLayer) {
        _currentPadSettings.value = _currentPadSettings.value.copy(
            layers = _currentPadSettings.value.layers.map {
                if (it.id == updatedLayer.id) updatedLayer else it
            }.toMutableList()
        )
    }

    fun updateLayerTriggerRule(rule: LayerTriggerRule) {
        _currentPadSettings.value = _currentPadSettings.value.copy(layerTriggerRule = rule)
    }

    // --- Envelope Management ---
    fun updateEnvelope(type: String, envelopeSettings: EnvelopeSettings) {
        // type: "amp", "filter", "pitch"
        val current = _currentPadSettings.value
        _currentPadSettings.value = when (type.lowercase()) {
            "amp" -> current.copy(ampEnvelope = envelopeSettings)
            "filter" -> current.copy(filterEnvelope = envelopeSettings)
            "pitch" -> current.copy(pitchEnvelope = envelopeSettings)
            else -> current // No change
        }
    }

    // --- LFO Management ---
    fun addLfo(): LFOSettings {
        val newLfo = LFOSettings() // Creates LFO with default values and new ID
        _currentPadSettings.value = _currentPadSettings.value.copy(
            lfos = (_currentPadSettings.value.lfos + newLfo).toMutableList()
        )
        return newLfo
    }

    fun removeLfo(lfoId: String) {
        _currentPadSettings.value = _currentPadSettings.value.copy(
            lfos = _currentPadSettings.value.lfos.filterNot { it.id == lfoId }.toMutableList()
        )
    }

    fun updateLfo(updatedLfo: LFOSettings) {
        _currentPadSettings.value = _currentPadSettings.value.copy(
            lfos = _currentPadSettings.value.lfos.map {
                if (it.id == updatedLfo.id) updatedLfo else it
            }.toMutableList()
        )
    }

    // --- Base Pad Settings ---
    fun updateBaseVolume(volume: Float) {
        _currentPadSettings.value = _currentPadSettings.value.copy(volume = volume)
    }

    fun updateBasePan(pan: Float) {
        _currentPadSettings.value = _currentPadSettings.value.copy(pan = pan)
    }

    fun updateBaseCoarseTune(coarseTune: Int) {
        _currentPadSettings.value = _currentPadSettings.value.copy(tuningCoarse = coarseTune)
    }

    fun updateBaseFineTune(fineTune: Int) {
        _currentPadSettings.value = _currentPadSettings.value.copy(tuningFine = fineTune)
    }


    // --- Actions ---
    fun saveChanges() {
        viewModelScope.launch {
            projectManager.savePadSettings(_currentPadSettings.value)
            // Optionally, add some user feedback state (e.g., saved successfully)
        }
    }

    fun auditionPad(velocity: Int) {
        // Use padSettingsIdToEdit for consistency if it represents the specific pad slot/ID in the engine
        audioEngine.triggerPad(
            padId = padSettingsIdToEdit, // The ID of the pad slot being configured
            padSettings = _currentPadSettings.value, // The current state of settings
            velocity = velocity
        )
    }
}
