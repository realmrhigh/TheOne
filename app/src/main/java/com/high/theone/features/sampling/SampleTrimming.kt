package com.high.theone.features.sampling

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import kotlin.math.*

/**
 * Advanced sample trimming component with draggable handles, real-time preview,
 * and precise time-based input controls.
 * 
 * Requirements: 8.3 (sample trimming), 8.4 (fade options), 8.5 (processing)
 */
@Composable
fun AdvancedSampleTrimming(
    sampleMetadata: SampleMetadata?,
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    isPlaying: Boolean,
    playbackPosition: Float,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onPreviewTrimmed: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreciseInput by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(TrimPreviewMode.FULL_SAMPLE) }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Trim mode selector
        TrimModeSelector(
            previewMode = previewMode,
            onPreviewModeChange = { previewMode = it },
            onPreviewTrimmed = onPreviewTrimmed,
            trimSettings = trimSettings
        )
        
        // Interactive waveform with trim handles
        InteractiveTrimWaveform(
            waveformData = waveformData,
            trimSettings = trimSettings,
            playbackPosition = playbackPosition,
            previewMode = previewMode,
            onTrimChange = onTrimChange,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        
        // Trim handle controls
        TrimHandleControls(
            trimSettings = trimSettings,
            sampleMetadata = sampleMetadata,
            onTrimChange = onTrimChange,
            showPreciseInput = showPreciseInput,
            onTogglePreciseInput = { showPreciseInput = !showPreciseInput }
        )
        
        // Precise time input (when enabled)
        if (showPreciseInput) {
            PreciseTimeInput(
                trimSettings = trimSettings,
                sampleMetadata = sampleMetadata,
                onTrimChange = onTrimChange
            )
        }
        
        // Fade controls
        FadeControls(
            trimSettings = trimSettings,
            onTrimChange = onTrimChange
        )
        
        // Trim statistics
        TrimStatistics(
            trimSettings = trimSettings,
            sampleMetadata = sampleMetadata
        )
    }
}

/**
 * Trim preview mode selector
 */
