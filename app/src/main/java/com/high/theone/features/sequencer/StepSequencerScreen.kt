package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.model.*

/**
 * Main step sequencer screen integrating all UI components.
 * Provides complete pattern programming interface with transport controls.
 * 
 * Requirements: All requirements integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepSequencerScreen(
    navController: NavHostController,
    viewModel: SequencerViewModel = hiltViewModel()
) {
    val sequencerState by viewModel.sequencerState.collectAsState()
    val patterns by viewModel.patterns.collectAsState()
    val pads by viewModel.pads.collectAsState()
    val muteSoloState by viewModel.muteSoloState.collectAsState()
    
    val currentPattern = patterns.find { it.id == sequencerState.currentPattern }
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentPattern?.name ?: "Step Sequencer"
                    ) 
                },
                actions = {
                    // Pattern management actions can be added here
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transport controls and pattern info
            TransportSection(
                sequencerState = sequencerState,
                currentPattern = currentPattern,
                onTransportAction = viewModel::handleTransportAction,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Playback position indicator
            PlaybackPositionIndicator(
                currentStep = sequencerState.currentStep,
                patternLength = currentPattern?.length ?: 16,
                isPlaying = sequencerState.isActivelyPlaying,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Pad selection interface
            PadSelector(
                pads = pads,
                selectedPads = sequencerState.selectedPads,
                mutedPads = muteSoloState.mutedTracks,
                soloedPads = muteSoloState.soloedTracks,
                onPadSelect = viewModel::togglePadSelection,
                onPadMute = viewModel::togglePadMute,
                onPadSolo = viewModel::togglePadSolo,
                onShowAll = viewModel::selectAllPads,
                onShowAssigned = viewModel::selectAssignedPads,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Main step grid
            StepGrid(
                pattern = currentPattern,
                pads = pads,
                currentStep = sequencerState.currentStep,
                selectedPads = sequencerState.selectedPads,
                onStepToggle = viewModel::toggleStep,
                onStepVelocityChange = viewModel::setStepVelocity,
                onPadSelect = viewModel::togglePadSelection,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Pattern information and statistics
            if (currentPattern != null) {
                PatternInfoSection(
                    pattern = currentPattern,
                    sequencerState = sequencerState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Transport controls section with playback and pattern controls
 */
@Composable
private fun TransportSection(
    sequencerState: SequencerState,
    currentPattern: Pattern?,
    onTransportAction: (TransportAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Transport buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { 
                        onTransportAction(
                            if (sequencerState.isPlaying) TransportAction.Pause else TransportAction.Play
                        ) 
                    }
                ) {
                    Text(if (sequencerState.isPlaying) "Pause" else "Play")
                }
                
                Button(
                    onClick = { onTransportAction(TransportAction.Stop) }
                ) {
                    Text("Stop")
                }
                
                Button(
                    onClick = { onTransportAction(TransportAction.ToggleRecord) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sequencerState.isRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(if (sequencerState.isRecording) "Recording" else "Record")
                }
            }
            
            // Tempo and swing controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tempo indicator
                TempoIndicator(
                    currentTempo = currentPattern?.tempo ?: 120f,
                    isPlaying = sequencerState.isActivelyPlaying
                )
                
                // Pattern length indicator
                PatternLengthIndicator(
                    currentLength = currentPattern?.length ?: 16,
                    maxLength = 32
                )
            }
        }
    }
}

/**
 * Pattern information and statistics section
 */
@Composable
private fun PatternInfoSection(
    pattern: Pattern,
    sequencerState: SequencerState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pattern Info",
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Length: ${pattern.length} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tempo: ${pattern.tempo.toInt()} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column {
                    Text(
                        text = "Swing: ${(pattern.swing * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Active Steps: ${pattern.steps.values.flatten().count { it.isActive }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Transport actions for sequencer control
 */
sealed class TransportAction {
    object Play : TransportAction()
    object Pause : TransportAction()
    object Stop : TransportAction()
    object ToggleRecord : TransportAction()
    data class SetTempo(val tempo: Float) : TransportAction()
    data class SetSwing(val swing: Float) : TransportAction()
}
