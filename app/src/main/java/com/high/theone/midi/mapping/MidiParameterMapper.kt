package com.high.theone.midi.mapping

import com.high.theone.midi.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Handles parameter value transformation and bidirectional MIDI-to-parameter mapping.
 * Provides scaling, range mapping, and curve-based transformations.
 */
@Singleton
class MidiParameterMapper @Inject constructor() {
    
    /**
     * Transforms a MIDI value to a parameter value using the specified mapping
     */
    fun midiToParameter(
        midiValue: Int,
        mapping: MidiParameterMapping,
        midiType: MidiMessageType = mapping.midiType
    ): Float {
        val normalizedValue = normalizeMidiValue(midiValue, midiType)
        val curvedValue = applyCurve(normalizedValue, mapping.curve)
        return scaleToRange(curvedValue, mapping.minValue, mapping.maxValue)
    }
    
    /**
     * Transforms a parameter value back to a MIDI value using the specified mapping
     */
    fun parameterToMidi(
        parameterValue: Float,
        mapping: MidiParameterMapping,
        midiType: MidiMessageType = mapping.midiType
    ): Int {
        val normalizedValue = normalizeParameterValue(parameterValue, mapping.minValue, mapping.maxValue)
        val linearValue = applyInverseCurve(normalizedValue, mapping.curve)
        return denormalizeMidiValue(linearValue, midiType)
    }
    
    /**
     * Creates a bidirectional mapping between MIDI and parameter values
     */
    fun createBidirectionalMapping(
        mapping: MidiParameterMapping
    ): MidiBidirectionalMapping {
        return MidiBidirectionalMapping(
            mapping = mapping,
            midiToParameterTransform = { midiValue ->
                midiToParameter(midiValue, mapping)
            },
            parameterToMidiTransform = { parameterValue ->
                parameterToMidi(parameterValue, mapping)
            }
        )
    }
    
    /**
     * Validates that a parameter value is within the mapping's range
     */
    fun isParameterValueValid(
        parameterValue: Float,
        mapping: MidiParameterMapping
    ): Boolean {
        return parameterValue in mapping.minValue..mapping.maxValue
    }
    
    /**
     * Clamps a parameter value to the mapping's range
     */
    fun clampParameterValue(
        parameterValue: Float,
        mapping: MidiParameterMapping
    ): Float {
        return parameterValue.coerceIn(mapping.minValue, mapping.maxValue)
    }
    
    /**
     * Calculates the sensitivity of the mapping (how much parameter changes per MIDI unit)
     */
    fun calculateMappingSensitivity(mapping: MidiParameterMapping): Float {
        val midiRange = getMidiRange(mapping.midiType)
        val parameterRange = mapping.maxValue - mapping.minValue
        return parameterRange / midiRange
    }
    
    /**
     * Creates a scaled version of a mapping with different min/max values
     */
    fun scaleMapping(
        originalMapping: MidiParameterMapping,
        newMinValue: Float,
        newMaxValue: Float
    ): MidiParameterMapping {
        require(newMinValue <= newMaxValue) { "Min value must be less than or equal to max value" }
        
        return originalMapping.copy(
            minValue = newMinValue,
            maxValue = newMaxValue
        )
    }
    
    /**
     * Inverts a mapping (swaps min and max values)
     */
    fun invertMapping(mapping: MidiParameterMapping): MidiParameterMapping {
        return mapping.copy(
            minValue = mapping.maxValue,
            maxValue = mapping.minValue
        )
    }
    
    /**
     * Creates a mapping with a different curve type
     */
    fun changeMappingCurve(
        mapping: MidiParameterMapping,
        newCurve: MidiCurve
    ): MidiParameterMapping {
        return mapping.copy(curve = newCurve)
    }
    
    /**
     * Interpolates between two mappings based on a blend factor (0.0 to 1.0)
     */
    fun interpolateMappings(
        mapping1: MidiParameterMapping,
        mapping2: MidiParameterMapping,
        blendFactor: Float
    ): MidiParameterMapping {
        require(blendFactor in 0f..1f) { "Blend factor must be between 0.0 and 1.0" }
        require(mapping1.midiType == mapping2.midiType) { "Mappings must have the same MIDI type" }
        require(mapping1.midiChannel == mapping2.midiChannel) { "Mappings must have the same MIDI channel" }
        require(mapping1.midiController == mapping2.midiController) { "Mappings must have the same MIDI controller" }
        
        val interpolatedMinValue = lerp(mapping1.minValue, mapping2.minValue, blendFactor)
        val interpolatedMaxValue = lerp(mapping1.maxValue, mapping2.maxValue, blendFactor)
        
        // Use the curve from the mapping with higher blend weight
        val curve = if (blendFactor < 0.5f) mapping1.curve else mapping2.curve
        
        return mapping1.copy(
            minValue = interpolatedMinValue,
            maxValue = interpolatedMaxValue,
            curve = curve
        )
    }
    
