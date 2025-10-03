package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Sample preview component with waveform visualization, playback controls,
 * and basic trim functionality.
 * 
 * Requirements: 8.1 (waveform display), 8.2 (playback controls), 8.3 (trim controls)
 */
@Composable
fun SamplePreview(
    sampleMetadata: SampleMetadata?,
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    isPlaying: Boolean,
    playbackPosition: Float, // 0.0 to 1.0
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with sample info
            SampleInfoHeader(sampleMetadata = sampleMetadata)
            
            // Waveform display with trim controls
            WaveformDisplay(
                waveformData = waveformData,
                trimSettings = trimSettings,
                playbackPosition = playbackPosition,
                onSeek = onSeek,
                onTrimChange = onTrimChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            
            // Playback controls
            PlaybackControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onStop = onStop,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Trim information
            TrimInformation(
                trimSettings = trimSettings,
                sampleMetadata = sampleMetadata
            )
        }
    }
}

/**
 * Sample information header
 */
@Composable
private fun SampleInfoHeader(
    sampleMetadata: SampleMetadata?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = sampleMetadata?.name ?: "Sample Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            sampleMetadata?.let { metadata ->
                Text(
                    text = "${metadata.durationMs}ms • ${metadata.sampleRate}Hz • ${if (metadata.channels == 1) "Mono" else "Stereo"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Waveform display with interactive trim handles and playback position
 */
@Composable
private fun WaveformDisplay(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normalizedX = offset.x / size.width
                            val startHandleX = trimSettings.startTime
                            val endHandleX = trimSettings.endTime
                            
                            // Determine which handle is being dragged or if seeking
                            when {
                                abs(normalizedX - startHandleX) < 0.05f -> {
                                    isDraggingStart = true
                                }
                                abs(normalizedX - endHandleX) < 0.05f -> {
                                    isDraggingEnd = true
                                }
                                else -> {
                                    // Seek to position
                                    onSeek(normalizedX.coerceIn(0f, 1f))
                                }
                            }
                        },
                        onDragEnd = {
                            isDraggingStart = false
                            isDraggingEnd = false
                        },
                        onDrag = { _, dragAmount ->
                            val dragDelta = dragAmount.x / size.width
                            
                            when {
                                isDraggingStart -> {
                                    val newStart = (trimSettings.startTime + dragDelta)
                                        .coerceIn(0f, trimSettings.endTime - 0.01f)
                                    onTrimChange(trimSettings.copy(startTime = newStart))
                                }
                                isDraggingEnd -> {
                                    val newEnd = (trimSettings.endTime + dragDelta)
                                        .coerceIn(trimSettings.startTime + 0.01f, 1f)
                                    onTrimChange(trimSettings.copy(endTime = newEnd))
                                }
                            }
                        }
                    )
                }
        ) {
            drawWaveform(
                waveformData = waveformData,
                trimSettings = trimSettings,
                playbackPosition = playbackPosition,
                canvasSize = size
            )
        }
        
        // Trim handle indicators
        TrimHandles(
            trimSettings = trimSettings,
            isDraggingStart = isDraggingStart,
            isDraggingEnd = isDraggingEnd,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Draw the waveform visualization
 */
private fun DrawScope.drawWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    canvasSize: Size
) {
    if (waveformData.isEmpty()) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val centerY = height / 2f
    
    // Draw background waveform (dimmed)
    val waveformPath = Path()
    val samplesPerPixel = max(1, waveformData.size / width.toInt())
    
    for (x in 0 until width.toInt()) {
        val sampleIndex = (x * samplesPerPixel).coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.8f)
        
        if (x == 0) {
            waveformPath.moveTo(x.toFloat(), y)
        } else {
            waveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    // Draw complete waveform path
    drawPath(
        path = waveformPath,
        color = Color.Gray.copy(alpha = 0.3f)
    )
    
    // Draw trimmed region highlight
    val trimStartX = trimSettings.startTime * width
    val trimEndX = trimSettings.endTime * width
    
    drawRect(
        color = Color.Blue.copy(alpha = 0.2f),
        topLeft = Offset(trimStartX, 0f),
        size = Size(trimEndX - trimStartX, height)
    )
    
    // Draw active waveform in trimmed region
    val activeWaveformPath = Path()
    val startPixel = (trimSettings.startTime * width).toInt()
    val endPixel = (trimSettings.endTime * width).toInt()
    
    for (x in startPixel..endPixel) {
        val normalizedX = x.toFloat() / width
        val sampleIndex = (normalizedX * waveformData.size).toInt()
            .coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.8f)
        
        if (x == startPixel) {
            activeWaveformPath.moveTo(x.toFloat(), y)
        } else {
            activeWaveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    drawPath(
        path = activeWaveformPath,
        color = Color.Blue
    )
    
    // Draw playback position indicator
    val playbackX = playbackPosition * width
    drawLine(
        color = Color.Red,
        start = Offset(playbackX, 0f),
        end = Offset(playbackX, height),
        strokeWidth = 2.dp.toPx()
    )
    
    // Draw trim boundaries
    drawLine(
        color = Color.Green,
        start = Offset(trimStartX, 0f),
        end = Offset(trimStartX, height),
        strokeWidth = 3.dp.toPx()
    )
    
    drawLine(
        color = Color.Green,
        start = Offset(trimEndX, 0f),
        end = Offset(trimEndX, height),
        strokeWidth = 3.dp.toPx()
    )
}

/**
 * Trim handle indicators
 */
@Composable
private fun TrimHandles(
    trimSettings: SampleTrimSettings,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val startX = (trimSettings.startTime * width.value).dp
        val endX = (trimSettings.endTime * width.value).dp
        
        // Start handle
        Box(
            modifier = Modifier
                .offset(x = startX - 8.dp, y = (-4).dp)
                .size(16.dp, 8.dp)
                .background(
                    color = if (isDraggingStart) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Green
                    },
                    shape = RoundedCornerShape(4.dp)
                )
        )
        
        // End handle
        Box(
            modifier = Modifier
                .offset(x = endX - 8.dp, y = maxHeight - 4.dp)
                .size(16.dp, 8.dp)
                .background(
                    color = if (isDraggingEnd) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Green
                    },
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

/**
 * Playback controls for sample preview
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Stop button
        OutlinedIconButton(
            onClick = onStop,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Trim information display
 */
@Composable
private fun TrimInformation(
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Trim Information",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            sampleMetadata?.let { metadata ->
                val originalDuration = metadata.durationMs
                val trimmedDuration = trimSettings.trimmedDurationMs
                val startTimeMs = (trimSettings.startTime * originalDuration.toFloat()).toLong()
                val endTimeMs = (trimSettings.endTime * originalDuration.toFloat()).toLong()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${startTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "End:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${endTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${trimmedDuration}ms (${((trimmedDuration.toFloat() / originalDuration) * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (trimSettings.hasProcessing) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Text(
                    text = "Processing Applied:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (trimSettings.fadeInMs > 0f) {
                    Text(
                        text = "• Fade In: ${trimSettings.fadeInMs.toInt()}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (trimSettings.fadeOutMs > 0f) {
                    Text(
                        text = "• Fade Out: ${trimSettings.fadeOutMs.toInt()}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (trimSettings.normalize) {
                    Text(
                        text = "• Normalize: Enabled",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (trimSettings.reverse) {
                    Text(
                        text = "• Reverse: Enabled",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (trimSettings.gain != 1.0f) {
                    Text(
                        text = "• Gain: ${String.format("%.1f", trimSettings.gain)}x",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Simplified sample preview for quick playback without full editing capabilities
 */
@Composable
fun SimpleSamplePreview(
    sampleMetadata: SampleMetadata?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sampleMetadata?.name ?: "Sample",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                sampleMetadata?.let { metadata ->
                    Text(
                        text = "${metadata.durationMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop"
                    )
                }
            }
        }
    }
}