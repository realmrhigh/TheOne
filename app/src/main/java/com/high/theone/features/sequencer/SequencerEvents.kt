package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PadTriggerEvent(val padId: String, val velocity: Int)

@Singleton
class SequencerEventBus @Inject constructor() {
    private val _padTriggerEvents = MutableSharedFlow<PadTriggerEvent>(extraBufferCapacity = 16) // Add buffer to avoid missed events if collector is slow
    val padTriggerEvents = _padTriggerEvents.asSharedFlow()

    suspend fun emitPadTriggerEvent(event: PadTriggerEvent) {
        _padTriggerEvents.emit(event)
    }
}
