package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.features.sequencer.SequencerViewModel // Added import
import com.example.theone.model.SampleMetadata
import dagger.hilt.android.lifecycle.HiltViewModel // Added import
import javax.inject.Inject // Added import
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// Removed viewModelScope and launch for now, will add back if projectManager.getSamplesFromPool() is suspend
// import androidx.lifecycle.viewModelScope
// import kotlinx.coroutines.launch

@HiltViewModel // Added annotation
class DrumTrackViewModel @Inject constructor( // Added @Inject
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager,
    private val sequencerViewModel: SequencerViewModel // Added parameter
) : ViewModel() {

    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap())
    val padSettingsMap: StateFlow<Map<String, PadSettings>> = _padSettingsMap.asStateFlow()

    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    // Define number of pads, e.g., for a 4x4 grid
    private val NUM_PADS = 16

    init {
        val initialPads = (0 until NUM_PADS).associate { index ->
            val padId = "Pad$index" // Generate IDs like "Pad0", "Pad1", ... "Pad15"
            padId to PadSettings(id = padId)
        }
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

    fun assignSampleToPad(padId: String, sample: SampleMetadata) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId) // Ensure PadSettings has an ID
        // Assuming sample.uri can serve as a unique identifier for the sample, as per plan.
        // If SampleMetadata has a persistent `id` from ProjectManager, that should be preferred.
        currentPads[padId] = existingPadSetting.copy(sampleId = sample.uri, sampleName = sample.name)
        _padSettingsMap.value = currentPads
    }

    fun clearSampleFromPad(padId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId) // Ensure PadSettings has an ID
        currentPads[padId] = existingPadSetting.copy(sampleId = null, sampleName = null)
        _padSettingsMap.value = currentPads
    }

    fun onPadTriggered(padId: String) {
        val padSetting = _padSettingsMap.value[padId]
        if (padSetting != null && padSetting.sampleId != null) {
            // Ensure the padSetting passed to audioEngine has the correct ID,
            // though it should already if map consistency is maintained.
            audioEngine.playPadSample(padSetting.copy(id = padId))
            // Add this line
            sequencerViewModel.recordPadTrigger(padId = padId, velocity = 127) // Assuming default velocity
        } else {
            // Consider logging instead of println for production apps
            println("DrumTrackViewModel: Pad $padId triggered, but no sample assigned.")
        }
    }

    fun updatePadSetting(padId: String, newSettings: PadSettings) { // For volume, pan, tuning
        val currentPads = _padSettingsMap.value.toMutableMap()
        // Ensure the newSettings has the correct id matching the padId key.
        // If newSettings comes from a generic editor, its id might be default or different.
        // Best practice: newSettings should already have its 'id' field correctly set to padId.
        // For safety, we can use .copy(id = padId) if newSettings might have a mismatched ID.
        currentPads[padId] = newSettings.copy(id = padId) // Ensure ID consistency
        // Ensure the sampleId and sampleName are preserved if newSettings doesn't include them
        // or if newSettings is only for partial updates like volume/pan/tuning.
        // The current PadSettings data class will copy all fields.
        // If newSettings comes from a UI that only modified e.g. volume, it should be based on the existing settings.
        // The original code regarding preserving sampleId/Name if newSettings is partial:
        // This logic might need re-evaluation. If newSettings is meant to be a full replacement,
        // then just putting it is fine. If it's partial, the ViewModel needs a more complex update strategy.
        // For now, assuming newSettings is intended to be a complete setting for the pad,
        // but we ensure its 'id' field is correct.
        _padSettingsMap.value = currentPads
    }
}
