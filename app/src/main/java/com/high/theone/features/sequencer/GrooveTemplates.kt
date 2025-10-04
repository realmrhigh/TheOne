package com.high.theone.features.sequencer

import com.high.theone.model.Pattern
import com.high.theone.model.Step
import kotlinx.serialization.Serializable
import kotlin.math.*
import kotlin.random.Random

/**
 * Advanced groove template system for creating sophisticated timing feels
 */
class GrooveTemplates {
    
    /**
     * Applies a groove template to a pattern
     */
    fun applyGrooveTemplate(
        pattern: Pattern,
        template: GrooveTemplate
    ): Pattern {
        val newStepsMap = pattern.steps.mapValues { (_, steps) ->
            steps.map { step ->
                val grooveTiming = template.calculateStepTiming(step.position, pattern.length)
                step.copy(microTiming = grooveTiming)
            }
        }
        
        return pattern.copy(steps = newStepsMap).withModification()
    }
    
    /**
     * Analyzes a recorded pattern to extract groove characteristics
     */
    fun analyzePatternGroove(pattern: Pattern): GrooveAnalysis {
        val allSteps = pattern.steps.values.flatten()
        if (allSteps.isEmpty()) {
            return GrooveAnalysis()
        }
        
        // Calculate timing deviations from perfect grid
        val timingDeviations = allSteps.map { it.microTiming }
        val avgDeviation = timingDeviations.average().toFloat()
        val maxDeviation = timingDeviations.maxOrNull() ?: 0f
        val minDeviation = timingDeviations.minOrNull() ?: 0f
        
        // Detect swing pattern
        val offBeatSteps = allSteps.filter { it.position % 2 == 1 }
        val onBeatSteps = allSteps.filter { it.position % 2 == 0 }
        
        val avgOffBeatTiming = offBeatSteps.map { it.microTiming }.average().toFloat()
        val avgOnBeatTiming = onBeatSteps.map { it.microTiming }.average().toFloat()
        
        val detectedSwing = (avgOffBeatTiming - avgOnBeatTiming).coerceIn(-50f, 50f)
        
        // Calculate humanization level
        val timingVariance = timingDeviations.map { (it - avgDeviation).pow(2) }.average()
        val humanizationLevel = sqrt(timingVariance).toFloat().coerceIn(0f, 10f)
        
        return GrooveAnalysis(
            swingAmount = detectedSwing / 50f, // Normalize to 0-1 range
            humanizationLevel = humanizationLevel / 10f,
            avgTiming = avgDeviation,
            timingRange = maxDeviation - minDeviation,
            confidence = calculateConfidence(allSteps.size, timingVariance)
        )
    }
    
    /**
     * Creates a custom groove template from user input
     */
    fun createCustomGroove(
        name: String,
        swingType: SwingType = SwingType.LINEAR,
        swingAmount: Float = 0f,
        accentPattern: List<Float> = emptyList(),
        timingOffsets: List<Float> = emptyList(),
        humanization: Float = 0f
    ): GrooveTemplate {
        return CustomGrooveTemplate(
            name = name,
            swingType = swingType,
            swingAmount = swingAmount,
            accentPattern = accentPattern,
            timingOffsets = timingOffsets,
            humanization = humanization
        )
    }
    
    private fun calculateConfidence(stepCount: Int, variance: Double): Float {
        // More steps and lower variance = higher confidence
        val stepFactor = (stepCount / 16f).coerceIn(0f, 1f)
        val varianceFactor = (1f - (variance / 100f).toFloat()).coerceIn(0f, 1f)
        return (stepFactor * varianceFactor).coerceIn(0f, 1f)
    }
}

/**
 * Base interface for groove templates
 */
@Serializable
sealed class GrooveTemplate {
    abstract val name: String
    abstract val description: String
    
    /**
     * Calculates the timing offset for a specific step position
     */
    abstract fun calculateStepTiming(stepPosition: Int, patternLength: Int): Float
    
    /**
     * Gets the swing amount for display purposes
     */
    abstract val swingAmount: Float
}

/**
 * Built-in groove templates based on famous drum machines and styles
 */
@Serializable
data class BuiltInGrooveTemplate(
    override val name: String,
    override val description: String,
    val swingType: SwingType,
    override val swingAmount: Float,
    val accentPattern: List<Float> = emptyList(),
    val timingOffsets: List<Float> = emptyList()
) : GrooveTemplate() {
    
    override fun calculateStepTiming(stepPosition: Int, patternLength: Int): Float {
        var timing = 0f
        
        // Apply swing
        timing += calculateSwingOffset(stepPosition, swingAmount, swingType)
        
        // Apply accent pattern timing adjustments
        if (accentPattern.isNotEmpty()) {
            val accentIndex = stepPosition % accentPattern.size
            timing += accentPattern[accentIndex] * 2f // Convert accent to timing offset
        }
        
        // Apply custom timing offsets
        if (timingOffsets.isNotEmpty()) {
            val offsetIndex = stepPosition % timingOffsets.size
            timing += timingOffsets[offsetIndex]
        }
        
        return maxOf(-50f, minOf(50f, timing))
    }
    
    private fun calculateSwingOffset(stepPosition: Int, amount: Float, type: SwingType): Float {
        if (amount == 0f || stepPosition % 2 == 0) return 0f
        
        val clampedAmount = if (amount < 0f) 0f else if (amount > 1f) 1f else amount
        
        return when (type) {
            SwingType.LINEAR -> (clampedAmount * 20f)
            SwingType.EXPONENTIAL -> (clampedAmount * 25f)
            SwingType.LOGARITHMIC -> (ln(1f + clampedAmount * (E - 1f)).toFloat() * 15f)
            SwingType.SINE -> (sin(clampedAmount * PI / 2).toFloat() * 20f)
        }
    }
}

