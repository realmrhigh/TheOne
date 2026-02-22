package com.high.theone.features.compactui.performance

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frame rate monitoring system specifically for recording operations
 * 
 * Implements requirement 6.2: Frame rate monitoring and automatic optimization
 * when frame rate drops below 50fps during recording operations
 */
@Singleton
class RecordingFrameRateMonitor @Inject constructor() {
    companion object {
        private const val TAG = "RecordingFrameRateMonitor"
        private const val TARGET_FRAME_RATE = 60f
        private const val CRITICAL_FRAME_RATE = 50f
        private const val SEVERE_FRAME_RATE = 30f
        private const val FRAME_TIME_BUFFER_SIZE = 120 // 2 seconds at 60fps
        private const val MONITORING_INTERVAL_MS = 16L // ~60fps monitoring
    }

    private val _frameRateState = MutableStateFlow(FrameRateState())
    val frameRateState: StateFlow<FrameRateState> = _frameRateState.asStateFlow()

    private val _optimizationTriggers = MutableStateFlow<List<FrameRateOptimizationTrigger>>(emptyList())
    val optimizationTriggers: StateFlow<List<FrameRateOptimizationTrigger>> = _optimizationTriggers.asStateFlow()

    private val frameTimeBuffer = mutableListOf<Long>()
    private val frameStartTime = AtomicLong(0L)
    private val lastFrameTime = AtomicLong(0L)
    
    private var monitoringJob: Job? = null
    private var isRecordingActive = false
    private var recordingStartTime = 0L
    private var frameDropCount = 0
    private var consecutiveLowFrameCount = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Start frame rate monitoring during recording
     */
    fun startRecordingFrameRateMonitoring(coroutineScope: CoroutineScope) {
        isRecordingActive = true
        recordingStartTime = System.currentTimeMillis()
        frameDropCount = 0
        consecutiveLowFrameCount = 0
        
        monitoringJob = coroutineScope.launch {
            while (isRecordingActive) {
                updateFrameRateMetrics()
                checkFrameRateOptimizations()
                delay(MONITORING_INTERVAL_MS)
            }
        }
        
        Log.d(TAG, "Recording frame rate monitoring started")
    }

    /**
     * Stop frame rate monitoring
     */
    fun stopRecordingFrameRateMonitoring() {
        isRecordingActive = false
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Reset state
        frameTimeBuffer.clear()
        frameStartTime.set(0L)
        lastFrameTime.set(0L)
        
        Log.d(TAG, "Recording frame rate monitoring stopped")
    }

    /**
     * Record frame start time (call at beginning of frame)
     */
    fun recordFrameStart() {
        if (!isRecordingActive) return
        
        val currentTime = System.nanoTime()
        frameStartTime.set(currentTime)
    }

    /**
     * Record frame end time (call at end of frame)
     */
    fun recordFrameEnd() {
        if (!isRecordingActive) return
        
        val currentTime = System.nanoTime()
        val startTime = frameStartTime.get()
        
        if (startTime > 0) {
            val frameTime = currentTime - startTime
            recordFrameTime(frameTime)
        }
    }

    /**
     * Record a complete frame time in nanoseconds
     */
    fun recordFrameTime(frameTimeNanos: Long) {
        if (!isRecordingActive) return
        
        synchronized(frameTimeBuffer) {
            frameTimeBuffer.add(frameTimeNanos)
            
            // Keep buffer size manageable
            if (frameTimeBuffer.size > FRAME_TIME_BUFFER_SIZE) {
                frameTimeBuffer.removeAt(0)
            }
        }
        
        // Check for frame drops
        val frameTimeMs = frameTimeNanos / 1_000_000f
        if (frameTimeMs > 16.67f * 1.5f) { // 1.5x target frame time
            frameDropCount++
        }
        
        lastFrameTime.set(System.currentTimeMillis())
    }

