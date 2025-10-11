package com.high.theone.features.compactui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.high.theone.model.*
import com.high.theone.ui.layout.ResponsiveLayoutUtils
import com.high.theone.ui.layout.PanelDimensions
import com.high.theone.features.compactui.animations.EnhancedAnimationIntegration
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.features.compactui.animations.AnimationSystem

/**
 * Responsive recording panel that adapts to different screen sizes and orientations
 * 
 * Features:
 * - Bottom sheet behavior for portrait mode
 * - Side panel layout for landscape and tablet modes
 * - Adaptive content layout based on available space
 * - Smooth transitions between orientations
 * 
 * Requirements: 3.3 (responsive design), 2.2 (accessibility)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsiveRecordingPanel(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    isVisible: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    // Calculate panel dimensions based on screen configuration
    val panelDimensions = ResponsiveLayoutUtils.calculatePanelDimensions(
        layoutMode = screenConfiguration.layoutMode,
        panelType = PanelType.SAMPLING,
        screenWidth = screenConfiguration.screenWidth,
        screenHeight = screenConfiguration.screenHeight
    )
    
    // Determine panel behavior based on screen configuration
    when (screenConfiguration.layoutMode) {
        LayoutMode.COMPACT_PORTRAIT, LayoutMode.STANDARD_PORTRAIT -> {
            // Bottom sheet behavior for portrait modes
            BottomSheetRecordingPanel(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                panelDimensions = panelDimensions,
                isVisible = isVisible,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
        LayoutMode.LANDSCAPE -> {
            // Side panel for landscape mode
            SidePanelRecordingPanel(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                panelDimensions = panelDimensions,
                isVisible = isVisible,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
        LayoutMode.TABLET -> {
            // Dedicated panel for tablet mode
            TabletRecordingPanel(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                panelDimensions = panelDimensions,
                isVisible = isVisible,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
    }
}

/**
 * Bottom sheet recording panel for portrait modes
 */
@Composable
private fun BottomSheetRecordingPanel(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    panelDimensions: PanelDimensions,
    isVisible: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
        exit = slideOutVertically(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = panelDimensions.maxHeight)
                .zIndex(20f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                    
                    // Compact header for portrait mode
                    PortraitRecordingHeader(
                        recordingState = recordingState,
                        onHidePanel = onHidePanel
                    )
                    
                    // Compact recording controls
                    CompactRecordingControls(
                        recordingState = recordingState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onDiscardRecording = onDiscardRecording
                    )
                    
                    // Level meter (only when recording)
                    if (recordingState.isRecording) {
                        CompactLevelMeter(
                            recordingState = recordingState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Sample assignment (when sample is ready)
                    if (recordingState.recordedSampleId != null && !recordingState.isRecording) {
                        CompactSampleAssignment(
                            drumPadState = drumPadState,
                            onAssignToPad = onAssignToPad,
                            onDiscardRecording = onDiscardRecording
                        )
                    }
                }
            }
        }
    }
}

/**
 * Side panel recording panel for landscape mode
 */
@Composable
private fun SidePanelRecordingPanel(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    panelDimensions: PanelDimensions,
    isVisible: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
        exit = slideOutHorizontally(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = panelDimensions.width)
                .fillMaxHeight()
                .zIndex(20f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterEnd),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Side panel header
                    LandscapeRecordingHeader(
                        recordingState = recordingState,
                        onHidePanel = onHidePanel
                    )
                    
                    // Full recording controls with more space
                    ExpandedRecordingControls(
                        recordingState = recordingState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onDiscardRecording = onDiscardRecording
                    )
                    
                    // Level meter with more detail
                    if (recordingState.isRecording || recordingState.peakLevel > 0f) {
                        ExpandedLevelMeter(
                            recordingState = recordingState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Duration display
                    if (recordingState.isRecording || recordingState.durationMs > 0) {
                        RecordingDurationDisplay(
                            recordingState = recordingState
                        )
                    }
                    
                    // Sample assignment with grid
                    if (recordingState.recordedSampleId != null && !recordingState.isRecording) {
                        ExpandedSampleAssignment(
                            drumPadState = drumPadState,
                            onAssignToPad = onAssignToPad,
                            onDiscardRecording = onDiscardRecording
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tablet recording panel with dedicated space
 */
@Composable
private fun TabletRecordingPanel(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    panelDimensions: PanelDimensions,
    isVisible: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialScale = 0.8f
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
        exit = scaleOut(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetScale = 0.8f
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = panelDimensions.width)
                .fillMaxHeight()
                .zIndex(20f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterEnd),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Tablet header with more space
                    TabletRecordingHeader(
                        recordingState = recordingState,
                        onHidePanel = onHidePanel
                    )
                    
                    // Professional recording controls
                    ProfessionalRecordingControls(
                        recordingState = recordingState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onDiscardRecording = onDiscardRecording
                    )
                    
                    // Professional level meter
                    if (recordingState.isRecording || recordingState.peakLevel > 0f) {
                        ProfessionalLevelMeter(
                            recordingState = recordingState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Duration and metadata
                    if (recordingState.isRecording || recordingState.durationMs > 0) {
                        RecordingMetadataDisplay(
                            recordingState = recordingState
                        )
                    }
                    
                    // Professional sample assignment
                    if (recordingState.recordedSampleId != null && !recordingState.isRecording) {
                        ProfessionalSampleAssignment(
                            drumPadState = drumPadState,
                            onAssignToPad = onAssignToPad,
                            onDiscardRecording = onDiscardRecording
                        )
                    }
                }
            }
        }
    }
}
/**
 *
 Portrait mode header - compact and minimal
 */
@Composable
private fun PortraitRecordingHeader(
    recordingState: IntegratedRecordingState,
    onHidePanel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Recording",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        MicroInteractions.AnimatedIconButton(
            onClick = onHidePanel
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Hide Recording Panel"
            )
        }
    }
}

