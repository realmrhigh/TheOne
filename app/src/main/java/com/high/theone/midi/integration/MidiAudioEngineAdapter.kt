package com.high.theone.midi.integration

import com.high.theone.audio.AudioEngineControl
import com.high.theone.midi.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Extension to AudioEngineControl interface that adds MIDI-specific functionality.
 * This interface provides MIDI-triggered sample playback, real-time parameter control,
 * and integration with the existing audio engine.
 */
interface MidiAudioEngineControl : AudioEngineControl {
    
    // MIDI-triggered sample playback
    
    /**
     * Trigger a sample from MIDI input with velocity sensitivity
     * @param padIndex The pad index to trigger (0-15)
     * @param velocity MIDI velocity (0-127)
     * @param midiNote The MIDI note number that triggered this
     * @param midiChannel The MIDI channel (0-15)
     * @return True if sample was successfully triggered
     */
    suspend fun triggerSampleFromMidi(
        padIndex: Int,
        velocity: Int,
        midiNote: Int,
        midiChannel: Int
    ): Boolean
    
    /**
     * Stop a sample triggered by MIDI note off
     * @param padIndex The pad index to stop
     * @param midiNote The MIDI note number
     * @param midiChannel The MIDI channel (0-15)
     * @param releaseTimeMs Optional release time in milliseconds
     */
    suspend fun stopSampleFromMidi(
        padIndex: Int,
        midiNote: Int,
        midiChannel: Int,
        releaseTimeMs: Float? = null
    ): Boolean
    
    // MIDI parameter control
    
    /**
     * Set a parameter value from MIDI control change message
     * @param parameterId The parameter identifier (e.g., "pad_0_volume", "master_volume")
     * @param value The normalized parameter value (0.0-1.0)
     * @param midiController The MIDI controller number (0-127)
     * @param midiChannel The MIDI channel (0-15)
     */
    suspend fun setParameterFromMidi(
        parameterId: String,
        value: Float,
        midiController: Int,
        midiChannel: Int
    )
    
    /**
     * Set pad volume from MIDI input
     * @param padIndex The pad index (0-15)
     * @param volume Volume level (0.0-1.0)
     * @param midiChannel The MIDI channel (0-15)
     */
    suspend fun setPadVolumeFromMidi(
        padIndex: Int,
        volume: Float,
        midiChannel: Int
    )
    
    /**
     * Set pad pan from MIDI input
     * @param padIndex The pad index (0-15)
     * @param pan Pan position (-1.0 to 1.0)
     * @param midiChannel The MIDI channel (0-15)
     */
    suspend fun setPadPanFromMidi(
        padIndex: Int,
        pan: Float,
        midiChannel: Int
    )
    
    /**
     * Set master volume from MIDI input
     * @param volume Volume level (0.0-1.0)
     * @param midiChannel The MIDI channel (0-15)
     */
    suspend fun setMasterVolumeFromMidi(
        volume: Float,
        midiChannel: Int
    )
    
    // MIDI clock synchronization
    
    /**
     * Synchronize audio engine to external MIDI clock
     * @param clockPulse MIDI clock pulse information
     */
    suspend fun syncToMidiClock(clockPulse: MidiClockPulse)
    
    /**
     * Handle MIDI transport messages (Start, Stop, Continue)
     * @param message The transport message type
     * @param songPosition Optional song position for Continue message
     */
    suspend fun handleMidiTransport(
        message: MidiTransportMessage,
        songPosition: Int? = null
    )
    
    /**
     * Enable or disable external MIDI clock synchronization
     * @param enabled True to sync to external clock, false for internal clock
     */
    suspend fun setMidiClockSyncEnabled(enabled: Boolean)
    
    /**
     * Set the MIDI clock source for synchronization
     * @param source The clock source type
     * @param deviceId Optional device ID for external device clock
     */
    suspend fun setMidiClockSource(
        source: MidiClockSource,
        deviceId: String? = null
    )
    
    // MIDI note mapping
    
    /**
     * Map a MIDI note to a specific pad
     * @param midiNote The MIDI note number (0-127)
     * @param midiChannel The MIDI channel (0-15)
     * @param padIndex The target pad index (0-15)
     */
    suspend fun mapMidiNoteToPad(
        midiNote: Int,
        midiChannel: Int,
        padIndex: Int
    )
    
    /**
     * Remove MIDI note mapping
     * @param midiNote The MIDI note number (0-127)
     * @param midiChannel The MIDI channel (0-15)
     */
    suspend fun removeMidiNoteMapping(
        midiNote: Int,
        midiChannel: Int
    )
    
    /**
     * Get current MIDI note mappings
     * @return Map of (note, channel) pairs to pad indices
     */
    suspend fun getMidiNoteMappings(): Map<Pair<Int, Int>, Int>
    
    // MIDI velocity curves
    
    /**
     * Set velocity curve for MIDI input
     * @param curve The velocity curve type
     * @param sensitivity Velocity sensitivity (0.0-2.0, 1.0 = linear)
     */
    suspend fun setMidiVelocityCurve(
        curve: MidiCurve,
        sensitivity: Float = 1.0f
    )
    
    /**
     * Apply velocity curve to MIDI velocity value
     * @param velocity Raw MIDI velocity (0-127)
     * @return Processed velocity (0.0-1.0)
     */
    suspend fun applyVelocityCurve(velocity: Int): Float
    
    // MIDI monitoring and diagnostics
    
    /**
     * Get MIDI processing statistics
     * @return Statistics about MIDI message processing
     */
    suspend fun getMidiProcessingStats(): MidiStatistics
    
    /**
     * Enable or disable MIDI message monitoring
     * @param enabled True to enable monitoring
     */
    suspend fun setMidiMonitoringEnabled(enabled: Boolean)
    
    /**
     * Get flow of processed MIDI messages for monitoring
     * @return Flow of MIDI messages being processed
     */
    fun getMidiMessageFlow(): Flow<MidiMessage>
    
    // MIDI latency compensation
    
    /**
     * Set MIDI input latency compensation
     * @param latencyMs Latency compensation in milliseconds
     */
    suspend fun setMidiInputLatency(latencyMs: Float)
    
    /**
     * Get current MIDI input latency
     * @return Current latency compensation in milliseconds
     */
    suspend fun getMidiInputLatency(): Float
    
    /**
     * Measure and calibrate MIDI input latency
     * @return Measured latency in milliseconds
     */
    suspend fun calibrateMidiLatency(): Float
}