    /**
     * Get current frame rate during recording
     */
    fun getCurrentFrameRate(): Float {
        synchronized(frameTimeBuffer) {
            if (frameTimeBuffer.size < 2) return TARGET_FRAME_RATE
            
            val totalTimeNanos = frameTimeBuffer.takeLast(60).let { recentFrames ->
                if (recentFrames.size < 2) return TARGET_FRAME_RATE
                recentFrames.sum()
            }
            
            val avgFrameTimeNanos = totalTimeNanos / frameTimeBuffer.size.coerceAtMost(60)
            return if (avgFrameTimeNanos > 0) {
                1_000_000_000f / avgFrameTimeNanos
            } else TARGET_FRAME_RATE
        }
    }

    /**
     * Get frame drop percentage
     */
    fun getFrameDropPercentage(): Float {
        synchronized(frameTimeBuffer) {
            if (frameTimeBuffer.isEmpty()) return 0f
            
            val droppedFrames = frameTimeBuffer.count { frameTimeNanos ->
                val frameTimeMs = frameTimeNanos / 1_000_000f
                frameTimeMs > 16.67f * 1.5f
            }
            
            return (droppedFrames.toFloat() / frameTimeBuffer.size) * 100f
        }
    }

    /**
     * Check if frame rate optimization is needed
     */
    fun shouldOptimizeFrameRate(): Boolean {
        val currentFrameRate = getCurrentFrameRate()
        return currentFrameRate < CRITICAL_FRAME_RATE
    }

    /**
     * Get frame rate optimization recommendations
     */
    fun getFrameRateOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val currentFrameRate = getCurrentFrameRate()
        val frameDropPercentage = getFrameDropPercentage()
        
        when {
            currentFrameRate < SEVERE_FRAME_RATE -> {
                recommendations.add("Critical frame rate - enable simplified recording mode")
                recommendations.add("Disable all animations and visual effects")
                recommendations.add("Close all non-essential panels")
                recommendations.add("Reduce recording quality if possible")
            }
            currentFrameRate < CRITICAL_FRAME_RATE -> {
                recommendations.add("Low frame rate detected - reduce visual effects")
                recommendations.add("Close unnecessary UI panels")
                recommendations.add("Disable background animations")
            }
            currentFrameRate < TARGET_FRAME_RATE -> {
                recommendations.add("Suboptimal frame rate - consider reducing UI complexity")
                recommendations.add("Minimize panel updates during recording")
            }
        }
        
        if (frameDropPercentage > 10f) {
            recommendations.add("High frame drop rate (${frameDropPercentage.toInt()}%) - optimize UI updates")
        }
        
