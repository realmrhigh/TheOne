package com.high.theone.midi.performance

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors MIDI system performance including latency, throughput, and resource usage.
 * Provides real-time metrics and optimization recommendations.
 * 
 * Requirements: 7.4, 7.5
 */
@Singleton
class MidiPerformanceMonitor @Inject constructor() {
    
    companion object {
        private const val TAG = "MidiPerformanceMonitor"
        private const val METRICS_COLLECTION_INTERVAL_MS = 1000L
        private const val LATENCY_HISTORY_SIZE = 100
        private const val THROUGHPUT_WINDOW_MS = 5000L
        private const val WARNING_LATENCY_MS = 10.0f
        private const val CRITICAL_LATENCY_MS = 20.0f
        private const val MAX_THROUGHPUT_MESSAGES_PER_SEC = 1000
    }
    
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Performance metrics
    private val _currentMetrics = MutableStateFlow(MidiPerformanceMetrics())
    val currentMetrics: StateFlow<MidiPerformanceMetrics> = _currentMetrics.asStateFlow()
    
    private val _performanceAlerts = MutableSharedFlow<MidiPerformanceAlert>()
    val performanceAlerts: SharedFlow<MidiPerformanceAlert> = _performanceAlerts.asSharedFlow()
    
    // Latency tracking
    private val latencyHistory = ConcurrentLinkedQueue<LatencyMeasurement>()
    private val inputMessageCount = AtomicLong(0)
    private val outputMessageCount = AtomicLong(0)
    private val droppedMessageCount = AtomicLong(0)
    
    // Throughput tracking
    private val messageTimestamps = ConcurrentLinkedQueue<Long>()
    
    // Resource usage tracking
    private var lastCpuMeasurement = 0L
    private var lastMemoryMeasurement = 0L
    
    // Performance optimization state
    private val _optimizationRecommendations = MutableStateFlow<List<MidiOptimizationRecommendation>>(emptyList())
    val optimizationRecommendations: StateFlow<List<MidiOptimizationRecommendation>> = _optimizationRecommendations.asStateFlow()
    
    private var isMonitoring = false
    
    /**
     * Start performance monitoring
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        Log.i(TAG, "Starting MIDI performance monitoring")
        isMonitoring = true
        
        // Start metrics collection
        monitorScope.launch {
            while (isMonitoring) {
                try {
                    collectMetrics()
                    analyzePerformance()
                    delay(METRICS_COLLECTION_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in performance monitoring", e)
                }
            }
        }
    }
    
    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping MIDI performance monitoring")
        isMonitoring = false
        monitorScope.cancel()
    }
    
    /**
     * Record MIDI input latency measurement
     */
    fun recordInputLatency(latencyMs: Float, deviceId: String) {
        val measurement = LatencyMeasurement(
            latencyMs = latencyMs,
            timestamp = SystemClock.elapsedRealtime(),
            deviceId = deviceId,
            type = LatencyType.INPUT
        )
        
        addLatencyMeasurement(measurement)
        inputMessageCount.incrementAndGet()
        
        // Check for latency alerts
        if (latencyMs > CRITICAL_LATENCY_MS) {
            emitAlert(MidiPerformanceAlert.CriticalLatency(latencyMs, deviceId))
        } else if (latencyMs > WARNING_LATENCY_MS) {
            emitAlert(MidiPerformanceAlert.HighLatency(latencyMs, deviceId))
        }
    }
    
    /**
     * Record MIDI output latency measurement
     */
    fun recordOutputLatency(latencyMs: Float, deviceId: String) {
        val measurement = LatencyMeasurement(
            latencyMs = latencyMs,
            timestamp = SystemClock.elapsedRealtime(),
            deviceId = deviceId,
            type = LatencyType.OUTPUT
        )
        
        addLatencyMeasurement(measurement)
        outputMessageCount.incrementAndGet()
    }
    
    /**
     * Record dropped message
     */
    fun recordDroppedMessage(reason: String, deviceId: String) {
        droppedMessageCount.incrementAndGet()
        Log.w(TAG, "MIDI message dropped: $reason (device: $deviceId)")
        
        emitAlert(MidiPerformanceAlert.DroppedMessages(reason, deviceId))
    }
    
    /**
     * Record message throughput
     */
    fun recordMessageThroughput() {
        val now = SystemClock.elapsedRealtime()
        messageTimestamps.offer(now)
        
        // Clean old timestamps
        cleanOldTimestamps(now)
        
        // Check throughput limits
        val currentThroughput = calculateCurrentThroughput()
        if (currentThroughput > MAX_THROUGHPUT_MESSAGES_PER_SEC) {
            emitAlert(MidiPerformanceAlert.HighThroughput(currentThroughput))
        }
    }
    
