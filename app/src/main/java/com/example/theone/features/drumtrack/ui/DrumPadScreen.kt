package com.example.theone.features.drumtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text // Ensure this is Material3 Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.theone.features.drumtrack.DrumTrackViewModel
// Import for DrumProgramEditViewModel might be removed by IDE if not directly used.
// import com.example.theone.features.drumtrack.edit.DrumProgramEditViewModel
import com.example.theone.features.drumtrack.edit.DrumProgramEditDialog
import com.example.theone.features.drumtrack.model.PadSettings


@Composable
fun DrumPadScreen(
    drumTrackViewModel: DrumTrackViewModel
) {
    val padSettingsMap by drumTrackViewModel.padSettingsMap.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var padToEdit by remember { mutableStateOf<PadSettings?>(null) }

    val padList = remember(padSettingsMap) {
        padSettingsMap.values.toList().sortedBy { it.id }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(padList.size) { index ->
            val currentPad = padList[index]
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
        val currentPadToEdit = padToEdit!!
        // Use the factory from DrumTrackViewModel to create DrumProgramEditViewModel
        val drumProgramEditViewModel = remember(currentPadToEdit, drumTrackViewModel.drumProgramEditViewModelFactory) {
            drumTrackViewModel.drumProgramEditViewModelFactory.create(currentPadToEdit)
        }

        DrumProgramEditDialog(
            viewModel = drumProgramEditViewModel,
            onDismiss = { updatedSettings ->
                if (updatedSettings != null && padToEdit != null) {
                    // Ensure padToEdit is not null again before using its id
                    val currentPadId = padToEdit?.id
                    if (currentPadId != null) {
                        drumTrackViewModel.updatePadSetting(currentPadId, updatedSettings)
                    }
                }
                showEditDialog = false
                padToEdit = null
            }
        )
    }
}

@Composable
fun PadView(
    padModel: PadSettings,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.DarkGray)
            .pointerInput(padModel) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onClick() }
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = padModel.name,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
