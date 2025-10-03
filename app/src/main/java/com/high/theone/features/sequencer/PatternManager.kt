package com.high.theone.features.sequencer

import com.high.theone.model.Pattern
import com.high.theone.model.Step
import com.high.theone.model.Quantization
import com.high.theone.model.StepCondition
import com.high.theone.model.StepHumanization
import java.util.UUID
import kotlin.random.Random

/**
 * Manages pattern operations including creation, manipulation, and validation
 */
class PatternManager {
    
    /**
     * Creates a new empty pattern with specified parameters
     */
    fun createEmptyPattern(
        name: String,
        length: Int = 16,
        tempo: Float = 120f,
        swing: Float = 0f
    ): Pattern {
        require(name.isNotBlank()) { "Pattern name cannot be blank" }
        require(length in listOf(8, 16, 24, 32)) { "Pattern length must be 8, 16, 24, or 32 steps" }
        require(tempo in 60f..200f) { "Tempo must be between 60 and 200 BPM" }
        require(swing in 0f..0.75f) { "Swing must be between 0.0 and 0.75" }
        
        return Pattern(
            id = UUID.randomUUID().toString(),
            name = name,
            length = length,
            steps = emptyMap(),
            tempo = tempo,
            swing = swing
        )
    }
    
    /**
     * Toggles a step on/off for a specific pad
     */
    fun toggleStep(pattern: Pattern, padIndex: Int, stepIndex: Int): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        
        val currentSteps = pattern.steps[padIndex] ?: emptyList()
        val existingStep = currentSteps.find { it.position == stepIndex }
        
