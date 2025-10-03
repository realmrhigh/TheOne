package com.high.theone.features.sequencer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Individual step button component with tap-to-toggle and long-press velocity editing.
 * Provides visual feedback for step states and current playback position.
 * 
 * Requirements: 1.2, 6.1, 6.2, 6.3, 9.2, 9.4
 */
@Composable
fun StepButton(
    isActive: Boolean,
    velocity: Int = 100,
    isCurrentStep: Boolean = false,
    isEnabled: Boolean = true,
    onToggle: () -> Unit,
    onVelocityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    var showVelocityEditor by remember { mutableStateOf(false) }
    
    // Animation states
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "step_button_scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = getStepButtonColor(
            isActive = isActive,
            isCurrentStep = isCurrentStep,
            isEnabled = isEnabled,
            velocity = velocity
        ),
        animationSpec = tween(durationMillis = 150),
        label = "step_button_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrentStep -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "step_button_border"
    )
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = if (isCurrentStep) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            
                            // Wait for long press threshold
                            val longPressJob = kotlinx.coroutines.launch {
                                delay(500) // 500ms for long press
                                if (isPressed) {
                                    showVelocityEditor = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            
                            // Wait for release
                            tryAwaitRelease()
                            longPressJob.cancel()
                            isPressed = false
                        },
                        onTap = {
                            if (!showVelocityEditor) {
                                onToggle()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Velocity visualization
        if (isActive) {
            VelocityIndicator(
                velocity = velocity,
                isCurrentStep = isCurrentStep,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Current step pulse animation
        if (isCurrentStep) {
            CurrentStepPulse(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    // Velocity editor dialog
    if (showVelocityEditor) {
        VelocityEditorDialog(
            currentVelocity = velocity,
            onVelocityChange = onVelocityChange,
            onDismiss = { showVelocityEditor = false }
        )
    }
}

/**
 * Determines the background color for a step button based on its state
 */
@Composable
private fun getStepButtonColor(
    isActive: Boolean,
    isCurrentStep: Boolean,
    isEnabled: Boolean,
    velocity: Int
): Color {
    return when {
        !isEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        isActive -> {
            val alpha = (velocity / 127f).coerceIn(0.4f, 1.0f)
            if (isCurrentStep) {
                MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
            }
        }
        isCurrentStep -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }
}

/**
 * Visual indicator showing velocity level within an active step
 */
@Composable
private fun VelocityIndicator(
    velocity: Int,
    isCurrentStep: Boolean,
    modifier: Modifier = Modifier
) {
    val velocityRatio = (velocity / 127f).coerceIn(0f, 1f)
    
    Box(
        modifier = modifier.padding(2.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Velocity bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(velocityRatio)
                .background(
                    color = if (isCurrentStep) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    },
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * Pulsing animation for the current step indicator
 */
@Composable
private fun CurrentStepPulse(
    modifier: Modifier = Modifier
) {
    var pulseState by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (true) {
            pulseState = !pulseState
            delay(300) // Pulse every 300ms
        }
    }
    
    val pulseAlpha by animateFloatAsState(
        targetValue = if (pulseState) 0.8f else 0.3f,
        animationSpec = tween(durationMillis = 300),
        label = "current_step_pulse"
    )
    
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                shape = RoundedCornerShape(6.dp)
            )
    )
}

/**
 * Dialog for editing step velocity with slider control
 */
@Composable
private fun VelocityEditorDialog(
    currentVelocity: Int,
    onVelocityChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempVelocity by remember { mutableStateOf(currentVelocity.toFloat()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Step Velocity",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = "Velocity: ${tempVelocity.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Slider(
                    value = tempVelocity,
                    onValueChange = { tempVelocity = it },
                    valueRange = 1f..127f,
                    steps = 125, // 127 total values minus endpoints
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Velocity level indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Soft",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Medium",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Hard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onVelocityChange(tempVelocity.toInt())
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Preset velocity values for quick selection
 */
object VelocityPresets {
    const val SOFT = 40
    const val MEDIUM_SOFT = 60
    const val MEDIUM = 80
    const val MEDIUM_HARD = 100
    const val HARD = 120
    const val MAX = 127
    
    val ALL = listOf(SOFT, MEDIUM_SOFT, MEDIUM, MEDIUM_HARD, HARD, MAX)
    
    fun getPresetName(velocity: Int): String = when (velocity) {
        SOFT -> "Soft"
        MEDIUM_SOFT -> "Medium Soft"
        MEDIUM -> "Medium"
        MEDIUM_HARD -> "Medium Hard"
        HARD -> "Hard"
        MAX -> "Max"
        else -> "Custom"
    }
}