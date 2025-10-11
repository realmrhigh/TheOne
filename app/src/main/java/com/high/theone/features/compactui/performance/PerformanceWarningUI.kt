package com.high.theone.features.compactui.performance

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI component for displaying performance warnings during recording
 * 
 * Shows warnings, optimization suggestions, and allows user to apply optimizations
 */
@Composable
fun PerformanceWarningUI(
    warnings: List<PerformanceWarning>,
    suggestions: List<OptimizationSuggestion>,
    performanceState: RecordingPerformanceState,
    onApplyOptimization: (OptimizationSuggestionType) -> Unit,
    onDismissWarning: (PerformanceWarningType) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = warnings.isNotEmpty() || suggestions.isNotEmpty(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    PerformanceStatusIndicator(
                        frameRate = performanceState.frameRate,
                        audioLatency = performanceState.audioLatency,
                        memoryPressure = performanceState.memoryPressure
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Performance Warnings
                if (warnings.isNotEmpty()) {
                    Text(
                        text = "Performance Issues",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(warnings) { warning ->
                            PerformanceWarningItem(
                                warning = warning,
                                onDismiss = { onDismissWarning(warning.type) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Optimization Suggestions
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = "Optimization Suggestions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            OptimizationSuggestionItem(
                                suggestion = suggestion,
                                onApply = { onApplyOptimization(suggestion.type) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceStatusIndicator(
    frameRate: Float,
    audioLatency: Float,
    memoryPressure: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame Rate Indicator
        StatusDot(
            color = when {
                frameRate >= 60f -> Color.Green
                frameRate >= 50f -> Color.Yellow
                frameRate >= 30f -> Color(0xFFFF8C00) // Orange
                else -> Color.Red
            },
            label = "${frameRate.toInt()}fps"
        )
        
        // Audio Latency Indicator
        StatusDot(
            color = when {
                audioLatency <= 20f -> Color.Green
                audioLatency <= 40f -> Color.Yellow
                audioLatency <= 80f -> Color(0xFFFF8C00) // Orange
                else -> Color.Red
            },
            label = "${audioLatency.toInt()}ms"
        )
        
        // Memory Pressure Indicator
        StatusDot(
            color = when {
                memoryPressure <= 0.7f -> Color.Green
                memoryPressure <= 0.8f -> Color.Yellow
                memoryPressure <= 0.9f -> Color(0xFFFF8C00) // Orange
                else -> Color.Red
            },
            label = "${(memoryPressure * 100).toInt()}%"
        )
    }
}

@Composable
private fun StatusDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PerformanceWarningItem(
    warning: PerformanceWarning,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (warning.severity) {
                PerformanceWarningSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                PerformanceWarningSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                PerformanceWarningSeverity.MEDIUM -> MaterialTheme.colorScheme.warningContainer
                PerformanceWarningSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getWarningIcon(warning.type),
                    contentDescription = null,
                    tint = when (warning.severity) {
                        PerformanceWarningSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                        PerformanceWarningSeverity.HIGH -> MaterialTheme.colorScheme.error
                        PerformanceWarningSeverity.MEDIUM -> MaterialTheme.colorScheme.onWarningContainer
                        PerformanceWarningSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
                
                Column {
                    Text(
                        text = warning.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = warning.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss warning",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OptimizationSuggestionItem(
    suggestion: OptimizationSuggestion,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getOptimizationIcon(suggestion.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                
                Column {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    
                    Text(
                        text = "Expected: ${suggestion.estimatedImprovement}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            if (suggestion.canAutoApply) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "Manual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun getWarningIcon(type: PerformanceWarningType): ImageVector {
    return when (type) {
        PerformanceWarningType.LOW_FRAME_RATE,
        PerformanceWarningType.CRITICAL_FRAME_RATE -> Icons.Default.Videocam
        PerformanceWarningType.HIGH_AUDIO_LATENCY,
        PerformanceWarningType.CRITICAL_AUDIO_LATENCY -> Icons.Default.VolumeUp
        PerformanceWarningType.HIGH_MEMORY,
        PerformanceWarningType.CRITICAL_MEMORY -> Icons.Default.Memory
        PerformanceWarningType.LONG_RECORDING -> Icons.Default.Schedule
    }
}

private fun getOptimizationIcon(type: OptimizationSuggestionType): ImageVector {
    return when (type) {
        OptimizationSuggestionType.REDUCE_VISUAL_EFFECTS -> Icons.Default.Visibility
        OptimizationSuggestionType.MEMORY_CLEANUP -> Icons.Default.CleaningServices
        OptimizationSuggestionType.AUDIO_OPTIMIZATION -> Icons.Default.Tune
        OptimizationSuggestionType.SIMPLIFY_UI -> Icons.Default.ViewCompact
    }
}

// Extension property for warning container color
private val ColorScheme.warningContainer: Color
    @Composable get() = Color(0xFFFFF3CD)

private val ColorScheme.onWarningContainer: Color
    @Composable get() = Color(0xFF856404)

/**
 * Compact performance indicator for minimal UI impact
 */
@Composable
fun CompactPerformanceIndicator(
    performanceState: RecordingPerformanceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = remember(performanceState) {
        when {
            performanceState.frameRate < 30f || 
            performanceState.audioLatency > 80f ||
            performanceState.memoryPressure > 0.9f -> RecordingPerformanceStatus.CRITICAL
            
            performanceState.frameRate < 50f || 
            performanceState.audioLatency > 40f ||
            performanceState.memoryPressure > 0.8f -> RecordingPerformanceStatus.DEGRADED
            
            performanceState.frameRate < 60f || 
            performanceState.audioLatency > 20f ||
            performanceState.memoryPressure > 0.7f -> RecordingPerformanceStatus.SUBOPTIMAL
            
            else -> RecordingPerformanceStatus.OPTIMAL
        }
    }
    
    val indicatorColor = when (status) {
        RecordingPerformanceStatus.OPTIMAL -> Color.Green
        RecordingPerformanceStatus.SUBOPTIMAL -> Color.Yellow
        RecordingPerformanceStatus.DEGRADED -> Color(0xFFFF8C00) // Orange
        RecordingPerformanceStatus.CRITICAL -> Color.Red
    }
    
    IconButton(
        onClick = onClick,
        modifier = modifier.size(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(indicatorColor)
        )
    }
}