#ifndef THEONE_LFO_GENERATOR_H
#define THEONE_LFO_GENERATOR_H

#include <string>
#include <vector>

enum class LfoDestinationCpp {
    NONE, PITCH, PAN, VOLUME, FILTER_CUTOFF, FILTER_RESONANCE,
    NUM_LFO_DESTINATIONS
};
#include <map>
#include <cstdint> // For float, uint32_t
#include <cmath>   // For M_PI, sinf, etc.

// Ensure M_PI is defined (often in cmath or math.h)
#ifndef M_PI
#define M_PI (3.14159265358979323846)
#endif

namespace theone {
namespace audio {

// C++ counterparts for Kotlin enums
enum class LfoWaveformCpp {
    SINE,
    TRIANGLE,
    SQUARE,
    SAW_UP, // Ramp up
    SAW_DOWN, // Ramp down
    RANDOM_STEP, // Stepped random values
    RANDOM_SMOOTH, // Smoothly interpolated random values (more complex, placeholder)
    NUM_LFO_WAVEFORMS // FIX: Add this line
};

// Assuming TimeDivisionCpp is needed if syncToTempo is true.
// This should ideally align with a shared TimeDivision definition if one exists (e.g., from SynthModels.kt)
enum class TimeDivisionCpp {
    WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH, THIRTY_SECOND, SIXTY_FOURTH,
    DOTTED_HALF, DOTTED_QUARTER, DOTTED_EIGHTH, DOTTED_SIXTEENTH,
    TRIPLET_WHOLE, TRIPLET_HALF, TRIPLET_QUARTER, TRIPLET_EIGHTH, TRIPLET_SIXTEENTH,
    // Add more as needed based on the Kotlin definition
    NONE, // Default if not synced or applicable
    NUM_TIME_DIVISIONS // FIX: Add this line
};


struct LfoSettingsCpp {
    std::string id; // Identifier for the LFO instance
    bool isEnabled = false;                             // FIX: Add this line
    LfoWaveformCpp waveform = LfoWaveformCpp::SINE;
    float rateHz = 1.0f;
    bool syncToTempo = false;
    TimeDivisionCpp tempoDivision = TimeDivisionCpp::QUARTER; // e.g., LFO cycle = 1 quarter note
    float depth = 0.5f;                                 // FIX: Add this line
    LfoDestinationCpp primaryDestination = LfoDestinationCpp::NONE; // FIX: Add this line
    // Destinations are handled by the system using this LFO, not by the LFO generator itself.
    // The generator just outputs a value. The mapping (destinations, modDepth) is applied externally.
    // std::map<std::string, float> destinations; // ParamID -> ModDepth

    // Default constructor
    LfoSettingsCpp() = default;

    // Parameterized constructor
    LfoSettingsCpp(std::string lfoId, LfoWaveformCpp wf, float rate, bool sync, TimeDivisionCpp division)
        : id(std::move(lfoId)), waveform(wf), rateHz(rate), syncToTempo(sync), tempoDivision(division) {}
};

class LfoGenerator {
public:
    LfoGenerator();

    // Configure the LFO with settings and system parameters
    void configure(const LfoSettingsCpp& settings, float sampleRate, float tempoBpm = 120.0f);

    // Process one sample, returns LFO value in -1.0 to 1.0 range (mostly)
    float process();

    // Resets the LFO's phase to a default start (typically 0)
    void resetPhase();

    // Re-trigger the LFO, possibly syncing its phase
    void retrigger();

    // Get current LFO parameters
    const LfoSettingsCpp& getSettings() const { return settings; }

private:
    LfoSettingsCpp settings;
    float sampleRate;
    float currentTempoBpm; // Store the last known tempo for synced LFOs

    double phase;        // Current phase of the LFO, 0.0 to 1.0 (or 0 to 2*PI for some calculations)
    double phaseIncrement; // Amount to increment phase per sample

    float lastRandomValue; // For RANDOM_STEP and RANDOM_SMOOTH
    float nextRandomValue;   // For RANDOM_SMOOTH interpolation
    uint32_t samplesUntilNextRandomStep; // For RANDOM_STEP

    void calculatePhaseIncrement(); // Helper to update phaseIncrement based on current settings
    float generateSine();
    float generateTriangle();
    float generateSquare();
    float generateSawUp();
    float generateSawDown();
    float generateRandomStep();
    // float generateRandomSmooth(); // More complex, might defer full implementation
};

} // namespace audio
} // namespace theone

#endif // THEONE_LFO_GENERATOR_H
