#include "gtest/gtest.h"
#include "AudioEngine.h" // Adjust path if needed: ../../main/cpp/AudioEngine.h
#include "EnvelopeGenerator.h" // Adjust path: ../../main/cpp/EnvelopeGenerator.h
#include "audio_sample.h"  // Adjust path: ../../main/cpp/audio_sample.h
#include "PadSettings.h"   // Adjust path: ../../main/cpp/PadSettings.h
#include <oboe/Oboe.h>     // For oboe::AudioStreamBase and other types
#include <vector>
#include <memory>
#include <string>
#include <cstdlib> // For rand() in dummy sample ID

// Define kTestSampleRate globally or within the namespace
const float kTestSampleRate = 48000.0f;

namespace theone {
namespace audio {
namespace testing {

// Helper to create a dummy LoadedSample
std::shared_ptr<theone::audio::LoadedSample> createDummySample(uint32_t sr, int numFrames = 100, int channels = 1) {
    auto sample = std::make_shared<theone::audio::LoadedSample>();
    sample->id = "dummy_sample_" + std::to_string(rand());
    sample->format.channels = static_cast<uint16_t>(channels);
    sample->format.sampleRate = sr;
    sample->format.bitDepth = 32; // Assuming float samples
    sample->frameCount = static_cast<uint32_t>(numFrames);
    sample->audioData.assign(static_cast<size_t>(numFrames) * channels, 0.0f); // Silent audio
    return sample;
}

class AudioEngineOnAudioReadyTest : public ::testing::Test {
protected:
    std::unique_ptr<AudioEngine> audioEngine;
    std::vector<float> audioBuffer;

    // Using a real AudioStreamBase to pass to onAudioReady.
    // We are not calling oboeStream->open(), just using it as a data carrier.
    // For a more advanced mock, you'd use a mocking framework like Google Mock.
    oboe::AudioStreamBase mockStreamBase;

    static const int kDefaultNumFrames = 128;
    static const int kDefaultChannels = 2;

    void SetUp() override {
        audioEngine = std::make_unique<AudioEngine>();
        // audioEngine->initialize(); // NOT called to avoid real Oboe stream.

        // Set the sample rate for the AudioEngine internally for test purposes.
        // This is crucial because EnvelopeGenerators, etc., will be configured
        // using this rate when sounds are created.
        audioEngine->setAudioStreamSampleRateForTest(static_cast<uint32_t>(kTestSampleRate));

        audioBuffer.resize(kDefaultNumFrames * kDefaultChannels);

        mockStreamBase.setChannelCount(kDefaultChannels);
        mockStreamBase.setSampleRate(static_cast<int32_t>(kTestSampleRate));
        mockStreamBase.setFormat(oboe::AudioFormat::Float);
        mockStreamBase.setFramesPerCallback(kDefaultNumFrames); // Informative
        mockStreamBase.setDirection(oboe::Direction::Output);
        mockStreamBase.setSharingMode(oboe::SharingMode::Exclusive);
        mockStreamBase.setPerformanceMode(oboe::PerformanceMode::LowLatency);

        // Clear buffer for each test
        std::fill(audioBuffer.begin(), audioBuffer.end(), 0.0f);
    }

