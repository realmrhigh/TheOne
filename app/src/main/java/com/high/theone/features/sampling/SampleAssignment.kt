package com.high.theone.features.sampling

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.PadState
import com.high.theone.model.SampleMetadata

/**
 * Sample assignment workflow with drag-and-drop and alternative assignment methods.
 * Provides intuitive sample-to-pad assignment with visual feedback and confirmation dialogs.
 * 
 * Requirements: 2.1, 2.2 (sample assignment workflow)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SampleAssignmentWorkflow(
    pads: List<PadState>,
    samples: List<SampleMetadata>,
    selectedPadIndex: Int?,
    onPadSelect: (Int) -> Unit,
    onSampleAssign: (padIndex: Int, sampleId: String) -> Unit,
    onSampleRemove: (padIndex: Int) -> Unit,
    onBulkAssign: (List<Pair<Int, String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAssignmentDialog by remember { mutableStateOf(false) }
    var showBulkAssignDialog by remember { mutableStateOf(false) }
    var draggedSample by remember { mutableStateOf<SampleMetadata?>(null) }
    var dropTargetPad by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Assignment controls header
        AssignmentControlsHeader(
            onShowBulkAssign = { showBulkAssignDialog = true },
            onClearAllAssignments = {
                pads.forEachIndexed { index, pad ->
                    if (pad.hasAssignedSample) {
                        onSampleRemove(index)
                    }
                }
            },
            hasAssignedPads = pads.any { it.hasAssignedSample }
        )
        
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sample library (drag source)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Sample Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(samples, key = { it.id }) { sample ->
                            DraggableSampleItem(
                                sample = sample,
                                onDragStart = { draggedSample = sample },
                                onDragEnd = { draggedSample = null },
                                onTapAssign = {
                                    selectedPadIndex?.let { padIndex ->
                                        onSampleAssign(padIndex, sample.id.toString())
                                    } ?: run {
                                        showAssignmentDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Pad grid (drop target)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Drum Pads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 4x4 pad grid with drop targets
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until 4) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val padIndex = row * 4 + col
                                    val pad = pads.getOrNull(padIndex)
                                    
                                    if (pad != null) {
                                        DropTargetPad(
                                            pad = pad,
                                            isSelected = selectedPadIndex == padIndex,
                                            isDropTarget = dropTargetPad == padIndex,
                                            draggedSample = draggedSample,
                                            onPadSelect = { onPadSelect(padIndex) },
                                            onSampleDrop = { sample ->
                                                onSampleAssign(padIndex, sample.id.toString())
                                                dropTargetPad = null
                                            },
                                            onDropTargetChange = { isTarget ->
                                                dropTargetPad = if (isTarget) padIndex else null
                                            },
                                            onRemoveSample = { onSampleRemove(padIndex) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Assignment dialog for tap-to-assign
    if (showAssignmentDialog) {
        SampleAssignmentDialog(
            samples = samples,
            onSampleSelected = { sample ->
                selectedPadIndex?.let { padIndex ->
                    onSampleAssign(padIndex, sample.id.toString())
                }
                showAssignmentDialog = false
            },
            onDismiss = { showAssignmentDialog = false }
        )
    }
    
    // Bulk assignment dialog
    if (showBulkAssignDialog) {
        BulkAssignmentDialog(
            pads = pads,
            samples = samples,
            onBulkAssign = { assignments ->
                onBulkAssign(assignments)
                showBulkAssignDialog = false
            },
            onDismiss = { showBulkAssignDialog = false }
        )
    }
}

/**
 * Header with assignment control buttons.
 */
@Composable
private fun AssignmentControlsHeader(
    onShowBulkAssign: () -> Unit,
    onClearAllAssignments: () -> Unit,
    hasAssignedPads: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Sample Assignment",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShowBulkAssign) {
                Icon(Icons.Default.SelectAll, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bulk Assign")
            }
            
            if (hasAssignedPads) {
                OutlinedButton(
                    onClick = onClearAllAssignments,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }
    }
}

