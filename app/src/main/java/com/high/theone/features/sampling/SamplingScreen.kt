package com.high.theone.features.sampling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.SamplingUiState
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import com.high.theone.model.AudioInputSource
import java.util.UUID

/**
 * Main sampling screen that combines recording controls, workflow UI, and sample preview.
 * This serves as the primary interface for the sampling feature.
 * 
 * Requirements: 1.2, 1.3, 1.6, 8.1, 8.2, 8.3
 */
@Composable
fun SamplingScreen(
    uiState: SamplingUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onChannelsChange: (Int) -> Unit,
    onMaxDurationChange: (Int) -> Unit,
    onInputSourceChange: (AudioInputSource) -> Unit,
    onSaveSample: (String, List<String>) -> Unit,
    onDiscardSample: () -> Unit,
    onRetryRecording: () -> Unit,
    onPreviewPlayPause: () -> Unit,
    onPreviewStop: () -> Unit,
    onPreviewSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onPadTrigger: (Int, Float) -> Unit = { _, _ -> }, // Pad trigger callback
    onPadLongPress: (Int) -> Unit = { _ -> }, // Pad configuration callback
    onMidiPadTrigger: (MidiPadTriggerEvent) -> Unit = { _ -> }, // MIDI trigger callback
    onMidiPadStop: (MidiPadStopEvent) -> Unit = { _ -> }, // MIDI stop callback
    modifier: Modifier = Modifier
) {
    var showPreparationScreen by remember { mutableStateOf(false) }
    var showPostRecordingActions by remember { mutableStateOf(false) }
    var sampleName by remember { mutableStateOf("") }
    var sampleTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var waveformData by remember { mutableStateOf(FloatArray(0)) }
    var trimSettings by remember { mutableStateOf(SampleTrimSettings()) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableStateOf(0f) }
    
    // Handle recording state changes
    LaunchedEffect(uiState.recordingState.isRecording) {
        if (!uiState.recordingState.isRecording && uiState.recordingState.durationMs > 0) {
            // Recording just finished
            showPostRecordingActions = true
            sampleName = "Sample ${System.currentTimeMillis()}"
            sampleTags = emptyList()
            // Generate mock waveform data (in real implementation, this would come from audio engine)
            waveformData = generateMockWaveform(1000)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main recording controls
        RecordingControls(
            recordingState = uiState.recordingState,
            onStartRecording = {
                if (uiState.recordingState.canStartRecording) {
                    onStartRecording()
                } else {
                    showPreparationScreen = true
                }
            },
            onStopRecording = onStopRecording
        )
        
        // Post-recording actions (shown after recording completes)
        if (showPostRecordingActions && !uiState.recordingState.isRecording) {
            PostRecordingActions(
                recordingState = uiState.recordingState,
                sampleName = sampleName,
                onSampleNameChange = { sampleName = it },
                tags = sampleTags,
                onTagsChange = { sampleTags = it },
                onSave = {
                    onSaveSample(sampleName, sampleTags)
                    showPostRecordingActions = false
                    sampleName = ""
                    sampleTags = emptyList()
                },
                onDiscard = {
                    onDiscardSample()
                    showPostRecordingActions = false
                    sampleName = ""
                    sampleTags = emptyList()
                },
                onRetry = {
                    onRetryRecording()
                    showPostRecordingActions = false
                }
            )
        }
        
        // Sample preview (shown when there's recorded content)
        if (waveformData.isNotEmpty() && showPostRecordingActions) {
            SamplePreview(
                sampleMetadata = SampleMetadata(
                    id = UUID.randomUUID(),
                    name = sampleName.ifBlank { "Preview" },
                    filePath = "",
                    durationMs = uiState.recordingState.durationMs,
                    sampleRate = uiState.recordingState.sampleRate,
                    channels = uiState.recordingState.channels,
                    createdAt = System.currentTimeMillis(),
                    tags = sampleTags
                ),
                waveformData = waveformData,
                trimSettings = trimSettings,
                isPlaying = isPreviewPlaying,
                playbackPosition = previewPosition,
                onPlayPause = {
                    isPreviewPlaying = !isPreviewPlaying
                    onPreviewPlayPause()
                },
                onStop = {
                    isPreviewPlaying = false
                    previewPosition = 0f
                    onPreviewStop()
                },
                onSeek = { position ->
                    previewPosition = position
                    onPreviewSeek(position)
                },
                onTrimChange = { newTrimSettings ->
                    trimSettings = newTrimSettings
                    onTrimChange(newTrimSettings)
                }
            )
        }
        
        // Pad Grid Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Drum Pads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                
                PadGrid(
                    pads = uiState.pads,
                    onPadTap = onPadTrigger,
                    onPadLongPress = onPadLongPress,
                    onMidiTrigger = onMidiPadTrigger,
                    onMidiStop = onMidiPadStop,
                    enabled = uiState.isAudioEngineReady && !uiState.isBusy
                )
            }
        }
        
        // Error display
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
    
    // Recording preparation screen (modal)
    if (showPreparationScreen) {
        RecordingPreparationScreen(
            recordingState = uiState.recordingState,
            onSampleRateChange = onSampleRateChange,
            onChannelsChange = onChannelsChange,
            onMaxDurationChange = onMaxDurationChange,
            onInputSourceChange = onInputSourceChange,
            onStartRecording = {
                onStartRecording()
                showPreparationScreen = false
            },
            onCancel = {
                showPreparationScreen = false
            }
        )
    }
}

/**
 * Generate mock waveform data for preview purposes.
 * In a real implementation, this would be provided by the audio engine.
 */
private fun generateMockWaveform(samples: Int): FloatArray {
    return FloatArray(samples) { i ->
        val frequency = 0.01f
        val amplitude = 0.5f
        val decay = 1f - (i.toFloat() / samples) * 0.8f
        (kotlin.math.sin(i * frequency * 2 * kotlin.math.PI) * amplitude * decay).toFloat()
    }
}