package com.high.theone.features.midi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.midi.model.MidiDeviceInfo
import com.high.theone.midi.model.MidiDeviceType

/**
 * MIDI Settings Screen for device management and configuration.
 * 
 * Provides:
 * - Device list and connection management UI
 * - Device configuration and settings interface  
 * - Real-time device status display
 * 
 * Requirements: 3.1, 3.2, 3.3
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiSettingsScreen(
    navController: NavHostController,
    viewModel: MidiSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeviceConfigDialog by remember { mutableStateOf<MidiDeviceInfo?>(null) }
    
    // Handle permission requests
    LaunchedEffect(uiState.permissionRequired) {
        if (uiState.permissionRequired) {
            showPermissionDialog = true
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIDI Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                // Refresh devices button
                IconButton(
                    onClick = { viewModel.refreshDevices() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Devices"
                    )
                }
                
                // MIDI status indicator
                MidiStatusIndicator(
                    isEnabled = uiState.midiEnabled,
                    hasConnectedDevices = connectedDevices.isNotEmpty()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error display
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Main content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connected Devices Section
            item {
                DeviceSection(
                    title = "Connected Devices",
                    devices = connectedDevices.values.toList(),
                    isConnectedSection = true,
                    onDeviceAction = { device, action ->
                        when (action) {
                            DeviceAction.DISCONNECT -> viewModel.disconnectDevice(device.id)
                            DeviceAction.CONFIGURE -> showDeviceConfigDialog = device
                            else -> {}
                        }
                    }
                )
            }
            
            // Available Devices Section
            item {
                DeviceSection(
                    title = "Available Devices",
                    devices = availableDevices.filter { !connectedDevices.containsKey(it.id) },
                    isConnectedSection = false,
                    onDeviceAction = { device, action ->
                        when (action) {
                            DeviceAction.CONNECT -> viewModel.connectDevice(device.id)
                            DeviceAction.CONFIGURE -> showDeviceConfigDialog = device
                            else -> {}
                        }
                    }
                )
            }
            
            // MIDI System Status
            item {
                Spacer(modifier = Modifier.height(16.dp))
                MidiSystemStatus(
                    statistics = uiState.statistics,
                    isLoading = uiState.isLoading
                )
            }
        }
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        MidiPermissionDialog(
            onGrantPermission = {
                viewModel.requestMidiPermission()
                showPermissionDialog = false
            },
            onDismiss = {
                showPermissionDialog = false
            }
        )
    }
    
    // Device Configuration Dialog
    showDeviceConfigDialog?.let { device ->
        MidiDeviceConfigDialog(
            device = device,
            onSave = { config ->
                viewModel.updateDeviceConfiguration(device.id, config)
                showDeviceConfigDialog = null
            },
            onDismiss = {
                showDeviceConfigDialog = null
            }
        )
    }
}

@Composable
private fun DeviceSection(
    title: String,
    devices: List<MidiDeviceInfo>,
    isConnectedSection: Boolean,
    onDeviceAction: (MidiDeviceInfo, DeviceAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (devices.isEmpty()) {
                Text(
                    text = if (isConnectedSection) "No connected devices" else "No available devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    MidiDeviceItem(
                        device = device,
                        isConnected = isConnectedSection,
                        onAction = { action -> onDeviceAction(device, action) }
                    )
                    
                    if (device != devices.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MidiDeviceItem(
    device: MidiDeviceInfo,
    isConnected: Boolean,
    onAction: (DeviceAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${device.manufacturer} • ${device.type.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (device.inputPortCount > 0) {
                    Text(
                        text = "In: ${device.inputPortCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (device.outputPortCount > 0) {
                    Text(
                        text = "Out: ${device.outputPortCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Configure button
            IconButton(
                onClick = { onAction(DeviceAction.CONFIGURE) }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Device"
                )
            }
            
            // Connect/Disconnect button
            if (isConnected) {
                FilledTonalButton(
                    onClick = { onAction(DeviceAction.DISCONNECT) }
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = { onAction(DeviceAction.CONNECT) }
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun MidiStatusIndicator(
    isEnabled: Boolean,
    hasConnectedDevices: Boolean
) {
    val color = when {
        !isEnabled -> MaterialTheme.colorScheme.error
        hasConnectedDevices -> Color.Green
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val icon = when {
        !isEnabled -> Icons.Default.MusicOff
        hasConnectedDevices -> Icons.Default.MusicNote
        else -> Icons.Default.MusicNote
    }
    
    Icon(
        imageVector = icon,
        contentDescription = "MIDI Status",
        tint = color
    )
}

@Composable
private fun MidiSystemStatus(
    statistics: com.high.theone.midi.model.MidiStatistics?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "System Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading...")
                }
            } else if (statistics != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusRow("Input Messages", statistics.inputMessageCount.toString())
                    StatusRow("Output Messages", statistics.outputMessageCount.toString())
                    StatusRow("Average Latency", "${statistics.averageInputLatency}ms")
                    StatusRow("Dropped Messages", statistics.droppedMessageCount.toString())
                    
                    statistics.lastErrorMessage?.let { error ->
                        StatusRow("Last Error", error, isError = true)
                    }
                }
            } else {
                Text(
                    text = "No statistics available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

enum class DeviceAction {
    CONNECT,
    DISCONNECT,
    CONFIGURE
}

// Extension property for device type display names
private val MidiDeviceType.displayName: String
    get() = when (this) {
        MidiDeviceType.KEYBOARD -> "Keyboard"
        MidiDeviceType.CONTROLLER -> "Controller"
        MidiDeviceType.INTERFACE -> "Interface"
        MidiDeviceType.SYNTHESIZER -> "Synthesizer"
        MidiDeviceType.DRUM_MACHINE -> "Drum Machine"
        MidiDeviceType.OTHER -> "Other"
    }