        return recommendations
    }

    private suspend fun updateFrameRateMetrics() {
        val currentTime = System.currentTimeMillis()
        val currentFrameRate = getCurrentFrameRate()
        val frameDropPercentage = getFrameDropPercentage()
        val recordingDuration = currentTime - recordingStartTime
        
        // Track consecutive low frame rate periods
        if (currentFrameRate < CRITICAL_FRAME_RATE) {
            consecutiveLowFrameCount++
        } else {
            consecutiveLowFrameCount = 0
        }
        
        val newState = FrameRateState(
            currentFrameRate = currentFrameRate,
            averageFrameRate = calculateAverageFrameRate(),
            frameDropPercentage = frameDropPercentage,
            totalFrameDrops = frameDropCount,
            consecutiveLowFrameCount = consecutiveLowFrameCount,
            recordingDuration = recordingDuration,
            lastUpdateTime = currentTime,
            isOptimizationNeeded = shouldOptimizeFrameRate()
        )
        
        _frameRateState.value = newState
    }

    private suspend fun checkFrameRateOptimizations() {
        val currentState = _frameRateState.value
        val triggers = mutableListOf<FrameRateOptimizationTrigger>()
        
        // Check for critical frame rate
        if (currentState.currentFrameRate < SEVERE_FRAME_RATE) {
            triggers.add(
                FrameRateOptimizationTrigger(
                    type = FrameRateOptimizationType.EMERGENCY_OPTIMIZATION,
                    severity = OptimizationSeverity.CRITICAL,
                    message = "Critical frame rate (${currentState.currentFrameRate.toInt()}fps) - emergency optimization needed",
                    autoApply = true,
                    estimatedImprovement = "10-20 fps"
                )
            )
        } else if (currentState.currentFrameRate < CRITICAL_FRAME_RATE) {
            triggers.add(
                FrameRateOptimizationTrigger(
                    type = FrameRateOptimizationType.REDUCE_VISUAL_EFFECTS,
                    severity = OptimizationSeverity.HIGH,
                    message = "Low frame rate (${currentState.currentFrameRate.toInt()}fps) - reduce visual effects",
                    autoApply = true,
                    estimatedImprovement = "5-10 fps"
                )
            )
        }
        
        // Check for sustained low frame rate
        if (currentState.consecutiveLowFrameCount > 60) { // 1 second at 60fps
            triggers.add(
                FrameRateOptimizationTrigger(
                    type = FrameRateOptimizationType.SUSTAINED_LOW_PERFORMANCE,
                    severity = OptimizationSeverity.HIGH,
                    message = "Sustained low frame rate - consider reducing UI complexity",
                    autoApply = false,
                    estimatedImprovement = "Smoother recording experience"
                )
            )
        }
        
        // Check for high frame drop rate
        if (currentState.frameDropPercentage > 15f) {
            triggers.add(
                FrameRateOptimizationTrigger(
                    type = FrameRateOptimizationType.HIGH_FRAME_DROPS,
                    severity = OptimizationSeverity.MEDIUM,
                    message = "High frame drop rate (${currentState.frameDropPercentage.toInt()}%) - optimize UI updates",
                    autoApply = true,
                    estimatedImprovement = "Reduced frame drops"
                )
            )
        }
        
        _optimizationTriggers.value = triggers
    }

    private fun calculateAverageFrameRate(): Float {
        synchronized(frameTimeBuffer) {
            if (frameTimeBuffer.isEmpty()) return TARGET_FRAME_RATE
            
            val avgFrameTimeNanos = frameTimeBuffer.average()
            return if (avgFrameTimeNanos > 0) {
                1_000_000_000f / avgFrameTimeNanos.toFloat()
            } else TARGET_FRAME_RATE
        }
    }
}

/**
 * Frame rate monitoring state
 */
@Stable
data class FrameRateState(
    val currentFrameRate: Float = 60f,
    val averageFrameRate: Float = 60f,
    val frameDropPercentage: Float = 0f,
    val totalFrameDrops: Int = 0,
    val consecutiveLowFrameCount: Int = 0,
    val recordingDuration: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val isOptimizationNeeded: Boolean = false
)

/**
 * Frame rate optimization trigger
 */
@Stable
data class FrameRateOptimizationTrigger(
    val type: FrameRateOptimizationType,
    val severity: OptimizationSeverity,
    val message: String,
    val autoApply: Boolean,
    val estimatedImprovement: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of frame rate optimizations
 */
enum class FrameRateOptimizationType {
    REDUCE_VISUAL_EFFECTS,
    EMERGENCY_OPTIMIZATION,
    SUSTAINED_LOW_PERFORMANCE,
    HIGH_FRAME_DROPS
}

/**
 * Optimization severity levels
 */
enum class OptimizationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Composable hook for frame rate monitoring integration
 */
@Composable
fun rememberRecordingFrameRateMonitor(
    frameRateMonitor: RecordingFrameRateMonitor,
    isRecording: Boolean
): RecordingFrameRateMonitor {
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(isRecording) {
        if (isRecording) {
            frameRateMonitor.startRecordingFrameRateMonitoring(coroutineScope)
        } else {
            frameRateMonitor.stopRecordingFrameRateMonitoring()
        }
    }
    
    // Record frame times during composition
    DisposableEffect(Unit) {
        val frameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (isRecording) {
                    frameRateMonitor.recordFrameTime(frameTimeNanos)
                    android.view.Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        
        if (isRecording) {
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        
        onDispose {
            android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }
    
    return frameRateMonitor
}