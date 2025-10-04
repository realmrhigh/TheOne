package com.high.theone.features.sequencer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * Pad selection interface for choosing visible tracks in the sequencer.
 * Provides pad info display, mute/solo functionality, and visual indication of assignments.
 * 
 * Requirements: 8.1, 8.2, 8.5, 9.6
 */
@Composable
fun PadSelector(
    pads: List<SequencerPadInfo>,
    selectedPads: Set<Int>,
    mutedPads: Set<Int> = emptySet(),
    soloedPads: Set<Int> = emptySet(),
    onPadSelect: (Int) -> Unit,
    onPadMute: (Int) -> Unit = {},
    onPadSolo: (Int) -> Unit = {},
    onShowAll: () -> Unit = {},
    onShowAssigned: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with controls
            PadSelectorHeader(
                totalPads = pads.size,
                selectedCount = selectedPads.size,
                assignedCount = pads.count { it.hasAssignedSample },
                onShowAll = onShowAll,
                onShowAssigned = onShowAssigned,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Pad selection grid
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = pads,
                    key = { it.index }
                ) { pad ->
                    PadSelectorItem(
                        pad = pad,
                        isSelected = selectedPads.contains(pad.index),
                        isMuted = mutedPads.contains(pad.index),
                        isSoloed = soloedPads.contains(pad.index),
                        hasSoloedPads = soloedPads.isNotEmpty(),
                        onSelect = { onPadSelect(pad.index) },
                        onMute = { onPadMute(pad.index) },
                        onSolo = { onPadSolo(pad.index) }
                    )
                }
            }
        }
    }
}

/**
 * Header section with pad selection controls and statistics
 */
@Composable
private fun PadSelectorHeader(
    totalPads: Int,
    selectedCount: Int,
    assignedCount: Int,
    onShowAll: () -> Unit,
    onShowAssigned: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pad statistics
        Column {
            Text(
                text = "Pad Tracks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$selectedCount selected • $assignedCount assigned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Quick selection buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShowAll,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            OutlinedButton(
                onClick = onShowAssigned,
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Assigned",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Individual pad selector item with mute/solo controls
 */
@Composable
private fun PadSelectorItem(
    pad: SequencerPadInfo,
    isSelected: Boolean,
    isMuted: Boolean,
    isSoloed: Boolean,
    hasSoloedPads: Boolean,
    onSelect: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSoloed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            isMuted -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            pad.hasAssignedSample -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "pad_selector_background"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isSoloed -> MaterialTheme.colorScheme.primary
            isMuted -> MaterialTheme.colorScheme.error
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "pad_selector_border"
    )
    
    Card(
        modifier = modifier
            .width(80.dp)
            .height(72.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected || isSoloed) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected || isSoloed || isMuted) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Pad info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "P${pad.index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                
                Text(
                    text = pad.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (pad.hasAssignedSample) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
                
                // Assignment indicator
                if (pad.hasAssignedSample) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
            
            // Mute/Solo controls
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mute button
                IconButton(
                    onClick = onMute,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(12.dp)
                    )
                }
                
                // Solo button
                IconButton(
                    onClick = onSolo,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = if (isSoloed) "Unsolo" else "Solo",
                        tint = if (isSoloed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Pad assignment status indicator
 */
@Composable
fun PadAssignmentIndicator(
    pad: SequencerPadInfo,
    showDetails: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        pad.isLoading -> MaterialTheme.colorScheme.secondary
                        pad.hasAssignedSample -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(4.dp)
                )
        )
        
        if (showDetails) {
            Text(
                text = when {
                    pad.isLoading -> "Loading..."
                    pad.hasAssignedSample -> pad.displayName
                    else -> "Empty"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Track mute/solo functionality state
 */
data class TrackMuteSoloState(
    val mutedTracks: Set<Int> = emptySet(),
    val soloedTracks: Set<Int> = emptySet()
) {
    /**
     * Check if a track should be audible based on mute/solo state
     */
    fun isTrackAudible(trackIndex: Int): Boolean {
        return when {
            soloedTracks.isNotEmpty() -> soloedTracks.contains(trackIndex)
            else -> !mutedTracks.contains(trackIndex)
        }
    }
    
    /**
     * Toggle mute state for a track
     */
    fun toggleMute(trackIndex: Int): TrackMuteSoloState {
        return copy(
            mutedTracks = if (mutedTracks.contains(trackIndex)) {
                mutedTracks - trackIndex
            } else {
                mutedTracks + trackIndex
            }
        )
    }
    
    /**
     * Toggle solo state for a track
     */
    fun toggleSolo(trackIndex: Int): TrackMuteSoloState {
        return copy(
            soloedTracks = if (soloedTracks.contains(trackIndex)) {
                soloedTracks - trackIndex
            } else {
                soloedTracks + trackIndex
            }
        )
    }
    
    /**
     * Clear all mute states
     */
    fun clearMutes(): TrackMuteSoloState = copy(mutedTracks = emptySet())
    
    /**
     * Clear all solo states
     */
    fun clearSolos(): TrackMuteSoloState = copy(soloedTracks = emptySet())
}