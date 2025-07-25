#ifndef THEONE_PADSETTINGS_H
#define THEONE_PADSETTINGS_H

#include <string>
#include <vector>
#include "EnvelopeGenerator.h"
#include "LfoGenerator.h"
#include "StateVariableFilter.h" // For SVF_Mode enum

namespace theone {
namespace audio {

// --- Filter Settings ---
struct FilterSettingsCpp {
    bool enabled = false;
    theone::audio::SVF_Mode mode = theone::audio::SVF_Mode::LOW_PASS;
    float cutoffHz = 18000.0f;
    float resonance = 0.707f; // Q factor
    float envAmount = 0.0f;   // e.g., +/- octaves for cutoff modulation by filter env
    // LFO modulation for filter is typically handled by LFO target settings
};

// C++ Data Structures Mirroring Kotlin Models
struct SampleLayerCpp {
    std::string id;
    std::string sampleId;
    bool enabled = true;
    int velocityRangeMin = 0;
    int velocityRangeMax = 127;
    int tuningCoarseOffset = 0;
    int tuningFineOffset = 0;
    float volumeOffsetDb = 0.0f;
    float panOffset = 0.0f;
};

enum class LayerTriggerRuleCpp {
    VELOCITY,
    CYCLE,
    RANDOM
};

enum class PlaybackModeCpp {
    ONE_SHOT,
    LOOP,
    GATE
};

struct PadSettingsCpp {
    std::string id;
    std::vector<SampleLayerCpp> layers;
    LayerTriggerRuleCpp layerTriggerRule = LayerTriggerRuleCpp::VELOCITY;
    int currentCycleLayerIndex = 0;
    PlaybackModeCpp playbackMode = PlaybackModeCpp::ONE_SHOT;
    int tuningCoarse = 0;
    int tuningFine = 0;
    float volume = 1.0f;
    float pan = 0.0f;
    int muteGroup = 0;
    int polyphony = 16;
    EnvelopeSettingsCpp ampEnvelope;
    bool hasFilterEnvelope = false;
    EnvelopeSettingsCpp filterEnvelope;
    FilterSettingsCpp filterSettings; // Added FilterSettings
    bool hasPitchEnvelope = false;
    EnvelopeSettingsCpp pitchEnvelope;
    std::vector<LfoSettingsCpp> lfos;
};

} // namespace audio
} // namespace theone

#endif //THEONE_PADSETTINGS_H
