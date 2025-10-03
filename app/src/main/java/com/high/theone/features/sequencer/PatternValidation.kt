package com.high.theone.features.sequencer

import com.high.theone.model.Pattern
import com.high.theone.model.Step
import com.high.theone.model.SequencerState
import com.high.theone.model.SongMode
import com.high.theone.model.SongStep

/**
 * Comprehensive validation system for sequencer data models
 */
object PatternValidation {
    
    /**
     * Validation result containing errors and warnings
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<ValidationWarning> = emptyList()
    ) {
        val hasErrors: Boolean get() = errors.isNotEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
        
        fun getAllMessages(): List<String> = errors.map { it.message } + warnings.map { it.message }
    }
    
    /**
     * Validation error with severity and context
     */
    data class ValidationError(
        val code: String,
        val message: String,
        val field: String? = null,
        val context: Map<String, Any> = emptyMap()
    )
    
    /**
     * Validation warning for non-critical issues
     */
    data class ValidationWarning(
        val code: String,
        val message: String,
        val field: String? = null,
        val context: Map<String, Any> = emptyMap()
    )
    
    /**
     * Validates a complete pattern
     */
    fun validatePattern(pattern: Pattern): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Validate basic pattern properties
        validatePatternBasics(pattern, errors)
        
        // Validate steps
        validatePatternSteps(pattern, errors, warnings)
        
        // Validate timing constraints
        validatePatternTiming(pattern, errors, warnings)
        
        // Performance warnings
        checkPerformanceWarnings(pattern, warnings)
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates basic pattern properties
     */
    private fun validatePatternBasics(pattern: Pattern, errors: MutableList<ValidationError>) {
        // Name validation
        if (pattern.name.isBlank()) {
            errors.add(ValidationError(
                code = "PATTERN_NAME_BLANK",
                message = "Pattern name cannot be blank",
                field = "name"
            ))
        }
        
        if (pattern.name.length > 100) {
            errors.add(ValidationError(
                code = "PATTERN_NAME_TOO_LONG",
                message = "Pattern name cannot exceed 100 characters",
                field = "name",
                context = mapOf("length" to pattern.name.length)
            ))
        }
        
        // Length validation
        if (pattern.length !in listOf(8, 16, 24, 32)) {
            errors.add(ValidationError(
                code = "INVALID_PATTERN_LENGTH",
                message = "Pattern length must be 8, 16, 24, or 32 steps",
                field = "length",
                context = mapOf("length" to pattern.length)
            ))
        }
        
        // Tempo validation
        if (pattern.tempo !in 60f..200f) {
            errors.add(ValidationError(
                code = "INVALID_TEMPO",
                message = "Tempo must be between 60 and 200 BPM",
                field = "tempo",
                context = mapOf("tempo" to pattern.tempo)
            ))
        }
        
        // Swing validation
        if (pattern.swing !in 0f..0.75f) {
            errors.add(ValidationError(
                code = "INVALID_SWING",
                message = "Swing must be between 0.0 and 0.75",
                field = "swing",
                context = mapOf("swing" to pattern.swing)
            ))
        }
        
        // ID validation
        if (pattern.id.isBlank()) {
            errors.add(ValidationError(
                code = "PATTERN_ID_BLANK",
                message = "Pattern ID cannot be blank",
                field = "id"
            ))
        }
        
        // Timestamp validation
        if (pattern.createdAt <= 0) {
            errors.add(ValidationError(
                code = "INVALID_CREATED_TIMESTAMP",
                message = "Created timestamp must be positive",
                field = "createdAt"
            ))
        }
        
        if (pattern.modifiedAt < pattern.createdAt) {
            errors.add(ValidationError(
                code = "INVALID_MODIFIED_TIMESTAMP",
                message = "Modified timestamp cannot be before created timestamp",
                field = "modifiedAt"
            ))
        }
    }
    
