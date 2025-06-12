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


// --- Modulation Models ---
data class ModulationRouting(
    val id: String = java.util.UUID.randomUUID().toString(),
    var source: ModSource = ModSource.LFO1,
    var destination: ModDestination = ModDestination.PITCH,
    var amount: Float = 0f, // Typically -1.0 to 1.0 for bipolar, or 0.0 to 1.0 for unipolar
    var isEnabled: Boolean = true
)

enum class ModSource {
    NONE, LFO1, LFO2, AMP_ENV, PITCH_ENV, FILTER_ENV, VELOCITY, KEY_TRACKING, MOD_WHEEL, PITCH_BEND
    // TODO: Add more sources like Sequencer CV, Random, etc.
}

enum class ModDestination {
    NONE, PITCH, VOLUME, PAN, FILTER_CUTOFF, FILTER_RESONANCE,
    LFO1_RATE, LFO1_DEPTH, LFO2_RATE, LFO2_DEPTH,
    AMP_ENV_ATTACK, AMP_ENV_DECAY, AMP_ENV_SUSTAIN, AMP_ENV_RELEASE,
    SAMPLE_START, SAMPLE_END, SAMPLE_LOOP_POINT
    // TODO: Add more destinations like effect parameters, other envelope parameters etc.
}

// --- Effects Models ---
data class EffectSetting(
    val id: String = java.util.UUID.randomUUID().toString(),
    var type: EffectType = EffectType.DELAY,
    var parameters: MutableMap<String, Float> = mutableMapOf(), // Parameter name to value (e.g., "delayTimeMs" to 250f)
    var isEnabled: Boolean = true,
    var mix: Float = 0.5f // Wet/Dry mix, 0.0 (dry) to 1.0 (wet)
    // TODO: Add sidechain input selection?
)

enum class EffectType {
    DELAY, REVERB, DISTORTION, EQ_PARAMETRIC, COMPRESSOR, CHORUS, FLANGER, PHASER, BITCRUSHER, FILTER
    // TODO: Add more effect types
}

data class EffectParameterDefinition(
    val key: String, // Matches the key in EffectSetting.parameters map
    val displayName: String, // Name for UI display
    val valueRange: ClosedFloatingPointRange<Float>, // Min and Max for sliders
    val steps: Int = 0, // For discrete sliders, 0 means continuous. If > 0, actual steps = steps + 1.
    val unit: String = "", // e.g., "ms", "Hz", "%", for display
    val defaultValue: Float // Default value for this parameter when an effect is added
)

object DefaultEffectParameterProvider {
    private val definitions: Map<EffectType, List<EffectParameterDefinition>> = mapOf(
        EffectType.DELAY to listOf(
            EffectParameterDefinition(key = "timeMs", displayName = "Time", valueRange = 0f..2000f, unit = "ms", defaultValue = 300f),
            EffectParameterDefinition(key = "feedback", displayName = "Feedback", valueRange = 0f..0.95f, steps = 95, unit = "", defaultValue = 0.4f),
            EffectParameterDefinition(key = "lowPassCutoffHz", displayName = "LP Cutoff", valueRange = 1000f..20000f, unit = "Hz", defaultValue = 18000f),
            EffectParameterDefinition(key = "highPassCutoffHz", displayName = "HP Cutoff", valueRange = 20f..5000f, unit = "Hz", defaultValue = 100f)
        ),
        EffectType.REVERB to listOf(
            EffectParameterDefinition(key = "size", displayName = "Size", valueRange = 0.1f..1.0f, steps = 90, unit = "", defaultValue = 0.7f),
            EffectParameterDefinition(key = "decayMs", displayName = "Decay", valueRange = 100f..10000f, unit = "ms", defaultValue = 1500f),
            EffectParameterDefinition(key = "damping", displayName = "Damping", valueRange = 0f..1.0f, steps = 100, unit = "", defaultValue = 0.5f),
            EffectParameterDefinition(key = "earlyReflectionsLevel", displayName = "Early Reflect", valueRange = 0f..1.0f, steps = 100, unit = "", defaultValue = 0.8f)
        )
        // TODO: Add definitions for other EffectTypes (DISTORTION, EQ_PARAMETRIC, etc.)
        // EffectType.DISTORTION -> listOf(
        // EffectParameterDefinition(key = "drive", displayName = "Drive", valueRange = 0f..1f, defaultValue = 0.5f),
        // EffectParameterDefinition(key = "tone", displayName = "Tone", valueRange = 0f..1f, defaultValue = 0.5f)
        // ),
        // EffectType.FILTER -> listOf(
        // EffectParameterDefinition(key = "cutoffHz", displayName = "Cutoff", valueRange = 20f..20000f, unit="Hz", defaultValue = 1000f),
        // EffectParameterDefinition(key = "resonance", displayName = "Resonance", valueRange = 0.5f..10f, defaultValue = 0.707f)
        // // Mode would be a separate enum selection, not a slider here.
        // )
    )

    fun getDefinitions(type: EffectType): List<EffectParameterDefinition> {
        return definitions[type] ?: emptyList()
    }

    fun getDefaultParameters(type: EffectType): MutableMap<String, Float> {
        val params = mutableMapOf<String, Float>()
        getDefinitions(type).forEach { def ->
            params[def.key] = def.defaultValue
        }
        return params
    }
}

class SynthModels {
    // This class can be removed if it's truly empty and not used elsewhere.
    // For now, keeping it as it was in the original file structure.
}
