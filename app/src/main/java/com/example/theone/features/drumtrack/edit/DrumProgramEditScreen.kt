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
import com.example.theone.model.SynthModels.EnvelopeSettings // Direct use, no alias needed here unless conflict
import com.example.theone.model.SynthModels.LFOSettings // Direct use
import com.example.theone.model.SynthModels.LfoWaveform
import com.example.theone.model.SynthModels.LfoDestination
// Editor-specific enums like EditorTab, LayerParameter, EnvelopeType (AMP/PITCH/FILTER)
// are imported because DrumProgramEditViewModel is imported.

@Composable
fun DrumProgramEditDialog(
    viewModel: DrumProgramEditViewModel,
    onDismiss: (updatedSettings: PadSettings?) -> Unit // Signature already updated, uses consolidated PadSettings now
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
    padSettings: PadSettings, // Now uses imported com.example.theone.features.drumtrack.model.PadSettings
    selectedLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    viewModel: DrumProgramEditViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ScrollableTabRow(
            selectedTabIndex = if (selectedLayerIndex < 0 && padSettings.layers.isNotEmpty()) 0 else selectedLayerIndex.coerceAtLeast(0), // Use .layers
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
        ) {
            padSettings.layers.forEachIndexed { index, layer -> // Use .layers
                Tab(
                    selected = index == selectedLayerIndex,
                    onClick = { onLayerSelected(index) },
                    text = {
                        // Consolidated SampleLayer (from model.LayerModels) has sampleNameCache
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
    if (padSettings.layers.isEmpty()) { // Use .layers
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
    padSettings: PadSettings, // Now uses imported com.example.theone.features.drumtrack.model.PadSettings
    selectedLayerIndex: Int,
    viewModel: DrumProgramEditViewModel
) {
    val scrollState = rememberScrollState()
    val currentLayer = padSettings.layers.getOrNull(selectedLayerIndex) // Use .layers
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
            value = currentLayer.loopPoint, // Consolidated SampleLayer has loopPoint
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.LOOP_POINT, it) },
            valueRange = 0f..1f,
            enabled = currentLayer.loopEnabled // Consolidated SampleLayer has loopEnabled
        )
        Divider()

        // Tuning Controls
        ParameterSlider(
            label = "Tune (Semi)",
            value = currentLayer.tuningSemi.toFloat(), // Consolidated SampleLayer has tuningSemi
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_SEMI, it.toInt()) },
            valueRange = -24f..24f,
            steps = 47
        )
        ParameterSlider(
            label = "Tune (Fine)",
            value = currentLayer.tuningFine.toFloat(), // Consolidated SampleLayer has tuningFine
            onValueChange = { viewModel.updateLayerParameter(selectedLayerIndex, LayerParameter.TUNING_FINE, it.toInt()) },
            valueRange = -100f..100f,
            steps = 199
        )
        Divider()

        // Playback Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reverse:", modifier = Modifier.weight(1f))
            Switch(
                checked = currentLayer.reverse, // Consolidated SampleLayer has reverse
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
    availableSamples: List<SampleMetadata>, // Now uses imported com.example.theone.model.SampleMetadata
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
fun EnvelopesEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) { // PadSettings is consolidated
    var selectedEditorEnvelopeType by remember { mutableStateOf(com.example.theone.features.drumtrack.edit.EnvelopeType.AMP) } // Use editor's EnvelopeType for local state
    val scrollState = rememberScrollState()

    // currentModelEnvelope is of type com.example.theone.model.SynthModels.EnvelopeSettings
    val currentModelEnvelope = when (selectedEditorEnvelopeType) {
        com.example.theone.features.drumtrack.edit.EnvelopeType.AMP -> padSettings.ampEnvelope
        com.example.theone.features.drumtrack.edit.EnvelopeType.PITCH -> padSettings.pitchEnvelope
        com.example.theone.features.drumtrack.edit.EnvelopeType.FILTER -> padSettings.filterEnvelope
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        // Envelope Selection (TabRow for AMP, PITCH, FILTER)
        TabRow(selectedTabIndex = selectedEditorEnvelopeType.ordinal, backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)) {
            com.example.theone.features.drumtrack.edit.EnvelopeType.values().forEach { type -> // Iterate editor's EnvelopeType
                Tab(
                    selected = selectedEditorEnvelopeType == type,
                    onClick = { selectedEditorEnvelopeType = type },
                    text = { Text(type.name) } // Display editor's EnvelopeType name
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
            Text("Graphical Envelope Editor for ${selectedEditorEnvelopeType.name}")
        }
        Spacer(Modifier.height(8.dp))

        // Parameter Sliders - UI works in seconds, ViewModel converts to Ms for model
        // currentModelEnvelope is ModelEnvelopeSettings, its time fields are in Ms.
        // Sliders should display and accept seconds.
        ParameterSlider(
            label = "Attack (s)",
            value = currentModelEnvelope.attackMs / 1000f, // Convert Ms to Seconds for UI
            onValueChange = { sliderValueInSeconds ->
                // Pass ModelEnvelopeSettings with the 'attackMs' field actually holding seconds. ViewModel will convert.
                viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(attackMs = sliderValueInSeconds))
            },
            valueRange = 0f..2f
        )
        ParameterSlider(
            label = "Decay (s)",
            value = currentModelEnvelope.decayMs / 1000f, // Convert Ms to Seconds for UI
            onValueChange = { sliderValueInSeconds ->
                viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(decayMs = sliderValueInSeconds))
            },
            valueRange = 0f..2f
        )
        ParameterSlider(
            label = "Sustain Level",
            value = currentModelEnvelope.sustainLevel, // This is 0-1f, already correct
            onValueChange = { sliderValue ->
                viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(sustainLevel = sliderValue))
            },
            valueRange = 0f..1f
        )
        ParameterSlider(
            label = "Release (s)",
            value = currentModelEnvelope.releaseMs / 1000f, // Convert Ms to Seconds for UI
            onValueChange = { sliderValueInSeconds ->
                viewModel.updateEnvelope(selectedEditorEnvelopeType, currentModelEnvelope.copy(releaseMs = sliderValueInSeconds))
            },
            valueRange = 0f..5f
        )
    }
}