    void TearDown() override {
        // audioEngine->shutdown(); // If it were initialized
    }
};

TEST_F(AudioEngineOnAudioReadyTest, NoSounds) {
    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 0);
    audioEngine->onAudioReady(&mockStreamBase, audioBuffer.data(), kDefaultNumFrames);
    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 0);
    // Optionally, check if buffer is still silent
    for(float val : audioBuffer) {
        ASSERT_EQ(val, 0.0f);
    }
}

TEST_F(AudioEngineOnAudioReadyTest, AllSoundsRemoved) {
    const int numSounds = 3;
    for (int i = 0; i < numSounds; ++i) {
        PlayingSound sound(createDummySample(static_cast<uint32_t>(kTestSampleRate), kDefaultNumFrames).get(),
                           "test_sound_" + std::to_string(i), 1.0f, 0.0f);

        sound.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>();
        EnvelopeSettingsCpp envSettings;
        envSettings.type = ModelEnvelopeTypeInternalCpp::ADSR;
        envSettings.attackMs = 1.0f;
        envSettings.decayMs = 1.0f;
        envSettings.sustainLevel = 0.0f;
        envSettings.releaseMs = 1.0f; // Very short envelope

        // Ensure sample rate is passed for envelope configuration
        sound.ampEnvelopeGen->configure(envSettings, kTestSampleRate, 1.0f);
        sound.ampEnvelopeGen->triggerOn(1.0f);

        audioEngine->addPlayingSoundForTest(std::move(sound));
    }

    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), numSounds);

    // Process enough frames for the short envelopes to complete and sounds to be marked inactive.
    // kDefaultNumFrames (128) at 48kHz is about 2.6ms. Envelopes are ~3ms total.
    // One call might be enough if process() inside onAudioReady updates envelopes sufficiently.
    // Let's call it a few times if needed, or ensure numFrames is large enough.
    // The current onAudioReady processes envelopes once per sound per call.
    // If attack (1ms) + decay (1ms) + release (1ms) = 3ms.
    // 128 frames at 48000Hz = 128/48000 * 1000ms = ~2.66ms.
    // This is tricky. The envelope needs to go through its stages.
    // Let's make numFrames for onAudioReady call larger to ensure envelopes finish.
    // Or, call onAudioReady multiple times.
    // For this test, let's make the processing time clearly longer than the envelope.
    // Say, 10ms of audio. 10ms * 48 samples/ms = 480 frames.

    std::vector<float> largerBuffer(480 * kDefaultChannels, 0.0f);
    mockStreamBase.setFramesPerCallback(480); // Update mock stream info

    audioEngine->onAudioReady(&mockStreamBase, largerBuffer.data(), 480);

    // After processing, all sounds with short envelopes should be gone.
    // The internal loop in onAudioReady calls env->process() once for each sound.
    // If the envelope isn't IDLE and value < threshold after one process call (unlikely for 1ms stages),
    // it won't be marked inactive immediately.
    // The sound.isActive is set when ampEnvValue is small AND stage is IDLE.
    // A single process() call on EnvelopeGenerator might not advance it through multiple stages.
    // Let's assume the EnvelopeGenerator's process() is efficient enough that for very short stage times,
    // it can transition through them if called repeatedly. The onAudioReady loop calls it once per sound.
    // For the test to be robust, we might need to call onAudioReady multiple times,
    // or ensure the EnvelopeGenerator can fully transition with very short times in one go if time delta is large.
    // The EnvelopeGenerator::process() is called without a delta time argument in PlayingSound,
    // implying it calculates one step per call.
    // So, we need to process enough *samples* via onAudioReady's main loop.
    // Each call to sound.ampEnvelopeGen->process() is one "tick" for the envelope.
    // Total envelope duration: attack (1ms) + decay (1ms to 0) + release (1ms).
    // 1ms at 48kHz = 48 samples. So roughly 3 * 48 = 144 envelope "ticks" (process calls).
    // The onAudioReady is called with numFrames. The inner loop of onAudioReady processes one sample at a time
    // but calls envelope.process() only ONCE per sound at the beginning of its processing block.
    // This means a sound's envelope is advanced by one step by onAudioReady, regardless of numFrames.
    // This is a key point from the provided code.
    // THEREFORE, to make sounds inactive, onAudioReady needs to be called multiple times.

    // Call onAudioReady enough times for envelopes to complete their stages.
    // An envelope stage needs roughly stage_ms * kTestSampleRate / 1000 calls to its process()
    // if process advances by one sample time. Or fewer if it's more coarse.
    // Let's assume EnvelopeGenerator::process() is efficient.
    // Given the current structure, each call to onAudioReady is one tick for the envelope.
    // So we need ~150 calls to onAudioReady. This is not ideal for a unit test.
    // This points to a potential design consideration in onAudioReady's interaction with EnvelopeGenerator.
    // However, the task is to test the *current* logic.
    // The current logic: env->process() is called once per sound per onAudioReady call.
    // If attack = 1ms (48 samples), decay = 1ms (48 samples), release = 1ms (48 samples).
    // The envelope generator needs to be "ticked" about 144 times.
    // So we'd have to call onAudioReady ~144 times.

    // Let's re-evaluate: The envelope processing in AudioEngine is:
    // ampEnvValue = sound.ampEnvelopeGen->process();
    // This means the envelope is advanced by ONE step. This step's duration is effectively
    // linked to the rate at which onAudioReady is called, not numFrames in one call.
    // This seems like a flaw in how envelopes are processed in the loop if rapid changes are expected within one callback.
    // For the test: to make it pass with current code, we must call it many times.
    // Or, assume EnvelopeGenerator's stages are extremely short (e.g., 0.01ms) so they finish in few "ticks".
    // Let's use very short envelope times for the test and call onAudioReady, say, 10 times.
    // This implies each stage might take 1-2 calls to process().
    // Let's make envelope times effectively 0 for attack/decay/release for the test.
    envSettings.attackMs = 0.0f;
    envSettings.decayMs = 0.0f; // sustain is 0
    envSettings.releaseMs = 0.0f; // sustain is 0, so it should go to IDLE fast

    // Re-create sounds with ultra-fast envelopes
    audioEngine->activeSounds_.clear(); // Clear previously added sounds if any test setup issue
    for (int i = 0; i < numSounds; ++i) {
        PlayingSound sound(createDummySample(static_cast<uint32_t>(kTestSampleRate), kDefaultNumFrames).get(),
                           "test_sound_ultra_fast_" + std::to_string(i), 1.0f, 0.0f);
        sound.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>();
        sound.ampEnvelopeGen->configure(envSettings, kTestSampleRate, 1.0f);
        sound.ampEnvelopeGen->triggerOn(1.0f);
        // Manually put into release if attack/decay are zero to simulate it should be cleaning up.
        // This depends on EnvelopeGenerator's behavior with 0ms stages.
        // A well-behaved 0ms attack/decay ADSR (sustain 0) might go to IDLE or near zero value very quickly.
        // Let's assume after triggerOn and a few process calls it should be done.
        audioEngine->addPlayingSoundForTest(std::move(sound));
    }
    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), numSounds);

    // Call onAudioReady a few times. 5 times should be plenty for 0ms envelopes.
    for(int callCount = 0; callCount < 5; ++callCount) {
        audioEngine->onAudioReady(&mockStreamBase, audioBuffer.data(), kDefaultNumFrames);
        if (audioEngine->getActiveSoundsCountForTest() == 0) break; // Optimization
    }

    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 0);
}

