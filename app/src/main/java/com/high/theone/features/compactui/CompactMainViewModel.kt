package com.high.theone.features.compactui

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.model.*
import com.high.theone.model.PerformanceMode
import com.high.theone.features.sampling.PerformanceMonitor
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.sequencer.SimpleSequencerViewModel
import com.high.theone.features.midi.ui.MidiSettingsViewModel
import com.high.theone.features.midi.ui.MidiMappingViewModel
import com.high.theone.features.midi.ui.MidiMonitorViewModel
import com.high.theone.features.sequencer.TransportControlAction
import com.high.theone.midi.model.MidiTargetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for the compact UI system
 * Combines and manages state from all UI components including DrumTrack, Sequencer, and MIDI
 */
class CompactMainViewModel(
    private val performanceMonitor: PerformanceMonitor,
    private val drumTrackViewModel: DrumTrackViewModel,
    private val sequencerViewModel: SimpleSequencerViewModel,
    private val midiSettingsViewModel: MidiSettingsViewModel,
    private val midiMappingViewModel: MidiMappingViewModel,
    private val midiMonitorViewModel: MidiMonitorViewModel,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    
    // Screen configuration state
    private val _screenConfiguration = MutableStateFlow(
        ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
    )
    val screenConfiguration: StateFlow<ScreenConfiguration> = _screenConfiguration.asStateFlow()
    
    // Transport state
    private val _transportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()
    
    // Layout state
    private val _layoutState = MutableStateFlow(
        LayoutState(configuration = _screenConfiguration.value)
    )
    val layoutState: StateFlow<LayoutState> = _layoutState.asStateFlow()
    
    // Panel states
    private val _panelStates = MutableStateFlow<Map<PanelType, PanelState>>(emptyMap())
    val panelStates: StateFlow<Map<PanelType, PanelState>> = _panelStates.asStateFlow()
    
    // UI customization preferences from PreferenceManager
    val uiPreferences: StateFlow<UICustomizationPreferences> = preferenceManager.uiPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UICustomizationPreferences()
        )
    
    // Layout customization from PreferenceManager
    val layoutCustomization: StateFlow<LayoutCustomization> = preferenceManager.layoutCustomization
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LayoutCustomization()
        )
    
    // Feature visibility preferences from PreferenceManager
    val featureVisibility: StateFlow<FeatureVisibilityPreferences> = preferenceManager.featureVisibility
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeatureVisibilityPreferences()
        )
    
    // Layout presets from PreferenceManager
    val layoutPresets: StateFlow<List<com.high.theone.model.LayoutPreset>> = preferenceManager.layoutPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Active preset from PreferenceManager
    val activePreset: StateFlow<String?> = preferenceManager.activePreset
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    // Panel positions from PreferenceManager
    val panelPositions: StateFlow<Map<PanelType, PanelPosition>> = preferenceManager.panelPositions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    // Drum pad state from DrumTrackViewModel
    val drumPadState: StateFlow<DrumPadState> = combine(
        drumTrackViewModel.padSettingsMap,
        drumTrackViewModel.isPlaying,
        drumTrackViewModel.isRecording,
        drumTrackViewModel.activePadId
    ) { padSettings, isPlaying, isRecording, activePadId ->
        DrumPadState(
            padSettings = padSettings,
            isPlaying = isPlaying,
            isRecording = isRecording,
            activePadId = activePadId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrumPadState()
    )
    
    // Sequencer state from SimpleSequencerViewModel
    val sequencerState: StateFlow<SequencerState> = sequencerViewModel.sequencerState
    
    // MIDI state from MIDI ViewModels
    val midiState: StateFlow<MidiState> = combine(
        midiSettingsViewModel.uiState,
        midiMappingViewModel.uiState,
        midiMonitorViewModel.uiState
    ) { settingsState, mappingState, monitorState ->
        MidiState(
            isEnabled = settingsState.midiEnabled,
            connectedDevices = midiSettingsViewModel.connectedDevices.value.size,
            activeMappings = mappingState.activeMappings.size,
            isMonitoring = monitorState.isMonitoring,
            statistics = monitorState.statistics
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MidiState()
    )
    
    // Combined compact UI state
    private val _compactUIState = MutableStateFlow(
        CompactUIState(
            layoutState = LayoutState(
                configuration = ScreenConfiguration(
                    screenWidth = 360.dp,
                    screenHeight = 640.dp,
                    orientation = Orientation.PORTRAIT,
                    densityDpi = 420,
                    isTablet = false
                )
            ),
            isInitialized = false
        )
    )
    val compactUIState: StateFlow<CompactUIState> = _compactUIState.asStateFlow()
    
    init {
        // Initialize performance monitoring
        performanceMonitor.startMonitoring()
        
        // Initialize panel states
        initializePanelStates()
        
        // Mark as initialized after initial setup
        viewModelScope.launch {
            // Give a moment for all ViewModels to initialize
            kotlinx.coroutines.delay(100)
            _compactUIState.value = _compactUIState.value.copy(isInitialized = true)
        }
        
        // Monitor performance mode changes
        viewModelScope.launch {
            performanceMonitor.performanceMode.collect { mode ->
                handlePerformanceModeChange(mode)
            }
        }
        
        // Sync transport state with sequencer state
        viewModelScope.launch {
            sequencerViewModel.sequencerState.collect { sequencerState ->
                val currentTransport = _transportState.value
                if (currentTransport.isPlaying != sequencerState.isPlaying ||
                    currentTransport.isRecording != sequencerState.isRecording) {
                    _transportState.value = currentTransport.copy(
                        isPlaying = sequencerState.isPlaying,
                        isRecording = sequencerState.isRecording,
                        currentPosition = sequencerState.currentStep.toLong()
                    )
                }
            }
        }
        
        // Monitor performance and auto-optimize if needed
        viewModelScope.launch {
            performanceMonitor.performanceMetrics.collect { metrics ->
                if (metrics.averageFrameTime > 20f) { // Below 50fps
                    optimizePerformance()
                }
            }
        }
        
        // Apply UI preferences to performance monitor
        viewModelScope.launch {
            uiPreferences.collect { preferences ->
                performanceMonitor.setPerformanceMode(preferences.performanceMode)
            }
        }
        
        // Update layout state when collapsed sections change
        viewModelScope.launch {
            preferenceManager.collapsedSections.collect { collapsedSections ->
                val currentLayout = _layoutState.value
                _layoutState.value = currentLayout.copy(collapsedSections = collapsedSections)
            }
        }
    }
    
    /**
     * Update screen configuration when device orientation or size changes
     */
    fun updateScreenConfiguration(configuration: ScreenConfiguration) {
        _screenConfiguration.value = configuration
        _layoutState.value = _layoutState.value.copy(configuration = configuration)
    }
    
    /**
     * Transport control actions - delegates to sequencer and updates local state
     */
    fun onPlayPause() {
        val current = _transportState.value
        val newPlayingState = !current.isPlaying
        
        // Update local transport state
        _transportState.value = current.copy(isPlaying = newPlayingState)
        
        // Delegate to sequencer
        if (newPlayingState) {
            sequencerViewModel.handleTransportAction(TransportControlAction.Play)
        } else {
            sequencerViewModel.handleTransportAction(TransportControlAction.Pause)
        }
        
        // Record performance metrics
        recordFrameTime()
    }
    
    fun onStop() {
        // Update local transport state
        _transportState.value = _transportState.value.copy(
            isPlaying = false,
            currentPosition = 0
        )
        
        // Delegate to sequencer
        sequencerViewModel.handleTransportAction(TransportControlAction.Stop)
        
        // Record performance metrics
        recordFrameTime()
    }
    
    fun onRecord() {
        val current = _transportState.value
        val newRecordingState = !current.isRecording
        
        // Update local transport state
        _transportState.value = current.copy(isRecording = newRecordingState)
        
        // Delegate to sequencer
        sequencerViewModel.handleTransportAction(TransportControlAction.ToggleRecord)
        
        // Record performance metrics
        recordFrameTime()
    }
    
    fun onBpmChange(newBpm: Int) {
        if (newBpm in 60..200) {
            // Update local transport state
            _transportState.value = _transportState.value.copy(bpm = newBpm)
            
            // Delegate to sequencer
            sequencerViewModel.handleTransportAction(TransportControlAction.SetTempo(newBpm.toFloat()))
            
            // Record performance metrics
            recordFrameTime()
        }
    }
    
    /**
     * Panel management actions
     */
    fun showPanel(panelType: PanelType) {
        val currentPanels = _panelStates.value.toMutableMap()
        currentPanels[panelType] = PanelState(
            isVisible = true,
            isExpanded = false,
            snapPosition = SnapPosition.PEEK,
            contentType = panelType
        )
        _panelStates.value = currentPanels
        
        // Update layout state
        val currentLayout = _layoutState.value
        _layoutState.value = currentLayout.copy(
            activePanels = currentLayout.activePanels + panelType,
            panelVisibility = currentLayout.panelVisibility + (panelType to true)
        )
    }
    
    fun hidePanel(panelType: PanelType) {
        val currentPanels = _panelStates.value.toMutableMap()
        currentPanels[panelType] = PanelState(
            isVisible = false,
            snapPosition = SnapPosition.HIDDEN,
            contentType = panelType
        )
        _panelStates.value = currentPanels
        
        // Update layout state
        val currentLayout = _layoutState.value
        _layoutState.value = currentLayout.copy(
            activePanels = currentLayout.activePanels - panelType,
            panelVisibility = currentLayout.panelVisibility + (panelType to false)
        )
    }
    
    fun expandPanel(panelType: PanelType) {
        val currentPanels = _panelStates.value.toMutableMap()
        val currentState = currentPanels[panelType] ?: return
        
        currentPanels[panelType] = currentState.copy(
            isExpanded = true,
            snapPosition = SnapPosition.FULL
        )
        _panelStates.value = currentPanels
    }
    
    fun collapsePanel(panelType: PanelType) {
        val currentPanels = _panelStates.value.toMutableMap()
        val currentState = currentPanels[panelType] ?: return
        
        currentPanels[panelType] = currentState.copy(
            isExpanded = false,
            snapPosition = SnapPosition.PEEK
        )
        _panelStates.value = currentPanels
    }
    
    /**
     * Section management actions
     */
    fun toggleSection(sectionType: SectionType) {
        val currentLayout = _layoutState.value
        val collapsedSections = currentLayout.collapsedSections.toMutableSet()
        
        if (collapsedSections.contains(sectionType)) {
            collapsedSections.remove(sectionType)
        } else {
            collapsedSections.add(sectionType)
        }
        
        _layoutState.value = currentLayout.copy(collapsedSections = collapsedSections)
    }
    
    /**
     * UI customization actions
     */
    fun updateUIPreferences(preferences: UICustomizationPreferences) {
        viewModelScope.launch {
            preferenceManager.saveUIPreferences(preferences)
            
            // Apply performance mode if specified
            preferences.performanceMode.let { mode ->
                performanceMonitor.setPerformanceMode(mode)
            }
        }
        recordFrameTime()
    }
    
    /**
     * Layout customization actions
     */
    fun updateLayoutCustomization(customization: LayoutCustomization) {
        viewModelScope.launch {
            preferenceManager.saveLayoutCustomization(customization)
        }
        recordFrameTime()
    }
    
    /**
     * Feature visibility actions
     */
    fun updateFeatureVisibility(visibility: FeatureVisibilityPreferences) {
        viewModelScope.launch {
            preferenceManager.saveFeatureVisibility(visibility)
        }
        recordFrameTime()
    }
    
    /**
     * Layout preset actions
     */
    fun createLayoutPreset(name: String) {
        viewModelScope.launch {
            val currentLayout = layoutCustomization.value
            val currentVisibility = featureVisibility.value
            val currentPositions = panelPositions.value
            
            preferenceManager.createPreset(name, currentLayout, currentVisibility, currentPositions)
        }
        recordFrameTime()
    }
    
    fun applyLayoutPreset(presetId: String) {
        viewModelScope.launch {
            preferenceManager.applyPreset(presetId)
        }
        recordFrameTime()
    }
    
    fun deleteLayoutPreset(presetId: String) {
        viewModelScope.launch {
            preferenceManager.deletePreset(presetId)
        }
        recordFrameTime()
    }
    
    fun resetToDefaults() {
        viewModelScope.launch {
            preferenceManager.resetToDefaults()
        }
        recordFrameTime()
    }
    
    /**
     * Panel position management
     */
    fun updatePanelPosition(panelType: PanelType, position: PanelPosition) {
        viewModelScope.launch {
            val currentPositions = panelPositions.value.toMutableMap()
            currentPositions[panelType] = position
            preferenceManager.savePanelPositions(currentPositions)
        }
        recordFrameTime()
    }
    
    /**
     * Drum pad actions - delegates to DrumTrackViewModel
     */
    fun onPadTriggered(padId: String) {
        drumTrackViewModel.onPadTriggered(padId)
        recordFrameTime()
    }
    
    fun updatePadSettings(padSettings: com.high.theone.features.drumtrack.model.PadSettings) {
        drumTrackViewModel.updatePadSettings(padSettings)
        recordFrameTime()
    }
    
    /**
     * Sequencer actions - delegates to SimpleSequencerViewModel
     */
    fun toggleStep(padId: Int, stepIndex: Int) {
        sequencerViewModel.toggleStep(padId, stepIndex)
        recordFrameTime()
    }
    
    fun setStepVelocity(padId: Int, stepIndex: Int, velocity: Int) {
        sequencerViewModel.setStepVelocity(padId, stepIndex, velocity)
        recordFrameTime()
    }
    
    fun selectPattern(patternId: String) {
        sequencerViewModel.selectPattern(patternId)
        recordFrameTime()
    }
    
    fun togglePadMute(padId: Int) {
        sequencerViewModel.togglePadMute(padId)
        recordFrameTime()
    }
    
    fun togglePadSolo(padId: Int) {
        sequencerViewModel.togglePadSolo(padId)
        recordFrameTime()
    }
    
    /**
     * MIDI actions - delegates to MIDI ViewModels
     */
    fun connectMidiDevice(deviceId: String) {
        midiSettingsViewModel.connectDevice(deviceId)
        recordFrameTime()
    }
    
    fun disconnectMidiDevice(deviceId: String) {
        midiSettingsViewModel.disconnectDevice(deviceId)
        recordFrameTime()
    }
    
    fun startMidiLearn(targetType: MidiTargetType, targetId: String) {
        midiMappingViewModel.onStartMidiLearn(targetType, targetId)
        recordFrameTime()
    }
    
    fun stopMidiLearn() {
        midiMappingViewModel.onStopMidiLearn()
        recordFrameTime()
    }
    
    fun toggleMidiMonitoring() {
        midiMonitorViewModel.onToggleMonitoring()
        recordFrameTime()
    }
    
    /**
     * Performance monitoring actions
     */
    fun recordFrameTime() {
        performanceMonitor.recordFrameTime()
    }
    
    fun updateAudioLatency(latency: Float) {
        performanceMonitor.updateAudioLatency(latency)
    }
    
    /**
     * Performance optimization based on current metrics
     */
    fun optimizePerformance() {
        viewModelScope.launch {
            val metrics = performanceMonitor.performanceMetrics.value
            
            // Auto-adjust UI preferences based on performance
            if (metrics.averageFrameTime > 16.67f) { // Below 60fps
                val currentPreferences = uiPreferences.value
                val currentLayout = layoutCustomization.value
                
                // Optimize UI preferences
                val optimizedPreferences = currentPreferences.copy(
                    performanceMode = PerformanceMode.BATTERY_SAVER,
                    collapsedSectionsByDefault = currentPreferences.collapsedSectionsByDefault + 
                        setOf(SectionType.UTILITY_PANEL, SectionType.QUICK_ACCESS)
                )
                
                // Optimize layout customization
                val optimizedLayout = currentLayout.copy(
                    enableAnimations = false,
                    compactMode = true
                )
                
                // Optimize feature visibility
                val currentVisibility = featureVisibility.value
                val optimizedVisibility = currentVisibility.copy(
                    showVisualEffects = false,
                    showAdvancedControls = false,
                    showPerformanceMetrics = false
                )
                
                // Apply optimizations
                updateUIPreferences(optimizedPreferences)
                updateLayoutCustomization(optimizedLayout)
                updateFeatureVisibility(optimizedVisibility)
            }
        }
    }
    
    /**
     * Initialize panel states for all panel types
     */
    private fun initializePanelStates() {
        val initialPanels = PanelType.values().associateWith { panelType ->
            PanelState(
                isVisible = false,
                isExpanded = false,
                snapPosition = SnapPosition.HIDDEN,
                contentType = panelType
            )
        }
        _panelStates.value = initialPanels
    }
    
    /**
     * Handle performance mode changes
     */
    private fun handlePerformanceModeChange(mode: PerformanceMode) {
        viewModelScope.launch {
            val currentPreferences = uiPreferences.value
            val currentLayout = layoutCustomization.value
            
            // Update preferences based on performance mode
            when (mode) {
                PerformanceMode.HIGH_PERFORMANCE -> {
                    val updatedPreferences = currentPreferences.copy(performanceMode = mode)
                    val updatedLayout = currentLayout.copy(enableAnimations = true)
                    
                    preferenceManager.saveUIPreferences(updatedPreferences)
                    preferenceManager.saveLayoutCustomization(updatedLayout)
                }
                PerformanceMode.BALANCED -> {
                    val updatedPreferences = currentPreferences.copy(performanceMode = mode)
                    val updatedLayout = currentLayout.copy(enableAnimations = true)
                    
                    preferenceManager.saveUIPreferences(updatedPreferences)
                    preferenceManager.saveLayoutCustomization(updatedLayout)
                }
                PerformanceMode.BATTERY_SAVER -> {
                    val updatedPreferences = currentPreferences.copy(
                        performanceMode = mode,
                        collapsedSectionsByDefault = currentPreferences.collapsedSectionsByDefault + 
                            setOf(SectionType.UTILITY_PANEL, SectionType.QUICK_ACCESS)
                    )
                    val updatedLayout = currentLayout.copy(
                        enableAnimations = false,
                        compactMode = true
                    )
                    
                    preferenceManager.saveUIPreferences(updatedPreferences)
                    preferenceManager.saveLayoutCustomization(updatedLayout)
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        performanceMonitor.stopMonitoring()
        
        // Note: Individual ViewModels will handle their own cleanup
        // when their lifecycle ends, so we don't need to explicitly clean them up here
    }
}