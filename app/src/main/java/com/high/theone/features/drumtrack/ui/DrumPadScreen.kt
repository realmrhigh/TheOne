package com.high.theone.features.drumtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
    drumTrackViewModel: DrumTrackViewModel,
    navController: NavController? = null
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
        // Title and navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🥁 THE ONE MPC", 
                color = Color.White, 
                fontSize = 20.sp,
                style = MaterialTheme.typography.headlineSmall
            )
            
            if (navController != null) {
                Row {
                    Button(
                        onClick = { navController.navigate("debug_screen") },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Debug")
                    }
                    Button(
                        onClick = { navController.navigate("step_sequencer_screen") }
                    ) {
                        Text("Sequencer")
                    }
                }
            }
        }
        
        // Drum pad grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), // 4x4 grid for 16 pads
            modifier = Modifier.weight(1f).padding(16.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    val baseColor = Color(0xFF1E1E1E) // Dark gray
    val activeColor = Color(0xFF00FF88) // Bright green when active
    val accentColor = Color(0xFF444444) // Lighter gray for border
    
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) activeColor else baseColor,
        animationSpec = tween(durationMillis = 150),
        label = "pad-color"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "pad-glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow-alpha"
    )
    
    Box(
        modifier = modifier
            .size(80.dp)
            .graphicsLayer {
                shadowElevation = if (isActive) 16f else 4f
                shape = CircleShape
                clip = true
            }
            .background(
                brush = Brush.radialGradient(
                    colors = if (isActive) {
                        listOf(
                            activeColor.copy(alpha = glowAlpha),
                            activeColor.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    } else {
                        listOf(
                            accentColor.copy(alpha = 0.8f),
                            baseColor.copy(alpha = 0.9f),
                            Color.Black
                        )
                    },
                    center = androidx.compose.ui.geometry.Offset(40f, 40f),
                    radius = 50f
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = padSettings.name,
                color = if (isActive) Color.Black else Color.White,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium
            )
            if (padSettings.layers.isNotEmpty()) {
                Text(
                    text = padSettings.layers.first().sample.name,
                    color = if (isActive) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
// TODO: Complete implementation as needed