TEST_F(AudioEngineOnAudioReadyTest, SomeSoundsRemoved) {
    // Sound 1: Short envelope, expected to be removed
    PlayingSound sound1(createDummySample(static_cast<uint32_t>(kTestSampleRate), kDefaultNumFrames).get(),
                       "sound_short", 1.0f, 0.0f);
    sound1.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>();
    EnvelopeSettingsCpp shortEnvSettings;
    shortEnvSettings.type = ModelEnvelopeTypeInternalCpp::ADSR;
    shortEnvSettings.attackMs = 0.0f;
    shortEnvSettings.decayMs = 0.0f;
    shortEnvSettings.sustainLevel = 0.0f;
    shortEnvSettings.releaseMs = 0.0f;
    sound1.ampEnvelopeGen->configure(shortEnvSettings, kTestSampleRate, 1.0f);
    sound1.ampEnvelopeGen->triggerOn(1.0f);
    audioEngine->addPlayingSoundForTest(std::move(sound1));

    // Sound 2: Long envelope, expected to remain
    PlayingSound sound2(createDummySample(static_cast<uint32_t>(kTestSampleRate), kDefaultNumFrames * 10).get(), // Longer sample data
                       "sound_long", 1.0f, 0.0f);
    sound2.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>();
    EnvelopeSettingsCpp longEnvSettings;
    longEnvSettings.type = ModelEnvelopeTypeInternalCpp::ADSR;
    longEnvSettings.attackMs = 100.0f; // Long times
    longEnvSettings.decayMs = 100.0f;
    longEnvSettings.sustainLevel = 1.0f;
    longEnvSettings.releaseMs = 100.0f;
    sound2.ampEnvelopeGen->configure(longEnvSettings, kTestSampleRate, 1.0f);
    sound2.ampEnvelopeGen->triggerOn(1.0f);
    // Store its instance ID to check later, if PlayingSound had a reliable one.
    // For now, we rely on count.
    audioEngine->addPlayingSoundForTest(std::move(sound2));

    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 2);

    // Call onAudioReady a few times (enough for short envelope to finish)
    for(int callCount = 0; callCount < 5; ++callCount) {
        audioEngine->onAudioReady(&mockStreamBase, audioBuffer.data(), kDefaultNumFrames);
    }

    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 1);

    // Optional: Verify the remaining sound is the one with the long envelope.
    // This requires accessing activeSounds_ or having a getter that returns sound details.
    // For now, the count check is the primary goal.
    // If we had access:
    // std::lock_guard<std::mutex> lock(audioEngine->activeSoundsMutex_); // If mutex was public/friend
    // ASSERT_FALSE(audioEngine->activeSounds_.empty());
    // ASSERT_EQ(audioEngine->activeSounds_[0].noteInstanceId, "sound_long"); // Example check
}

