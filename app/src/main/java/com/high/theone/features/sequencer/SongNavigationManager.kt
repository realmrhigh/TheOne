package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages song navigation, position tracking, and timeline scrubbing
 */
@Singleton
class SongNavigationManager @Inject constructor() {
    
    private val _navigationState = MutableStateFlow(SongNavigationState())
    val navigationState: StateFlow<SongNavigationState> = _navigationState.asStateFlow()
    
    /**
     * Initialize navigation with song mode
     */
    fun initializeNavigation(songMode: SongMode) {
        _navigationState.update { 
            it.copy(
                songMode = songMode,
                timeline = buildTimeline(songMode),
                currentPosition = SongPosition(0, 0),
                totalDuration = calculateTotalDuration(songMode)
            )
        }
    }
    
    /**
     * Update current playback position
     */
    fun updatePosition(sequencePosition: Int, patternRepeat: Int, stepInPattern: Int) {
        val state = _navigationState.value
        val songMode = state.songMode ?: return
        
        if (sequencePosition >= 0 && sequencePosition < songMode.sequence.size) {
            val songStep = songMode.sequence[sequencePosition]
            val clampedRepeat = patternRepeat.coerceIn(0, songStep.repeatCount - 1)
            
            _navigationState.update { 
                it.copy(
                    currentPosition = SongPosition(sequencePosition, clampedRepeat, stepInPattern),
                    absoluteStep = calculateAbsoluteStep(songMode, sequencePosition, clampedRepeat, stepInPattern)
                )
            }
        }
    }
    
    /**
     * Navigate to specific timeline position (0.0 to 1.0)
     */
    fun navigateToTimelinePosition(position: Float): SongPosition? {
        val state = _navigationState.value
        val songMode = state.songMode ?: return null
        
        val clampedPosition = position.coerceIn(0f, 1f)
        val totalSteps = songMode.getTotalSteps()
        val targetAbsoluteStep = (clampedPosition * totalSteps).toInt()
        
        return findPositionForAbsoluteStep(songMode, targetAbsoluteStep)
    }
    
    /**
     * Navigate to specific pattern in sequence
     */
    fun navigateToPattern(sequencePosition: Int): SongPosition? {
        val state = _navigationState.value
        val songMode = state.songMode ?: return null
        
        if (sequencePosition >= 0 && sequencePosition < songMode.sequence.size) {
            return SongPosition(sequencePosition, 0, 0)
        }
        return null
    }
    
    /**
     * Navigate to next pattern boundary
     */
    fun navigateToNextPattern(): SongPosition? {
        val state = _navigationState.value
        val songMode = state.songMode ?: return null
        val currentPos = state.currentPosition
        
        val nextSequencePos = currentPos.sequencePosition + 1
        return if (nextSequencePos < songMode.sequence.size) {
            SongPosition(nextSequencePos, 0, 0)
        } else if (songMode.loopEnabled) {
            SongPosition(0, 0, 0)
        } else {
            null
        }
    }
    
    /**
     * Navigate to previous pattern boundary
     */
    fun navigateToPreviousPattern(): SongPosition? {
        val state = _navigationState.value
        val songMode = state.songMode ?: return null
        val currentPos = state.currentPosition
        
        val prevSequencePos = currentPos.sequencePosition - 1
        return if (prevSequencePos >= 0) {
            SongPosition(prevSequencePos, 0, 0)
        } else if (songMode.loopEnabled) {
            val lastPos = songMode.sequence.size - 1
            SongPosition(lastPos, 0, 0)
        } else {
            null
        }
    }
    
    /**
     * Get timeline markers for visual representation
     */
    fun getTimelineMarkers(): List<TimelineMarker> {
        val state = _navigationState.value
        return state.timeline
    }
    
    /**
     * Get current playback progress (0.0 to 1.0)
     */
    fun getPlaybackProgress(): Float {
        val state = _navigationState.value
        val songMode = state.songMode ?: return 0f
        
        val totalSteps = songMode.getTotalSteps()
        return if (totalSteps > 0) {
            state.absoluteStep.toFloat() / totalSteps
        } else {
            0f
        }
    }
    
    /**
     * Get remaining time in song (in milliseconds)
     */
    fun getRemainingTime(currentTempo: Float): Long {
        val state = _navigationState.value
        val songMode = state.songMode ?: return 0L
        
        val totalSteps = songMode.getTotalSteps()
        val remainingSteps = totalSteps - state.absoluteStep
        val stepDurationMs = (60000f / currentTempo / 4f).toLong() // 16th note duration
        
        return remainingSteps * stepDurationMs
    }
    
    /**
     * Get elapsed time in song (in milliseconds)
     */
    fun getElapsedTime(currentTempo: Float): Long {
        val state = _navigationState.value
        val stepDurationMs = (60000f / currentTempo / 4f).toLong() // 16th note duration
        return state.absoluteStep * stepDurationMs
    }
    
