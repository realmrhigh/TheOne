package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.high.theone.features.compactui.animations.AnimationSystem.LinearOutSlowIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Visual feedback system for user interactions
 * Provides consistent visual responses across the compact UI
 */
object VisualFeedbackSystem {
    
    /**
     * Ripple effect for touch interactions
     */
    @Composable
    fun TouchRipple(
        triggered: Boolean,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        size: Dp = 40.dp
    ) {
        val scale by animateFloatAsState(
            targetValue = if (triggered) 1.5f else 0f,
            animationSpec = if (triggered) {
                tween(durationMillis = 300, easing = LinearOutSlowIn)
            } else {
                tween(durationMillis = 0)
            },
            label = "ripple_scale"
        )
        
        val alpha by animateFloatAsState(
            targetValue = if (triggered) 0f else 1f,
            animationSpec = if (triggered) {
                tween(durationMillis = 300, easing = LinearOutSlowIn)
            } else {
                tween(durationMillis = 0)
            },
            label = "ripple_alpha"
        )
        
        Box(
            modifier = modifier
                .size(size)
                .scale(scale)
                .graphicsLayer { this.alpha = alpha }
                .background(color, CircleShape)
        )
    }
    
    /**
     * Success feedback animation
     */
    @Composable
    fun SuccessFeedback(
        triggered: Boolean,
        modifier: Modifier = Modifier,
        onAnimationComplete: () -> Unit = {}
    ) {
        LaunchedEffect(triggered) {
            if (triggered) {
                delay(1000)
                onAnimationComplete()
            }
        }
        
        AnimatedVisibility(
            visible = triggered,
            enter = scaleIn(
                animationSpec = AnimationSystem.BounceSpring,
                initialScale = 0f
            ) + fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            exit = fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            modifier = modifier
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            ) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
    
    /**
     * Error feedback animation
     */
    @Composable
    fun ErrorFeedback(
        triggered: Boolean,
        modifier: Modifier = Modifier,
        onAnimationComplete: () -> Unit = {}
    ) {
        LaunchedEffect(triggered) {
            if (triggered) {
                delay(1500)
                onAnimationComplete()
            }
        }
        
        val shake by animateFloatAsState(
            targetValue = if (triggered) 1f else 0f,
            animationSpec = if (triggered) {
                keyframes {
                    durationMillis = 600
                    0f at 0
                    -10f at 100
                    10f at 200
                    -5f at 300
                    5f at 400
                    0f at 500
                }
            } else {
                tween(0)
            },
            label = "error_shake"
        )
        
        AnimatedVisibility(
            visible = triggered,
            enter = scaleIn(
                animationSpec = tween(AnimationSystem.FAST_ANIMATION),
                initialScale = 0.8f
            ) + fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            exit = fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION)),
            modifier = modifier.graphicsLayer { translationX = shake }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.error,
                        CircleShape
                    )
            ) {
                Text(
                    text = "✗",
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
    
    /**
     * Audio level visualization with smooth animations
     */
    @Composable
    fun AudioLevelMeter(
        level: Float,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.primary,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        val animatedLevel by animateFloatAsState(
            targetValue = level.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 50,
                easing = LinearEasing
            ),
            label = "audio_level"
        )
        
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(backgroundColor, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedLevel)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
    
    /**
     * Step sequencer step highlight animation
     */
    @Composable
    fun SequencerStepHighlight(
        isActive: Boolean,
        isCurrentStep: Boolean,
        modifier: Modifier = Modifier
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isCurrentStep) 1.1f else 1f,
            animationSpec = AnimationSystem.QuickSpring,
            label = "step_scale"
        )
        
        val alpha by animateFloatAsState(
            targetValue = when {
                isCurrentStep -> 1f
                isActive -> 0.8f
                else -> 0.3f
            },
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "step_alpha"
        )
        
        val color = when {
            isCurrentStep -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outline
        }
        
        Box(
            modifier = modifier
                .scale(scale)
                .graphicsLayer { this.alpha = alpha }
                .background(color, RoundedCornerShape(4.dp))
        )
    }
    
    /**
     * MIDI activity indicator
     */
    @Composable
    fun MidiActivityIndicator(
        isActive: Boolean,
        modifier: Modifier = Modifier
    ) {
        val pulseScale by animateFloatAsState(
            targetValue = if (isActive) 1.2f else 1f,
            animationSpec = if (isActive) {
                infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(AnimationSystem.FAST_ANIMATION)
            },
            label = "midi_pulse"
        )
        
        val color by animateColorAsState(
            targetValue = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "midi_color"
        )
        
        Box(
            modifier = modifier
                .size(8.dp)
                .scale(pulseScale)
                .background(color, CircleShape)
        )
    }
    
    /**
     * Loading state with pulsing animation
     */
    @Composable
    fun PulsingLoadingIndicator(
        isLoading: Boolean,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.primary
    ) {
        val alpha by animateFloatAsState(
            targetValue = if (isLoading) 0.5f else 1f,
            animationSpec = if (isLoading) {
                infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            } else {
                tween(AnimationSystem.FAST_ANIMATION)
            },
            label = "loading_pulse"
        )
        
        Box(
            modifier = modifier
                .graphicsLayer { this.alpha = alpha }
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}

/**
 * Composable for managing multiple visual feedback states
 */
@Composable
fun VisualFeedbackManager(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var successTrigger by remember { mutableStateOf(false) }
    var errorTrigger by remember { mutableStateOf(false) }
    var rippleTrigger by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        content()
        
        // Overlay feedback elements
        VisualFeedbackSystem.TouchRipple(
            triggered = rippleTrigger,
            modifier = Modifier.align(Alignment.Center)
        )
        
        VisualFeedbackSystem.SuccessFeedback(
            triggered = successTrigger,
            modifier = Modifier.align(Alignment.Center),
            onAnimationComplete = { successTrigger = false }
        )
        
        VisualFeedbackSystem.ErrorFeedback(
            triggered = errorTrigger,
            modifier = Modifier.align(Alignment.Center),
            onAnimationComplete = { errorTrigger = false }
        )
    }
}