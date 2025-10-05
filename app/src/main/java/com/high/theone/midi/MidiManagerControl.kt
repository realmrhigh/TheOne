package com.high.theone.midi

import com.high.theone.midi.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for controlling the MIDI system.
 * Provides methods for device management, mapping configuration, and system control.
 */
interface MidiManagerControl {
    
    // System state
    val isInitialized: StateFlow<Boolean>
    val systemState: StateFlow<MidiSystemState>
    val lastError: StateFlow<MidiError?>
    
    // Message flows
    val inputMessages: Flow<MidiMessage>
    val outputMessages: Flow<MidiMessage>
    
    // System lifecycle
    suspend fun initialize(): Boolean
    suspend fun shutdown()
    
    // Device Management
    suspend fun scanForDevices(): List<MidiDeviceInfo>
    suspend fun connectDevice(deviceId: String): Boolean
    suspend fun disconnectDevice(deviceId: String): Boolean
    
    // Input/Output Control
    suspend fun enableMidiInput(deviceId: String, enabled: Boolean): Boolean
    suspend fun enableMidiOutput(deviceId: String, enabled: Boolean): Boolean
    suspend fun setInputLatencyCompensation(deviceId: String, latencyMs: Float)
    
    // Mapping Management
    suspend fun loadMidiMapping(mappingId: String): Boolean
    suspend fun saveMidiMapping(mapping: MidiMapping): Boolean
    suspend fun setActiveMidiMapping(mappingId: String): Boolean
    suspend fun startMidiLearn(targetType: MidiTargetType, targetId: String): Boolean
    suspend fun stopMidiLearn(): MidiParameterMapping?
    
    // Clock and Sync
    suspend fun enableMidiClock(enabled: Boolean)
    suspend fun setClockSource(source: MidiClockSource)
    suspend fun sendTransportMessage(message: MidiTransportMessage)
    
    // Monitoring and Diagnostics
    suspend fun getMidiStatistics(): MidiStatistics
}

/**
 * Represents the overall state of the MIDI system
 */
enum class MidiSystemState {
    STOPPED,
    INITIALIZING,
    RUNNING,
    ERROR,
    SHUTTING_DOWN
}

/**
 * MIDI clock source options
 */
enum class MidiClockSource {
    INTERNAL,
    EXTERNAL_AUTO,
    EXTERNAL_DEVICE
}

/**
 * MIDI transport message types
 */
enum class MidiTransportMessage {
    START,
    STOP,
    CONTINUE,
    SONG_POSITION
}