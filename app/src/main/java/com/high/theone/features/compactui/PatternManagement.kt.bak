package com.high.theone.features.compactui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.*

/**
 * Pattern switching controls for quick pattern selection
 */
@Composable
fun PatternSwitchingControls(
    patterns: List<Pattern>,
    currentPatternId: String?,
    onPatternSelect: (String) -> Unit,
    onPatternCreate: () -> Unit,
    onPatternManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Header with title and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Patterns",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Create new pattern button
                    IconButton(
                        onClick = onPatternCreate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Pattern",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    // Manage patterns button
                    IconButton(
                        onClick = onPatternManage,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Patterns",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pattern selection row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                itemsIndexed(patterns) { index, pattern ->
                    PatternSelectionCard(
                        pattern = pattern,
                        isSelected = pattern.id == currentPatternId,
                        patternNumber = index + 1,
                        onSelect = { onPatternSelect(pattern.id) }
                    )
                }
            }
        }
    }
}

/**
 * Individual pattern selection card
 */
@Composable
private fun PatternSelectionCard(
    pattern: Pattern,
    isSelected: Boolean,
    patternNumber: Int,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .width(80.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }
            .semantics {
                contentDescription = "Pattern $patternNumber: ${pattern.name}" +
                    if (isSelected) " (Selected)" else ""
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pattern number
            Text(
                text = patternNumber.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // Pattern name
            Text(
                text = pattern.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Pattern info
            Text(
                text = "${pattern.length}/${pattern.tempo.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Collapsible track list for space optimization
 */
@Composable
fun CollapsibleTrackList(
    availableTracks: List<SequencerPadInfo>,
    selectedTracks: Set<Int>,
    muteSoloState: TrackMuteSoloState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTrackSelect: (Int) -> Unit,
    onTrackMute: (Int) -> Unit,
    onTrackSolo: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectAssigned: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with expand/collapse control
            TrackListHeader(
                selectedCount = selectedTracks.size,
                totalCount = availableTracks.count { it.hasAssignedSample },
                isExpanded = isExpanded,
                onToggleExpanded = onToggleExpanded,
                onSelectAll = onSelectAll,
                onSelectAssigned = onSelectAssigned,
                onClearSelection = onClearSelection
            )
            
            // Expandable track list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                TrackListContent(
                    availableTracks = availableTracks,
                    selectedTracks = selectedTracks,
                    muteSoloState = muteSoloState,
                    onTrackSelect = onTrackSelect,
                    onTrackMute = onTrackMute,
                    onTrackSolo = onTrackSolo
                )
            }
        }
    }
}

/**
 * Header for the collapsible track list
 */
@Composable
private fun TrackListHeader(
    selectedCount: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectAssigned: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track selection info
        Column {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$selectedCount of $totalCount selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick selection buttons (only show when expanded)
            if (isExpanded) {
                TrackSelectionButton(
                    text = "All",
                    onClick = onSelectAll
                )
                TrackSelectionButton(
                    text = "Assigned",
                    onClick = onSelectAssigned
                )
                TrackSelectionButton(
                    text = "Clear",
                    onClick = onClearSelection
                )
            }
            
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Quick track selection button
 */
@Composable
private fun TrackSelectionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}

/**
 * Content of the expandable track list
 */
@Composable
private fun TrackListContent(
    availableTracks: List<SequencerPadInfo>,
    selectedTracks: Set<Int>,
    muteSoloState: TrackMuteSoloState,
    onTrackSelect: (Int) -> Unit,
    onTrackMute: (Int) -> Unit,
    onTrackSolo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(availableTracks.filter { it.hasAssignedSample }) { track ->
            TrackListItem(
                track = track,
                isSelected = selectedTracks.contains(track.index),
                isMuted = muteSoloState.mutedTracks.contains(track.index),
                isSoloed = muteSoloState.soloedTracks.contains(track.index),
                hasSolo = muteSoloState.soloedTracks.isNotEmpty(),
                onSelect = { onTrackSelect(track.index) },
                onMute = { onTrackMute(track.index) },
                onSolo = { onTrackSolo(track.index) }
            )
        }
    }
}

/**
 * Individual track item in the list
 */
@Composable
private fun TrackListItem(
    track: SequencerPadInfo,
    isSelected: Boolean,
    isMuted: Boolean,
    isSoloed: Boolean,
    hasSolo: Boolean,
    onSelect: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDimmed = isMuted || (hasSolo && !isSoloed)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics {
                contentDescription = "Track ${track.index + 1}: ${track.sampleName}" +
                    if (isSelected) " (Selected)" else "" +
                    if (isMuted) " (Muted)" else "" +
                    if (isSoloed) " (Soloed)" else ""
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selection indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            
            // Track number and name
            Column {
                Text(
                    text = "Track ${track.index + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isDimmed) 0.5f else 1f
                    )
                )
                Text(
                    text = track.sampleName ?: "Empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isDimmed) 0.3f else 0.7f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Mute/Solo controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mute button
            MuteSoloToggleButton(
                icon = Icons.Default.VolumeOff,
                isActive = isMuted,
                activeColor = MaterialTheme.colorScheme.error,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMute()
                }
            )
            
            // Solo button
            MuteSoloToggleButton(
                icon = Icons.Default.Headphones,
                isActive = isSoloed,
                activeColor = MaterialTheme.colorScheme.tertiary,
                contentDescription = if (isSoloed) "Unsolo" else "Solo",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSolo()
                }
            )
        }
    }
}

