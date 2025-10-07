package com.high.theone.features.drumtrack.model

import com.high.theone.model.LayerTriggerRule
import com.high.theone.model.PlaybackMode
import com.high.theone.model.SampleLayer
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.EffectSetting
import com.high.theone.model.LFOSettings
import com.high.theone.model.ModulationRouting
import com.high.theone.model.EnvelopeType

// PlaybackMode is now defined in com.high.theone.model.SharedModels

data class PadSettings(
    val id: String,
    var name: String = "Default Pad", // Added from edit version for Program Name

    // Sample assignment
    var sampleId: String? = null,
    var sampleName: String? = null,

    // --- Layering ---
    val layers: MutableList<SampleLayer> = mutableListOf(), // Using consolidated SampleLayer
    var layerTriggerRule: LayerTriggerRule = LayerTriggerRule.VELOCITY,

    // --- Global Pad Parameters (from edit version and existing model) ---
    var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    var volume: Float = 1.0f, // Overall pad volume
    var pan: Float = 0.0f,    // Overall pad pan
    // tuningCoarse and tuningFine at global level are base values; layers have offsets or absolute values
    var tuningCoarse: Int = 0, // Base coarse tuning for the pad
    var tuningFine: Int = 0,   // Base fine tuning for the pad
    var muteGroup: Int = 0,
    var polyphony: Int = 16,

    // --- Envelopes (non-nullable as per editor's expectation) ---
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings(
        type = EnvelopeType.ADSR, // Use the correct enum
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f,
        releaseMs = 100f
    ),
    var pitchEnvelope: EnvelopeSettings = EnvelopeSettings(
        type = EnvelopeType.ADSR,
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f,
        releaseMs = 100f
    )
    // TODO: Add other properties and methods as needed
)
