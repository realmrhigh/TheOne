package com.example.theone.features.sampleeditor

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
import com.example.theone.model.LoopMode
import com.example.theone.model.SampleMetadata

@Composable
fun SampleEditScreen(viewModel: SampleEditViewModel) {
    val currentSample by viewModel.currentSample.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Edit Sample: ${currentSample.name}") })
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text("Duration: ${currentSample.durationMs}ms")
                Text("Effective Duration: ${currentSample.getEffectiveDuration()}ms")
                Spacer(modifier = Modifier.height(16.dp))

                // Trim Points
                var trimStart by remember { mutableStateOf(currentSample.trimStartMs) }
                var trimEnd by remember { mutableStateOf(currentSample.trimEndMs) }
                RangeSlider(
                    value = trimStart..trimEnd,
                    onValueChange = { range ->
                        trimStart = range.start
                        trimEnd = range.end
                    },
                    valueRange = 0f..currentSample.durationMs.toFloat(),
                    onValueChangeFinished = {
                        viewModel.updateTrimPoints(trimStart, trimEnd)
                    }
                )
                Text("Trim: ${trimStart.toInt()}ms - ${trimEnd.toInt()}ms")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.auditionSelection(trimStart, trimEnd) }) {
                    Text("Audition Trim")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))


                // Loop Mode Dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = currentSample.loopMode.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loop Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        LoopMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    viewModel.setLoopMode(mode)
                                    expanded = false
                                }
                            )
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.saveChanges() }) {
                    Text("Save Changes")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SampleEditScreenPreview() {
    val previewSample = SampleMetadata(
        id = "1",
        name = "preview_kick.wav",
        uri = Uri.EMPTY,
        durationMs = 1000L
    )
    val viewModel = SampleEditViewModel(initialSampleMetadata = previewSample)
    SampleEditScreen(viewModel = viewModel)
}

// Keep the mock classes for now if they are used in other previews,
// but they are not used for the SampleEditScreen preview itself.
class MockAudioEngineControl : AudioEngineControl {
    override fun loadSample(filePath: String): Int = 0
    override fun playSample(sampleId: Int) {}
    override fun stopSample(sampleId: Int) {}
    override fun unloadSample(sampleId: Int) {}
    override fun setSampleVolume(sampleId: Int, volume: Float) {}
    override fun setSampleLooping(sampleId: Int, isLooping: Boolean) {}
    override fun loadSequenceData(sequenceData: ByteArray) {}
}

class MockProjectManager : ProjectManager {
    override suspend fun createNewProject(name: String) {}
    override suspend fun loadProject(uri: String) {}
    override suspend fun saveProject() {}
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata {
        return SampleMetadata("id", name, Uri.parse(sourceFileUri), 0)
    }
}