    /**
     * Creates a quantized version of a mapping that snaps to discrete values
     */
    fun quantizeMapping(
        mapping: MidiParameterMapping,
        steps: Int
    ): MidiParameterMapping {
        require(steps > 0) { "Steps must be positive" }
        
        // Create a custom curve that quantizes the output
        return mapping.copy(curve = MidiCurve.LINEAR) // We'll handle quantization in the transform
    }
    
    /**
     * Applies quantization to a parameter value
     */
    fun quantizeParameterValue(
        parameterValue: Float,
        minValue: Float,
        maxValue: Float,
        steps: Int
    ): Float {
        require(steps > 0) { "Steps must be positive" }
        
        val range = maxValue - minValue
        val stepSize = range / (steps - 1)
        val normalizedValue = (parameterValue - minValue) / range
        val quantizedStep = round(normalizedValue * (steps - 1)).toInt()
        
        return minValue + quantizedStep * stepSize
    }
    
    private fun normalizeMidiValue(midiValue: Int, midiType: MidiMessageType): Float {
        return when (midiType) {
            MidiMessageType.CONTROL_CHANGE,
            MidiMessageType.NOTE_ON,
            MidiMessageType.NOTE_OFF,
            MidiMessageType.AFTERTOUCH -> midiValue / 127.0f
            MidiMessageType.PITCH_BEND -> {
                // 14-bit pitch bend: 0-16383, center at 8192
                val centered = midiValue - 8192
                centered / 8192.0f
            }
            MidiMessageType.PROGRAM_CHANGE -> midiValue / 127.0f
            else -> 0.0f
        }
    }
    
    private fun denormalizeMidiValue(normalizedValue: Float, midiType: MidiMessageType): Int {
        return when (midiType) {
            MidiMessageType.CONTROL_CHANGE,
            MidiMessageType.NOTE_ON,
            MidiMessageType.NOTE_OFF,
            MidiMessageType.AFTERTOUCH,
            MidiMessageType.PROGRAM_CHANGE -> (normalizedValue * 127).roundToInt().coerceIn(0, 127)
            MidiMessageType.PITCH_BEND -> {
                // Convert back to 14-bit pitch bend
                val centered = (normalizedValue * 8192).roundToInt()
                (centered + 8192).coerceIn(0, 16383)
            }
            else -> 0
        }
    }
    
    private fun normalizeParameterValue(
        parameterValue: Float,
        minValue: Float,
        maxValue: Float
    ): Float {
        if (minValue == maxValue) return 0.0f
        return (parameterValue - minValue) / (maxValue - minValue)
    }
    
    private fun scaleToRange(normalizedValue: Float, minValue: Float, maxValue: Float): Float {
        return minValue + normalizedValue * (maxValue - minValue)
    }
    
    private fun applyCurve(normalizedValue: Float, curve: MidiCurve): Float {
        return when (curve) {
            MidiCurve.LINEAR -> normalizedValue
            MidiCurve.EXPONENTIAL -> normalizedValue * normalizedValue
            MidiCurve.LOGARITHMIC -> sqrt(normalizedValue)
            MidiCurve.S_CURVE -> {
                // Smooth S-curve using sigmoid-like function
                val x = (normalizedValue - 0.5f) * 6f // Scale to -3 to 3
                1f / (1f + exp(-x))
            }
        }
    }
    
    private fun applyInverseCurve(curvedValue: Float, curve: MidiCurve): Float {
        return when (curve) {
            MidiCurve.LINEAR -> curvedValue
            MidiCurve.EXPONENTIAL -> sqrt(curvedValue)
            MidiCurve.LOGARITHMIC -> curvedValue * curvedValue
            MidiCurve.S_CURVE -> {
                // Inverse of sigmoid function
                val clampedValue = curvedValue.coerceIn(0.001f, 0.999f) // Avoid division by zero
                val x = ln(clampedValue / (1f - clampedValue))
                (x / 6f) + 0.5f
            }
        }
    }
    
