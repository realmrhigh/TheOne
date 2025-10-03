package com.high.theone.features.sequencer

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * Comprehensive performance monitoring system for the sequencer
 * Tracks timing accuracy, jitter, CPU usage, memory usage, and audio latency
 */
class SequencerPerformanceMonitor {
    
    companion object {
        private const val TAG = "SequencerPerformanceMonitor"
        private const val TIMING_HISTORY_SIZE = 1000
        private const val METRICS_UPDATE_INTERVAL_MS = 50L
        private const val JITTER_CALCULATION_WINDOW = 100
        private const val LATENCY_MEASUREMENT_SAMPLES = 50
    }
    
    // Timing measurements
    private val timingMeasurements = ConcurrentLinkedQueue<TimingMeasurement>()
    private val expectedTimings = ConcurrentLinkedQueue<Long>()
    private val actualTimings = ConcurrentLinkedQueue<Long>()
    
    // Performance counters
    private val totalStepTriggers = AtomicLong(0)
    private val missedStepTriggers = AtomicLong(0)
    private val lateStepTriggers = AtomicLong(0)
    private val earlyStepTriggers = AtomicLong(0)
    
    // Audio latency measurements
    private val latencyMeasurements = ConcurrentLinkedQueue<LatencyMeasurement>()
    
    // Performance metrics flow
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    // Monitoring state
    private var isMonitoring = false
    private var monitoringJob: Job? = null
    
    /**
     * Start performance monitoring
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                try {
                    updateMetrics()
                    delay(METRICS_UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating performance metrics", e)
                    delay(1000L)
                }
            }
        }
        
        Log.d(TAG, "Performance monitoring started")
    }
    
    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        
        Log.d(TAG, "Performance monitoring stopped")
    }
    
    /**
     * Record a step trigger timing measurement
     */
    fun recordStepTrigger(
        expectedTime: Long,
        actualTime: Long,
        stepIndex: Int,
        padIndex: Int
    ) {
        val measurement = TimingMeasurement(
            expectedTime = expectedTime,
            actualTime = actualTime,
            stepIndex = stepIndex,
            padIndex = padIndex,
            timestamp = SystemClock.elapsedRealtimeNanos()
        )
        
        // Add to timing measurements
        timingMeasurements.offer(measurement)
        if (timingMeasurements.size > TIMING_HISTORY_SIZE) {
            timingMeasurements.poll()
        }
        
        // Add to timing arrays for jitter calculation
        expectedTimings.offer(expectedTime)
        actualTimings.offer(actualTime)
        if (expectedTimings.size > JITTER_CALCULATION_WINDOW) {
            expectedTimings.poll()
            actualTimings.poll()
        }
        
        // Update counters
        totalStepTriggers.incrementAndGet()
        
        val timingError = actualTime - expectedTime
        when {
            abs(timingError) > 5000L -> { // 5ms threshold
                if (timingError > 0) {
                    lateStepTriggers.incrementAndGet()
                } else {
                    earlyStepTriggers.incrementAndGet()
                }
            }
        }
    }
    
    /**
     * Record a missed step trigger
     */
    fun recordMissedStepTrigger(expectedTime: Long, stepIndex: Int, padIndex: Int) {
        missedStepTriggers.incrementAndGet()
        totalStepTriggers.incrementAndGet()
        
        Log.w(TAG, "Missed step trigger: step=$stepIndex, pad=$padIndex, time=$expectedTime")
    }
    
    /**
     * Record audio latency measurement
     */
    fun recordAudioLatency(
        inputTime: Long,
        outputTime: Long,
        sampleId: String
    ) {
        val latency = outputTime - inputTime
        val measurement = LatencyMeasurement(
            inputTime = inputTime,
            outputTime = outputTime,
            latency = latency,
            sampleId = sampleId,
            timestamp = SystemClock.elapsedRealtimeNanos()
        )
        
        latencyMeasurements.offer(measurement)
        if (latencyMeasurements.size > LATENCY_MEASUREMENT_SAMPLES) {
            latencyMeasurements.poll()
        }
    }
    
    /**
     * Measure timing jitter
     */
    private fun calculateTimingJitter(): JitterMetrics {
        val timings = actualTimings.toList()
        val expected = expectedTimings.toList()
        
        if (timings.size < 2 || expected.size < 2) {
            return JitterMetrics()
        }
        
        // Calculate timing errors
        val errors = timings.zip(expected) { actual, exp -> actual - exp }
        
        // Calculate jitter statistics
        val meanError = errors.average()
        val variance = errors.map { (it - meanError).pow(2) }.average()
        val standardDeviation = sqrt(variance)
        val maxError = errors.maxOrNull() ?: 0L
        val minError = errors.minOrNull() ?: 0L
        
        // Calculate RMS jitter
        val rmsJitter = sqrt(errors.map { it.toDouble().pow(2) }.average())
        
        return JitterMetrics(
            meanError = meanError.toLong(),
            standardDeviation = standardDeviation.toLong(),
            rmsJitter = rmsJitter.toLong(),
            maxError = maxError,
            minError = minError,
            sampleCount = errors.size
        )
    }
    
