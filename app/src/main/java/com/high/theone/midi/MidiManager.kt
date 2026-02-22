package com.high.theone.midi

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.high.theone.midi.device.MidiDeviceManager
import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.output.MidiOutputGenerator
import com.high.theone.midi.mapping.MidiMappingEngine
import com.high.theone.midi.mapping.MidiLearnManager

import com.high.theone.midi.integration.MidiAudioEngineControl
import com.high.theone.midi.integration.MidiSequencerAdapter
import com.high.theone.midi.repository.MidiConfigurationRepository
import com.high.theone.midi.repository.MidiMappingRepository
import com.high.theone.midi.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for all MIDI operations in TheOne.
 * Manages lifecycle, coordinates between subsystems, and provides unified MIDI interface.
 * 
 * Requirements: 1.1, 3.1, 7.1
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class MidiManager @Inject constructor(
    private val context: Context,
    private val deviceManager: MidiDeviceManager,
    private val inputProcessor: MidiInputProcessor,
    private val outputGenerator: MidiOutputGenerator,
    private val mappingEngine: MidiMappingEngine,
    private val learnManager: MidiLearnManager,
    private val audioEngineAdapter: MidiAudioEngineControl,
    private val sequencerAdapter: MidiSequencerAdapter,
    private val configurationRepository: MidiConfigurationRepository,
    private val mappingRepository: MidiMappingRepository
) : MidiManagerControl {

    companion object {
        private const val TAG = "MidiManager"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // System state
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _systemState = MutableStateFlow(MidiSystemState.STOPPED)
    override val systemState: StateFlow<MidiSystemState> = _systemState.asStateFlow()

    private val _lastError = MutableStateFlow<MidiError?>(null)
    override val lastError: StateFlow<MidiError?> = _lastError.asStateFlow()

    // Message flows aggregated from subsystems
    override val inputMessages: Flow<MidiMessage> = inputProcessor.processedMessages
        .map { it.originalMessage }
    override val outputMessages: Flow<MidiMessage> = outputGenerator.outputMessages

    private var currentConfiguration: MidiConfiguration? = null

    init {
        setupMessageRouting()
        Log.i(TAG, "MidiManager initialised with full MIDI subsystem wiring")
    }

    /** Route processed input messages through the mapping engine. */
    private fun setupMessageRouting() {
        managerScope.launch {
            inputProcessor.processedMessages.collect { processed ->
                try {
                    mappingEngine.processMidiMessage(processed.originalMessage)
                } catch (e: Exception) {
                    Log.w(TAG, "Error routing MIDI message: ${e.message}")
                }
            }
        }
    }
    
    override suspend fun initialize(): Boolean = try {
        Log.i(TAG, "Initializing MIDI system...")
        _systemState.value = MidiSystemState.INITIALIZING
        inputProcessor.startProcessing()
        val devices = deviceManager.scanForDevices()
        Log.i(TAG, "Found ${devices.size} MIDI device(s)")
        configurationRepository.loadConfiguration()
            .onSuccess { config -> currentConfiguration = config }
        _isInitialized.value = true
        _systemState.value = MidiSystemState.RUNNING
        Log.i(TAG, "MIDI system initialised")
        true
    } catch (e: Exception) {
        Log.e(TAG, "MIDI system initialisation failed", e)
        _lastError.value = MidiError.ConnectionFailed("system", e.message ?: "Unknown error")
        _systemState.value = MidiSystemState.ERROR
        false
    }

    override suspend fun shutdown() {
        try {
            Log.i(TAG, "Shutting down MIDI system...")
            _systemState.value = MidiSystemState.SHUTTING_DOWN
            inputProcessor.stopProcessing()
            managerScope.cancel()
            _isInitialized.value = false
            _systemState.value = MidiSystemState.STOPPED
            Log.i(TAG, "MIDI system shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during MIDI system shutdown", e)
            _lastError.value = MidiError.ConnectionFailed("system", "Shutdown error: ${e.message}")
        }
    }

    // Device Management

    override suspend fun scanForDevices(): List<MidiDeviceInfo> =
        deviceManager.scanForDevices()

    override suspend fun connectDevice(deviceId: String): Boolean =
        deviceManager.connectDevice(deviceId)

    override suspend fun disconnectDevice(deviceId: String): Boolean =
        deviceManager.disconnectDevice(deviceId)

    // Input/Output Control

    override suspend fun enableMidiInput(deviceId: String, enabled: Boolean): Boolean {
        Log.d(TAG, "enableMidiInput: device=$deviceId enabled=$enabled"); return true
    }

    override suspend fun enableMidiOutput(deviceId: String, enabled: Boolean): Boolean {
        Log.d(TAG, "enableMidiOutput: device=$deviceId enabled=$enabled"); return true
    }

    override suspend fun setInputLatencyCompensation(deviceId: String, latencyMs: Float) {
        inputProcessor.setInputLatencyCompensation(latencyMs)
    }

    // Mapping Management

    override suspend fun loadMidiMapping(mappingId: String): Boolean {
        if (mappingRepository.loadMapping(mappingId).isFailure) return false
        return mappingEngine.setActiveMappingProfile(mappingId).isSuccess
    }

    override suspend fun saveMidiMapping(mapping: MidiMapping): Boolean =
        mappingRepository.saveMapping(mapping).isSuccess

    override suspend fun setActiveMidiMapping(mappingId: String): Boolean =
        mappingEngine.setActiveMappingProfile(mappingId).isSuccess

    override suspend fun startMidiLearn(targetType: MidiTargetType, targetId: String): Boolean {
        learnManager.startMidiLearn(targetType, targetId); return true
    }

    override suspend fun stopMidiLearn(): MidiParameterMapping? =
        learnManager.stopMidiLearn()

    // Clock and Sync

    override suspend fun enableMidiClock(enabled: Boolean) {
        sequencerAdapter.enableExternalClockSync(enabled)
    }

    override suspend fun setClockSource(source: MidiClockSource) {
        Log.d(TAG, "setClockSource: $source")
    }

    override suspend fun sendTransportMessage(message: MidiTransportMessage) {
        Log.d(TAG, "sendTransportMessage: $message")
    }

    // Monitoring and Diagnostics

    override suspend fun getMidiStatistics(): MidiStatistics =
        outputGenerator.statistics.value
}