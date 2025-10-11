package com.high.theone.features.sampling

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Sample metadata editor dialog for editing name and tags of recorded samples.
 * Provides comprehensive editing capabilities with validation and suggestions.
 * 
 * Requirements: 4.4 (sample metadata editing)
 */
@Composable
fun SampleMetadataEditor(
    currentName: String,
    currentTags: List<String>,
    onNameChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedName by remember { mutableStateOf(currentName) }
    var editedTags by remember { mutableStateOf(currentTags.toMutableList()) }
    var newTag by remember { mutableStateOf("") }
    var showTagSuggestions by remember { mutableStateOf(false) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Common tag suggestions
    val tagSuggestions = listOf(
        "Drum", "Bass", "Melody", "Vocal", "Percussion", "Synth", "Guitar", 
        "Piano", "Ambient", "Loop", "One-shot", "Kick", "Snare", "Hi-hat",
        "Crash", "Ride", "Tom", "Clap", "Snap", "FX", "Riser", "Drop"
    ).filter { suggestion ->
        suggestion.lowercase().contains(newTag.lowercase()) && 
        !editedTags.contains(suggestion)
    }.take(6)
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                MetadataEditorHeader(onDismiss = onDismiss)
                
                // Name editor
                NameEditor(
                    name = editedName,
                    onNameChange = { editedName = it },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Tags editor
                TagsEditor(
                    tags = editedTags,
                    newTag = newTag,
                    onNewTagChange = { 
                        newTag = it
                        showTagSuggestions = it.isNotEmpty()
                    },
                    onAddTag = { tag ->
                        if (tag.isNotBlank() && !editedTags.contains(tag.trim())) {
                            editedTags.add(tag.trim())
                            newTag = ""
                            showTagSuggestions = false
                            keyboardController?.hide()
                        }
                    },
                    onRemoveTag = { tag ->
                        editedTags.remove(tag)
                    },
                    tagSuggestions = if (showTagSuggestions) tagSuggestions else emptyList(),
                    onSuggestionSelected = { suggestion ->
                        editedTags.add(suggestion)
                        newTag = ""
                        showTagSuggestions = false
                        keyboardController?.hide()
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // Action buttons
                MetadataEditorActions(
                    onSave = {
                        onNameChange(editedName.trim())
                        onTagsChange(editedTags.toList())
                        onDismiss()
                    },
                    onCancel = onDismiss,
                    hasChanges = editedName.trim() != currentName || editedTags != currentTags,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Header section with title and close button
 */
@Composable
private fun MetadataEditorHeader(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Edit Sample Info",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Customize name and tags for better organization",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Name editor section
 */
@Composable
private fun NameEditor(
    name: String,
    onNameChange: (String) -> Unit,
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
                text = "Sample Name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("Enter sample name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            
            // Name suggestions/tips
            if (name.isEmpty()) {
                Text(
                    text = "💡 Tip: Use descriptive names like 'Kick Heavy 808' or 'Vocal Chop Melodic'",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tags editor section with suggestions
 */
@Composable
private fun TagsEditor(
    tags: List<String>,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    tagSuggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Add new tag input
            OutlinedTextField(
                value = newTag,
                onValueChange = onNewTagChange,
                label = { Text("Add Tag") },
                placeholder = { Text("Enter tag name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onAddTag(newTag) },
                        enabled = newTag.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add tag"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onAddTag(newTag)
                        keyboardController?.hide()
                    }
                )
            )
            
            // Tag suggestions
            if (tagSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Suggestions:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tagSuggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { onSuggestionSelected(suggestion) },
                                label = { 
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Current tags
            if (tags.isNotEmpty()) {
                Text(
                    text = "Current Tags (${tags.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tags) { tag ->
                        TagItem(
                            tag = tag,
                            onRemove = { onRemoveTag(tag) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        
                        Text(
                            text = "No tags added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Tags help organize and find your samples quickly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual tag item with remove button
 */
@Composable
private fun TagItem(
    tag: String,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Text(
                    text = tag,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove tag",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Action buttons for save/cancel
 */
@Composable
private fun MetadataEditorActions(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    hasChanges: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel")
            }
            
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = hasChanges
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Changes")
            }
        }
    }
}

/**
 * Pad selector dialog for assigning samples to pads
 */
@Composable
fun PadSelectorDialog(
    availablePads: List<Int>,
    onPadSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                PadSelectorHeader(onDismiss = onDismiss)
                
                // Pad grid
                PadGrid(
                    availablePads = availablePads,
                    onPadSelected = onPadSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Header for pad selector dialog
 */
@Composable
private fun PadSelectorHeader(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Assign to Pad",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select a pad to assign this sample",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close"
            )
        }
    }
}

/**
 * Grid of available pads for selection
 */
@Composable
private fun PadGrid(
    availablePads: List<Int>,
    onPadSelected: (Int) -> Unit,
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
                text = "Available Pads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // 4x4 grid of pads
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0..3) {
                        val padIndex = row * 4 + col
                        val isAvailable = availablePads.contains(padIndex)
                        
                        PadButton(
                            padIndex = padIndex,
                            isAvailable = isAvailable,
                            onSelected = { onPadSelected(padIndex) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    text = "Available"
                )
                
                LegendItem(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    text = "Occupied"
                )
            }
        }
    }
}

/**
 * Individual pad button for selection
 */
@Composable
private fun PadButton(
    padIndex: Int,
    isAvailable: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSelected,
        enabled = isAvailable,
        modifier = modifier.aspectRatio(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAvailable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
            contentColor = if (isAvailable) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "${padIndex + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Legend item for pad status
 */
@Composable
private fun LegendItem(
    color: androidx.compose.ui.graphics.Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}