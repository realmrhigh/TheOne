package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlin.math.*

/**
 * Comprehensive sample editor interface with waveform display, zoom/scroll functionality,
 * playback controls, and editing tools.
 * 
 * Requirements: 8.1 (waveform display), 8.2 (zoom/scroll), 8.3 (trim controls), 8.4 (fade options)
 */
@Composable
fun SampleEditor(
    sampleMetadata: SampleMetadata?,
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    isPlaying: Boolean,
    playbackPosition: Float, // 0.0 to 1.0 within the full sample
    zoomLevel: Float = 1.0f, // 1.0 = full sample visible, >1.0 = zoomed in
    scrollPosition: Float = 0.0f, // 0.0 to 1.0 horizontal scroll position
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onZoomChange: (Float) -> Unit,
    onScrollChange: (Float) -> Unit,
    onApplyEdits: () -> Unit,
    onCancelEdits: () -> Unit,
    onResetTrim: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedControls by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Editor toolbar
        SampleEditorToolbar(
            sampleMetadata = sampleMetadata,
            trimSettings = trimSettings,
            showAdvancedControls = showAdvancedControls,
            onToggleAdvancedControls = { showAdvancedControls = !showAdvancedControls },
            onResetTrim = onResetTrim,
            onApplyEdits = onApplyEdits,
            onCancelEdits = onCancelEdits,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        // Main waveform editor
        WaveformEditor(
            waveformData = waveformData,
            trimSettings = trimSettings,
            playbackPosition = playbackPosition,
            zoomLevel = zoomLevel,
            scrollPosition = scrollPosition,
            onSeek = onSeek,
            onTrimChange = onTrimChange,
            onZoomChange = onZoomChange,
            onScrollChange = onScrollChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp)
        )
        
        // Zoom and scroll controls
        ZoomScrollControls(
            zoomLevel = zoomLevel,
            scrollPosition = scrollPosition,
            onZoomChange = onZoomChange,
            onScrollChange = onScrollChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        // Playback controls
        SampleEditorPlaybackControls(
            isPlaying = isPlaying,
            playbackPosition = playbackPosition,
            trimSettings = trimSettings,
            sampleMetadata = sampleMetadata,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        // Trim controls
        TrimControls(
            trimSettings = trimSettings,
            sampleMetadata = sampleMetadata,
            onTrimChange = onTrimChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        // Advanced controls (fade, processing)
        if (showAdvancedControls) {
            AdvancedProcessingControls(
                trimSettings = trimSettings,
                onTrimChange = onTrimChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        
        // Sample information
        SampleInformation(
            sampleMetadata = sampleMetadata,
            trimSettings = trimSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * Editor toolbar with common actions and sample info
 */
@Composable
private fun SampleEditorToolbar(
    sampleMetadata: SampleMetadata?,
    trimSettings: SampleTrimSettings,
    showAdvancedControls: Boolean,
    onToggleAdvancedControls: () -> Unit,
    onResetTrim: () -> Unit,
    onApplyEdits: () -> Unit,
    onCancelEdits: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with sample name and info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sampleMetadata?.name ?: "Sample Editor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    sampleMetadata?.let { metadata ->
                        Text(
                            text = "${metadata.formattedDuration} • ${metadata.sampleRate}Hz • ${if (metadata.channels == 1) "Mono" else "Stereo"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Advanced controls toggle
                IconButton(onClick = onToggleAdvancedControls) {
                    Icon(
                        imageVector = if (showAdvancedControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvancedControls) "Hide Advanced" else "Show Advanced"
                    )
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reset button
                OutlinedButton(
                    onClick = onResetTrim,
                    enabled = trimSettings.hasProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Cancel button
                OutlinedButton(onClick = onCancelEdits) {
                    Text("Cancel")
                }
                
                // Apply button
                Button(
                    onClick = onApplyEdits,
                    enabled = trimSettings.hasProcessing
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

/**
 * Main waveform editor with interactive trim handles and zoom/scroll support
 */
@Composable
private fun WaveformEditor(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    zoomLevel: Float,
    scrollPosition: Float,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onZoomChange: (Float) -> Unit,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var isDraggingPlayhead by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                val actualPosition = (normalizedX / zoomLevel) + scrollPosition
                                
                                val startHandleX = trimSettings.startTime
                                val endHandleX = trimSettings.endTime
                                val playheadX = playbackPosition
                                
                                // Determine what's being dragged
                                when {
                                    abs(actualPosition - startHandleX) < 0.02f -> {
                                        isDraggingStart = true
                                    }
                                    abs(actualPosition - endHandleX) < 0.02f -> {
                                        isDraggingEnd = true
                                    }
                                    abs(actualPosition - playheadX) < 0.02f -> {
                                        isDraggingPlayhead = true
                                    }
                                    else -> {
                                        // Seek to position
                                        onSeek(actualPosition.coerceIn(0f, 1f))
                                    }
                                }
                            },
                            onDragEnd = {
                                isDraggingStart = false
                                isDraggingEnd = false
                                isDraggingPlayhead = false
                            },
                            onDrag = { _, dragAmount ->
                                val dragDelta = (dragAmount.x / size.width) / zoomLevel
                                
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
                                    isDraggingPlayhead -> {
                                        val newPosition = (playbackPosition + dragDelta)
                                            .coerceIn(0f, 1f)
                                        onSeek(newPosition)
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (zoomLevel * zoom).coerceIn(1f, 10f)
                            onZoomChange(newZoom)
                        }
                    }
            ) {
                drawWaveformWithZoom(
                    waveformData = waveformData,
                    trimSettings = trimSettings,
                    playbackPosition = playbackPosition,
                    zoomLevel = zoomLevel,
                    scrollPosition = scrollPosition,
                    canvasSize = size,
                    isDraggingStart = isDraggingStart,
                    isDraggingEnd = isDraggingEnd,
                    isDraggingPlayhead = isDraggingPlayhead
                )
            }
        }
    }
}

/**
 * Draw waveform with zoom and scroll support
 */
private fun DrawScope.drawWaveformWithZoom(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    zoomLevel: Float,
    scrollPosition: Float,
    canvasSize: Size,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean,
    isDraggingPlayhead: Boolean
) {
    if (waveformData.isEmpty()) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val centerY = height / 2f
    
    // Calculate visible range based on zoom and scroll
    val visibleStart = scrollPosition
    val visibleEnd = min(1f, scrollPosition + (1f / zoomLevel))
    val visibleRange = visibleEnd - visibleStart
    
    // Draw background
    drawRect(
        color = Color.Black.copy(alpha = 0.1f),
        size = canvasSize
    )
    
    // Draw waveform
    val waveformPath = Path()
    val samplesPerPixel = max(1, (waveformData.size * visibleRange / width).toInt())
    val startSample = (visibleStart * waveformData.size).toInt()
    
    for (x in 0 until width.toInt()) {
        val sampleProgress = x.toFloat() / width
        val globalProgress = visibleStart + (sampleProgress * visibleRange)
        val sampleIndex = (globalProgress * waveformData.size).toInt()
            .coerceIn(0, waveformData.size - 1)
        
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.8f)
        
        if (x == 0) {
            waveformPath.moveTo(x.toFloat(), y)
        } else {
            waveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    // Draw complete waveform (dimmed)
    drawPath(
        path = waveformPath,
        color = Color.Gray.copy(alpha = 0.4f)
    )
    
    // Draw trimmed region highlight
    val trimStartScreen = ((trimSettings.startTime - visibleStart) / visibleRange) * width
    val trimEndScreen = ((trimSettings.endTime - visibleStart) / visibleRange) * width
    
    if (trimStartScreen < width && trimEndScreen > 0) {
        val clampedStart = trimStartScreen.coerceIn(0f, width)
        val clampedEnd = trimEndScreen.coerceIn(0f, width)
        
        if (clampedEnd > clampedStart) {
            drawRect(
                color = Color.Blue.copy(alpha = 0.2f),
                topLeft = Offset(clampedStart, 0f),
                size = Size(clampedEnd - clampedStart, height)
            )
            
            // Draw active waveform in trimmed region
            val activeWaveformPath = Path()
            val startPixel = clampedStart.toInt()
            val endPixel = clampedEnd.toInt()
            
            for (x in startPixel..endPixel) {
                val sampleProgress = x.toFloat() / width
                val globalProgress = visibleStart + (sampleProgress * visibleRange)
                val sampleIndex = (globalProgress * waveformData.size).toInt()
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
        }
    }
    
    // Draw playback position indicator
    val playbackScreen = ((playbackPosition - visibleStart) / visibleRange) * width
    if (playbackScreen >= 0 && playbackScreen <= width) {
        drawLine(
            color = if (isDraggingPlayhead) Color.Red.copy(alpha = 0.8f) else Color.Red,
            start = Offset(playbackScreen, 0f),
            end = Offset(playbackScreen, height),
            strokeWidth = if (isDraggingPlayhead) 4.dp.toPx() else 2.dp.toPx()
        )
    }
    
    // Draw trim boundaries
    if (trimStartScreen >= 0 && trimStartScreen <= width) {
        drawLine(
            color = if (isDraggingStart) Color.Green.copy(alpha = 0.8f) else Color.Green,
            start = Offset(trimStartScreen, 0f),
            end = Offset(trimStartScreen, height),
            strokeWidth = if (isDraggingStart) 4.dp.toPx() else 3.dp.toPx()
        )
    }
    
    if (trimEndScreen >= 0 && trimEndScreen <= width) {
        drawLine(
            color = if (isDraggingEnd) Color.Green.copy(alpha = 0.8f) else Color.Green,
            start = Offset(trimEndScreen, 0f),
            end = Offset(trimEndScreen, height),
            strokeWidth = if (isDraggingEnd) 4.dp.toPx() else 3.dp.toPx()
        )
    }
    
    // Draw center line
    drawLine(
        color = Color.Gray.copy(alpha = 0.3f),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 1.dp.toPx()
    )
}
/**

 * Zoom and scroll controls for waveform navigation
 */
@Composable
private fun ZoomScrollControls(
    zoomLevel: Float,
    scrollPosition: Float,
    onZoomChange: (Float) -> Unit,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Navigation",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            // Zoom control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Zoom:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(60.dp)
                )
                
                IconButton(
                    onClick = { onZoomChange((zoomLevel / 1.5f).coerceAtLeast(1f)) },
                    enabled = zoomLevel > 1f
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out"
                    )
                }
                
                Slider(
                    value = zoomLevel,
                    onValueChange = onZoomChange,
                    valueRange = 1f..10f,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = { onZoomChange((zoomLevel * 1.5f).coerceAtMost(10f)) },
                    enabled = zoomLevel < 10f
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In"
                    )
                }
                
                Text(
                    text = "${String.format("%.1f", zoomLevel)}x",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(40.dp)
                )
            }
            
            // Scroll control (only visible when zoomed in)
            if (zoomLevel > 1f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Scroll:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(60.dp)
                    )
                    
                    Slider(
                        value = scrollPosition,
                        onValueChange = onScrollChange,
                        valueRange = 0f..(1f - (1f / zoomLevel)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Enhanced playback controls with scrubbing and position display
 */
@Composable
private fun SampleEditorPlaybackControls(
    isPlaying: Boolean,
    playbackPosition: Float,
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Playback",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            // Position scrubber
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sampleMetadata?.let { metadata ->
                    val currentTimeMs = (playbackPosition * metadata.durationMs).toLong()
                    Text(
                        text = formatTime(currentTimeMs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(50.dp)
                    )
                }
                
                Slider(
                    value = playbackPosition,
                    onValueChange = onSeek,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                
                sampleMetadata?.let { metadata ->
                    Text(
                        text = formatTime(metadata.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(50.dp)
                    )
                }
            }
            
            // Playback buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause button
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Stop button
                OutlinedIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Skip to trim start
                IconButton(
                    onClick = { onSeek(trimSettings.startTime) },
                    enabled = trimSettings.startTime > 0f
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Skip to Trim Start"
                    )
                }
                
                // Skip to trim end
                IconButton(
                    onClick = { onSeek(trimSettings.endTime) },
                    enabled = trimSettings.endTime < 1f
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip to Trim End"
                    )
                }
            }
        }
    }
}

/**
 * Precise trim controls with numeric input
 */
@Composable
private fun TrimControls(
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?,
    onTrimChange: (SampleTrimSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trim Settings",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            sampleMetadata?.let { metadata ->
                // Start time control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Start:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(60.dp)
                    )
                    
                    Slider(
                        value = trimSettings.startTime,
                        onValueChange = { newStart ->
                            val clampedStart = newStart.coerceIn(0f, trimSettings.endTime - 0.01f)
                            onTrimChange(trimSettings.copy(startTime = clampedStart))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = formatTime((trimSettings.startTime * metadata.durationMs).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(60.dp)
                    )
                }
                
                // End time control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "End:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(60.dp)
                    )
                    
                    Slider(
                        value = trimSettings.endTime,
                        onValueChange = { newEnd ->
                            val clampedEnd = newEnd.coerceIn(trimSettings.startTime + 0.01f, 1f)
                            onTrimChange(trimSettings.copy(endTime = clampedEnd))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = formatTime((trimSettings.endTime * metadata.durationMs).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(60.dp)
                    )
                }
                
                // Duration display
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Trimmed Duration:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    val trimmedDuration = trimSettings.trimmedDurationMs
                    val percentage = ((trimmedDuration.toFloat() / metadata.durationMs) * 100).toInt()
                    
                    Text(
                        text = "${formatTime(trimmedDuration)} ($percentage%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Advanced processing controls (fade, normalize, reverse, gain)
 */
@Composable
private fun AdvancedProcessingControls(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Processing Options",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            // Fade In control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fade In:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp)
                )
                
                Slider(
                    value = trimSettings.fadeInMs,
                    onValueChange = { newFadeIn ->
                        onTrimChange(trimSettings.copy(fadeInMs = newFadeIn))
                    },
                    valueRange = 0f..1000f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${trimSettings.fadeInMs.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
            
            // Fade Out control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fade Out:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp)
                )
                
                Slider(
                    value = trimSettings.fadeOutMs,
                    onValueChange = { newFadeOut ->
                        onTrimChange(trimSettings.copy(fadeOutMs = newFadeOut))
                    },
                    valueRange = 0f..1000f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${trimSettings.fadeOutMs.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
            
            // Gain control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Gain:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(80.dp)
                )
                
                Slider(
                    value = trimSettings.gain,
                    onValueChange = { newGain ->
                        onTrimChange(trimSettings.copy(gain = newGain))
                    },
                    valueRange = 0.1f..3.0f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${String.format("%.1f", trimSettings.gain)}x",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            // Toggle options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Normalize toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = trimSettings.normalize,
                        onCheckedChange = { checked ->
                            onTrimChange(trimSettings.copy(normalize = checked))
                        }
                    )
                    Text(
                        text = "Normalize",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Reverse toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = trimSettings.reverse,
                        onCheckedChange = { checked ->
                            onTrimChange(trimSettings.copy(reverse = checked))
                        }
                    )
                    Text(
                        text = "Reverse",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Sample information display with processing summary
 */
@Composable
private fun SampleInformation(
    sampleMetadata: SampleMetadata?,
    trimSettings: SampleTrimSettings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
            Text(
                text = "Sample Information",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            sampleMetadata?.let { metadata ->
                // Basic info
                InfoRow("File Size", metadata.formattedFileSize)
                InfoRow("Format", metadata.format.uppercase())
                InfoRow("Bit Depth", "${metadata.bitDepth}-bit")
                
                if (metadata.tags.isNotEmpty()) {
                    InfoRow("Tags", metadata.tags.joinToString(", "))
                }
                
                // Processing summary
                if (trimSettings.hasProcessing) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "Processing Applied",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (trimSettings.isTrimmed) {
                        InfoRow("Trimmed", "Yes")
                    }
                    
                    if (trimSettings.fadeInMs > 0f) {
                        InfoRow("Fade In", "${trimSettings.fadeInMs.toInt()}ms")
                    }
                    
                    if (trimSettings.fadeOutMs > 0f) {
                        InfoRow("Fade Out", "${trimSettings.fadeOutMs.toInt()}ms")
                    }
                    
                    if (trimSettings.gain != 1.0f) {
                        InfoRow("Gain", "${String.format("%.1f", trimSettings.gain)}x")
                    }
                    
                    if (trimSettings.normalize) {
                        InfoRow("Normalize", "Enabled")
                    }
                    
                    if (trimSettings.reverse) {
                        InfoRow("Reverse", "Enabled")
                    }
                }
            }
        }
    }
}

/**
 * Helper composable for information rows
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Format time in milliseconds to MM:SS format
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}