/**
 * Landscape mode header - more detailed
 */
@Composable
private fun LandscapeRecordingHeader(
    recordingState: IntegratedRecordingState,
    onHidePanel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Recording Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    recordingState.isRecording -> "Recording in progress..."
                    recordingState.recordedSampleId != null -> "Sample ready for assignment"
                    else -> "Ready to record"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        MicroInteractions.AnimatedIconButton(
            onClick = onHidePanel
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Recording Panel"
            )
        }
    }
}

/**
 * Tablet mode header - professional layout
 */
@Composable
private fun TabletRecordingHeader(
    recordingState: IntegratedRecordingState,
    onHidePanel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "Professional Recording",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    recordingState.isRecording -> "Recording: ${formatDuration(recordingState.durationMs)}"
                    recordingState.recordedSampleId != null -> "Sample captured successfully"
                    recordingState.error != null -> "Recording error occurred"
                    else -> "Professional audio recording ready"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                StatusChip(
                    text = if (recordingState.canStartRecording) "Ready" else "Busy",
                    color = if (recordingState.canStartRecording) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outline
                )
                
                if (recordingState.isRecording) {
                    StatusChip(
                        text = "REC",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        MicroInteractions.AnimatedIconButton(
            onClick = onHidePanel
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Recording Panel",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Status chip for tablet mode
 */
@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Format duration for display
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 60000) % 60
    return String.format("%02d:%02d", minutes, seconds)
}/**

 * Compact recording controls for portrait mode
 */
@Composable
private fun CompactRecordingControls(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Recording status indicator
        EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
            isRecording = recordingState.isRecording,
            isInitializing = recordingState.isProcessing,
            hasError = recordingState.error != null
        )
        
        // Main record button
        MicroInteractions.RecordingButton(
            isRecording = recordingState.isRecording,
            isInitializing = recordingState.isProcessing,
            canRecord = recordingState.canStartRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            modifier = Modifier.size(56.dp)
        )
        
        // Discard button (when applicable)
        if (recordingState.recordedSampleId != null || recordingState.error != null) {
            MicroInteractions.AnimatedIconButton(
                onClick = onDiscardRecording
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Discard Recording"
                )
            }
        }
    }
}

/**
 * Expanded recording controls for landscape mode
 */
@Composable
private fun ExpandedRecordingControls(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                isRecording = recordingState.isRecording,
                isInitializing = recordingState.isProcessing,
                hasError = recordingState.error != null
            )
            
            Text(
                text = when {
                    recordingState.error != null -> "Error"
                    recordingState.isRecording -> "Recording..."
                    recordingState.isProcessing -> "Processing..."
                    recordingState.recordedSampleId != null -> "Sample Ready"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    recordingState.error != null -> MaterialTheme.colorScheme.error
                    recordingState.isRecording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        
        // Main record button
        MicroInteractions.RecordingButton(
            isRecording = recordingState.isRecording,
            isInitializing = recordingState.isProcessing,
            canRecord = recordingState.canStartRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            modifier = Modifier.size(72.dp)
        )
        
        // Action buttons
        if (recordingState.recordedSampleId != null || recordingState.error != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscardRecording
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Discard")
                }
                
                if (recordingState.recordedSampleId != null) {
                    Button(
                        onClick = {
                            onDiscardRecording()
                            onStartRecording()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-record")
                    }
                }
            }
        }
    }
}

/**
 * Professional recording controls for tablet mode
 */
@Composable
private fun ProfessionalRecordingControls(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Recording Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Professional status display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                            isRecording = recordingState.isRecording,
                            isInitializing = recordingState.isProcessing,
                            hasError = recordingState.error != null
                        )
                        Text(
                            text = when {
                                recordingState.error != null -> "Error Occurred"
                                recordingState.isRecording -> "Recording Active"
                                recordingState.isProcessing -> "Processing Audio"
                                recordingState.recordedSampleId != null -> "Sample Captured"
                                else -> "Ready to Record"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Quality:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "48kHz / 24-bit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Large professional record button
            MicroInteractions.RecordingButton(
                isRecording = recordingState.isRecording,
                isInitializing = recordingState.isProcessing,
                canRecord = recordingState.canStartRecording,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                modifier = Modifier.size(88.dp)
            )
            
            // Professional action buttons
            if (recordingState.recordedSampleId != null || recordingState.error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDiscardRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Discard Sample")
                    }
                    
                    if (recordingState.recordedSampleId != null) {
                        Button(
                            onClick = {
                                onDiscardRecording()
                                onStartRecording()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-record")
                        }
                    }
                }
            }
        }
    }
}/*
*
 * Compact level meter for portrait mode
 */
