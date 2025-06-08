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
import androidx.compose.material.*
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
import com.example.theone.model.LayerModels.SampleLayer
import com.example.theone.model.SampleMetadata
import com.example.theone.model.SynthModels.EnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings
import com.example.theone.model.SynthModels.LfoWaveform
import com.example.theone.model.SynthModels.LfoDestination
import com.example.theone.model.SynthModels.ModulationRouting // Added in previous step
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
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium
        ) {
            DrumProgramEditScreen(viewModel = viewModel, onDismiss = onDismiss)
        }
    }
}

@Composable
fun DrumProgramEditScreen(
    viewModel: DrumProgramEditViewModel,
    onDismiss: (updatedSettings: PadSettings?) -> Unit
) {
    val padSettings by viewModel.padSettings.collectAsState()
    val selectedLayerIndex by viewModel.selectedLayerIndex.collectAsState()
    val currentEditorTab by viewModel.currentEditorTab.collectAsState()

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
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Audition Pad")
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
                    val firstSample = viewModel.projectManager.getAvailableSamples().firstOrNull()
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
fun SamplesEditorContent( /* ... existing code ... */
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
            availableSamples = viewModel.projectManager.getAvailableSamples(),
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
        Divider()
        ParameterSlider(label = "Start Point", value = currentLayer.startPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.START_POINT, it) }, valueRange = 0f..1f)
        ParameterSlider(label = "End Point", value = currentLayer.endPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.END_POINT, it) }, valueRange = 0f..1f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Loop Enabled:", modifier = Modifier.weight(1f))
            Switch(checked = currentLayer.loopEnabled, onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_ENABLED, it) })
        }
        ParameterSlider(label = "Loop Point", value = currentLayer.loopPoint, onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_POINT, it) }, valueRange = 0f..1f, enabled = currentLayer.loopEnabled)
        Divider()
        ParameterSlider(label = "Tune (Semi)", value = currentLayer.tuningSemi.toFloat(), onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_SEMI, it.toInt()) }, valueRange = -24f..24f, steps = 47 )
        ParameterSlider(label = "Tune (Fine)", value = currentLayer.tuningFine.toFloat(), onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_FINE, it.toInt()) }, valueRange = -100f..100f, steps = 199 )
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse:", modifier = Modifier.weight(1f))
            Switch(checked = currentLayer.reverse, onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.REVERSE, it) })
        }

        Divider() // Add a divider before global pad controls

        Text(
            "Overall Pad Settings",
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        ParameterSlider(
            label = "Pad Volume",
            value = padSettings.volume,
            onValueChange = { viewModel.updatePadOverallVolume(it) },
            valueRange = 0f..1.5f // Example range, allows some boost
        )

        ParameterSlider(
            label = "Pad Pan",
            value = padSettings.pan,
            onValueChange = { viewModel.updatePadOverallPan(it) },
            valueRange = -1f..1f // Standard pan range
        )

        Divider() // Add a divider after global pad controls before layer removal button or other content

        Button(onClick = { viewModel.removeLayer(selectedLayerIndex) }, colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error), modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
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
        TabRow(selectedTabIndex = selectedEditorEnvelopeType.ordinal, backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)) {
            com.example.theone.features.drumtrack.edit.EnvelopeType.values().forEach { type ->
                Tab(selected = selectedEditorEnvelopeType == type, onClick = { selectedEditorEnvelopeType = type }, text = { Text(type.name) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).border(1.dp, Color.Gray).padding(8.dp), contentAlignment = Alignment.Center) {
            Text("Graphical Envelope Editor for ${selectedEditorEnvelopeType.name}")
        }
        Spacer(Modifier.height(8.dp))
        ParameterSlider(label = "Attack (s)", value = currentModelEnvelope.attackMs / 1000f, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(attackMs = it)) }, valueRange = 0f..2f )
        ParameterSlider(label = "Decay (s)", value = currentModelEnvelope.decayMs / 1000f, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(decayMs = it)) }, valueRange = 0f..2f)
        ParameterSlider(label = "Sustain Level", value = currentModelEnvelope.sustainLevel, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(sustainLevel = it)) }, valueRange = 0f..1f)
        ParameterSlider(label = "Release (s)", value = currentModelEnvelope.releaseMs / 1000f, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(releaseMs = it)) }, valueRange = 0f..5f)
    }
}

@Composable
fun LfosEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        padSettings.lfos.forEachIndexed { lfoIndex, lfoSettings ->
            if (lfoIndex > 0) Spacer(Modifier.height(8.dp))
            LfoControls(lfoIndex = lfoIndex, lfoSettings = lfoSettings, onLfoUpdate = { newSettings -> viewModel.updateLfo(lfoIndex, newSettings) })
            if (lfoIndex < padSettings.lfos.size - 1) Divider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
fun ModulationEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) { /* ... existing code ... */
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Modulation Routings", style = MaterialTheme.typography.h6)
            Button(onClick = { viewModel.addModulationRouting() }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (padSettings.modulations.isEmpty()) {
            Text("No modulation routings defined.")
        } else {
            padSettings.modulations.forEach { routing ->
                ModulationRoutingItem(routing = routing, viewModel = viewModel)
                Divider()
            }
        }
    }
}

