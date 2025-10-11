package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.LayoutPreset
import com.high.theone.model.*

/**
 * Customization panel for layout and feature preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationPanel(
    layoutCustomization: LayoutCustomization,
    featureVisibility: FeatureVisibilityPreferences,
    layoutPresets: List<LayoutPreset>,
    activePresetId: String?,
    onLayoutCustomizationChange: (LayoutCustomization) -> Unit,
    onFeatureVisibilityChange: (FeatureVisibilityPreferences) -> Unit,
    onPresetSelected: (String) -> Unit,
    onCreatePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreatePresetDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Tab row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Layout") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Features") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Presets") }
            )
        }
        
        // Tab content
        when (selectedTab) {
            0 -> LayoutCustomizationTab(
                customization = layoutCustomization,
                onCustomizationChange = onLayoutCustomizationChange,
                modifier = Modifier.fillMaxSize()
            )
            1 -> FeatureVisibilityTab(
                visibility = featureVisibility,
                onVisibilityChange = onFeatureVisibilityChange,
                modifier = Modifier.fillMaxSize()
            )
            2 -> PresetsTab(
                presets = layoutPresets,
                activePresetId = activePresetId,
                onPresetSelected = onPresetSelected,
                onCreatePreset = { showCreatePresetDialog = true },
                onDeletePreset = onDeletePreset,
                onResetToDefaults = onResetToDefaults,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    // Create preset dialog
    if (showCreatePresetDialog) {
        CreatePresetDialog(
            onCreatePreset = { name ->
                onCreatePreset(name)
                showCreatePresetDialog = false
            },
            onDismiss = { showCreatePresetDialog = false }
        )
    }
}

/**
 * Layout customization tab
 */
@Composable
private fun LayoutCustomizationTab(
    customization: LayoutCustomization,
    onCustomizationChange: (LayoutCustomization) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Component Sizes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            ComponentSizeSelector(
                label = "Drum Pad Size",
                selectedSize = customization.drumPadSize,
                onSizeSelected = { size ->
                    onCustomizationChange(customization.copy(drumPadSize = size))
                }
            )
        }
        
        item {
            ComponentSizeSelector(
                label = "Sequencer Height",
                selectedSize = customization.sequencerHeight,
                onSizeSelected = { size ->
                    onCustomizationChange(customization.copy(sequencerHeight = size))
                }
            )
        }
        
        item {
            ComponentSizeSelector(
                label = "Transport Bar Height",
                selectedSize = customization.transportBarHeight,
                onSizeSelected = { size ->
                    onCustomizationChange(customization.copy(transportBarHeight = size))
                }
            )
        }
        
        item {
            ComponentSizeSelector(
                label = "Panel Spacing",
                selectedSize = customization.panelSpacing,
                onSizeSelected = { size ->
                    onCustomizationChange(customization.copy(panelSpacing = size))
                }
            )
        }
        
        item {
            Divider()
            Text(
                text = "Visual Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Animations")
                Switch(
                    checked = customization.enableAnimations,
                    onCheckedChange = { enabled ->
                        onCustomizationChange(customization.copy(enableAnimations = enabled))
                    }
                )
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compact Mode")
                Switch(
                    checked = customization.compactMode,
                    onCheckedChange = { compact ->
                        onCustomizationChange(customization.copy(compactMode = compact))
                    }
                )
            }
        }
    }
}

/**
 * Feature visibility tab
 */
@Composable
private fun FeatureVisibilityTab(
    visibility: FeatureVisibilityPreferences,
    onVisibilityChange: (FeatureVisibilityPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Visible Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        val features = listOf(
            Triple("Transport Controls", visibility.showTransportControls) { show: Boolean ->
                onVisibilityChange(visibility.copy(showTransportControls = show))
            },
            Triple("Drum Pads", visibility.showDrumPads) { show: Boolean ->
                onVisibilityChange(visibility.copy(showDrumPads = show))
            },
            Triple("Sequencer", visibility.showSequencer) { show: Boolean ->
                onVisibilityChange(visibility.copy(showSequencer = show))
            },
            Triple("MIDI Controls", visibility.showMidiControls) { show: Boolean ->
                onVisibilityChange(visibility.copy(showMidiControls = show))
            },
            Triple("Mixer Controls", visibility.showMixerControls) { show: Boolean ->
                onVisibilityChange(visibility.copy(showMixerControls = show))
            },
            Triple("Sampling Controls", visibility.showSamplingControls) { show: Boolean ->
                onVisibilityChange(visibility.copy(showSamplingControls = show))
            },
            Triple("Advanced Controls", visibility.showAdvancedControls) { show: Boolean ->
                onVisibilityChange(visibility.copy(showAdvancedControls = show))
            },
            Triple("Performance Metrics", visibility.showPerformanceMetrics) { show: Boolean ->
                onVisibilityChange(visibility.copy(showPerformanceMetrics = show))
            },
            Triple("Visual Effects", visibility.showVisualEffects) { show: Boolean ->
                onVisibilityChange(visibility.copy(showVisualEffects = show))
            },
            Triple("Audio Levels", visibility.showAudioLevels) { show: Boolean ->
                onVisibilityChange(visibility.copy(showAudioLevels = show))
            }
        )
        
        items(features) { (label, isVisible, onToggle) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label)
                Switch(
                    checked = isVisible,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

/**
 * Presets tab
 */
@Composable
private fun PresetsTab(
    presets: List<LayoutPreset>,
    activePresetId: String?,
    onPresetSelected: (String) -> Unit,
    onCreatePreset: () -> Unit,
    onDeletePreset: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layout Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCreatePreset) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create")
                    }
                    
                    OutlinedButton(onClick = onResetToDefaults) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
        }
        
        items(presets) { preset ->
            PresetCard(
                preset = preset,
                isActive = preset.id == activePresetId,
                onSelect = { onPresetSelected(preset.id) },
                onDelete = if (!preset.isDefault) {
                    { onDeletePreset(preset.id) }
                } else null
            )
        }
    }
}

/**
 * Component size selector
 */
@Composable
private fun ComponentSizeSelector(
    label: String,
    selectedSize: ComponentSize,
    onSizeSelected: (ComponentSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComponentSize.values().forEach { size ->
                FilterChip(
                    selected = selectedSize == size,
                    onClick = { onSizeSelected(size) },
                    label = { Text(size.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

/**
 * Preset card
 */
@Composable
private fun PresetCard(
    preset: LayoutPreset,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                if (preset.isDefault) {
                    Text(
                        text = "Default preset",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(onClick = onSelect) {
                        Text("Apply")
                    }
                }
                
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

/**
 * Create preset dialog
 */
@Composable
private fun CreatePresetDialog(
    onCreatePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Layout Preset") },
        text = {
            Column {
                Text("Enter a name for your custom layout preset:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreatePreset(presetName) },
                enabled = presetName.isNotBlank()
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