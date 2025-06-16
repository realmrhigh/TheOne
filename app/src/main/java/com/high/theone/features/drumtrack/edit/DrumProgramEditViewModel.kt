package com.high.theone.features.drumtrack.edit

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.model.LayerModels.SampleLayer
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SynthModels.EffectSetting
import com.high.theone.model.SynthModels.EnvelopeSettings
import com.high.theone.model.SynthModels.LFOSettings
import com.high.theone.model.SynthModels.ModulationRouting
import com.high.theone.model.SynthModels.ModSource
import com.high.theone.model.SynthModels.ModDestination
import com.high.theone.model.SynthModels.EffectType
import com.high.theone.model.SynthModels.EffectParameterDefinition
import com.high.theone.model.SynthModels.DefaultEffectParameterProvider
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.features.drumtrack.edit.DrumProgramEditEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID // For generating noteInstanceId

// Enums specific to this ViewModel's editing logic
enum class EditorTab { SAMPLES, ENVELOPES, LFO, MODULATION, EFFECTS }
enum class LayerParameter { SAMPLE_ID, TUNING_COARSE_OFFSET, TUNING_FINE_OFFSET, START_POINT, END_POINT, LOOP_POINT, LOOP_ENABLED, REVERSE }
// This EnvelopeType is for selecting which envelope to edit (Amp, Pitch, Filter)
enum class EnvelopeType { AMP, PITCH, FILTER }

// TODO: Ensure SampleMetadata is also consolidated, current ProjectManager interface uses local def.
// For now, local SampleMetadata, ProjectManager, AudioEngine, and DummyProjectManagerImpl remain
// to minimize the scope of this change. Subsequent steps will update their signatures.

// Local interfaces and dummy implementation - these will need to be updated
// to use consolidated models in their method signatures in a later step.
// For now, they use the local SampleMetadata for getAvailableSamples etc.
// ...rest of the code remains unchanged...

class DrumProgramEditViewModel(
    private val audioEngine: AudioEngine, // Should match the interface used in tests
    private val projectManager: ProjectManager,
    initialPadSettings: PadSettings
) : ViewModel() {
    // State
    private val _padSettings = MutableStateFlow(initialPadSettings)
    val padSettings: StateFlow<PadSettings> = _padSettings.asStateFlow()

    private val _selectedLayerIndex = MutableStateFlow(
        if (initialPadSettings.sampleLayers.isNotEmpty()) 0 else -1
    )
    val selectedLayerIndex: StateFlow<Int> = _selectedLayerIndex.asStateFlow()

    private val _currentEditorTab = MutableStateFlow(EditorTab.SAMPLES)
    val currentEditorTab: StateFlow<EditorTab> = _currentEditorTab.asStateFlow()

    // Layer selection
    fun selectLayer(index: Int) {
        if (_padSettings.value.sampleLayers.isEmpty()) {
            _selectedLayerIndex.value = -1
            return
        }
        if (index in _padSettings.value.sampleLayers.indices) {
            _selectedLayerIndex.value = index
        }
    }

    fun addSampleLayer(sample: SampleMetadata) {
        val newLayer = SampleLayer(
            id = UUID.randomUUID().toString(),
            sampleId = sample.id,
            sampleNameCache = sample.name
        )
        val newLayers = _padSettings.value.sampleLayers + newLayer
        _padSettings.value = _padSettings.value.copy(sampleLayers = newLayers)
        _selectedLayerIndex.value = newLayers.lastIndex
    }

    fun removeLayer(index: Int) {
        val layers = _padSettings.value.sampleLayers
        if (index !in layers.indices) return
        val newLayers = layers.toMutableList().apply { removeAt(index) }
        _padSettings.value = _padSettings.value.copy(sampleLayers = newLayers)
        _selectedLayerIndex.value = when {
            newLayers.isEmpty() -> -1
            index <= 0 -> 0
            index > newLayers.lastIndex -> newLayers.lastIndex
            else -> index - 1
        }
    }

    fun updateLayerParameter(index: Int, param: LayerParameter, value: Any?) {
        val layers = _padSettings.value.sampleLayers
        if (index !in layers.indices) return
        val oldLayer = layers[index]
        val newLayer = when (param) {
            LayerParameter.SAMPLE_ID -> {
                val sampleId = value as? String ?: return
                val sample = projectManager.getSampleMetadataById(sampleId)
                oldLayer.copy(sampleId = sampleId, sampleNameCache = sample?.name ?: sampleId)
            }
            LayerParameter.TUNING_COARSE_OFFSET -> {
                oldLayer.copy(tuningCoarseOffset = value as? Int ?: oldLayer.tuningCoarseOffset)
            }
            LayerParameter.TUNING_FINE_OFFSET -> {
                oldLayer.copy(tuningFineOffset = value as? Int ?: oldLayer.tuningFineOffset)
            }
            LayerParameter.START_POINT -> {
                oldLayer.copy(startPoint = value as? Float ?: oldLayer.startPoint)
            }
            LayerParameter.END_POINT -> {
                oldLayer.copy(endPoint = value as? Float ?: oldLayer.endPoint)
            }
            LayerParameter.LOOP_POINT -> {
                oldLayer.copy(loopPoint = value as? Float ?: oldLayer.loopPoint)
            }
            LayerParameter.LOOP_ENABLED -> {
                oldLayer.copy(loopEnabled = value as? Boolean ?: oldLayer.loopEnabled)
            }
            LayerParameter.REVERSE -> {
                oldLayer.copy(reverse = value as? Boolean ?: oldLayer.reverse)
            }
        }
        val newLayers = layers.toMutableList().apply { set(index, newLayer) }
        _padSettings.value = _padSettings.value.copy(sampleLayers = newLayers)
    }

    fun updateEnvelope(type: EnvelopeType, envelope: EnvelopeSettings) {
        _padSettings.value = when (type) {
            EnvelopeType.AMP -> _padSettings.value.copy(ampEnvelope = envelope)
            EnvelopeType.PITCH -> _padSettings.value.copy(pitchEnvelope = envelope)
            EnvelopeType.FILTER -> _padSettings.value.copy(filterEnvelope = envelope)
        }
    }

    fun updateLfo(index: Int, lfo: LFOSettings) {
        val lfos = _padSettings.value.lfos
        if (index !in lfos.indices) return
        val newLfos = lfos.toMutableList().apply { set(index, lfo) }
        _padSettings.value = _padSettings.value.copy(lfos = newLfos)
    }

    fun selectEditorTab(tab: EditorTab) {
        _currentEditorTab.value = tab
    }

    fun saveChanges(): PadSettings {
        return _padSettings.value
    }

    fun auditionPad() {
        audioEngine.triggerPad(_padSettings.value)
    }
}
