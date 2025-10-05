package com.high.theone.features.midi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.high.theone.midi.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * MIDI Monitor Screen for diagnostics and troubleshooting
 * 
 * Provides:
 * - Real-time MIDI message monitoring display
 * - MIDI statistics and performance metrics
 * - MIDI troubleshooting and diagnostic tools
 * 
 * Requirements: 7.3, 7.4, 7.5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiMonitorScreen(
    onNavigateBack: () -> Unit,
    viewModel: MidiMonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.midiMessages.size) {
        if (uiState.autoScroll && uiState.midiMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.midiMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MIDI Monitor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onToggleMonitoring() }
                    ) {
                        Icon(
                            if (uiState.isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isMonitoring) "Pause" else "Start",
                            tint = if (uiState.isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.onClearMessages() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                    IconButton(onClick = { viewModel.onToggleSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics Panel
            MidiStatisticsPanel(
                statistics = uiState.statistics,
                modifier = Modifier.padding(16.dp)
            )

            // Filter and Settings Panel
            AnimatedVisibility(visible = uiState.showSettings) {
                MidiMonitorSettingsPanel(
                    settings = uiState.settings,
                    onUpdateSettings = { viewModel.onUpdateSettings(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIDI Messages (${uiState.midiMessages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    FilterChip(
                        selected = uiState.autoScroll,
                        onClick = { viewModel.onToggleAutoScroll() },
                        label = { Text("Auto-scroll") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MIDI Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = uiState.filteredMessages,
                    key = { it.id }
                ) { message ->
                    MidiMessageItem(
                        message = message,
                        showTimestamp = uiState.settings.showTimestamp,
                        showRawData = uiState.settings.showRawData
                    )
                }
                
                if (uiState.filteredMessages.isEmpty() && uiState.isMonitoring) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Waiting for MIDI messages...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Play your MIDI device to see messages here",
                                    style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun MidiStatisticsPanel(
    statistics: MidiStatistics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Input Messages",
                    value = statistics.inputMessageCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatisticItem(
                    label = "Output Messages", 
                    value = statistics.outputMessageCount.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )
                StatisticItem(
                    label = "Avg Latency",
                    value = "${String.format("%.1f", statistics.averageInputLatency)}ms",
                    color = if (statistics.averageInputLatency > 10f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                )
                StatisticItem(
                    label = "Dropped",
                    value = statistics.droppedMessageCount.toString(),
                    color = if (statistics.droppedMessageCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            
            statistics.lastErrorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Last Error: $error",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MidiMonitorSettingsPanel(
    settings: MidiMonitorSettings,
    onUpdateSettings: (MidiMonitorSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Monitor Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Message Type Filters
            Text(
                text = "Message Types",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(MidiMessageType.values()) { messageType ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = messageType in settings.enabledMessageTypes,
                            onCheckedChange = { enabled ->
                                val updatedTypes = if (enabled) {
                                    settings.enabledMessageTypes + messageType
                                } else {
                                    settings.enabledMessageTypes - messageType
                                }
                                onUpdateSettings(settings.copy(enabledMessageTypes = updatedTypes))
                            }
                        )
                        Text(
                            text = messageType.name,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.showTimestamp,
                    onCheckedChange = { onUpdateSettings(settings.copy(showTimestamp = it)) }
                )
                Text(
                    text = "Show Timestamp",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = settings.showRawData,
                    onCheckedChange = { onUpdateSettings(settings.copy(showRawData = it)) }
                )
                Text(
                    text = "Show Raw Data",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            // Max Messages Slider
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Max Messages: ${settings.maxMessages}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = settings.maxMessages.toFloat(),
                onValueChange = { onUpdateSettings(settings.copy(maxMessages = it.toInt())) },
                valueRange = 100f..2000f,
                steps = 18
            )
        }
    }
}

@Composable
private fun MidiMessageItem(
    message: MidiMessageDisplay,
    showTimestamp: Boolean,
    showRawData: Boolean
) {
    val backgroundColor = when (message.type) {
        MidiMessageType.NOTE_ON -> MaterialTheme.colorScheme.primaryContainer
        MidiMessageType.NOTE_OFF -> MaterialTheme.colorScheme.secondaryContainer
        MidiMessageType.CONTROL_CHANGE -> MaterialTheme.colorScheme.tertiaryContainer
        MidiMessageType.PITCH_BEND -> MaterialTheme.colorScheme.surfaceVariant
        MidiMessageType.CLOCK -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Message Type Badge
                    Badge(
                        containerColor = when (message.direction) {
                            MidiDirection.INPUT -> MaterialTheme.colorScheme.primary
                            MidiDirection.OUTPUT -> MaterialTheme.colorScheme.secondary
                        }
                    ) {
                        Text(
                            text = if (message.direction == MidiDirection.INPUT) "IN" else "OUT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = message.type.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (showTimestamp) {
                    Text(
                        text = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                            .format(Date(message.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Message Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (showRawData) {
                        Text(
                            text = "Raw: ${message.rawData}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Channel and Device Info
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (message.channel != null) {
                        Text(
                            text = "CH ${message.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    message.deviceName?.let { deviceName ->
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}