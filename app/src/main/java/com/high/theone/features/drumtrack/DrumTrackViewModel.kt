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

    private val _samplesLoaded = MutableStateFlow(false)
    val samplesLoaded: StateFlow<Boolean> = _samplesLoaded.asStateFlow()

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
            val padId = "$index"
            val samplePath = if (index < defaultSamples.size) defaultSamples[index] else "asset://test.wav"
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
                println("DrumTrack: Starting sample loading...")
                
                // Initialize the audio engine first and wait for it to complete
                val initialized = audioEngine.initialize(44100, 256, true)
                if (!initialized) {
                    println("DrumTrack: ⚠️ Audio engine initialization failed")
                    return@launch
                }
                
                println("DrumTrack: ✓ Audio engine initialized successfully")
                
                // Small delay to ensure AssetManager is fully set up
                kotlinx.coroutines.delay(100)
                
                var successCount = 0
                var failCount = 0
                
                // Load each pad's sample sequentially
                _padSettingsMap.value.forEach { (padId, padSettings) ->
                    // Load the first layer's sample for each pad
                    if (padSettings.layers.isNotEmpty()) {
                        val samplePath = padSettings.layers.first().sample.filePath
                        if (samplePath.isNotEmpty()) {
                            try {
                                val success = audioEngine.loadSampleToMemory(padId, samplePath)
                                if (success) {
                                    successCount++
                                    println("DrumTrack: ✓ Loaded $padId (${padSettings.name}) from $samplePath")
                                } else {
                                    failCount++
                                    println("DrumTrack: ✗ Failed to load $padId (${padSettings.name}) from $samplePath")
                                }
                            } catch (e: Exception) {
                                failCount++
                                println("DrumTrack: ✗ Exception loading $padId: ${e.message}")
                            }
                        }
                    }
                }
                
                _samplesLoaded.value = true
                println("DrumTrack: Sample loading completed - Success: $successCount, Failed: $failCount")
            } catch (e: Exception) {
                // Log error but don't crash
                println("DrumTrack: ✗ Failed to load initial drum samples: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onPadTriggered(padId: String, velocity: Float = 1.0f) {
        _activePadId.value = padId

        // Trigger audio playback
        viewModelScope.launch {
            try {
                val padSettings = _padSettingsMap.value[padId]
                if (padSettings != null && padSettings.layers.isNotEmpty()) {
                    // Check if samples are loaded
                    if (!_samplesLoaded.value) {
                        println("DrumTrack: ⚠️ Samples not yet loaded, skipping pad trigger")
                        _activePadId.value = null
                        return@launch
                    }

                    // Apply velocity sensitivity to volume
                    val adjustedVolume = (padSettings.volume * velocity.coerceIn(0f, 1f))
                    println("DrumTrack: Triggering pad $padId (${padSettings.name}) vel=$velocity")
                    audioEngine.triggerSample(padId, adjustedVolume, padSettings.pan)

                    // Emit event for sequencer if needed
                    sequencerEventBus.emitPadTriggerEvent(PadTriggerEvent(padId, (velocity * 127).toInt()))

                    // Clear active state after a short delay
                    kotlinx.coroutines.delay(100)
                    if (_activePadId.value == padId) {
                        _activePadId.value = null
                    }
                } else {
                    println("DrumTrack: ⚠️ Pad settings not found for $padId")
                    _activePadId.value = null
                }
            } catch (e: Exception) {
                println("DrumTrack: ✗ Failed to trigger pad $padId: ${e.message}")
                _activePadId.value = null
            }
        }
    }

    fun updatePadSettings(updated: PadSettings) {
        _padSettingsMap.value = _padSettingsMap.value.toMutableMap().apply {
            put(updated.id, updated)
        }
        // Push filter settings to the audio engine so the next trigger picks them up
        val fs = updated.filterSettings
        viewModelScope.launch {
            audioEngine.setPadFilter(
                padId      = updated.id,
                enabled    = fs.enabled,
                modeOrdinal = fs.mode.ordinal,
                cutoffHz   = fs.cutoffHz,
                resonance  = fs.resonance
            )
        }
    }
    
    // TODO: Complete implementation as needed
}
