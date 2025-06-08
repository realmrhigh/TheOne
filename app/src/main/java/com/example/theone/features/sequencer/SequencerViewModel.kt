package com.example.theone.features.sequencer

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.theone.model.Event
import com.example.theone.model.EventType
import com.example.theone.model.PlaybackMode // Added import
import com.example.theone.model.Sequence // This was the problematic import before
import com.example.theone.model.SynthModels.EnvelopeSettings // Added import
import com.example.theone.model.SynthModels.EnvelopeType // Added import
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
// import com.example.theone.model.Project // Not using Project directly for now
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import com.example.theone.features.sequencer.SequencerEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager


@HiltViewModel
class SequencerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context, // Injected context
    private val audioEngine: com.example.theone.audio.AudioEngineControl, // Changed to interface
    private val sequencerEventBus: SequencerEventBus
) : ViewModel() {

    private val _playheadPositionTicks = MutableStateFlow(0L)
    val playheadPositionTicks: StateFlow<Long> = _playheadPositionTicks.asStateFlow()

    private var playbackJob: Job? = null // Will be used for polling playhead position
    private val viewModelScope = CoroutineScope(Dispatchers.Main + Job()) // Use Dispatchers.Main for UI updates

    // List to hold all sequences for the current session/project (simplified)
    private val _sequences = MutableStateFlow<List<Sequence>>(emptyList())
    val sequences: StateFlow<List<Sequence>> = _sequences.asStateFlow()

    // currentSequence is now a Compose State object
    var currentSequence: Sequence? by mutableStateOf(null)
        private set


    // Placeholder for recording state
    private var isRecording: Boolean = false // Original recording state
    // Initialize _isRecordingState in init block after isRecording is set by constructor or default
    private val _isRecordingState = MutableStateFlow(false) // Default, will be updated in init
    val isRecordingState: StateFlow<Boolean> = _isRecordingState.asStateFlow()


    // Optionally, expose isPlaying state for the Play button to change (e.g., to a Pause button)
    private val _isPlayingState = MutableStateFlow(false)
    val isPlayingState: StateFlow<Boolean> = _isPlayingState.asStateFlow()

    // Placeholder for quantization settings (e.g., 16th notes)
    // 96 PPQN (Pulses Per Quarter Note) is common. If 16th notes, then 96/4 = 24 ticks.
    val ticksPer16thNote: Long = 24 // Assuming 96 PPQN, made public

    init {
        // Create an initial default sequence
        val initialSeq = Sequence(
            id = UUID.randomUUID().toString(),
            name = "Sequence 1",
            bpm = 120.0f,
            events = mutableListOf()
            // barLength, timeSignatureNumerator, timeSignatureDenominator use defaults
        )
        _sequences.value = listOf(initialSeq)
        setCurrentSequenceInternal(initialSeq) // Set it as current

        // Initialize recording state flow based on the initial isRecording value
        _isRecordingState.value = isRecording // isRecording is a var Boolean

        // Collect pad trigger events from the event bus
        viewModelScope.launch {
            sequencerEventBus.padTriggerEvents.collect { event ->
                recordPadTrigger(event.padId, event.velocity)
            }
        }
    }

    // Method to internally set the current sequence and update related states
    private fun setCurrentSequenceInternal(sequence: Sequence?) {
        currentSequence = sequence
        stop() // Stop any current playback and reset playhead for the old sequence
        _playheadPositionTicks.value = 0L

        sequence?.let {
            viewModelScope.launch {
                audioEngine.loadSequenceData(it)
                Log.d("SequencerViewModel", "Loaded sequence ${it.name} into native layer.")
            }
        }
        Log.d("SequencerViewModel", "Current sequence set to ${sequence?.name}")
    }

    // Public method for sequence selection (will be used by dropdown later)
    fun selectSequence(sequenceId: String) {
        val sequenceToSelect = _sequences.value.find { it.id == sequenceId }
        sequenceToSelect?.let {
            setCurrentSequenceInternal(it)
            // audioEngine.loadSequenceData should be called by setCurrentSequenceInternal
        }
    }

    fun createNewSequence() {
        val newSeqNumber = _sequences.value.size + 1
        val newSequence = Sequence(
            id = UUID.randomUUID().toString(),
            name = "Sequence $newSeqNumber",
            bpm = currentSequence?.bpm ?: 120.0f, // Default to current BPM or 120
            barLength = currentSequence?.barLength ?: 4,
            timeSignatureNumerator = currentSequence?.timeSignatureNumerator ?: 4,
            timeSignatureDenominator = currentSequence?.timeSignatureDenominator ?: 4,
            ppqn = currentSequence?.ppqn ?: 96, // Ensure PPQN is also carried over or defaulted
            events = mutableListOf()
        )
        _sequences.value = _sequences.value + newSequence // Add to the list
        setCurrentSequenceInternal(newSequence) // This will also call loadSequenceData
        Log.d("SequencerViewModel", "Created new sequence '${newSequence.name}' and set as current.")
    }

    // This method is effectively replaced by setCurrentSequenceInternal + selectSequence
    // fun setCurrentSequence(sequence: Sequence) {
    //     setCurrentSequenceInternal(sequence)
    // }


    // For TransportBar's BPM Editor
    fun setBpm(newBpm: Float) {
        val targetSequence = currentSequence // Use currentSequence
        targetSequence?.let {
            if (newBpm > 0) { // Basic validation
                it.bpm = newBpm // This directly mutates the bpm property of the Sequence object
                println("SequencerViewModel: BPM set to $newBpm for sequence ${it.name}")

                // To make sure Compose recomposes if only bpm of currentSequence object changes:
                // One way is to replace the sequence in the list and currentSequence with a new instance.
                // currentSequence = it.copy(bpm = newBpm) // If Sequence is data class & this triggers recomposition
                // _sequences.value = _sequences.value.map { s -> if (s.id == it.id) currentSequence!! else s }
                // For now, direct mutation. `bpmText` in `TransportBar` will update due to `remember(currentSeq?.id, currentSeq?.bpm)`
                viewModelScope.launch {
                    audioEngine.setSequencerBpm(newBpm)
                }
                // No need to stop/start playback, C++ side handles BPM changes dynamically if playing.
            }
        }
    }

    fun startRecording() {
        isRecording = true
        _isRecordingState.value = true
    }

    fun stopRecording() {
        isRecording = false
        _isRecordingState.value = false
    }

    fun toggleRecording() {
        isRecording = !isRecording
        _isRecordingState.value = isRecording
        println("SequencerViewModel: Recording toggled to $isRecording")
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    // Method to record a pad trigger event
    fun recordPadTrigger(padId: String, velocity: Int) {
        if (!isRecording) return
        val targetSequence = currentSequence // Capture currentSequence
        if (targetSequence == null) {
            println("SequencerViewModel: No current sequence to record into.")
            return
        }

        targetSequence.let { seq -> // Use targetSequence, which is effectively currentSequence
            // 1. Get current timestamp (placeholder - this needs a real time source from playback engine)
            // For now, let's simulate a simple incrementing time or use System.currentTimeMillis()
            // This should eventually be replaced by the playhead position from M2.4
            val currentTimeTicks = System.currentTimeMillis() // THIS IS A VERY CRUDE PLACEHOLDER

            // 2. Quantize the timestamp
            // Example: Round to the nearest 16th note.
            // This is a simplified quantization logic. Real quantization can be more complex.
            val quantizedTimeTicks = (currentTimeTicks / ticksPer16thNote) * ticksPer16thNote

            // 3. Create a new Event
            val newEvent = Event(
                id = UUID.randomUUID().toString(), // Generate a unique ID for the event
                trackId = "", // Placeholder: associate with a track if necessary, or remove if not used
                startTimeTicks = quantizedTimeTicks,
                type = EventType.PadTrigger(
                    padId = padId,
                    velocity = velocity,
                    durationTicks = ticksPer16thNote // Default duration, might be configurable
                )
            )

            // 4. Add this new Event to the events list of the current Sequence
            // If seq.events is not a snapshot state list, UI might not update.
            // For now, direct mutation.
            seq.events.add(newEvent)
            // To trigger UI update if events list is observed as state:
            // currentSequence = seq.copy(events = seq.events.toMutableList().apply { add(newEvent) })
            // _sequences.value = _sequences.value.map { s -> if (s.id == seq.id) currentSequence!! else s }


            // For debugging:
            println("Recorded event: $newEvent to sequence ${seq.name}")
            println("Total events in ${seq.name}: ${seq.events.size}")
        }
    }

    // Placeholder for getting the current sequence's events for the UI or playback
    fun getEventsForCurrentSequence(): List<Event> {
        return currentSequence?.events ?: emptyList()
    }

    fun play() {
        // Ensure sequence is loaded before playing.
        // setCurrentSequenceInternal handles loading, so if currentSequence is set, it should be loaded.
        // If events were added via recordPadTrigger, setCurrentSequenceInternal or explicit load is needed.
        currentSequence?.let { seq ->
            if (seq.bpm <= 0) {
                Log.e("SequencerViewModel", "Cannot play, BPM is invalid (${seq.bpm}).")
                return@play // Use qualified return
            }
            if (_isPlayingState.value && playbackJob?.isActive == true) {
                 Log.d("SequencerViewModel", "Play called but already playing and polling job active.")
                return@play
            }

            viewModelScope.launch {
                // Explicitly ensure latest version of current sequence is loaded before playing.
                // This is important if events were added and not yet synced to native layer.
                // However, if this is called frequently, it might be inefficient.
                // For now, we assume that if `currentSequence` is set, it's the one to play.
                // A more robust system might have a "dirty" flag for the sequence.
                // audioEngine.loadSequenceData(seq) // Re-load to be absolutely sure, or rely on prior loads.
                                                 // For now, relying on prior loads via setCurrentSequenceInternal.

                audioEngine.playSequence()
                _isPlayingState.value = true
                Log.d("SequencerViewModel", "Playback started via native engine for sequence: ${seq.name}")

                // Cancel any existing polling job before starting a new one
                playbackJob?.cancel()
                playbackJob = viewModelScope.launch {
                    try {
                        while (isActive && _isPlayingState.value) { // Loop while coroutine is active and playing
                            _playheadPositionTicks.value = audioEngine.getSequencerPlayheadPosition()
                            delay(50) // Poll every 50ms
                        }
                    } finally {
                        // This block executes when the polling loop is cancelled or completes
                        if (!_isPlayingState.value) { // If stopped, ensure playhead is reset
                            _playheadPositionTicks.value = 0L
                        }
                        Log.d("SequencerViewModel", "Playhead polling job ended. isActive: $isActive, isPlaying: ${_isPlayingState.value}")
                    }
                }
            }
        } ?: run {
            Log.w("SequencerViewModel", "Play called but currentSequence is null.")
        }
    }

    fun stop() {
        viewModelScope.launch {
            if (!_isPlayingState.value && playbackJob == null) {
                Log.d("SequencerViewModel", "Stop called but already stopped or polling job null.")
                // Ensure UI state is consistent even if called redundantly
                _isPlayingState.value = false
                _playheadPositionTicks.value = 0L
                return@launch
            }
            audioEngine.stopSequence()
            _isPlayingState.value = false // Set state before cancelling job to influence job's finally block
            playbackJob?.cancel()
            playbackJob = null
            // _playheadPositionTicks.value = 0L // Reset in the polling job's finally or here. Redundant if also in finally.
                                             // Set to 0L directly here to ensure it's immediate.
            _playheadPositionTicks.value = 0L
            Log.d("SequencerViewModel", "Playback stopped via native engine. Polling job cancelled.")
        }
    }

    // For "Clear Sequence" Button
    fun clearCurrentSequenceEvents() {
        val targetSequence = currentSequence // Use currentSequence
        targetSequence?.events?.clear()
        println("SequencerViewModel: Cleared all events from sequence ${targetSequence?.name}")
        // Similar to recordPadTrigger, if events list is not a snapshot state list, UI might not update.
        // currentSequence = targetSequence?.copy(events = mutableListOf())
        // _sequences.value = _sequences.value.map { s -> if (s.id == targetSequence?.id) currentSequence!! else s }
    }

    // Call this when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        viewModelScope.coroutineContext[Job]?.cancel()
    }

    // --- Test Logic Moved from MainActivity ---

    private fun copyAssetToCache(context: Context, assetName: String, cacheFileName: String): String? {
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open(assetName)
            val cacheDir = context.cacheDir
            val outFile = File(cacheDir, cacheFileName)
            val outputStream = FileOutputStream(outFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            Log.i("SequencerViewModel_Test", "Copied asset '$assetName' to '${outFile.absolutePath}'")
            return outFile.absolutePath
        } catch (e: IOException) {
            Log.e("SequencerViewModel_Test", "Failed to copy asset $assetName to cache: ${e.message}", e)
            return null
        }
    }

    fun testMetronomeFullSequence() {
        viewModelScope.launch {
            if (!audioEngine.isInitialized()) {
                Log.e("SequencerViewModel_Test", "AudioEngine not initialized. Cannot run metronome test.")
                return@launch
            }
            val primaryClickId = "__METRONOME_PRIMARY_TEST__"
            val secondaryClickId = "__METRONOME_SECONDARY_TEST__"
            val primaryAssetName = "click_primary.wav" // Assuming these assets exist
            val secondaryAssetName = "click_secondary.wav"

            val primaryCachedPath = copyAssetToCache(applicationContext, primaryAssetName, "cached_click_primary_test.wav")
            if (primaryCachedPath != null) {
                val primaryFileUri = "file://$primaryCachedPath"
                audioEngine.loadSampleToMemory(applicationContext, primaryClickId, primaryFileUri)
            }
            val secondaryCachedPath = copyAssetToCache(applicationContext, secondaryAssetName, "cached_click_secondary_test.wav")
            if (secondaryCachedPath != null) {
                val secondaryFileUri = "file://$secondaryCachedPath"
                audioEngine.loadSampleToMemory(applicationContext, secondaryClickId, secondaryFileUri)
            }

            audioEngine.setMetronomeVolume(0.8f)
            Log.i("SequencerViewModel_Test", "Enabling metronome at 120 BPM, 4/4.")
            audioEngine.setMetronomeState( true, 120.0f, 4, 4, primaryClickId, secondaryClickId )
            delay(3000) // Shortened delay
            Log.i("SequencerViewModel_Test", "Changing metronome BPM to 180.")
            audioEngine.setMetronomeState( true, 180.0f, 4, 4, primaryClickId, secondaryClickId )
            delay(3000)
            Log.i("SequencerViewModel_Test", "Disabling metronome.")
            audioEngine.setMetronomeState( false, 180.0f, 4, 4, primaryClickId, secondaryClickId )
            // Cleanup loaded test samples
            audioEngine.unloadSample(primaryClickId)
            audioEngine.unloadSample(secondaryClickId)
        }
    }

    fun testAudioRecording() {
        viewModelScope.launch {
            if (!audioEngine.isInitialized()) {
                Log.e("SequencerViewModel_Test", "AudioEngine not initialized. Cannot run recording test.")
                return@launch
            }
            Log.i("SequencerViewModel_Test", "--- Starting Audio Recording Test ---")
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e("SequencerViewModel_Test", "RECORD_AUDIO permission not granted. Skipping recording test.")
                return@launch
            }
            val recordFileName = "vm_test_recording.wav"
            val recordFile = File(applicationContext.cacheDir, recordFileName)
            val recordFileUriString = "file://${recordFile.absolutePath}"
            val recordingSampleRate = 48000
            val recordingChannels = 1

            Log.i("SequencerViewModel_Test", "Attempting to start recording to: $recordFileUriString")
            val startSuccess = audioEngine.startAudioRecording( applicationContext, recordFileUriString, recordingSampleRate, recordingChannels )
            Log.i("SequencerViewModel_Test", "startAudioRecording result: $startSuccess")

            if (startSuccess) {
                Log.i("SequencerViewModel_Test", "Recording started. Will record for ~3 seconds.")
                delay(3000)
                Log.i("SequencerViewModel_Test", "Stopping recording...")
                val recordedSampleMetadata = audioEngine.stopAudioRecording()
                if (recordedSampleMetadata != null) {
                    Log.i("SequencerViewModel_Test", "stopAudioRecording successful. Metadata: $recordedSampleMetadata")
                    // Optional: load, play, unload test as in MainActivity
                } else { Log.e("SequencerViewModel_Test", "stopAudioRecording failed or returned null metadata.") }
            } else { Log.e("SequencerViewModel_Test", "Failed to start recording.") }
            Log.i("SequencerViewModel_Test", "--- Audio Recording Test Finished ---")
        }
    }

    fun testLoadAndPlaySample(assetName: String, sampleId: String) {
        viewModelScope.launch {
            if (!audioEngine.isInitialized()) {
                Log.e("SequencerViewModel_Test", "AudioEngine not initialized. Cannot run load/play test.")
                return@launch
            }
            Log.i("SequencerViewModel_Test", "Testing general sample loading ($assetName).")
            val cachedFilePath = copyAssetToCache(applicationContext, assetName, "cached_test_$sampleId.wav")
            if (cachedFilePath != null) {
                val fileUriString = "file://$cachedFilePath"
                val loadSuccess = audioEngine.loadSampleToMemory(applicationContext, sampleId, fileUriString)
                Log.i("SequencerViewModel_Test", "loadSampleToMemory($sampleId) result: $loadSuccess")
                if (loadSuccess && audioEngine.isSampleLoaded(sampleId)) {
                    Log.i("SequencerViewModel_Test", "Attempting to play sample $sampleId...")
                    audioEngine.playSample(sampleId = sampleId, noteInstanceId = "test_instance_${sampleId}", volume = 0.8f, pan = 0.0f)
                    delay(1000) // Play for a bit
                    audioEngine.unloadSample(sampleId)
                    Log.i("SequencerViewModel_Test", "Unloaded $sampleId")
                } else { Log.e("SequencerViewModel_Test", "Failed to load $sampleId or sample not reported as loaded.") }
            } else { Log.e("SequencerViewModel_Test", "Failed to copy asset $assetName to cache.") }
        }
    }
}
