package com.example.theone.features.drumtrack.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.*
import java.util.Locale

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
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            DrumProgramEditScreen(viewModel = viewModel, onDismiss = onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                    val firstSample = viewModel.projectManager.getSamplesFromPool().firstOrNull()
                    if (firstSample != null) {
                        viewModel.addSampleLayer(firstSample)
                    }
                }
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No layers. Click '+' to add one.", modifier = Modifier.padding(16.dp))
        }
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
        HorizontalDivider()
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
    var selectedEditorEnvelopeType by remember { mutableStateOf(EnvelopeType.AMP) }
    val scrollState = rememberScrollState()
    val currentModelEnvelope = when (selectedEditorEnvelopeType) {
        EnvelopeType.AMP -> padSettings.ampEnvelope
        EnvelopeType.PITCH -> padSettings.pitchEnvelope
        EnvelopeType.FILTER -> padSettings.filterEnvelope
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        TabRow(selectedTabIndex = selectedEditorEnvelopeType.ordinal) {
            EnvelopeType.entries.forEach { type ->
                Tab(
                    selected = selectedEditorEnvelopeType == type,
                    onClick = { selectedEditorEnvelopeType = type },
                    text = { Text(type.name, modifier = Modifier.padding(16.dp)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(100.dp).border(1.dp, Color.Gray).padding(8.dp), contentAlignment = Alignment.Center) {
            Text("Graphical Envelope Editor for ${selectedEditorEnvelopeType.name}")
        }
        Spacer(Modifier.height(8.dp))
        ParameterSlider(label = "Attack (ms)", value = currentModelEnvelope.attackMs, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(attackMs = it)) }, valueRange = 0f..2000f )
        ParameterSlider(label = "Decay (ms)", value = currentModelEnvelope.decayMs, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(decayMs = it)) }, valueRange = 0f..2000f)
        ParameterSlider(label = "Sustain Level", value = currentModelEnvelope.sustainLevel, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(sustainLevel = it)) }, valueRange = 0f..1f)
        ParameterSlider(label = "Release (ms)", value = currentModelEnvelope.releaseMs, onValueChange = { viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(releaseMs = it)) }, valueRange = 0f..5000f)
    }
}

@Composable
fun LfosEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        // CORRECTED: Use a for loop instead of forEachIndexed
        for ((lfoIndex, lfoSettings) in padSettings.lfos.withIndex()) {
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
            Text("Modulation Routings", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { viewModel.addModulationRouting() }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (padSettings.modulations.isEmpty()) {
            Text("No modulation routings defined.")
        } else {
            // CORRECTED: Call composables directly in the Column's scope
            for (routing in padSettings.modulations) {
                ModulationRoutingItem(routing = routing, viewModel = viewModel)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ModulationRoutingItem(routing: ModulationRouting, viewModel: DrumProgramEditViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
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
fun EffectsEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    var showAddEffectDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Effects Chain", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showAddEffectDialog = true }) {
                Text("Add Effect")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (padSettings.effects.isEmpty()) {
            Text("No effects in the chain.")
        } else {
            // CORRECTED: Call composables directly in the Column's scope
            for (effect in padSettings.effects) {
                EffectSettingItem(effectSetting = effect, viewModel = viewModel)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun EffectSettingItem(effectSetting: EffectSetting, viewModel: DrumProgramEditViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Type: ${effectSetting.type.name} (ID: ${effectSetting.id.take(8)}...)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled:", modifier = Modifier.weight(1f))
                Switch(checked = effectSetting.isEnabled, onCheckedChange = { viewModel.toggleEffectEnabled(effectSetting.id) })
            }
            ParameterSlider(label = "Mix", value = effectSetting.mix, onValueChange = { newMix -> viewModel.updateEffectMix(effectSetting.id, newMix) }, valueRange = 0f..1f)

            val paramsString = effectSetting.parameters.entries.joinToString(", ") { entry ->
                "${entry.key}=${String.format(Locale.US, "%.2f", entry.value)}"
            }
            Text("Parameters: $paramsString")

            Button(onClick = { viewModel.removeEffect(effectSetting.id) }) {
                Text("Remove")
            }
        }
    }
}

@Composable
fun AddEffectDialog(onDismiss: () -> Unit, onEffectSelected: (EffectType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Effect") },
        text = {
            LazyColumn {
                items(EffectType.entries) { effectType ->
                    Button(onClick = { onEffectSelected(effectType) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = if (selectedLayerIndex < 0 && padSettings.layers.isNotEmpty()) 0 else selectedLayerIndex.coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp
        ) {
            padSettings.layers.forEachIndexed { index, layer ->
                Tab(
                    selected = index == selectedLayerIndex,
                    onClick = { onLayerSelected(index) },
                    text = {
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
fun EditorTabsBottomNavigation(selectedTab: EditorTab, onTabSelected: (EditorTab) -> Unit) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        EditorTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.name.uppercase(Locale.getDefault()), modifier = Modifier.padding(16.dp)) }
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
                    Text(
                        text = "${sample.name} (${sample.duration}ms)",
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
            steps = if (steps > 0) steps else 0,
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

            ExposedDropdownMenuBox(
                expanded = waveformDropdownExpanded,
                onExpandedChange = { if (lfoSettings.isEnabled) waveformDropdownExpanded = !waveformDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = lfoSettings.waveform.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Wave") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = waveformDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = lfoSettings.isEnabled
                )
                ExposedDropdownMenu(
                    expanded = waveformDropdownExpanded,
                    onDismissRequest = { waveformDropdownExpanded = false }
                ) {
                    LfoWaveform.entries.forEach { waveform ->
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

            ExposedDropdownMenuBox(
                expanded = destinationDropdownExpanded,
                onExpandedChange = { if (lfoSettings.isEnabled) destinationDropdownExpanded = !destinationDropdownExpanded }
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
                    LfoDestination.entries.forEach { destination ->
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
            ParameterSlider(label = "Depth", value = lfoSettings.depth, onValueChange = { onLfoUpdate(lfoSettings.copy(depth = it)) }, valueRange = 0f..1f, enabled = lfoSettings.isEnabled && lfoSettings.primaryDestination != LfoDestination.NONE)
        }
    }
}
