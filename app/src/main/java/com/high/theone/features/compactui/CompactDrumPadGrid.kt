package com.high.theone.features.compactui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import com.high.theone.features.compactui.animations.VisualFeedbackSystem
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.features.compactui.animations.padPressAnimation
import com.high.theone.features.sampling.MidiPadTriggerEvent
import com.high.theone.features.sampling.MidiPadStopEvent
import com.high.theone.model.ScreenConfiguration
import com.high.theone.model.PadState
import com.high.theone.features.compactui.accessibility.*
import kotlin.math.*

/**
 * Enhanced drum pad grid component optimized for compact UI layouts.
 * Provides adaptive sizing, sample name/waveform overlays, velocity-sensitive feedback,
 * and MIDI integration with visual feedback.
 * 
 * Requirements: 2.1 (pad display), 2.2 (sample indicators), 2.4 (velocity feedback), 
 *              2.3 (MIDI integration), 2.5 (MIDI highlighting), 7.1 (visual feedback)
 */
@Composable
fun CompactDrumPadGrid(
    pads: List<PadState>,
    onPadTap: (Int, Float) -> Unit,
    onPadLongPress: (Int) -> Unit,
    screenConfiguration: ScreenConfiguration,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSampleNames: Boolean = true,
    showWaveformPreviews: Boolean = true,
    onMidiTrigger: ((MidiPadTriggerEvent) -> Unit)? = null,
    onMidiStop: ((MidiPadStopEvent) -> Unit)? = null,
    midiHighlightedPads: Set<Int> = emptySet() // Pads currently highlighted by MIDI input
) {
    // Calculate square pad size that fills the width
    val density = LocalDensity.current
    val screenWidth = screenConfiguration.screenWidth
    val availableWidth = with(density) { screenWidth.toPx() } - 32f // Account for padding
    val padSizePx = availableWidth / 4f
    val padSize = with(density) { padSizePx.toDp() }
    val padSpacing = 4.dp // Fixed small spacing
    
    // Ensure we have exactly 16 pads
    val padList = pads.take(16).let { currentPads ->
        if (currentPads.size < 16) {
            currentPads + List(16 - currentPads.size) { index ->
                PadState(index = currentPads.size + index)
            }
        } else {
            currentPads
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(padSpacing)
    ) {
        // Create 4 rows of 4 pads each
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(padSpacing, Alignment.CenterHorizontally)
            ) {
                for (col in 0 until 4) {
                    val padIndex = row * 4 + col
                    val padState = padList[padIndex]
                    val isMidiHighlighted = midiHighlightedPads.contains(padIndex)
                    
                    CompactPad(
                        padState = padState,
                        onTap = { velocity -> onPadTap(padIndex, velocity) },
                        onLongPress = { onPadLongPress(padIndex) },
                        enabled = enabled,
                        padSize = padSize,
                        showSampleName = showSampleNames,
                        showWaveformPreview = showWaveformPreviews,
                        isMidiHighlighted = isMidiHighlighted,
                        modifier = Modifier.size(padSize)
                    )
                }
            }
        }
    }
}

/**
 * Calculate adaptive pad size based on screen configuration for optimal space utilization.
 * 
 * Requirements: 6.1 (responsive layout), 6.2 (space optimization)
 */
