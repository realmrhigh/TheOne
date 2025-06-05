package com.example.theone.features.drumtrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.features.drumtrack.model.DrumTrack
// Ensure SampleMetadata is the one from drumtrack.model if it's different from the common one.
// However, ProjectManager will likely return ::model::SampleMetadata. For now, keep as is.
import com.example.theone.features.drumtrack.model.SampleMetadata
// import com.example.theone.features.drumtrack.model.PlaybackMode // Removed
import com.example.theone.features.drumtrack.model.createDefaultDrumTrack
// Use the main interfaces
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
// Specific types for playPadSample parameters
// import com.example.theone.features.sampler.PlaybackMode as SamplerPlaybackMode // Removed
// import com.example.theone.features.sampler.SamplerViewModel.EnvelopeSettings as SamplerEnvelopeSettings // Removed
import com.example.theone.model.PlaybackMode // Added
import com.example.theone.model.EnvelopeSettings // Added
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// TODO: These interfaces (AudioEngineControl, ProjectManager) and SampleMetadata
// should eventually be moved to common/core modules and injected via Hilt.
// For now, we assume they are accessible.
// AudioEngineControl and ProjectManager are currently taken from the .sampler package.

// Placeholder for AudioEngineControl.playPadSample's EnvelopeSettings if not defined globally
// This should ideally come from a shared module.


class DrumTrackViewModel(
    private val audioEngine: AudioEngineControl, // Use main interface
    private val projectManager: ProjectManager // Use main interface
) : ViewModel() {

    private val _drumTrack = MutableStateFlow(createDefaultDrumTrack())
    val drumTrack: StateFlow<DrumTrack> = _drumTrack.asStateFlow()

    // To communicate messages like "sample assigned" or "playback failed" to the UI
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumedUserMessage() {
        _userMessage.value = null
    }

    // --- Pad Interaction ---

    fun onPadTriggered(padId: String) {
        val currentTrack = _drumTrack.value
        val padSetting = currentTrack.pads.find { it.id == padId }

        if (padSetting == null) {
            _userMessage.value = "Error: Pad $padId not found."
            return
        }

        if (padSetting.sampleId == null) {
            _userMessage.value = "Pad $padId has no sample assigned."
            return
        }

        viewModelScope.launch {
            val noteInstanceId = "pad_note_${System.currentTimeMillis()}"

            _userMessage.value = "Playing Pad $padId (Sample: ${padSetting.sampleName ?: padSetting.sampleId})"

            // --- THIS IS THE CORRECTED BLOCK ---
            // It maps the PlaybackMode from the drum track's model to the one
            // expected by the audio engine, and it is now "exhaustive".
            val mappedSamplerPlaybackMode = when (padSetting.playbackMode) {
                PlaybackMode.ONE_SHOT -> PlaybackMode.ONE_SHOT
                PlaybackMode.NOTE_ON_OFF -> PlaybackMode.NOTE_ON_OFF
            }
            // --- END OF CORRECTION ---

            // Using SamplerViewModel.EnvelopeSettings as expected by the AudioEngineControl interface for playPadSample
            val defaultAmpEnv = EnvelopeSettings( // Use the new shared EnvelopeSettings
                attackMs = 5f,
                decayMs = 0f, // For one-shot, decay might be irrelevant if sample plays fully
                sustainLevel = 1f,
                releaseMs = 100f // A small release for click removal
            )

            _userMessage.value = "Playing Pad $padId (Sample: ${padSetting.sampleName ?: padSetting.sampleId})"

            val success = audioEngine.playPadSample(
                noteInstanceId = noteInstanceId,
                trackId = currentTrack.id,
                padId = padSetting.id,
                sampleId = padSetting.sampleId!!,
                sliceId = null, // No slices in M1.2
                velocity = 1.0f, // Default velocity for M1.2
                playbackMode = mappedSamplerPlaybackMode, // Use the mapped variable (now com.example.theone.model.PlaybackMode)
                coarseTune = padSetting.tuningCoarse, // from drumtrack.model.PadSettings
                fineTune = padSetting.tuningFine,   // from drumtrack.model.PadSettings
                pan = padSetting.pan,               // from drumtrack.model.PadSettings
                volume = padSetting.volume,           // from drumtrack.model.PadSettings
                ampEnv = defaultAmpEnv, // This is now the shared EnvelopeSettings
                filterEnv = null, // No filter envelope in M1.2
                pitchEnv = null,  // No pitch envelope in M1.2
                lfos = emptyList() // No LFOs in M1.2
            )

            if (!success) {
                _userMessage.value = "Playback failed for Pad $padId."
            } else {
                // Optionally, clear the message or set a different success message if needed
                // For example, if the "Playing Pad..." message is too transient.
                // If playPadSample is very fast, the "Playing..." message might be immediately overwritten
                // by a null from consumedUserMessage() in the UI if not handled carefully.
                // For now, this is okay.
            }
        }
    }

    // --- Sample Assignment ---

    fun assignSampleToPad(padId: String, sample: com.example.theone.model.SampleMetadata) {
        _drumTrack.update { currentTrack ->
            val newPads = currentTrack.pads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(
                        sampleId = sample.id,
                        sampleName = sample.name
                        // Reset other params or inherit from sample if needed in future
                    )
                } else {
                    pad
                }
            }
            currentTrack.copy(pads = newPads)
        }
        _userMessage.value = "Sample '${sample.name}' assigned to Pad $padId."
    }

    fun clearSampleFromPad(padId: String) {
        _drumTrack.update { currentTrack ->
            val newPads = currentTrack.pads.map { pad ->
                if (pad.id == padId) {
                    pad.copy(sampleId = null, sampleName = null)
                } else {
                    pad
                }
            }
            currentTrack.copy(pads = newPads)
        }
        _userMessage.value = "Sample cleared from Pad $padId."
    }

    // --- Track Level Adjustments (Placeholders for M1.2, more relevant later) ---
    fun setTrackVolume(volume: Float) {
        _drumTrack.update { it.copy(trackVolume = volume.coerceIn(0f, 2f)) }
    }

    fun setTrackPan(pan: Float) {
        _drumTrack.update { it.copy(trackPan = pan.coerceIn(-1f, 1f)) }
    }

    // --- Example: Load available samples (simulated) ---
    // In a real app, this would come from ProjectManager (C3)
    private val _availableSamples = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val availableSamples: StateFlow<List<SampleMetadata>> = _availableSamples.asStateFlow()

    fun fetchAvailableSamples() {
        viewModelScope.launch {
            // Simulate fetching from ProjectManager
            // In a real scenario: val samples = projectManager.getAllSamplesInPool()
            // For now, using a hardcoded list for UI development.
            _availableSamples.value = listOf(
                SampleMetadata(id = "sample1", name = "Kick Basic", filePathUri = "path/kick.wav"),
                SampleMetadata(id = "sample2", name = "Snare Bright", filePathUri = "path/snare.wav"),
                SampleMetadata(id = "sample3", name = "HiHat Closed", filePathUri = "path/hat.wav"),
                SampleMetadata(id = "sample4", name = "Clap Classic", filePathUri = "path/clap.wav")
                // Add more samples as recorded by M1.1 or loaded from disk by C3
            )
            if (_availableSamples.value.isEmpty()){
                 _userMessage.value = "No samples available in the pool (simulated)."
            }
        }
    }

    init {
        fetchAvailableSamples() // Load initially
    }
}
