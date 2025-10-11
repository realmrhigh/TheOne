package com.high.theone.features.compactui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing layout presets and customization
 */
@HiltViewModel
class LayoutPresetManager @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LayoutPresetUiState())
    val uiState: StateFlow<LayoutPresetUiState> = _uiState.asStateFlow()
    
    // Observe preferences as StateFlows
    val layoutCustomization = preferenceManager.layoutCustomization
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutCustomization())
    val featureVisibility = preferenceManager.featureVisibility
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureVisibilityPreferences())
    val layoutPresets = preferenceManager.layoutPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activePreset = preferenceManager.activePreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val panelPositions = preferenceManager.panelPositions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap<PanelType, PanelPosition>())
    
    init {
        // Combine all preference flows into UI state
        viewModelScope.launch {
            combine(
                layoutCustomization,
                featureVisibility,
                layoutPresets,
                activePreset,
                panelPositions
            ) { layout, visibility, presets, active, positions ->
                _uiState.value = _uiState.value.copy(
                    layoutCustomization = layout,
                    featureVisibility = visibility,
                    layoutPresets = presets,
                    activePresetId = active,
                    panelPositions = positions,
                    isLoading = false
                )
            }.collect { }
        }
    }
    
    /**
     * Create a new layout preset from current settings
     */
    fun createPreset(name: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val currentLayout = layoutCustomization.value
                val currentVisibility = featureVisibility.value
                val currentPositions = panelPositions.value
                
                val preset = preferenceManager.createPreset(
                    name = name,
                    layoutCustomization = currentLayout,
                    featureVisibility = currentVisibility,
                    panelPositions = currentPositions
                )
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Preset '${preset.name}' created successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create preset: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Apply a layout preset
     */
    fun applyPreset(presetId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                preferenceManager.applyPreset(presetId)
                
                val presetName = layoutPresets.value.find { it.id == presetId }?.name ?: "Unknown"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Applied preset '$presetName'"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to apply preset: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Delete a layout preset
     */
    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            try {
                val presetName = layoutPresets.value.find { it.id == presetId }?.name ?: "Unknown"
                
                preferenceManager.deletePreset(presetId)
                
                _uiState.value = _uiState.value.copy(
                    message = "Deleted preset '$presetName'"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete preset: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Update layout customization
     */
    fun updateLayoutCustomization(customization: LayoutCustomization) {
        viewModelScope.launch {
            try {
                preferenceManager.saveLayoutCustomization(customization)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save layout customization: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Update feature visibility
     */
    fun updateFeatureVisibility(visibility: FeatureVisibilityPreferences) {
        viewModelScope.launch {
            try {
                preferenceManager.saveFeatureVisibility(visibility)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save feature visibility: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Update panel position
     */
    fun updatePanelPosition(panelType: PanelType, position: PanelPosition) {
        viewModelScope.launch {
            try {
                val currentPositions = panelPositions.value.toMutableMap()
                currentPositions[panelType] = position
                preferenceManager.savePanelPositions(currentPositions)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update panel position: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                preferenceManager.resetToDefaults()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Settings reset to defaults"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to reset settings: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Duplicate an existing preset
     */
    fun duplicatePreset(presetId: String, newName: String) {
        viewModelScope.launch {
            try {
                val preset = layoutPresets.value.find { it.id == presetId } ?: return@launch
                
                preferenceManager.createPreset(
                    name = newName,
                    layoutCustomization = preset.layoutCustomization,
                    featureVisibility = preset.featureVisibility,
                    panelPositions = preset.panelPositions
                )
                
                _uiState.value = _uiState.value.copy(
                    message = "Duplicated preset as '$newName'"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to duplicate preset: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Export preset as JSON string
     */
    fun exportPreset(presetId: String): String? {
        return try {
            val preset = layoutPresets.value.find { it.id == presetId }
            preset?.let { 
                kotlinx.serialization.json.Json.encodeToString(com.high.theone.model.LayoutPreset.serializer(), it)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to export preset: ${e.message}"
            )
            null
        }
    }
    
    /**
     * Import preset from JSON string
     */
    fun importPreset(jsonString: String, newName: String? = null) {
        viewModelScope.launch {
            try {
                val preset = kotlinx.serialization.json.Json.decodeFromString(
                    com.high.theone.model.LayoutPreset.serializer(), 
                    jsonString
                )
                
                val importedPreset = preset.copy(
                    id = "imported_${System.currentTimeMillis()}",
                    name = newName ?: "${preset.name} (Imported)",
                    createdAt = System.currentTimeMillis(),
                    isDefault = false
                )
                
                val currentPresets = layoutPresets.value
                preferenceManager.saveLayoutPresets(currentPresets + importedPreset)
                
                _uiState.value = _uiState.value.copy(
                    message = "Imported preset '${importedPreset.name}'"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to import preset: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clear messages
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

/**
 * UI state for layout preset management
 */
data class LayoutPresetUiState(
    val layoutCustomization: LayoutCustomization = LayoutCustomization(),
    val featureVisibility: FeatureVisibilityPreferences = FeatureVisibilityPreferences(),
    val layoutPresets: List<com.high.theone.model.LayoutPreset> = emptyList(),
    val activePresetId: String? = null,
    val panelPositions: Map<PanelType, PanelPosition> = emptyMap(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val errorMessage: String? = null
)