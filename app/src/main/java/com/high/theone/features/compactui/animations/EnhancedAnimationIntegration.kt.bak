package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.IntegratedRecordingState
import com.high.theone.model.RecordingError
import kotlinx.coroutines.delay

/**
 * Enhanced animation integration for the compact UI recording system
 * Provides comprehensive visual feedback with haptic integration
 */
object EnhancedAnimationIntegration {
    
    /**
     * Complete recording workflow animation system
     */
    @Composable
    fun RecordingWorkflowAnimations(
        recordingState: IntegratedRecordingState,
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit,
        onAssignToPad: (Int) -> Unit,
        onDiscardRecording: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val haptic = LocalHapticFeedback.current
        
        Box(modifier = modifier) {
            // Recording initialization animation
            RecordingInitializationLoader(
                isInitializing = recordingState.isProcessing && !recordingState.isRecording,
                initializationStep = when {
                    recordingState.error != null -> RecordingInitStep.READY
                    recordingState.canStartRecording -> RecordingInitStep.READY
                    else -> RecordingInitStep.AUDIO_ENGINE
                },
                progress = if (recordingState.isProcessing) 0.7f else 0f,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // Recording button with enhanced animations
            MicroInteractions.RecordingButton(
                isRecording = recordingState.isRecording,
                isInitializing = recordingState.isProcessing && !recordingState.isRecording,
                canRecord = recordingState.canStartRecording,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
            
            // Sample assignment success animation
            if (recordingState.recordedSampleId != null && recordingState.isAssignmentMode) {
                SampleAssignmentSuccessAnimation(
                    triggered = true,
                    padIndex = null, // Would be provided when assignment happens
                    sampleName = "Recorded Sample",
                    modifier = Modifier.align(Alignment.Center),
                    onAnimationComplete = onDiscardRecording
                )
            }
            
            // Error feedback animation
            recordingState.error?.let { error ->
                RecordingErrorAnimation(
                    error = error,
                    onDismiss = onDiscardRecording,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
    
    /**
     * Recording error animation with haptic feedback
     */
    @Composable
    fun RecordingErrorAnimation(
        error: RecordingError,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val haptic = LocalHapticFeedback.current
        
        LaunchedEffect(error) {
            // Error haptic pattern
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(100)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(100)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                initialOffsetY = { -it }
            ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
            exit = slideOutVertically(
                animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                targetOffsetY = { -it }
            ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            modifier = modifier
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Animated error icon
                    val infiniteTransition = rememberInfiniteTransition(label = "error_animation")
                    val shake by infiniteTransition.animateFloat(
                        initialValue = -2f,
                        targetValue = 2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(100, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "error_shake"
                    )
                    
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Recording Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { translationX = shake }
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recording Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    if (error.isRecoverable) {
                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text("Retry")
                        }
                    } else {
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss"
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Enhanced recording level meter with visual feedback
     */
    @Composable
    fun EnhancedRecordingLevelMeter(
        peakLevel: Float,
        averageLevel: Float,
        isRecording: Boolean,
        modifier: Modifier = Modifier
    ) {
        val animatedPeak by animateFloatAsState(
            targetValue = peakLevel.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 50,
                easing = LinearEasing
            ),
            label = "peak_level"
        )
        
        val animatedAverage by animateFloatAsState(
            targetValue = averageLevel.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 100,
                easing = LinearEasing
            ),
            label = "average_level"
        )
        
        val glowIntensity by animateFloatAsState(
            targetValue = if (isRecording && peakLevel > 0.8f) 1f else 0f,
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "glow_intensity"
        )
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp)
                )
        ) {
            // Average level background
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedAverage)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
            )
            
            // Peak level foreground with color coding
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedPeak)
                    .background(
                        when {
                            animatedPeak > 0.9f -> MaterialTheme.colorScheme.error
                            animatedPeak > 0.7f -> Color(0xFFFF9800) // Orange
                            else -> MaterialTheme.colorScheme.primary
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .graphicsLayer {
                        if (glowIntensity > 0f) {
                            shadowElevation = (glowIntensity * 6).dp.toPx()
                        }
                    }
            )
            
            // Clipping indicator with pulsing animation
            if (animatedPeak > 0.95f) {
                val infiniteTransition = rememberInfiniteTransition(label = "clipping_animation")
                val clipAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "clip_alpha"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = clipAlpha),
                            RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
    
    /**
     * Recording duration display with smooth updates
     */
    @Composable
    fun AnimatedRecordingDuration(
        durationMs: Long,
        isRecording: Boolean,
        modifier: Modifier = Modifier
    ) {
        val animatedDuration by animateFloatAsState(
            targetValue = durationMs.toFloat(),
            animationSpec = tween(
                durationMillis = 100,
                easing = LinearEasing
            ),
            label = "recording_duration"
        )
        
        val textColor by animateColorAsState(
            targetValue = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            label = "duration_color"
        )
        
        val scale by animateFloatAsState(
            targetValue = if (isRecording) 1.05f else 1f,
            animationSpec = if (isRecording) {
                infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(AnimationSystem.FAST_ANIMATION)
            },
            label = "duration_scale"
        )
        
        Text(
            text = formatDuration(animatedDuration.toLong()),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
            modifier = modifier.scale(scale)
        )
    }
    
    /**
     * Recording status indicator with animated states
     */
    @Composable
    fun AnimatedRecordingStatusIndicator(
        isRecording: Boolean,
        isInitializing: Boolean,
        hasError: Boolean,
        modifier: Modifier = Modifier
    ) {
        val color by animateColorAsState(
            targetValue = when {
                hasError -> MaterialTheme.colorScheme.error
                isRecording -> MaterialTheme.colorScheme.error
                isInitializing -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            },
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            label = "status_color"
        )
        
        val pulseAlpha by animateFloatAsState(
            targetValue = if (isRecording) 0.6f else 1f,
            animationSpec = if (isRecording) {
                infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(AnimationSystem.FAST_ANIMATION)
            },
            label = "pulse_alpha"
        )
        
        val scale by animateFloatAsState(
            targetValue = if (isRecording) 1.3f else 1f,
            animationSpec = if (isRecording) {
                infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                AnimationSystem.SmoothSpring
            },
            label = "status_scale"
        )
        
        Box(
            modifier = modifier
                .size(16.dp)
                .scale(scale)
                .graphicsLayer { alpha = pulseAlpha }
                .background(color, CircleShape)
        )
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 60000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Composable for managing comprehensive recording animations
 */
@Composable
fun RecordingAnimationManager(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (Int) -> Unit,
    onDiscardRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Main recording workflow animations
        EnhancedAnimationIntegration.RecordingWorkflowAnimations(
            recordingState = recordingState,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onAssignToPad = onAssignToPad,
            onDiscardRecording = onDiscardRecording,
            modifier = Modifier.fillMaxSize()
        )
        
        // Recording status overlay
        if (recordingState.isRecording || recordingState.isProcessing) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EnhancedAnimationIntegration.AnimatedRecordingStatusIndicator(
                        isRecording = recordingState.isRecording,
                        isInitializing = recordingState.isProcessing,
                        hasError = recordingState.error != null
                    )
                    
                    EnhancedAnimationIntegration.AnimatedRecordingDuration(
                        durationMs = recordingState.durationMs,
                        isRecording = recordingState.isRecording
                    )
                }
            }
        }
        
        // Level meter overlay
        if (recordingState.isRecording) {
            EnhancedAnimationIntegration.EnhancedRecordingLevelMeter(
                peakLevel = recordingState.peakLevel,
                averageLevel = recordingState.averageLevel,
                isRecording = recordingState.isRecording,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}