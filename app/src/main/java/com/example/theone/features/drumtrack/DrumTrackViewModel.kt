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
    val audioEngine: com.example.theone.audio.AudioEngineControl, // Changed to interface
    val projectManager: ProjectManager,
    private val sequencerEventBus: SequencerEventBus
) : ViewModel() {

    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap())
    val padSettingsMap: StateFlow<Map<String, PadSettings>> = _padSettingsMap.asStateFlow()

    // --- Pad Settings Persistence ---
    private fun persistPadSettings(padId: String, settings: PadSettings) {
        viewModelScope.launch {
            try {
                // Assuming projectManager.savePadSettings exists and is suspending or handled appropriately
                val result = projectManager.savePadSettings(padId, settings)
                if (result.isSuccess) {
                    android.util.Log.d("DrumTrackViewModel", "Pad settings persistence successful for $padId.")
                } else {
                    android.util.Log.e("DrumTrackViewModel", "Pad settings persistence failed for $padId: ${result.exceptionOrNull()?.message}")
                    // Handle error (e.g., update a StateFlow to show a message to the user)
                }
            } catch (e: Exception) {
                android.util.Log.e("DrumTrackViewModel", "Error saving pad settings for $padId", e)
                // Handle error
            }
        }
    }

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
        // MODIFIED: Update first layer or create one. This is a simplified assignment logic.
        // A more complete implementation might involve selecting a target layer if multiple exist.
        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isEmpty()) {
            layers.add(com.example.theone.model.LayerModels.SampleLayer(id = "layer_0", sampleId = sample.id, sampleNameCache = sample.name))
        } else {
            // For simplicity, assign to the first layer.
            // Consider resetting other layer params if a new sample implies a "fresh start" for the layer.
            layers[0] = layers[0].copy(
                sampleId = sample.id,
                sampleNameCache = sample.name,
                // Resetting other relevant parameters for the new sample
                startPoint = 0f,
                endPoint = 1f,
                loopPoint = 0f,
                loopEnabled = false,
                reverse = false,
                tuningSemi = 0,
                tuningFine = 0
                // volume and pan might be kept or reset based on desired behavior
            )
        }
        currentPads[padId] = existingPadSetting.copy(layers = layers)
        _padSettingsMap.value = currentPads
    }

    fun clearSampleFromPad(padId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId)
        // MODIFIED: Clear sample and related parameters from the first layer.
        // This is a simplified approach. A full implementation might allow clearing specific layers.
        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isNotEmpty()) {
            layers[0] = layers[0].copy(
                sampleId = "", // Or null if your model.SampleLayer.sampleId is nullable
                sampleNameCache = "Empty",
                // Reset other parameters to a default state when sample is cleared
                startPoint = 0f,
                endPoint = 1f,
                loopPoint = 0f,
                loopEnabled = false,
                reverse = false,
                tuningSemi = 0,
                tuningFine = 0,
                volume = 1.0f, // Reset to default volume
                pan = 0.0f     // Reset to center pan
            )
        }
        currentPads[padId] = existingPadSetting.copy(layers = layers)
        _padSettingsMap.value = currentPads
    }

    fun onPadTriggered(padId: String, triggerVelocity: Float = 1.0f) { // Added triggerVelocity parameter
        val padSetting = _padSettingsMap.value[padId]
        if (padSetting != null) {
            // C++ side handles layer selection. Provide a fallback sample ID from the first layer if it exists.
            val fallbackSampleId = padSetting.layers.firstOrNull()?.sampleId ?: ""

            // Ensure velocity is within expected normalized range for the audio engine call
            val normalizedVelocity = triggerVelocity.coerceIn(0.0f, 1.0f)

            viewModelScope.launch {
                audioEngine.playPadSample(
                    noteInstanceId = UUID.randomUUID().toString(),
                    trackId = "drumTrack_TODO", // Placeholder - needs to be the actual track ID this pad belongs to
                    padId = padSetting.id,
                    sampleId = fallbackSampleId, // Fallback for C++ if PadSettingsCpp lookup fails or has no layers
                    sliceId = null, // Slicing not yet part of this layer logic
                    velocity = normalizedVelocity, // Pass the normalized trigger velocity
                    playbackMode = padSetting.playbackMode,
                    coarseTune = padSetting.tuningCoarse, // Global pad coarse tune
                    fineTune = padSetting.tuningFine,     // Global pad fine tune
                    pan = padSetting.pan,                 // Global pad pan
                    volume = padSetting.volume,           // Global pad volume
                    ampEnv = padSetting.ampEnvelope,
                    filterEnv = padSetting.filterEnvelope,
                    pitchEnv = padSetting.pitchEnvelope,
                    lfos = padSetting.lfos
                )
            }

            // For sequencer events, convert normalized velocity back to MIDI 0-127 range
            val midiVelocity = (normalizedVelocity * 127).toInt().coerceIn(0, 127)
            viewModelScope.launch {
                sequencerEventBus.emitPadTriggerEvent(PadTriggerEvent(padId = padId, velocity = midiVelocity))
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
        val settingsToPersist = newSettings.copy(id = padId) // Ensure ID consistency
        currentPads[padId] = settingsToPersist
        _padSettingsMap.value = currentPads.toMap() // Ensure immutable map is set back
        persistPadSettings(padId, settingsToPersist)
    }

    fun updatePadVolume(padId: String, volume: Float) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingSetting = currentPads[padId]
        if (existingSetting != null) {
            val newSettings = existingSetting.copy(volume = volume)
            currentPads[padId] = newSettings
            _padSettingsMap.value = currentPads.toMap()
            persistPadSettings(padId, newSettings) // Persist changes

            // Call new audio engine methods for real-time effect
            viewModelScope.launch {
                audioEngine.setPadVolume("drumTrack_TODO", padId, volume) // "drumTrack_TODO" needs to be replaced with actual track ID if available
            }
        }
    }

    fun updatePadPan(padId: String, pan: Float) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingSetting = currentPads[padId]
        if (existingSetting != null) {
            val newSettings = existingSetting.copy(pan = pan)
            currentPads[padId] = newSettings
            _padSettingsMap.value = currentPads.toMap()
            persistPadSettings(padId, newSettings) // Persist changes

            // Call new audio engine methods for real-time effect
            viewModelScope.launch {
                audioEngine.setPadPan("drumTrack_TODO", padId, pan) // "drumTrack_TODO" needs to be replaced with actual track ID if available
            }
        }
    }
}