@Composable
fun LfosEditorContent(padSettings: PadSettings, viewModel: DrumProgramEditViewModel) { // PadSettings is consolidated
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)) {
        padSettings.lfos.forEachIndexed { lfoIndex, lfoSettings -> // lfoSettings is ModelLFOSettings
            if (lfoIndex > 0) Spacer(Modifier.height(8.dp))
            LfoControls(
                lfoIndex = lfoIndex,
                lfoSettings = lfoSettings, // Pass ModelLFOSettings
                onLfoUpdate = { newSettings -> viewModel.updateLfo(lfoIndex, newSettings) }
            )
            if (lfoIndex < padSettings.lfos.size - 1) Divider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
fun LfoControls(
    lfoIndex: Int,
    lfoSettings: LFOSettings, // Now uses imported com.example.theone.model.SynthModels.LFOSettings
    onLfoUpdate: (LFOSettings) -> Unit
) {
    var waveformDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Card(elevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LFO ${lfoIndex + 1}", style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
                Switch(
                    checked = lfoSettings.isEnabled, // Consolidated LFOSettings has isEnabled
                    onCheckedChange = { onLfoUpdate(lfoSettings.copy(isEnabled = it)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            // Waveform Selection
            OutlinedButton(onClick = { waveformDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("Wave: ${lfoSettings.waveform.name}") // Consolidated LFOSettings has waveform (enum)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Waveform")
            }
            DropdownMenu(
                expanded = waveformDropdownExpanded,
                onDismissRequest = { waveformDropdownExpanded = false }
            ) {
                // Uses LfoWaveform enum from consolidated model package
                com.example.theone.model.SynthModels.LfoWaveform.values().forEach { waveform ->
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
                value = lfoSettings.rateHz, // Consolidated LFOSettings uses rateHz
                onValueChange = { onLfoUpdate(lfoSettings.copy(rateHz = it)) },
                valueRange = 0.01f..20f,
                enabled = lfoSettings.isEnabled
            )

            // BPM Sync Toggle
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("BPM Sync:", modifier = Modifier.weight(1f))
                Switch(
                    checked = lfoSettings.syncToTempo, // Consolidated LFOSettings uses syncToTempo
                    onCheckedChange = { onLfoUpdate(lfoSettings.copy(syncToTempo = it)) },
                    enabled = lfoSettings.isEnabled
                )
            }
            // TODO: If BPM Sync, show subdivision dropdown instead of Rate slider (using lfoSettings.tempoDivision)

            Spacer(Modifier.height(8.dp))
            Text("Routing", style = MaterialTheme.typography.subtitle1)

            // Destination Parameter
            OutlinedButton(onClick = { destinationDropdownExpanded = true }, enabled = lfoSettings.isEnabled) {
                Text("To: ${lfoSettings.primaryDestination.name}") // Consolidated uses primaryDestination
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Destination")
            }
            DropdownMenu(
                expanded = destinationDropdownExpanded,
                onDismissRequest = { destinationDropdownExpanded = false }
            ) {
                 // Uses LfoDestination enum from consolidated model package
                com.example.theone.model.SynthModels.LfoDestination.values().forEach { destination ->
                    DropdownMenuItem(onClick = {
                        onLfoUpdate(lfoSettings.copy(primaryDestination = destination))
                        destinationDropdownExpanded = false
                    }) { Text(destination.name) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Modulation Depth
            ParameterSlider(
                label = "Depth",
                value = lfoSettings.depth, // Consolidated LFOSettings has depth
                onValueChange = { onLfoUpdate(lfoSettings.copy(depth = it)) },
                valueRange = 0f..1f,
                enabled = lfoSettings.isEnabled && lfoSettings.primaryDestination != com.example.theone.model.SynthModels.LfoDestination.NONE
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
