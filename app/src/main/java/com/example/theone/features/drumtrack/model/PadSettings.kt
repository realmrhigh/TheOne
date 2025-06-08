package com.example.theone.features.drumtrack.model

import com.example.theone.model.SampleLayer // Added import
import com.example.theone.model.LayerTriggerRule // Added import
import com.example.theone.model.SynthModels.EnvelopeSettings // Corrected import
import com.example.theone.model.SynthModels.LFOSettings // Corrected import
import com.example.theone.model.SynthModels.EnvelopeType // Corrected import
import com.example.theone.model.PlaybackMode // Added import for consolidated PlaybackMode

// PlaybackMode is now defined in com.example.theone.model.SharedModels

data class PadSettings(
    val id: String,
    var name: String = "Default Pad", // Added from edit version for Program Name

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
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings( // Using consolidated EnvelopeSettings
        type = com.example.theone.model.ModelEnvelopeTypeInternal.ADSR, // Explicitly use model's enum
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f, // Now non-nullable in model
        releaseMs = 100f
    ),
    var pitchEnvelope: EnvelopeSettings = EnvelopeSettings( // Non-nullable
        type = com.example.theone.model.ModelEnvelopeTypeInternal.ADSR,
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f,
        releaseMs = 100f
    ),
    var filterEnvelope: EnvelopeSettings = EnvelopeSettings( // Non-nullable
        type = com.example.theone.model.ModelEnvelopeTypeInternal.ADSR,
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f,
        releaseMs = 100f
    ),
    // --- LFOs ---
    var lfos: MutableList<LFOSettings> = mutableListOf() // Using consolidated LFOSettings
)
