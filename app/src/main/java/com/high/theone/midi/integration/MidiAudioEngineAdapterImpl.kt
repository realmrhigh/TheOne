package com.high.theone.midi.integration

import com.high.theone.audio.AudioEngineControl
import com.high.theone.midi.model.*
import com.high.theone.midi.input.MidiVelocityCurve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MidiAudioEngineControl that wraps an existing AudioEngineControl
 * and adds MIDI-specific functionality for sample triggering and parameter control.
 */
@Singleton
class MidiAudioEngineAdapterImpl @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val velocityCurve: MidiVelocityCurve
) : MidiAudioEngineControl, AudioEngineControl by audioEngine {
    
    private val mutex = Mutex()
    private val _midiMessageFlow = MutableSharedFlow<MidiMessage>(replay = 0, extraBufferCapacity = 100)
    private val midiMessageFlow = _midiMessageFlow.asSharedFlow()
    
    // MIDI state
    private var midiClockSyncEnabled = false
    private var currentClockSource = MidiClockSource.INTERNAL
    private var clockDeviceId: String? = null
    private var midiInputLatency = 0.0f
    private var monitoringEnabled = false
    
    // MIDI note mappings: (note, channel) -> padIndex
    private val noteMappings = mutableMapOf<Pair<Int, Int>, Int>()
    
    // Statistics
    private var processedMessageCount = 0L
    private var droppedMessageCount = 0L
    private var totalLatency = 0.0
    private var lastErrorMessage: String? = null
    
    // Default note mappings (C3-D#4 maps to pads 0-15)
    init {
        setupDefaultNoteMappings()
    }
    
    private fun setupDefaultNoteMappings() {
        // Map MIDI notes 60-75 (C4-D#5) to pads 0-15 on channel 0
        for (i in 0..15) {
            noteMappings[Pair(60 + i, 0)] = i
        }
    }
    
    override suspend fun triggerSampleFromMidi(
        padIndex: Int,
        velocity: Int,
        midiNote: Int,
        midiChannel: Int
    ): Boolean {
        return try {
            require(padIndex in 0..15) { "Pad index must be between 0 and 15" }
            require(velocity in 0..127) { "Velocity must be between 0 and 127" }
            require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
            require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
            
            // Apply velocity curve
            val processedVelocity = velocityCurve.applyVelocityCurve(velocity)
            
            // Trigger the drum pad with processed velocity
            audioEngine.triggerDrumPad(padIndex, processedVelocity)
            
            // Update statistics
            mutex.withLock {
                processedMessageCount++
            }
            
            // Emit MIDI message for monitoring
            if (monitoringEnabled) {
                _midiMessageFlow.tryEmit(
                    MidiMessage(
                        type = MidiMessageType.NOTE_ON,
                        channel = midiChannel,
                        data1 = midiNote,
                        data2 = velocity,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            true
        } catch (e: Exception) {
            lastErrorMessage = "Failed to trigger sample from MIDI: ${e.message}"
            mutex.withLock {
                droppedMessageCount++
            }
            false
        }
    }
    
    override suspend fun stopSampleFromMidi(
        padIndex: Int,
        midiNote: Int,
        midiChannel: Int,
        releaseTimeMs: Float?
    ): Boolean {
        return try {
            require(padIndex in 0..15) { "Pad index must be between 0 and 15" }
            require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
            require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
            
            // Release the drum pad
            audioEngine.releaseDrumPad(padIndex)
            
            // Update statistics
            mutex.withLock {
                processedMessageCount++
            }
            
            // Emit MIDI message for monitoring
            if (monitoringEnabled) {
                _midiMessageFlow.tryEmit(
                    MidiMessage(
                        type = MidiMessageType.NOTE_OFF,
                        channel = midiChannel,
                        data1 = midiNote,
                        data2 = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            true
        } catch (e: Exception) {
            lastErrorMessage = "Failed to stop sample from MIDI: ${e.message}"
            mutex.withLock {
                droppedMessageCount++
            }
            false
        }
    }
    
    override suspend fun setParameterFromMidi(
        parameterId: String,
        value: Float,
        midiController: Int,
        midiChannel: Int
    ) {
        try {
            require(value in 0.0f..1.0f) { "Parameter value must be between 0.0 and 1.0" }
            require(midiController in 0..127) { "MIDI controller must be between 0 and 127" }
            require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
            
            when {
                parameterId.startsWith("pad_") && parameterId.endsWith("_volume") -> {
                    val padIndex = parameterId.substringAfter("pad_").substringBefore("_").toIntOrNull()
                    if (padIndex != null && padIndex in 0..15) {
                        audioEngine.setDrumPadVolume(padIndex, value)
                    }
                }
                parameterId.startsWith("pad_") && parameterId.endsWith("_pan") -> {
                    val padIndex = parameterId.substringAfter("pad_").substringBefore("_").toIntOrNull()
                    if (padIndex != null && padIndex in 0..15) {
                        // Convert 0.0-1.0 to -1.0-1.0 for pan
                        val panValue = (value * 2.0f) - 1.0f
                        audioEngine.setDrumPadPan(padIndex, panValue)
                    }
                }
                parameterId == "master_volume" -> {
                    audioEngine.setDrumMasterVolume(value)
                }
                parameterId == "sequencer_tempo" -> {
                    // Convert 0.0-1.0 to BPM range (60-200)
                    val bpm = 60.0f + (value * 140.0f)
                    audioEngine.setSequencerTempo(bpm)
                }
            }
            
            // Update statistics
            mutex.withLock {
                processedMessageCount++
            }
            
            // Emit MIDI message for monitoring
            if (monitoringEnabled) {
                _midiMessageFlow.tryEmit(
                    MidiMessage(
                        type = MidiMessageType.CONTROL_CHANGE,
                        channel = midiChannel,
                        data1 = midiController,
                        data2 = (value * 127).toInt(),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            lastErrorMessage = "Failed to set parameter from MIDI: ${e.message}"
            mutex.withLock {
                droppedMessageCount++
            }
        }
    }
    
    override suspend fun setPadVolumeFromMidi(padIndex: Int, volume: Float, midiChannel: Int) {
        setParameterFromMidi("pad_${padIndex}_volume", volume, 7, midiChannel)
    }
    
    override suspend fun setPadPanFromMidi(padIndex: Int, pan: Float, midiChannel: Int) {
        // Convert -1.0-1.0 to 0.0-1.0 for parameter system
        val normalizedPan = (pan + 1.0f) / 2.0f
        setParameterFromMidi("pad_${padIndex}_pan", normalizedPan, 10, midiChannel)
    }
    
    override suspend fun setMasterVolumeFromMidi(volume: Float, midiChannel: Int) {
        setParameterFromMidi("master_volume", volume, 7, midiChannel)
    }
    
    override suspend fun syncToMidiClock(clockPulse: MidiClockPulse) {
        if (!midiClockSyncEnabled) return
        
        try {
            // Update sequencer tempo based on MIDI clock
            audioEngine.setSequencerTempo(clockPulse.bpm)
            
            // Update statistics
            mutex.withLock {
                processedMessageCount++
            }
        } catch (e: Exception) {
            lastErrorMessage = "Failed to sync to MIDI clock: ${e.message}"
        }
    }
    
    override suspend fun handleMidiTransport(message: MidiTransportMessage, songPosition: Int?) {
        try {
            when (message) {
                MidiTransportMessage.START -> {
                    // Start sequencer playback
                    // Note: This would need integration with sequencer transport controls
                }
                MidiTransportMessage.STOP -> {
                    // Stop sequencer playback
                    audioEngine.stopAllNotes(null, true)
                }
                MidiTransportMessage.CONTINUE -> {
                    // Continue sequencer playback from current position
                }
                MidiTransportMessage.SONG_POSITION -> {
                    // Set song position if provided
                }
            }
            
            // Update statistics
            mutex.withLock {
                processedMessageCount++
            }
        } catch (e: Exception) {
            lastErrorMessage = "Failed to handle MIDI transport: ${e.message}"
        }
    }
    
    override suspend fun setMidiClockSyncEnabled(enabled: Boolean) {
        midiClockSyncEnabled = enabled
        native_setMidiClockSyncEnabled(enabled)
    }
    
    override suspend fun setMidiClockSource(source: MidiClockSource, deviceId: String?) {
        currentClockSource = source
        clockDeviceId = deviceId
    }
    
    override suspend fun mapMidiNoteToPad(midiNote: Int, midiChannel: Int, padIndex: Int) {
        require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
        require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
        require(padIndex in 0..15) { "Pad index must be between 0 and 15" }
        
        mutex.withLock {
            noteMappings[Pair(midiNote, midiChannel)] = padIndex
        }
        
        // Update native mapping
        native_setMidiNoteMapping(midiNote, midiChannel, padIndex)
    }
    
    override suspend fun removeMidiNoteMapping(midiNote: Int, midiChannel: Int) {
        require(midiNote in 0..127) { "MIDI note must be between 0 and 127" }
        require(midiChannel in 0..15) { "MIDI channel must be between 0 and 15" }
        
        mutex.withLock {
            noteMappings.remove(Pair(midiNote, midiChannel))
        }
        
        // Update native mapping
        native_removeMidiNoteMapping(midiNote, midiChannel)
    }
    
    override suspend fun getMidiNoteMappings(): Map<Pair<Int, Int>, Int> {
        return mutex.withLock {
            noteMappings.toMap()
        }
    }
    
    override suspend fun setMidiVelocityCurve(curve: MidiCurve, sensitivity: Float) {
        require(sensitivity > 0.0f) { "Velocity sensitivity must be positive" }
        velocityCurve.setCurve(curve, sensitivity)
        
        // Update native curve
        val curveType = when (curve) {
            MidiCurve.LINEAR -> 0
            MidiCurve.EXPONENTIAL -> 1
            MidiCurve.LOGARITHMIC -> 2
            MidiCurve.S_CURVE -> 3
        }
        native_setMidiVelocityCurve(curveType, sensitivity)
    }
    
    override suspend fun applyVelocityCurve(velocity: Int): Float {
        require(velocity in 0..127) { "Velocity must be between 0 and 127" }
        return velocityCurve.applyVelocityCurve(velocity)
    }
    
    override suspend fun getMidiProcessingStats(): MidiStatistics {
        return mutex.withLock {
            val avgLatency = if (processedMessageCount > 0) {
                (totalLatency / processedMessageCount).toFloat()
            } else {
                0.0f
            }
            
            MidiStatistics(
                inputMessageCount = processedMessageCount,
                outputMessageCount = 0L, // This adapter doesn't generate output
                averageInputLatency = avgLatency,
                droppedMessageCount = droppedMessageCount,
                lastErrorMessage = lastErrorMessage
            )
        }
    }
    
    override suspend fun setMidiMonitoringEnabled(enabled: Boolean) {
        monitoringEnabled = enabled
    }
    
    override fun getMidiMessageFlow(): Flow<MidiMessage> {
        return midiMessageFlow
    }
    
    override suspend fun setMidiInputLatency(latencyMs: Float) {
        require(latencyMs >= 0.0f) { "Latency cannot be negative" }
        midiInputLatency = latencyMs
    }
    
    override suspend fun getMidiInputLatency(): Float {
        return midiInputLatency
    }
    
    override suspend fun calibrateMidiLatency(): Float {
        // Simple latency calibration - in a real implementation this would
        // involve measuring round-trip time with audio feedback
        val measuredLatency = audioEngine.getReportedLatencyMillis()
        midiInputLatency = measuredLatency
        return measuredLatency
    }
    
    // Native method declarations
    private external fun native_processMidiMessage(type: Int, channel: Int, data1: Int, data2: Int, timestamp: Long)
    private external fun native_scheduleMidiEvent(type: Int, channel: Int, data1: Int, data2: Int, timestamp: Long)
    private external fun native_setMidiNoteMapping(midiNote: Int, midiChannel: Int, padIndex: Int)
    private external fun native_removeMidiNoteMapping(midiNote: Int, midiChannel: Int)
    private external fun native_setMidiVelocityCurve(curveType: Int, sensitivity: Float)
    private external fun native_applyMidiVelocityCurve(velocity: Int): Float
    private external fun native_setMidiClockSyncEnabled(enabled: Boolean)
    private external fun native_processMidiClockPulse(timestamp: Long, bpm: Float)
    private external fun native_handleMidiTransport(transportType: Int)
    private external fun native_setMidiInputLatency(latencyMicros: Long)
    private external fun native_getMidiStatistics(): Map<String, Long>
    private external fun native_setExternalClockEnabled(useExternal: Boolean)
    private external fun native_setClockSmoothingFactor(factor: Float)
    private external fun native_getCurrentBpm(): Float
    private external fun native_isClockStable(): Boolean
    
    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
    
    /**
     * Process a MIDI message and route it to the appropriate handler
     */
    suspend fun processMidiMessage(message: MidiMessage) {
        val startTime = System.nanoTime()
        
        try {
            // Send to native processing for optimal performance
            val typeValue = when (message.type) {
                MidiMessageType.NOTE_ON -> 0x90
                MidiMessageType.NOTE_OFF -> 0x80
                MidiMessageType.CONTROL_CHANGE -> 0xB0
                MidiMessageType.PROGRAM_CHANGE -> 0xC0
                MidiMessageType.PITCH_BEND -> 0xE0
                MidiMessageType.AFTERTOUCH -> 0xA0
                MidiMessageType.SYSTEM_EXCLUSIVE -> 0xF0
                MidiMessageType.CLOCK -> 0xF8
                MidiMessageType.START -> 0xFA
                MidiMessageType.STOP -> 0xFC
                MidiMessageType.CONTINUE -> 0xFB
            }
            
            native_processMidiMessage(
                typeValue or message.channel,
                message.channel,
                message.data1,
                message.data2,
                message.timestamp
            )
            
            // Update latency statistics
            val processingTime = (System.nanoTime() - startTime) / 1_000_000.0 // Convert to ms
            mutex.withLock {
                totalLatency += processingTime
            }
            
        } catch (e: Exception) {
            lastErrorMessage = "Error processing MIDI message: ${e.message}"
            mutex.withLock {
                droppedMessageCount++
            }
        }
    }
}