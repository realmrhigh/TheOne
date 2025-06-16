package com.example.theone.features.drumtrack.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.high.theone.domain.model.DrumPad
import com.high.theone.domain.model.PadSettings
import com.high.theone.domain.model.SampleMetadata

@Composable
fun DrumProgramEditScreen(
    viewModel: DrumProgramEditViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val drumProgram = uiState.drumProgram
    val selectedPadId = uiState.selectedPadId
    val selectedPad = drumProgram?.pads?.find { it.id == selectedPadId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .padding(16.dp)
    ) {
        Text(
            text = drumProgram?.name ?: "Drum Program",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))
        drumProgram?.let {
            PadGrid(
                pads = it.pads,
                selectedPadId = selectedPadId,
                onPadSelected = { viewModel.selectPad(it) }
            )
        }
        Spacer(Modifier.height(24.dp))
        selectedPad?.let { pad ->
            PadControls(
                pad = pad,
                onVolumeChange = { viewModel.onPadVolumeChange(pad.id, it) },
                onPanChange = { viewModel.onPadPanChange(pad.id, it) },
                onSampleAssign = { /* TODO: Show sample picker */ }
            )
        }
    }
}

@Composable
fun PadGrid(
    pads: List<DrumPad>,
    selectedPadId: String?,
    onPadSelected: (String) -> Unit
) {
    // 4x4 grid for 16 pads
    Column {
        for (row in 0 until 4) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    val pad = pads.getOrNull(index)
                    if (pad != null) {
                        PadButton(
                            pad = pad,
                            selected = pad.id == selectedPadId,
                            onClick = { onPadSelected(pad.id) }
                        )
                    } else {
                        Spacer(Modifier.size(56.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun PadButton(
    pad: DrumPad,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(if (selected) 8.dp else 2.dp, CircleShape)
            .background(if (selected) Color(0xFF00C853) else Color.DarkGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = pad.name.take(2),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        // TODO: Replace with animated/graphic pad visuals
    }
}

@Composable
fun PadControls(
    pad: DrumPad,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onSampleAssign: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF232323), shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(text = pad.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        // Volume
        Text("Volume", color = Color.LightGray)
        Slider(
            value = pad.settings.volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        // Pan
        Text("Pan", color = Color.LightGray)
        Slider(
            value = pad.settings.pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        // Sample
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSampleAssign) {
            Text("Assign Sample")
        }
        // TODO: Add envelope, LFO, and animated controls
    }
}