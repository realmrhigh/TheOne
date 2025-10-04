package com.high.theone.features.sampling

import com.high.theone.model.SamplingUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that provides pad state data to other components.
 * Acts as a bridge between SamplingViewModel and components that need pad state.
 */
@Singleton
class PadStateProvider @Inject constructor() {

    private val _padState = MutableStateFlow(SamplingUiState())
    val padState: StateFlow<SamplingUiState> = _padState.asStateFlow()

    /**
     * Update the pad state. Called by SamplingViewModel when its state changes.
     */
    fun updatePadState(newState: SamplingUiState) {
        _padState.value = newState
    }
}