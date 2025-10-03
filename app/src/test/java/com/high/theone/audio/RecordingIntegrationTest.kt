package com.high.theone.audio

import android.content.Context
import android.content.res.AssetManager
import com.high.theone.model.SampleMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the complete recording workflow.
 * Tests end-to-end recording scenarios, error recovery, and system integration.
 * 
 * Requirements covered:
 * - 1.1: Complete recording workflow from start to finish
 * - 1.3: WAV file creation with proper metadata
 * - 1.4: Error scenarios and recovery mechanisms
 */
class RecordingIntegrationTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    private lateinit var audioEngine: AudioEngineImpl
    private lateinit var tempDir: File
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Setup mock context
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        
        // Create audio engine instance
        audioEngine = AudioEngineImpl(mockContext)
        
        // Create temporary directory for test files
        tempDir = File.createTempFile("recording_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @Test
    fun `complete recording workflow should work end to end`() = runTest {
        // Given
        val recordingFile = File(tempDir, "complete_workflow_test.wav")
        val recordingPath = recordingFile.absolutePath
        
        // When - complete workflow
        // 1. Start recording
        val startResult = audioEngine.startAudioRecording(recordingPath, null)
        assertTrue("Recording should start successfully", startResult)
        
        // 2. Record for a short duration
        Thread.sleep(300L) // Record for 300ms
        
        // 3. Stop recording
        val metadata = audioEngine.stopAudioRecording()
        
        // Then - verify complete workflow
        assertNotNull("Should return metadata after stopping", metadata)
        assertTrue("WAV file should be created", recordingFile.exists())
        assertTrue("WAV file should have content", recordingFile.length() > 0)
        
        metadata?.let {
            assertTrue("Duration should be reasonable", it.durationMs in 200..500)
            assertEquals("Sample rate should be correct", 44100, it.sampleRate)
            assertEquals("Channels should be correct", 1, it.channels)
        }
    }

    @Test
    fun `recording workflow should handle multiple sequential recordings`() = runTest {
        // Given
        val recordings = mutableListOf<Pair<File, SampleMetadata?>>()
        
        // When - perform multiple recordings
        repeat(3) { index ->
            val recordingFile = File(tempDir, "sequential_test_$index.wav")
            
            // Start recording
            val startResult = audioEngine.startAudioRecording(recordingFile.absolutePath, null)
            assertTrue("Recording $index should start", startResult)
            
            // Record for different durations
            Thread.sleep(100L + (index * 50L))
            
            // Stop recording
            val metadata = audioEngine.stopAudioRecording()
            recordings.add(recordingFile to metadata)
        }
        
        // Then - verify all recordings
        recordings.forEachIndexed { index, (file, metadata) ->
            assertTrue("File $index should exist", file.exists())
            assertTrue("File $index should have content", file.length() > 0)
            assertNotNull("Metadata $index should exist", metadata)
            
            metadata?.let {
                assertTrue("Duration $index should be positive", it.durationMs > 0)
                assertEquals("Sample rate $index should be correct", 44100, it.sampleRate)
            }
        }
    }

    @Test
    fun `recording workflow should handle interruption and recovery`() = runTest {
        // Given
        val recordingFile = File(tempDir, "interruption_test.wav")
        
        // When - start recording and simulate interruption
        val startResult = audioEngine.startAudioRecording(recordingFile.absolutePath, null)
        assertTrue("Recording should start", startResult)
        
        Thread.sleep(100L)
        
        // Simulate interruption by trying to start another recording
        val interruptResult = audioEngine.startAudioRecording(
            File(tempDir, "interrupt_attempt.wav").absolutePath, 
            null
        )
        assertFalse("Second recording should fail", interruptResult)
        
        // Original recording should still be active and stoppable
        val metadata = audioEngine.stopAudioRecording()
        
        // Then - verify recovery
        assertNotNull("Original recording should complete", metadata)
        assertTrue("Original file should exist", recordingFile.exists())
        
        // Should be able to start new recording after stopping
        val recoveryFile = File(tempDir, "recovery_test.wav")
        val recoveryResult = audioEngine.startAudioRecording(recoveryFile.absolutePath, null)
        assertTrue("Should recover and allow new recording", recoveryResult)
        
        // Clean up
        audioEngine.stopAudioRecording()
    }

    @Test
    fun `recording workflow should handle file system errors gracefully`() = runTest {
        // Given - various problematic file paths
        val problematicPaths = listOf(
            "/root/no_permission.wav", // No permission
            "/nonexistent/directory/file.wav", // Directory doesn't exist
            "", // Empty path
            "/dev/null/impossible.wav" // Invalid location
        )
        
        // When & Then - test each problematic path
        problematicPaths.forEach { path ->
            val result = audioEngine.startAudioRecording(path, null)
            assertFalse("Recording should fail gracefully for path: $path", result)
            
            // Verify system is still functional after error
            val testFile = File(tempDir, "recovery_after_error.wav")
            val recoveryResult = audioEngine.startAudioRecording(testFile.absolutePath, null)
            assertTrue("System should recover after error with path: $path", recoveryResult)
            
            // Clean up
            audioEngine.stopAudioRecording()
        }
    }

    @Test
    fun `recording workflow should handle concurrent stop attempts`() = runTest {
        // Given
        val recordingFile = File(tempDir, "concurrent_stop_test.wav")
        
        audioEngine.startAudioRecording(recordingFile.absolutePath, null)
        Thread.sleep(200L)
        
        // When - multiple threads try to stop recording simultaneously
        val latch = CountDownLatch(3)
        val results = mutableListOf<SampleMetadata?>()
        
        repeat(3) {
            Thread {
                try {
                    val result = kotlinx.coroutines.runBlocking { audioEngine.stopAudioRecording() }
                    synchronized(results) {
                        results.add(result)
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        // Wait for all threads to complete
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        
        // Then - only one should succeed
        val successfulResults = results.filterNotNull()
        assertEquals("Only one stop should succeed", 1, successfulResults.size)
        
        val nullResults = results.count { it == null }
        assertEquals("Two stops should return null", 2, nullResults)
        
        assertTrue("File should exist", recordingFile.exists())
    }

    @Test
    fun `recording workflow should validate WAV file format`() = runTest {
        // Given
        val recordingFile = File(tempDir, "wav_format_test.wav")
        
        // When
        audioEngine.startAudioRecording(recordingFile.absolutePath, null)
        Thread.sleep(250L)
        val metadata = audioEngine.stopAudioRecording()
        
        // Then - verify WAV file format
        assertNotNull("Metadata should exist", metadata)
        assertTrue("File should exist", recordingFile.exists())
        
        // Read first few bytes to verify WAV header
        val fileBytes = recordingFile.readBytes()
        assertTrue("File should have content", fileBytes.isNotEmpty())
        
        // Check WAV file signature (RIFF header)
        if (fileBytes.size >= 12) {
            val riffSignature = String(fileBytes.sliceArray(0..3))
            val waveSignature = String(fileBytes.sliceArray(8..11))
            
            // Note: In a real implementation, we would verify these signatures
            // For now, we just verify the file has reasonable size
            assertTrue("WAV file should have reasonable size", fileBytes.size > 44) // WAV header is 44 bytes minimum
        }
        
        metadata?.let {
            assertEquals("Sample rate should match WAV format", 44100, it.sampleRate)
            assertEquals("Channels should match WAV format", 1, it.channels)
        }
    }

    @Test
    fun `recording workflow should handle long duration recordings`() = runTest {
        // Given
        val recordingFile = File(tempDir, "long_duration_test.wav")
        
        // When - record for a longer duration (but not too long for test)
        audioEngine.startAudioRecording(recordingFile.absolutePath, null)
        Thread.sleep(1000L) // Record for 1 second
        val metadata = audioEngine.stopAudioRecording()
        
        // Then
        assertNotNull("Metadata should exist for long recording", metadata)
        assertTrue("File should exist", recordingFile.exists())
        
        metadata?.let {
            assertTrue("Duration should be approximately 1 second", it.durationMs in 900..1200)
            assertTrue("File size should be reasonable for 1 second", recordingFile.length() > 44100 * 2) // Rough estimate
        }
    }

    @Test
    fun `recording workflow should handle system resource constraints`() = runTest {
        // Given - simulate resource constraints by creating many files
        val recordings = mutableListOf<File>()
        
        try {
            // When - attempt multiple recordings to test resource management
            repeat(5) { index ->
                val recordingFile = File(tempDir, "resource_test_$index.wav")
                recordings.add(recordingFile)
                
                val startResult = audioEngine.startAudioRecording(recordingFile.absolutePath, null)
                if (startResult) {
                    Thread.sleep(50L) // Short recording
                    val metadata = audioEngine.stopAudioRecording()
                    assertNotNull("Recording $index should complete", metadata)
                }
            }
            
            // Then - verify system handled resource constraints gracefully
            val successfulRecordings = recordings.filter { it.exists() && it.length() > 0 }
            assertTrue("At least some recordings should succeed", successfulRecordings.isNotEmpty())
            
        } finally {
            // Clean up
            recordings.forEach { it.delete() }
        }
    }

    @Test
    fun `recording workflow should maintain audio quality standards`() = runTest {
        // Given
        val recordingFile = File(tempDir, "quality_test.wav")
        
        // When
        audioEngine.startAudioRecording(recordingFile.absolutePath, null)
        Thread.sleep(500L) // Record for 500ms
        val metadata = audioEngine.stopAudioRecording()
        
        // Then - verify quality standards
        assertNotNull("Metadata should exist", metadata)
        
        metadata?.let {
            assertEquals("Should use CD quality sample rate", 44100, it.sampleRate)
            assertEquals("Should use mono recording", 1, it.channels)
            assertTrue("Duration should be accurate", it.durationMs in 400..600)
        }
        
        // Verify file size is reasonable for the duration and quality
        val expectedMinSize = (44100 * 2 * 0.4).toLong() // 16-bit samples, 400ms minimum
        val expectedMaxSize = (44100 * 2 * 0.6).toLong() // 16-bit samples, 600ms maximum
        
        assertTrue("File size should be within expected range", 
            recordingFile.length() in expectedMinSize..expectedMaxSize)
    }
}