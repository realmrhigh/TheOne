package com.high.theone.features.compactui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.model.*
import com.high.theone.ui.layout.ResponsiveLayoutUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Composable function to provide layout state management in Compose UI
 */
@Composable
fun rememberLayoutState(
    layoutStateManager: LayoutStateManager = hiltViewModel<LayoutStateViewModel>().layoutStateManager
): LayoutStateController {
    val screenConfiguration = ResponsiveLayoutUtils.calculateScreenConfiguration()
    val layoutState by layoutStateManager.layoutState.collectAsState()
    val customizationPreferences by layoutStateManager.customizationPreferences.collectAsState()
    
    // Update screen configuration when it changes
    LaunchedEffect(screenConfiguration) {
        layoutStateManager.updateScreenConfiguration(screenConfiguration)
    }
    
    return remember(layoutStateManager) {
        LayoutStateController(layoutStateManager)
    }
}

/**
 * Controller class that provides convenient methods for managing layout state from Compose
 */
class LayoutStateController(
    private val layoutStateManager: LayoutStateManager
) {
    val layoutState: StateFlow<LayoutState> = layoutStateManager.layoutState
    val customizationPreferences: StateFlow<UICustomizationPreferences> = layoutStateManager.customizationPreferences
    
    fun togglePanelVisibility(panelType: PanelType) {
        layoutStateManager.togglePanelVisibility(panelType)
    }
    
    fun setPanelVisibility(panelType: PanelType, isVisible: Boolean) {
        layoutStateManager.setPanelVisibility(panelType, isVisible)
    }
    
    fun toggleSectionCollapse(sectionType: SectionType) {
        layoutStateManager.toggleSectionCollapse(sectionType)
    }
    
    fun setSectionCollapsed(sectionType: SectionType, isCollapsed: Boolean) {
        layoutStateManager.setSectionCollapsed(sectionType, isCollapsed)
    }
    
    fun updateBottomSheetState(panelState: PanelState) {
        layoutStateManager.updateBottomSheetState(panelState)
    }
    
    fun updateSidePanelState(panelState: PanelState) {
        layoutStateManager.updateSidePanelState(panelState)
    }
    
    fun applyLayoutPreset(preset: LayoutPreset) {
        layoutStateManager.applyLayoutPreset(preset)
    }
    
    fun saveLayoutPreset(name: String): LayoutPreset {
        return layoutStateManager.saveLayoutPreset(name)
    }
    
    fun updateCustomizationPreferences(preferences: UICustomizationPreferences) {
        layoutStateManager.updateCustomizationPreferences(preferences)
    }
    
    fun resetToDefaults() {
        layoutStateManager.resetToDefaults()
    }
    
    fun getLayoutPresets(): List<LayoutPreset> {
        return layoutStateManager.getLayoutPresets()
    }
}

/**
 * ViewModel wrapper for LayoutStateManager to work with Hilt
 */
@HiltViewModel
class LayoutStateViewModel @Inject constructor(
    val layoutStateManager: LayoutStateManager
) : ViewModel()

/**
 * Composable for observing layout mode changes
 */
@Composable
fun OnLayoutModeChange(
    layoutState: LayoutState,
    onLayoutModeChange: (LayoutMode) -> Unit
) {
    val currentLayoutMode = layoutState.configuration.layoutMode
    
    LaunchedEffect(currentLayoutMode) {
        onLayoutModeChange(currentLayoutMode)
    }
}

/**
 * Composable for observing panel visibility changes
 */
@Composable
fun OnPanelVisibilityChange(
    layoutState: LayoutState,
    panelType: PanelType,
    onVisibilityChange: (Boolean) -> Unit
) {
    val isVisible = layoutState.panelVisibility[panelType] ?: false
    
    LaunchedEffect(isVisible) {
        onVisibilityChange(isVisible)
    }
}

/**
 * Composable for observing section collapse changes
 */
@Composable
fun OnSectionCollapseChange(
    layoutState: LayoutState,
    sectionType: SectionType,
    onCollapseChange: (Boolean) -> Unit
) {
    val isCollapsed = layoutState.collapsedSections.contains(sectionType)
    
    LaunchedEffect(isCollapsed) {
        onCollapseChange(isCollapsed)
    }
}