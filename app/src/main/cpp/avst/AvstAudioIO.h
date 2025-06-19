#pragma once

#include <vector>
#include <cstdint>
#include <string>

namespace avst {

struct AudioChannelConfig {
    uint32_t inputChannels;
    uint32_t outputChannels;
    std::string name;  // "Mono", "Stereo", "5.1", etc.
};

struct AudioIOConfig {
    std::vector<AudioChannelConfig> supportedConfigs;
    uint32_t currentInputChannels = 0;
    uint32_t currentOutputChannels = 2;  // Default stereo
    
    float sampleRate = 44100.0f;
    uint32_t maxBlockSize = 512;
    
    // Mobile-specific optimizations
    bool supportsVariableBlockSize = true;
    bool supportsSampleRateConversion = false;
    uint32_t preferredLatencyFrames = 128;
};

} // namespace avst
