package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.PanelType

/**
 * Content router for different panel types
 */
@Composable
fun QuickAccessPanelContent(
    panelType: PanelType,
    modifier: Modifier = Modifier
) {
    when (panelType) {
        PanelType.SAMPLING -> SamplingPanel(modifier = modifier)
        PanelType.MIDI -> MidiPanel(modifier = modifier)
        PanelType.MIXER -> MixerPanel(modifier = modifier)
        PanelType.SETTINGS -> SettingsPanel(modifier = modifier)
        PanelType.SAMPLE_EDITOR -> SampleEditorPanel(modifier = modifier)
    }
}

/**
 * Sampling panel with recording controls and sample browser
 */
@Composable
fun SamplingPanel(
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingLevel by remember { mutableFloatStateOf(0f) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Recording Controls Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Recording Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Record button
                    FilledTonalButton(
                        onClick = { isRecording = !isRecording },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRecording) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRecording) "Stop" else "Record")
                    }
                    
                    // Input level indicator
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Input Level",
                            style = MaterialTheme.typography.labelSmall
                        )
                        LinearProgressIndicator(
                            progress = { recordingLevel },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                
                // Recording settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-trim", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = true, onCheckedChange = {})
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monitor", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = false, onCheckedChange = {})
                }
            }
        }
        
        // Sample Browser Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sample Browser",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Sample categories
                val sampleCategories = listOf("Drums", "Bass", "Synth", "Vocal", "FX")
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sampleCategories) { category ->
                        ListItem(
                            headlineContent = { Text(category) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * MIDI panel with device settings and mapping controls
 */
@Composable
fun MidiPanel(
    modifier: Modifier = Modifier
) {
    var midiEnabled by remember { mutableStateOf(true) }
    var selectedDevice by remember { mutableStateOf("No Device") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MIDI Device Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MIDI Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MIDI Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = midiEnabled,
                        onCheckedChange = { midiEnabled = it }
                    )
                }
                
                // Device selector
                OutlinedTextField(
                    value = selectedDevice,
                    onValueChange = { selectedDevice = it },
                    label = { Text("MIDI Device") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { /* Open device selector */ }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Device"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // MIDI channel
                OutlinedTextField(
                    value = "1",
                    onValueChange = { },
                    label = { Text("MIDI Channel") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // MIDI Mapping Controls
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MIDI Mapping",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = { /* Start MIDI learn */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Learn MIDI Mapping")
                }
                
                OutlinedButton(
                    onClick = { /* Clear mappings */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Mappings")
                }
                
                // Current mappings list
                Text(
                    text = "Active Mappings",
                    style = MaterialTheme.typography.labelMedium
                )
                
                LazyColumn(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(3) { index ->
                        ListItem(
                            headlineContent = { Text("Pad ${index + 1}") },
                            supportingContent = { Text("Note C${index + 3}") },
                            trailingContent = {
                                IconButton(onClick = { /* Remove mapping */ }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mixer panel with track levels and effects
 */
@Composable
fun MixerPanel(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Master",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Volume", modifier = Modifier.width(60.dp))
                    Slider(
                        value = 0.8f,
                        onValueChange = { },
                        modifier = Modifier.weight(1f)
                    )
                    Text("80%", modifier = Modifier.width(40.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { /* Mute all */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Mute All")
                    }
                    
                    Button(
                        onClick = { /* Solo clear */ }
                    ) {
                        Text("Solo Clear")
                    }
                }
            }
        }
        
        // Track Levels
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Track Levels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(8) { trackIndex ->
                        TrackMixerStrip(
                            trackName = "Track ${trackIndex + 1}",
                            level = 0.7f,
                            isMuted = false,
                            isSoloed = false,
                            onLevelChange = { },
                            onMuteToggle = { },
                            onSoloToggle = { }
                        )
                    }
                }
            }
        }
        
        // Effects Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Effects",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        onClick = { },
                        label = { Text("Reverb") },
                        selected = true
                    )
                    FilterChip(
                        onClick = { },
                        label = { Text("Delay") },
                        selected = false
                    )
                    FilterChip(
                        onClick = { },
                        label = { Text("Filter") },
                        selected = true
                    )
                }
            }
        }
    }
}

/**
 * Individual track mixer strip component
 */
@Composable
private fun TrackMixerStrip(
    trackName: String,
    level: Float,
    isMuted: Boolean,
    isSoloed: Boolean,
    onLevelChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = trackName,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall
        )
        
        Slider(
            value = level,
            onValueChange = onLevelChange,
            modifier = Modifier.weight(1f)
        )
        
        IconButton(
            onClick = onMuteToggle,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute"
            )
        }
        
        IconButton(
            onClick = onSoloToggle,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (isSoloed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = if (isSoloed) "Unsolo" else "Solo"
            )
        }
    }
}

/**
 * Settings panel with app preferences
 */
@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier
) {
    var audioLatency by remember { mutableStateOf("Low") }
    var enableAnimations by remember { mutableStateOf(true) }
    var performanceMode by remember { mutableStateOf("Balanced") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Audio Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Audio latency setting
                OutlinedTextField(
                    value = audioLatency,
                    onValueChange = { audioLatency = it },
                    label = { Text("Audio Latency") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { /* Open latency selector */ }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Latency"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Sample rate
                OutlinedTextField(
                    value = "44.1 kHz",
                    onValueChange = { },
                    label = { Text("Sample Rate") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Buffer size
                OutlinedTextField(
                    value = "256 samples",
                    onValueChange = { },
                    label = { Text("Buffer Size") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // UI Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "UI Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Animations", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = enableAnimations,
                        onCheckedChange = { enableAnimations = it }
                    )
                }
                
                // Performance mode
                OutlinedTextField(
                    value = performanceMode,
                    onValueChange = { performanceMode = it },
                    label = { Text("Performance Mode") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { /* Open performance mode selector */ }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Performance Mode"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // App Info
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("1.0.0") }
                )
                
                ListItem(
                    headlineContent = { Text("Build") },
                    supportingContent = { Text("2024.01.15") }
                )
                
                Button(
                    onClick = { /* Show about dialog */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("About TheOne")
                }
            }
        }
    }
}

/**
 * Sample editor panel (placeholder for future implementation)
 */
@Composable
fun SampleEditorPanel(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Sample Editor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Advanced sample editing features will be available here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}