/**
 * Draggable sample item that can be dragged to pads.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableSampleItem(
    sample: SampleMetadata,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTapAssign: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .dragAndDropSource {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart()
                        startTransfer(
                            DragAndDropTransferData(
                                clipData = android.content.ClipData.newPlainText(
                                    "sample",
                                    sample.id.toString()
                                )
                            )
                        )
                    },
                    onTap = { onTapAssign() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                Icons.Default.DragIndicator,
                contentDescription = "Drag to assign",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sample.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${sample.durationMs / 1000.0}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.TouchApp,
                contentDescription = "Tap to assign",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Pad that can receive dropped samples.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DropTargetPad(
    pad: PadState,
    isSelected: Boolean,
    isDropTarget: Boolean,
    draggedSample: SampleMetadata?,
    onPadSelect: () -> Unit,
    onSampleDrop: (SampleMetadata) -> Unit,
    onDropTargetChange: (Boolean) -> Unit,
    onRemoveSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { draggedSample != null },
                target = object : DragAndDropTarget {
                    override fun onEntered(event: DragAndDropEvent) {
                        onDropTargetChange(true)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    
                    override fun onExited(event: DragAndDropEvent) {
                        onDropTargetChange(false)
                    }
                    
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        draggedSample?.let { sample ->
                            onSampleDrop(sample)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return true
                        }
                        return false
                    }
                }
            )
            .clickable { onPadSelect() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDropTarget -> MaterialTheme.colorScheme.primaryContainer
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                pad.hasAssignedSample -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isDropTarget -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (pad.hasAssignedSample) {
                    // Show assigned sample info
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = pad.sampleName ?: "Sample",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Remove button (only show when selected)
                    if (isSelected) {
                        IconButton(
                            onClick = onRemoveSample,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove sample",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    // Show empty pad
                    Icon(
                        if (isDropTarget) Icons.Default.Add else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDropTarget) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    Text(
                        text = "Pad ${pad.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isDropTarget) {
                        Text(
                            text = "Drop here",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for tap-to-assign sample selection.
 */
@Composable
private fun SampleAssignmentDialog(
    samples: List<SampleMetadata>,
    onSampleSelected: (SampleMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Sample to Assign",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(samples) { sample ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSampleSelected(sample) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sample.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    Text(
                                        text = "${sample.durationMs / 1000.0}s • ${sample.sampleRate}Hz",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for bulk sample assignment operations.
 */
@Composable
private fun BulkAssignmentDialog(
    pads: List<PadState>,
    samples: List<SampleMetadata>,
    onBulkAssign: (List<Pair<Int, String>>) -> Unit,
    onDismiss: () -> Unit
) {
    var assignments by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Bulk Sample Assignment",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pads.chunked(4)) { padRow ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            padRow.forEach { pad ->
                                BulkAssignmentPadItem(
                                    pad = pad,
                                    samples = samples,
                                    selectedSampleId = assignments[pad.index],
                                    onSampleSelected = { sampleId ->
                                        assignments = if (sampleId != null) {
                                            assignments + (pad.index to sampleId)
                                        } else {
                                            assignments - pad.index
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            onBulkAssign(assignments.toList())
                        },
                        modifier = Modifier.weight(1f),
                        enabled = assignments.isNotEmpty()
                    ) {
                        Text("Assign (${assignments.size})")
                    }
                }
            }
        }
    }
}

/**
 * Individual pad item for bulk assignment.
 */
@Composable
private fun BulkAssignmentPadItem(
    pad: PadState,
    samples: List<SampleMetadata>,
    selectedSampleId: String?,
    onSampleSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSamplePicker by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { showSamplePicker = true },
        colors = CardDefaults.cardColors(
            containerColor = if (selectedSampleId != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "P${pad.index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (selectedSampleId != null) {
                    val sample = samples.find { it.id.toString() == selectedSampleId }
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sample?.name?.take(8) ?: "Sample",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    
    if (showSamplePicker) {
        SamplePickerDialog(
            samples = samples,
            currentSelection = selectedSampleId,
            onSampleSelected = { sampleId ->
                onSampleSelected(sampleId)
                showSamplePicker = false
            },
            onDismiss = { showSamplePicker = false }
        )
    }
}

/**
 * Simple sample picker dialog for bulk assignment.
 */
@Composable
private fun SamplePickerDialog(
    samples: List<SampleMetadata>,
    currentSelection: String?,
    onSampleSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Sample",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        // Clear selection option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSampleSelected(null) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentSelection == null) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("No Sample")
                            }
                        }
                    }
                    
                    items(samples) { sample ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSampleSelected(sample.id.toString()) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentSelection == sample.id.toString()) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sample.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}