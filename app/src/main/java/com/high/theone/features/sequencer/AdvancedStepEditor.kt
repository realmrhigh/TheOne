package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.Step
import com.high.theone.model.StepCondition
import com.high.theone.model.StepHumanization

/**
 * Advanced step editor dialog for detailed step parameter editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedStepEditor(
    step: Step,
    onStepChange: (Step) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Step Editor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Divider()
                
                // Basic Parameters
                BasicParametersSection(
                    step = step,
                    onStepChange = onStepChange
                )
                
                Divider()
                
                // Probability Section
                ProbabilitySection(
                    step = step,
                    onStepChange = onStepChange
                )
                
                Divider()
                
                // Condition Section
                ConditionSection(
                    step = step,
                    onStepChange = onStepChange
                )
                
                Divider()
                
                // Humanization Section
                HumanizationSection(
                    step = step,
                    onStepChange = onStepChange
                )
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Reset to defaults
                            onStepChange(
                                step.copy(
                                    probability = 1.0f,
                                    condition = StepCondition.Always,
                                    humanization = StepHumanization(),
                                    microTiming = 0f
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicParametersSection(
    step: Step,
    onStepChange: (Step) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Basic Parameters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        // Velocity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Velocity")
            Text("${step.velocity}")
        }
        
        Slider(
            value = step.velocity.toFloat(),
            onValueChange = { value ->
                onStepChange(step.copy(velocity = value.toInt()))
            },
            valueRange = 1f..127f,
            steps = 125
        )
        
        // Micro Timing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Micro Timing")
            Text("${String.format("%.1f", step.microTiming)}ms")
        }
        
        Slider(
            value = step.microTiming,
            onValueChange = { value ->
                onStepChange(step.copy(microTiming = value))
            },
            valueRange = -50f..50f
        )
    }
}

@Composable
private fun ProbabilitySection(
    step: Step,
    onStepChange: (Step) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Probability",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trigger Probability")
            Text("${(step.probability * 100).toInt()}%")
        }
        
        Slider(
            value = step.probability,
            onValueChange = { value ->
                onStepChange(step.copy(probability = value))
            },
            valueRange = 0f..1f
        )
        
        // Quick probability presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val presets = listOf(0.25f to "25%", 0.5f to "50%", 0.75f to "75%", 1.0f to "100%")
            presets.forEach { (value, label) ->
                FilterChip(
                    onClick = { onStepChange(step.copy(probability = value)) },
                    label = { Text(label) },
                    selected = step.probability == value,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConditionSection(
    step: Step,
    onStepChange: (Step) -> Unit
) {
    var selectedConditionType by remember { mutableStateOf(getConditionType(step.condition)) }
    var everyNValue by remember { mutableStateOf(2) }
    var firstOfNValue by remember { mutableStateOf(4) }
    var lastOfNValue by remember { mutableStateOf(4) }
    var notOnBeatValue by remember { mutableStateOf(4) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Playback Condition",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        // Condition type selection
        val conditionTypes = listOf("Always", "Every N Times", "First of N", "Last of N", "Not on Beat")
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conditionTypes) { conditionType ->
                FilterChip(
                    onClick = { selectedConditionType = conditionTypes.indexOf(conditionType) },
                    label = { Text(conditionType) },
                    selected = selectedConditionType == conditionTypes.indexOf(conditionType)
                )
            }
        }
        
        // Condition parameters
        when (selectedConditionType) {
            0 -> {
                // Always - no parameters
                onStepChange(step.copy(condition = StepCondition.Always))
            }
            1 -> {
                // Every N Times
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Play every")
                    Text("$everyNValue times")
                }
                
                Slider(
                    value = everyNValue.toFloat(),
                    onValueChange = { value ->
                        everyNValue = value.toInt()
                        onStepChange(step.copy(condition = StepCondition.EveryNTimes(everyNValue)))
                    },
                    valueRange = 2f..16f,
                    steps = 14
                )
            }
            2 -> {
                // First of N
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("First of every")
                    Text("$firstOfNValue loops")
                }
                
                Slider(
                    value = firstOfNValue.toFloat(),
                    onValueChange = { value ->
                        firstOfNValue = value.toInt()
                        onStepChange(step.copy(condition = StepCondition.FirstOfN(firstOfNValue)))
                    },
                    valueRange = 2f..16f,
                    steps = 14
                )
            }
            3 -> {
                // Last of N
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last of every")
                    Text("$lastOfNValue loops")
                }
                
                Slider(
                    value = lastOfNValue.toFloat(),
                    onValueChange = { value ->
                        lastOfNValue = value.toInt()
                        onStepChange(step.copy(condition = StepCondition.LastOfN(lastOfNValue)))
                    },
                    valueRange = 2f..16f,
                    steps = 14
                )
            }
            4 -> {
                // Not on Beat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Skip every")
                    Text("$notOnBeatValue beats")
                }
                
                Slider(
                    value = notOnBeatValue.toFloat(),
                    onValueChange = { value ->
                        notOnBeatValue = value.toInt()
                        onStepChange(step.copy(condition = StepCondition.NotOnBeat(notOnBeatValue)))
                    },
                    valueRange = 2f..8f,
                    steps = 6
                )
            }
        }
    }
}

@Composable
private fun HumanizationSection(
    step: Step,
    onStepChange: (Step) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Humanization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Switch(
                checked = step.humanization.enabled,
                onCheckedChange = { enabled ->
                    onStepChange(
                        step.copy(
                            humanization = step.humanization.copy(enabled = enabled)
                        )
                    )
                }
            )
        }
        
        if (step.humanization.enabled) {
            // Timing Variation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Timing Variation")
                Text("±${String.format("%.1f", step.humanization.timingVariation)}ms")
            }
            
            Slider(
                value = step.humanization.timingVariation,
                onValueChange = { value ->
                    onStepChange(
                        step.copy(
                            humanization = step.humanization.copy(timingVariation = value)
                        )
                    )
                },
                valueRange = 0f..10f
            )
            
            // Velocity Variation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Velocity Variation")
                Text("${(step.humanization.velocityVariation * 100).toInt()}%")
            }
            
            Slider(
                value = step.humanization.velocityVariation,
                onValueChange = { value ->
                    onStepChange(
                        step.copy(
                            humanization = step.humanization.copy(velocityVariation = value)
                        )
                    )
                },
                valueRange = 0f..1f
            )
            
            // Humanization presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val presets = listOf(
                    "Subtle" to StepHumanization(2f, 0.1f, true),
                    "Medium" to StepHumanization(5f, 0.2f, true),
                    "Heavy" to StepHumanization(8f, 0.3f, true)
                )
                
                presets.forEach { (label, humanization) ->
                    OutlinedButton(
                        onClick = { onStepChange(step.copy(humanization = humanization)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

private fun getConditionType(condition: StepCondition): Int {
    return when (condition) {
        is StepCondition.Always -> 0
        is StepCondition.EveryNTimes -> 1
        is StepCondition.FirstOfN -> 2
        is StepCondition.LastOfN -> 3
        is StepCondition.NotOnBeat -> 4
    }
}

@Composable
private fun LazyRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        content()
    }
}