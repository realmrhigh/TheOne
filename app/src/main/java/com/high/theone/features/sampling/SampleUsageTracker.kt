package com.high.theone.features.sampling

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.PadState
import com.high.theone.model.SampleMetadata
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sample usage tracking and analytics component.
 * Provides insights into sample usage patterns across pads and projects.
 * 
 * Requirements: 7.3 (sample usage tracking across pads)
 */
@Composable
fun SampleUsageTracker(
    samples: List<SampleMetadata>,
    pads: List<PadState>,
    usageStats: Map<String, SampleUsageStats>,
    onShowSampleDetails: (SampleMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with summary stats
        UsageOverviewCard(
            samples = samples,
            pads = pads,
            usageStats = usageStats
        )
        
        // Usage breakdown by sample
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sample Usage Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val samplesWithUsage = samples.map { sample ->
                        val stats = usageStats[sample.id.toString()] ?: SampleUsageStats()
                        val assignedPads = pads.filter { it.sampleId == sample.id.toString() }
                        sample to Pair(stats, assignedPads)
                    }.sortedByDescending { it.second.first.totalTriggers }
                    
                    items(samplesWithUsage) { (sample, usageData) ->
                        val (stats, assignedPads) = usageData
                        SampleUsageItem(
                            sample = sample,
                            stats = stats,
                            assignedPads = assignedPads,
                            onClick = { onShowSampleDetails(sample) }
                        )
                    }
                    
                    if (samples.isEmpty()) {
                        item {
                            EmptyUsageState()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overview card with summary statistics.
 */
@Composable
private fun UsageOverviewCard(
    samples: List<SampleMetadata>,
    pads: List<PadState>,
    usageStats: Map<String, SampleUsageStats>
) {
    val totalSamples = samples.size
    val assignedSamples = pads.mapNotNull { it.sampleId }.distinct().size
    val totalTriggers = usageStats.values.sumOf { it.totalTriggers }
    val averageTriggersPerSample = if (assignedSamples > 0) totalTriggers / assignedSamples else 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Usage Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                UsageStatItem(
                    label = "Total Samples",
                    value = totalSamples.toString(),
                    icon = Icons.Default.MusicNote
                )
                
                UsageStatItem(
                    label = "In Use",
                    value = assignedSamples.toString(),
                    icon = Icons.Default.Assignment
                )
                
                UsageStatItem(
                    label = "Total Triggers",
                    value = totalTriggers.toString(),
                    icon = Icons.Default.TouchApp
                )
                
                UsageStatItem(
                    label = "Avg/Sample",
                    value = averageTriggersPerSample.toString(),
                    icon = Icons.Default.TrendingUp
                )
            }
        }
    }
}

/**
 * Individual usage statistic item.
 */
@Composable
private fun UsageStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Individual sample usage item.
 */
@Composable
private fun SampleUsageItem(
    sample: SampleMetadata,
    stats: SampleUsageStats,
    assignedPads: List<PadState>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (assignedPads.isNotEmpty()) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sample name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sample.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = if (assignedPads.isNotEmpty()) {
                            "Assigned to ${assignedPads.size} pad${if (assignedPads.size == 1) "" else "s"}"
                        } else {
                            "Not assigned"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Usage indicator
                UsageIndicator(
                    triggerCount = stats.totalTriggers,
                    isAssigned = assignedPads.isNotEmpty()
                )
            }
            
            // Usage statistics
            if (stats.totalTriggers > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UsageMetric(
                        label = "Triggers",
                        value = stats.totalTriggers.toString(),
                        icon = Icons.Default.TouchApp
                    )
                    
                    UsageMetric(
                        label = "Last Used",
                        value = stats.lastUsed?.let { formatRelativeTime(it) } ?: "Never",
                        icon = Icons.Default.Schedule
                    )
                    
                    if (stats.averageVelocity > 0f) {
                        UsageMetric(
                            label = "Avg Velocity",
                            value = "${(stats.averageVelocity * 100).toInt()}%",
                            icon = Icons.Default.VolumeUp
                        )
                    }
                }
            }
            
            // Assigned pads
            if (assignedPads.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Pads: ${assignedPads.joinToString(", ") { "${it.index + 1}" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Usage indicator badge.
 */
@Composable
private fun UsageIndicator(
    triggerCount: Int,
    isAssigned: Boolean
) {
    val (color, icon, text) = when {
        !isAssigned -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Default.RadioButtonUnchecked,
            "Unused"
        )
        triggerCount == 0 -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Default.Assignment,
            "Assigned"
        )
        triggerCount < 10 -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Default.TrendingUp,
            "Low Use"
        )
        triggerCount < 50 -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.TrendingUp,
            "Active"
        )
        else -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.Whatshot,
            "Heavy Use"
        )
    }
    
    AssistChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color
        )
    )
}

/**
 * Individual usage metric display.
 */
@Composable
private fun UsageMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state when no samples are available.
 */
@Composable
private fun EmptyUsageState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "No Usage Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Record or import samples to see usage statistics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Detailed usage analytics dialog.
 */
@Composable
fun SampleUsageAnalyticsDialog(
    sample: SampleMetadata,
    stats: SampleUsageStats,
    assignedPads: List<PadState>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usage Analytics",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = sample.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                // Detailed statistics
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        DetailedUsageStats(
                            stats = stats,
                            assignedPads = assignedPads
                        )
                    }
                }
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Detailed usage statistics section.
 */
@Composable
private fun DetailedUsageStats(
    stats: SampleUsageStats,
    assignedPads: List<PadState>
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Basic stats
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Usage Statistics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                DetailedStatRow("Total Triggers", stats.totalTriggers.toString())
                DetailedStatRow("Average Velocity", "${(stats.averageVelocity * 100).toInt()}%")
                DetailedStatRow("First Used", stats.firstUsed?.let { formatDate(it) } ?: "Never")
                DetailedStatRow("Last Used", stats.lastUsed?.let { formatDate(it) } ?: "Never")
            }
        }
        
        // Pad assignments
        if (assignedPads.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pad Assignments",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    assignedPads.forEach { pad ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pad ${pad.index + 1}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vol: ${(pad.volume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = pad.playbackMode.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detailed statistic row.
 */
@Composable
private fun DetailedStatRow(
    label: String,
    value: String
) {
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

/**
 * Data class for sample usage statistics.
 */
data class SampleUsageStats(
    val totalTriggers: Int = 0,
    val averageVelocity: Float = 0f,
    val firstUsed: Long? = null,
    val lastUsed: Long? = null,
    val triggersByPad: Map<Int, Int> = emptyMap()
)

/**
 * Format timestamp to relative time string.
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> formatDate(timestamp)
    }
}

/**
 * Format timestamp to date string.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}