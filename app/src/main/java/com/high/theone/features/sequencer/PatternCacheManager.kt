package com.high.theone.features.sequencer

import com.high.theone.domain.PatternRepository
import com.high.theone.domain.PatternMetadata
import com.high.theone.domain.Result
import com.high.theone.model.Pattern
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent pattern caching system with lazy loading and memory management.
 * Provides performance optimization for large pattern libraries.
 * 
 * Requirements: 3.6, 10.6
 */
@Singleton
class PatternCacheManager @Inject constructor(
    private val patternRepository: PatternRepository
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache configuration
    private val maxCacheSize = 50 // Maximum number of patterns to keep in memory
    private val maxMemoryUsageMB = 10 // Maximum memory usage in MB
    private val cacheExpirationMs = 5 * 60 * 1000L // 5 minutes
    private val preloadThreshold = 5 // Preload patterns when cache size drops below this
    
    // Cache storage
    private val patternCache = ConcurrentHashMap<String, PatternCacheEntry>()
    private val metadataCache = ConcurrentHashMap<String, CachedMetadata>()
    private val accessTracker = ConcurrentHashMap<String, AtomicLong>()
    
    // Cache statistics
    private val _cacheStats = MutableStateFlow(PatternCacheStats())
    val cacheStats: StateFlow<PatternCacheStats> = _cacheStats.asStateFlow()
    
    // Background cleanup job
    private var cleanupJob: Job? = null
    
    init {
        startBackgroundCleanup()
    }
    
    /**
     * Get a pattern from cache or load it lazily.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project identifier
     * @return Result with pattern or error
     */
    suspend fun getPattern(
        patternId: String,
        projectId: String
    ): Result<Pattern, PatternCacheError> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = createCacheKey(patternId, projectId)
            
            // Update access time
            updateAccessTime(cacheKey)
            
            // Check cache first
            val cachedPattern = patternCache[cacheKey]
            if (cachedPattern != null && !isExpired(cachedPattern)) {
                updateCacheStats { it.copy(hits = it.hits + 1) }
                return@withContext Result.Success(cachedPattern.pattern)
            }
            
            // Cache miss - load from repository
            updateCacheStats { it.copy(misses = it.misses + 1) }
            
            val loadResult = patternRepository.loadPattern(patternId, projectId)
            if (loadResult.isFailure) {
                return@withContext Result.Failure(
                    PatternCacheError.LoadFailed(patternId, loadResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            val pattern = loadResult.getOrNull()!!
            
            // Cache the pattern
            cachePattern(cacheKey, pattern)
            
            // Trigger preloading if needed
            triggerPreloadingIfNeeded(projectId)
            
            Result.Success(pattern)
        } catch (e: Exception) {
            Result.Failure(PatternCacheError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Get pattern metadata from cache or load it lazily.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project identifier
     * @return Result with metadata or error
     */
    suspend fun getPatternMetadata(
        patternId: String,
        projectId: String
    ): Result<PatternMetadata, PatternCacheError> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = createCacheKey(patternId, projectId)
            
            // Update access time
            updateAccessTime(cacheKey)
            
            // Check metadata cache first
            val cachedMetadata = metadataCache[cacheKey]
            if (cachedMetadata != null && !isExpired(cachedMetadata)) {
                updateCacheStats { it.copy(metadataHits = it.metadataHits + 1) }
                return@withContext Result.Success(cachedMetadata.metadata)
            }
            
            // Check if we have the full pattern cached
            val cachedPattern = patternCache[cacheKey]
            if (cachedPattern != null && !isExpired(cachedPattern)) {
                val metadata = createMetadataFromPattern(cachedPattern.pattern)
                cacheMetadata(cacheKey, metadata)
                updateCacheStats { it.copy(metadataHits = it.metadataHits + 1) }
                return@withContext Result.Success(metadata)
            }
            
            // Cache miss - load metadata from repository
            updateCacheStats { it.copy(metadataMisses = it.metadataMisses + 1) }
            
            val metadataResult = patternRepository.getPatternMetadata(patternId, projectId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(
                    PatternCacheError.LoadFailed(patternId, metadataResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            val metadata = metadataResult.getOrNull()!!
            
            // Cache the metadata
            cacheMetadata(cacheKey, metadata)
            
            Result.Success(metadata)
        } catch (e: Exception) {
            Result.Failure(PatternCacheError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Preload patterns for a project to improve performance.
     * 
     * @param projectId Project identifier
     * @param maxPatterns Maximum number of patterns to preload
     */
    suspend fun preloadPatterns(
        projectId: String,
        maxPatterns: Int = preloadThreshold
    ) = withContext(Dispatchers.IO) {
        try {
            val metadataResult = patternRepository.getAllPatternMetadata(projectId)
            if (metadataResult.isFailure) return@withContext
            
            val allMetadata = metadataResult.getOrNull()!!
            
            // Sort by most recently modified and limit
            val patternsToPreload = allMetadata
                .sortedByDescending { it.modifiedAt }
                .take(maxPatterns)
            
            // Preload patterns that aren't already cached
            patternsToPreload.forEach { metadata ->
                val cacheKey = createCacheKey(metadata.id, projectId)
                if (!patternCache.containsKey(cacheKey)) {
                    launch {
                        try {
                            val loadResult = patternRepository.loadPattern(metadata.id, projectId)
                            if (loadResult.isSuccess) {
                                val pattern = loadResult.getOrNull()!!
                                cachePattern(cacheKey, pattern)
                                updateCacheStats { it.copy(preloaded = it.preloaded + 1) }
                            }
                        } catch (e: Exception) {
                            // Ignore preload failures
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore preload failures
        }
    }
    
    /**
     * Invalidate cache entry for a specific pattern.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project identifier
     */
    fun invalidatePattern(patternId: String, projectId: String) {
        val cacheKey = createCacheKey(patternId, projectId)
        patternCache.remove(cacheKey)
        metadataCache.remove(cacheKey)
        accessTracker.remove(cacheKey)
        updateCacheStats { it.copy(evictions = it.evictions + 1) }
    }
    
    /**
     * Invalidate all cache entries for a project.
     * 
     * @param projectId Project identifier
     */
    fun invalidateProject(projectId: String) {
        val keysToRemove = patternCache.keys.filter { it.startsWith("$projectId:") }
        keysToRemove.forEach { key ->
            patternCache.remove(key)
            metadataCache.remove(key)
            accessTracker.remove(key)
        }
        updateCacheStats { it.copy(evictions = it.evictions + keysToRemove.size) }
    }
    
    /**
     * Clear all cache entries.
     */
    fun clearCache() {
        val totalEntries = patternCache.size + metadataCache.size
        patternCache.clear()
        metadataCache.clear()
        accessTracker.clear()
        updateCacheStats { 
            PatternCacheStats(evictions = it.evictions + totalEntries)
        }
    }
    
    /**
     * Get current cache statistics.
     */
    fun getCacheStats(): PatternCacheStats = _cacheStats.value
    
    /**
     * Get memory usage estimation in bytes.
     */
    fun getMemoryUsageBytes(): Long {
        var totalBytes = 0L
        
        patternCache.values.forEach { cachedPattern ->
            totalBytes += estimatePatternSize(cachedPattern.pattern)
        }
        
        metadataCache.values.forEach { cachedMetadata ->
            totalBytes += estimateMetadataSize(cachedMetadata.metadata)
        }
        
        return totalBytes
    }
    
    /**
     * Force cleanup of expired and least recently used entries.
     */
    suspend fun forceCleanup() = withContext(Dispatchers.IO) {
        performCleanup()
    }
    
    // Private helper methods
    
    private fun createCacheKey(patternId: String, projectId: String): String {
        return "$projectId:$patternId"
    }
    
    private fun updateAccessTime(cacheKey: String) {
        accessTracker.getOrPut(cacheKey) { AtomicLong(0) }.set(System.currentTimeMillis())
    }
    
    private fun cachePattern(cacheKey: String, pattern: Pattern) {
        // Check if we need to make room
        if (patternCache.size >= maxCacheSize || getMemoryUsageBytes() > maxMemoryUsageMB * 1024 * 1024) {
            evictLeastRecentlyUsed()
        }
        
        val cachedPattern = PatternCacheEntry(
            pattern = pattern,
            cachedAt = System.currentTimeMillis(),
            accessCount = 1
        )
        
        patternCache[cacheKey] = cachedPattern
        updateAccessTime(cacheKey)
        
        updateCacheStats { it.copy(totalPatternsCached = patternCache.size) }
    }
    
    private fun cacheMetadata(cacheKey: String, metadata: PatternMetadata) {
        val cachedMetadata = CachedMetadata(
            metadata = metadata,
            cachedAt = System.currentTimeMillis(),
            accessCount = 1
        )
        
        metadataCache[cacheKey] = cachedMetadata
        updateAccessTime(cacheKey)
        
        updateCacheStats { it.copy(totalMetadataCached = metadataCache.size) }
    }
    
    private fun isExpired(cachedPattern: PatternCacheEntry): Boolean {
        return System.currentTimeMillis() - cachedPattern.cachedAt > cacheExpirationMs
    }
    
    private fun isExpired(cachedMetadata: CachedMetadata): Boolean {
        return System.currentTimeMillis() - cachedMetadata.cachedAt > cacheExpirationMs
    }
    
    private fun evictLeastRecentlyUsed() {
        // Find least recently used entries
        val sortedByAccess = accessTracker.entries
            .sortedBy { it.value.get() }
            .take(maxCacheSize / 4) // Evict 25% of entries
        
        sortedByAccess.forEach { (cacheKey, _) ->
            patternCache.remove(cacheKey)
            metadataCache.remove(cacheKey)
            accessTracker.remove(cacheKey)
        }
        
        updateCacheStats { 
            it.copy(
                evictions = it.evictions + sortedByAccess.size,
                totalPatternsCached = patternCache.size,
                totalMetadataCached = metadataCache.size
            )
        }
    }
    
    private fun triggerPreloadingIfNeeded(projectId: String) {
        val projectPatterns = patternCache.keys.count { it.startsWith("$projectId:") }
        if (projectPatterns < preloadThreshold) {
            scope.launch {
                preloadPatterns(projectId, preloadThreshold - projectPatterns)
            }
        }
    }
    
    private fun startBackgroundCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60_000) // Run cleanup every minute
                performCleanup()
            }
        }
    }
    
    private suspend fun performCleanup() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        var expiredCount = 0
        
        // Remove expired pattern cache entries
        val expiredPatternKeys = patternCache.entries
            .filter { currentTime - it.value.cachedAt > cacheExpirationMs }
            .map { it.key }
        
        expiredPatternKeys.forEach { key ->
            patternCache.remove(key)
            accessTracker.remove(key)
            expiredCount++
        }
        
        // Remove expired metadata cache entries
        val expiredMetadataKeys = metadataCache.entries
            .filter { currentTime - it.value.cachedAt > cacheExpirationMs }
            .map { it.key }
        
        expiredMetadataKeys.forEach { key ->
            metadataCache.remove(key)
            accessTracker.remove(key)
            expiredCount++
        }
        
        // Check memory usage and evict if necessary
        if (getMemoryUsageBytes() > maxMemoryUsageMB * 1024 * 1024) {
            evictLeastRecentlyUsed()
        }
        
        updateCacheStats { 
            it.copy(
                evictions = it.evictions + expiredCount,
                totalPatternsCached = patternCache.size,
                totalMetadataCached = metadataCache.size,
                lastCleanup = currentTime
            )
        }
    }
    
    private fun createMetadataFromPattern(pattern: Pattern): PatternMetadata {
        val stepCount = pattern.steps.values.sumOf { stepList ->
            stepList.count { it.isActive }
        }
        
        return PatternMetadata(
            id = pattern.id,
            name = pattern.name,
            length = pattern.length,
            tempo = pattern.tempo,
            swing = pattern.swing,
            createdAt = pattern.createdAt,
            modifiedAt = pattern.modifiedAt,
            stepCount = stepCount
        )
    }
    
    private fun estimatePatternSize(pattern: Pattern): Long {
        // Rough estimation of pattern memory usage
        var size = 200L // Base object overhead
        size += pattern.name.length * 2L // String characters
        size += pattern.steps.size * 50L // Map overhead
        size += pattern.steps.values.sumOf { it.size * 30L } // Step objects
        return size
    }
    
    private fun estimateMetadataSize(metadata: PatternMetadata): Long {
        // Rough estimation of metadata memory usage
        return 100L + metadata.name.length * 2L
    }
    
    private fun updateCacheStats(update: (PatternCacheStats) -> PatternCacheStats) {
        _cacheStats.value = update(_cacheStats.value)
    }
    
    fun cleanup() {
        cleanupJob?.cancel()
        scope.cancel()
    }
}

/**
 * Cached pattern with metadata.
 */
private data class PatternCacheEntry(
    val pattern: Pattern,
    val cachedAt: Long,
    val accessCount: Int
)

/**
 * Cached metadata with tracking information.
 */
private data class CachedMetadata(
    val metadata: PatternMetadata,
    val cachedAt: Long,
    val accessCount: Int
)

/**
 * Cache statistics for monitoring and debugging.
 */
data class PatternCacheStats(
    val hits: Long = 0,
    val misses: Long = 0,
    val metadataHits: Long = 0,
    val metadataMisses: Long = 0,
    val evictions: Long = 0,
    val preloaded: Long = 0,
    val totalPatternsCached: Int = 0,
    val totalMetadataCached: Int = 0,
    val lastCleanup: Long = 0
) {
    val hitRate: Float get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
    val metadataHitRate: Float get() = if (metadataHits + metadataMisses > 0) metadataHits.toFloat() / (metadataHits + metadataMisses) else 0f
}

/**
 * Specialized error types for pattern cache operations.
 */
sealed class PatternCacheError(val message: String) {
    data class LoadFailed(val patternId: String, val reason: String) : PatternCacheError("Failed to load pattern '$patternId': $reason")
    data class UnexpectedError(val reason: String) : PatternCacheError("Unexpected cache error: $reason")
}