package com.example.theone.model

import kotlinx.serialization.Serializable

// Represents a single event on a track's timeline
@Serializable
data class Event(
    val id: String,
    val trackId: String,
    val startTimeTicks: Long, // Absolute time in sequence ticks (e.g., 96 PPQN)
    val type: EventType
)

// Defines the different kinds of events that can exist
@Serializable
sealed class EventType {
    @Serializable
    data class PadTrigger(
        val padId: String,       // e.g., "Pad0" to "Pad15"
        val velocity: Int,       // 0-127
        val durationTicks: Long, // How long the note event is, may not affect one-shot samples
    ) : EventType()

    // Placeholder for future event types like NoteOn for pitched instruments
}

@Serializable
data class Sequence(
    val id: String,
    var name: String,
    var bpm: Float,
    var barLength: Int = 4,
    var timeSignatureNumerator: Int = 4,
    var timeSignatureDenominator: Int = 4,
    val ppqn: Int = 96, // Pulses Per Quarter Note - ADDED THIS LINE
    val events: MutableList<Event> = mutableListOf()
)