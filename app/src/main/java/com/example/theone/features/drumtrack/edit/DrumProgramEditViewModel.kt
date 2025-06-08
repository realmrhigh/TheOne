package com.example.theone.features.drumtrack.edit

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Consolidated Model Imports
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.LayerModels.SampleLayer
import com.example.theone.model.SampleMetadata // Consolidated SampleMetadata
import com.example.theone.model.SynthModels.EnvelopeSettings as ModelEnvelopeSettings // Alias to avoid name clash if needed locally
import com.example.theone.model.SynthModels.LFOSettings as ModelLFOSettings // Alias
import com.example.theone.model.SynthModels.LfoWaveform // Consolidated Enum
import com.example.theone.model.SynthModels.LfoDestination // Consolidated Enum
// ModelEnvelopeTypeInternal is used by model.EnvelopeSettings, not directly by ViewModel logic here for type selection.

// Enums specific to this ViewModel's editing logic
enum class EditorTab { SAMPLES, ENVELOPES, LFO, MODULATION, EFFECTS }
enum class LayerParameter { SAMPLE_ID, TUNING_SEMI, TUNING_FINE, VOLUME, PAN, START_POINT, END_POINT, LOOP_POINT, LOOP_ENABLED, REVERSE }
// This EnvelopeType is for selecting which envelope to edit (Amp, Pitch, Filter)
enum class EnvelopeType { AMP, PITCH, FILTER }

// TODO: Ensure SampleMetadata is also consolidated, current ProjectManager interface uses local def.
// For now, local SampleMetadata, ProjectManager, AudioEngine, and DummyProjectManagerImpl remain
// to minimize the scope of this change. Subsequent steps will update their signatures.

// Local interfaces and dummy implementation - these will need to be updated
// to use consolidated models in their method signatures in a later step.
// For now, they use the local SampleMetadata for getAvailableSamples etc.
// which will cause issues after SampleMetadata is removed.
// This highlights that model consolidation needs to be holistic.

// For this specific step, we are focusing on removing the *data classes* PadSettings, SampleLayer, etc.
// and the LFO enums. The SampleMetadata, interfaces and dummy impl will be addressed next.

// LocalSampleMetadata has been removed. Using com.example.theone.model.SampleMetadata now.

interface AudioEngine {
    // TODO: This is a placeholder interface. A real AudioEngine would have more complex methods
    //  to handle sample loading/streaming, voice management, effects processing, MIDI events, etc.
    fun triggerPad(padSettings: PadSettings)
}

interface ProjectManager {
    // TODO: This is a placeholder interface. A real ProjectManager would handle loading/saving
    //  projects, managing sample pools, and potentially interacting with a file system or database.
    fun getSampleMetadataById(sampleId: String): SampleMetadata?
    fun getAvailableSamples(): List<SampleMetadata>
    // Potentially: fun savePadSettings(padSettings: PadSettings)
    // Potentially: fun loadProject(), fun saveProject()
}

// TODO: This is a DUMMY implementation for ProjectManager.
//  Replace with actual logic that loads and manages samples from the device or a defined sample pool.
class DummyProjectManagerImpl : ProjectManager {
    // TODO: The available samples list should be dynamic, loaded from storage or a user-defined library.
    private val samples = listOf(
        SampleMetadata(id="kick_01", name="Kick Drum 1", uri="/samples/kick_01.wav", duration=500L),
        SampleMetadata(id="snare_02", name="Snare Drum 2", uri="/samples/snare_02.wav", duration=300L),
        SampleMetadata(id="hat_03", name="Hi-Hat 3", uri="/samples/hat_03.wav", duration=150L),
        SampleMetadata(id="long_cymbal", name="Long Cymbal", uri="/samples/long_cymbal.wav", duration=2500L)
    )
    override fun getSampleMetadataById(sampleId: String): SampleMetadata? = samples.find { it.id == sampleId }
    override fun getAvailableSamples(): List<SampleMetadata> = samples
}

