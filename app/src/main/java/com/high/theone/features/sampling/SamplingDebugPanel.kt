package com.high.theone.features.sampling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug panel for displaying comprehensive sampling system diagnostics.
 * Requirements: All requirements (debugging and monitoring UI)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplingDebugPanel(
    debugInfo: SamplingDebugManager.SamplingDebugInfo,
    onRunDiagnostics: () -> Unit,
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Sampling System Debug Panel",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRunDiagnostics,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run Diagnostics")
                        }
                        
                        OutlinedButton(
                            onClick = onExportReport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Report")
                        }
                    }
                }
            }
        }

        // Audio Engine Status
        item {
            DebugSection(
                title = "Audio Engine Status",
                status = if (debugInfo.audioEngineStatus.isInitialized) "Running" else "Not Initialized"
            ) {
                DebugInfoRow("Latency", "${debugInfo.audioEngineStatus.reportedLatency}ms")
                DebugInfoRow("Active Voices", "${debugInfo.audioEngineStatus.activeVoices}")
                DebugInfoRow("Loaded Samples", "${debugInfo.audioEngineStatus.loadedSamples}")
                DebugInfoRow("Engine State", debugInfo.audioEngineStatus.engineState)
            }
        }

        // Performance Status
        item {
            DebugSection(
                title = "Performance Status",
                status = if (debugInfo.performanceStatus.isPerformanceGood) "Good" else "Issues Detected"
            ) {
                DebugInfoRow("Frame Time", "${debugInfo.performanceStatus.averageFrameTime.toInt()}ms")
                DebugInfoRow("Frame Drops", "${debugInfo.performanceStatus.frameDrops}")
                DebugInfoRow("Audio Latency", "${debugInfo.performanceStatus.audioLatency.toInt()}ms")
                DebugInfoRow("Memory Usage", "${debugInfo.performanceStatus.memoryUsage / 1024 / 1024}MB")
                DebugInfoRow("CPU Usage", "${debugInfo.performanceStatus.cpuUsage.toInt()}%")
                
                if (debugInfo.performanceStatus.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Warnings:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    debugInfo.performanceStatus.warnings.forEach { warning ->
                        Text(
                            text = "• ${warning.message}",
                            fontSize = 12.sp,
                            color = when (warning.severity) {
                                PerformanceMonitor.Severity.CRITICAL -> MaterialTheme.colorScheme.error
                                PerformanceMonitor.Severity.WARNING -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        // Cache Status
        item {
            DebugSection(
                title = "Sample Cache Status",
                status = "${debugInfo.cacheStatus.loadedSamples}/${debugInfo.cacheStatus.totalSamples} loaded"
            ) {
                DebugInfoRow("Total Samples", "${debugInfo.cacheStatus.totalSamples}")
                DebugInfoRow("Loaded Samples", "${debugInfo.cacheStatus.loadedSamples}")
                DebugInfoRow("Cache Hit Rate", "${(debugInfo.cacheStatus.cacheHitRate * 100).toInt()}%")
                DebugInfoRow("Avg Load Time", "${debugInfo.cacheStatus.averageLoadTime.toInt()}ms")
                DebugInfoRow("Memory Usage", "${debugInfo.cacheStatus.memoryUsage / 1024 / 1024}MB")
            }
        }

        // Voice Status
        item {
            DebugSection(
                title = "Voice Management",
                status = "${debugInfo.voiceStatus.activeVoices}/${debugInfo.voiceStatus.maxVoices} voices"
            ) {
                DebugInfoRow("Active Voices", "${debugInfo.voiceStatus.activeVoices}")
                DebugInfoRow("Max Voices", "${debugInfo.voiceStatus.maxVoices}")
                DebugInfoRow("Utilization", "${(debugInfo.voiceStatus.voiceUtilization * 100).toInt()}%")
                DebugInfoRow("Oldest Voice", "${debugInfo.voiceStatus.oldestVoiceAge}ms")
                
                if (debugInfo.voiceStatus.voicesByPriority.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Voices by Priority:",
                        fontWeight = FontWeight.Bold
                    )
                    debugInfo.voiceStatus.voicesByPriority.forEach { (priority, count) ->
                        Text(
                            text = "• $priority: $count",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // System Information
        item {
            DebugSection(
                title = "System Information",
                status = debugInfo.systemInfo.deviceModel
            ) {
                DebugInfoRow("Device", debugInfo.systemInfo.deviceModel)
                DebugInfoRow("Android", debugInfo.systemInfo.androidVersion)
                DebugInfoRow("Processors", "${debugInfo.systemInfo.processorCount}")
                DebugInfoRow("Available Memory", "${debugInfo.systemInfo.availableMemory / 1024 / 1024}MB")
                DebugInfoRow("Total Memory", "${debugInfo.systemInfo.totalMemory / 1024 / 1024}MB")
            }
        }

        // Recent Logs
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Recent Diagnostic Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (debugInfo.diagnosticLogs.isEmpty()) {
                        Text(
                            text = "No logs available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        debugInfo.diagnosticLogs.takeLast(10).forEach { log ->
                            LogEntry(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    status: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            content()
        }
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LogEntry(
    log: SamplingDebugManager.DiagnosticLog
) {
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    val time = remember(log.timestamp) { timeFormat.format(java.util.Date(log.timestamp)) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = time,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = log.level.name,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = when (log.level) {
                SamplingDebugManager.LogLevel.ERROR, SamplingDebugManager.LogLevel.CRITICAL -> MaterialTheme.colorScheme.error
                SamplingDebugManager.LogLevel.WARNING -> Color(0xFFFF9800)
                SamplingDebugManager.LogLevel.INFO -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Text(
            text = "[${log.category}]",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = log.message,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Diagnostic results display component.
 */
@Composable
fun DiagnosticResultsPanel(
    report: SamplingDebugManager.DiagnosticReport,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Diagnostic Results",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Passed: ${report.passedTests}/${report.totalTests}",
                            color = if (report.overallPassed) Color.Green else MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            text = if (report.overallPassed) "✓ All tests passed" else "⚠ Issues detected",
                            color = if (report.overallPassed) Color.Green else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Test Categories
        items(
            listOf(
                "Audio Engine Tests" to report.audioEngineTests,
                "Sample Loading Tests" to report.sampleLoadingTests,
                "Performance Tests" to report.performanceTests,
                "Memory Tests" to report.memoryTests
            )
        ) { (category, tests) ->
            TestCategoryCard(
                category = category,
                tests = tests
            )
        }
    }
}

@Composable
private fun TestCategoryCard(
    category: String,
    tests: List<SamplingDebugManager.TestResult>
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            tests.forEach { test ->
                TestResultRow(test = test)
            }
        }
    }
}

@Composable
private fun TestResultRow(
    test: SamplingDebugManager.TestResult
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = test.name,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (test.details != null) {
                Text(
                    text = test.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = if (test.passed) "✓" else "✗",
                color = if (test.passed) Color.Green else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = test.value,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                text = "Expected: ${test.expected}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}