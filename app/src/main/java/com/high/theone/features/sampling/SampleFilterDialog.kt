package com.high.theone.features.sampling

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Filter dialog for sample browser with tag filtering and sorting options.
 * 
 * Requirements: 7.1 (sample filtering and organization)
 */
@Composable
fun SampleFilterDialog(
    availableTags: List<String>,
    selectedTags: Set<String>,
    sortBy: SampleSortBy,
    onTagSelectionChange: (Set<String>) -> Unit,
    onSortByChange: (SampleSortBy) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localSelectedTags by remember { mutableStateOf(selectedTags) }
    var localSortBy by remember { mutableStateOf(sortBy) }
    
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                // Sort section
                SortSection(
                    currentSortBy = localSortBy,
                    onSortByChange = { localSortBy = it }
                )
                
                // Tag filter section
                if (availableTags.isNotEmpty()) {
                    TagFilterSection(
                        availableTags = availableTags,
                        selectedTags = localSelectedTags,
                        onTagSelectionChange = { localSelectedTags = it }
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset button
                    OutlinedButton(
                        onClick = {
                            localSelectedTags = emptySet()
                            localSortBy = SampleSortBy.NAME
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                    
                    // Apply button
                    Button(
                        onClick = {
                            onTagSelectionChange(localSelectedTags)
                            onSortByChange(localSortBy)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Sort options section.
 */
@Composable
private fun SortSection(
    currentSortBy: SampleSortBy,
    onSortByChange: (SampleSortBy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Sort By",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleSortBy.entries.forEach { sortOption ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSortBy == sortOption,
                        onClick = { onSortByChange(sortOption) }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = sortOption.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Sort direction icon
                    Icon(
                        when (sortOption) {
                            SampleSortBy.NAME -> Icons.Default.SortByAlpha
                            SampleSortBy.CREATED_DATE -> Icons.Default.Schedule
                            SampleSortBy.DURATION -> Icons.Default.Timer
                            SampleSortBy.SIZE -> Icons.Default.Storage
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Tag filtering section.
 */
@Composable
private fun TagFilterSection(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagSelectionChange: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter by Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            // Select all/none buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onTagSelectionChange(availableTags.toSet()) }
                ) {
                    Text("All", style = MaterialTheme.typography.labelMedium)
                }
                
                TextButton(
                    onClick = { onTagSelectionChange(emptySet()) }
                ) {
                    Text("None", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        
        // Tag list
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(availableTags) { tag ->
                    TagFilterItem(
                        tag = tag,
                        isSelected = tag in selectedTags,
                        onSelectionChange = { isSelected ->
                            if (isSelected) {
                                onTagSelectionChange(selectedTags + tag)
                            } else {
                                onTagSelectionChange(selectedTags - tag)
                            }
                        }
                    )
                }
                
                if (availableTags.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tags available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // Selected tags summary
        if (selectedTags.isNotEmpty()) {
            Text(
                text = "${selectedTags.size} tag${if (selectedTags.size == 1) "" else "s"} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Individual tag filter item with checkbox.
 */
@Composable
private fun TagFilterItem(
    tag: String,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChange
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = tag,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        
        // Tag icon
        Icon(
            Icons.Default.Tag,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}