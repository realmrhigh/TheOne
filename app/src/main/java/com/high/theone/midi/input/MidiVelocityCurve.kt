package com.high.theone.midi.input

import com.high.theone.midi.model.MidiCurve
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * MIDI velocity curve calculations and transformations for velocity sensitivity and scaling.
 * Supports linear, exponential, logarithmic, and S-curve transformations with user-configurable
 * velocity response curves.
 * 
 * Requirements: 1.3, 4.2
 */
@Singleton
class MidiVelocityCurve @Inject constructor() {
    
    // Velocity sensitivity settings
    private var velocitySensitivity: Float = 1.0f
    private var velocityOffset: Float = 0.0f
    private var velocityScale: Float = 1.0f
    
    // Curve parameters
    private var exponentialFactor: Float = 2.0f
    private var logarithmicBase: Float = 10.0f
    private var sCurveSharpness: Float = 3.0f
    
    /**
     * Transform velocity using the specified curve type
     */
    fun transformVelocity(inputVelocity: Int, curve: MidiCurve): Int {
        // Normalize input velocity to 0.0-1.0 range
        val normalizedInput = inputVelocity.coerceIn(0, 127) / 127.0f
        
        // Apply curve transformation
        val curveOutput = when (curve) {
            MidiCurve.LINEAR -> applyLinearCurve(normalizedInput)
            MidiCurve.EXPONENTIAL -> applyExponentialCurve(normalizedInput)
            MidiCurve.LOGARITHMIC -> applyLogarithmicCurve(normalizedInput)
            MidiCurve.S_CURVE -> applySCurve(normalizedInput)
        }
        
        // Apply sensitivity and scaling
        val scaledOutput = applyVelocityScaling(curveOutput)
        
        // Convert back to MIDI velocity range (0-127)
        return (scaledOutput * 127.0f).roundToInt().coerceIn(0, 127)
    }
    
    /**
     * Transform velocity with custom curve parameters
     */
    fun transformVelocityCustom(
        inputVelocity: Int,
        curve: MidiCurve,
        sensitivity: Float = velocitySensitivity,
        offset: Float = velocityOffset,
        scale: Float = velocityScale
    ): Int {
        val normalizedInput = inputVelocity.coerceIn(0, 127) / 127.0f
        
        val curveOutput = when (curve) {
            MidiCurve.LINEAR -> applyLinearCurve(normalizedInput)
            MidiCurve.EXPONENTIAL -> applyExponentialCurve(normalizedInput)
            MidiCurve.LOGARITHMIC -> applyLogarithmicCurve(normalizedInput)
            MidiCurve.S_CURVE -> applySCurve(normalizedInput)
        }
        
        // Apply custom scaling parameters
        val scaledOutput = applyCustomScaling(curveOutput, sensitivity, offset, scale)
        
        return (scaledOutput * 127.0f).roundToInt().coerceIn(0, 127)
    }
    
    /**
     * Set global velocity sensitivity (0.1 to 3.0)
     */
    fun setVelocitySensitivity(sensitivity: Float) {
        this.velocitySensitivity = sensitivity.coerceIn(0.1f, 3.0f)
    }
    
    /**
     * Set velocity offset (-0.5 to 0.5)
     */
    fun setVelocityOffset(offset: Float) {
        this.velocityOffset = offset.coerceIn(-0.5f, 0.5f)
    }
    
    /**
     * Set velocity scale (0.1 to 2.0)
     */
    fun setVelocityScale(scale: Float) {
        this.velocityScale = scale.coerceIn(0.1f, 2.0f)
    }
    
    /**
     * Set exponential curve factor (0.5 to 5.0)
     */
    fun setExponentialFactor(factor: Float) {
        this.exponentialFactor = factor.coerceIn(0.5f, 5.0f)
    }
    
    /**
     * Set logarithmic curve base (2.0 to 20.0)
     */
    fun setLogarithmicBase(base: Float) {
        this.logarithmicBase = base.coerceIn(2.0f, 20.0f)
    }
    
    /**
     * Set S-curve sharpness (0.5 to 10.0)
     */
    fun setSCurveSharpness(sharpness: Float) {
        this.sCurveSharpness = sharpness.coerceIn(0.5f, 10.0f)
    }
    
    /**
     * Get current velocity curve configuration
     */
    fun getVelocityConfiguration(): VelocityConfiguration {
        return VelocityConfiguration(
            sensitivity = velocitySensitivity,
            offset = velocityOffset,
            scale = velocityScale,
            exponentialFactor = exponentialFactor,
            logarithmicBase = logarithmicBase,
            sCurveSharpness = sCurveSharpness
        )
    }
    
    /**
     * Set velocity configuration from saved settings
     */
    fun setVelocityConfiguration(config: VelocityConfiguration) {
        setVelocitySensitivity(config.sensitivity)
        setVelocityOffset(config.offset)
        setVelocityScale(config.scale)
        setExponentialFactor(config.exponentialFactor)
        setLogarithmicBase(config.logarithmicBase)
        setSCurveSharpness(config.sCurveSharpness)
    }
    
    /**
     * Generate velocity curve lookup table for real-time performance
     */
    fun generateLookupTable(curve: MidiCurve): IntArray {
        return IntArray(128) { velocity ->
            transformVelocity(velocity, curve)
        }
    }
    
