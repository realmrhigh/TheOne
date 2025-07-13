package com.high.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.features.sequencer.SequencerEventBus
import com.high.theone.features.sequencer.PadTriggerEvent
import com.high.theone.model.SampleMetadata
import com.high.theone.model.Sample
import com.high.theone.model.SampleLayer
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
) : ViewModel() {    private val _padSettingsMap = MutableStateFlow<Map<String, PadSettings>>(emptyMap())
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
        // Initialize pads with default drum samples from trap_808_king kit
        val defaultSamples = listOf(
            "asset://drum_kits/trap_808_king/kick_808.wav",
            "asset://drum_kits/trap_808_king/snare_electronic.wav",
            "asset://drum_kits/trap_808_king/hihat_trap.wav",
            "asset://drum_kits/trap_808_king/clap_trap.wav",
            "asset://drum_kits/trap_808_king/bass_808.wav",
            "asset://drum_kits/trap_808_king/tom_electronic.wav",
            "asset://drum_kits/trap_808_king/kick_simmons.wav",
            "asset://drum_kits/trap_808_king/effect_reverse.wav",
            "asset://kick.wav", "asset://snare.wav", "asset://hat.wav", "asset://clap.wav", // Fallback samples
            "asset://test.wav", "asset://click_primary.wav", "asset://click_secondary.wav", "asset://test.wav"
        )
        
        val initialPads = (0 until NUM_PADS).associate { index ->
            val padId = "Pad$index"
            val samplePath = if (index < defaultSamples.size) defaultSamples[index] else "test.wav"
            val padName = when (index) {
                0 -> "Kick"
                1 -> "Snare" 
                2 -> "HiHat"
                3 -> "Clap"
                4 -> "808"
                5 -> "Tom"
                6 -> "Kick2"
                7 -> "FX"
                else -> "Pad${index + 1}"
            }
            
            // Create a sample layer with the appropriate sample
            val sample = Sample(
                id = UUID.randomUUID(),
                name = padName,
                filePath = samplePath
            )
            val layer = SampleLayer(
                sample = sample,
                velocityRange = 1..127,
                gain = 0.8f
            )
            
            padId to PadSettings(
                id = padId,
                name = padName,
                layers = mutableListOf(layer),
                volume = 0.8f
            )
        }
        _padSettingsMap.value = initialPads
          // Load initial samples into audio engine
        loadInitialSamples()
    }
    
    private fun loadInitialSamples() {
        viewModelScope.launch {
            try {
                // Initialize the audio engine first
                val initialized = audioEngine.initialize(44100, 256, true)
                if (!initialized) {
                    println("Warning: Audio engine initialization failed")
                    return@launch
                }
                
                // Load each pad's sample
                _padSettingsMap.value.forEach { (padId, padSettings) ->
                    // Load the first layer's sample for each pad
                    if (padSettings.layers.isNotEmpty()) {
                        val samplePath = padSettings.layers.first().sample.filePath
                        if (samplePath.isNotEmpty()) {
                            val success = audioEngine.loadSampleToMemory(padId, samplePath)
                            println("Loading sample $padId ($samplePath): ${if (success) "SUCCESS" else "FAILED"}")
                        }
                    }
                }
                println("Drum pad sample loading completed")
            } catch (e: Exception) {
                // Log error but don't crash
                println("Failed to load initial drum samples: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onPadTriggered(padId: String) {
        _activePadId.value = padId
        
        // Trigger audio playback
        viewModelScope.launch {            try {
                val padSettings = _padSettingsMap.value[padId]
                if (padSettings != null && padSettings.layers.isNotEmpty()) {
                    // Use the simple triggerSample method for immediate playback
                    audioEngine.triggerSample(padId, padSettings.volume, padSettings.pan)
                    
                    // Emit event for sequencer if needed
                    sequencerEventBus.emitPadTriggerEvent(PadTriggerEvent(padId, (padSettings.volume * 127).toInt()))
                    
                    // Clear active state after a short delay
                    kotlinx.coroutines.delay(100)
                    if (_activePadId.value == padId) {
                        _activePadId.value = null
                    }
                }
            } catch (e: Exception) {
                println("Failed to trigger pad $padId: ${e.message}")
                _activePadId.value = null
            }
        }
    }    fun updatePadSettings(updated: PadSettings) {
        _padSettingsMap.value = _padSettingsMap.value.toMutableMap().apply {
            put(updated.id, updated)
        }
    }
    
    // TODO: Complete implementation as needed
}
