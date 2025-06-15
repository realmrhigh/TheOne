package com.example.theone.features.sampleeditor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.theone.model.LoopMode
import com.example.theone.model.PlaybackMode
import com.example.theone.model.SampleMetadata
import com.example.theone.model.SynthModels.EffectSetting
import com.example.theone.model.SynthModels.EnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings
import com.example.theone.model.SynthModels.ModulationRouting
import java.util.UUID

// Mock AudioEngineControl for preview
class MockAudioEngineControl : com.example.theone.audio.AudioEngineControl {
    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean = true
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean = true // This is on interface
    // This overload is not in AudioEngineControl interface, so remove override
    suspend fun loadSampleToMemory(context: android.content.Context, sampleId: String, filePathUri: String): Boolean = true
    override suspend fun unloadSample(sampleId: String) {}
    override fun isSampleLoaded(sampleId: String): Boolean = true
    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean = true

    // Add missing interface methods
    override suspend fun playPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String,
        sliceId: String?, velocity: Float,
        playbackMode: com.example.theone.model.PlaybackMode, // Corrected type
        coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        ampEnv: com.example.theone.model.SynthModels.EnvelopeSettings, // Corrected type
        filterEnv: com.example.theone.model.SynthModels.EnvelopeSettings?, // Corrected type
        pitchEnv: com.example.theone.model.SynthModels.EnvelopeSettings?, // Corrected type
        lfos: List<Any> // Matches interface
    ): Boolean = true

    override suspend fun playSampleSlice(
        sampleId: String, noteInstanceId: String, volume: Float, pan: Float,
        trimStartMs: Long, trimEndMs: Long,
        loopStartMs: Long?, loopEndMs: Long?,
        isLooping: Boolean
    ): Boolean = true

    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, primarySoundSampleId: String, secondarySoundSampleId: String?) {}
    override suspend fun setMetronomeVolume(volume: Float) {}
    override suspend fun startAudioRecording(context: android.content.Context, filePathUri: String, sampleRate: Int, channels: Int, inputDeviceId: String?): Boolean = true
    override suspend fun stopAudioRecording(): com.example.theone.model.SampleMetadata? = null // Use com.example.theone.model.SampleMetadata
    override fun isRecordingActive(): Boolean = false
    override fun getRecordingLevelPeak(): Float = 0.0f
    override suspend fun shutdown() {}
    override fun isInitialized(): Boolean = true
    override fun getReportedLatencyMillis(): Float = 0.0f
}

// Mock ProjectManager for preview
class MockProjectManager : com.example.theone.domain.ProjectManager {
    private val samples = mutableMapOf<String, SampleMetadata>()
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val id = UUID.randomUUID().toString()
        val meta = SampleMetadata(id, name, sourceFileUri, 1000L, 44100, 1)
        samples[id] = meta
        return meta
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        samples[sample.id] = sample
        return true
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? = samples[sampleId]
}

@Composable
fun SampleEditScreen(viewModel: SampleEditViewModel) {
    val sampleMetadata by viewModel.currentSample.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Local states for text fields to allow user typing before parsing
    var trimStartMsString by remember(sampleMetadata.trimStartMs) { mutableStateOf(sampleMetadata.trimStartMs.toString()) }
    var trimEndMsString by remember(sampleMetadata.trimEndMs) { mutableStateOf(sampleMetadata.trimEndMs.toString()) }
    var loopStartMsString by remember(sampleMetadata.loopStartMs) { mutableStateOf(sampleMetadata.loopStartMs?.toString() ?: "") }
    var loopEndMsString by remember(sampleMetadata.loopEndMs) { mutableStateOf(sampleMetadata.loopEndMs?.toString() ?: "") }

    LaunchedEffect(userMessage) {
        // Optionally, auto-dismiss message after some time
        // if (userMessage != null) {
        //     kotlinx.coroutines.delay(3000)
        //     viewModel.consumedUserMessage()
        // }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Edit Sample: ${sampleMetadata.name}") })
        },
        snackbarHost = {
            SnackbarHost(hostState = LocalSnackbarHostState.current) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Sample: ${sampleMetadata.name}", style = MaterialTheme.typography.titleMedium)
            Text("Duration: ${sampleMetadata.durationMs} ms")
            Text("Effective Duration (Trimmed): ${sampleMetadata.getEffectiveDuration()} ms")

            // Waveform Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .border(1.dp, Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("Waveform Display Area (C4)")
            }

            // Trim Points
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = trimStartMsString,
                    onValueChange = { trimStartMsString = it },
                    label = { Text("Trim Start (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = trimEndMsString,
                    onValueChange = { trimEndMsString = it },
                    label = { Text("Trim End (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    val start = trimStartMsString.toLongOrNull() ?: 0L
                    val end = trimEndMsString.toLongOrNull() ?: sampleMetadata.durationMs
                    viewModel.updateTrimPoints(start, end)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Trim Points")
            }

            Divider()

            // Loop Mode
            Text("Loop Mode", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoopMode.values().forEach { mode ->
                    Button(
                        onClick = { viewModel.setLoopMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sampleMetadata.loopMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(mode.name)
                    }
                }
            }

            // Loop Points (enabled only if loop mode is not NONE)
            val loopControlsEnabled = sampleMetadata.loopMode != LoopMode.NONE
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = loopStartMsString,
                    onValueChange = { loopStartMsString = it },
                    label = { Text("Loop Start (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = loopControlsEnabled
                )
                OutlinedTextField(
                    value = loopEndMsString,
                    onValueChange = { loopEndMsString = it },
                    label = { Text("Loop End (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = loopControlsEnabled
                )
            }
             Button(
                onClick = {
                    val start = loopStartMsString.toLongOrNull()
                    val end = loopEndMsString.toLongOrNull()
                    viewModel.updateLoopPoints(start, end)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loopControlsEnabled
            ) {
                Text("Apply Loop Points")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.auditionSelection() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Audition")
                }
                Button(
                    onClick = { viewModel.saveChanges() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Save Changes")
                }
            }

            userMessage?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Failed")) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LaunchedEffect(it) { // Allows message to be re-displayed if it changes
                     // You might want a Snackbar here instead for non-modal messages
                     // For now, just clear it after a delay or let user clear it
                     // viewModel.consumedUserMessage() // Or handled by a dismiss button
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSampleEditScreen() {
    val mockAudioEngine = MockAudioEngineControl()
    val mockProjectManager = MockProjectManager()
    val initialSample = SampleMetadata(
        id = "previewSample1",
        name = "Kick Drum",
        uri = "file:///dev/null/kick.wav", // Changed from filePathUri
        duration = 1200L,                   // Changed from durationMs
        sampleRate = 44100,
        channels = 1,
        trimStartMs = 100L,
        trimEndMs = 1000L
        // loopMode is not part of SampleMetadata
    )
    // Populate mock project manager for preview
    LaunchedEffect(Unit) {
        mockProjectManager.addSampleToPool(initialSample.name, initialSample.uri, false) // use uri
    }

    val viewModel = SampleEditViewModel(mockAudioEngine, mockProjectManager, initialSample)

    // Simulate a user message for preview
    // LaunchedEffect(Unit) {
    //     kotlinx.coroutines.delay(1000)
    //     viewModel.forceUserMessageForPreview("Preview: Sample loaded for editing.")
    // }


    MaterialTheme { // Replace with your app's theme if it's different
        SampleEditScreen(viewModel = viewModel)
    }
}
