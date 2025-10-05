package com.high.theone.features.midi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.midi.mapping.MidiMappingEngine
import com.high.theone.midi.mapping.MidiLearnManager
import com.high.theone.midi.mapping.MidiLearnState
import com.high.theone.midi.mapping.MidiMappingConflict
import com.high.theone.midi.mapping.MidiConflictResolution
import com.high.theone.midi.mapping.MidiConflictResolutionType
import com.high.theone.midi.model.*
import com.high.theone.midi.repository.MidiMappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MIDI mapping configuration screen.
 * Manages mapping profiles, MIDI learn functionality, and conflict resolution.
 */
@HiltViewModel
class MidiMappingViewModel @Inject constructor(
    private val mappingEngine: MidiMappingEngine,
    private val learnManager: MidiLearnManager,
    private val mappingRepository: MidiMappingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MidiMappingUiState())
    val uiState: StateFlow<MidiMappingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MidiMappingEvent>()
    val events: SharedFlow<MidiMappingEvent> = _events.asSharedFlow()

    init {
        observeMappingEngine()
        observeLearnManager()
        loadMappingProfiles()
    }

    private fun observeMappingEngine() {
        viewModelScope.launch {
            combine(
                mappingEngine.activeMappings,
                mappingEngine.currentProfile,
                mappingEngine.mappingConflicts
            ) { activeMappings, currentProfile, conflicts ->
                _uiState.value = _uiState.value.copy(
                    activeMappings = activeMappings,
                    currentProfile = currentProfile,
                    mappingConflicts = conflicts
                )
            }.collect()
        }
    }

    private fun observeLearnManager() {
        viewModelScope.launch {
            combine(
                learnManager.learnState,
                learnManager.learnProgress
            ) { learnState, learnProgress ->
                _uiState.value = _uiState.value.copy(
                    learnState = learnState,
                    learnProgress = learnProgress
                )
            }.collect()
        }
    }

    private fun loadMappingProfiles() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val profiles = mappingRepository.getAllMappings()
                val allProfiles = mappingEngine.getAllMappingProfiles() + profiles
                
                _uiState.value = _uiState.value.copy(
                    mappingProfiles = allProfiles,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load mapping profiles: ${e.message}"
                )
            }
        }
    }

    fun onCreateNewMapping() {
        val newMapping = MidiMapping(
            id = "mapping_${System.currentTimeMillis()}",
            name = "New Mapping",
            deviceId = null,
            mappings = emptyList(),
            isActive = false
        )
        
        _uiState.value = _uiState.value.copy(
            selectedMapping = newMapping,
            isEditingMapping = true
        )
    }

    fun onEditMapping(mapping: MidiMapping) {
        _uiState.value = _uiState.value.copy(
            selectedMapping = mapping,
            isEditingMapping = true
        )
    }

    fun onSaveMapping(mapping: MidiMapping) {
        viewModelScope.launch {
            try {
                val result = mappingEngine.updateMappingProfile(mapping)
                if (result.isSuccess) {
                    mappingRepository.saveMapping(mapping)
                    _uiState.value = _uiState.value.copy(
                        isEditingMapping = false,
                        selectedMapping = null
                    )
                    _events.emit(MidiMappingEvent.MappingSaved(mapping))
                    loadMappingProfiles()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save mapping"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save mapping: ${e.message}"
                )
            }
        }
    }

    fun onDeleteMapping(mappingId: String) {
        viewModelScope.launch {
            try {
                val result = mappingEngine.removeMappingProfile(mappingId)
                if (result.isSuccess) {
                    mappingRepository.deleteMapping(mappingId)
                    _events.emit(MidiMappingEvent.MappingDeleted(mappingId))
                    loadMappingProfiles()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete mapping"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete mapping: ${e.message}"
                )
            }
        }
    }

    fun onActivateMapping(mappingId: String) {
        viewModelScope.launch {
            try {
                val result = mappingEngine.setActiveMappingProfile(mappingId)
                if (result.isSuccess) {
                    _events.emit(MidiMappingEvent.MappingActivated(mappingId))
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to activate mapping"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to activate mapping: ${e.message}"
                )
            }
        }
    }

    fun onStartMidiLearn(targetType: MidiTargetType, targetId: String) {
        viewModelScope.launch {
            try {
                val result = learnManager.startMidiLearn(targetType, targetId)
                if (result.isSuccess) {
                    _events.emit(MidiMappingEvent.MidiLearnStarted(targetType, targetId))
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to start MIDI learn"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to start MIDI learn: ${e.message}"
                )
            }
        }
    }

    fun onStopMidiLearn() {
        viewModelScope.launch {
            try {
                val learnedMapping = learnManager.stopMidiLearn()
                if (learnedMapping != null) {
                    _events.emit(MidiMappingEvent.MidiLearnCompleted(learnedMapping))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to stop MIDI learn: ${e.message}"
                )
            }
        }
    }

    fun onCancelMidiLearn() {
        viewModelScope.launch {
            try {
                learnManager.cancelMidiLearn()
                _events.emit(MidiMappingEvent.MidiLearnCancelled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to cancel MIDI learn: ${e.message}"
                )
            }
        }
    }

    fun onResolveConflict(conflict: MidiMappingConflict, resolution: MidiConflictResolution) {
        viewModelScope.launch {
            try {
                val result = mappingEngine.resolveMappingConflict(conflict, resolution)
                if (result.isSuccess) {
                    _events.emit(MidiMappingEvent.ConflictResolved(conflict))
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to resolve conflict"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to resolve conflict: ${e.message}"
                )
            }
        }
    }

    fun onAddParameterMapping(mapping: MidiMapping, parameterMapping: MidiParameterMapping) {
        val updatedMappings = mapping.mappings + parameterMapping
        val updatedMapping = mapping.copy(mappings = updatedMappings)
        onSaveMapping(updatedMapping)
    }

    fun onRemoveParameterMapping(mapping: MidiMapping, parameterMapping: MidiParameterMapping) {
        val updatedMappings = mapping.mappings - parameterMapping
        val updatedMapping = mapping.copy(mappings = updatedMappings)
        onSaveMapping(updatedMapping)
    }

    fun onUpdateParameterMapping(
        mapping: MidiMapping,
        oldMapping: MidiParameterMapping,
        newMapping: MidiParameterMapping
    ) {
        val updatedMappings = mapping.mappings.map { 
            if (it == oldMapping) newMapping else it 
        }
        val updatedMapping = mapping.copy(mappings = updatedMappings)
        onSaveMapping(updatedMapping)
    }

    fun onCancelEdit() {
        _uiState.value = _uiState.value.copy(
            isEditingMapping = false,
            selectedMapping = null
        )
    }

    fun onDismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onShowConflictDialog(conflict: MidiMappingConflict) {
        _uiState.value = _uiState.value.copy(
            selectedConflict = conflict,
            showConflictDialog = true
        )
    }

    fun onDismissConflictDialog() {
        _uiState.value = _uiState.value.copy(
            selectedConflict = null,
            showConflictDialog = false
        )
    }
}

