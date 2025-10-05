package com.high.theone.features.midi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.midi.model.MidiDeviceInfo
import com.high.theone.midi.model.MidiCurve

/**
 * Dialog for configuring MIDI device settings.
 * Allows users to customize device behavior and parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiDeviceConfigDialog(
    device: MidiDeviceInfo,
    onSave: (MidiDeviceConfiguration) -> Unit,
    onDismiss: () -> Unit
) {
    var inputEnabled by remember { mutableStateOf(true) }
    var outputEnabled by remember { mutableStateOf(true) }
    var inputLatency by remember { mutableStateOf(0f) }
    var outputLatency by remember { mutableStateOf(0f) }
    var velocityCurve by remember { mutableStateOf(MidiCurve.LINEAR) }
    var velocitySensitivity by remember { mutableStateOf(1.0f) }
    var channelFilter by remember { mutableStateOf<Set<Int>?>(null) }
    var omniMode by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Card {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Device Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Input/Output Settings
                if (device.inputPortCount > 0) {
                    ConfigSection("Input Settings") {
                        SwitchSetting(
                            label = "Enable MIDI Input",
                            checked = inputEnabled,
                            onCheckedChange = { inputEnabled = it }
                        )
                        
                        if (inputEnabled) {
                            SliderSetting(
                                label = "Input Latency Compensation",
                                value = inputLatency,
                                onValueChange = { inputLatency = it },
                                valueRange = 0f..50f,
                                unit = "ms"
                            )
                        }
                    }
                }
                
                if (device.outputPortCount > 0) {
                    ConfigSection("Output Settings") {
                        SwitchSetting(
                            label = "Enable MIDI Output",
                            checked = outputEnabled,
                            onCheckedChange = { outputEnabled = it }
                        )
                        
                        if (outputEnabled) {
                            SliderSetting(
                                label = "Output Latency Compensation",
                                value = outputLatency,
                                onValueChange = { outputLatency = it },
                                valueRange = 0f..50f,
                                unit = "ms"
                            )
                        }
                    }
                }
                
                // Velocity Settings
                ConfigSection("Velocity Settings") {
                    VelocityCurveSelector(
                        selectedCurve = velocityCurve,
                        onCurveSelected = { velocityCurve = it }
                    )
                    
                    SliderSetting(
                        label = "Velocity Sensitivity",
                        value = velocitySensitivity,
                        onValueChange = { velocitySensitivity = it },
                        valueRange = 0.1f..2.0f,
                        unit = "x"
                    )
                }
                
                // Channel Settings
                ConfigSection("Channel Settings") {
                    SwitchSetting(
                        label = "Omni Mode (All Channels)",
                        checked = omniMode,
                        onCheckedChange = { 
                            omniMode = it
                            if (it) channelFilter = null
                        }
                    )
                    
                    if (!omniMode) {
                        Text(
                            text = "Channel Filter",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        ChannelFilterGrid(
                            selectedChannels = channelFilter ?: emptySet(),
                            onChannelsChanged = { channelFilter = it.takeIf { it.isNotEmpty() } }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            onSave(
                                MidiDeviceConfiguration(
                                    deviceId = device.id,
                                    isInputEnabled = inputEnabled,
                                    isOutputEnabled = outputEnabled,
                                    inputLatencyMs = inputLatency,
                                    outputLatencyMs = outputLatency,
                                    velocityCurve = velocityCurve,
                                    velocitySensitivity = velocitySensitivity,
                                    channelFilter = channelFilter,
                                    omniMode = omniMode
                                )
                            )
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        content()
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "${value.toInt()}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VelocityCurveSelector(
    selectedCurve: MidiCurve,
    onCurveSelected: (MidiCurve) -> Unit
) {
    Column {
        Text(
            text = "Velocity Curve",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        MidiCurve.values().forEach { curve ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedCurve == curve,
                        onClick = { onCurveSelected(curve) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedCurve == curve,
                    onClick = null
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = curve.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ChannelFilterGrid(
    selectedChannels: Set<Int>,
    onChannelsChanged: (Set<Int>) -> Unit
) {
    Column {
        for (row in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0..3) {
                    val channel = row * 4 + col + 1
                    if (channel <= 16) {
                        val isSelected = selectedChannels.contains(channel)
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newChannels = if (isSelected) {
                                    selectedChannels - channel
                                } else {
                                    selectedChannels + channel
                                }
                                onChannelsChanged(newChannels)
                            },
                            label = {
                                Text(
                                    text = channel.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            if (row < 3) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * MIDI device configuration data class
 */
data class MidiDeviceConfiguration(
    val deviceId: String,
    val isInputEnabled: Boolean,
    val isOutputEnabled: Boolean,
    val inputLatencyMs: Float,
    val outputLatencyMs: Float,
    val velocityCurve: MidiCurve,
    val velocitySensitivity: Float,
    val channelFilter: Set<Int>?,
    val omniMode: Boolean
)

// Extension property for curve display names
private val MidiCurve.displayName: String
    get() = when (this) {
        MidiCurve.LINEAR -> "Linear"
        MidiCurve.EXPONENTIAL -> "Exponential"
        MidiCurve.LOGARITHMIC -> "Logarithmic"
        MidiCurve.S_CURVE -> "S-Curve"
    }