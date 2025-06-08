package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
// import com.example.theone.features.sequencer.SequencerViewModel // Removed import
import com.example.theone.features.sequencer.SequencerEventBus // Added import
import com.example.theone.features.sequencer.PadTriggerEvent // Added import
import com.example.theone.model.SampleMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope // Added import
import kotlinx.coroutines.launch // Added import
import java.util.UUID // Added import

@HiltViewModel
class DrumTrackViewModel @Inject constructor(
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager,
    private val sequencerEventBus: SequencerEventBus // Injected Event Bus
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
        // MODIFIED: Update first layer or create one. This is a temporary fix.
        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isEmpty()) {
            layers.add(com.example.theone.model.LayerModels.SampleLayer(id = "layer_0", sampleId = sample.id, sampleNameCache = sample.name))
        } else {
            layers[0] = layers[0].copy(sampleId = sample.id, sampleNameCache = sample.name)
        }
        currentPads[padId] = existingPadSetting.copy(layers = layers)
        _padSettingsMap.value = currentPads
    }

    fun clearSampleFromPad(padId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId) // Ensure PadSettings has an ID
        // MODIFIED: Clear sample from first layer. This is a temporary fix.
        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isNotEmpty()) {
            layers[0] = layers[0].copy(sampleId = "", sampleNameCache = "Empty") // Or some placeholder for empty
        }
        currentPads[padId] = existingPadSetting.copy(layers = layers)
        _padSettingsMap.value = currentPads
    }

    fun onPadTriggered(padId: String) {
        val padSetting = _padSettingsMap.value[padId]
        if (padSetting != null) {
            val firstLayer = padSetting.layers.firstOrNull()
            val sampleIdToPlay = firstLayer?.sampleId

            if (sampleIdToPlay != null) {
                viewModelScope.launch {
                    audioEngine.playPadSample(
                        noteInstanceId = UUID.randomUUID().toString(),
                        trackId = "drumTrack_TODO", // Placeholder
                        padId = padSetting.id,
                        sampleId = sampleIdToPlay,
                        sliceId = null, // Placeholder, needs mapping from firstLayer.activeSliceInfo if relevant
                        velocity = 1.0f, // Default velocity for direct pad press from UI
                        playbackMode = padSetting.playbackMode,
                        coarseTune = padSetting.tuningCoarse,
                        fineTune = padSetting.tuningFine,
                        pan = padSetting.pan,
                        volume = padSetting.volume,
                        ampEnv = padSetting.ampEnvelope,
                        filterEnv = padSetting.filterEnvelope,
                        pitchEnv = padSetting.pitchEnvelope,
                        lfos = padSetting.lfos // Directly pass List<LFOSettings>
                    )
                }
                // Also call the sequencer recording logic via event bus
                viewModelScope.launch { // Launch a coroutine to emit the event
                    sequencerEventBus.emitPadTriggerEvent(PadTriggerEvent(padId = padId, velocity = 127))
                }
            } else {
                println("DrumTrackViewModel: Pad $padId triggered, but no sample assigned to its first layer.")
            }
        } else {
            println("DrumTrackViewModel: Pad $padId triggered, but no settings found.")
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