/**
 * Custom user-created groove template
 */
@Serializable
data class CustomGrooveTemplate(
    override val name: String,
    override val description: String = "Custom groove",
    val swingType: SwingType,
    override val swingAmount: Float,
    val accentPattern: List<Float>,
    val timingOffsets: List<Float>,
    val humanization: Float = 0f
) : GrooveTemplate() {
    
    override fun calculateStepTiming(stepPosition: Int, patternLength: Int): Float {
        var timing = 0f
        
        // Apply swing
        timing += calculateSwingOffset(stepPosition, swingAmount, swingType)
        
        // Apply accent pattern
        if (accentPattern.isNotEmpty()) {
            val accentIndex = stepPosition % accentPattern.size
            timing += accentPattern[accentIndex] * 2f
        }
        
        // Apply custom timing offsets
        if (timingOffsets.isNotEmpty()) {
            val offsetIndex = stepPosition % timingOffsets.size
            timing += timingOffsets[offsetIndex]
        }
        
        // Apply humanization
        if (humanization > 0f) {
            val randomOffset = Random.nextFloat() * 2f - 1f // -1 to 1
            timing += randomOffset * humanization * 5f // Max 5ms humanization
        }
        
        return maxOf(-50f, minOf(50f, timing))
    }
    
    private fun calculateSwingOffset(stepPosition: Int, amount: Float, type: SwingType): Float {
        if (amount == 0f || stepPosition % 2 == 0) return 0f
        
        return when (type) {
            SwingType.LINEAR -> amount * 20f
            SwingType.EXPONENTIAL -> (amount.pow(1.5f) * 25f)
            SwingType.LOGARITHMIC -> (ln(1f + amount * (E.toFloat() - 1f)) * 15f).toFloat()
            SwingType.SINE -> (sin(amount * PI.toFloat() / 2).toFloat() * 20f)
        }
    }
}

/**
 * Different types of swing algorithms
 */
@Serializable
enum class SwingType(val displayName: String, val description: String) {
    LINEAR("Linear", "Even swing progression"),
    EXPONENTIAL("Exponential", "Gradual then sharp swing increase"),
    LOGARITHMIC("Logarithmic", "Sharp then gradual swing increase"),
    SINE("Sine Wave", "Smooth, musical swing curve")
}

/**
 * Analysis results from pattern groove detection
 */
data class GrooveAnalysis(
    val swingAmount: Float = 0f,
    val humanizationLevel: Float = 0f,
    val avgTiming: Float = 0f,
    val timingRange: Float = 0f,
    val confidence: Float = 0f
) {
    /**
     * Returns a suggested groove template based on analysis
     */
    fun getSuggestedTemplate(): GrooveTemplate? {
        if (confidence < 0.3f) return null
        
        return when {
            swingAmount > 0.3f -> GroovePresets.HEAVY_SWING
            swingAmount > 0.15f -> GroovePresets.MEDIUM_SWING
            swingAmount > 0.05f -> GroovePresets.LIGHT_SWING
            humanizationLevel > 0.5f -> GroovePresets.HUMAN_FEEL
            else -> GroovePresets.STRAIGHT
        }
    }
}

/**
 * Predefined groove templates based on famous drum machines and musical styles
 */
object GroovePresets {
    
    val STRAIGHT = BuiltInGrooveTemplate(
        name = "Straight",
        description = "Perfect timing, no swing",
        swingType = SwingType.LINEAR,
        swingAmount = 0f
    )
    
    val LIGHT_SWING = BuiltInGrooveTemplate(
        name = "Light Swing",
        description = "Subtle swing feel",
        swingType = SwingType.LINEAR,
        swingAmount = 0.08f
    )
    
    val MEDIUM_SWING = BuiltInGrooveTemplate(
        name = "Medium Swing",
        description = "Classic swing feel",
        swingType = SwingType.LINEAR,
        swingAmount = 0.15f
    )
    
    val HEAVY_SWING = BuiltInGrooveTemplate(
        name = "Heavy Swing",
        description = "Strong swing feel",
        swingType = SwingType.LINEAR,
        swingAmount = 0.25f
    )
    
    val SHUFFLE = BuiltInGrooveTemplate(
        name = "Shuffle",
        description = "Classic shuffle rhythm",
        swingType = SwingType.LINEAR,
        swingAmount = 0.67f
    )
    
