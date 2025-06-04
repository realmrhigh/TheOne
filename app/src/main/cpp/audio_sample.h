#ifndef THEONE_AUDIO_SAMPLE_H
#define THEONE_AUDIO_SAMPLE_H

#include <string>
#include <vector> // Using vector to manage memory for the sample data

namespace theone {
namespace audio {

struct SampleFormat {
    uint16_t channels;       // Number of channels (e.g., 1 for mono, 2 for stereo)
    uint32_t sampleRate;     // Sample rate (e.g., 44100, 48000 Hz)
    uint16_t bitDepth;       // Bits per sample (e.g., 16 for int16_t, 32 for float)
                             // Note: Internally, we might convert to float for processing
};

struct LoadedSample {
    std::string id;          // Unique sample ID
    SampleFormat format;
    std::vector<float> audioData; // Store audio data as floats, normalized to -1.0 to 1.0
    size_t frameCount;       // Number of frames (samples per channel)

    LoadedSample() : frameCount(0) {}

    // Helper to get total samples (frames * channels)
    size_t getTotalSamples() const {
        return frameCount * format.channels;
    }
};

} // namespace audio
} // namespace theone

#endif //THEONE_AUDIO_SAMPLE_H
