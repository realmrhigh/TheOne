package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    
    var showPatternCreationDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentPattern?.name ?: "Step Sequencer"
                    ) 
                },
                actions = {
                    // Transport state indicator
                    TransportStateIndicator(
                        sequencerState = sequencerState
                    )
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
            // Transport controls
            TransportControls(
                sequencerState = sequencerState,
                onTransportAction = viewModel::handleTransportAction,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Tempo and swing controls
            CompactTempoSwingControls(
                tempo = currentPattern?.tempo ?: 120f,
                swing = currentPattern?.swing ?: 0f,
                onTempoChange = { viewModel.handleTransportAction(TransportControlAction.SetTempo(it)) },
                onSwingChange = { viewModel.handleTransportAction(TransportControlAction.SetSwing(it)) },
                isPlaying = sequencerState.isPlaying,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Pattern management
            PatternSelector(
                patterns = patterns,
                currentPatternId = sequencerState.currentPattern,
                onPatternSelect = viewModel::selectPattern,
                onPatternCreate = { showPatternCreationDialog = true },
                onPatternDuplicate = viewModel::duplicatePattern,
                onPatternDelete = viewModel::deletePattern,
                onPatternRename = viewModel::renamePattern,
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
    
    // Pattern creation dialog
    if (showPatternCreationDialog) {
        PatternCreationDialog(
            onConfirm = { name, length ->
                viewModel.createPattern(name, length)
                showPatternCreationDialog = false
            },
            onDismiss = { showPatternCreationDialog = false }
        )
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