@Composable
private fun calculateAdaptivePadSize(screenConfiguration: ScreenConfiguration): Dp {
    val density = LocalDensity.current
    
    return when (screenConfiguration.layoutMode) {
        com.high.theone.model.LayoutMode.COMPACT_PORTRAIT -> {
            // Smaller pads for compact screens
            val availableWidth = screenConfiguration.screenWidth.value - 32f // Account for padding
            val padWithSpacing = availableWidth / 4f
            ((padWithSpacing * 0.85f).coerceAtLeast(48f)).dp // Minimum touch target
        }
        com.high.theone.model.LayoutMode.STANDARD_PORTRAIT -> {
            // Standard size for normal portrait
            val availableWidth = screenConfiguration.screenWidth.value - 32f
            val padWithSpacing = availableWidth / 4f
            ((padWithSpacing * 0.9f).coerceAtLeast(56f)).dp
        }
        com.high.theone.model.LayoutMode.LANDSCAPE -> {
            // Larger pads in landscape with more space
            val availableWidth = screenConfiguration.screenWidth.value * 0.6f - 32f // Reserve space for other components
            val padWithSpacing = availableWidth / 4f
            ((padWithSpacing * 0.9f).coerceAtLeast(64f)).dp
        }
        com.high.theone.model.LayoutMode.TABLET -> {
            // Largest pads for tablets
            val availableWidth = screenConfiguration.screenWidth.value * 0.5f - 32f
            val padWithSpacing = availableWidth / 4f
            ((padWithSpacing * 0.9f).coerceAtLeast(80f)).dp
        }
    }
}

/**
 * Individual compact pad component with enhanced visual feedback and overlays.
 * 
 * Requirements: 2.1 (pad triggering), 2.2 (sample indicators), 2.4 (velocity feedback),
 *              2.5 (MIDI highlighting), 7.1 (visual feedback), 4.2 (visual confirmation)
 */
