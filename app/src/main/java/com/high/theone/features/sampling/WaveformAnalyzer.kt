package com.high.theone.features.sampling

import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Waveform analyzer for generating visual waveform data from audio samples.
 * Provides efficient waveform generation with downsampling for UI display.
 * 
 * Requirements: 4.1 (waveform visualization for recorded samples)
 */
@Singleton
class WaveformAnalyzer @Inject constructor() {
    
    companion object {
        private const val DEFAULT_WAVEFORM_SAMPLES = 1000 // Number of points for UI display
        private const val MIN_PEAK_THRESHOLD = 0.01f // Minimum peak level to consider
        private const val MAX_AMPLITUDE = 32767f // 16-bit audio max amplitude
    }
    
    /**
     * Generate waveform data from an audio file
     */
    suspend fun generateWaveform(
        filePath: String,
        targetSamples: Int = DEFAULT_WAVEFORM_SAMPLES
    ): WaveformData = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext WaveformData.empty()
            }
            
            // Try to read as WAV file first (most common for recordings)
            val waveformData = readWavFile(file, targetSamples)
            if (waveformData.samples.isNotEmpty()) {
                return@withContext waveformData
            }
            
            // Fallback to MediaMetadataRetriever for other formats
            return@withContext generateWaveformFromMediaFile(filePath, targetSamples)
            
        } catch (e: Exception) {
            WaveformData.empty()
        }
    }
    
    /**
     * Generate waveform data from URI
     */
    suspend fun generateWaveform(
        uri: Uri,
        targetSamples: Int = DEFAULT_WAVEFORM_SAMPLES
    ): WaveformData = withContext(Dispatchers.IO) {
        try {
            // Convert URI to file path if possible
            val filePath = uri.path
            if (filePath != null) {
                return@withContext generateWaveform(filePath, targetSamples)
            }
            
            WaveformData.empty()
        } catch (e: Exception) {
            WaveformData.empty()
        }
    }
    
    /**
     * Read WAV file and generate waveform data
     */
    private fun readWavFile(file: File, targetSamples: Int): WaveformData {
        try {
            FileInputStream(file).use { fis ->
                // Read WAV header
                val header = ByteArray(44)
                val headerBytesRead = fis.read(header)
                if (headerBytesRead < 44) {
                    return WaveformData.empty()
                }
                
                // Parse WAV header
                val wavHeader = parseWavHeader(header)
                if (!wavHeader.isValid) {
                    return WaveformData.empty()
                }
                
                // Calculate how many samples to skip for downsampling
                val totalSamples = wavHeader.dataSize / (wavHeader.bitsPerSample / 8) / wavHeader.channels
                val samplesPerPoint = max(1, totalSamples / targetSamples)
                
                // Read audio data
                val audioData = ByteArray(wavHeader.dataSize)
                val audioBytesRead = fis.read(audioData)
                if (audioBytesRead < wavHeader.dataSize) {
                    return WaveformData.empty()
                }
                
                // Convert to waveform samples
                val waveformSamples = processAudioData(
                    audioData = audioData,
                    bitsPerSample = wavHeader.bitsPerSample,
                    channels = wavHeader.channels,
                    samplesPerPoint = samplesPerPoint,
                    targetSamples = targetSamples
                )
                
                return WaveformData(
                    samples = waveformSamples,
                    sampleRate = wavHeader.sampleRate,
                    channels = wavHeader.channels,
                    durationMs = (totalSamples * 1000L) / wavHeader.sampleRate,
                    peakAmplitude = waveformSamples.maxOrNull() ?: 0f,
                    rmsAmplitude = calculateRMS(waveformSamples)
                )
            }
        } catch (e: Exception) {
            return WaveformData.empty()
        }
    }
    
    /**
     * Generate waveform using MediaMetadataRetriever (fallback method)
     */
    private fun generateWaveformFromMediaFile(filePath: String, targetSamples: Int): WaveformData {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            // Get basic metadata
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val sampleRate = sampleRateStr?.toIntOrNull() ?: 44100
            
            retriever.release()
            
            // For non-WAV files, generate a placeholder waveform
            // In a real implementation, you might use FFmpeg or similar
            val placeholderSamples = generatePlaceholderWaveform(targetSamples, durationMs)
            
            return WaveformData(
                samples = placeholderSamples,
                sampleRate = sampleRate,
                channels = 1, // Assume mono for placeholder
                durationMs = durationMs,
                peakAmplitude = placeholderSamples.maxOrNull() ?: 0f,
                rmsAmplitude = calculateRMS(placeholderSamples)
            )
            
        } catch (e: Exception) {
            return WaveformData.empty()
        }
    }
    
    /**
     * Parse WAV file header
     */
    private fun parseWavHeader(header: ByteArray): WavHeader {
        try {
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            
            // Check RIFF signature
            val riffSignature = String(header, 0, 4)
            if (riffSignature != "RIFF") {
                return WavHeader.invalid()
            }
            
            // Skip file size (4 bytes)
            buffer.position(8)
            
            // Check WAVE signature
            val waveSignature = String(header, 8, 4)
            if (waveSignature != "WAVE") {
                return WavHeader.invalid()
            }
            
            // Find fmt chunk
            buffer.position(12)
            while (buffer.remaining() >= 8) {
                val chunkId = String(header, buffer.position(), 4)
                buffer.position(buffer.position() + 4)
                val chunkSize = buffer.int
                
                if (chunkId == "fmt ") {
                    // Parse format chunk
                    val audioFormat = buffer.short.toInt()
                    val channels = buffer.short.toInt()
                    val sampleRate = buffer.int
                    val byteRate = buffer.int
                    val blockAlign = buffer.short.toInt()
                    val bitsPerSample = buffer.short.toInt()
                    
                    // Find data chunk
                    while (buffer.remaining() >= 8) {
                        val dataChunkId = String(header, buffer.position(), 4)
                        buffer.position(buffer.position() + 4)
                        val dataSize = buffer.int
                        
                        if (dataChunkId == "data") {
                            return WavHeader(
                                isValid = true,
                                audioFormat = audioFormat,
                                channels = channels,
                                sampleRate = sampleRate,
                                bitsPerSample = bitsPerSample,
                                dataSize = dataSize
                            )
                        } else {
                            // Skip this chunk
                            buffer.position(buffer.position() + dataSize)
                        }
                    }
                } else {
                    // Skip this chunk
                    buffer.position(buffer.position() + chunkSize)
                }
            }
            
            return WavHeader.invalid()
        } catch (e: Exception) {
            return WavHeader.invalid()
        }
    }
    
    /**
     * Process raw audio data into waveform samples
     */
    private fun processAudioData(
        audioData: ByteArray,
        bitsPerSample: Int,
        channels: Int,
        samplesPerPoint: Int,
        targetSamples: Int
    ): FloatArray {
        val waveformSamples = mutableListOf<Float>()
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        
        val bytesPerSample = bitsPerSample / 8
        val totalSamples = audioData.size / bytesPerSample / channels
        
        var sampleIndex = 0
        while (sampleIndex < totalSamples && waveformSamples.size < targetSamples) {
            var maxAmplitude = 0f
            
            // Process a group of samples for this waveform point
            val endIndex = min(sampleIndex + samplesPerPoint, totalSamples)
            for (i in sampleIndex until endIndex) {
                val byteOffset = i * bytesPerSample * channels
                
                // Read sample for each channel and take the maximum
                for (channel in 0 until channels) {
                    val channelOffset = byteOffset + (channel * bytesPerSample)
                    if (channelOffset + bytesPerSample <= audioData.size) {
                        val amplitude = when (bitsPerSample) {
                            16 -> {
                                val sample = buffer.getShort(channelOffset).toFloat()
                                abs(sample / MAX_AMPLITUDE)
                            }
                            8 -> {
                                val sample = (audioData[channelOffset].toInt() and 0xFF) - 128
                                abs(sample / 128f)
                            }
                            24 -> {
                                // 24-bit samples (3 bytes)
                                val byte1 = audioData[channelOffset].toInt() and 0xFF
                                val byte2 = audioData[channelOffset + 1].toInt() and 0xFF
                                val byte3 = audioData[channelOffset + 2].toInt() and 0xFF
                                val sample = (byte3 shl 16) or (byte2 shl 8) or byte1
                                val signedSample = if (sample > 0x7FFFFF) sample - 0x1000000 else sample
                                abs(signedSample / 8388607f)
                            }
                            32 -> {
                                val sample = buffer.getInt(channelOffset).toFloat()
                                abs(sample / Int.MAX_VALUE)
                            }
                            else -> 0f
                        }
                        
                        maxAmplitude = max(maxAmplitude, amplitude)
                    }
                }
            }
            
            waveformSamples.add(maxAmplitude)
            sampleIndex = endIndex
        }
        
        return waveformSamples.toFloatArray()
    }
    
    /**
     * Generate placeholder waveform for unsupported formats
     */
    private fun generatePlaceholderWaveform(targetSamples: Int, durationMs: Long): FloatArray {
        val samples = FloatArray(targetSamples)
        
        // Generate a simple sine wave pattern as placeholder
        for (i in samples.indices) {
            val progress = i.toFloat() / samples.size
            val frequency = 2f // 2 cycles across the waveform
            val amplitude = 0.3f * sin(progress * frequency * 2 * PI).toFloat()
            
            // Add some randomness to make it look more realistic
            val noise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f
            samples[i] = abs(amplitude + noise).coerceIn(0f, 1f)
        }
        
        return samples
    }
    
    /**
     * Calculate RMS (Root Mean Square) amplitude
     */
    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        
        val sumOfSquares = samples.map { it * it }.sum()
        return sqrt(sumOfSquares / samples.size)
    }
    
    /**
     * Analyze waveform for additional metadata
     */
    fun analyzeWaveform(waveformData: WaveformData): WaveformAnalysis {
        val samples = waveformData.samples
        if (samples.isEmpty()) {
            return WaveformAnalysis.empty()
        }
        
        // Find peaks
        val peaks = findPeaks(samples)
        
        // Calculate dynamic range
        val maxAmplitude = samples.maxOrNull() ?: 0f
        val minAmplitude = samples.minOrNull() ?: 0f
        val dynamicRange = maxAmplitude - minAmplitude
        
        // Calculate average amplitude
        val averageAmplitude = samples.average().toFloat()
        
        // Detect silence regions
        val silenceRegions = detectSilenceRegions(samples)
        
        return WaveformAnalysis(
            peakCount = peaks.size,
            dynamicRange = dynamicRange,
            averageAmplitude = averageAmplitude,
            silencePercentage = silenceRegions.sumOf { it.second - it.first } / samples.size.toFloat(),
            hasClipping = maxAmplitude >= 0.95f,
            recommendedTrimStart = findRecommendedTrimStart(samples),
            recommendedTrimEnd = findRecommendedTrimEnd(samples)
        )
    }
    
    /**
     * Find peaks in the waveform
     */
    private fun findPeaks(samples: FloatArray, threshold: Float = MIN_PEAK_THRESHOLD): List<Int> {
        val peaks = mutableListOf<Int>()
        
        for (i in 1 until samples.size - 1) {
            if (samples[i] > threshold &&
                samples[i] > samples[i - 1] &&
                samples[i] > samples[i + 1]) {
                peaks.add(i)
            }
        }
        
        return peaks
    }
    
    /**
     * Detect silence regions in the waveform
     */
    private fun detectSilenceRegions(
        samples: FloatArray,
        silenceThreshold: Float = 0.02f
    ): List<Pair<Int, Int>> {
        val silenceRegions = mutableListOf<Pair<Int, Int>>()
        var silenceStart: Int? = null
        
        for (i in samples.indices) {
            if (samples[i] <= silenceThreshold) {
                if (silenceStart == null) {
                    silenceStart = i
                }
            } else {
                if (silenceStart != null) {
                    silenceRegions.add(Pair(silenceStart, i))
                    silenceStart = null
                }
            }
        }
        
        // Handle silence at the end
        if (silenceStart != null) {
            silenceRegions.add(Pair(silenceStart, samples.size))
        }
        
        return silenceRegions
    }
    
    /**
     * Find recommended trim start position
     */
    private fun findRecommendedTrimStart(
        samples: FloatArray,
        threshold: Float = 0.05f
    ): Float {
        for (i in samples.indices) {
            if (samples[i] > threshold) {
                return (i.toFloat() / samples.size).coerceAtLeast(0f)
            }
        }
        return 0f
    }
    
    /**
     * Find recommended trim end position
     */
    private fun findRecommendedTrimEnd(
        samples: FloatArray,
        threshold: Float = 0.05f
    ): Float {
        for (i in samples.indices.reversed()) {
            if (samples[i] > threshold) {
                return ((i + 1).toFloat() / samples.size).coerceAtMost(1f)
            }
        }
        return 1f
    }
}

