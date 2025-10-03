package com.high.theone.features.sequencer

import com.high.theone.model.Quantization
import kotlin.math.roundToLong

/**
 * Handles all timing calculations for the step sequencer including swing, tempo conversion,
 * and step duration calculations
 */
class TimingCalculator {
    
    /**
     * Calculates the duration of a single step in milliseconds based on tempo and quantization
     */
    fun calculateStepDuration(tempo: Float, quantization: Quantization = Quantization.SIXTEENTH): Long {
        // Calculate quarter note duration in milliseconds
        val quarterNoteDurationMs = (60000f / tempo)
        
        // Calculate step duration based on quantization
        return when (quantization) {
            Quantization.QUARTER -> quarterNoteDurationMs.roundToLong()
            Quantization.EIGHTH -> (quarterNoteDurationMs / 2f).roundToLong()
            Quantization.SIXTEENTH -> (quarterNoteDurationMs / 4f).roundToLong()
            Quantization.THIRTY_SECOND -> (quarterNoteDurationMs / 8f).roundToLong()
            Quantization.OFF -> quarterNoteDurationMs.roundToLong() // Default to quarter note
        }
    }
    
    /**
     * Calculates the absolute timing for a specific step considering swing
     */
    fun calculateStepTiming(
        stepIndex: Int,
        baseTempo: Float,
        swingAmount: Float,
        patternLength: Int = 16
    ): Long {
        val baseStepDuration = calculateStepDuration(baseTempo, Quantization.SIXTEENTH)
        val stepStartTime = stepIndex * baseStepDuration
        
        // Apply swing to off-beat steps (odd numbered steps in 16th note grid)
        return if (stepIndex % 2 == 1) {
            // Off-beat steps get delayed by swing amount
            val swingDelay = (baseStepDuration * swingAmount).roundToLong()
            stepStartTime + swingDelay
        } else {
            stepStartTime
        }
    }
    
    /**
     * Calculates the total pattern duration in milliseconds
     */
    fun calculatePatternDuration(
        patternLength: Int,
        tempo: Float,
        swingAmount: Float = 0f
    ): Long {
        val baseStepDuration = calculateStepDuration(tempo, Quantization.SIXTEENTH)
        val baseDuration = patternLength * baseStepDuration
        
        // Add swing compensation - swing affects timing but not total duration significantly
        // The last step timing determines actual pattern length
        val lastStepTiming = calculateStepTiming(patternLength - 1, tempo, swingAmount, patternLength)
        return lastStepTiming + baseStepDuration
    }
    
    /**
     * Converts a timestamp to the nearest step position with quantization
     */
    fun quantizeTimestampToStep(
        timestamp: Long,
        patternStartTime: Long,
        tempo: Float,
        patternLength: Int,
        quantization: Quantization = Quantization.SIXTEENTH
    ): Int {
        val relativeTime = timestamp - patternStartTime
        val stepDuration = calculateStepDuration(tempo, quantization)
        
        if (stepDuration <= 0) return 0
        
        val rawStep = (relativeTime / stepDuration).toInt()
        return (rawStep % patternLength).coerceIn(0, patternLength - 1)
    }
    
    /**
     * Calculates the progress within the current step (0.0 to 1.0)
     */
    fun calculateStepProgress(
        currentTime: Long,
        stepStartTime: Long,
        stepDuration: Long
    ): Float {
        if (stepDuration <= 0) return 0f
        
        val elapsed = currentTime - stepStartTime
        return (elapsed.toFloat() / stepDuration).coerceIn(0f, 1f)
    }
}

/**
 * Specialized calculator for swing and groove timing
 */
class SwingCalculator {
    
