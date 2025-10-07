package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Micro-interactions for enhanced user experience
 * Provides subtle animations and feedback for common UI interactions
 */
object MicroInteractions {
    
    /**
     * Enhanced button with press animation and haptic feedback
     */
    @Composable
    fun AnimatedButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        hapticEnabled: Boolean = true,
        content: @Composable RowScope.() -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = AnimationSystem.QuickSpring,
            label = "button_scale"
        )
        
        val elevation by animateDpAsState(
            targetValue = if (isPressed) 1.dp else 4.dp,
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "button_elevation"
        )
        
        Button(
            onClick = {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            },
            enabled = enabled,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = elevation,
                pressedElevation = 1.dp
            ),
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            content = content
        )
    }
    
    /**
     * Floating Action Button with enhanced animations
     */
    @Composable
    fun AnimatedFAB(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        expanded: Boolean = false,
        icon: @Composable () -> Unit,
        text: @Composable (() -> Unit)? = null
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else 1f,
            animationSpec = AnimationSystem.BounceSpring,
            label = "fab_scale"
        )
        
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            animationSpec = tween(
                durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                easing = AnimationSystem.FastOutSlowIn
            ),
            label = "fab_rotation"
        )
        
        ExtendedFloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            icon = {
                Box(
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                ) {
                    icon()
                }
            },
            text = text ?: {},
            expanded = expanded,
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
        )
    }
    
    /**
     * Switch with smooth transition animations
     */
    @Composable
    fun AnimatedSwitch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        val haptic = LocalHapticFeedback.current
        
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(newValue)
            },
            enabled = enabled,
            modifier = modifier
        )
    }
    
    /**
     * Slider with enhanced visual feedback
     */
    @Composable
    fun AnimatedSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        steps: Int = 0,
        onValueChangeFinished: (() -> Unit)? = null
    ) {
        val haptic = LocalHapticFeedback.current
        var isDragging by remember { mutableStateOf(false) }
        
        val thumbScale by animateFloatAsState(
            targetValue = if (isDragging) 1.2f else 1f,
            animationSpec = AnimationSystem.QuickSpring,
            label = "slider_thumb_scale"
        )
        
        Slider(
            value = value,
            onValueChange = { newValue ->
                if (!isDragging) {
                    isDragging = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onValueChange(newValue)
            },
            modifier = modifier.graphicsLayer { scaleX = thumbScale; scaleY = thumbScale },
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = {
                isDragging = false
                onValueChangeFinished?.invoke()
            }
        )
    }
    
    /**
     * Card with hover and press animations
     */
    @Composable
    fun AnimatedCard(
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable ColumnScope.() -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }
        var isHovered by remember { mutableStateOf(false) }
        
        val scale by animateFloatAsState(
            targetValue = when {
                isPressed -> 0.98f
                isHovered -> 1.02f
                else -> 1f
            },
            animationSpec = AnimationSystem.QuickSpring,
            label = "card_scale"
        )
        
        val elevation by animateDpAsState(
            targetValue = when {
                isPressed -> 2.dp
                isHovered -> 8.dp
                else -> 4.dp
            },
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "card_elevation"
        )
        
        Card(
            onClick = onClick ?: {},
            enabled = enabled,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            content = content
        )
    }
    
    /**
     * Chip with selection animation
     */
    @Composable
    fun AnimatedChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        leadingIcon: @Composable (() -> Unit)? = null
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = AnimationSystem.QuickSpring,
            label = "chip_scale"
        )
        
        FilterChip(
            selected = selected,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            label = label,
            enabled = enabled,
            leadingIcon = leadingIcon,
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
        )
    }
    
    /**
     * Icon button with ripple and scale animation
     */
    @Composable
    fun AnimatedIconButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.9f else 1f,
            animationSpec = AnimationSystem.BounceSpring,
            label = "icon_button_scale"
        )
        
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            enabled = enabled,
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            content = content
        )
    }
}

/**
 * Staggered animation for lists and grids
 */
@Composable
fun StaggeredAnimation(
    visible: Boolean,
    itemCount: Int,
    modifier: Modifier = Modifier,
    staggerDelay: Int = 50,
    content: @Composable (index: Int) -> Unit
) {
    Column(modifier = modifier) {
        repeat(itemCount) { index ->
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                        delayMillis = index * staggerDelay,
                        easing = AnimationSystem.FastOutSlowIn
                    ),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = AnimationSystem.MEDIUM_ANIMATION,
                        delayMillis = index * staggerDelay
                    )
                ),
                exit = slideOutVertically(
                    animationSpec = tween(
                        durationMillis = AnimationSystem.FAST_ANIMATION,
                        easing = AnimationSystem.FastOutLinearIn
                    ),
                    targetOffsetY = { -it / 2 }
                ) + fadeOut(
                    animationSpec = tween(AnimationSystem.FAST_ANIMATION)
                )
            ) {
                content(index)
            }
        }
    }
}