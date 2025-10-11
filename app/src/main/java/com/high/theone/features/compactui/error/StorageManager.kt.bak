package com.high.theone.features.compactui.error

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage management system for handling storage errors and space management
 * Requirements: 5.5 (storage error handling with space management guidance)
 */
@Singleton
class StorageManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "StorageManager"
        private const val MIN_RECORDING_SPACE_MB = 50L
        private const val RECOMMENDED_FREE_SPACE_MB = 200L
        private const val BYTES_PER_MB = 1024 * 1024L
        private const val TEMP_CLEANUP_THRESHOLD_MB = 100L
    }
    
    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()
    
    private val _cleanupProgress = MutableStateFlow(0f)
    val cleanupProgress: StateFlow<Float> = _cleanupProgress.asStateFlow()
    
    private val _isCleaningUp = MutableStateFlow(false)
    val isCleaningUp: StateFlow<Boolean> = _isCleaningUp.asStateFlow()
    
    /**
     * Update storage information
     */
    fun updateStorageInfo() {
        try {
            val stat = StatFs(context.filesDir.path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            
            val info = StorageInfo(
                totalSpaceMB = totalBytes / BYTES_PER_MB,
                availableSpaceMB = availableBytes / BYTES_PER_MB,
                usedSpaceMB = usedBytes / BYTES_PER_MB,
                canRecord = availableBytes / BYTES_PER_MB >= MIN_RECORDING_SPACE_MB,
                hasRecommendedSpace = availableBytes / BYTES_PER_MB >= RECOMMENDED_FREE_SPACE_MB,
                lastUpdated = System.currentTimeMillis()
            )
            
            _storageInfo.value = info
            Log.d(TAG, "Storage info updated: ${info.availableSpaceMB}MB available")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating storage info", e)
            _storageInfo.value = StorageInfo(hasError = true, errorMessage = e.message)
        }
    }
    
    /**
     * Check if there's enough space for recording
     */
    fun hasEnoughSpaceForRecording(): Boolean {
        updateStorageInfo()
        return _storageInfo.value.canRecord
    }
    
    /**
     * Get estimated recording time based on available space
     */
    fun getEstimatedRecordingTime(): Long {
        val availableMB = _storageInfo.value.availableSpaceMB
        // Assuming 44.1kHz, 16-bit mono = ~2.6MB per minute
        return if (availableMB > MIN_RECORDING_SPACE_MB) {
            ((availableMB - MIN_RECORDING_SPACE_MB) / 2.6).toLong()
        } else {
            0L
        }
    }
    
    /**
     * Clean up temporary files and cache
     */
    suspend fun cleanupStorage(): CleanupResult {
        _isCleaningUp.value = true
        _cleanupProgress.value = 0f
        
        var totalFreed = 0L
        val cleanupSteps = listOf(
            "Cleaning temporary recordings...",
            "Clearing audio cache...",
            "Removing old samples...",
            "Finalizing cleanup..."
        )
        
        try {
            // Step 1: Clean temporary recording files
            _cleanupProgress.value = 0.25f
            val tempFreed = cleanupTempRecordings()
            totalFreed += tempFreed
            Log.d(TAG, "Cleaned ${tempFreed / BYTES_PER_MB}MB from temp recordings")
            
            // Step 2: Clear audio cache
            _cleanupProgress.value = 0.5f
            val cacheFreed = cleanupAudioCache()
            totalFreed += cacheFreed
            Log.d(TAG, "Cleaned ${cacheFreed / BYTES_PER_MB}MB from audio cache")
            
            // Step 3: Remove old unused samples (if any)
            _cleanupProgress.value = 0.75f
            val samplesFreed = cleanupOldSamples()
            totalFreed += samplesFreed
            Log.d(TAG, "Cleaned ${samplesFreed / BYTES_PER_MB}MB from old samples")
            
            // Step 4: Update storage info
            _cleanupProgress.value = 1f
            updateStorageInfo()
            
            _isCleaningUp.value = false
            
            return CleanupResult(
                success = true,
                freedSpaceMB = totalFreed / BYTES_PER_MB,
                message = "Cleanup completed successfully. Freed ${totalFreed / BYTES_PER_MB}MB of space."
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage cleanup", e)
            _isCleaningUp.value = false
            return CleanupResult(
                success = false,
                freedSpaceMB = totalFreed / BYTES_PER_MB,
                message = "Cleanup failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get storage management recommendations
     */
    fun getStorageRecommendations(): List<String> {
        val info = _storageInfo.value
        val recommendations = mutableListOf<String>()
        
        if (!info.canRecord) {
            recommendations.add("⚠️ Insufficient space for recording (${info.availableSpaceMB}MB available, ${MIN_RECORDING_SPACE_MB}MB required)")
            recommendations.add("Delete unused files or move them to cloud storage")
        }
        
        if (!info.hasRecommendedSpace) {
            recommendations.add("💡 Consider freeing up more space for optimal performance")
            recommendations.add("Recommended: ${RECOMMENDED_FREE_SPACE_MB}MB free space")
        }
        
        if (info.availableSpaceMB < TEMP_CLEANUP_THRESHOLD_MB) {
            recommendations.add("🧹 Run storage cleanup to free temporary files")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("✅ Storage space is adequate for recording")
        }
        
        return recommendations
    }
    
    /**
     * Get user-friendly storage status message
     */
    fun getStorageStatusMessage(): String {
        val info = _storageInfo.value
        return when {
            info.hasError -> "Storage information unavailable: ${info.errorMessage}"
            !info.canRecord -> "Insufficient storage space (${info.availableSpaceMB}MB available)"
            !info.hasRecommendedSpace -> "Low storage space (${info.availableSpaceMB}MB available)"
            else -> "Storage space is adequate (${info.availableSpaceMB}MB available)"
        }
    }
    
    private fun cleanupTempRecordings(): Long {
        var freedBytes = 0L
        try {
            val tempDir = File(context.cacheDir, "temp_recordings")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("temp_recording_")) {
                        freedBytes += file.length()
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning temp recordings", e)
        }
        return freedBytes
    }
    
    private fun cleanupAudioCache(): Long {
        var freedBytes = 0L
        try {
            val cacheDir = File(context.cacheDir, "audio_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        freedBytes += file.length()
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning audio cache", e)
        }
        return freedBytes
    }
    
    private fun cleanupOldSamples(): Long {
        var freedBytes = 0L
        try {
            // This would need to integrate with the sample repository
            // to identify truly unused samples. For now, we'll just
            // clean up any orphaned sample files
            val samplesDir = File(context.filesDir, "samples")
            if (samplesDir.exists()) {
                val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days ago
                samplesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < cutoffTime && file.name.startsWith("temp_")) {
                        freedBytes += file.length()
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning old samples", e)
        }
        return freedBytes
    }
}

/**
 * Storage information data class
 */
data class StorageInfo(
    val totalSpaceMB: Long = 0L,
    val availableSpaceMB: Long = 0L,
    val usedSpaceMB: Long = 0L,
    val canRecord: Boolean = false,
    val hasRecommendedSpace: Boolean = false,
    val lastUpdated: Long = 0L,
    val hasError: Boolean = false,
    val errorMessage: String? = null
) {
    val usagePercentage: Float
        get() = if (totalSpaceMB > 0) (usedSpaceMB.toFloat() / totalSpaceMB.toFloat()) * 100f else 0f
}

/**
 * Cleanup operation result
 */
data class CleanupResult(
    val success: Boolean,
    val freedSpaceMB: Long,
    val message: String
)