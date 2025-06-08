package com.example.theone.features.drumtrack.model

import com.example.theone.model.SampleLayer // Added import
import com.example.theone.model.LayerTriggerRule // Added import
import com.example.theone.model.SynthModels.EnvelopeSettings // Corrected import
import com.example.theone.model.SynthModels.LFOSettings // Corrected import
import com.example.theone.model.SynthModels.EnvelopeType // Corrected import
import com.example.theone.model.PlaybackMode // Added import for consolidated PlaybackMode

// PlaybackMode is now defined in com.example.theone.model.SharedModels

data class PadSettings(
    val id: String, // e.g., "Pad1" - Assuming id is a new requirement, was not in the old file

    // --- NEW: Layering ---
    val layers: MutableList<SampleLayer> = mutableListOf(),
    var layerTriggerRule: LayerTriggerRule = LayerTriggerRule.VELOCITY,
    // --- REMOVED (or deprecated): var sampleId: String? ---
    // val sampleName: String? = null, // Also removed as layer specific sample info will be used

    // These now become the BASE settings, with layers providing offsets
    var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT, // Added, assuming default
    var tuningCoarse: Int = 0, // Replaces 'tuning: Float'
    var tuningFine: Int = 0,   // New for fine tuning
    var volume: Float = 1.0f, // Kept
    var pan: Float = 0.0f,    // Kept
    var muteGroup: Int = 0,   // Added, assuming default
    var polyphony: Int = 16,  // Added, assuming default

    // --- NEW: Sound Design ---
    // Default values for ampEnvelope need to be sensible.
    // Example: ADSR with quick attack, medium decay, full sustain, medium release.
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings(
        type = EnvelopeType.ADSR,
        attackMs = 5f,
        holdMs = 0f,
        decayMs = 150f,
        sustainLevel = 1.0f,
        releaseMs = 100f
    ),
    var filterEnvelope: EnvelopeSettings? = null,
    var pitchEnvelope: EnvelopeSettings? = null,
    var lfos: MutableList<LFOSettings> = mutableListOf()
)
