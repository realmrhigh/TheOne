#ifndef THEONE_AUDIO_SAMPLE_H
#define THEONE_AUDIO_SAMPLE_H

#include <string>
#include <vector>
#include <atomic>   // For std::atomic<bool>
#include <cstdint>  // For uint64_t etc.

// Ensure cmath is included for M_PI, cosf, sinf
#ifndef M_PI
#define M_PI (3.14159265358979323846f)
#endif
#include <cmath>


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

struct PlayingSound {
    const LoadedSample* loadedSamplePtr; // Pointer to the sample data in gSampleMap
    size_t currentFrame;                 // Current frame to read from the sample
    float gainLeft;                      // Gain for the left channel (0.0 to 1.0)
    float gainRight;                     // Gain for the right channel (0.0 to 1.0)
    std::atomic<bool> isActive;          // Is this sound currently active and playing?
    std::string noteInstanceId;          // Unique ID for this playing instance

    PlayingSound() : loadedSamplePtr(nullptr), currentFrame(0), gainLeft(1.0f), gainRight(1.0f), isActive(false) {}

    PlayingSound(const LoadedSample* sample, std::string id, float volume, float pan)
        : loadedSamplePtr(sample),
          currentFrame(0),
          isActive(true),
          noteInstanceId(std::move(id)) {
        float panRad = (pan * 0.5f + 0.5f) * (M_PI / 2.0f);
        gainLeft = volume * cosf(panRad);
        gainRight = volume * sinf(panRad);
    }
    // Explicit Copy Constructor
    PlayingSound(const PlayingSound& other)
            : loadedSamplePtr(other.loadedSamplePtr),
              currentFrame(other.currentFrame),
              gainLeft(other.gainLeft),
              gainRight(other.gainRight),
              isActive(other.isActive.load()), // Load the value from the atomic bool
              noteInstanceId(other.noteInstanceId) {}

    // Copy Assignment Operator
    PlayingSound& operator=(const PlayingSound& other) {
        if (this == &other) { // Handle self-assignment
            return *this;
        }
        loadedSamplePtr = other.loadedSamplePtr;
        currentFrame = other.currentFrame;
        gainLeft = other.gainLeft;
        gainRight = other.gainRight;
        isActive.store(other.isActive.load()); // Store for atomic bool
        noteInstanceId = other.noteInstanceId;
        return *this;
    }
};

// New struct for Metronome State
struct MetronomeState {
    std::atomic<bool> enabled;
    std::atomic<float> bpm;
    std::atomic<int> timeSignatureNum;
    std::atomic<int> timeSignatureDen;
    std::atomic<float> volume;

    const LoadedSample* primaryBeatSample;   // Pointer to pre-loaded sample
    const LoadedSample* secondaryBeatSample; // Pointer to pre-loaded sample

    uint64_t framesPerBeat;
    uint64_t samplesUntilNextBeat; // Frames remaining until the next beat event
    int currentBeatInBar;       // Which beat are we on in the current bar (e.g., 1 to 4 for 4/4)
    uint32_t audioStreamSampleRate; // Cache the stream's sample rate for calculations

    MetronomeState() :
        enabled(false), bpm(120.0f), timeSignatureNum(4), timeSignatureDen(4),
        volume(0.7f), primaryBeatSample(nullptr), secondaryBeatSample(nullptr),
        framesPerBeat(0), samplesUntilNextBeat(0), currentBeatInBar(0), audioStreamSampleRate(48000) {}

    void updateSchedulingParameters() {
        if (bpm.load() <= 0.0f || audioStreamSampleRate == 0) {
            framesPerBeat = 0;
            return;
        }
        double secondsPerBeat = 60.0 / bpm.load();
        framesPerBeat = static_cast<uint64_t>(secondsPerBeat * audioStreamSampleRate);
    }
};

} // namespace audio
} // namespace theone

#endif //THEONE_AUDIO_SAMPLE_H