    /**
     * Calculate audio latency statistics
     */
    private fun calculateLatencyMetrics(): LatencyMetrics {
        val measurements = latencyMeasurements.toList()
        
        if (measurements.isEmpty()) {
            return LatencyMetrics()
        }
        
        val latencies = measurements.map { it.latency }
        val avgLatency = latencies.average().toLong()
        val minLatency = latencies.minOrNull() ?: 0L
        val maxLatency = latencies.maxOrNull() ?: 0L
        
        // Calculate latency jitter
        val latencyVariance = latencies.map { (it - avgLatency).toDouble().pow(2) }.average()
        val latencyJitter = sqrt(latencyVariance).toLong()
        
        return LatencyMetrics(
            averageLatency = avgLatency,
            minLatency = minLatency,
            maxLatency = maxLatency,
            jitter = latencyJitter,
            sampleCount = measurements.size
        )
    }
    
    /**
     * Calculate CPU and memory usage
     */
    private fun calculateSystemMetrics(): SystemMetrics {
        val runtime = Runtime.getRuntime()
        
        // Memory metrics
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100f
        
        // CPU usage estimation (simplified)
        val cpuUsagePercent = estimateCpuUsage()
        
        return SystemMetrics(
            usedMemory = usedMemory,
            totalMemory = totalMemory,
            maxMemory = maxMemory,
            memoryUsagePercent = memoryUsagePercent,
            cpuUsagePercent = cpuUsagePercent,
            availableProcessors = runtime.availableProcessors()
        )
    }
    
    /**
     * Estimate CPU usage (simplified approach)
     */
    private fun estimateCpuUsage(): Float {
        // This is a simplified estimation
        // In a real implementation, you might use more sophisticated methods
        val totalTriggers = totalStepTriggers.get()
        val missedTriggers = missedStepTriggers.get()
        
        if (totalTriggers == 0L) return 0f
        
        val missedRatio = missedTriggers.toFloat() / totalTriggers.toFloat()
        
        // Estimate CPU usage based on missed triggers and timing accuracy
        val jitterMetrics = calculateTimingJitter()
        val jitterFactor = (jitterMetrics.rmsJitter / 10000f).coerceIn(0f, 1f) // Normalize to 0-1
        
        return ((missedRatio * 50f) + (jitterFactor * 30f)).coerceIn(0f, 100f)
    }
    
    /**
     * Update performance metrics
     */
    private suspend fun updateMetrics() {
        try {
            val jitterMetrics = calculateTimingJitter()
            val latencyMetrics = calculateLatencyMetrics()
            val systemMetrics = calculateSystemMetrics()
            
            val totalTriggers = totalStepTriggers.get()
            val missedTriggers = missedStepTriggers.get()
            val lateTriggers = lateStepTriggers.get()
            val earlyTriggers = earlyStepTriggers.get()
            
            val accuracyPercent = if (totalTriggers > 0) {
                ((totalTriggers - missedTriggers).toFloat() / totalTriggers.toFloat()) * 100f
            } else {
                100f
            }
            
            val metrics = PerformanceMetrics(
                // Timing metrics
                timingAccuracy = accuracyPercent,
                averageJitter = jitterMetrics.rmsJitter,
                maxJitter = jitterMetrics.maxError,
                minJitter = jitterMetrics.minError,
                
                // Trigger statistics
                totalTriggers = totalTriggers,
                missedTriggers = missedTriggers,
                lateTriggers = lateTriggers,
                earlyTriggers = earlyTriggers,
                
                // Audio latency
                averageLatency = latencyMetrics.averageLatency,
                minLatency = latencyMetrics.minLatency,
                maxLatency = latencyMetrics.maxLatency,
                latencyJitter = latencyMetrics.jitter,
                
                // System metrics
                cpuUsage = systemMetrics.cpuUsagePercent,
                memoryUsage = systemMetrics.usedMemory,
                memoryUsagePercent = systemMetrics.memoryUsagePercent,
                
                // Status
                isMonitoring = isMonitoring,
                lastUpdate = System.currentTimeMillis()
            )
            
            _performanceMetrics.value = metrics
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating performance metrics", e)
        }
    }
    
    /**
     * Reset all performance counters
     */
    fun resetCounters() {
        totalStepTriggers.set(0)
        missedStepTriggers.set(0)
        lateStepTriggers.set(0)
        earlyStepTriggers.set(0)
        
        timingMeasurements.clear()
        expectedTimings.clear()
        actualTimings.clear()
        latencyMeasurements.clear()
        
        Log.d(TAG, "Performance counters reset")
    }
    
