package com.high.theone.features.compactui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.high.theone.features.sampling.RecordingControls
import com.high.theone.features.compactui.performance.CompactPerformanceIndicator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import kotlin.math.log2
import kotlin.math.pow
import com.high.theone.model.FilterMode
import com.high.theone.model.FilterSettings

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
    val samplingViewModel: com.high.theone.features.sampling.SamplingViewModel = hiltViewModel()
    
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
    val errorHandlingSystem = remember { entryPoint.errorHandlingSystem() }
    val permissionManager = remember { entryPoint.permissionManager() }
    val audioEngineRecovery = remember { entryPoint.audioEngineRecovery() }
    val storageManager = remember { entryPoint.storageManager() }
    val recordingPerformanceMonitor = remember { entryPoint.recordingPerformanceMonitor() }
    val recordingMemoryManager = remember { entryPoint.recordingMemoryManager() }
    val recordingFrameRateMonitor = remember { entryPoint.recordingFrameRateMonitor() }
    
    // Get CompactMainViewModel using Hilt
    val viewModel: CompactMainViewModel = hiltViewModel()
    // Collect UI state
    val compactUIState by viewModel.compactUIState.collectAsState()
    val layoutState by viewModel.layoutState.collectAsState()
    val transportState by viewModel.transportState.collectAsState()
    val panelStates by viewModel.panelStates.collectAsState()
    val drumPadState by viewModel.drumPadState.collectAsState()
    val sequencerState by viewModel.sequencerState.collectAsState()
    val midiState by viewModel.midiState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val isRecordingPanelVisible by viewModel.isRecordingPanelVisible.collectAsState()
    
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

    // Track which pad was long-pressed to show its config sheet
    var selectedPadId by remember { mutableStateOf<String?>(null) }
    
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

    // Sync DrumTrackViewModel state into CompactMainViewModel so drumPadState is populated
    LaunchedEffect(Unit) {
        combine(
            drumTrackViewModel.padSettingsMap,
            drumTrackViewModel.activePadId,
            drumTrackViewModel.isPlaying,
            drumTrackViewModel.isRecording
        ) { padSettings, activePadId, isPlaying, isRecording ->
            DrumPadState(
                padSettings = padSettings,
                isPlaying = isPlaying,
                isRecording = isRecording,
                activePadId = activePadId
            )
        }.collect { state ->
            viewModel.updateDrumPadState(state)
        }
    }

    // Sync SimpleSequencerViewModel state into CompactMainViewModel
    LaunchedEffect(Unit) {
        sequencerViewModel.sequencerState.collect { seqState ->
            viewModel.updateSequencerState(seqState)
        }
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
    
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
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
                    onRecordingToggle = {
                        if (recordingState.isRecording) {
                            viewModel.stopRecording()
                        } else if (recordingState.canStartRecording) {
                            viewModel.startRecording()
                        }
                        
                        // Also toggle panel visibility
                        if (isRecordingPanelVisible) {
                            viewModel.hideRecordingPanel()
                        } else {
                            viewModel.showRecordingPanel()
                        }
                        viewModel.recordFrameTime()
                    },
                    isRecordingPanelVisible = isRecordingPanelVisible,
                    recordingState = recordingState,
                    onSettingsClick = {
                        navController.navigate("project_settings")
                        viewModel.recordFrameTime()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Drum pads - directly under transport controls, filling width
                CompactDrumPadContent(
                    drumPadState = drumPadState,
                    midiState = midiState,
                    screenConfiguration = screenConfiguration,
                    onPadTap = { padId, velocity ->
                        drumTrackViewModel.onPadTriggered(padId, velocity)
                        viewModel.recordFrameTime()
                    },
                    onPadLongPress = { padId ->
                        // Open per-pad config sheet for this specific pad
                        selectedPadId = padId
                        viewModel.recordFrameTime()
                    }
                )
                
                // Sequencer and other content below
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
            }
            
            // Enhanced recording panel integration with animations
            if (isRecordingPanelVisible) {
                CompactRecordingPanelIntegration(
                    recordingState = recordingState,
                    drumPadState = drumPadState,
                    screenConfiguration = screenConfiguration,
                    onStartRecording = {
                        viewModel.startRecording()
                        viewModel.recordFrameTime()
                    },
                    onStopRecording = {
                        viewModel.stopRecording()
                        viewModel.recordFrameTime()
                    },
                    onAssignToPad = { padId ->
                        viewModel.assignRecordedSampleToPad(padId)
                        viewModel.recordFrameTime()
                    },
                    onDiscardRecording = {
                        viewModel.discardRecording()
                        viewModel.recordFrameTime()
                    },
                    onHidePanel = {
                        viewModel.hideRecordingPanel()
                        viewModel.recordFrameTime()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Quick pad assignment flow overlay
            QuickPadAssignmentFlow(
                recordingState = recordingState,
                drumPadState = drumPadState,
                screenConfiguration = screenConfiguration,
                onAssignToPad = { padId ->
                    viewModel.assignRecordedSampleToPad(padId)
                    viewModel.recordFrameTime()
                },
                onCancel = {
                    viewModel.exitAssignmentMode()
                    viewModel.recordFrameTime()
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Pad configuration bottom sheet — opens when a pad is long-pressed
            val padSettingsForConfig by drumTrackViewModel.padSettingsMap.collectAsState()
            val configuredPadSettings = selectedPadId?.let { padSettingsForConfig[it] }
            if (selectedPadId != null && configuredPadSettings != null) {
                PadConfigBottomSheet(
                    padSettings = configuredPadSettings,
                    onSave = { updated ->
                        drumTrackViewModel.updatePadSettings(updated)
                        selectedPadId = null
                    },
                    onDismiss = { selectedPadId = null }
                )
            }

            // Quick access panels (side panels for landscape, bottom sheet for portrait)
            CompactMainScreenPanels(
                panelStates = panelStates,
                layoutState = layoutState,
                optimizationState = optimizationState,
                lazyPanelManager = lazyPanelManager,
                onNavigateToMidiSettings = { navController.navigate("midi_settings") },
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
        
        // Performance monitoring UI - collect performance state
        // val recordingPerformanceState by viewModel.recordingPerformanceState.collectAsState()
        // val performanceWarnings by viewModel.performanceWarnings.collectAsState()
        // val optimizationSuggestions by viewModel.optimizationSuggestions.collectAsState()

        // Performance warning UI - shows during recording when performance issues detected
        // if (recordingState.isRecording && (performanceWarnings.isNotEmpty() || optimizationSuggestions.isNotEmpty())) {
        //     PerformanceWarningUI(
        //         warnings = performanceWarnings,
        //         suggestions = optimizationSuggestions,
        //         performanceState = recordingPerformanceState,
        //         onApplyOptimization = { optimizationType ->
        //             viewModel.applyPerformanceOptimization(optimizationType)
        //         },
        //         onDismissWarning = { warningType ->
        //             viewModel.dismissPerformanceWarning(warningType)
        //         },
        //         modifier = Modifier
        //             .align(Alignment.TopEnd)
        //             .padding(16.dp)
        //             .widthIn(max = 300.dp)
        //     )
        // }

        // Compact performance indicator - always visible in top-right corner
        // CompactPerformanceIndicator(
        //     performanceState = recordingPerformanceState,
        //     onClick = {
        //         // Could expand to show detailed performance metrics
        //         viewModel.recordFrameTime()
        //     },
        //     modifier = Modifier
        //         .align(Alignment.TopEnd)
        //         .padding(8.dp)
        // )
        
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
    onNavigateToMidiSettings: () -> Unit = {},
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
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToMidiSettings = onNavigateToMidiSettings
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
                            modifier = Modifier.fillMaxSize(),
                            onNavigateToMidiSettings = onNavigateToMidiSettings
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
 * Recording panel integration with responsive layout
 */
@Composable
private fun CompactRecordingPanelIntegration(
    recordingState: IntegratedRecordingState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine layout based on screen configuration
    when (screenConfiguration.layoutMode) {
        LayoutMode.COMPACT_PORTRAIT -> {
            // Bottom sheet style for compact portrait
            CompactRecordingPanel(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
        LayoutMode.STANDARD_PORTRAIT -> {
            // Inline panel for standard portrait
            StandardRecordingPanel(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
        LayoutMode.LANDSCAPE, LayoutMode.TABLET -> {
            // Side panel for landscape and tablet
            SideRecordingPanel(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAssignToPad = onAssignToPad,
                onDiscardRecording = onDiscardRecording,
                onHidePanel = onHidePanel,
                modifier = modifier
            )
        }
    }
}

/**
 * Compact recording panel for small portrait screens
 */
@Composable
private fun CompactRecordingPanel(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onHidePanel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Recording Panel"
                    )
                }
            }
            
            // Enhanced Recording controls with real-time updates
            EnhancedRecordingControls(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Quick pad assignment if recording is complete
            if (recordingState.recordedSampleId != null && recordingState.availablePadsForAssignment.isNotEmpty()) {
                QuickPadAssignment(
                    availablePads = recordingState.availablePadsForAssignment,
                    onAssignToPad = onAssignToPad,
                    onDiscard = onDiscardRecording,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Standard recording panel for normal portrait screens
 */
@Composable
private fun StandardRecordingPanel(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enhanced Recording controls with real-time updates
            EnhancedRecordingControls(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                modifier = Modifier.weight(1f)
            )
            
            // Quick actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recordingState.recordedSampleId != null) {
                    Button(
                        onClick = { onAssignToPad(recordingState.availablePadsForAssignment.firstOrNull() ?: "0") },
                        enabled = recordingState.availablePadsForAssignment.isNotEmpty()
                    ) {
                        Text("Assign")
                    }
                }
                
                IconButton(onClick = onHidePanel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Recording Panel"
                    )
                }
            }
        }
    }
}

/**
 * Side recording panel for landscape and tablet layouts
 */
@Composable
private fun SideRecordingPanel(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onHidePanel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Recording Panel"
                    )
                }
            }
            
            // Enhanced Recording controls with real-time updates
            EnhancedRecordingControls(
                recordingState = recordingState,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Pad assignment section
            if (recordingState.recordedSampleId != null && recordingState.availablePadsForAssignment.isNotEmpty()) {
                HorizontalDivider()
                
                Text(
                    text = "Assign to Pad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recordingState.availablePadsForAssignment) { padId ->
                        OutlinedButton(
                            onClick = { onAssignToPad(padId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pad ${padId.toIntOrNull()?.plus(1) ?: padId}")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                OutlinedButton(
                    onClick = onDiscardRecording,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard Recording")
                }
            }
        }
    }
}

/**
 * Quick pad assignment component for compact layouts
 */
@Composable
private fun QuickPadAssignment(
    availablePads: List<String>,
    onAssignToPad: (String) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Assign to Pad",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availablePads.take(8)) { padId -> // Show max 8 pads in compact view
                AssistChip(
                    onClick = { onAssignToPad(padId) },
                    label = { Text("${padId.toIntOrNull()?.plus(1) ?: padId}") }
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Discard")
            }
            
            if (availablePads.isNotEmpty()) {
                Button(
                    onClick = { onAssignToPad(availablePads.first()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Auto Assign")
                }
            }
        }
    }
}

/**
 * Enhanced recording controls that work directly with IntegratedRecordingState
 * and provide real-time level meter updates and duration display
 */
@Composable
private fun EnhancedRecordingControls(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Recording Status Header
        EnhancedRecordingStatusHeader(recordingState = recordingState)
        
        // Main Record Button with enhanced animations
        EnhancedRecordButton(
            recordingState = recordingState,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording
        )
        
        // Real-time Level Meter (only show when recording or has levels)
        if (recordingState.isRecording || recordingState.peakLevel > 0.0f) {
            EnhancedLevelMeter(
                peakLevel = recordingState.peakLevel,
                averageLevel = recordingState.averageLevel,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Duration Display with enhanced formatting
        if (recordingState.isRecording || recordingState.durationMs > 0) {
            EnhancedDurationDisplay(
                recordingState = recordingState,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Error Display
        recordingState.error?.let { error ->
            EnhancedErrorMessage(
                error = error,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Enhanced recording status header with better visual feedback
 */
@Composable
private fun EnhancedRecordingStatusHeader(
    recordingState: IntegratedRecordingState
) {
    val statusText = when {
        recordingState.isProcessing -> "Processing..."
        recordingState.isRecording -> "Recording"
        recordingState.durationMs > 0 -> "Ready"
        recordingState.error != null -> "Error"
        !recordingState.canStartRecording -> "Initializing..."
        else -> "Ready to Record"
    }
    
    val statusColor = when {
        recordingState.isProcessing -> MaterialTheme.colorScheme.tertiary
        recordingState.isRecording -> MaterialTheme.colorScheme.error
        recordingState.error != null -> MaterialTheme.colorScheme.error
        !recordingState.canStartRecording -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    
    // Pulsing animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse_alpha"
    )
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    statusColor.copy(
                        alpha = if (recordingState.isRecording) pulseAlpha else 1f
                    )
                )
        )
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor.copy(
                alpha = if (recordingState.isRecording) pulseAlpha else 1f
            ),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Enhanced record button with improved animations and visual feedback
 */
@Composable
private fun EnhancedRecordButton(
    recordingState: IntegratedRecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    // Enhanced pulsing animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "record_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val buttonSize = 80.dp
    val buttonColor = when {
        recordingState.isProcessing -> MaterialTheme.colorScheme.tertiary
        recordingState.isRecording -> MaterialTheme.colorScheme.error
        !recordingState.canStartRecording -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    
    val buttonAlpha = if (recordingState.isRecording) pulseAlpha else 1.0f
    val buttonScale = if (recordingState.isRecording) pulseScale else 1.0f
    
    FloatingActionButton(
        onClick = {
            if (recordingState.isRecording) {
                onStopRecording()
            } else if (recordingState.canStartRecording) {
                onStartRecording()
            }
        },
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        containerColor = buttonColor.copy(alpha = buttonAlpha),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (recordingState.isRecording) 12.dp else 6.dp
        )
    ) {
        Icon(
            imageVector = if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (recordingState.isRecording) "Stop Recording" else "Start Recording",
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Enhanced level meter with improved visualization and smooth animations
 */
@Composable
private fun EnhancedLevelMeter(
    peakLevel: Float,
    averageLevel: Float,
    modifier: Modifier = Modifier
) {
    // Animate level changes for smoother visualization
    val animatedPeakLevel by animateFloatAsState(
        targetValue = peakLevel,
        animationSpec = tween(durationMillis = 50),
        label = "peak_level"
    )
    
    val animatedAverageLevel by animateFloatAsState(
        targetValue = averageLevel,
        animationSpec = tween(durationMillis = 100),
        label = "average_level"
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Input Level",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawEnhancedLevelMeter(
                    peakLevel = animatedPeakLevel,
                    averageLevel = animatedAverageLevel,
                    size = size
                )
            }
        }
        
        // Enhanced level indicators with percentage and dB values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peak: ${(animatedPeakLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (animatedPeakLevel > 0.9f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "Avg: ${(animatedAverageLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Enhanced duration display with better formatting and progress indication
 */
@Composable
private fun EnhancedDurationDisplay(
    recordingState: IntegratedRecordingState,
    modifier: Modifier = Modifier
) {
    val maxDurationMs = 30_000L // 30 seconds max
    val isNearMaxDuration = recordingState.durationMs > (maxDurationMs * 0.9f)
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Enhanced duration text with millisecond precision
        Text(
            text = recordingState.formattedDuration,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isNearMaxDuration) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        
        // Progress bar for max duration with smooth animation
        if (recordingState.isRecording) {
            val progress = (recordingState.durationMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 100),
                label = "duration_progress"
            )
            
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isNearMaxDuration) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Time remaining indicator
            if (isNearMaxDuration) {
                val remainingSeconds = (maxDurationMs - recordingState.durationMs) / 1000
                Text(
                    text = "${remainingSeconds}s remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Enhanced error message display with better styling and recovery options
 */
@Composable
private fun EnhancedErrorMessage(
    error: RecordingError,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            // Recovery action button if available
            error.recoveryAction?.let { action ->
                val actionText = when (action) {
                    RecordingRecoveryAction.REQUEST_PERMISSION -> "Grant Permission"
                    RecordingRecoveryAction.RETRY_RECORDING -> "Retry"
                    RecordingRecoveryAction.RESTART_AUDIO_ENGINE -> "Restart Audio"
                    RecordingRecoveryAction.FREE_STORAGE_SPACE -> "Free Space"
                    RecordingRecoveryAction.REDUCE_QUALITY -> "Reduce Quality"
                }
                
                Button(
                    onClick = { /* Recovery action would be handled by parent */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(actionText)
                }
            }
        }
    }
}

/**
 * Enhanced level meter drawing with improved visualization
 */
private fun DrawScope.drawEnhancedLevelMeter(
    peakLevel: Float,
    averageLevel: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val meterHeight = size.height
    val meterWidth = size.width
    val cornerRadius = meterHeight / 4f
    
    // Draw average level with gradient (background bar)
    val avgWidth = (averageLevel * meterWidth).coerceIn(0f, meterWidth)
    if (avgWidth > 0) {
        val avgGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF4CAF50).copy(alpha = 0.6f), // Green start
                Color(0xFF8BC34A) // Light green end
            ),
            startX = 0f,
            endX = avgWidth
        )
        
        drawRoundRect(
            brush = avgGradient,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(avgWidth, meterHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
    
    // Draw peak level with dynamic gradient (foreground bar)
    val peakWidth = (peakLevel * meterWidth).coerceIn(0f, meterWidth)
    if (peakWidth > 0) {
        val peakColors = when {
            peakLevel > 0.95f -> listOf(Color(0xFFFF1744), Color(0xFFD50000)) // Critical red - clipping
            peakLevel > 0.85f -> listOf(Color(0xFFFF5722), Color(0xFFFF1744)) // Red gradient - danger
            peakLevel > 0.7f -> listOf(Color(0xFFFF9800), Color(0xFFFF5722)) // Orange to red - hot
            peakLevel > 0.5f -> listOf(Color(0xFFFFC107), Color(0xFFFF9800)) // Yellow to orange - warm
            else -> listOf(Color(0xFF8BC34A), Color(0xFF4CAF50)) // Green gradient - good
        }
        
        val peakGradient = Brush.horizontalGradient(
            colors = peakColors,
            startX = 0f,
            endX = peakWidth
        )
        
        val peakBarHeight = meterHeight * 0.8f
        val peakBarTop = meterHeight * 0.1f
        
        drawRoundRect(
            brush = peakGradient,
            topLeft = Offset(0f, peakBarTop),
            size = androidx.compose.ui.geometry.Size(peakWidth, peakBarHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius * 0.8f)
        )
        
        // Add enhanced glow effect for high levels
        if (peakLevel > 0.8f) {
            val glowAlpha = ((peakLevel - 0.8f) / 0.2f).coerceIn(0f, 1f)
            val glowSize = 4.dp.toPx()
            drawRoundRect(
                color = peakColors.last().copy(alpha = glowAlpha * 0.4f),
                topLeft = Offset(-glowSize, peakBarTop - glowSize),
                size = androidx.compose.ui.geometry.Size(
                    peakWidth + (glowSize * 2), 
                    peakBarHeight + (glowSize * 2)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
    
    // Enhanced scale marks with better visibility
    val scaleMarks = listOf(0.25f, 0.5f, 0.75f, 0.85f, 0.95f)
    scaleMarks.forEach { mark ->
        val x = mark * meterWidth
        val markColor = when {
            mark >= 0.95f -> Color.Red.copy(alpha = 0.8f)
            mark >= 0.85f -> Color(0xFFFF5722).copy(alpha = 0.7f)
            mark >= 0.75f -> Color(0xFFFF9800).copy(alpha = 0.6f)
            else -> Color.White.copy(alpha = 0.5f)
        }
        
        drawLine(
            color = markColor,
            start = Offset(x, meterHeight * 0.05f),
            end = Offset(x, meterHeight * 0.95f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
    
    // Add subtle highlight on top edge
    drawLine(
        color = Color.White.copy(alpha = 0.3f),
        start = Offset(0f, 1.dp.toPx()),
        end = Offset(meterWidth, 1.dp.toPx()),
        strokeWidth = 1.dp.toPx()
    )
}

/**
 * Extension function to convert IntegratedRecordingState to RecordingState
 */
private fun IntegratedRecordingState.toRecordingState(): com.high.theone.model.RecordingState {
    return com.high.theone.model.RecordingState(
        isRecording = this.isRecording,
        isPaused = false,
        durationMs = this.durationMs,
        peakLevel = this.peakLevel,
        averageLevel = this.averageLevel,
        isInitialized = true,
        error = this.error?.message,
        inputSource = com.high.theone.model.AudioInputSource.MICROPHONE,
        sampleRate = 44100,
        channels = 1,
        bitDepth = 16,
        maxDurationSeconds = 300, // 5 minutes default
        recordingFilePath = null,
        isProcessing = this.isProcessing
    )
}

/**
 * 
Lazy loading panel content with performance optimization
 */
@Composable
private fun LazyPanelContent(
    panelType: PanelType,
    optimizationState: OptimizationState,
    modifier: Modifier = Modifier,
    onNavigateToMidiSettings: () -> Unit = {}
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
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToMidiSettings = onNavigateToMidiSettings
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
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToMidiSettings = onNavigateToMidiSettings
                    )
                } else {
                    SimplifiedMidiPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.MIXER -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToMidiSettings = onNavigateToMidiSettings
                    )
                } else {
                    SimplifiedMixerPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.SETTINGS -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToMidiSettings = onNavigateToMidiSettings
                    )
                } else {
                    SimplifiedSettingsPanel(modifier = Modifier.fillMaxSize())
                }
            }
            PanelType.SAMPLE_EDITOR -> {
                if (enableVisualEffects) {
                    QuickAccessPanelContent(
                        panelType = panelType,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToMidiSettings = onNavigateToMidiSettings
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

/**
 * Modal bottom sheet for configuring an individual drum pad.
 * Opened by long-pressing a pad in the grid.
 * Lets the user adjust volume, pan, playback mode and mute group,
 * then saves the changes back through DrumTrackViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PadConfigBottomSheet(
    padSettings: com.high.theone.features.drumtrack.model.PadSettings,
    onSave: (com.high.theone.features.drumtrack.model.PadSettings) -> Unit,
    onDismiss: () -> Unit
) {
    // ── Basic tab state ──────────────────────────────────────────────────────
    var volume       by remember { mutableFloatStateOf(padSettings.volume) }
    var pan          by remember { mutableFloatStateOf(padSettings.pan) }
    var playbackMode by remember { mutableStateOf(padSettings.playbackMode) }
    var muteGroup    by remember { mutableIntStateOf(padSettings.muteGroup) }

    // ── Filter tab state ─────────────────────────────────────────────────────
    var filterEnabled   by remember { mutableStateOf(padSettings.filterSettings.enabled) }
    var filterMode      by remember { mutableStateOf(padSettings.filterSettings.mode) }
    var filterCutoffHz  by remember { mutableFloatStateOf(padSettings.filterSettings.cutoffHz) }
    var filterResonance by remember { mutableFloatStateOf(padSettings.filterSettings.resonance) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Basic", "Filter")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Text(
                text = padSettings.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> PadConfigBasicTab(
                    volume            = volume,
                    onVolumeChange    = { volume = it },
                    pan               = pan,
                    onPanChange       = { pan = it },
                    playbackMode      = playbackMode,
                    onPlaybackModeChange = { playbackMode = it },
                    muteGroup         = muteGroup,
                    onMuteGroupChange = { muteGroup = it },
                    modifier          = Modifier.padding(horizontal = 24.dp)
                )
                1 -> PadConfigFilterTab(
                    enabled          = filterEnabled,
                    onEnabledChange  = { filterEnabled = it },
                    mode             = filterMode,
                    onModeChange     = { filterMode = it },
                    cutoffHz         = filterCutoffHz,
                    onCutoffChange   = { filterCutoffHz = it },
                    resonance        = filterResonance,
                    onResonanceChange = { filterResonance = it },
                    modifier          = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        onSave(
                            padSettings.copy(
                                volume       = volume,
                                pan          = pan,
                                playbackMode = playbackMode,
                                muteGroup    = muteGroup,
                                filterSettings = padSettings.filterSettings.copy(
                                    enabled   = filterEnabled,
                                    mode      = filterMode,
                                    cutoffHz  = filterCutoffHz,
                                    resonance = filterResonance
                                )
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Basic tab ──────────────────────────────────────────────────────────────────
@Composable
private fun PadConfigBasicTab(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    pan: Float,
    onPanChange: (Float) -> Unit,
    playbackMode: PlaybackMode,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    muteGroup: Int,
    onMuteGroupChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Volume
        LabeledSlider(
            label      = "Volume",
            valueText  = "${(volume * 100).toInt()}%",
            value      = volume,
            onValueChange = onVolumeChange
        )

        // Pan
        LabeledSlider(
            label = "Pan",
            valueText = when {
                pan < -0.05f -> "L ${(-pan * 100).toInt()}"
                pan >  0.05f -> "R ${(pan * 100).toInt()}"
                else         -> "Center"
            },
            value      = pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f
        )

        // Playback mode
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Playback Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlaybackMode.values().forEach { mode ->
                    FilterChip(
                        selected = playbackMode == mode,
                        onClick  = { onPlaybackModeChange(mode) },
                        label    = {
                            Text(
                                text  = mode.name.replace("_", "\n"),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        // Mute group
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text  = "Mute Group: ${if (muteGroup == 0) "None" else muteGroup.toString()}",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..4).forEach { g ->
                    FilterChip(
                        selected = muteGroup == g,
                        onClick  = { onMuteGroupChange(g) },
                        label    = { Text(if (g == 0) "Off" else "$g") }
                    )
                }
            }
        }
    }
}

// ── Filter tab ─────────────────────────────────────────────────────────────────
@Composable
private fun PadConfigFilterTab(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    mode: FilterMode,
    onModeChange: (FilterMode) -> Unit,
    cutoffHz: Float,
    onCutoffChange: (Float) -> Unit,
    resonance: Float,
    onResonanceChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Log-scale cutoff slider:  sliderVal = log2(hz / 20) / 10   (0→20 Hz, 1→20 kHz)
    var cutoffSlider by remember(cutoffHz) {
        mutableFloatStateOf(
            (log2(cutoffHz.coerceAtLeast(20f) / 20f) / 10f).coerceIn(0f, 1f)
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Enable toggle
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Enable Filter", style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (enabled) {
            // Mode chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMode.values().forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick  = { onModeChange(m) },
                            label    = {
                                Text(
                                    m.name
                                        .replace("_", " ")
                                        .lowercase()
                                        .replaceFirstChar { it.uppercase() }
                                )
                            }
                        )
                    }
                }
            }

            // Cutoff (log-scale)
            LabeledSlider(
                label = "Cutoff",
                valueText = if (cutoffHz >= 1000f)
                    "${"%.1f".format(cutoffHz / 1000f)} kHz"
                else
                    "${cutoffHz.toInt()} Hz",
                value = cutoffSlider,
                onValueChange = { sv ->
                    cutoffSlider = sv
                    onCutoffChange(
                        (20.0 * 2.0.pow(sv.toDouble() * 10.0))
                            .toFloat()
                            .coerceIn(20f, 20000f)
                    )
                }
            )

            // Resonance (linear 0.5 – 25, displayed as 0..1 on slider)
            LabeledSlider(
                label     = "Resonance",
                valueText = "%.2f".format(resonance),
                value     = ((resonance - 0.5f) / 24.5f).coerceIn(0f, 1f),
                onValueChange = { onResonanceChange((0.5f + it * 24.5f).coerceIn(0.5f, 25f)) }
            )
        }
    }
}

// ── Shared slider helper ───────────────────────────────────────────────────────
@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label,     style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            modifier      = Modifier.fillMaxWidth()
        )
    }
}