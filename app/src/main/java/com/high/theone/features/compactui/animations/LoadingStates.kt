package com.high.theone.features.compactui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
                SpinningLoadingIndicator(
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
                SpinningLoadingIndicator(
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
                        SpinningLoadingIndicator(
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