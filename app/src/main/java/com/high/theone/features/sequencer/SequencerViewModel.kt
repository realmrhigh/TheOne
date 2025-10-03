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
    private val patternManager: PatternManager,
    private val recordingEngine: RecordingEngine,
    private val overdubManager: OverdubManager,
    private val historyManager: PatternHistoryManager
) : ViewModel() {
    
    private val _sequencerState = MutableStateFlow(SequencerState())
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()
    
    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()
    
    private val _pads = MutableStateFlow<List<PadState>>(List(16) { PadState(it) })
    val pads: StateFlow<List<PadState>> = _pads.asStateFlow()
    
    private val _muteSoloState = MutableStateFlow(TrackMuteSoloState())
    val muteSoloState: StateFlow<TrackMuteSoloState> = _muteSoloState.asStateFlow()
    
    // Recording state from recording engine
    val recordingState: StateFlow<RecordingState> = recordingEngine.recordingState
    
    // Overdub state from overdub manager
    val overdubState: StateFlow<OverdubState> = overdubManager.overdubState
    
    // History state from history manager
    val historyState: StateFlow<PatternHistoryState> = historyManager.historyState
    
    init {
        setupTimingCallbacks()
        loadPatterns()
        loadPadStates()
        setupRecordingCallbacks()
    }
    
    private fun setupTimingCallbacks() {
        // Setup timing engine callbacks for step triggers
        timingEngine.scheduleStepCallback { step, microTime ->
            handleStepTrigger(step, microTime)
        }
    }
    
    private fun setupRecordingCallbacks() {
        // Setup audio engine callback for pad hits during recording
        // This would be implemented when audio engine supports recording callbacks
    }
    
    private fun handleStepTrigger(step: Int, microTime: Long) {
        val currentPattern = getCurrentPattern() ?: return
        val state = _sequencerState.value
        
        // Update current step in UI
        _sequencerState.update { it.copy(currentStep = step) }
        
        // Trigger samples for active steps
        currentPattern.steps.forEach { (padIndex, steps) ->
            val activeStep = steps.find { it.position == step && it.isActive }
            if (activeStep != null && !isMuted(padIndex)) {
                audioEngine.triggerPad(
                    padIndex = padIndex,
                    velocity = activeStep.velocity / 127f,
                    timestamp = microTime + activeStep.microTiming.toLong()
                )
            }
        }
    }
    
    private fun isMuted(padIndex: Int): Boolean {
        val muteState = _muteSoloState.value
        return muteState.mutedTracks.contains(padIndex) || 
               (muteState.soloedTracks.isNotEmpty() && !muteState.soloedTracks.contains(padIndex))
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
        viewModelScope.launch {
            val currentState = _sequencerState.value
            if (currentState.isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }
    
    /**
     * Start recording with current pattern and settings
     */
    fun startRecording(
        mode: RecordingMode = RecordingMode.REPLACE,
        quantization: Quantization = Quantization.SIXTEENTH
    ) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            val selectedPads = _sequencerState.value.selectedPads
            
            recordingEngine.startRecording(
                pattern = currentPattern,
                mode = mode,
                quantization = quantization,
                selectedPads = selectedPads
            )
            
            _sequencerState.update { 
                it.copy(
                    isRecording = true,
                    recordingMode = mode
                ) 
            }
        }
    }
    
    /**
     * Stop recording and apply recorded hits to pattern
     */
    fun stopRecording() {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern()
            if (currentPattern != null) {
                // Save state before applying recording
                val recordingState = recordingEngine.recordingState.value
                val operation = when (recordingState.mode) {
                    RecordingMode.REPLACE -> HistoryOperation.RECORDING
                    RecordingMode.OVERDUB -> HistoryOperation.OVERDUB
                    RecordingMode.PUNCH_IN -> HistoryOperation.RECORDING
                }
                
                historyManager.saveState(
                    pattern = currentPattern,
                    operation = operation,
                    description = "${recordingState.mode.displayName} (${recordingState.getTotalHitCount()} hits)"
                )
            }
            
            val updatedPattern = recordingEngine.stopRecording()
            if (updatedPattern != null) {
                updatePattern(updatedPattern)
            }
            
            _sequencerState.update { 
                it.copy(isRecording = false) 
            }
        }
    }
    
    /**
     * Record a pad hit during playback (called by audio engine)
     */
    fun recordPadHit(padIndex: Int, velocity: Int) {
        viewModelScope.launch {
            val state = _sequencerState.value
            val currentPattern = getCurrentPattern()
            
            if (!state.isRecording || currentPattern == null) return@launch
            
            val timestamp = System.nanoTime() / 1000 // Convert to microseconds
            val stepProgress = timingEngine.getStepProgress()
            
            recordingEngine.recordPadHit(
                padIndex = padIndex,
                velocity = velocity,
                timestamp = timestamp,
                currentStep = state.currentStep,
                stepProgress = stepProgress,
                tempo = currentPattern.tempo,
                swing = currentPattern.swing
            )
        }
    }
    
    /**
     * Clear recorded hits without stopping recording
     */
    fun clearRecordedHits() {
        viewModelScope.launch {
            recordingEngine.clearRecordedHits()
        }
    }
    
    /**
     * Set recording mode
     */
    fun setRecordingMode(mode: RecordingMode) {
        _sequencerState.update { 
            it.copy(recordingMode = mode) 
        }
    }
    
    /**
     * Configure overdub settings
     */
    fun configureOverdub(
        selectedPads: Set<Int> = emptySet(),
        punchInStep: Int? = null,
        punchOutStep: Int? = null,
        layerMode: LayerMode = LayerMode.ADD,
        velocityMode: VelocityMode = VelocityMode.REPLACE
    ) {
        viewModelScope.launch {
            overdubManager.configureOverdub(
                selectedPads = selectedPads,
                punchInStep = punchInStep,
                punchOutStep = punchOutStep,
                layerMode = layerMode,
                velocityMode = velocityMode
            )
        }
    }
    
    /**
     * Set punch-in point for overdub recording
     */
    fun setPunchIn(stepPosition: Int?) {
        overdubManager.setPunchIn(stepPosition)
    }
    
    /**
     * Set punch-out point for overdub recording
     */
    fun setPunchOut(stepPosition: Int?) {
        overdubManager.setPunchOut(stepPosition)
    }
    
    /**
     * Toggle pad selection for overdub recording
     */
    fun toggleOverdubPadSelection(padIndex: Int) {
        overdubManager.togglePadSelection(padIndex)
    }
    
    /**
     * Set layer mode for overdub
     */
    fun setLayerMode(mode: LayerMode) {
        overdubManager.setLayerMode(mode)
    }
    
    /**
     * Set velocity mode for overdub
     */
    fun setVelocityMode(mode: VelocityMode) {
        overdubManager.setVelocityMode(mode)
    }
    
    /**
     * Clear overdub configuration
     */
    fun clearOverdubConfig() {
        viewModelScope.launch {
            overdubManager.clearOverdubConfig()
        }
    }
    
    /**
     * Undo the last operation on current pattern
     */
    fun undo() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            val undonePattern = historyManager.undo(currentPatternId)
            
            if (undonePattern != null) {
                // Update pattern without saving to history (to avoid creating new history entry)
                updatePatternWithoutHistory(undonePattern)
            }
        }
    }
    
    /**
     * Redo the last undone operation on current pattern
     */
    fun redo() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            val redonePattern = historyManager.redo(currentPatternId)
            
            if (redonePattern != null) {
                // Update pattern without saving to history (to avoid creating new history entry)
                updatePatternWithoutHistory(redonePattern)
            }
        }
    }
    
    /**
     * Clear pattern (with history)
     */
    fun clearPattern() {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before clearing
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.PATTERN_CLEAR,
                description = "Clear all steps"
            )
            
            val clearedPattern = patternManager.clearPattern(currentPattern)
            updatePattern(clearedPattern)
        }
    }
    
    /**
     * Clear history for current pattern
     */
    fun clearPatternHistory() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            historyManager.clearHistory(currentPatternId)
        }
    }
    
    /**
     * Get history statistics for current pattern
     */
    fun getHistoryStats(): HistoryStats? {
        val currentPatternId = _sequencerState.value.currentPattern ?: return null
        return historyManager.getHistoryStats(currentPatternId)
    }
    
    private fun setTempo(tempo: Float) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.TEMPO_CHANGE,
                description = "${currentPattern.tempo} → $tempo BPM"
            )
            
            val updatedPattern = currentPattern.copy(tempo = tempo)
            updatePattern(updatedPattern)
        }
    }
    
    private fun setSwing(swing: Float) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.SWING_CHANGE,
                description = "${(currentPattern.swing * 100).toInt()}% → ${(swing * 100).toInt()}%"
            )
            
            val updatedPattern = currentPattern.copy(swing = swing)
            updatePattern(updatedPattern)
        }
    }
    
    fun toggleStep(padIndex: Int, stepIndex: Int) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.STEP_TOGGLE,
                description = "Pad ${padIndex + 1}, Step ${stepIndex + 1}"
            )
            
            val updatedPattern = patternManager.toggleStep(currentPattern, padIndex, stepIndex)
            updatePattern(updatedPattern)
        }
    }
    
    fun setStepVelocity(padIndex: Int, stepIndex: Int, velocity: Int) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.STEP_VELOCITY,
                description = "Pad ${padIndex + 1}, Step ${stepIndex + 1} = $velocity"
            )
            
            val updatedPattern = patternManager.setStepVelocity(currentPattern, padIndex, stepIndex, velocity)
            updatePattern(updatedPattern)
        }
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
    
    fun selectPattern(patternId: String) {
        _sequencerState.update { it.copy(currentPattern = patternId) }
        historyManager.setCurrentPattern(patternId)
    }
    
    fun createPattern(name: String, length: Int) {
        viewModelScope.launch {
            try {
                val newPattern = Pattern(
                    name = name,
                    length = length,
                    tempo = 120f,
                    swing = 0f
                )
                
                patternRepository.savePattern(newPattern)
                
                val updatedPatterns = _patterns.value + newPattern
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    state.copy(
                        currentPattern = newPattern.id,
                        patterns = updatedPatterns.map { it.id }
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun duplicatePattern(patternId: String) {
        viewModelScope.launch {
            try {
                val originalPattern = _patterns.value.find { it.id == patternId } ?: return@launch
                val duplicatedPattern = originalPattern.copy(
                    id = generatePatternId(),
                    name = "${originalPattern.name} Copy"
                )
                
                patternRepository.savePattern(duplicatedPattern)
                
                val updatedPatterns = _patterns.value + duplicatedPattern
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    state.copy(patterns = updatedPatterns.map { it.id })
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deletePattern(patternId: String) {
        viewModelScope.launch {
            try {
                patternRepository.deletePattern(patternId)
                
                val updatedPatterns = _patterns.value.filter { it.id != patternId }
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    val newCurrentPattern = if (state.currentPattern == patternId) {
                        updatedPatterns.firstOrNull()?.id
                    } else {
                        state.currentPattern
                    }
                    
                    state.copy(
                        currentPattern = newCurrentPattern,
                        patterns = updatedPatterns.map { it.id }
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun renamePattern(patternId: String, newName: String) {
        viewModelScope.launch {
            try {
                val pattern = _patterns.value.find { it.id == patternId } ?: return@launch
                val updatedPattern = pattern.copy(name = newName)
                
                patternRepository.savePattern(updatedPattern)
                updatePattern(updatedPattern)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun generatePatternId(): String {
        return "pattern_${System.currentTimeMillis()}"
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
    
    /**
     * Update pattern without saving to history (used for undo/redo)
     */
    private fun updatePatternWithoutHistory(pattern: Pattern) {
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
