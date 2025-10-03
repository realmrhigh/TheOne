package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Undo/Redo controls for pattern editing
 * Provides visual feedback and keyboard shortcuts
 */
@Composable
fun UndoRedoControls(
    historyState: PatternHistoryState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    showDescriptions: Boolean = true,
    compact: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    
    if (compact) {
        CompactUndoRedoControls(
            historyState = historyState,
            onUndo = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUndo()
            },
            onRedo = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRedo()
            },
            modifier = modifier
        )
    } else {
        FullUndoRedoControls(
            historyState = historyState,
            onUndo = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onUndo()
            },
            onRedo = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRedo()
            },
            onClearHistory = onClearHistory,
            showDescriptions = showDescriptions,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactUndoRedoControls(
    historyState: PatternHistoryState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo button
        IconButton(
            onClick = onUndo,
            enabled = historyState.canUndo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = historyState.undoDescription ?: "Undo",
                tint = if (historyState.canUndo) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        
        // Redo button
        IconButton(
            onClick = onRedo,
            enabled = historyState.canRedo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = historyState.redoDescription ?: "Redo",
                tint = if (historyState.canRedo) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

@Composable
private fun FullUndoRedoControls(
    historyState: PatternHistoryState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearHistory: () -> Unit,
    showDescriptions: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (historyState.canUndo || historyState.canRedo) {
                    TextButton(
                        onClick = onClearHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // Undo/Redo buttons with descriptions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Undo section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Button(
                        onClick = onUndo,
                        enabled = historyState.canUndo,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Undo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    if (showDescriptions && historyState.undoDescription != null) {
                        Text(
                            text = historyState.undoDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                
                // Redo section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Button(
                        onClick = onRedo,
                        enabled = historyState.canRedo,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Redo",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    if (showDescriptions && historyState.redoDescription != null) {
                        Text(
                            text = historyState.redoDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            
            // History status
            if (historyState.totalPatterns > 0) {
                Text(
                    text = "Tracking ${historyState.totalPatterns} pattern${if (historyState.totalPatterns != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Floating undo/redo buttons for overlay use
 */
@Composable
fun FloatingUndoRedoControls(
    historyState: PatternHistoryState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Undo FAB
        if (historyState.canUndo) {
            SmallFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUndo()
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = historyState.undoDescription ?: "Undo"
                )
            }
        }
        
        // Redo FAB
        if (historyState.canRedo) {
            SmallFloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRedo()
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = historyState.redoDescription ?: "Redo"
                )
            }
        }
    }
}

/**
 * History statistics display for debugging/monitoring
 */
@Composable
fun HistoryStatsDisplay(
    stats: HistoryStats?,
    modifier: Modifier = Modifier
) {
    if (stats == null) return
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "History Statistics",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Entries: ${stats.totalEntries}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Current: ${stats.currentIndex + 1}/${stats.totalEntries}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Memory: ${stats.memoryUsageBytes / 1024}KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}