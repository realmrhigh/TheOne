package com.high.theone.midi.integration

import com.high.theone.features.sequencer.*
import com.high.theone.midi.model.*
import com.high.theone.midi.output.MidiClockGenerator
import com.high.theone.midi.output.MidiTransportController
import com.high.theone.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Adapter that integrates MIDI functionality with the step sequencer.
 * Handles MIDI input recording to patterns, external clock synchronization,
 * and MIDI transport control for sequencer playback.
 */
@Singleton
class MidiSequencerAdapter @Inject constructor(
    private val timingEngine: TimingEngine,
    private val recordingEngine: RecordingEngine,
    private val midiClockGenerator: MidiClockGenerator,
    private val midiTransportController: MidiTransportController,
    private val patternManager: PatternManager
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    
    // MIDI sequencer state
    private val _midiSequencerState = MutableStateFlow(MidiSequencerState())
    val midiSequencerState: StateFlow<MidiSequencerState> = _midiSequencerState.asStateFlow()
    
    // External clock synchronization
    private var externalClockJob: Job? = null
    private var lastClockPulseTime = 0L
    private var clockPulseCount = 0
    
    // MIDI input recording
    private var recordingJob: Job? = null
    private val recordedMidiEvents = mutableListOf<RecordedMidiEvent>()
    
    /**
     * Initialize the MIDI sequencer adapter
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            // Set up timing engine callbacks for MIDI clock output
            timingEngine.scheduleStepCallback { step, microTime ->
                scope.launch {
                    handleStepCallback(step, microTime)
                }
            }
            
            // Set up pattern completion callback
            timingEngine.schedulePatternCompleteCallback {
                scope.launch {
                    handlePatternComplete()
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to initialize MIDI sequencer adapter: ${e.message}"))
        }
    }
    
    // MIDI Input Recording
    
    /**
     * Start recording MIDI input to the current pattern
     */
    suspend fun startMidiRecording(
        pattern: Pattern,
        recordingMode: RecordingMode = RecordingMode.REPLACE,
        quantization: Quantization = Quantization.SIXTEENTH,
        selectedPads: Set<Int> = emptySet()
    ): Result<Unit> = stateMutex.withLock {
        return try {
            // Start the recording engine
            recordingEngine.startRecording(pattern, recordingMode, quantization, selectedPads)
            
            // Clear previous recorded MIDI events
            recordedMidiEvents.clear()
            
            // Start MIDI input monitoring
            recordingJob = scope.launch {
                monitorMidiInputForRecording()
            }
            
            _midiSequencerState.update { 
                it.copy(
                    isMidiRecording = true,
                    recordingPattern = pattern.id,
                    recordingMode = recordingMode,
                    recordingQuantization = quantization
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to start MIDI recording: ${e.message}"))
        }
    }
    
    /**
     * Stop MIDI recording and return the updated pattern
     */
    suspend fun stopMidiRecording(): Result<Pattern?> = stateMutex.withLock {
        return try {
            recordingJob?.cancel()
            recordingJob = null
            
            // Stop the recording engine and get the updated pattern
            val updatedPattern = recordingEngine.stopRecording()
            
            _midiSequencerState.update { 
                it.copy(
                    isMidiRecording = false,
                    recordingPattern = null
                )
            }
            
            Result.success(updatedPattern)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to stop MIDI recording: ${e.message}"))
        }
    }
    
    /**
     * Process incoming MIDI message for recording
     */
    suspend fun processMidiInputForRecording(message: MidiMessage) {
        if (!_midiSequencerState.value.isMidiRecording) return
        
        when (message.type) {
            MidiMessageType.NOTE_ON -> {
                if (message.data2 > 0) { // Velocity > 0
                    handleMidiNoteOnForRecording(message)
                }
            }
            MidiMessageType.NOTE_OFF -> {
                handleMidiNoteOffForRecording(message)
            }
            else -> {
                // Other MIDI messages can be recorded as automation
                recordMidiEvent(message)
            }
        }
    }
    
    /**
     * Handle MIDI Note On for recording
     */
    private suspend fun handleMidiNoteOnForRecording(message: MidiMessage) {
        // Map MIDI note to pad index (this could be configurable)
        val padIndex = mapMidiNoteToPad(message.data1, message.channel)
        if (padIndex == -1) return
        
        // Get current timing information
        val currentStep = timingEngine.getCurrentStep()
        val stepProgress = timingEngine.getStepProgress()
        val state = _midiSequencerState.value
        
        // Record the pad hit through the recording engine
        recordingEngine.recordPadHit(
            padIndex = padIndex,
            velocity = message.data2,
            timestamp = message.timestamp,
            currentStep = currentStep,
            stepProgress = stepProgress,
            tempo = state.currentTempo,
            swing = state.currentSwing
        )
        
        // Store the MIDI event for potential playback
        recordMidiEvent(message)
    }
    
    /**
     * Handle MIDI Note Off for recording
     */
    private suspend fun handleMidiNoteOffForRecording(message: MidiMessage) {
        // For now, we don't need to do anything special with note off during recording
        // But we store it for completeness
        recordMidiEvent(message)
    }
    
    /**
     * Record a MIDI event for potential playback
     */
    private fun recordMidiEvent(message: MidiMessage) {
        val currentStep = timingEngine.getCurrentStep()
        val stepProgress = timingEngine.getStepProgress()
        
        val recordedEvent = RecordedMidiEvent(
            message = message,
            stepPosition = currentStep + stepProgress,
            recordingTimestamp = System.currentTimeMillis()
        )
        
        recordedMidiEvents.add(recordedEvent)
    }
    
    /**
     * Monitor MIDI input during recording
     */
    private suspend fun monitorMidiInputForRecording() {
        // This would be connected to the MIDI input processor
        // For now, it's a placeholder that would be called by the MIDI input system
        while (currentCoroutineContext().isActive) {
            delay(1) // Minimal delay to prevent tight loop
        }
    }
    
    // External Clock Synchronization
    
    /**
     * Enable external MIDI clock synchronization
     */
    suspend fun enableExternalClockSync(enabled: Boolean): Result<Unit> {
        return try {
            val clockSource = if (enabled) ClockSource.EXTERNAL else ClockSource.INTERNAL
            timingEngine.setClockSource(clockSource)
            
            if (enabled) {
                startExternalClockSync()
            } else {
                stopExternalClockSync()
            }
            
            _midiSequencerState.update { 
                it.copy(externalClockEnabled = enabled)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to ${if (enabled) "enable" else "disable"} external clock sync: ${e.message}"))
        }
    }
    
    /**
     * Process incoming MIDI clock pulse
     */
    suspend fun processMidiClockPulse(timestamp: Long) {
        if (!_midiSequencerState.value.externalClockEnabled) return
        
        val currentTime = System.nanoTime()
        clockPulseCount++
        
        // Create MIDI clock pulse for timing engine
        val clockPulse = MidiClockPulse(
            timestamp = timestamp,
            pulseNumber = clockPulseCount,
            bpm = _midiSequencerState.value.currentTempo
        )
        
        // Send to timing engine for synchronization
        timingEngine.processExternalClockPulse(clockPulse)
        
        // Update sync status
        val isNowSynced = timingEngine.isExternalClockSynced()
        _midiSequencerState.update { 
            it.copy(
                clockSyncStatus = if (isNowSynced) ClockSyncStatus.SYNCED else ClockSyncStatus.SYNCING
            )
        }
        
        lastClockPulseTime = currentTime
    }
    
    /**
     * Process MIDI transport messages
     */
    suspend fun processMidiTransportMessage(message: MidiTransportMessage) {
        when (message) {
            MidiTransportMessage.START -> {
                handleMidiStart()
            }
            MidiTransportMessage.STOP -> {
                handleMidiStop()
            }
            MidiTransportMessage.CONTINUE -> {
                handleMidiContinue()
            }
            MidiTransportMessage.SONG_POSITION -> {
                // Handle song position pointer if needed
            }
        }
    }
    
    /**
     * Start external clock synchronization
     */
    private suspend fun startExternalClockSync() {
        externalClockJob = scope.launch {
            monitorExternalClock()
        }
        
        clockPulseCount = 0
        lastClockPulseTime = 0L
    }
    
    /**
     * Stop external clock synchronization
     */
    private suspend fun stopExternalClockSync() {
        externalClockJob?.cancel()
        externalClockJob = null
        
        _midiSequencerState.update { 
            it.copy(clockSyncStatus = ClockSyncStatus.DISCONNECTED)
        }
    }
    
    /**
     * Monitor external clock for synchronization
     */
    private suspend fun monitorExternalClock() {
        while (currentCoroutineContext().isActive) {
            // Check for clock timeout (no clock received for 1 second)
            val currentTime = System.nanoTime()
            if (lastClockPulseTime > 0 && (currentTime - lastClockPulseTime) > 1_000_000_000L) {
                // Clock timeout - fall back to internal clock
                _midiSequencerState.update { 
                    it.copy(
                        externalClockEnabled = false,
                        clockSyncStatus = ClockSyncStatus.TIMEOUT
                    )
                }
                break
            }
            
            delay(100) // Check every 100ms
        }
    }
    

    
    // MIDI Transport Control
    
    /**
     * Handle MIDI Start message
     */
    private suspend fun handleMidiStart() {
        if (_midiSequencerState.value.externalClockEnabled) {
            val state = _midiSequencerState.value
            timingEngine.start(
                tempo = state.currentTempo,
                swing = state.currentSwing,
                patternLength = state.currentPatternLength
            )
            
            _midiSequencerState.update { 
                it.copy(transportState = TransportState.PLAYING)
            }
        }
    }
    
    /**
     * Handle MIDI Stop message
     */
    private suspend fun handleMidiStop() {
        if (_midiSequencerState.value.externalClockEnabled) {
            timingEngine.stop()
            
            _midiSequencerState.update { 
                it.copy(transportState = TransportState.STOPPED)
            }
        }
    }
    
    /**
     * Handle MIDI Continue message
     */
    private suspend fun handleMidiContinue() {
        if (_midiSequencerState.value.externalClockEnabled) {
            timingEngine.resume()
            
            _midiSequencerState.update { 
                it.copy(transportState = TransportState.PLAYING)
            }
        }
    }
    
    // Internal Callbacks
    
    /**
     * Handle step callback from timing engine
     */
    private suspend fun handleStepCallback(step: Int, microTime: Long) {
        // Send MIDI clock if we're the master
        if (_midiSequencerState.value.isMidiClockMaster) {
            // Send 6 clock pulses per step (24 per quarter note, 4 steps per quarter note)
            repeat(6) { pulseIndex ->
                val pulseTime = microTime + (pulseIndex * 1000) // Spread pulses over the step
                midiClockGenerator.startClock(_midiSequencerState.value.currentTempo)
            }
        }
        
        _midiSequencerState.update { 
            it.copy(currentStep = step)
        }
    }
    
    /**
     * Handle pattern completion callback
     */
    private suspend fun handlePatternComplete() {
        // Reset clock pulse count for tempo detection
        clockPulseCount = 0
    }
    
    // Utility Functions
    
    /**
     * Map MIDI note to pad index (configurable mapping)
     */
    private fun mapMidiNoteToPad(midiNote: Int, midiChannel: Int): Int {
        // Default mapping: C3 (60) = pad 0, C#3 (61) = pad 1, etc.
        // This could be made configurable through MIDI mapping system
        return when {
            midiNote in 60..75 -> midiNote - 60 // C3 to D#4 maps to pads 0-15
            midiNote in 36..51 -> midiNote - 36 // C1 to D#2 maps to pads 0-15 (drum mapping)
            else -> -1 // No mapping
        }
    }
    
    /**
     * Set current pattern for MIDI operations
     */
    suspend fun setCurrentPattern(pattern: Pattern) {
        _midiSequencerState.update { 
            it.copy(
                currentPatternId = pattern.id,
                currentPatternLength = pattern.length,
                currentTempo = pattern.tempo,
                currentSwing = pattern.swing
            )
        }
    }
    
    /**
     * Enable/disable MIDI clock master mode
     */
    suspend fun setMidiClockMaster(enabled: Boolean): Result<Unit> {
        return try {
            if (enabled) {
                midiClockGenerator.startClock(_midiSequencerState.value.currentTempo)
            } else {
                midiClockGenerator.stopClock()
            }
            
            _midiSequencerState.update { 
                it.copy(isMidiClockMaster = enabled)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set MIDI clock master: ${e.message}"))
        }
    }
    
    /**
     * Get recorded MIDI events
     */
    fun getRecordedMidiEvents(): List<RecordedMidiEvent> = recordedMidiEvents.toList()
    
    /**
     * Clear recorded MIDI events
     */
    fun clearRecordedMidiEvents() {
        recordedMidiEvents.clear()
    }
    
    /**
     * Set tempo from MIDI input
     */
    suspend fun setTempoFromMidi(bpm: Float) {
        _midiSequencerState.update { 
            it.copy(currentTempo = bpm)
        }
        
        // Update timing engine if we're not synced to external clock
        if (!_midiSequencerState.value.externalClockEnabled) {
            timingEngine.setTempo(bpm)
        }
    }
    
    /**
     * Handle transport message from MIDI
     */
    suspend fun handleTransportMessage(message: MidiTransportMessage) {
        processMidiTransportMessage(message)
    }
    

    
    /**
     * Shutdown the adapter and clean up resources
     */
    fun shutdown() {
        scope.launch {
            stopMidiRecording()
            stopExternalClockSync()
            midiClockGenerator.stopClock()
        }
        scope.cancel()
    }
}

/**
 * State of the MIDI sequencer adapter
 */
data class MidiSequencerState(
    val isMidiRecording: Boolean = false,
    val recordingPattern: String? = null,
    val recordingMode: RecordingMode = RecordingMode.REPLACE,
    val recordingQuantization: Quantization = Quantization.SIXTEENTH,
    val externalClockEnabled: Boolean = false,
    val isMidiClockMaster: Boolean = false,
    val clockSyncStatus: ClockSyncStatus = ClockSyncStatus.DISCONNECTED,
    val transportState: TransportState = TransportState.STOPPED,
    val currentPatternId: String? = null,
    val currentPatternLength: Int = 16,
    val currentTempo: Float = 120.0f,
    val currentSwing: Float = 0.0f,
    val currentStep: Int = 0
)

/**
 * Status of external clock synchronization
 */
enum class ClockSyncStatus {
    DISCONNECTED,
    SYNCING,
    SYNCED,
    TIMEOUT
}

/**
 * Transport state for MIDI control
 */
enum class TransportState {
    STOPPED,
    PLAYING,
    PAUSED
}

/**
 * Recorded MIDI event with timing information
 */
data class RecordedMidiEvent(
    val message: MidiMessage,
    val stepPosition: Float, // Step position when recorded (with fractional part)
    val recordingTimestamp: Long // System timestamp when recorded
)