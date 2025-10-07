package com.high.theone.features.compactui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.high.theone.model.*
import com.high.theone.ui.layout.ResponsiveLayoutUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layout state manager that handles UI configuration, panel visibility, 
 * collapsible section tracking, and layout preference persistence.
 * 
 * Requirements addressed:
 * - 6.5: Layout changes preserve user context and current state
 * - 10.3: Layout preference persistence across app sessions
 * - 10.4: Quick switching between layout presets
 */
@Singleton
class LayoutStateManager @Inject constructor(
    private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        LAYOUT_PREFERENCES_NAME, 
        Context.MODE_PRIVATE
    )
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val _layoutState = MutableStateFlow(createDefaultLayoutState())
    val layoutState: StateFlow<LayoutState> = _layoutState.asStateFlow()
    
    private val _customizationPreferences = MutableStateFlow(loadCustomizationPreferences())
    val customizationPreferences: StateFlow<UICustomizationPreferences> = _customizationPreferences.asStateFlow()
    
    init {
        loadLayoutState()
    }
    
    /**
     * Update screen configuration and adapt layout accordingly
     */
    fun updateScreenConfiguration(configuration: ScreenConfiguration) {
        val currentState = _layoutState.value
        val newState = currentState.copy(
            configuration = configuration,
            // Reset panel states if layout mode changed significantly
            panelVisibility = if (configuration.layoutMode != currentState.configuration.layoutMode) {
                adaptPanelVisibilityToLayoutMode(currentState.panelVisibility, configuration.layoutMode)
            } else {
                currentState.panelVisibility
            }
        )
        
        _layoutState.value = newState
        saveLayoutState()
    }
    
    /**
     * Toggle panel visibility
     */
    fun togglePanelVisibility(panelType: PanelType) {
        val currentState = _layoutState.value
        val currentVisibility = currentState.panelVisibility[panelType] ?: false
        
        val newVisibility = currentState.panelVisibility.toMutableMap().apply {
            this[panelType] = !currentVisibility
        }
        
        val newActivePanels = if (!currentVisibility) {
            currentState.activePanels + panelType
        } else {
            currentState.activePanels - panelType
        }
        
        _layoutState.value = currentState.copy(
            panelVisibility = newVisibility,
            activePanels = newActivePanels
        )
        
        saveLayoutState()
    }
    
    /**
     * Set panel visibility state
     */
    fun setPanelVisibility(panelType: PanelType, isVisible: Boolean) {
        val currentState = _layoutState.value
        val newVisibility = currentState.panelVisibility.toMutableMap().apply {
            this[panelType] = isVisible
        }
        
        val newActivePanels = if (isVisible) {
            currentState.activePanels + panelType
        } else {
            currentState.activePanels - panelType
        }
        
        _layoutState.value = currentState.copy(
            panelVisibility = newVisibility,
            activePanels = newActivePanels
        )
        
        saveLayoutState()
    }
    
    /**
     * Toggle section collapse state
     */
    fun toggleSectionCollapse(sectionType: SectionType) {
        val currentState = _layoutState.value
        val isCurrentlyCollapsed = currentState.collapsedSections.contains(sectionType)
        
        val newCollapsedSections = if (isCurrentlyCollapsed) {
            currentState.collapsedSections - sectionType
        } else {
            currentState.collapsedSections + sectionType
        }
        
        _layoutState.value = currentState.copy(
            collapsedSections = newCollapsedSections
        )
        
        saveLayoutState()
    }
    
    /**
     * Set section collapse state
     */
    fun setSectionCollapsed(sectionType: SectionType, isCollapsed: Boolean) {
        val currentState = _layoutState.value
        val newCollapsedSections = if (isCollapsed) {
            currentState.collapsedSections + sectionType
        } else {
            currentState.collapsedSections - sectionType
        }
        
        _layoutState.value = currentState.copy(
            collapsedSections = newCollapsedSections
        )
        
        saveLayoutState()
    }
    
    /**
     * Update bottom sheet state
     */
    fun updateBottomSheetState(panelState: PanelState) {
        val currentState = _layoutState.value
        _layoutState.value = currentState.copy(
            bottomSheetState = panelState
        )
        
        saveLayoutState()
    }
    
    /**
     * Update side panel state
     */
    fun updateSidePanelState(panelState: PanelState) {
        val currentState = _layoutState.value
        _layoutState.value = currentState.copy(
            sidePanel = panelState
        )
        
        saveLayoutState()
    }
    
    /**
     * Apply layout preset
     */
    fun applyLayoutPreset(preset: LayoutPreset) {
        val currentState = _layoutState.value
        
        _layoutState.value = currentState.copy(
            panelVisibility = preset.panelVisibility,
            collapsedSections = preset.collapsedSections,
            activePanels = preset.panelVisibility.filterValues { it }.keys
        )
        
        saveLayoutState()
    }
    
    /**
     * Save current layout as preset
     */
    fun saveLayoutPreset(name: String): LayoutPreset {
        val currentState = _layoutState.value
        val preset = LayoutPreset(
            name = name,
            layoutMode = currentState.configuration.layoutMode,
            panelVisibility = currentState.panelVisibility,
            collapsedSections = currentState.collapsedSections
        )
        
        savePreset(preset)
        return preset
    }
    
    /**
     * Update customization preferences
     */
    fun updateCustomizationPreferences(preferences: UICustomizationPreferences) {
        _customizationPreferences.value = preferences
        saveCustomizationPreferences(preferences)
        
        // Apply preferences to current layout state
        applyCustomizationPreferences(preferences)
    }
    
    /**
     * Reset layout to defaults
     */
    fun resetToDefaults() {
        _layoutState.value = createDefaultLayoutState()
        _customizationPreferences.value = UICustomizationPreferences()
        
        saveLayoutState()
        saveCustomizationPreferences(_customizationPreferences.value)
    }
    
    /**
     * Get available layout presets
     */
    fun getLayoutPresets(): List<LayoutPreset> {
        return loadLayoutPresets()
    }
    
    private fun createDefaultLayoutState(): LayoutState {
        val defaultConfiguration = ScreenConfiguration(
            screenWidth = 360.dp,
            screenHeight = 640.dp,
            orientation = Orientation.PORTRAIT,
            densityDpi = 420,
            isTablet = false
        )
        
        return LayoutState(
            configuration = defaultConfiguration,
            activePanels = emptySet(),
            panelVisibility = PanelType.values().associateWith { false },
            collapsedSections = emptySet(),
            bottomSheetState = PanelState(),
            sidePanel = PanelState()
        )
    }
    
    private fun adaptPanelVisibilityToLayoutMode(
        currentVisibility: Map<PanelType, Boolean>,
        newLayoutMode: LayoutMode
    ): Map<PanelType, Boolean> {
        return when (newLayoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> {
                // In compact mode, hide less essential panels by default
                currentVisibility.mapValues { (panelType, isVisible) ->
                    when (panelType) {
                        PanelType.SAMPLING, PanelType.MIDI -> isVisible
                        else -> false
                    }
                }
            }
            LayoutMode.LANDSCAPE, LayoutMode.TABLET -> {
                // In landscape/tablet mode, more panels can be visible
                currentVisibility
            }
            else -> currentVisibility
        }
    }
    
    private fun applyCustomizationPreferences(preferences: UICustomizationPreferences) {
        val currentState = _layoutState.value
        
        // Apply hidden panels
        val newVisibility = currentState.panelVisibility.toMutableMap().apply {
            preferences.hiddenPanels.forEach { panelType ->
                this[panelType] = false
            }
        }
        
        // Apply default collapsed sections
        val newCollapsedSections = currentState.collapsedSections + preferences.collapsedSectionsByDefault
        
        _layoutState.value = currentState.copy(
            panelVisibility = newVisibility,
            collapsedSections = newCollapsedSections,
            activePanels = newVisibility.filterValues { it }.keys
        )
    }
    
    private fun saveLayoutState() {
        try {
            val currentState = _layoutState.value
            val stateJson = json.encodeToString(currentState)
            preferences.edit()
                .putString(KEY_LAYOUT_STATE, stateJson)
                .apply()
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }
    
    private fun loadLayoutState() {
        try {
            val stateJson = preferences.getString(KEY_LAYOUT_STATE, null)
            if (stateJson != null) {
                val loadedState = json.decodeFromString<LayoutState>(stateJson)
                _layoutState.value = loadedState
            }
        } catch (e: Exception) {
            // If loading fails, use default state
            e.printStackTrace()
            _layoutState.value = createDefaultLayoutState()
        }
    }
    
    private fun saveCustomizationPreferences(preferences: UICustomizationPreferences) {
        try {
            val preferencesJson = json.encodeToString(preferences)
            this.preferences.edit()
                .putString(KEY_CUSTOMIZATION_PREFERENCES, preferencesJson)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadCustomizationPreferences(): UICustomizationPreferences {
        return try {
            val preferencesJson = preferences.getString(KEY_CUSTOMIZATION_PREFERENCES, null)
            if (preferencesJson != null) {
                json.decodeFromString<UICustomizationPreferences>(preferencesJson)
            } else {
                UICustomizationPreferences()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UICustomizationPreferences()
        }
    }
    
    private fun savePreset(preset: LayoutPreset) {
        try {
            val presets = loadLayoutPresets().toMutableList()
            val existingIndex = presets.indexOfFirst { it.name == preset.name }
            
            if (existingIndex >= 0) {
                presets[existingIndex] = preset
            } else {
                presets.add(preset)
            }
            
            val presetsJson = json.encodeToString(presets)
            preferences.edit()
                .putString(KEY_LAYOUT_PRESETS, presetsJson)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadLayoutPresets(): List<LayoutPreset> {
        return try {
            val presetsJson = preferences.getString(KEY_LAYOUT_PRESETS, null)
            if (presetsJson != null) {
                json.decodeFromString<List<LayoutPreset>>(presetsJson)
            } else {
                createDefaultPresets()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createDefaultPresets()
        }
    }
    
    private fun createDefaultPresets(): List<LayoutPreset> {
        return listOf(
            LayoutPreset(
                name = "Minimal",
                layoutMode = LayoutMode.COMPACT_PORTRAIT,
                panelVisibility = mapOf(
                    PanelType.SAMPLING to false,
                    PanelType.MIDI to false,
                    PanelType.MIXER to false,
                    PanelType.SETTINGS to false,
                    PanelType.SAMPLE_EDITOR to false
                ),
                collapsedSections = setOf(SectionType.UTILITY_PANEL)
            ),
            LayoutPreset(
                name = "Producer",
                layoutMode = LayoutMode.STANDARD_PORTRAIT,
                panelVisibility = mapOf(
                    PanelType.SAMPLING to true,
                    PanelType.MIDI to false,
                    PanelType.MIXER to true,
                    PanelType.SETTINGS to false,
                    PanelType.SAMPLE_EDITOR to false
                ),
                collapsedSections = emptySet()
            ),
            LayoutPreset(
                name = "Live Performance",
                layoutMode = LayoutMode.LANDSCAPE,
                panelVisibility = mapOf(
                    PanelType.SAMPLING to false,
                    PanelType.MIDI to true,
                    PanelType.MIXER to true,
                    PanelType.SETTINGS to false,
                    PanelType.SAMPLE_EDITOR to false
                ),
                collapsedSections = setOf(SectionType.UTILITY_PANEL)
            )
        )
    }
    
    companion object {
        private const val LAYOUT_PREFERENCES_NAME = "layout_preferences"
        private const val KEY_LAYOUT_STATE = "layout_state"
        private const val KEY_CUSTOMIZATION_PREFERENCES = "customization_preferences"
        private const val KEY_LAYOUT_PRESETS = "layout_presets"
    }
}

/**
 * Layout preset data class for saving and loading layout configurations
 */
@kotlinx.serialization.Serializable
data class LayoutPreset(
    val name: String,
    val layoutMode: LayoutMode,
    val panelVisibility: Map<PanelType, Boolean>,
    val collapsedSections: Set<SectionType>
)