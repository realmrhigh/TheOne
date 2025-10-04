package com.high.theone.features.sequencer

import android.util.Log
import com.high.theone.features.sampling.SampleCacheManager
import com.high.theone.model.Pattern
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Specialized sample caching system optimized for sequencer playback.
 * Provides intelligent preloading, memory management, and performance optimization
 * for real-time pattern playback.
 * 
 * Requirements: 10.2, 10.4, 10.5, 10.6
 */
@Singleton
class SequencerSampleCache @Inject constructor(
    private val baseSampleCache: SampleCacheManager
) {
    companion object {
        private const val TAG = "SequencerSampleCache"
        private const val MAX_SEQUENCER_CACHE_SIZE = 64 * 1024 * 1024 // 64MB
        private const val PRELOAD_LOOKAHEAD_PATTERNS = 2
        private const val CACHE_CLEANUP_INTERVAL_MS = 10000L
        private const val SAMPLE_ACCESS_TIMEOUT_MS = 30000L
    }

    // Sequencer-specific cache
    private val sequencerCache = ConcurrentHashMap<String, SequencerCachedSample>()
    
    // Pattern-to-samples mapping for quick lookup
    private val patternSampleMap = ConcurrentHashMap<String, Set<String>>()
    
    // Cache statistics
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val totalCacheSize = AtomicLong(0)
    
    // Cleanup coroutine
    private var cleanupJob: Job? = null

    /**
     * Initialize the sequencer cache system
     * Requirements: 10.6
     */
    fun initialize() {
        startCacheCleanup()
        Log.d(TAG, "Sequencer sample cache initialized")
    }

    /**
     * Preload samples for a pattern with intelligent caching
     * Requirements: 10.2, 10.6
     */
    suspend fun preloadPatternSamples(
        pattern: Pattern,
        padSampleMap: Map<Int, String>,
        priority: CachePriority = CachePriority.NORMAL
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val samplesToLoad = mutableSetOf<String>()
                
                // Collect all samples used in the pattern
                pattern.steps.forEach { (padIndex, steps) ->
                    val sampleId = padSampleMap[padIndex]
                    if (sampleId != null && steps.any { it.isActive }) {
                        samplesToLoad.add(sampleId)
                    }
                }
                
                // Update pattern-sample mapping
                patternSampleMap[pattern.id] = samplesToLoad
                
                // Preload samples
                var successCount = 0
                val loadJobs = samplesToLoad.map { sampleId ->
                    async {
                        if (preloadSample(sampleId, priority)) {
                            successCount++
                            true
                        } else {
                            false
                        }
                    }
                }
                
                // Wait for all preloads to complete
                loadJobs.awaitAll()
                
                val success = successCount == samplesToLoad.size
                Log.d(TAG, "Preloaded $successCount/${samplesToLoad.size} samples for pattern ${pattern.name}")
                
                success
                
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading pattern samples", e)
                false
            }
        }
    }

    /**
     * Preload a single sample with sequencer optimizations
     * Requirements: 10.2, 10.6
     */
    private suspend fun preloadSample(sampleId: String, priority: CachePriority): Boolean {
        return try {
            // Check if already cached
            if (sequencerCache.containsKey(sampleId)) {
                updateSampleAccess(sampleId)
                cacheHits.incrementAndGet()
                return true
            }
            
            // Check cache size limits
            if (!canAddToCache(sampleId)) {
                performCacheEviction(priority)
            }
            
            // Load sample through base cache manager
            val success = baseSampleCache.preloadSample(sampleId)
            
            if (success) {
                // Add to sequencer cache
                val cachedSample = SequencerCachedSample(
                    sampleId = sampleId,
                    loadTime = System.currentTimeMillis(),
                    lastAccess = System.currentTimeMillis(),
                    accessCount = 1,
                    priority = priority,
                    estimatedSize = estimateSampleSize(sampleId)
                )
                
                sequencerCache[sampleId] = cachedSample
                totalCacheSize.addAndGet(cachedSample.estimatedSize)
                
                Log.d(TAG, "Preloaded sample $sampleId (${cachedSample.estimatedSize} bytes)")
            } else {
                cacheMisses.incrementAndGet()
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading sample $sampleId", e)
            cacheMisses.incrementAndGet()
            false
        }
    }

    /**
     * Ensure sample is ready for immediate playback
     * Requirements: 10.2, 10.4
     */
    suspend fun ensureSampleReady(sampleId: String): Boolean {
        return try {
            // Check sequencer cache first
            if (sequencerCache.containsKey(sampleId)) {
                updateSampleAccess(sampleId)
                cacheHits.incrementAndGet()
                return true
            }
            
            // Check base cache
            if (baseSampleCache.ensureSampleLoaded(sampleId)) {
                // Add to sequencer cache for future access
                addToSequencerCache(sampleId, CachePriority.HIGH)
                cacheHits.incrementAndGet()
                return true
            }
            
            cacheMisses.incrementAndGet()
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring sample ready: $sampleId", e)
            cacheMisses.incrementAndGet()
            false
        }
    }

    /**
     * Preload samples for upcoming patterns in song mode
     * Requirements: 10.2
     */
    suspend fun preloadUpcomingPatterns(
        upcomingPatterns: List<Pattern>,
        padSampleMap: Map<Int, String>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val preloadJobs = upcomingPatterns.take(PRELOAD_LOOKAHEAD_PATTERNS).map { pattern ->
                    async {
                        preloadPatternSamples(pattern, padSampleMap, CachePriority.LOW)
                    }
                }
                
                preloadJobs.awaitAll()
                Log.d(TAG, "Preloaded samples for ${upcomingPatterns.size} upcoming patterns")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading upcoming patterns", e)
            }
        }
    }

    /**
     * Optimize cache for current playback context
     * Requirements: 10.4, 10.5, 10.6
     */
    suspend fun optimizeForPlayback(
        currentPattern: Pattern,
        upcomingPatterns: List<Pattern>,
        padSampleMap: Map<Int, String>
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Ensure current pattern samples are high priority
                preloadPatternSamples(currentPattern, padSampleMap, CachePriority.HIGH)
                
                // Preload upcoming patterns with lower priority
                preloadUpcomingPatterns(upcomingPatterns, padSampleMap)
                
                // Clean up unused samples
                cleanupUnusedSamples(currentPattern, upcomingPatterns)
                
                Log.d(TAG, "Optimized cache for playback")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing cache for playback", e)
            }
        }
    }

    /**
     * Clean up samples not used in current or upcoming patterns
     * Requirements: 10.6
     */
    private suspend fun cleanupUnusedSamples(currentPattern: Pattern, upcomingPatterns: List<Pattern>) {
        try {
            val usedSamples = mutableSetOf<String>()
            
            // Collect samples from current and upcoming patterns
            (listOf(currentPattern) + upcomingPatterns).forEach { pattern ->
                patternSampleMap[pattern.id]?.let { samples ->
                    usedSamples.addAll(samples)
                }
            }
            
            // Remove unused samples from cache
            val toRemove = sequencerCache.keys.filter { sampleId ->
                !usedSamples.contains(sampleId) && 
                sequencerCache[sampleId]?.priority != CachePriority.HIGH
            }
            
            toRemove.forEach { sampleId ->
                removeSampleFromCache(sampleId)
            }
            
            if (toRemove.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${toRemove.size} unused samples")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up unused samples", e)
        }
    }

    /**
     * Update sample access time and count
     * Requirements: 10.6
     */
    private fun updateSampleAccess(sampleId: String) {
        sequencerCache[sampleId]?.let { sample ->
            sequencerCache[sampleId] = sample.copy(
                lastAccess = System.currentTimeMillis(),
                accessCount = sample.accessCount + 1
            )
        }
    }

    /**
     * Add sample to sequencer cache
     * Requirements: 10.6
     */
    private fun addToSequencerCache(sampleId: String, priority: CachePriority) {
        try {
            if (!sequencerCache.containsKey(sampleId)) {
                val estimatedSize = estimateSampleSize(sampleId)
                
                val cachedSample = SequencerCachedSample(
                    sampleId = sampleId,
                    loadTime = System.currentTimeMillis(),
                    lastAccess = System.currentTimeMillis(),
                    accessCount = 1,
                    priority = priority,
                    estimatedSize = estimatedSize
                )
                
                sequencerCache[sampleId] = cachedSample
                totalCacheSize.addAndGet(estimatedSize)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding sample to cache: $sampleId", e)
        }
    }

    /**
     * Check if sample can be added to cache
     * Requirements: 10.6
     */
    private fun canAddToCache(sampleId: String): Boolean {
        val estimatedSize = estimateSampleSize(sampleId)
        return (totalCacheSize.get() + estimatedSize) <= MAX_SEQUENCER_CACHE_SIZE
    }

    /**
     * Perform cache eviction based on priority and usage
     * Requirements: 10.6
     */
    private suspend fun performCacheEviction(newSamplePriority: CachePriority) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Find candidates for eviction (low priority, old, rarely used)
            val evictionCandidates = sequencerCache.values
                .filter { sample ->
                    sample.priority.ordinal <= newSamplePriority.ordinal &&
                    (currentTime - sample.lastAccess) > SAMPLE_ACCESS_TIMEOUT_MS
                }
                .sortedWith(compareBy<SequencerCachedSample> { it.priority.ordinal }
                    .thenBy { it.accessCount }
                    .thenBy { it.lastAccess })
            
            // Remove samples until we have enough space
            val targetSize = MAX_SEQUENCER_CACHE_SIZE * 0.8 // Target 80% usage
            var removedCount = 0
            
            for (candidate in evictionCandidates) {
                if (totalCacheSize.get() <= targetSize) break
                
                removeSampleFromCache(candidate.sampleId)
                removedCount++
            }
            
            if (removedCount > 0) {
                Log.d(TAG, "Evicted $removedCount samples from cache")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing cache eviction", e)
        }
    }

    /**
     * Remove sample from cache
     * Requirements: 10.6
     */
    private suspend fun removeSampleFromCache(sampleId: String) {
        sequencerCache.remove(sampleId)?.let { sample ->
            totalCacheSize.addAndGet(-sample.estimatedSize)
            baseSampleCache.unloadSample(sampleId)
        }
    }

    /**
     * Estimate sample size for cache management
     * Requirements: 10.6
     */
    private fun estimateSampleSize(sampleId: String): Long {
        // This would ideally get actual sample size from metadata
        // For now, use a reasonable estimate
        return 1024 * 1024L // 1MB estimate per sample
    }

    /**
     * Start periodic cache cleanup
     * Requirements: 10.6
     */
    private fun startCacheCleanup() {
        cleanupJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    performPeriodicCleanup()
                    delay(CACHE_CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic cleanup", e)
                    delay(5000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Perform periodic cache cleanup
     * Requirements: 10.6
     */
    private suspend fun performPeriodicCleanup() {
        try {
            val currentTime = System.currentTimeMillis()
            val toRemove = mutableListOf<String>()
            
            sequencerCache.forEach { (sampleId, sample) ->
                // Remove samples that haven't been accessed recently
                if ((currentTime - sample.lastAccess) > SAMPLE_ACCESS_TIMEOUT_MS * 2 &&
                    sample.priority == CachePriority.LOW) {
                    toRemove.add(sampleId)
                }
            }
            
            toRemove.forEach { sampleId ->
                removeSampleFromCache(sampleId)
            }
            
            if (toRemove.isNotEmpty()) {
                Log.d(TAG, "Periodic cleanup removed ${toRemove.size} samples")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in periodic cleanup", e)
        }
    }

    /**
     * Get cache statistics
     * Requirements: 10.1, 10.6
     */
    fun getCacheStatistics(): SequencerCacheStatistics {
        return SequencerCacheStatistics(
            totalSamples = sequencerCache.size,
            totalSizeBytes = totalCacheSize.get(),
            maxSizeBytes = MAX_SEQUENCER_CACHE_SIZE.toLong(),
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            hitRate = if (cacheHits.get() + cacheMisses.get() > 0) {
                cacheHits.get().toFloat() / (cacheHits.get() + cacheMisses.get()).toFloat()
            } else {
                0f
            },
            samplesPerPriority = sequencerCache.values.groupBy { it.priority }.mapValues { it.value.size }
        )
    }

    /**
     * Clear all cached samples
     * Requirements: 10.6
     */
    suspend fun clearCache() {
        try {
            sequencerCache.keys.forEach { sampleId ->
                baseSampleCache.unloadSample(sampleId)
            }
            
            sequencerCache.clear()
            patternSampleMap.clear()
            totalCacheSize.set(0)
            cacheHits.set(0)
            cacheMisses.set(0)
            
            Log.d(TAG, "Cleared sequencer sample cache")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     * Shutdown the cache system
     */
    suspend fun shutdown() {
        cleanupJob?.cancel()
        clearCache()
        Log.d(TAG, "Sequencer sample cache shutdown")
    }
}

/**
 * Cached sample information for sequencer
 */
data class SequencerCachedSample(
    val sampleId: String,
    val loadTime: Long,
    val lastAccess: Long,
    val accessCount: Int,
    val priority: CachePriority,
    val estimatedSize: Long
)

/**
 * Cache priority levels
 */
enum class CachePriority {
    LOW,     // Upcoming patterns, rarely used samples
    NORMAL,  // Regular pattern samples
    HIGH     // Currently playing pattern samples
}

/**
 * Cache statistics for monitoring
 */
data class SequencerCacheStatistics(
    val totalSamples: Int,
    val totalSizeBytes: Long,
    val maxSizeBytes: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val hitRate: Float,
    val samplesPerPriority: Map<CachePriority, Int>
)