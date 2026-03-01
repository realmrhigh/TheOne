package com.high.theone.features.compactui

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.model.*
import com.high.theone.model.PerformanceMode
import com.high.theone.features.sampling.PerformanceMonitor
import com.high.theone.features.compactui.performance.RecordingPerformanceMonitor
import com.high.theone.features.compactui.performance.RecordingMemoryManager
import com.high.theone.features.compactui.performance.RecordingFrameRateMonitor
import com.high.theone.features.compactui.performance.RecordingPerformanceState
import com.high.theone.features.compactui.performance.PerformanceWarning
import com.high.theone.features.compactui.performance.OptimizationSuggestion
import com.high.theone.features.compactui.performance.OptimizationSuggestionType
import com.high.theone.midi.model.MidiTargetType
import com.high.theone.features.compactui.error.ErrorHandlingSystem
import com.high.theone.features.compactui.error.PermissionManager
import com.high.theone.features.compactui.error.AudioEngineRecovery
import com.high.theone.features.compactui.error.StorageManager
import com.high.theone.features.compactui.error.PermissionState
import com.high.theone.features.compactui.error.RecoveryState
import com.high.theone.features.compactui.error.StorageInfo
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Main ViewModel for the compact UI system
 * Manages UI state and coordinates between different components
 * Note: Individual feature ViewModels (SamplingViewModel, DrumTrackViewModel, etc.) 
 * should be created directly in composables using hiltViewModel() to avoid injection issues
 */
