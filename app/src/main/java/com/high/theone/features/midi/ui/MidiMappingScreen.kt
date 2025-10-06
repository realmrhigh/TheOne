package com.high.theone.features.midi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.high.theone.midi.model.MidiLearnState
import com.high.theone.midi.mapping.MidiMappingConflict
import com.high.theone.midi.mapping.MidiConflictResolution
import com.high.theone.midi.mapping.MidiConflictResolutionType
import com.high.theone.midi.model.*

/**
 * MIDI Mapping Configuration Screen
 * 
 * Provides interface for:
 * - Managing MIDI mapping profiles
 * - Configuring parameter mappings
 * - MIDI learn functionality with visual feedback
 * - Resolving mapping conflicts
 * 
 * Requirements: 4.1, 4.3, 4.5
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiMappingScreen(
    onNavigateBack: () -> Unit,
    viewModel: MidiMappingViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle events
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is MidiMappingEvent.MappingSaved -> {
                    // Show success message
                }
                is MidiMappingEvent.MidiLearnStarted -> {
                    // Show learn mode feedback
                }
                is MidiMappingEvent.MidiLearnCompleted -> {
                    // Show completion feedback
                }
                else -> { /* Handle other events */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MIDI Mapping") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onCreateNewMapping() }) {
                        Icon(Icons.Default.Add, contentDescription = "Create New Mapping")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // MIDI Learn Status
            MidiLearnStatusCard(
                learnState = uiState.learnState,
                learnProgress = uiState.learnProgress,
                onCancelLearn = { viewModel.onCancelMidiLearn() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mapping Profiles List
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.mappingProfiles) { mapping ->
                        MidiMappingCard(
                            mapping = mapping,
                            isActive = uiState.currentProfile?.id == mapping.id,
                            conflicts = uiState.mappingConflicts.filter { it.mapping1.id == mapping.id || it.mapping2.id == mapping.id },
                            onEdit = { viewModel.onEditMapping(mapping) },
                            onDelete = { viewModel.onDeleteMapping(mapping.id) },
                            onActivate = { viewModel.onActivateMapping(mapping.id) },
                            onShowConflicts = { conflict ->
                                viewModel.onShowConflictDialog(conflict)
                            }
                        )
                    }
                }
            }
        }
    }

    // Mapping Editor Dialog
    if (uiState.isEditingMapping && uiState.selectedMapping != null) {
        MidiMappingEditorDialog(
            mapping = uiState.selectedMapping!!,
            onSave = { viewModel.onSaveMapping(it) },
            onCancel = { viewModel.onCancelEdit() },
            onStartMidiLearn = { targetType, targetId ->
                viewModel.onStartMidiLearn(targetType, targetId)
            }
        )
    }

    // Conflict Resolution Dialog
    if (uiState.showConflictDialog && uiState.selectedConflict != null) {
        MidiConflictResolutionDialog(
            conflict = uiState.selectedConflict!!,
            onResolve = { resolution ->
                viewModel.onResolveConflict(uiState.selectedConflict!!, resolution)
                viewModel.onDismissConflictDialog()
            },
            onDismiss = { viewModel.onDismissConflictDialog() }
        )
    }

    // Error Snackbar
    uiState.errorMessage?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // Show snackbar or toast
            viewModel.onDismissError()
        }
    }
}

