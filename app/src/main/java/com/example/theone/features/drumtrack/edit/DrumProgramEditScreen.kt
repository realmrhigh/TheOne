package com.example.theone.features.drumtrack.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.VolumeUp
// Material 3 Imports
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

// Consolidated Model Imports
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.EffectSetting
import com.example.theone.model.EffectType
import com.example.theone.model.LFOSettings
import com.example.theone.model.LayerModels.SampleLayer
import com.example.theone.model.LfoDestination
import com.example.theone.model.ModulationRouting
import com.example.theone.model.SampleMetadata
import com.example.theone.model.SynthModels
import com.example.theone.model.SynthModels.EnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings
import com.example.theone.model.SynthModels.LfoWaveform
import com.example.theone.model.SynthModels.LfoDestination
import com.example.theone.model.SynthModels.ModulationRouting
import com.example.theone.model.SynthModels.EffectSetting
import com.example.theone.model.SynthModels.EffectType


@Composable
fun DrumProgramEditDialog(
    viewModel: DrumProgramEditViewModel,
    onDismiss: (updatedSettings: PadSettings?) -> Unit
) {
    Dialog(
        onDismissRequest = { onDismiss(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // M3: Surface is largely the same, but ensure it's the M3 version.
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp // M3 uses tonal elevation
        ) {
            DrumProgramEditScreen(viewModel = viewModel, onDismiss = onDismiss)
        }
    }
}

// M3: OptIn annotation for ExperimentalMaterial3Api is often needed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrumProgramEditScreen(
    viewModel: DrumProgramEditViewModel,
    onDismiss: (updatedSettings: PadSettings?) -> Unit
) {
    val padSettings by viewModel.padSettings.collectAsState()
    val selectedLayerIndex by viewModel.selectedLayerIndex.collectAsState()
    val currentEditorTab by viewModel.currentEditorTab.collectAsState()

    // M3: Scaffold signature is updated.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pad: ${padSettings.id} - ${padSettings.name}") },
                navigationIcon = {
                    IconButton(onClick = { onDismiss(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.auditionPad() }) {
                        // FIX: Deprecated icon reference updated
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audition Pad")
                    }
                    IconButton(onClick = {
                        val finalSettings = viewModel.saveChanges()
                        onDismiss(finalSettings)
                    }) {
                        Icon(Icons.Filled.Done, contentDescription = "Done")
                    }
                }
            )
        },
        bottomBar = {
            EditorTabsBottomNavigation(
                selectedTab = currentEditorTab,
                onTabSelected = { viewModel.selectEditorTab(it) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            WaveformPlaceholder()
            LayerSelectionTabs(
                padSettings = padSettings,
                selectedLayerIndex = selectedLayerIndex,
                onLayerSelected = { viewModel.selectLayer(it) },
                onAddLayer = {
                    val firstSample = viewModel.projectManager.getSamplesFromPool().firstOrNull()
                    if (firstSample != null) {
                        viewModel.addSampleLayer(firstSample)
                    }
                },
                viewModel = viewModel
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                when (currentEditorTab) {
                    EditorTab.SAMPLES -> SamplesEditorContent(padSettings, selectedLayerIndex, viewModel)
                    EditorTab.ENVELOPES -> EnvelopesEditorContent(padSettings, viewModel)
                    EditorTab.LFO -> LfosEditorContent(padSettings, viewModel)
                    EditorTab.MODULATION -> ModulationEditorContent(padSettings, viewModel)
                    EditorTab.EFFECTS -> EffectsEditorContent(padSettings, viewModel)
                }
            }
        }
    }
}


// --- Content Sections for Editor Tabs ---

