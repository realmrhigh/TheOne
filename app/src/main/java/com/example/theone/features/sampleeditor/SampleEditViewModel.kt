package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.Sample // Import full Sample
import com.example.theone.model.SampleMetadata
import com.example.theone.model.trim // Import the trim extension function
import com.example.theone.model.normalize // Import the normalize extension function
import com.example.theone.model.reverse // Import the reverse extension function
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SampleEditViewModel(
    private val initialSampleUri: String, // Changed from SampleMetadata
    private val audioEngine: AudioEngine,
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _currentSample = MutableStateFlow<Sample?>(null)
    val currentSample: StateFlow<Sample?> = _currentSample.asStateFlow()

    // isLoading can be used to show a spinner while the initial sample is loading
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // User messages for feedback
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Expose metadata as a derived StateFlow for convenience if needed by UI,
    // but primary observation should be on currentSample itself.
    val currentSampleMetadata: StateFlow<SampleMetadata?> =
        _currentSample.map { it?.metadata }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _userMessage.value = "Loading sample..."
            val result = projectManager.loadWavFile(initialSampleUri)
            result.fold(
                onSuccess = { sample ->
                    // Ensure trimEndMs is correctly set if it was 0 from metadata
                    val metadata = sample.metadata
                    val correctedMetadata = if (metadata.trimEndMs == 0L && metadata.duration > 0L) {
                        metadata.copy(trimEndMs = metadata.duration)
                    } else {
                        metadata
                    }
                    _currentSample.value = sample.copy(metadata = correctedMetadata)
                    _userMessage.value = "Sample '${sample.metadata.name}' loaded."
                },
                onFailure = { error ->
                    _currentSample.value = null
                    _userMessage.value = "Error loading sample: ${error.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun updateTrimPoints(startMs: Long, endMs: Long) {
        val sample = _currentSample.value ?: return
        val metadata = sample.metadata

        // Ensure startMs is not negative and less than or equal to duration
        val validStartMs = startMs.coerceIn(0, metadata.duration)
        // Ensure endMs is not less than startMs and not greater than duration
        val validEndMs = endMs.coerceIn(validStartMs, metadata.duration)

        if (validStartMs == metadata.trimStartMs && validEndMs == metadata.trimEndMs) {
            return // No change
        }

        val updatedMetadata = metadata.copy(trimStartMs = validStartMs, trimEndMs = validEndMs)
        _currentSample.value = sample.copy(metadata = updatedMetadata)
        // UI will observe currentSample.value.metadata.trimStartMs and trimEndMs
    }

    fun auditionSlice() {
        val sample = _currentSample.value ?: return
        val metadata = sample.metadata
        viewModelScope.launch {
            audioEngine.playSampleSlice(
                sampleId = sample.id,
                noteInstanceId = "audition_${sample.id}",
                volume = 1.0f,
                pan = 0.0f,
                trimStartMs = metadata.trimStartMs,
                trimEndMs = metadata.trimEndMs,
                loopStartMs = null,
                loopEndMs = null,
                isLooping = false
            )
            _userMessage.value = "Auditioning slice..."
        }
    }

    fun saveChanges() {
        val sample = _currentSample.value ?: return
        viewModelScope.launch {
            _userMessage.value = "Saving changes..."
            val success = projectManager.updateSampleMetadata(sample.metadata)
            if (success) {
                _userMessage.value = "Metadata changes saved."
                // Optionally, if ProjectManager's pool needs to reflect this exact Sample instance
                // (not just metadata by URI lookup), you might need another method in ProjectManager.
                // For now, assuming updateSampleMetadata is sufficient for persisting.
            } else {
                _userMessage.value = "Failed to save metadata changes."
            }
        }
    }

    fun consumedUserMessage() {
        _userMessage.value = null
    }

    fun trimCurrentSample(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val originalSample = _currentSample.value
            if (originalSample == null) {
                _userMessage.value = "No sample loaded to trim."
                return@launch
            }

            _isLoading.value = true
            _userMessage.value = "Trimming sample..."

            // Perform the trim operation (non-destructive)
            val trimmedSample = originalSample.trim(startMs, endMs) // Uses the extension function

            // Add the new trimmed sample's metadata to ProjectManager's pool
            projectManager.addSampleToPool(trimmedSample.metadata)

            // Update the ViewModel's current sample to the new trimmed version
            _currentSample.value = trimmedSample
            _isLoading.value = false
            _userMessage.value = "Sample trimmed. New duration: ${trimmedSample.metadata.duration} ms."
        }
    }

    fun normalizeCurrentSample(targetPeakDb: Float = 0.0f) {
        viewModelScope.launch {
            val originalSample = _currentSample.value
            if (originalSample == null) {
                _userMessage.value = "No sample loaded to normalize."
                return@launch
            }

            _isLoading.value = true
            _userMessage.value = "Normalizing sample..."

            val normalizedSample = originalSample.normalize(targetPeakDb)

            projectManager.addSampleToPool(normalizedSample.metadata)

            _currentSample.value = normalizedSample
            _isLoading.value = false
            _userMessage.value = "Sample normalized to ${targetPeakDb}dB peak."
        }
    }

    fun reverseCurrentSample() {
        viewModelScope.launch {
            val originalSample = _currentSample.value
            if (originalSample == null) {
                _userMessage.value = "No sample loaded to reverse."
                return@launch
            }

            _isLoading.value = true
            _userMessage.value = "Reversing sample..."

            val reversedSample = originalSample.reverse() // Uses the extension function

            projectManager.addSampleToPool(reversedSample.metadata)

            _currentSample.value = reversedSample
            _isLoading.value = false
            _userMessage.value = "Sample reversed."
        }
    }

    // TODO("Implement waveform rendering") - UI responsibility using currentSample.value?.audioData
    // TODO("Implement sample trimming") - This is the main implementation.
    // TODO("Implement sample normalization") - This is the main implementation.
    // TODO("Implement sample reversal") - This is the main implementation.
}