    /**
     * Calculate curve response at specific input value (for UI visualization)
     */
    fun calculateCurveResponse(inputValue: Float, curve: MidiCurve): Float {
        val normalizedInput = inputValue.coerceIn(0.0f, 1.0f)
        
        return when (curve) {
            MidiCurve.LINEAR -> applyLinearCurve(normalizedInput)
            MidiCurve.EXPONENTIAL -> applyExponentialCurve(normalizedInput)
            MidiCurve.LOGARITHMIC -> applyLogarithmicCurve(normalizedInput)
            MidiCurve.S_CURVE -> applySCurve(normalizedInput)
        }
    }
    
    /**
     * Generate curve data points for UI visualization
     */
    fun generateCurvePoints(curve: MidiCurve, pointCount: Int = 100): List<CurvePoint> {
        return (0 until pointCount).map { i ->
            val input = i.toFloat() / (pointCount - 1)
            val output = calculateCurveResponse(input, curve)
            CurvePoint(input, output)
        }
    }
    
    // Private curve implementation methods
    
    private fun applyLinearCurve(input: Float): Float {
        return input
    }
    
    private fun applyExponentialCurve(input: Float): Float {
        return input.pow(exponentialFactor)
    }
    
    private fun applyLogarithmicCurve(input: Float): Float {
        if (input <= 0.0f) return 0.0f
        return ln(1.0f + input * (logarithmicBase - 1.0f)) / ln(logarithmicBase)
    }
    
    private fun applySCurve(input: Float): Float {
        // Sigmoid-based S-curve
        val x = (input - 0.5f) * sCurveSharpness
        return 1.0f / (1.0f + exp(-x))
    }
    
    private fun applyVelocityScaling(curveOutput: Float): Float {
        // Apply sensitivity (affects curve steepness)
        val sensitized = curveOutput.pow(1.0f / velocitySensitivity)
        
        // Apply offset
        val offset = sensitized + velocityOffset
        
        // Apply scale
        val scaled = offset * velocityScale
        
        // Ensure output stays in valid range
        return scaled.coerceIn(0.0f, 1.0f)
    }
    
    private fun applyCustomScaling(
        curveOutput: Float,
        sensitivity: Float,
        offset: Float,
        scale: Float
    ): Float {
        val sensitized = curveOutput.pow(1.0f / sensitivity)
        val offsetApplied = sensitized + offset
        val scaled = offsetApplied * scale
        return scaled.coerceIn(0.0f, 1.0f)
    }
}

/**
 * Velocity curve configuration settings
 */
data class VelocityConfiguration(
    val sensitivity: Float = 1.0f,
    val offset: Float = 0.0f,
    val scale: Float = 1.0f,
    val exponentialFactor: Float = 2.0f,
    val logarithmicBase: Float = 10.0f,
    val sCurveSharpness: Float = 3.0f
) {
    init {
        require(sensitivity in 0.1f..3.0f) { "Sensitivity must be between 0.1 and 3.0" }
        require(offset in -0.5f..0.5f) { "Offset must be between -0.5 and 0.5" }
        require(scale in 0.1f..2.0f) { "Scale must be between 0.1 and 2.0" }
        require(exponentialFactor in 0.5f..5.0f) { "Exponential factor must be between 0.5 and 5.0" }
        require(logarithmicBase in 2.0f..20.0f) { "Logarithmic base must be between 2.0 and 20.0" }
        require(sCurveSharpness in 0.5f..10.0f) { "S-curve sharpness must be between 0.5 and 10.0" }
    }
}

/**
 * Point on a velocity curve for visualization
 */
data class CurvePoint(
    val input: Float,
    val output: Float
)

/**
 * Predefined velocity curve presets
 */
object VelocityCurvePresets {
    
    val SOFT_TOUCH = VelocityConfiguration(
        sensitivity = 0.7f,
        offset = 0.1f,
        scale = 0.9f,
        exponentialFactor = 1.5f
    )
    
    val HARD_TOUCH = VelocityConfiguration(
        sensitivity = 1.5f,
        offset = -0.1f,
        scale = 1.1f,
        exponentialFactor = 2.5f
    )
    
    val PIANO_LIKE = VelocityConfiguration(
        sensitivity = 1.2f,
        offset = 0.0f,
        scale = 1.0f,
        exponentialFactor = 1.8f
    )
    
    val DRUM_MACHINE = VelocityConfiguration(
        sensitivity = 0.8f,
        offset = 0.05f,
        scale = 1.0f,
        exponentialFactor = 2.2f
    )
    
    val CONTROLLER_PAD = VelocityConfiguration(
        sensitivity = 1.0f,
        offset = 0.0f,
        scale = 1.0f,
        exponentialFactor = 2.0f
    )
    
    /**
     * Get all available presets
     */
    fun getAllPresets(): Map<String, VelocityConfiguration> {
        return mapOf(
            "Soft Touch" to SOFT_TOUCH,
            "Hard Touch" to HARD_TOUCH,
            "Piano-like" to PIANO_LIKE,
            "Drum Machine" to DRUM_MACHINE,
            "Controller Pad" to CONTROLLER_PAD
        )
    }
}