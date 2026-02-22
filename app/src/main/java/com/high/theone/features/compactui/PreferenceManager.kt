package com.high.theone.features.compactui

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.high.theone.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user preferences and customization settings for the compact UI
 */
@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "compact_ui_preferences")
    
    companion object {
        // Preference keys
        private val LAYOUT_CUSTOMIZATION_KEY = stringPreferencesKey("layout_customization")
        private val FEATURE_VISIBILITY_KEY = stringPreferencesKey("feature_visibility")
        private val LAYOUT_PRESETS_KEY = stringPreferencesKey("layout_presets")
        private val ACTIVE_PRESET_KEY = stringPreferencesKey("active_preset")
        private val UI_PREFERENCES_KEY = stringPreferencesKey("ui_preferences")
        private val PERFORMANCE_MODE_KEY = stringPreferencesKey("performance_mode")
        private val PANEL_POSITIONS_KEY = stringPreferencesKey("panel_positions")
        private val COLLAPSED_SECTIONS_KEY = stringSetPreferencesKey("collapsed_sections")
        private val BPM_KEY = intPreferencesKey("bpm")
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Layout customization preferences
     */
    val layoutCustomization: Flow<LayoutCustomization> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[LAYOUT_CUSTOMIZATION_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<LayoutCustomization>(jsonString)
                } catch (e: Exception) {
                    LayoutCustomization()
                }
            } else {
                LayoutCustomization()
            }
        }
        .catch { emit(LayoutCustomization()) }
    
    /**
     * Feature visibility preferences
     */
    val featureVisibility: Flow<FeatureVisibilityPreferences> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[FEATURE_VISIBILITY_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<FeatureVisibilityPreferences>(jsonString)
                } catch (e: Exception) {
                    FeatureVisibilityPreferences()
                }
            } else {
                FeatureVisibilityPreferences()
            }
        }
        .catch { emit(FeatureVisibilityPreferences()) }
    
    /**
     * Layout presets
     */
    val layoutPresets: Flow<List<com.high.theone.model.LayoutPreset>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[LAYOUT_PRESETS_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<com.high.theone.model.LayoutPreset>>(jsonString)
                } catch (e: Exception) {
                    getDefaultPresets()
                }
            } else {
                getDefaultPresets()
            }
        }
        .catch { emit(getDefaultPresets()) }
    
    /**
     * Active preset ID
     */
    val activePreset: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ACTIVE_PRESET_KEY] }
        .catch { emit(null) }
    
    /**
     * UI customization preferences
     */
    val uiPreferences: Flow<UICustomizationPreferences> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[UI_PREFERENCES_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<UICustomizationPreferences>(jsonString)
                } catch (e: Exception) {
                    UICustomizationPreferences()
                }
            } else {
                UICustomizationPreferences()
            }
        }
        .catch { emit(UICustomizationPreferences()) }
    
    /**
     * Panel positions
     */
    val panelPositions: Flow<Map<PanelType, PanelPosition>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PANEL_POSITIONS_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<Map<PanelType, PanelPosition>>(jsonString)
                } catch (e: Exception) {
                    getDefaultPanelPositions()
                }
            } else {
                getDefaultPanelPositions()
            }
        }
        .catch { emit(getDefaultPanelPositions()) }
    
    /**
     * Collapsed sections
     */
    val collapsedSections: Flow<Set<SectionType>> = context.dataStore.data
        .map { preferences ->
            preferences[COLLAPSED_SECTIONS_KEY]?.map { 
                try {
                    SectionType.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }?.filterNotNull()?.toSet() ?: emptySet()
        }
        .catch { emit(emptySet()) }
    
    /**
     * BPM (tempo) setting
     */
    val bpm: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[BPM_KEY] ?: 120 }
        .catch { emit(120) }
    
    /**
     * Save layout customization
     */
    suspend fun saveLayoutCustomization(customization: LayoutCustomization) {
        context.dataStore.edit { preferences ->
            preferences[LAYOUT_CUSTOMIZATION_KEY] = json.encodeToString(customization)
        }
    }
    
    /**
     * Save feature visibility preferences
     */
    suspend fun saveFeatureVisibility(visibility: FeatureVisibilityPreferences) {
        context.dataStore.edit { preferences ->
            preferences[FEATURE_VISIBILITY_KEY] = json.encodeToString(visibility)
        }
    }
    
    /**
     * Save layout presets
     */
    suspend fun saveLayoutPresets(presets: List<com.high.theone.model.LayoutPreset>) {
        context.dataStore.edit { preferences ->
            preferences[LAYOUT_PRESETS_KEY] = json.encodeToString(presets)
        }
    }
    
    /**
     * Set active preset
     */
    suspend fun setActivePreset(presetId: String?) {
        context.dataStore.edit { preferences ->
            if (presetId != null) {
                preferences[ACTIVE_PRESET_KEY] = presetId
            } else {
                preferences.remove(ACTIVE_PRESET_KEY)
            }
        }
    }
    
    /**
     * Save UI preferences
     */
    suspend fun saveUIPreferences(preferences: UICustomizationPreferences) {
        context.dataStore.edit { prefs ->
            prefs[UI_PREFERENCES_KEY] = json.encodeToString(preferences)
        }
    }
    
    /**
     * Save panel positions
     */
    suspend fun savePanelPositions(positions: Map<PanelType, PanelPosition>) {
        context.dataStore.edit { preferences ->
            preferences[PANEL_POSITIONS_KEY] = json.encodeToString(positions)
        }
    }
    
    /**
     * Save collapsed sections
     */
    suspend fun saveCollapsedSections(sections: Set<SectionType>) {
        context.dataStore.edit { preferences ->
            preferences[COLLAPSED_SECTIONS_KEY] = sections.map { it.name }.toSet()
        }
    }
    
    /**
     * Create a new layout preset
     */
    suspend fun createPreset(
        name: String,
        layoutCustomization: LayoutCustomization,
        featureVisibility: FeatureVisibilityPreferences,
        panelPositions: Map<PanelType, PanelPosition>
    ): com.high.theone.model.LayoutPreset {
        val preset = LayoutPreset(
            id = "preset_${System.currentTimeMillis()}",
            name = name,
            layoutCustomization = layoutCustomization,
            featureVisibility = featureVisibility,
            panelPositions = panelPositions,
            createdAt = System.currentTimeMillis()
        )
        
        val currentPresets = layoutPresets.first()
        saveLayoutPresets(currentPresets + preset)
        
        return preset
    }
    
    /**
     * Delete a layout preset
     */
    suspend fun deletePreset(presetId: String) {
        val currentPresets = layoutPresets.first()
        val updatedPresets = currentPresets.filter { it.id != presetId }
        saveLayoutPresets(updatedPresets)
        
        // Clear active preset if it was deleted
        val currentActive = activePreset.first()
        if (currentActive == presetId) {
            setActivePreset(null)
        }
    }
    
    /**
     * Apply a layout preset
     */
    suspend fun applyPreset(presetId: String) {
        val presets = layoutPresets.first()
        val preset = presets.find { it.id == presetId } ?: return
        
        // Apply all settings from the preset
        saveLayoutCustomization(preset.layoutCustomization)
        saveFeatureVisibility(preset.featureVisibility)
        savePanelPositions(preset.panelPositions)
        setActivePreset(presetId)
    }
    
    /**
     * Reset to default settings
     */
    suspend fun resetToDefaults() {
        saveLayoutCustomization(LayoutCustomization())
        saveFeatureVisibility(FeatureVisibilityPreferences())
        savePanelPositions(getDefaultPanelPositions())
        saveCollapsedSections(emptySet())
        saveBpm(120) // Reset to default BPM
        setActivePreset(null)
    }
    
    /**
     * Get default layout presets
     */
    private fun getDefaultPresets(): List<com.high.theone.model.LayoutPreset> {
        return listOf(
            LayoutPreset(
                id = "default",
                name = "Default Layout",
                layoutCustomization = LayoutCustomization(),
                featureVisibility = FeatureVisibilityPreferences(),
                panelPositions = getDefaultPanelPositions(),
                createdAt = 0L
            ),
            LayoutPreset(
                id = "performance",
                name = "Performance Mode",
                layoutCustomization = LayoutCustomization(
                    enableAnimations = false,
                    compactMode = true
                ),
                featureVisibility = FeatureVisibilityPreferences(
                    showAdvancedControls = false,
                    showVisualEffects = false
                ),
                panelPositions = getDefaultPanelPositions(),
                createdAt = 0L
            ),
            LayoutPreset(
                id = "producer",
                name = "Producer Layout",
                layoutCustomization = LayoutCustomization(
                    drumPadSize = ComponentSize.LARGE,
                    sequencerHeight = ComponentSize.LARGE
                ),
                featureVisibility = FeatureVisibilityPreferences(
                    showAdvancedControls = true,
                    showMidiControls = true,
                    showMixerControls = true
                ),
                panelPositions = getDefaultPanelPositions(),
                createdAt = 0L
            )
        )
    }
    
    /**
     * Save BPM setting
     */
    suspend fun saveBpm(bpm: Int) {
        context.dataStore.edit { preferences ->
            preferences[BPM_KEY] = bpm
        }
    }
    
    /**
     * Get default panel positions
     */
    private fun getDefaultPanelPositions(): Map<PanelType, PanelPosition> {
        return mapOf(
            PanelType.SAMPLING to PanelPosition.BOTTOM_SHEET,
            PanelType.MIDI to PanelPosition.SIDE_PANEL,
            PanelType.MIXER to PanelPosition.BOTTOM_SHEET,
            PanelType.SETTINGS to PanelPosition.SIDE_PANEL,
            PanelType.SAMPLE_EDITOR to PanelPosition.BOTTOM_SHEET
        )
    }
}