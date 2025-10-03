package com.high.theone.features.sampling

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sample browser interface for selecting available samples.
 * Provides search, filtering, preview, and import functionality.
 * 
 * Requirements: 7.1, 7.2 (sample browsing and selection)
 */
@Composable
fun SampleBrowser(
    samples: List<SampleMetadata>,
    searchQuery: String,
    selectedTags: Set<String>,
    sortBy: SampleSortBy,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onTagFilterChange: (Set<String>) -> Unit,
    onSortByChange: (SampleSortBy) -> Unit,
    onSampleSelect: (SampleMetadata) -> Unit,
    onSamplePreview: (SampleMetadata) -> Unit,
    onImportSample: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with search and actions
        SampleBrowserHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onFilterClick = { showFilterDialog = true },
            onImportClick = onImportSample,
            onRefreshClick = onRefresh,
            onSearchSubmit = { keyboardController?.hide() }
        )
        
        // Filter chips (if any filters are active)
        if (selectedTags.isNotEmpty() || sortBy != SampleSortBy.NAME) {
            ActiveFiltersRow(
                selectedTags = selectedTags,
                sortBy = sortBy,
                onTagRemove = { tag ->
                    onTagFilterChange(selectedTags - tag)
                },
                onClearAll = {
                    onTagFilterChange(emptySet())
                    onSortByChange(SampleSortBy.NAME)
                }
            )
        }
        
        // Sample list
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                LoadingIndicator()
            } else if (samples.isEmpty()) {
                EmptyState(
                    hasSearchQuery = searchQuery.isNotBlank(),
                    onImportClick = onImportSample
                )
            } else {
                SampleList(
                    samples = samples,
                    onSampleSelect = onSampleSelect,
                    onSamplePreview = onSamplePreview
                )
            }
        }
    }
    
    // Filter dialog
    if (showFilterDialog) {
        SampleFilterDialog(
            availableTags = samples.flatMap { it.tags }.distinct().sorted(),
            selectedTags = selectedTags,
            sortBy = sortBy,
            onTagSelectionChange = onTagFilterChange,
            onSortByChange = onSortByChange,
            onDismiss = { showFilterDialog = false }
        )
    }
}

/**
 * Header section with search bar and action buttons.
 */
@Composable
private fun SampleBrowserHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onImportClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Title and actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sample Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRefreshClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Default.Add, contentDescription = "Import Sample")
                }
            }
        }
        
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search samples...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() })
        )
    }
}

/**
 * Row showing active filters with remove options.
 */
@Composable
private fun ActiveFiltersRow(
    selectedTags: Set<String>,
    sortBy: SampleSortBy,
    onTagRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Filters:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort filter chip
                if (sortBy != SampleSortBy.NAME) {
                    FilterChip(
                        selected = true,
                        onClick = { },
                        label = { Text("Sort: ${sortBy.displayName}") },
                        leadingIcon = {
                            Icon(Icons.Default.Sort, contentDescription = null)
                        }
                    )
                }
                
                // Tag filter chips
                selectedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onTagRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove filter")
                        }
                    )
                }
            }
        }
    }
}

/**
 * List of samples with metadata display.
 */
@Composable
private fun SampleList(
    samples: List<SampleMetadata>,
    onSampleSelect: (SampleMetadata) -> Unit,
    onSamplePreview: (SampleMetadata) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(samples, key = { it.id }) { sample ->
            SampleListItem(
                sample = sample,
                onSelect = { onSampleSelect(sample) },
                onPreview = { onSamplePreview(sample) }
            )
        }
    }
}

/**
 * Individual sample item with metadata and actions.
 */
@Composable
private fun SampleListItem(
    sample: SampleMetadata,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
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
            // Header row with name and preview button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sample.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = onPreview) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Preview sample",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(sample.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Sample rate and channels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${sample.sampleRate}Hz • ${if (sample.channels == 1) "Mono" else "Stereo"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Tags row (if any)
            if (sample.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sample.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    if (sample.tags.size > 3) {
                        Text(
                            text = "+${sample.tags.size - 3} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
            
            // Created date
            Text(
                text = "Created ${formatDate(sample.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Loading indicator for sample loading state.
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading samples...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty state when no samples are available.
 */
@Composable
private fun EmptyState(
    hasSearchQuery: Boolean,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                if (hasSearchQuery) Icons.Default.SearchOff else Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = if (hasSearchQuery) "No samples found" else "No samples available",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = if (hasSearchQuery) {
                    "Try adjusting your search or filters"
                } else {
                    "Record or import samples to get started"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            if (!hasSearchQuery) {
                Button(onClick = onImportClick) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Sample")
                }
            }
        }
    }
}

/**
 * Sample sorting options.
 */
enum class SampleSortBy(val displayName: String) {
    NAME("Name"),
    CREATED_DATE("Date Created"),
    DURATION("Duration"),
    SIZE("File Size")
}

/**
 * Format duration in milliseconds to readable string.
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000.0
    return if (totalSeconds < 60) {
        String.format("%.1fs", totalSeconds)
    } else {
        val minutes = (totalSeconds / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Format timestamp to readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}