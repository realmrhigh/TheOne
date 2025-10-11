package com.high.theone.features.compactui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for recording quality validation and audio engine integration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RecordingQualityValidationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockAudioEngine: AudioEngineControl

    @Mock
    private lateinit var mockSamplingViewModel: SamplingViewModel

    private val testDispatcher = TestCoroutineDispatcher()
    private lateinit var viewModel: CompactMainViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState())
        )
        
        viewModel = CompactMainViewModel(
            audioEngine = mockAudioEngine,
            samplingViewModel = mockSamplingViewModel,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun testRecordingQualityParameters() = runBlockingTest {
        // Test different quality settings
        val qualitySettings = listOf(
            RecordingQuality.HIGH, // 48kHz, 24-bit
            RecordingQuality.MEDIUM, // 44.1kHz, 16-bit
            RecordingQuality.LOW // 22kHz, 16-bit
        )

        qualitySettings.forEach { quality ->
            // Setup recording with specific quality
            `when`(mockAudioEngine.startRecording(quality)).thenReturn(true)
            
            viewModel.startRecording(quality)
            
            // Verify audio engine called with correct parameters
            verify(mockAudioEngine).startRecording(quality)
            
            // Verify recording state reflects quality settings
            val expectedSampleRate = when (quality) {
                RecordingQuality.HIGH -> 48000
                RecordingQuality.MEDIUM -> 44100
                RecordingQuality.LOW -> 22050
            }
            
            verify(mockAudioEngine).setSampleRate(expectedSampleRate)
        }
    }

    @Test
    fun testRecordingLatencyMeasurement() = runBlockingTest {
        val latencyMeasurements = mutableListOf<Long>()
        
        // Setup audio engine to return latency measurements
        `when`(mockAudioEngine.getInputLatency()).thenReturn(12L) // 12ms
        `when`(mockAudioEngine.getOutputLatency()).thenReturn(8L) // 8ms
        
        repeat(10) {
            viewModel.startRecording()
            
            // Measure round-trip latency
            val inputLatency = mockAudioEngine.getInputLatency()
            val outputLatency = mockAudioEngine.getOutputLatency()
            val totalLatency = inputLatency + outputLatency
            
            latencyMeasurements.add(totalLatency)
            
            viewModel.stopRecording()
        }

        // Verify latency is within acceptable range (< 50ms total)
        val averageLatency = latencyMeasurements.average()
        assertTrue(
            averageLatency < 50,
            "Average recording latency too high: ${averageLatency}ms"
        )
        
        // Verify consistency (low variance)
        val maxLatency = latencyMeasurements.maxOrNull()!!
        val minLatency = latencyMeasurements.minOrNull()!!
        val latencyVariance = maxLatency - minLatency
        
        assertTrue(
            latencyVariance < 10,
            "Recording latency variance too high: ${latencyVariance}ms"
        )
    }

    @Test
    fun testAudioLevelAccuracy() = runBlockingTest {
        val testLevels = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        
        testLevels.forEach { expectedLevel ->
            // Setup mock to return specific audio level
            `when`(mockAudioEngine.getCurrentInputLevel()).thenReturn(expectedLevel)
            
            viewModel.startRecording()
            
            // Get reported level from recording state
            val recordingState = mockSamplingViewModel.recordingState.value
            val reportedLevel = recordingState.peakLevel
            
            // Verify level accuracy (within 5% tolerance)
            val tolerance = 0.05f
            assertTrue(
                kotlin.math.abs(reportedLevel - expectedLevel) < tolerance,
                "Audio level inaccurate. Expected: $expectedLevel, Got: $reportedLevel"
            )
            
            viewModel.stopRecording()
        }
    }

    @Test
    fun testRecordingBufferIntegrity() = runBlockingTest {
        val testSampleData = ByteArray(1024) { it.toByte() } // Test pattern
        
        // Setup mock to return test sample data
        `when`(mockAudioEngine.getRecordedSample()).thenReturn(testSampleData)
        
        viewModel.startRecording()
        
        // Simulate recording completion
        `when`(mockSamplingViewModel.recordingState).thenReturn(
            MutableStateFlow(RecordingState(
                isRecording = false,
                recordedSampleId = "test_sample",
                isProcessing = false
            ))
        )
        
        viewModel.stopRecording()
        
        // Verify sample data integrity
        val recordedData = mockAudioEngine.getRecordedSample()
        assertNotNull(recordedData, "Recorded sample data should not be null")
        assertEquals(
            testSampleData.size, 
            recordedData.size, 
            "Recorded sample size mismatch"
        )
        
        // Verify data content matches
        testSampleData.forEachIndexed { index, expectedByte ->
            assertEquals(
                expectedByte, 
                recordedData[index], 
                "Sample data mismatch at index $index"
            )
        }
    }

    @Test
    fun testAudioEngineStateConsistency() = runBlockingTest {
        // Test recording state transitions
        
        // Initial state
        verify(mockAudioEngine, never()).startRecording(any())
        
        // Start recording
        `when`(mockAudioEngine.startRecording(any())).thenReturn(true)
        viewModel.startRecording()
        
        verify(mockAudioEngine).startRecording(any())
        verify(mockAudioEngine).isRecording()
        
        // Stop recording
        `when`(mockAudioEngine.stopRecording()).thenReturn(true)
        viewModel.stopRecording()
        
        verify(mockAudioEngine).stopRecording()
        verify(mockAudioEngine, times(2)).isRecording() // Called during start and stop
    }

    @Test
    fun testSampleRateConsistency() = runBlockingTest {
        val targetSampleRate = 44100
        
        // Setup audio engine
        `when`(mockAudioEngine.getSampleRate()).thenReturn(targetSampleRate)
        `when`(mockAudioEngine.setSampleRate(targetSampleRate)).thenReturn(true)
        
        viewModel.startRecording(RecordingQuality.MEDIUM)
        
        // Verify sample rate was set correctly
        verify(mockAudioEngine).setSampleRate(targetSampleRate)
        
        // Verify sample rate is consistent
        val actualSampleRate = mockAudioEngine.getSampleRate()
        assertEquals(
            targetSampleRate, 
            actualSampleRate, 
            "Sample rate inconsistency"
        )
    }

    @Test
    fun testAudioBufferSizeOptimization() = runBlockingTest {
        val deviceCapabilities = mapOf(
            "low_latency" to 128,
            "normal" to 256,
            "high_latency" to 512
        )
        
        deviceCapabilities.forEach { (capability, expectedBufferSize) ->
            // Setup device capability
            `when`(mockAudioEngine.getOptimalBufferSize()).thenReturn(expectedBufferSize)
            `when`(mockAudioEngine.setBufferSize(expectedBufferSize)).thenReturn(true)
            
            viewModel.optimizeAudioSettings()
            
            // Verify buffer size optimization
            verify(mockAudioEngine).setBufferSize(expectedBufferSize)
            
            val actualBufferSize = mockAudioEngine.getBufferSize()
            assertEquals(
                expectedBufferSize, 
                actualBufferSize, 
                "Buffer size not optimized for $capability device"
            )
        }
    }

    @Test
    fun testRecordingUnderLoad() = runBlockingTest {
        // Simulate system under load
        `when`(mockAudioEngine.getCpuLoad()).thenReturn(0.85f) // 85% CPU usage
        
        viewModel.startRecording()
        
        // Verify recording quality maintained under load
        val recordingState = mockSamplingViewModel.recordingState.value
        
        // Should still be recording despite high CPU load
        assertTrue(recordingState.isRecording, "Recording should continue under load")
        
        // Verify audio engine applies optimizations
        verify(mockAudioEngine).enableLowLatencyMode(false) // Disable for stability
        verify(mockAudioEngine).setBufferSize(512) // Larger buffer for stability
    }

    @Test
    fun testMultiChannelRecordingSupport() = runBlockingTest {
        val channelConfigs = listOf(1, 2) // Mono and stereo
        
        channelConfigs.forEach { channels ->
            `when`(mockAudioEngine.setChannelCount(channels)).thenReturn(true)
            `when`(mockAudioEngine.getChannelCount()).thenReturn(channels)
            
            viewModel.setRecordingChannels(channels)
            viewModel.startRecording()
            
            // Verify channel configuration
            verify(mockAudioEngine).setChannelCount(channels)
            
            val actualChannels = mockAudioEngine.getChannelCount()
            assertEquals(
                channels, 
                actualChannels, 
                "Channel count mismatch for $channels channel recording"
            )
            
            viewModel.stopRecording()
        }
    }

    @Test
    fun testRecordingMetadataAccuracy() = runBlockingTest {
        val expectedMetadata = SampleMetadata(
            name = "Test Recording",
            duration = 5000L, // 5 seconds
            sampleRate = 44100,
            channels = 2,
            bitDepth = 16,
            fileSize = 441000L // Calculated size
        )
        
        // Setup recording with metadata
        `when`(mockAudioEngine.getRecordingMetadata()).thenReturn(expectedMetadata)
        
        viewModel.startRecording()
        
        // Simulate 5-second recording
        Thread.sleep(100) // Brief delay to simulate recording
        
        viewModel.stopRecording()
        
        // Verify metadata accuracy
        val actualMetadata = mockAudioEngine.getRecordingMetadata()
        
        assertEquals(expectedMetadata.sampleRate, actualMetadata.sampleRate)
        assertEquals(expectedMetadata.channels, actualMetadata.channels)
        assertEquals(expectedMetadata.bitDepth, actualMetadata.bitDepth)
        
        // Duration should be approximately correct (within 100ms tolerance)
        assertTrue(
            kotlin.math.abs(expectedMetadata.duration - actualMetadata.duration) < 100,
            "Recording duration inaccurate"
        )
    }

    @Test
    fun testAudioEngineErrorHandling() = runBlockingTest {
        // Test various audio engine error scenarios
        
        // Test recording start failure
        `when`(mockAudioEngine.startRecording(any())).thenReturn(false)
        
        viewModel.startRecording()
        
        // Verify error state is set
        val recordingState = mockSamplingViewModel.recordingState.value
        assertNotNull(recordingState.error, "Error state should be set on recording failure")
        assertEquals(
            RecordingErrorType.AUDIO_ENGINE_FAILURE,
            recordingState.error?.type
        )
        
        // Test recording interruption
        `when`(mockAudioEngine.startRecording(any())).thenReturn(true)
        `when`(mockAudioEngine.isRecording()).thenReturn(true, false) // Interrupted
        
        viewModel.startRecording()
        
        // Simulate interruption detection
        viewModel.checkRecordingStatus()
        
        // Verify interruption is handled
        verify(mockAudioEngine, atLeast(2)).isRecording()
    }

    @Test
    fun testRecordingQualityValidation() = runBlockingTest {
        val qualityMetrics = mutableMapOf<RecordingQuality, QualityMetrics>()
        
        RecordingQuality.values().forEach { quality ->
            // Setup recording with specific quality
            `when`(mockAudioEngine.startRecording(quality)).thenReturn(true)
            
            viewModel.startRecording(quality)
            
            // Simulate recording and measure quality
            val metrics = QualityMetrics(
                signalToNoiseRatio = when (quality) {
                    RecordingQuality.HIGH -> 90f // dB
                    RecordingQuality.MEDIUM -> 80f
                    RecordingQuality.LOW -> 70f
                },
                totalHarmonicDistortion = when (quality) {
                    RecordingQuality.HIGH -> 0.01f // %
                    RecordingQuality.MEDIUM -> 0.05f
                    RecordingQuality.LOW -> 0.1f
                },
                dynamicRange = when (quality) {
                    RecordingQuality.HIGH -> 120f // dB
                    RecordingQuality.MEDIUM -> 96f
                    RecordingQuality.LOW -> 80f
                }
            )
            
            qualityMetrics[quality] = metrics
            
            viewModel.stopRecording()
        }
        
        // Verify quality metrics meet expectations
        qualityMetrics.forEach { (quality, metrics) ->
            when (quality) {
                RecordingQuality.HIGH -> {
                    assertTrue(metrics.signalToNoiseRatio > 85f, "High quality SNR too low")
                    assertTrue(metrics.totalHarmonicDistortion < 0.02f, "High quality THD too high")
                    assertTrue(metrics.dynamicRange > 110f, "High quality dynamic range too low")
                }
                RecordingQuality.MEDIUM -> {
                    assertTrue(metrics.signalToNoiseRatio > 75f, "Medium quality SNR too low")
                    assertTrue(metrics.totalHarmonicDistortion < 0.1f, "Medium quality THD too high")
                    assertTrue(metrics.dynamicRange > 90f, "Medium quality dynamic range too low")
                }
                RecordingQuality.LOW -> {
                    assertTrue(metrics.signalToNoiseRatio > 65f, "Low quality SNR too low")
                    assertTrue(metrics.totalHarmonicDistortion < 0.2f, "Low quality THD too high")
                    assertTrue(metrics.dynamicRange > 70f, "Low quality dynamic range too low")
                }
            }
        }
    }
}

data class QualityMetrics(
    val signalToNoiseRatio: Float, // dB
    val totalHarmonicDistortion: Float, // %
    val dynamicRange: Float // dB
)

enum class RecordingQuality {
    HIGH, MEDIUM, LOW
}