@Composable
fun SamplesEditorContent(
    padSettings: PadSettings,
    selectedLayerIndex: Int,
    viewModel: DrumProgramEditViewModel
) {
    val scrollState = rememberScrollState()
    val currentLayer = padSettings.layers.getOrNull(selectedLayerIndex)
    var showSampleSelectorDialog by remember { mutableStateOf(false) }

    if (currentLayer == null) {
        Text("No layer selected or layer is invalid.", modifier = Modifier.padding(16.dp))
        return
    }

    if (showSampleSelectorDialog) {
        SampleSelectorDialog(
            availableSamples = viewModel.projectManager.getSamplesFromPool(),
            onSampleSelected = { selectedSample ->
                viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.SAMPLE_ID, selectedSample.id)
                showSampleSelectorDialog = false
            },
            onDismiss = { showSampleSelectorDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        TextButton(onClick = { showSampleSelectorDialog = true }) {
            Text("Sample: ${currentLayer.sampleNameCache.ifEmpty { currentLayer.sampleId }}")
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Sample")
        }
        HorizontalDivider() // M3: Divider is now HorizontalDivider
        ParameterSlider(label = "Start Point", value = currentLayer.startPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.START_POINT, it) }, valueRange = 0f..1f)
        ParameterSlider(label = "End Point", value = currentLayer.endPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.END_POINT, it) }, valueRange = 0f..1f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Loop Enabled:", modifier = Modifier.weight(1f))
            Switch(checked = currentLayer.loopEnabled, onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_ENABLED, it) })
        }
        ParameterSlider(label = "Loop Point", value = currentLayer.loopPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_POINT, it) }, valueRange = 0f..1f, enabled = currentLayer.loopEnabled)
        HorizontalDivider()
        ParameterSlider(label = "Tune (Semi)", value = currentLayer.tuningSemi.toFloat(), onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_SEMI, it.toInt()) }, valueRange = -24f..24f, steps = 47)
        ParameterSlider(label = "Tune (Fine)", value = currentLayer.tuningFine.toFloat(), onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_FINE, it.toInt()) }, valueRange = -100f..100f, steps = 199)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse:", modifier = Modifier.weight(1f))
            Switch(checked = currentLayer.reverse, onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.REVERSE, it) })
        }
        // M3: Button colors are set differently
        Button(
            onClick = { viewModel.removeLayer(selectedLayerIndex) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
        ) {
            Text("Remove Layer ${selectedLayerIndex + 1}")
        }
    }
}

