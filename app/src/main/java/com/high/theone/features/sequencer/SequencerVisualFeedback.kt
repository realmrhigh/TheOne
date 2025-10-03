package com.high.theone.features.sequencer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual feedback system for the sequencer providing playback position indicators,
 * velocity visualization, and step state animations.
 * 
 * Requirements: 1.4, 6.4, 6.5, 6.6, 9.1, 9.2, 9.3, 9.4
 */

/**
 * Playback position indicator that shows current step with smooth animation
 */
@Composable
fun PlaybackPositionIndicator(
    currentStep: Int,
    patternLength: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (patternLength > 0) currentStep.toFloat() / patternLength else 0f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 100, easing = LinearEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "playback_progress"
    )
    
    val indicatorColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        label = "playback_indicator_color"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(2.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(
                    color = indicatorColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        
        // Current position marker
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(x = (progress * (modifier.fillMaxWidth().toString().length * 8)).dp) // Approximate positioning
                    .background(
                        color = indicatorColor,
                        shape = CircleShape
                    )
                    .align(Alignment.CenterStart)
            )
        }
    }
}

/**
 * Circular progress indicator for pattern playback
 */
@Composable
fun CircularPlaybackIndicator(
    currentStep: Int,
    patternLength: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (patternLength > 0) currentStep.toFloat() / patternLength else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 100, easing = LinearEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "circular_progress"
    )
    
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier.size(48.dp)
    ) {
        val strokeWidth = with(density) { 4.dp.toPx() }
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        // Background circle
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        // Progress arc
        if (animatedProgress > 0f) {
            drawArc(
                color = if (isPlaying) Color.Blue else Color.Gray,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                topLeft = Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
        }
        
        // Current step marker
        if (isPlaying && animatedProgress > 0f) {
            val angle = (animatedProgress * 360f - 90f) * (Math.PI / 180f)
            val markerX = center.x + cos(angle).toFloat() * radius
            val markerY = center.y + sin(angle).toFloat() * radius
            
            drawCircle(
                color = Color.Blue,
                radius = strokeWidth,
                center = Offset(markerX, markerY)
            )
        }
    }
}

/**
 * Velocity visualization component with color coding
 */
@Composable
fun VelocityVisualization(
    velocity: Int,
    maxVelocity: Int = 127,
    showNumeric: Boolean = false,
    modifier: Modifier = Modifier
) {
    val velocityRatio = (velocity.toFloat() / maxVelocity).coerceIn(0f, 1f)
    
    val velocityColor = when {
        velocityRatio < 0.3f -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        velocityRatio < 0.6f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        velocityRatio < 0.8f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Visual velocity bar
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(velocityRatio)
                    .background(
                        color = velocityColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
        
        // Numeric display
        if (showNumeric) {
            Text(
                text = velocity.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Step highlighting animation for current playback position
 */
@Composable
fun StepHighlight(
    isCurrentStep: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var pulseState by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCurrentStep) {
        if (isCurrentStep) {
            while (true) {
                pulseState = !pulseState
                kotlinx.coroutines.delay(250) // Pulse every 250ms
            }
        } else {
            pulseState = false
        }
    }
    
    val highlightAlpha by animateFloatAsState(
        targetValue = when {
            isCurrentStep && pulseState -> 0.8f
            isCurrentStep -> 0.4f
            isActive -> 0.2f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 200),
        label = "step_highlight"
    )
    
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha),
                shape = RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Visual distinction for different step states
 */
@Composable
fun StepStateIndicator(
    stepState: StepState,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (stepState) {
        StepState.INACTIVE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) to null
        StepState.ACTIVE -> MaterialTheme.colorScheme.primary to "●"
        StepState.CURRENT -> MaterialTheme.colorScheme.primary to "▶"
        StepState.CURRENT_ACTIVE -> MaterialTheme.colorScheme.primary to "●▶"
        StepState.MUTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f) to "✕"
    }
    
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        icon?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * Animated pattern length indicator
 */
@Composable
fun PatternLengthIndicator(
    currentLength: Int,
    maxLength: Int = 32,
    onLengthChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lengthRatio = currentLength.toFloat() / maxLength
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Length: $currentLength",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(lengthRatio)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

/**
 * Tempo visualization with BPM indicator
 */
@Composable
fun TempoIndicator(
    currentTempo: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var beatState by remember { mutableStateOf(false) }
    
    LaunchedEffect(currentTempo, isPlaying) {
        if (isPlaying) {
            val beatInterval = (60000f / currentTempo / 4f).toLong() // 16th note interval
            while (true) {
                beatState = !beatState
                kotlinx.coroutines.delay(beatInterval)
            }
        } else {
            beatState = false
        }
    }
    
    val beatAlpha by animateFloatAsState(
        targetValue = if (beatState && isPlaying) 1f else 0.3f,
        animationSpec = tween(durationMillis = 50),
        label = "tempo_beat"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Beat indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = beatAlpha),
                    shape = CircleShape
                )
        )
        
        // BPM text
        Text(
            text = "${currentTempo.toInt()} BPM",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Enum representing different step states for visual feedback
 */
enum class StepState {
    INACTIVE,
    ACTIVE,
    CURRENT,
    CURRENT_ACTIVE,
    MUTED
}

/**
 * Extension function to determine step state based on conditions
 */
fun getStepState(
    isActive: Boolean,
    isCurrentStep: Boolean,
    isMuted: Boolean = false
): StepState = when {
    isMuted -> StepState.MUTED
    isCurrentStep && isActive -> StepState.CURRENT_ACTIVE
    isCurrentStep -> StepState.CURRENT
    isActive -> StepState.ACTIVE
    else -> StepState.INACTIVE
}