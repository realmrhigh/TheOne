package com.high.theone.features.sequencer

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.*

/**
 * Pattern chain editor with drag-and-drop functionality for song arrangement
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PatternChainEditor(
    songMode: SongMode,
    availablePatterns: List<Pattern>,
    currentSequencePosition: Int,
    onSequenceUpdate: (List<SongStep>) -> Unit,
    onPatternSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedItem by remember { mutableStateOf<DraggedItem?>(null) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Song Arrangement",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Loop toggle
                IconButton(
                    onClick = { 
                        val updatedSong = songMode.copy(loopEnabled = !songMode.loopEnabled)
                        onSequenceUpdate(updatedSong.sequence)
                    }
                ) {
                    Icon(
                        imageVector = if (songMode.loopEnabled) Icons.Default.Repeat else Icons.Default.PlayArrow,
                        contentDescription = if (songMode.loopEnabled) "Loop enabled" else "Loop disabled",
                        tint = if (songMode.loopEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Clear all
                IconButton(
                    onClick = { onSequenceUpdate(emptyList()) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear arrangement"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pattern library (source for dragging)
        Text(
            text = "Available Patterns",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(availablePatterns) { pattern ->
                PatternLibraryItem(
                    pattern = pattern,
                    onDragStart = { 
                        draggedItem = DraggedItem.FromLibrary(pattern)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { draggedItem = null },
                    isDragging = draggedItem is DraggedItem.FromLibrary && 
                                (draggedItem as DraggedItem.FromLibrary).pattern.id == pattern.id
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Song sequence (drop target)
        Text(
            text = "Song Sequence",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (songMode.sequence.isEmpty()) {
            // Empty state
            EmptySequenceDropTarget(
                onDrop = { pattern ->
                    onSequenceUpdate(listOf(SongStep(pattern.id, 1)))
                },
                draggedItem = draggedItem
            )
        } else {
            // Sequence with items
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = songMode.sequence,
                    key = { index, _ -> "sequence_$index" }
                ) { index, songStep ->
                    val pattern = availablePatterns.find { it.id == songStep.patternId }
                    
                    SequenceItem(
                        songStep = songStep,
                        pattern = pattern,
                        index = index,
                        isCurrentlyPlaying = index == currentSequencePosition,
                        isDropTarget = dropTargetIndex == index,
                        onDragStart = { 
                            draggedItem = DraggedItem.FromSequence(index, songStep)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = { draggedItem = null },
                        onDrop = { droppedItem ->
                            handleSequenceDrop(
                                draggedItem = droppedItem,
                                targetIndex = index,
                                currentSequence = songMode.sequence,
                                onSequenceUpdate = onSequenceUpdate
                            )
                        },
                        onRepeatCountChange = { newCount ->
                            val updatedSequence = songMode.sequence.toMutableList()
                            updatedSequence[index] = songStep.copy(repeatCount = newCount)
                            onSequenceUpdate(updatedSequence)
                        },
                        onDelete = {
                            val updatedSequence = songMode.sequence.toMutableList()
                            updatedSequence.removeAt(index)
                            onSequenceUpdate(updatedSequence)
                        },
                        onSelect = { onPatternSelect(index) },
                        isDragging = draggedItem is DraggedItem.FromSequence && 
                                   (draggedItem as DraggedItem.FromSequence).index == index,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
                
                // Drop target at end
                item {
                    SequenceDropTarget(
                        index = songMode.sequence.size,
                        isDropTarget = dropTargetIndex == songMode.sequence.size,
                        onDrop = { droppedItem ->
                            handleSequenceDrop(
                                draggedItem = droppedItem,
                                targetIndex = songMode.sequence.size,
                                currentSequence = songMode.sequence,
                                onSequenceUpdate = onSequenceUpdate
                            )
                        },
                        draggedItem = draggedItem
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternLibraryItem(
    pattern: Pattern,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .height(80.dp)
            .pointerInput(pattern.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() }
                ) { _, _ -> }
            }
            .alpha(if (isDragging) 0.5f else 1f),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pattern.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pattern.length} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${pattern.tempo.toInt()} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SequenceItem(
    songStep: SongStep,
    pattern: Pattern?,
    index: Int,
    isCurrentlyPlaying: Boolean,
    isDropTarget: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrop: (DraggedItem) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isCurrentlyPlaying -> MaterialTheme.colorScheme.primary
        isDropTarget -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() }
                ) { _, _ -> }
            }
            .alpha(if (isDragging) 0.5f else 1f)
            .clickable { onSelect() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Pattern info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pattern?.name ?: "Unknown Pattern",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "Position ${index + 1} • ${pattern?.length ?: 0} steps • ${pattern?.tempo?.toInt() ?: 0} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Repeat count controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { 
                        if (songStep.repeatCount > 1) {
                            onRepeatCountChange(songStep.repeatCount - 1)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease repeats",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Text(
                    text = "${songStep.repeatCount}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(min = 32.dp)
                )
                
                IconButton(
                    onClick = { 
                        if (songStep.repeatCount < 99) {
                            onRepeatCountChange(songStep.repeatCount + 1)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase repeats",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from sequence",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptySequenceDropTarget(
    onDrop: (Pattern) -> Unit,
    draggedItem: DraggedItem?,
    modifier: Modifier = Modifier
) {
    val isValidDropTarget = draggedItem is DraggedItem.FromLibrary
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(
                width = 2.dp,
                color = if (isValidDropTarget) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isValidDropTarget) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Drag patterns here to create a song",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SequenceDropTarget(
    index: Int,
    isDropTarget: Boolean,
    onDrop: (DraggedItem) -> Unit,
    draggedItem: DraggedItem?,
    modifier: Modifier = Modifier
) {
    val isValidDropTarget = draggedItem != null
    
    if (isValidDropTarget) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(
                    width = 2.dp,
                    color = if (isDropTarget) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isDropTarget) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Drop here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun handleSequenceDrop(
    draggedItem: DraggedItem?,
    targetIndex: Int,
    currentSequence: List<SongStep>,
    onSequenceUpdate: (List<SongStep>) -> Unit
) {
    when (draggedItem) {
        is DraggedItem.FromLibrary -> {
            // Add pattern from library to sequence
            val newStep = SongStep(draggedItem.pattern.id, 1)
            val updatedSequence = currentSequence.toMutableList()
            updatedSequence.add(targetIndex, newStep)
            onSequenceUpdate(updatedSequence)
        }
        
        is DraggedItem.FromSequence -> {
            // Reorder existing item in sequence
            if (draggedItem.index != targetIndex) {
                val updatedSequence = currentSequence.toMutableList()
                val item = updatedSequence.removeAt(draggedItem.index)
                val adjustedTargetIndex = if (targetIndex > draggedItem.index) {
                    targetIndex - 1
                } else {
                    targetIndex
                }
                updatedSequence.add(adjustedTargetIndex, item)
                onSequenceUpdate(updatedSequence)
            }
        }
        
        null -> {
            // No drag operation
        }
    }
}

/**
 * Represents an item being dragged in the pattern chain editor
 */
private sealed class DraggedItem {
    data class FromLibrary(val pattern: Pattern) : DraggedItem()
    data class FromSequence(val index: Int, val songStep: SongStep) : DraggedItem()
}