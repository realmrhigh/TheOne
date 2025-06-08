#ifndef THEONE_AUDIO_SAMPLE_H
#define THEONE_AUDIO_SAMPLE_H

#include <string>
#include <vector>
#include <atomic>   // For std::atomic<bool>
#include <cstdint>  // For uint64_t etc.
#include <memory>   // For std::unique_ptr

// Ensure cmath is included for M_PI, cosf, sinf
#ifndef M_PI
#define M_PI (3.14159265358979323846f)
#endif
#include <cmath>


namespace theone {
namespace audio {

// Forward declarations
class EnvelopeGenerator;
class LfoGenerator;
struct PadSettingsCpp; // Forward declaration for PadSettingsCpp

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
    const LoadedSample* loadedSamplePtr;
    size_t currentFrame;
    float gainLeft;
    float gainRight;
    std::atomic<bool> isActive;
    std::string noteInstanceId;

    float initialVolume; // Base volume before envelope and LFOs
    float initialPan;    // Base pan before LFOs

    // --- NEW: Envelopes and LFOs ---
    std::unique_ptr<EnvelopeGenerator> ampEnvelopeGen;
    std::unique_ptr<EnvelopeGenerator> filterEnvelopeGen; // Optional, might be nullptr
    std::unique_ptr<EnvelopeGenerator> pitchEnvelopeGen;  // Optional, might be nullptr
    std::vector<std::unique_ptr<LfoGenerator>> lfoGens;
    // --- END NEW ---

    std::shared_ptr<PadSettingsCpp> padSettings; // Pad settings for this sound

    // New members for slicing and looping
    size_t startFrame;
    size_t endFrame; // Relative to the start of the sample data. 0 means effective end of sample.
    size_t loopStartFrame; // Relative to the start of the sample data.
    size_t loopEndFrame;   // Relative to the start of the sample data. 0 means effective end of sample.
    bool isLooping;
    bool useSlicing;

    // Default constructor
    PlayingSound() : loadedSamplePtr(nullptr), currentFrame(0),
                     gainLeft(1.0f), gainRight(1.0f), isActive(false),
                     initialVolume(1.0f), initialPan(0.0f), // Initialize new members
                     ampEnvelopeGen(nullptr), filterEnvelopeGen(nullptr), pitchEnvelopeGen(nullptr),
                     padSettings(nullptr), // Initialize padSettings
                     startFrame(0), endFrame(0),
                     loopStartFrame(0), loopEndFrame(0),
                     isLooping(false), useSlicing(false) {}

    // Constructor for simple playback (no slicing)
    PlayingSound(const LoadedSample* sample, std::string id, float volume, float pan)
        : loadedSamplePtr(sample),
          currentFrame(0), // For non-sliced, always start at 0
          isActive(true),
          noteInstanceId(std::move(id)),
          initialVolume(volume), initialPan(pan), // Initialize new members
          ampEnvelopeGen(nullptr), filterEnvelopeGen(nullptr), pitchEnvelopeGen(nullptr),
          padSettings(nullptr), // Initialize padSettings
          startFrame(0),
          endFrame(sample ? sample->frameCount : 0), // Default to full sample length
          loopStartFrame(0),
          loopEndFrame(0), // Default to full sample length if looping were added here
          isLooping(false),
          useSlicing(false) { // Explicitly false for this constructor
        float panRad = (pan * 0.5f + 0.5f) * (M_PI / 2.0f);
        gainLeft = volume * cosf(panRad);
        gainRight = volume * sinf(panRad);
    }

