package com.high.theone.features.drumtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.drumtrack.edit.DrumProgramEditDialog
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.EffectSetting
import com.high.theone.model.LFOSettings
import com.high.theone.model.ModulationRouting
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun DrumPadScreen(
    drumTrackViewModel: DrumTrackViewModel
) {
    val padSettingsMap by drumTrackViewModel.padSettingsMap.collectAsState()
    val isPlaying by drumTrackViewModel.isPlaying.collectAsState(initial = false)
    val isRecording by drumTrackViewModel.isRecording.collectAsState(initial = false)
    var showEditDialog by remember { mutableStateOf(false) }
    var padToEdit by remember { mutableStateOf<PadSettings?>(null) }
    val padList = remember(padSettingsMap) {
        padSettingsMap.values.toList().sortedBy { it.id }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Drum Pad Screen", color = Color.White, fontSize = 24.sp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(padList.size) { index ->
                val padSettings = padList[index]
                val isActive = drumTrackViewModel.activePadId.collectAsState().value == padSettings.id
                PadItem(
                    padSettings = padSettings,
                    isActive = isActive,
                    onPadClick = {
                        drumTrackViewModel.onPadTriggered(padSettings.id)
                    },
                    onPadLongPress = {
                        if (!isPlaying && !isRecording) {
                            padToEdit = padSettings
                            showEditDialog = true
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
    if (showEditDialog && padToEdit != null) {
        DrumProgramEditDialog(
            padSettings = padToEdit!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedSettings ->
                drumTrackViewModel.updatePadSettings(updatedSettings)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun PadItem(
    padSettings: PadSettings,
    isActive: Boolean = false,
    onPadClick: () -> Unit,
    onPadLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animate glow color based on pad state
    val baseColor = Color.DarkGray
    val activeColor = Color.Cyan
    val editColor = Color.Magenta
    val animatedColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor
            else -> baseColor
        },
        animationSpec = tween(durationMillis = 300)
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pad-glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow-alpha"
    )
    Box(
        modifier = modifier
            .size(100.dp)
            .graphicsLayer {
                shadowElevation = if (isActive) 24f else 8f
                shape = CircleShape
                clip = true
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor.copy(alpha = glowAlpha), animatedColor.copy(alpha = 0.2f)),
                    center = androidx.compose.ui.geometry.Offset(50f, 50f),
                    radius = 60f
                ),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onPadClick() },
                    onLongPress = { onPadLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(padSettings.name, color = Color.White)
    }
}
// TODO: Complete implementation as needed
