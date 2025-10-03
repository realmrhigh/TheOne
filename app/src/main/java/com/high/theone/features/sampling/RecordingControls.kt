package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.RecordingState

/**
 * RecordingControls composable provides the main interface for audio recording
 * including record button, level meter, duration display, and status indicators.
 * 
 * Requirements: 1.2 (recording status), 1.3 (level monitoring)
 */
@Composable
fun RecordingControls(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Status Header
            RecordingStatusHeader(recordingState = recordingState)
            
            // Main Record Button
            RecordButton(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )
            
            // Level Meter
            if (recordingState.isRecording || recordingState.peakLevel > 0.0f) {
                LevelMeter(
                    peakLevel = recordingState.peakLevel,
                    averageLevel = recordingState.averageLevel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Duration Display
            if (recordingState.isRecording || recordingState.durationMs > 0) {
                DurationDisplay(
                    recordingState = recordingState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Error Display
            recordingState.error?.let { error ->
                ErrorMessage(
                    error = error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Recording status header showing current recording state
 */
@Composable
private fun RecordingStatusHeader(
    recordingState: RecordingState
) {
    val statusText = when {
        recordingState.isProcessing -> "Processing..."
        recordingState.isRecording -> "Recording"
        recordingState.isPaused -> "Paused"
        recordingState.durationMs > 0 -> "Ready"
        recordingState.error != null -> "Error"
        !recordingState.isInitialized -> "Initializing..."
        else -> "Ready to Record"
    }
    
    val statusColor = when {
        recordingState.isProcessing -> MaterialTheme.colorScheme.tertiary
        recordingState.isRecording -> MaterialTheme.colorScheme.error
        recordingState.isPaused -> MaterialTheme.colorScheme.secondary
        recordingState.error != null -> MaterialTheme.colorScheme.error
        !recordingState.isInitialized -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Main record button with visual states and animations
 */
@Composable
private fun RecordButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    // Pulsing animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "record_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val buttonSize = 80.dp
    val buttonColor = when {
        recordingState.isProcessing -> MaterialTheme.colorScheme.tertiary
        recordingState.isRecording -> MaterialTheme.colorScheme.error
        !recordingState.canStartRecording -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    
    val buttonAlpha = if (recordingState.isRecording) pulseAlpha else 1.0f
    
    FloatingActionButton(
        onClick = {
            if (recordingState.isRecording) {
                onStopRecording()
            } else if (recordingState.canStartRecording) {
                onStartRecording()
            }
        },
        modifier = Modifier.size(buttonSize),
        containerColor = buttonColor.copy(alpha = buttonAlpha),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (recordingState.isRecording) 8.dp else 6.dp
        )
    ) {
        Icon(
            imageVector = if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (recordingState.isRecording) "Stop Recording" else "Start Recording",
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Real-time level meter showing peak and average levels
 */
@Composable
private fun LevelMeter(
    peakLevel: Float,
    averageLevel: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Input Level",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawLevelMeter(
                    peakLevel = peakLevel,
                    averageLevel = averageLevel,
                    size = size
                )
            }
        }
        
        // Level indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peak: ${(peakLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Avg: ${(averageLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Enhanced level meter visualization with smooth animations and gradient effects
 */
private fun DrawScope.drawLevelMeter(
    peakLevel: Float,
    averageLevel: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val meterHeight = size.height
    val meterWidth = size.width
    val cornerRadius = meterHeight / 4f
    
    // Draw average level with gradient (background bar)
    val avgWidth = (averageLevel * meterWidth).coerceIn(0f, meterWidth)
    if (avgWidth > 0) {
        val avgGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF4CAF50).copy(alpha = 0.6f), // Green start
                Color(0xFF8BC34A) // Light green end
            ),
            startX = 0f,
            endX = avgWidth
        )
        
        drawRoundRect(
            brush = avgGradient,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(avgWidth, meterHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
    
    // Draw peak level with dynamic gradient (foreground bar)
    val peakWidth = (peakLevel * meterWidth).coerceIn(0f, meterWidth)
    if (peakWidth > 0) {
        val peakColors = when {
            peakLevel > 0.9f -> listOf(Color(0xFFFF5722), Color(0xFFFF1744)) // Red gradient - clipping
            peakLevel > 0.7f -> listOf(Color(0xFFFF9800), Color(0xFFFF5722)) // Orange to red - hot
            peakLevel > 0.5f -> listOf(Color(0xFFFFC107), Color(0xFFFF9800)) // Yellow to orange - warm
            else -> listOf(Color(0xFF8BC34A), Color(0xFF4CAF50)) // Green gradient - good
        }
        
        val peakGradient = Brush.horizontalGradient(
            colors = peakColors,
            startX = 0f,
            endX = peakWidth
        )
        
        val peakBarHeight = meterHeight * 0.7f
        val peakBarTop = meterHeight * 0.15f
        
        drawRoundRect(
            brush = peakGradient,
            topLeft = Offset(0f, peakBarTop),
            size = androidx.compose.ui.geometry.Size(peakWidth, peakBarHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius * 0.8f)
        )
        
        // Add glow effect for high levels
        if (peakLevel > 0.8f) {
            val glowAlpha = ((peakLevel - 0.8f) / 0.2f).coerceIn(0f, 1f)
            drawRoundRect(
                color = peakColors.last().copy(alpha = glowAlpha * 0.3f),
                topLeft = Offset(-2.dp.toPx(), peakBarTop - 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(
                    peakWidth + 4.dp.toPx(), 
                    peakBarHeight + 4.dp.toPx()
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
    
    // Enhanced scale marks with better visibility
    val scaleMarks = listOf(0.25f, 0.5f, 0.75f, 0.9f)
    scaleMarks.forEach { mark ->
        val x = mark * meterWidth
        val markColor = when {
            mark >= 0.9f -> Color.Red.copy(alpha = 0.6f)
            mark >= 0.75f -> Color(0xFFFF9800).copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.4f)
        }
        
        drawLine(
            color = markColor,
            start = Offset(x, meterHeight * 0.1f),
            end = Offset(x, meterHeight * 0.9f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
    
    // Add subtle highlight on top edge
    drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = Offset(0f, 1.dp.toPx()),
        end = Offset(meterWidth, 1.dp.toPx()),
        strokeWidth = 1.dp.toPx()
    )
}

/**
 * Duration display with formatted time and progress indication
 */
@Composable
private fun DurationDisplay(
    recordingState: RecordingState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Duration text
        Text(
            text = recordingState.formattedDuration,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (recordingState.isNearMaxDuration) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        
        // Progress bar for max duration
        if (recordingState.isRecording) {
            val progress = (recordingState.durationMs / 1000f) / recordingState.maxDurationSeconds
            
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (recordingState.isNearMaxDuration) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Time remaining indicator
            if (recordingState.isNearMaxDuration) {
                val remainingSeconds = recordingState.maxDurationSeconds - (recordingState.durationMs / 1000)
                Text(
                    text = "${remainingSeconds}s remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Error message display with styling
 */
@Composable
private fun ErrorMessage(
    error: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop, // Using Stop as error indicator
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}