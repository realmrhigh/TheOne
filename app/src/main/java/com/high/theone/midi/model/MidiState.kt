package com.high.theone.midi.model

/**
 * Runtime MIDI state management
 */
data class MidiRuntimeState(
    val connectedDevices: Map<String, MidiDeviceInfo>,
    val activeInputs: Set<String>,
    val activeOutputs: Set<String>,
    val currentMapping: MidiMapping?,
    val isLearning: Boolean,
    val learnTarget: MidiLearnTarget?,
    val clockState: MidiClockState
)

/**
 * Represents the overall state of the MIDI system
 */
enum class MidiSystemState {
    STOPPED,
    INITIALIZING,
    RUNNING,
    ERROR,
    SHUTTING_DOWN
}

/**
 * MIDI clock synchronization state
 */
data class MidiClockState(
    val isReceiving: Boolean,
    val isSending: Boolean,
    val currentBpm: Float,
    val clockSource: String?,
    val lastClockTime: Long
) {
    init {
        require(currentBpm >= 0) { "Current BPM cannot be negative" }
        require(lastClockTime >= 0) { "Last clock time cannot be negative" }
    }
}

/**
 * Represents the current state of MIDI learn mode
 */
sealed class MidiLearnState {
    object Inactive : MidiLearnState()
    data class Active(val target: MidiLearnTarget) : MidiLearnState()
    data class Completed(val learnedMapping: MidiParameterMapping) : MidiLearnState()
    object TimedOut : MidiLearnState()
    object Cancelled : MidiLearnState()
}

/**
 * Represents a MIDI learn target parameter
 */
data class MidiLearnTarget(
    val targetType: MidiTargetType,
    val targetId: String,
    val allowedMessageTypes: Set<MidiMessageType>,
    val startTime: Long
) {
    init {
        require(targetId.isNotBlank()) { "Target ID cannot be blank" }
        require(allowedMessageTypes.isNotEmpty()) { "Must allow at least one message type" }
    }
}