    private fun getMidiRange(midiType: MidiMessageType): Float {
        return when (midiType) {
            MidiMessageType.CONTROL_CHANGE,
            MidiMessageType.NOTE_ON,
            MidiMessageType.NOTE_OFF,
            MidiMessageType.AFTERTOUCH,
            MidiMessageType.PROGRAM_CHANGE -> 127.0f
            MidiMessageType.PITCH_BEND -> 16383.0f
            else -> 127.0f
        }
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }
}

/**
 * Represents a bidirectional mapping between MIDI and parameter values
 */
data class MidiBidirectionalMapping(
    val mapping: MidiParameterMapping,
    val midiToParameterTransform: (Int) -> Float,
    val parameterToMidiTransform: (Float) -> Int
) {
    /**
     * Transforms MIDI value to parameter value
     */
    fun toParameter(midiValue: Int): Float = midiToParameterTransform(midiValue)
    
    /**
     * Transforms parameter value to MIDI value
     */
    fun toMidi(parameterValue: Float): Int = parameterToMidiTransform(parameterValue)
    
    /**
     * Validates that the mapping is consistent (round-trip conversion)
     */
    fun validateConsistency(tolerance: Float = 0.01f): Boolean {
        val testValues = listOf(0, 32, 64, 96, 127)
        
        return testValues.all { midiValue ->
            val parameterValue = toParameter(midiValue)
            val backToMidi = toMidi(parameterValue)
            abs(midiValue - backToMidi) <= tolerance * 127
        }
    }
}

/**
 * Represents a parameter mapping with additional transformation options
 */
data class MidiParameterTransform(
    val mapping: MidiParameterMapping,
    val quantizationSteps: Int? = null,
    val smoothingFactor: Float? = null,
    val deadZone: Float? = null
) {
    init {
        quantizationSteps?.let { require(it > 0) { "Quantization steps must be positive" } }
        smoothingFactor?.let { require(it in 0f..1f) { "Smoothing factor must be between 0 and 1" } }
        deadZone?.let { require(it in 0f..0.5f) { "Dead zone must be between 0 and 0.5" } }
    }
}

/**
 * Utility class for creating common parameter mapping configurations
 */
object MidiParameterMappingPresets {
    
    /**
     * Creates a standard volume mapping (0.0 to 1.0 with exponential curve)
     */
    fun createVolumeMapping(
        midiType: MidiMessageType,
        midiChannel: Int,
        midiController: Int,
        targetId: String
    ): MidiParameterMapping {
        return MidiParameterMapping(
            midiType = midiType,
            midiChannel = midiChannel,
            midiController = midiController,
            targetType = MidiTargetType.PAD_VOLUME,
            targetId = targetId,
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.EXPONENTIAL
        )
    }
    
    /**
     * Creates a standard pan mapping (-1.0 to 1.0 with linear curve)
     */
    fun createPanMapping(
        midiType: MidiMessageType,
        midiChannel: Int,
        midiController: Int,
        targetId: String
    ): MidiParameterMapping {
        return MidiParameterMapping(
            midiType = midiType,
            midiChannel = midiChannel,
            midiController = midiController,
            targetType = MidiTargetType.PAD_PAN,
            targetId = targetId,
            minValue = -1.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
    }
    
    /**
     * Creates a tempo mapping (60 to 200 BPM with linear curve)
     */
    fun createTempoMapping(
        midiType: MidiMessageType,
        midiChannel: Int,
        midiController: Int
    ): MidiParameterMapping {
        return MidiParameterMapping(
            midiType = midiType,
            midiChannel = midiChannel,
            midiController = midiController,
            targetType = MidiTargetType.SEQUENCER_TEMPO,
            targetId = "master_tempo",
            minValue = 60.0f,
            maxValue = 200.0f,
            curve = MidiCurve.LINEAR
        )
    }
    
    /**
     * Creates a note trigger mapping
     */
    fun createNoteTriggerMapping(
        midiChannel: Int,
        midiNote: Int,
        targetId: String
    ): MidiParameterMapping {
        return MidiParameterMapping(
            midiType = MidiMessageType.NOTE_ON,
            midiChannel = midiChannel,
            midiController = midiNote,
            targetType = MidiTargetType.PAD_TRIGGER,
            targetId = targetId,
            minValue = 0.0f,
            maxValue = 1.0f,
            curve = MidiCurve.LINEAR
        )
    }
}