package com.example.theone.model

// From the README, this is a solid definition
data class EnvelopeSettings(
    var type: EnvelopeType = EnvelopeType.ADSR,
    var attackMs: Float,
    var holdMs: Float? = null,
    var decayMs: Float,
    var sustainLevel: Float? = null, // 0.0 to 1.0
    var releaseMs: Float,
    var velocityToAttack: Float = 0f,
    var velocityToLevel: Float = 0f
)

enum class EnvelopeType { AD, AHDS, ADSR }

// Also from the README
data class LFOSettings(
    val id: String,
    var waveform: LfoWaveform = LfoWaveform.SINE,
    var rateHz: Float = 1.0f,
    var syncToTempo: Boolean = false,
    var tempoDivision: TimeDivision = TimeDivision.Quarter, // Assuming TimeDivision is defined elsewhere or will be
    var destinations: MutableMap<String, Float> = mutableMapOf() // ParamID -> ModDepth
)

enum class LfoWaveform { SINE, TRIANGLE, SQUARE, SAW_UP, SAW_DOWN, RANDOM }

// Placeholder for TimeDivision if not defined elsewhere yet.
// If it's in another module, it should be imported.
// If it's part of this feature, it should be defined properly.
enum class TimeDivision {
    Whole, Half, Quarter, Eighth, Sixteenth, ThirtySecond, SixtyFourth,
    DottedHalf, DottedQuarter, DottedEighth, DottedSixteenth,
    TripletWhole, TripletHalf, TripletQuarter, TripletEighth, TripletSixteenth
}
