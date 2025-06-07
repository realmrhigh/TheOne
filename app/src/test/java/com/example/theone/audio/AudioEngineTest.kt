package com.example.theone.audio

import com.example.theone.model.AudioInputSource
import com.example.theone.model.SampleMetadata
import com.example.theone.features.drumtrack.model.PadSettings // Required for playPadSample
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AudioEngineTest {

    private lateinit var audioEngine: AudioEngine

    @Before
    fun setUp() {
        audioEngine = AudioEngine()
        // Note: AudioEngine uses JNI and expects System.loadLibrary("theone") to have worked.
        // This won't work in a standard unit test environment without native libraries present.
        // Tests here will focus on the non-JNI wrapper logic that was added for simulation.
    }

    @Test
    fun `startAudioRecording simulated returns SampleMetadata with expected URI and duration`() {
        val tempDir = System.getProperty("java.io.tmpdir")
        val tempFile = File(tempDir, "test_sample_rec.wav")
        val tempFilePath = tempFile.absolutePath

        try {
            val inputSource = AudioInputSource.MICROPHONE
            // The simulated startAudioRecording sleeps for 2000ms and creates the file.
            val returnedMetadata = audioEngine.startAudioRecording(inputSource, tempFilePath)

            assertNotNull(returnedMetadata)
            assertEquals(tempFile.toURI().toString(), returnedMetadata.uri)
            assertEquals(2000L, returnedMetadata.duration) // Simulated duration
            assertEquals(tempFile.nameWithoutExtension, returnedMetadata.name)
            assertEquals(0L, returnedMetadata.trimStartMs)
            assertEquals(2000L, returnedMetadata.trimEndMs) // Should default to duration

            // Verify file was created by simulation
            assertTrue("Simulated recording file should exist", tempFile.exists())

        } finally {
            // Clean up the dummy file
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @Test
    fun `playSampleSlice simulated runs without error`() {
        // This test mainly checks that the simulated method can be called without crashing.
        // Actual playback is not verifiable in unit tests.
        val testUri = "file:///fake/sample.wav"
        val startMs = 100L
        val endMs = 500L

        var exceptionThrown: Exception? = null
        try {
            audioEngine.playSampleSlice(testUri, startMs, endMs)
        } catch (e: Exception) {
            exceptionThrown = e
        }
        assertNull("playSampleSlice (simulated) should not throw an exception", exceptionThrown)
    }

    @Test
    fun `playSampleSlice with startMs greater than endMs does not throw error and simulates no time`() {
        val testUri = "file:///fake/sample.wav"
        val startMs = 500L
        val endMs = 100L // start > end

        var exceptionThrown: Exception? = null
        try {
            audioEngine.playSampleSlice(testUri, startMs, endMs)
        } catch (e: Exception) {
            exceptionThrown = e
        }
        // The simulation Thread.sleep(endMs - startMs) would get a negative number if not handled.
        // The implementation has `if (startMs < endMs)` so it should not sleep or error.
        assertNull("playSampleSlice (simulated) with start > end should not throw an exception", exceptionThrown)
    }

    @Test
    fun `stopCurrentRecording simulated runs without error`(){
        var exceptionThrown: Exception? = null
        try {
            audioEngine.stopCurrentRecording()
        } catch (e: Exception) {
            exceptionThrown = e
        }
        assertNull("stopCurrentRecording (simulated) should not throw an exception", exceptionThrown)
    }

    @Test
    fun `playPadSample simulated runs without error`() {
        val padSettings = PadSettings(sampleId = "uri/dummy.wav", volume = 0.8f)
        var exceptionThrown: Exception? = null
        try {
            audioEngine.playPadSample(padSettings)
        } catch (e: Exception) {
            exceptionThrown = e
        }
        assertNull("playPadSample (simulated) should not throw an exception", exceptionThrown)
    }
}
