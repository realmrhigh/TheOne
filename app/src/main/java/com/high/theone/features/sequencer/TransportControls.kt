package com.high.theone.features.sequencer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.SequencerState

/**
 * Transport controls component with play/pause/stop/record buttons.
 * Provides visual feedback and animations for transport state changes.
 * 
 * Requirements: 2.1, 2.2, 2.3, 4.3
 */
@Composable
fun TransportControls(
    sequencerState: SequencerState,
    onTransportAction: (TransportAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause button
            TransportButton(
                icon = if (sequencerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = { 
                    onTransportAction(
                        if (sequencerState.isPlaying) TransportAction.Pause else TransportAction.Play
                    ) 
                },
                isActive = sequencerState.isPlaying,
                contentDescription = if (sequencerState.isPlaying) "Pause" else "Play",
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (sequencerState.isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            )
            
            // Stop button
            TransportButton(
                icon = Icons.Default.Stop,
                onClick = { onTransportAction(TransportAction.Stop) },
                isActive = false,
                contentDescription = "Stop"
            )
            
            // Record button with pulsing animation when recording
            RecordButton(
                isRecording = sequencerState.isRecording,
                onClick = { onTransportAction(TransportAction.ToggleRecord) }
            )
        }
    }
}

/**
 * Individual transport button with visual feedback
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    onClick: () -> Unit,
    isActive: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors()
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .scale(scale),
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Record button with pulsing animation when recording
 */
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "record_pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 0.7f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .scale(if (isRecording) pulseScale else 1f),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha)
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FiberManualRecord,
            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
            modifier = Modifier.size(24.dp),
            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * Transport state indicator showing current playback status
 */
@Composable
fun TransportStateIndicator(
    sequencerState: SequencerState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playback state indicator
        AnimatedVisibility(
            visible = sequencerState.isPlaying,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Recording state indicator
        AnimatedVisibility(
            visible = sequencerState.isRecording,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = "Recording",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "REC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        
        // Paused state indicator
        AnimatedVisibility(
            visible = sequencerState.isPaused,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Transport actions for sequencer control
 */
sealed class TransportAction {
    object Play : TransportAction()
    object Pause : TransportAction()
    object Stop : TransportAction()
    object ToggleRecord : TransportAction()
    data class SetTempo(val tempo: Float) : TransportAction()
    data class SetSwing(val swing: Float) : TransportAction()
    data class SetPatternLength(val length: Int) : TransportAction()
}