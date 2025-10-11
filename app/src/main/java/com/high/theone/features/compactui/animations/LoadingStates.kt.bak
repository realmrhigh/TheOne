package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Loading states and progress indicators for the compact UI
 * Provides consistent loading feedback across all components
 */
object LoadingStates {
    
    /**
     * Sample loading indicator with waveform animation
     */
    @Composable
    fun SampleLoadingIndicator(
        isLoading: Boolean,
        progress: Float = 0f,
        sampleName: String = "",
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)) +
                    slideInVertically(
                        animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                        initialOffsetY = { it / 2 }
                    ),
            exit = fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)) +
                   slideOutVertically(
                       animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                       targetOffsetY = { -it / 2 }
                   ),
            modifier = modifier
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Loading Sample",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    if (sampleName.isNotEmpty()) {
                        Text(
                            text = sampleName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Animated waveform
                    WaveformLoadingAnimation(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress bar
                    AnimatedProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                    
                    if (progress > 0f) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Waveform loading animation
     */
    @Composable
    private fun WaveformLoadingAnimation(
        modifier: Modifier = Modifier,
        barCount: Int = 20
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(barCount) { index ->
                val animationDelay = index * 50
                val infiniteTransition = rememberInfiniteTransition(label = "waveform_animation")
                
                val height by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 800,
                            delayMillis = animationDelay,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_height_$index"
                )
                
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight(height)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
    
    /**
     * Audio processing indicator
     */
    @Composable
    fun AudioProcessingIndicator(
        isProcessing: Boolean,
        operation: String = "Processing",
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            exit = fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                LoadingStates.SpinningLoadingIndicator(
                    size = 16.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = operation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    /**
     * Spinning loading indicator
     */
    @Composable
    fun SpinningLoadingIndicator(
        modifier: Modifier = Modifier,
        size: androidx.compose.ui.unit.Dp = 24.dp,
        color: Color = MaterialTheme.colorScheme.primary,
        strokeWidth: androidx.compose.ui.unit.Dp = 2.dp
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "spinning_animation")
        
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        CircularProgressIndicator(
            modifier = modifier
                .size(size)
                .rotate(rotation),
            color = color,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round
        )
    }
    
    /**
     * MIDI connection status indicator
     */
    @Composable
    fun MidiConnectionIndicator(
        isConnecting: Boolean,
        isConnected: Boolean,
        deviceName: String = "",
        modifier: Modifier = Modifier
    ) {
        val backgroundColor by animateColorAsState(
            targetValue = when {
                isConnecting -> MaterialTheme.colorScheme.tertiary
                isConnected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            label = "midi_bg_color"
        )
        
        val textColor by animateColorAsState(
            targetValue = when {
                isConnecting -> MaterialTheme.colorScheme.onTertiary
                isConnected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            label = "midi_text_color"
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isConnecting) {
                LoadingStates.SpinningLoadingIndicator(
                    size = 12.dp,
                    color = textColor,
                    strokeWidth = 1.5.dp
                )
            } else {
                VisualFeedbackSystem.MidiActivityIndicator(
                    isActive = isConnected,
                    modifier = Modifier.size(8.dp)
                )
            }
            
            Text(
                text = when {
                    isConnecting -> "Connecting..."
                    isConnected -> if (deviceName.isNotEmpty()) deviceName else "Connected"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
    
    /**
     * Project save/load indicator
     */
    @Composable
    fun ProjectOperationIndicator(
        isActive: Boolean,
        operation: ProjectOperation,
        projectName: String = "",
        progress: Float = 0f,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = isActive,
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
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LoadingStates.SpinningLoadingIndicator(
                            size = 20.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (operation) {
                                    ProjectOperation.SAVING -> "Saving Project"
                                    ProjectOperation.LOADING -> "Loading Project"
                                    ProjectOperation.EXPORTING -> "Exporting Project"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            
                            if (projectName.isNotEmpty()) {
                                Text(
                                    text = projectName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    if (progress > 0f) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        AnimatedProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Skeleton loading for UI components
     */
    @Composable
    fun SkeletonLoader(
        modifier: Modifier = Modifier,
        shape: Shape = RoundedCornerShape(4.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton_animation")
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )
        
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
        )
    }
}

enum class ProjectOperation {
    SAVING, LOADING, EXPORTING
}

/**
 * Enhanced recording initialization loading state with haptic feedback and improved animations
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingInitializationLoader(
    isInitializing: Boolean,
    initializationStep: RecordingInitStep = RecordingInitStep.AUDIO_ENGINE,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    // Haptic feedback when initialization starts
    LaunchedEffect(isInitializing) {
        if (isInitializing) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    // Haptic feedback when step changes
    LaunchedEffect(initializationStep) {
        if (isInitializing) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    AnimatedVisibility(
        visible = isInitializing,
        enter = slideInVertically(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialOffsetY = { it / 2 }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)) +
        scaleIn(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialScale = 0.9f
        ),
        exit = slideOutVertically(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION, easing = AnimationSystem.FastOutLinearIn),
            targetOffsetY = { -it / 2 }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)) +
        scaleOut(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetScale = 1.1f
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Enhanced animated microphone icon with glow effect
                val infiniteTransition = rememberInfiniteTransition(label = "mic_animation")
                
                val micScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_scale"
                )
                
                val micRotation by infiniteTransition.animateFloat(
                    initialValue = -8f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_rotation"
                )
                
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_alpha"
                )
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.3f),
                            CircleShape
                        )
                        .graphicsLayer {
                            shadowElevation = (4 + glowAlpha * 6).dp.toPx()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Initializing Recording",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(micScale)
                            .graphicsLayer { rotationZ = micRotation }
                    )
                }
                
                // Initialization step text with animated transitions
                AnimatedContent(
                    targetState = initializationStep,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
                            initialOffsetY = { it / 4 }
                        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)) with
                        slideOutVertically(
                            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                            targetOffsetY = { -it / 4 }
                        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
                    },
                    label = "initialization_step"
                ) { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Initializing Recording",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = when (step) {
                                RecordingInitStep.AUDIO_ENGINE -> "Starting audio engine..."
                                RecordingInitStep.MICROPHONE -> "Configuring microphone..."
                                RecordingInitStep.PERMISSIONS -> "Checking permissions..."
                                RecordingInitStep.BUFFER_SETUP -> "Setting up audio buffers..."
                                RecordingInitStep.READY -> "Ready to record!"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Enhanced progress indicator
                if (progress > 0f) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            backgroundColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                        
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Enhanced animated dots with staggered animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { index ->
                        val dotScale by infiniteTransition.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 800,
                                    delayMillis = index * 150
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_scale_$index"
                        )
                        
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 800,
                                    delayMillis = index * 150
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha_$index"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(dotScale)
                                .graphicsLayer { alpha = dotAlpha }
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sample assignment loading state with pad highlighting
 */
@Composable
fun SampleAssignmentLoader(
    isAssigning: Boolean,
    targetPadIndex: Int?,
    sampleName: String = "",
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isAssigning,
        enter = scaleIn(
            animationSpec = AnimationSystem.BounceSpring,
            initialScale = 0.8f
        ) + fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        exit = scaleOut(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetScale = 1.2f
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animated assignment icon
                val infiniteTransition = rememberInfiniteTransition(label = "assignment_animation")
                
                val iconRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "icon_rotation"
                )
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "→",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Column {
                    Text(
                        text = "Assigning Sample",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    if (targetPadIndex != null) {
                        Text(
                            text = "to Pad ${targetPadIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    if (sampleName.isNotEmpty()) {
                        Text(
                            text = sampleName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recording processing indicator for post-recording operations
 */
@Composable
fun RecordingProcessingIndicator(
    isProcessing: Boolean,
    operation: RecordingProcessingOperation,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isProcessing,
        enter = slideInHorizontally(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)),
        exit = slideOutHorizontally(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LoadingStates.SpinningLoadingIndicator(
                    size = 16.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Column {
                    Text(
                        text = when (operation) {
                            RecordingProcessingOperation.SAVING -> "Saving Recording"
                            RecordingProcessingOperation.PROCESSING -> "Processing Audio"
                            RecordingProcessingOperation.ANALYZING -> "Analyzing Waveform"
                            RecordingProcessingOperation.OPTIMIZING -> "Optimizing Quality"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (progress > 0f) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (progress > 0f) {
                AnimatedProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

enum class RecordingInitStep {
    AUDIO_ENGINE,
    MICROPHONE,
    PERMISSIONS,
    BUFFER_SETUP,
    READY
}

enum class RecordingProcessingOperation {
    SAVING,
    PROCESSING,
    ANALYZING,
    OPTIMIZING
}