package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import com.high.theone.audio.AudioEngineControl
import com.high.theone.model.PlaybackMode
import com.high.theone.model.Sequence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val eventBus: SequencerEventBus
    // Add other dependencies if needed, e.g., ProjectManager
) : ViewModel() {

    // Example LiveData/StateFlow for UI state
    // val currentSequence: StateFlow<Sequence?> = ...
    // val playbackState: StateFlow<PlaybackMode> = ...

    fun onPlayPauseClicked() {
        // audioEngine.togglePlayback()
    }

    fun onStopClicked() {
        // audioEngine.stopPlayback()
    }

    fun onRecordClicked() {
        // Handle recording logic
    }

    fun onStepClicked(trackIndex: Int, stepIndex: Int) {
        // Handle step click, update sequence, notify audioEngine
    }

    // More functions to handle UI events and interactions with AudioEngine
}
