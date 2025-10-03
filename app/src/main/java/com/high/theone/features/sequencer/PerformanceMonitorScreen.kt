package com.high.theone.features.sequencer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.sin

/**
 * Performance monitoring screen showing real-time performance metrics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceMonitorScreen(
    performanceMetrics: PerformanceMetrics,
    onOptimizationApply: () -> Unit,
    onResetCounters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Timing", "System", "Recommendations")
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Monitor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row {
                        IconButton(onClick = onResetCounters) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Counters")
                        }
                        
                        IconButton(onClick = onOptimizationApply) {
                            Icon(Icons.Default.Tune, contentDescription = "Apply Optimizations")
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                // Status indicator
                PerformanceStatusIndicator(
                    metrics = performanceMetrics,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                // Tab row
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab content
                when (selectedTab) {
                    0 -> OverviewTab(performanceMetrics)
                    1 -> TimingTab(performanceMetrics)
                    2 -> SystemTab(performanceMetrics)
                    3 -> RecommendationsTab(performanceMetrics)
                }
            }
        }
    }
}

@Composable
private fun PerformanceStatusIndicator(
    metrics: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    val overallHealth = calculateOverallHealth(metrics)
    val healthColor = when {
        overallHealth >= 80f -> MaterialTheme.colorScheme.primary
        overallHealth >= 60f -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = healthColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Overall Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = getHealthDescription(overallHealth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            CircularProgressIndicator(
                progress = overallHealth / 100f,
                modifier = Modifier.size(48.dp),
                color = healthColor,
                strokeWidth = 4.dp
            )
        }
    }
}

@Composable
private fun OverviewTab(metrics: PerformanceMetrics) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MetricCard(
                title = "Timing Accuracy",
                value = "${String.format("%.1f", metrics.timingAccuracy)}%",
                icon = Icons.Default.Timer,
                color = getMetricColor(metrics.timingAccuracy, 95f, 90f)
            )
        }
        
        item {
            MetricCard(
                title = "Average Jitter",
                value = "${metrics.averageJitter / 1000}ms",
                icon = Icons.Default.GraphicEq,
                color = getMetricColor(100f - (metrics.averageJitter / 100f), 95f, 90f)
            )
        }
        
        item {
            MetricCard(
                title = "Audio Latency",
                value = "${metrics.averageLatency / 1000}ms",
                icon = Icons.Default.Headphones,
                color = getLatencyColor(metrics.averageLatency)
            )
        }
        
        item {
            MetricCard(
                title = "CPU Usage",
                value = "${String.format("%.1f", metrics.cpuUsage)}%",
                icon = Icons.Default.Memory,
                color = getMetricColor(100f - metrics.cpuUsage, 70f, 50f)
            )
        }
        
        item {
            MetricCard(
                title = "Memory Usage",
                value = "${String.format("%.1f", metrics.memoryUsagePercent)}%",
                icon = Icons.Default.Storage,
                color = getMetricColor(100f - metrics.memoryUsagePercent, 70f, 50f)
            )
        }
        
        item {
            TriggerStatisticsCard(metrics)
        }
    }
}

@Composable
private fun TimingTab(metrics: PerformanceMetrics) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timing accuracy chart
        TimingAccuracyChart(
            accuracy = metrics.timingAccuracy,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
        
        // Jitter visualization
        JitterVisualization(
            averageJitter = metrics.averageJitter,
            maxJitter = metrics.maxJitter,
            minJitter = metrics.minJitter,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
        
        // Detailed timing metrics
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Timing Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                TimingDetailRow("Average Jitter", "${metrics.averageJitter / 1000}ms")
                TimingDetailRow("Max Jitter", "${metrics.maxJitter / 1000}ms")
                TimingDetailRow("Min Jitter", "${metrics.minJitter / 1000}ms")
                TimingDetailRow("Total Triggers", "${metrics.totalTriggers}")
                TimingDetailRow("Missed Triggers", "${metrics.missedTriggers}")
                TimingDetailRow("Late Triggers", "${metrics.lateTriggers}")
                TimingDetailRow("Early Triggers", "${metrics.earlyTriggers}")
            }
        }
    }
}

@Composable
private fun SystemTab(metrics: PerformanceMetrics) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CPU usage chart
        SystemUsageChart(
            cpuUsage = metrics.cpuUsage,
            memoryUsage = metrics.memoryUsagePercent,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
        
        // Memory details
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Memory Usage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                SystemDetailRow("Used Memory", "${metrics.memoryUsage / 1024 / 1024}MB")
                SystemDetailRow("Memory %", "${String.format("%.1f", metrics.memoryUsagePercent)}%")
            }
        }
        
        // Audio latency details
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Audio Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                SystemDetailRow("Average Latency", "${metrics.averageLatency / 1000}ms")
                SystemDetailRow("Min Latency", "${metrics.minLatency / 1000}ms")
                SystemDetailRow("Max Latency", "${metrics.maxLatency / 1000}ms")
                SystemDetailRow("Latency Jitter", "${metrics.latencyJitter / 1000}ms")
            }
        }
    }
}

@Composable
private fun RecommendationsTab(metrics: PerformanceMetrics) {
    val recommendations = generateRecommendations(metrics)
    
    if (recommendations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Performance is optimal!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "No recommendations at this time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recommendations) { recommendation ->
                RecommendationCard(recommendation)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TriggerStatisticsCard(metrics: PerformanceMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Trigger Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem("Total", "${metrics.totalTriggers}")
                StatisticItem("Missed", "${metrics.missedTriggers}")
                StatisticItem("Late", "${metrics.lateTriggers}")
                StatisticItem("Early", "${metrics.earlyTriggers}")
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimingAccuracyChart(
    accuracy: Float,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Timing Accuracy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = accuracy / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = getMetricColor(accuracy, 95f, 90f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${String.format("%.1f", accuracy)}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun JitterVisualization(
    averageJitter: Long,
    maxJitter: Long,
    minJitter: Long,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Timing Jitter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                drawJitterWaveform(averageJitter, maxJitter, minJitter)
            }
        }
    }
}

@Composable
private fun SystemUsageChart(
    cpuUsage: Float,
    memoryUsage: Float,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "System Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // CPU Usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CPU")
                Text("${String.format("%.1f", cpuUsage)}%")
            }
            
            LinearProgressIndicator(
                progress = cpuUsage / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = getMetricColor(100f - cpuUsage, 70f, 50f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Memory Usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Memory")
                Text("${String.format("%.1f", memoryUsage)}%")
            }
            
            LinearProgressIndicator(
                progress = memoryUsage / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = getMetricColor(100f - memoryUsage, 70f, 50f)
            )
        }
    }
}

@Composable
private fun TimingDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SystemDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecommendationCard(recommendation: PerformanceRecommendation) {
    val severityColor = when (recommendation.severity) {
        "High" -> MaterialTheme.colorScheme.error
        "Medium" -> Color(0xFFFF9800) // Orange
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (recommendation.severity) {
                        "High" -> Icons.Default.Warning
                        "Medium" -> Icons.Default.Info
                        else -> Icons.Default.Lightbulb
                    },
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = recommendation.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = severityColor
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = recommendation.severity,
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor
                )
            }
            
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = recommendation.suggestion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawJitterWaveform(
    averageJitter: Long,
    maxJitter: Long,
    minJitter: Long
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    
    val path = Path()
    val points = 50
    
    for (i in 0 until points) {
        val x = (i.toFloat() / points) * width
        val jitterAmount = (averageJitter / 10000f) * sin(i * 0.5f) * (height / 4f)
        val y = centerY + jitterAmount
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = Color(0xFF2196F3),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
}

private fun calculateOverallHealth(metrics: PerformanceMetrics): Float {
    val accuracyScore = metrics.timingAccuracy
    val jitterScore = (100f - (metrics.averageJitter / 100f)).coerceIn(0f, 100f)
    val cpuScore = (100f - metrics.cpuUsage).coerceIn(0f, 100f)
    val memoryScore = (100f - metrics.memoryUsagePercent).coerceIn(0f, 100f)
    val latencyScore = (100f - (metrics.averageLatency / 1000f)).coerceIn(0f, 100f)
    
    return (accuracyScore + jitterScore + cpuScore + memoryScore + latencyScore) / 5f
}

private fun getHealthDescription(health: Float): String {
    return when {
        health >= 80f -> "Excellent"
        health >= 60f -> "Good"
        health >= 40f -> "Fair"
        else -> "Poor"
    }
}

private fun getMetricColor(value: Float, goodThreshold: Float, okThreshold: Float): Color {
    return when {
        value >= goodThreshold -> Color(0xFF4CAF50) // Green
        value >= okThreshold -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

private fun getLatencyColor(latency: Long): Color {
    return when {
        latency <= 20000L -> Color(0xFF4CAF50) // Green - under 20ms
        latency <= 50000L -> Color(0xFFFF9800) // Orange - 20-50ms
        else -> Color(0xFFF44336) // Red - over 50ms
    }
}

private fun generateRecommendations(metrics: PerformanceMetrics): List<PerformanceRecommendation> {
    val recommendations = mutableListOf<PerformanceRecommendation>()
    
    if (metrics.timingAccuracy < 95f) {
        recommendations.add(
            PerformanceRecommendation(
                category = "Timing",
                severity = if (metrics.timingAccuracy < 90f) "High" else "Medium",
                message = "Low timing accuracy detected",
                suggestion = "Reduce system load or increase audio buffer size"
            )
        )
    }
    
    if (metrics.averageJitter > 5000L) {
        recommendations.add(
            PerformanceRecommendation(
                category = "Jitter",
                severity = if (metrics.averageJitter > 10000L) "High" else "Medium",
                message = "High timing jitter detected",
                suggestion = "Close other applications or reduce pattern complexity"
            )
        )
    }
    
    if (metrics.cpuUsage > 70f) {
        recommendations.add(
            PerformanceRecommendation(
                category = "CPU",
                severity = if (metrics.cpuUsage > 85f) "High" else "Medium",
                message = "High CPU usage detected",
                suggestion = "Reduce polyphony or pattern complexity"
            )
        )
    }
    
    if (metrics.memoryUsagePercent > 80f) {
        recommendations.add(
            PerformanceRecommendation(
                category = "Memory",
                severity = if (metrics.memoryUsagePercent > 90f) "High" else "Medium",
                message = "High memory usage detected",
                suggestion = "Clear sample cache or reduce cached patterns"
            )
        )
    }
    
    return recommendations
}