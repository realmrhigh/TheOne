package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time pattern recording engine with quantization and overdub support
 * Captures pad hits during playback and converts them to pattern steps
 */
@Singleton
class RecordingEngine @Inject constructor(
    private val timingCalculator: TimingCalculator,
    private val overdubManager: OverdubManager
) {
    
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val recordingMutex = Mutex()
    private var currentPattern: Pattern? = null
    private var recordingStartTime: Long = 0L
    private var patternStartTime: Long = 0L
    
    /**
     * Start recording mode for the given pattern
     */
    suspend fun startRecording(
        pattern: Pattern,
        mode: RecordingMode = RecordingMode.REPLACE,
        quantization: Quantization = Quantization.SIXTEENTH,
        selectedPads: Set<Int> = emptySet()
    ) = recordingMutex.withLock {
        currentPattern = pattern
        recordingStartTime = System.nanoTime() / 1000 // Convert to microseconds
        patternStartTime = recordingStartTime
        
        _recordingState.update { 
            it.copy(
                isRecording = true,
                mode = mode,
                quantization = quantization,
                selectedPads = selectedPads,
                recordedHits = emptyList(),
                startTime = recordingStartTime
            )
        }
    }
    
    /**
     * Stop recording and return the updated pattern
     */
    suspend fun stopRecording(): Pattern? = recordingMutex.withLock {
        val pattern = currentPattern ?: return null
        val state = _recordingState.value
        
        if (!state.isRecording) return pattern
        
        // Apply recorded hits to pattern based on recording mode
        val updatedPattern = when (state.mode) {
            RecordingMode.REPLACE -> applyRecordedHitsReplace(pattern, state.recordedHits, state.quantization)
            RecordingMode.OVERDUB -> applyRecordedHitsOverdub(pattern, state.recordedHits, state.quantization)
            RecordingMode.PUNCH_IN -> applyRecordedHitsPunchIn(pattern, state.recordedHits, state.quantization)
        }
        
        _recordingState.update { 
            RecordingState() // Reset to default state
        }
        
        currentPattern = null
        updatedPattern
    }
    
    /**
     * Record a pad hit during playback
     */
    suspend fun recordPadHit(
        padIndex: Int,
        velocity: Int,
        timestamp: Long,
        currentStep: Int,
        stepProgress: Float,
        tempo: Float,
        swing: Float
    ) = recordingMutex.withLock {
        val state = _recordingState.value
        val pattern = currentPattern
        
        if (!state.isRecording || pattern == null) return
        
        // Check overdub manager for recording eligibility
        if (!overdubManager.shouldRecordHit(padIndex, currentStep, stepProgress)) {
            return
        }
        
        // Check if this pad is selected for recording (if selection is active)
        if (state.selectedPads.isNotEmpty() && !state.selectedPads.contains(padIndex)) {
            return
        }
        
        // Calculate relative timestamp from recording start
        val relativeTimestamp = timestamp - recordingStartTime
        
        // Create recorded hit with timing information
        val recordedHit = RecordedHit(
            padIndex = padIndex,
            velocity = velocity,
            timestamp = relativeTimestamp,
            currentStep = currentStep,
            stepProgress = stepProgress,
            tempo = tempo,
            swing = swing
        )
        
        _recordingState.update { 
            it.copy(recordedHits = it.recordedHits + recordedHit)
        }
    }
    
    /**
     * Update pattern start time when playback position changes
     */
    fun updatePatternStartTime(newStartTime: Long) {
        patternStartTime = newStartTime
    }
    
    /**
     * Clear recorded hits without stopping recording
     */
    suspend fun clearRecordedHits() = recordingMutex.withLock {
        _recordingState.update { 
            it.copy(recordedHits = emptyList())
        }
    }
    
    /**
     * Apply recorded hits using replace mode (clear existing steps first)
     */
    private fun applyRecordedHitsReplace(
        pattern: Pattern,
        recordedHits: List<RecordedHit>,
        quantization: Quantization
    ): Pattern {
        val state = _recordingState.value
        
        // Clear existing steps for selected pads
        val clearedSteps = if (state.selectedPads.isNotEmpty()) {
            pattern.steps.filterKeys { !state.selectedPads.contains(it) }
        } else {
            emptyMap()
        }
        
        // Convert recorded hits to steps and add to pattern
        val newSteps = convertRecordedHitsToSteps(recordedHits, quantization, pattern.length)
        val mergedSteps = mergeStepMaps(clearedSteps, newSteps)
        
        return pattern.copy(
            steps = mergedSteps,
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Apply recorded hits using overdub mode (add to existing steps)
     */
    private suspend fun applyRecordedHitsOverdub(
        pattern: Pattern,
        recordedHits: List<RecordedHit>,
        quantization: Quantization
    ): Pattern {
        // Convert recorded hits to steps
        val newSteps = convertRecordedHitsToSteps(recordedHits, quantization, pattern.length)
        
        // Use overdub manager for sophisticated merging
        return overdubManager.mergeWithOverdub(pattern, newSteps)
    }
    
    /**
     * Apply recorded hits using punch-in mode (replace only during recording period)
     */
    private fun applyRecordedHitsPunchIn(
        pattern: Pattern,
        recordedHits: List<RecordedHit>,
        quantization: Quantization
    ): Pattern {
        // For punch-in, we need to determine which steps were recorded over
        // and only replace those specific steps
        val newSteps = convertRecordedHitsToSteps(recordedHits, quantization, pattern.length)
        
        // Get the range of steps that were recorded
        val recordedStepPositions = newSteps.values.flatten().map { it.position }.toSet()
        
        // Remove existing steps in the recorded range for affected pads
        val filteredExistingSteps = pattern.steps.mapValues { (padIndex, steps) ->
            if (newSteps.containsKey(padIndex)) {
                steps.filterNot { recordedStepPositions.contains(it.position) }
            } else {
                steps
            }
        }
        
        val mergedSteps = mergeStepMaps(filteredExistingSteps, newSteps)
        
        return pattern.copy(
            steps = mergedSteps,
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Convert recorded hits to quantized steps
     */
    private fun convertRecordedHitsToSteps(
        recordedHits: List<RecordedHit>,
        quantization: Quantization,
        patternLength: Int
    ): Map<Int, List<Step>> {
        if (quantization == Quantization.OFF) {
            // No quantization - use exact timing
            return convertHitsWithMicroTiming(recordedHits, patternLength)
        }
        
        // Group hits by pad and quantize to nearest step
        return recordedHits
            .groupBy { it.padIndex }
            .mapValues { (_, hits) ->
                hits.mapNotNull { hit ->
                    val quantizedStep = quantizeHitToStep(hit, quantization, patternLength)
                    quantizedStep?.let { stepPosition ->
                        Step(
                            position = stepPosition,
                            velocity = hit.velocity,
                            isActive = true,
                            microTiming = calculateMicroTiming(hit, stepPosition, quantization)
                        )
                    }
                }.distinctBy { it.position } // Remove duplicate steps at same position
            }
    }
    
    /**
     * Convert hits with micro-timing (no quantization)
     */
    private fun convertHitsWithMicroTiming(
        recordedHits: List<RecordedHit>,
        patternLength: Int
    ): Map<Int, List<Step>> {
        return recordedHits
            .groupBy { it.padIndex }
            .mapValues { (_, hits) ->
                hits.map { hit ->
                    val exactStepPosition = (hit.currentStep + hit.stepProgress).toInt()
                    val microTiming = ((hit.currentStep + hit.stepProgress) - exactStepPosition) * 
                                    timingCalculator.calculateStepDuration(hit.tempo).toFloat()
                    
                    Step(
                        position = exactStepPosition.coerceIn(0, patternLength - 1),
                        velocity = hit.velocity,
                        isActive = true,
                        microTiming = microTiming.coerceIn(-50f, 50f)
                    )
                }
            }
    }
    
    /**
     * Quantize a recorded hit to the nearest step position
     */
    private fun quantizeHitToStep(
        hit: RecordedHit,
        quantization: Quantization,
        patternLength: Int
    ): Int? {
        val subdivisionSteps = patternLength / (quantization.subdivision / 4)
        if (subdivisionSteps <= 0) return null
        
        val exactPosition = hit.currentStep + hit.stepProgress
        val quantizedPosition = (exactPosition / subdivisionSteps).toInt() * subdivisionSteps
        
        return quantizedPosition.coerceIn(0, patternLength - 1)
    }
    
    /**
     * Calculate micro-timing offset from quantized position
     */
    private fun calculateMicroTiming(
        hit: RecordedHit,
        quantizedStep: Int,
        quantization: Quantization
    ): Float {
        val exactPosition = hit.currentStep + hit.stepProgress
        val timingOffset = (exactPosition - quantizedStep) * 
                          timingCalculator.calculateStepDuration(hit.tempo).toFloat()
        
        return timingOffset.coerceIn(-50f, 50f)
    }
    
    /**
     * Merge two step maps, combining steps for the same pad
     */
    private fun mergeStepMaps(
        existing: Map<Int, List<Step>>,
        new: Map<Int, List<Step>>
    ): Map<Int, List<Step>> {
        val result = existing.toMutableMap()
        
        new.forEach { (padIndex, newSteps) ->
            val existingSteps = result[padIndex] ?: emptyList()
            result[padIndex] = (existingSteps + newSteps)
                .distinctBy { it.position } // Remove duplicates
                .sortedBy { it.position }
        }
        
        return result.filterValues { it.isNotEmpty() }
    }
}

/**
 * Current state of the recording engine
 */
data class RecordingState(
    val isRecording: Boolean = false,
    val mode: RecordingMode = RecordingMode.REPLACE,
    val quantization: Quantization = Quantization.SIXTEENTH,
    val selectedPads: Set<Int> = emptySet(),
    val recordedHits: List<RecordedHit> = emptyList(),
    val startTime: Long = 0L
) {
    /**
     * Returns the number of hits recorded for a specific pad
     */
    fun getHitCountForPad(padIndex: Int): Int {
        return recordedHits.count { it.padIndex == padIndex }
    }
    
    /**
     * Returns the total number of recorded hits
     */
    fun getTotalHitCount(): Int = recordedHits.size
    
    /**
     * Returns true if any hits have been recorded
     */
    fun hasRecordedHits(): Boolean = recordedHits.isNotEmpty()
}

/**
 * Represents a single recorded pad hit with timing information
 */
data class RecordedHit(
    val padIndex: Int,
    val velocity: Int,
    val timestamp: Long, // Microseconds from recording start
    val currentStep: Int,
    val stepProgress: Float, // 0.0 to 1.0 within current step
    val tempo: Float,
    val swing: Float
) {
    init {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(velocity in 1..127) { "Velocity must be between 1 and 127" }
        require(stepProgress in 0f..1f) { "Step progress must be between 0.0 and 1.0" }
        require(tempo > 0) { "Tempo must be positive" }
        require(swing in 0f..0.75f) { "Swing must be between 0.0 and 0.75" }
    }
}