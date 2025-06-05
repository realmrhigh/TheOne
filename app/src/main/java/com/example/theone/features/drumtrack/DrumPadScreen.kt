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
// Use the main interfaces from 'audio' and 'domain' packages
import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
// SamplerPlaybackMode and SamplerEnvelopeSettings are specific types expected by the playPadSample method in AudioEngineControl
import com.example.theone.features.sampler.PlaybackMode as SamplerPlaybackMode
import com.example.theone.features.sampler.SamplerViewModel.EnvelopeSettings as SamplerEnvelopeSettings
import com.example.theone.model.SampleMetadata // Common model for stopAudioRecording
import android.content.Context // For startAudioRecording in PreviewAudioEngineControl
// data class LFOSettingsPreviewStub(val id: String = "dummyLfo") // Not using this for List<Any>


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
// Dummy AudioEngineControl for preview implementing the main ::audio::AudioEngineControl
private class PreviewAudioEngineControl(private val context: Context) : AudioEngineControl {
    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean { println("Preview: init"); return true }
    override suspend fun shutdown() { println("Preview: shutdown") }
    override fun isInitialized(): Boolean = true
    override fun getReportedLatencyMillis(): Float = 10f

    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean { println("Preview: load $sampleId"); return true }
    // This overload is not in AudioEngineControl, but often useful for implementations.
    // For a strict preview of the interface, this would be removed or not have override.
    // However, if AudioEngine (concrete) has it, and VM uses it via cast, it can be here without override.
    /*override*/ suspend fun loadSampleToMemory(context: Context, sampleId: String, filePathUri: String): Boolean { println("Preview: load $sampleId with context"); return true }
    override suspend fun unloadSample(sampleId: String) { println("Preview: unload $sampleId") }
    override fun isSampleLoaded(sampleId: String): Boolean = true

    override suspend fun startAudioRecording(context: Context, filePathUri: String, sampleRate: Int, channels: Int, inputDeviceId: String?): Boolean { println("Preview: start rec $filePathUri"); return true }
    override suspend fun stopAudioRecording(): com.example.theone.model.SampleMetadata? { println("Preview: stop rec"); return null } // Use the common model type
    override fun isRecordingActive(): Boolean = false
    override fun getRecordingLevelPeak(): Float = 0.0f

    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean { println("Preview: play $sampleId"); return true }

    override suspend fun playPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String,
        sliceId: String?, velocity: Float,
        playbackMode: SamplerPlaybackMode, // This is com.example.theone.features.sampler.PlaybackMode
        coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        ampEnv: SamplerEnvelopeSettings, // This is com.example.theone.features.sampler.SamplerViewModel.EnvelopeSettings
        filterEnv: SamplerEnvelopeSettings?,
        pitchEnv: SamplerEnvelopeSettings?,
        lfos: List<Any> // Interface expects List<Any>
    ): Boolean {
        println("Preview: Play $sampleId on $padId ($playbackMode), amp attack: ${ampEnv.attackMs}")
        return true
    }

    override suspend fun playSampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, trimStartMs: Long, trimEndMs: Long, loopStartMs: Long?, loopEndMs: Long?, isLooping: Boolean): Boolean { println("Preview: play slice $sampleId"); return true}

    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, primarySoundSampleId: String, secondarySoundSampleId: String?) { println("Preview: metronome state $isEnabled") }
    override suspend fun setMetronomeVolume(volume: Float) { println("Preview: metronome vol $volume") }
}

// Dummy ProjectManager for preview implementing the main ::domain::ProjectManager
private class PreviewProjectManager : ProjectManager {
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): com.example.theone.model.SampleMetadata? { println("Preview: add sample $name"); return null } // Use common model
    override suspend fun updateSampleMetadata(sample: com.example.theone.model.SampleMetadata): Boolean { println("Preview: update $sample.id"); return true }
    override suspend fun getSampleById(sampleId: String): com.example.theone.model.SampleMetadata? { println("Preview: get $sampleId"); return null }
    // Add getAllSamplesInPool if DrumTrackViewModel uses it, for now it simulates its own list
    // suspend fun getAllSamplesInPool(): List<com.example.theone.model.SampleMetadata> = emptyList()
}


@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun DefaultDrumPadScreenPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewAudioEngine = PreviewAudioEngineControl(context)
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
