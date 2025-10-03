package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.PatternRepository
import com.high.theone.model.*
import javax.inject.Inject

/**
 * ViewModel for the step sequencer managing pattern state, playback control,
 * and UI interactions.
 */
@HiltViewModel
class SequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val patternRepository: PatternRepository,
    private val timingEngine: TimingEngine,
    private val patternManager: PatternManager
) : ViewModel() {
    
    private val _sequencerState = MutableStateFlow(SequencerState())
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()
    
    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()
    
    private val _pads = MutableStateFlow<List<PadState>>(List(16) { PadState(it) })
    val pads: StateFlow<List<PadState>> = _pads.asStateFlow()
    
    private val _muteSoloState = MutableStateFlow(TrackMuteSoloState())
    val muteSoloState: StateFlow<TrackMuteSoloState> = _muteSoloState.asStateFlow()
    
    init {
        setupTimingCallbacks()
        loadPatterns()
        loadPadStates()
    }
    
    private fun setupTimingCallbacks() {
        // Setup timing engine callbacks for step triggers
        // This would integrate with the actual timing engine implementation
    }
    
    private fun loadPatterns() {
        viewModelScope.launch {
            try {
                // Load patterns from repository
                // For now, create a default pattern
                val defaultPattern = Pattern(
                    name = "New Pattern",
                    length = 16,
                    tempo = 120f,
                    swing = 0f
                )
                _patterns.value = listOf(defaultPattern)
                _sequencerState.update { 
                    it.copy(
                        currentPattern = defaultPattern.id,
                        patterns = listOf(defaultPattern.id)
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun loadPadStates() {
        // Load pad states from audio engine or repository
        // For now, use default pad states
    }
    
    fun handleTransportAction(action: TransportAction) {
        when (action) {
            is TransportAction.Play -> playPattern()
            is TransportAction.Pause -> pausePattern()
            is TransportAction.Stop -> stopPattern()
            is TransportAction.ToggleRecord -> toggleRecording()
            is TransportAction.SetTempo -> setTempo(action.tempo)
            is TransportAction.SetSwing -> setSwing(action.swing)
        }
    }
    
    private fun playPattern() {
        val currentPattern = getCurrentPattern() ?: return
        // Start timing engine with pattern settings
        _sequencerState.update { 
            it.copy(isPlaying = true, isPaused = false) 
        }
    }
    
    private fun pausePattern() {
        _sequencerState.update { 
            it.copy(isPlaying = false, isPaused = true) 
        }
    }
    
    private fun stopPattern() {
        _sequencerState.update { 
            it.copy(
                isPlaying = false, 
                isPaused = false, 
                currentStep = 0
            ) 
        }
    }
    
    private fun toggleRecording() {
        _sequencerState.update { 
            it.copy(isRecording = !it.isRecording) 
        }
    }
    
    private fun setTempo(tempo: Float) {
        val currentPattern = getCurrentPattern() ?: return
        val updatedPattern = currentPattern.copy(tempo = tempo)
        updatePattern(updatedPattern)
    }
    
    private fun setSwing(swing: Float) {
        val currentPattern = getCurrentPattern() ?: return
        val updatedPattern = currentPattern.copy(swing = swing)
        updatePattern(updatedPattern)
    }
    
    fun toggleStep(padIndex: Int, stepIndex: Int) {
        val currentPattern = getCurrentPattern() ?: return
        val updatedPattern = patternManager.toggleStep(currentPattern, padIndex, stepIndex)
        updatePattern(updatedPattern)
    }
    
    fun setStepVelocity(padIndex: Int, stepIndex: Int, velocity: Int) {
        val currentPattern = getCurrentPattern() ?: return
        val updatedPattern = patternManager.setStepVelocity(currentPattern, padIndex, stepIndex, velocity)
        updatePattern(updatedPattern)
    }
    
    fun togglePadSelection(padIndex: Int) {
        _sequencerState.update { state ->
            val selectedPads = state.selectedPads.toMutableSet()
            if (selectedPads.contains(padIndex)) {
                selectedPads.remove(padIndex)
            } else {
                selectedPads.add(padIndex)
            }
            state.copy(selectedPads = selectedPads)
        }
    }
    
    fun togglePadMute(padIndex: Int) {
        _muteSoloState.update { it.toggleMute(padIndex) }
    }
    
    fun togglePadSolo(padIndex: Int) {
        _muteSoloState.update { it.toggleSolo(padIndex) }
    }
    
    fun selectAllPads() {
        _sequencerState.update { state ->
            state.copy(selectedPads = (0..15).toSet())
        }
    }
    
    fun selectAssignedPads() {
        val assignedPadIndices = _pads.value
            .filter { it.hasAssignedSample }
            .map { it.index }
            .toSet()
        
        _sequencerState.update { state ->
            state.copy(selectedPads = assignedPadIndices)
        }
    }
    
    private fun getCurrentPattern(): Pattern? {
        val currentPatternId = _sequencerState.value.currentPattern
        return _patterns.value.find { it.id == currentPatternId }
    }
    
    private fun updatePattern(pattern: Pattern) {
        val updatedPatterns = _patterns.value.map { 
            if (it.id == pattern.id) pattern else it 
        }
        _patterns.value = updatedPatterns
        
        // Save to repository
        viewModelScope.launch {
            try {
                patternRepository.savePattern(pattern)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup timing engine and other resources
    }
}
