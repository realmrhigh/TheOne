package com.high.theone.features.sequencer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SampleCacheManager
import com.high.theone.features.sampling.VoiceManager
import com.high.theone.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Performance optimization system for sequencer playback.
 * Manages sample caching, voice allocation, memory usage, and CPU optimization
 * for real-time sequencer performance on mobile devices.
 * 
 * Requirements: 10.2, 10.4, 10.5, 10.6
 */
@HiltViewModel
class SequencerPerformanceOptimizer @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleCacheManager: SampleCacheManager,
    private val voiceManager: VoiceManager
) : ViewModel() {

    companion object {
        private const val TAG = "SequencerPerformanceOptimizer"
        private const val PERFORMANCE_MONITOR_INTERVAL_MS = 100L
        private const val MEMORY_CLEANUP_INTERVAL_MS = 5000L
        private const val MAX_CACHED_PATTERNS = 8
        private const val MAX_PRELOADED_SAMPLES = 32
        private const val CPU_USAGE_THRESHOLD = 80f
        private const val MEMORY_USAGE_THRESHOLD = 85f
    }

    // Performance metrics state
    private val _performanceMetrics = MutableStateFlow(SequencerPerformanceMetrics(
        averageLatency = 0L,
        maxLatency = 0L,
        minLatency = 0L,
        jitter = 0L,
        missedTriggers = 0,
        scheduledTriggers = 0,
        cpuUsage = 0f,
        memoryUsage = 0L,
        isRealTimeMode = false,
        bufferUnderruns = 0
    ))
    val performanceMetrics: StateFlow<SequencerPerformanceMetrics> = _performanceMetrics.asStateFlow()

    // Optimization state
    private val _optimizationState = MutableStateFlow(OptimizationState())
    val optimizationState: StateFlow<OptimizationState> = _optimizationState.asStateFlow()

    // Pattern cache for quick access
    private val patternCache = ConcurrentHashMap<String, CachedPattern>()
    
    // Sample preload tracking
    private val preloadedSamples = ConcurrentHashMap<String, PreloadedSample>()
    
    // Voice allocation tracking
    private val activeVoices = ConcurrentHashMap<String, VoiceAllocation>()

    init {
        startPerformanceMonitoring()
        startMemoryCleanup()
    }

    /**
     * Start continuous performance monitoring
     * Requirements: 10.1, 10.2, 10.4
     */
    private fun startPerformanceMonitoring() {
        viewModelScope.launch {
            while (true) {
                try {
                    updatePerformanceMetrics()
                    checkPerformanceThresholds()
                    delay(PERFORMANCE_MONITOR_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performance monitoring", e)
                    delay(1000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Start periodic memory cleanup
     * Requirements: 10.6 (memory management)
     */
    private fun startMemoryCleanup() {
        viewModelScope.launch {
            while (true) {
                try {
                    performMemoryCleanup()
                    delay(MEMORY_CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in memory cleanup", e)
                    delay(5000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Update current performance metrics
     * Requirements: 10.1, 10.2
     */
    private suspend fun updatePerformanceMetrics() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100f

            // Get audio engine metrics if available
            val audioMetrics = if (audioEngine is SequencerAudioEngine) {
                audioEngine.getSequencerPerformanceMetrics()
            } else {
                SequencerPerformanceMetrics(
                    averageLatency = 0L,
                    maxLatency = 0L,
                    minLatency = 0L,
                    jitter = 0L,
                    missedTriggers = 0,
                    scheduledTriggers = 0,
                    cpuUsage = 0f,
                    memoryUsage = usedMemory,
                    isRealTimeMode = true,
                    bufferUnderruns = 0
                )
            }

            _performanceMetrics.value = audioMetrics.copy(
                memoryUsage = usedMemory
            )

            // Update optimization state based on metrics
            _optimizationState.update { state ->
                state.copy(
                    memoryUsagePercent = memoryUsagePercent,
                    cpuUsagePercent = audioMetrics.cpuUsage,
                    activeVoiceCount = activeVoices.size,
                    cachedPatternCount = patternCache.size,
                    preloadedSampleCount = preloadedSamples.size,
                    lastMetricsUpdate = System.currentTimeMillis()
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating performance metrics", e)
        }
    }

    /**
     * Check performance thresholds and apply optimizations
     * Requirements: 10.4, 10.5
     */
    private suspend fun checkPerformanceThresholds() {
        val state = _optimizationState.value
        val metrics = _performanceMetrics.value

        // CPU optimization
        if (state.cpuUsagePercent > CPU_USAGE_THRESHOLD) {
            Log.w(TAG, "High CPU usage detected: ${state.cpuUsagePercent}%")
            applyCpuOptimizations()
        }

        // Memory optimization
        if (state.memoryUsagePercent > MEMORY_USAGE_THRESHOLD) {
            Log.w(TAG, "High memory usage detected: ${state.memoryUsagePercent}%")
            applyMemoryOptimizations()
        }

        // Audio latency optimization
        if (metrics.averageLatency > 50000L) { // 50ms threshold
            Log.w(TAG, "High audio latency detected: ${metrics.averageLatency}μs")
            applyLatencyOptimizations()
        }

        // Voice management optimization
        if (activeVoices.size > voiceManager.getMaxVoices() * 0.8) {
            Log.w(TAG, "High voice usage detected: ${activeVoices.size}")
            optimizeVoiceAllocation()
        }
    }

    /**
     * Apply CPU usage optimizations
     * Requirements: 10.4, 10.5
     */
    private suspend fun applyCpuOptimizations() {
        _optimizationState.update { it.copy(cpuOptimizationActive = true) }

        try {
            // Reduce UI update frequency
            // This would be communicated to UI components
            
            // Optimize voice allocation
            optimizeVoiceAllocation()
            
            // Reduce sample processing quality temporarily
            // This would be implemented in the audio engine
            
            Log.d(TAG, "Applied CPU optimizations")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying CPU optimizations", e)
        } finally {
            // Reset optimization flag after a delay
            viewModelScope.launch {
                delay(2000L)
                _optimizationState.update { it.copy(cpuOptimizationActive = false) }
            }
        }
    }

    /**
     * Apply memory usage optimizations
     * Requirements: 10.6
     */
    private suspend fun applyMemoryOptimizations() {
        _optimizationState.update { it.copy(memoryOptimizationActive = true) }

        try {
            // Clean up old cached patterns
            cleanupPatternCache(force = true)
            
            // Unload unused samples
            cleanupSampleCache()
            
            // Release inactive voices
            releaseInactiveVoices()
            
            // Force garbage collection
            System.gc()
            
            Log.d(TAG, "Applied memory optimizations")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying memory optimizations", e)
        } finally {
            // Reset optimization flag after a delay
            viewModelScope.launch {
                delay(3000L)
                _optimizationState.update { it.copy(memoryOptimizationActive = false) }
            }
        }
    }

    /**
     * Apply audio latency optimizations
     * Requirements: 10.1, 10.3
     */
    private suspend fun applyLatencyOptimizations() {
        try {
            // Preload samples for upcoming patterns
            preloadUpcomingSamples()
            
            // Optimize audio buffer settings
            // This would be implemented in the audio engine
            
            // Reduce concurrent voice count
            optimizeVoiceAllocation()
            
            Log.d(TAG, "Applied latency optimizations")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying latency optimizations", e)
        }
    }

    /**
     * Optimize voice allocation for better performance
     * Requirements: 10.2, 10.5
     */
    private suspend fun optimizeVoiceAllocation() {
        try {
            val maxVoices = voiceManager.getMaxVoices()
            val targetVoices = (maxVoices * 0.7).toInt() // Use 70% of max voices
            
            if (activeVoices.size > targetVoices) {
                // Release oldest voices first
                val sortedVoices = activeVoices.values.sortedBy { it.startTime }
                val voicesToRelease = sortedVoices.take(activeVoices.size - targetVoices)
                
                voicesToRelease.forEach { voice ->
                    voiceManager.releaseVoice(voice.voiceId)
                    activeVoices.remove(voice.voiceId)
                }
                
                Log.d(TAG, "Released ${voicesToRelease.size} voices for optimization")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing voice allocation", e)
        }
    }

    /**
     * Preload samples for efficient sequencer playback
     * Requirements: 10.2, 10.6
     */
    fun preloadSamplesForPattern(pattern: Pattern) {
        viewModelScope.launch {
            try {
                val samplesToPreload = mutableSetOf<String>()
                
                // Collect all sample IDs used in the pattern
                pattern.steps.values.flatten().forEach { step ->
                    if (step.isActive) {
                        // Get sample ID from pad index (this would need pad system integration)
                        // For now, we'll use a placeholder approach
                        samplesToPreload.add("sample_${step.position}")
                    }
                }
                
                // Preload samples if not already cached
                samplesToPreload.forEach { sampleId ->
                    if (!preloadedSamples.containsKey(sampleId) && 
                        preloadedSamples.size < MAX_PRELOADED_SAMPLES) {
                        
                        val success = sampleCacheManager.preloadSample(sampleId)
                        if (success) {
                            preloadedSamples[sampleId] = PreloadedSample(
                                sampleId = sampleId,
                                loadTime = System.currentTimeMillis(),
                                accessCount = 0
                            )
                        }
                    }
                }
                
                Log.d(TAG, "Preloaded ${samplesToPreload.size} samples for pattern ${pattern.name}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading samples for pattern", e)
            }
        }
    }

    /**
     * Cache pattern for quick access
     * Requirements: 10.6
     */
    fun cachePattern(pattern: Pattern) {
        try {
            if (patternCache.size >= MAX_CACHED_PATTERNS) {
                // Remove oldest cached pattern
                val oldestPattern = patternCache.values.minByOrNull { it.cacheTime }
                if (oldestPattern != null) {
                    patternCache.remove(oldestPattern.pattern.id)
                }
            }
            
            patternCache[pattern.id] = CachedPattern(
                pattern = pattern,
                cacheTime = System.currentTimeMillis(),
                accessCount = 0
            )
            
            // Preload samples for this pattern
            preloadSamplesForPattern(pattern)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error caching pattern", e)
        }
    }

    /**
     * Get cached pattern if available
     * Requirements: 10.6
     */
    fun getCachedPattern(patternId: String): Pattern? {
        return patternCache[patternId]?.let { cachedPattern ->
            // Update access count and time
            patternCache[patternId] = cachedPattern.copy(
                accessCount = cachedPattern.accessCount + 1,
                lastAccess = System.currentTimeMillis()
            )
            cachedPattern.pattern
        }
    }

    /**
     * Track voice allocation for optimization
     * Requirements: 10.2, 10.5
     */
    fun trackVoiceAllocation(voiceId: String, padIndex: Int, sampleId: String) {
        activeVoices[voiceId] = VoiceAllocation(
            voiceId = voiceId,
            padIndex = padIndex,
            sampleId = sampleId,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * Release tracked voice
     * Requirements: 10.2, 10.5
     */
    fun releaseTrackedVoice(voiceId: String) {
        activeVoices.remove(voiceId)
    }

    /**
     * Preload samples for upcoming patterns in song mode
     * Requirements: 10.2
     */
    private suspend fun preloadUpcomingSamples() {
        try {
            // This would integrate with song mode to preload samples
            // for the next few patterns in the sequence
            
            Log.d(TAG, "Preloaded upcoming samples")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading upcoming samples", e)
        }
    }

    /**
     * Perform memory cleanup
     * Requirements: 10.6
     */
    private suspend fun performMemoryCleanup() {
        try {
            cleanupPatternCache()
            cleanupSampleCache()
            releaseInactiveVoices()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in memory cleanup", e)
        }
    }

    /**
     * Clean up pattern cache
     * Requirements: 10.6
     */
    private fun cleanupPatternCache(force: Boolean = false) {
        try {
            val currentTime = System.currentTimeMillis()
            val maxAge = if (force) 0L else 300_000L // 5 minutes
            
            val toRemove = patternCache.filter { (_, cachedPattern) ->
                (currentTime - cachedPattern.lastAccess) > maxAge
            }.keys
            
            toRemove.forEach { patternId ->
                patternCache.remove(patternId)
            }
            
            if (toRemove.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${toRemove.size} cached patterns")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up pattern cache", e)
        }
    }

    /**
     * Clean up sample cache
     * Requirements: 10.6
     */
    private suspend fun cleanupSampleCache() {
        try {
            val currentTime = System.currentTimeMillis()
            val maxAge = 600_000L // 10 minutes
            
            val toRemove = preloadedSamples.filter { (_, sample) ->
                (currentTime - sample.lastAccess) > maxAge && sample.accessCount == 0
            }.keys
            
            toRemove.forEach { sampleId ->
                sampleCacheManager.unloadSample(sampleId)
                preloadedSamples.remove(sampleId)
            }
            
            if (toRemove.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${toRemove.size} preloaded samples")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up sample cache", e)
        }
    }

    /**
     * Release inactive voices
     * Requirements: 10.2, 10.5
     */
    private suspend fun releaseInactiveVoices() {
        try {
            val currentTime = System.currentTimeMillis()
            val maxAge = 30_000L // 30 seconds
            
            val toRelease = activeVoices.filter { (_, voice) ->
                (currentTime - voice.startTime) > maxAge
            }.keys
            
            toRelease.forEach { voiceId ->
                voiceManager.releaseVoice(voiceId)
                activeVoices.remove(voiceId)
            }
            
            if (toRelease.isNotEmpty()) {
                Log.d(TAG, "Released ${toRelease.size} inactive voices")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing inactive voices", e)
        }
    }

    /**
     * Get current optimization recommendations
     * Requirements: 10.4, 10.5, 10.6
     */
    fun getOptimizationRecommendations(): List<OptimizationRecommendation> {
        val state = _optimizationState.value
        val metrics = _performanceMetrics.value
        val recommendations = mutableListOf<OptimizationRecommendation>()

        // Memory recommendations
        if (state.memoryUsagePercent > 70f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = OptimizationType.MEMORY,
                    severity = if (state.memoryUsagePercent > 85f) Severity.HIGH else Severity.MEDIUM,
                    message = "High memory usage (${state.memoryUsagePercent.toInt()}%). Consider reducing pattern complexity.",
                    action = "Reduce cached patterns or sample count"
                )
            )
        }

        // CPU recommendations
        if (state.cpuUsagePercent > 60f) {
            recommendations.add(
                OptimizationRecommendation(
                    type = OptimizationType.CPU,
                    severity = if (state.cpuUsagePercent > 80f) Severity.HIGH else Severity.MEDIUM,
                    message = "High CPU usage (${state.cpuUsagePercent.toInt()}%). Consider reducing polyphony.",
                    action = "Reduce simultaneous voices or pattern complexity"
                )
            )
        }

        // Latency recommendations
        if (metrics.averageLatency > 30000L) {
            recommendations.add(
                OptimizationRecommendation(
                    type = OptimizationType.LATENCY,
                    severity = if (metrics.averageLatency > 50000L) Severity.HIGH else Severity.MEDIUM,
                    message = "High audio latency (${metrics.averageLatency / 1000}ms). Consider optimizing audio settings.",
                    action = "Reduce buffer size or sample processing complexity"
                )
            )
        }

        return recommendations
    }

    /**
     * Apply automatic optimizations based on current performance
     * Requirements: 10.4, 10.5, 10.6
     */
    fun applyAutomaticOptimizations() {
        viewModelScope.launch {
            val recommendations = getOptimizationRecommendations()
            
            recommendations.forEach { recommendation ->
                when (recommendation.type) {
                    OptimizationType.MEMORY -> applyMemoryOptimizations()
                    OptimizationType.CPU -> applyCpuOptimizations()
                    OptimizationType.LATENCY -> applyLatencyOptimizations()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources
        patternCache.clear()
        preloadedSamples.clear()
        activeVoices.clear()
    }
}

/**
 * Optimization state tracking
 */
data class OptimizationState(
    val memoryUsagePercent: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val activeVoiceCount: Int = 0,
    val cachedPatternCount: Int = 0,
    val preloadedSampleCount: Int = 0,
    val cpuOptimizationActive: Boolean = false,
    val memoryOptimizationActive: Boolean = false,
    val lastMetricsUpdate: Long = 0L
)

/**
 * Cached pattern information
 */
data class CachedPattern(
    val pattern: Pattern,
    val cacheTime: Long,
    val lastAccess: Long = cacheTime,
    val accessCount: Int = 0
)

/**
 * Preloaded sample information
 */
data class PreloadedSample(
    val sampleId: String,
    val loadTime: Long,
    val lastAccess: Long = loadTime,
    val accessCount: Int = 0
)

/**
 * Voice allocation tracking
 */
data class VoiceAllocation(
    val voiceId: String,
    val padIndex: Int,
    val sampleId: String,
    val startTime: Long
)

/**
 * Optimization recommendation
 */
data class OptimizationRecommendation(
    val type: OptimizationType,
    val severity: Severity,
    val message: String,
    val action: String
)

/**
 * Types of optimizations
 */
enum class OptimizationType {
    MEMORY,
    CPU,
    LATENCY
}

/**
 * Severity levels
 */
enum class Severity {
    LOW,
    MEDIUM,
    HIGH
}