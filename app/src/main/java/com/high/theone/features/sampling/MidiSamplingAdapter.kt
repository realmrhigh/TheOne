package com.high.theone.features.sampling

import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.input.ProcessedMidiMessage
import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.midi.model.MidiTargetType
import com.high.theone.model.PadState
import com.high.theone.model.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * Adapter that connects MIDI input processing to the sampling system.
 * Handles MIDI note-to-pad mapping and triggers pad events from MIDI input.
 * Provides comprehensive MIDI-to-sampling integration including velocity-sensitive
 * sample playback, note-off handling for sustained samples, and real-time pad triggering.
 * 
 * Requirements: 1.1 (MIDI note mapping), 1.2 (note-off handling), 5.1 (velocity-sensitive playback)
 */
@Singleton
class MidiSamplingAdapter @Inject constructor(
    private val midiInputProcessor: MidiInputProcessor
) {
    
    companion object {
        private const val TAG = "MidiSamplingAdapter"
        private const val MAX_SUSTAINED_NOTES = 128 // Maximum number of sustained notes to track
    }
    
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Flows for MIDI pad events
    private val _padTriggerEvents = MutableSharedFlow<MidiPadTriggerEvent>(
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val padTriggerEvents: Flow<MidiPadTriggerEvent> = _padTriggerEvents.asSharedFlow()
    
    private val _padStopEvents = MutableSharedFlow<MidiPadStopEvent>(
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val padStopEvents: Flow<MidiPadStopEvent> = _padStopEvents.asSharedFlow()
    
    // Current pad configuration for MIDI mapping
    private var currentPads: List<PadState> = emptyList()
    
    // Track sustained notes for proper note-off handling
    private val sustainedNotes = mutableMapOf<Pair<Int, Int>, SustainedNoteInfo>() // (note, channel) -> info
    
    // Statistics tracking
    private var totalTriggerEvents = 0L
    private var totalStopEvents = 0L
    private var lastProcessingTimeMs = 0f
    
    private var isInitialized = false
    
    /**
     * Initialize the MIDI sampling adapter
     */
    fun initialize() {
        if (isInitialized) return
        
        // Register as a handler for pad trigger messages
        midiInputProcessor.registerMessageHandler(MidiTargetType.PAD_TRIGGER) { processedMessage ->
            handleMidiMessage(processedMessage.originalMessage)
        }
        
        // Start processing MIDI messages
        midiInputProcessor.startProcessing()
        
        isInitialized = true
        Log.d(TAG, "MIDI sampling adapter initialized")
    }
    
    /**
     * Shutdown the MIDI sampling adapter
     */
    fun shutdown() {
        if (!isInitialized) return
        
        midiInputProcessor.unregisterMessageHandler(MidiTargetType.PAD_TRIGGER)
        midiInputProcessor.stopProcessing()
        
        isInitialized = false
        Log.d(TAG, "MIDI sampling adapter shutdown")
    }
    
    /**
     * Update the current pad configuration for MIDI mapping
     */
    fun updatePadConfiguration(pads: List<PadState>) {
        currentPads = pads
        Log.d(TAG, "Updated pad configuration with ${pads.size} pads")
    }
    
    /**
     * Process a MIDI message and generate pad events if applicable
     */
    private fun handleMidiMessage(message: MidiMessage) {
        adapterScope.launch {
            try {
                when (message.type) {
                    MidiMessageType.NOTE_ON -> {
                        if (message.data2 > 0) { // Velocity > 0 means actual note on
                            handleNoteOn(message)
                        } else { // Velocity 0 means note off
                            handleNoteOff(message)
                        }
                    }
                    MidiMessageType.NOTE_OFF -> {
                        handleNoteOff(message)
                    }
                    else -> {
                        // Other message types not handled for pad triggering
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling MIDI message", e)
            }
        }
    }
    
    /**
     * Handle MIDI Note On message with enhanced sustained note tracking
     */
    private fun handleNoteOn(message: MidiMessage) {
        val startTime = System.nanoTime()
        
        val padIndex = MidiPadIntegration.findPadForMidiNote(
            pads = currentPads,
            midiNote = message.data1,
            midiChannel = message.channel
        )
        
        if (padIndex != null) {
            val pad = currentPads.getOrNull(padIndex)
            if (pad != null) {
                val triggerEvent = MidiPadIntegration.createPadTriggerEvent(message, pad)
                if (triggerEvent != null) {
                    // Track sustained notes for proper note-off handling
                    if (pad.playbackMode == PlaybackMode.NOTE_ON_OFF || pad.playbackMode == PlaybackMode.GATE) {
                        val noteKey = Pair(message.data1, message.channel)
                        sustainedNotes[noteKey] = SustainedNoteInfo(
                            padIndex = padIndex,
                            velocity = message.data2,
                            startTime = message.timestamp,
                            playbackMode = pad.playbackMode
                        )
                        
                        // Clean up old sustained notes if we exceed the limit
                        if (sustainedNotes.size > MAX_SUSTAINED_NOTES) {
                            val oldestNote = sustainedNotes.minByOrNull { it.value.startTime }
                            oldestNote?.let { sustainedNotes.remove(it.key) }
                        }
                    }
                    
                    _padTriggerEvents.tryEmit(triggerEvent)
                    totalTriggerEvents++
                    
                    Log.d(TAG, "MIDI Note On: Pad $padIndex triggered (note ${message.data1}, velocity ${message.data2}, mode ${pad.playbackMode})")
                }
            }
        } else {
            Log.v(TAG, "MIDI Note On: No pad mapped to note ${message.data1} on channel ${message.channel}")
        }
        
        // Update processing time statistics
        val processingTime = (System.nanoTime() - startTime) / 1_000_000f
        lastProcessingTimeMs = processingTime
        
        if (processingTime > 1.0f) { // Log if processing takes more than 1ms
            Log.w(TAG, "MIDI Note On processing took ${processingTime}ms")
        }
    }
    
    /**
     * Handle MIDI Note Off message with enhanced sustained note tracking
     */
    private fun handleNoteOff(message: MidiMessage) {
        val noteKey = Pair(message.data1, message.channel)
        val sustainedNote = sustainedNotes.remove(noteKey)
        
        if (sustainedNote != null) {
            val stopEvent = MidiPadStopEvent(
                padIndex = sustainedNote.padIndex,
                midiNote = message.data1,
                midiChannel = message.channel,
                timestamp = message.timestamp,
                originalVelocity = sustainedNote.velocity,
                sustainDurationMs = (message.timestamp - sustainedNote.startTime) / 1_000_000L
            )
            _padStopEvents.tryEmit(stopEvent)
            totalStopEvents++
            
            Log.d(TAG, "MIDI Note Off: Pad ${sustainedNote.padIndex} stopped (note ${message.data1}, sustained for ${stopEvent.sustainDurationMs}ms)")
        } else {
            // Try to find pad even if not sustained (for compatibility)
            val padIndex = MidiPadIntegration.findPadForMidiNote(
                pads = currentPads,
                midiNote = message.data1,
                midiChannel = message.channel
            )
            
            if (padIndex != null) {
                val stopEvent = MidiPadStopEvent(
                    padIndex = padIndex,
                    midiNote = message.data1,
                    midiChannel = message.channel,
                    timestamp = message.timestamp
                )
                _padStopEvents.tryEmit(stopEvent)
                totalStopEvents++
                
                Log.d(TAG, "MIDI Note Off: Pad $padIndex stopped (note ${message.data1}, not sustained)")
            }
        }
    }
    
    /**
     * Get statistics about MIDI pad processing
     */
    fun getProcessingStatistics(): MidiSamplingStatistics {
        val midiStats = midiInputProcessor.getProcessingStatistics()
        
        return MidiSamplingStatistics(
            totalMidiMessages = midiStats.processedMessageCount,
            padTriggerEvents = totalTriggerEvents,
            padStopEvents = totalStopEvents,
            mappedPads = currentPads.count { it.canTriggerFromMidi },
            totalPads = currentPads.size,
            sustainedNotes = sustainedNotes.size,
            averageProcessingTimeMs = lastProcessingTimeMs
        )
    }
    
    /**
     * Get information about currently sustained notes
     */
    fun getSustainedNotes(): Map<Pair<Int, Int>, SustainedNoteInfo> {
        return sustainedNotes.toMap()
    }
    
    /**
     * Force stop all sustained notes (panic function)
     */
    fun stopAllSustainedNotes() {
        val currentTime = System.nanoTime()
        
        sustainedNotes.forEach { (noteKey, noteInfo) ->
            val stopEvent = MidiPadStopEvent(
                padIndex = noteInfo.padIndex,
                midiNote = noteKey.first,
                midiChannel = noteKey.second,
                timestamp = currentTime,
                originalVelocity = noteInfo.velocity,
                sustainDurationMs = (currentTime - noteInfo.startTime) / 1_000_000L
            )
            _padStopEvents.tryEmit(stopEvent)
            totalStopEvents++
        }
        
        val stoppedCount = sustainedNotes.size
        sustainedNotes.clear()
        
        if (stoppedCount > 0) {
            Log.i(TAG, "Panic: Stopped $stoppedCount sustained notes")
        }
    }
    
    /**
     * Test MIDI pad mapping by simulating a MIDI message
     */
    fun testPadMapping(padIndex: Int, velocity: Int = 100) {
        val pad = currentPads.getOrNull(padIndex)
        if (pad?.midiNote != null) {
            val testMessage = MidiMessage(
                type = MidiMessageType.NOTE_ON,
                channel = pad.midiChannel,
                data1 = pad.midiNote,
                data2 = velocity,
                timestamp = System.nanoTime()
            )
            handleMidiMessage(testMessage)
            Log.d(TAG, "Test triggered pad $padIndex with MIDI note ${pad.midiNote}")
        } else {
            Log.w(TAG, "Cannot test pad $padIndex - no MIDI note assigned")
        }
    }
    
    /**
     * Test MIDI note-off for a specific pad
     */
    fun testPadNoteOff(padIndex: Int) {
        val pad = currentPads.getOrNull(padIndex)
        if (pad?.midiNote != null) {
            val testMessage = MidiMessage(
                type = MidiMessageType.NOTE_OFF,
                channel = pad.midiChannel,
                data1 = pad.midiNote,
                data2 = 0,
                timestamp = System.nanoTime()
            )
            handleMidiMessage(testMessage)
            Log.d(TAG, "Test note-off for pad $padIndex with MIDI note ${pad.midiNote}")
        } else {
            Log.w(TAG, "Cannot test note-off for pad $padIndex - no MIDI note assigned")
        }
    }
    
    /**
     * Check if a specific MIDI note is currently sustained
     */
    fun isNoteSustained(midiNote: Int, midiChannel: Int): Boolean {
        return sustainedNotes.containsKey(Pair(midiNote, midiChannel))
    }
    
    /**
     * Get the pad index for a sustained note
     */
    fun getPadForSustainedNote(midiNote: Int, midiChannel: Int): Int? {
        return sustainedNotes[Pair(midiNote, midiChannel)]?.padIndex
    }
    
    /**
     * Validate current pad configuration for MIDI compatibility
     */
    fun validatePadConfiguration(): List<String> {
        val issues = mutableListOf<String>()
        
        // Check for duplicate MIDI note assignments
        val noteAssignments = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
        
        currentPads.forEach { pad ->
            if (pad.midiNote != null && pad.canTriggerFromMidi) {
                val noteKey = Pair(pad.midiNote, pad.midiChannel)
                noteAssignments.getOrPut(noteKey) { mutableListOf() }.add(pad.index)
            }
        }
        
        noteAssignments.forEach { (noteKey, padIndices) ->
            if (padIndices.size > 1) {
                issues.add("MIDI note ${noteKey.first} on channel ${noteKey.second} is assigned to multiple pads: ${padIndices.joinToString()}")
            }
        }
        
        // Check for pads with samples but no MIDI mapping
        val unmappedPads = currentPads.filter { it.hasAssignedSample && it.midiNote == null }
        if (unmappedPads.isNotEmpty()) {
            issues.add("${unmappedPads.size} pads have samples but no MIDI note assignment")
        }
        
        return issues
    }
}

/**
 * Information about a sustained note for proper note-off handling
 */
data class SustainedNoteInfo(
    val padIndex: Int,
    val velocity: Int,
    val startTime: Long,
    val playbackMode: PlaybackMode
)

/**
 * Statistics for MIDI sampling processing
 */
data class MidiSamplingStatistics(
    val totalMidiMessages: Long,
    val padTriggerEvents: Long,
    val padStopEvents: Long,
    val mappedPads: Int,
    val totalPads: Int,
    val sustainedNotes: Int,
    val averageProcessingTimeMs: Float
)