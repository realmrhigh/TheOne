package com.high.theone.features.compactui

import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.model.*
import com.high.theone.ui.performance.PerformanceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for the compact UI system
 * Combines and manages state from all UI components
 */
@HiltViewModel
class CompactMainViewModel @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
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
    
    // UI customization preferences
    private val _uiPreferences = MutableStateFlow(UICustomizationPreferences())
    val uiPreferences: StateFlow<UICustomizationPreferences> = _uiPreferences.asStateFlow()
    
    // Combined compact UI state
    val compactUIState: StateFlow<CompactUIState> = combine(
        transportState,
        layoutState,
        panelStates,
        performanceMonitor.performanceMetrics
    ) { transport, layout, panels, performance ->
        CompactUIState(
            transportState = transport,
            layoutState = layout,
            panelStates = panels,
            performanceMetrics = performance,
            isInitialized = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CompactUIState(
            layoutState = _layoutState.value,
            isInitialized = false
        )
    )
    
    init {
        // Initialize performance monitoring
        performanceMonitor.startMonitoring(viewModelScope)
        
        // Initialize panel states
        initializePanelStates()
        
        // Monitor performance mode changes
        viewModelScope.launch {
            performanceMonitor.performanceMode.collect { mode ->
                handlePerformanceModeChange(mode)
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
     * Transport control actions
     */
    fun onPlayPause() {
        val current = _transportState.value
        _transportState.value = current.copy(isPlaying = !current.isPlaying)
    }
    
    fun onStop() {
        _transportState.value = _transportState.value.copy(
            isPlaying = false,
            currentPosition = 0
        )
    }
    
    fun onRecord() {
        val current = _transportState.value
        _transportState.value = current.copy(isRecording = !current.isRecording)
    }
    
    fun onBpmChange(newBpm: Int) {
        if (newBpm in 60..200) {
            _transportState.value = _transportState.value.copy(bpm = newBpm)
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
        _uiPreferences.value = preferences
        
        // Apply performance mode if specified
        preferences.performanceMode.let { mode ->
            performanceMonitor.setPerformanceMode(mode)
        }
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
        val currentPreferences = _uiPreferences.value
        
        // Update UI preferences to match performance mode
        val updatedPreferences = when (mode) {
            PerformanceMode.HIGH_PERFORMANCE -> currentPreferences.copy(
                enableAnimations = true,
                performanceMode = mode
            )
            PerformanceMode.BALANCED -> currentPreferences.copy(
                enableAnimations = true,
                performanceMode = mode
            )
            PerformanceMode.BATTERY_SAVER -> currentPreferences.copy(
                enableAnimations = false,
                performanceMode = mode,
                collapsedSectionsByDefault = currentPreferences.collapsedSectionsByDefault + 
                    setOf(SectionType.UTILITY_PANEL, SectionType.QUICK_ACCESS)
            )
        }
        
        _uiPreferences.value = updatedPreferences
    }
    
    override fun onCleared() {
        super.onCleared()
        performanceMonitor.stopMonitoring()
    }
}