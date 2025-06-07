package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.SampleMetadata
import com.example.theone.model.SampleLayer // New Import
import com.example.theone.model.LayerTriggerRule // New Import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// Removed viewModelScope and launch for now, will add back if projectManager.getSamplesFromPool() is suspend
// import androidx.lifecycle.viewModelScope
// import kotlinx.coroutines.launch

class DrumTrackViewModel(
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap()) // Key type changed to String
    val padSettingsMap: StateFlow<Map<String, PadSettings>> = _padSettingsMap.asStateFlow()

    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    // private val NUM_PADS = 16 // May still be relevant for UI, but not for map keys

    init {
        loadPadSettingsFromProject()
    }

    fun loadPadSettingsFromProject() {
        // In a real scenario, this would fetch PadSettings from ProjectManager
        // For now, let's create a few default pads for testing if the map is empty.
        if (_padSettingsMap.value.isEmpty()) {
            val defaultPads = (0 until 4).associate { index ->
                val defaultPadSetting = PadSettings() // Generates its own UUID padSettingsId
                // Optionally, add a default layer to each pad for testing
                // defaultPadSetting.addLayer("initial_sample_uri_for_pad_${index}")
                defaultPadSetting.padSettingsId to defaultPadSetting
            }
            _padSettingsMap.value = defaultPads
        }
        // Also, refresh available samples for assignment
        loadSamplesForAssignment()
    }

    // --- Sample Loading (for layer assignment choices) ---
    fun loadSamplesForAssignment() {
        _availableSamples.value = projectManager.getSamplesFromPool()
    }

    // Layer Management Methods
    fun addLayerToPad(padSettingsId: String, sampleId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val padSetting = currentPads[padSettingsId]
        if (padSetting != null) {
            padSetting.addLayer(sampleId) // Uses the helper in PadSettings
            _padSettingsMap.value = currentPads
        } else {
            println("DrumTrackViewModel: PadSettings with ID $padSettingsId not found to add layer.")
        }
    }

    fun removeLayerFromPad(padSettingsId: String, layerId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val padSetting = currentPads[padSettingsId]
        if (padSetting != null) {
            padSetting.removeLayerById(layerId) // Uses the helper in PadSettings
            _padSettingsMap.value = currentPads
        } else {
            println("DrumTrackViewModel: PadSettings with ID $padSettingsId not found to remove layer.")
        }
    }

    fun updateLayerInPad(padSettingsId: String, updatedLayer: SampleLayer) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val padSetting = currentPads[padSettingsId]
        if (padSetting != null) {
            val layerIndex = padSetting.layers.indexOfFirst { it.id == updatedLayer.id }
            if (layerIndex != -1) {
                padSetting.layers[layerIndex] = updatedLayer
                _padSettingsMap.value = currentPads
            } else {
                println("DrumTrackViewModel: Layer with ID ${updatedLayer.id} not found in PadSettings $padSettingsId.")
            }
        } else {
            println("DrumTrackViewModel: PadSettings with ID $padSettingsId not found to update layer.")
        }
    }

    fun clearAllLayersFromPad(padSettingsId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val padSetting = currentPads[padSettingsId]
        if (padSetting != null) {
            padSetting.layers.clear()
            _padSettingsMap.value = currentPads
        } else {
            println("DrumTrackViewModel: PadSettings with ID $padSettingsId not found to clear layers.")
        }
    }

    fun onPadTriggered(padSettingsId: String, velocity: Int) {
        val padSetting = _padSettingsMap.value[padSettingsId]
        if (padSetting != null && padSetting.layers.isNotEmpty()) {
            // Pass the full PadSettings and velocity to AudioEngine
            // The AudioEngine's JNI call will handle layer selection logic (VELOCITY, CYCLE, RANDOM)
            audioEngine.triggerPad(padSettingsId, padSetting, velocity) // Assuming audioEngine.triggerPad is the new method
        } else {
            println("DrumTrackViewModel: Pad $padSettingsId triggered, but no layers assigned or pad not found.")
        }
    }

    fun updatePadSetting(padSettingsId: String, newSettings: PadSettings) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        // Ensure the padSettingsId in newSettings matches the key, or update key if padSettingsId can change.
        // For now, assume padSettingsId in the object is canonical and matches the key.
        if (currentPads.containsKey(padSettingsId)) {
            currentPads[padSettingsId] = newSettings
             _padSettingsMap.value = currentPads
        } else {
             println("DrumTrackViewModel: PadSettings with ID $padSettingsId not found to update.")
        }
    }
}
