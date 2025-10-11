package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced sample preview component specifically for recorded samples with comprehensive
 * editing capabilities including waveform visualization, trimming, metadata editing,
 * and pad assignment workflow.
 * 
 * Requirements: 4.1 (sample preview), 4.4 (metadata editing)
 */
@Composable
fun RecordedSamplePreview(
    sampleMetadata: SampleMetadata?,
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    isPlaying: Boolean,
    playbackPosition: Float, // 0.0 to 1.0
    sampleName: String,
    sampleTags: List<String>,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onNameChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onAssignToPad: (Int) -> Unit,
    onSaveAndClose: () -> Unit,
    onDiscard: () -> Unit,
    availablePads: List<Int> = (0..15).toList(),
    modifier: Modifier = Modifier
) {
    var showMetadataEditor by remember { mutableStateOf(false) }
    var showPadSelector by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with sample info and actions
            RecordedSampleHeader(
                sampleName = sampleName,
                sampleMetadata = sampleMetadata,
                onEditMetadata = { showMetadataEditor = true },
                onSaveAndClose = onSaveAndClose,
                onDiscard = onDiscard
            )
            
            // Enhanced waveform display with trimming
            EnhancedWaveformDisplay(
                waveformData = waveformData,
                trimSettings = trimSettings,
                playbackPosition = playbackPosition,
                onSeek = onSeek,
                onTrimChange = onTrimChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            
            // Playback controls with enhanced features
            EnhancedPlaybackControls(
                isPlaying = isPlaying,
                playbackPosition = playbackPosition,
                trimSettings = trimSettings,
                sampleMetadata = sampleMetadata,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Trim controls with precise adjustment
            PreciseTrimControls(
                trimSettings = trimSettings,
                sampleMetadata = sampleMetadata,
                onTrimChange = onTrimChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Sample information and metadata
            SampleMetadataDisplay(
                sampleName = sampleName,
                sampleTags = sampleTags,
                trimSettings = trimSettings,
                sampleMetadata = sampleMetadata,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Action buttons
            RecordedSampleActions(
                onAssignToPad = { showPadSelector = true },
                onEditMetadata = { showMetadataEditor = true },
                onSaveAndClose = onSaveAndClose,
                onDiscard = onDiscard,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Metadata editor dialog
    if (showMetadataEditor) {
        SampleMetadataEditor(
            currentName = sampleName,
            currentTags = sampleTags,
            onNameChange = onNameChange,
            onTagsChange = onTagsChange,
            onDismiss = { showMetadataEditor = false }
        )
    }
    
    // Pad selector dialog
    if (showPadSelector) {
        PadSelectorDialog(
            availablePads = availablePads,
            onPadSelected = { padIndex ->
                onAssignToPad(padIndex)
                showPadSelector = false
            },
            onDismiss = { showPadSelector = false }
        )
    }
}

/**
 * Header section with sample name and primary actions
 */
@Composable
private fun RecordedSampleHeader(
    sampleName: String,
    sampleMetadata: SampleMetadata?,
    onEditMetadata: () -> Unit,
    onSaveAndClose: () -> Unit,
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Recorded Sample",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = sampleName.ifEmpty { "Untitled Recording" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            sampleMetadata?.let { metadata ->
                Text(
                    text = "${metadata.durationMs}ms • ${metadata.sampleRate}Hz • ${if (metadata.channels == 1) "Mono" else "Stereo"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        IconButton(onClick = onEditMetadata) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Metadata",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Enhanced waveform display with better visual feedback and trimming
 */
@Composable
private fun EnhancedWaveformDisplay(
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
    var isDraggingPlayhead by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
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
                                val playheadX = playbackPosition
                                
                                // Determine what's being dragged with better hit detection
                                when {
                                    abs(normalizedX - startHandleX) < 0.03f -> {
                                        isDraggingStart = true
                                    }
                                    abs(normalizedX - endHandleX) < 0.03f -> {
                                        isDraggingEnd = true
                                    }
                                    abs(normalizedX - playheadX) < 0.02f -> {
                                        isDraggingPlayhead = true
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
                                isDraggingPlayhead = false
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
                                    isDraggingPlayhead -> {
                                        val newPosition = (playbackPosition + dragDelta)
                                            .coerceIn(0f, 1f)
                                        onSeek(newPosition)
                                    }
                                }
                            }
                        )
                    }
            ) {
                drawEnhancedWaveform(
                    waveformData = waveformData,
                    trimSettings = trimSettings,
                    playbackPosition = playbackPosition,
                    canvasSize = size,
                    isDraggingStart = isDraggingStart,
                    isDraggingEnd = isDraggingEnd,
                    isDraggingPlayhead = isDraggingPlayhead
                )
            }
            
            // Trim handle indicators with better visibility
            EnhancedTrimHandles(
                trimSettings = trimSettings,
                isDraggingStart = isDraggingStart,
                isDraggingEnd = isDraggingEnd,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Draw enhanced waveform with better visual feedback
 */
private fun DrawScope.drawEnhancedWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    canvasSize: Size,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean,
    isDraggingPlayhead: Boolean
) {
    if (waveformData.isEmpty()) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val centerY = height / 2f
    
    // Draw background grid
    val gridColor = Color.Gray.copy(alpha = 0.1f)
    for (i in 1..4) {
        val x = (width / 5f) * i
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1.dp.toPx()
        )
    }
    
    // Draw center line
    drawLine(
        color = Color.Gray.copy(alpha = 0.3f),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 1.dp.toPx()
    )
    
    // Draw complete waveform (background)
    val waveformPath = Path()
    val samplesPerPixel = max(1, waveformData.size / width.toInt())
    
    for (x in 0 until width.toInt()) {
        val sampleIndex = (x * samplesPerPixel).coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.9f)
        
        if (x == 0) {
            waveformPath.moveTo(x.toFloat(), y)
        } else {
            waveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    // Draw background waveform
    drawPath(
        path = waveformPath,
        color = Color.Gray.copy(alpha = 0.4f)
    )
    
    // Draw trimmed region highlight
    val trimStartX = trimSettings.startTime * width
    val trimEndX = trimSettings.endTime * width
    
    // Trimmed region background
    drawRect(
        color = Color.Blue.copy(alpha = 0.15f),
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
        val y = centerY - (amplitude * centerY * 0.9f)
        
        if (x == startPixel) {
            activeWaveformPath.moveTo(x.toFloat(), y)
        } else {
            activeWaveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    // Draw active waveform with gradient effect
    drawPath(
        path = activeWaveformPath,
        color = Color.Blue.copy(alpha = 0.8f)
    )
    
    // Draw playback position indicator
    val playbackX = playbackPosition * width
    val playbackColor = if (isDraggingPlayhead) Color.Red.copy(alpha = 0.9f) else Color.Red
    val playbackWidth = if (isDraggingPlayhead) 3.dp.toPx() else 2.dp.toPx()
    
    drawLine(
        color = playbackColor,
        start = Offset(playbackX, 0f),
        end = Offset(playbackX, height),
        strokeWidth = playbackWidth
    )
    
    // Draw trim boundaries with enhanced visibility
    val trimColor = Color.Green.copy(alpha = 0.9f)
    val startWidth = if (isDraggingStart) 4.dp.toPx() else 3.dp.toPx()
    val endWidth = if (isDraggingEnd) 4.dp.toPx() else 3.dp.toPx()
    
    drawLine(
        color = trimColor,
        start = Offset(trimStartX, 0f),
        end = Offset(trimStartX, height),
        strokeWidth = startWidth
    )
    
    drawLine(
        color = trimColor,
        start = Offset(trimEndX, 0f),
        end = Offset(trimEndX, height),
        strokeWidth = endWidth
    )
}

/**
 * Enhanced trim handles with better visual feedback
 */
@Composable
private fun EnhancedTrimHandles(
    trimSettings: SampleTrimSettings,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        val startX = (trimSettings.startTime * width.value).dp
        val endX = (trimSettings.endTime * width.value).dp
        
        // Start handle
        Box(
            modifier = Modifier
                .offset(x = startX - 12.dp, y = (-6).dp)
                .size(24.dp, 12.dp)
                .background(
                    color = if (isDraggingStart) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Green.copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Start Trim Handle",
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.Center),
                tint = Color.White
            )
        }
        
        // End handle
        Box(
            modifier = Modifier
                .offset(x = endX - 12.dp, y = height - 6.dp)
                .size(24.dp, 12.dp)
                .background(
                    color = if (isDraggingEnd) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Green.copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "End Trim Handle",
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.Center),
                tint = Color.White
            )
        }
    }
}

/**
 * Enhanced playback controls with additional features
 */
@Composable
private fun EnhancedPlaybackControls(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Playback Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Position display and scrubber
            sampleMetadata?.let { metadata ->
                val currentTimeMs = (playbackPosition * metadata.durationMs).toLong()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatTime(currentTimeMs),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.Center
                    )
                    
                    Slider(
                        value = playbackPosition,
                        onValueChange = onSeek,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = formatTime(metadata.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Play/Pause button
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
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
 * Precise trim controls with numeric input and presets
 */
@Composable
private fun PreciseTrimControls(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trim Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Reset button
                TextButton(
                    onClick = {
                        onTrimChange(
                            trimSettings.copy(
                                startTime = 0f,
                                endTime = 1f
                            )
                        )
                    },
                    enabled = trimSettings.isTrimmed
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }
            
            sampleMetadata?.let { metadata ->
                // Start time control
                TrimTimeControl(
                    label = "Start Time",
                    value = trimSettings.startTime,
                    maxValue = trimSettings.endTime - 0.01f,
                    durationMs = metadata.durationMs,
                    onValueChange = { newStart ->
                        onTrimChange(trimSettings.copy(startTime = newStart))
                    }
                )
                
                // End time control
                TrimTimeControl(
                    label = "End Time",
                    value = trimSettings.endTime,
                    minValue = trimSettings.startTime + 0.01f,
                    durationMs = metadata.durationMs,
                    onValueChange = { newEnd ->
                        onTrimChange(trimSettings.copy(endTime = newEnd))
                    }
                )
                
                // Trim information
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val trimmedDuration = trimSettings.trimmedDurationMs
                        val originalDuration = metadata.durationMs
                        val percentage = ((trimmedDuration.toFloat() / originalDuration) * 100).toInt()
                        
                        Text(
                            text = "Trimmed Duration: ${formatTime(trimmedDuration)} ($percentage% of original)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        if (trimSettings.isTrimmed) {
                            Text(
                                text = "Removed: ${formatTime(originalDuration - trimmedDuration)} from original",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual trim time control with slider and time display
 */
@Composable
private fun TrimTimeControl(
    label: String,
    value: Float,
    minValue: Float = 0f,
    maxValue: Float = 1f,
    durationMs: Long,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = formatTime((value * durationMs).toLong()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = minValue..maxValue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Sample metadata display with current information
 */
@Composable
private fun SampleMetadataDisplay(
    sampleName: String,
    sampleTags: List<String>,
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?,
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
                text = "Sample Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Name:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = sampleName.ifEmpty { "Untitled Recording" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Tags
            if (sampleTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Tags:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sampleTags.take(3).forEach { tag ->
                            AssistChip(
                                onClick = { },
                                label = { Text(tag, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                        if (sampleTags.size > 3) {
                            AssistChip(
                                onClick = { },
                                label = { Text("+${sampleTags.size - 3}", style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }
            
            // Technical info
            sampleMetadata?.let { metadata ->
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sample Rate:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${metadata.sampleRate}Hz",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Channels:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (metadata.channels == 1) "Mono" else "Stereo",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Action buttons for recorded sample management
 */
@Composable
private fun RecordedSampleActions(
    onAssignToPad: () -> Unit,
    onEditMetadata: () -> Unit,
    onSaveAndClose: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAssignToPad,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign to Pad")
            }
            
            OutlinedButton(
                onClick = onEditMetadata,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Info")
            }
        }
        
        // Secondary actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discard")
            }
            
            Button(
                onClick = onSaveAndClose,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }
        }
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