class DrumProgramEditViewModel(
    private val audioEngine: AudioEngine,
    val projectManager: ProjectManager,
    initialPadSettings: PadSettings
) {

    private val _padSettings = MutableStateFlow(initialPadSettings)
    val padSettings: StateFlow<PadSettings> = _padSettings.asStateFlow()

    private val _selectedLayerIndex = MutableStateFlow(0)
    val selectedLayerIndex: StateFlow<Int> = _selectedLayerIndex.asStateFlow()

    private val _currentEditorTab = MutableStateFlow(EditorTab.SAMPLES)
    val currentEditorTab: StateFlow<EditorTab> = _currentEditorTab.asStateFlow()

    init {
        _selectedLayerIndex.value = if (initialPadSettings.sampleLayers.isNotEmpty()) 0 else -1
    }

    // TODO: Consider if projectManager.getAvailableSamples() should be called here or managed by a higher-level ViewModel.
    //  If called here, it might be on init or when a sample selection UI is about to be shown.

    fun selectEditorTab(tab: EditorTab) { _currentEditorTab.value = tab }

    fun selectLayer(layerIndex: Int) {
        if (layerIndex >= 0 && layerIndex < _padSettings.value.sampleLayers.size) {
            _selectedLayerIndex.value = layerIndex
        }
    }

    fun addSampleLayer(sample: SampleMetadata) {
        val newLayer = SampleLayer( // Uses imported com.example.theone.model.LayerModels.SampleLayer
            id = "layer_${System.nanoTime()}",
            sampleId = sample.id,
            sampleNameCache = sample.name
        )
        _padSettings.update { currentSettings ->
            currentSettings.copy(layers = (currentSettings.layers.toMutableList() + newLayer).toMutableList() )
        }
        _selectedLayerIndex.value = _padSettings.value.sampleLayers.size - 1
    }

    fun removeLayer(layerIndex: Int) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.sampleLayers.size) return
        _padSettings.update { currentSettings ->
            currentSettings.copy(sampleLayers = currentSettings.sampleLayers.filterIndexed { index, _ -> index != layerIndex })
        }
        // Adjust selectedLayerIndex
        val currentSelection = _selectedLayerIndex.value
        val newSize = _padSettings.value.sampleLayers.size
        if (newSize == 0) _selectedLayerIndex.value = -1
        else if (layerIndex < currentSelection) _selectedLayerIndex.value = currentSelection - 1
        else if (layerIndex == currentSelection) {
            _selectedLayerIndex.value = maxOf(0, currentSelection - 1)
            if (_selectedLayerIndex.value >= newSize && newSize > 0) _selectedLayerIndex.value = newSize - 1
        }
    }

    fun updateLayerParameter(layerIndex: Int, parameter: LayerParameter, value: Any) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.layers.size) return // Use .layers
        _padSettings.update { currentSettings ->
            val updatedLayers = currentSettings.layers.toMutableList()
            val layerToUpdate = updatedLayers[layerIndex]

            val updatedLayer = when (parameter) {
                LayerParameter.SAMPLE_ID -> if (value is String) {
                    val sampleInfo = projectManager.getSampleMetadataById(value) // returns consolidated SampleMetadata
                    layerToUpdate.copy(sampleId = value, sampleNameCache = sampleInfo?.name ?: "N/A")
                } else layerToUpdate
                LayerParameter.TUNING_SEMI -> if (value is Int) layerToUpdate.copy(tuningSemi = value) else layerToUpdate
                LayerParameter.TUNING_FINE -> if (value is Int) layerToUpdate.copy(tuningFine = value) else layerToUpdate
                LayerParameter.VOLUME -> if (value is Float) layerToUpdate.copy(volume = value) else layerToUpdate
                LayerParameter.PAN -> if (value is Float) layerToUpdate.copy(pan = value) else layerToUpdate
                LayerParameter.START_POINT -> if (value is Float) layerToUpdate.copy(startPoint = value) else layerToUpdate
                LayerParameter.END_POINT -> if (value is Float) layerToUpdate.copy(endPoint = value) else layerToUpdate
                LayerParameter.LOOP_POINT -> if (value is Float) layerToUpdate.copy(loopPoint = value) else layerToUpdate
                LayerParameter.LOOP_ENABLED -> if (value is Boolean) layerToUpdate.copy(loopEnabled = value) else layerToUpdate
                LayerParameter.REVERSE -> if (value is Boolean) layerToUpdate.copy(reverse = value) else layerToUpdate
            }
            updatedLayers[layerIndex] = updatedLayer
            currentSettings.copy(layers = updatedLayers) // Ensure this creates a new list if layers was immutable
        }
    }

    // Method to update global PadSettings parameters like overall volume or pan
    fun updateGlobalPadParameter(updateAction: (PadSettings) -> PadSettings) {
        _padSettings.update(updateAction)
    }


    fun updateEnvelope(envelopeTypeLocal: EnvelopeType, newSettingsFromUI: ModelEnvelopeSettings) {
        // newSettingsFromUI is a ModelEnvelopeSettings where fields like attackMs might hold values in seconds from UI.
        // We need to convert these to milliseconds before updating the actual state.
        val currentPadState = _padSettings.value
        val envelopeToUpdate = when (envelopeTypeLocal) {
            EnvelopeType.AMP -> currentPadState.ampEnvelope
            EnvelopeType.PITCH -> currentPadState.pitchEnvelope
            EnvelopeType.FILTER -> currentPadState.filterEnvelope
        }

        // Assume newSettingsFromUI was created by UI like: currentEnvelope.copy(attackMs = sliderValueInSeconds)
        // So, newSettingsFromUI.attackMs actually contains seconds.
        val finalSettings = envelopeToUpdate.copy(
            attackMs = newSettingsFromUI.attackMs * 1000f, // Convert seconds to ms
            decayMs = newSettingsFromUI.decayMs * 1000f,   // Convert seconds to ms
            sustainLevel = newSettingsFromUI.sustainLevel, // This is 0-1f, no conversion
            releaseMs = newSettingsFromUI.releaseMs * 1000f, // Convert seconds to ms
            // Copy other fields like type, holdMs, velocityToAttack, velocityToLevel if they could be changed by UI
            // For now, assume UI only changes ADSR values passed as if they were ms.
            type = newSettingsFromUI.type, // Preserve type
            holdMs = newSettingsFromUI.holdMs,
            velocityToAttack = newSettingsFromUI.velocityToAttack,
            velocityToLevel = newSettingsFromUI.velocityToLevel
        )

        _padSettings.update { currentSettings ->
            when (envelopeTypeLocal) {
                EnvelopeType.AMP -> currentSettings.copy(ampEnvelope = finalSettings)
                EnvelopeType.PITCH -> currentSettings.copy(pitchEnvelope = finalSettings)
                EnvelopeType.FILTER -> currentSettings.copy(filterEnvelope = finalSettings)
            }
        }
    }

    fun updateLfo(lfoIndex: Int, newSettings: ModelLFOSettings) {
        if (lfoIndex < 0 || lfoIndex >= _padSettings.value.lfos.size) return
        _padSettings.update { currentSettings ->
            val updatedLfos = currentSettings.lfos.toMutableList().apply { this[lfoIndex] = newSettings }
            currentSettings.copy(lfos = updatedLfos)
        }
    }

    fun auditionPad() {
        // TODO: This function should interface with the AudioEngine to:
        //  1. Compile the current _padSettings.value (all layers, envelopes, LFOs, global params, and potentially effects)
        //     into a playable sound or program.
        //  2. Trigger this compiled sound.
        //  Complexities:
        //  - Real-time parameter updates: If parameters are changed rapidly while auditioning,
        //    how does the AudioEngine handle these? (e.g., take current snapshot, or allow hot-swapping params).
        //  - Voice management: Ensure multiple triggers don't overload the audio engine or cut off previous sounds incorrectly.
        //  - Error handling: What if a sample path is invalid or an LFO target is misconfigured?
        audioEngine.triggerPad(_padSettings.value)
    }

    fun saveChanges(): PadSettings { // Return consolidated
        Log.d("DrumProgramEditVM", "Changes saved for pad: ${_padSettings.value.id}")
        // Here, you might also call projectManager.savePadSettings(_padSettings.value) if that's a requirement
        return _padSettings.value // Return the current state
    }
}
