package com.example.theone.features.sampler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngine
import com.example.theone.domain.ProjectManager
import com.example.theone.model.AudioInputSource
import com.example.theone.model.SampleMetadata
import com.example.theone.model.Sample // Import the Sample class
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import android.util.Log
import android.content.Context
import com.example.theone.audio.AudioEngineControl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class RecordingState {
    IDLE,       // Not recording, sampler not armed
    ARMED,      // Ready to record, waiting for input or start command
    RECORDING,  // Actively recording audio
    REVIEWING   // Recording finished, user can review/save/discard
}

@HiltViewModel
class SamplerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: AudioEngineControl, // Use interface
    private val projectManager: ProjectManager
) : ViewModel() {

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordedSamplesQueue = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val recordedSamplesQueue: StateFlow<List<SampleMetadata>> = _recordedSamplesQueue.asStateFlow()

    val samplePool: StateFlow<List<SampleMetadata>> = projectManager.samplePool

    // Optional: Add a state for save operation status if UI needs to react to it
    private val _saveSampleStatus = MutableStateFlow<String?>(null)
    val saveSampleStatus: StateFlow<String?> = _saveSampleStatus.asStateFlow()

    private val MAX_RECORDINGS = 3

    fun loadSample(uri: String) {
        viewModelScope.launch {
            println("SamplerViewModel: loadSample called for URI: $uri")
            _saveSampleStatus.value = "Loading sample..." // Indicate activity
            val result = projectManager.loadWavFile(uri)

            result.fold(
                onSuccess = { sample ->
                    println("SamplerViewModel: Successfully loaded WAV file. Sample ID: ${sample.id}, Name: ${sample.metadata.name}")
                    projectManager.addSampleToPool(sample.metadata)
                    println("SamplerViewModel: Added sample metadata to ProjectManager pool.")

                    val engineLoaded = audioEngine.loadSampleToMemory(sample.id, sample.metadata.uri)
                    if (engineLoaded) {
                        println("SamplerViewModel: Sample ${sample.id} successfully loaded into AudioEngine.")
                        _saveSampleStatus.value = "Sample '${sample.metadata.name}' loaded."
                    } else {
                        println("SamplerViewModel: Failed to load sample ${sample.id} into AudioEngine.")
                        _saveSampleStatus.value = "Error loading sample '${sample.metadata.name}' into audio engine."
                    }
                },
                onFailure = { error ->
                    println("SamplerViewModel: Failed to load WAV file. Error: ${error.message}")
                    _saveSampleStatus.value = "Error loading WAV file: ${error.message}"
                }
            )
        }
    }

    // --- TODO: Implement sample saving to storage ---
    // Original user request: saveSample(sample: Sample, file: File)
    fun saveSample(sample: Sample, uri: String) {
        viewModelScope.launch {
            println("SamplerViewModel: saveSample called for Sample ID: ${sample.id}, URI: $uri")
            _saveSampleStatus.value = "Saving sample '${sample.metadata.name}'..." // Indicate activity
            val result = projectManager.saveWavFile(sample, uri) // Suspend call

            result.fold(
                onSuccess = {
                    println("SamplerViewModel: Successfully saved sample ${sample.id} to URI: $uri")
                    // Optionally, if this sample's metadata isn't in the main pool yet, add it.
                    // Or, if it was a temporary sample, this step might make it permanent.
                    // For now, we assume the 'sample' object is complete and we're just writing it out.
                    // If save implies adding to pool, that logic would go here.
                    // e.g., projectManager.addSampleToPool(sample.metadata)
                    _saveSampleStatus.value = "Sample '${sample.metadata.name}' saved successfully."
                },
                onFailure = { error ->
                    println("SamplerViewModel: Failed to save sample ${sample.id} to URI: $uri. Error: ${error.message}")
                    _saveSampleStatus.value = "Error saving sample: ${error.message}"
                }
            )
        }
    }
    // --- End of saveSample ---

    // Call this to clear the status message after a while
    fun clearSaveSampleStatus() {
        _saveSampleStatus.value = null
    }

    fun armSampler() {
        _recordedSamplesQueue.value = emptyList()
        _recordingState.value = RecordingState.ARMED
    }

    fun startRecording(audioInputSource: AudioInputSource) { // audioInputSource currently not used for path
        if (_recordingState.value == RecordingState.ARMED) {
            _recordingState.value = RecordingState.RECORDING
            viewModelScope.launch {
                val tempFile: File?
                try {
                    tempFile = File.createTempFile("temp_sampler_audio_", ".wav", context.cacheDir)
                    val tempFilePath = tempFile.absolutePath
                    Log.d("SamplerViewModel", "Recording to temporary file: $tempFilePath")

                    // TODO: Define actual recording parameters
                    val sampleRate = 48000
                    val channels = 1 // Mono

                    val success = audioEngine.startAudioRecording(
                        context = context, // Pass application context
                        filePathUri = tempFilePath,
                        sampleRate = sampleRate,
                        channels = channels
                        // inputDeviceId can be null for default mic
                    )

                    if (success) {
                        Log.i("SamplerViewModel", "AudioEngine recording started successfully.")
                        // UI state already RECORDING. Actual stop and onRecordingFinished will be separate.
                    } else {
                        Log.e("SamplerViewModel", "Failed to start AudioEngine recording.")
                        _recordingState.value = RecordingState.ARMED // Revert state if failed to start
                    }

                } catch (e: IOException) {
                    Log.e("SamplerViewModel", "Error creating temporary file for recording", e)
                    _recordingState.value = RecordingState.ARMED // Revert state
                    return@launch
                }
            }
        }
    }

    fun stopRecordingAndFinalize() {
        if (_recordingState.value == RecordingState.RECORDING) {
            viewModelScope.launch {
                Log.i("SamplerViewModel", "Attempting to stop recording.")
                val recordedSampleMetadata = audioEngine.stopAudioRecording()
                if (recordedSampleMetadata != null) {
                    Log.i("SamplerViewModel", "Recording stopped. Metadata: $recordedSampleMetadata")
                    onRecordingFinished(recordedSampleMetadata) // Pass the actual metadata
                } else {
                    Log.e("SamplerViewModel", "Stopping recording failed or returned null metadata.")
                    _recordingState.value = RecordingState.ARMED // Revert or go to error state
                }
            }
        } else {
            Log.w("SamplerViewModel", "stopRecordingAndFinalize called when not in RECORDING state.")
        }
    }

    fun onRecordingFinished(newSample: SampleMetadata) { // This is now called by stopRecordingAndFinalize
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        if (currentQueue.size == MAX_RECORDINGS) {
            currentQueue.removeAt(0)
        }
        currentQueue.add(newSample)
        _recordedSamplesQueue.value = currentQueue
        _recordingState.value = RecordingState.ARMED
    }

    fun disarmOrFinishSession() {
        if (_recordingState.value == RecordingState.ARMED) {
            if (_recordedSamplesQueue.value.isNotEmpty()) {
                _recordingState.value = RecordingState.REVIEWING
            } else {
                _recordingState.value = RecordingState.IDLE
            }
        } else if (_recordingState.value == RecordingState.REVIEWING) {
            _recordingState.value = RecordingState.IDLE
            _recordedSamplesQueue.value = emptyList()
        }
    }

    fun auditionSample(sample: SampleMetadata) {
        println("SamplerViewModel: auditionSample for URI ${sample.uri}. (Actual playback logic might need review)")
    }

    // This is the existing save for recorded samples from the queue
    fun saveSample(sample: SampleMetadata, name: String) {
        val namedSample = sample.copy(name = name)
        projectManager.addSampleToPool(namedSample)
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        currentQueue.remove(sample)
        _recordedSamplesQueue.value = currentQueue
        if (_recordedSamplesQueue.value.isEmpty()) {
            _recordingState.value = RecordingState.IDLE
        }
        println("SamplerViewModel: Saved recorded sample '$name' to ProjectManager pool.")
        _saveSampleStatus.value = "Recorded sample '$name' added to pool."
    }

    fun discardSample(sample: SampleMetadata) {
        val currentQueue = _recordedSamplesQueue.value.toMutableList()
        currentQueue.remove(sample)
        _recordedSamplesQueue.value = currentQueue
        if (_recordedSamplesQueue.value.isEmpty()) {
            _recordingState.value = RecordingState.IDLE
        }
    }
}
