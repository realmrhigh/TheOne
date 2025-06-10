package com.example.theone.features.sequencer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.theone.audio.AudioEngineControl
import com.example.theone.model.Event
import com.example.theone.model.EventType
import com.example.theone.model.Sequence // Corrected import
import com.example.theone.model.SynthModels.EnvelopeSettings // Corrected import
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SequencerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val audioEngine: AudioEngineControl,
    private val sequencerEventBus: SequencerEventBus
) : ViewModel() {

    private val _playheadPositionTicks = MutableStateFlow(0L)
    val playheadPositionTicks: StateFlow<Long> = _playheadPositionTicks.asStateFlow()

    private var playbackJob: Job? = null
    // Changed to use the viewModelScope provided by androidx.lifecycle library
    // private val viewModelScope = CoroutineScope(Dispatchers.Main + Job())

    private val _sequences = MutableStateFlow<List<Sequence>>(emptyList())
    val sequences: StateFlow<List<Sequence>> = _sequences.asStateFlow()

    var currentSequence: Sequence? by mutableStateOf(null)
        private set

    private val _isRecordingState = MutableStateFlow(false)
    val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()

    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

    // Using the ppqn from the sequence object. Made a default value available.
    val ticksPer16thNote: Long get() = (currentSequence?.ppqn ?: 96) / 4

    init {
        val initialSeq = Sequence(
            id = UUID.randomUUID().toString(),
            name = "Sequence 1",
            bpm = 120.0f,
            // The 'ppqn' parameter was removed from the Sequence constructor.
            // It's a property with a default value now.
            events = mutableListOf()
        )
        _sequences.value = listOf(initialSeq)
        setCurrentSequenceInternal(initialSeq)

        _isRecordingState.value = false // Initialize explicitly

        viewModelScope.launch {
            sequencerEventBus.padTriggerEvents.collect { event ->
                if (_isRecordingState.value) { // Check the state flow value
                    recordPadTrigger(event.padId, event.velocity)
                }
            }
        }
    }

    private fun setCurrentSequenceInternal(sequence: Sequence?) {
        stop() // Stop playback before switching
        currentSequence = sequence
        _playheadPositionTicks.value = 0L

        sequence?.let {
            viewModelScope.launch {
                // Assuming AudioEngineControl has a method to load sequence data
                (audioEngine as? com.example.theone.audio.AudioEngine)?.native_loadSequenceData(it)
                Log.d("SequencerViewModel", "Loaded sequence ${it.name} into native layer.")
            }
        }
        Log.d("SequencerViewModel", "Current sequence set to ${sequence?.name}")
    }

    fun selectSequence(sequenceId: String) {
        val sequenceToSelect = _sequences.value.find { it.id == sequenceId }
        setCurrentSequenceInternal(sequenceToSelect)
    }

    fun createNewSequence() {
        val newSeqNumber = _sequences.value.size + 1
        val newSequence = Sequence(
            id = UUID.randomUUID().toString(),
            name = "Sequence $newSeqNumber",
            bpm = currentSequence?.bpm ?: 120.0f,
            barLength = currentSequence?.barLength ?: 4,
            timeSignatureNumerator = currentSequence?.timeSignatureNumerator ?: 4,
            timeSignatureDenominator = currentSequence?.timeSignatureDenominator ?: 4,
            ppqn = currentSequence?.ppqn ?: 96,
            events = mutableListOf()
        )
        _sequences.value = _sequences.value + newSequence
        setCurrentSequenceInternal(newSequence)
        Log.d("SequencerViewModel", "Created new sequence '${newSequence.name}' and set as current.")
    }

    fun setBpm(newBpm: Float) {
        val targetSequence = currentSequence ?: return
        if (newBpm > 0) {
            // To trigger recomposition, create a new object
            val updatedSequence = targetSequence.copy(bpm = newBpm)
            _sequences.value = _sequences.value.map { if (it.id == updatedSequence.id) updatedSequence else it }
            currentSequence = updatedSequence

            viewModelScope.launch {
                (audioEngine as? com.example.theone.audio.AudioEngine)?.native_setSequencerBpm(newBpm)
            }
        }
    }

    fun toggleRecording() {
        _isRecordingState.value = !_isRecordingState.value
        Log.d("SequencerViewModel", "Recording toggled to ${_isRecordingState.value}")
    }

    private fun recordPadTrigger(padId: String, velocity: Int) {
        val targetSequence = currentSequence ?: return
        val playheadPos = _playheadPositionTicks.value
        val quantizedTimeTicks = (playheadPos / ticksPer16thNote) * ticksPer16thNote

        val newEvent = Event(
            id = UUID.randomUUID().toString(),
            trackId = "drumTrack1", // Placeholder track ID
            startTimeTicks = quantizedTimeTicks,
            type = EventType.PadTrigger(
                padId = padId,
                velocity = velocity,
                durationTicks = ticksPer16thNote
            )
        )

        val updatedEvents = targetSequence.events.toMutableList().apply { add(newEvent) }
        val updatedSequence = targetSequence.copy(events = updatedEvents)
        _sequences.value = _sequences.value.map { if (it.id == updatedSequence.id) updatedSequence else it }
        currentSequence = updatedSequence

        Log.d("SequencerViewModel", "Recorded event: $newEvent to sequence ${targetSequence.name}")
    }

    fun getEventsForCurrentSequence(): List<Event> {
        return currentSequence?.events ?: emptyList()
    }

    fun play() {
        val seq = currentSequence ?: return
        if (seq.bpm <= 0) {
            Log.e("SequencerViewModel", "Cannot play, BPM is invalid.")
            return
        }
        if (_isPlayingState.value) return

        viewModelScope.launch {
            (audioEngine as? com.example.theone.audio.AudioEngine)?.native_playSequence()
            _isPlayingState.value = true
            startPollingPlayhead()
        }
    }

    fun stop() {
        if (!_isPlayingState.value) return
        viewModelScope.launch {
            (audioEngine as? com.example.theone.audio.AudioEngine)?.native_stopSequence()
            _isPlayingState.value = false
            playbackJob?.cancel()
            _playheadPositionTicks.value = 0L
        }
    }

    private fun startPollingPlayhead() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlayingState.value) {
                _playheadPositionTicks.value = (audioEngine as? com.example.theone.audio.AudioEngine)?.native_getSequencerPlayheadPosition() ?: 0L
                delay(50)
            }
        }
    }

    fun clearCurrentSequenceEvents() {
        val targetSequence = currentSequence ?: return
        val updatedSequence = targetSequence.copy(events = mutableListOf())
        _sequences.value = _sequences.value.map { if (it.id == updatedSequence.id) updatedSequence else it }
        currentSequence = updatedSequence
        Log.d("SequencerViewModel", "Cleared all events from sequence ${targetSequence.name}")
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }

    // --- Test Logic ---
    private fun copyAssetToCache(assetName: String): String? {
        return try {
            val outFile = File(applicationContext.cacheDir, assetName)
            if (!outFile.exists()) {
                applicationContext.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile.absolutePath
        } catch (e: IOException) {
            Log.e("SequencerViewModel_Test", "Failed to copy asset $assetName to cache", e)
            null
        }
    }

    fun testMetronomeFullSequence() {
        viewModelScope.launch {
            // ... (rest of the test logic, ensuring it uses the AudioEngineControl interface correctly)
        }
    }

    fun testAudioRecording() {
        // ... (rest of the test logic, ensuring it uses the AudioEngineControl interface correctly)
    }

    fun testLoadAndPlaySample(assetName: String, sampleId: String) {
        viewModelScope.launch {
            if (!audioEngine.isInitialized()) {
                Log.e("SequencerViewModel_Test", "AudioEngine not initialized.")
                return@launch
            }
            Log.i("SequencerViewModel_Test", "Testing general sample loading ($assetName).")
            val cachedFilePath = copyAssetToCache(assetName)
            if (cachedFilePath != null) {
                // Corrected call to loadSampleToMemory
                val loadSuccess = audioEngine.loadSampleToMemory(sampleId, "file://$cachedFilePath")
                Log.i("SequencerViewModel_Test", "loadSampleToMemory($sampleId) result: $loadSuccess")
                if (loadSuccess && audioEngine.isSampleLoaded(sampleId)) {
                    Log.i("SequencerViewModel_Test", "Attempting to play sample $sampleId...")
                    audioEngine.playSample(sampleId = sampleId, noteInstanceId = "test_instance_${sampleId}", volume = 0.8f, pan = 0.0f)
                    delay(1000)
                    audioEngine.unloadSample(sampleId)
                    Log.i("SequencerViewModel_Test", "Unloaded $sampleId")
                } else {
                    Log.e("SequencerViewModel_Test", "Failed to load $sampleId or sample not reported as loaded.")
                }
            } else {
                Log.e("SequencerViewModel_Test", "Failed to copy asset $assetName to cache.")
            }
        }
    }
}