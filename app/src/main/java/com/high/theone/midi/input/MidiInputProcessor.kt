package com.high.theone.midi.input

import com.high.theone.midi.model.MidiMessage
import com.high.theone.midi.model.MidiMessageType
import com.high.theone.midi.model.MidiParameterMapping
import com.high.theone.midi.model.MidiTargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time MIDI message processing pipeline that handles incoming MIDI messages,
 * applies latency compensation, and routes messages to appropriate handlers.
 * 
 * Requirements: 1.1, 1.3, 1.6, 5.4
 */
@Singleton
class MidiInputProcessor @Inject constructor() {
    
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Message queuing system with priority handling
    private val highPriorityQueue = ConcurrentLinkedQueue<MidiMessage>()
    private val normalPriorityQueue = ConcurrentLinkedQueue<MidiMessage>()
    private val lowPriorityQueue = ConcurrentLinkedQueue<MidiMessage>()
    
    // Processing state
    private val isProcessing = AtomicBoolean(false)
    private val processedMessageCount = AtomicLong(0)
    private val droppedMessageCount = AtomicLong(0)
    
    // Latency compensation settings
    private var inputLatencyMs: Float = 0f
    private var timingCorrectionEnabled: Boolean = true
    
    // Output flows for processed messages
    private val _processedMessages = MutableSharedFlow<ProcessedMidiMessage>(
        extraBufferCapacity = 1000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val processedMessages: Flow<ProcessedMidiMessage> = _processedMessages.asSharedFlow()
    
    // Message routing callbacks
    private val messageHandlers = mutableMapOf<MidiTargetType, (ProcessedMidiMessage) -> Unit>()
    
    // Performance monitoring
    private var lastProcessingTime = 0L
    private var averageProcessingTime = 0f
    
    /**
     * Starts the MIDI input processing pipeline
     */
    fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            processingScope.launch {
                processMessageQueue()
            }
        }
    }
    
    /**
     * Stops the MIDI input processing pipeline
     */
    fun stopProcessing() {
        isProcessing.set(false)
        clearQueues()
    }
    
    /**
     * Processes an incoming MIDI message with priority-based queuing
     */
    fun processMessage(message: MidiMessage) {
        val correctedMessage = if (timingCorrectionEnabled) {
            applyLatencyCompensation(message)
        } else {
            message
        }
        
        // Determine message priority and queue accordingly
        when (getMessagePriority(correctedMessage)) {
            MessagePriority.HIGH -> {
                if (!highPriorityQueue.offer(correctedMessage)) {
                    droppedMessageCount.incrementAndGet()
                }
            }
            MessagePriority.NORMAL -> {
                if (!normalPriorityQueue.offer(correctedMessage)) {
                    droppedMessageCount.incrementAndGet()
                }
            }
            MessagePriority.LOW -> {
                if (!lowPriorityQueue.offer(correctedMessage)) {
                    droppedMessageCount.incrementAndGet()
                }
            }
        }
    }
    
    /**
     * Sets input latency compensation in milliseconds
     */
    fun setInputLatencyCompensation(latencyMs: Float) {
        this.inputLatencyMs = latencyMs
    }
    
    /**
     * Enables or disables timing correction
     */
    fun setTimingCorrectionEnabled(enabled: Boolean) {
        this.timingCorrectionEnabled = enabled
    }
    
    /**
     * Registers a message handler for a specific target type
     */
    fun registerMessageHandler(targetType: MidiTargetType, handler: (ProcessedMidiMessage) -> Unit) {
        messageHandlers[targetType] = handler
    }
    
    /**
     * Unregisters a message handler
     */
    fun unregisterMessageHandler(targetType: MidiTargetType) {
        messageHandlers.remove(targetType)
    }
    
    /**
     * Gets processing statistics
     */
    fun getProcessingStatistics(): ProcessingStatistics {
        return ProcessingStatistics(
            processedMessageCount = processedMessageCount.get(),
            droppedMessageCount = droppedMessageCount.get(),
            averageProcessingTimeMs = averageProcessingTime,
            queueSizes = QueueSizes(
                highPriority = highPriorityQueue.size,
                normal = normalPriorityQueue.size,
                low = lowPriorityQueue.size
            )
        )
    }
    
    private suspend fun processMessageQueue() {
        while (isProcessing.get()) {
            val startTime = System.nanoTime()
            
            // Process messages in priority order
            val message = dequeueNextMessage()
            if (message != null) {
                try {
                    val processedMessage = createProcessedMessage(message)
                    
                    // Emit to flow
                    _processedMessages.tryEmit(processedMessage)
                    
                    // Route to registered handlers
                    routeMessage(processedMessage)
                    
                    processedMessageCount.incrementAndGet()
                } catch (e: Exception) {
                    // Log error but continue processing
                    droppedMessageCount.incrementAndGet()
                }
            }
            
            // Update performance metrics
            val processingTime = (System.nanoTime() - startTime) / 1_000_000f
            updateAverageProcessingTime(processingTime)
            
            // Small delay to prevent busy waiting when no messages
            if (message == null) {
                kotlinx.coroutines.delay(1)
            }
        }
    }
    
    private fun dequeueNextMessage(): MidiMessage? {
        // Process high priority messages first
        highPriorityQueue.poll()?.let { return it }
        
        // Then normal priority
        normalPriorityQueue.poll()?.let { return it }
        
        // Finally low priority
        return lowPriorityQueue.poll()
    }
    
    private fun getMessagePriority(message: MidiMessage): MessagePriority {
        return when (message.type) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> MessagePriority.HIGH
            MidiMessageType.CONTROL_CHANGE, MidiMessageType.PITCH_BEND -> MessagePriority.NORMAL
            MidiMessageType.PROGRAM_CHANGE, MidiMessageType.AFTERTOUCH -> MessagePriority.NORMAL
            MidiMessageType.CLOCK, MidiMessageType.START, MidiMessageType.STOP, MidiMessageType.CONTINUE -> MessagePriority.HIGH
            MidiMessageType.SYSTEM_EXCLUSIVE -> MessagePriority.LOW
        }
    }
    
    private fun applyLatencyCompensation(message: MidiMessage): MidiMessage {
        val compensatedTimestamp = message.timestamp - (inputLatencyMs * 1_000_000).toLong()
        return message.copy(timestamp = maxOf(0, compensatedTimestamp))
    }
    
    private fun createProcessedMessage(message: MidiMessage): ProcessedMidiMessage {
        return ProcessedMidiMessage(
            originalMessage = message,
            processedTimestamp = System.nanoTime(),
            latencyCompensationApplied = timingCorrectionEnabled,
            priority = getMessagePriority(message)
        )
    }
    
    private fun routeMessage(processedMessage: ProcessedMidiMessage) {
        // Route based on message type - this will be enhanced with mapping engine integration
        val targetType = when (processedMessage.originalMessage.type) {
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> MidiTargetType.PAD_TRIGGER
            MidiMessageType.CONTROL_CHANGE -> MidiTargetType.EFFECT_PARAMETER
            MidiMessageType.START, MidiMessageType.STOP, MidiMessageType.CONTINUE -> MidiTargetType.TRANSPORT_CONTROL
            else -> null
        }
        
        targetType?.let { type ->
            messageHandlers[type]?.invoke(processedMessage)
        }
    }
    
    private fun updateAverageProcessingTime(newTime: Float) {
        averageProcessingTime = if (averageProcessingTime == 0f) {
            newTime
        } else {
            (averageProcessingTime * 0.9f) + (newTime * 0.1f)
        }
    }
    
    private fun clearQueues() {
        highPriorityQueue.clear()
        normalPriorityQueue.clear()
        lowPriorityQueue.clear()
    }
}

/**
 * Message priority levels for queue management
 */
enum class MessagePriority {
    HIGH,    // Note events, clock, transport
    NORMAL,  // Control changes, pitch bend
    LOW      // System exclusive, program changes
}

/**
 * Processed MIDI message with additional metadata
 */
data class ProcessedMidiMessage(
    val originalMessage: MidiMessage,
    val processedTimestamp: Long,
    val latencyCompensationApplied: Boolean,
    val priority: MessagePriority
)

/**
 * Processing performance statistics
 */
data class ProcessingStatistics(
    val processedMessageCount: Long,
    val droppedMessageCount: Long,
    val averageProcessingTimeMs: Float,
    val queueSizes: QueueSizes
)

/**
 * Current queue sizes for monitoring
 */
data class QueueSizes(
    val highPriority: Int,
    val normal: Int,
    val low: Int
)