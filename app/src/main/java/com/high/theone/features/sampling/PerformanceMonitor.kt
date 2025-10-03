package com.high.theone.features.sampling

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors performance metrics for responsive UI updates during audio operations.
 * Requirements: 3.3, 4.1, 4.2 (responsive UI updates)
 */
@Singleton
class PerformanceMonitor @Inject constructor() {
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MONITORING_INTERVAL_MS = 100L
        private const val METRICS_HISTORY_SIZE = 100
        private const val UI_FRAME_TARGET_MS = 16.67f // 60 FPS target
        private const val AUDIO_LATENCY_TARGET_MS = 20f // Low latency target
    }

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    private val frameTimeHistory = ConcurrentLinkedQueue<Float>()
    private val audioLatencyHistory = ConcurrentLinkedQueue<Float>()
    private val memoryUsageHistory = ConcurrentLinkedQueue<Long>()
    
    private var monitoringJob: Job? = null
    private var isMonitoring = false

    data class PerformanceMetrics(
        val averageFrameTime: Float = 0f,
        val frameDrops: Int = 0,
        val audioLatency: Float = 0f,
        val memoryUsage: Long = 0L,
        val cpuUsage: Float = 0f,
        val isPerformanceGood: Boolean = true,
        val warnings: List<PerformanceWarning> = emptyList()
    )

    data class PerformanceWarning(
        val type: WarningType,
        val message: String,
        val severity: Severity,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class WarningType {
        HIGH_FRAME_TIME,
        AUDIO_LATENCY,
        MEMORY_PRESSURE,
        CPU_OVERLOAD,
        VOICE_LIMIT_REACHED
    }

    enum class Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /**
     * Start performance monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                try {
                    updateMetrics()
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performance monitoring", e)
                }
            }
        }
        
        Log.d(TAG, "Performance monitoring started")
    }

    /**
     * Stop performance monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        
        Log.d(TAG, "Performance monitoring stopped")
    }

    /**
     * Record a UI frame time for monitoring.
     */
    fun recordFrameTime(frameTimeMs: Float) {
        frameTimeHistory.offer(frameTimeMs)
        
        // Keep history size manageable
        while (frameTimeHistory.size > METRICS_HISTORY_SIZE) {
            frameTimeHistory.poll()
        }
    }

    /**
     * Record audio latency measurement.
     */
    fun recordAudioLatency(latencyMs: Float) {
        audioLatencyHistory.offer(latencyMs)
        
        while (audioLatencyHistory.size > METRICS_HISTORY_SIZE) {
            audioLatencyHistory.poll()
        }
    }

    /**
     * Record memory usage measurement.
     */
    fun recordMemoryUsage(memoryBytes: Long) {
        memoryUsageHistory.offer(memoryBytes)
        
        while (memoryUsageHistory.size > METRICS_HISTORY_SIZE) {
            memoryUsageHistory.poll()
        }
    }

    /**
     * Get current performance status for UI adaptation.
     */
    fun getPerformanceStatus(): PerformanceStatus {
        val metrics = _performanceMetrics.value
        
        return when {
            metrics.warnings.any { it.severity == Severity.CRITICAL } -> PerformanceStatus.CRITICAL
            metrics.warnings.any { it.severity == Severity.WARNING } -> PerformanceStatus.DEGRADED
            !metrics.isPerformanceGood -> PerformanceStatus.POOR
            else -> PerformanceStatus.GOOD
        }
    }

    /**
     * Check if UI should reduce complexity for better performance.
     */
    fun shouldReduceUIComplexity(): Boolean {
        val status = getPerformanceStatus()
        return status == PerformanceStatus.CRITICAL || status == PerformanceStatus.POOR
    }

    /**
     * Check if audio operations should be throttled.
     */
    fun shouldThrottleAudioOperations(): Boolean {
        val metrics = _performanceMetrics.value
        return metrics.audioLatency > AUDIO_LATENCY_TARGET_MS * 2 || 
               metrics.warnings.any { it.type == WarningType.CPU_OVERLOAD }
    }

    /**
     * Get performance recommendations for optimization.
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val metrics = _performanceMetrics.value
        
        if (metrics.averageFrameTime > UI_FRAME_TARGET_MS * 1.5f) {
            recommendations.add("Reduce UI animation complexity")
        }
        
        if (metrics.audioLatency > AUDIO_LATENCY_TARGET_MS * 1.5f) {
            recommendations.add("Reduce audio buffer size or sample count")
        }
        
        if (metrics.memoryUsage > Runtime.getRuntime().maxMemory() * 0.8) {
            recommendations.add("Clear unused samples from cache")
        }
        
        return recommendations
    }

    private suspend fun updateMetrics() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Calculate average frame time
            val avgFrameTime = if (frameTimeHistory.isNotEmpty()) {
                frameTimeHistory.average().toFloat()
            } else 0f
            
            // Calculate frame drops
            val frameDrops = frameTimeHistory.count { it > UI_FRAME_TARGET_MS * 2 }
            
            // Calculate average audio latency
            val avgAudioLatency = if (audioLatencyHistory.isNotEmpty()) {
                audioLatencyHistory.average().toFloat()
            } else 0f
            
            // Get current memory usage
            val runtime = Runtime.getRuntime()
            val memoryUsage = runtime.totalMemory() - runtime.freeMemory()
            
            // Estimate CPU usage (simplified)
            val cpuUsage = estimateCpuUsage()
            
            // Generate warnings
            val warnings = generateWarnings(avgFrameTime, avgAudioLatency, memoryUsage, cpuUsage)
            
            // Determine overall performance status
            val isPerformanceGood = avgFrameTime <= UI_FRAME_TARGET_MS * 1.2f &&
                                   avgAudioLatency <= AUDIO_LATENCY_TARGET_MS * 1.2f &&
                                   warnings.none { it.severity == Severity.CRITICAL }
            
            // Update metrics
            val newMetrics = PerformanceMetrics(
                averageFrameTime = avgFrameTime,
                frameDrops = frameDrops,
                audioLatency = avgAudioLatency,
                memoryUsage = memoryUsage,
                cpuUsage = cpuUsage,
                isPerformanceGood = isPerformanceGood,
                warnings = warnings
            )
            
            _performanceMetrics.value = newMetrics
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating performance metrics", e)
        }
    }

    private fun generateWarnings(
        frameTime: Float,
        audioLatency: Float,
        memoryUsage: Long,
        cpuUsage: Float
    ): List<PerformanceWarning> {
        val warnings = mutableListOf<PerformanceWarning>()
        
        // Frame time warnings
        when {
            frameTime > UI_FRAME_TARGET_MS * 3 -> {
                warnings.add(PerformanceWarning(
                    WarningType.HIGH_FRAME_TIME,
                    "Severe frame drops detected (${frameTime.toInt()}ms)",
                    Severity.CRITICAL
                ))
            }
            frameTime > UI_FRAME_TARGET_MS * 2 -> {
                warnings.add(PerformanceWarning(
                    WarningType.HIGH_FRAME_TIME,
                    "High frame time detected (${frameTime.toInt()}ms)",
                    Severity.WARNING
                ))
            }
        }
        
        // Audio latency warnings
        when {
            audioLatency > AUDIO_LATENCY_TARGET_MS * 3 -> {
                warnings.add(PerformanceWarning(
                    WarningType.AUDIO_LATENCY,
                    "Critical audio latency (${audioLatency.toInt()}ms)",
                    Severity.CRITICAL
                ))
            }
            audioLatency > AUDIO_LATENCY_TARGET_MS * 2 -> {
                warnings.add(PerformanceWarning(
                    WarningType.AUDIO_LATENCY,
                    "High audio latency (${audioLatency.toInt()}ms)",
                    Severity.WARNING
                ))
            }
        }
        
        // Memory warnings
        val maxMemory = Runtime.getRuntime().maxMemory()
        val memoryPercent = (memoryUsage.toFloat() / maxMemory * 100).toInt()
        
        when {
            memoryPercent > 90 -> {
                warnings.add(PerformanceWarning(
                    WarningType.MEMORY_PRESSURE,
                    "Critical memory usage ($memoryPercent%)",
                    Severity.CRITICAL
                ))
            }
            memoryPercent > 80 -> {
                warnings.add(PerformanceWarning(
                    WarningType.MEMORY_PRESSURE,
                    "High memory usage ($memoryPercent%)",
                    Severity.WARNING
                ))
            }
        }
        
        // CPU warnings
        when {
            cpuUsage > 90 -> {
                warnings.add(PerformanceWarning(
                    WarningType.CPU_OVERLOAD,
                    "Critical CPU usage (${cpuUsage.toInt()}%)",
                    Severity.CRITICAL
                ))
            }
            cpuUsage > 80 -> {
                warnings.add(PerformanceWarning(
                    WarningType.CPU_OVERLOAD,
                    "High CPU usage (${cpuUsage.toInt()}%)",
                    Severity.WARNING
                ))
            }
        }
        
        return warnings
    }

    private fun estimateCpuUsage(): Float {
        // Simplified CPU usage estimation
        // In a real implementation, this would use more sophisticated methods
        val runtime = Runtime.getRuntime()
        val processors = runtime.availableProcessors()
        
        // Use frame time as a proxy for CPU usage
        val avgFrameTime = if (frameTimeHistory.isNotEmpty()) {
            frameTimeHistory.average().toFloat()
        } else UI_FRAME_TARGET_MS
        
        return ((avgFrameTime / UI_FRAME_TARGET_MS) * 50f).coerceIn(0f, 100f)
    }

    enum class PerformanceStatus {
        GOOD,
        DEGRADED,
        POOR,
        CRITICAL
    }
}