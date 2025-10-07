package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.model.*
import com.high.theone.features.sampling.PerformanceMonitor
import com.high.theone.ui.layout.ResponsiveLayoutUtils
import com.high.theone.features.compactui.accessibility.*
import com.high.theone.features.compactui.animations.*
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.sequencer.SimpleSequencerViewModel
import com.high.theone.features.midi.ui.MidiSettingsViewModel
import com.high.theone.features.midi.ui.MidiMappingViewModel
import com.high.theone.features.midi.ui.MidiMonitorViewModel
import com.high.theone.features.compactui.PreferenceManager
import com.high.theone.features.compactui.CompactMainScreenEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * CompactMainScreen - Root component that combines all UI components into a unified main screen * 
 * This component implements the complete compact main UI system by:
 * - Combining all components (transport, drum pads, sequencer, panels) into unified interface
 * - Implementing component communication and state sharing through CompactMainViewModel
 * - Adding error handling and performance monitoring
 * - Providing responsive layout that adapts to different screen configurations
 * 
 * Requirements addressed:
 * - 1.1: All essential controls accessible on single main screen
 * - 1.2: Access to sampling, sequencing, drum pads, MIDI controls, transport without navigation
 * - 1.3: Collapsible sections, tabs, overlay panels for space utilization
 * - 1.4: Immediate response without screen transitions
 * - 8.1: Performance monitoring and optimization
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactMainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // Get all ViewModels using Hilt
    val drumTrackViewModel: DrumTrackViewModel = hiltViewModel()
    val sequencerViewModel: SimpleSequencerViewModel = hiltViewModel()
    val midiSettingsViewModel: MidiSettingsViewModel = hiltViewModel()
    val midiMappingViewModel: MidiMappingViewModel = hiltViewModel()
    val midiMonitorViewModel: MidiMonitorViewModel = hiltViewModel()
    
    // Get Hilt dependencies through EntryPoint
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext as Application,
            CompactMainScreenEntryPoint::class.java
        )
    }
    val performanceMonitor = remember { entryPoint.performanceMonitor() }
    val preferenceManager = remember { entryPoint.preferenceManager() }
    val performanceOptimizer = remember { entryPoint.performanceOptimizer() }
    
    // Create CompactMainViewModel manually with all dependencies
    val viewModel = remember {
        CompactMainViewModel(
            performanceMonitor = performanceMonitor,
            drumTrackViewModel = drumTrackViewModel,
            sequencerViewModel = sequencerViewModel,
            midiSettingsViewModel = midiSettingsViewModel,
            midiMappingViewModel = midiMappingViewModel,
            midiMonitorViewModel = midiMonitorViewModel,
            preferenceManager = preferenceManager
        )
    }
    // Collect UI state
    val compactUIState by viewModel.compactUIState.collectAsState()
    val layoutState by viewModel.layoutState.collectAsState()
    val transportState by viewModel.transportState.collectAsState()
    val panelStates by viewModel.panelStates.collectAsState()
    val drumPadState by viewModel.drumPadState.collectAsState()
    val sequencerState by viewModel.sequencerState.collectAsState()
    val midiState by viewModel.midiState.collectAsState()
    
    // Performance monitoring and optimization
    val performanceMetrics by viewModel.compactUIState.map { it.performanceMetrics }.collectAsState(PerformanceMetrics())
    val optimizationState by performanceOptimizer.optimizationState.collectAsState()
    
    // Initialize performance optimizer
    val optimizerInstance = rememberCompactUIPerformanceOptimizer(performanceOptimizer)
    
    // Lazy panel content manager
    val lazyPanelManager = remember { LazyPanelContentManager() }
    
    // Screen configuration
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenConfiguration = ResponsiveLayoutUtils.calculateScreenConfiguration()
    
    // Update screen configuration when it changes
    LaunchedEffect(screenConfiguration) {
        viewModel.updateScreenConfiguration(screenConfiguration)
    }
    
    // Error handling state
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Handle errors from UI state
    LaunchedEffect(compactUIState.errorState) {
        compactUIState.errorState?.let { error ->
            errorMessage = error
        }
    }
    
    // Performance monitoring - record frame time on each recomposition
    LaunchedEffect(Unit) {
        viewModel.recordFrameTime()
    }
    
    // Apply performance optimizations based on current state
    LaunchedEffect(optimizationState) {
        if (optimizationState.activeOptimizations.contains(OptimizationType.UNLOAD_INACTIVE_PANELS)) {
            // Unload inactive panels to save memory
            panelStates.forEach { (panelType, panelState) ->
                if (!panelState.isVisible) {
                    lazyPanelManager.unloadPanel(panelType)
                }
            }
        }
        
        if (optimizationState.activeOptimizations.contains(OptimizationType.AGGRESSIVE_MEMORY_CLEANUP)) {
            // Force garbage collection
            System.gc()
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (!compactUIState.isInitialized) {
            // Loading state
            CompactMainScreenLoadingState()
        } else {
            // Main content
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Transport control bar - always visible at top
                TransportControlBar(
                    transportState = transportState,
                    onPlayPause = { 
                        viewModel.onPlayPause()
                        viewModel.recordFrameTime()
                    },
                    onStop = { 
                        viewModel.onStop()
                        viewModel.recordFrameTime()
                    },
                    onRecord = { 
                        viewModel.onRecord()
                        viewModel.recordFrameTime()
                    },
                    onBpmChange = { bpm -> 
                        viewModel.onBpmChange(bpm)
                        viewModel.recordFrameTime()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Main responsive layout container
                ResponsiveMainLayout(
                    layoutState = layoutState,
                    drumPadContent = {
                        CompactDrumPadContent(
                            drumPadState = drumPadState,
                            midiState = midiState,
                            screenConfiguration = screenConfiguration,
                            onPadTap = { padId, velocity ->
                                viewModel.onPadTriggered(padId)
                                viewModel.recordFrameTime()
                            },
                            onPadLongPress = { padId ->
                                // Show pad configuration panel
                                viewModel.showPanel(PanelType.SAMPLING)
                                viewModel.recordFrameTime()
                            }
                        )
                    },
                    sequencerContent = {
                        InlineSequencerContent(
                            sequencerState = sequencerState,
                            drumPadState = drumPadState,
                            onStepToggle = { padId, stepIndex ->
                                viewModel.toggleStep(padId, stepIndex)
                                viewModel.recordFrameTime()
                            },
                            onStepLongPress = { padId, stepIndex ->
                                // Show step editor in bottom sheet
                                viewModel.showPanel(PanelType.SAMPLE_EDITOR)
                                viewModel.recordFrameTime()
                            },
                            onPatternSelect = { patternId ->
                                viewModel.selectPattern(patternId)
                                viewModel.recordFrameTime()
                            },
                            onTrackMute = { padId ->
                                viewModel.togglePadMute(padId)
                                viewModel.recordFrameTime()
                            },
                            onTrackSolo = { padId ->
                                viewModel.togglePadSolo(padId)
                                viewModel.recordFrameTime()
                            }
                        )
                    },
                    utilityContent = {
                        UtilityPanelContent(
                            midiState = midiState,
                            performanceMetrics = performanceMetrics,
                            onShowMidiPanel = {
                                viewModel.showPanel(PanelType.MIDI)
                                viewModel.recordFrameTime()
                            },
                            onShowMixerPanel = {
                                viewModel.showPanel(PanelType.MIXER)
                                viewModel.recordFrameTime()
                            },
                            onShowSettingsPanel = {
                                viewModel.showPanel(PanelType.SETTINGS)
                                viewModel.recordFrameTime()
                            }
                        )
                    },
                    onLayoutModeChange = { layoutMode ->
                        // Handle layout mode changes if needed
                        viewModel.recordFrameTime()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
            
            // Quick access panels (side panels for landscape, bottom sheet for portrait)
            CompactMainScreenPanels(
                panelStates = panelStates,
                layoutState = layoutState,
                optimizationState = optimizationState,
                lazyPanelManager = lazyPanelManager,
                onPanelStateChange = { panelType, newState ->
                    if (newState.isVisible) {
                        viewModel.showPanel(panelType)
                        lazyPanelManager.markPanelLoaded(panelType)
                    } else {
                        viewModel.hidePanel(panelType)
                        // Unload panel if optimization is active
                        if (optimizationState.activeOptimizations.contains(OptimizationType.LAZY_LOAD_PANELS)) {
                            lazyPanelManager.unloadPanel(panelType)
                        }
                    }
                    viewModel.recordFrameTime()
                },
                onPanelTypeChange = { oldType, newType ->
                    viewModel.hidePanel(oldType)
                    viewModel.showPanel(newType)
                    lazyPanelManager.unloadPanel(oldType)
                    lazyPanelManager.markPanelLoaded(newType)
                    viewModel.recordFrameTime()
                }
            )
        }
        
        // Error handling overlay
        errorMessage?.let { error ->
            CompactMainScreenErrorOverlay(
                errorMessage = error,
                onDismiss = { errorMessage = null },
                onRetry = {
                    errorMessage = null
                    // Trigger state refresh
                    coroutineScope.launch {
                        viewModel.recordFrameTime()
                    }
                }
            )
        }
        
        // Performance monitoring overlay (only when performance is poor)
        if (performanceMetrics.frameRate < 50f || optimizationState.performanceLevel == PerformanceLevel.DEGRADED) {
            PerformanceWarningOverlay(
                performanceMetrics = performanceMetrics,
                optimizationState = optimizationState,
                onOptimize = {
                    viewModel.optimizePerformance()
                    performanceOptimizer.forceMemoryCleanup()
                    viewModel.recordFrameTime()
                },
                onApplyOptimization = { optimizationType ->
                    performanceOptimizer.applyOptimization(optimizationType)
                    viewModel.recordFrameTime()
                }
            )
        }
    }
}

/**
 * Loading state for CompactMainScreen while initialization is in progress
 */
@Composable
private fun CompactMainScreenLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Initializing Compact UI...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Drum pad content wrapper with error handling
 */
@Composable
private fun CompactDrumPadContent(
    drumPadState: DrumPadState,
    midiState: MidiState,
    screenConfiguration: ScreenConfiguration,
    onPadTap: (String, Float) -> Unit,
    onPadLongPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert drum pad state to pad states for the grid
    val padStates = remember(drumPadState) {
        (0..15).map { index ->
            val padId = index.toString()
            val padSettings = drumPadState.padSettings[padId]
            
            PadState(
                index = index,
                sampleId = padSettings?.sampleId,
                sampleName = padSettings?.sampleName,
                hasAssignedSample = padSettings?.sampleId != null,
                isPlaying = drumPadState.isPlaying && drumPadState.activePadId == padId,
                isLoading = false, // Would come from actual loading state
                volume = padSettings?.volume ?: 1.0f,
                pan = padSettings?.pan ?: 0.0f,
                playbackMode = padSettings?.playbackMode ?: PlaybackMode.ONE_SHOT,
                lastTriggerVelocity = 0.0f,
                chokeGroup = padSettings?.muteGroup,
                isEnabled = true,
                midiNote = null, // Would come from MIDI mapping
                midiChannel = 0,
                midiVelocitySensitivity = 1.0f,
                acceptsAllChannels = false,
                lastMidiTrigger = 0L
            )
        }
    }
    
    // MIDI highlighted pads (would come from MIDI state)
    val midiHighlightedPads = remember(midiState) {
        // In a real implementation, this would track which pads are currently
        // being triggered by MIDI input
        emptySet<Int>()
    }
    
    CompactDrumPadGrid(
        pads = padStates,
        onPadTap = { padIndex, velocity ->
            onPadTap(padIndex.toString(), velocity)
        },
        onPadLongPress = { padIndex ->
            onPadLongPress(padIndex.toString())
        },
        screenConfiguration = screenConfiguration,
        enabled = true,
        showSampleNames = true,
        showWaveformPreviews = true,
        midiHighlightedPads = midiHighlightedPads,
        modifier = modifier
    )
}

/**
 * Inline sequencer content wrapper
 */
@Composable
private fun InlineSequencerContent(
    sequencerState: SequencerState,
    drumPadState: DrumPadState,
    onStepToggle: (Int, Int) -> Unit,
    onStepLongPress: (Int, Int) -> Unit,
    onPatternSelect: (String) -> Unit,
    onTrackMute: (Int) -> Unit,
    onTrackSolo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert states to required format for InlineSequencer
    val availableTracks = remember(drumPadState) {
        drumPadState.padSettings.entries.mapIndexed { index, (padId, settings) ->
            SequencerPadInfo(
                index = index,
                sampleId = settings.sampleId,
                sampleName = settings.sampleName,
                hasAssignedSample = settings.sampleId != null,
                isEnabled = true,
                volume = settings.volume,
                canTrigger = settings.sampleId != null
            )
        }
    }
    
    val selectedTracks = remember(sequencerState) {
        sequencerState.selectedPads
    }
    
    val muteSoloState = remember(sequencerState) {
        TrackMuteSoloState(
            mutedTracks = sequencerState.mutedPads,
            soloedTracks = sequencerState.soloedPads
        )
    }
    
    InlineSequencer(
        sequencerState = sequencerState,
        currentPattern = null, // sequencerState.currentPattern as Pattern? - need to convert
        availablePatterns = emptyList(), // sequencerState.patterns.map { Pattern(id = it, name = it, length = 16) } - need to convert
        availableTracks = availableTracks,
        selectedTracks = selectedTracks,
        muteSoloState = muteSoloState,
        onStepToggle = onStepToggle,
        onStepLongPress = onStepLongPress,
        onTrackSelect = { trackId ->
            // Handle track selection
        },
        onTrackMute = onTrackMute,
        onTrackSolo = onTrackSolo,
        onPatternSelect = onPatternSelect,
        onPatternCreate = { name, length ->
            // Handle pattern creation
        },
        onPatternDuplicate = { patternId ->
            // Handle pattern duplication
        },
        onPatternDelete = { patternId ->
            // Handle pattern deletion
        },
        onPatternRename = { patternId, newName ->
            // Handle pattern renaming
        },
        onSelectAllTracks = {
            // Handle select all tracks
        },
        onSelectAssignedTracks = {
            // Handle select assigned tracks
        },
        onClearTrackSelection = {
            // Handle clear track selection
        },
        modifier = modifier
    )
}

/**
 * Utility panel content with quick access buttons
 */
@Composable
private fun UtilityPanelContent(
    midiState: MidiState,
    performanceMetrics: PerformanceMetrics,
    onShowMidiPanel: () -> Unit,
    onShowMixerPanel: () -> Unit,
    onShowSettingsPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MIDI status and quick access
        IconButton(
            onClick = onShowMidiPanel
        ) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = if (midiState.isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ) {
                        Text(
                            text = midiState.connectedDevices.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "MIDI"
                )
            }
        }
        
        // Mixer quick access
        IconButton(
            onClick = onShowMixerPanel
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Mixer"
            )
        }
        
        // Settings quick access
        IconButton(
            onClick = onShowSettingsPanel
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
            )
        }
        
        // Performance indicator
        if (performanceMetrics.frameRate < 55f) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Performance Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Panel system for quick access panels with performance optimization
 */
@Composable
private fun CompactMainScreenPanels(
    panelStates: Map<PanelType, PanelState>,
    layoutState: LayoutState,
    optimizationState: OptimizationState,
    lazyPanelManager: LazyPanelContentManager,
    onPanelStateChange: (PanelType, PanelState) -> Unit,
    onPanelTypeChange: (PanelType, PanelType) -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine panel display mode based on layout
    val useBottomSheet = layoutState.configuration.layoutMode in listOf(
        LayoutMode.COMPACT_PORTRAIT,
        LayoutMode.STANDARD_PORTRAIT
    )
    
    if (useBottomSheet) {
        // Use adaptive bottom sheet for portrait modes
        val bottomSheetController = rememberBottomSheetController()
        
        // Find active panel
        val activePanel = panelStates.entries.find { it.value.isVisible }
        
        LaunchedEffect(activePanel) {
            activePanel?.let { (panelType, panelState) ->
                bottomSheetController.show(
                    contentType = panelType,
                    snapPosition = panelState.snapPosition
                )
            } ?: bottomSheetController.hide()
        }
        
        AdaptiveBottomSheet(
            state = bottomSheetController.state,
            onStateChange = { newState ->
                activePanel?.let { (panelType, _) ->
                    onPanelStateChange(panelType, PanelState(
                        isVisible = newState.isVisible,
                        isExpanded = newState.isExpanded,
                        snapPosition = newState.snapPosition,
                        contentType = panelType
                    ))
                }
            },
            content = { panelType ->
                BottomSheetContentSwitcher(
                    contentType = panelType,
                    modifier = Modifier.fillMaxSize()
                )
            },
            modifier = modifier
        )
    } else {
        // Use side panels for landscape and tablet modes
        panelStates.forEach { (panelType, panelState) ->
            if (panelState.isVisible) {
                // Check if panel should be loaded based on optimization state
                val shouldLoad = lazyPanelManager.shouldLoadPanel(
                    panelType = panelType,
                    isVisible = panelState.isVisible,
                    optimizationState = optimizationState
                )
                
                if (shouldLoad) {
                    QuickAccessPanel(
                        panelState = panelState,
                        panelType = panelType,
                        onPanelStateChange = { newState ->
                            onPanelStateChange(panelType, newState)
                        },
                        onPanelTypeChange = { newType ->
                            onPanelTypeChange(panelType, newType)
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) { currentPanelType ->
                        // Use lazy loading for panel content
                        LazyPanelContent(
                            panelType = currentPanelType,
                            optimizationState = optimizationState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Error overlay for handling and displaying errors
 */
@Composable
private fun CompactMainScreenErrorOverlay(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dismiss")
                    }
                    
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * Performance warning overlay with optimization options
 */
@Composable
private fun PerformanceWarningOverlay(
    performanceMetrics: PerformanceMetrics,
    optimizationState: OptimizationState,
    onOptimize: () -> Unit,
    onApplyOptimization: (OptimizationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Performance Warning",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Performance: ${performanceMetrics.frameRate.toInt()}fps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (optimizationState.activeOptimizations.isNotEmpty()) {
                        Text(
                            text = "${optimizationState.activeOptimizations.size} optimizations active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!optimizationState.activeOptimizations.contains(OptimizationType.REDUCE_VISUAL_EFFECTS)) {
                    TextButton(
                        onClick = { onApplyOptimization(OptimizationType.REDUCE_VISUAL_EFFECTS) }
                    ) {
                        Text("Reduce Effects", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onOptimize) {
                    Text("Auto Optimize", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
/**
 * 
Lazy loading panel content with performance optimization
 */
@Composable
private fun LazyPanelContent(
    panelType: PanelType,
    optimizationState: OptimizationState,
    modifier: Modifier = Modifier
) {
    // Use lazy loading if optimization is active
    if (optimizationState.activeOptimizations.contains(OptimizationType.LAZY_LOAD_PANELS)) {
        // Load content only when needed
        LaunchedEffect(panelType) {
            // Simulate lazy loading delay
            kotlinx.coroutines.delay(100)
        }
    }
    
    // Reduce visual effects if optimization is active
    val enableAnimations = !optimizationState.activeOptimizations.contains(OptimizationType.DISABLE_ANIMATIONS)
    val enableVisualEffects = !optimizationState.activeOptimizations.contains(OptimizationType.REDUCE_VISUAL_EFFECTS)
    
    Box(modifier = modifier) {
        when (panelType) {
            PanelType.SAMPLING -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Simplified content for performance
                    SimplifiedSamplingPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.MIDI -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SimplifiedMidiPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.MIXER -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SimplifiedMixerPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.SETTINGS -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SimplifiedSettingsPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.SAMPLE_EDITOR -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SimplifiedSampleEditorPanel(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Simplified panel content for performance optimization
 */
@Composable
private fun SimplifiedSamplingPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sampling",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Performance mode - reduced features",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { /* Basic sampling action */ }) {
            Text("Record")
        }
    }
}

@Composable
private fun SimplifiedMidiPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "MIDI",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Performance mode - basic controls",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { /* Basic MIDI action */ }) {
            Text("Settings")
        }
    }
}

@Composable
private fun SimplifiedMixerPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Mixer",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Performance mode - essential controls",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { /* Basic mixer action */ }) {
            Text("Levels")
        }
    }
}

@Composable
private fun SimplifiedSettingsPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Performance mode - basic settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { /* Basic settings action */ }) {
            Text("Audio")
        }
    }
}

@Composable
private fun SimplifiedSampleEditorPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sample Editor",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Performance mode - basic editing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { /* Basic editor action */ }) {
            Text("Trim")
        }
    }
}