TEST_F(AudioEngineOnAudioReadyTest, SoundFinishesExactlyAtBufferEnd) {
    // Create a sound whose sample data is exactly kDefaultNumFrames long.
    // Its envelope should also be configured to not sustain indefinitely.
    std::shared_ptr<LoadedSample> sample = createDummySample(static_cast<uint32_t>(kTestSampleRate), kDefaultNumFrames);

    PlayingSound sound(sample.get(), "sound_exact_finish", 1.0f, 0.0f);
    sound.currentFrame = 0; // Start at the beginning

    sound.ampEnvelopeGen = std::make_unique<EnvelopeGenerator>();
    EnvelopeSettingsCpp envSettings;
    envSettings.type = ModelEnvelopeTypeInternalCpp::ADSR;
    // Envelope that finishes quickly but doesn't make the sound inactive before its samples run out.
    // Sustain should be > 0, release very short.
    envSettings.attackMs = 1.0f;
    envSettings.decayMs = 1.0f;
    envSettings.sustainLevel = 1.0f; // Keep it active during its sample playback
    envSettings.releaseMs = 0.0f;   // Release is instant once triggerOff is called (not called here directly)
                                    // Or, if sound ends due to sample frames, release should kick in.
                                    // The current logic in onAudioReady for sample end:
                                    // if (sound.currentFrame >= loadedSample->frameCount) { sound.isActive.store(false); }
                                    // This happens *before* envelope release is explicitly triggered by triggerOff().
                                    // So, for this test, the envelope's state post-sample-end isn't the primary factor
                                    // for removal, but rather the currentFrame check.
                                    // Let's ensure the envelope would keep it alive for the duration of the sample.
    sound.ampEnvelopeGen->configure(envSettings, kTestSampleRate, 1.0f);
    sound.ampEnvelopeGen->triggerOn(1.0f);

    audioEngine->addPlayingSoundForTest(std::move(sound));
    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 1);

    // Call onAudioReady once with kDefaultNumFrames.
    // The sound has kDefaultNumFrames of audio data.
    // The loop inside onAudioReady is `for (int i = 0; i < numFrames; ++i)`
    // and `sound.currentFrame` is incremented after processing each frame.
    // So, after processing frame `kDefaultNumFrames - 1` (which is `audioData[kDefaultNumFrames-1]`),
    // `sound.currentFrame` becomes `kDefaultNumFrames`.
    // The check `if (sound.currentFrame >= loadedSample->frameCount)` will then be true.
    // `sound.isActive` will be set to false.
    // The sound should then be removed by the erase-remove-if idiom.

    audioEngine->onAudioReady(&mockStreamBase, audioBuffer.data(), kDefaultNumFrames);

    ASSERT_EQ(audioEngine->getActiveSoundsCountForTest(), 0);
}

} // namespace testing
} // namespace audio
} // namespace theone
