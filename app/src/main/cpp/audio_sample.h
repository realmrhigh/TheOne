#ifndef THEONE_AUDIO_SAMPLE_H
#define THEONE_AUDIO_SAMPLE_H

#include <string>
#include <vector>
#include <atomic> // For std::atomic_bool if fine-grained activity status is needed later
#include <cmath>  // For M_PI, cosf, sinf (might need _USE_MATH_DEFINES for M_PI)

#ifndef M_PI // Define M_PI if not defined by cmath (common on some compilers with strict ANSI)
#define M_PI (3.14159265358979323846f)
#endif

namespace theone {
namespace audio {

// Existing SampleFormat and LoadedSample structs...
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

// New struct for active playing sounds
struct PlayingSound {
    const LoadedSample* loadedSamplePtr; // Pointer to the sample data in gSampleMap
    size_t currentFrame;                 // Current frame to read from the sample
    float gainLeft;                      // Gain for the left channel (0.0 to 1.0)
    float gainRight;                     // Gain for the right channel (0.0 to 1.0)
    std::atomic<bool> isActive;          // Is this sound currently active and playing?
    std::string noteInstanceId;          // Unique ID for this playing instance

    PlayingSound() : loadedSamplePtr(nullptr), currentFrame(0), gainLeft(1.0f), gainRight(1.0f), isActive(false) {}

    // Constructor to initialize a new playing sound
    PlayingSound(const LoadedSample* sample, std::string id, float volume, float pan)
        : loadedSamplePtr(sample),
          currentFrame(0),
          isActive(true),
          noteInstanceId(std::move(id)) {
        // Basic panning:
        // Pan = 0.0 (center): gainLeft = volume, gainRight = volume
        // Pan = -1.0 (left): gainLeft = volume, gainRight = 0
        // Pan = 1.0 (right): gainLeft = 0, gainRight = volume
        // Using square root panning for smoother perceived loudness
        float panRad = (pan * 0.5f + 0.5f) * (M_PI / 2.0f); // Map pan from [-1, 1] to [0, PI/2]
        gainLeft = volume * cosf(panRad);
        gainRight = volume * sinf(panRad);
    }
};

} // namespace audio
} // namespace theone

#endif //THEONE_AUDIO_SAMPLE_H
