package com.example.theone.features.drumtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.theone.features.drumtrack.DrumTrackViewModel
// Now using consolidated models directly
import com.example.theone.features.drumtrack.edit.DrumProgramEditDialog
import com.example.theone.features.drumtrack.edit.DrumProgramEditViewModel
import com.example.theone.features.drumtrack.model.PadSettings // Consolidated PadSettings
// Import placeholder/dummy AudioEngine and ProjectManager for DrumProgramEditViewModel
// These are still defined in DrumProgramEditViewModel.kt for now
import com.example.theone.features.drumtrack.edit.AudioEngine
import com.example.theone.features.drumtrack.edit.DummyProjectManagerImpl

// Placeholder for AudioEngine implementation needed by DrumProgramEditViewModel
// This instance is defined here, but the interface is from DrumProgramEditViewModel.kt
object PlaceholderAudioEngine : AudioEngine { // Renamed to avoid conflict if EditAudioEngine was an actual type
    override fun triggerPad(padSettings: PadSettings) { // Uses consolidated PadSettings
        println("PlaceholderAudioEngine: Triggering pad ${padSettings.id} with name ${padSettings.name}")
    }
}

// Mapper functions are no longer needed as DrumProgramEditViewModel now uses consolidated models.

@Composable
fun DrumPadScreen(
    drumTrackViewModel: DrumTrackViewModel // Assuming this is HiltViewModel or passed down
) {
    val padSettingsMap by drumTrackViewModel.padSettingsMap.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    // padToEdit will now directly store the consolidated PadSettings type
    var padToEdit by remember { mutableStateOf<PadSettings?>(null) }

    val localProjectManager = remember { DummyProjectManagerImpl() }

    // Convert map to a list of pairs for LazyVerticalGrid, sorted by a conventional pad order if possible
    val padList = remember(padSettingsMap) {
        padSettingsMap.values.toList().sortedBy { it.id } // Simple sort by ID
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4), // 4x4 grid
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(padList.size) { index ->
            val currentPad = padList[index] // This is already the consolidated PadSettings
            PadView(
                padModel = currentPad,
                onLongPress = {
                    padToEdit = currentPad
                    showEditDialog = true
                },
                onClick = {
                    drumTrackViewModel.onPadTriggered(currentPad.id)
                }
            )
        }
    }

    if (showEditDialog && padToEdit != null) {
        // DrumProgramEditViewModel now takes the consolidated PadSettings directly.
        // No mapping needed for initialEditSettings.
        val drumProgramEditViewModel = remember(padToEdit) { // Re-remember if padToEdit changes
            DrumProgramEditViewModel(
                initialPadSettings = padToEdit!!, // Pass the consolidated model directly
                audioEngine = PlaceholderAudioEngine,
                projectManager = localProjectManager
            )
        }

        DrumProgramEditDialog(
            viewModel = drumProgramEditViewModel,
            onDismiss = { updatedSettings -> // updatedSettings is now consolidated PadSettings?
                if (updatedSettings != null && padToEdit != null) {
                    // No mapping needed for updatedModelSettings
                    drumTrackViewModel.updatePadSetting(padToEdit!!.id, updatedSettings)
                }
                showEditDialog = false
                padToEdit = null
            }
        )
    }
}

@Composable
fun PadView(
    padModel: PadSettings, // Now uses consolidated PadSettings
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f) // Square pads
            .background(Color.DarkGray)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onClick() }
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = padModel.name ?: padModel.id, // Display name or ID
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        // TODO: Display sample name if available on padModel.sampleName
    }
}

// Definition for com.example.theone.features.drumtrack.model.PadSettings and its dependencies
// would need to be available or created if they don't exist, e.g. in a model.kt file.
// For this subtask, we are defining mappers assuming their structure based on DrumTrackViewModel.
// If these files (model/PadSettings.kt, model/SampleLayer.kt etc.) were available,
// the mappers would be more accurate.
typealias EditLfoWaveform = com.example.theone.features.drumtrack.edit.LfoWaveform
typealias EditLfoDestination = com.example.theone.features.drumtrack.edit.LfoDestination
```
