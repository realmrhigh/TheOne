package com.high.theone.features.compactui

import androidx.compose.foundation.clickable
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.midi.ui.MidiSettingsViewModel
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.PanelType

/**
 * Content router for different panel types.
 * Each panel pulls its own ViewModel via hiltViewModel() so it always has
 * the same instance that's live in the rest of the screen.
 */
@Composable
fun QuickAccessPanelContent(
    panelType: PanelType,
    modifier: Modifier = Modifier,
    onNavigateToMidiSettings: () -> Unit = {}
) {
    when (panelType) {
        PanelType.SAMPLING -> SamplingPanel(modifier = modifier)
        PanelType.MIDI -> MidiPanel(modifier = modifier, onOpenMidiSettings = onNavigateToMidiSettings)
        PanelType.MIXER -> MixerPanel(modifier = modifier)
        PanelType.SETTINGS -> SettingsPanel(modifier = modifier)
        PanelType.SAMPLE_EDITOR -> SampleEditorPanel(modifier = modifier)
    }
}

/**
 * Sampling panel — wired to SamplingViewModel for live recording state & sample list.
 */
@Composable
fun SamplingPanel(
    modifier: Modifier = Modifier
) {
    val samplingViewModel: SamplingViewModel = hiltViewModel()
    val uiState by samplingViewModel.uiState.collectAsState()
    val recordingState = uiState.recordingState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Recording Controls Section
        Card(modifier = Modifier.fillMaxWidth()) {
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
                    // Record / Stop button wired to SamplingViewModel
                    FilledTonalButton(
                        onClick = {
                            if (recordingState.isRecording) {
                                samplingViewModel.stopRecording()
                            } else {
                                samplingViewModel.startRecording()
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (recordingState.isRecording)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (recordingState.isRecording)
                                Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (recordingState.isRecording)
                                "Stop Recording" else "Start Recording"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (recordingState.isRecording) "Stop" else "Record")
                    }

                    // Live input level indicator
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (recordingState.isRecording)
                                recordingState.formattedDuration else "Input Level",
                            style = MaterialTheme.typography.labelSmall
                        )
                        LinearProgressIndicator(
                            progress = { recordingState.peakLevel },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Error state
                if (recordingState.error != null) {
                    Text(
                        text = recordingState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Loaded Samples Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Samples (${uiState.availableSamples.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.availableSamples.isEmpty()) {
                    Text(
                        text = "No samples recorded yet. Tap Record to capture audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.availableSamples) { sample ->
                            ListItem(
                                headlineContent = { Text(sample.name) },
                                supportingContent = {
                                    Text(
                                        "${sample.formattedDuration}  •  ${sample.formattedFileSize}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
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
}

/**
 * MIDI panel — shows connected devices from MidiSettingsViewModel,
 * and provides a direct "Open MIDI Settings" navigation button.
 */
@Composable
fun MidiPanel(
    modifier: Modifier = Modifier,
    onOpenMidiSettings: () -> Unit = {}
) {
    val midiViewModel: MidiSettingsViewModel = hiltViewModel()
    val uiState by midiViewModel.uiState.collectAsState()
    val connectedDevices by midiViewModel.connectedDevices.collectAsState()
    val availableDevices by midiViewModel.availableDevices.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MIDI Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIDI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Status badge
                    Badge(
                        containerColor = if (uiState.midiEnabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ) {
                        Text(if (uiState.midiEnabled) "ON" else "OFF")
                    }
                }

                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Connected devices
                if (connectedDevices.isNotEmpty()) {
                    Text(
                        text = "Connected (${connectedDevices.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    connectedDevices.values.forEach { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                } else {
                    Text(
                        text = "No MIDI devices connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Available devices
                if (availableDevices.isNotEmpty()) {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableDevices) { device ->
                            ListItem(
                                headlineContent = { Text(device.name) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Usb,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = {
                                        midiViewModel.connectDevice(device.id)
                                    }) {
                                        Text("Connect")
                                    }
                                }
                            )
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Full settings navigation
        Button(
            onClick = onOpenMidiSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open MIDI Settings")
        }
    }
}

/**
 * Mixer panel — real pad volume / pan from DrumTrackViewModel.
 */
@Composable
fun MixerPanel(
    modifier: Modifier = Modifier
) {
    val drumTrackViewModel: DrumTrackViewModel = hiltViewModel()
    val padSettingsMap by drumTrackViewModel.padSettingsMap.collectAsState()
    // Sort pads by their numeric index
    val pads = remember(padSettingsMap) {
        padSettingsMap.entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pad level strips
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pad Levels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                pads.forEach { (padId, settings) ->
                    PadMixerStrip(
                        padName = settings.name,
                        volume = settings.volume,
                        pan = settings.pan,
                        onVolumeChange = { newVol ->
                            drumTrackViewModel.updatePadSettings(settings.copy(volume = newVol))
                        },
                        onPanChange = { newPan ->
                            drumTrackViewModel.updatePadSettings(settings.copy(pan = newPan))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Per-pad mixer strip: name label, volume slider, pan slider.
 */
@Composable
private fun PadMixerStrip(
    padName: String,
    volume: Float,
    pan: Float,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = padName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Volume
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                modifier = Modifier.size(16.dp)
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pan
            Text(
                text = "Pan",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.size(16.dp)
            )
            Slider(
                value = pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    pan < -0.05f -> "L${(-pan * 100).toInt()}"
                    pan > 0.05f -> "R${(pan * 100).toInt()}"
                    else -> "C"
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(36.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Settings panel with app preferences.
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = audioLatency,
                    onValueChange = { audioLatency = it },
                    label = { Text("Audio Latency") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Latency")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = "44.1 kHz",
                    onValueChange = { },
                    label = { Text("Sample Rate") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
        Card(modifier = Modifier.fillMaxWidth()) {
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

                OutlinedTextField(
                    value = performanceMode,
                    onValueChange = { performanceMode = it },
                    label = { Text("Performance Mode") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Mode")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // App Info
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ListItem(headlineContent = { Text("Version") }, supportingContent = { Text("1.0.0") })
                ListItem(headlineContent = { Text("Build") }, supportingContent = { Text("2026.02") })
            }
        }
    }
}

/**
 * Sample editor panel — placeholder (full editor is in SampleEditScreen).
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
            text = "Long-press a pad after assigning a sample to edit trim and fade.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
