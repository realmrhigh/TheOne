package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import com.high.theone.model.*
import javax.inject.Inject

/**
 * Simplified SequencerViewModel that works without complex dependencies
 */
@HiltViewModel
class SimpleSequencerViewModel @Inject constructor() : ViewModel() {
    
    private val _sequencerState = MutableStateFlow(
        SequencerState(
            isPlaying = false,
            isRecording = false,
            currentStep = 0,
            currentPattern = "default",
            selectedPads = setOf(0, 1, 2, 3)
        )
    )
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()
    
    private val _patterns = MutableStateFlow(
        listOf(
            Pattern(
                id = "default",
                name = "Default Pattern",
                length = 16,
                tempo = 120f,
                swing = 0f,
                steps = emptyMap()
            )
        )
    )
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()
    
    private val _pads = MutableStateFlow(
        (0..15).map { index ->
            SequencerPadInfo(
                index = index,
                sampleId = if (index < 8) "sample_$index" else null,
                sampleName = if (index < 8) "Sample ${index + 1}" else null,
                hasAssignedSample = index < 8,
                isEnabled = true,
                volume = 1.0f,
                pan = 0.0f,
                playbackMode = com.high.theone.model.PlaybackMode.ONE_SHOT,
                chokeGroup = null,
                isLoading = false,
                canTrigger = true
            )
        }
    )
    val pads: StateFlow<List<SequencerPadInfo>> = _pads.asStateFlow()
    
    private val _muteSoloState = MutableStateFlow(
        TrackMuteSoloState(
            mutedTracks = emptySet(),
            soloedTracks = emptySet()
        )
    )
    val muteSoloState: StateFlow<TrackMuteSoloState> = _muteSoloState.asStateFlow()
    
    // Transport actions
    fun handleTransportAction(action: TransportControlAction) {
        when (action) {
            is TransportControlAction.Play -> {
                _sequencerState.value = _sequencerState.value.copy(isPlaying = true)
            }
            is TransportControlAction.Pause -> {
                _sequencerState.value = _sequencerState.value.copy(isPlaying = false)
            }
            is TransportControlAction.Stop -> {
                _sequencerState.value = _sequencerState.value.copy(
                    isPlaying = false,
                    currentStep = 0
                )
            }
            is TransportControlAction.ToggleRecord -> {
                _sequencerState.value = _sequencerState.value.copy(isRecording = !_sequencerState.value.isRecording)
            }
            is TransportControlAction.SetTempo -> {
                // Update current pattern tempo
                val currentPatterns = _patterns.value.toMutableList()
                val currentPatternIndex = currentPatterns.indexOfFirst { it.id == _sequencerState.value.currentPattern }
                if (currentPatternIndex >= 0) {
                    currentPatterns[currentPatternIndex] = currentPatterns[currentPatternIndex].copy(tempo = action.tempo)
                    _patterns.value = currentPatterns
                }
            }
            is TransportControlAction.SetSwing -> {
                // Update current pattern swing
                val currentPatterns = _patterns.value.toMutableList()
                val currentPatternIndex = currentPatterns.indexOfFirst { it.id == _sequencerState.value.currentPattern }
                if (currentPatternIndex >= 0) {
                    currentPatterns[currentPatternIndex] = currentPatterns[currentPatterns.indexOfFirst { it.id == _sequencerState.value.currentPattern }].copy(swing = action.swing)
                    _patterns.value = currentPatterns
                }
            }
            is TransportControlAction.SetPatternLength -> {
                // Update current pattern length
                val currentPatterns = _patterns.value.toMutableList()
                val currentPatternIndex = currentPatterns.indexOfFirst { it.id == _sequencerState.value.currentPattern }
                if (currentPatternIndex >= 0) {
                    currentPatterns[currentPatternIndex] = currentPatterns[currentPatternIndex].copy(length = action.length)
                    _patterns.value = currentPatterns
                }
            }
        }
    }
    
    // Pattern management
    fun selectPattern(patternId: String) {
        _sequencerState.value = _sequencerState.value.copy(currentPattern = patternId)
    }
    
    fun createPattern(name: String, length: Int) {
        val newPattern = Pattern(
            id = "pattern_${System.currentTimeMillis()}",
            name = name,
            length = length,
            tempo = 120f,
            swing = 0f,
            steps = emptyMap()
        )
        _patterns.value = _patterns.value + newPattern
    }
    
    fun duplicatePattern(patternId: String) {
        val pattern = _patterns.value.find { it.id == patternId }
        if (pattern != null) {
            val duplicatedPattern = pattern.copy(
                id = "pattern_${System.currentTimeMillis()}",
                name = "${pattern.name} Copy"
            )
            _patterns.value = _patterns.value + duplicatedPattern
        }
    }
    
    fun deletePattern(patternId: String) {
        if (_patterns.value.size > 1) { // Keep at least one pattern
            _patterns.value = _patterns.value.filter { it.id != patternId }
            if (_sequencerState.value.currentPattern == patternId) {
                _sequencerState.value = _sequencerState.value.copy(currentPattern = _patterns.value.first().id)
            }
        }
    }
    
    fun renamePattern(patternId: String, newName: String) {
        val currentPatterns = _patterns.value.toMutableList()
        val patternIndex = currentPatterns.indexOfFirst { it.id == patternId }
        if (patternIndex >= 0) {
            currentPatterns[patternIndex] = currentPatterns[patternIndex].copy(name = newName)
            _patterns.value = currentPatterns
        }
    }
    
    // Pad management
    fun togglePadSelection(padId: Int) {
        val currentSelected = _sequencerState.value.selectedPads.toMutableSet()
        if (currentSelected.contains(padId)) {
            currentSelected.remove(padId)
        } else {
            currentSelected.add(padId)
        }
        _sequencerState.value = _sequencerState.value.copy(selectedPads = currentSelected)
    }
    
    fun togglePadMute(padId: Int) {
        val currentMuted = _muteSoloState.value.mutedTracks.toMutableSet()
        if (currentMuted.contains(padId)) {
            currentMuted.remove(padId)
        } else {
            currentMuted.add(padId)
        }
        _muteSoloState.value = _muteSoloState.value.copy(mutedTracks = currentMuted)
    }
    
    fun togglePadSolo(padId: Int) {
        val currentSoloed = _muteSoloState.value.soloedTracks.toMutableSet()
        if (currentSoloed.contains(padId)) {
            currentSoloed.remove(padId)
        } else {
            currentSoloed.add(padId)
        }
        _muteSoloState.value = _muteSoloState.value.copy(soloedTracks = currentSoloed)
    }
    
    fun selectAllPads() {
        _sequencerState.value = _sequencerState.value.copy(selectedPads = (0..15).toSet())
    }
    
    fun selectAssignedPads() {
        val assignedPads = _pads.value.filter { it.hasAssignedSample }.map { it.index }.toSet()
        _sequencerState.value = _sequencerState.value.copy(selectedPads = assignedPads)
    }
    
    // Step management
    fun toggleStep(patternId: String, padId: Int, stepIndex: Int) {
        // Implementation for toggling steps would go here
        // For now, just a placeholder
    }
    
    fun setStepVelocity(patternId: String, padId: Int, stepIndex: Int, velocity: Float) {
        // Implementation for setting step velocity would go here
        // For now, just a placeholder
    }
}