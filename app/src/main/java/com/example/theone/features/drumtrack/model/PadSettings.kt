package com.example.theone.features.drumtrack.model

import com.example.theone.model.EnvelopeSettings // Import new model
import com.example.theone.model.LFOSettings // Import new model
// LayerModels import might not be needed if SampleLayer and LayerTriggerRule are imported directly
import com.example.theone.model.LayerTriggerRule // Import new model
import com.example.theone.model.SampleLayer // Import new model
import java.util.UUID

// Define PlaybackMode enum within this file or a shared model file
// For this subtask, defining it here is fine.
enum class PlaybackMode {
    ONE_SHOT, // Plays the full sample regardless of note off
    GATE,     // Sample plays only while the pad is held (or until note off event)
    LOOP      // Sample loops while pad is held (or until note off, depending on gate behavior)
}

data class PadSettings(
    // M3.1: padId is a String. This might be a unique ID for the settings instance itself,
    // or the identifier of the pad it's assigned to (e.g., "Pad_1", "A1").
    // Let's assume it's an ID for the PadSettings data object.
    val padSettingsId: String = UUID.randomUUID().toString(),

    // --- NEW: Layering ---
    val layers: MutableList<SampleLayer> = mutableListOf(),
    var layerTriggerRule: LayerTriggerRule = LayerTriggerRule.VELOCITY,

    // --- Base sound parameters (offsets are now in SampleLayer) ---
    var volume: Float = 1.0f, // Base volume (0.0 to 2.0+)
    var pan: Float = 0.0f,    // Base pan (-1.0 to 1.0)
    var tuningCoarse: Int = 0, // Base coarse tuning in semitones
    var tuningFine: Int = 0,   // Base fine tuning in cents

    // --- Playback and Polyphony ---
    var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    // var muteGroup: Int = 0, // As per M3.1 spec, can be added if needed by other features
    // var polyphony: Int = 16, // As per M3.1 spec, can be added if needed

    // --- NEW: Sound Design ---
    // Initialize with default EnvelopeSettings constructor
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings(),
    var filterEnvelope: EnvelopeSettings? = null,
    var pitchEnvelope: EnvelopeSettings? = null,

    val lfos: MutableList<LFOSettings> = mutableListOf()

    // --- DEPRECATED/REMOVED from M1 PadSettings (if they existed there) ---
    // The fields `sampleId: String? = null` and `sampleName: String? = null` from M1
    // are effectively replaced by the `layers` list.
    // The field `tuning: Float = 0.0f` from M1 is replaced by `tuningCoarse` and `tuningFine`.
) {
    // Helper function to get a layer by its ID
    fun getLayerById(layerId: String): SampleLayer? {
        return layers.find { it.id == layerId }
    }

    // Helper to add a new layer from a sampleId (e.g., URI or ID from pool)
    // This creates a SampleLayer with default settings.
    fun addLayer(newSampleId: String): SampleLayer {
        val newLayer = SampleLayer(sampleId = newSampleId)
        layers.add(newLayer)
        return newLayer
    }

    // Helper to remove a layer by its ID
    fun removeLayerById(layerId: String): Boolean {
        return layers.removeIf { it.id == layerId }
    }
}
