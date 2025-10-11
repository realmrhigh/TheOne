package com.high.theone.features.compactui.performance

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory management system specifically for recording operations
 * 
 * Implements requirement 6.3: Memory management and component unloading
 * - Tracks recording buffer memory usage
 * - Automatically cleans up unused buffers
 * - Provides memory pressure monitoring
 * - Implements buffer pooling for efficiency
 */
@Singleton
class RecordingMemoryManager @Inject constructor() {
    companion object {
        private const val TAG = "RecordingMemoryManager"
        private const val MAX_BUFFER_POOL_SIZE = 10
        private const val BUFFER_CLEANUP_INTERVAL_MS = 5000L // 5 seconds
        private const val UNUSED_BUFFER_TIMEOUT_MS = 30000L // 30 seconds
        private const val MEMORY_PRESSURE_THRESHOLD = 0.8f
        private const val CRITICAL_MEMORY_THRESHOLD = 0.9f
    }

    private val _memoryState = MutableStateFlow(RecordingMemoryState())
    val memoryState: StateFlow<RecordingMemoryState> = _memoryState.asStateFlow()

    private val activeBuffers = ConcurrentHashMap<String, RecordingBuffer>()
    private val bufferPool = mutableListOf<RecordingBuffer>()
    private val bufferUsageTracker = ConcurrentHashMap<String, Long>() // Last access time
    
    private var cleanupJob: Job? = null
    private var memoryMonitoringJob: Job? = null

    /**
     * Start memory management monitoring
     */
    fun startMemoryManagement(coroutineScope: CoroutineScope) {
        stopMemoryManagement()
        
        // Start periodic cleanup
        cleanupJob = coroutineScope.launch {
            while (isActive) {
                performPeriodicCleanup()
                delay(BUFFER_CLEANUP_INTERVAL_MS)
            }
        }
        
        // Start memory monitoring
        memoryMonitoringJob = coroutineScope.launch {
            while (isActive) {
                updateMemoryState()
                delay(1000) // Update every second
            }
        }
        
        Log.d(TAG, "Recording memory management started")
    }

    /**
     * Stop memory management
     */
    fun stopMemoryManagement() {
        cleanupJob?.cancel()
        memoryMonitoringJob?.cancel()
        cleanupJob = null
        memoryMonitoringJob = null
        
        Log.d(TAG, "Recording memory management stopped")
    }

    /**
     * Allocate a recording buffer
     */
    fun allocateRecordingBuffer(
        bufferId: String,
        sizeBytes: Long,
        sampleRate: Int = 44100,
        channels: Int = 2
    ): RecordingBuffer? {
        return try {
            // Check if we have memory pressure
            val currentState = _memoryState.value
            if (currentState.memoryPressure > CRITICAL_MEMORY_THRESHOLD) {
                Log.w(TAG, "Cannot allocate buffer - critical memory pressure")
                return null
            }
            
            // Try to reuse from pool first
            val reusableBuffer = bufferPool.find { 
                it.sizeBytes >= sizeBytes && 
                it.sampleRate == sampleRate && 
                it.channels == channels 
            }
            
            val buffer = if (reusableBuffer != null) {
                bufferPool.remove(reusableBuffer)
                reusableBuffer.copy(
                    id = bufferId,
                    allocatedTime = System.currentTimeMillis()
                )
            } else {
                // Allocate new buffer
                RecordingBuffer(
                    id = bufferId,
                    sizeBytes = sizeBytes,
                    sampleRate = sampleRate,
                    channels = channels,
                    allocatedTime = System.currentTimeMillis()
                )
            }
            
            activeBuffers[bufferId] = buffer
            bufferUsageTracker[bufferId] = System.currentTimeMillis()
            
            Log.d(TAG, "Allocated recording buffer: $bufferId (${sizeBytes / 1024}KB)")
            buffer
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory allocating recording buffer", e)
            // Try emergency cleanup and retry once
            performEmergencyCleanup()
            null
        }
    }

    /**
     * Release a recording buffer
     */
    fun releaseRecordingBuffer(bufferId: String) {
        val buffer = activeBuffers.remove(bufferId)
        if (buffer != null) {
            // Add to pool for reuse if pool isn't full
            if (bufferPool.size < MAX_BUFFER_POOL_SIZE) {
                bufferPool.add(buffer)
            }
            
            bufferUsageTracker.remove(bufferId)
            Log.d(TAG, "Released recording buffer: $bufferId")
        }
    }

    /**
     * Update buffer usage timestamp
     */
    fun updateBufferUsage(bufferId: String) {
        if (activeBuffers.containsKey(bufferId)) {
            bufferUsageTracker[bufferId] = System.currentTimeMillis()
        }
    }

    /**
     * Get current memory usage for recording buffers
     */
    fun getRecordingBufferMemoryUsage(): Long {
        return activeBuffers.values.sumOf { it.sizeBytes } +
               bufferPool.sumOf { it.sizeBytes }
    }

    /**
     * Force cleanup of all unused buffers
     */
    fun forceCleanup() {
        performEmergencyCleanup()
        Log.d(TAG, "Forced cleanup completed")
    }

    /**
     * Get memory recommendations
     */
    fun getMemoryRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val currentState = _memoryState.value
        
