package com.example.theone.features.sampler

import com.example.theone.audio.AudioEngineControl
import com.example.theone.domain.ProjectManager
import com.example.theone.model.SampleMetadata
import android.util.Log // For logging pad assignment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Placeholder for AudioEngineControl interface (C1)
// Placeholder for ProjectManager interface (C3)
// Local Placeholder for SampleMetadata.
// Ideally, this would be in a shared 'core.model' module as per README.
// Ensure this definition is present if not already.

enum class RecordingState {
    IDLE,
    ARMED,
    RECORDING,
    REVIEWING
}

class SamplerViewModel(
    private val audioEngine: AudioEngineControl,
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _inputLevel = MutableStateFlow(0.0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _isThresholdRecordingEnabled = MutableStateFlow(false)
    val isThresholdRecordingEnabled: StateFlow<Boolean> = _isThresholdRecordingEnabled.asStateFlow()

    private val _thresholdValue = MutableStateFlow(0.1f) // Example threshold
    val thresholdValue: StateFlow<Float> = _thresholdValue.asStateFlow()

    private var lastRecordedSampleMetadata: SampleMetadata? = null

    // For UI messages like errors or success
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumedUserMessage() {
        _userMessage.value = null
    }

    init {
        viewModelScope.launch {
            while (true) {
                if (_recordingState.value == RecordingState.ARMED || _recordingState.value == RecordingState.RECORDING) {
                    // _inputLevel.value = audioEngine.getRecordingLevelPeak() // Uncomment when audioEngine is real
                }
                // Simulate input level for preview if needed, or remove if only real values are desired
                if(audioEngine.isRecordingActive()){ // Basic simulation
                     _inputLevel.value = (Math.random() * 0.7f).toFloat() + 0.1f
                } else if (_recordingState.value != RecordingState.RECORDING) {
                     _inputLevel.value = 0.0f
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun toggleThresholdRecording(enabled: Boolean) {
        _isThresholdRecordingEnabled.value = enabled
        if (enabled && _recordingState.value == RecordingState.IDLE) {
            // Optional: Automatically arm if idle and threshold is enabled
            // _recordingState.value = RecordingState.ARMED
        } else if (!enabled && _recordingState.value == RecordingState.ARMED) {
            // If disabling threshold while armed, go back to idle
            _recordingState.value = RecordingState.IDLE
        }
    }

    fun setThresholdValue(value: Float) {
        _thresholdValue.value = value.coerceIn(0.0f, 1.0f)
    }

    fun startRecordingPressed() {
        if (_recordingState.value == RecordingState.IDLE || _recordingState.value == RecordingState.REVIEWING) {
            lastRecordedSampleMetadata = null // Clear previous recording
            if (_isThresholdRecordingEnabled.value) {
                _recordingState.value = RecordingState.ARMED
                _userMessage.value = "Armed for threshold recording. Make some noise!"
                // TODO: Implement actual threshold detection logic.
                // This would involve periodically checking audioEngine.getRecordingLevelPeak()
                // and calling initiateRecording() when threshold is met.
                // For now, user might need to press record again or we can simulate.
                Log.d("SamplerVM", "Armed for threshold recording. Waiting for input > ${_thresholdValue.value}")

            } else {
                initiateRecording()
            }
        } else if (_recordingState.value == RecordingState.ARMED && _isThresholdRecordingEnabled.value) {
            // If already armed by threshold, pressing record again can start immediately (manual override)
            initiateRecording()
        }
    }

    private fun initiateRecording() {
        viewModelScope.launch {
            // TODO: Use a proper file naming/management strategy from C3 or app's cache directory
            val tempRecordingFileName = "sampler_temp_${System.currentTimeMillis()}.wav"
            Log.d("SamplerVM", "Attempting to start recording to: $tempRecordingFileName")
            val success = audioEngine.startAudioRecording(tempRecordingFileName, null) // null for default input device
            if (success) {
                _recordingState.value = RecordingState.RECORDING
                _userMessage.value = "Recording..."
                Log.d("SamplerVM", "Recording started successfully.")
            } else {
                _recordingState.value = RecordingState.IDLE
                _userMessage.value = "Failed to start recording."
                Log.e("SamplerVM", "AudioEngine failed to start recording.")
            }
        }
    }

    fun stopRecordingPressed() {
        if (_recordingState.value == RecordingState.RECORDING || _recordingState.value == RecordingState.ARMED) {
            viewModelScope.launch {
                Log.d("SamplerVM", "Attempting to stop recording.")
                val recordedMetadata = audioEngine.stopAudioRecording()
                if (recordedMetadata != null) {
                    lastRecordedSampleMetadata = recordedMetadata
                    _recordingState.value = RecordingState.REVIEWING
                    _userMessage.value = "Recording stopped. Review your sample."
                    Log.d("SamplerVM", "Recording stopped. Metadata: $recordedMetadata")
                } else {
                    // If ARMED and stop is pressed before recording started, or if stopAudioRecording returns null
                    _recordingState.value = RecordingState.IDLE
                    _userMessage.value = "Recording stopped or no audio data."
                    Log.d("SamplerVM", "Recording stopped, but no metadata received.")
                }
            }
        }
    }

    fun playbackLastRecordingPressed() {
        if (_recordingState.value == RecordingState.REVIEWING && lastRecordedSampleMetadata != null) {
            viewModelScope.launch {
                // TODO: Implement actual playback logic using audioEngine.
                // This requires the AudioEngineControl to have a method like:
                // suspend fun playSample(sampleId: String, filePathUri: String, /* other relevant params */)
                // For now, we'll just log it.
                Log.d("SamplerVM", "Playback of ${lastRecordedSampleMetadata!!.name} requested.")
                _userMessage.value = "Playback functionality is not yet implemented."
                // Example call if available:
                // audioEngine.playSample(lastRecordedSampleMetadata!!.id /*, other params */)
            }
        }
    }

    fun saveRecording(sampleName: String, assignToPadId: String?) {
        if (_recordingState.value == RecordingState.REVIEWING && lastRecordedSampleMetadata != null) {
            val metadataToSave = lastRecordedSampleMetadata!!.copy(name = sampleName)
            Log.d("SamplerVM", "Attempting to save recording: $metadataToSave")
            viewModelScope.launch {
                // Assuming the filePathUri in metadataToSave is the path to the temp recorded file
                val savedMetadata = projectManager.addSampleToPool(
                    name = metadataToSave.name,
                    sourceFileUri = metadataToSave.filePathUri, // This URI should point to the actual temp audio file
                    copyToProjectDir = true // As per README M1.1, copy to project
                )

                if (savedMetadata != null) {
                    _userMessage.value = "Sample '${savedMetadata.name}' saved successfully!"
                    Log.d("SamplerVM", "Sample saved: $savedMetadata")
                    if (assignToPadId != null) {
                        // TODO: Implement assignment to pad logic.
                        // This might involve calling a method on a DrumPadViewModel or a shared service
                        // that manages pad assignments within the current project/track.
                        Log.d("SamplerVM", "Assigning ${savedMetadata.name} to $assignToPadId (Placeholder)")
                        _userMessage.value = "Sample '${savedMetadata.name}' saved and assigned to $assignToPadId (Placeholder)."
                    }
                    _recordingState.value = RecordingState.IDLE
                    lastRecordedSampleMetadata = null // Clear after saving
                } else {
                    _userMessage.value = "Failed to save sample."
                    Log.e("SamplerVM", "ProjectManager failed to add sample to pool.")
                    // Keep state as REVIEWING so user can try again or discard
                }
            }
        } else {
            Log.w("SamplerVM", "Save recording called in invalid state or with no metadata.")
        }
    }

    fun discardRecording() {
        if (_recordingState.value == RecordingState.REVIEWING) {
            Log.d("SamplerVM", "Discarding recording: ${lastRecordedSampleMetadata?.name}")
            // TODO: Optionally, delete the temporary recording file from disk
            // This would require knowing the filePathUri and using file system operations.
            // For now, just clear the metadata.
            lastRecordedSampleMetadata = null
            _recordingState.value = RecordingState.IDLE
            _userMessage.value = "Recording discarded."
        }
    }
    // NOTE: The SampleMetadata class definition was moved up to be with the interfaces
    // to match the provided code structure. No change needed here if it's already moved.
}
