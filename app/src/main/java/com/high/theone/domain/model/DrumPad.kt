package com.high.theone.domain.model

data class DrumPad(
    val id: String = "",
    val name: String = "Pad",
    val settings: PadSettings = PadSettings(),
    val sample: SampleMetadata? = null
)
