package com.high.theone.features.sequencer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.model.*

typealias MuteSoloState = TrackMuteSoloState

/**
 * Main sequencer screen with responsive design and tabbed interface.
 * Provides complete pattern programming interface with collapsible sections.
 * 
 * Requirements: 9.1, 9.5, 9.6, 9.7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerScreen(
    navController: NavHostController,
    viewModel: SequencerViewModel = hiltViewModel()
) {
    val sequencerState by viewModel.sequencerState.collectAsState()
    val patterns by viewModel.patterns.collectAsState()
    val pads by viewModel.pads.collectAsState()
    val muteSoloState by viewModel.muteSoloState.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    var selectedTab by remember { mutableStateOf(SequencerTab.PATTERN) }
    var showPatternCreationDialog by remember { mutableStateOf(false) }
    
    // Collapsible section states
    var transportExpanded by remember { mutableStateOf(true) }
    var patternExpanded by remember { mutableStateOf(true) }
    var padSelectorExpanded by remember { mutableStateOf(!isLandscape) }
    
    val currentPattern = patterns.find { it.id == sequencerState.currentPattern }
    
    val topBar: @Composable () -> Unit = {
        SequencerTopBar(
            currentPattern = currentPattern,
            sequencerState = sequencerState,
            onNavigateBack = { navController.popBackStack() },
            onShowSettings = { navController.navigate("sequencer_settings") }
        )
    }
    
    if (isLandscape) {
        Scaffold(
            topBar = topBar
        ) { paddingValues: PaddingValues ->
            LandscapeSequencerLayout(
                paddingValues = paddingValues,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                sequencerState = sequencerState,
                patterns = patterns,
                pads = pads,
                muteSoloState = muteSoloState,
                currentPattern = currentPattern,
                viewModel = viewModel,
                transportExpanded = transportExpanded,
                onTransportExpandedChange = { transportExpanded = it },
                patternExpanded = patternExpanded,
                onPatternExpandedChange = { patternExpanded = it },
                padSelectorExpanded = padSelectorExpanded,
                onPadSelectorExpandedChange = { padSelectorExpanded = it },
                showPatternCreationDialog = showPatternCreationDialog,
                onShowPatternCreationDialog = { showPatternCreationDialog = it }
            )
        }
    } else {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                SequencerBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { paddingValues: PaddingValues ->
            PortraitSequencerLayout(
                paddingValues = paddingValues,
                selectedTab = selectedTab,
                sequencerState = sequencerState,
                patterns = patterns,
                pads = pads,
                muteSoloState = muteSoloState,
                currentPattern = currentPattern,
                viewModel = viewModel,
                transportExpanded = transportExpanded,
                onTransportExpandedChange = { transportExpanded = it },
                patternExpanded = patternExpanded,
                onPatternExpandedChange = { patternExpanded = it },
                padSelectorExpanded = padSelectorExpanded,
                onPadSelectorExpandedChange = { padSelectorExpanded = it },
                showPatternCreationDialog = showPatternCreationDialog,
                onShowPatternCreationDialog = { showPatternCreationDialog = it }
            )
        }
    }
    
    // Pattern creation dialog
    if (showPatternCreationDialog) {
        PatternCreationDialog(
            onConfirm = { name, length ->
                viewModel.createPattern(name, length)
                showPatternCreationDialog = false
            },
            onDismiss = { showPatternCreationDialog = false }
        )
    }
}

/**
 * Sequencer tab definitions
 */
