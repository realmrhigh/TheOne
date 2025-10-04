package com.high.theone.features.sampling

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.model.PadState
import kotlin.math.*

/**
 * Utilities for integrating MIDI input with the pad system.
 * Handles MIDI note mapping, velocity conversion, and pad triggering logic.
 * 
 * Requirements: 1.1 (MIDI note mapping), 1.3 (velocity conversion), 5.2 (pad integration)
 */
object MidiPadIntegration {
    
    /**
     * Convert MIDI velocity (0-127) to pad velocity (0.0-1.0) with sensitivity curve
     */
    fun convertMidiVelocityToPadVelocity(
        midiVelocity: Int,
        sensitivityMultiplier: Float = 1.0f,
        curve: VelocityCurve = VelocityCurve.LINEAR
    ): Float {
        require(midiVelocity in 0..127) { "MIDI velocity must be between 0 and 127" }
        require(sensitivityMultiplier >= 0f) { "Sensitivity multiplier must be non-negative" }
        
        // Normalize MIDI velocity to 0.0-1.0 range
        val normalizedVelocity = midiVelocity / 127.0f
        
        // Apply velocity curve
        val curvedVelocity = when (curve) {
            VelocityCurve.LINEAR -> normalizedVelocity
            VelocityCurve.EXPONENTIAL -> normalizedVelocity.pow(2f)
            VelocityCurve.LOGARITHMIC -> sqrt(normalizedVelocity)
            VelocityCurve.S_CURVE -> {
                // Smooth S-curve using sigmoid-like function
                val x = (normalizedVelocity - 0.5f) * 6f // Scale to -3 to 3
                1f / (1f + exp(-x))
            }
        }
        
        // Apply sensitivity multiplier and clamp to valid range
        val finalVelocity = curvedVelocity * sensitivityMultiplier
        return finalVelocity.coerceIn(0f, 1f)
    }
    
    /**
     * Find the pad index that should respond to a MIDI note message
     */
    fun findPadForMidiNote(
        pads: List<PadState>,
        midiNote: Int,
        midiChannel: Int
    ): Int? {
        return pads.firstOrNull { pad ->
            pad.respondsToMidiNote(midiNote, midiChannel)
        }?.index
    }
    
    /**
     * Create a MIDI note mapping for a pad using standard drum mapping
     */
    fun getStandardDrumMidiNote(padIndex: Int): Int {
        // Standard GM drum mapping for common pad positions
        return when (padIndex) {
            0 -> 36  // C2 - Kick Drum
            1 -> 38  // D2 - Snare Drum
            2 -> 42  // F#2 - Closed Hi-Hat
            3 -> 46  // A#2 - Open Hi-Hat
            4 -> 41  // F2 - Low Tom
            5 -> 43  // G2 - High Tom
            6 -> 45  // A2 - Low Tom
            7 -> 47  // B2 - Mid Tom
            8 -> 49  // C#3 - Crash Cymbal
            9 -> 51  // D#3 - Ride Cymbal
            10 -> 39 // D#2 - Hand Clap
            11 -> 54 // F#3 - Tambourine
            12 -> 56 // G#3 - Cowbell
            13 -> 58 // A#3 - Vibraslap
            14 -> 60 // C4 - High Bongo
            15 -> 62 // D4 - Mute High Conga
            else -> 36 + (padIndex % 12) // Fallback pattern
        }
    }
    
    /**
     * Auto-assign MIDI notes to all pads using standard drum mapping
     */
    fun autoAssignMidiNotes(pads: List<PadState>, channel: Int = 9): List<PadState> {
        return pads.mapIndexed { index, pad ->
            pad.copy(
                midiNote = getStandardDrumMidiNote(index),
                midiChannel = channel, // Channel 10 (0-indexed as 9) is standard for drums
                acceptsAllChannels = false
            )
        }
    }
    
    /**
     * Check if a MIDI message should trigger a pad
     */
    fun shouldTriggerPad(message: MidiMessage, pad: PadState): Boolean {
        return when (message.type) {
            MidiMessageType.NOTE_ON -> {
                message.data2 > 0 && // Velocity > 0 (NOTE_ON with velocity 0 is NOTE_OFF)
                pad.respondsToMidiNote(message.data1, message.channel)
            }
            else -> false
        }
    }
    
    /**
     * Check if a MIDI message should stop a pad (for NOTE_ON_OFF playback mode)
     */
    fun shouldStopPad(message: MidiMessage, pad: PadState): Boolean {
        return when (message.type) {
            MidiMessageType.NOTE_OFF -> {
                pad.respondsToMidiNote(message.data1, message.channel)
            }
            MidiMessageType.NOTE_ON -> {
                message.data2 == 0 && // NOTE_ON with velocity 0 is equivalent to NOTE_OFF
                pad.respondsToMidiNote(message.data1, message.channel)
            }
            else -> false
        }
    }
    
    /**
     * Create a MIDI pad trigger event from a MIDI message
     */
    fun createPadTriggerEvent(
        message: MidiMessage,
        pad: PadState
    ): MidiPadTriggerEvent? {
        if (!shouldTriggerPad(message, pad)) return null
        
        val padVelocity = convertMidiVelocityToPadVelocity(
            midiVelocity = message.data2,
            sensitivityMultiplier = pad.midiVelocitySensitivity,
            curve = VelocityCurve.LINEAR // TODO: Make this configurable per pad
        )
        
        return MidiPadTriggerEvent(
            padIndex = pad.index,
            velocity = padVelocity,
            midiNote = message.data1,
            midiChannel = message.channel,
            midiVelocity = message.data2,
            timestamp = message.timestamp
        )
    }
}

/**
 * Velocity curve types for MIDI-to-pad velocity conversion
 */
enum class VelocityCurve {
    LINEAR,      // Direct 1:1 mapping
    EXPONENTIAL, // Softer response, more dynamic range at high velocities
    LOGARITHMIC, // More sensitive at low velocities
    S_CURVE      // Smooth curve with gentle start and end
}

/**
 * Event representing a pad trigger from MIDI input
 */
data class MidiPadTriggerEvent(
    val padIndex: Int,
    val velocity: Float,
    val midiNote: Int,
    val midiChannel: Int,
    val midiVelocity: Int,
    val timestamp: Long
)

/**
 * Event representing a pad stop from MIDI input (for NOTE_ON_OFF mode)
 */
data class MidiPadStopEvent(
    val padIndex: Int,
    val midiNote: Int,
    val midiChannel: Int,
    val timestamp: Long,
    val originalVelocity: Int? = null, // Original note-on velocity for sustained notes
    val sustainDurationMs: Long? = null // How long the note was sustained
)