@Composable
private fun MidiLearnStatusCard(
    learnState: MidiLearnState,
    learnProgress: com.high.theone.midi.mapping.MidiLearnProgress?,
    onCancelLearn: () -> Unit
) {
    AnimatedVisibility(
        visible = learnState != MidiLearnState.Inactive,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (learnState) {
                    is MidiLearnState.Active -> MaterialTheme.colorScheme.primaryContainer
                    is MidiLearnState.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (learnState) {
                                is MidiLearnState.Active -> "MIDI Learn Mode"
                                is MidiLearnState.Completed -> "MIDI Learn Completed"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        learnProgress?.let { progress ->
                            Text(
                                text = "Target: ${progress.target.targetType} - ${progress.target.targetId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (learnState is MidiLearnState.Active) {
                        Row {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onCancelLearn) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                if (learnState is MidiLearnState.Active) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Move a control on your MIDI device to assign it to the selected parameter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MidiMappingCard(
    mapping: MidiMapping,
    isActive: Boolean,
    conflicts: List<MidiMappingConflict>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onShowConflicts: (MidiMappingConflict) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (conflicts.isNotEmpty()) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mapping.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge {
                                Text("Active")
                            }
                        }
                    }
                    
                    Text(
                        text = "${mapping.mappings.size} mappings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    mapping.deviceId?.let { deviceId ->
                        Text(
                            text = "Device: $deviceId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    if (conflicts.isNotEmpty()) {
                        IconButton(
                            onClick = { onShowConflicts(conflicts.first()) }
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Conflicts",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    if (!isActive) {
                        IconButton(onClick = onActivate) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Activate")
                        }
                    }
                    
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }

            // Show conflicts summary
            if (conflicts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "${conflicts.size} mapping conflict(s) detected",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Show mapping preview
            if (mapping.mappings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(mapping.mappings.take(3)) { paramMapping ->
                        MidiParameterMappingPreview(paramMapping)
                    }
                    if (mapping.mappings.size > 3) {
                        item {
                            Text(
                                text = "... and ${mapping.mappings.size - 3} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MidiParameterMappingPreview(mapping: MidiParameterMapping) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${mapping.midiType} CH${mapping.midiChannel} CC${mapping.midiController}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${mapping.targetType}: ${mapping.targetId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MidiMappingEditorDialog(
    mapping: MidiMapping,
    onSave: (MidiMapping) -> Unit,
    onCancel: () -> Unit,
    onStartMidiLearn: (MidiTargetType, String) -> Unit
) {
    var editedMapping by remember { mutableStateOf(mapping) }
    var showAddMappingDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(if (mapping.id.startsWith("mapping_")) "Create Mapping" else "Edit Mapping")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = editedMapping.name,
                        onValueChange = { editedMapping = editedMapping.copy(name = it) },
                        label = { Text("Mapping Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = editedMapping.deviceId ?: "",
                        onValueChange = { editedMapping = editedMapping.copy(deviceId = it.ifEmpty { null }) },
                        label = { Text("Device ID (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Parameter Mappings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { showAddMappingDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Mapping")
                        }
                    }
                }

                items(editedMapping.mappings) { paramMapping ->
                    MidiParameterMappingEditor(
                        mapping = paramMapping,
                        onUpdate = { updated ->
                            val updatedMappings = editedMapping.mappings.map {
                                if (it == paramMapping) updated else it
                            }
                            editedMapping = editedMapping.copy(mappings = updatedMappings)
                        },
                        onDelete = {
                            val updatedMappings = editedMapping.mappings - paramMapping
                            editedMapping = editedMapping.copy(mappings = updatedMappings)
                        },
                        onStartMidiLearn = { targetType, targetId ->
                            onStartMidiLearn(targetType, targetId)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editedMapping) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )

    if (showAddMappingDialog) {
        AddParameterMappingDialog(
            onAdd = { newMapping ->
                editedMapping = editedMapping.copy(
                    mappings = editedMapping.mappings + newMapping
                )
                showAddMappingDialog = false
            },
            onDismiss = { showAddMappingDialog = false },
            onStartMidiLearn = onStartMidiLearn
        )
    }
}

@Composable
private fun MidiParameterMappingEditor(
    mapping: MidiParameterMapping,
    onUpdate: (MidiParameterMapping) -> Unit,
    onDelete: () -> Unit,
    onStartMidiLearn: (MidiTargetType, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${mapping.midiType} CH${mapping.midiChannel} CC${mapping.midiController}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${mapping.targetType}: ${mapping.targetId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = { onStartMidiLearn(mapping.targetType, mapping.targetId) }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "MIDI Learn")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mapping.minValue.toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { floatValue ->
                                    onUpdate(mapping.copy(minValue = floatValue))
                                }
                            },
                            label = { Text("Min Value") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mapping.maxValue.toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { floatValue ->
                                    onUpdate(mapping.copy(maxValue = floatValue))
                                }
                            },
                            label = { Text("Max Value") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Curve selection
                    var showCurveMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showCurveMenu,
                        onExpandedChange = { showCurveMenu = it }
                    ) {
                        OutlinedTextField(
                            value = mapping.curve.name,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Response Curve") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurveMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showCurveMenu,
                            onDismissRequest = { showCurveMenu = false }
                        ) {
                            MidiCurve.values().forEach { curve ->
                                DropdownMenuItem(
                                    text = { Text(curve.name) },
                                    onClick = {
                                        onUpdate(mapping.copy(curve = curve))
                                        showCurveMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddParameterMappingDialog(
    onAdd: (MidiParameterMapping) -> Unit,
    onDismiss: () -> Unit,
    onStartMidiLearn: (MidiTargetType, String) -> Unit
) {
    var midiType by remember { mutableStateOf(MidiMessageType.CONTROL_CHANGE) }
    var midiChannel by remember { mutableStateOf(1) }
    var midiController by remember { mutableStateOf(1) }
    var targetType by remember { mutableStateOf(MidiTargetType.PAD_VOLUME) }
    var targetId by remember { mutableStateOf("") }
    var minValue by remember { mutableStateOf(0f) }
    var maxValue by remember { mutableStateOf(1f) }
    var curve by remember { mutableStateOf(MidiCurve.LINEAR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Parameter Mapping") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // MIDI Input Configuration
                    Text(
                        text = "MIDI Input",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    var showTypeMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showTypeMenu,
                        onExpandedChange = { showTypeMenu = it }
                    ) {
                        OutlinedTextField(
                            value = midiType.name,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("MIDI Message Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = { showTypeMenu = false }
                        ) {
                            listOf(
                                MidiMessageType.NOTE_ON,
                                MidiMessageType.NOTE_OFF,
                                MidiMessageType.CONTROL_CHANGE,
                                MidiMessageType.PITCH_BEND
                            ).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        midiType = type
                                        showTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = midiChannel.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { intValue ->
                                    if (intValue in 1..16) {
                                        midiChannel = intValue
                                    }
                                }
                            },
                            label = { Text("Channel") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = midiController.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { intValue ->
                                    if (intValue in 0..127) {
                                        midiController = intValue
                                    }
                                }
                            },
                            label = { Text("Controller") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    // Target Configuration
                    Text(
                        text = "Target Parameter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    var showTargetMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showTargetMenu,
                        onExpandedChange = { showTargetMenu = it }
                    ) {
                        OutlinedTextField(
                            value = targetType.name,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Target Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTargetMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false }
                        ) {
                            MidiTargetType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        targetType = type
                                        showTargetMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetId,
                            onValueChange = { targetId = it },
                            label = { Text("Target ID") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onStartMidiLearn(targetType, targetId) }
                        ) {
                            Text("Learn")
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minValue.toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { floatValue ->
                                    minValue = floatValue
                                }
                            },
                            label = { Text("Min Value") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxValue.toString(),
                            onValueChange = { value ->
                                value.toFloatOrNull()?.let { floatValue ->
                                    maxValue = floatValue
                                }
                            },
                            label = { Text("Max Value") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newMapping = MidiParameterMapping(
                        midiType = midiType,
                        midiChannel = midiChannel,
                        midiController = midiController,
                        targetType = targetType,
                        targetId = targetId,
                        minValue = minValue,
                        maxValue = maxValue,
                        curve = curve
                    )
                    onAdd(newMapping)
                },
                enabled = targetId.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MidiConflictResolutionDialog(
    conflict: MidiMappingConflict,
    onResolve: (MidiConflictResolution) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapping Conflict") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Multiple parameters are mapped to the same MIDI input:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                listOf(conflict.conflictingParameter1, conflict.conflictingParameter2).forEach { mapping ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "${mapping.targetType}: ${mapping.targetId}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Text(
                    text = "How would you like to resolve this conflict?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = {
                        onResolve(
                            MidiConflictResolution(
                                type = MidiConflictResolutionType.REPLACE_ALL,
                                selectedMapping = conflict.conflictingParameter1
                            )
                        )
                    }
                ) {
                    Text("Replace All with First")
                }
                TextButton(
                    onClick = {
                        onResolve(
                            MidiConflictResolution(
                                type = MidiConflictResolutionType.KEEP_FIRST,
                                selectedMapping = conflict.conflictingParameter1
                            )
                        )
                    }
                ) {
                    Text("Keep First Only")
                }
                TextButton(
                    onClick = {
                        onResolve(
                            MidiConflictResolution(
                                type = MidiConflictResolutionType.REMOVE_ALL,
                                selectedMapping = null
                            )
                        )
                    }
                ) {
                    Text("Remove All")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}