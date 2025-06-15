#ifndef THEONE_SEQUENCE_CPP_H
#define THEONE_SEQUENCE_CPP_H

#include <map>
#include <string>
#include <vector>

namespace theone {
namespace audio {

// Event trigger type enum
enum class EventTriggerTypeCpp {
    PAD_TRIGGER = 0,
    // Add other event types as needed
};

// Pad trigger event struct
struct PadTriggerEventCpp {
    std::string padId;
    int velocity = 127;
    int64_t durationTicks = 0; // Added for native-lib.cpp compatibility
};

// Sequence event struct
struct SequenceEventCpp {
    std::string id;
    std::string trackId;
    int64_t startTimeTicks = 0;
    EventTriggerTypeCpp type = EventTriggerTypeCpp::PAD_TRIGGER;
    PadTriggerEventCpp padTrigger;
    // Add other event fields as needed
};

// Track struct
struct SequenceTrackCpp {
    std::string id;
    std::vector<SequenceEventCpp> events;
};

// Sequence struct
struct SequenceCpp {
    std::string id; // Added for native-lib.cpp compatibility
    std::string name; // Added for native-lib.cpp compatibility
    bool isPlaying = false;
    int64_t currentPlayheadTicks = 0;
    int timeSignatureNumerator = 4;
    int timeSignatureDenominator = 4;
    int ppqn = 24; // Pulses per quarter note
    int barLength = 4; // Number of bars in the sequence
    float bpm = 120.0f; // Add bpm to match usage in native-lib.cpp
    std::map<std::string, SequenceTrackCpp> tracks;
};

} // namespace audio
} // namespace theone

#endif // THEONE_SEQUENCE_CPP_H
