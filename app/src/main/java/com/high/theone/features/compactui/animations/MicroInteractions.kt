package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
    
    /**
     * Enhanced recording button with advanced haptic feedback and animations
     */
    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun RecordingButton(
        isRecording: Boolean,
        isInitializing: Boolean,
        canRecord: Boolean,
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        
        // Enhanced haptic feedback for recording state changes
        LaunchedEffect(isRecording) {
            if (isRecording) {
                // Triple pulse pattern for recording start
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(80)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(80)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        
        // Haptic feedback for initialization
        LaunchedEffect(isInitializing) {
            if (isInitializing) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        
        FloatingActionButton(
            onClick = {
                if (isRecording) {
                    // Double pulse for recording stop
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStopRecording()
                } else if (canRecord && !isInitializing) {
                    // Single strong pulse for recording start
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRecording()
                } else if (!canRecord) {
                    // Error haptic pattern
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            modifier = modifier
                .recordingButtonAnimation(
                    isRecording = isRecording,
                    isPressed = isPressed,
                    isInitializing = isInitializing
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            if (canRecord || isRecording) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            containerColor = when {
                isInitializing -> MaterialTheme.colorScheme.tertiary
                isRecording -> MaterialTheme.colorScheme.error
                !canRecord -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = when {
                isInitializing -> MaterialTheme.colorScheme.onTertiary
                isRecording -> MaterialTheme.colorScheme.onError
                !canRecord -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onPrimary
            }
        ) {
            AnimatedContent(
                targetState = when {
                    isInitializing -> "initializing"
                    isRecording -> "recording"
                    else -> "ready"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(AnimationSystem.FAST_ANIMATION)) with
                    fadeOut(animationSpec = tween(AnimationSystem.FAST_ANIMATION))
                },
                label = "recording_button_content"
            ) { state ->
                when (state) {
                    "initializing" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 2.dp
                        )
                    }
                    "recording" -> {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Pad button with velocity-sensitive haptic feedback
     */
    @Composable
    fun VelocitySensitivePadButton(
        onClick: (Float) -> Unit,
        onLongPress: () -> Unit = {},
        hasAssignedSample: Boolean,
        isPlaying: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        var pressStartTime by remember { mutableStateOf(0L) }
        
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else if (isPlaying) 1.05f else 1f,
            animationSpec = AnimationSystem.QuickSpring,
            label = "pad_scale"
        )
        
        val elevation by animateDpAsState(
            targetValue = if (isPressed) 2.dp else if (isPlaying) 8.dp else 4.dp,
            animationSpec = tween(AnimationSystem.FAST_ANIMATION),
            label = "pad_elevation"
        )
        
        Card(
            onClick = {
                val pressDuration = System.currentTimeMillis() - pressStartTime
                val velocity = (pressDuration / 200f).coerceIn(0.1f, 1f)
                
                // Velocity-sensitive haptic feedback
                when {
                    !hasAssignedSample -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    velocity > 0.8f -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    velocity > 0.5f -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    else -> {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                
                onClick(velocity)
            },
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            modifier = modifier
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            pressStartTime = System.currentTimeMillis()
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    )
                },
            content = { content() }
        )
    }
    
    /**
     * Sample assignment confirmation button with success feedback
     */
    @Composable
    fun SampleAssignmentButton(
        padIndex: Int,
        isSelected: Boolean,
        onAssign: (Int) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val haptic = LocalHapticFeedback.current
        var showSuccess by remember { mutableStateOf(false) }
        
        LaunchedEffect(showSuccess) {
            if (showSuccess) {
                // Success haptic pattern
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(100)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(1500)
                showSuccess = false
            }
        }
        
        AnimatedButton(
            onClick = {
                onAssign(padIndex)
                showSuccess = true
            },
            modifier = modifier,
            hapticEnabled = false // We handle haptics manually
        ) {
            if (showSuccess) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Assigned",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Assigned!")
            } else {
                Text("Pad ${padIndex + 1}")
            }
        }
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