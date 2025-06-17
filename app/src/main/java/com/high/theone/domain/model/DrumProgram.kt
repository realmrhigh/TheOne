package com.high.theone.domain.model

data class DrumProgram(
    val id: String = "",
    val name: String = "Drum Program",
    val pads: List<DrumPad> = emptyList()
)
