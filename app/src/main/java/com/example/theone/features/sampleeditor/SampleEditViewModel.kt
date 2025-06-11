package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.model.LoopMode
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SampleEditViewModel(
    initialSampleMetadata: SampleMetadata
) : ViewModel() {

    private val _currentSample = MutableStateFlow(initialSampleMetadata)
    val currentSample: StateFlow<SampleMetadata> = _currentSample.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateTrimPoints(start: Float, end: Float) {
        _currentSample.value = _currentSample.value.copy(trimStartMs = start, trimEndMs = end)
    }

    fun setLoopMode(loopMode: LoopMode) {
        _currentSample.value = _currentSample.value.copy(loopMode = loopMode)
    }

    fun updateLoopPoints(start: Float, end: Float) {
        _currentSample.value = _currentSample.value.copy(loopStartMs = start, loopEndMs = end)
    }

    fun auditionSelection(from: Float, to: Float) {
        // TODO: Implement audio engine call
        viewModelScope.launch {
            _userMessage.value = "Auditioning from ${from}ms to ${to}ms"
        }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: Implement save logic
            _isLoading.value = false
            _userMessage.value = "Changes saved!"
        }
    }
}
