package com.high.theone.features.sampling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.PadState
import com.high.theone.model.SampleMetadata

/**
 * Sample management features including deletion, duplication, renaming, and usage tracking.
 * Provides comprehensive sample library management with safety confirmations.
 * 
 * Requirements: 7.3 (sample management and organization)
 */
@Composable
fun SampleManagementDialog(
    sample: SampleMetadata,
    usageInfo: SampleUsageInfo,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onAddTags: (List<String>) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                SampleManagementHeader(
                    sample = sample,
                    onDismiss = onDismiss
                )
                
                Divider()
                
                // Sample info section
                SampleInfoSection(
                    sample = sample,
                    usageInfo = usageInfo
                )
                
                // Management actions
                SampleActionsSection(
                    onRename = { showRenameDialog = true },
                    onDuplicate = onDuplicate,
                    onDelete = { showDeleteConfirmation = true },
                    onEditTags = { showTagEditor = true },
                    canDelete = usageInfo.assignedPads.isEmpty()
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
    
    // Rename dialog
    if (showRenameDialog) {
        SampleRenameDialog(
            currentName = sample.name,
            onRename = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
    
    // Delete confirmation
    if (showDeleteConfirmation) {
        SampleDeleteConfirmationDialog(
            sample = sample,
            usageInfo = usageInfo,
            onConfirm = {
                onDelete()
                showDeleteConfirmation = false
                onDismiss()
            },
            onCancel = { showDeleteConfirmation = false }
        )
    }
    
    // Tag editor
    if (showTagEditor) {
        SampleTagEditorDialog(
            sample = sample,
            onAddTags = { tags ->
                onAddTags(tags)
                showTagEditor = false
            },
            onRemoveTag = onRemoveTag,
            onDismiss = { showTagEditor = false }
        )
    }
}

/**
 * Header section with sample name and close button.
 */
@Composable
private fun SampleManagementHeader(
    sample: SampleMetadata,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Manage Sample",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sample.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

/**
 * Sample information and usage statistics section.
 */
@Composable
private fun SampleInfoSection(
    sample: SampleMetadata,
    usageInfo: SampleUsageInfo
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Sample Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Basic info card
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
                SampleInfoRow(
                    label = "Duration",
                    value = "${sample.durationMs / 1000.0}s",
                    icon = Icons.Default.Schedule
                )
                
                SampleInfoRow(
                    label = "Format",
                    value = "${sample.sampleRate}Hz • ${if (sample.channels == 1) "Mono" else "Stereo"}",
                    icon = Icons.Default.GraphicEq
                )
                
                SampleInfoRow(
                    label = "File Size",
                    value = formatFileSize(sample.fileSizeBytes ?: 0L),
                    icon = Icons.Default.Storage
                )
                
                SampleInfoRow(
                    label = "Created",
                    value = formatDate(sample.createdAt),
                    icon = Icons.Default.DateRange
                )
            }
        }
        
        // Usage info card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (usageInfo.assignedPads.isNotEmpty()) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        tint = if (usageInfo.assignedPads.isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    Text(
                        text = "Usage Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (usageInfo.assignedPads.isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                if (usageInfo.assignedPads.isNotEmpty()) {
                    Text(
                        text = "Assigned to ${usageInfo.assignedPads.size} pad${if (usageInfo.assignedPads.size == 1) "" else "s"}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Text(
                        text = usageInfo.assignedPads.joinToString(", ") { "Pad ${it + 1}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = "Not currently assigned to any pads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "Total triggers: ${usageInfo.totalTriggers}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (usageInfo.assignedPads.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        
        // Tags section
        if (sample.tags.isNotEmpty()) {
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
                        text = "Tags",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sample.tags.forEach { tag ->
                            AssistChip(
                                onClick = { },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual sample info row.
 */
@Composable
private fun SampleInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Sample management actions section.
 */
@Composable
private fun SampleActionsSection(
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    canDelete: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Action buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Rename
            SampleActionButton(
                icon = Icons.Default.Edit,
                text = "Rename Sample",
                description = "Change the sample name",
                onClick = onRename
            )
            
            // Edit tags
            SampleActionButton(
                icon = Icons.Default.Tag,
                text = "Edit Tags",
                description = "Add or remove tags for organization",
                onClick = onEditTags
            )
            
            // Duplicate
            SampleActionButton(
                icon = Icons.Default.ContentCopy,
                text = "Duplicate Sample",
                description = "Create a copy of this sample",
                onClick = onDuplicate
            )
            
            // Delete
            SampleActionButton(
                icon = Icons.Default.Delete,
                text = "Delete Sample",
                description = if (canDelete) {
                    "Permanently remove this sample"
                } else {
                    "Cannot delete - sample is assigned to pads"
                },
                onClick = onDelete,
                enabled = canDelete,
                isDestructive = true
            )
        }
    }
}

/**
 * Individual action button for sample management.
 */
@Composable
private fun SampleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isDestructive -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    isDestructive -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isDestructive -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isDestructive -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (enabled) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sample rename dialog.
 */
@Composable
private fun SampleRenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rename Sample",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Sample Name") },
                    placeholder = { Text("Enter new name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (newName.isNotBlank() && newName != currentName) {
                                onRename(newName.trim())
                            }
                        }
                    )
                )
                
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
                            if (newName.isNotBlank() && newName != currentName) {
                                onRename(newName.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = newName.isNotBlank() && newName != currentName
                    ) {
                        Text("Rename")
                    }
                }
            }
        }
    }
}

/**
 * Sample deletion confirmation dialog.
 */
@Composable
private fun SampleDeleteConfirmationDialog(
    sample: SampleMetadata,
    usageInfo: SampleUsageInfo,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = "Delete Sample?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // Warning message
                Text(
                    text = "This action cannot be undone. The sample \"${sample.name}\" will be permanently deleted from your library.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Usage warning if applicable
                if (usageInfo.assignedPads.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Warning: Sample is in use",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            
                            Text(
                                text = "This sample is currently assigned to ${usageInfo.assignedPads.size} pad${if (usageInfo.assignedPads.size == 1) "" else "s"}. Deleting it will remove the assignment${if (usageInfo.assignedPads.size == 1) "" else "s"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

/**
 * Sample tag editor dialog.
 */
@Composable
private fun SampleTagEditorDialog(
    sample: SampleMetadata,
    onAddTags: (List<String>) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Tags",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Add new tag
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Add Tag") },
                    placeholder = { Text("Enter tag name") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    onAddTags(listOf(newTag.trim()))
                                    newTag = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = newTag.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add tag")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newTag.isNotBlank()) {
                                onAddTags(listOf(newTag.trim()))
                                newTag = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )
                
                // Current tags
                if (sample.tags.isNotEmpty()) {
                    Text(
                        text = "Current Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sample.tags) { tag ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    
                                    IconButton(
                                        onClick = { onRemoveTag(tag) }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tags added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * Data class for sample usage information.
 */
data class SampleUsageInfo(
    val assignedPads: List<Int> = emptyList(),
    val totalTriggers: Int = 0,
    val lastUsed: Long? = null
)

/**
 * Format file size in bytes to human-readable string.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * Format timestamp to readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}