/**
 * Mute/Solo toggle button for track list
 */
@Composable
private fun MuteSoloToggleButton(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "mute_solo_color"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Pattern management dialog for creating, editing, and organizing patterns
 */
@Composable
fun PatternManagementDialog(
    patterns: List<Pattern>,
    currentPatternId: String?,
    onDismiss: () -> Unit,
    onPatternCreate: (name: String, length: Int) -> Unit,
    onPatternDuplicate: (patternId: String) -> Unit,
    onPatternDelete: (patternId: String) -> Unit,
    onPatternRename: (patternId: String, newName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<Pattern?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Patterns",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Create new pattern button
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Pattern")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pattern list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(patterns) { pattern ->
                        PatternManagementItem(
                            pattern = pattern,
                            isSelected = pattern.id == currentPatternId,
                            onDuplicate = { onPatternDuplicate(pattern.id) },
                            onDelete = { 
                                if (patterns.size > 1) { // Don't delete the last pattern
                                    onPatternDelete(pattern.id)
                                }
                            },
                            onRename = { editingPattern = pattern },
                            canDelete = patterns.size > 1
                        )
                    }
                }
            }
        }
    }
    
    // Create pattern dialog
    if (showCreateDialog) {
        CreatePatternDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, length ->
                onPatternCreate(name, length)
                showCreateDialog = false
            }
        )
    }
    
    // Rename pattern dialog
    editingPattern?.let { pattern ->
        RenamePatternDialog(
            pattern = pattern,
            onDismiss = { editingPattern = null },
            onRename = { newName ->
                onPatternRename(pattern.id, newName)
                editingPattern = null
            }
        )
    }
}

/**
 * Individual pattern item in management dialog
 */
@Composable
private fun PatternManagementItem(
    pattern: Pattern,
    isSelected: Boolean,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pattern info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pattern.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pattern.length} steps • ${pattern.tempo.toInt()} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }.copy(alpha = 0.7f)
                )
                if (isSelected) {
                    Text(
                        text = "Current Pattern",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog for creating a new pattern
 */
@Composable
private fun CreatePatternDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, length: Int) -> Unit
) {
    var patternName by remember { mutableStateOf("") }
    var patternLength by remember { mutableStateOf(16) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Pattern")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = patternName,
                    onValueChange = { patternName = it },
                    label = { Text("Pattern Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Column {
                    Text(
                        text = "Pattern Length: $patternLength steps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = patternLength.toFloat(),
                        onValueChange = { patternLength = it.toInt() },
                        valueRange = 8f..32f,
                        steps = 2, // 8, 16, 24, 32
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (patternName.isNotBlank()) {
                        onCreate(patternName, patternLength)
                    }
                },
                enabled = patternName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for renaming a pattern
 */
@Composable
private fun RenamePatternDialog(
    pattern: Pattern,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(pattern.name) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Rename Pattern")
        },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Pattern Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank() && newName != pattern.name) {
                        onRename(newName)
                    }
                },
                enabled = newName.isNotBlank() && newName != pattern.name
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PatternSwitchingControlsPreview() {
    MaterialTheme {
        val samplePatterns = listOf(
            Pattern(id = "1", name = "Kick Pattern", length = 16, tempo = 120f),
            Pattern(id = "2", name = "Snare Fill", length = 8, tempo = 140f),
            Pattern(id = "3", name = "Hi-Hat Groove", length = 32, tempo = 110f)
        )
        
        PatternSwitchingControls(
            patterns = samplePatterns,
            currentPatternId = "1",
            onPatternSelect = { },
            onPatternCreate = { },
            onPatternManage = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollapsibleTrackListPreview() {
    MaterialTheme {
        val sampleTracks = listOf(
            SequencerPadInfo(0, "kick", "Kick", true),
            SequencerPadInfo(1, "snare", "Snare", true),
            SequencerPadInfo(2, "hihat", "Hi-Hat", true),
            SequencerPadInfo(3, "crash", "Crash", true)
        )
        
        CollapsibleTrackList(
            availableTracks = sampleTracks,
            selectedTracks = setOf(0, 1),
            muteSoloState = TrackMuteSoloState(
                mutedTracks = setOf(2),
                soloedTracks = setOf(1)
            ),
            isExpanded = true,
            onToggleExpanded = { },
            onTrackSelect = { },
            onTrackMute = { },
            onTrackSolo = { },
            onSelectAll = { },
            onSelectAssigned = { },
            onClearSelection = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}