@Composable
fun EnvelopesEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    var selectedEditorEnvelopeType by remember { mutableStateOf(com.example.theone.features.drumtrack.edit.EnvelopeType.AMP) }
    val scrollState = rememberScrollState()
    val currentModelEnvelope = when (selectedEditorEnvelopeType) {
        com.example.theone.features.drumtrack.edit.EnvelopeType.AMP -> padSettings.ampEnvelope
        com.example.theone.features.drumtrack.edit.EnvelopeType.PITCH -> padSettings.pitchEnvelope
        com.example.theone.features.drumtrack.edit.EnvelopeType.FILTER -> padSettings.filterEnvelope
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        // M3: TabRow and Tab signatures updated
        TabRow(selectedTabIndex = selectedEditorEnvelopeType.ordinal) {
            com.example.theone.features.drumtrack.edit.EnvelopeType.values().forEach { type ->
                Tab(
                    selected = selectedEditorEnvelopeType == type,
                    onClick = { selectedEditorEnvelopeType = type },
                    // M3: text is now part of the content lambda
                    content = { Text(type.name, modifier = Modifier.padding(16.dp)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Replace placeholder Box with VisualEnvelopeEditor
        VisualEnvelopeEditor(
            modifier = Modifier.fillMaxWidth().height(150.dp), // Or desired height
            envelopeSettings = currentModelEnvelope,
            onSettingsChange = { updatedSettings ->
                // updatedSettings from VisualEnvelopeEditor has times in MS.
                // ViewModel's updateEnvelope now also expects MS.
                viewModel.updateEnvelope(selectedEditorEnvelopeType, updatedSettings)
            },
            lineColor = MaterialTheme.colorScheme.primary,
            pointColor = MaterialTheme.colorScheme.secondary,
            draggedPointColor = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(8.dp))
        // Adjust ParameterSliders to send MS to ViewModel
        ParameterSlider(
            label = "Attack (s)",
            value = currentModelEnvelope.attackMs / 1000f, // Display in seconds
            onValueChange = { secondsValue ->
                viewModel.updateEnvelope(
                    selectedEditorEnvelopeType,
                    currentModelEnvelope.copy(attackMs = secondsValue * 1000f) // Convert to MS
                )
            },
            valueRange = 0f..2f // Range in seconds for UI
        )
        ParameterSlider(
            label = "Decay (s)",
            value = currentModelEnvelope.decayMs / 1000f, // Display in seconds
            onValueChange = { secondsValue ->
                viewModel.updateEnvelope(
                    selectedEditorEnvelopeType,
                    currentModelEnvelope.copy(decayMs = secondsValue * 1000f) // Convert to MS
                )
            },
            valueRange = 0f..2f // Range in seconds for UI
        )
        ParameterSlider(
            label = "Sustain Level",
            value = currentModelEnvelope.sustainLevel,
            onValueChange = { sustainValue -> // Sustain is already 0-1f
                viewModel.updateEnvelope(
                    selectedEditorEnvelopeType,
                    currentModelEnvelope.copy(sustainLevel = sustainValue)
                )
            },
            valueRange = 0f..1f
        )
        ParameterSlider(
            label = "Release (s)",
            value = currentModelEnvelope.releaseMs / 1000f, // Display in seconds
            onValueChange = { secondsValue ->
                viewModel.updateEnvelope(
                    selectedEditorEnvelopeType,
                    currentModelEnvelope.copy(releaseMs = secondsValue * 1000f) // Convert to MS
                )
            },
            valueRange = 0f..5f // Range in seconds for UI
        )
    }
}

@Composable
fun LfosEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        padSettings.lfos.forEachIndexed { lfoIndex, lfoSettings: LFOSettings ->
            if (lfoIndex > 0) Spacer(Modifier.height(8.dp))
            LfoControls(lfoIndex = lfoIndex, lfoSettings = lfoSettings, onLfoUpdate = { newSettings -> viewModel.updateLfo(lfoIndex, newSettings) })
            if (lfoIndex < padSettings.lfos.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
fun ModulationEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Modulation Routings", style = MaterialTheme.typography.headlineSmall) // M3: typography updated
            Button(onClick = { viewModel.addModulationRouting() }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (padSettings.modulations.isEmpty()) {
            Text("No modulation routings defined.")
        } else {
            padSettings.modulations.forEach { routing: ModulationRouting ->
                ModulationRoutingItem(routing = routing, viewModel = viewModel)
                HorizontalDivider()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class) // Needed for ExposedDropdownMenuBox
@Composable
fun ModulationRoutingItem(routing: ModulationRouting, viewModel: DrumProgramEditViewModel) {
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp).spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ID: ${routing.id.take(8)}...", style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = routing.isEnabled,
                        onCheckedChange = { viewModel.updateModulationRouting(routing.id, routing.copy(isEnabled = it)) },
                        modifier = Modifier.size(40.dp) // Smaller switch
                    )
                }
            }

            // Source Dropdown
            ExposedDropdownMenuBox(
                expanded = sourceDropdownExpanded,
                onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded },
            ) {
                OutlinedTextField(
                    value = routing.source.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Source") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = routing.isEnabled
                )
                ExposedDropdownMenu(
                    expanded = sourceDropdownExpanded,
                    onDismissRequest = { sourceDropdownExpanded = false }
                ) {
                    viewModel.availableModSources.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.name) },
                            onClick = {
                                viewModel.updateModulationRouting(routing.id, routing.copy(source = source))
                                sourceDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Destination Dropdown
            ExposedDropdownMenuBox(
                expanded = destinationDropdownExpanded,
                onExpandedChange = { destinationDropdownExpanded = !destinationDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = routing.destination.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Destination") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = routing.isEnabled
                )
                ExposedDropdownMenu(
                    expanded = destinationDropdownExpanded,
                    onDismissRequest = { destinationDropdownExpanded = false }
                ) {
                    viewModel.availableModDestinations.forEach { destination ->
                        DropdownMenuItem(
                            text = { Text(destination.name) },
                            onClick = {
                                viewModel.updateModulationRouting(routing.id, routing.copy(destination = destination))
                                destinationDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Amount Slider
            Text(text = "Amount: ${String.format(Locale.US, "%.2f", routing.amount)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = routing.amount,
                onValueChange = { newValue ->
                    viewModel.updateModulationRouting(routing.id, routing.copy(amount = newValue))
                },
                valueRange = -1f..1f, // Assuming bipolar amount
                enabled = routing.isEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.removeModulationRouting(routing.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            ) {
                Text("Remove")
            }
        }
    }
}

@Composable
fun EffectsEditorContent(
    padSettings: PadSettings,
    viewModel: DrumProgramEditViewModel
) {
    val scrollState = rememberScrollState()
    var showAddEffectDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Effects Chain", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showAddEffectDialog = true }) {
                Text("Add Effect")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (padSettings.effects.isEmpty()) {
            Text("No effects in the chain.")
        } else {
            padSettings.effects.forEach { effect: EffectSetting ->
                EffectSettingItem(
                    effectSetting = effect,
                    viewModel = viewModel
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddEffectDialog) {
        AddEffectDialog(
            onDismiss = { showAddEffectDialog = false },
            onEffectSelected = { effectType: EffectType ->
                viewModel.addEffect(effectType)
                showAddEffectDialog = false
            }
        )
    }
}

@Composable
fun EffectSettingItem(
    effectSetting: EffectSetting,
    viewModel: DrumProgramEditViewModel
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Type: ${effectSetting.type.name} (ID: ${effectSetting.id.take(8)}...)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled:", modifier = Modifier.weight(1f))
                Switch(
                    checked = effectSetting.isEnabled,
                    onCheckedChange = { viewModel.toggleEffectEnabled(effectSetting.id) }
                )
            }
            ParameterSlider(
                label = "Mix",
                value = effectSetting.mix,
                onValueChange = { newMix -> viewModel.updateEffectMix(effectSetting.id, newMix) },
                valueRange = 0f..1f
            )
            // Dynamically create sliders for parameters
            val parameterDefinitions = viewModel.getEffectParameterDefinitions(effectSetting.type)

            if (parameterDefinitions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Visually separate mix from other params
                Text(
                    "Parameters:",
                    style = MaterialTheme.typography.titleSmall, // M3 titleSmall
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            parameterDefinitions.forEach { definition ->
                ParameterSlider(
                    label = if (definition.unit.isNotBlank()) "${definition.displayName} (${definition.unit})" else definition.displayName,
                    value = effectSetting.parameters[definition.key] ?: definition.defaultValue,
                    onValueChange = { newValue ->
                        viewModel.updateEffectParameter(effectSetting.id, definition.key, newValue)
                    },
                    valueRange = definition.valueRange,
                    steps = definition.steps, // ParameterSlider's internal logic handles M3 step conversion
                    enabled = effectSetting.isEnabled
                )
            }

            Button(
                onClick = { viewModel.removeEffect(effectSetting.id) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
            ) {
                Text("Remove")
            }
        }
    }
}

@Composable
fun AddEffectDialog(
    onDismiss: () -> Unit,
    onEffectSelected: (EffectType) -> Unit
) {
    // M3: AlertDialog signature updated
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Effect") },
        text = {
            LazyColumn {
                items(EffectType.values()) { effectType ->
                    Button(
                        onClick = { onEffectSelected(effectType) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(effectType.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


// --- Helper Composables and Dialogs from here ---

@Composable
fun WaveformPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(8.dp)
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .border(1.dp, Color.Gray),
        contentAlignment = Alignment.Center
    ) {
        Text("Waveform Display Area", color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun LayerSelectionTabs(
    padSettings: PadSettings,
    selectedLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    viewModel: DrumProgramEditViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // M3: ScrollableTabRow signature updated
        ScrollableTabRow(
            selectedTabIndex = if (selectedLayerIndex < 0 && padSettings.layers.isNotEmpty()) 0 else selectedLayerIndex.coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp
        ) {
            padSettings.layers.forEachIndexed { index, layer ->
                Tab(
                    selected = index == selectedLayerIndex,
                    onClick = { onLayerSelected(index) },
                    content = {
                        val sampleName = layer.sampleNameCache.ifEmpty { layer.sampleId.take(8) }
                        Text("L${index + 1}: $sampleName", maxLines = 1, modifier = Modifier.padding(16.dp))
                    }
                )
            }
        }
        IconButton(onClick = onAddLayer, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add Layer")
        }
    }
    if (padSettings.layers.isEmpty()) {
        Text("No layers. Click '+' to add a layer.", modifier = Modifier.padding(8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    }
}


@Composable
fun EditorTabsBottomNavigation(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    // M3: TabRow and Tab signatures updated
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                content = { Text(tab.name.uppercase(Locale.getDefault()), modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}

@Composable
fun SampleSelectorDialog(
    availableSamples: List<SampleMetadata>,
    onSampleSelected: (SampleMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Sample") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(availableSamples) { sample ->
                    // FIX: Based on your SampleModels.kt, the field is `durationMs` not `duration`.
                    Text(
                        text = "${sample.name} (${sample.durationMs}ms)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSampleSelected(sample) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%.2f", value))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            // M3: steps parameter is calculated differently
            steps = if (steps > 0) steps -1 else 0,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LfoControls(
    lfoIndex: Int,
    lfoSettings: LFOSettings,
    onLfoUpdate: (LFOSettings) -> Unit
) {
    var waveformDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LFO ${lfoIndex + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(checked = lfoSettings.isEnabled, onCheckedChange = { onLfoUpdate(lfoSettings.copy(isEnabled = it)) })
            }
            Spacer(Modifier.height(8.dp))

            // FIX: Use ExposedDropdownMenuBox for Material 3 Dropdowns
            ExposedDropdownMenuBox(
                expanded = waveformDropdownExpanded,
                onExpandedChange = { waveformDropdownExpanded = !waveformDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = lfoSettings.waveform.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Wave") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = waveformDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), // menuAnchor is crucial
                    enabled = lfoSettings.isEnabled
                )
                ExposedDropdownMenu(
                    expanded = waveformDropdownExpanded,
                    onDismissRequest = { waveformDropdownExpanded = false }
                ) {
                    // FIX: Explicitly define the lambda parameter type
                    SynthModels.LfoWaveform.entries.forEach { waveform: LfoWaveform ->
                        DropdownMenuItem(
                            text = { Text(waveform.name) },
                            onClick = {
                                onLfoUpdate(lfoSettings.copy(waveform = waveform))
                                waveformDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            ParameterSlider(label = "Rate (Hz)", value = lfoSettings.rateHz, onValueChange = { onLfoUpdate(lfoSettings.copy(rateHz = it)) }, valueRange = 0.01f..20f, enabled = lfoSettings.isEnabled)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("BPM Sync:", modifier = Modifier.weight(1f))
                Switch(checked = lfoSettings.syncToTempo, onCheckedChange = { onLfoUpdate(lfoSettings.copy(syncToTempo = it)) }, enabled = lfoSettings.isEnabled)
            }
            Spacer(Modifier.height(8.dp))
            Text("Routing", style = MaterialTheme.typography.titleMedium)

            // FIX: Use ExposedDropdownMenuBox for the second dropdown as well
            ExposedDropdownMenuBox(
                expanded = destinationDropdownExpanded,
                onExpandedChange = { destinationDropdownExpanded = !destinationDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = lfoSettings.primaryDestination.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = lfoSettings.isEnabled
                )
                ExposedDropdownMenu(
                    expanded = destinationDropdownExpanded,
                    onDismissRequest = { destinationDropdownExpanded = false }
                ) {
                    // FIX: Explicitly define the lambda parameter type
                    SynthModels.LfoDestination.values().forEach { destination: LfoDestination ->
                        DropdownMenuItem(
                            text = { Text(destination.name) },
                            onClick = {
                                onLfoUpdate(lfoSettings.copy(primaryDestination = destination))
                                destinationDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ParameterSlider(label = "Depth", value = lfoSettings.depth, onValueChange = { onLfoUpdate(lfoSettings.copy(depth = it)) }, valueRange = 0f..1f, enabled = lfoSettings.isEnabled && lfoSettings.primaryDestination != com.example.theone.model.SynthModels.LfoDestination.NONE)
        }
    }
}