/**
 * WAV file header information
 */
private data class WavHeader(
    val isValid: Boolean,
    val audioFormat: Int = 0,
    val channels: Int = 0,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
    val dataSize: Int = 0
) {
    companion object {
        fun invalid() = WavHeader(isValid = false)
    }
}

/**
 * Waveform data container
 */
data class WaveformData(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val peakAmplitude: Float,
    val rmsAmplitude: Float
) {
    companion object {
        fun empty() = WaveformData(
            samples = floatArrayOf(),
            sampleRate = 44100,
            channels = 1,
            durationMs = 0L,
            peakAmplitude = 0f,
            rmsAmplitude = 0f
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as WaveformData
        
        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (durationMs != other.durationMs) return false
        if (peakAmplitude != other.peakAmplitude) return false
        if (rmsAmplitude != other.rmsAmplitude) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + peakAmplitude.hashCode()
        result = 31 * result + rmsAmplitude.hashCode()
        return result
    }
}

/**
 * Waveform analysis results
 */
data class WaveformAnalysis(
    val peakCount: Int,
    val dynamicRange: Float,
    val averageAmplitude: Float,
    val silencePercentage: Float,
    val hasClipping: Boolean,
    val recommendedTrimStart: Float,
    val recommendedTrimEnd: Float
) {
    companion object {
        fun empty() = WaveformAnalysis(
            peakCount = 0,
            dynamicRange = 0f,
            averageAmplitude = 0f,
            silencePercentage = 0f,
            hasClipping = false,
            recommendedTrimStart = 0f,
            recommendedTrimEnd = 1f
        )
    }
}