        if (currentState.memoryPressure > MEMORY_PRESSURE_THRESHOLD) {
            recommendations.add("High memory usage detected")
            
            if (currentState.unusedBufferCount > 0) {
                recommendations.add("Clean up ${currentState.unusedBufferCount} unused buffers")
            }
            
            if (currentState.pooledBufferCount > 5) {
                recommendations.add("Reduce buffer pool size")
            }
            
            recommendations.add("Consider reducing recording quality")
        }
        
        if (currentState.activeBufferCount > 5) {
            recommendations.add("Many active buffers - consider limiting concurrent recordings")
        }
        
        return recommendations
    }

    private suspend fun updateMemoryState() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        val memoryPressure = usedMemory.toFloat() / maxMemory.toFloat()
        val recordingBufferMemory = getRecordingBufferMemoryUsage()
        
        val currentTime = System.currentTimeMillis()
        val unusedBuffers = bufferUsageTracker.count { (_, lastUsed) ->
            currentTime - lastUsed > UNUSED_BUFFER_TIMEOUT_MS
        }
        
        val newState = RecordingMemoryState(
            totalMemoryUsage = usedMemory,
            recordingBufferMemory = recordingBufferMemory,
            memoryPressure = memoryPressure,
            activeBufferCount = activeBuffers.size,
            pooledBufferCount = bufferPool.size,
            unusedBufferCount = unusedBuffers,
            lastCleanupTime = _memoryState.value.lastCleanupTime
        )
        
        _memoryState.value = newState
        
        // Trigger automatic cleanup if needed
        if (memoryPressure > MEMORY_PRESSURE_THRESHOLD) {
            performAutomaticCleanup()
        }
    }

    private suspend fun performPeriodicCleanup() {
        val currentTime = System.currentTimeMillis()
        val buffersToRemove = mutableListOf<String>()
        
        // Find unused buffers
        bufferUsageTracker.forEach { (bufferId, lastUsed) ->
            if (currentTime - lastUsed > UNUSED_BUFFER_TIMEOUT_MS) {
                buffersToRemove.add(bufferId)
            }
        }
        
        // Remove unused buffers
        buffersToRemove.forEach { bufferId ->
            releaseRecordingBuffer(bufferId)
        }
        
        // Trim buffer pool if it's too large
        while (bufferPool.size > MAX_BUFFER_POOL_SIZE) {
            bufferPool.removeAt(0)
        }
        
        if (buffersToRemove.isNotEmpty()) {
            Log.d(TAG, "Periodic cleanup removed ${buffersToRemove.size} unused buffers")
            
            val currentState = _memoryState.value
            _memoryState.value = currentState.copy(
                lastCleanupTime = currentTime
            )
        }
    }

    private fun performAutomaticCleanup() {
        val currentTime = System.currentTimeMillis()
        val buffersToRemove = mutableListOf<String>()
        
        // More aggressive cleanup under memory pressure
        bufferUsageTracker.forEach { (bufferId, lastUsed) ->
            if (currentTime - lastUsed > UNUSED_BUFFER_TIMEOUT_MS / 2) { // Half the normal timeout
                buffersToRemove.add(bufferId)
            }
        }
        
        buffersToRemove.forEach { bufferId ->
            releaseRecordingBuffer(bufferId)
        }
        
        // Clear buffer pool under memory pressure
        bufferPool.clear()
        
        // Force garbage collection
        System.gc()
        
        Log.d(TAG, "Automatic cleanup removed ${buffersToRemove.size} buffers due to memory pressure")
        
        val currentState = _memoryState.value
        _memoryState.value = currentState.copy(
            lastCleanupTime = currentTime
        )
    }

    private fun performEmergencyCleanup() {
        // Emergency cleanup - remove all unused buffers immediately
        val buffersToRemove = bufferUsageTracker.keys.toList()
        buffersToRemove.forEach { bufferId ->
            releaseRecordingBuffer(bufferId)
        }
        
        // Clear all pooled buffers
        bufferPool.clear()
        
        // Force garbage collection
        System.gc()
        
        Log.w(TAG, "Emergency cleanup removed ${buffersToRemove.size} buffers")
        
        val currentState = _memoryState.value
        _memoryState.value = currentState.copy(
            lastCleanupTime = System.currentTimeMillis()
        )
    }
}

/**
 * Recording buffer data structure
 */
data class RecordingBuffer(
    val id: String,
    val sizeBytes: Long,
    val sampleRate: Int,
    val channels: Int,
    val allocatedTime: Long,
    val data: ByteArray? = null // Actual buffer data would be managed by native code
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as RecordingBuffer
        
        if (id != other.id) return false
        if (sizeBytes != other.sizeBytes) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (allocatedTime != other.allocatedTime) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + allocatedTime.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Recording memory state
 */
data class RecordingMemoryState(
    val totalMemoryUsage: Long = 0L,
    val recordingBufferMemory: Long = 0L,
    val memoryPressure: Float = 0f, // 0.0 to 1.0
    val activeBufferCount: Int = 0,
    val pooledBufferCount: Int = 0,
    val unusedBufferCount: Int = 0,
    val lastCleanupTime: Long = 0L
)