    /**
     * Check if position is at pattern boundary
     */
    fun isAtPatternBoundary(): Boolean {
        val state = _navigationState.value
        return state.currentPosition.patternRepeat == 0 && state.currentPosition.stepInPattern == 0
    }
    
    /**
     * Get pattern info for current position
     */
    fun getCurrentPatternInfo(): PatternInfo? {
        val state = _navigationState.value
        val songMode = state.songMode ?: return null
        val currentPos = state.currentPosition
        
        if (currentPos.sequencePosition >= songMode.sequence.size) return null
        
        val songStep = songMode.sequence[currentPos.sequencePosition]
        return PatternInfo(
            patternId = songStep.patternId,
            sequencePosition = currentPos.sequencePosition,
            currentRepeat = currentPos.patternRepeat + 1,
            totalRepeats = songStep.repeatCount,
            stepInPattern = currentPos.stepInPattern
        )
    }
    
    /**
     * Build timeline markers for visualization
     */
    private fun buildTimeline(songMode: SongMode): List<TimelineMarker> {
        val markers = mutableListOf<TimelineMarker>()
        var absolutePosition = 0
        val totalSteps = songMode.getTotalSteps()
        
        songMode.sequence.forEachIndexed { index, songStep ->
            // Add pattern start marker
            markers.add(
                TimelineMarker(
                    position = absolutePosition.toFloat() / totalSteps,
                    type = TimelineMarkerType.PATTERN_START,
                    label = "Pattern ${index + 1}",
                    patternId = songStep.patternId,
                    sequencePosition = index
                )
            )
            
            // Add repeat markers if pattern repeats more than once
            repeat(songStep.repeatCount) { repeatIndex ->
                if (repeatIndex > 0) {
                    markers.add(
                        TimelineMarker(
                            position = absolutePosition.toFloat() / totalSteps,
                            type = TimelineMarkerType.PATTERN_REPEAT,
                            label = "Repeat ${repeatIndex + 1}",
                            patternId = songStep.patternId,
                            sequencePosition = index,
                            repeatIndex = repeatIndex
                        )
                    )
                }
                absolutePosition += 16 // Assuming 16 steps per pattern (this should be dynamic)
            }
        }
        
        return markers
    }
    
    /**
     * Calculate total duration in steps
     */
    private fun calculateTotalDuration(songMode: SongMode): Int {
        return songMode.sequence.sumOf { it.repeatCount * 16 } // Assuming 16 steps per pattern
    }
    
    /**
     * Calculate absolute step position
     */
    private fun calculateAbsoluteStep(
        songMode: SongMode,
        sequencePosition: Int,
        patternRepeat: Int,
        stepInPattern: Int
    ): Int {
        var absoluteStep = 0
        
        // Add steps from completed patterns
        for (i in 0 until sequencePosition) {
            absoluteStep += songMode.sequence[i].repeatCount * 16 // Assuming 16 steps per pattern
        }
        
        // Add steps from completed repeats of current pattern
        absoluteStep += patternRepeat * 16
        
        // Add current step within pattern
        absoluteStep += stepInPattern
        
        return absoluteStep
    }
    
    /**
     * Find song position for absolute step
     */
    private fun findPositionForAbsoluteStep(songMode: SongMode, targetStep: Int): SongPosition? {
        var remainingSteps = targetStep
        
        songMode.sequence.forEachIndexed { sequenceIndex, songStep ->
            val patternSteps = 16 // Assuming 16 steps per pattern
            val totalPatternSteps = songStep.repeatCount * patternSteps
            
            if (remainingSteps < totalPatternSteps) {
                val patternRepeat = remainingSteps / patternSteps
                val stepInPattern = remainingSteps % patternSteps
                return SongPosition(sequenceIndex, patternRepeat, stepInPattern)
            }
            
            remainingSteps -= totalPatternSteps
        }
        
        return null
    }
}

/**
 * Represents a position within a song
 */
data class SongPosition(
    val sequencePosition: Int,
    val patternRepeat: Int,
    val stepInPattern: Int = 0
)

/**
 * Navigation state for song mode
 */
data class SongNavigationState(
    val songMode: SongMode? = null,
    val currentPosition: SongPosition = SongPosition(0, 0, 0),
    val timeline: List<TimelineMarker> = emptyList(),
    val absoluteStep: Int = 0,
    val totalDuration: Int = 0
)

/**
 * Timeline marker for visualization
 */
data class TimelineMarker(
    val position: Float, // 0.0 to 1.0
    val type: TimelineMarkerType,
    val label: String,
    val patternId: String,
    val sequencePosition: Int,
    val repeatIndex: Int = 0
)

/**
 * Types of timeline markers
 */
enum class TimelineMarkerType {
    PATTERN_START,
    PATTERN_REPEAT,
    SECTION_BOUNDARY
}

/**
 * Information about current pattern in song
 */
data class PatternInfo(
    val patternId: String,
    val sequencePosition: Int,
    val currentRepeat: Int,
    val totalRepeats: Int,
    val stepInPattern: Int
)