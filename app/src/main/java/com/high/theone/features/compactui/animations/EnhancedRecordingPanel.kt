package com.high.theone.features.compactui.animations

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
import com.high.theone.model.*

import kotlinx.coroutines.delay

/**
 * Enhanced recording panel with comprehensive animations and visual feedback
 * Integrates all recording-related animations into a cohesive user experience
 */
@Composable
fun EnhancedRecordingPanel(
    recordingState: IntegratedRecordingState,
    drumPadState: DrumPadState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    // Panel transition animation
    RecordingPanelTransition(
        visible = true,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recording",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    MicroInteractions.AnimatedIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onHidePanel()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Recording Panel"
                        )
                    }
                }
                
                // Recording status and controls
                RecordingControlsSection(
                    recordingState = recordingState,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onDiscardRecording = onDiscardRecording
                )
                
                // Level meter (only visible when recording)
                AnimatedVisibility(
                    visible = recordingState.isRecording,
                    enter = slideInVertically(
                        animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                        initialOffsetY = { it / 2 }
                    ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
                    exit = slideOutVertically(
                        animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                        targetOffsetY = { it / 2 }
                    ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Input Level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
                            peakLevel = recordingState.peakLevel,
                            averageLevel = recordingState.averageLevel,
                            isRecording = recordingState.isRecording,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Recording duration (only visible when recording or has recorded sample)
                AnimatedVisibility(
                    visible = recordingState.isRecording || recordingState.recordedSampleId != null,
                    enter = slideInHorizontally(
                        animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                        initialOffsetX = { -it / 2 }
                    ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                        targetOffsetX = { -it / 2 }
                    ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        EnhancedAnimationIntegration.AnimatedRecordingDuration(
                            durationMs = recordingState.durationMs,
                            isRecording = recordingState.isRecording
                        )
                    }
                }
                
                // Sample assignment section (visible when sample is recorded)
                AnimatedVisibility(
                    visible = recordingState.recordedSampleId != null && !recordingState.isRecording,
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                            easing = AnimationSystem.FastOutSlowIn
                        ),
                        initialOffsetY = { it }
                    ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
                    exit = slideOutVertically(
                        animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                        targetOffsetY = { it }
                    ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
                ) {
                    SampleAssignmentSection(
                        drumPadState = drumPadState,
                        onAssignToPad = onAssignToPad,
                        onDiscardRecording = onDiscardRecording
                    )
                }
                
                // Error display
                recordingState.error?.let { error ->
                    EnhancedAnimationIntegration.RecordingErrorAnimation(
                        error = error,
                        onDismiss = onDiscardRecording
                    )
                }
            }
        }
    }
}

/**
 * Recording controls section with enhanced animations
 */
@Composable
private fun RecordingControlsSection(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Recording status indicator
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
                    recordingState.isProcessing -> "Initializing..."
                    recordingState.recordedSampleId != null -> "Sample Ready"
                    else -> "Ready to Record"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    recordingState.error != null -> MaterialTheme.colorScheme.error
                    recordingState.isRecording -> MaterialTheme.colorScheme.error
                    recordingState.isProcessing -> MaterialTheme.colorScheme.tertiary
                    recordingState.recordedSampleId != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        
        // Main recording button
        MicroInteractions.RecordingButton(
            isRecording = recordingState.isRecording,
            isInitializing = recordingState.isProcessing,
            canRecord = recordingState.canStartRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            modifier = Modifier.size(72.dp)
        )
        
        // Action buttons row
        AnimatedVisibility(
            visible = recordingState.recordedSampleId != null || recordingState.error != null,
            enter = slideInVertically(
                animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                initialOffsetY = { it / 2 }
            ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
            exit = slideOutVertically(
                animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                targetOffsetY = { it / 2 }
            ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Discard button
                MicroInteractions.AnimatedButton(
                    onClick = onDiscardRecording,
                    hapticEnabled = true
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Discard Recording"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Discard")
                }
                
                // Re-record button (only if there's a recorded sample)
                if (recordingState.recordedSampleId != null) {
                    MicroInteractions.AnimatedButton(
                        onClick = {
                            onDiscardRecording()
                            onStartRecording()
                        },
                        hapticEnabled = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-record"
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
 * Sample assignment section with pad selection
 */
@Composable
private fun SampleAssignmentSection(
    drumPadState: DrumPadState,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Assign to Pad",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Select a pad to assign the recorded sample:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Pad selection grid (simplified 4x4 grid)
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
                        
                        MicroInteractions.SampleAssignmentButton(
                            padIndex = index,
                            isSelected = false,
                            onAssign = { onAssignToPad(padId) },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
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

