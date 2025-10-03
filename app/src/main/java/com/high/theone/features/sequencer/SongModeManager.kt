package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages song mode functionality including pattern sequencing, transitions,
 * and song playback state.
 */
@Singleton
class SongModeManager @Inject constructor(
    private val timingEngine: TimingEngine
) {
    
    private val _songState = MutableStateFlow(SongPlaybackState())
    val songState: StateFlow<SongPlaybackState> = _songState.asStateFlow()
    
    private var currentPatternRepeatCount = 0
    private var patternTransitionCallback: ((String) -> Unit)? = null
    
    /**
     * Initialize song mode with a song sequence
     */
    fun initializeSong(songMode: SongMode, onPatternTransition: (String) -> Unit) {
        patternTransitionCallback = onPatternTransition
        
        _songState.update { 
            it.copy(
                songMode = songMode,
                currentSequencePosition = 0,
                currentPatternRepeatCount = 0,
                isActive = songMode.isActive
            )
        }
        
        // Trigger initial pattern if song is active
        if (songMode.isActive && songMode.sequence.isNotEmpty()) {
            val firstStep = songMode.sequence.first()
            patternTransitionCallback?.invoke(firstStep.patternId)
        }
    }
    
    /**
     * Start song playback
     */
    fun startSong() {
        val currentSong = _songState.value.songMode ?: return
        
        _songState.update { 
            it.copy(
                isActive = true,
                isPlaying = true
            )
        }
        
        // Setup pattern end callback to handle transitions
        setupPatternEndCallback()
    }
    
    /**
     * Stop song playback and return to beginning
     */
    fun stopSong() {
        _songState.update { 
            it.copy(
                isPlaying = false,
                currentSequencePosition = 0,
                currentPatternRepeatCount = 0
            )
        }
        
        // Trigger first pattern if song exists
        val currentSong = _songState.value.songMode
        if (currentSong != null && currentSong.sequence.isNotEmpty()) {
            val firstStep = currentSong.sequence.first()
            patternTransitionCallback?.invoke(firstStep.patternId)
        }
    }
    
    /**
     * Pause song playback
     */
    fun pauseSong() {
        _songState.update { 
            it.copy(isPlaying = false)
        }
    }
    
    /**
     * Resume song playback
     */
    fun resumeSong() {
        _songState.update { 
            it.copy(isPlaying = true)
        }
    }
    
    /**
     * Navigate to specific position in song
     */
    fun navigateToPosition(sequencePosition: Int, patternRepeat: Int = 0) {
        val currentSong = _songState.value.songMode ?: return
        
        if (sequencePosition >= 0 && sequencePosition < currentSong.sequence.size) {
            val songStep = currentSong.sequence[sequencePosition]
            val clampedRepeat = patternRepeat.coerceIn(0, songStep.repeatCount - 1)
            
            _songState.update { 
                it.copy(
                    currentSequencePosition = sequencePosition,
                    currentPatternRepeatCount = clampedRepeat
                )
            }
            
            // Trigger pattern transition
            patternTransitionCallback?.invoke(songStep.patternId)
        }
    }
    
    /**
     * Move to next pattern in sequence
     */
    fun nextPattern() {
        val state = _songState.value
        val currentSong = state.songMode ?: return
        
        if (currentSong.sequence.isEmpty()) return
        
        val currentStep = currentSong.sequence[state.currentSequencePosition]
        val nextRepeatCount = state.currentPatternRepeatCount + 1
        
        if (nextRepeatCount < currentStep.repeatCount) {
            // Stay on same pattern, increment repeat count
            _songState.update { 
                it.copy(currentPatternRepeatCount = nextRepeatCount)
            }
        } else {
            // Move to next pattern in sequence
            val nextPosition = state.currentSequencePosition + 1
            
            if (nextPosition < currentSong.sequence.size) {
                // Move to next pattern
                val nextStep = currentSong.sequence[nextPosition]
                _songState.update { 
                    it.copy(
                        currentSequencePosition = nextPosition,
                        currentPatternRepeatCount = 0
                    )
                }
                patternTransitionCallback?.invoke(nextStep.patternId)
            } else if (currentSong.loopEnabled) {
                // Loop back to beginning
                val firstStep = currentSong.sequence.first()
                _songState.update { 
                    it.copy(
                        currentSequencePosition = 0,
                        currentPatternRepeatCount = 0
                    )
                }
                patternTransitionCallback?.invoke(firstStep.patternId)
            } else {
                // End of song, stop playback
                stopSong()
            }
        }
    }
    
    /**
     * Move to previous pattern in sequence
     */
    fun previousPattern() {
        val state = _songState.value
        val currentSong = state.songMode ?: return
        
        if (currentSong.sequence.isEmpty()) return
        
        if (state.currentPatternRepeatCount > 0) {
            // Go to previous repeat of current pattern
            _songState.update { 
                it.copy(currentPatternRepeatCount = state.currentPatternRepeatCount - 1)
            }
        } else {
            // Move to previous pattern in sequence
            val prevPosition = if (state.currentSequencePosition > 0) {
                state.currentSequencePosition - 1
            } else if (currentSong.loopEnabled) {
                currentSong.sequence.size - 1
            } else {
                return // Can't go back further
            }
            
            val prevStep = currentSong.sequence[prevPosition]
            _songState.update { 
                it.copy(
                    currentSequencePosition = prevPosition,
                    currentPatternRepeatCount = prevStep.repeatCount - 1
                )
            }
            patternTransitionCallback?.invoke(prevStep.patternId)
        }
    }
    
    /**
     * Toggle song loop mode
     */
    fun toggleLoop() {
        _songState.update { state ->
            val updatedSong = state.songMode?.copy(loopEnabled = !state.songMode.loopEnabled)
            state.copy(songMode = updatedSong)
        }
    }
    
    /**
     * Update song sequence
     */
    fun updateSongSequence(sequence: List<SongStep>) {
        _songState.update { state ->
            val updatedSong = state.songMode?.copy(sequence = sequence) ?: SongMode(sequence = sequence)
            state.copy(
                songMode = updatedSong,
                currentSequencePosition = 0,
                currentPatternRepeatCount = 0
            )
        }
        
        // Trigger first pattern if sequence is not empty
        if (sequence.isNotEmpty()) {
            patternTransitionCallback?.invoke(sequence.first().patternId)
        }
    }
    
    /**
     * Get current song step
     */
    fun getCurrentSongStep(): SongStep? {
        val state = _songState.value
        val currentSong = state.songMode ?: return null
        return currentSong.sequence.getOrNull(state.currentSequencePosition)
    }
    
    /**
     * Get next song step
     */
    fun getNextSongStep(): SongStep? {
        val state = _songState.value
        val currentSong = state.songMode ?: return null
        
        val currentStep = currentSong.sequence.getOrNull(state.currentSequencePosition) ?: return null
        val nextRepeatCount = state.currentPatternRepeatCount + 1
        
        return if (nextRepeatCount < currentStep.repeatCount) {
            // Next repeat of current pattern
            currentStep
        } else {
            // Next pattern in sequence
            val nextPosition = state.currentSequencePosition + 1
            if (nextPosition < currentSong.sequence.size) {
                currentSong.sequence[nextPosition]
            } else if (currentSong.loopEnabled) {
                currentSong.sequence.firstOrNull()
            } else {
                null
            }
        }
    }
    
    /**
     * Get song progress as percentage (0.0 to 1.0)
     */
    fun getSongProgress(): Float {
        val state = _songState.value
        val currentSong = state.songMode ?: return 0f
        
        if (currentSong.sequence.isEmpty()) return 0f
        
        val totalSteps = currentSong.getTotalSteps()
        if (totalSteps == 0) return 0f
        
        // Calculate completed steps
        var completedSteps = 0
        for (i in 0 until state.currentSequencePosition) {
            completedSteps += currentSong.sequence[i].repeatCount
        }
        completedSteps += state.currentPatternRepeatCount
        
        return completedSteps.toFloat() / totalSteps
    }
    
    /**
     * Get remaining time in current pattern repeat
     */
    fun getRemainingPatternTime(currentPattern: Pattern?, currentStep: Int): Long {
        val pattern = currentPattern ?: return 0L
        val remainingSteps = pattern.length - currentStep
        val stepDurationMs = (60000f / pattern.tempo / 4f).toLong() // 16th note duration
        return remainingSteps * stepDurationMs
    }
    
    /**
     * Setup callback for pattern end detection
     */
    private fun setupPatternEndCallback() {
        // This would be called by the timing engine when a pattern completes
        // For now, this is a placeholder for the integration point
    }
    
    /**
     * Handle pattern completion (called by timing engine)
     */
    fun onPatternComplete() {
        val state = _songState.value
        if (state.isActive && state.isPlaying) {
            nextPattern()
        }
    }
    
    /**
     * Activate song mode
     */
    fun activateSongMode() {
        _songState.update { 
            it.copy(isActive = true)
        }
    }
    
    /**
     * Deactivate song mode (return to pattern mode)
     */
    fun deactivateSongMode() {
        _songState.update { 
            it.copy(
                isActive = false,
                isPlaying = false
            )
        }
    }
}

/**
 * State for song mode playback
 */
data class SongPlaybackState(
    val songMode: SongMode? = null,
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSequencePosition: Int = 0,
    val currentPatternRepeatCount: Int = 0
) {
    /**
     * Get current absolute position in song (total pattern repeats completed)
     */
    fun getAbsolutePosition(): Int {
        val song = songMode ?: return 0
        var position = 0
        
        for (i in 0 until currentSequencePosition) {
            position += song.sequence[i].repeatCount
        }
        position += currentPatternRepeatCount
        
        return position
    }
    
    /**
     * Get total song length in pattern repeats
     */
    fun getTotalLength(): Int {
        return songMode?.getTotalSteps() ?: 0
    }
    
    /**
     * Check if at end of song
     */
    fun isAtEnd(): Boolean {
        val song = songMode ?: return true
        if (song.sequence.isEmpty()) return true
        
        val isLastPattern = currentSequencePosition == song.sequence.size - 1
        val currentStep = song.sequence[currentSequencePosition]
        val isLastRepeat = currentPatternRepeatCount == currentStep.repeatCount - 1
        
        return isLastPattern && isLastRepeat
    }
}