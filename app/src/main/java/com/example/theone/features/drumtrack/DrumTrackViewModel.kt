package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
// Import the factory
import com.example.theone.features.drumtrack.edit.DrumProgramEditViewModelFactory
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.features.sequencer.PadTriggerEvent
import com.example.theone.features.sequencer.SequencerEventBus
import com.example.theone.model.SampleMetadata
import com.example.theone.model.SampleLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DrumTrackViewModel @Inject constructor(
    val audioEngine: AudioEngineControl,
    val projectManager: ProjectManager,
    private val sequencerEventBus: SequencerEventBus,
    // Add the factory here
    val drumProgramEditViewModelFactory: DrumProgramEditViewModelFactory
) : ViewModel() {

    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap())
    val padSettingsMap: StateFlow<Map<String, PadSettings>> = _padSettingsMap.asStateFlow()

    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    private val NUM_PADS = 16

    init {
        val initialPads = (0 until NUM_PADS).associate { index ->
            val padId = "Pad${index}" // Escaped for subtask, will be "Pad${index}" in Kotlin
            padId to PadSettings(id = padId)
        }
        _padSettingsMap.value = initialPads
        loadSamplesForAssignment()
    }

    fun loadSamplesForAssignment() {
        _availableSamples.value = projectManager.getSamplesFromPool()
    }

    fun assignSampleToPad(padId: String, sample: SampleMetadata) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId)

        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isEmpty()) {
            layers.add(SampleLayer(id = "layer_0", sampleId = sample.id, sampleNameCache = sample.name))
        } else {
            layers[0] = layers[0].copy(
                sampleId = sample.id,
                sampleNameCache = sample.name,
                startPoint = 0f,
                endPoint = 1f,
                loopPoint = 0f,
                loopEnabled = false,
                reverse = false,
                tuningSemi = 0,
                tuningFine = 0
            )
        }
        currentPads[padId] = existingPadSetting.copy(layers = layers)
        _padSettingsMap.value = currentPads
    }

    fun clearSampleFromPad(padId: String) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        val existingPadSetting = currentPads[padId] ?: PadSettings(id = padId)

        val layers = existingPadSetting.layers.toMutableList()
        if (layers.isNotEmpty()) {
            layers[0] = layers[0].copy(
                sampleId = "",
                sampleNameCache = "Empty",
                startPoint = 0f,
                endPoint = 1f,
                loopPoint = 0f,
                loopEnabled = false,
                reverse = false,
                tuningSemi = 0,
                tuningFine = 0,
                volume = 1.0f,
                pan = 0.0f
            )
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
                        trackId = "drumTrack_TODO",
                        padId = padSetting.id,
                        sampleId = sampleIdToPlay,
                        sliceId = null,
                        velocity = 1.0f,
                        playbackMode = padSetting.playbackMode,
                        coarseTune = padSetting.tuningCoarse,
                        fineTune = padSetting.tuningFine,
                        pan = padSetting.pan,
                        volume = padSetting.volume,
                        ampEnv = padSetting.ampEnvelope,
                        filterEnv = padSetting.filterEnvelope,
                        pitchEnv = padSetting.pitchEnvelope,
                        lfos = padSetting.lfos
                    )
                }
                viewModelScope.launch {
                    sequencerEventBus.emitPadTriggerEvent(PadTriggerEvent(padId = padId, velocity = 127))
                }
            } else {
                println("DrumTrackViewModel: Pad ${padId} triggered, but no sample assigned to its first layer.")
            }
        } else {
            println("DrumTrackViewModel: Pad ${padId} triggered, but no settings found.")
        }
    }

    fun updatePadSetting(padId: String, newSettings: PadSettings) {
        val currentPads = _padSettingsMap.value.toMutableMap()
        currentPads[padId] = newSettings.copy(id = padId)
        _padSettingsMap.value = currentPads.toMap()

        viewModelScope.launch {
            try {
                projectManager.savePadSettings(padId, newSettings).let { result ->
                    if (result.isSuccess) {
                        android.util.Log.d("DrumTrackViewModel", "Pad settings persistence successful for ${padId}.")
                    } else {
                        android.util.Log.e("DrumTrackViewModel", "Pad settings persistence failed for ${padId}: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DrumTrackViewModel", "Error saving pad settings for ${padId}", e)
            }
        }
    }
}