package com.high.theone.midi.output

import com.high.theone.midi.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls MIDI transport messages (Start, Stop, Continue) and song position.
 * Provides synchronization with sequencer and external devices.
 */
@Singleton
class MidiTransportController @Inject constructor(
    private val outputGenerator: MidiOutputGenerator,
    private val clockGenerator: MidiClockGenerator
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Transport state
    private val _transportState = MutableStateFlow(MidiTransportState.STOPPED)
    val transportState: StateFlow<MidiTransportState> = _transportState.asStateFlow()
    
    // Song position in MIDI beats (16th notes)
    private val _songPosition = MutableStateFlow(0)
    val songPosition: StateFlow<Int> = _songPosition.asStateFlow()
    
    // Transport events for monitoring
    private val _transportEvents = MutableSharedFlow<MidiTransportEvent>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val transportEvents: SharedFlow<MidiTransportEvent> = _transportEvents.asSharedFlow()
    
    // Sequencer integration callbacks
    private var sequencerStartCallback: (() -> Unit)? = null
    private var sequencerStopCallback: (() -> Unit)? = null
    private var sequencerContinueCallback: (() -> Unit)? = null
    private var sequencerPositionCallback: ((Int) -> Unit)? = null
    
    /**
     * Starts transport and begins clock generation
     */
    suspend fun start(bpm: Float = 120.0f): Result<Unit> {
        return try {
            // Send MIDI Start message
            val startMessage = MidiMessage(
                type = MidiMessageType.START,
                channel = 0,
                data1 = 0,
                data2 = 0,
                timestamp = System.nanoTime()
            )
            
            outputGenerator.sendMessage(startMessage).getOrThrow()
            
            // Start clock generation
            clockGenerator.startClock(bpm).getOrThrow()
            
            // Update state
            _transportState.value = MidiTransportState.PLAYING
            _songPosition.value = 0
            
            // Notify sequencer
            sequencerStartCallback?.invoke()
            
            // Emit transport event
            val event = MidiTransportEvent(
                type = MidiTransportMessage.START,
                timestamp = System.nanoTime(),
                songPosition = 0,
                bpm = bpm
            )
            _transportEvents.tryEmit(event)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to start transport: ${e.message}"))
        }
    }
    
    /**
     * Stops transport and clock generation
     */
    suspend fun stop(): Result<Unit> {
        return try {
            // Send MIDI Stop message
            val stopMessage = MidiMessage(
                type = MidiMessageType.STOP,
                channel = 0,
                data1 = 0,
                data2 = 0,
                timestamp = System.nanoTime()
            )
            
            outputGenerator.sendMessage(stopMessage).getOrThrow()
            
            // Stop clock generation
            clockGenerator.stopClock().getOrThrow()
            
            // Update state
            _transportState.value = MidiTransportState.STOPPED
            
            // Notify sequencer
            sequencerStopCallback?.invoke()
            
            // Emit transport event
            val event = MidiTransportEvent(
                type = MidiTransportMessage.STOP,
                timestamp = System.nanoTime(),
                songPosition = _songPosition.value,
                bpm = clockGenerator.getCurrentTempo()
            )
            _transportEvents.tryEmit(event)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to stop transport: ${e.message}"))
        }
    }
    
    /**
     * Continues transport from current position
     */
    suspend fun continueTransport(): Result<Unit> {
        return try {
            if (_transportState.value != MidiTransportState.PAUSED) {
                return Result.failure(Exception("Can only continue from paused state"))
            }
            
            // Send MIDI Continue message
            val continueMessage = MidiMessage(
                type = MidiMessageType.CONTINUE,
                channel = 0,
                data1 = 0,
                data2 = 0,
                timestamp = System.nanoTime()
            )
            
            outputGenerator.sendMessage(continueMessage).getOrThrow()
            
            // Resume clock generation
            clockGenerator.startClock(clockGenerator.getCurrentTempo()).getOrThrow()
            
            // Update state
            _transportState.value = MidiTransportState.PLAYING
            
            // Notify sequencer
            sequencerContinueCallback?.invoke()
            
            // Emit transport event
            val event = MidiTransportEvent(
                type = MidiTransportMessage.CONTINUE,
                timestamp = System.nanoTime(),
                songPosition = _songPosition.value,
                bpm = clockGenerator.getCurrentTempo()
            )
            _transportEvents.tryEmit(event)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to continue transport: ${e.message}"))
        }
    }
    
    /**
     * Pauses transport (stops clock but maintains position)
     */
    suspend fun pause(): Result<Unit> {
        return try {
            if (_transportState.value != MidiTransportState.PLAYING) {
                return Result.failure(Exception("Can only pause from playing state"))
            }
            
            // Stop clock but don't send MIDI Stop (use internal pause)
            clockGenerator.stopClock().getOrThrow()
            
            // Update state
            _transportState.value = MidiTransportState.PAUSED
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to pause transport: ${e.message}"))
        }
    }
    
    /**
     * Sets song position in MIDI beats (16th notes)
     */
    suspend fun setSongPosition(position: Int): Result<Unit> {
        return try {
            require(position >= 0) { "Song position cannot be negative" }
            
            // Send Song Position Pointer message
            // MIDI song position is in 16th notes, split into 14-bit value
            val lsb = position and 0x7F
            val msb = (position shr 7) and 0x7F
            
            val positionMessage = MidiMessage(
                type = MidiMessageType.SYSTEM_EXCLUSIVE, // Using SysEx type for song position
                channel = 0,
                data1 = lsb,
                data2 = msb,
                timestamp = System.nanoTime()
            )
            
            // Send raw song position pointer bytes
            val songPositionBytes = byteArrayOf(
                0xF2.toByte(), // Song Position Pointer status
                lsb.toByte(),
                msb.toByte()
            )
            
            // Update internal position
            _songPosition.value = position
            
            // Notify sequencer
            sequencerPositionCallback?.invoke(position)
            
            // Emit transport event
            val event = MidiTransportEvent(
                type = MidiTransportMessage.SONG_POSITION,
                timestamp = System.nanoTime(),
                songPosition = position,
                bpm = clockGenerator.getCurrentTempo()
            )
            _transportEvents.tryEmit(event)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set song position: ${e.message}"))
        }
    }
    
    /**
     * Advances song position by one MIDI beat (16th note)
     */
    suspend fun advanceSongPosition(): Result<Unit> {
        return setSongPosition(_songPosition.value + 1)
    }
    
    /**
     * Resets song position to beginning
     */
    suspend fun resetSongPosition(): Result<Unit> {
        return setSongPosition(0)
    }
    
    /**
     * Sets the tempo and updates clock generator
     */
    suspend fun setTempo(bpm: Float): Result<Unit> {
        return try {
            clockGenerator.setTempo(bpm).getOrThrow()
            
            // Emit tempo change event
            val event = MidiTransportEvent(
                type = MidiTransportMessage.START, // Using START type for tempo changes
                timestamp = System.nanoTime(),
                songPosition = _songPosition.value,
                bpm = bpm
            )
            _transportEvents.tryEmit(event)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set tempo: ${e.message}"))
        }
    }
    
    /**
     * Gets current transport state
     */
    fun getCurrentState(): MidiTransportState = _transportState.value
    
    /**
     * Gets current song position
     */
    fun getCurrentPosition(): Int = _songPosition.value
    
    /**
     * Gets current tempo
     */
    fun getCurrentTempo(): Float = clockGenerator.getCurrentTempo()
    
    /**
     * Checks if transport is currently playing
     */
    fun isPlaying(): Boolean = _transportState.value == MidiTransportState.PLAYING
    
    /**
     * Checks if transport is currently stopped
     */
    fun isStopped(): Boolean = _transportState.value == MidiTransportState.STOPPED
    
    /**
     * Checks if transport is currently paused
     */
    fun isPaused(): Boolean = _transportState.value == MidiTransportState.PAUSED
    
    /**
     * Sets callback for sequencer start events
     */
    fun setSequencerStartCallback(callback: () -> Unit) {
        sequencerStartCallback = callback
    }
    
    /**
     * Sets callback for sequencer stop events
     */
    fun setSequencerStopCallback(callback: () -> Unit) {
        sequencerStopCallback = callback
    }
    
    /**
     * Sets callback for sequencer continue events
     */
    fun setSequencerContinueCallback(callback: () -> Unit) {
        sequencerContinueCallback = callback
    }
    
    /**
     * Sets callback for sequencer position changes
     */
    fun setSequencerPositionCallback(callback: (Int) -> Unit) {
        sequencerPositionCallback = callback
    }
    
    /**
     * Handles external transport messages (from MIDI input)
     */
    suspend fun handleExternalTransportMessage(message: MidiMessage): Result<Unit> {
        return try {
            when (message.type) {
                MidiMessageType.START -> {
                    start(clockGenerator.getCurrentTempo())
                }
                MidiMessageType.STOP -> {
                    stop()
                }
                MidiMessageType.CONTINUE -> {
                    continue()
                }
                else -> {
                    Result.success(Unit) // Ignore other message types
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to handle external transport message: ${e.message}"))
        }
    }
    
    /**
     * Synchronizes with external clock pulses
     */
    suspend fun syncWithExternalClock(clockPulse: MidiClockPulse): Result<Unit> {
        return try {
            // Update song position based on clock pulses
            // Assuming 24 pulses per quarter note (standard MIDI clock)
            val pulsesPerBeat = 6 // 24 pulses per quarter note / 4 = 6 pulses per 16th note
            val newPosition = clockPulse.pulseNumber / pulsesPerBeat
            
            if (newPosition != _songPosition.value) {
                _songPosition.value = newPosition
                sequencerPositionCallback?.invoke(newPosition)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to sync with external clock: ${e.message}"))
        }
    }
    
    /**
     * Cleans up resources
     */
    fun shutdown() {
        scope.launch {
            if (_transportState.value == MidiTransportState.PLAYING) {
                stop()
            }
        }
        scope.cancel()
    }
}

/**
 * MIDI transport states
 */
enum class MidiTransportState {
    STOPPED,
    PLAYING,
    PAUSED
}

/**
 * Transport event for monitoring and logging
 */
data class MidiTransportEvent(
    val type: MidiTransportMessage,
    val timestamp: Long,
    val songPosition: Int,
    val bpm: Float
)