    /**
     * Get current performance summary
     */
    fun getPerformanceSummary(): MidiPerformanceSummary {
        val metrics = _currentMetrics.value
        val recommendations = _optimizationRecommendations.value
        
        return MidiPerformanceSummary(
            averageLatency = metrics.averageLatency,
            maxLatency = metrics.maxLatency,
            throughput = metrics.throughput,
            droppedMessages = metrics.droppedMessages,
            cpuUsage = metrics.cpuUsage,
            memoryUsage = metrics.memoryUsage,
            performanceRating = calculatePerformanceRating(metrics),
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Reset performance statistics
     */
    fun resetStatistics() {
        Log.i(TAG, "Resetting MIDI performance statistics")
        
        latencyHistory.clear()
        messageTimestamps.clear()
        inputMessageCount.set(0)
        outputMessageCount.set(0)
        droppedMessageCount.set(0)
        
        _currentMetrics.value = MidiPerformanceMetrics()
        _optimizationRecommendations.value = emptyList()
    }
    
    // Private helper methods
    
    private fun addLatencyMeasurement(measurement: LatencyMeasurement) {
        latencyHistory.offer(measurement)
        
        // Keep history size manageable
        while (latencyHistory.size > LATENCY_HISTORY_SIZE) {
            latencyHistory.poll()
        }
    }
    
    private fun collectMetrics() {
        val now = SystemClock.elapsedRealtime()
        
        // Calculate latency metrics
        val latencies = latencyHistory.map { it.latencyMs }
        val averageLatency = if (latencies.isNotEmpty()) latencies.average().toFloat() else 0f
        val maxLatency = latencies.maxOrNull() ?: 0f
        val minLatency = latencies.minOrNull() ?: 0f
        
        // Calculate throughput
        cleanOldTimestamps(now)
        val throughput = calculateCurrentThroughput()
        
        // Get resource usage
        val cpuUsage = measureCpuUsage()
        val memoryUsage = measureMemoryUsage()
        
        // Update metrics
        val metrics = MidiPerformanceMetrics(
            averageLatency = averageLatency,
            maxLatency = maxLatency,
            minLatency = minLatency,
            throughput = throughput,
            inputMessages = inputMessageCount.get(),
            outputMessages = outputMessageCount.get(),
            droppedMessages = droppedMessageCount.get(),
            cpuUsage = cpuUsage,
            memoryUsage = memoryUsage,
            timestamp = now
        )
        
        _currentMetrics.value = metrics
    }
    
    private fun analyzePerformance() {
        val metrics = _currentMetrics.value
        val recommendations = mutableListOf<MidiOptimizationRecommendation>()
        
        // Analyze latency
        if (metrics.averageLatency > WARNING_LATENCY_MS) {
            recommendations.add(
                MidiOptimizationRecommendation(
                    type = OptimizationType.REDUCE_LATENCY,
                    priority = if (metrics.averageLatency > CRITICAL_LATENCY_MS) 
                        OptimizationPriority.HIGH else OptimizationPriority.MEDIUM,
                    title = "High MIDI Latency Detected",
                    description = "Average latency is ${metrics.averageLatency}ms. Consider reducing buffer sizes or closing other apps.",
                    actions = listOf(
                        "Reduce audio buffer size",
                        "Close background apps",
                        "Check device performance mode"
                    )
                )
            )
        }
        
        // Analyze throughput
        if (metrics.throughput > MAX_THROUGHPUT_MESSAGES_PER_SEC * 0.8) {
            recommendations.add(
                MidiOptimizationRecommendation(
                    type = OptimizationType.REDUCE_THROUGHPUT,
                    priority = OptimizationPriority.MEDIUM,
                    title = "High MIDI Throughput",
                    description = "Processing ${metrics.throughput} messages/sec. Consider filtering unnecessary messages.",
                    actions = listOf(
                        "Enable MIDI message filtering",
                        "Reduce controller sensitivity",
                        "Limit active MIDI channels"
                    )
                )
            )
        }
        
        // Analyze dropped messages
        if (metrics.droppedMessages > 0) {
            recommendations.add(
                MidiOptimizationRecommendation(
                    type = OptimizationType.PREVENT_DROPS,
                    priority = OptimizationPriority.HIGH,
                    title = "MIDI Messages Being Dropped",
                    description = "${metrics.droppedMessages} messages dropped. System may be overloaded.",
                    actions = listOf(
                        "Increase buffer sizes",
                        "Reduce MIDI input rate",
                        "Check system resources"
                    )
                )
            )
        }
        
        // Analyze CPU usage
        if (metrics.cpuUsage > 80f) {
            recommendations.add(
                MidiOptimizationRecommendation(
                    type = OptimizationType.REDUCE_CPU,
                    priority = OptimizationPriority.HIGH,
                    title = "High CPU Usage",
                    description = "CPU usage at ${metrics.cpuUsage}%. MIDI performance may be affected.",
                    actions = listOf(
                        "Close background apps",
                        "Reduce audio quality",
                        "Disable unnecessary effects"
                    )
                )
            )
        }
        
        // Analyze memory usage
        if (metrics.memoryUsage > 85f) {
            recommendations.add(
                MidiOptimizationRecommendation(
                    type = OptimizationType.REDUCE_MEMORY,
                    priority = OptimizationPriority.MEDIUM,
                    title = "High Memory Usage",
                    description = "Memory usage at ${metrics.memoryUsage}%. Consider freeing resources.",
                    actions = listOf(
                        "Clear sample cache",
                        "Reduce loaded samples",
                        "Restart app if needed"
                    )
                )
            )
        }
        
        _optimizationRecommendations.value = recommendations
    }
    
    private fun cleanOldTimestamps(currentTime: Long) {
        val cutoff = currentTime - THROUGHPUT_WINDOW_MS
        while (messageTimestamps.isNotEmpty() && messageTimestamps.peek() < cutoff) {
            messageTimestamps.poll()
        }
    }
    
    private fun calculateCurrentThroughput(): Int {
        val windowSizeSeconds = THROUGHPUT_WINDOW_MS / 1000.0
        return (messageTimestamps.size / windowSizeSeconds).toInt()
    }
    
    private fun measureCpuUsage(): Float {
        // Simplified CPU measurement - in a real implementation,
        // you would use proper system APIs
        return kotlin.random.Random.nextFloat() * 100f // Placeholder
    }
    
    private fun measureMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        return (usedMemory.toFloat() / totalMemory.toFloat()) * 100f
    }
    
    private fun calculatePerformanceRating(metrics: MidiPerformanceMetrics): PerformanceRating {
        var score = 100f
        
        // Deduct points for high latency
        if (metrics.averageLatency > WARNING_LATENCY_MS) {
            score -= (metrics.averageLatency - WARNING_LATENCY_MS) * 2f
        }
        
        // Deduct points for dropped messages
        score -= metrics.droppedMessages * 5f
        
        // Deduct points for high CPU usage
        if (metrics.cpuUsage > 70f) {
            score -= (metrics.cpuUsage - 70f) * 0.5f
        }
        
        // Deduct points for high memory usage
        if (metrics.memoryUsage > 80f) {
            score -= (metrics.memoryUsage - 80f) * 0.3f
        }
        
        return when {
            score >= 90f -> PerformanceRating.EXCELLENT
            score >= 75f -> PerformanceRating.GOOD
            score >= 60f -> PerformanceRating.FAIR
            score >= 40f -> PerformanceRating.POOR
            else -> PerformanceRating.CRITICAL
        }
    }
    
    private fun emitAlert(alert: MidiPerformanceAlert) {
        monitorScope.launch {
            _performanceAlerts.emit(alert)
        }
    }
}

/**
 * MIDI performance metrics
 */
data class MidiPerformanceMetrics(
    val averageLatency: Float = 0f,
    val maxLatency: Float = 0f,
    val minLatency: Float = 0f,
    val throughput: Int = 0,
    val inputMessages: Long = 0L,
    val outputMessages: Long = 0L,
    val droppedMessages: Long = 0L,
    val cpuUsage: Float = 0f,
    val memoryUsage: Float = 0f,
    val timestamp: Long = 0L
)

/**
 * Latency measurement record
 */
data class LatencyMeasurement(
    val latencyMs: Float,
    val timestamp: Long,
    val deviceId: String,
    val type: LatencyType
)

/**
 * Types of latency measurements
 */
enum class LatencyType {
    INPUT,
    OUTPUT,
    ROUND_TRIP
}

/**
 * Performance alerts
 */
sealed class MidiPerformanceAlert {
    data class HighLatency(val latencyMs: Float, val deviceId: String) : MidiPerformanceAlert()
    data class CriticalLatency(val latencyMs: Float, val deviceId: String) : MidiPerformanceAlert()
    data class HighThroughput(val messagesPerSecond: Int) : MidiPerformanceAlert()
    data class DroppedMessages(val reason: String, val deviceId: String) : MidiPerformanceAlert()
    data class HighCpuUsage(val cpuPercent: Float) : MidiPerformanceAlert()
    data class HighMemoryUsage(val memoryPercent: Float) : MidiPerformanceAlert()
}

/**
 * Performance optimization recommendation
 */
data class MidiOptimizationRecommendation(
    val type: OptimizationType,
    val priority: OptimizationPriority,
    val title: String,
    val description: String,
    val actions: List<String>
)

/**
 * Types of performance optimizations
 */
enum class OptimizationType {
    REDUCE_LATENCY,
    REDUCE_THROUGHPUT,
    PREVENT_DROPS,
    REDUCE_CPU,
    REDUCE_MEMORY,
    IMPROVE_STABILITY
}

/**
 * Optimization priority levels
 */
enum class OptimizationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Performance rating
 */
enum class PerformanceRating {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL
}

/**
 * Performance summary
 */
data class MidiPerformanceSummary(
    val averageLatency: Float,
    val maxLatency: Float,
    val throughput: Int,
    val droppedMessages: Long,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val performanceRating: PerformanceRating,
    val recommendations: List<MidiOptimizationRecommendation>,
    val timestamp: Long
)