@HiltViewModel
class CompactMainViewModel @Inject constructor(
    private val performanceMonitor: PerformanceMonitor,
    private val preferenceManager: PreferenceManager,
    private val errorHandlingSystem: ErrorHandlingSystem,
    private val permissionManager: PermissionManager,
    private val audioEngineRecovery: AudioEngineRecovery,
    private val storageManager: StorageManager,
    private val recordingPerformanceMonitor: RecordingPerformanceMonitor,
    private val recordingMemoryManager: RecordingMemoryManager,
    private val recordingFrameRateMonitor: RecordingFrameRateMonitor
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
    
    // Transport state - BPM comes from preferences
    private val _transportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = combine(
        _transportState,
        preferenceManager.bpm
    ) { transportState, bpm ->
        transportState.copy(bpm = bpm)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransportState()
    )
    
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
    
    // Drum pad state - updated externally by composables via their own DrumTrackViewModel
    private val _drumPadState = MutableStateFlow(DrumPadState())
    val drumPadState: StateFlow<DrumPadState> = _drumPadState.asStateFlow()

    // Sequencer state - updated externally by composables via their own SimpleSequencerViewModel
    private val _sequencerState = MutableStateFlow(SequencerState())
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()

    // MIDI state - updated externally by composables via their own MIDI ViewModels
    private val _midiState = MutableStateFlow(MidiState())
    val midiState: StateFlow<MidiState> = _midiState.asStateFlow()

    // Recording state with pad assignment integration
    private val _lastRecordedSampleId = MutableStateFlow<String?>(null)
    private val _isAssignmentMode = MutableStateFlow(false)

    // Sampling state - updated externally by composables via their own SamplingViewModel
    private val _samplingState = MutableStateFlow(com.high.theone.model.SamplingUiState())
    val samplingState: StateFlow<com.high.theone.model.SamplingUiState> = _samplingState.asStateFlow()
    
    val recordingState: StateFlow<IntegratedRecordingState> = MutableStateFlow(IntegratedRecordingState()).asStateFlow()
    
    // Recording panel visibility state
    private val _isRecordingPanelVisible = MutableStateFlow(false)
    val isRecordingPanelVisible: StateFlow<Boolean> = _isRecordingPanelVisible.asStateFlow()
    
    // Error handling state flows
    val currentError: StateFlow<RecordingError?> = errorHandlingSystem.currentError
    val isRecovering: StateFlow<Boolean> = errorHandlingSystem.isRecovering
    val retryAttempts: StateFlow<Int> = errorHandlingSystem.retryAttempts
    
    // Permission management state flows
    val permissionState: StateFlow<PermissionState> = permissionManager.permissionState
    val shouldShowRationale: StateFlow<Boolean> = permissionManager.shouldShowRationale
    val permissionDeniedPermanently: StateFlow<Boolean> = permissionManager.permissionDeniedPermanently
    
    // Audio engine recovery state flows
    val recoveryState: StateFlow<RecoveryState> = audioEngineRecovery.recoveryState
    val recoveryProgress: StateFlow<Float> = audioEngineRecovery.recoveryProgress
    val lastRecoveryError: StateFlow<String?> = audioEngineRecovery.lastRecoveryError
    
    // Storage management state flows
    val storageInfo: StateFlow<StorageInfo> = storageManager.storageInfo
    val cleanupProgress: StateFlow<Float> = storageManager.cleanupProgress
    val isCleaningUp: StateFlow<Boolean> = storageManager.isCleaningUp
    
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
        
        // Initialize recording performance monitoring
        recordingMemoryManager.startMemoryManagement(viewModelScope)
        
        // Initialize panel states
        initializePanelStates()
        
        // Monitor performance mode changes
        viewModelScope.launch {
            performanceMonitor.performanceMode.collect { mode ->
                handlePerformanceModeChange(mode)
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
        
        // Recording lifecycle events are handled externally by composables
        // which call updateSamplingState() when their SamplingViewModel state changes
        
        // Monitor recording performance and apply automatic optimizations
        // viewModelScope.launch {
        //     recordingPerformanceState.collect { performanceState ->
        //         handleRecordingPerformanceChanges(performanceState)
        //     }
        // }
        
        // Monitor frame rate optimization triggers
        // viewModelScope.launch {
        //     frameRateOptimizationTriggers.collect { triggers ->
        //         handleFrameRateOptimizationTriggers(triggers)
        //     }
        // }
        
        // Initialize error handling systems
        initializeErrorHandling()

        // Mark UI as ready — all synchronous init is complete
        _compactUIState.value = _compactUIState.value.copy(isInitialized = true)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Stop all performance monitoring
        performanceMonitor.stopMonitoring()
        recordingPerformanceMonitor.stopRecordingMonitoring()
        recordingFrameRateMonitor.stopRecordingFrameRateMonitoring()
        recordingMemoryManager.stopMemoryManagement()
        
        // Note: Individual ViewModels will handle their own cleanup
        // when their lifecycle ends, so we don't need to explicitly clean them up here
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
        
        // Composables delegate directly to their own SimpleSequencerViewModel
        
        // Record performance metrics
        recordFrameTime()
    }
    
    fun onStop() {
        // Update local transport state
        _transportState.value = _transportState.value.copy(
            isPlaying = false,
            currentPosition = 0
        )
        
        // Composables delegate directly to their own SimpleSequencerViewModel
        recordFrameTime()
    }

    fun onRecord() {
        val current = _transportState.value
        val newRecordingState = !current.isRecording
        
        // Update local transport state
        _transportState.value = current.copy(isRecording = newRecordingState)
        
        // Composables delegate directly to their own SimpleSequencerViewModel
        recordFrameTime()
    }

    fun onBpmChange(newBpm: Int) {
        if (newBpm in 60..200) {
            // Save to preferences
            viewModelScope.launch {
                preferenceManager.saveBpm(newBpm)
            }
            
            // Composables delegate directly to their own SimpleSequencerViewModel
            
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
    fun onPadTriggered(padId: String, velocity: Float = 1.0f) {
        // Composables call their own DrumTrackViewModel directly
        recordFrameTime()
    }

    fun updatePadSettings(padSettings: com.high.theone.features.drumtrack.model.PadSettings) {
        // Composables call their own DrumTrackViewModel directly
        recordFrameTime()
    }
    
    /**
     * Sequencer actions - delegates to SimpleSequencerViewModel
     */
    fun toggleStep(padId: Int, stepIndex: Int) {
        // Composables call their own SimpleSequencerViewModel directly
        recordFrameTime()
    }

    fun setStepVelocity(padId: Int, stepIndex: Int, velocity: Int) {
        // Composables call their own SimpleSequencerViewModel directly
        recordFrameTime()
    }

    fun selectPattern(patternId: String) {
        // Composables call their own SimpleSequencerViewModel directly
        recordFrameTime()
    }

    fun togglePadMute(padId: Int) {
        // Composables call their own SimpleSequencerViewModel directly
        recordFrameTime()
    }

    fun togglePadSolo(padId: Int) {
        // Composables call their own SimpleSequencerViewModel directly
        recordFrameTime()
    }
    
    /**
     * MIDI actions - delegates to MIDI ViewModels
     */
    fun connectMidiDevice(deviceId: String) {
        // Composables call their own MidiSettingsViewModel directly
        recordFrameTime()
    }

    fun disconnectMidiDevice(deviceId: String) {
        // Composables call their own MidiSettingsViewModel directly
        recordFrameTime()
    }

    fun startMidiLearn(targetType: MidiTargetType, targetId: String) {
        // Composables call their own MidiMappingViewModel directly
        recordFrameTime()
    }

    fun stopMidiLearn() {
        // Composables call their own MidiMappingViewModel directly
        recordFrameTime()
    }

    fun toggleMidiMonitoring() {
        // Composables call their own MidiMonitorViewModel directly
        recordFrameTime()
    }
    
    /**
     * Recording actions - delegates to SamplingViewModel with error handling
     */
    fun startRecording() {
        startRecordingWithErrorHandling()
        
        // Start recording performance monitoring
        recordingPerformanceMonitor.startRecordingMonitoring(viewModelScope)
        recordingFrameRateMonitor.startRecordingFrameRateMonitoring(viewModelScope)
    }
    
    /**
     * Start recording with comprehensive error handling
     */
    private fun startRecordingWithErrorHandling() {
        viewModelScope.launch {
            try {
                // Check permissions first
                val permissionState = permissionManager.permissionState.value
                if (permissionState != PermissionState.GRANTED) {
                    errorHandlingSystem.handleRecordingError(
                        RuntimeException("Microphone permission not granted")
                    )
                    return@launch
                }
                
                // Check storage space
                val storageInfo = storageManager.storageInfo.value
                if (storageInfo.availableSpaceMB < 50L) { // 50MB minimum
                    errorHandlingSystem.handleRecordingError(
                        RuntimeException("Insufficient storage space")
                    )
                    return@launch
                }
                
                // All checks passed - composable must call its own SamplingViewModel.startRecording()
                recordFrameTime()
                
            } catch (e: Exception) {
                Log.e("CompactMainViewModel", "Error starting recording", e)
                errorHandlingSystem.handleRecordingError(e)
            }
        }
    }
    
    fun stopRecording() {
        // Composables call their own SamplingViewModel.stopRecording() directly

        // Stop recording performance monitoring
        recordingPerformanceMonitor.stopRecordingMonitoring()
        recordingFrameRateMonitor.stopRecordingFrameRateMonitoring()
        
        recordFrameTime()
    }
    
    fun showRecordingPanel() {
        _isRecordingPanelVisible.value = true
        showPanel(PanelType.SAMPLING)
        recordFrameTime()
    }
    
    fun hideRecordingPanel() {
        _isRecordingPanelVisible.value = false
        hidePanel(PanelType.SAMPLING)
        recordFrameTime()
    }
    
    fun assignRecordedSampleToPad(padId: String) {
        val currentRecordingState = recordingState.value
        val recordedSampleId = currentRecordingState.recordedSampleId
        
        if (recordedSampleId != null) {
            // Composables call their own SamplingViewModel.assignSampleToPad() directly

            // Clear assignment mode and recorded sample ID
            _isAssignmentMode.value = false
            _lastRecordedSampleId.value = null
            
            // Hide recording panel after assignment
            hideRecordingPanel()
        }
        recordFrameTime()
    }
    
    fun enterAssignmentMode() {
        _isAssignmentMode.value = true
        recordFrameTime()
    }
    
    fun exitAssignmentMode() {
        _isAssignmentMode.value = false
        recordFrameTime()
    }
    
    fun discardRecording() {
        // Composables call their own SamplingViewModel.clearError() directly
        
        // Clear assignment mode and recorded sample ID
        _isAssignmentMode.value = false
        _lastRecordedSampleId.value = null
        
        hideRecordingPanel()
        recordFrameTime()
    }
    
    /**
     * Performance monitoring actions
     */
    fun recordFrameTime() {
        performanceMonitor.recordFrameTime()
        
        // Also record in recording-specific monitors if recording is active
        if (_samplingState.value.recordingState.isRecording) {
            recordingPerformanceMonitor.recordRecordingFrameTime(16.67f) // Assume 60fps target
            recordingFrameRateMonitor.recordFrameEnd()
        }
    }
    
    fun updateAudioLatency(latency: Float) {
        performanceMonitor.updateAudioLatency(latency)
        
        // Also update recording performance monitor
        recordingPerformanceMonitor.recordRecordingAudioLatency(latency)
    }
    
    /**
     * Recording performance monitoring actions
     */
    fun updateRecordingBufferMemory(memoryBytes: Long) {
        recordingPerformanceMonitor.updateRecordingBufferMemory(memoryBytes)
        recordingMemoryManager.updateBufferUsage("current_recording")
    }
    
    fun applyPerformanceOptimization(optimizationType: OptimizationSuggestionType) {
        when (optimizationType) {
            OptimizationSuggestionType.REDUCE_VISUAL_EFFECTS -> {
                // Apply visual effects reduction
                val currentPreferences = uiPreferences.value
                val optimizedPreferences = currentPreferences.copy(
                    performanceMode = PerformanceMode.BATTERY_SAVER
                )
                updateUIPreferences(optimizedPreferences)
            }
            OptimizationSuggestionType.MEMORY_CLEANUP -> {
                // Force memory cleanup
                recordingMemoryManager.forceCleanup()
                recordingPerformanceMonitor.forceRecordingMemoryCleanup()
            }
            OptimizationSuggestionType.AUDIO_OPTIMIZATION -> {
                // Audio optimization would typically involve native audio engine changes
                // For now, we'll just log the request
                Log.d("CompactMainViewModel", "Audio optimization requested")
            }
            OptimizationSuggestionType.SIMPLIFY_UI -> {
                // Simplify UI by hiding non-essential panels
                hidePanel(PanelType.MIXER)
                hidePanel(PanelType.SETTINGS)
                
                val currentLayout = layoutCustomization.value
                val simplifiedLayout = currentLayout.copy(
                    enableAnimations = false,
                    compactMode = true
                )
                updateLayoutCustomization(simplifiedLayout)
            }
        }
        
        recordFrameTime()
    }
    
    fun dismissPerformanceWarning(warningType: com.high.theone.features.compactui.performance.PerformanceWarningType) {
        // Performance warnings are automatically managed by the monitoring system
        // This method could be used to temporarily suppress specific warning types
        Log.d("CompactMainViewModel", "Performance warning dismissed: $warningType")
    }
    
    fun getRecordingPerformanceRecommendations(): List<String> {
        return recordingPerformanceMonitor.getRecordingPerformanceRecommendations()
    }
    
    fun forcePerformanceOptimization() {
        // Apply all available optimizations
        recordingPerformanceMonitor.applyAutomaticOptimizations()
        recordingMemoryManager.forceCleanup()
        optimizePerformance()
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
    
    /**
     * Handle recording lifecycle events from SamplingViewModel
     */
    private fun handleRecordingLifecycleEvents(samplingState: com.high.theone.model.SamplingUiState) {
        val currentRecordingState = recordingState.value
        
        // Auto-show recording panel when recording starts
        if (samplingState.recordingState.isRecording && !_isRecordingPanelVisible.value) {
            showRecordingPanel()
        }
        
        // Handle recording completion - detect new sample and enter assignment mode
        if (!samplingState.recordingState.isRecording && 
            !samplingState.recordingState.isProcessing && 
            samplingState.recordingState.error == null &&
            samplingState.availableSamples.isNotEmpty()) {
            
            // Check if we just finished recording by comparing sample count
            val lastRecordedSample = samplingState.availableSamples.lastOrNull()
            if (lastRecordedSample != null && 
                _lastRecordedSampleId.value != lastRecordedSample.id.toString()) {
                
                // Set the recorded sample ID and enter assignment mode
                _lastRecordedSampleId.value = lastRecordedSample.id.toString()
                _isAssignmentMode.value = true
                
                // Ensure recording panel is visible for assignment
                if (!_isRecordingPanelVisible.value) {
                    showRecordingPanel()
                }
            }
        }
        
        // Handle recording errors
        if (samplingState.recordingState.error != null) {
            // Show recording panel to display error
            if (!_isRecordingPanelVisible.value) {
                showRecordingPanel()
            }
            
            // Clear assignment mode on error
            _isAssignmentMode.value = false
            _lastRecordedSampleId.value = null
        }
        
        // Auto-hide panel if recording is stopped, no error, and not in assignment mode
        if (!samplingState.recordingState.isRecording && 
            !samplingState.recordingState.isProcessing && 
            samplingState.recordingState.error == null &&
            !_isAssignmentMode.value &&
            _isRecordingPanelVisible.value) {
            
            // Auto-hide after successful recording completion
            hideRecordingPanel()
        }
        
        // Handle recording errors
        if (samplingState.recordingState.error != null) {
            handleRecordingError(samplingState.recordingState.error)
        }
    }
    
    /**
     * Initialize error handling systems and monitoring
     */
    private fun initializeErrorHandling() {
        // Initialize storage monitoring
        storageManager.updateStorageInfo()
        
        // Check initial permission state
        permissionManager.checkMicrophonePermission()
        
        // Monitor storage space periodically
        viewModelScope.launch {
            while (true) {
                delay(30000) // Check every 30 seconds
                storageManager.updateStorageInfo()
            }
        }
        
        // Monitor for audio engine health
        viewModelScope.launch {
            while (true) {
                delay(10000) // Check every 10 seconds
                if (!audioEngineRecovery.checkAudioEngineHealth()) {
                    // Audio engine is unhealthy, but don't auto-recover unless there's an active error
                    // This prevents unnecessary recovery attempts during normal operation
                }
            }
        }
    }
    
    /**
     * Handle recording errors with appropriate recovery actions
     */
    private fun handleRecordingError(errorMessage: String) {
        viewModelScope.launch {
            try {
                val error = when {
                    errorMessage.contains("permission", ignoreCase = true) -> {
                        errorHandlingSystem.handleSpecificError(RecordingErrorType.PERMISSION_DENIED, errorMessage)
                    }
                    errorMessage.contains("audio", ignoreCase = true) -> {
                        errorHandlingSystem.handleSpecificError(RecordingErrorType.AUDIO_ENGINE_FAILURE, errorMessage)
                    }
                    errorMessage.contains("storage", ignoreCase = true) -> {
                        errorHandlingSystem.handleSpecificError(RecordingErrorType.STORAGE_FAILURE, errorMessage)
                    }
                    errorMessage.contains("microphone", ignoreCase = true) -> {
                        errorHandlingSystem.handleSpecificError(RecordingErrorType.MICROPHONE_UNAVAILABLE, errorMessage)
                    }
                    else -> {
                        errorHandlingSystem.handleSpecificError(RecordingErrorType.SYSTEM_OVERLOAD, errorMessage)
                    }
                }
                
                // Show recording panel to display error
                if (!_isRecordingPanelVisible.value) {
                    showRecordingPanel()
                }
                
            } catch (e: Exception) {
                Log.e("CompactMainViewModel", "Error handling recording error", e)
            }
        }
    }
    
    /**
     * Error handling and recovery actions
     */
    
    /**
     * Execute recovery action based on error type
     */
    fun executeRecoveryAction(recoveryAction: RecordingRecoveryAction) {
        viewModelScope.launch {
            errorHandlingSystem.setRecovering(true)
            
            try {
                when (recoveryAction) {
                    RecordingRecoveryAction.REQUEST_PERMISSION -> {
                        // Permission request should be handled by the UI layer
                        // This just updates the permission state
                        permissionManager.checkMicrophonePermission()
                    }
                    
                    RecordingRecoveryAction.RETRY_RECORDING -> {
                        if (errorHandlingSystem.canRetry()) {
                            errorHandlingSystem.incrementRetryAttempts()
                            delay(1000) // Brief delay before retry
                            
                            // Clear the error and try recording again
                            errorHandlingSystem.clearError()
                            // Composables call their own SamplingViewModel.clearError() directly
                            
                            // Check prerequisites before retry
                            if (permissionManager.checkMicrophonePermission() == PermissionState.GRANTED &&
                                storageManager.hasEnoughSpaceForRecording()) {
                                startRecording()
                            }
                        }
                    }
                    
                    RecordingRecoveryAction.RESTART_AUDIO_ENGINE -> {
                        val success = audioEngineRecovery.recoverAudioEngine()
                        if (success) {
                            errorHandlingSystem.clearError()
                            // Composables call their own SamplingViewModel.clearError() directly
                        }
                    }
                    
                    RecordingRecoveryAction.FREE_STORAGE_SPACE -> {
                        val result = storageManager.cleanupStorage()
                        if (result.success) {
                            errorHandlingSystem.clearError()
                            // Composables call their own SamplingViewModel.clearError() directly
                        }
                    }
                    
                    RecordingRecoveryAction.REDUCE_QUALITY -> {
                        // This would need to be implemented in the audio engine
                        // For now, just clear the error and suggest the user try again
                        errorHandlingSystem.clearError()
                        // Composables call their own SamplingViewModel.clearError() directly
                    }
                }
            } catch (e: Exception) {
                Log.e("CompactMainViewModel", "Error during recovery action", e)
            } finally {
                errorHandlingSystem.setRecovering(false)
            }
        }
    }
    
    /**
     * Clear current error state
     */
    fun clearError() {
        errorHandlingSystem.clearError()
        // Composables call their own SamplingViewModel.clearError() directly
    }
    
    /**
     * Update permission state after permission request result
     */
    fun updatePermissionState(granted: Boolean, shouldShowRationale: Boolean) {
        permissionManager.updatePermissionState(granted, shouldShowRationale)
        
        if (granted) {
            // Permission granted, clear any permission-related errors
            val currentError = errorHandlingSystem.currentError.value
            if (currentError?.type == RecordingErrorType.PERMISSION_DENIED) {
                errorHandlingSystem.clearError()
                // Composables call their own SamplingViewModel.clearError() directly
            }
        }
    }
    
    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings() {
        permissionManager.openAppSettings()
    }
    
    /**
     * Get permission explanation text
     */
    fun getPermissionExplanation(): String {
        return permissionManager.getPermissionExplanation()
    }
    
    /**
     * Get permission recovery instructions
     */
    fun getPermissionRecoveryInstructions(): List<String> {
        return permissionManager.getRecoveryInstructions()
    }
    
    /**
     * Get storage recommendations
     */
    fun getStorageRecommendations(): List<String> {
        return storageManager.getStorageRecommendations()
    }
    
    /**
     * Get storage status message
     */
    fun getStorageStatusMessage(): String {
        return storageManager.getStorageStatusMessage()
    }
    
    /**
     * Get recovery status message
     */
    fun getRecoveryStatusMessage(): String {
        return audioEngineRecovery.getRecoveryStatusMessage()
    }
    
    /**
     * Get recovery instructions
     */
    fun getRecoveryInstructions(): List<String> {
        return audioEngineRecovery.getRecoveryInstructions()
    }
    
    /**
     * Cleanup storage manually
     */
    fun cleanupStorage() {
        viewModelScope.launch {
            storageManager.cleanupStorage()
        }
    }

    // -------------------------------------------------------------------------
    // State sync methods — composables call these to push external ViewModel
    // state into CompactMainViewModel so it can coordinate panel/layout logic.
    // -------------------------------------------------------------------------

    fun updateDrumPadState(state: DrumPadState) {
        _drumPadState.value = state
    }

    fun updateSequencerState(state: SequencerState) {
        _sequencerState.value = state
        // Keep transport state in sync
        val current = _transportState.value
        if (current.isPlaying != state.isPlaying || current.isRecording != state.isRecording) {
            _transportState.value = current.copy(
                isPlaying = state.isPlaying,
                isRecording = state.isRecording,
                currentPosition = state.currentStep.toLong()
            )
        }
    }

    fun updateMidiState(state: MidiState) {
        _midiState.value = state
    }

    fun updateSamplingState(state: com.high.theone.model.SamplingUiState) {
        _samplingState.value = state
        handleRecordingLifecycleEvents(state)
    }

}