    // Constructor for slicing and looping playback
    PlayingSound(const LoadedSample* sample, std::string id, float volume, float pan,
                 size_t sf, size_t ef, size_t lsf, size_t lef, bool looping)
        : loadedSamplePtr(sample),
          currentFrame(sf), // Start playback at startFrame
          isActive(true),
          noteInstanceId(std::move(id)),
          initialVolume(volume), initialPan(pan), // Initialize new members
          ampEnvelopeGen(nullptr), filterEnvelopeGen(nullptr), pitchEnvelopeGen(nullptr),
          padSettings(nullptr), // Initialize padSettings
          startFrame(sf),
          endFrame(ef == 0 && sample ? sample->frameCount : ef), // If 0, use sample's frameCount
          loopStartFrame(lsf),
          loopEndFrame(lef == 0 && sample ? sample->frameCount : lef), // If 0, use sample's frameCount
          isLooping(looping),
          useSlicing(true) { // Explicitly true for this constructor
        float panRad = (pan * 0.5f + 0.5f) * (M_PI / 2.0f);
        gainLeft = volume * cosf(panRad);
        gainRight = volume * sinf(panRad);

        // Basic validation: ensure endFrame is not beyond actual sample data
        if (loadedSamplePtr && this->endFrame > loadedSamplePtr->frameCount) {
            this->endFrame = loadedSamplePtr->frameCount;
        }
        if (loadedSamplePtr && this->loopEndFrame > loadedSamplePtr->frameCount) {
            this->loopEndFrame = loadedSamplePtr->frameCount;
        }
        // Ensure currentFrame is not beyond the (potentially adjusted) endFrame
        if (this->currentFrame >= this->endFrame) {
             this->currentFrame = this->startFrame; // Or handle as error/inactive
        }
    }

    // Copy Constructor (ensure all members are copied)
    PlayingSound(const PlayingSound&) = delete;
    PlayingSound& operator=(const PlayingSound&) = delete;

    PlayingSound(PlayingSound&& other) noexcept
        : loadedSamplePtr(other.loadedSamplePtr),
          currentFrame(other.currentFrame),
          gainLeft(other.gainLeft),
          gainRight(other.gainRight),
          isActive(other.isActive.load()), // std::atomic needs load()
          noteInstanceId(std::move(other.noteInstanceId)),
          initialVolume(other.initialVolume), // Move new members
          initialPan(other.initialPan),       // Move new members
          ampEnvelopeGen(std::move(other.ampEnvelopeGen)),
          filterEnvelopeGen(std::move(other.filterEnvelopeGen)),
          pitchEnvelopeGen(std::move(other.pitchEnvelopeGen)),
          lfoGens(std::move(other.lfoGens)),
          padSettings(std::move(other.padSettings)), // Move padSettings
          startFrame(other.startFrame),
          endFrame(other.endFrame),
          loopStartFrame(other.loopStartFrame),
          loopEndFrame(other.loopEndFrame),
          isLooping(other.isLooping),
          useSlicing(other.useSlicing) {
        other.loadedSamplePtr = nullptr; // Null out moved-from raw pointer
        other.currentFrame = 0;
        other.isActive.store(false);
        other.initialVolume = 1.0f; // Reset moved-from object's values
        other.initialPan = 0.0f;
        // other.padSettings is already moved and will be nullptr
    }

    PlayingSound& operator=(PlayingSound&& other) noexcept {
        if (this == &other) {
            return *this;
        }
        loadedSamplePtr = other.loadedSamplePtr;
        currentFrame = other.currentFrame;
        gainLeft = other.gainLeft;
        gainRight = other.gainRight;
        isActive.store(other.isActive.load()); // std::atomic needs load() then store()
        noteInstanceId = std::move(other.noteInstanceId);
        initialVolume = other.initialVolume; // Assign new members
        initialPan = other.initialPan;       // Assign new members
        ampEnvelopeGen = std::move(other.ampEnvelopeGen);
        filterEnvelopeGen = std::move(other.filterEnvelopeGen);
        pitchEnvelopeGen = std::move(other.pitchEnvelopeGen);
        lfoGens = std::move(other.lfoGens);
        padSettings = std::move(other.padSettings); // Assign padSettings
        startFrame = other.startFrame;
        endFrame = other.endFrame;
        loopStartFrame = other.loopStartFrame;
        loopEndFrame = other.loopEndFrame;
        isLooping = other.isLooping;
        useSlicing = other.useSlicing;

        other.loadedSamplePtr = nullptr; // Null out moved-from raw pointer
        other.currentFrame = 0;
        other.isActive.store(false);
        other.initialVolume = 1.0f; // Reset moved-from object's values
        other.initialPan = 0.0f;
        // other.padSettings is already moved and will be nullptr
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
