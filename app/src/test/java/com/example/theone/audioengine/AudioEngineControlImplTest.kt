package com.example.theone.audioengine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Using Robolectric to allow loading of native libraries in JVM tests.
// This is a common approach for testing JNI dependent code.
// Add `testImplementation "org.robolectric:robolectric:4.10.3"` to app/build.gradle.kts
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK]) // Basic Robolectric config
class AudioEngineControlImplTest {

    private lateinit var audioEngineControl: AudioEngineControl

    @Before
    fun setUp() {
        // Robolectric should handle System.loadLibrary.
        // If it doesn't, this test setup might need adjustment or to become an instrumented test.
        // For now, assuming Robolectric handles the native library loading for JVM tests.
        // If `System.loadLibrary` fails here, these tests would need to be Android Instrumented Tests.
        // Let's try with Robolectric first.
        try {
            audioEngineControl = AudioEngineControlImpl()
        } catch (e: UnsatisfiedLinkError) {
            // This catch block is for local debugging if Robolectric setup is tricky.
            // In a CI environment, this would just fail the test.
            System.err.println("Failed to load native library for testing: " + e.message)
            System.err.println("Ensure Robolectric is configured correctly or consider running as an Android Instrumented Test.")
            throw e // Re-throw to fail the test clearly
        }
    }

    @Test
    fun `getEngineVersion should return a non-empty string`() {
        val version = audioEngineControl.getEngineVersion()
        assertNotNull("Engine version should not be null", version)
        assertTrue("Engine version should not be empty", version.isNotEmpty())
        // We expect "0.0.1-alpha" but JNI calls might be tricky in pure JVM tests.
        // For now, checking for non-empty is a good start.
        assertEquals("0.0.1-alpha", version) // Let's try to assert the exact version.
        System.out.println("Reported Engine Version: $version")
    }

    @Test
    fun `initialize should not crash and return a boolean`() {
        // Parameters are typical defaults.
        val sampleRate = 48000
        val bufferSize = 256 // This is framesPerBurst
        val enableLowLatency = true

        // The main thing is that this call doesn't throw an UnsatisfiedLinkError or other JNI-related crash.
        // The actual success of initialization inside C++ is logged there.
        // Here we just check the JNI bridge.
        var initResult = false
        try {
            initResult = audioEngineControl.initialize(sampleRate, bufferSize, enableLowLatency)
            System.out.println("Initialization result: $initResult")
            // In a real test with a fully functional engine, you might want to assert true.
            // For now, just ensuring it completes and returns is key.
            // The C++ side is programmed to return true on successful stream opening.
            assertTrue("Initialization should return true if JNI call succeeds and Oboe setup is okay.", initResult)
        } catch (e: Exception) {
            fail("Call to initialize failed: ${e.message}")
        }
    }

    @Test
    fun `setMetronomeState should not crash`() {
        val isEnabled = true
        val bpm = 120.0f
        val timeSignatureNum = 4
        val timeSignatureDen = 4
        val primarySoundUri = "test://primary_sound"
        val secondarySoundUri = "test://secondary_sound"

        // Similar to initialize, the key is that the JNI call doesn't crash.
        try {
            audioEngineControl.setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, primarySoundUri, secondarySoundUri)
            // No return value to assert, just checking for no exceptions.
            System.out.println("setMetronomeState called successfully.")
        } catch (e: Exception) {
            fail("Call to setMetronomeState failed: ${e.message}")
        }
    }

    @Test
    fun `repeated initialize and setMetronomeState calls`() {
        // Call initialize
        val initSuccess = audioEngineControl.initialize(44100, 512, false)
        assertTrue("First initialization failed", initSuccess)

        // Call setMetronomeState
        audioEngineControl.setMetronomeState(true, 90.0f, 3, 4, "uri1", "uri2")
        System.out.println("First setMetronomeState call done.")

        // The C++ AudioEngine currently doesn't allow re-initialization without stopping.
        // This call should ideally return false or be handled gracefully by the C++ side.
        // Our C++ `initialize` has a check: `if (mIsStreamOpen) { LOGE("Stream already open."); return false; }`
        val reInitSuccess = audioEngineControl.initialize(48000, 256, true)
        assertFalse("Re-initialization without stopping should ideally fail or return false.", reInitSuccess)
        System.out.println("Second initialize call (expected to fail or be handled): $reInitSuccess")

         // Call setMetronomeState again
        audioEngineControl.setMetronomeState(false, 100.0f, 4, 4, "uri3", null)
        System.out.println("Second setMetronomeState call done.")
    }
}
