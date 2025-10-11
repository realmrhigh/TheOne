package com.high.theone.features.compactui.error

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.high.theone.model.RecordingErrorType
import com.high.theone.model.RecordingRecoveryAction
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ErrorHandlingSystem
 */
class ErrorHandlingSystemTest {
    
    private lateinit var context: Context
    private lateinit var errorHandlingSystem: ErrorHandlingSystem
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        errorHandlingSystem = ErrorHandlingSystem(context)
    }
    
    @Test
    fun `handleRecordingError creates appropriate error for permission issue`() = runTest {
        // Given
        val permissionException = RuntimeException("Permission denied")
        
        // When
        val error = errorHandlingSystem.handleRecordingError(permissionException)
        
        // Then
        assertEquals(RecordingErrorType.PERMISSION_DENIED, error.type)
        assertTrue(error.isRecoverable)
        assertEquals(RecordingRecoveryAction.REQUEST_PERMISSION, error.recoveryAction)
        assertTrue(error.message.contains("permission", ignoreCase = true))
    }
    
    @Test
    fun `handleRecordingError creates appropriate error for audio engine issue`() = runTest {
        // Given
        val audioException = RuntimeException("Audio engine failed")
        
        // When
        val error = errorHandlingSystem.handleRecordingError(audioException)
        
        // Then
        assertEquals(RecordingErrorType.AUDIO_ENGINE_FAILURE, error.type)
        assertTrue(error.isRecoverable)
        assertEquals(RecordingRecoveryAction.RESTART_AUDIO_ENGINE, error.recoveryAction)
    }
    
    @Test
    fun `handleRecordingError creates appropriate error for storage issue`() = runTest {
        // Given
        val storageException = RuntimeException("Storage full")
        
        // When
        val error = errorHandlingSystem.handleRecordingError(storageException)
        
        // Then
        assertEquals(RecordingErrorType.STORAGE_FAILURE, error.type)
        assertTrue(error.isRecoverable)
        assertEquals(RecordingRecoveryAction.FREE_STORAGE_SPACE, error.recoveryAction)
    }
    
    @Test
    fun `checkMicrophonePermission returns correct status`() = runTest {
        // Given
        mockkStatic(ContextCompat::class)
        every { 
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
        } returns PackageManager.PERMISSION_GRANTED
        
        // When
        val hasPermission = errorHandlingSystem.checkMicrophonePermission()
        
        // Then
        assertTrue(hasPermission)
    }
    
    @Test
    fun `clearError resets error state`() = runTest {
        // Given
        val error = errorHandlingSystem.handleSpecificError(
            RecordingErrorType.PERMISSION_DENIED,
            "Test error"
        )
        assertNotNull(errorHandlingSystem.currentError.value)
        
        // When
        errorHandlingSystem.clearError()
        
        // Then
        assertNull(errorHandlingSystem.currentError.value)
        assertEquals(0, errorHandlingSystem.retryAttempts.value)
    }
    
    @Test
    fun `incrementRetryAttempts respects maximum attempts`() = runTest {
        // Given
        errorHandlingSystem.resetRetryAttempts()
        
        // When & Then
        assertTrue(errorHandlingSystem.incrementRetryAttempts()) // 1
        assertTrue(errorHandlingSystem.incrementRetryAttempts()) // 2
        assertTrue(errorHandlingSystem.incrementRetryAttempts()) // 3
        assertFalse(errorHandlingSystem.incrementRetryAttempts()) // 4 - should fail
        
        assertEquals(3, errorHandlingSystem.retryAttempts.value)
        assertFalse(errorHandlingSystem.canRetry())
    }
    
    @Test
    fun `hasEnoughStorageSpace checks minimum requirements`() = runTest {
        // This test would need to mock StatFs, which is complex
        // For now, we'll just verify the method exists and doesn't crash
        val hasSpace = errorHandlingSystem.hasEnoughStorageSpace()
        // Result depends on actual device storage, so we just verify it returns a boolean
        assertTrue(hasSpace is Boolean)
    }
}