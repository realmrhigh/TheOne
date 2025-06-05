package com.example.theone.features.sampleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngineControl // Assuming this is the correct C1 interface
import com.example.theone.domain.ProjectManager     // Corrected C3 interface
import com.example.theone.model.LoopMode
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID // For unique note instance IDs during audition

class SampleEditViewModel(
    private val audioEngine: AudioEngineControl, // C1
    private val projectManager: ProjectManager,   // C3
    initialSampleMetadata: SampleMetadata
) : ViewModel() {

    private val _currentSample = MutableStateFlow(ensureValid(initialSampleMetadata.copy()))
    val currentSample: StateFlow<SampleMetadata> = _currentSample.asStateFlow()

    // Placeholder for waveform data - to be developed if C4.WaveformDisplay is implemented/found
    private val _waveformVisualData = MutableStateFlow<List<Float>>(emptyList())
    val waveformVisualData: StateFlow<List<Float>> = _waveformVisualData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    companion object {
        fun ensureValid(sample: SampleMetadata): SampleMetadata {
            var newTrimStartMs = sample.trimStartMs
            var newTrimEndMs = sample.trimEndMs

            if (newTrimEndMs == 0L && sample.durationMs > 0L) {
                newTrimEndMs = sample.durationMs
            }
            newTrimStartMs = newTrimStartMs.coerceAtLeast(0L)
            newTrimEndMs = newTrimEndMs.coerceAtMost(sample.durationMs)
            if (newTrimStartMs > newTrimEndMs) {
                newTrimStartMs = newTrimEndMs
            }

            var newLoopStartMs = sample.loopStartMs
            var newLoopEndMs = sample.loopEndMs

            if (sample.loopMode != LoopMode.NONE && newLoopStartMs != null && newLoopEndMs != null) {
                newLoopStartMs = newLoopStartMs.coerceIn(newTrimStartMs, newTrimEndMs)
                newLoopEndMs = newLoopEndMs.coerceIn(newLoopStartMs, newTrimEndMs)
                if (newLoopStartMs == newLoopEndMs) { // Zero length loop is not useful
                    newLoopStartMs = null
                    newLoopEndMs = null
                }
            } else if (sample.loopMode == LoopMode.NONE) {
                 newLoopStartMs = null
                 newLoopEndMs = null
            }


            return sample.copy(
                trimStartMs = newTrimStartMs,
                trimEndMs = newTrimEndMs,
                loopStartMs = newLoopStartMs,
                loopEndMs = newLoopEndMs
            )
        }
    }

    init {
         // The currentSample is already validated by the ensureValid function during initialization.
         // If waveform loading is implemented, it could be triggered here.
         // e.g., loadWaveformDisplayData(initialSampleMetadata.filePathUri)
    }

    fun updateTrimPoints(startMs: Long, endMs: Long) {
        val current = _currentSample.value
        var newStart = startMs.coerceIn(0L, current.durationMs)
        var newEnd = endMs.coerceIn(0L, current.durationMs)

        if (newStart > newEnd) { // Ensure start is not after end
           newStart = newEnd
        }

        var newLoopStart = current.loopStartMs
        var newLoopEnd = current.loopEndMs

        // Invalidate loop points if they fall outside the new trim region or become zero-length
        if (current.loopMode != LoopMode.NONE && newLoopStart != null && newLoopEnd != null) {
            if (newLoopStart < newStart || newLoopEnd > newEnd || newLoopStart >= newLoopEnd) {
                newLoopStart = null // Invalidate, will be reset if loop mode is active
                newLoopEnd = null
            }
        }

        _currentSample.value = ensureValid(current.copy(
            trimStartMs = newStart,
            trimEndMs = newEnd,
            loopStartMs = newLoopStart, // Preserve valid loop points or let setLoopMode re-initialize
            loopEndMs = newLoopEnd
        ))
    }

    fun updateLoopPoints(loopStartMsInput: Long?, loopEndMsInput: Long?) {
        val current = _currentSample.value
        if (current.loopMode == LoopMode.NONE) {
            // Do not update loop points if loop mode is NONE
             _currentSample.value = current.copy(loopStartMs = null, loopEndMs = null)
            return
        }

        var newLoopStart = loopStartMsInput
        var newLoopEnd = loopEndMsInput

        if (newLoopStart != null && newLoopEnd != null) {
            // Ensure loop points are within the current trim region
            newLoopStart = newLoopStart.coerceIn(current.trimStartMs, current.trimEndMs)
            newLoopEnd = newLoopEnd.coerceIn(current.trimStartMs, current.trimEndMs)

            if (newLoopStart >= newLoopEnd) { // Loop start must be before loop end
                 newLoopStart = null // Invalidate if not logical
                 newLoopEnd = null
            }
        } else { // If one is null, invalidate both
            newLoopStart = null
            newLoopEnd = null
        }
        _currentSample.value = ensureValid(current.copy(loopStartMs = newLoopStart, loopEndMs = newLoopEnd))
    }

    fun setLoopMode(loopMode: LoopMode) {
        val currentVal = _currentSample.value
        if (loopMode == LoopMode.NONE) {
            _currentSample.value = ensureValid(currentVal.copy(
                loopMode = loopMode,
                loopStartMs = null,
                loopEndMs = null
            ))
        } else { // For FORWARD or other active loop modes
            // If loop points are not set or invalid, default them to the current trim region
            if (currentVal.loopStartMs == null || currentVal.loopEndMs == null || currentVal.loopStartMs >= currentVal.loopEndMs ||
                currentVal.loopStartMs < currentVal.trimStartMs || currentVal.loopEndMs > currentVal.trimEndMs) {
                 _currentSample.value = ensureValid(currentVal.copy(
                    loopMode = loopMode,
                    loopStartMs = currentVal.trimStartMs,
                    loopEndMs = currentVal.trimEndMs
                ))
            } else {
                // Loop points are valid, just update the mode
                 _currentSample.value = ensureValid(currentVal.copy(loopMode = loopMode))
            }
        }
    }

    fun auditionSelection() {
        viewModelScope.launch {
            val sampleToPlay = _currentSample.value
            val noteInstanceId = "audition_slice_" + UUID.randomUUID().toString()

            if (sampleToPlay.sampleRate <= 0) {
                _userMessage.value = "Cannot audition: Invalid sample rate (${sampleToPlay.sampleRate}) for sample '${sampleToPlay.name}'."
                return@launch
            }

            _userMessage.value = "Auditioning selection for '${sampleToPlay.name}'..."

            val success = audioEngine.playSampleSlice(
                sampleId = sampleToPlay.id,
                noteInstanceId = noteInstanceId,
                volume = 1.0f, // Full volume for audition
                pan = 0.0f,    // Centered pan for audition
                sampleRate = sampleToPlay.sampleRate,
                trimStartMs = sampleToPlay.trimStartMs,
                trimEndMs = sampleToPlay.trimEndMs,
                loopStartMs = sampleToPlay.loopStartMs,
                loopEndMs = sampleToPlay.loopEndMs,
                isLooping = sampleToPlay.loopMode == LoopMode.FORWARD && sampleToPlay.loopStartMs != null && sampleToPlay.loopEndMs != null
            )

            if (success) {
                // The message in AudioEngine.kt already clarifies if fallback occurred.
                // So, we can be more general here, or rely on logs from AudioEngine.
                _userMessage.value = "Auditioning '${sampleToPlay.name}'. (Native slice/loop support pending if not working as expected)"
            } else {
                _userMessage.value = "Failed to audition sample '${sampleToPlay.name}' using slice playback."
            }
        }
    }

    fun saveChanges() {
        _isLoading.value = true
        viewModelScope.launch {
            var sampleToSave = _currentSample.value
            // Ensure loop points are null if loop mode is NONE
            if (sampleToSave.loopMode == LoopMode.NONE) {
                sampleToSave = sampleToSave.copy(loopStartMs = null, loopEndMs = null)
            } else {
                 // If loop mode is active but points are invalid, set them to full trim region
                if (sampleToSave.loopStartMs == null || sampleToSave.loopEndMs == null || sampleToSave.loopStartMs >= sampleToSave.loopEndMs) {
                     sampleToSave = sampleToSave.copy(loopStartMs = sampleToSave.trimStartMs, loopEndMs = sampleToSave.trimEndMs)
                }
            }

            sampleToSave = ensureValid(sampleToSave) // Final validation before saving

            val success = projectManager.updateSampleMetadata(sampleToSave)
            if (success) {
                _currentSample.value = sampleToSave // Ensure UI reflects the saved state
                _userMessage.value = "Sample '${sampleToSave.name}' updated."
            } else {
                _userMessage.value = "Failed to update sample '${sampleToSave.name}'."
            }
            _isLoading.value = false
        }
    }

    fun consumedUserMessage() {
        _userMessage.value = null
    }

    // Placeholder for loading waveform data - to be developed if C4.WaveformDisplay is used
    private fun loadWaveformDisplayData(filePathUri: String) {
        viewModelScope.launch {
            // This would ideally call a method in C1 or a utility to extract waveform data
            // For example: val data = audioEngine.extractWaveform(filePathUri, detailLevel)
            // _waveformVisualData.value = data
            _userMessage.value = "Waveform loading not yet implemented."
        }
    }
}
