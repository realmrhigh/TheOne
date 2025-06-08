package com.example.theone.features.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.theone.features.sequencer.SequencerViewModel

@Composable
fun DebugScreen(
    viewModel: SequencerViewModel = hiltViewModel() // Assuming SequencerViewModel can be hiltViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Debug & Test Controls", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.testMetronomeFullSequence() }) {
            Text("Test Metronome Sequence")
        }

        Button(onClick = { viewModel.testAudioRecording() }) {
            Text("Test Audio Recording")
        }

        Button(onClick = { viewModel.testLoadAndPlaySample("test.wav", "testSampleWav_debug") }) {
            Text("Test Load/Play 'test.wav'")
        }

        // Add more buttons for other test samples if needed
        // Button(onClick = { viewModel.testLoadAndPlaySample("other_sample.wav", "otherSample_debug") }) {
        //     Text("Test Load/Play 'other_sample.wav'")
        // }

        // TODO: Add UI elements to show feedback from these tests if necessary (e.g., log messages, status indicators)
    }
}