    val MPC_60 = BuiltInGrooveTemplate(
        name = "MPC 60%",
        description = "Classic MPC-style swing",
        swingType = SwingType.EXPONENTIAL,
        swingAmount = 0.2f
    )
    
    val MPC_HEAVY = BuiltInGrooveTemplate(
        name = "MPC Heavy",
        description = "Heavy MPC swing feel",
        swingType = SwingType.EXPONENTIAL,
        swingAmount = 0.32f
    )
    
    val TRAP_FEEL = BuiltInGrooveTemplate(
        name = "Trap Feel",
        description = "Modern trap timing",
        swingType = SwingType.LINEAR,
        swingAmount = 0.05f,
        accentPattern = listOf(0f, -0.5f, 0f, 0.5f) // Slight push/pull on hi-hats
    )
    
    val BOOM_BAP = BuiltInGrooveTemplate(
        name = "Boom Bap",
        description = "Classic hip-hop feel",
        swingType = SwingType.LINEAR,
        swingAmount = 0.12f,
        accentPattern = listOf(0f, 0f, -1f, 0f) // Slightly rushed snare
    )
    
    val REGGAE = BuiltInGrooveTemplate(
        name = "Reggae",
        description = "Reggae skank timing",
        swingType = SwingType.LINEAR,
        swingAmount = 0f,
        timingOffsets = listOf(0f, 2f, 0f, -1f) // Characteristic reggae timing
    )
    
    val LATIN = BuiltInGrooveTemplate(
        name = "Latin",
        description = "Latin percussion feel",
        swingType = SwingType.SINE,
        swingAmount = 0.1f,
        accentPattern = listOf(0f, 0.5f, -0.5f, 0f)
    )
    
    val HUMAN_FEEL = BuiltInGrooveTemplate(
        name = "Human Feel",
        description = "Natural human timing variations",
        swingType = SwingType.LINEAR,
        swingAmount = 0.05f,
        timingOffsets = listOf(-0.5f, 1f, -1f, 0.5f, 0f, -0.5f, 1f, 0f)
    )
    
    val DRUNK = BuiltInGrooveTemplate(
        name = "Drunk",
        description = "Heavily humanized, loose timing",
        swingType = SwingType.LINEAR,
        swingAmount = 0.1f,
        timingOffsets = listOf(-2f, 3f, -1f, 2f, -3f, 1f, -1f, 2f)
    )
    
    val TIGHT = BuiltInGrooveTemplate(
        name = "Tight",
        description = "Precise, quantized feel",
        swingType = SwingType.LINEAR,
        swingAmount = 0f,
        timingOffsets = listOf(0f, 0f, 0f, 0f) // Perfect timing
    )
    
    /**
     * All available groove presets
     */
    val ALL_PRESETS = listOf(
        STRAIGHT, LIGHT_SWING, MEDIUM_SWING, HEAVY_SWING, SHUFFLE,
        MPC_60, MPC_HEAVY, TRAP_FEEL, BOOM_BAP, REGGAE, LATIN,
        HUMAN_FEEL, DRUNK, TIGHT
    )
    
    /**
     * Gets a preset by name
     */
    fun getPresetByName(name: String): GrooveTemplate? {
        return ALL_PRESETS.find { it.name == name }
    }
    
    /**
     * Gets presets by category
     */
    fun getSwingPresets(): List<GrooveTemplate> {
        return listOf(STRAIGHT, LIGHT_SWING, MEDIUM_SWING, HEAVY_SWING, SHUFFLE)
    }
    
    fun getMpcPresets(): List<GrooveTemplate> {
        return listOf(MPC_60, MPC_HEAVY)
    }
    
    fun getGenrePresets(): List<GrooveTemplate> {
        return listOf(TRAP_FEEL, BOOM_BAP, REGGAE, LATIN)
    }
    
    fun getHumanizationPresets(): List<GrooveTemplate> {
        return listOf(HUMAN_FEEL, DRUNK, TIGHT)
    }
}

/**
 * Extension functions for groove template operations
 */

/**
 * Applies a groove template to this pattern
 */
fun Pattern.withGroove(template: GrooveTemplate): Pattern {
    return GrooveTemplates().applyGrooveTemplate(this, template)
}

/**
 * Analyzes the groove characteristics of this pattern
 */
fun Pattern.analyzeGroove(): GrooveAnalysis {
    return GrooveTemplates().analyzePatternGroove(this)
}

/**
 * Creates a groove template from this pattern's timing
 */
fun Pattern.extractGrooveTemplate(name: String): GrooveTemplate? {
    val analysis = analyzeGroove()
    if (analysis.confidence < 0.5f) return null
    
    return CustomGrooveTemplate(
        name = name,
        description = "Extracted from pattern",
        swingType = SwingType.LINEAR,
        swingAmount = analysis.swingAmount,
        accentPattern = emptyList(),
        timingOffsets = emptyList(),
        humanization = analysis.humanizationLevel
    )
}