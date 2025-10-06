package com.high.theone.ui.performance

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.high.theone.model.PerformanceMetrics
import com.high.theone.model.PerformanceMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance monitoring system for the compact UI
 * Tracks frame rate, memory usage, and other performance metrics
 */
@Singleton
class PerformanceMonitor @Inject constructor() {
    
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    private val _performanceMode = MutableStateFlow(PerformanceMode.BALANCED)
    val performanceMode: StateFlow<PerformanceMode> = _performanceMode.asStateFlow()
    
    private var monitoringJob: Job? = null
    private var frameTimeTracker: FrameTimeTracker? = null
    
    // Performance thresholds
    private val targetFrameRate = 60f
    private val lowFrameRateThreshold = 45f
    private val highMemoryThreshold = 100 * 1024 * 1024L // 100MB
    private val criticalMemoryThreshold = 200 * 1024 * 1024L // 200MB
    
    /**
     * Start performance monitoring
     */
    fun startMonitoring(coroutineScope: CoroutineScope) {
        stopMonitoring()
        
        frameTimeTracker = FrameTimeTracker()
        
        monitoringJob = coroutineScope.launch {
            while (isActive) {
                updateMetrics()
                delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        frameTimeTracker = null
    }
    
    /**
     * Update performance metrics
     */
    private suspend fun updateMetrics() {
        val currentTime = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        
        val frameRate = frameTimeTracker?.getCurrentFrameRate() ?: targetFrameRate
        val memoryUsage = runtime.totalMemory() - runtime.freeMemory()
        val droppedFrames = frameTimeTracker?.getDroppedFrameCount() ?: 0
        
        val newMetrics = PerformanceMetrics(
            frameRate = frameRate,
            memoryUsage = memoryUsage,
            cpuUsage = calculateCpuUsage(),
            audioLatency = 0f, // Will be updated by audio engine
            droppedFrames = droppedFrames,
            lastUpdateTime = currentTime
        )
        
        _performanceMetrics.value = newMetrics
        
        // Auto-adjust performance mode based on metrics
        adjustPerformanceMode(newMetrics)
    }
    
    /**
     * Calculate CPU usage (simplified estimation)
     */
    private fun calculateCpuUsage(): Float {
        // This is a simplified CPU usage calculation
        // In a real implementation, you might use more sophisticated methods
        val runtime = Runtime.getRuntime()
        val processors = runtime.availableProcessors()
        return minOf(100f, processors * 10f) // Placeholder calculation
    }
    
    /**
     * Automatically adjust performance mode based on current metrics
     */
    private fun adjustPerformanceMode(metrics: PerformanceMetrics) {
        val currentMode = _performanceMode.value
        
        when {
            // Switch to battery saver if performance is poor
            metrics.frameRate < lowFrameRateThreshold || 
            metrics.memoryUsage > criticalMemoryThreshold -> {
                if (currentMode != PerformanceMode.BATTERY_SAVER) {
                    _performanceMode.value = PerformanceMode.BATTERY_SAVER
                }
            }
            
            // Switch to balanced if memory usage is high but frame rate is ok
            metrics.memoryUsage > highMemoryThreshold && 
            metrics.frameRate >= lowFrameRateThreshold -> {
                if (currentMode == PerformanceMode.HIGH_PERFORMANCE) {
                    _performanceMode.value = PerformanceMode.BALANCED
                }
            }
            
            // Switch to high performance if metrics are good
            metrics.frameRate >= targetFrameRate && 
            metrics.memoryUsage < highMemoryThreshold -> {
                if (currentMode == PerformanceMode.BATTERY_SAVER) {
                    _performanceMode.value = PerformanceMode.BALANCED
                }
            }
        }
    }
    
    /**
     * Manually set performance mode
     */
    fun setPerformanceMode(mode: PerformanceMode) {
        _performanceMode.value = mode
    }
    
    /**
     * Record frame time for frame rate calculation
     */
    fun recordFrameTime() {
        frameTimeTracker?.recordFrame()
    }
    
    /**
     * Update audio latency from audio engine
     */
    fun updateAudioLatency(latency: Float) {
        _performanceMetrics.value = _performanceMetrics.value.copy(audioLatency = latency)
    }
    
    /**
     * Get performance recommendations based on current metrics
     */
    fun getPerformanceRecommendations(): List<PerformanceRecommendation> {
        val metrics = _performanceMetrics.value
        val recommendations = mutableListOf<PerformanceRecommendation>()
        
        if (metrics.frameRate < lowFrameRateThreshold) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.FRAME_RATE,
                    message = "Low frame rate detected. Consider reducing visual effects.",
                    severity = RecommendationSeverity.HIGH
                )
            )
        }
        
        if (metrics.memoryUsage > highMemoryThreshold) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.MEMORY,
                    message = "High memory usage. Consider closing unused panels.",
                    severity = if (metrics.memoryUsage > criticalMemoryThreshold) 
                        RecommendationSeverity.CRITICAL else RecommendationSeverity.MEDIUM
                )
            )
        }
        
        if (metrics.droppedFrames > 10) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.DROPPED_FRAMES,
                    message = "Dropped frames detected. UI may feel laggy.",
                    severity = RecommendationSeverity.MEDIUM
                )
            )
        }
        
        return recommendations
    }
}

/**
 * Frame time tracking for frame rate calculation
 */
private class FrameTimeTracker {
    private val frameTimes = mutableListOf<Long>()
    private var droppedFrames = 0
    private val maxFrameHistory = 60 // Keep last 60 frames
    
    fun recordFrame() {
        val currentTime = System.nanoTime()
        frameTimes.add(currentTime)
        
        // Remove old frame times
        if (frameTimes.size > maxFrameHistory) {
            frameTimes.removeAt(0)
        }
        
        // Check for dropped frames (frame time > 16.67ms for 60fps)
        if (frameTimes.size >= 2) {
            val frameTime = (currentTime - frameTimes[frameTimes.size - 2]) / 1_000_000f
            if (frameTime > 16.67f * 1.5f) { // 1.5x threshold for dropped frame
                droppedFrames++
            }
        }
    }
    
    fun getCurrentFrameRate(): Float {
        if (frameTimes.size < 2) return 60f
        
        val totalTime = (frameTimes.last() - frameTimes.first()) / 1_000_000_000.0
        return ((frameTimes.size - 1) / totalTime).toFloat()
    }
    
    fun getDroppedFrameCount(): Int = droppedFrames
}

/**
 * Performance recommendation data
 */
data class PerformanceRecommendation(
    val type: RecommendationType,
    val message: String,
    val severity: RecommendationSeverity
)

enum class RecommendationType {
    FRAME_RATE,
    MEMORY,
    DROPPED_FRAMES,
    AUDIO_LATENCY
}

enum class RecommendationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Composable for integrating performance monitoring with lifecycle
 */
@Composable
fun rememberPerformanceMonitor(
    performanceMonitor: PerformanceMonitor
): PerformanceMonitor {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    performanceMonitor.startMonitoring(coroutineScope)
                }
                Lifecycle.Event.ON_STOP -> {
                    performanceMonitor.stopMonitoring()
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            performanceMonitor.stopMonitoring()
        }
    }
    
    return performanceMonitor
}