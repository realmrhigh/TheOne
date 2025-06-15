package com.example.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.theone.model.SynthModels.EffectSetting
import com.example.theone.model.SynthModels.EnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings
import com.example.theone.model.SynthModels.ModulationRouting

@Composable
fun TransportBar(
    sequencerViewModel: SequencerViewModel // Pass the ViewModel instance
) {
    // Observe playback state from ViewModel if needed for button states (e.g., Play/Pause toggle)
    // val isPlaying by sequencerViewModel.isPlayingState.collectAsState() // Example for future
    // val isRecording by sequencerViewModel.isRecordingState.collectAsState() // Example for future

    // For BPM Editor
    // Observe currentSequence directly for changes to its instance or relevant properties like ID
    val currentSeq = sequencerViewModel.currentSequence
    var bpmText by remember(currentSeq?.id, currentSeq?.bpm) {
        mutableStateOf(currentSeq?.bpm?.toString() ?: "120.0")
    }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play Button
        Button(onClick = { sequencerViewModel.play() }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }

        // Stop Button
        Button(onClick = { sequencerViewModel.stop() }) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }

        // Record Button
        Button(onClick = { sequencerViewModel.toggleRecording() }) {
            Icon(Icons.Default.RadioButtonChecked, contentDescription = "Record")
        }

        // BPM Editor
        OutlinedTextField(
            value = bpmText,
            onValueChange = { newText ->
                bpmText = newText
                newText.toFloatOrNull()?.let { newBpm ->
                    sequencerViewModel.setBpm(newBpm) // Assumes setBpm method exists
                }
            },
            label = { Text("BPM") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(100.dp)
        )

        // Sequence Selector (Placeholder)
        Button(onClick = { /* TODO: Implement sequence selection dialog */ }) {
            Text("Seq Select") // Placeholder text
        }

        // New Sequence Button
        Button(onClick = { sequencerViewModel.createNewSequence() }) { // Assumes method exists
            Text("New Seq")
        }

        // "Clear Sequence" Button
        Button(onClick = { sequencerViewModel.clearCurrentSequenceEvents() }) { // Assumes method exists
            Icon(Icons.Default.Clear, contentDescription = "Clear Sequence")
        }
    }
}
