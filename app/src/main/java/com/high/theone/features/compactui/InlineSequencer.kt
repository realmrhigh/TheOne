package com.high.theone.features.compactui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.features.compactui.animations.VisualFeedbackSystem
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.model.*
import com.high.theone.features.compactui.accessibility.*

/**
 * Inline sequencer component for the compact main UI
 * Shows 16 steps with track selection and step editing capabilities
 * Includes pattern management and mute/solo controls
 */
@Composable
fun InlineSequencer(
    sequencerState: SequencerState,
    currentPattern: Pattern?,
    availablePatterns: List<Pattern>,
    availableTracks: List<SequencerPadInfo>,
    selectedTracks: Set<Int>,
    muteSoloState: TrackMuteSoloState,
    onStepToggle: (padId: Int, stepIndex: Int) -> Unit,
    onStepLongPress: (padId: Int, stepIndex: Int) -> Unit,
    onTrackSelect: (trackId: Int) -> Unit,
    onTrackMute: (trackId: Int) -> Unit,
    onTrackSolo: (trackId: Int) -> Unit,
    onPatternSelect: (patternId: String) -> Unit,
    onPatternCreate: (name: String, length: Int) -> Unit,
    onPatternDuplicate: (patternId: String) -> Unit,
    onPatternDelete: (patternId: String) -> Unit,
    onPatternRename: (patternId: String, newName: String) -> Unit,
    onSelectAllTracks: () -> Unit,
    onSelectAssignedTracks: () -> Unit,
    onClearTrackSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPatternManagement by remember { mutableStateOf(false) }
    var showTrackList by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pattern switching controls
            PatternSwitchingControls(
                patterns = availablePatterns,
                currentPatternId = currentPattern?.id,
                onPatternSelect = onPatternSelect,
                onPatternCreate = { showPatternManagement = true },
                onPatternManage = { showPatternManagement = true },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Collapsible track list
            CollapsibleTrackList(
                availableTracks = availableTracks,
                selectedTracks = selectedTracks,
                muteSoloState = muteSoloState,
                isExpanded = showTrackList,
                onToggleExpanded = { showTrackList = !showTrackList },
                onTrackSelect = onTrackSelect,
                onTrackMute = onTrackMute,
                onTrackSolo = onTrackSolo,
                onSelectAll = onSelectAllTracks,
                onSelectAssigned = onSelectAssignedTracks,
                onClearSelection = onClearTrackSelection,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Step grid for selected tracks
            StepGrid(
                currentPattern = currentPattern,
                selectedTracks = selectedTracks,
                currentStep = sequencerState.currentStep,
                isPlaying = sequencerState.isPlaying,
                onStepToggle = onStepToggle,
                onStepLongPress = onStepLongPress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Pattern management dialog
    if (showPatternManagement) {
        PatternManagementDialog(
            patterns = availablePatterns,
            currentPatternId = currentPattern?.id,
            onDismiss = { showPatternManagement = false },
            onPatternCreate = { name, length ->
                onPatternCreate(name, length)
                showPatternManagement = false
            },
            onPatternDuplicate = onPatternDuplicate,
            onPatternDelete = onPatternDelete,
            onPatternRename = onPatternRename
        )
    }
}



/**
 * Step grid showing 16 steps for selected tracks
 */
@Composable
private fun StepGrid(
    currentPattern: Pattern?,
    selectedTracks: Set<Int>,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (padId: Int, stepIndex: Int) -> Unit,
    onStepLongPress: (padId: Int, stepIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentPattern == null || selectedTracks.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentPattern == null) "No pattern selected" else "Select tracks to edit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Step numbers header
        StepNumbersHeader(
            patternLength = currentPattern.length,
            currentStep = currentStep,
            isPlaying = isPlaying
        )
        
        // Step rows for each selected track
        selectedTracks.forEach { trackId ->
            StepRow(
                trackId = trackId,
                pattern = currentPattern,
                currentStep = currentStep,
                isPlaying = isPlaying,
                onStepToggle = onStepToggle,
                onStepLongPress = onStepLongPress
            )
        }
    }
}

/**
 * Header showing step numbers with current position indicator
 */
@Composable
private fun StepNumbersHeader(
    patternLength: Int,
    currentStep: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(minOf(patternLength, 16)) { stepIndex ->
            val isCurrentStep = isPlaying && stepIndex == currentStep
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isCurrentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stepIndex + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (isCurrentStep) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isCurrentStep) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

/**
 * Row of steps for a single track
 */
@Composable
private fun StepRow(
    trackId: Int,
    pattern: Pattern,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (padId: Int, stepIndex: Int) -> Unit,
    onStepLongPress: (padId: Int, stepIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackSteps = pattern.steps[trackId] ?: emptyList()
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(minOf(pattern.length, 16)) { stepIndex ->
            val step = trackSteps.find { it.position == stepIndex }
            val hasStep = step != null && step.isActive
            val isCurrentStep = isPlaying && stepIndex == currentStep
            
            StepButton(
                hasStep = hasStep,
                velocity = step?.velocity ?: 100,
                isCurrentStep = isCurrentStep,
                stepIndex = stepIndex,
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onStepToggle(trackId, stepIndex)
                },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStepLongPress(trackId, stepIndex)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual step button with tap and long press handling
 */
@Composable
private fun StepButton(
    hasStep: Boolean,
    velocity: Int,
    isCurrentStep: Boolean,
    stepIndex: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .ensureMinimumTouchTarget()
            .clip(RoundedCornerShape(6.dp))
            .sequencerStepSemantics(
                trackId = 0, // Will be passed from parent
                stepIndex = stepIndex,
                hasStep = hasStep,
                velocity = velocity,
                isCurrentStep = isCurrentStep
            )
            .pointerInput(stepIndex) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Use enhanced sequencer step highlight animation
        VisualFeedbackSystem.SequencerStepHighlight(
            isActive = hasStep,
            isCurrentStep = isCurrentStep,
            modifier = Modifier.fillMaxSize()
        )
        
        // Step indicator dot
        if (hasStep) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondary)
            )
        }
    }
}

// Data class for track mute/solo state
data class TrackMuteSoloState(
    val mutedTracks: Set<Int> = emptySet(),
    val soloedTracks: Set<Int> = emptySet()
)

// Data class for sequencer pad information
data class SequencerPadInfo(
    val index: Int,
    val sampleId: String?,
    val sampleName: String?,
    val hasAssignedSample: Boolean,
    val isEnabled: Boolean = true,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    val chokeGroup: Int? = null,
    val isLoading: Boolean = false,
    val canTrigger: Boolean = true
)

@Preview(showBackground = true)
@Composable
private fun InlineSequencerPreview() {
    MaterialTheme {
        val samplePattern = Pattern(
            id = "preview",
            name = "Preview Pattern",
            length = 16,
            tempo = 120f,
            steps = mapOf(
                0 to listOf(
                    Step(position = 0, velocity = 127),
                    Step(position = 4, velocity = 100),
                    Step(position = 8, velocity = 127),
                    Step(position = 12, velocity = 100)
                ),
                1 to listOf(
                    Step(position = 4, velocity = 110),
                    Step(position = 12, velocity = 110)
                )
            )
        )
        
        val samplePatterns = listOf(
            samplePattern,
            Pattern(id = "pattern2", name = "Fill Pattern", length = 8, tempo = 140f),
            Pattern(id = "pattern3", name = "Breakdown", length = 32, tempo = 100f)
        )
        
        val sampleTracks = listOf(
            SequencerPadInfo(0, "kick", "Kick", true),
            SequencerPadInfo(1, "snare", "Snare", true),
            SequencerPadInfo(2, "hihat", "Hi-Hat", true),
            SequencerPadInfo(3, "crash", "Crash", true)
        )
        
        InlineSequencer(
            sequencerState = SequencerState(
                isPlaying = true,
                currentStep = 2,
                selectedPads = setOf(0, 1)
            ),
            currentPattern = samplePattern,
            availablePatterns = samplePatterns,
            availableTracks = sampleTracks,
            selectedTracks = setOf(0, 1),
            muteSoloState = TrackMuteSoloState(
                mutedTracks = emptySet(),
                soloedTracks = setOf(1)
            ),
            onStepToggle = { _, _ -> },
            onStepLongPress = { _, _ -> },
            onTrackSelect = { },
            onTrackMute = { },
            onTrackSolo = { },
            onPatternSelect = { },
            onPatternCreate = { _, _ -> },
            onPatternDuplicate = { },
            onPatternDelete = { },
            onPatternRename = { _, _ -> },
            onSelectAllTracks = { },
            onSelectAssignedTracks = { },
            onClearTrackSelection = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InlineSequencerEmptyPreview() {
    MaterialTheme {
        InlineSequencer(
            sequencerState = SequencerState(),
            currentPattern = null,
            availablePatterns = emptyList(),
            availableTracks = emptyList(),
            selectedTracks = emptySet(),
            muteSoloState = TrackMuteSoloState(),
            onStepToggle = { _, _ -> },
            onStepLongPress = { _, _ -> },
            onTrackSelect = { },
            onTrackMute = { },
            onTrackSolo = { },
            onPatternSelect = { },
            onPatternCreate = { _, _ -> },
            onPatternDuplicate = { },
            onPatternDelete = { },
            onPatternRename = { _, _ -> },
            onSelectAllTracks = { },
            onSelectAssignedTracks = { },
            onClearTrackSelection = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}