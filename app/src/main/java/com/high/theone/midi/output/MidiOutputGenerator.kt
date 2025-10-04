package com.high.theone.midi.output

import android.media.midi.MidiDevice
import android.media.midi.MidiInputPort
import com.high.theone.midi.MidiError
import com.high.theone.midi.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and sends MIDI output messages to connected devices.
 * Handles message formatting, device routing, and timing synchronization.
 */
@Singleton
class MidiOutputGenerator @Inject constructor() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Connected output devices and their ports
    private val outputDevices = ConcurrentHashMap<String, MidiDevice>()
    private val outputPorts = ConcurrentHashMap<String, MidiInputPort>()
    
    // Message queue for timing synchronization
    private val messageQueue = Channel<TimedMidiMessage>(Channel.UNLIMITED)
    private val messageCounter = AtomicLong(0)
    
    // Output statistics
    private val _statistics = MutableStateFlow(
        MidiStatistics(
            inputMessageCount = 0,
            outputMessageCount = 0,
            averageInputLatency = 0f,
            droppedMessageCount = 0,
            lastErrorMessage = null
        )
    )
    val statistics: StateFlow<MidiStatistics> = _statistics.asStateFlow()
    
    // Output message flow for monitoring
    private val _outputMessages = MutableSharedFlow<MidiMessage>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    val outputMessages: SharedFlow<MidiMessage> = _outputMessages.asSharedFlow()
    
    private var isProcessing = false
    
    init {
        startMessageProcessor()
    }
    
    /**
     * Connects an output device for MIDI message sending
     */
    suspend fun connectOutputDevice(deviceId: String, device: MidiDevice): Result<Unit> {
        return try {
            val inputPort = device.openInputPort(0)
                ?: return Result.failure(MidiError.ConnectionFailed(deviceId, "Failed to open input port"))
            
            outputDevices[deviceId] = device
            outputPorts[deviceId] = inputPort
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiError.ConnectionFailed(deviceId, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Disconnects an output device
     */
    suspend fun disconnectOutputDevice(deviceId: String): Result<Unit> {
        return try {
            outputPorts[deviceId]?.close()
            outputDevices[deviceId]?.close()
            
            outputPorts.remove(deviceId)
            outputDevices.remove(deviceId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiError.ConnectionFailed(deviceId, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Sends a MIDI message immediately to all connected output devices
     */
    suspend fun sendMessage(message: MidiMessage): Result<Unit> {
        return sendMessageToDevice(message, null)
    }
    
    /**
     * Sends a MIDI message to a specific device
     */
    suspend fun sendMessageToDevice(message: MidiMessage, deviceId: String?): Result<Unit> {
        val timedMessage = TimedMidiMessage(
            message = message,
            targetDeviceId = deviceId,
            scheduledTime = System.nanoTime()
        )
        
        return try {
            messageQueue.trySend(timedMessage)
            Result.success(Unit)
        } catch (e: Exception) {
            updateStatistics { it.copy(droppedMessageCount = it.droppedMessageCount + 1) }
            Result.failure(MidiError.BufferOverflow(deviceId ?: "all"))
        }
    }
    
    /**
     * Sends a MIDI message at a specific time (for synchronization)
     */
    suspend fun sendMessageAtTime(message: MidiMessage, timestampNanos: Long, deviceId: String? = null): Result<Unit> {
        val timedMessage = TimedMidiMessage(
            message = message,
            targetDeviceId = deviceId,
            scheduledTime = timestampNanos
        )
        
        return try {
            messageQueue.trySend(timedMessage)
            Result.success(Unit)
        } catch (e: Exception) {
            updateStatistics { it.copy(droppedMessageCount = it.droppedMessageCount + 1) }
            Result.failure(MidiError.BufferOverflow(deviceId ?: "all"))
        }
    }
    
    /**
     * Sends a Note On message
     */
    suspend fun sendNoteOn(channel: Int, note: Int, velocity: Int, deviceId: String? = null): Result<Unit> {
        val message = MidiMessage(
            type = MidiMessageType.NOTE_ON,
            channel = channel,
            data1 = note,
            data2 = velocity,
            timestamp = System.nanoTime()
        )
        return sendMessageToDevice(message, deviceId)
    }
    
    /**
     * Sends a Note Off message
     */
    suspend fun sendNoteOff(channel: Int, note: Int, velocity: Int = 64, deviceId: String? = null): Result<Unit> {
        val message = MidiMessage(
            type = MidiMessageType.NOTE_OFF,
            channel = channel,
            data1 = note,
            data2 = velocity,
            timestamp = System.nanoTime()
        )
        return sendMessageToDevice(message, deviceId)
    }
    
    /**
     * Sends a Control Change message
     */
    suspend fun sendControlChange(channel: Int, controller: Int, value: Int, deviceId: String? = null): Result<Unit> {
        val message = MidiMessage(
            type = MidiMessageType.CONTROL_CHANGE,
            channel = channel,
            data1 = controller,
            data2 = value,
            timestamp = System.nanoTime()
        )
        return sendMessageToDevice(message, deviceId)
    }
    
    /**
     * Sends a Program Change message
     */
    suspend fun sendProgramChange(channel: Int, program: Int, deviceId: String? = null): Result<Unit> {
        val message = MidiMessage(
            type = MidiMessageType.PROGRAM_CHANGE,
            channel = channel,
            data1 = program,
            data2 = 0,
            timestamp = System.nanoTime()
        )
        return sendMessageToDevice(message, deviceId)
    }
    
    /**
     * Gets list of connected output devices
     */
    fun getConnectedDevices(): List<String> {
        return outputDevices.keys.toList()
    }
    
    /**
     * Checks if a device is connected
     */
    fun isDeviceConnected(deviceId: String): Boolean {
        return outputDevices.containsKey(deviceId)
    }
    
    /**
     * Starts the message processing coroutine
     */
    private fun startMessageProcessor() {
        if (isProcessing) return
        
        isProcessing = true
        scope.launch {
            for (timedMessage in messageQueue) {
                processTimedMessage(timedMessage)
            }
        }
    }
    
    /**
     * Processes a timed MIDI message, handling timing and device routing
     */
    private suspend fun processTimedMessage(timedMessage: TimedMidiMessage) {
        try {
            // Wait until the scheduled time
            val currentTime = System.nanoTime()
            val delayNanos = timedMessage.scheduledTime - currentTime
            if (delayNanos > 0) {
                delay(delayNanos / 1_000_000) // Convert to milliseconds
            }
            
            // Convert MIDI message to byte array
            val midiBytes = formatMidiMessage(timedMessage.message)
            
            // Send to target device(s)
            val targetDevices = if (timedMessage.targetDeviceId != null) {
                listOfNotNull(outputPorts[timedMessage.targetDeviceId])
            } else {
                outputPorts.values.toList()
            }
            
            for (port in targetDevices) {
                try {
                    port.send(midiBytes, 0, midiBytes.size)
                    messageCounter.incrementAndGet()
                } catch (e: Exception) {
                    updateStatistics { stats ->
                        stats.copy(
                            droppedMessageCount = stats.droppedMessageCount + 1,
                            lastErrorMessage = "Failed to send to device: ${e.message}"
                        )
                    }
                }
            }
            
            // Update statistics and emit message for monitoring
            updateStatistics { stats ->
                stats.copy(outputMessageCount = stats.outputMessageCount + 1)
            }
            _outputMessages.tryEmit(timedMessage.message)
            
        } catch (e: Exception) {
            updateStatistics { stats ->
                stats.copy(
                    droppedMessageCount = stats.droppedMessageCount + 1,
                    lastErrorMessage = "Message processing error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Formats a MIDI message into byte array for transmission
     */
    private fun formatMidiMessage(message: MidiMessage): ByteArray {
        return when (message.type) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF,
            MidiMessageType.CONTROL_CHANGE, MidiMessageType.AFTERTOUCH,
            MidiMessageType.PITCH_BEND -> {
                byteArrayOf(
                    (message.type.statusByte or message.channel).toByte(),
                    message.data1.toByte(),
                    message.data2.toByte()
                )
            }
            MidiMessageType.PROGRAM_CHANGE -> {
                byteArrayOf(
                    (message.type.statusByte or message.channel).toByte(),
                    message.data1.toByte()
                )
            }
            MidiMessageType.CLOCK, MidiMessageType.START,
            MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                byteArrayOf(message.type.statusByte.toByte())
            }
            MidiMessageType.SYSTEM_EXCLUSIVE -> {
                // SysEx messages would need special handling
                byteArrayOf(0xF0.toByte(), 0xF7.toByte()) // Empty SysEx for now
            }
        }
    }
    
    /**
     * Updates statistics in a thread-safe manner
     */
    private fun updateStatistics(update: (MidiStatistics) -> MidiStatistics) {
        _statistics.value = update(_statistics.value)
    }
    
    /**
     * Cleans up resources
     */
    fun shutdown() {
        isProcessing = false
        scope.cancel()
        
        // Close all ports and devices
        outputPorts.values.forEach { it.close() }
        outputDevices.values.forEach { it.close() }
        
        outputPorts.clear()
        outputDevices.clear()
    }
}

/**
 * Internal data class for timed MIDI message delivery
 */
private data class TimedMidiMessage(
    val message: MidiMessage,
    val targetDeviceId: String?,
    val scheduledTime: Long
)