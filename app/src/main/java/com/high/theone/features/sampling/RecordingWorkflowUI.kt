package com.high.theone.features.sampling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.AudioInputSource
import com.high.theone.model.RecordingState

/**
 * Recording preparation screen with settings and configuration options.
 * Allows users to configure recording parameters before starting.
 * 
 * Requirements: 1.6 (recording configuration)
 */
@Composable
fun RecordingPreparationScreen(
    recordingState: RecordingState,
    onSampleRateChange: (Int) -> Unit,
    onChannelsChange: (Int) -> Unit,
    onMaxDurationChange: (Int) -> Unit,
    onInputSourceChange: (AudioInputSource) -> Unit,
    onStartRecording: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recording Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel"
                )
            }
        }
        
        HorizontalDivider()
        
        // Audio Quality Settings
        AudioQualitySettings(
            recordingState = recordingState,
            onSampleRateChange = onSampleRateChange,
            onChannelsChange = onChannelsChange
        )
        
        // Recording Duration Settings
        RecordingDurationSettings(
            maxDurationSeconds = recordingState.maxDurationSeconds,
            onMaxDurationChange = onMaxDurationChange
        )
        
        // Input Source Settings
        InputSourceSettings(
            currentSource = recordingState.inputSource,
            onInputSourceChange = onInputSourceChange
        )
        
        // Recording Status
        RecordingStatusCard(recordingState = recordingState)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = onStartRecording,
                enabled = recordingState.canStartRecording,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording")
            }
        }
    }
}

/**
 * Post-recording actions screen for save, discard, or retry operations.
 * 
 * Requirements: 1.6 (post-recording workflow)
 */
@Composable
fun PostRecordingActions(
    recordingState: RecordingState,
    sampleName: String,
    onSampleNameChange: (String) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Recording Complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // Recording Info
            RecordingInfoSummary(recordingState = recordingState)
            
            // Sample Naming
            SampleNamingSection(
                sampleName = sampleName,
                onSampleNameChange = onSampleNameChange
            )
            
            // Sample Tagging
            SampleTaggingSection(
                tags = tags,
                onTagsChange = onTagsChange
            )
            
            HorizontalDivider()
            
            // Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary action - Save
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sampleName.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Sample")
                }
                
                // Secondary actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                    
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard")
                    }
                }
            }
        }
    }
}

/**
 * Audio quality settings section
 */
@Composable
private fun AudioQualitySettings(
    recordingState: RecordingState,
    onSampleRateChange: (Int) -> Unit,
    onChannelsChange: (Int) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Audio Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Sample Rate Selection
            Text(
                text = "Sample Rate",
                style = MaterialTheme.typography.labelLarge
            )
            
            val sampleRates = listOf(22050, 44100, 48000)
            Column(modifier = Modifier.selectableGroup()) {
                sampleRates.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = recordingState.sampleRate == rate,
                                onClick = { onSampleRateChange(rate) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = recordingState.sampleRate == rate,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${rate} Hz",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Channels Selection
            Text(
                text = "Channels",
                style = MaterialTheme.typography.labelLarge
            )
            
            Column(modifier = Modifier.selectableGroup()) {
                listOf(1 to "Mono", 2 to "Stereo").forEach { (channels, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = recordingState.channels == channels,
                                onClick = { onChannelsChange(channels) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = recordingState.channels == channels,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recording duration settings section
 */
@Composable
private fun RecordingDurationSettings(
    maxDurationSeconds: Int,
    onMaxDurationChange: (Int) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recording Duration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "Maximum recording length: ${maxDurationSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = maxDurationSeconds.toFloat(),
                onValueChange = { onMaxDurationChange(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22, // 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 70, 80, 90, 100, 110, 120
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "5s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "2m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Input source settings section
 */
@Composable
private fun InputSourceSettings(
    currentSource: AudioInputSource,
    onInputSourceChange: (AudioInputSource) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Input Source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Column(modifier = Modifier.selectableGroup()) {
                AudioInputSource.entries.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = currentSource == source,
                                onClick = { onInputSourceChange(source) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSource == source,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (source) {
                                AudioInputSource.MICROPHONE -> "Microphone"
                                AudioInputSource.LINE_IN -> "Line In"
                                AudioInputSource.USB_AUDIO -> "USB Audio"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recording status information card
 */
@Composable
private fun RecordingStatusCard(
    recordingState: RecordingState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (recordingState.canStartRecording) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            val statusText = when {
                !recordingState.isInitialized -> "Audio engine not initialized"
                recordingState.error != null -> "Error: ${recordingState.error}"
                recordingState.isRecording -> "Currently recording"
                recordingState.isProcessing -> "Processing previous recording"
                else -> "Ready to record"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Recording information summary
 */
@Composable
private fun RecordingInfoSummary(
    recordingState: RecordingState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duration:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = recordingState.formattedDuration,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quality:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${recordingState.sampleRate}Hz, ${if (recordingState.channels == 1) "Mono" else "Stereo"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Sample naming section
 */
@Composable
private fun SampleNamingSection(
    sampleName: String,
    onSampleNameChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sample Name",
            style = MaterialTheme.typography.labelLarge
        )
        
        OutlinedTextField(
            value = sampleName,
            onValueChange = onSampleNameChange,
            placeholder = { Text("Enter sample name...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/**
 * Sample tagging section
 */
@Composable
private fun SampleTaggingSection(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Tags (Optional)",
            style = MaterialTheme.typography.labelLarge
        )
        
        // Tag input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                placeholder = { Text("Add tag...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            
            Button(
                onClick = {
                    if (newTag.isNotBlank() && !tags.contains(newTag.trim())) {
                        onTagsChange(tags.plus(newTag.trim()))
                        newTag = ""
                    }
                },
                enabled = newTag.isNotBlank()
            ) {
                Text("Add")
            }
        }
        
        // Display existing tags
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        onClick = { onTagsChange(tags.minus(tag)) },
                        label = { Text(tag) },
                        selected = false,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}