@Composable
fun ModulationRoutingItem(routing: ModulationRouting, viewModel: DrumProgramEditViewModel) { /* ... existing code ... */
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("ID: ${routing.id.take(8)}...")
            Text("Source: ${routing.source.name}, Dest: ${routing.destination.name}, Amt: ${routing.amount}, Enabled: ${routing.isEnabled}")
            Button(onClick = { viewModel.removeModulationRouting(routing.id) }) {
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
            Text("Effects Chain", style = MaterialTheme.typography.h6)
            Button(onClick = { showAddEffectDialog = true }) {
                Text("Add Effect")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (padSettings.effects.isEmpty()) {
            Text("No effects in the chain.")
        } else {
            padSettings.effects.forEach { effect ->
                EffectSettingItem(
                    effectSetting = effect,
                    viewModel = viewModel
                )
                Divider()
            }
        }
    }

    if (showAddEffectDialog) {
        AddEffectDialog(
            onDismiss = { showAddEffectDialog = false },
            onEffectSelected = { effectType ->
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
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 2.dp) {
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
            // TODO: Implement detailed parameter editing UI for each effect type.
            Text("Parameters: ${effectSetting.parameters.entries.joinToString { \"${it.key}=${it.value.toString().take(4)}\" }}")
            Button(onClick = { viewModel.removeEffect(effectSetting.id) }) {
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
fun WaveformPlaceholder() { /* ... existing code ... */
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
fun LayerSelectionTabs( /* ... existing code ... */
    padSettings: PadSettings,
    selectedLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    viewModel: DrumProgramEditViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = if (selectedLayerIndex < 0 && padSettings.layers.isNotEmpty()) 0 else selectedLayerIndex.coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
        ) {
            padSettings.layers.forEachIndexed { index, layer ->
                Tab(
                    selected = index == selectedLayerIndex,
                    onClick = { onLayerSelected(index) },
                    text = {
                        val sampleName = layer.sampleNameCache.ifEmpty { layer.sampleId.take(8) }
                        Text("L${index + 1}: $sampleName", maxLines = 1)
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
fun EditorTabsBottomNavigation( /* ... existing code ... */
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        EditorTab.values().forEach { tab ->
            Tab(selected = selectedTab == tab, onClick = { onTabSelected(tab) }, text = { Text(tab.name.uppercase(Locale.getDefault())) })
        }
    }
}

@Composable
fun SampleSelectorDialog( /* ... existing code ... */
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
                    Text(text = "${sample.name} (${sample.durationMs}ms)", modifier = Modifier.fillMaxWidth().clickable { onSampleSelected(sample) }.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ParameterSlider( /* ... existing code ... */
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
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = if (steps > 0) steps else 0, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun LfoControls( /* ... existing code ... */
    lfoIndex: Int,
    lfoSettings: LFOSettings,
    onLfoUpdate: (LFOSettings) -> Unit
) {
    var waveformDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Card(elevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LFO ${lfoIndex + 1}", style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
                Switch(checked = lfoSettings.isEnabled, onCheckedChange = { onLfoUpdate(lfoSettings.copy(isEnabled = it)) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { waveformDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("Wave: ${lfoSettings.waveform.name}")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Waveform")
            }
            DropdownMenu(expanded = waveformDropdownExpanded, onDismissRequest = { waveformDropdownExpanded = false }) {
                com.example.theone.model.SynthModels.LfoWaveform.values().forEach { waveform ->
                    DropdownMenuItem(onClick = { onLfoUpdate(lfoSettings.copy(waveform = waveform)); waveformDropdownExpanded = false }) { Text(waveform.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            ParameterSlider(label = "Rate (Hz)", value = lfoSettings.rateHz, onValueChange = { onLfoUpdate(lfoSettings.copy(rateHz = it)) }, valueRange = 0.01f..20f, enabled = lfoSettings.isEnabled)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("BPM Sync:", modifier = Modifier.weight(1f))
                Switch(checked = lfoSettings.syncToTempo, onCheckedChange = { onLfoUpdate(lfoSettings.copy(syncToTempo = it)) }, enabled = lfoSettings.isEnabled)
            }
            Spacer(Modifier.height(8.dp))
            Text("Routing", style = MaterialTheme.typography.subtitle1)
            OutlinedButton(onClick = { destinationDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("To: ${lfoSettings.primaryDestination.name}")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Destination")
            }
            DropdownMenu(expanded = destinationDropdownExpanded, onDismissRequest = { destinationDropdownExpanded = false }) {
                com.example.theone.model.SynthModels.LfoDestination.values().forEach { destination ->
                    DropdownMenuItem(onClick = { onLfoUpdate(lfoSettings.copy(primaryDestination = destination)); destinationDropdownExpanded = false }) { Text(destination.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            ParameterSlider(label = "Depth", value = lfoSettings.depth, onValueChange = { onLfoUpdate(lfoSettings.copy(depth = it)) }, valueRange = 0f..1f, enabled = lfoSettings.isEnabled && lfoSettings.primaryDestination != com.example.theone.model.SynthModels.LfoDestination.NONE)
        }
    }
}

@Composable
fun ParameterTextField( /* ... existing code ... */
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
    enabled: Boolean = true
) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true)
}