enum class SequencerTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PATTERN("Pattern", Icons.Default.GridOn),
    SONG("Song", Icons.Default.QueueMusic),
    MIXER("Mixer", Icons.Default.Tune),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Top app bar for sequencer screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SequencerTopBar(
    currentPattern: Pattern?,
    sequencerState: SequencerState,
    onNavigateBack: () -> Unit,
    onShowSettings: () -> Unit
) {
    TopAppBar(
        title = { 
            Column {
                Text(
                    text = currentPattern?.name ?: "Step Sequencer",
                    style = MaterialTheme.typography.titleMedium
                )
                if (sequencerState.isPlaying) {
                    Text(
                        text = "Playing - Step ${sequencerState.currentStep + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Transport state indicator
            TransportStateIndicator(sequencerState = sequencerState)
            
            IconButton(onClick = onShowSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

/**
 * Bottom navigation bar for portrait mode
 */
@Composable
private fun SequencerBottomBar(
    selectedTab: SequencerTab,
    onTabSelected: (SequencerTab) -> Unit
) {
    NavigationBar {
        SequencerTab.values().forEach { tab ->
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title) },
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

/**
 * Portrait layout for sequencer screen
 */
@Composable
private fun PortraitSequencerLayout(
    paddingValues: PaddingValues,
    selectedTab: SequencerTab,
    sequencerState: SequencerState,
    patterns: List<Pattern>,
    pads: List<SequencerPadInfo>,
    muteSoloState: MuteSoloState,
    currentPattern: Pattern?,
    viewModel: SequencerViewModel,
    transportExpanded: Boolean,
    onTransportExpandedChange: (Boolean) -> Unit,
    patternExpanded: Boolean,
    onPatternExpandedChange: (Boolean) -> Unit,
    padSelectorExpanded: Boolean,
    onPadSelectorExpandedChange: (Boolean) -> Unit,
    showPatternCreationDialog: Boolean,
    onShowPatternCreationDialog: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        when (selectedTab) {
            SequencerTab.PATTERN -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Transport controls section
                    CollapsibleSection(
                        title = "Transport",
                        expanded = transportExpanded,
                        onExpandedChange = onTransportExpandedChange
                    ) {
                        TransportControlsSection(
                            sequencerState = sequencerState,
                            currentPattern = currentPattern,
                            viewModel = viewModel
                        )
                    }
                    
                    // Pattern management section
                    CollapsibleSection(
                        title = "Pattern",
                        expanded = patternExpanded,
                        onExpandedChange = onPatternExpandedChange
                    ) {
                        PatternManagementSection(
                            patterns = patterns,
                            sequencerState = sequencerState,
                            viewModel = viewModel,
                            onShowPatternCreationDialog = onShowPatternCreationDialog
                        )
                    }
                    
                    // Pad selector section
                    CollapsibleSection(
                        title = "Tracks",
                        expanded = padSelectorExpanded,
                        onExpandedChange = onPadSelectorExpandedChange
                    ) {
                        PadSelectorSection(
                            pads = pads,
                            sequencerState = sequencerState,
                            muteSoloState = muteSoloState,
                            viewModel = viewModel
                        )
                    }
                    
                    // Main step grid
                    StepGridSection(
                        currentPattern = currentPattern,
                        pads = pads,
                        sequencerState = sequencerState,
                        viewModel = viewModel
                    )
                    
                    // Pattern info
                    if (currentPattern != null) {
                        PatternInfoSection(
                            pattern = currentPattern,
                            sequencerState = sequencerState
                        )
                    }
                }
            }
            
            SequencerTab.SONG -> {
                SongModeContent(
                    sequencerState = sequencerState,
                    patterns = patterns,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            
            SequencerTab.MIXER -> {
                MixerContent(
                    pads = pads,
                    muteSoloState = muteSoloState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            
            SequencerTab.SETTINGS -> {
                SequencerSettingsContent(
                    sequencerState = sequencerState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Landscape layout for sequencer screen
 */
@Composable
private fun LandscapeSequencerLayout(
    paddingValues: PaddingValues,
    selectedTab: SequencerTab,
    onTabSelected: (SequencerTab) -> Unit,
    sequencerState: SequencerState,
    patterns: List<Pattern>,
    pads: List<SequencerPadInfo>,
    muteSoloState: MuteSoloState,
    currentPattern: Pattern?,
    viewModel: SequencerViewModel,
    transportExpanded: Boolean,
    onTransportExpandedChange: (Boolean) -> Unit,
    patternExpanded: Boolean,
    onPatternExpandedChange: (Boolean) -> Unit,
    padSelectorExpanded: Boolean,
    onPadSelectorExpandedChange: (Boolean) -> Unit,
    showPatternCreationDialog: Boolean,
    onShowPatternCreationDialog: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Side navigation rail
        NavigationRail(
            modifier = Modifier.fillMaxHeight()
        ) {
            SequencerTab.values().forEach { tab ->
                NavigationRailItem(
                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                    label = { Text(tab.title) },
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
        
        // Main content area
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left panel - Controls
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Transport controls
                CollapsibleSection(
                    title = "Transport",
                    expanded = transportExpanded,
                    onExpandedChange = onTransportExpandedChange
                ) {
                    TransportControlsSection(
                        sequencerState = sequencerState,
                        currentPattern = currentPattern,
                        viewModel = viewModel
                    )
                }
                
                // Pattern management
                CollapsibleSection(
                    title = "Pattern",
                    expanded = patternExpanded,
                    onExpandedChange = onPatternExpandedChange
                ) {
                    PatternManagementSection(
                        patterns = patterns,
                        sequencerState = sequencerState,
                        viewModel = viewModel,
                        onShowPatternCreationDialog = onShowPatternCreationDialog
                    )
                }
                
                // Pad selector
                CollapsibleSection(
                    title = "Tracks",
                    expanded = padSelectorExpanded,
                    onExpandedChange = onPadSelectorExpandedChange
                ) {
                    PadSelectorSection(
                        pads = pads,
                        sequencerState = sequencerState,
                        muteSoloState = muteSoloState,
                        viewModel = viewModel
                    )
                }
            }
            
            // Right panel - Main content
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    SequencerTab.PATTERN -> {
                        // Step grid
                        StepGridSection(
                            currentPattern = currentPattern,
                            pads = pads,
                            sequencerState = sequencerState,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Pattern info
                        if (currentPattern != null) {
                            PatternInfoSection(
                                pattern = currentPattern,
                                sequencerState = sequencerState
                            )
                        }
                    }
                    
                    SequencerTab.SONG -> {
                        SongModeContent(
                            sequencerState = sequencerState,
                            patterns = patterns,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    SequencerTab.MIXER -> {
                        MixerContent(
                            pads = pads,
                            muteSoloState = muteSoloState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    SequencerTab.SETTINGS -> {
                        SequencerSettingsContent(
                            sequencerState = sequencerState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
/**
 * 
Collapsible section component for space optimization
 */
@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header with expand/collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(
                    onClick = { onExpandedChange(!expanded) }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            // Collapsible content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Transport controls section
 */
@Composable
private fun TransportControlsSection(
    sequencerState: SequencerState,
    currentPattern: Pattern?,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main transport controls
        TransportControls(
            sequencerState = sequencerState,
            onTransportAction = viewModel::handleTransportAction,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Tempo and swing controls
        CompactTempoSwingControls(
            tempo = currentPattern?.tempo ?: 120f,
            swing = currentPattern?.swing ?: 0f,
            onTempoChange = { viewModel.handleTransportAction(TransportControlAction.SetTempo(it)) },
            onSwingChange = { viewModel.handleTransportAction(TransportControlAction.SetSwing(it)) },
            isPlaying = sequencerState.isPlaying,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Playback position indicator
        PlaybackPositionIndicator(
            currentStep = sequencerState.currentStep,
            patternLength = currentPattern?.length ?: 16,
            isPlaying = sequencerState.isActivelyPlaying,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Pattern management section
 */
@Composable
private fun PatternManagementSection(
    patterns: List<Pattern>,
    sequencerState: SequencerState,
    viewModel: SequencerViewModel,
    onShowPatternCreationDialog: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PatternSelector(
        patterns = patterns,
        currentPatternId = sequencerState.currentPattern,
        onPatternSelect = viewModel::selectPattern,
        onPatternCreate = { onShowPatternCreationDialog(true) },
        onPatternDuplicate = viewModel::duplicatePattern,
        onPatternDelete = viewModel::deletePattern,
        onPatternRename = viewModel::renamePattern,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Pad selector section
 */
@Composable
private fun PadSelectorSection(
    pads: List<SequencerPadInfo>,
    sequencerState: SequencerState,
    muteSoloState: MuteSoloState,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    PadSelector(
        pads = pads,
        selectedPads = sequencerState.selectedPads,
        mutedPads = muteSoloState.mutedTracks,
        soloedPads = muteSoloState.soloedTracks,
        onPadSelect = viewModel::togglePadSelection,
        onPadMute = viewModel::togglePadMute,
        onPadSolo = viewModel::togglePadSolo,
        onShowAll = viewModel::selectAllPads,
        onShowAssigned = viewModel::selectAssignedPads,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Step grid section
 */
@Composable
private fun StepGridSection(
    currentPattern: Pattern?,
    pads: List<SequencerPadInfo>,
    sequencerState: SequencerState,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Step Grid",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            StepGrid(
                pattern = currentPattern,
                pads = pads,
                currentStep = sequencerState.currentStep,
                selectedPads = sequencerState.selectedPads,
                onStepToggle = viewModel::toggleStep,
                onStepVelocityChange = viewModel::setStepVelocity,
                onPadSelect = viewModel::togglePadSelection,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Song mode content
 */
@Composable
private fun SongModeContent(
    sequencerState: SequencerState,
    patterns: List<Pattern>,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Song Mode",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Song arrangement interface would go here
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Song Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Chain patterns together to create complete songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* TODO: Implement song mode */ }
                ) {
                    Text("Create Song")
                }
            }
        }
    }
}

/**
 * Mixer content
 */
@Composable
private fun MixerContent(
    pads: List<SequencerPadInfo>,
    muteSoloState: MuteSoloState,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Mixer",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Mixer interface would go here
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mixer",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Control volume, pan, and effects for each track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sequencer settings content
 */
@Composable
private fun SequencerSettingsContent(
    sequencerState: SequencerState,
    viewModel: SequencerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sequencer Settings",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Settings interface would go here
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Configure sequencer preferences and defaults",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Pattern information and statistics section
 */
@Composable
private fun PatternInfoSection(
    pattern: Pattern,
    sequencerState: SequencerState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pattern Info",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Length: ${pattern.length} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tempo: ${pattern.tempo.toInt()} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column {
                    Text(
                        text = "Swing: ${(pattern.swing * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Active Steps: ${pattern.steps.values.flatten().count { it.isActive }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}