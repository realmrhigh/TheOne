package com.high.theone.features.compactui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.TransportState
import com.high.theone.model.MidiSyncStatus
import com.high.theone.model.AudioLevels
import com.high.theone.features.compactui.accessibility.*
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.features.compactui.animations.VisualFeedbackSystem
import com.high.theone.features.compactui.animations.LoadingStates

/**
 * Transport control bar component with play/stop/record buttons, BPM controls, and status indicators
 * Implements Material 3 design with visual feedback for button states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportControlBar(
    transportState: TransportState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport buttons section
            TransportButtons(
                isPlaying = transportState.isPlaying,
                isRecording = transportState.isRecording,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onRecord = onRecord,
                modifier = Modifier.weight(1f)
            )
            
            // Status indicators
            StatusIndicators(
                midiSyncStatus = transportState.midiSyncStatus,
                audioLevels = transportState.audioLevels,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Transport control buttons with visual feedback
 */
@Composable
private fun TransportButtons(
    isPlaying: Boolean,
    isRecording: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        TransportButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            isActive = isPlaying,
            onClick = onPlayPause,
            contentDescription = if (isPlaying) "Pause" else "Play",
            activeColor = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Stop button
        TransportButton(
            icon = Icons.Default.Stop,
            isActive = false,
            onClick = onStop,
            contentDescription = "Stop",
            activeColor = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Record button
        TransportButton(
            icon = Icons.Default.FiberManualRecord,
            isActive = isRecording,
            onClick = onRecord,
            contentDescription = if (isRecording) "Stop Recording" else "Record",
            activeColor = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Individual transport button with visual feedback
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    // Animation for button state changes
    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "button_color"
    )
    
    MicroInteractions.AnimatedIconButton(
        onClick = onClick,
        modifier = modifier
            .ensureMinimumTouchTarget()
            .semantics { 
                this.contentDescription = contentDescription
                role = Role.Button
            }
    ) {
        Surface(
            color = if (isActive) {
                activeColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * BPM display and adjustment controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BpmControls(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Decrease BPM button
        MicroInteractions.AnimatedIconButton(
            onClick = { onBpmChange((bpm - 1).coerceAtLeast(60)) },
            modifier = Modifier
                .ensureMinimumTouchTarget()
                .semantics { 
                    contentDescription = "Decrease tempo, current $bpm BPM"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // BPM display
        Card(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { 
                    contentDescription = "Tempo: $bpm beats per minute"
                    role = Role.Image
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = bpm.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        // Increase BPM button
        MicroInteractions.AnimatedIconButton(
            onClick = { onBpmChange((bpm + 1).coerceAtMost(200)) },
            modifier = Modifier
                .ensureMinimumTouchTarget()
                .semantics { 
                    contentDescription = "Increase tempo, current $bpm BPM"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Status indicators for MIDI sync, battery, performance and audio levels
 */
@Composable
private fun StatusIndicators(
    midiSyncStatus: MidiSyncStatus,
    audioLevels: AudioLevels,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery status indicator
        BatteryStatusIndicator(
            modifier = Modifier.padding(end = 4.dp)
        )
        
        // Performance status indicator
        PerformanceStatusIndicator(
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // MIDI sync status indicator
        LoadingStates.MidiConnectionIndicator(
            isConnecting = midiSyncStatus == MidiSyncStatus.SYNCING,
            isConnected = midiSyncStatus == MidiSyncStatus.SYNCED || midiSyncStatus == MidiSyncStatus.CONNECTED,
            deviceName = if (midiSyncStatus == MidiSyncStatus.SYNCED) "MIDI" else "",
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // Audio level meters
        AudioLevelIndicator(
            levels = audioLevels
        )
        
        // Settings button
        MicroInteractions.AnimatedIconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .ensureMinimumTouchTarget()
                .semantics { 
                    contentDescription = "Open project settings"
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * MIDI synchronization status indicator
 */
@Composable
private fun MidiSyncIndicator(
    status: MidiSyncStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon, description) = when (status) {
        MidiSyncStatus.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.outline,
            Icons.Default.MusicOff,
            "MIDI Disconnected"
        )
        MidiSyncStatus.CONNECTED -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.MusicNote,
            "MIDI Connected"
        )
        MidiSyncStatus.SYNCING -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Sync,
            "MIDI Syncing"
        )
        MidiSyncStatus.SYNCED -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.SyncAlt,
            "MIDI Synced"
        )
        MidiSyncStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Default.SyncProblem,
            "MIDI Error"
        )
    }
    
    // Pulsing animation for syncing state
    val alpha by animateFloatAsState(
        targetValue = if (status == MidiSyncStatus.SYNCING) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "midi_sync_pulse"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = color.copy(alpha = alpha),
        modifier = modifier
            .size(20.dp)
            .semantics { 
                this.contentDescription = description
                role = Role.Image
                stateDescription = when (status) {
                    MidiSyncStatus.SYNCING -> "Syncing"
                    MidiSyncStatus.SYNCED -> "Synced"
                    MidiSyncStatus.CONNECTED -> "Connected"
                    MidiSyncStatus.ERROR -> "Error"
                    else -> "Disconnected"
                }
            }
    )
}

/**
 * Detailed audio level meters with master and input levels
 */
@Composable
private fun AudioLevelIndicator(
    levels: AudioLevels,
    modifier: Modifier = Modifier
) {
    // Animate level changes
    val animatedMasterLevel by animateFloatAsState(
        targetValue = levels.masterLevel,
        animationSpec = tween(durationMillis = 50),
        label = "master_level"
    )
    
    val animatedInputLevel by animateFloatAsState(
        targetValue = levels.inputLevel,
        animationSpec = tween(durationMillis = 50),
        label = "input_level"
    )
    
    val animatedPeakLevel by animateFloatAsState(
        targetValue = levels.peakLevel,
        animationSpec = tween(durationMillis = 100),
        label = "peak_level"
    )
    
    // Clip indicator animation
    val clipAlpha by animateFloatAsState(
        targetValue = if (levels.clipIndicator) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "clip_alpha"
    )
    
    Row(
        modifier = modifier
            .audioLevelSemantics(
                "Master and Input",
                maxOf(animatedMasterLevel, animatedInputLevel),
                levels.clipIndicator
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Master level meter
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "M",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp
            )
            VisualFeedbackSystem.AudioLevelMeter(
                level = animatedMasterLevel,
                modifier = Modifier.size(width = 8.dp, height = 20.dp)
            )
        }
        
        // Input level meter
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "I",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp
            )
            VisualFeedbackSystem.AudioLevelMeter(
                level = animatedInputLevel,
                modifier = Modifier.size(width = 8.dp, height = 20.dp)
            )
        }
        
        // Clip indicator
        if (levels.clipIndicator) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = clipAlpha)
                    )
            )
        }
    }
}

/**
 * Individual audio level meter bar
 */
@Composable
private fun AudioLevelMeter(
    level: Float,
    peakLevel: Float,
    isClipping: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Background segments
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(8) { index ->
                val segmentLevel = (7 - index) / 7f
                val isActive = level >= segmentLevel
                val isPeak = peakLevel >= segmentLevel && peakLevel < segmentLevel + 0.125f
                
                val segmentColor = when {
                    isClipping && segmentLevel > 0.85f -> MaterialTheme.colorScheme.error
                    segmentLevel > 0.7f -> MaterialTheme.colorScheme.tertiary
                    segmentLevel > 0.4f -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            if (isActive || isPeak) {
                                segmentColor
                            } else {
                                segmentColor.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }
    }
}

/**
 * Battery status indicator
 */
@Composable
private fun BatteryStatusIndicator(
    modifier: Modifier = Modifier
) {
    // In a real implementation, this would get battery level from system
    // For now, we'll use a placeholder
    val batteryLevel = remember { 0.75f } // 75% battery
    val isCharging = remember { false }
    
    val batteryIcon = when {
        isCharging -> Icons.Default.BatteryChargingFull
        batteryLevel > 0.9f -> Icons.Default.BatteryFull
        batteryLevel > 0.6f -> Icons.Default.Battery6Bar
        batteryLevel > 0.3f -> Icons.Default.Battery3Bar
        batteryLevel > 0.1f -> Icons.Default.Battery2Bar
        else -> Icons.Default.Battery1Bar
    }
    
    val batteryColor = when {
        isCharging -> MaterialTheme.colorScheme.primary
        batteryLevel > 0.2f -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    
    Icon(
        imageVector = batteryIcon,
        contentDescription = "Battery: ${(batteryLevel * 100).toInt()}%${if (isCharging) " (Charging)" else ""}",
        tint = batteryColor,
        modifier = modifier
            .size(16.dp)
            .semantics { 
                this.contentDescription = "Battery: ${(batteryLevel * 100).toInt()}%${if (isCharging) " (Charging)" else ""}"
                role = Role.Image
                stateDescription = when {
                    isCharging -> "Charging"
                    batteryLevel > 0.2f -> "Normal"
                    else -> "Low battery"
                }
            }
    )
}

/**
 * Performance status indicator showing CPU/memory usage
 */
@Composable
private fun PerformanceStatusIndicator(
    modifier: Modifier = Modifier
) {
    // In a real implementation, this would get performance metrics from PerformanceMonitor
    // For now, we'll use placeholder values
    val cpuUsage = remember { 0.45f } // 45% CPU usage
    val memoryPressure = remember { false }
    
    val performanceColor = when {
        memoryPressure || cpuUsage > 0.8f -> MaterialTheme.colorScheme.error
        cpuUsage > 0.6f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    // Pulsing animation for high usage
    val alpha by animateFloatAsState(
        targetValue = if (cpuUsage > 0.8f) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "performance_pulse"
    )
    
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(performanceColor.copy(alpha = 0.2f))
            .semantics { 
                contentDescription = "Performance: CPU ${(cpuUsage * 100).toInt()}%${if (memoryPressure) ", Memory Pressure" else ""}"
                role = Role.Image
                stateDescription = when {
                    memoryPressure || cpuUsage > 0.8f -> "High usage"
                    cpuUsage > 0.6f -> "Medium usage"
                    else -> "Normal"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(cpuUsage.coerceIn(0.1f, 1f))
                .clip(CircleShape)
                .background(performanceColor.copy(alpha = alpha))
        )
        
        // Center indicator
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransportControlBarPreview() {
    MaterialTheme {
        TransportControlBar(
            transportState = TransportState(
                isPlaying = true,
                isRecording = false,
                bpm = 120,
                midiSyncStatus = MidiSyncStatus.SYNCED,
                audioLevels = AudioLevels(
                    masterLevel = 0.7f,
                    inputLevel = 0.5f,
                    peakLevel = 0.8f,
                    clipIndicator = false
                )
            ),
            onPlayPause = {},
            onStop = {},
            onRecord = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransportControlBarRecordingPreview() {
    MaterialTheme {
        TransportControlBar(
            transportState = TransportState(
                isPlaying = false,
                isRecording = true,
                bpm = 140,
                midiSyncStatus = MidiSyncStatus.SYNCING,
                audioLevels = AudioLevels(
                    masterLevel = 0.9f,
                    inputLevel = 0.8f,
                    peakLevel = 0.95f,
                    clipIndicator = true
                )
            ),
            onPlayPause = {},
            onStop = {},
            onRecord = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransportControlBarCompactPreview() {
    MaterialTheme {
        Box(modifier = Modifier.width(300.dp)) {
            TransportControlBar(
                transportState = TransportState(
                    isPlaying = false,
                    isRecording = false,
                    bpm = 85,
                    midiSyncStatus = MidiSyncStatus.DISCONNECTED,
                    audioLevels = AudioLevels(
                        masterLevel = 0.3f,
                        inputLevel = 0.1f,
                        peakLevel = 0.4f,
                        clipIndicator = false
                    )
                ),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onSettingsClick = {}
            )
        }
    }
}