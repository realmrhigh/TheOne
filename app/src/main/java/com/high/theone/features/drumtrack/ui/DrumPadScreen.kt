package com.high.theone.features.drumtrack.ui

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
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.drumtrack.edit.DrumProgramEditDialog
import com.high.theone.features.drumtrack.edit.DrumProgramEditViewModel
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.model.SynthModels.EffectSetting
import com.high.theone.model.SynthModels.EnvelopeSettings
import com.high.theone.model.SynthModels.LFOSettings
import com.high.theone.model.SynthModels.ModulationRouting

@Composable
fun DrumPadScreen(
    drumTrackViewModel: DrumTrackViewModel // Assuming this is HiltViewModel or passed down
) {
    val padSettingsMap by drumTrackViewModel.padSettingsMap.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    // padToEdit will now directly store the consolidated PadSettings type
    var padToEdit by remember { mutableStateOf<PadSettings?>(null) }

    // localProjectManager removed, will use drumTrackViewModel.projectManager

    // Convert map to a list of pairs for LazyVerticalGrid, sorted by a conventional pad order if possible
    val padList = remember(padSettingsMap) {
        padSettingsMap.values.toList().sortedBy { it.id } // Simple sort by ID
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Drum Pad Screen", color = Color.White, fontSize = 24.sp)

        // Grid of drum pads
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(padList.size) { index ->
                val padSettings = padList[index]
                // PadItem composable represents each drum pad
                PadItem(
                    padSettings = padSettings,
                    onPadClick = {
                        // Handle pad click, possibly play sound or show settings
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Handle pad tap
                                }
                            )
                        }
                )
            }
        }

        // Edit button or area
        // This could be a separate Row or Button composable
        // On click, it should open the DrumProgramEditDialog
    }

    // Edit dialog
    if (showEditDialog && padToEdit != null) {
        DrumProgramEditDialog(
            padSettings = padToEdit!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedSettings ->
                // Handle saving of updated settings
                // Possibly call a method in drumTrackViewModel
                showEditDialog = false
            }
        )
    }
}
// TODO: Complete implementation as needed
