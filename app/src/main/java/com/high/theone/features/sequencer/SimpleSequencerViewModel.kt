package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import com.high.theone.audio.AudioEngineControl
import com.high.theone.commands.Command
import com.high.theone.commands.UndoRedoManager
import com.high.theone.model.*
import javax.inject.Inject

/**
 * Simplified SequencerViewModel with audio engine integration
 */
@HiltViewModel
class SimpleSequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl
) : ViewModel() {

    private val undoRedoManager = UndoRedoManager()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun refreshUndoRedo() {
        _canUndo.value = undoRedoManager.canUndo()
        _canRedo.value = undoRedoManager.canRedo()
    }

    fun undo() {
        undoRedoManager.undo()
        refreshUndoRedo()
    }

    fun redo() {
        undoRedoManager.redo()
        refreshUndoRedo()
    }
    
    companion object {
        private const val TAG = "SimpleSequencerViewModel"
    }
    
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
    
    // Timing variables
    private var timingJob: kotlinx.coroutines.Job? = null
    private var stepIntervalMs: Long = 125L // Default for 120 BPM, 16th notes
    
    init {
        // Initialize audio engine and load samples
        viewModelScope.launch {
            initializeAudioEngine()
        }
        
        // Update step interval when tempo changes
        viewModelScope.launch {
            patterns.collect { patternList ->
                val currentPattern = patternList.find { it.id == _sequencerState.value.currentPattern }
                currentPattern?.let { pattern ->
                    updateStepInterval(pattern.tempo)
                }
            }
        }
    }
    
    private suspend fun initializeAudioEngine() {
        try {
            Log.d(TAG, "Initializing audio engine for sequencer...")
            
            // Initialize the audio engine
            val initialized = audioEngine.initialize(44100, 256, true)
            if (!initialized) {
                Log.w(TAG, "Audio engine initialization failed")
                return
            }
            
            // Initialize drum engine
            val drumInitialized = audioEngine.initializeDrumEngine()
            Log.d(TAG, "Drum engine initialized: $drumInitialized")
            
            // Try to load test samples or create them
            val testSampleCreated = audioEngine.createAndTriggerTestSample()
            Log.d(TAG, "Test sample created: $testSampleCreated")
            
            // Load test sample for testing
            val testSampleLoaded = audioEngine.loadTestSample()
            Log.d(TAG, "Test sample loaded: $testSampleLoaded")
            
            Log.d(TAG, "Sequencer audio engine initialization completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sequencer audio engine: ${e.message}")
        }
    }
    
    private fun updateStepInterval(tempo: Float) {
        // Calculate milliseconds per 16th note
        // 60 seconds / tempo = seconds per beat
        // seconds per beat / 4 = seconds per 16th note
        // * 1000 = milliseconds per 16th note
        stepIntervalMs = ((60f / tempo / 4f) * 1000f).toLong()
    }
    
    private fun startSequencer() {
        stopSequencer() // Stop any existing timer
        
        timingJob = viewModelScope.launch {
            while (_sequencerState.value.isPlaying) {
                val currentState = _sequencerState.value
                val currentPattern = _patterns.value.find { it.id == currentState.currentPattern }
                
                if (currentPattern != null) {
                    // Trigger steps for current position
                    triggerStepsAtPosition(currentPattern, currentState.currentStep)
                    
                    // Advance to next step
                    val nextStep = (currentState.currentStep + 1) % currentPattern.length
                    _sequencerState.value = currentState.copy(currentStep = nextStep)
                }
                
                delay(stepIntervalMs)
            }
        }
    }
    
    private fun stopSequencer() {
        timingJob?.cancel()
        timingJob = null
    }
    
    private fun triggerStepsAtPosition(pattern: Pattern, stepPosition: Int) {
        // Get steps for this position
        pattern.steps.forEach { (padIndex, steps) ->
            val stepAtPosition = steps.find { it.position == stepPosition }
            if (stepAtPosition != null && stepAtPosition.isActive) {
                // Check if pad is muted or if solo is active
                val isMuted = _muteSoloState.value.mutedTracks.contains(padIndex)
                val hasSolo = _muteSoloState.value.soloedTracks.isNotEmpty()
                val isSoloed = _muteSoloState.value.soloedTracks.contains(padIndex)
                
                val shouldPlay = !isMuted && (!hasSolo || isSoloed)
                
                if (shouldPlay) {
                    triggerPad(padIndex, stepAtPosition.velocity)
                }
            }
        }
    }
    
    private fun triggerPad(padIndex: Int, velocity: Int) {
        viewModelScope.launch {
            try {
                // Trigger the pad through the audio engine
                val noteInstanceId = "seq_${System.currentTimeMillis()}_$padIndex"
                val trackId = "track_$padIndex"
                val padId = "pad_$padIndex"
                
                audioEngine.playPadSample(noteInstanceId, trackId, padId)
                Log.d(TAG, "Triggered pad $padIndex with velocity $velocity")
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering pad $padIndex: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSequencer()
    }
    
    // Transport actions
    fun handleTransportAction(action: TransportControlAction) {
        when (action) {
            is TransportControlAction.Play -> {
                _sequencerState.value = _sequencerState.value.copy(isPlaying = true)
                startSequencer()
                Log.d(TAG, "Sequencer started")
            }
            is TransportControlAction.Pause -> {
                _sequencerState.value = _sequencerState.value.copy(isPlaying = false)
                stopSequencer()
                Log.d(TAG, "Sequencer paused")
            }
            is TransportControlAction.Stop -> {
                _sequencerState.value = _sequencerState.value.copy(
                    isPlaying = false,
                    currentStep = 0
                )
                stopSequencer()
                Log.d(TAG, "Sequencer stopped")
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
                    updateStepInterval(action.tempo)
                    Log.d(TAG, "Tempo changed to ${action.tempo} BPM, step interval: ${stepIntervalMs}ms")
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
    fun toggleStep(padId: Int, stepIndex: Int) {
        val patternId = _sequencerState.value.currentPattern ?: return
        val command = object : Command {
            override fun execute() = applyToggleStep(patternId, padId, stepIndex)
            override fun undo()    = applyToggleStep(patternId, padId, stepIndex) // toggle is its own inverse
        }
        undoRedoManager.executeCommand(command)
        refreshUndoRedo()
    }

    private fun applyToggleStep(patternId: String, padId: Int, stepIndex: Int) {
        val currentPatterns = _patterns.value.toMutableList()
        val patternIndex = currentPatterns.indexOfFirst { it.id == patternId }
        if (patternIndex < 0) return

        val pattern = currentPatterns[patternIndex]
        val currentSteps = pattern.steps.toMutableMap()
        val padSteps = currentSteps[padId]?.toMutableList() ?: mutableListOf()

        val existingIdx = padSteps.indexOfFirst { it.position == stepIndex }
        if (existingIdx >= 0) {
            padSteps.removeAt(existingIdx)
            Log.d(TAG, "Removed step at pad $padId, position $stepIndex")
        } else {
            padSteps.add(Step(position = stepIndex, velocity = 100, isActive = true))
            Log.d(TAG, "Added step at pad $padId, position $stepIndex")
        }

        if (padSteps.isEmpty()) currentSteps.remove(padId) else currentSteps[padId] = padSteps
        currentPatterns[patternIndex] = pattern.copy(steps = currentSteps)
        _patterns.value = currentPatterns
    }

    fun setStepVelocity(padId: Int, stepIndex: Int, velocity: Int) {
        val patternId = _sequencerState.value.currentPattern ?: return
        val currentPatterns = _patterns.value
        val patternIndex = currentPatterns.indexOfFirst { it.id == patternId }
        if (patternIndex < 0) return

        val existingStep = currentPatterns[patternIndex].steps[padId]
            ?.firstOrNull { it.position == stepIndex } ?: return
        val oldVelocity = existingStep.velocity

        val command = object : Command {
            override fun execute() = applySetVelocity(patternId, padId, stepIndex, velocity)
            override fun undo()    = applySetVelocity(patternId, padId, stepIndex, oldVelocity)
        }
        undoRedoManager.executeCommand(command)
        refreshUndoRedo()
    }

    private fun applySetVelocity(patternId: String, padId: Int, stepIndex: Int, velocity: Int) {
        val currentPatterns = _patterns.value.toMutableList()
        val patternIndex = currentPatterns.indexOfFirst { it.id == patternId }
        if (patternIndex < 0) return

        val pattern = currentPatterns[patternIndex]
        val currentSteps = pattern.steps.toMutableMap()
        val padSteps = currentSteps[padId]?.toMutableList() ?: return

        val idx = padSteps.indexOfFirst { it.position == stepIndex }
        if (idx >= 0) {
            padSteps[idx] = padSteps[idx].copy(velocity = velocity)
            currentSteps[padId] = padSteps
            currentPatterns[patternIndex] = pattern.copy(steps = currentSteps)
            _patterns.value = currentPatterns
            Log.d(TAG, "Set velocity $velocity for pad $padId, step $stepIndex")
        }
    }
    
    // Manual pad triggering for testing
    fun triggerPadManually(padIndex: Int) {
        viewModelScope.launch {
            try {
                val noteInstanceId = "manual_${System.currentTimeMillis()}_$padIndex"
                val trackId = "track_$padIndex"
                val padId = "pad_$padIndex"
                
                val success = audioEngine.playPadSample(noteInstanceId, trackId, padId)
                Log.d(TAG, "Manual trigger pad $padIndex: success=$success")
                
                if (!success) {
                    // Try alternative methods
                    Log.d(TAG, "Trying alternative trigger methods for pad $padIndex")
                    
                    // Try drum pad trigger
                    audioEngine.triggerDrumPad(padIndex, 1.0f)
                    
                    // Try test pad sample
                    audioEngine.triggerTestPadSample(padIndex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error manually triggering pad $padIndex: ${e.message}")
            }
        }
    }
    
    // Test audio engine connectivity
    fun testAudioEngine() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Testing audio engine connectivity...")
                
                // Test basic connectivity
                val latency = audioEngine.getReportedLatencyMillis()
                Log.d(TAG, "Audio engine latency: ${latency}ms")
                
                // Test drum engine
                val drumInitialized = audioEngine.initializeDrumEngine()
                Log.d(TAG, "Drum engine initialized: $drumInitialized")
                
                // Test sample creation
                val testSampleCreated = audioEngine.createAndTriggerTestSample()
                Log.d(TAG, "Test sample created and triggered: $testSampleCreated")
                
                // Get drum engine state
                val loadedSamples = audioEngine.getDrumEngineLoadedSamples()
                Log.d(TAG, "Drum engine loaded samples: $loadedSamples")
                
                audioEngine.debugPrintDrumEngineState()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing audio engine: ${e.message}")
            }
        }
    }
    
    // Add some default patterns with steps for testing
    fun addTestPattern() {
        val testPattern = Pattern(
            id = "test_pattern",
            name = "Test Beat",
            length = 16,
            tempo = 120f,
            swing = 0f,
            steps = mapOf(
                0 to listOf( // Kick on 1, 5, 9, 13
                    Step(position = 0, velocity = 127),
                    Step(position = 4, velocity = 127),
                    Step(position = 8, velocity = 127),
                    Step(position = 12, velocity = 127)
                ),
                1 to listOf( // Snare on 4, 12
                    Step(position = 4, velocity = 110),
                    Step(position = 12, velocity = 110)
                ),
                2 to listOf( // Hi-hat on every step
                    Step(position = 0, velocity = 80),
                    Step(position = 1, velocity = 60),
                    Step(position = 2, velocity = 80),
                    Step(position = 3, velocity = 60),
                    Step(position = 4, velocity = 80),
                    Step(position = 5, velocity = 60),
                    Step(position = 6, velocity = 80),
                    Step(position = 7, velocity = 60),
                    Step(position = 8, velocity = 80),
                    Step(position = 9, velocity = 60),
                    Step(position = 10, velocity = 80),
                    Step(position = 11, velocity = 60),
                    Step(position = 12, velocity = 80),
                    Step(position = 13, velocity = 60),
                    Step(position = 14, velocity = 80),
                    Step(position = 15, velocity = 60)
                )
            )
        )
        
        _patterns.value = _patterns.value + testPattern
        Log.d(TAG, "Added test pattern with basic drum beat")
    }
}