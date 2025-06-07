package com.example.theone.model

import java.util.UUID

// Envelope Definitions
enum class EnvelopeType {
    AD,    // Attack, Decay
    AHDS,  // Attack, Hold, Decay, Sustain
    ADSR   // Attack, Decay, Sustain, Release (most common)
}

data class EnvelopeSettings(
    var type: EnvelopeType = EnvelopeType.ADSR,
    var attackMs: Float = 5f,
    var holdMs: Float? = null, // Relevant for AHDS
    var decayMs: Float = 100f,
    var sustainLevel: Float? = 0.7f, // 0.0 to 1.0, relevant for AHDS, ADSR
    var releaseMs: Float = 100f,    // Relevant for ADSR

    // Velocity modulation (0.0 means no influence, 1.0 means full influence)
    // Positive values typically shorten attack, negative values lengthen it
    var velocityToAttack: Float = 0f,
    // Positive values increase level, negative values decrease it
    var velocityToLevel: Float = 0f
)

// LFO Definitions
enum class LfoWaveform {
    SINE,
    TRIANGLE,
    SQUARE,
    SAW_UP,  // Sawtooth rising
    SAW_DOWN, // Sawtooth falling (ramp down)
    RANDOM   // Random value stepping
}

// Time Division for tempo-synced LFOs
// (Based on common DAW/sampler divisions)
enum class TimeDivision(val multiplier: Double) {
    WHOLE(4.0), // Whole note
    HALF_DOTTED(3.0),
    HALF(2.0),
    QUARTER_DOTTED(1.5),
    QUARTER(1.0),      // Quarter note (typically 1 beat)
    QUARTER_TRIPLET(2.0/3.0),
    EIGHTH_DOTTED(0.75),
    EIGHTH(0.5),
    EIGHTH_TRIPLET(1.0/3.0),
    SIXTEENTH_DOTTED(0.375),
    SIXTEENTH(0.25),
    SIXTEENTH_TRIPLET(1.0/6.0),
    THIRTY_SECOND(0.125),
    SIXTY_FOURTH(0.0625);

    // Could add methods here to calculate actual time based on tempo if needed
}

data class LFOSettings(
    val id: String = UUID.randomUUID().toString(), // Unique ID for this LFO instance
    var waveform: LfoWaveform = LfoWaveform.SINE,
    var rateHz: Float = 1.0f,        // LFO rate in Hertz (cycles per second)
    var syncToTempo: Boolean = false, // If true, rateHz is ignored, tempoDivision is used
    var tempoDivision: TimeDivision = TimeDivision.QUARTER,
    // Destinations: Key is a String identifying the parameter (e.g., "volume", "pan", "pitchCoarse", "filterCutoff")
    // Value is the modulation depth/amount (+/-)
    var destinations: MutableMap<String, Float> = mutableMapOf()
)
