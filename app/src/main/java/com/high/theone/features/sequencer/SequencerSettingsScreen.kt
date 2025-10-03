package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.model.*

/**
 * Sequencer settings screen for configuring preferences and defaults
 * Requirements: 9.7 - settings and preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerSettingsScreen(
    navController: NavHostController,
    viewModel: SequencerViewModel = hiltViewModel()
) {
    val settings by viewModel.sequencerSettings.collectAsState()
    val scrollState = rememberScrollState()
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sequencer Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset to defaults")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Default Pattern Settings
            SettingsSection(
                title = "Default Pattern Settings",
                icon = Icons.Default.GridOn
            ) {
                // Default Tempo
                SettingsSlider(
                    label = "Default Tempo",
                    value = settings.defaultTempo,
                    valueRange = 60f..200f,
                    steps = 139,
                    onValueChange = { newTempo ->
                        viewModel.updateSettings(settings.copy(defaultTempo = newTempo))
                    },
                    valueFormatter = { "${it.toInt()} BPM" }
                )
                
                // Default Swing
                SettingsSlider(
                    label = "Default Swing",
                    value = settings.defaultSwing,
                    valueRange = 0f..0.75f,
                    steps = 74,
                    onValueChange = { newSwing ->
                        viewModel.updateSettings(settings.copy(defaultSwing = newSwing))
                    },
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )
                
                // Default Quantization
                SettingsDropdown(
                    label = "Default Quantization",
                    value = settings.defaultQuantization,
                    options = Quantization.values().toList(),
                    onValueChange = { newQuantization ->
                        viewModel.updateSettings(settings.copy(defaultQuantization = newQuantization))
                    },
                    optionFormatter = { it.displayName }
                )
            }
            
            // Recording Settings
            SettingsSection(
                title = "Recording Settings",
                icon = Icons.Default.FiberManualRecord
            ) {
                // Default Recording Mode
                SettingsDropdown(
                    label = "Default Recording Mode",
                    value = settings.recordingMode,
                    options = RecordingMode.values().toList(),
                    onValueChange = { newMode ->
                        viewModel.updateSettings(settings.copy(recordingMode = newMode))
                    },
                    optionFormatter = { it.displayName }
                )
                
                // Auto-save
                SettingsSwitch(
                    label = "Auto-save Patterns",
                    description = "Automatically save patterns when modified",
                    checked = settings.autoSaveEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings(settings.copy(autoSaveEnabled = enabled))
                    }
                )
            }
            
            // Performance Settings
            SettingsSection(
                title = "Performance Settings",
                icon = Icons.Default.Speed
            ) {
                // Performance Mode
                SettingsDropdown(
                    label = "Performance Mode",
                    value = settings.performanceMode,
                    options = PerformanceMode.values().toList(),
                    onValueChange = { newMode ->
                        viewModel.updateSettings(settings.copy(performanceMode = newMode))
                    },
                    optionFormatter = { it.displayName }
                )
                
                // Max Pattern Length
                SettingsDropdown(
                    label = "Max Pattern Length",
                    value = settings.maxPatternLength,
                    options = listOf(8, 16, 24, 32),
                    onValueChange = { newLength ->
                        viewModel.updateSettings(settings.copy(maxPatternLength = newLength))
                    },
                    optionFormatter = { "$it steps" }
                )
                
                // Max Patterns
                SettingsDropdown(
                    label = "Max Patterns per Project",
                    value = settings.maxPatterns,
                    options = listOf(16, 32, 64, 128),
                    onValueChange = { newMax ->
                        viewModel.updateSettings(settings.copy(maxPatterns = newMax))
                    },
                    optionFormatter = { "$it patterns" }
                )
            }
            
            // User Interface Settings
            SettingsSection(
                title = "User Interface",
                icon = Icons.Default.Palette
            ) {
                // Visual Feedback
                SettingsSwitch(
                    label = "Visual Feedback",
                    description = "Enable visual animations and effects",
                    checked = settings.visualFeedbackEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings(settings.copy(visualFeedbackEnabled = enabled))
                    }
                )
                
                // Haptic Feedback
                SettingsSwitch(
                    label = "Haptic Feedback",
                    description = "Enable vibration feedback for interactions",
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings(settings.copy(hapticFeedbackEnabled = enabled))
                    }
                )
                
                // Metronome
                SettingsSwitch(
                    label = "Metronome",
                    description = "Enable metronome click during playback",
                    checked = settings.metronomeEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings(settings.copy(metronomeEnabled = enabled))
                    }
                )
            }
            
            // Advanced Settings
            SettingsSection(
                title = "Advanced Features",
                icon = Icons.Default.Settings
            ) {
                // Advanced Features
                SettingsSwitch(
                    label = "Enable Advanced Features",
                    description = "Enable experimental and advanced sequencer features",
                    checked = settings.enableAdvancedFeatures,
                    onCheckedChange = { enabled ->
                        viewModel.updateSettings(settings.copy(enableAdvancedFeatures = enabled))
                    }
                )
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
                
                Button(
                    onClick = { navController.navigate("sequencer_help") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Help, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Help & Tutorial")
                }
            }
        }
    }
    
    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("Are you sure you want to reset all settings to their default values? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSettings(SequencerSettings()) // Reset to defaults
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Help dialog
    if (showHelpDialog) {
        SequencerHelpDialog(
            onDismiss = { showHelpDialog = false },
            onNavigateToTutorial = { 
                showHelpDialog = false
                navController.navigate("sequencer_tutorial")
            }
        )
    }
}

/**
 * Settings section with title and icon
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            content()
        }
    }
}

/**
 * Settings slider component
 */
@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Settings dropdown component
 */
@Composable
private fun <T> SettingsDropdown(
    label: String,
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    optionFormatter: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = optionFormatter(value),
                onValueChange = { },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionFormatter(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Settings switch component
 */
@Composable
private fun SettingsSwitch(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Help dialog for sequencer settings
 */
@Composable
private fun SequencerHelpDialog(
    onDismiss: () -> Unit,
    onNavigateToTutorial: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sequencer Help") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Configure your sequencer preferences:")
                Text("• Default Tempo: Sets the initial tempo for new patterns")
                Text("• Default Swing: Adds groove to your patterns")
                Text("• Recording Mode: Choose how new recordings are handled")
                Text("• Performance Mode: Optimize for battery life or performance")
                Text("• Visual/Haptic Feedback: Customize user interface responses")
            }
        },
        confirmButton = {
            TextButton(onClick = onNavigateToTutorial) {
                Text("View Tutorial")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Extension properties for display names
private val Quantization.displayName: String
    get() = when (this) {
        Quantization.EIGHTH -> "1/8 Note"
        Quantization.SIXTEENTH -> "1/16 Note"
        Quantization.THIRTY_SECOND -> "1/32 Note"
        Quantization.NONE -> "No Quantization"
    }

private val RecordingMode.displayName: String
    get() = when (this) {
        RecordingMode.REPLACE -> "Replace"
        RecordingMode.OVERDUB -> "Overdub"
        RecordingMode.PUNCH_IN -> "Punch In"
    }

private val PerformanceMode.displayName: String
    get() = when (this) {
        PerformanceMode.POWER_SAVE -> "Power Save"
        PerformanceMode.BALANCED -> "Balanced"
        PerformanceMode.PERFORMANCE -> "Performance"
    }