    /**
     * Get performance report
     */
    fun getPerformanceReport(): PerformanceReport {
        val metrics = _performanceMetrics.value
        val jitterMetrics = calculateTimingJitter()
        val latencyMetrics = calculateLatencyMetrics()
        val systemMetrics = calculateSystemMetrics()
        
        return PerformanceReport(
            metrics = metrics,
            jitterDetails = jitterMetrics,
            latencyDetails = latencyMetrics,
            systemDetails = systemMetrics,
            recommendations = generateRecommendations(metrics),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Generate performance recommendations
     */
    private fun generateRecommendations(metrics: PerformanceMetrics): List<PerformanceRecommendation> {
        val recommendations = mutableListOf<PerformanceRecommendation>()
        
        // Timing accuracy recommendations
        if (metrics.timingAccuracy < 95f) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Timing",
                    severity = if (metrics.timingAccuracy < 90f) "High" else "Medium",
                    message = "Low timing accuracy (${String.format("%.1f", metrics.timingAccuracy)}%)",
                    suggestion = "Reduce system load or increase audio buffer size"
                )
            )
        }
        
        // Jitter recommendations
        if (metrics.averageJitter > 5000L) { // 5ms
            recommendations.add(
                PerformanceRecommendation(
                    category = "Jitter",
                    severity = if (metrics.averageJitter > 10000L) "High" else "Medium",
                    message = "High timing jitter (${metrics.averageJitter / 1000}ms)",
                    suggestion = "Close other applications or reduce pattern complexity"
                )
            )
        }
        
        // Latency recommendations
        if (metrics.averageLatency > 50000L) { // 50ms
            recommendations.add(
                PerformanceRecommendation(
                    category = "Latency",
                    severity = if (metrics.averageLatency > 100000L) "High" else "Medium",
                    message = "High audio latency (${metrics.averageLatency / 1000}ms)",
                    suggestion = "Reduce audio buffer size or optimize audio settings"
                )
            )
        }
        
        // CPU recommendations
        if (metrics.cpuUsage > 70f) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "CPU",
                    severity = if (metrics.cpuUsage > 85f) "High" else "Medium",
                    message = "High CPU usage (${String.format("%.1f", metrics.cpuUsage)}%)",
                    suggestion = "Reduce polyphony or pattern complexity"
                )
            )
        }
        
        // Memory recommendations
        if (metrics.memoryUsagePercent > 80f) {
            recommendations.add(
                PerformanceRecommendation(
                    category = "Memory",
                    severity = if (metrics.memoryUsagePercent > 90f) "High" else "Medium",
                    message = "High memory usage (${String.format("%.1f", metrics.memoryUsagePercent)}%)",
                    suggestion = "Clear sample cache or reduce cached patterns"
                )
            )
        }
        
        return recommendations
    }
}

/**
 * Individual timing measurement
 */
data class TimingMeasurement(
    val expectedTime: Long,
    val actualTime: Long,
    val stepIndex: Int,
    val padIndex: Int,
    val timestamp: Long
) {
    val error: Long get() = actualTime - expectedTime
    val absoluteError: Long get() = abs(error)
}

/**
 * Audio latency measurement
 */
data class LatencyMeasurement(
    val inputTime: Long,
    val outputTime: Long,
    val latency: Long,
    val sampleId: String,
    val timestamp: Long
)

/**
 * Jitter calculation results
 */
data class JitterMetrics(
    val meanError: Long = 0L,
    val standardDeviation: Long = 0L,
    val rmsJitter: Long = 0L,
    val maxError: Long = 0L,
    val minError: Long = 0L,
    val sampleCount: Int = 0
)

/**
 * Latency calculation results
 */
data class LatencyMetrics(
    val averageLatency: Long = 0L,
    val minLatency: Long = 0L,
    val maxLatency: Long = 0L,
    val jitter: Long = 0L,
    val sampleCount: Int = 0
)

/**
 * System performance metrics
 */
data class SystemMetrics(
    val usedMemory: Long = 0L,
    val totalMemory: Long = 0L,
    val maxMemory: Long = 0L,
    val memoryUsagePercent: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val availableProcessors: Int = 1
)

/**
 * Overall performance metrics
 */
data class PerformanceMetrics(
    // Timing metrics
    val timingAccuracy: Float = 100f,
    val averageJitter: Long = 0L,
    val maxJitter: Long = 0L,
    val minJitter: Long = 0L,
    
    // Trigger statistics
    val totalTriggers: Long = 0L,
    val missedTriggers: Long = 0L,
    val lateTriggers: Long = 0L,
    val earlyTriggers: Long = 0L,
    
    // Audio latency
    val averageLatency: Long = 0L,
    val minLatency: Long = 0L,
    val maxLatency: Long = 0L,
    val latencyJitter: Long = 0L,
    
    // System metrics
    val cpuUsage: Float = 0f,
    val memoryUsage: Long = 0L,
    val memoryUsagePercent: Float = 0f,
    
    // Status
    val isMonitoring: Boolean = false,
    val lastUpdate: Long = 0L
)

/**
 * Performance recommendation
 */
data class PerformanceRecommendation(
    val category: String,
    val severity: String,
    val message: String,
    val suggestion: String
)

/**
 * Complete performance report
 */
data class PerformanceReport(
    val metrics: PerformanceMetrics,
    val jitterDetails: JitterMetrics,
    val latencyDetails: LatencyMetrics,
    val systemDetails: SystemMetrics,
    val recommendations: List<PerformanceRecommendation>,
    val timestamp: Long
)