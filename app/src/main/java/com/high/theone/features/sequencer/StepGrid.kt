package com.high.theone.features.sequencer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.Pattern
import com.high.theone.model.Step

/**
 * Main step grid component that displays the pattern programming interface.
 * Shows multiple pad tracks with their respective step sequences.
 * 
 * Requirements: 1.1, 1.3, 9.1, 9.5
 */
@Composable
fun StepGrid(
    pattern: Pattern?,
    pads: List<SequencerPadInfo>,
    currentStep: Int,
    selectedPads: Set<Int> = emptySet(),
    onStepToggle: (padIndex: Int, stepIndex: Int) -> Unit,
    onStepVelocityChange: (padIndex: Int, stepIndex: Int, velocity: Int) -> Unit,
    onPadSelect: (padIndex: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // Filter pads to show only those with assigned samples or selected pads
    val visiblePads = remember(pads, selectedPads) {
        pads.filter { pad ->
            pad.hasAssignedSample || selectedPads.contains(pad.index)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Header with step numbers
            StepGridHeader(
                patternLength = pattern?.length ?: 16,
                currentStep = currentStep,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Step grid rows
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                visiblePads.forEach { pad ->
                    StepRow(
                        pad = pad,
                        steps = pattern?.steps?.get(pad.index) ?: emptyList(),
                        patternLength = pattern?.length ?: 16,
                        currentStep = currentStep,
                        isSelected = selectedPads.contains(pad.index),
                        onStepToggle = { stepIndex -> onStepToggle(pad.index, stepIndex) },
                        onStepVelocityChange = { stepIndex, velocity -> 
                            onStepVelocityChange(pad.index, stepIndex, velocity) 
                        },
                        onPadSelect = { onPadSelect(pad.index) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Show message if no pads are visible
            if (visiblePads.isEmpty()) {
                EmptyStepGridMessage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        }
    }
}

/**
 * Header row showing step numbers and current playback position
 */
@Composable
private fun StepGridHeader(
    patternLength: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Pad info spacer
        Spacer(modifier = Modifier.width(80.dp))
        
        // Step number indicators
        repeat(patternLength) { stepIndex ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .background(
                        color = if (stepIndex == currentStep) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stepIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (stepIndex == currentStep) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (stepIndex == currentStep) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Individual row for a single pad showing its steps
 */
@Composable
private fun StepRow(
    pad: SequencerPadInfo,
    steps: List<Step>,
    patternLength: Int,
    currentStep: Int,
    isSelected: Boolean,
    onStepToggle: (Int) -> Unit,
    onStepVelocityChange: (Int, Int) -> Unit,
    onPadSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pad info section
        PadInfo(
            pad = pad,
            isSelected = isSelected,
            onSelect = onPadSelect,
            modifier = Modifier.width(80.dp)
        )
        
        // Step buttons
        repeat(patternLength) { stepIndex ->
            val step = steps.find { it.position == stepIndex }
            StepButton(
                isActive = step?.isActive == true,
                velocity = step?.velocity ?: 100,
                isCurrentStep = stepIndex == currentStep,
                isEnabled = pad.hasAssignedSample,
                onToggle = { onStepToggle(stepIndex) },
                onVelocityChange = { velocity -> onStepVelocityChange(stepIndex, velocity) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Pad information display showing name and sample status
 */
@Composable
private fun PadInfo(
    pad: SequencerPadInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(40.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                pad.hasAssignedSample -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pad.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    pad.hasAssignedSample -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            
            if (pad.hasAssignedSample) {
                Text(
                    text = "●",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Message displayed when no pads are available to show
 */
@Composable
private fun EmptyStepGridMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Pads Available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Assign samples to pads to start programming patterns",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}