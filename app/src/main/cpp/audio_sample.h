#ifndef THEONE_AUDIO_SAMPLE_H
#define THEONE_AUDIO_SAMPLE_H

#include <string>
#include <vector>
#include <atomic>   // Still needed for MetronomeState
#include <cstdint>
#include <cmath>

#ifndef M_PI
#define M_PI (3.14159265358979323846f)
#endif

namespace theone {
namespace audio {

struct SampleFormat {
    uint16_t channels;
    uint32_t sampleRate;
    uint16_t bitDepth;
};

struct LoadedSample {
    std::string id;
    SampleFormat format;
    std::vector<float> audioData;
    size_t frameCount;

    LoadedSample() : frameCount(0) {}

    size_t getTotalSamples() const {
        return frameCount * format.channels;
    }
};

struct PlayingSound {
    const LoadedSample* loadedSamplePtr;
    size_t currentFrame;
    float gainLeft;
    float gainRight;
    bool isActive;  // MODIFIED: Changed from std::atomic<bool>
    std::string noteInstanceId;

    PlayingSound() : loadedSamplePtr(nullptr), currentFrame(0), gainLeft(1.0f), gainRight(1.0f), isActive(false) {}

    PlayingSound(const LoadedSample* sample, std::string id, float volume, float pan)
        : loadedSamplePtr(sample),
          currentFrame(0),
          isActive(true),
          noteInstanceId(std::move(id)) {
        float panRad = (pan * 0.5f + 0.5f) * (static_cast<float>(M_PI) / 2.0f);
        gainLeft = volume * cosf(panRad);
        gainRight = volume * sinf(panRad);
    }
};

struct MetronomeState {
    std::atomic<bool> enabled;
    std::atomic<float> bpm;
    std::atomic<int> timeSignatureNum;
    std::atomic<int> timeSignatureDen;
    std::atomic<float> volume;
    const LoadedSample* primaryBeatSample;
    const LoadedSample* secondaryBeatSample;
    uint64_t framesPerBeat;
    uint64_t samplesUntilNextBeat;
    int currentBeatInBar;
    uint32_t audioStreamSampleRate;

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
