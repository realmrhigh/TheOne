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
import com.example.theone.model.SynthModels.EnvelopeSettings as ModelEnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings as ModelLFOSettings
import com.example.theone.model.SynthModels.LfoWaveform // Already imported
import com.example.theone.model.SynthModels.LfoDestination // Already imported
import com.example.theone.model.SynthModels.ModulationRouting
import com.example.theone.model.SynthModels.ModSource // For ModulationRouting's source type
import com.example.theone.model.SynthModels.ModDestination // For ModulationRouting's destination type
import com.example.theone.model.SynthModels.EffectSetting
import com.example.theone.model.SynthModels.EffectType
// Real AudioEngineControl and ProjectManager interfaces
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager

// AndroidX ViewModel and scope for coroutines
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID // For generating noteInstanceId

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

// Local/dummy interface definitions for AudioEngine and ProjectManager, and DummyProjectManagerImpl are removed.
// This ViewModel will now expect the real AudioEngineControl and domain.ProjectManager.

class DrumProgramEditViewModel(
    private val audioEngine: AudioEngineControl, // Use real AudioEngineControl
    val projectManager: com.example.theone.domain.ProjectManager, // Use real ProjectManager
    initialPadSettings: PadSettings
) : ViewModel() { // Inherit from ViewModel for viewModelScope

    private val _padSettings = MutableStateFlow(initialPadSettings)
    val padSettings: StateFlow<PadSettings> = _padSettings.asStateFlow()

    private val _selectedLayerIndex = MutableStateFlow(0)
    val selectedLayerIndex: StateFlow<Int> = _selectedLayerIndex.asStateFlow()

    private val _currentEditorTab = MutableStateFlow(EditorTab.SAMPLES)
    val currentEditorTab: StateFlow<EditorTab> = _currentEditorTab.asStateFlow()

    init {
        _selectedLayerIndex.value = if (initialPadSettings.layers.isNotEmpty()) 0 else -1 // Use .layers
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
        val newLayer = SampleLayer(
            id = "layer_${System.nanoTime()}",
            sampleId = sample.id,
            sampleNameCache = sample.name
        )
        _padSettings.update { currentSettings ->
            currentSettings.copy(layers = (currentSettings.layers.toMutableList() + newLayer).toMutableList() )
        }
        _selectedLayerIndex.value = _padSettings.value.layers.size - 1 // use .layers
    }

    fun removeLayer(layerIndex: Int) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.layers.size) return // use .layers
        _padSettings.update { currentSettings ->
            val updatedLayers = currentSettings.layers.toMutableList().apply { removeAt(layerIndex) }
            currentSettings.copy(layers = updatedLayers)
        }
        // Adjust selectedLayerIndex
        val currentSelection = _selectedLayerIndex.value
        val newSize = _padSettings.value.layers.size // use .layers
        if (newSize == 0) _selectedLayerIndex.value = -1
        else if (layerIndex < currentSelection) _selectedLayerIndex.value = currentSelection - 1
        // If selected was removed or selection is now out of bounds (e.g. last item removed)
        else if (layerIndex == currentSelection || currentSelection >= newSize) {
            _selectedLayerIndex.value = maxOf(0, newSize - 1)
        }
    }

    fun updateLayerParameter(layerIndex: Int, parameter: LayerParameter, value: Any) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.layers.size) return

        if (parameter == LayerParameter.SAMPLE_ID && value is String) {
            val newSampleId = value
            // Immediately update with new ID and temporary name
            _padSettings.update { currentSettings ->
                val updatedLayers = currentSettings.layers.toMutableList()
                if (layerIndex < updatedLayers.size) { // Check bounds again before access
                    val layerToUpdate = updatedLayers[layerIndex]
                    updatedLayers[layerIndex] = layerToUpdate.copy(sampleId = newSampleId, sampleNameCache = "Loading...")
                    currentSettings.copy(layers = updatedLayers)
                } else {
                    currentSettings
                }
            }
            // Launch coroutine to fetch real name from (now suspend) projectManager
            viewModelScope.launch {
                val sampleInfo = projectManager.getSampleMetadataById(newSampleId)
                _padSettings.update { currentSettings ->
                    val updatedLayers = currentSettings.layers.toMutableList()
                    // Check if the layer still exists and if sampleId hasn't changed again by another rapid update
                    if (layerIndex < updatedLayers.size && updatedLayers[layerIndex].sampleId == newSampleId) {
                        val layerToUpdate = updatedLayers[layerIndex]
                        updatedLayers[layerIndex] = layerToUpdate.copy(sampleNameCache = sampleInfo?.name ?: "N/A")
                        currentSettings.copy(layers = updatedLayers)
                    } else {
                        currentSettings
                    }
                }
            }
        } else {
            // Handle other parameters synchronously
            _padSettings.update { currentSettings ->
                val updatedLayers = currentSettings.layers.toMutableList()
                if (layerIndex < updatedLayers.size) { // Check bounds
                    val layerToUpdate = updatedLayers[layerIndex]
                    val updatedLayer = when (parameter) {
                        // SAMPLE_ID handled above
                        LayerParameter.TUNING_SEMI -> if (value is Int) layerToUpdate.copy(tuningSemi = value) else layerToUpdate
                        LayerParameter.TUNING_FINE -> if (value is Int) layerToUpdate.copy(tuningFine = value) else layerToUpdate
                        LayerParameter.VOLUME -> if (value is Float) layerToUpdate.copy(volume = value) else layerToUpdate
                        LayerParameter.PAN -> if (value is Float) layerToUpdate.copy(pan = value) else layerToUpdate
                        LayerParameter.START_POINT -> if (value is Float) layerToUpdate.copy(startPoint = value) else layerToUpdate
                        LayerParameter.END_POINT -> if (value is Float) layerToUpdate.copy(endPoint = value) else layerToUpdate
                        LayerParameter.LOOP_POINT -> if (value is Float) layerToUpdate.copy(loopPoint = value) else layerToUpdate
                        LayerParameter.LOOP_ENABLED -> if (value is Boolean) layerToUpdate.copy(loopEnabled = value) else layerToUpdate
                        LayerParameter.REVERSE -> if (value is Boolean) layerToUpdate.copy(reverse = value) else layerToUpdate
                        else -> layerToUpdate // Should not happen if SAMPLE_ID is handled
                    }
                    updatedLayers[layerIndex] = updatedLayer
                    currentSettings.copy(layers = updatedLayers)
                } else {
                    currentSettings // Index out of bounds
                }
            }
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
        viewModelScope.launch {
            val currentSettings = _padSettings.value
            val selectedLayer = if (selectedLayerIndex.value >= 0 && selectedLayerIndex.value < currentSettings.layers.size) {
                currentSettings.layers[selectedLayerIndex.value]
            } else {
                currentSettings.layers.firstOrNull() // Fallback to first layer if selection is invalid or list empty
            }

            if (selectedLayer?.sampleId?.isNotBlank() == true) {
                // The AudioEngineControl.playPadSample expects global pad parameters.
                // For auditioning a specific layer's sound with global effects/modifiers:
                // We use the layer's sampleId, and its specific tuning/vol/pan,
                // but apply the global pad's envelopes and LFOs.
                // Layer's tuningSemi/Fine are absolute. Pad's tuningCoarse/Fine are base values.
                // For audition, we combine them or prioritize layer's if it's meant to be an override.
                // The consolidated SampleLayer has absolute tuningSemi/Fine. PadSettings has coarse/fine.
                // Let's assume layer's tuning is the primary one for audition.

                val effectiveVolume = currentSettings.volume * selectedLayer.volume // Layer vol modulates global vol
                val effectivePan = (currentSettings.pan + selectedLayer.pan).coerceIn(-1f, 1f) // Layer pan offsets global, clamped

                // Ensure the SampleLayer's sampleId is valid and loaded if AudioEngine requires it.
                // For now, assuming AudioEngine handles unknown sampleId gracefully or sample is preloaded.

                audioEngine.playPadSample(
                    noteInstanceId = UUID.randomUUID().toString(),
                    trackId = "audition_track", // Specific track ID for auditioning context
                    padId = currentSettings.id,
                    sampleId = selectedLayer.sampleId,
                    sliceId = null, // Slicing not handled in this editor's scope
                    velocity = 1.0f, // Full velocity for audition
                    playbackMode = currentSettings.playbackMode, // Pad's global playback mode
                    coarseTune = selectedLayer.tuningSemi, // Use layer's absolute coarse tuning
                    fineTune = selectedLayer.tuningFine,   // Use layer's absolute fine tuning
                    pan = effectivePan,
                    volume = effectiveVolume,
                    ampEnv = currentSettings.ampEnvelope,
                    filterEnv = currentSettings.filterEnvelope, // Nullable, as per model
                    pitchEnv = currentSettings.pitchEnvelope, // Nullable, as per model
                    lfos = currentSettings.lfos.filter { it.isEnabled } // Pass only enabled LFOs
                )
            } else {
                Log.w("DrumProgramEditVM", "AuditionPad: No valid sample in selected/first layer.")
                // Optionally, play a "click" or silent sound to indicate button press acknowledged
            }
        }
    }

    fun saveChanges(): PadSettings {
        Log.d("DrumProgramEditVM", "Changes saved for pad: ${_padSettings.value.id}")
        return _padSettings.value
    }

    // --- Modulation Functions ---
    fun addModulationRouting() {
        _padSettings.update { currentSettings ->
            val newModRoutings = currentSettings.modulations.toMutableList() + ModulationRouting()
            currentSettings.copy(modulations = newModRoutings)
        }
    }

    fun removeModulationRouting(id: String) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(modulations = currentSettings.modulations.filterNot { it.id == id }.toMutableList())
        }
    }

    fun updateModulationRouting(id: String, updatedRouting: ModulationRouting) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(modulations = currentSettings.modulations.map {
                if (it.id == id) updatedRouting else it
            }.toMutableList())
        }
    }

    // --- Effects Functions ---
    fun addEffect(type: EffectType) {
        // TODO: Initialize with actual default parameters for the given EffectType
        val defaultParams = mutableMapOf<String, Float>() // Placeholder
        when (type) {
            EffectType.DELAY -> {
                defaultParams["timeMs"] = 250f
                defaultParams["feedback"] = 0.3f
            }
            EffectType.REVERB -> {
                defaultParams["size"] = 0.7f
                defaultParams["decayMs"] = 1000f
            }
            // Add other effect type default params here
            else -> {}
        }
        val newEffect = EffectSetting(type = type, parameters = defaultParams)
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = (currentSettings.effects.toMutableList() + newEffect).toMutableList())
        }
    }

    fun removeEffect(id: String) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = currentSettings.effects.filterNot { it.id == id }.toMutableList())
        }
    }

    fun updateEffectParameter(effectId: String, paramName: String, value: Float) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = currentSettings.effects.map { effect ->
                if (effect.id == effectId) {
                    val updatedParams = effect.parameters.toMutableMap() // Ensure parameters is mutable for this operation
                    updatedParams[paramName] = value
                    effect.copy(parameters = updatedParams)
                } else {
                    effect
                }
            }.toMutableList())
        }
    }

    fun updateEffectMix(effectId: String, mix: Float) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = currentSettings.effects.map { effect ->
                if (effect.id == effectId) {
                    effect.copy(mix = mix.coerceIn(0f, 1f))
                } else {
                    effect
                }
            }.toMutableList())
        }
    }

    fun toggleEffectEnabled(effectId: String) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = currentSettings.effects.map { effect ->
                if (effect.id == effectId) {
                    effect.copy(isEnabled = !effect.isEnabled)
                } else {
                    effect
                }
            }.toMutableList())
        }
    }
    // TODO: Implement reorderEffect(fromIndex: Int, toIndex: Int) if needed.
    // This would typically involve removing and inserting at specific indices,
    // and ensuring the underlying list in PadSettings is updated accordingly.
}
