package com.example.theone.features.drumtrack.edit

sealed class DrumProgramEditEvent {
    // data class SomeOtherExistingEvent(...) : DrumProgramEditEvent() // If others exist, keep them
    data class OnPadVolumeChange(val trackId: String, val padId: String, val volume: Float) : DrumProgramEditEvent()
}
