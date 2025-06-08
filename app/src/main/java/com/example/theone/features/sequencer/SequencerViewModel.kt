package com.example.theone.features.sequencer

import androidx.lifecycle.ViewModel
import com.example.theone.audio.AudioEngine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.theone.model.Event
import com.example.theone.model.EventType
import com.example.theone.model.Sequence // This was the problematic import before
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
// import com.example.theone.model.Project // Not using Project directly for now
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
// import kotlinx.coroutines.GlobalScope // Not needed if using println

@HiltViewModel
class SequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngine // Added AudioEngine
) : ViewModel() {

    private val _playheadPositionTicks = MutableStateFlow(0L)
    val playheadPositionTicks: StateFlow<Long> = _playheadPositionTicks.asStateFlow()

    private var playbackJob: Job? = null
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
    }

    // Method to internally set the current sequence and update related states
    private fun setCurrentSequenceInternal(sequence: Sequence?) {
        currentSequence = sequence
        // When sequence changes, update BPM text in TransportBar (if it's observing currentSequence directly or via a derived state)
        // For now, TransportBar reads currentSequence.bpm, so this should be fine.
        // Also reset playback and playhead
        stop() // Stops playback and resets playhead
        _playheadPositionTicks.value = 0L // Ensure playhead is at 0 for the new sequence
        // Update any other UI or state that depends on the current sequence
        println("SequencerViewModel: Current sequence set to ${sequence?.name}")
    }

    // Public method for sequence selection (will be used by dropdown later)
    fun selectSequence(sequenceId: String) {
        val sequenceToSelect = _sequences.value.find { it.id == sequenceId }
        sequenceToSelect?.let {
            setCurrentSequenceInternal(it)
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
            events = mutableListOf()
        )
        _sequences.value = _sequences.value + newSequence // Add to the list
        setCurrentSequenceInternal(newSequence) // Set the new sequence as current
        println("SequencerViewModel: Created new sequence '${newSequence.name}' and set as current.")
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

                if (playbackJob?.isActive == true) {
                    stop()
                    play()
                }
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
        if (playbackJob?.isActive == true) {
            return
        }
        val targetSequence = currentSequence // Use currentSequence
        targetSequence?.let { seq ->
            if (seq.events.isEmpty() && seq.bpm <= 0) {
                println("SequencerViewModel: Cannot play, sequence is empty or BPM is invalid.")
                return
            }

            playbackJob = viewModelScope.launch {
                _isPlayingState.value = true
                var lastTickTime = System.currentTimeMillis()
                // PPQN (Pulses Per Quarter Note) is a standard. If 96 PPQN, and we have 16th notes:
                // Ticks per quarter note = 96. So, ticksPer16thNote = 96 / 4 = 24.
                // This means `ticksPer16thNote` should represent the number of ticks in a 16th note.
                val ppqn = ticksPer16thNote * 4 // Pulses Per Quarter Note

                if (ppqn <= 0) {
                    println("SequencerViewModel: PPQN is invalid, cannot calculate tick duration. ppqn: $ppqn, ticksPer16thNote: $ticksPer16thNote")
                    return@launch
                }
                if (seq.bpm <= 0) {
                    println("SequencerViewModel: BPM is invalid, cannot calculate tick duration. bpm: ${seq.bpm}")
                    return@launch
                }


                // Delay for one tick in ms: (60,000 ms per minute / BPM) / PPQN for that beat type
                // If PPQN is for quarter notes, then this is (ms per quarter note) / ticks_per_quarter_note
                val tickDurationMs = (60000.0 / seq.bpm) / ppqn

                if (tickDurationMs <= 0) {
                    println("SequencerViewModel: Tick duration is invalid. Check BPM and PPQN values. tickDurationMs: $tickDurationMs")
                    return@launch
                }


                _playheadPositionTicks.value = 0L // Start from the beginning

                while (isActive) {
                    val currentLoopPositionTicks = _playheadPositionTicks.value

                    // Check for events at the current playhead position
                    seq.events.filter { it.startTimeTicks == currentLoopPositionTicks }.forEach { event ->
                        if (event.type is EventType.PadTrigger) {
                            val padTrigger = event.type as EventType.PadTrigger
                            // audioEngine.playPadSample(padId = padTrigger.padId, velocity = padTrigger.velocity, /* other params */)
                            // This is where the actual audio triggering would happen.
                            // For now, printing to console as per subtask instructions.
                            println("Playback: Triggering Pad ${padTrigger.padId} at velocity ${padTrigger.velocity} at tick $currentLoopPositionTicks")
                            // The actual call to audioEngine would require mapping padId to sampleId/trackId etc.
                            // e.g., audioEngine.playPadSample(trackId = "someTrack", padId = padTrigger.padId, sampleUri = "uriForPad", velocity = padTrigger.velocity)
                            // This detail needs to be fleshed out based on AudioEngine's final API and project structure.
                        }
                    }

                    // Increment playhead
                    _playheadPositionTicks.value = (currentLoopPositionTicks + 1)

                    // Handle Looping
                    // seq.barLength = number of bars in the sequence (e.g. 1, 2, 4 bars)
                    // seq.timeSignatureNumerator = beats per bar (e.g. 4 for 4/4 time)
                    // ppqn = ticks per quarter note
                    // A beat is usually a quarter note in contexts like this (e.g. 4/4 time, BPM refers to quarter notes)
                    val ticksPerBeat = ppqn.toLong() // If ppqn is for quarter notes
                    val totalBeatsInSequence = seq.barLength * seq.timeSignatureNumerator // Total quarter notes in sequence
                    val totalTicksInSequence = totalBeatsInSequence * ticksPerBeat

                    if (totalTicksInSequence <= 0) {
                         println("SequencerViewModel: totalTicksInSequence is invalid ($totalTicksInSequence), playback may not loop correctly.")
                        // Potentially stop playback or handle error, for now, it will just not loop.
                    } else if (_playheadPositionTicks.value >= totalTicksInSequence) {
                        _playheadPositionTicks.value = 0L // Loop back to the beginning
                         println("SequencerViewModel: Looping sequence. Total Ticks: $totalTicksInSequence")
                    }


                    // Delay for the duration of one tick
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - lastTickTime
                    // Ensure calculated delayTime is not negative, which can happen if processing takes longer than tickDurationMs
                    val delayTime = Math.max(0, (tickDurationMs - elapsedTime).toLong())

                    delay(delayTime)
                    lastTickTime = System.currentTimeMillis() // More accurate for next tick calculation
                }
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _playheadPositionTicks.value = 0L // Reset playhead
        _isPlayingState.value = false // Add this - As per instruction
        println("SequencerViewModel: Playback stopped.")
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
        // viewModelScope.cancel() // Cancel the scope itself. It's good practice.
        // However, the Job passed to CoroutineScope constructor will be cancelled if the ViewModel is cleared
        // if this viewModelScope is tied to viewModelScope provided by androidx.lifecycle.viewModelScope
        // Since we created a custom one `CoroutineScope(Dispatchers.Main + Job())`, we should cancel its Job.
        viewModelScope.coroutineContext[Job]?.cancel() // Cancel the custom scope's job
    }
}
