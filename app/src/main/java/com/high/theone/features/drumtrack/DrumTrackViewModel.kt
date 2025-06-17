package com.high.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.features.sequencer.SequencerEventBus
import com.high.theone.features.sequencer.PadTriggerEvent
import com.high.theone.model.SampleMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class DrumTrackViewModel @Inject constructor(
    val audioEngine: AudioEngineControl,
    val projectManager: ProjectManager,
    private val sequencerEventBus: SequencerEventBus
) : ViewModel() {

    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap())
    val padSettingsMap: StateFlow<Map<String, PadSettings>> = _padSettingsMap.asStateFlow()

    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    // Define number of pads, e.g., for a 4x4 grid
    private val NUM_PADS = 16

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _activePadId = MutableStateFlow<String?>(null)
    val activePadId: StateFlow<String?> = _activePadId.asStateFlow()

    init {
        val initialPads = (0 until NUM_PADS).associate { index ->
            val padId = "Pad$index" // Generate IDs like "Pad0", "Pad1", ... "Pad15"
            padId to PadSettings(id = padId)
        }
        _padSettingsMap.value = initialPads
    }

    fun onPadTriggered(padId: String) {
        _activePadId.value = padId
        // TODO: Trigger sound playback via audioEngine
    }

    fun updatePadSettings(updated: PadSettings) {
        _padSettingsMap.value = _padSettingsMap.value.toMutableMap().apply {
            put(updated.id, updated)
        }
    }
// TODO: Complete implementation as needed
}
