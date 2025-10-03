package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.high.theone.model.PadState
import kotlin.math.*

/**
 * Advanced visual feedback system for drum pads.
 * Provides real-time playing indicators, waveform thumbnails, and state animations.
 * 
 * Requirements: 6.1 (real-time indicators), 6.2 (waveform thumbnails), 6.3 (state animations)
 */

/**
 * Waveform thumbnail display for loaded samples.
 * Shows a miniature waveform visualization within the pad.
 */
@Composable
fun WaveformThumbnail(
    waveformData: FloatArray,
    isPlaying: Boolean,
    playbackPosition: Float = 0f,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val animatedPlaybackPosition by animateFloatAsState(
        targetValue = if (isPlaying) playbackPosition else 0f,
        animationSpec = tween(100),
        label = "waveform_playback_position"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        drawWaveform(
            waveformData = waveformData,
            color = color,
            playbackPosition = animatedPlaybackPosition,
            isPlaying = isPlaying
        )
    }
}

/**
 * Enhanced real-time playing indicator with smooth pulsing animation and velocity response.
 */
@Composable
fun PlayingIndicator(
    isPlaying: Boolean,
    velocity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "playing_indicator")
    
    // Enhanced pulsing with velocity-based timing
    val pulseSpeed = (800 - velocity * 300).toInt().coerceIn(300, 800)
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playing_alpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f + velocity * 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed / 2, easing = EaseInOutBack),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playing_scale"
    )
    
    // Ripple effect for high velocity hits
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (velocity > 0.7f) 2f else 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )
    
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )
    
    if (isPlaying) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            // Outer ripple effect for high velocity
            if (velocity > 0.5f) {
                Box(
                    modifier = Modifier
                        .size((12 + velocity * 8).dp)
                        .graphicsLayer {
                            scaleX = rippleScale
                            scaleY = rippleScale
                        }
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = rippleAlpha * velocity)
                        )
                )
            }
            
            // Main pulsing indicator
            Box(
                modifier = Modifier
                    .size((8 + velocity * 6).dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )
            )
        }
    }
}

/**
 * Enhanced loading animation with progress indication and smooth transitions.
 */
@Composable
fun LoadingIndicator(
    progress: Float = -1f, // -1 for indeterminate, 0-1 for determinate
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_indicator")
    
    // Smooth rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loading_rotation"
    )
    
    // Pulsing scale for visual appeal
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_scale"
    )
    
    Box(
        modifier = modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (progress >= 0f) {
            // Determinate progress indicator
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { 
                        scaleX = scale
                        scaleY = scale
                    },
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        } else {
            // Indeterminate loading indicator
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { 
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    },
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        // Center dot for additional visual feedback
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
    }
}

/**
 * Error state indicator for pads with sample loading errors.
 */
@Composable
fun ErrorIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "error_indicator")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "error_alpha"
    )
    
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = alpha)
            )
    )
}

/**
 * Volume level indicator showing current pad volume.
 */
