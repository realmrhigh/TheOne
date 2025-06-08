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
import com.example.theone.model.LoopMode // Keep if still used, otherwise remove
import com.example.theone.model.SampleMetadata
import com.example.theone.model.Sample // Added for currentSample
import com.example.theone.model.PlaybackMode
import com.example.theone.model.EnvelopeSettings
import java.util.UUID
import com.example.theone.ui.theme.TheONETheme // Assuming this is your theme

// Mock AudioEngineControl for preview - Kept from existing, ensure it's compatible
class MockAudioEngineControl : com.example.theone.audio.AudioEngineControl {
    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean = true
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean = true
    suspend fun loadSampleToMemory(context: android.content.Context, sampleId: String, filePathUri: String): Boolean = true
    override suspend fun unloadSample(sampleId: String) {}
    override fun isSampleLoaded(sampleId: String): Boolean = true
    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean = true
    override suspend fun playPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String,
        sliceId: String?, velocity: Float,
        playbackMode: PlaybackMode,
        coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        ampEnv: EnvelopeSettings,
        filterEnv: EnvelopeSettings?,
        pitchEnv: EnvelopeSettings?,
        lfos: List<Any>
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
    override suspend fun stopAudioRecording(): SampleMetadata? = null
    override fun isRecordingActive(): Boolean = false
    override fun getRecordingLevelPeak(): Float = 0.0f
    override suspend fun shutdown() {}
    override fun isInitialized(): Boolean = true
    override fun getReportedLatencyMillis(): Float = 0.0f
}

// Mock ProjectManager for preview - Modified to support new preview
open class MockProjectManager : com.example.theone.domain.ProjectManager {
    protected val metadataSamples = mutableMapOf<String, SampleMetadata>()
    protected val fullSamplesByUri = mutableMapOf<String, Sample>()

