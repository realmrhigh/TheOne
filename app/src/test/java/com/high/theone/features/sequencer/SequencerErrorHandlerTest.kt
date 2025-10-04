package com.high.theone.features.sequencer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.PatternRepository
import com.high.theone.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for SequencerErrorHandler
 * Tests error handling, recovery mechanisms, and logging functionality
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SequencerErrorHandlerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var audioEngine: AudioEngineControl
    private lateinit var patternRepository: PatternRepository
    private lateinit var errorHandler: SequencerErrorHandler

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock dependencies
        audioEngine = mockk(relaxed = true) {
            every { initialize(any(), any(), any()) } returns true
            every { initializeDrumEngine() } returns true
        }
        
        patternRepository = mockk(relaxed = true) {
            coEvery { loadPattern(any()) } returns com.high.theone.domain.Result.Success(
                Pattern(name = "Test Pattern")
            )
        }

        // Create error handler instance
        errorHandler = SequencerErrorHandler(audioEngine, patternRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should initialize with default error state`() = runTest {
        // Given - fresh error handler instance
        
        // When - checking initial state
        val errorState = errorHandler.errorState.value
        
        // Then - should have default values
        assertFalse(errorState.isRecovering)
        assertTrue(errorState.lastRecoverySuccess)
        assertEquals(null, errorState.criticalError)
        assertEquals(null, errorState.userMessage)
    }

    @Test
    fun `should handle audio engine failure with recovery`() = runTest {
        // Given - audio engine that can be recovered
        every { audioEngine.initialize(any(), any(), any()) } returns true
        every { audioEngine.initializeDrumEngine() } returns true
        
        // When - handling audio engine failure
        val result = errorHandler.handleAudioEngineFailure(
            "test_operation",
            RuntimeException("Test exception"),
            mapOf("context" to "test")
        )
        
        // Then - should attempt recovery
        assertEquals(RecoveryResult.SUCCESS, result)
        verify { audioEngine.initialize(any(), any(), any()) }
        verify { audioEngine.initializeDrumEngine() }
    }

    @Test
    fun `should handle audio engine failure with retry needed`() = runTest {
        // Given - audio engine that fails to initialize
        every { audioEngine.initialize(any(), any(), any()) } returns false
        
        // When - handling audio engine failure
        val result = errorHandler.handleAudioEngineFailure(
            "test_operation",
            RuntimeException("Test exception")
        )
        
        // Then - should indicate retry needed
        assertEquals(RecoveryResult.RETRY_NEEDED, result)
        
        val errorState = errorHandler.errorState.value
        assertTrue(errorState.isRecovering)
        assertFalse(errorState.lastRecoverySuccess)
    }

    @Test
    fun `should handle pattern loading error with recovery`() = runTest {
        // Given - pattern repository that can load pattern
        val testPattern = Pattern(id = "test_pattern", name = "Test Pattern")
        coEvery { patternRepository.loadPattern("test_pattern", "test_project") } returns 
            com.high.theone.domain.Result.Success(testPattern)
        
        // When - handling pattern loading error
        val result = errorHandler.handlePatternLoadingError(
            "test_pattern",
            "test_project",
            RuntimeException("Loading failed")
        )
        
        // Then - should recover successfully
        assertEquals(RecoveryResult.SUCCESS, result)
        coVerify { patternRepository.loadPattern("test_pattern", "test_project") }
    }

    @Test
    fun `should handle pattern loading error with failure`() = runTest {
        // Given - pattern repository that fails to load pattern
        coEvery { patternRepository.loadPattern("test_pattern", "test_project") } returns 
            com.high.theone.domain.Result.Failure(RuntimeException("Pattern not found"))
        
        // When - handling pattern loading error multiple times
        repeat(4) { // Exceed MAX_RETRY_ATTEMPTS
            errorHandler.handlePatternLoadingError(
                "test_pattern",
                "test_project",
                RuntimeException("Loading failed")
            )
            advanceUntilIdle()
        }
        
        // Then - should eventually fail
        val errorState = errorHandler.errorState.value
        assertFalse(errorState.lastRecoverySuccess)
        assertTrue(errorState.userMessage?.contains("Failed to load pattern") == true)
    }

    @Test
    fun `should handle timing errors with compensation`() = runTest {
        // Given - timing parameters with significant drift
        val expectedTime = 1000000L // 1 second in microseconds
        val actualTime = 1100000L   // 1.1 seconds (100ms drift)
        
        // When - handling timing error
        val result = errorHandler.handleTimingError(
            expectedTime,
            actualTime,
            "test_operation"
        )
        
        // Then - should apply compensation
        assertEquals(RecoveryResult.SUCCESS, result)
    }

    @Test
    fun `should handle small timing errors without intervention`() = runTest {
        // Given - timing parameters with small drift
        val expectedTime = 1000000L // 1 second in microseconds
        val actualTime = 1010000L   // 1.01 seconds (10ms drift)
        
        // When - handling timing error
        val result = errorHandler.handleTimingError(
            expectedTime,
            actualTime,
            "test_operation"
        )
        
        // Then - should succeed without intervention
        assertEquals(RecoveryResult.SUCCESS, result)
    }

    @Test
    fun `should handle sample loading errors`() = runTest {
        // Given - sample loading failure
        val sampleId = "test_sample"
        val exception = RuntimeException("Sample not found")
        
        // When - handling sample loading error
        val result = errorHandler.handleSampleLoadingError(
            sampleId,
            exception,
            mapOf("padIndex" to 0)
        )
        
        // Then - should attempt recovery
        assertEquals(RecoveryResult.SUCCESS, result)
    }

    @Test
    fun `should handle voice allocation errors gracefully`() = runTest {
        // Given - voice allocation failure
        val padIndex = 0
        val sampleId = "test_sample"
        val exception = RuntimeException("No voices available")
        
        // When - handling voice allocation error
        val result = errorHandler.handleVoiceAllocationError(
            padIndex,
            sampleId,
            exception
        )
        
        // Then - should handle gracefully (voice errors are usually recoverable)
        assertEquals(RecoveryResult.SUCCESS, result)
    }

    @Test
    fun `should handle general errors with appropriate severity`() = runTest {
        // Given - general error with high severity
        val operation = "test_operation"
        val exception = RuntimeException("Critical failure")
        
        // When - handling general error with high severity
        val result = errorHandler.handleGeneralError(
            operation,
            exception,
            ErrorSeverity.HIGH
        )
        
        // Then - should indicate failure for high severity
        assertEquals(RecoveryResult.FAILED, result)
        
        val errorState = errorHandler.errorState.value
        assertTrue(errorState.criticalError?.contains("Critical error") == true)
    }

    @Test
    fun `should handle general errors with low severity`() = runTest {
        // Given - general error with low severity
        val operation = "test_operation"
        val exception = RuntimeException("Minor issue")
        
        // When - handling general error with low severity
        val result = errorHandler.handleGeneralError(
            operation,
            exception,
            ErrorSeverity.LOW
        )
        
        // Then - should succeed for low severity
        assertEquals(RecoveryResult.SUCCESS, result)
    }

    @Test
    fun `should provide error statistics`() = runTest {
        // Given - some errors have occurred
        errorHandler.handleGeneralError("op1", RuntimeException("Error 1"), ErrorSeverity.LOW)
        errorHandler.handleGeneralError("op2", RuntimeException("Error 2"), ErrorSeverity.MEDIUM)
        advanceUntilIdle()
        
        // When - getting error statistics
        val stats = errorHandler.getErrorStatistics()
        
        // Then - should provide accurate statistics
        assertEquals(2, stats.totalErrors)
        assertTrue(stats.errorsByType.containsKey(ErrorType.GENERAL_ERROR))
        assertEquals(2, stats.recentErrors.size)
    }

    @Test
    fun `should clear error history`() = runTest {
        // Given - some errors have occurred
        errorHandler.handleGeneralError("op1", RuntimeException("Error 1"))
        advanceUntilIdle()
        
        // When - clearing error history
        errorHandler.clearErrorHistory()
        
        // Then - error history should be cleared
        val stats = errorHandler.getErrorStatistics()
        assertEquals(0, stats.totalErrors)
        
        val errorState = errorHandler.errorState.value
        assertFalse(errorState.isRecovering)
        assertTrue(errorState.lastRecoverySuccess)
        assertEquals(null, errorState.criticalError)
        assertEquals(null, errorState.userMessage)
    }

    @Test
    fun `should clear user messages`() = runTest {
        // Given - error state with user message
        errorHandler.handleGeneralError("op1", RuntimeException("Error"), ErrorSeverity.MEDIUM)
        advanceUntilIdle()
        
        // When - clearing user message
        errorHandler.clearUserMessage()
        
        // Then - user message should be cleared
        val errorState = errorHandler.errorState.value
        assertEquals(null, errorState.userMessage)
    }

    @Test
    fun `should clear critical errors`() = runTest {
        // Given - error state with critical error
        errorHandler.handleGeneralError("op1", RuntimeException("Critical"), ErrorSeverity.HIGH)
        advanceUntilIdle()
        
        // When - clearing critical error
        errorHandler.clearCriticalError()
        
        // Then - critical error should be cleared
        val errorState = errorHandler.errorState.value
        assertEquals(null, errorState.criticalError)
    }

    @Test
    fun `should handle concurrent error operations safely`() = runTest {
        // Given - multiple concurrent error operations
        val operations = (1..10).map { "operation_$it" }
        
        // When - handling concurrent errors
        val jobs = operations.map { operation ->
            launch {
                errorHandler.handleGeneralError(operation, RuntimeException("Error"), ErrorSeverity.LOW)
            }
        }
        
        jobs.forEach { it.join() }
        
        // Then - should handle all errors without issues
        val stats = errorHandler.getErrorStatistics()
        assertEquals(10, stats.totalErrors)
    }

    @Test
    fun `should limit retry attempts for failed operations`() = runTest {
        // Given - audio engine that always fails
        every { audioEngine.initialize(any(), any(), any()) } returns false
        
        // When - repeatedly handling the same failure
        repeat(5) {
            errorHandler.handleAudioEngineFailure("persistent_failure", RuntimeException("Always fails"))
            advanceUntilIdle()
        }
        
        // Then - should eventually give up and mark as failed
        val errorState = errorHandler.errorState.value
        assertFalse(errorState.lastRecoverySuccess)
    }
}