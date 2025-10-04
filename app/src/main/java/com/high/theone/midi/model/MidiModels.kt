package com.high.theone.midi.model

import kotlinx.serialization.Serializable

/**
 * Core MIDI message representation
 */
data class MidiMessage(
    val type: MidiMessageType,
    val channel: Int,
    val data1: Int,
    val data2: Int,
    val timestamp: Long
) {
    init {
        require(channel in 0..15) { "MIDI channel must be between 0 and 15" }
        require(data1 in 0..127) { "MIDI data1 must be between 0 and 127" }
        require(data2 in 0..127) { "MIDI data2 must be between 0 and 127" }
    }
}

/**
 * MIDI message types supported by the engine
 */
enum class MidiMessageType(val statusByte: Int) {
    NOTE_ON(0x90),
    NOTE_OFF(0x80),
    CONTROL_CHANGE(0xB0),
    PROGRAM_CHANGE(0xC0),
    PITCH_BEND(0xE0),
    AFTERTOUCH(0xA0),
    SYSTEM_EXCLUSIVE(0xF0),
    CLOCK(0xF8),
    START(0xFA),
    STOP(0xFC),
    CONTINUE(0xFB);

    companion object {
        fun fromStatusByte(statusByte: Int): MidiMessageType? {
            return values().find { it.statusByte == (statusByte and 0xF0) }
        }
    }
}

/**
 * MIDI device information
 */
data class MidiDeviceInfo(
    val id: String,
    val name: String,
    val manufacturer: String,
    val type: MidiDeviceType,
    val inputPortCount: Int,
    val outputPortCount: Int,
    val isConnected: Boolean
) {
    init {
        require(id.isNotBlank()) { "Device ID cannot be blank" }
        require(name.isNotBlank()) { "Device name cannot be blank" }
        require(inputPortCount >= 0) { "Input port count cannot be negative" }
        require(outputPortCount >= 0) { "Output port count cannot be negative" }
    }
}

/**
 * Types of MIDI devices
 */
enum class MidiDeviceType {
    KEYBOARD,
    CONTROLLER,
    INTERFACE,
    SYNTHESIZER,
    DRUM_MACHINE,
    OTHER
}

/**
 * MIDI mapping configuration
 */
@Serializable
data class MidiMapping(
    val id: String,
    val name: String,
    val deviceId: String?,
    val mappings: List<MidiParameterMapping>,
    val isActive: Boolean
) {
    init {
        require(id.isNotBlank()) { "Mapping ID cannot be blank" }
        require(name.isNotBlank()) { "Mapping name cannot be blank" }
    }
}

/**
 * Individual parameter mapping from MIDI to application parameter
 */
@Serializable
data class MidiParameterMapping(
    val midiType: MidiMessageType,
    val midiChannel: Int,
    val midiController: Int,
    val targetType: MidiTargetType,
    val targetId: String,
    val minValue: Float,
    val maxValue: Float,
    val curve: MidiCurve
) {
    init {
        require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
        require(midiController in 0..127) { "MIDI controller must be between 0 and 127" }
        require(targetId.isNotBlank()) { "Target ID cannot be blank" }
        require(minValue <= maxValue) { "Min value must be less than or equal to max value" }
    }
}

/**
 * Types of targets that MIDI can control
 */
@Serializable
enum class MidiTargetType {
    PAD_TRIGGER,
    PAD_VOLUME,
    PAD_PAN,
    MASTER_VOLUME,
    EFFECT_PARAMETER,
    SEQUENCER_TEMPO,
    TRANSPORT_CONTROL
}

/**
 * Curve types for MIDI parameter mapping
 */
@Serializable
enum class MidiCurve {
    LINEAR,
    EXPONENTIAL,
    LOGARITHMIC,
    S_CURVE
}

/**
 * MIDI clock source options
 */
enum class MidiClockSource {
    INTERNAL,
    EXTERNAL_AUTO,
    EXTERNAL_DEVICE
}

/**
 * MIDI transport message types
 */
enum class MidiTransportMessage {
    START,
    STOP,
    CONTINUE,
    SONG_POSITION
}

/**
 * MIDI clock pulse information
 */
data class MidiClockPulse(
    val timestamp: Long,
    val pulseNumber: Int,
    val bpm: Float
) {
    init {
        require(pulseNumber >= 0) { "Pulse number cannot be negative" }
        require(bpm > 0) { "BPM must be positive" }
    }
}

/**
 * MIDI statistics for monitoring and diagnostics
 */
data class MidiStatistics(
    val inputMessageCount: Long,
    val outputMessageCount: Long,
    val averageInputLatency: Float,
    val droppedMessageCount: Long,
    val lastErrorMessage: String?
) {
    init {
        require(inputMessageCount >= 0) { "Input message count cannot be negative" }
        require(outputMessageCount >= 0) { "Output message count cannot be negative" }
        require(averageInputLatency >= 0) { "Average input latency cannot be negative" }
        require(droppedMessageCount >= 0) { "Dropped message count cannot be negative" }
    }
}