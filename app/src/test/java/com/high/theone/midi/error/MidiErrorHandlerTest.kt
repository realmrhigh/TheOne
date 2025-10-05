package com.high.theone.midi.error

import android.content.Context
import com.high.theone.midi.MidiError
import com.high.theone.midi.MidiErrorContext
import com.high.theone.midi.MidiErrorRecoveryStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for MidiErrorHandler
 */
class MidiErrorHandlerTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockNotificationManager: MidiNotificationManager
    
    private lateinit var errorHandler: MidiErrorHandler
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        errorHandler = MidiErrorHandler(mockContext, mockNotificationManager)
    }
    
    @Test
    fun `handleError should record error in history`() = runTest {
        // Given
        val error = MidiError.DeviceNotFound("test-device")
        val context = MidiErrorContext(
            deviceId = "test-device",
            operation = "connect",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        val result = errorHandler.handleError(error, context)
        
        // Then
        assertTrue(errorHandler.errorHistory.value.isNotEmpty())
        assertEquals(1, errorHandler.errorHistory.value.size)
        assertEquals(error, errorHandler.errorHistory.value.first().error)
    }
    
    @Test
    fun `handleError should apply correct recovery strategy for device not found`() = runTest {
        // Given
        val error = MidiError.DeviceNotFound("test-device")
        val context = MidiErrorContext(
            deviceId = "test-device",
            operation = "connect",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        val result = errorHandler.handleError(error, context)
        
        // Then
        assertFalse(result.recovered) // Should not recover without callback
        assertTrue(result.shouldNotifyUser)
        assertEquals(MidiRecoveryAction.CHECK_CONNECTION, result.recoveryAction)
    }
    
    @Test
    fun `handleError should use custom recovery strategy when provided`() = runTest {
        // Given
        val error = MidiError.DeviceNotFound("test-device")
        val context = MidiErrorContext(
            deviceId = "test-device",
            operation = "connect",
            timestamp = System.currentTimeMillis()
        )
        val customStrategy = MidiErrorRecoveryStrategy.IGNORE
        
        // When
        val result = errorHandler.handleError(error, context, customStrategy)
        
        // Then
        assertTrue(result.recovered) // IGNORE strategy should mark as recovered
        assertFalse(result.shouldNotifyUser)
    }
    
    @Test
    fun `registerRecoveryCallback should allow custom error recovery`() = runTest {
        // Given
        val error = MidiError.DeviceNotFound("test-device")
        val context = MidiErrorContext(
            deviceId = "test-device",
            operation = "connect",
            timestamp = System.currentTimeMillis()
        )
        
        var callbackCalled = false
        errorHandler.registerRecoveryCallback("DeviceNotFound") { _, _ ->
            callbackCalled = true
            true // Simulate successful recovery
        }
        
        // When
        val result = errorHandler.handleError(error, context)
        
        // Then
        assertTrue(callbackCalled)
        assertTrue(result.recovered)
        assertFalse(result.shouldNotifyUser)
    }
    
    @Test
    fun `getErrorStatistics should return correct statistics`() = runTest {
        // Given
        val error1 = MidiError.DeviceNotFound("device1")
        val error2 = MidiError.ConnectionFailed("device2", "timeout")
        val error3 = MidiError.DeviceNotFound("device3")
        
        val context = MidiErrorContext(
            deviceId = "test",
            operation = "test",
            timestamp = System.currentTimeMillis()
        )
        
        // When
        errorHandler.handleError(error1, context)
        errorHandler.handleError(error2, context)
        errorHandler.handleError(error3, context)
        
        val statistics = errorHandler.getErrorStatistics()
        
        // Then
        assertEquals(3, statistics.totalErrors)
        assertEquals("DeviceNotFound", statistics.mostCommonError)
        assertEquals(MidiSystemHealth.DEGRADED, statistics.systemHealth)
    }
    
    @Test
    fun `clearErrorHistory should remove all errors`() = runTest {
        // Given
        val error = MidiError.DeviceNotFound("test-device")
        val context = MidiErrorContext(
            deviceId = "test-device",
            operation = "connect",
            timestamp = System.currentTimeMillis()
        )
        
        errorHandler.handleError(error, context)
        assertTrue(errorHandler.errorHistory.value.isNotEmpty())
        
        // When
        errorHandler.clearErrorHistory()
        
        // Then
        assertTrue(errorHandler.errorHistory.value.isEmpty())
    }
}