@Composable
private fun CompactLevelMeter(
    recordingState: IntegratedRecordingState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
            peakLevel = recordingState.peakLevel,
            averageLevel = recordingState.averageLevel,
            isRecording = recordingState.isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        )
    }
}

/**
 * Expanded level meter for landscape mode
 */
@Composable
private fun ExpandedLevelMeter(
    recordingState: IntegratedRecordingState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Input Level",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(recordingState.peakLevel * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (recordingState.peakLevel > 0.9f) 
                    MaterialTheme.colorScheme.error 
                else MaterialTheme.colorScheme.onSurface
            )
        }
        
        EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
            peakLevel = recordingState.peakLevel,
            averageLevel = recordingState.averageLevel,
            isRecording = recordingState.isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
        
        // Level indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peak: ${(recordingState.peakLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Avg: ${(recordingState.averageLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Professional level meter for tablet mode
 */
@Composable
private fun ProfessionalLevelMeter(
    recordingState: IntegratedRecordingState,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Input Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Peak warning indicator
                if (recordingState.peakLevel > 0.9f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peak Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PEAK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Professional level meter with more detail
            EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
                peakLevel = recordingState.peakLevel,
                averageLevel = recordingState.averageLevel,
                isRecording = recordingState.isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
            
            // Detailed level information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Peak Level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(recordingState.peakLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            recordingState.peakLevel > 0.9f -> MaterialTheme.colorScheme.error
                            recordingState.peakLevel > 0.7f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Average Level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(recordingState.averageLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Recording duration display for landscape/tablet modes
 */
@Composable
private fun RecordingDurationDisplay(
    recordingState: IntegratedRecordingState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (recordingState.isRecording) 
                MaterialTheme.colorScheme.errorContainer 
            else MaterialTheme.colorScheme.surfaceVariant
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
                    text = "Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EnhancedAnimationIntegration.AnimatedRecordingDuration(
                    durationMs = recordingState.durationMs,
                    isRecording = recordingState.isRecording
                )
            }
            
            if (recordingState.isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "REC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Recording metadata display for tablet mode
 */
@Composable
private fun RecordingMetadataDisplay(
    recordingState: IntegratedRecordingState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recording Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EnhancedAnimationIntegration.AnimatedRecordingDuration(
                        durationMs = recordingState.durationMs,
                        isRecording = recordingState.isRecording
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Sample Rate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "48 kHz",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (recordingState.isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECORDING IN PROGRESS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}/**

 * Compact sample assignment for portrait mode
 */
@Composable
private fun CompactSampleAssignment(
    drumPadState: DrumPadState,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Assign to Pad",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Compact 4x4 grid
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(4) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) { col ->
                        val index = row * 4 + col
                        val padId = index.toString()
                        val padSettings = drumPadState.padSettings[padId]
                        val hasAssignedSample = padSettings?.sampleId != null
                        
                        MicroInteractions.SampleAssignmentButton(
                            padIndex = index,
                            isSelected = false,
                            onAssign = { onAssignToPad(padId) },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (hasAssignedSample) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expanded sample assignment for landscape mode
 */
@Composable
private fun ExpandedSampleAssignment(
    drumPadState: DrumPadState,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sample Assignment",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Select a pad to assign the recorded sample:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // 4x4 grid with labels
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(4) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(4) { col ->
                        val index = row * 4 + col
                        val padId = index.toString()
                        val padSettings = drumPadState.padSettings[padId]
                        val hasAssignedSample = padSettings?.sampleId != null
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasAssignedSample) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            MicroInteractions.SampleAssignmentButton(
                                padIndex = index,
                                isSelected = false,
                                onAssign = { onAssignToPad(padId) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDiscardRecording,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
}

/**
 * Professional sample assignment for tablet mode
 */
@Composable
private fun ProfessionalSampleAssignment(
    drumPadState: DrumPadState,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sample Assignment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Choose a drum pad to assign your recorded sample. Pads with existing samples will be replaced.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Professional pad grid with detailed information
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(4) { col ->
                            val index = row * 4 + col
                            val padId = index.toString()
                            val padSettings = drumPadState.padSettings[padId]
                            val hasAssignedSample = padSettings?.sampleId != null
                            
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasAssignedSample) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (hasAssignedSample) 2.dp else 1.dp
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        MicroInteractions.SampleAssignmentButton(
                                            padIndex = index,
                                            isSelected = false,
                                            onAssign = { onAssignToPad(padId) },
                                            modifier = Modifier.size(40.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Text(
                                            text = "Pad ${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        if (hasAssignedSample) {
                                            Text(
                                                text = "Sample",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Professional action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscardRecording,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Assignment")
                }
                
                Button(
                    onClick = { /* Show sample preview */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preview Sample")
                }
            }
        }
    }
}