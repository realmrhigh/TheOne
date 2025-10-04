package com.high.theone.midi.model

import kotlinx.serialization.Serializable

/**
 * Persistent MIDI configuration
 */
@Serializable
data class MidiConfiguration(
    val deviceConfigurations: Map<String, MidiDeviceConfiguration>,
    val activeMappingId: String?,
    val globalSettings: MidiGlobalSettings,
    val clockSettings: MidiClockSettings
)

/**
 * Configuration for individual MIDI devices
 */
@Serializable
data class MidiDeviceConfiguration(
    val deviceId: String,
    val isInputEnabled: Boolean,
    val isOutputEnabled: Boolean,
    val inputLatencyMs: Float,
    val outputLatencyMs: Float,
    val velocityCurve: MidiCurve,
    val channelFilter: Set<Int>? // null = all channels
) {
    init {
        require(deviceId.isNotBlank()) { "Device ID cannot be blank" }
        require(inputLatencyMs >= 0) { "Input latency cannot be negative" }
        require(outputLatencyMs >= 0) { "Output latency cannot be negative" }
        channelFilter?.forEach { channel ->
            require(channel in 0..15) { "MIDI channel must be between 0 and 15" }
        }
    }
}

/**
 * Global MIDI settings
 */
@Serializable
data class MidiGlobalSettings(
    val midiThru: Boolean,
    val velocitySensitivity: Float,
    val panicOnStop: Boolean,
    val omniMode: Boolean
) {
    init {
        require(velocitySensitivity in 0.0f..2.0f) { "Velocity sensitivity must be between 0.0 and 2.0" }
    }
}

/**
 * MIDI clock synchronization settings
 */
@Serializable
data class MidiClockSettings(
    val clockSource: MidiClockSource,
    val sendClock: Boolean,
    val receiveClock: Boolean,
    val clockDivision: Int,
    val syncToFirstClock: Boolean
) {
    init {
        require(clockDivision > 0) { "Clock division must be positive" }
    }
}