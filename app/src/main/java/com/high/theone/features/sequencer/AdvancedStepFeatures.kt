package com.high.theone.features.sequencer

import com.high.theone.model.Step
import com.high.theone.model.StepCondition
import com.high.theone.model.StepHumanization
import kotlin.random.Random

/**
 * Manages advanced step features including probability, conditions, and humanization
 */
class AdvancedStepFeatures {
    
    private val stepPlayCounts = mutableMapOf<String, Int>() // stepId -> play count
    private val random = Random.Default
    
    /**
     * Evaluates whether a step should trigger based on its conditions and probability
     */
    fun shouldStepTrigger(
        step: Step,
        stepId: String,
        currentLoop: Int,
        patternLength: Int
    ): Boolean {
        // Check probability first
        if (step.probability < 1.0f && random.nextFloat() > step.probability) {
            return false
        }
        
        // Evaluate step condition
        return evaluateStepCondition(step.condition, stepId, currentLoop, patternLength)
    }
    
    /**
     * Applies humanization to a step's timing and velocity
     */
    fun applyHumanization(step: Step): Pair<Float, Int> {
        if (!step.humanization.enabled) {
            return step.microTiming to step.velocity
        }
        
        // Apply timing humanization
        val timingVariation = if (step.humanization.timingVariation > 0f) {
            random.nextFloat() * step.humanization.timingVariation * 2f - step.humanization.timingVariation
        } else {
            0f
        }
        val humanizedTiming = (step.microTiming + timingVariation).coerceIn(-50f, 50f)
        
        // Apply velocity humanization
        val velocityVariation = if (step.humanization.velocityVariation > 0f) {
            val variation = (random.nextFloat() * 2f - 1f) * step.humanization.velocityVariation * 20f // ±20 velocity units max
            variation.toInt()
        } else {
            0
        }
        val humanizedVelocity = (step.velocity + velocityVariation).coerceIn(1, 127)
        
        return humanizedTiming to humanizedVelocity
    }
    
    /**
     * Randomizes step parameters within specified ranges
     */
    fun randomizeStep(
        step: Step,
        timingRange: Float = 5f, // ±5ms
        velocityRange: Int = 10, // ±10 velocity units
        probabilityRange: Float = 0.2f // ±0.2 probability
    ): Step {
        val randomTiming = step.microTiming + (random.nextFloat() * 2f - 1f) * timingRange
        val randomVelocity = step.velocity + (random.nextInt(-velocityRange, velocityRange + 1))
        val randomProbability = step.probability + (random.nextFloat() * 2f - 1f) * probabilityRange
        
        return step.copy(
            microTiming = randomTiming.coerceIn(-50f, 50f),
            velocity = randomVelocity.coerceIn(1, 127),
            probability = randomProbability.coerceIn(0f, 1f)
        )
    }
    
    /**
     * Creates a humanized version of a step with natural variations
     */
    fun humanizeStep(
        step: Step,
        timingVariation: Float = 3f,
        velocityVariation: Float = 0.15f
    ): Step {
        return step.copy(
            humanization = StepHumanization(
                timingVariation = timingVariation,
                velocityVariation = velocityVariation,
                enabled = true
            )
        )
    }
    
    /**
     * Resets play counts for step conditions
     */
    fun resetPlayCounts() {
        stepPlayCounts.clear()
    }
    
    /**
     * Gets the current play count for a step
     */
    fun getStepPlayCount(stepId: String): Int {
        return stepPlayCounts[stepId] ?: 0
    }
    
    private fun evaluateStepCondition(
        condition: StepCondition,
        stepId: String,
        currentLoop: Int,
        patternLength: Int
    ): Boolean {
        return when (condition) {
            is StepCondition.Always -> true
            
            is StepCondition.EveryNTimes -> {
                val playCount = stepPlayCounts.getOrPut(stepId) { 0 }
                val shouldPlay = (playCount + condition.offset) % condition.n == 0
                if (shouldPlay) {
                    stepPlayCounts[stepId] = playCount + 1
                }
                shouldPlay
            }
            
            is StepCondition.FirstOfN -> {
                currentLoop % condition.n == 0
            }
            
            is StepCondition.LastOfN -> {
                (currentLoop + 1) % condition.n == 0
            }
            
            is StepCondition.NotOnBeat -> {
                val beatPosition = currentLoop % condition.beat
                beatPosition != 0
            }
        }
    }
}

/**
 * Extension functions for advanced step operations
 */

/**
 * Creates a step with probability settings
 */
fun Step.withProbability(probability: Float): Step {
    require(probability in 0f..1f) { "Probability must be between 0.0 and 1.0" }
    return copy(probability = probability)
}

/**
 * Creates a step with condition settings
 */
fun Step.withCondition(condition: StepCondition): Step {
    return copy(condition = condition)
}

/**
 * Creates a step with humanization settings
 */
fun Step.withHumanization(
    timingVariation: Float = 3f,
    velocityVariation: Float = 0.15f,
    enabled: Boolean = true
): Step {
    return copy(
        humanization = StepHumanization(
            timingVariation = timingVariation,
            velocityVariation = velocityVariation,
            enabled = enabled
        )
    )
}

/**
 * Creates a randomized version of this step
 */
fun Step.randomized(
    timingRange: Float = 5f,
    velocityRange: Int = 10,
    probabilityRange: Float = 0.2f
): Step {
    val random = Random.Default
    
    val randomTiming = microTiming + (random.nextFloat() * 2f - 1f) * timingRange
    val randomVelocity = velocity + random.nextInt(-velocityRange, velocityRange + 1)
    val randomProbability = probability + (random.nextFloat() * 2f - 1f) * probabilityRange
    
    return copy(
        microTiming = randomTiming.coerceIn(-50f, 50f),
        velocity = randomVelocity.coerceIn(1, 127),
        probability = randomProbability.coerceIn(0f, 1f)
    )
}

/**
 * Checks if this step has advanced features enabled
 */
fun Step.hasAdvancedFeatures(): Boolean {
    return probability < 1.0f || 
           condition != StepCondition.Always || 
           humanization.enabled ||
           microTiming != 0f
}