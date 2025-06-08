package com.example.theone.model

// --- Enums to be used by editor and model ---
// Kept LfoWaveform from original SynthModels.kt, editor will adapt or it's already compatible
enum class LfoWaveform { SINE, TRIANGLE, SQUARE, SAW_UP, SAW_DOWN, RANDOM }

// This LfoDestination is what the editor was using.
// The model's LFOSettings.destinations map is more advanced.
// We add a primary destination field to LFOSettings for now.
enum class LfoDestination { NONE, PITCH, PAN, VOLUME, FILTER_CUTOFF, FILTER_RESONANCE }

// EnvelopeType for selecting which envelope in editor (AMP, PITCH, FILTER)
// This is different from the model's internal EnvelopeType (AD, AHDS, ADSR)
// This enum should live with the editor logic, not here.
// So, I will NOT redefine it here. It should remain in DrumProgramEditViewModel.kt.

// Model's internal EnvelopeType
enum class ModelEnvelopeTypeInternal { AD, AHDS, ADSR }


// --- Data Classes ---

data class EnvelopeSettings(
    var type: ModelEnvelopeTypeInternal = ModelEnvelopeTypeInternal.ADSR, // Use the model's internal type
    var attackMs: Float, // Editor uses seconds, ViewModel will convert
    var holdMs: Float? = null,
    var decayMs: Float, // Editor uses seconds, ViewModel will convert
    var sustainLevel: Float = 1.0f, // Editor expects non-nullable, default 1.0f
    var releaseMs: Float, // Editor uses seconds, ViewModel will convert
    var velocityToAttack: Float = 0f, // How much velocity affects attack time
    var velocityToLevel: Float = 0f   // How much velocity affects overall envelope level
)

data class LFOSettings(
    val id: String, // Unique ID for this LFO instance if needed, or type if predefined
    var isEnabled: Boolean = false, // Added from edit version
    var waveform: LfoWaveform = LfoWaveform.SINE, // Uses enum defined above
    var rateHz: Float = 1.0f, // Renamed from 'rate' in edit version
    var syncToTempo: Boolean = false, // Was 'bpmSync' in edit version
    var tempoDivision: TimeDivision = TimeDivision.Quarter,
    var depth: Float = 0.5f, // Added from edit version (overall depth)
    var primaryDestination: LfoDestination = LfoDestination.NONE, // Added for editor compatibility
    var destinations: MutableMap<String, Float> = mutableMapOf() // Advanced: ParamID -> ModDepth. Editor may use primaryDestination for now.
    // TODO: Add LFO phase control (e.g., 0-360 degrees).
    // TODO: Add LFO retrigger options (e.g., free-running, on note, on layer).
)

// TimeDivision for BPM-synced LFO rates
enum class TimeDivision {
    Whole, Half, Quarter, Eighth, Sixteenth, ThirtySecond, SixtyFourth,
    DottedHalf, DottedQuarter, DottedEighth, DottedSixteenth,
    TripletWhole, TripletHalf, TripletQuarter, TripletEighth, TripletSixteenth
}
