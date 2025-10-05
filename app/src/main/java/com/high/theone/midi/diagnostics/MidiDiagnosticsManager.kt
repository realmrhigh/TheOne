package com.high.theone.midi.diagnostics

import android.content.Context
import android.util.Log
import com.high.theone.midi.error.MidiErrorHandler
import com.high.theone.midi.error.MidiGracefulDegradation
import com.high.theone.midi.error.MidiNotificationManager
import com.high.theone.midi.performance.MidiPerformanceMonitor
import com.high.theone.midi.performance.MidiPerformanceOptimizer
import com.high.theone.midi.MidiError
import com.high.theone.midi.MidiErrorContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for MIDI diagnostics, error handling, and performance monitoring.
 * Coordinates between error handling, performance monitoring, and graceful degradation.
 * 
 * Requirements: 7.1, 7.2, 7.4, 7.5, 7.6
 */
@Singleton
class MidiDiagnosticsManager @Inject constructor(
    private val context: Context,
    private val errorHandler: MidiErrorHandler,
    private val performanceMonitor: MidiPerformanceMonitor,
    private val performanceOptimizer: MidiPerformanceOptimizer,
    private val gracefulDegradation: MidiGracefulDegradation,
    private val notificationManager: MidiNotificationManager
) {
    
    companion object {
        private const val TAG = "MidiDiagnosticsManager"
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L // 30 seconds
        private const val PERFORMANCE_OPTIMIZATION_INTERVAL_MS = 60000L // 1 minute
    }
    
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // System health state
    private val _systemHealth = MutableStateFlow(MidiSystemHealthStatus())
    val systemHealth: StateFlow<MidiSystemHealthStatus> = _systemHealth.asStateFlow()
    
    // Diagnostic reports
    private val _diagnosticReports = MutableStateFlow<List<MidiDiagnosticReport>>(emptyList())
    val diagnosticReports: StateFlow<List<MidiDiagnosticReport>> = _diagnosticReports.asStateFlow()
    
    private var isRunning = false
    
    /**
     * Start comprehensive MIDI diagnostics and monitoring
     */
    fun startDiagnostics() {
        if (isRunning) return
        
        Log.i(TAG, "Starting MIDI diagnostics and monitoring")
        isRunning = true
        
        // Start performance monitoring
        performanceMonitor.startMonitoring()
        
        // Start health monitoring
        startHealthMonitoring()
        
        // Start automatic optimization
        startAutomaticOptimization()
        
        // Monitor error handler alerts
        monitorErrorAlerts()
        
        // Monitor performance alerts
        monitorPerformanceAlerts()
    }
    
    /**
     * Stop diagnostics and monitoring
     */
    fun stopDiagnostics() {
        Log.i(TAG, "Stopping MIDI diagnostics and monitoring")
        isRunning = false
        
        performanceMonitor.stopMonitoring()
        errorHandler.shutdown()
        diagnosticsScope.cancel()
    }
    
    /**
     * Handle a MIDI error with comprehensive diagnostics
     */
    suspend fun handleMidiError(
        error: MidiError,
        context: MidiErrorContext
    ): MidiDiagnosticResult {
        Log.i(TAG, "Handling MIDI error with diagnostics: ${error::class.simpleName}")
        
        // Handle error through error handler
        val errorResult = errorHandler.handleError(error, context)
        
        // Apply graceful degradation if needed
        if (!errorResult.recovered) {
            gracefulDegradation.applyDegradation(error, com.high.theone.midi.model.MidiSystemState.ERROR)
        }
        
        // Generate diagnostic report
        val diagnosticReport = generateDiagnosticReport(error, context, errorResult)
        addDiagnosticReport(diagnosticReport)
        
        // Update system health
        updateSystemHealth()
        
        return MidiDiagnosticResult(
            errorHandled = true,
            errorRecovered = errorResult.recovered,
            degradationApplied = !errorResult.recovered,
            diagnosticReport = diagnosticReport,
            recommendedActions = errorResult.recoveryAction?.let { listOf(it.name) } ?: emptyList()
        )
    }
    
    /**
     * Perform comprehensive system health check
     */
    suspend fun performHealthCheck(): MidiHealthCheckResult {
        Log.i(TAG, "Performing comprehensive MIDI health check")
        
        val performanceMetrics = performanceMonitor.currentMetrics.value
        val errorStatistics = errorHandler.getErrorStatistics()
        val degradationMode = gracefulDegradation.degradationMode.value
        val optimizationSettings = performanceOptimizer.optimizationSettings.value
        
        val issues = mutableListOf<MidiHealthIssue>()
        
        // Check performance issues
        if (performanceMetrics.averageLatency > 15f) {
            issues.add(
                MidiHealthIssue(
                    severity = HealthIssueSeverity.WARNING,
                    category = HealthIssueCategory.PERFORMANCE,
                    title = "High MIDI Latency",
                    description = "Average latency is ${performanceMetrics.averageLatency}ms",
                    recommendation = "Consider reducing buffer size or closing background apps"
                )
            )
        }
        
        if (performanceMetrics.droppedMessages > 0) {
            issues.add(
                MidiHealthIssue(
                    severity = HealthIssueSeverity.ERROR,
                    category = HealthIssueCategory.RELIABILITY,
                    title = "Dropped MIDI Messages",
                    description = "${performanceMetrics.droppedMessages} messages dropped",
                    recommendation = "Increase buffer sizes or reduce MIDI input rate"
                )
            )
        }
        
        // Check error frequency
        if (errorStatistics.errorsLast24Hours > 10) {
            issues.add(
                MidiHealthIssue(
                    severity = HealthIssueSeverity.WARNING,
                    category = HealthIssueCategory.RELIABILITY,
                    title = "Frequent MIDI Errors",
                    description = "${errorStatistics.errorsLast24Hours} errors in last 24 hours",
                    recommendation = "Check device connections and system resources"
                )
            )
        }
        
        // Check degradation status
        if (degradationMode != com.high.theone.midi.error.MidiDegradationMode.FULL_FUNCTIONALITY) {
            issues.add(
                MidiHealthIssue(
                    severity = HealthIssueSeverity.INFO,
                    category = HealthIssueCategory.FUNCTIONALITY,
                    title = "Reduced MIDI Functionality",
                    description = "System running in $degradationMode mode",
                    recommendation = gracefulDegradation.getDegradationExplanation() ?: "Check system status"
                )
            )
        }
        
        val overallHealth = when {
            issues.any { it.severity == HealthIssueSeverity.CRITICAL } -> MidiOverallHealth.CRITICAL
            issues.any { it.severity == HealthIssueSeverity.ERROR } -> MidiOverallHealth.POOR
            issues.any { it.severity == HealthIssueSeverity.WARNING } -> MidiOverallHealth.FAIR
            else -> MidiOverallHealth.GOOD
        }
        
        return MidiHealthCheckResult(
            overallHealth = overallHealth,
            issues = issues,
            performanceMetrics = performanceMetrics,
            errorStatistics = errorStatistics,
            degradationMode = degradationMode,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Generate comprehensive diagnostic report
     */
    fun generateComprehensiveDiagnosticReport(): MidiComprehensiveDiagnosticReport {
        val performanceMetrics = performanceMonitor.currentMetrics.value
        val performanceSummary = performanceMonitor.getPerformanceSummary()
        val errorStatistics = errorHandler.getErrorStatistics()
        val errorHistory = errorHandler.errorHistory.value
        val optimizationSettings = performanceOptimizer.optimizationSettings.value
        val appliedOptimizations = performanceOptimizer.appliedOptimizations.value
        val degradationMode = gracefulDegradation.degradationMode.value
        val disabledFeatures = gracefulDegradation.disabledFeatures.value
        
        return MidiComprehensiveDiagnosticReport(
            timestamp = System.currentTimeMillis(),
            systemHealth = _systemHealth.value,
            performanceMetrics = performanceMetrics,
            performanceSummary = performanceSummary,
            errorStatistics = errorStatistics,
            recentErrors = errorHistory.take(10),
            optimizationSettings = optimizationSettings,
            appliedOptimizations = appliedOptimizations,
            degradationMode = degradationMode,
            disabledFeatures = disabledFeatures.toList(),
            recommendations = generateSystemRecommendations()
        )
    }
    
    /**
     * Export diagnostic data for support
     */
    fun exportDiagnosticData(): String {
        val report = generateComprehensiveDiagnosticReport()
        
        return buildString {
            appendLine("=== MIDI Diagnostic Report ===")
            appendLine("Generated: ${java.util.Date(report.timestamp)}")
            appendLine()
            
            appendLine("System Health: ${report.systemHealth.overallStatus}")
            appendLine("Performance Rating: ${report.performanceSummary.performanceRating}")
            appendLine("Degradation Mode: ${report.degradationMode}")
            appendLine()
            
            appendLine("Performance Metrics:")
            appendLine("  Average Latency: ${report.performanceMetrics.averageLatency}ms")
            appendLine("  Max Latency: ${report.performanceMetrics.maxLatency}ms")
            appendLine("  Throughput: ${report.performanceMetrics.throughput} msg/sec")
            appendLine("  Dropped Messages: ${report.performanceMetrics.droppedMessages}")
            appendLine("  CPU Usage: ${report.performanceMetrics.cpuUsage}%")
            appendLine("  Memory Usage: ${report.performanceMetrics.memoryUsage}%")
            appendLine()
            
            appendLine("Error Statistics:")
            appendLine("  Total Errors: ${report.errorStatistics.totalErrors}")
            appendLine("  Errors (24h): ${report.errorStatistics.errorsLast24Hours}")
            appendLine("  Most Common: ${report.errorStatistics.mostCommonError ?: "None"}")
            appendLine("  Avg Recovery Time: ${report.errorStatistics.averageRecoveryTime}ms")
            appendLine()
            
            if (report.disabledFeatures.isNotEmpty()) {
                appendLine("Disabled Features:")
                report.disabledFeatures.forEach { feature ->
                    appendLine("  - $feature")
                }
                appendLine()
            }
            
            if (report.appliedOptimizations.isNotEmpty()) {
                appendLine("Applied Optimizations:")
                report.appliedOptimizations.forEach { opt ->
                    appendLine("  - ${opt.description}")
                }
                appendLine()
            }
            
            if (report.recommendations.isNotEmpty()) {
                appendLine("Recommendations:")
                report.recommendations.forEach { rec ->
                    appendLine("  - ${rec.title}: ${rec.description}")
                }
            }
        }
    }
    
    // Private helper methods
    
    private fun startHealthMonitoring() {
        diagnosticsScope.launch {
            while (isRunning) {
                try {
                    updateSystemHealth()
                    delay(HEALTH_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in health monitoring", e)
                }
            }
        }
    }
    
    private fun startAutomaticOptimization() {
        diagnosticsScope.launch {
            while (isRunning) {
                try {
                    performanceOptimizer.applyAutomaticOptimizations()
                    delay(PERFORMANCE_OPTIMIZATION_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in automatic optimization", e)
                }
            }
        }
    }
    
    private fun monitorErrorAlerts() {
        diagnosticsScope.launch {
            errorHandler.systemHealth.collect { health ->
                if (health != com.high.theone.midi.error.MidiSystemHealth.HEALTHY) {
                    notificationManager.showSystemHealthNotification(
                        health,
                        "MIDI system health: $health"
                    )
                }
            }
        }
    }
    
    private fun monitorPerformanceAlerts() {
        diagnosticsScope.launch {
            performanceMonitor.performanceAlerts.collect { alert ->
                Log.w(TAG, "Performance alert: $alert")
                // Could show notifications for critical performance issues
            }
        }
    }
    
    private suspend fun updateSystemHealth() {
        val healthCheck = performHealthCheck()
        
        val status = MidiSystemHealthStatus(
            overallStatus = healthCheck.overallHealth,
            lastCheckTime = System.currentTimeMillis(),
            criticalIssues = healthCheck.issues.count { it.severity == HealthIssueSeverity.CRITICAL },
            warnings = healthCheck.issues.count { it.severity == HealthIssueSeverity.WARNING },
            performanceScore = calculatePerformanceScore(healthCheck.performanceMetrics)
        )
        
        _systemHealth.value = status
    }
    
    private fun generateDiagnosticReport(
        error: MidiError,
        context: MidiErrorContext,
        errorResult: com.high.theone.midi.error.MidiErrorResult
    ): MidiDiagnosticReport {
        return MidiDiagnosticReport(
            id = "report_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            error = error,
            context = context,
            errorResult = errorResult,
            performanceAtTime = performanceMonitor.currentMetrics.value,
            systemHealthAtTime = _systemHealth.value
        )
    }
    
    private fun addDiagnosticReport(report: MidiDiagnosticReport) {
        val current = _diagnosticReports.value.toMutableList()
        current.add(0, report)
        
        // Keep only last 50 reports
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        
        _diagnosticReports.value = current
    }
    
    private fun calculatePerformanceScore(metrics: com.high.theone.midi.performance.MidiPerformanceMetrics): Float {
        var score = 100f
        
        // Deduct for high latency
        if (metrics.averageLatency > 10f) {
            score -= (metrics.averageLatency - 10f) * 2f
        }
        
        // Deduct for dropped messages
        score -= metrics.droppedMessages * 5f
        
        // Deduct for high resource usage
        if (metrics.cpuUsage > 70f) {
            score -= (metrics.cpuUsage - 70f) * 0.5f
        }
        
        return maxOf(0f, minOf(100f, score))
    }
    
    private fun generateSystemRecommendations(): List<MidiSystemRecommendation> {
        val recommendations = mutableListOf<MidiSystemRecommendation>()
        val metrics = performanceMonitor.currentMetrics.value
        
        if (metrics.averageLatency > 15f) {
            recommendations.add(
                MidiSystemRecommendation(
                    priority = com.high.theone.midi.performance.OptimizationPriority.HIGH,
                    title = "Optimize Audio Latency",
                    description = "Reduce buffer sizes and enable low-latency mode",
                    category = "Performance"
                )
            )
        }
        
        if (metrics.droppedMessages > 0) {
            recommendations.add(
                MidiSystemRecommendation(
                    priority = com.high.theone.midi.performance.OptimizationPriority.CRITICAL,
                    title = "Prevent Message Drops",
                    description = "Increase buffer sizes or reduce MIDI input rate",
                    category = "Reliability"
                )
            )
        }
        
        return recommendations
    }
}

// Data classes for diagnostics

data class MidiSystemHealthStatus(
    val overallStatus: MidiOverallHealth = MidiOverallHealth.GOOD,
    val lastCheckTime: Long = 0L,
    val criticalIssues: Int = 0,
    val warnings: Int = 0,
    val performanceScore: Float = 100f
)

data class MidiDiagnosticResult(
    val errorHandled: Boolean,
    val errorRecovered: Boolean,
    val degradationApplied: Boolean,
    val diagnosticReport: MidiDiagnosticReport,
    val recommendedActions: List<String>
)

data class MidiDiagnosticReport(
    val id: String,
    val timestamp: Long,
    val error: MidiError,
    val context: MidiErrorContext,
    val errorResult: com.high.theone.midi.error.MidiErrorResult,
    val performanceAtTime: com.high.theone.midi.performance.MidiPerformanceMetrics,
    val systemHealthAtTime: MidiSystemHealthStatus
)

data class MidiHealthCheckResult(
    val overallHealth: MidiOverallHealth,
    val issues: List<MidiHealthIssue>,
    val performanceMetrics: com.high.theone.midi.performance.MidiPerformanceMetrics,
    val errorStatistics: com.high.theone.midi.error.MidiErrorStatistics,
    val degradationMode: com.high.theone.midi.error.MidiDegradationMode,
    val timestamp: Long
)

data class MidiHealthIssue(
    val severity: HealthIssueSeverity,
    val category: HealthIssueCategory,
    val title: String,
    val description: String,
    val recommendation: String
)

data class MidiComprehensiveDiagnosticReport(
    val timestamp: Long,
    val systemHealth: MidiSystemHealthStatus,
    val performanceMetrics: com.high.theone.midi.performance.MidiPerformanceMetrics,
    val performanceSummary: com.high.theone.midi.performance.MidiPerformanceSummary,
    val errorStatistics: com.high.theone.midi.error.MidiErrorStatistics,
    val recentErrors: List<com.high.theone.midi.error.MidiErrorRecord>,
    val optimizationSettings: com.high.theone.midi.performance.MidiOptimizationSettings,
    val appliedOptimizations: List<com.high.theone.midi.performance.AppliedOptimization>,
    val degradationMode: com.high.theone.midi.error.MidiDegradationMode,
    val disabledFeatures: List<com.high.theone.midi.error.MidiFeature>,
    val recommendations: List<MidiSystemRecommendation>
)

data class MidiSystemRecommendation(
    val priority: com.high.theone.midi.performance.OptimizationPriority,
    val title: String,
    val description: String,
    val category: String
)

enum class MidiOverallHealth {
    GOOD,
    FAIR,
    POOR,
    CRITICAL
}

enum class HealthIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

enum class HealthIssueCategory {
    PERFORMANCE,
    RELIABILITY,
    FUNCTIONALITY,
    CONFIGURATION
}