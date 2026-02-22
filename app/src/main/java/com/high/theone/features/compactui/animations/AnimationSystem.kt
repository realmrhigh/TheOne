package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Central animation system for the compact UI
 * Provides consistent animations and transitions across all components
 */
object AnimationSystem {
    
    // Animation durations
    const val FAST_ANIMATION = 150
    const val MEDIUM_ANIMATION = 300
    const val SLOW_ANIMATION = 500
    
    // Easing curves
    val FastOutSlowIn = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val FastOutLinearIn = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val LinearOutSlowIn = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    
    // Spring specifications
    val BounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    val QuickSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * Panel transition animations for sliding panels and bottom sheets
 */
@Composable
fun PanelTransition(
    visible: Boolean,
    direction: PanelDirection = PanelDirection.Bottom,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val enterTransition = when (direction) {
        PanelDirection.Bottom -> slideInVertically(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION))
        
        PanelDirection.Top -> slideInVertically(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION))
        
        PanelDirection.Left -> slideInHorizontally(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialOffsetX = { -it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION))
        
        PanelDirection.Right -> slideInHorizontally(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION, easing = AnimationSystem.FastOutSlowIn),
            initialOffsetX = { it }
        ) + fadeIn(animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION))
    }
    
    val exitTransition = when (direction) {
        PanelDirection.Bottom -> slideOutVertically(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION, easing = AnimationSystem.FastOutLinearIn),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
        
        PanelDirection.Top -> slideOutVertically(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION, easing = AnimationSystem.FastOutLinearIn),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
        
        PanelDirection.Left -> slideOutHorizontally(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION, easing = AnimationSystem.FastOutLinearIn),
            targetOffsetX = { -it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
        
        PanelDirection.Right -> slideOutHorizontally(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION, easing = AnimationSystem.FastOutLinearIn),
            targetOffsetX = { it }
        ) + fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier,
        content = content
    )
}

enum class PanelDirection {
    Top, Bottom, Left, Right
}

/**
 * Micro-interactions for buttons and interactive elements
 */
@Composable
fun Modifier.buttonPressAnimation(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnimationSystem.QuickSpring,
        label = "button_press_scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = tween(AnimationSystem.FAST_ANIMATION),
        label = "button_press_alpha"
    )
    
    return this
        .scale(scale)
        .alpha(alpha)
}

/**
 * Pad press animation with velocity-sensitive feedback
 */
@Composable
fun Modifier.padPressAnimation(
    isPressed: Boolean,
    velocity: Float = 1f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f + (velocity * 0.05f) else 1f,
        animationSpec = if (isPressed) AnimationSystem.QuickSpring else AnimationSystem.SmoothSpring,
        label = "pad_press_scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) (4 + velocity * 2).dp else 2.dp,
        animationSpec = tween(AnimationSystem.FAST_ANIMATION),
        label = "pad_press_elevation"
    )
    
    return this
        .scale(scale)
        .graphicsLayer {
            shadowElevation = elevation.toPx()
        }
}

/**
 * Loading state animations
 */
@Composable
fun LoadingIndicator(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        exit = fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
        modifier = modifier
    ) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Progress indicator with smooth animations
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = AnimationSystem.MEDIUM_ANIMATION,
            easing = AnimationSystem.FastOutSlowIn
        ),
        label = "progress_animation"
    )
    
    LinearProgressIndicator(
        progress = animatedProgress,
        modifier = modifier,
        color = color,
        trackColor = backgroundColor
    )
}

/**
 * Enhanced recording panel transition with smooth slide animations and backdrop
 */
