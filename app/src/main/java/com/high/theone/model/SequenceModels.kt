package com.high.theone.model
import kotlinx.serialization.Serializable

// Minimal stubs for Event, EventType, and Sequence
@Serializable
data class Event(
    val id: Int = 0,
    val type: EventType = EventType.NOTE_ON,
    val time: Long = 0L,
    val value: Float = 0f
)

enum class EventType {
    NOTE_ON, NOTE_OFF, CONTROL_CHANGE, OTHER
}

@Serializable
data class Sequence(
    val id: Int = 0,
    val events: List<Event> = emptyList()
)