/**
 * UI state for MIDI mapping screen
 */
data class MidiMappingUiState(
    val mappingProfiles: List<MidiMapping> = emptyList(),
    val activeMappings: List<MidiMapping> = emptyList(),
    val currentProfile: MidiMapping? = null,
    val selectedMapping: MidiMapping? = null,
    val isEditingMapping: Boolean = false,
    val mappingConflicts: List<MidiMappingConflict> = emptyList(),
    val selectedConflict: MidiMappingConflict? = null,
    val showConflictDialog: Boolean = false,
    val learnState: MidiLearnState = MidiLearnState.Inactive,
    val learnProgress: com.high.theone.midi.mapping.MidiLearnProgress? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Events emitted by the MIDI mapping screen
 */
sealed class MidiMappingEvent {
    data class MappingSaved(val mapping: MidiMapping) : MidiMappingEvent()
    data class MappingDeleted(val mappingId: String) : MidiMappingEvent()
    data class MappingActivated(val mappingId: String) : MidiMappingEvent()
    data class MidiLearnStarted(val targetType: MidiTargetType, val targetId: String) : MidiMappingEvent()
    data class MidiLearnCompleted(val mapping: MidiParameterMapping) : MidiMappingEvent()
    object MidiLearnCancelled : MidiMappingEvent()
    data class ConflictResolved(val conflict: MidiMappingConflict) : MidiMappingEvent()
}