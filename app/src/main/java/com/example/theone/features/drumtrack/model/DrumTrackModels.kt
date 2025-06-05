package com.example.theone.features.drumtrack.model

import com.example.theone.model.PlaybackMode // Added import for shared model

// --- Enums and Basic Types ---

// PlaybackMode is now defined in com.example.theone.model.SharedModels

// --- Sample Related Data ---
// Placeholder - This should ideally come from a shared 'core.model' module.
// Copied from SamplerViewModel.kt for now.
data class SampleMetadata(
    val id: String, // Unique ID for the sample
    val name: String, // User-defined name
    val filePathUri: String, // URI to the actual audio file
    val durationMs: Long = 0,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val detectedBpm: Float? = null,
    val detectedKey: String? = null,
    var userBpm: Float? = null,
    var userKey: String? = null,
    var rootNote: Int = 60 // MIDI C3, default root note
)

// --- Pad and Track Specific Data ---

data class PadSettings(
    val id: String, // e.g., "Pad1", "Pad2", ... "Pad16"
    var sampleId: String? = null, // ID of the sample assigned from SamplePool (C3)
    var sampleName: String? = null, // Convenience to store name of assigned sample
    var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,

    // Basic parameters for M1.2, more advanced in M3.1
    var volume: Float = 1.0f, // 0.0f to 2.0f (double gain)
    var pan: Float = 0.0f,    // -1.0f (Left) to 1.0f (Right)

    // Future M3.1 features (placeholders for now, not used in M1.2 logic)
    var tuningCoarse: Int = 0, // Semitones
    var tuningFine: Int = 0,   // Cents
    // var ampEnvelope: EnvelopeSettings? = null,
    // var filterEnvelope: EnvelopeSettings? = null,
    // var pitchEnvelope: EnvelopeSettings? = null,
    // var lfos: MutableList<Any /* LFOSettings */> = mutableListOf(),
    // var insertEffects: MutableList<Any /* EffectInstance */> = mutableListOf(),
    var muteGroup: Int = 0, // 0 = none
    var polyphony: Int = 16 // Max simultaneous notes for this pad (more relevant for NOTE_ON_OFF)
)

data class DrumTrack(
    val id: String, // Unique ID for this drum track instance
    var name: String = "Drum Track 1",
    // Using a List of PadSettings assuming a fixed order (e.g. 16 pads)
    // A Map<String, PadSettings> where key is PadSettings.id is also good.
    // For simplicity in iterating for a UI grid, a List might be easier initially.
    val pads: List<PadSettings> = List(16) { index -> PadSettings(id = "Pad${index + 1}") },
    // Example: pads[0] is Pad1, pads[15] is Pad16

    // Other track-specific properties as per README's full Track definition (volume, pan, solo, mute etc.)
    // For M1.2, we focus on pad assignments and playback.
    var trackVolume: Float = 1.0f,
    var trackPan: Float = 0.0f,
    var isMuted: Boolean = false,
    var isSoloed: Boolean = false // Solo logic can be complex, deferring full implementation
)

// --- Helper function to create a default drum track ---
fun createDefaultDrumTrack(id: String = "dt1", name: String = "Default Drum Track"): DrumTrack {
    return DrumTrack(
        id = id,
        name = name
        // Pads are already initialized with default PadSettings
    )
}