@Composable
fun VolumeLevelIndicator(
    volume: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedVolume by animateFloatAsState(
        targetValue = if (isActive) volume else 0f,
        animationSpec = tween(200),
        label = "volume_level"
    )
    
    Canvas(
        modifier = modifier
            .width(4.dp)
            .height(24.dp)
    ) {
        drawVolumeLevel(
            volume = animatedVolume,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Pan position indicator showing stereo positioning.
 */
@Composable
fun PanIndicator(
    pan: Float, // -1.0 to 1.0
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedPan by animateFloatAsState(
        targetValue = if (isActive) pan else 0f,
        animationSpec = tween(200),
        label = "pan_position"
    )
    
    Canvas(
        modifier = modifier
            .width(20.dp)
            .height(4.dp)
    ) {
        drawPanIndicator(
            pan = animatedPan,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

/**
 * Velocity sensitivity indicator showing last trigger velocity.
 */
@Composable
fun VelocityIndicator(
    velocity: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "velocity_alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "velocity_scale"
    )
    
    Box(
        modifier = modifier
            .size((6 + velocity * 8).dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = alpha * velocity)
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

/**
 * Comprehensive pad state overlay that combines all visual feedback elements.
 */
@Composable
fun PadStateOverlay(
    padState: PadState,
    waveformData: FloatArray? = null,
    playbackPosition: Float = 0f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Waveform thumbnail (bottom area)
        if (padState.hasAssignedSample && waveformData != null && waveformData.isNotEmpty()) {
            WaveformThumbnail(
                waveformData = waveformData,
                isPlaying = padState.isPlaying,
                playbackPosition = playbackPosition,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
        
        // Playing indicator (center)
        if (padState.isPlaying) {
            PlayingIndicator(
                isPlaying = true,
                velocity = padState.lastTriggerVelocity,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Loading indicator (center)
        if (padState.isLoading) {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Volume indicator (right side)
        if (padState.hasAssignedSample && padState.volume != 1f) {
            VolumeLevelIndicator(
                volume = padState.volume,
                isActive = padState.hasAssignedSample,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            )
        }
        
        // Pan indicator (top)
        if (padState.hasAssignedSample && abs(padState.pan) > 0.1f) {
            PanIndicator(
                pan = padState.pan,
                isActive = padState.hasAssignedSample,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }
        
        // Velocity indicator (top-right corner)
        if (padState.lastTriggerVelocity > 0f) {
            VelocityIndicator(
                velocity = padState.lastTriggerVelocity,
                isVisible = padState.isPlaying,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Draw waveform visualization on canvas.
 */
private fun DrawScope.drawWaveform(
    waveformData: FloatArray,
    color: Color,
    playbackPosition: Float,
    isPlaying: Boolean
) {
    if (waveformData.isEmpty()) return
    
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val samplesPerPixel = waveformData.size / width.toInt().coerceAtLeast(1)
    
    val path = Path()
    var isFirstPoint = true
    
    for (x in 0 until width.toInt()) {
        val sampleIndex = (x * samplesPerPixel).coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY + (amplitude * centerY * 0.8f)
        
        if (isFirstPoint) {
            path.moveTo(x.toFloat(), y)
            isFirstPoint = false
        } else {
            path.lineTo(x.toFloat(), y)
        }
    }
    
    // Draw waveform
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.dp.toPx())
    )
    
    // Draw playback position indicator
    if (isPlaying && playbackPosition > 0f) {
        val playbackX = width * playbackPosition
        drawLine(
            color = MaterialTheme.colorScheme.primary,
            start = Offset(playbackX, 0f),
            end = Offset(playbackX, height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * Draw volume level indicator on canvas.
 */
private fun DrawScope.drawVolumeLevel(
    volume: Float,
    color: Color
) {
    val width = size.width
    val height = size.height
    val levelHeight = height * volume
    
    // Background
    drawRoundRect(
        color = color.copy(alpha = 0.2f),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f)
    )
    
    // Volume level
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, height - levelHeight),
        size = androidx.compose.ui.geometry.Size(width, levelHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f)
    )
}

/**
 * Draw pan position indicator on canvas.
 */
private fun DrawScope.drawPanIndicator(
    pan: Float, // -1.0 to 1.0
    color: Color
) {
    val width = size.width
    val height = size.height
    val centerX = width / 2f
    val panX = centerX + (pan * centerX * 0.8f)
    
    // Background track
    drawRoundRect(
        color = color.copy(alpha = 0.2f),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2f)
    )
    
    // Pan position dot
    drawCircle(
        color = color,
        radius = height / 2f,
        center = Offset(panX, height / 2f)
    )
}
/**

 * Sample loading progress indicator with smooth animations and visual feedback.
 */
@Composable
fun SampleLoadingProgress(
    progress: Float, // 0.0 to 1.0
    sampleName: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) progress else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sample_loading_progress"
    )
    
    val slideOffset by animateIntAsState(
        targetValue = if (isVisible) 0 else 100,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "slide_offset"
    )
    
    if (isVisible) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .offset(y = slideOffset.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loading: $sampleName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

/**
 * Enhanced pad press animation with ripple effect and velocity response.
 */
@Composable
fun PadPressAnimation(
    isPressed: Boolean,
    velocity: Float = 1f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f - (velocity * 0.05f) else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pad_press_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 6f + (velocity * 4f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pad_press_elevation"
    )
    
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        content()
    }
}

/**
 * Interactive feedback for all user interactions with smooth transitions.
 */
@Composable
fun InteractionFeedback(
    isHovered: Boolean = false,
    isPressed: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "interaction_background"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "interaction_border"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "interaction_scale"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
    ) {
        content()
    }
}