package com.high.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.high.theone.audio.AudioEngine
import com.high.theone.domain.ProjectManager
import com.high.theone.features.drumtrack.model.PadSettings
// import com.high.theone.features.sequencer.SequencerViewModel // Removed import
import com.high.theone.features.sequencer.SequencerEventBus // Added import
import com.high.theone.features.sequencer.PadTriggerEvent // Added import
import com.high.theone.model.SampleMetadata
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
    val audioEngine: com.high.theone.audio.AudioEngineControl, // Changed to interface
    val projectManager: ProjectManager,
    private val sequencerEventBus: SequencerEventBus
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
// ...rest of the code remains unchanged...
