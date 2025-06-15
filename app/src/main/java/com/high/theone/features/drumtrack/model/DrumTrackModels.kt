package com.high.theone.features.drumtrack.model

// Removed: import com.high.theone.model.PlaybackMode
import com.high.theone.features.drumtrack.model.PadSettings // Added

// --- Enums and Basic Types ---

// PlaybackMode is now defined in com.high.theone.model.SharedModels.kt
// PadSettings.kt imports it from there.

// --- Sample Related Data ---
// Placeholder - This should ideally come from a shared 'core.model' module.
// Copied from SamplerViewModel.kt for now.
// --- SampleMetadata is now defined in com.high.theone.model.SampleModels.kt ---

// --- Pad and Track Specific Data ---

// PadSettings data class was removed from here. It's now imported.

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
    )
}
