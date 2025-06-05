package com.example.theone.features.sampler

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel // Required for viewModel()

// Assuming SamplerViewModel is in the same package
// Assuming RecordingState is accessible

@Composable
fun SamplerScreen(samplerViewModel: SamplerViewModel = viewModel()) { // Placeholder for Hilt injection later

    val recordingState by samplerViewModel.recordingState.collectAsState()
    val inputLevel by samplerViewModel.inputLevel.collectAsState() // Simulated input level
    val isThresholdEnabled by samplerViewModel.isThresholdRecordingEnabled.collectAsState()
    val thresholdValue by samplerViewModel.thresholdValue.collectAsState()

    var showNameSampleDialog by remember { mutableStateOf(false) }
    var sampleName by remember { mutableStateOf("") }
    var showAssignToPadDialog by remember { mutableStateOf(false) }
    var selectedPadId by remember { mutableStateOf<String?>(null) }


    // Update dialog visibility based on ViewModel state
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.REVIEWING) {
            showNameSampleDialog = true
        } else {
            showNameSampleDialog = false
            showAssignToPadDialog = false // Also reset assign dialog if not reviewing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sampler (M1.1)") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Input Source Selection (Placeholder)
                Text("Input Source: Mic (Placeholder)", style = MaterialTheme.typography.subtitle1)
                Spacer(modifier = Modifier.height(16.dp))

                // Live Input Level Meter
                LinearProgressIndicator(
                    progress = inputLevel, // ViewModel will provide this
                    modifier = Modifier.fillMaxWidth()
                )
                Text(String.format("Input Level: %.2f", inputLevel), style = MaterialTheme.typography.caption)
                Spacer(modifier = Modifier.height(16.dp))

                // Threshold Recording
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isThresholdEnabled,
                        onCheckedChange = { samplerViewModel.toggleThresholdRecording(it) }
                    )
                    Text("Threshold Recording")
                }
                if (isThresholdEnabled) {
                    Slider(
                        value = thresholdValue,
                        onValueChange = { samplerViewModel.setThresholdValue(it) },
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(String.format("Threshold: %.2f", thresholdValue))
                }
            }


            // Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Spacer(modifier = Modifier.height(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { samplerViewModel.startRecordingPressed() },
                        enabled = recordingState == RecordingState.IDLE || recordingState == RecordingState.REVIEWING
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Record")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (recordingState == RecordingState.ARMED) "Armed" else "Record")
                    }

                    Button(
                        onClick = { samplerViewModel.stopRecordingPressed() },
                        enabled = recordingState == RecordingState.RECORDING || recordingState == RecordingState.ARMED
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Stop")
                    }
                }
                 Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { samplerViewModel.playbackLastRecordingPressed() },
                    enabled = recordingState == RecordingState.REVIEWING
                ) {
                    Text("Playback Last Recording")
                }
            }


            if (showNameSampleDialog && recordingState == RecordingState.REVIEWING) {
                NameSampleDialog(
                    currentName = sampleName,
                    onNameChange = { sampleName = it },
                    onDismiss = {
                        showNameSampleDialog = false
                        samplerViewModel.discardRecording() // Or handle differently
                    },
                    onSave = { finalName ->
                        showNameSampleDialog = false
                        sampleName = finalName // update local state if needed
                        // After naming, decide if we show assign to pad or just save
                        showAssignToPadDialog = true // Let's assume we always ask for pad assignment next
                    }
                )
            }

            if (showAssignToPadDialog && recordingState == RecordingState.REVIEWING) {
                AssignToPadDialog(
                    onDismiss = {
                        showAssignToPadDialog = false
                        // If they dismiss pad assignment, still save with the name
                        samplerViewModel.saveRecording(sampleName, null)
                        sampleName = "" // Reset for next time
                    },
                    onAssign = { padId ->
                        showAssignToPadDialog = false
                        samplerViewModel.saveRecording(sampleName, padId)
                        sampleName = "" // Reset for next time
                    },
                    onSkip = {
                         showAssignToPadDialog = false
                         samplerViewModel.saveRecording(sampleName, null) // Save without assigning
                         sampleName = "" // Reset for next time
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp)) // Pushes controls up a bit
        }
    }
}

@Composable
fun NameSampleDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var tempName by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name Your Sample") },
        text = {
            OutlinedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text("Sample Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                if (tempName.isNotBlank()) {
                    onSave(tempName)
                }
            }) {
                Text("Save & Assign Pad")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Discard")
            }
        }
    )
}

@Composable
fun AssignToPadDialog(
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit,
    onSkip: () -> Unit
) {
    // Placeholder for pad selection. In a real app, this would be a grid or list of pads.
    val padOptions = List(16) { "Pad ${it + 1}" } // Example: Pad 1 to Pad 16
    var selectedPad by remember { mutableStateOf(padOptions[0]) }

    AlertDialog(
        onDismissRequest = onDismiss, // User clicked outside
        title = { Text("Assign to Pad (Optional)") },
        text = {
            Column {
                Text("Select a pad to assign this sample to:")
                Spacer(modifier = Modifier.height(8.dp))
                // Simple dropdown for now
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = selectedPad,
                        onValueChange = { },
                        label = { Text("Pad") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        padOptions.forEach { selectionOption ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedPad = selectionOption
                                    expanded = false
                                }
                            ) {
                                Text(text = selectionOption)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAssign(selectedPad) }) {
                Text("Assign and Save")
            }
        },
        dismissButton = {
            Button(onClick = onSkip ) { // Changed to "Skip" for clarity
                Text("Save without Assigning")
            }
        },
         neutralButton = { // Added a neutral button for true dismiss/cancel
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// Preview for SamplerScreen
@Preview(showBackground = true)
@Composable
fun DefaultSamplerScreenPreview() {
    // Create a dummy SamplerViewModel for preview
    // This requires AudioEngineControl and ProjectManager stubs for the preview
    val dummyAudioEngine = object : AudioEngineControl {
        override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?) = true
        override suspend fun stopAudioRecording(): SamplerViewModel.SampleMetadata? = SamplerViewModel.SampleMetadata("prev_id", "PreviewSample", "file://preview")
        override fun getRecordingLevelPeak()= 0.5f
        override fun isRecordingActive() = false
    }
    val dummyProjectManager = object : ProjectManager {
        override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean) = SamplerViewModel.SampleMetadata("new_id", name, sourceFileUri)
    }
    val dummyViewModel = SamplerViewModel(dummyAudioEngine, dummyProjectManager)

    MaterialTheme { // Ensure a MaterialTheme is applied for previews
        SamplerScreen(samplerViewModel = dummyViewModel)
    }
}
