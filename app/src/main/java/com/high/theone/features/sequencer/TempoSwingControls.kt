package com.high.theone.features.sequencer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Tempo control component with slider and numeric input.
 * Supports real-time tempo changes during playback.
 * 
 * Requirements: 2.6, 2.7, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
@Composable
fun TempoControl(
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(tempo.roundToInt().toString()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Update temp value when tempo changes externally
    LaunchedEffect(tempo) {
        if (!isEditing) {
            tempValue = tempo.roundToInt().toString()
        }
    }
    
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
            // Header with tempo indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tempo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                // Tempo value display/editor
                if (isEditing) {
                    OutlinedTextField(
                        value = tempValue,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                                tempValue = newValue
                            }
                        },
                        modifier = Modifier
                            .width(80.dp)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val newTempo = tempValue.toFloatOrNull()?.coerceIn(60f, 200f)
                                if (newTempo != null) {
                                    onTempoChange(newTempo)
                                }
                                isEditing = false
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center
                        )
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { isEditing = true }
                    ) {
                        Text(
                            text = "${tempo.roundToInt()}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "BPM",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Tempo slider
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = tempo,
                    onValueChange = onTempoChange,
                    valueRange = 60f..200f,
                    steps = 139, // 140 steps for 1 BPM increments
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Tempo range indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "60",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "200",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Tempo presets
            TempoPresets(
                currentTempo = tempo,
                onTempoSelect = onTempoChange
            )
        }
    }
    
    // Auto-focus when editing starts
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * Swing control component with preset and custom values.
 * Provides visual feedback for swing amount changes.
 */
@Composable
fun SwingControl(
    swing: Float,
    onSwingChange: (Float) -> Unit,
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
            // Header with swing indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Swing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${(swing * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (swing > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Swing slider
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = swing,
                    onValueChange = onSwingChange,
                    valueRange = 0f..0.75f,
                    steps = 74, // 75 steps for 1% increments
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Swing range indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "75%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Swing presets
            SwingPresets(
                currentSwing = swing,
                onSwingSelect = onSwingChange
            )
        }
    }
}

/**
 * Tempo preset buttons for quick tempo selection
 */
@Composable
private fun TempoPresets(
    currentTempo: Float,
    onTempoSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "Slow" to 80f,
        "Mid" to 120f,
        "Fast" to 140f,
        "Rapid" to 170f
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (name, tempo) ->
                val isSelected = kotlin.math.abs(currentTempo - tempo) < 1f
                
                FilterChip(
                    onClick = { onTempoSelect(tempo) },
                    label = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "${tempo.roundToInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    },
                    selected = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Swing preset buttons for quick swing selection
 */
@Composable
private fun SwingPresets(
    currentSwing: Float,
    onSwingSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        "None" to 0f,
        "Light" to 0.08f,
        "Medium" to 0.15f,
        "Heavy" to 0.25f,
        "Extreme" to 0.4f
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { (name, swing) ->
                val isSelected = kotlin.math.abs(currentSwing - swing) < 0.01f
                
                FilterChip(
                    onClick = { onSwingSelect(swing) },
                    label = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Combined tempo and swing controls in a compact layout
 */
@Composable
fun CompactTempoSwingControls(
    tempo: Float,
    swing: Float,
    onTempoChange: (Float) -> Unit,
    onSwingChange: (Float) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tempo section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tempo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tempo.roundToInt()} BPM",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Slider(
                    value = tempo,
                    onValueChange = onTempoChange,
                    valueRange = 60f..200f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Divider
            Divider(
                modifier = Modifier
                    .height(60.dp)
                    .width(1.dp)
            )
            
            // Swing section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Swing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(swing * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (swing > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Slider(
                    value = swing,
                    onValueChange = onSwingChange,
                    valueRange = 0f..0.75f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}