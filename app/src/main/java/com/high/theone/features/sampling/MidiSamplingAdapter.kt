package com.high.theone.features.sampling

import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.input.ProcessedMidiMessage
import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.midi.model.MidiTargetType
import com.high.theone.model.PadState
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
 * 
 * Requirements: 1.1 (MIDI note mapping), 1.3 (MIDI velocity), 5.2 (pad integration)
 */
@Singleton
class MidiSamplingAdapter @Inject constructor(
    private val midiInputProcessor: MidiInputProcessor
) {
    
    companion object {
        private const val TAG = "MidiSamplingAdapter"
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
     * Handle MIDI Note On message
     */
    private fun handleNoteOn(message: MidiMessage) {
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
                    _padTriggerEvents.tryEmit(triggerEvent)
                    Log.d(TAG, "MIDI Note On: Pad $padIndex triggered (note ${message.data1}, velocity ${message.data2})")
                }
            }
        } else {
            Log.v(TAG, "MIDI Note On: No pad mapped to note ${message.data1} on channel ${message.channel}")
        }
    }
    
    /**
     * Handle MIDI Note Off message
     */
    private fun handleNoteOff(message: MidiMessage) {
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
            Log.d(TAG, "MIDI Note Off: Pad $padIndex stopped (note ${message.data1})")
        }
    }
    
    /**
     * Get statistics about MIDI pad processing
     */
    fun getProcessingStatistics(): MidiSamplingStatistics {
        val midiStats = midiInputProcessor.getProcessingStatistics()
        
        return MidiSamplingStatistics(
            totalMidiMessages = midiStats.processedMessageCount,
            padTriggerEvents = 0, // TODO: Track this
            padStopEvents = 0, // TODO: Track this
            mappedPads = currentPads.count { it.canTriggerFromMidi },
            totalPads = currentPads.size,
            averageProcessingTimeMs = midiStats.averageProcessingTimeMs
        )
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
}

/**
 * Statistics for MIDI sampling processing
 */
data class MidiSamplingStatistics(
    val totalMidiMessages: Long,
    val padTriggerEvents: Long,
    val padStopEvents: Long,
    val mappedPads: Int,
    val totalPads: Int,
    val averageProcessingTimeMs: Float
)