    companion object {
        /**
         * Predefined groove presets with swing amounts
         */
        val GROOVE_PRESETS = mapOf(
            "None" to 0f,
            "Light" to 0.08f,
            "Medium" to 0.15f,
            "Heavy" to 0.25f,
            "Extreme" to 0.4f,
            "Shuffle" to 0.67f // Classic shuffle feel
        )
        
        /**
         * MPC-style swing presets (percentage values)
         */
        val MPC_SWING_PRESETS = mapOf(
            "50%" to 0f,      // No swing (straight)
            "54%" to 0.08f,   // Light swing
            "58%" to 0.16f,   // Medium swing
            "62%" to 0.24f,   // Heavy swing
            "66%" to 0.32f,   // Very heavy swing
            "75%" to 0.5f     // Maximum practical swing
        )
    }
    
    /**
     * Calculates swing delay for a specific step
     */
    fun calculateSwingDelay(
        stepIndex: Int,
        baseStepDuration: Long,
        swingAmount: Float
    ): Long {
        // Only apply swing to off-beat steps (odd indices in 16th note grid)
        return if (stepIndex % 2 == 1) {
            (baseStepDuration * swingAmount).roundToLong()
        } else {
            0L
        }
    }
    
    /**
     * Calculates timing for all steps in a pattern with swing applied
     */
    fun calculateSwingPattern(
        patternLength: Int,
        tempo: Float,
        swingAmount: Float
    ): List<Long> {
        val timingCalculator = TimingCalculator()
        val baseStepDuration = timingCalculator.calculateStepDuration(tempo, Quantization.SIXTEENTH)
        
        return (0 until patternLength).map { stepIndex ->
            val baseTime = stepIndex * baseStepDuration
            val swingDelay = calculateSwingDelay(stepIndex, baseStepDuration, swingAmount)
            baseTime + swingDelay
        }
    }
    
    /**
     * Converts swing percentage (50-75%) to internal swing amount (0.0-0.5)
     */
    fun swingPercentageToAmount(percentage: Int): Float {
        val clampedPercentage = percentage.coerceIn(50, 75)
        return (clampedPercentage - 50) / 50f
    }
    
    /**
     * Converts internal swing amount to percentage for display
     */
    fun swingAmountToPercentage(amount: Float): Int {
        val clampedAmount = amount.coerceIn(0f, 0.5f)
        return (50 + (clampedAmount * 50)).toInt()
    }
    
    /**
     * Applies humanization to timing (subtle random variations)
     */
    fun applyHumanization(
        baseTimings: List<Long>,
        humanizationAmount: Float = 0.1f // 0.0 to 1.0
    ): List<Long> {
        if (humanizationAmount <= 0f) return baseTimings
        
        return baseTimings.mapIndexed { index, timing ->
            // Calculate maximum deviation (percentage of step duration)
            val stepDuration = if (index < baseTimings.size - 1) {
                baseTimings[index + 1] - timing
            } else {
                timing - baseTimings.getOrElse(index - 1) { 0L }
            }
            
            val maxDeviation = (stepDuration * humanizationAmount * 0.1f).toLong()
            val randomDeviation = (-maxDeviation..maxDeviation).random()
            
            (timing + randomDeviation).coerceAtLeast(0L)
        }
    }
}

/**
 * Utility class for tempo-related calculations
 */
object TempoUtils {
    
    /**
     * Converts BPM to milliseconds per beat
     */
    fun bpmToMillisPerBeat(bpm: Float): Float = 60000f / bpm
    
    /**
     * Converts milliseconds per beat to BPM
     */
    fun millisPerBeatToBpm(millisPerBeat: Float): Float = 60000f / millisPerBeat
    
    /**
     * Calculates the number of steps that fit in a given time duration
     */
    fun calculateStepsInDuration(
        durationMs: Long,
        tempo: Float,
        quantization: Quantization = Quantization.SIXTEENTH
    ): Int {
        val calculator = TimingCalculator()
        val stepDuration = calculator.calculateStepDuration(tempo, quantization)
        return if (stepDuration > 0) (durationMs / stepDuration).toInt() else 0
    }
    
    /**
     * Validates tempo is within acceptable range
     */
    fun isValidTempo(tempo: Float): Boolean = tempo in 60f..200f
    
    /**
     * Clamps tempo to valid range
     */
    fun clampTempo(tempo: Float): Float = tempo.coerceIn(60f, 200f)
}