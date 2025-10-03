package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced song playback engine with smooth transitions and enhanced features
 */
@Singleton
class SongPlaybackEngine @Inject constructor(
    private val timingEngine: TimingEngine,
    private val patternManager: PatternManager
) {
    
    private val _playbackState = MutableStateFlow(SongPlaybackEngineState())
    val playbackState: StateFlow<SongPlaybackEngineState> = _playbackState.asStateFlow()
    
    private var transitionCallback: ((PatternTransition) -> Unit)? = null
    private var patternCompleteCallback: (() -> Unit)? = null
    
    /**
     * Initialize playback engine with song and callbacks
     */
    fun initialize(
        songMode: SongMode,
        onPatternTransition: (PatternTransition) -> Unit,
        onPatternComplete: () -> Unit
    ) {
        transitionCallback = onPatternTransition
        patternCompleteCallback = onPatternComplete
        
        _playbackState.update { 
            it.copy(
                songMode = songMode,
                currentSequencePosition = 0,
                currentPatternRepeat = 0,
                isInitialized = true
            )
        }
    }
    
    /**
     * Start song playback with smooth transitions
     */
    fun startPlayback(startPosition: Int = 0, startRepeat: Int = 0) {
        val state = _playbackState.value
        val songMode = state.songMode ?: return
        
        if (songMode.sequence.isEmpty()) return
        
        // Navigate to start position
        navigateToPosition(startPosition, startRepeat)
        
        _playbackState.update { 
            it.copy(
                isPlaying = true,
                isPaused = false,
                playbackStartTime = System.currentTimeMillis()
            )
        }
        
        // Start pattern playback
        val currentStep = songMode.sequence[startPosition]
        triggerPatternTransition(
            PatternTransition(
                fromPatternId = null,
                toPatternId = currentStep.patternId,
                transitionType = TransitionType.START,
                sequencePosition = startPosition,
                patternRepeat = startRepeat
            )
        )
    }
    
    /**
     * Stop playback and return to beginning
     */
    fun stopPlayback() {
        _playbackState.update { 
            it.copy(
                isPlaying = false,
                isPaused = false,
                currentSequencePosition = 0,
                currentPatternRepeat = 0,
                playbackStartTime = null
            )
        }
        
        // Trigger stop transition
        val state = _playbackState.value
        val songMode = state.songMode
        if (songMode != null && songMode.sequence.isNotEmpty()) {
            val firstStep = songMode.sequence.first()
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = null,
                    toPatternId = firstStep.patternId,
                    transitionType = TransitionType.STOP,
                    sequencePosition = 0,
                    patternRepeat = 0
                )
            )
        }
    }
    
    /**
     * Pause playback at current position
     */
    fun pausePlayback() {
        _playbackState.update { 
            it.copy(
                isPlaying = false,
                isPaused = true
            )
        }
    }
    
    /**
     * Resume playback from paused position
     */
    fun resumePlayback() {
        val state = _playbackState.value
        if (!state.isPaused) return
        
        _playbackState.update { 
            it.copy(
                isPlaying = true,
                isPaused = false
            )
        }
        
        // Resume current pattern
        val songMode = state.songMode ?: return
        if (state.currentSequencePosition < songMode.sequence.size) {
            val currentStep = songMode.sequence[state.currentSequencePosition]
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = null,
                    toPatternId = currentStep.patternId,
                    transitionType = TransitionType.RESUME,
                    sequencePosition = state.currentSequencePosition,
                    patternRepeat = state.currentPatternRepeat
                )
            )
        }
    }
    
    /**
     * Navigate to specific position in song
     */
    fun navigateToPosition(sequencePosition: Int, patternRepeat: Int = 0) {
        val state = _playbackState.value
        val songMode = state.songMode ?: return
        
        if (sequencePosition >= 0 && sequencePosition < songMode.sequence.size) {
            val songStep = songMode.sequence[sequencePosition]
            val clampedRepeat = patternRepeat.coerceIn(0, songStep.repeatCount - 1)
            
            val wasPlaying = state.isPlaying
            val previousPatternId = if (state.currentSequencePosition < songMode.sequence.size) {
                songMode.sequence[state.currentSequencePosition].patternId
            } else null
            
            _playbackState.update { 
                it.copy(
                    currentSequencePosition = sequencePosition,
                    currentPatternRepeat = clampedRepeat
                )
            }
            
            // Trigger navigation transition
            if (wasPlaying || previousPatternId != songStep.patternId) {
                triggerPatternTransition(
                    PatternTransition(
                        fromPatternId = previousPatternId,
                        toPatternId = songStep.patternId,
                        transitionType = if (wasPlaying) TransitionType.NAVIGATE else TransitionType.SELECT,
                        sequencePosition = sequencePosition,
                        patternRepeat = clampedRepeat
                    )
                )
            }
        }
    }
    
    /**
     * Advance to next pattern in sequence (called by pattern completion)
     */
    fun advanceToNextPattern() {
        val state = _playbackState.value
        val songMode = state.songMode ?: return
        
        if (!state.isPlaying || songMode.sequence.isEmpty()) return
        
        val currentStep = songMode.sequence[state.currentSequencePosition]
        val nextRepeat = state.currentPatternRepeat + 1
        
        if (nextRepeat < currentStep.repeatCount) {
            // Stay on same pattern, increment repeat
            _playbackState.update { 
                it.copy(currentPatternRepeat = nextRepeat)
            }
            
            // Trigger repeat transition
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = currentStep.patternId,
                    toPatternId = currentStep.patternId,
                    transitionType = TransitionType.REPEAT,
                    sequencePosition = state.currentSequencePosition,
                    patternRepeat = nextRepeat
                )
            )
        } else {
            // Move to next pattern
            val nextPosition = state.currentSequencePosition + 1
            
            if (nextPosition < songMode.sequence.size) {
                // Move to next pattern in sequence
                val nextStep = songMode.sequence[nextPosition]
                
                _playbackState.update { 
                    it.copy(
                        currentSequencePosition = nextPosition,
                        currentPatternRepeat = 0
                    )
                }
                
                triggerPatternTransition(
                    PatternTransition(
                        fromPatternId = currentStep.patternId,
                        toPatternId = nextStep.patternId,
                        transitionType = TransitionType.NEXT_PATTERN,
                        sequencePosition = nextPosition,
                        patternRepeat = 0
                    )
                )
            } else if (songMode.loopEnabled) {
                // Loop back to beginning
                val firstStep = songMode.sequence.first()
                
                _playbackState.update { 
                    it.copy(
                        currentSequencePosition = 0,
                        currentPatternRepeat = 0
                    )
                }
                
                triggerPatternTransition(
                    PatternTransition(
                        fromPatternId = currentStep.patternId,
                        toPatternId = firstStep.patternId,
                        transitionType = TransitionType.LOOP,
                        sequencePosition = 0,
                        patternRepeat = 0
                    )
                )
            } else {
                // End of song, stop playback
                stopPlayback()
            }
        }
    }
    
    /**
     * Move to previous pattern in sequence
     */
    fun moveToPreviousPattern() {
        val state = _playbackState.value
        val songMode = state.songMode ?: return
        
        if (songMode.sequence.isEmpty()) return
        
        val currentStep = songMode.sequence[state.currentSequencePosition]
        
        if (state.currentPatternRepeat > 0) {
            // Go to previous repeat of current pattern
            val prevRepeat = state.currentPatternRepeat - 1
            
            _playbackState.update { 
                it.copy(currentPatternRepeat = prevRepeat)
            }
            
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = currentStep.patternId,
                    toPatternId = currentStep.patternId,
                    transitionType = TransitionType.PREVIOUS_REPEAT,
                    sequencePosition = state.currentSequencePosition,
                    patternRepeat = prevRepeat
                )
            )
        } else {
            // Move to previous pattern in sequence
            val prevPosition = if (state.currentSequencePosition > 0) {
                state.currentSequencePosition - 1
            } else if (songMode.loopEnabled) {
                songMode.sequence.size - 1
            } else {
                return // Can't go back further
            }
            
            val prevStep = songMode.sequence[prevPosition]
            val lastRepeat = prevStep.repeatCount - 1
            
            _playbackState.update { 
                it.copy(
                    currentSequencePosition = prevPosition,
                    currentPatternRepeat = lastRepeat
                )
            }
            
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = currentStep.patternId,
                    toPatternId = prevStep.patternId,
                    transitionType = TransitionType.PREVIOUS_PATTERN,
                    sequencePosition = prevPosition,
                    patternRepeat = lastRepeat
                )
            )
        }
    }
    
    /**
     * Toggle loop mode
     */
    fun toggleLoop() {
        _playbackState.update { state ->
            val updatedSong = state.songMode?.copy(loopEnabled = !state.songMode.loopEnabled)
            state.copy(songMode = updatedSong)
        }
    }
    
    /**
     * Update song sequence
     */
    fun updateSongSequence(sequence: List<SongStep>) {
        _playbackState.update { state ->
            val updatedSong = state.songMode?.copy(sequence = sequence) ?: SongMode(sequence = sequence)
            state.copy(
                songMode = updatedSong,
                currentSequencePosition = 0,
                currentPatternRepeat = 0
            )
        }
        
        // If playing, restart from beginning
        val state = _playbackState.value
        if (state.isPlaying && sequence.isNotEmpty()) {
            val firstStep = sequence.first()
            triggerPatternTransition(
                PatternTransition(
                    fromPatternId = null,
                    toPatternId = firstStep.patternId,
                    transitionType = TransitionType.SEQUENCE_UPDATE,
                    sequencePosition = 0,
                    patternRepeat = 0
                )
            )
        }
    }
    
    /**
     * Get current song progress (0.0 to 1.0)
     */
    fun getSongProgress(): Float {
        val state = _playbackState.value
        val songMode = state.songMode ?: return 0f
        
        if (songMode.sequence.isEmpty()) return 0f
        
        val totalSteps = songMode.getTotalSteps()
        if (totalSteps == 0) return 0f
        
        // Calculate completed steps
        var completedSteps = 0
        for (i in 0 until state.currentSequencePosition) {
            completedSteps += songMode.sequence[i].repeatCount
        }
        completedSteps += state.currentPatternRepeat
        
        return completedSteps.toFloat() / totalSteps
    }
    
    /**
     * Get estimated remaining time in milliseconds
     */
    fun getRemainingTime(currentTempo: Float): Long {
        val state = _playbackState.value
        val songMode = state.songMode ?: return 0L
        
        if (songMode.sequence.isEmpty()) return 0L
        
        // Calculate remaining pattern repeats
        var remainingRepeats = 0
        
        // Current pattern remaining repeats
        val currentStep = songMode.sequence.getOrNull(state.currentSequencePosition)
        if (currentStep != null) {
            remainingRepeats += currentStep.repeatCount - state.currentPatternRepeat - 1
        }
        
        // Remaining patterns
        for (i in (state.currentSequencePosition + 1) until songMode.sequence.size) {
            remainingRepeats += songMode.sequence[i].repeatCount
        }
        
        // Estimate time (assuming 16 steps per pattern)
        val stepsPerPattern = 16
        val stepDurationMs = (60000f / currentTempo / 4f) // 16th note duration
        
        return (remainingRepeats * stepsPerPattern * stepDurationMs).toLong()
    }
    
    /**
     * Get current pattern info
     */
    fun getCurrentPatternInfo(): CurrentPatternInfo? {
        val state = _playbackState.value
        val songMode = state.songMode ?: return null
        
        if (state.currentSequencePosition >= songMode.sequence.size) return null
        
        val songStep = songMode.sequence[state.currentSequencePosition]
        return CurrentPatternInfo(
            patternId = songStep.patternId,
            sequencePosition = state.currentSequencePosition,
            currentRepeat = state.currentPatternRepeat + 1,
            totalRepeats = songStep.repeatCount,
            isLastPattern = state.currentSequencePosition == songMode.sequence.size - 1,
            isLastRepeat = state.currentPatternRepeat == songStep.repeatCount - 1
        )
    }
    
    /**
     * Trigger pattern transition with smooth handling
     */
    private fun triggerPatternTransition(transition: PatternTransition) {
        transitionCallback?.invoke(transition)
        
        // Update transition state
        _playbackState.update { 
            it.copy(
                lastTransition = transition,
                transitionTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Check if at end of song
     */
    fun isAtEndOfSong(): Boolean {
        val state = _playbackState.value
        val songMode = state.songMode ?: return true
        
        if (songMode.sequence.isEmpty()) return true
        
        val isLastPattern = state.currentSequencePosition == songMode.sequence.size - 1
        val currentStep = songMode.sequence[state.currentSequencePosition]
        val isLastRepeat = state.currentPatternRepeat == currentStep.repeatCount - 1
        
        return isLastPattern && isLastRepeat && !songMode.loopEnabled
    }
    
    /**
     * Reset playback engine
     */
    fun reset() {
        _playbackState.update { 
            SongPlaybackEngineState()
        }
        transitionCallback = null
        patternCompleteCallback = null
    }
}

/**
 * State for song playback engine
 */
data class SongPlaybackEngineState(
    val songMode: SongMode? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentSequencePosition: Int = 0,
    val currentPatternRepeat: Int = 0,
    val isInitialized: Boolean = false,
    val playbackStartTime: Long? = null,
    val lastTransition: PatternTransition? = null,
    val transitionTime: Long? = null
)

/**
 * Represents a pattern transition in song playback
 */
data class PatternTransition(
    val fromPatternId: String?,
    val toPatternId: String,
    val transitionType: TransitionType,
    val sequencePosition: Int,
    val patternRepeat: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of pattern transitions
 */
enum class TransitionType {
    START,              // Starting song playback
    STOP,               // Stopping song playback
    PAUSE,              // Pausing playback
    RESUME,             // Resuming playback
    NEXT_PATTERN,       // Moving to next pattern
    PREVIOUS_PATTERN,   // Moving to previous pattern
    REPEAT,             // Repeating current pattern
    PREVIOUS_REPEAT,    // Going to previous repeat
    LOOP,               // Looping back to beginning
    NAVIGATE,           // Manual navigation during playback
    SELECT,             // Selecting pattern while stopped
    SEQUENCE_UPDATE     // Song sequence was updated
}

/**
 * Information about current pattern in playback
 */
data class CurrentPatternInfo(
    val patternId: String,
    val sequencePosition: Int,
    val currentRepeat: Int,
    val totalRepeats: Int,
    val isLastPattern: Boolean,
    val isLastRepeat: Boolean
)