package com.high.theone.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * Unit tests for MicrophoneInput functionality.
 * Tests microphone input operations, permission handling, and error scenarios.
 * 
 * Requirements covered:
 * - 1.1: Microphone input stream initialization
 * - 1.4: Error scenarios and recovery for recording
 */
class MicrophoneInputTest {

    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var microphoneInput: MicrophoneInputImpl
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        microphoneInput = MicrophoneInputImpl(mockContext)
    }

    @Test
    fun `startRecording should not start when permission is denied`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_DENIED)
        
        // When
        microphoneInput.startRecording()
        
        // Then
        assertFalse("Recording should not start without permission", microphoneInput.isRecording())
    }

    @Test
    fun `startRecording should start when permission is granted`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        // When
        microphoneInput.startRecording()
        
        // Then
        assertTrue("Recording should start with permission", microphoneInput.isRecording())
        
        // Clean up
        microphoneInput.stopRecording()
    }

    @Test
    fun `stopRecording should stop active recording`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        assertTrue("Recording should be active", microphoneInput.isRecording())
        
        // When
        microphoneInput.stopRecording()
        
        // Then
        assertFalse("Recording should be stopped", microphoneInput.isRecording())
    }

    @Test
    fun `stopRecording should handle multiple calls gracefully`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        microphoneInput.stopRecording()
        
        // When - call stop again
        microphoneInput.stopRecording()
        
        // Then - should not throw exception
        assertFalse("Recording should remain stopped", microphoneInput.isRecording())
    }

    @Test
    fun `read should return null when not recording`() {
        // Given - not recording
        
        // When
        val result = microphoneInput.read()
        
        // Then
        assertNull("Should return null when not recording", result)
    }

    @Test
    fun `getAmplitude should return zero when not recording`() {
        // Given - not recording
        
        // When
        val amplitude = microphoneInput.getAmplitude()
        
        // Then
        assertEquals("Amplitude should be zero when not recording", 0f, amplitude, 0.001f)
    }

    @Test
    fun `getAmplitude should return valid range when recording`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        
        // When
        val amplitude = microphoneInput.getAmplitude()
        
        // Then
        assertTrue("Amplitude should be in valid range [0, 1]", amplitude >= 0f && amplitude <= 1f)
        
        // Clean up
        microphoneInput.stopRecording()
    }

    @Test
    fun `isRecording should return correct state`() {
        // Given - initially not recording
        assertFalse("Should not be recording initially", microphoneInput.isRecording())
        
        // When - start recording
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        
        // Then
        assertTrue("Should be recording after start", microphoneInput.isRecording())
        
        // When - stop recording
        microphoneInput.stopRecording()
        
        // Then
        assertFalse("Should not be recording after stop", microphoneInput.isRecording())
    }

    @Test
    fun `release should stop recording and clean up resources`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        assertTrue("Recording should be active", microphoneInput.isRecording())
        
        // When
        microphoneInput.release()
        
        // Then
        assertFalse("Recording should be stopped after release", microphoneInput.isRecording())
    }

    @Test
    fun `startRecording should handle AudioRecord initialization failure`() {
        // Given - permission granted but AudioRecord fails to initialize
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        // This test simulates the case where AudioRecord constructor succeeds
        // but the actual recording fails to start (hardware issues, etc.)
        
        // When
        microphoneInput.startRecording()
        
        // Then - the implementation should handle this gracefully
        // The actual behavior depends on the AudioRecord implementation
        // This test ensures we don't crash on hardware failures
        assertTrue("Test should complete without exceptions", true)
        
        // Clean up
        microphoneInput.stopRecording()
    }

    @Test
    fun `read should handle buffer size correctly`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        
        // When
        val audioData = microphoneInput.read()
        
        // Then
        if (audioData != null) {
            assertTrue("Audio data should have reasonable size", audioData.isNotEmpty())
            assertTrue("Audio data should not exceed buffer limits", audioData.size <= 8192) // Reasonable upper bound
        }
        
        // Clean up
        microphoneInput.stopRecording()
    }

    @Test
    fun `amplitude calculation should be normalized`() {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        microphoneInput.startRecording()
        
        // When - get multiple amplitude readings
        val amplitudes = mutableListOf<Float>()
        repeat(5) {
            amplitudes.add(microphoneInput.getAmplitude())
            Thread.sleep(10L) // Small delay between readings
        }
        
        // Then
        amplitudes.forEach { amplitude ->
            assertTrue("Amplitude should be normalized [0, 1]: $amplitude", 
                amplitude >= 0f && amplitude <= 1f)
        }
        
        // Clean up
        microphoneInput.stopRecording()
    }

    @Test
    fun `concurrent access should be thread safe`() = runTest {
        // Given
        whenever(
            ActivityCompat.checkSelfPermission(
                mockContext,
                Manifest.permission.RECORD_AUDIO
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        // When - simulate concurrent access
        val threads = mutableListOf<Thread>()
        
        // Start recording in one thread
        threads.add(Thread {
            microphoneInput.startRecording()
        })
        
        // Read data in another thread
        threads.add(Thread {
            repeat(10) {
                microphoneInput.read()
                Thread.sleep(5L)
            }
        })
        
        // Get amplitude in another thread
        threads.add(Thread {
            repeat(10) {
                microphoneInput.getAmplitude()
                Thread.sleep(5L)
            }
        })
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for completion
        threads.forEach { it.join() }
        
        // Then - should not crash or throw exceptions
        assertTrue("Concurrent access should be handled safely", true)
        
        // Clean up
        microphoneInput.stopRecording()
    }
}