@Composable
private fun CompactPad(
    padState: PadState,
    onTap: (Float) -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean,
    padSize: Dp,
    showSampleName: Boolean,
    showWaveformPreview: Boolean,
    isMidiHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var pressVelocity by remember { mutableStateOf(0f) }
    var showAssignmentConfirmation by remember { mutableStateOf(false) }
    
    val hapticFeedback = LocalHapticFeedback.current
    
    // Detect when a new sample is assigned to show confirmation animation
    LaunchedEffect(padState.sampleId) {
        if (padState.hasAssignedSample && padState.sampleId != null) {
            showAssignmentConfirmation = true
            delay(2000) // Show confirmation for 2 seconds
            showAssignmentConfirmation = false
        }
    }
    
    // Use enhanced pad press animation from animation system
    val padPressModifier = Modifier.padPressAnimation(
        isPressed = isPressed,
        velocity = pressVelocity
    )
    
    // Animation for playing state with enhanced pulsing
    val playingScale by animateFloatAsState(
        targetValue = if (padState.isPlaying) 1.08f else 1f,
        animationSpec = if (padState.isPlaying) {
            infiniteRepeatable(
                animation = tween(250, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(150)
        },
        label = "compact_pad_playing_scale"
    )
    
    // MIDI highlight animation
    val midiHighlightAlpha by animateFloatAsState(
        targetValue = if (isMidiHighlighted) 0.8f else 0f,
        animationSpec = tween(100),
        label = "midi_highlight_alpha"
    )
    
    // Velocity-based color intensity with enhanced sensitivity
    val pressIntensity by animateFloatAsState(
        targetValue = if (isPressed) pressVelocity else 0f,
        animationSpec = tween(30), // Faster response for better feel
        label = "compact_pad_press_intensity"
    )
    
    // Determine pad colors with enhanced visual feedback
    val colorScheme = MaterialTheme.colorScheme
    val (backgroundColor, borderColor, contentColor) = getCompactPadColors(
        padState, enabled, pressIntensity, isMidiHighlighted, colorScheme
    )
    
    Box(
        modifier = modifier
            .size(padSize)
            .then(padPressModifier)
            .clip(RoundedCornerShape(8.dp)) // Slightly smaller radius for compact feel
            .background(backgroundColor)
            .border(
                width = when {
                    padState.isPlaying -> 3.dp
                    isMidiHighlighted -> 2.5.dp
                    else -> 1.5.dp
                },
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .drumPadSemantics(
                padState = padState,
                padIndex = padState.index,
                isPressed = isPressed,
                velocity = pressVelocity,
                isMidiHighlighted = isMidiHighlighted
            )
            .pointerInput(enabled, padState.canTrigger) {
                if (enabled && padState.canTrigger) {
                    detectTapGestures(
                        onPress = { offset ->
                            isPressed = true
                            // Calculate velocity based on press position (center = max velocity)
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val distanceFromCenter = sqrt(
                                (offset.x - centerX).pow(2) + (offset.y - centerY).pow(2)
                            )
                            val maxDistance = sqrt(centerX.pow(2) + centerY.pow(2))
                            val normalizedDistance = (distanceFromCenter / maxDistance).coerceIn(0f, 1f)
                            pressVelocity = (1f - normalizedDistance * 0.3f).coerceIn(0.3f, 1f)
                            
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            
                            // Wait for release
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = {
                            onTap(pressVelocity)
                        }
                    )
                } else if (enabled) {
                    // Handle taps on empty pads for assignment
                    detectTapGestures(
                        onLongPress = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                        onTap = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLongPress() // Open assignment dialog
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Waveform preview overlay (behind content)
        if (showWaveformPreview && padState.hasAssignedSample) {
            WaveformPreviewOverlay(
                padState = padState,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // MIDI highlight overlay
        if (isMidiHighlighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = midiHighlightAlpha),
                        RoundedCornerShape(8.dp)
                    )
            )
        }
        
        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            // Pad number - smaller font for compact layout
            Text(
                text = "${padState.index + 1}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )
            
            // Sample name or status - only show if enabled and space allows
            if (showSampleName && padSize > 56.dp) {
                Text(
                    text = when {
                        padState.isLoading -> "..."
                        padState.hasAssignedSample -> padState.sampleName?.take(8) ?: "Sample"
                        else -> "Empty"
                    },
                    fontSize = 8.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            }
        }
        
        // Velocity indicator overlay (top-right corner)
        if (isPressed && pressVelocity > 0f) {
            VelocityIndicator(
                velocity = pressVelocity,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        
        // Sample assignment confirmation overlay
        if (showAssignmentConfirmation) {
            SampleAssignmentConfirmation(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Waveform preview overlay for pads with assigned samples.
 * 
 * Requirements: 2.2 (waveform preview), 7.1 (visual feedback)
 */
@Composable
private fun WaveformPreviewOverlay(
    padState: PadState,
    modifier: Modifier = Modifier
) {
    // Use real waveform data from the audio engine when available; fall back to mock
    val waveformData = remember(padState.sampleId, padState.waveformData) {
        padState.waveformData
            ?.takeIf { it.isNotEmpty() }
            ?.toFloatArray()
            ?: generateMockWaveformForPad(padState)
    }

    val waveformColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    if (waveformData != null) {
        Canvas(modifier = modifier) {
            drawWaveform(
                waveformData = waveformData,
                color = waveformColor,
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Velocity indicator showing current press velocity.
 * 
 * Requirements: 2.4 (velocity feedback), 7.1 (visual feedback)
 */
@Composable
private fun VelocityIndicator(
    velocity: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        velocity > 0.8f -> MaterialTheme.colorScheme.error
        velocity > 0.5f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                color.copy(alpha = velocity),
                RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Draw waveform visualization on canvas.
 */
private fun DrawScope.drawWaveform(
    waveformData: FloatArray,
    color: Color,
    strokeWidth: Float
) {
    if (waveformData.isEmpty()) return
    
    val path = Path()
    val centerY = size.height / 2f
    val stepX = size.width / waveformData.size
    
    // Start path
    path.moveTo(0f, centerY)
    
    // Draw waveform
    waveformData.forEachIndexed { index, amplitude ->
        val x = index * stepX
        val y = centerY + (amplitude * centerY * 0.8f) // Scale amplitude to fit
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    // Draw the path
    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )
}

/**
 * Enhanced color calculation for compact pads with MIDI highlighting.
 * 
 * Requirements: 2.5 (MIDI highlighting), 7.1 (visual feedback)
 */
private fun getCompactPadColors(
    padState: PadState,
    enabled: Boolean,
    pressIntensity: Float = 0f,
    isMidiHighlighted: Boolean = false,
    colorScheme: ColorScheme
): Triple<Color, Color, Color> {
    
    // Base colors with enhanced contrast for compact layout
    val (baseBackground, baseBorder, baseContent) = when {
        !enabled -> Triple(
            colorScheme.surfaceVariant.copy(alpha = 0.4f),
            colorScheme.outline.copy(alpha = 0.4f),
            colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        
        padState.isLoading -> Triple(
            colorScheme.primaryContainer.copy(alpha = 0.8f),
            colorScheme.primary,
            colorScheme.onPrimaryContainer
        )
        
        padState.isPlaying -> Triple(
            colorScheme.primary,
            colorScheme.primary,
            colorScheme.onPrimary
        )
        
        padState.hasAssignedSample -> Triple(
            colorScheme.secondaryContainer.copy(alpha = 0.9f),
            colorScheme.secondary,
            colorScheme.onSecondaryContainer
        )
        
        else -> Triple(
            colorScheme.surface.copy(alpha = 0.8f),
            colorScheme.outline.copy(alpha = 0.6f),
            colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
    
    // Apply MIDI highlighting
    val midiColor = colorScheme.tertiary
    val (midiBackground, midiBorder) = if (isMidiHighlighted) {
        Pair(
            Color(
                red = lerp(baseBackground.red, midiColor.red, 0.4f),
                green = lerp(baseBackground.green, midiColor.green, 0.4f),
                blue = lerp(baseBackground.blue, midiColor.blue, 0.4f),
                alpha = baseBackground.alpha
            ),
            midiColor
        )
    } else {
        Pair(baseBackground, baseBorder)
    }
    
    // Apply press intensity effect
    val pressColor = colorScheme.primary
    val backgroundColor = if (pressIntensity > 0f && enabled) {
        Color(
            red = lerp(midiBackground.red, pressColor.red, pressIntensity * 0.4f),
            green = lerp(midiBackground.green, pressColor.green, pressIntensity * 0.4f),
            blue = lerp(midiBackground.blue, pressColor.blue, pressIntensity * 0.4f),
            alpha = midiBackground.alpha
        )
    } else {
        midiBackground
    }
    
    val borderColor = if (pressIntensity > 0f && enabled) {
        Color(
            red = lerp(midiBorder.red, pressColor.red, pressIntensity * 0.6f),
            green = lerp(midiBorder.green, pressColor.green, pressIntensity * 0.6f),
            blue = lerp(midiBorder.blue, pressColor.blue, pressIntensity * 0.6f),
            alpha = midiBorder.alpha
        )
    } else {
        midiBorder
    }
    
    return Triple(backgroundColor, borderColor, baseContent)
}

/**
 * Linear interpolation between two float values.
 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

/**
 * Sample assignment confirmation overlay with animation
 * 
 * Requirements: 4.2 (visual confirmation when sample is assigned to pad)
 */
@Composable
private fun SampleAssignmentConfirmation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "assignment_confirmation")
    
    // Pulsing scale animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "confirmation_scale"
    )
    
    // Fade in/out animation
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "confirmation_alpha"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.3f),
                RoundedCornerShape(8.dp)
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Sample Assigned",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Generate mock waveform data for visual feedback.
 * In a real implementation, this would come from the actual sample data.
 */
private fun generateMockWaveformForPad(padState: PadState): FloatArray? {
    if (!padState.hasAssignedSample) return null
    
    // Generate a more detailed waveform pattern for compact display
    val samples = 64 // Reduced for performance in compact layout
    return FloatArray(samples) { i ->
        val frequency = 0.08f + (padState.index % 4) * 0.04f
        val amplitude = 0.25f + padState.volume * 0.35f
        val phase = (padState.index % 8) * 0.2f
        val decay = 1f - (i.toFloat() / samples) * 0.7f
        val noise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f
        
        val baseWave = sin((i * frequency + phase) * 2 * PI) * amplitude * decay
        (baseWave + noise).toFloat().coerceIn(-1f, 1f)
    }
}