    /**
     * Validates pattern steps
     */
    private fun validatePatternSteps(
        pattern: Pattern,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        pattern.steps.forEach { (padIndex, steps) ->
            // Validate pad index
            if (padIndex < 0) {
                errors.add(ValidationError(
                    code = "INVALID_PAD_INDEX",
                    message = "Pad index must be non-negative",
                    field = "steps",
                    context = mapOf("padIndex" to padIndex)
                ))
            }
            
            if (padIndex > 15) { // Assuming max 16 pads (0-15)
                warnings.add(ValidationWarning(
                    code = "HIGH_PAD_INDEX",
                    message = "Pad index $padIndex is higher than typical range (0-15)",
                    field = "steps",
                    context = mapOf("padIndex" to padIndex)
                ))
            }
            
            // Validate individual steps
            steps.forEachIndexed { stepIndex, step ->
                validateStep(step, pattern.length, padIndex, stepIndex, errors, warnings)
            }
            
            // Check for duplicate positions
            val positions = steps.map { it.position }
            val duplicates = positions.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicates.isNotEmpty()) {
                errors.add(ValidationError(
                    code = "DUPLICATE_STEP_POSITIONS",
                    message = "Pad $padIndex has duplicate steps at positions: ${duplicates.joinToString()}",
                    field = "steps",
                    context = mapOf("padIndex" to padIndex, "duplicatePositions" to duplicates)
                ))
            }
            
            // Check step ordering
            if (steps.size > 1 && steps != steps.sortedBy { it.position }) {
                warnings.add(ValidationWarning(
                    code = "STEPS_NOT_SORTED",
                    message = "Steps for pad $padIndex are not sorted by position",
                    field = "steps",
                    context = mapOf("padIndex" to padIndex)
                ))
            }
        }
    }
    
    /**
     * Validates a single step
     */
    private fun validateStep(
        step: Step,
        patternLength: Int,
        padIndex: Int,
        stepIndex: Int,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        val context = mapOf(
            "padIndex" to padIndex,
            "stepIndex" to stepIndex,
            "step" to step
        )
        
        // Position validation
        if (step.position < 0) {
            errors.add(ValidationError(
                code = "NEGATIVE_STEP_POSITION",
                message = "Step position cannot be negative",
                field = "steps",
                context = context + mapOf("position" to step.position)
            ))
        }
        
        if (step.position >= patternLength) {
            errors.add(ValidationError(
                code = "STEP_POSITION_OUT_OF_BOUNDS",
                message = "Step position ${step.position} exceeds pattern length $patternLength",
                field = "steps",
                context = context + mapOf("position" to step.position, "patternLength" to patternLength)
            ))
        }
        
        // Velocity validation
        if (step.velocity !in 1..127) {
            errors.add(ValidationError(
                code = "INVALID_STEP_VELOCITY",
                message = "Step velocity must be between 1 and 127",
                field = "steps",
                context = context + mapOf("velocity" to step.velocity)
            ))
        }
        
        // Velocity warnings
        if (step.velocity < 10) {
            warnings.add(ValidationWarning(
                code = "LOW_STEP_VELOCITY",
                message = "Step velocity ${step.velocity} is very low and may be inaudible",
                field = "steps",
                context = context
            ))
        }
        
        // Micro timing validation
        if (step.microTiming !in -50f..50f) {
            errors.add(ValidationError(
                code = "INVALID_MICRO_TIMING",
                message = "Micro timing must be between -50ms and +50ms",
                field = "steps",
                context = context + mapOf("microTiming" to step.microTiming)
            ))
        }
        
        // Micro timing warnings
        if (kotlin.math.abs(step.microTiming) > 25f) {
            warnings.add(ValidationWarning(
                code = "EXTREME_MICRO_TIMING",
                message = "Micro timing ${step.microTiming}ms is quite extreme and may affect groove",
                field = "steps",
                context = context
            ))
        }
    }
    
    /**
     * Validates timing-related constraints
     */
    private fun validatePatternTiming(
        pattern: Pattern,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Check for timing conflicts with swing
        if (pattern.swing > 0f) {
            val hasOffBeatSteps = pattern.steps.values.flatten().any { it.position % 2 == 1 }
            if (!hasOffBeatSteps) {
                warnings.add(ValidationWarning(
                    code = "SWING_WITHOUT_OFFBEAT_STEPS",
                    message = "Pattern has swing but no off-beat steps to apply it to",
                    field = "swing"
                ))
            }
        }
        
        // Check for very dense patterns that might cause performance issues
        val totalSteps = pattern.steps.values.sumOf { it.size }
        val density = totalSteps.toFloat() / pattern.length
        if (density > 8f) { // More than 8 simultaneous hits per step on average
            warnings.add(ValidationWarning(
                code = "HIGH_PATTERN_DENSITY",
                message = "Pattern has high step density (${"%.1f".format(density)} steps per position) which may impact performance",
                field = "steps",
                context = mapOf("density" to density, "totalSteps" to totalSteps)
            ))
        }
    }
    
    /**
     * Checks for performance-related warnings
     */
    private fun checkPerformanceWarnings(
        pattern: Pattern,
        warnings: MutableList<ValidationWarning>
    ) {
        // Check for simultaneous steps that might overload audio engine
        (0 until pattern.length).forEach { position ->
            val simultaneousSteps = pattern.steps.values.count { steps ->
                steps.any { it.position == position && it.isActive }
            }
            
            if (simultaneousSteps > 8) {
                warnings.add(ValidationWarning(
                    code = "HIGH_SIMULTANEOUS_STEPS",
                    message = "Position $position has $simultaneousSteps simultaneous steps which may cause audio glitches",
                    field = "steps",
                    context = mapOf("position" to position, "simultaneousSteps" to simultaneousSteps)
                ))
            }
        }
        
        // Check for patterns with no steps
        val hasActiveSteps = pattern.steps.values.flatten().any { it.isActive }
        if (pattern.steps.isEmpty() || !hasActiveSteps) {
            warnings.add(ValidationWarning(
                code = "EMPTY_PATTERN",
                message = "Pattern has no active steps",
                field = "steps"
            ))
        }
    }
    
    /**
     * Validates sequencer state
     */
    fun validateSequencerState(state: SequencerState): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Validate current step
        if (state.currentStep < 0) {
            errors.add(ValidationError(
                code = "NEGATIVE_CURRENT_STEP",
                message = "Current step cannot be negative",
                field = "currentStep"
            ))
        }
        
        // Validate state consistency
        if (state.isPlaying && state.currentPattern == null) {
            errors.add(ValidationError(
                code = "PLAYING_WITHOUT_PATTERN",
                message = "Cannot be playing without a current pattern",
                field = "currentPattern"
            ))
        }
        
        if (state.isRecording && !state.isPlaying) {
            warnings.add(ValidationWarning(
                code = "RECORDING_WITHOUT_PLAYBACK",
                message = "Recording is enabled but playback is not active",
                field = "isRecording"
            ))
        }
        
        // Validate selected pads
        if (state.selectedPads.any { it < 0 }) {
            errors.add(ValidationError(
                code = "NEGATIVE_PAD_INDEX",
                message = "Selected pad indices cannot be negative",
                field = "selectedPads"
            ))
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates song mode configuration
     */
    fun validateSongMode(songMode: SongMode): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Validate sequence position
        if (songMode.currentSequencePosition < 0) {
            errors.add(ValidationError(
                code = "NEGATIVE_SEQUENCE_POSITION",
                message = "Current sequence position cannot be negative",
                field = "currentSequencePosition"
            ))
        }
        
        if (songMode.currentSequencePosition >= songMode.sequence.size && songMode.sequence.isNotEmpty()) {
            errors.add(ValidationError(
                code = "SEQUENCE_POSITION_OUT_OF_BOUNDS",
                message = "Current sequence position exceeds sequence length",
                field = "currentSequencePosition"
            ))
        }
        
        // Validate song steps
        songMode.sequence.forEachIndexed { index, songStep ->
            val stepErrors = validateSongStep(songStep, index)
            errors.addAll(stepErrors)
        }
        
        // Check for empty sequence
        if (songMode.isActive && songMode.sequence.isEmpty()) {
            warnings.add(ValidationWarning(
                code = "EMPTY_SONG_SEQUENCE",
                message = "Song mode is active but sequence is empty",
                field = "sequence"
            ))
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validates a single song step
     */
    private fun validateSongStep(songStep: SongStep, index: Int): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val context = mapOf("songStepIndex" to index, "songStep" to songStep)
        
        if (songStep.patternId.isBlank()) {
            errors.add(ValidationError(
                code = "BLANK_PATTERN_ID",
                message = "Song step pattern ID cannot be blank",
                field = "sequence",
                context = context
            ))
        }
        
        if (songStep.repeatCount <= 0) {
            errors.add(ValidationError(
                code = "INVALID_REPEAT_COUNT",
                message = "Song step repeat count must be positive",
                field = "sequence",
                context = context + mapOf("repeatCount" to songStep.repeatCount)
            ))
        }
        
        if (songStep.repeatCount > 100) {
            errors.add(ValidationError(
                code = "EXCESSIVE_REPEAT_COUNT",
                message = "Song step repeat count ${songStep.repeatCount} is excessive",
                field = "sequence",
                context = context
            ))
        }
        
        return errors
    }
}

/**
 * Exception thrown when pattern validation fails
 */
class PatternValidationException(
    message: String,
    val validationResult: PatternValidation.ValidationResult
) : Exception(message)

/**
 * Extension functions for validation
 */

/**
 * Validates pattern and throws exception if invalid
 */
fun Pattern.validateOrThrow() {
    val result = PatternValidation.validatePattern(this)
    if (!result.isValid) {
        throw PatternValidationException(
            "Pattern validation failed: ${result.errors.joinToString { it.message }}",
            result
        )
    }
}

/**
 * Returns true if pattern is valid
 */
fun Pattern.isValid(): Boolean = PatternValidation.validatePattern(this).isValid

/**
 * Returns validation errors for this pattern
 */
fun Pattern.getValidationErrors(): List<PatternValidation.ValidationError> = 
    PatternValidation.validatePattern(this).errors