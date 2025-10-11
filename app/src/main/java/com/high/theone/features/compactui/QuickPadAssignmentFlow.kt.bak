package com.high.theone.features.compactui

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.high.theone.model.*
import kotlinx.coroutines.delay

/**
 * Quick pad assignment flow component that appears after recording completion.
 * Provides immediate pad assignment with visual feedback and confirmation.
 * 
 * Requirements: 2.1, 2.3, 4.1, 4.2 (post-recording UI, pad highlighting, one-tap assignment, visual confirmation)
 */
@Composable
fun QuickPadAssignmentFlow(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    onAssignToPad: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show assignment flow when recording is complete and we have a recorded sample
    val showAssignmentFlow = recordingState.recordedSampleId != null && 
                            !recordingState.isRecording && 
                            !recordingState.isProcessing &&
                            recordingState.error == null

    AnimatedVisibility(
        visible = showAssignmentFlow,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with instructions
                AssignmentFlowHeader(
                    recordingState = recordingState,
                    onCancel = onCancel
                )
                
                // Available pads grid for assignment
                AvailablePadsGrid(
                    drumPadState = drumPadState,
                    availablePadIds = recordingState.availablePadsForAssignment,
                    screenConfiguration = screenConfiguration,
                    onPadSelect = onAssignToPad
                )
            }
        }
    }
}

/**
 * Header section with instructions and cancel option
 */
@Composable
private fun AssignmentFlowHeader(
    recordingState: IntegratedRecordingState,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Assign Sample to Pad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Tap an empty pad to assign your recorded sample",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            // Show recording duration for context
            Text(
                text = "Duration: ${recordingState.formattedDuration}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel Assignment",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Grid showing available pads for assignment with highlighting
 */
@Composable
private fun AvailablePadsGrid(
    drumPadState: DrumPadState,
    availablePadIds: List<String>,
    screenConfiguration: ScreenConfiguration,
    onPadSelect: (String) -> Unit
) {
    val availablePadIndices = availablePadIds.map { it.toIntOrNull() ?: -1 }.filter { it >= 0 }
    
    // Calculate pad size for assignment grid (smaller than main grid)
    val padSize = when (screenConfiguration.layoutMode) {
        LayoutMode.COMPACT_PORTRAIT -> 48.dp
        LayoutMode.STANDARD_PORTRAIT -> 56.dp
        LayoutMode.LANDSCAPE -> 64.dp
        LayoutMode.TABLET -> 72.dp
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Available Pads (${availablePadIndices.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Create rows of available pads (4 per row)
        availablePadIndices.chunked(4).forEach { rowPads ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                rowPads.forEach { padIndex ->
                    AssignablePad(
                        padIndex = padIndex,
                        padSize = padSize,
                        onSelect = { onPadSelect(padIndex.toString()) },
                        modifier = Modifier.size(padSize)
                    )
                }
                
                // Fill remaining space if row is not complete
                repeat(4 - rowPads.size) {
                    Spacer(modifier = Modifier.size(padSize))
                }
            }
        }
        
        // Show message if no pads available
        if (availablePadIndices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "No empty pads available. Clear a pad first to assign this sample.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * Individual assignable pad with highlighting and animation
 */
@Composable
private fun AssignablePad(
    padIndex: Int,
    padSize: Dp,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    
    // Pulsing animation to draw attention
    val infiniteTransition = rememberInfiniteTransition(label = "pad_highlight")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    // Press animation
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "press_scale"
    )
    
    // Confirmation animation
    LaunchedEffect(showConfirmation) {
        if (showConfirmation) {
            delay(1500) // Show confirmation for 1.5 seconds
            showConfirmation = false
        }
    }
    
    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha)
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable {
                isPressed = true
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showConfirmation = true
                onSelect()
            },
        contentAlignment = Alignment.Center
    ) {
        if (showConfirmation) {
            // Show confirmation checkmark
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Assigned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            // Show pad number
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${padIndex + 1}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Text(
                    text = "Empty",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Recording panel integration component that shows recording controls and assignment flow
 */
@Composable
fun RecordingPanelIntegration(
    recordingState: IntegratedRecordingState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine panel position based on screen configuration
    val useBottomSheet = screenConfiguration.layoutMode in listOf(
        LayoutMode.COMPACT_PORTRAIT,
        LayoutMode.STANDARD_PORTRAIT
    )
    
    if (useBottomSheet) {
        // Bottom sheet for portrait modes
        RecordingBottomSheet(
            recordingState = recordingState,
            screenConfiguration = screenConfiguration,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onAssignToPad = onAssignToPad,
            onDiscardRecording = onDiscardRecording,
            onHidePanel = onHidePanel,
            modifier = modifier
        )
    } else {
        // Side panel for landscape and tablet modes
        RecordingSidePanel(
            recordingState = recordingState,
            screenConfiguration = screenConfiguration,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onAssignToPad = onAssignToPad,
            onDiscardRecording = onDiscardRecording,
            onHidePanel = onHidePanel,
            modifier = modifier
        )
    }
}

/**
 * Bottom sheet implementation for portrait modes
 */
@Composable
private fun RecordingBottomSheet(
    recordingState: IntegratedRecordingState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle bar for bottom sheet
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )
            
            // Recording controls
            RecordingControlsSection(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel
            )
        }
    }
}

/**
 * Side panel implementation for landscape and tablet modes
 */
@Composable
private fun RecordingSidePanel(
    recordingState: IntegratedRecordingState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = onHidePanel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Panel"
                    )
                }
            }
            
            // Recording controls
            RecordingControlsSection(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel
            )
        }
    }
}

/**
 * Common recording controls section used by both bottom sheet and side panel
 */
@Composable
private fun RecordingControlsSection(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit
) {
    // Convert IntegratedRecordingState to RecordingState for RecordingControls component
    val legacyRecordingState = RecordingState(
        isRecording = recordingState.isRecording,
        isProcessing = recordingState.isProcessing,
        durationMs = recordingState.durationMs,
        peakLevel = recordingState.peakLevel,
        averageLevel = recordingState.averageLevel,
        error = recordingState.error?.message,
        recordingFilePath = "", // Not needed for UI
        isInitialized = true,
        isPaused = false
    )
    
    com.high.theone.features.sampling.RecordingControls(
        recordingState = legacyRecordingState,
        onStartRecording = onStartRecording,
        onStopRecording = onStopRecording
    )
    
    // Action buttons
    if (recordingState.recordedSampleId != null || recordingState.error != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDiscardRecording,
                modifier = Modifier.weight(1f)
            ) {
                Text("Discard")
            }
            
            if (recordingState.error == null) {
                Button(
                    onClick = onHidePanel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep")
                }
            }
        }
    }
}