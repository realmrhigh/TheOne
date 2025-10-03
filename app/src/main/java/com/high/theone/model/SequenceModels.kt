package com.high.theone.model

import kotlinx.serialization.Serializable
import java.util.UUID

// Legacy sequence models (keeping for compatibility)
@Serializable
data class Event(
    val id: Int = 0,
    val type: EventType = EventType.NOTE_ON,
    val time: Long = 0L,
    val value: Float = 0f
)

enum class EventType {
    NOTE_ON, NOTE_OFF, CONTROL_CHANGE, OTHER
}

@Serializable
data class Sequence(
    val id: Int = 0,
    val events: List<Event> = emptyList()
)

// Step Sequencer Data Models

/**
 * Represents a single step in a pattern with timing and velocity information
 */
@Serializable
data class Step(
    val position: Int, // 0-31 for step position within pattern
    val velocity: Int = 100, // 1-127 MIDI velocity
    val isActive: Boolean = true,
    val microTiming: Float = 0f // Fine timing adjustment in milliseconds (-50 to +50)
) {
    init {
        require(position >= 0) { "Step position must be non-negative" }
        require(velocity in 1..127) { "Velocity must be between 1 and 127" }
        require(microTiming in -50f..50f) { "Micro timing must be between -50ms and +50ms" }
    }
}

/**
 * Represents a complete drum pattern with steps, timing, and metadata
 */
@Serializable
data class Pattern(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val length: Int = 16, // 8, 16, 24, or 32 steps
    val steps: Map<Int, List<Step>> = emptyMap(), // padIndex -> List of steps
    val tempo: Float = 120f, // BPM
    val swing: Float = 0f, // 0.0 to 0.75 swing amount
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Pattern name cannot be blank" }
        require(length in listOf(8, 16, 24, 32)) { "Pattern length must be 8, 16, 24, or 32 steps" }
        require(tempo in 60f..200f) { "Tempo must be between 60 and 200 BPM" }
        require(swing in 0f..0.75f) { "Swing must be between 0.0 and 0.75" }
        
        // Validate all steps are within pattern length
        steps.values.flatten().forEach { step ->
            require(step.position < length) { "Step position ${step.position} exceeds pattern length $length" }
        }
    }
    
    /**
     * Returns a copy of this pattern with updated modification timestamp
     */
    fun withModification(): Pattern = copy(modifiedAt = System.currentTimeMillis())
}

/**
 * Quantization settings for recording and playback
 */
enum class Quantization(val subdivision: Int, val displayName: String) {
    QUARTER(4, "1/4"),
    EIGHTH(8, "1/8"), 
    SIXTEENTH(16, "1/16"),
    THIRTY_SECOND(32, "1/32"),
    OFF(0, "Off")
}

/**
 * Current state of the sequencer including playback and UI state
 */
@Serializable
data class SequencerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isRecording: Boolean = false,
    val currentStep: Int = 0,
    val currentPattern: String? = null,
    val patterns: List<String> = emptyList(), // Pattern IDs
    val songMode: SongMode? = null,
    val metronomeEnabled: Boolean = false,
    val quantization: Quantization = Quantization.SIXTEENTH,
    val selectedPads: Set<Int> = emptySet(), // Currently visible/selected pad indices
    val recordingMode: RecordingMode = RecordingMode.REPLACE
) {
    /**
     * Returns true if sequencer is actively playing (not paused)
     */
    val isActivelyPlaying: Boolean get() = isPlaying && !isPaused
    
    /**
     * Returns the current playback position as a percentage (0.0 to 1.0)
     */
    fun getPlaybackProgress(patternLength: Int): Float {
        return if (patternLength > 0) currentStep.toFloat() / patternLength else 0f
    }
}

/**
 * Recording mode for pattern recording
 */
enum class RecordingMode(val displayName: String) {
    REPLACE("Replace"),
    OVERDUB("Overdub"),
    PUNCH_IN("Punch In")
}

/**
 * Song mode for chaining patterns together
 */
@Serializable
data class SongMode(
    val sequence: List<SongStep> = emptyList(),
    val currentSequencePosition: Int = 0,
    val isActive: Boolean = false,
    val loopEnabled: Boolean = true
) {
    /**
     * Returns the current song step if available
     */
    fun getCurrentStep(): SongStep? = sequence.getOrNull(currentSequencePosition)
    
    /**
     * Returns the next song step if available
     */
    fun getNextStep(): SongStep? = sequence.getOrNull(currentSequencePosition + 1)
    
    /**
     * Returns total number of pattern repetitions in the song
     */
    fun getTotalSteps(): Int = sequence.sumOf { it.repeatCount }
}

/**
 * A single step in a song sequence
 */
@Serializable
data class SongStep(
    val patternId: String,
    val repeatCount: Int = 1
) {
    init {
        require(patternId.isNotBlank()) { "Pattern ID cannot be blank" }
        require(repeatCount > 0) { "Repeat count must be positive" }
    }
}
