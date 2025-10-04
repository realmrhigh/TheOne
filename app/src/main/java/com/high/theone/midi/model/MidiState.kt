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
 * MIDI learn target information
 */
data class MidiLearnTarget(
    val targetType: MidiTargetType,
    val targetId: String,
    val startTime: Long
) {
    init {
        require(targetId.isNotBlank()) { "Target ID cannot be blank" }
        require(startTime > 0) { "Start time must be positive" }
    }
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