@Composable
fun RecordingPanelTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                easing = AnimationSystem.FastOutSlowIn
            ),
            initialOffsetY = { it }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                easing = AnimationSystem.FastOutSlowIn
            )
        ) + scaleIn(
            animationSpec = tween(
                durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                easing = AnimationSystem.FastOutSlowIn
            ),
            initialScale = 0.95f
        ),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = AnimationSystem.FAST_ANIMATION,
                easing = AnimationSystem.FastOutLinearIn
            ),
            targetOffsetY = { it }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = AnimationSystem.FAST_ANIMATION,
                easing = AnimationSystem.FastOutLinearIn
            )
        ) + scaleOut(
            animationSpec = tween(
                durationMillis = AnimationSystem.FAST_ANIMATION,
                easing = AnimationSystem.FastOutLinearIn
            ),
            targetScale = 0.95f
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Enhanced recording button with pulse animation during active recording
 * Includes haptic feedback integration and improved visual states
 */
@Composable
fun Modifier.recordingButtonAnimation(
    isRecording: Boolean,
    isPressed: Boolean = false,
    isInitializing: Boolean = false
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    
    // Enhanced pulse animation with different patterns for different states
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isInitializing) 1.05f else 1.1f,
        animationSpec = if (isRecording) {
            infiniteRepeatable(
                animation = tween(
                    durationMillis = 800,
                    easing = EaseInOut
                ),
                repeatMode = RepeatMode.Reverse
            )
        } else if (isInitializing) {
            infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            infiniteRepeatable(
                animation = tween(durationMillis = 0),
                repeatMode = RepeatMode.Restart
            )
        },
        label = "pulse_scale"
    )
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnimationSystem.QuickSpring,
        label = "press_scale"
    )
    
    // Enhanced glow effect with color transitions
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isInitializing) 0.2f else 0.3f,
        targetValue = if (isInitializing) 0.6f else 0.8f,
        animationSpec = if (isRecording) {
            infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = EaseInOut
                ),
                repeatMode = RepeatMode.Reverse
            )
        } else if (isInitializing) {
            infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = EaseInOut
                ),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            infiniteRepeatable(
                animation = tween(durationMillis = 0),
                repeatMode = RepeatMode.Restart
            )
        },
        label = "glow_alpha"
    )
    
    // Rotation animation for initializing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = if (isInitializing) {
            infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        } else {
            infiniteRepeatable(
                animation = tween(durationMillis = 0),
                repeatMode = RepeatMode.Restart
            )
        },
        label = "initialization_rotation"
    )
    
    return this
        .scale(if (isRecording || isInitializing) pulseScale else pressScale)
        .graphicsLayer {
            if (isRecording || isInitializing) {
                shadowElevation = (8 + glowAlpha * 6).dp.toPx()
            }
            if (isInitializing) {
                rotationZ = rotation
            }
        }
}

/**
 * Enhanced sample assignment success animation with haptic feedback
 */
@Composable
fun SampleAssignmentSuccessAnimation(
    triggered: Boolean,
    padIndex: Int? = null,
    sampleName: String = "",
    modifier: Modifier = Modifier,
    onAnimationComplete: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    
    LaunchedEffect(triggered) {
        if (triggered) {
            // Haptic feedback for success
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(100)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            
            delay(2500) // Show success for 2.5 seconds
            onAnimationComplete()
        }
    }
    
    AnimatedVisibility(
        visible = triggered,
        enter = scaleIn(
            animationSpec = AnimationSystem.BounceSpring,
            initialScale = 0.3f
        ) + fadeIn(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION)
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                easing = AnimationSystem.FastOutSlowIn
            ),
            initialOffsetY = { -it / 2 }
        ),
        exit = scaleOut(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            targetScale = 1.2f
        ) + fadeOut(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION)
        ) + slideOutVertically(
            animationSpec = tween(AnimationSystem.MEDIUM_ANIMATION),
            targetOffsetY = { -it }
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated success icon with glow effect
                val infiniteTransition = rememberInfiniteTransition(label = "success_glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_alpha"
                )
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                            CircleShape
                        )
                        .graphicsLayer {
                            shadowElevation = (4 + glowAlpha * 4).dp.toPx()
                        }
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.scale(1.2f)
                    )
                }
                
                Column {
                    Text(
                        text = "Sample Assigned!",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (padIndex != null) {
                        Text(
                            text = "to Pad ${padIndex + 1}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (sampleName.isNotEmpty()) {
                        Text(
                            text = sampleName,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recording initialization loading animation
 */
@Composable
fun RecordingInitializationLoader(
    isInitializing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isInitializing,
        enter = fadeIn(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION)
        ) + scaleIn(
            animationSpec = AnimationSystem.SmoothSpring,
            initialScale = 0.8f
        ),
        exit = fadeOut(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION)
        ) + scaleOut(
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            targetScale = 0.8f
        ),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "init_animation")
                
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
                
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Initializing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                
                Column {
                    Text(
                        text = "Initializing Recording",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Setting up audio engine...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}