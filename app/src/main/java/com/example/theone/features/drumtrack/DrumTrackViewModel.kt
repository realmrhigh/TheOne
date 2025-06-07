package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.SampleMetadata
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

    private val _padSettingsMap = MutableStateFlow<Map<Int, PadSettings>>(emptyMap())
    val padSettingsMap: StateFlow<Map<Int, PadSettings>> = _padSettingsMap.asStateFlow()

    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    // Define number of pads, e.g., for a 4x4 grid
    private val NUM_PADS = 16

    init {
        val initialPads = (0 until NUM_PADS).associateWith { PadSettings() }
        _padSettingsMap.value = initialPads
        loadSamplesForAssignment() // Load samples on init
    }

    // --- Sample Loading ---
    fun loadSamplesForAssignment() {
        // This would typically be called when the user wants to assign a sample
        // Potentially using viewModelScope.launch if projectManager.getSamplesFromPool() is suspending
        // For now, assuming projectManager.getSamplesFromPool() is a synchronous call or handled by ProjectManager internally
        _availableSamples.value = projectManager.getSamplesFromPool()
    }

    fun assignSampleToPad(padId: Int, sample: SampleMetadata) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings()
        // Assuming sample.uri can serve as a unique identifier for the sample, as per plan.
        // If SampleMetadata has a persistent `id` from ProjectManager, that should be preferred.
        currentPads[padId] = existingPadSetting.copy(sampleId = sample.uri, sampleName = sample.name)
        _padSettingsMap.value = currentPads
    }

    fun clearSampleFromPad(padId: Int) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings()
        currentPads[padId] = existingPadSetting.copy(sampleId = null, sampleName = null)
        _padSettingsMap.value = currentPads
    }

    fun onPadTriggered(padId: Int) {
        val padSetting = _padSettingsMap.value[padId]
        if (padSetting != null && padSetting.sampleId != null) {
            audioEngine.playPadSample(padSetting)
        } else {
            // Consider logging instead of println for production apps
            println("DrumTrackViewModel: Pad $padId triggered, but no sample assigned.")
        }
    }

    fun updatePadSetting(padId: Int, newSettings: PadSettings) { // For volume, pan, tuning
        val currentPads = _padSettingsMap.value.toMutableMap()
        // Ensure the sampleId and sampleName are preserved if newSettings doesn't include them
        // or if newSettings is only for partial updates like volume/pan/tuning.
        // The current PadSettings data class will copy all fields.
        // If newSettings comes from a UI that only modified e.g. volume, it should be based on the existing settings.
        val existingSetting = currentPads[padId]
        if (existingSetting != null) {
             // This ensures that if newSettings is a partial update (e.g. only volume changed in UI),
             // we don't lose sampleId or sampleName.
             // However, the most direct interpretation of the method signature is a full replacement.
             // For now, direct replacement:
            currentPads[padId] = newSettings
        } else {
            // If no existing settings for a padId (should not happen with init block),
            // then just place the newSettings.
            currentPads[padId] = newSettings
        }
        _padSettingsMap.value = currentPads
    }
}
