package com.example.theone.features.drumtrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.features.drumtrack.model.SampleMetadata
// Assuming DrumTrackViewModel and related models are in this package or accessible

// These are needed for the Preview stubs.
// Ideally, these would come from a common module if they were fully defined.
// For now, using the sampler package versions as placeholders.
import com.example.theone.features.sampler.AudioEngineControl
import com.example.theone.features.sampler.ProjectManager
import com.example.theone.features.sampler.PlaybackMode as SamplerPlaybackMode
import com.example.theone.features.sampler.SamplerViewModel // For SamplerViewModel.SampleMetadata & .LFOSettings in preview stubs

// Local placeholder for LFOSettings if not defined, for PreviewAudioEngineControl
// This should ideally come from a shared module or be properly defined in SamplerViewModel if that's its origin
data class LFOSettingsPreviewStub(val id: String = "dummyLfo")


@Composable
fun DrumPadScreen(
    drumTrackViewModel: DrumTrackViewModel = viewModel() // Placeholder for Hilt
) {
    val drumTrackState by drumTrackViewModel.drumTrack.collectAsState()
    val availableSamples by drumTrackViewModel.availableSamples.collectAsState()
    val userMessage by drumTrackViewModel.userMessage.collectAsState()

    var showAssignSampleDialog by remember { mutableStateOf(false) }
    var selectedPadForAssignment by remember { mutableStateOf<PadSettings?>(null) }

    val scaffoldState = rememberScaffoldState()

    LaunchedEffect(userMessage) {
        userMessage?.let {
            scaffoldState.snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            drumTrackViewModel.consumedUserMessage()
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(title = { Text("Drum Pads (M1.2) - ${drumTrackState.name}") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(8.dp)
                .fillMaxSize()
        ) {
            // Pad Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), // 4x4 grid
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drumTrackState.pads, key = { it.id }) { pad ->
                    PadView(
                        padSettings = pad,
                        onPadClick = { drumTrackViewModel.onPadTriggered(pad.id) },
                        onAssignSampleClick = {
                            selectedPadForAssignment = pad
                            showAssignSampleDialog = true
                        }
                    )
                }
            }

            // TODO: Add controls for track volume, pan later if needed for M1.2
            // For now, focus is on pad playback and assignment.
        }
    }

    if (showAssignSampleDialog && selectedPadForAssignment != null) {
        AssignSampleDialog(
            pad = selectedPadForAssignment!!,
            availableSamples = availableSamples,
            onDismiss = { showAssignSampleDialog = false; selectedPadForAssignment = null },
            onAssignSample = { pad, sample ->
                drumTrackViewModel.assignSampleToPad(pad.id, sample)
                showAssignSampleDialog = false
                selectedPadForAssignment = null
            },
            onClearSample = { pad ->
                drumTrackViewModel.clearSampleFromPad(pad.id)
                showAssignSampleDialog = false
                selectedPadForAssignment = null
            }
        )
    }
}

@Composable
fun PadView(
    padSettings: PadSettings,
    onPadClick: () -> Unit,
    onAssignSampleClick: () -> Unit
) {
    Button(
        onClick = onPadClick,
        modifier = Modifier
            .aspectRatio(1f) // Make it square
            .padding(2.dp),
        elevation = ButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (padSettings.sampleId != null) MaterialTheme.colors.primaryVariant else MaterialTheme.colors.surface
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = padSettings.sampleName ?: padSettings.id,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption,
                color = if (padSettings.sampleId != null) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
            )
            // Icon to trigger assignment dialog
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                 Icon(
                    imageVector = Icons.Default.AddCircle, // Or a settings icon
                    contentDescription = "Assign Sample",
                    modifier = Modifier.size(20.dp).clickable(onClick = onAssignSampleClick),
                    tint = if (padSettings.sampleId != null) Color.White.copy(alpha=0.7f) else Color.Gray.copy(alpha=0.7f)
                )
            }
        }
    }
}

@Composable
fun AssignSampleDialog(
    pad: PadSettings,
    availableSamples: List<SampleMetadata>,
    onDismiss: () -> Unit,
    onAssignSample: (PadSettings, SampleMetadata) -> Unit,
    onClearSample: (PadSettings) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Sample to ${pad.id}") },
        text = {
            if (availableSamples.isEmpty()) {
                Text("No samples available in the pool. Record some samples first using M1.1 Sampler.")
            } else {
                Column {
                    availableSamples.forEach { sample ->
                        Text(
                            text = sample.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssignSample(pad, sample) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        buttons = {
             Row(
                modifier = Modifier.padding(all = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pad.sampleId != null) {
                    Button(
                        onClick = { onClearSample(pad) }
                    ) {
                        Text("Clear Sample")
                        Icon(Icons.Default.Clear, contentDescription = "Clear Sample")
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f)) // Keep dismiss to the right
                }
                Button(
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}


// --- Preview ---
// Dummy AudioEngineControl for preview
private class PreviewAudioEngineControl : AudioEngineControl {
    override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?) = true
    // Changed to use SamplerViewModel.SampleMetadata as per its definition in SamplerViewModel
    override suspend fun stopAudioRecording(): SamplerViewModel.SampleMetadata? = null
    override fun getRecordingLevelPeak() = 0f
    override fun isRecordingActive() = false
    // Changed playSample to match a potential signature used by SamplerViewModel (if any was defined)
    // For DrumPadScreen preview, this specific signature might not be critical unless called directly.
    override suspend fun playSample(sampleId: String, /* other params for playback */): Boolean {
        println("Preview: Play Sample $sampleId")
        return true
    }

    // Implement the complex playPadSample with default behavior for preview
    // Using SamplerViewModel.EnvelopeSettings and SamplerPlaybackMode
    override suspend fun playPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String,
        sliceId: String?, velocity: Float, playbackMode: SamplerPlaybackMode,
        coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        ampEnv: com.example.theone.features.drumtrack.EnvelopeSettings, // Corrected to use the local one from DrumTrackVM
        filterEnv: com.example.theone.features.drumtrack.EnvelopeSettings?,
        pitchEnv: com.example.theone.features.drumtrack.EnvelopeSettings?,
        lfos: List<LFOSettingsPreviewStub> // Using local LFOSettingsPreviewStub
    ): Boolean {
        println("Preview: Play $sampleId on $padId using $playbackMode")
        return true
    }
}

// Dummy ProjectManager for preview
private class PreviewProjectManager : ProjectManager {
    // Changed to use SamplerViewModel.SampleMetadata as per its definition in SamplerViewModel for consistency with AudioEngineControl stub
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SamplerViewModel.SampleMetadata? = null
}


@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun DefaultDrumPadScreenPreview() {
    val previewAudioEngine = PreviewAudioEngineControl()
    val previewProjectManager = PreviewProjectManager()
    val drumTrackViewModel = DrumTrackViewModel(previewAudioEngine, previewProjectManager)

    // Simulate some samples for assignment in preview
    LaunchedEffect(Unit) {
        drumTrackViewModel.fetchAvailableSamples() // To populate the simulated list
         // Assign a sample to a pad for visual preview
        val samples = drumTrackViewModel.availableSamples.value
        if (samples.isNotEmpty()) {
            drumTrackViewModel.assignSampleToPad("Pad1", samples[0])
        }
        if (samples.size > 1) {
            drumTrackViewModel.assignSampleToPad("Pad2", samples[1])
        }
    }

    MaterialTheme {
        DrumPadScreen(drumTrackViewModel = drumTrackViewModel)
    }
}
