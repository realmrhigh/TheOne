package com.high.theone.features.sampling

import android.util.Log
import com.high.theone.audio.AudioEngineControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive debugging and monitoring tools for the sampling system.
 * Requirements: All requirements (debugging and troubleshooting)
 */
@Singleton
class SamplingDebugManager @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleCacheManager: SampleCacheManager,
    private val voiceManager: VoiceManager,
    private val performanceMonitor: PerformanceMonitor
) {
    companion object {
        private const val TAG = "SamplingDebugManager"
    }

    private val _debugInfo = MutableStateFlow(SamplingDebugInfo())
    val debugInfo: StateFlow<SamplingDebugInfo> = _debugInfo.asStateFlow()

    data class SamplingDebugInfo(
        val audioEngineStatus: AudioEngineStatus = AudioEngineStatus(),
        val cacheStatus: CacheStatus = CacheStatus(),
        val voiceStatus: VoiceStatus = VoiceStatus(),
        val performanceStatus: PerformanceStatus = PerformanceStatus(),
        val systemInfo: SystemInfo = SystemInfo(),
        val diagnosticLogs: List<DiagnosticLog> = emptyList()
    )

    data class AudioEngineStatus(
        val isInitialized: Boolean = false,
        val reportedLatency: Float = 0f,
        val activeVoices: Int = 0,
        val loadedSamples: Int = 0,
        val engineState: String = "Unknown"
    )

    data class CacheStatus(
        val totalSamples: Int = 0,
        val loadedSamples: Int = 0,
        val averageLoadTime: Double = 0.0,
        val cacheHitRate: Float = 0f,
        val memoryUsage: Long = 0L
    )

    data class VoiceStatus(
        val activeVoices: Int = 0,
        val maxVoices: Int = 0,
        val voiceUtilization: Float = 0f,
        val voicesByPriority: Map<VoiceManager.VoicePriority, Int> = emptyMap(),
        val oldestVoiceAge: Long = 0L
    )

    data class PerformanceStatus(
        val averageFrameTime: Float = 0f,
        val frameDrops: Int = 0,
        val audioLatency: Float = 0f,
        val memoryUsage: Long = 0L,
        val cpuUsage: Float = 0f,
        val isPerformanceGood: Boolean = true,
        val warnings: List<PerformanceMonitor.PerformanceWarning> = emptyList()
    )

    data class SystemInfo(
        val availableMemory: Long = 0L,
        val totalMemory: Long = 0L,
        val processorCount: Int = 0,
        val androidVersion: String = "",
        val deviceModel: String = ""
    )

    data class DiagnosticLog(
        val timestamp: Long,
        val level: LogLevel,
        val category: String,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR, CRITICAL
    }

    /**
     * Update all debug information.
     */
    suspend fun updateDebugInfo() {
        try {
            val audioStatus = getAudioEngineStatus()
            val cacheStatus = getCacheStatus()
            val voiceStatus = getVoiceStatus()
            val performanceStatus = getPerformanceStatus()
            val systemInfo = getSystemInfo()

            _debugInfo.value = SamplingDebugInfo(
                audioEngineStatus = audioStatus,
                cacheStatus = cacheStatus,
                voiceStatus = voiceStatus,
                performanceStatus = performanceStatus,
                systemInfo = systemInfo,
                diagnosticLogs = _debugInfo.value.diagnosticLogs.takeLast(100) // Keep last 100 logs
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error updating debug info", e)
            addDiagnosticLog(LogLevel.ERROR, "Debug", "Failed to update debug info: ${e.message}")
        }
    }

    /**
     * Get comprehensive audio engine diagnostics.
     */
    private suspend fun getAudioEngineStatus(): AudioEngineStatus {
        return try {
            AudioEngineStatus(
                isInitialized = true, // Would check actual state
                reportedLatency = audioEngine.getReportedLatencyMillis(),
                activeVoices = audioEngine.getDrumActiveVoices(),
                loadedSamples = audioEngine.getDrumEngineLoadedSamples(),
                engineState = "Running" // Would get actual state
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio engine status", e)
            AudioEngineStatus(engineState = "Error: ${e.message}")
        }
    }

    /**
     * Get sample cache diagnostics.
     */
    private fun getCacheStatus(): CacheStatus {
        return try {
            val cacheStats = sampleCacheManager.getCacheStats()
            CacheStatus(
                totalSamples = cacheStats.totalSamples,
                loadedSamples = cacheStats.loadedSamples,
                averageLoadTime = cacheStats.averageLoadTime,
                cacheHitRate = if (cacheStats.totalSamples > 0) {
                    cacheStats.loadedSamples.toFloat() / cacheStats.totalSamples
                } else 0f,
                memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache status", e)
            CacheStatus()
        }
    }

    /**
     * Get voice management diagnostics.
     */
    private fun getVoiceStatus(): VoiceStatus {
        return try {
            val voiceStats = voiceManager.getVoiceStats()
            VoiceStatus(
                activeVoices = voiceStats.activeVoices,
                maxVoices = voiceStats.maxVoices,
                voiceUtilization = voiceStats.voiceUtilization,
                voicesByPriority = voiceStats.voicesByPriority,
                oldestVoiceAge = voiceStats.oldestVoiceAge
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting voice status", e)
            VoiceStatus()
        }
    }

    /**
     * Get performance monitoring diagnostics.
     */
    private fun getPerformanceStatus(): PerformanceStatus {
        return try {
            val perfMetrics = performanceMonitor.performanceMetrics.value
            PerformanceStatus(
                averageFrameTime = perfMetrics.averageFrameTime,
                frameDrops = perfMetrics.frameDrops,
                audioLatency = perfMetrics.audioLatency,
                memoryUsage = perfMetrics.memoryUsage,
                cpuUsage = perfMetrics.cpuUsage,
                isPerformanceGood = perfMetrics.isPerformanceGood,
                warnings = perfMetrics.warnings
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting performance status", e)
            PerformanceStatus()
        }
    }

    /**
     * Get system information.
     */
    private fun getSystemInfo(): SystemInfo {
        return try {
            val runtime = Runtime.getRuntime()
            SystemInfo(
                availableMemory = runtime.freeMemory(),
                totalMemory = runtime.totalMemory(),
                processorCount = runtime.availableProcessors(),
                androidVersion = android.os.Build.VERSION.RELEASE,
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system info", e)
            SystemInfo()
        }
    }

    /**
     * Add a diagnostic log entry.
     */
    fun addDiagnosticLog(level: LogLevel, category: String, message: String, details: Map<String, Any> = emptyMap()) {
        val log = DiagnosticLog(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message,
            details = details
        )

        _debugInfo.value = _debugInfo.value.copy(
            diagnosticLogs = (_debugInfo.value.diagnosticLogs + log).takeLast(100)
        )

        // Also log to Android Log
        val logMessage = "[$category] $message ${if (details.isNotEmpty()) details else ""}"
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, logMessage)
            LogLevel.INFO -> Log.i(TAG, logMessage)
            LogLevel.WARNING -> Log.w(TAG, logMessage)
            LogLevel.ERROR -> Log.e(TAG, logMessage)
            LogLevel.CRITICAL -> Log.e(TAG, "CRITICAL: $logMessage")
        }
    }

    /**
     * Run comprehensive system diagnostics.
     */
    suspend fun runDiagnostics(): DiagnosticReport {
        addDiagnosticLog(LogLevel.INFO, "Diagnostics", "Starting comprehensive system diagnostics")

        val report = DiagnosticReport()

        try {
            // Test audio engine
            report.audioEngineTests = runAudioEngineTests()
            
            // Test sample loading
            report.sampleLoadingTests = runSampleLoadingTests()
            
            // Test performance
            report.performanceTests = runPerformanceTests()
            
            // Test memory usage
            report.memoryTests = runMemoryTests()

            addDiagnosticLog(LogLevel.INFO, "Diagnostics", "Diagnostics completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error running diagnostics", e)
            addDiagnosticLog(LogLevel.ERROR, "Diagnostics", "Diagnostics failed: ${e.message}")
        }

        return report
    }

    /**
     * Test audio engine functionality.
     */
    private suspend fun runAudioEngineTests(): List<TestResult> {
        val tests = mutableListOf<TestResult>()

        try {
            // Test latency
            val latency = audioEngine.getReportedLatencyMillis()
            tests.add(TestResult(
                name = "Audio Latency",
                passed = latency < 50f,
                value = "${latency}ms",
                expected = "<50ms"
            ))

            // Test voice count
            val voices = audioEngine.getDrumActiveVoices()
            tests.add(TestResult(
                name = "Active Voices",
                passed = voices >= 0,
                value = voices.toString(),
                expected = ">=0"
            ))

            // Test sample loading
            val loadedSamples = audioEngine.getDrumEngineLoadedSamples()
            tests.add(TestResult(
                name = "Loaded Samples",
                passed = loadedSamples >= 0,
                value = loadedSamples.toString(),
                expected = ">=0"
            ))

        } catch (e: Exception) {
            tests.add(TestResult(
                name = "Audio Engine Test",
                passed = false,
                value = "Error: ${e.message}",
                expected = "No errors"
            ))
        }

        return tests
    }

    /**
     * Test sample loading performance.
     */
    private suspend fun runSampleLoadingTests(): List<TestResult> {
        val tests = mutableListOf<TestResult>()

        try {
            val cacheStats = sampleCacheManager.getCacheStats()
            
            tests.add(TestResult(
                name = "Cache Hit Rate",
                passed = cacheStats.totalSamples == 0 || cacheStats.loadedSamples.toFloat() / cacheStats.totalSamples > 0.8f,
                value = "${(if (cacheStats.totalSamples > 0) cacheStats.loadedSamples.toFloat() / cacheStats.totalSamples * 100 else 0f).toInt()}%",
                expected = ">80%"
            ))

            tests.add(TestResult(
                name = "Average Load Time",
                passed = cacheStats.averageLoadTime < 100.0,
                value = "${cacheStats.averageLoadTime.toInt()}ms",
                expected = "<100ms"
            ))

        } catch (e: Exception) {
            tests.add(TestResult(
                name = "Sample Loading Test",
                passed = false,
                value = "Error: ${e.message}",
                expected = "No errors"
            ))
        }

        return tests
    }

    /**
     * Test performance metrics.
     */
    private fun runPerformanceTests(): List<TestResult> {
        val tests = mutableListOf<TestResult>()

        try {
            val perfMetrics = performanceMonitor.performanceMetrics.value

            tests.add(TestResult(
                name = "Frame Time",
                passed = perfMetrics.averageFrameTime <= 16.67f * 1.2f,
                value = "${perfMetrics.averageFrameTime.toInt()}ms",
                expected = "≤20ms"
            ))

            tests.add(TestResult(
                name = "Frame Drops",
                passed = perfMetrics.frameDrops < 5,
                value = perfMetrics.frameDrops.toString(),
                expected = "<5"
            ))

            tests.add(TestResult(
                name = "Performance Status",
                passed = perfMetrics.isPerformanceGood,
                value = if (perfMetrics.isPerformanceGood) "Good" else "Poor",
                expected = "Good"
            ))

        } catch (e: Exception) {
            tests.add(TestResult(
                name = "Performance Test",
                passed = false,
                value = "Error: ${e.message}",
                expected = "No errors"
            ))
        }

        return tests
    }

    /**
     * Test memory usage.
     */
    private fun runMemoryTests(): List<TestResult> {
        val tests = mutableListOf<TestResult>()

        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryPercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

            tests.add(TestResult(
                name = "Memory Usage",
                passed = memoryPercent < 80,
                value = "$memoryPercent%",
                expected = "<80%"
            ))

            tests.add(TestResult(
                name = "Available Memory",
                passed = runtime.freeMemory() > 50 * 1024 * 1024, // 50MB
                value = "${runtime.freeMemory() / 1024 / 1024}MB",
                expected = ">50MB"
            ))

        } catch (e: Exception) {
            tests.add(TestResult(
                name = "Memory Test",
                passed = false,
                value = "Error: ${e.message}",
                expected = "No errors"
            ))
        }

        return tests
    }

    /**
     * Export debug information for troubleshooting.
     */
    fun exportDebugReport(): String {
        val debugInfo = _debugInfo.value
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        return buildString {
            appendLine("=== TheOne Sampling Debug Report ===")
            appendLine("Generated: $timestamp")
            appendLine()

            appendLine("=== System Information ===")
            appendLine("Device: ${debugInfo.systemInfo.deviceModel}")
            appendLine("Android: ${debugInfo.systemInfo.androidVersion}")
            appendLine("Processors: ${debugInfo.systemInfo.processorCount}")
            appendLine("Memory: ${debugInfo.systemInfo.availableMemory / 1024 / 1024}MB / ${debugInfo.systemInfo.totalMemory / 1024 / 1024}MB")
            appendLine()

            appendLine("=== Audio Engine Status ===")
            appendLine("Initialized: ${debugInfo.audioEngineStatus.isInitialized}")
            appendLine("Latency: ${debugInfo.audioEngineStatus.reportedLatency}ms")
            appendLine("Active Voices: ${debugInfo.audioEngineStatus.activeVoices}")
            appendLine("Loaded Samples: ${debugInfo.audioEngineStatus.loadedSamples}")
            appendLine("State: ${debugInfo.audioEngineStatus.engineState}")
            appendLine()

            appendLine("=== Performance Status ===")
            appendLine("Frame Time: ${debugInfo.performanceStatus.averageFrameTime}ms")
            appendLine("Frame Drops: ${debugInfo.performanceStatus.frameDrops}")
            appendLine("Audio Latency: ${debugInfo.performanceStatus.audioLatency}ms")
            appendLine("Memory Usage: ${debugInfo.performanceStatus.memoryUsage / 1024 / 1024}MB")
            appendLine("CPU Usage: ${debugInfo.performanceStatus.cpuUsage}%")
            appendLine("Performance Good: ${debugInfo.performanceStatus.isPerformanceGood}")
            appendLine()

            if (debugInfo.performanceStatus.warnings.isNotEmpty()) {
                appendLine("=== Performance Warnings ===")
                debugInfo.performanceStatus.warnings.forEach { warning ->
                    appendLine("[${warning.severity}] ${warning.type}: ${warning.message}")
                }
                appendLine()
            }

            appendLine("=== Recent Diagnostic Logs ===")
            debugInfo.diagnosticLogs.takeLast(20).forEach { log ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(log.timestamp))
                appendLine("[$time] [${log.level}] ${log.category}: ${log.message}")
            }
        }
    }

    data class DiagnosticReport(
        var audioEngineTests: List<TestResult> = emptyList(),
        var sampleLoadingTests: List<TestResult> = emptyList(),
        var performanceTests: List<TestResult> = emptyList(),
        var memoryTests: List<TestResult> = emptyList()
    ) {
        val allTests: List<TestResult>
            get() = audioEngineTests + sampleLoadingTests + performanceTests + memoryTests

        val passedTests: Int
            get() = allTests.count { it.passed }

        val totalTests: Int
            get() = allTests.size

        val overallPassed: Boolean
            get() = allTests.all { it.passed }
    }

    data class TestResult(
        val name: String,
        val passed: Boolean,
        val value: String,
        val expected: String,
        val details: String? = null
    )
}