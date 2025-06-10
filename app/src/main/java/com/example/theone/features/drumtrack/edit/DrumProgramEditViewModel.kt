package com.example.theone.features.drumtrack.edit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// Enums specific to this ViewModel's editing logic
enum class EditorTab { SAMPLES, ENVELOPES, LFO, MODULATION, EFFECTS }
enum class LayerParameter { SAMPLE_ID, TUNING_SEMI, TUNING_FINE, VOLUME, PAN, START_POINT, END_POINT, LOOP_POINT, LOOP_ENABLED, REVERSE }
enum class EnvelopeType { AMP, PITCH, FILTER }


class DrumProgramEditViewModel(
    private val audioEngine: AudioEngineControl,
    val projectManager: ProjectManager,
    initialPadSettings: PadSettings
) : ViewModel() {

    private val _padSettings = MutableStateFlow(initialPadSettings)
    val padSettings: StateFlow<PadSettings> = _padSettings.asStateFlow()

    private val _selectedLayerIndex = MutableStateFlow(0)
    val selectedLayerIndex: StateFlow<Int> = _selectedLayerIndex.asStateFlow()

    private val _currentEditorTab = MutableStateFlow(EditorTab.SAMPLES)
    val currentEditorTab: StateFlow<EditorTab> = _currentEditorTab.asStateFlow()

    init {
        _selectedLayerIndex.value = if (initialPadSettings.layers.isNotEmpty()) 0 else -1
    }

    fun selectEditorTab(tab: EditorTab) { _currentEditorTab.value = tab }

    fun selectLayer(layerIndex: Int) {
        if (layerIndex >= 0 && layerIndex < _padSettings.value.layers.size) {
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
            currentSettings.copy(layers = (currentSettings.layers.toMutableList() + newLayer))
        }
        _selectedLayerIndex.value = _padSettings.value.layers.size - 1
    }

    fun removeLayer(layerIndex: Int) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.layers.size) return
        _padSettings.update { currentSettings ->
            val updatedLayers = currentSettings.layers.toMutableList().apply { removeAt(layerIndex) }
            currentSettings.copy(layers = updatedLayers)
        }
        // Adjust selectedLayerIndex
        val currentSelection = _selectedLayerIndex.value
        val newSize = _padSettings.value.layers.size
        if (newSize == 0) {
            _selectedLayerIndex.value = -1
        } else if (currentSelection >= newSize) {
            _selectedLayerIndex.value = newSize - 1
        } else if (layerIndex < currentSelection) {
            _selectedLayerIndex.value = currentSelection - 1
        }
    }

    fun updateLayerParameter(layerIndex: Int, parameter: LayerParameter, value: Any) {
        if (layerIndex < 0 || layerIndex >= _padSettings.value.layers.size) return

        _padSettings.update { currentSettings ->
            val updatedLayers = currentSettings.layers.toMutableList()
            val layerToUpdate = updatedLayers[layerIndex]

            val updatedLayer = when (parameter) {
                LayerParameter.SAMPLE_ID -> {
                    val newSampleId = value as String
                    // Asynchronously fetch sample name
                    viewModelScope.launch {
                        val sampleInfo = projectManager.getSampleById(newSampleId)
                        _padSettings.update { settings ->
                            val layers = settings.layers.toMutableList()
                            if (layerIndex < layers.size && layers[layerIndex].sampleId == newSampleId) {
                                layers[layerIndex] = layers[layerIndex].copy(sampleNameCache = sampleInfo?.name ?: "N/A")
                                settings.copy(layers = layers)
                            } else {
                                settings
                            }
                        }
                    }
                    layerToUpdate.copy(sampleId = newSampleId, sampleNameCache = "Loading...")
                }
                LayerParameter.TUNING_SEMI -> layerToUpdate.copy(tuningSemi = value as Int)
                LayerParameter.TUNING_FINE -> layerToUpdate.copy(tuningFine = value as Int)
                LayerParameter.VOLUME -> layerToUpdate.copy(volume = value as Float)
                LayerParameter.PAN -> layerToUpdate.copy(pan = value as Float)
                LayerParameter.START_POINT -> layerToUpdate.copy(startPoint = value as Float)
                LayerParameter.END_POINT -> layerToUpdate.copy(endPoint = value as Float)
                LayerParameter.LOOP_POINT -> layerToUpdate.copy(loopPoint = value as Float)
                LayerParameter.LOOP_ENABLED -> layerToUpdate.copy(loopEnabled = value as Boolean)
                LayerParameter.REVERSE -> layerToUpdate.copy(reverse = value as Boolean)
            }
            updatedLayers[layerIndex] = updatedLayer
            currentSettings.copy(layers = updatedLayers)
        }
    }

    fun updateEnvelope(envelopeType: EnvelopeType, newSettings: EnvelopeSettings) {
        _padSettings.update { currentSettings ->
            when (envelopeType) {
                EnvelopeType.AMP -> currentSettings.copy(ampEnvelope = newSettings)
                EnvelopeType.PITCH -> currentSettings.copy(pitchEnvelope = newSettings)
                EnvelopeType.FILTER -> currentSettings.copy(filterEnvelope = newSettings)
            }
        }
    }

    fun updateLfo(lfoIndex: Int, newSettings: LFOSettings) {
        if (lfoIndex < 0 || lfoIndex >= _padSettings.value.lfos.size) return
        _padSettings.update { currentSettings ->
            val updatedLfos = currentSettings.lfos.toMutableList().apply { this[lfoIndex] = newSettings }
            currentSettings.copy(lfos = updatedLfos)
        }
    }

    fun auditionPad() {
        viewModelScope.launch {
            val currentSettings = _padSettings.value
            val selectedLayer = currentSettings.layers.getOrNull(selectedLayerIndex.value) ?: return@launch

            if (selectedLayer.sampleId.isNotBlank()) {
                val effectiveVolume = currentSettings.volume * selectedLayer.volume
                val effectivePan = (currentSettings.pan + selectedLayer.pan).coerceIn(-1f, 1f)

                audioEngine.playPadSample(
                    noteInstanceId = UUID.randomUUID().toString(),
                    trackId = "audition_track",
                    padId = currentSettings.id,
                    sampleId = selectedLayer.sampleId,
                    sliceId = null,
                    velocity = 1.0f,
                    playbackMode = currentSettings.playbackMode,
                    coarseTune = selectedLayer.tuningSemi,
                    fineTune = selectedLayer.tuningFine,
                    pan = effectivePan,
                    volume = effectiveVolume,
                    ampEnv = currentSettings.ampEnvelope,
                    filterEnv = currentSettings.filterEnvelope,
                    pitchEnv = currentSettings.pitchEnvelope,
                    lfos = currentSettings.lfos.filter { it.isEnabled }
                )
            } else {
                Log.w("DrumProgramEditVM", "AuditionPad: No valid sample in selected layer.")
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

    // --- Effects Functions ---
    fun addEffect(type: EffectType) {
        val defaultParams = mutableMapOf<String, Float>()
        when (type) {
            EffectType.DELAY -> {
                defaultParams["timeMs"] = 250f
                defaultParams["feedback"] = 0.3f
            }
            EffectType.REVERB -> {
                defaultParams["size"] = 0.7f
                defaultParams["decayMs"] = 1000f
            }
            else -> {}
        }
        val newEffect = EffectSetting(type = type, parameters = defaultParams)
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = (currentSettings.effects.toMutableList() + newEffect))
        }
    }

    fun removeEffect(id: String) {
        _padSettings.update { currentSettings ->
            currentSettings.copy(effects = currentSettings.effects.filterNot { it.id == id }.toMutableList())
        }
    }

    fun updateEffectMix(effectId: String, mix: Float) {
        _padSettings.update { currentSettings ->
            val updatedEffects = currentSettings.effects.map { effect ->
                if (effect.id == effectId) effect.copy(mix = mix.coerceIn(0f, 1f)) else effect
            }.toMutableList()
            currentSettings.copy(effects = updatedEffects)
        }
    }

    fun toggleEffectEnabled(effectId: String) {
        _padSettings.update { currentSettings ->
            val updatedEffects = currentSettings.effects.map { effect ->
                if (effect.id == effectId) effect.copy(isEnabled = !effect.isEnabled) else effect
            }.toMutableList()
            currentSettings.copy(effects = updatedEffects)
        }
    }
}