    // For StateFlow<List<SampleMetadata>>
    private val _samplePoolFlow = MutableStateFlow<List<SampleMetadata>>(emptyList())
    override val samplePool: StateFlow<List<SampleMetadata>> = _samplePoolFlow.asStateFlow()

    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        val id = UUID.randomUUID().toString()
        // Use the full SampleMetadata constructor
        val meta = SampleMetadata(uri = sourceFileUri, duration = 1000L, name = name, sampleRate = 44100, channels = 1, bitDepth = 16)
        metadataSamples[id] = meta // Assuming id is uri for this mock, or manage mapping
         _samplePoolFlow.value = metadataSamples.values.toList()
        return meta
    }
    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        if (metadataSamples.containsKey(sample.uri)) { // Assuming uri is the key
            metadataSamples[sample.uri] = sample
            _samplePoolFlow.value = metadataSamples.values.toList()
            return true
        }
        return false
    }
    override suspend fun getSampleById(sampleId: String): SampleMetadata? = metadataSamples[sampleId] // Assuming sampleId is uri

    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        metadataSamples[sampleMetadata.uri] = sampleMetadata // Assuming uri is the key
         _samplePoolFlow.value = metadataSamples.values.toList()
    }
    override fun getSamplesFromPool(): List<SampleMetadata> = metadataSamples.values.toList()

    override suspend fun loadWavFile(fileUri: String): Result<Sample, Error> {
        return fullSamplesByUri[fileUri]?.let { Result.success(it) }
            ?: Result.failure(Error("Mock: Sample not found for URI $fileUri"))
    }
    override suspend fun saveWavFile(sample: Sample, fileUri: String): Result<Unit, Error> {
        println("MockProjectManager: saveWavFile called for ${sample.id} to $fileUri")
        fullSamplesByUri[fileUri] = sample // Store it for potential reload in mock
        metadataSamples[sample.metadata.uri] = sample.metadata // Ensure metadata is also in pool
        _samplePoolFlow.value = metadataSamples.values.toList()
        return Result.success(Unit)
    }

    // Helper for preview
    fun addPreviewSample(uri: String, sample: Sample) {
        fullSamplesByUri[uri] = sample
        metadataSamples[sample.metadata.uri] = sample.metadata
         _samplePoolFlow.value = metadataSamples.values.toList()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleEditScreen(viewModel: SampleEditViewModel) {
    val currentSample by viewModel.currentSample.collectAsState() // Now Sample?
    val userMessage by viewModel.userMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Local states for text fields, initialized when currentSample is available
    var trimStartMsString by remember(currentSample?.metadata?.trimStartMs) {
        mutableStateOf(currentSample?.metadata?.trimStartMs?.toString() ?: "0")
    }
    var trimEndMsString by remember(currentSample?.metadata?.trimEndMs) {
        mutableStateOf(currentSample?.metadata?.trimEndMs?.toString() ?: currentSample?.metadata?.duration?.toString() ?: "0")
    }
    // Loop point strings - keep if loop UI is present, for now commenting out as it's not in the diff's focus
    // var loopStartMsString by remember(currentSample?.metadata?.loopStartMs) { mutableStateOf(currentSample?.metadata?.loopStartMs?.toString() ?: "") }
    // var loopEndMsString by remember(currentSample?.metadata?.loopEndMs) { mutableStateOf(currentSample?.metadata?.loopEndMs?.toString() ?: "") }


    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.consumedUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(currentSample?.metadata?.name?.let { "Edit: $it" } ?: "Edit Sample") })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()) // Ensure content is scrollable
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (currentSample == null) {
                Text("Sample not loaded or error.", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // Safe to use currentSample!! here or use currentSample?.let { sample -> ... }
                val sample = currentSample!!
                val metadata = sample.metadata

                Text("Sample: ${metadata.name}", style = MaterialTheme.typography.titleMedium)
                Text("Duration: ${metadata.duration} ms")
                Text("Effective Duration (Trimmed): ${metadata.trimEndMs - metadata.trimStartMs} ms")

                WaveformDisplay( // WaveformDisplay composable added here
                    sample = sample,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp) // Increased height
                        .border(1.dp, Color.DarkGray),
                    waveformColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant // Use theme colors
                )

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
                        val end = trimEndMsString.toLongOrNull() ?: metadata.duration
                        viewModel.updateTrimPoints(start, end)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Trim Points")
                }

                // Button to create a new sample from the current trim selection
                Button(
                    onClick = {
                        val currentTrimStart = trimStartMsString.toLongOrNull() ?: 0L
                        val currentTrimEnd = trimEndMsString.toLongOrNull() ?: (currentSample.value?.metadata?.duration ?: 0L)
                        viewModel.trimCurrentSample(currentTrimStart, currentTrimEnd)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && currentSample != null
                ) {
                    Text("Create New Sample from Trim")
                }

                Button(
                    onClick = { viewModel.normalizeCurrentSample() /* Default 0dB */ },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && currentSample != null
                ) {
                    Text("Normalize Sample (to 0dB)")
                }

                Button(
                    onClick = { viewModel.reverseCurrentSample() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && currentSample != null
                ) {
                    Text("Reverse Sample")
                }

                Divider()

                // Placeholder for Loop Mode UI - Re-add if LoopMode is still part of SampleMetadata and relevant
                // Text("Loop Mode", style = MaterialTheme.typography.titleSmall)
                // ...

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.auditionSlice() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading // Button should be enabled if not loading
                    ) {
                        Text("Audition")
                    }
                    Button(
                        onClick = { viewModel.saveChanges() }, // Saves metadata changes
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Save Metadata") // Text changes based on state removed for simplicity from diff
                    }
                }
            } // End of else (currentSample != null)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSampleEditScreen() {
    val mockAudioEngine = MockAudioEngineControl()
    // Use remember for MockProjectManager in Preview
    val mockProjectManager = remember { MockProjectManager() }
    val initialSampleUri = "preview://kick.wav"

    LaunchedEffect(Unit) {
        val previewMetadata = SampleMetadata(
            uri = initialSampleUri,
            duration = 1200L,
            name = "Preview Kick",
            sampleRate = 44100,
            channels = 1,
            bitDepth = 16,
            trimStartMs = 100L,
            trimEndMs = 1000L
            // loopMode = LoopMode.NONE // If LoopMode is used
        )
        val previewAudio = FloatArray( (1.2 * 44100).toInt() ) { kotlin.random.Random.nextFloat() * 2f - 1f }
        val previewSample = Sample(id = "previewSample1", metadata = previewMetadata, audioData = previewAudio)
        mockProjectManager.addPreviewSample(initialSampleUri, previewSample)
    }

    val viewModel = SampleEditViewModel(
        initialSampleUri = initialSampleUri,
        audioEngine = mockAudioEngine,
        projectManager = mockProjectManager
    )

    TheONETheme { // Using TheONETheme from imports
        SampleEditScreen(viewModel = viewModel)
    }
}
