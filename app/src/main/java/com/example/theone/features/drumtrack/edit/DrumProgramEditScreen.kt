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

@Composable
fun DrumProgramEditDialog(
    viewModel: DrumProgramEditViewModel,
    onDismiss: (updatedSettings: PadSettings?) -> Unit // Modified signature
) {
    Dialog(
        onDismissRequest = { onDismiss(null) }, // Dismiss with null for no changes on outside click
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
    onDismiss: (updatedSettings: PadSettings?) -> Unit // Modified signature
) {
    val padSettings by viewModel.padSettings.collectAsState()
    val selectedLayerIndex by viewModel.selectedLayerIndex.collectAsState()
    val currentEditorTab by viewModel.currentEditorTab.collectAsState()

    // Dummy ProjectManager for preview if needed, otherwise use viewModel.projectManager
    // val projectManager = remember { viewModel.projectManager ?: DummyProjectManagerImpl() }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pad: ${padSettings.id} - ${padSettings.name}") }, // Used .id
                navigationIcon = {
                    IconButton(onClick = { onDismiss(null) }) { // Pass null for Cancel/Close
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.auditionPad() }) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Audition Pad")
                    }
                    IconButton(onClick = {
                        val finalSettings = viewModel.saveChanges()
                        onDismiss(finalSettings) // Pass updated settings on Done
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
                    } else {
                        // Handle case where no samples are available, maybe show a message
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
                    EditorTab.MODULATION -> {
                        // TODO: Implement Modulation Matrix Editor.
                        //  This section should allow routing various modulation sources
                        //  (LFOs, Envelopes, Velocity, Note, etc.)
                        //  to various destinations (Pitch, Pan, Filter Cutoff, Sample Start, etc.)
                        //  with control over modulation amount (depth) and potentially via curves.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("MODULATION Content Area (Placeholder)")
                        }
                    }
                    EditorTab.EFFECTS -> {
                        // TODO: Implement Effects Rack/Chain Editor.
                        //  This section should allow adding, removing, and reordering audio effects
                        //  (e.g., Reverb, Delay, EQ, Compressor, Distortion).
                        //  Each effect would have its own set of parameters to edit.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("EFFECTS Content Area (Placeholder)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp) // Reduced height a bit
            .padding(8.dp)
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .border(1.dp, Color.Gray),
        contentAlignment = Alignment.Center
    ) {
        // TODO: Render the audio waveform of the selected sample layer here.
        //  - Should visually represent Start, End, and Loop points.
        //  - Ideally, these points should be draggable on the waveform.
        Text("Waveform Display Area", color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun LayerSelectionTabs(
    padSettings: PadSettings,
    selectedLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    viewModel: DrumProgramEditViewModel // Pass viewModel for sample name lookup
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = if (selectedLayerIndex < 0 && padSettings.sampleLayers.isNotEmpty()) 0 else selectedLayerIndex.coerceAtLeast(0),
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
        ) {
            padSettings.sampleLayers.forEachIndexed { index, layer ->
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
    if (padSettings.sampleLayers.isEmpty()) {
        Text(
            "No layers. Click '+' to add a layer.",
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
fun EditorTabsBottomNavigation(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.name.uppercase(Locale.getDefault())) }
            )
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
    val currentLayer = padSettings.sampleLayers.getOrNull(selectedLayerIndex)
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
        // Sample Name Display and Selection
        // TODO: SampleSelectorDialog is currently a simplified browser.
        //  Consider features like: search, filtering by type, folder navigation, sample preview.
        TextButton(onClick = { showSampleSelectorDialog = true }) {
            Text("Sample: ${currentLayer.sampleNameCache.ifEmpty { currentLayer.sampleId }}")
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Sample")
        }
        Divider()

        // Trim/Loop Controls (as percentages 0.0 to 1.0)
        ParameterSlider(
            label = "Start Point",
            value = currentLayer.startPoint,
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.START_POINT, it) },
            valueRange = 0f..1f
        )
        ParameterSlider(
            label = "End Point",
            value = currentLayer.endPoint,
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.END_POINT, it) },
            valueRange = 0f..1f
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Loop Enabled:", modifier = Modifier.weight(1f))
            Switch(
                checked = currentLayer.loopEnabled,
                onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_ENABLED, it) }
            )
        }
        ParameterSlider(
            label = "Loop Point",
            value = currentLayer.loopPoint,
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_POINT, it) },
            valueRange = 0f..1f,
            enabled = currentLayer.loopEnabled // Example: enable only if loop is on
        )
        Divider()

        // Tuning Controls
        ParameterSlider(
            label = "Tune (Semi)",
            value = currentLayer.tuningSemi.toFloat(),
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_SEMI, it.toInt()) },
            valueRange = -24f..24f,
            steps = 47 // 24*2 steps + 0
        )
        ParameterSlider(
            label = "Tune (Fine)",
            value = currentLayer.tuningFine.toFloat(),
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_FINE, it.toInt()) },
            valueRange = -100f..100f,
            steps = 199 // 100*2 steps + 0
        )
        Divider()

        // Playback Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse:", modifier = Modifier.weight(1f))
            Switch(
                checked = currentLayer.reverse,
                onCheckedChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.REVERSE, it) }
            )
        }
         Button(
            onClick = { viewModel.removeLayer(selectedLayerIndex) },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
        ) {
            Text("Remove Layer ${selectedLayerIndex + 1}")
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
                        text = "${sample.name} (${sample.durationMs}ms)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSampleSelected(sample) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun EnvelopesEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    var selectedEnvelopeType by remember { mutableStateOf(EnvelopeType.AMP) }
    val scrollState = rememberScrollState()

    val currentEnvelope = when (selectedEnvelopeType) {
        EnvelopeType.AMP -> padSettings.ampEnvelope
        EnvelopeType.PITCH -> padSettings.pitchEnvelope
        EnvelopeType.FILTER -> padSettings.filterEnvelope
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        // Envelope Selection (TabRow for AMP, PITCH, FILTER)
        TabRow(selectedTabIndex = selectedEnvelopeType.ordinal, backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)) {
            EnvelopeType.values().forEach { type ->
                Tab(
                    selected = selectedEnvelopeType == type,
                    onClick = { selectedEnvelopeType = type },
                    text = { Text(type.name) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Graphical Editor Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .border(1.dp, Color.Gray)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // TODO: Implement graphical, drag-and-drop interface to edit envelope curves.
            //  Should visualize Attack, Decay, Sustain, Release.
            //  Potentially allow editing curve shapes (linear, exponential) for each segment.
            Text("Graphical Envelope Editor for ${selectedEnvelopeType.name}")
        }
        Spacer(Modifier.height(8.dp))

        // Parameter Sliders
        ParameterSlider(
            label = "Attack (s)",
            value = currentEnvelope.attack,
            onValueChange = { viewModel.updateEnvelope(selectedEnvelopeType, currentEnvelope.copy(attack = it)) },
            valueRange = 0f..2f // Example range
        )
        ParameterSlider(
            label = "Decay (s)",
            value = currentEnvelope.decay,
            onValueChange = { viewModel.updateEnvelope(selectedEnvelopeType, currentEnvelope.copy(decay = it)) },
            valueRange = 0f..2f
        )
        ParameterSlider(
            label = "Sustain Level",
            value = currentEnvelope.sustain,
            onValueChange = { viewModel.updateEnvelope(selectedEnvelopeType, currentEnvelope.copy(sustain = it)) },
            valueRange = 0f..1f
        )
        ParameterSlider(
            label = "Release (s)",
            value = currentEnvelope.release,
            onValueChange = { viewModel.updateEnvelope(selectedEnvelopeType, currentEnvelope.copy(release = it)) },
            valueRange = 0f..5f
        )
    }
}

@Composable
fun LfosEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        padSettings.lfos.forEachIndexed { lfoIndex, lfoSettings ->
            if (lfoIndex > 0) Spacer(Modifier.height(8.dp))
            LfoControls(
                lfoIndex = lfoIndex,
                lfoSettings = lfoSettings,
                onLfoUpdate = { newSettings -> viewModel.updateLfo(lfoIndex, newSettings) }
            )
            if (lfoIndex < padSettings.lfos.size - 1) Divider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
fun LfoControls(
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
                Switch(
                    checked = lfoSettings.isEnabled,
                    onCheckedChange = { onLfoUpdate(lfoSettings.copy(isEnabled = it)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            // Waveform Selection
            OutlinedButton(onClick = { waveformDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("Wave: ${lfoSettings.waveform.name}")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Waveform")
            }
            DropdownMenu(
                expanded = waveformDropdownExpanded,
                onDismissRequest = { waveformDropdownExpanded = false }
            ) {
                LfoWaveform.values().forEach { waveform ->
                    DropdownMenuItem(onClick = {
                        onLfoUpdate(lfoSettings.copy(waveform = waveform))
                        waveformDropdownExpanded = false
                    }) { Text(waveform.name) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Rate Control
            ParameterSlider(
                label = "Rate (Hz)",
                value = lfoSettings.rate,
                onValueChange = { onLfoUpdate(lfoSettings.copy(rate = it)) },
                valueRange = 0.01f..20f, // Example range
                enabled = lfoSettings.isEnabled
            )

            // BPM Sync Toggle
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("BPM Sync:", modifier = Modifier.weight(1f))
                Switch(
                    checked = lfoSettings.bpmSync,
                    onCheckedChange = { onLfoUpdate(lfoSettings.copy(bpmSync = it)) },
                    enabled = lfoSettings.isEnabled
                )
            }
            // TODO: If BPM Sync, show subdivision dropdown instead of Rate slider

            Spacer(Modifier.height(8.dp))
            Text("Routing", style = MaterialTheme.typography.subtitle1)

            // Destination Parameter
            OutlinedButton(onClick = { destinationDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("To: ${lfoSettings.destination.name}")
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Destination")
            }
            DropdownMenu(
                expanded = destinationDropdownExpanded,
                onDismissRequest = { destinationDropdownExpanded = false }
            ) {
                LfoDestination.values().forEach { destination ->
                    DropdownMenuItem(onClick = {
                        onLfoUpdate(lfoSettings.copy(destination = destination))
                        destinationDropdownExpanded = false
                    }) { Text(destination.name) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Modulation Depth
            ParameterSlider(
                label = "Depth",
                value = lfoSettings.depth,
                onValueChange = { onLfoUpdate(lfoSettings.copy(depth = it)) },
                valueRange = 0f..1f,
                enabled = lfoSettings.isEnabled && lfoSettings.destination != LfoDestination.NONE
            )
        }
    }
}


@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, // 0 means continuous
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
            steps = if (steps > 0) steps else 0, // Ensure steps is non-negative
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Minimalist TextField for numeric inputs - can be expanded
@Composable
fun ParameterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true
    )
}
```