        val updatedSteps = if (existingStep != null) {
            // Remove existing step (toggle off)
            currentSteps.filter { it.position != stepIndex }
        } else {
            // Add new step (toggle on)
            currentSteps + Step(position = stepIndex, velocity = 100, isActive = true)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        if (updatedSteps.isEmpty()) {
            newStepsMap.remove(padIndex)
        } else {
            newStepsMap[padIndex] = updatedSteps.sortedBy { it.position }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets the velocity for a specific step
     */
    fun setStepVelocity(
        pattern: Pattern,
        padIndex: Int,
        stepIndex: Int,
        velocity: Int
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        require(velocity in 1..127) { "Velocity must be between 1 and 127" }
        
        val currentSteps = pattern.steps[padIndex] ?: emptyList()
        val existingStepIndex = currentSteps.indexOfFirst { it.position == stepIndex }
        
        val updatedSteps = if (existingStepIndex >= 0) {
            // Update existing step velocity
            currentSteps.toMutableList().apply {
                this[existingStepIndex] = this[existingStepIndex].copy(velocity = velocity)
            }
        } else {
            // Create new step with specified velocity
            currentSteps + Step(position = stepIndex, velocity = velocity, isActive = true)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap[padIndex] = updatedSteps.sortedBy { it.position }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets micro-timing for a specific step
     */
    fun setStepMicroTiming(
        pattern: Pattern,
        padIndex: Int,
        stepIndex: Int,
        microTiming: Float
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        require(microTiming in -50f..50f) { "Micro timing must be between -50ms and +50ms" }
        
        val currentSteps = pattern.steps[padIndex] ?: emptyList()
        val existingStepIndex = currentSteps.indexOfFirst { it.position == stepIndex }
        
        if (existingStepIndex < 0) {
            // No step exists at this position
            return pattern
        }
        
        val updatedSteps = currentSteps.toMutableList().apply {
            this[existingStepIndex] = this[existingStepIndex].copy(microTiming = microTiming)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap[padIndex] = updatedSteps
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Clears a specific step
     */
    fun clearStep(pattern: Pattern, padIndex: Int, stepIndex: Int): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        
        val currentSteps = pattern.steps[padIndex] ?: return pattern
        val updatedSteps = currentSteps.filter { it.position != stepIndex }
        
        val newStepsMap = pattern.steps.toMutableMap()
        if (updatedSteps.isEmpty()) {
            newStepsMap.remove(padIndex)
        } else {
            newStepsMap[padIndex] = updatedSteps
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Clears all steps for a specific pad
     */
    fun clearPad(pattern: Pattern, padIndex: Int): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap.remove(padIndex)
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Clears the entire pattern
     */
    fun clearPattern(pattern: Pattern): Pattern {
        return pattern.copy(steps = emptyMap()).withModification()
    }
    
    /**
     * Copies a pattern with a new name and ID
     */
    fun copyPattern(source: Pattern, newName: String): Pattern {
        require(newName.isNotBlank()) { "Pattern name cannot be blank" }
        
        return source.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Duplicates a pattern with automatic naming
     */
    fun duplicatePattern(source: Pattern): Pattern {
        val newName = generateDuplicateName(source.name)
        return copyPattern(source, newName)
    }
    
    /**
     * Changes the length of a pattern, truncating or extending as needed
     */
    fun changePatternLength(pattern: Pattern, newLength: Int): Pattern {
        require(newLength in listOf(8, 16, 24, 32)) { "Pattern length must be 8, 16, 24, or 32 steps" }
        
        if (newLength == pattern.length) return pattern
        
        val newStepsMap = if (newLength < pattern.length) {
            // Truncate: remove steps beyond new length
            pattern.steps.mapValues { (_, steps) ->
                steps.filter { it.position < newLength }
            }.filterValues { it.isNotEmpty() }
        } else {
            // Extend: keep existing steps (no new steps added automatically)
            pattern.steps
        }
        
        return pattern.copy(
            length = newLength,
            steps = newStepsMap
        ).withModification()
    }
    
    /**
     * Updates pattern tempo
     */
    fun setTempo(pattern: Pattern, tempo: Float): Pattern {
        require(tempo in 60f..200f) { "Tempo must be between 60 and 200 BPM" }
        return pattern.copy(tempo = tempo).withModification()
    }
    
    /**
     * Updates pattern swing amount
     */
    fun setSwing(pattern: Pattern, swing: Float): Pattern {
        require(swing in 0f..0.75f) { "Swing must be between 0.0 and 0.75" }
        return pattern.copy(swing = swing).withModification()
    }
    
    /**
     * Quantizes all steps in a pattern to the nearest quantization boundary
     */
    fun quantizePattern(pattern: Pattern, quantization: Quantization): Pattern {
        if (quantization == Quantization.OFF) return pattern
        
        val quantizationSteps = when (quantization) {
            Quantization.QUARTER -> 4
            Quantization.EIGHTH -> 8
            Quantization.SIXTEENTH -> 16
            Quantization.THIRTY_SECOND -> 32
            Quantization.OFF -> return pattern
        }
        
        val stepSize = pattern.length / quantizationSteps
        if (stepSize <= 0) return pattern
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val quantizedPosition = (step.position / stepSize) * stepSize
                step.copy(position = quantizedPosition.coerceIn(0, pattern.length - 1))
            }.distinctBy { it.position } // Remove duplicates after quantization
        }.filterValues { it.isNotEmpty() }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Shifts all steps in a pattern by the specified number of steps
     */
    fun shiftPattern(pattern: Pattern, shiftAmount: Int): Pattern {
        if (shiftAmount == 0) return pattern
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val newPosition = (step.position + shiftAmount) % pattern.length
                val adjustedPosition = if (newPosition < 0) newPosition + pattern.length else newPosition
                step.copy(position = adjustedPosition)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Reverses the pattern (mirrors all step positions)
     */
    fun reversePattern(pattern: Pattern): Pattern {
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val reversedPosition = pattern.length - 1 - step.position
                step.copy(position = reversedPosition)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Applies velocity scaling to all steps in the pattern
     */
    fun scaleVelocity(pattern: Pattern, scaleFactor: Float): Pattern {
        require(scaleFactor > 0f) { "Scale factor must be positive" }
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val newVelocity = (step.velocity * scaleFactor).toInt().coerceIn(1, 127)
                step.copy(velocity = newVelocity)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Randomizes step velocities within a specified range
     */
    fun randomizeVelocities(
        pattern: Pattern,
        minVelocity: Int = 80,
        maxVelocity: Int = 127
    ): Pattern {
        require(minVelocity in 1..127) { "Min velocity must be between 1 and 127" }
        require(maxVelocity in 1..127) { "Max velocity must be between 1 and 127" }
        require(minVelocity <= maxVelocity) { "Min velocity must be <= max velocity" }
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val randomVelocity = (minVelocity..maxVelocity).random()
                step.copy(velocity = randomVelocity)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets probability for a specific step
     */
    fun setStepProbability(
        pattern: Pattern,
        padIndex: Int,
        stepIndex: Int,
        probability: Float
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        require(probability in 0f..1f) { "Probability must be between 0.0 and 1.0" }
        
        val currentSteps = pattern.steps[padIndex] ?: return pattern
        val existingStepIndex = currentSteps.indexOfFirst { it.position == stepIndex }
        
        if (existingStepIndex < 0) {
            // No step exists at this position
            return pattern
        }
        
        val updatedSteps = currentSteps.toMutableList().apply {
            this[existingStepIndex] = this[existingStepIndex].copy(probability = probability)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap[padIndex] = updatedSteps
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets condition for a specific step
     */
    fun setStepCondition(
        pattern: Pattern,
        padIndex: Int,
        stepIndex: Int,
        condition: com.high.theone.model.StepCondition
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        
        val currentSteps = pattern.steps[padIndex] ?: return pattern
        val existingStepIndex = currentSteps.indexOfFirst { it.position == stepIndex }
        
        if (existingStepIndex < 0) {
            // No step exists at this position
            return pattern
        }
        
        val updatedSteps = currentSteps.toMutableList().apply {
            this[existingStepIndex] = this[existingStepIndex].copy(condition = condition)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap[padIndex] = updatedSteps
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets humanization for a specific step
     */
    fun setStepHumanization(
        pattern: Pattern,
        padIndex: Int,
        stepIndex: Int,
        humanization: com.high.theone.model.StepHumanization
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(stepIndex in 0 until pattern.length) { "Step index must be within pattern length" }
        
        val currentSteps = pattern.steps[padIndex] ?: return pattern
        val existingStepIndex = currentSteps.indexOfFirst { it.position == stepIndex }
        
        if (existingStepIndex < 0) {
            // No step exists at this position
            return pattern
        }
        
        val updatedSteps = currentSteps.toMutableList().apply {
            this[existingStepIndex] = this[existingStepIndex].copy(humanization = humanization)
        }
        
        val newStepsMap = pattern.steps.toMutableMap()
        newStepsMap[padIndex] = updatedSteps
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Randomizes all step parameters in a pattern
     */
    fun randomizePattern(
        pattern: Pattern,
        timingRange: Float = 5f,
        velocityRange: Int = 10,
        probabilityRange: Float = 0.2f
    ): Pattern {
        require(timingRange >= 0f) { "Timing range must be non-negative" }
        require(velocityRange >= 0) { "Velocity range must be non-negative" }
        require(probabilityRange >= 0f) { "Probability range must be non-negative" }
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val randomTiming = step.microTiming + ((-1f..1f).random() * timingRange)
                val randomVelocity = step.velocity + ((-velocityRange..velocityRange).random())
                val randomProbability = step.probability + ((-1f..1f).random() * probabilityRange)
                
                step.copy(
                    microTiming = randomTiming.coerceIn(-50f, 50f),
                    velocity = randomVelocity.coerceIn(1, 127),
                    probability = randomProbability.coerceIn(0f, 1f)
                )
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Applies humanization to all steps in a pattern
     */
    fun humanizePattern(
        pattern: Pattern,
        timingVariation: Float = 3f,
        velocityVariation: Float = 0.15f
    ): Pattern {
        require(timingVariation in 0f..10f) { "Timing variation must be between 0 and 10ms" }
        require(velocityVariation in 0f..1f) { "Velocity variation must be between 0.0 and 1.0" }
        
        val humanization = com.high.theone.model.StepHumanization(
            timingVariation = timingVariation,
            velocityVariation = velocityVariation,
            enabled = true
        )
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                step.copy(humanization = humanization)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Sets probability for all steps in a pattern
     */
    fun setPatternProbability(pattern: Pattern, probability: Float): Pattern {
        require(probability in 0f..1f) { "Probability must be between 0.0 and 1.0" }
        
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                step.copy(probability = probability)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Creates a euclidean rhythm pattern for a specific pad
     */
    fun createEuclideanRhythm(
        pattern: Pattern,
        padIndex: Int,
        hits: Int,
        steps: Int,
        rotation: Int = 0,
        velocity: Int = 100
    ): Pattern {
        require(padIndex >= 0) { "Pad index must be non-negative" }
        require(hits >= 0) { "Hits must be non-negative" }
        require(steps > 0) { "Steps must be positive" }
        require(hits <= steps) { "Hits cannot exceed steps" }
        require(steps <= pattern.length) { "Steps cannot exceed pattern length" }
        require(velocity in 1..127) { "Velocity must be between 1 and 127" }
        
        // Generate euclidean rhythm
        val euclideanSteps = generateEuclideanRhythm(hits, steps, rotation)
        
        // Convert to Step objects
        val newSteps = euclideanSteps.mapIndexed { index, isActive ->
            if (isActive) {
                Step(position = index, velocity = velocity, isActive = true)
            } else null
        }.filterNotNull()
        
        val newStepsMap = pattern.steps.toMutableMap()
        if (newSteps.isEmpty()) {
            newStepsMap.remove(padIndex)
        } else {
            newStepsMap[padIndex] = newSteps
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    private fun generateEuclideanRhythm(hits: Int, steps: Int, rotation: Int): List<Boolean> {
        if (hits == 0) return List(steps) { false }
        if (hits >= steps) return List(steps) { true }
        
        val rhythm = MutableList(steps) { false }
        val interval = steps.toFloat() / hits
        
        for (i in 0 until hits) {
            val position = ((i * interval).toInt() + rotation) % steps
            rhythm[position] = true
        }
        
        return rhythm
    }
    
    /**
     * Gets all active steps at a specific position across all pads
     */
    fun getStepsAtPosition(pattern: Pattern, position: Int): Map<Int, Step> {
        require(position in 0 until pattern.length) { "Position must be within pattern length" }
        
        return pattern.steps.mapNotNull { (padIndex, steps) ->
            val step = steps.find { it.position == position && it.isActive }
            if (step != null) padIndex to step else null
        }.toMap()
    }
    
    /**
     * Validates pattern integrity
     */
    fun validatePattern(pattern: Pattern): List<String> {
        val errors = mutableListOf<String>()
        
        // Check basic constraints
        if (pattern.name.isBlank()) {
            errors.add("Pattern name cannot be blank")
        }
        
        if (pattern.length !in listOf(8, 16, 24, 32)) {
            errors.add("Pattern length must be 8, 16, 24, or 32 steps")
        }
        
        if (pattern.tempo !in 60f..200f) {
            errors.add("Tempo must be between 60 and 200 BPM")
        }
        
        if (pattern.swing !in 0f..0.75f) {
            errors.add("Swing must be between 0.0 and 0.75")
        }
        
        // Check step constraints
        pattern.steps.forEach { (padIndex, steps) ->
            if (padIndex < 0) {
                errors.add("Pad index $padIndex must be non-negative")
            }
            
            steps.forEach { step ->
                if (step.position < 0 || step.position >= pattern.length) {
                    errors.add("Step position ${step.position} is outside pattern length ${pattern.length}")
                }
                
                if (step.velocity !in 1..127) {
                    errors.add("Step velocity ${step.velocity} must be between 1 and 127")
                }
                
                if (step.microTiming !in -50f..50f) {
                    errors.add("Step micro timing ${step.microTiming} must be between -50ms and +50ms")
                }
            }
            
            // Check for duplicate positions
            val positions = steps.map { it.position }
            val duplicates = positions.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicates.isNotEmpty()) {
                errors.add("Pad $padIndex has duplicate steps at positions: ${duplicates.joinToString()}")
            }
        }
        
        return errors
    }
    
    /**
     * Generates a duplicate name for a pattern
     */
    private fun generateDuplicateName(originalName: String): String {
        val copyRegex = Regex("""^(.+?)(?: \((\d+)\))?$""")
        val match = copyRegex.find(originalName)
        
        return if (match != null) {
            val baseName = match.groupValues[1]
            val copyNumber = match.groupValues[2].toIntOrNull() ?: 1
            "$baseName (${copyNumber + 1})"
        } else {
            "$originalName (2)"
        }
    }
}

/**
 * Extension functions for Pattern operations
 */

/**
 * Returns true if the pattern has any active steps
 */
fun Pattern.hasActiveSteps(): Boolean = steps.values.any { stepList ->
    stepList.any { it.isActive }
}

/**
 * Returns the total number of active steps in the pattern
 */
fun Pattern.getActiveStepCount(): Int = steps.values.sumOf { stepList ->
    stepList.count { it.isActive }
}

/**
 * Returns all pad indices that have steps in this pattern
 */
fun Pattern.getActivePads(): Set<Int> = steps.keys.toSet()

/**
 * Returns true if the specified pad has any steps
 */
fun Pattern.hasPadSteps(padIndex: Int): Boolean = steps[padIndex]?.isNotEmpty() == true

/**
 * Returns the step at the specified position for the given pad, if it exists
 */
fun Pattern.getStep(padIndex: Int, position: Int): Step? = 
    steps[padIndex]?.find { it.position == position && it.isActive }