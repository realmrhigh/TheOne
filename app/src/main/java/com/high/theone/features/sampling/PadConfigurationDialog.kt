package com.high.theone.features.sampling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.*

/**
 * Configuration dialog for individual drum pads.
 * Provides controls for volume, pan, playback mode, sample assignment, and visual customization.
 * 
 * Requirements: 2.2 (sample assignment), 4.1 (volume/pan), 4.2 (playback mode), 5.1 (sample selection)
 */
@Composable
fun PadConfigurationDialog(
    padState: PadState,
    availableSamples: List<SampleMetadata>,
    onDismiss: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSampleAssign: (String?) -> Unit,
    onPadNameChange: (String?) -> Unit,
    onPadColorChange: (String?) -> Unit,
    onRemoveSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentVolume by remember { mutableFloatStateOf(padState.volume) }
    var currentPan by remember { mutableFloatStateOf(padState.pan) }
    var currentPlaybackMode by remember { mutableStateOf(padState.playbackMode) }
    var currentName by remember { mutableStateOf(padState.displayName) }
    var showSampleBrowser by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pad ${padState.index + 1} Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                // Sample Assignment Section
                SampleAssignmentSection(
                    padState = padState,
                    onAssignSample = { showSampleBrowser = true },
                    onRemoveSample = onRemoveSample
                )
                
                // Audio Controls Section
                if (padState.hasAssignedSample) {
                    AudioControlsSection(
                        volume = currentVolume,
                        pan = currentPan,
                        playbackMode = currentPlaybackMode,
                        onVolumeChange = { volume ->
                            currentVolume = volume
                            onVolumeChange(volume)
                        },
                        onPanChange = { pan ->
                            currentPan = pan
                            onPanChange(pan)
                        },
                        onPlaybackModeChange = { mode ->
                            currentPlaybackMode = mode
                            onPlaybackModeChange(mode)
                        }
                    )
                }
                
                // Customization Section
                CustomizationSection(
                    padName = currentName,
                    onNameChange = { name ->
                        currentName = name
                        onPadNameChange(name.takeIf { it.isNotBlank() })
                    },
                    onColorChange = { showColorPicker = true }
                )
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
    
    // Sample Browser Dialog
    if (showSampleBrowser) {
        SampleBrowserDialog(
            samples = availableSamples,
            onSampleSelected = { sample ->
                onSampleAssign(sample?.id?.toString())
                showSampleBrowser = false
            },
            onDismiss = { showSampleBrowser = false }
        )
    }
    
    // Color Picker Dialog
    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { color ->
                onPadColorChange(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

/**
 * Section for sample assignment and management.
 */
@Composable
private fun SampleAssignmentSection(
    padState: PadState,
    onAssignSample: () -> Unit,
    onRemoveSample: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Sample Assignment",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                if (padState.hasAssignedSample) {
                    // Show assigned sample info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = padState.sampleName ?: "Unknown Sample",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Sample ID: ${padState.sampleId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row {
                            IconButton(onClick = onAssignSample) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Change Sample")
                            }
                            IconButton(onClick = onRemoveSample) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Sample")
                            }
                        }
                    }
                } else {
                    // Show assignment button
                    Button(
                        onClick = onAssignSample,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Assign Sample")
                    }
                }
            }
        }
    }
}

/**
 * Section for audio controls (volume, pan, playback mode).
 */
@Composable
private fun AudioControlsSection(
    volume: Float,
    pan: Float,
    playbackMode: PlaybackMode,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Audio Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Volume Control
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Volume", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Pan Control
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pan", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        pan < -0.1f -> "L${((-pan) * 100).toInt()}"
                        pan > 0.1f -> "R${(pan * 100).toInt()}"
                        else -> "Center"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Slider(
                value = pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Playback Mode
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Playback Mode", style = MaterialTheme.typography.bodyMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaybackMode.entries.forEach { mode ->
                    FilterChip(
                        selected = playbackMode == mode,
                        onClick = { onPlaybackModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    PlaybackMode.ONE_SHOT -> "One-Shot"
                                    PlaybackMode.LOOP -> "Loop"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Section for pad customization (name, color).
 */
@Composable
private fun CustomizationSection(
    padName: String,
    onNameChange: (String) -> Unit,
    onColorChange: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Customization",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Pad Name
        OutlinedTextField(
            value = padName,
            onValueChange = onNameChange,
            label = { Text("Pad Name") },
            placeholder = { Text("Enter custom name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Color Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pad Color", style = MaterialTheme.typography.bodyMedium)
            
            Button(
                onClick = onColorChange,
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose Color")
            }
        }
    }
}

/**
 * Sample browser dialog for selecting samples to assign to pads.
 */
@Composable
private fun SampleBrowserDialog(
    samples: List<SampleMetadata>,
    onSampleSelected: (SampleMetadata?) -> Unit,
    onDismiss: () -> Unit
) {
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Sample",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Clear assignment option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSampleSelected(null) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Remove Sample Assignment",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Sample list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(samples) { sample ->
                        SampleItem(
                            sample = sample,
                            onClick = { onSampleSelected(sample) }
                        )
                    }
                    
                    if (samples.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No samples available",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Record or import samples to get started",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
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

/**
 * Individual sample item in the browser.
 */
@Composable
private fun SampleItem(
    sample: SampleMetadata,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
                
                if (sample.tags.isNotEmpty()) {
                    Text(
                        text = sample.tags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Simple color picker dialog.
 */
@Composable
private fun ColorPickerDialog(
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val predefinedColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Choose Pad Color",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Color grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in predefinedColors.chunked(4)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { colorHex ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(android.graphics.Color.parseColor(colorHex)))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onColorSelected(colorHex) }
                                )
                            }
                        }
                    }
                }
                
                // Reset to default
                TextButton(
                    onClick = { onColorSelected("") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Default")
                }
            }
        }
    }
}