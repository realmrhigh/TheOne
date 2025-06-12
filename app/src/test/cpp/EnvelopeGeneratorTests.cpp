#include "gtest/gtest.h"
#include "../../main/cpp/EnvelopeGenerator.h" // Adjusted path
#include <cmath>

namespace theone {
namespace audio {
namespace testing {

const float kTestEpsilon = 1e-5f;

// Test fixture for EnvelopeGenerator tests
class EnvelopeGeneratorTest : public ::testing::Test {
protected:
    theone::audio::EnvelopeGenerator envelopeGenerator;
    theone::audio::EnvelopeSettingsCpp settings;
    float sampleRate = 44100.0f;

    void SetUp() override {
        // Default settings for many tests
        settings.type = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR;
        settings.attackMs = 10.0f;
        settings.holdMs = 0.0f;
        settings.decayMs = 20.0f;
        settings.sustainLevel = 0.5f;
        settings.releaseMs = 15.0f;
        // Configure with default sampleRate, can be overridden in tests
        envelopeGenerator.configure(sampleRate, settings);
    }
};

TEST_F(EnvelopeGeneratorTest, InitialState) {
    // Default constructed EnvelopeGenerator (implicitly done by fixture)
    // Re-create one to ensure it's default constructed for this specific test
    theone::audio::EnvelopeGenerator defaultGenerator;
    EXPECT_EQ(defaultGenerator.getCurrentValue(), 0.0f);
    EXPECT_EQ(defaultGenerator.getCurrentStage(), theone::audio::EnvelopeStage::IDLE);
    EXPECT_FALSE(defaultGenerator.isActive());
}

TEST_F(EnvelopeGeneratorTest, ADSR_BasicCycle) {
    // Settings are configured in SetUp
    envelopeGenerator.triggerOn();
    EXPECT_TRUE(envelopeGenerator.isActive());
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::ATTACK);

    // Attack phase
    int attackSamples = static_cast<int>(std::ceil(settings.attackMs / 1000.0f * sampleRate));
    for (int i = 0; i < attackSamples; ++i) {
        envelopeGenerator.process();
    }
    // Allow for slight variance due to discrete steps
    EXPECT_NEAR(envelopeGenerator.getCurrentValue(), 1.0f, kTestEpsilon);
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::DECAY);

    // Decay phase
    int decaySamples = static_cast<int>(std::ceil(settings.decayMs / 1000.0f * sampleRate));
    for (int i = 0; i < decaySamples; ++i) {
        envelopeGenerator.process();
    }
    EXPECT_NEAR(envelopeGenerator.getCurrentValue(), settings.sustainLevel, kTestEpsilon);
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::SUSTAIN);

    // Sustain phase - process a few samples
    for (int i = 0; i < 100; ++i) {
        envelopeGenerator.process();
    }
    EXPECT_NEAR(envelopeGenerator.getCurrentValue(), settings.sustainLevel, kTestEpsilon);
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::SUSTAIN);

    envelopeGenerator.triggerOff();
    // Depending on implementation, it might go to RELEASE immediately or after next process call
    // If it doesn't go immediately, the first process() in release phase will transition it.
    if (envelopeGenerator.getCurrentStage() != theone::audio::EnvelopeStage::RELEASE) {
        envelopeGenerator.process(); // process one sample to ensure stage transition
    }
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::RELEASE);

    // Release phase
    int releaseSamples = static_cast<int>(std::ceil(settings.releaseMs / 1000.0f * sampleRate));
    for (int i = 0; i < releaseSamples; ++i) {
        envelopeGenerator.process();
    }
    // After full release, value should be close to 0
    EXPECT_NEAR(envelopeGenerator.getCurrentValue(), 0.0f, kTestEpsilon);
    // And stage should transition to IDLE
     if (envelopeGenerator.getCurrentStage() != theone::audio::EnvelopeStage::IDLE) {
        envelopeGenerator.process(); // Ensure transition if it needs one more process call
    }
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::IDLE);
    EXPECT_FALSE(envelopeGenerator.isActive());
}

TEST_F(EnvelopeGeneratorTest, ADSR_ZeroAttackTime) {
    settings.attackMs = 0.0f;
    settings.decayMs = 10.0f;
    settings.sustainLevel = 0.5f;
    settings.releaseMs = 10.0f;
    envelopeGenerator.configure(sampleRate, settings);

    envelopeGenerator.triggerOn();
    // With zero attack time, the first process call should ideally move it to peak and then decay
    envelopeGenerator.process();

    // Value should be 1.0f (or very close, as decay might start applying)
    // If the envelope instantly jumps to 1.0 and then decay starts, the value after one sample
    // will be slightly less than 1.0.
    // If it processes attack (0ms) then decay in one go, it should be 1.0, then decay applies.
    // Let's check if it's in DECAY stage first.
    EXPECT_EQ(envelopeGenerator.getCurrentStage(), theone::audio::EnvelopeStage::DECAY);

    // Given it's in DECAY, it must have reached 1.0 at the boundary of ATTACK and DECAY.
    // If process() calculates the value *before* stage transition, it might still be 1.0.
    // If process() calculates value *after* stage transition (i.e. applies first sample of decay),
    // then it will be slightly less than 1.0.
    // A common behavior for 0ms attack is to immediately set value to 1.0.
    // Let's assume it hits 1.0, then the *next* process starts decay.
    // However, the current test structure calls process() once.
    // If attack is truly instant, currentValue should be 1.0f.
    // Then, the *same* process() call would evaluate the decay stage.
    // So, it might be slightly less than 1.0f if decay is applied in the same sample.

    // To be robust: if attack is 0, after triggerOn() and one process(),
    // we should be in DECAY, and the value should be very close to 1.0 or settings.sustainLevel
    // if decay is also 0. But decay is 10ms. So it should be near 1.0.
    EXPECT_NEAR(envelopeGenerator.getCurrentValue(), 1.0f, kTestEpsilon);
}

} // namespace testing
} // namespace audio
} // namespace theone
