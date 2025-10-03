package com.high.theone.features.sequencer

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.*
import kotlin.math.roundToInt

/**
 * Visual timeline representation of song arrangement with scrubbing support
 */
@Composable
fun SongTimeline(
    songMode: SongMode,
    availablePatterns: List<Pattern>,
    currentPosition: SongPosition,
    playbackProgress: Float,
    isPlaying: Boolean,
    onPositionScrub: (Float) -> Unit,
    onPatternSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var timelineWidth by remember { mutableStateOf(0f) }
    
    // Animation for playback indicator
    val animatedProgress by animateFloatAsState(
        targetValue = playbackProgress,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 100, easing = LinearEasing)
        } else {
            snap()
        },
        label = "playback_progress"
    )
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Timeline header
        TimelineHeader(
            songMode = songMode,
            currentPosition = currentPosition,
            isPlaying = isPlaying
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Main timeline
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val position = offset.x / timelineWidth
                                onPositionScrub(position.coerceIn(0f, 1f))
                            },
                            onDrag = { _, dragAmount ->
                                if (isDragging) {
                                    // Handle scrubbing during drag
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val position = offset.x / timelineWidth
                            onPositionScrub(position.coerceIn(0f, 1f))
                        }
                    }
            ) {
                // Pattern blocks
                TimelinePatternBlocks(
                    songMode = songMode,
                    availablePatterns = availablePatterns,
                    currentPosition = currentPosition,
                    onPatternSelect = onPatternSelect,
                    onSizeChanged = { width -> timelineWidth = width }
                )
                
                // Playback indicator
                if (timelineWidth > 0) {
                    PlaybackIndicator(
                        progress = animatedProgress,
                        timelineWidth = timelineWidth,
                        isPlaying = isPlaying,
                        isDragging = isDragging
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Timeline controls
        TimelineControls(
            songMode = songMode,
            currentPosition = currentPosition,
            onPositionScrub = onPositionScrub
        )
    }
}

@Composable
private fun TimelineHeader(
    songMode: SongMode,
    currentPosition: SongPosition,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Song Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = buildString {
                    append("Position ${currentPosition.sequencePosition + 1}")
                    if (songMode.sequence.isNotEmpty()) {
                        append(" of ${songMode.sequence.size}")
                    }
                    if (currentPosition.patternRepeat > 0) {
                        append(" (Repeat ${currentPosition.patternRepeat + 1})")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback status indicator
            Icon(
                imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPlaying) "Playing" else "Paused",
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            
            // Loop indicator
            if (songMode.loopEnabled) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Loop enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Total patterns count
            Text(
                text = "${songMode.getTotalSteps()} total steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelinePatternBlocks(
    songMode: SongMode,
    availablePatterns: List<Pattern>,
    currentPosition: SongPosition,
    onPatternSelect: (Int) -> Unit,
    onSizeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val totalWidth = maxWidth
        val totalSteps = songMode.getTotalSteps()
        
        LaunchedEffect(totalWidth) {
            onSizeChanged(totalWidth.value)
        }
        
        if (totalSteps > 0) {
            var currentOffset = 0f
            
            songMode.sequence.forEachIndexed { sequenceIndex, songStep ->
                val pattern = availablePatterns.find { it.id == songStep.patternId }
                val patternSteps = pattern?.length ?: 16
                val totalPatternSteps = songStep.repeatCount * patternSteps
                val blockWidth = (totalPatternSteps.toFloat() / totalSteps) * totalWidth.value
                
                // Pattern block
                PatternBlock(
                    songStep = songStep,
                    pattern = pattern,
                    sequenceIndex = sequenceIndex,
                    isCurrentPattern = sequenceIndex == currentPosition.sequencePosition,
                    width = blockWidth.dp,
                    onClick = { onPatternSelect(sequenceIndex) },
                    modifier = Modifier.offset(x = currentOffset.dp)
                )
                
                currentOffset += blockWidth
            }
        }
    }
}

@Composable
private fun PatternBlock(
    songStep: SongStep,
    pattern: Pattern?,
    sequenceIndex: Int,
    isCurrentPattern: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isCurrentPattern) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val borderColor = if (isCurrentPattern) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    
    Card(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.dp)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Pattern name
            Text(
                text = pattern?.name ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrentPattern) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp
            )
            
            // Pattern info
            Column {
                if (songStep.repeatCount > 1) {
                    Text(
                        text = "${songStep.repeatCount}x",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${pattern?.length ?: 0} steps",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaybackIndicator(
    progress: Float,
    timelineWidth: Float,
    isPlaying: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when {
        isDragging -> MaterialTheme.colorScheme.secondary
        isPlaying -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val indicatorX = (progress * timelineWidth).coerceIn(0f, timelineWidth)
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Playback line
        drawLine(
            color = indicatorColor,
            start = Offset(indicatorX, 0f),
            end = Offset(indicatorX, size.height),
            strokeWidth = 3.dp.toPx()
        )
        
        // Playback indicator head
        drawCircle(
            color = indicatorColor,
            radius = 6.dp.toPx(),
            center = Offset(indicatorX, 8.dp.toPx())
        )
    }
}

@Composable
private fun TimelineControls(
    songMode: SongMode,
    currentPosition: SongPosition,
    onPositionScrub: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick navigation buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onPositionScrub(0f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Go to beginning",
                    modifier = Modifier.size(16.dp)
                )
            }
            
            IconButton(
                onClick = { 
                    val prevPosition = (currentPosition.sequencePosition - 1).coerceAtLeast(0)
                    val progress = prevPosition.toFloat() / songMode.sequence.size.coerceAtLeast(1)
                    onPositionScrub(progress)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateBefore,
                    contentDescription = "Previous pattern",
                    modifier = Modifier.size(16.dp)
                )
            }
            
            IconButton(
                onClick = { 
                    val nextPosition = (currentPosition.sequencePosition + 1).coerceAtMost(songMode.sequence.size - 1)
                    val progress = nextPosition.toFloat() / songMode.sequence.size.coerceAtLeast(1)
                    onPositionScrub(progress)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateNext,
                    contentDescription = "Next pattern",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        // Position info
        Text(
            text = buildString {
                if (songMode.sequence.isNotEmpty()) {
                    val currentStep = songMode.sequence.getOrNull(currentPosition.sequencePosition)
                    if (currentStep != null) {
                        append("${currentPosition.patternRepeat + 1}/${currentStep.repeatCount}")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}