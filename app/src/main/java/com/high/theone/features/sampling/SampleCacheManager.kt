package com.high.theone.features.sampling

import android.util.Log
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.SampleRepository
import com.high.theone.model.SampleMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages efficient sample loading and caching for optimal performance.
 * Requirements: 3.3, 4.1, 4.2 (performance optimization)
 */
@Singleton
class SampleCacheManager @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleRepository: SampleRepository
) {
    companion object {
        private const val TAG = "SampleCacheManager"
        private const val MAX_CACHED_SAMPLES = 32 // Maximum samples to keep in memory
        private const val PRELOAD_THRESHOLD_MS = 100L // Preload samples smaller than this
    }

    // Cache state tracking
    private val loadedSamples = ConcurrentHashMap<String, SampleCacheEntry>()
    private val loadingMutex = Mutex()
    private val accessOrder = mutableListOf<String>() // LRU tracking
    private val accessMutex = Mutex()

    data class SampleCacheEntry(
        val sampleId: String,
        val metadata: SampleMetadata,
        val isLoaded: Boolean,
        val lastAccessTime: Long,
        val loadTime: Long = 0L
    )

    /**
     * Preload a sample into the audio engine cache.
     * Uses intelligent caching based on sample size and usage patterns.
     */
    suspend fun preloadSample(sampleId: String): Boolean {
        return loadingMutex.withLock {
            try {
                // Check if already loaded
                val existing = loadedSamples[sampleId]
                if (existing?.isLoaded == true) {
                    updateAccessTime(sampleId)
                    return@withLock true
                }

                // Load sample metadata
                val sampleResult = sampleRepository.loadSample(sampleId)
                val metadata = when (sampleResult) {
                    is com.high.theone.domain.Result.Success -> sampleResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to load sample metadata: $sampleId")
                        return@withLock false
                    }
                }

                // Get sample file path
                val pathResult = sampleRepository.getSampleFilePath(sampleId)
                val filePath = when (pathResult) {
                    is com.high.theone.domain.Result.Success -> pathResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to get sample file path: $sampleId")
                        return@withLock false
                    }
                }

                // Check if we need to make room in cache
                if (loadedSamples.size >= MAX_CACHED_SAMPLES) {
                    evictLeastRecentlyUsed()
                }

                // Load sample into audio engine
                val startTime = System.currentTimeMillis()
                val success = audioEngine.loadSampleToMemory(sampleId, filePath)
                val loadTime = System.currentTimeMillis() - startTime

                if (success) {
                    val entry = SampleCacheEntry(
                        sampleId = sampleId,
                        metadata = metadata,
                        isLoaded = true,
                        lastAccessTime = System.currentTimeMillis(),
                        loadTime = loadTime
                    )
                    
                    loadedSamples[sampleId] = entry
                    updateAccessOrder(sampleId)
                    
                    Log.d(TAG, "Preloaded sample $sampleId in ${loadTime}ms")
                } else {
                    Log.e(TAG, "Failed to preload sample $sampleId")
                }

                success
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading sample $sampleId", e)
                false
            }
        }
    }

    /**
     * Ensure a sample is loaded for immediate playback.
     * Returns true if sample is ready to play.
     */
    suspend fun ensureSampleLoaded(sampleId: String): Boolean {
        val existing = loadedSamples[sampleId]
        if (existing?.isLoaded == true) {
            updateAccessTime(sampleId)
            return true
        }
        
        return preloadSample(sampleId)
    }

    /**
     * Unload a sample from the cache and audio engine.
     */
    suspend fun unloadSample(sampleId: String) {
        loadingMutex.withLock {
            try {
                audioEngine.unloadSample(sampleId)
                loadedSamples.remove(sampleId)
                
                accessMutex.withLock {
                    accessOrder.remove(sampleId)
                }
                
                Log.d(TAG, "Unloaded sample $sampleId")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading sample $sampleId", e)
            }
        }
    }

    /**
     * Preload samples that are likely to be used soon.
     * Called when samples are assigned to pads.
     */
    suspend fun preloadPadSamples(padSamples: Map<Int, String>) {
        try {
            // Preload all assigned samples
            padSamples.values.forEach { sampleId ->
                if (!isSampleLoaded(sampleId)) {
                    preloadSample(sampleId)
                }
            }
            
            Log.d(TAG, "Preloaded ${padSamples.size} pad samples")
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading pad samples", e)
        }
    }

    /**
     * Check if a sample is currently loaded in the cache.
     */
    fun isSampleLoaded(sampleId: String): Boolean {
        return loadedSamples[sampleId]?.isLoaded == true
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            totalSamples = loadedSamples.size,
            loadedSamples = loadedSamples.values.count { it.isLoaded },
            averageLoadTime = loadedSamples.values
                .filter { it.loadTime > 0 }
                .map { it.loadTime }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0
        )
    }

    /**
     * Clear all cached samples.
     */
    suspend fun clearCache() {
        loadingMutex.withLock {
            try {
                loadedSamples.keys.forEach { sampleId ->
                    audioEngine.unloadSample(sampleId)
                }
                
                loadedSamples.clear()
                
                accessMutex.withLock {
                    accessOrder.clear()
                }
                
                Log.d(TAG, "Cleared sample cache")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
            }
        }
    }

    /**
     * Update access time for LRU tracking.
     */
    private suspend fun updateAccessTime(sampleId: String) {
        loadedSamples[sampleId]?.let { entry ->
            loadedSamples[sampleId] = entry.copy(lastAccessTime = System.currentTimeMillis())
        }
        updateAccessOrder(sampleId)
    }

    /**
     * Update access order for LRU eviction.
     */
    private suspend fun updateAccessOrder(sampleId: String) {
        accessMutex.withLock {
            accessOrder.remove(sampleId)
            accessOrder.add(sampleId)
        }
    }

    /**
     * Evict the least recently used sample to make room.
     */
    private suspend fun evictLeastRecentlyUsed() {
        accessMutex.withLock {
            if (accessOrder.isNotEmpty()) {
                val lruSampleId = accessOrder.removeFirst()
                loadedSamples.remove(lruSampleId)
                
                try {
                    audioEngine.unloadSample(lruSampleId)
                    Log.d(TAG, "Evicted LRU sample: $lruSampleId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error evicting sample $lruSampleId", e)
                }
            }
        }
    }

    data class CacheStats(
        val totalSamples: Int,
        val loadedSamples: Int,
        val averageLoadTime: Double
    )
}