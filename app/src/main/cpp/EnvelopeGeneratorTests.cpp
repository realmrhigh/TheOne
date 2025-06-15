#include "gtest/gtest.h"
#include "EnvelopeGenerator.h" // Assuming this path is correct relative to where tests are run
                               // CMakeLists.txt should handle include directories

TEST(EnvelopeGeneratorTest, AttackPhaseIncreasesValue) {
    theone::audio::EnvelopeGenerator envGen;
    theone::audio::EnvelopeSettingsCpp settings;
    settings.type = theone::audio::ModelEnvelopeTypeInternalCpp::ADSR;
    settings.attackMs = 10.0f;
    settings.decayMs = 20.0f;
    settings.sustainLevel = 0.5f;
    settings.releaseMs = 30.0f;
    // Ensure holdMs is 0 for a standard ADSR, or set appropriately if testing AHDSR
    settings.holdMs = 0.0f;


    float sampleRate = 44100.0f;
    envGen.configure(settings, sampleRate);
    envGen.triggerOn();

    float previousValue = envGen.process();
    // First sample after triggerOn might be 0 or very small depending on implementation
    // So, we start checking for increase from the second sample in attack phase.

    // Process a few more samples
    for (int i = 0; i < 5; ++i) {
        float currentValue = envGen.process();
        // Check if current stage is Attack, as we only expect increase in Attack
        if (envGen.getCurrentStage() == theone::audio::EnvelopeStage::ATTACK) {
            EXPECT_GT(currentValue, previousValue);
        }
        previousValue = currentValue;
        // Stop if envelope is no longer active or past attack and we don't want to test further stages here
        if (!envGen.isActive() || currentValue >= 1.0f) break;
    }
}

// It's good practice to also test that the basic test still passes or remove it.
// For now, let's keep it to ensure the test runner is working.
TEST(EnvelopeGeneratorTest, BasicTestStillWorks) {
    EXPECT_EQ(1, 1);
}
