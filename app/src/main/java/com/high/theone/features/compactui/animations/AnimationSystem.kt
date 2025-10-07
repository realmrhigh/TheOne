package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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