@Composable
private fun TrimModeSelector(
    previewMode: TrimPreviewMode,
    onPreviewModeChange: (TrimPreviewMode) -> Unit,
    onPreviewTrimmed: () -> Unit,
    trimSettings: SampleTrimSettings
) {
    Card(
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
                text = "Trim Preview Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Full sample mode
                FilterChip(
                    onClick = { onPreviewModeChange(TrimPreviewMode.FULL_SAMPLE) },
                    label = { Text("Full Sample") },
                    selected = previewMode == TrimPreviewMode.FULL_SAMPLE,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ViewAgenda,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                // Trimmed only mode
                FilterChip(
                    onClick = { onPreviewModeChange(TrimPreviewMode.TRIMMED_ONLY) },
                    label = { Text("Trimmed Only") },
                    selected = previewMode == TrimPreviewMode.TRIMMED_ONLY,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Preview trimmed button
                Button(
                    onClick = onPreviewTrimmed,
                    enabled = trimSettings.isTrimmed,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Interactive waveform with draggable trim handles
 */
@Composable
private fun InteractiveTrimWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    previewMode: TrimPreviewMode,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableStateOf(0f) }
    var dragEndOffset by remember { mutableStateOf(0f) }
    
    // Animation for handle highlighting
    val startHandleAlpha by animateFloatAsState(
        targetValue = if (isDraggingStart) 1f else 0.8f,
        animationSpec = tween(150),
        label = "startHandleAlpha"
    )
    
    val endHandleAlpha by animateFloatAsState(
        targetValue = if (isDraggingEnd) 1f else 0.8f,
        animationSpec = tween(150),
        label = "endHandleAlpha"
    )
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                val startHandleX = trimSettings.startTime
                                val endHandleX = trimSettings.endTime
                                
                                // Determine which handle is being dragged
                                val startDistance = abs(normalizedX - startHandleX)
                                val endDistance = abs(normalizedX - endHandleX)
                                
                                when {
                                    startDistance < 0.03f && startDistance <= endDistance -> {
                                        isDraggingStart = true
                                        dragStartOffset = normalizedX - startHandleX
                                    }
                                    endDistance < 0.03f -> {
                                        isDraggingEnd = true
                                        dragEndOffset = normalizedX - endHandleX
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
                                dragStartOffset = 0f
                                dragEndOffset = 0f
                            },
                            onDrag = { _, dragAmount ->
                                val dragDelta = dragAmount.x / size.width
                                
                                when {
                                    isDraggingStart -> {
                                        val newStart = (trimSettings.startTime + dragDelta)
                                            .coerceIn(0f, trimSettings.endTime - 0.005f)
                                        onTrimChange(trimSettings.copy(startTime = newStart))
                                    }
                                    isDraggingEnd -> {
                                        val newEnd = (trimSettings.endTime + dragDelta)
                                            .coerceIn(trimSettings.startTime + 0.005f, 1f)
                                        onTrimChange(trimSettings.copy(endTime = newEnd))
                                    }
                                }
                            }
                        )
                    }
            ) {
                drawTrimWaveform(
                    waveformData = waveformData,
                    trimSettings = trimSettings,
                    playbackPosition = playbackPosition,
                    previewMode = previewMode,
                    canvasSize = size,
                    startHandleAlpha = startHandleAlpha,
                    endHandleAlpha = endHandleAlpha,
                    isDraggingStart = isDraggingStart,
                    isDraggingEnd = isDraggingEnd
                )
            }
            
            // Floating trim handles
            TrimHandleOverlays(
                trimSettings = trimSettings,
                isDraggingStart = isDraggingStart,
                isDraggingEnd = isDraggingEnd,
                startHandleAlpha = startHandleAlpha,
                endHandleAlpha = endHandleAlpha,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Draw waveform with trim visualization
 */
private fun DrawScope.drawTrimWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    playbackPosition: Float,
    previewMode: TrimPreviewMode,
    canvasSize: Size,
    startHandleAlpha: Float,
    endHandleAlpha: Float,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean
) {
    if (waveformData.isEmpty()) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val centerY = height / 2f
    
    // Draw background
    drawRect(
        color = Color.Black.copy(alpha = 0.05f),
        size = canvasSize
    )
    
    // Draw center line
    drawLine(
        color = Color.Gray.copy(alpha = 0.2f),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 1.dp.toPx()
    )
    
    // Calculate positions
    val trimStartX = trimSettings.startTime * width
    val trimEndX = trimSettings.endTime * width
    
    // Draw waveform based on preview mode
    when (previewMode) {
        TrimPreviewMode.FULL_SAMPLE -> {
            drawFullSampleWaveform(
                waveformData = waveformData,
                trimSettings = trimSettings,
                width = width,
                height = height,
                centerY = centerY
            )
        }
        TrimPreviewMode.TRIMMED_ONLY -> {
            drawTrimmedOnlyWaveform(
                waveformData = waveformData,
                trimSettings = trimSettings,
                width = width,
                height = height,
                centerY = centerY
            )
        }
    }
    
    // Draw trim region highlight
    if (previewMode == TrimPreviewMode.FULL_SAMPLE) {
        drawRect(
            color = Color.Blue.copy(alpha = 0.15f),
            topLeft = Offset(trimStartX, 0f),
            size = Size(trimEndX - trimStartX, height)
        )
    }
    
    // Draw fade regions
    if (trimSettings.fadeInMs > 0f) {
        val fadeInWidth = (trimSettings.fadeInMs / 1000f) * (trimEndX - trimStartX) * 0.1f // Approximate
        drawRect(
            color = Color.Green.copy(alpha = 0.2f),
            topLeft = Offset(trimStartX, 0f),
            size = Size(fadeInWidth, height)
        )
    }
    
    if (trimSettings.fadeOutMs > 0f) {
        val fadeOutWidth = (trimSettings.fadeOutMs / 1000f) * (trimEndX - trimStartX) * 0.1f // Approximate
        drawRect(
            color = Color.Orange.copy(alpha = 0.2f),
            topLeft = Offset(trimEndX - fadeOutWidth, 0f),
            size = Size(fadeOutWidth, height)
        )
    }
    
    // Draw playback position
    val playbackX = playbackPosition * width
    drawLine(
        color = Color.Red,
        start = Offset(playbackX, 0f),
        end = Offset(playbackX, height),
        strokeWidth = 2.dp.toPx()
    )
    
    // Draw trim boundaries
    drawLine(
        color = Color.Green.copy(alpha = startHandleAlpha),
        start = Offset(trimStartX, 0f),
        end = Offset(trimStartX, height),
        strokeWidth = if (isDraggingStart) 4.dp.toPx() else 3.dp.toPx()
    )
    
    drawLine(
        color = Color.Green.copy(alpha = endHandleAlpha),
        start = Offset(trimEndX, 0f),
        end = Offset(trimEndX, height),
        strokeWidth = if (isDraggingEnd) 4.dp.toPx() else 3.dp.toPx()
    )
}

/**
 * Draw full sample waveform with trim regions highlighted
 */
private fun DrawScope.drawFullSampleWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    width: Float,
    height: Float,
    centerY: Float
) {
    val waveformPath = Path()
    val samplesPerPixel = max(1, waveformData.size / width.toInt())
    
    // Draw complete waveform (dimmed)
    for (x in 0 until width.toInt()) {
        val sampleIndex = (x * samplesPerPixel).coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.7f)
        
        if (x == 0) {
            waveformPath.moveTo(x.toFloat(), y)
        } else {
            waveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    drawPath(
        path = waveformPath,
        color = Color.Gray.copy(alpha = 0.4f)
    )
    
    // Draw active region (trimmed part) with full opacity
    val activeWaveformPath = Path()
    val startPixel = (trimSettings.startTime * width).toInt()
    val endPixel = (trimSettings.endTime * width).toInt()
    
    for (x in startPixel..endPixel) {
        val normalizedX = x.toFloat() / width
        val sampleIndex = (normalizedX * waveformData.size).toInt()
            .coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.7f)
        
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

/**
 * Draw only the trimmed portion of the waveform, stretched to fill the view
 */
private fun DrawScope.drawTrimmedOnlyWaveform(
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    width: Float,
    height: Float,
    centerY: Float
) {
    val trimmedWaveformPath = Path()
    val startSample = (trimSettings.startTime * waveformData.size).toInt()
    val endSample = (trimSettings.endTime * waveformData.size).toInt()
    val trimmedSamples = endSample - startSample
    
    if (trimmedSamples <= 0) return
    
    val samplesPerPixel = max(1, trimmedSamples / width.toInt())
    
    for (x in 0 until width.toInt()) {
        val sampleOffset = (x * samplesPerPixel).coerceIn(0, trimmedSamples - 1)
        val sampleIndex = (startSample + sampleOffset).coerceIn(0, waveformData.size - 1)
        val amplitude = waveformData[sampleIndex]
        val y = centerY - (amplitude * centerY * 0.8f)
        
        if (x == 0) {
            trimmedWaveformPath.moveTo(x.toFloat(), y)
        } else {
            trimmedWaveformPath.lineTo(x.toFloat(), y)
        }
    }
    
    drawPath(
        path = trimmedWaveformPath,
        color = Color.Blue
    )
}

/**
 * Floating trim handle overlays
 */
@Composable
private fun TrimHandleOverlays(
    trimSettings: SampleTrimSettings,
    isDraggingStart: Boolean,
    isDraggingEnd: Boolean,
    startHandleAlpha: Float,
    endHandleAlpha: Float,
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
                .offset(x = startX - 12.dp, y = height - 24.dp)
                .size(24.dp)
                .background(
                    color = Color.Green.copy(alpha = startHandleAlpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Start Trim Handle",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // End handle
        Box(
            modifier = Modifier
                .offset(x = endX - 12.dp, y = height - 24.dp)
                .size(24.dp)
                .background(
                    color = Color.Green.copy(alpha = endHandleAlpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "End Trim Handle",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Handle labels when dragging
        if (isDraggingStart) {
            Card(
                modifier = Modifier.offset(x = startX - 30.dp, y = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Green
                )
            ) {
                Text(
                    text = "START",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        
        if (isDraggingEnd) {
            Card(
                modifier = Modifier.offset(x = endX - 30.dp, y = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Green
                )
            ) {
                Text(
                    text = "END",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Trim handle controls with sliders and quick actions
 */
@Composable
private fun TrimHandleControls(
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?,
    onTrimChange: (SampleTrimSettings) -> Unit,
    showPreciseInput: Boolean,
    onTogglePreciseInput: () -> Unit
) {
    Card(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trim Handles",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                
                TextButton(onClick = onTogglePreciseInput) {
                    Icon(
                        imageVector = if (showPreciseInput) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showPreciseInput) "Hide Precise" else "Precise Input")
                }
            }
            
            // Quick trim actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onTrimChange(trimSettings.copy(startTime = 0f, endTime = 1f))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset", style = MaterialTheme.typography.bodySmall)
                }
                
                OutlinedButton(
                    onClick = {
                        val center = (trimSettings.startTime + trimSettings.endTime) / 2f
                        val halfRange = (trimSettings.endTime - trimSettings.startTime) / 4f
                        onTrimChange(
                            trimSettings.copy(
                                startTime = (center - halfRange).coerceAtLeast(0f),
                                endTime = (center + halfRange).coerceAtMost(1f)
                            )
                        )
                    },
                    enabled = trimSettings.isTrimmed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tighten", style = MaterialTheme.typography.bodySmall)
                }
                
                OutlinedButton(
                    onClick = {
                        val center = (trimSettings.startTime + trimSettings.endTime) / 2f
                        val range = trimSettings.endTime - trimSettings.startTime
                        val newRange = (range * 1.5f).coerceAtMost(1f)
                        val halfNewRange = newRange / 2f
                        onTrimChange(
                            trimSettings.copy(
                                startTime = (center - halfNewRange).coerceAtLeast(0f),
                                endTime = (center + halfNewRange).coerceAtMost(1f)
                            )
                        )
                    },
                    enabled = trimSettings.isTrimmed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Expand", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Precise time-based input controls
 */
@Composable
private fun PreciseTimeInput(
    trimSettings: SampleTrimSettings,
    sampleMetadata: SampleMetadata?,
    onTrimChange: (SampleTrimSettings) -> Unit
) {
    Card(
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
                text = "Precise Time Input",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            sampleMetadata?.let { metadata ->
                var startTimeText by remember(trimSettings.startTime) {
                    mutableStateOf(((trimSettings.startTime * metadata.durationMs).toLong()).toString())
                }
                var endTimeText by remember(trimSettings.endTime) {
                    mutableStateOf(((trimSettings.endTime * metadata.durationMs).toLong()).toString())
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start time input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start Time (ms)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedTextField(
                            value = startTimeText,
                            onValueChange = { newValue ->
                                startTimeText = newValue
                                newValue.toLongOrNull()?.let { timeMs ->
                                    val normalizedTime = (timeMs.toFloat() / metadata.durationMs)
                                        .coerceIn(0f, trimSettings.endTime - 0.001f)
                                    onTrimChange(trimSettings.copy(startTime = normalizedTime))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // End time input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "End Time (ms)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedTextField(
                            value = endTimeText,
                            onValueChange = { newValue ->
                                endTimeText = newValue
                                newValue.toLongOrNull()?.let { timeMs ->
                                    val normalizedTime = (timeMs.toFloat() / metadata.durationMs)
                                        .coerceIn(trimSettings.startTime + 0.001f, 1f)
                                    onTrimChange(trimSettings.copy(endTime = normalizedTime))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                    Text(
                        text = "${trimSettings.trimmedDurationMs}ms",
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
 * Fade controls for smooth transitions
 */
@Composable
private fun FadeControls(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit
) {
    Card(
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
                text = "Fade Options",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            // Fade In
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fade In:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(70.dp)
                )
                
                Slider(
                    value = trimSettings.fadeInMs,
                    onValueChange = { newFadeIn ->
                        onTrimChange(trimSettings.copy(fadeInMs = newFadeIn))
                    },
                    valueRange = 0f..500f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${trimSettings.fadeInMs.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
            
            // Fade Out
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fade Out:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(70.dp)
                )
                
                Slider(
                    value = trimSettings.fadeOutMs,
                    onValueChange = { newFadeOut ->
                        onTrimChange(trimSettings.copy(fadeOutMs = newFadeOut))
                    },
                    valueRange = 0f..500f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${trimSettings.fadeOutMs.toInt()}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
        }
    }
}

/**
 * Trim statistics and information
 */
@Composable
private fun TrimStatistics(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Trim Statistics",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            sampleMetadata?.let { metadata ->
                val originalDuration = metadata.durationMs
                val trimmedDuration = trimSettings.trimmedDurationMs
                val removedDuration = originalDuration - trimmedDuration
                val retainedPercentage = ((trimmedDuration.toFloat() / originalDuration) * 100).toInt()
                
                StatRow("Original Duration", formatDuration(originalDuration))
                StatRow("Trimmed Duration", formatDuration(trimmedDuration))
                StatRow("Removed Duration", formatDuration(removedDuration))
                StatRow("Retained", "$retainedPercentage%")
                
                if (trimSettings.hasProcessing) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "Processing Applied",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (trimSettings.fadeInMs > 0f) {
                        StatRow("Fade In", "${trimSettings.fadeInMs.toInt()}ms")
                    }
                    if (trimSettings.fadeOutMs > 0f) {
                        StatRow("Fade Out", "${trimSettings.fadeOutMs.toInt()}ms")
                    }
                }
            }
        }
    }
}

/**
 * Helper composable for statistics rows
 */
@Composable
private fun StatRow(label: String, value: String) {
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
 * Format duration in milliseconds to a readable format
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000.0
    return when {
        seconds < 1.0 -> "${durationMs}ms"
        seconds < 60.0 -> String.format("%.1fs", seconds)
        else -> {
            val minutes = (seconds / 60).toInt()
            val remainingSeconds = seconds % 60
            String.format("%dm %.1fs", minutes, remainingSeconds)
        }
    }
}

/**
 * Trim preview modes
 */
enum class TrimPreviewMode {
    FULL_SAMPLE,    // Show full sample with trim region highlighted
    TRIMMED_ONLY    // Show only trimmed portion, stretched to fill view
}