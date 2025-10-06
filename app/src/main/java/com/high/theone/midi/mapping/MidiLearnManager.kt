package com.high.theone.midi.mapping

import com.high.theone.midi.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MIDI learn functionality for automatic mapping assignment.
 * Handles learn mode activation, message capture, and timeout management.
 */
@Singleton
class MidiLearnManager @Inject constructor() {
    
    private val _learnState = MutableStateFlow<MidiLearnState>(MidiLearnState.Inactive)
    val learnState: StateFlow<MidiLearnState> = _learnState.asStateFlow()
    
    private val _learnProgress = MutableStateFlow<MidiLearnProgress?>(null)
    val learnProgress: StateFlow<MidiLearnProgress?> = _learnProgress.asStateFlow()
    
    private var learnJob: Job? = null
    private var currentLearnTarget: MidiLearnTarget? = null
    private val defaultLearnTimeoutMs = 30_000L // 30 seconds
    
    /**
     * Starts MIDI learn mode for a specific target parameter
     */
    suspend fun startMidiLearn(
        targetType: MidiTargetType,
        targetId: String,
        timeoutMs: Long = defaultLearnTimeoutMs,
        allowedMessageTypes: Set<MidiMessageType> = setOf(
            MidiMessageType.NOTE_ON,
            MidiMessageType.CONTROL_CHANGE,
            MidiMessageType.PITCH_BEND
        )
    ): Result<Unit> {
        return try {
            // Cancel any existing learn session
            stopMidiLearn()
            
            val learnTarget = MidiLearnTarget(
                targetType = targetType,
                targetId = targetId,
                allowedMessageTypes = allowedMessageTypes,
                startTime = System.currentTimeMillis()
            )
            
            currentLearnTarget = learnTarget
            _learnState.value = MidiLearnState.Active(learnTarget)
            _learnProgress.value = MidiLearnProgress(
                target = learnTarget,
                timeoutMs = timeoutMs,
                startTime = System.currentTimeMillis(),
                capturedMessages = emptyList()
            )
            
            // Start timeout job
            learnJob = CoroutineScope(Dispatchers.Default).launch {
                delay(timeoutMs)
                if (_learnState.value is MidiLearnState.Active) {
                    handleLearnTimeout()
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(MidiLearnException("Failed to start MIDI learn: ${e.message}", e))
        }
    }
    
    /**
     * Stops the current MIDI learn session
     */
    suspend fun stopMidiLearn(): MidiParameterMapping? {
        val currentState = _learnState.value
        
        learnJob?.cancel()
        learnJob = null
        
        val result = when (currentState) {
            is MidiLearnState.Active -> {
                val progress = _learnProgress.value
                if (progress != null && progress.capturedMessages.isNotEmpty()) {
                    createMappingFromLearnedMessage(progress.capturedMessages.first(), currentState.target)
                } else {
                    null
                }
            }
            is MidiLearnState.Completed -> currentState.learnedMapping
            else -> null
        }
        
        _learnState.value = MidiLearnState.Inactive
        _learnProgress.value = null
        currentLearnTarget = null
        
        return result
    }
    
    /**
     * Cancels the current MIDI learn session without creating a mapping
     */
    suspend fun cancelMidiLearn() {
        learnJob?.cancel()
        learnJob = null
        
        _learnState.value = MidiLearnState.Cancelled
        _learnProgress.value = null
        currentLearnTarget = null
        
        // Reset to inactive after a brief delay to show cancelled state
        delay(1000)
        _learnState.value = MidiLearnState.Inactive
    }
    
    /**
     * Processes a MIDI message during learn mode
     */
    suspend fun processMidiMessageForLearn(message: MidiMessage): MidiLearnResult {
        val currentState = _learnState.value
        
        if (currentState !is MidiLearnState.Active) {
            return MidiLearnResult.NotLearning
        }
        
        val target = currentState.target
        
        // Check if message type is allowed for this learn session
        if (message.type !in target.allowedMessageTypes) {
            return MidiLearnResult.MessageTypeNotAllowed(message.type)
        }
        
        // Filter out invalid messages
        if (!isValidLearnMessage(message)) {
            return MidiLearnResult.InvalidMessage(message)
        }
        
        // Update progress with captured message
        val currentProgress = _learnProgress.value
        if (currentProgress != null) {
            val updatedMessages = currentProgress.capturedMessages + message
            _learnProgress.value = currentProgress.copy(capturedMessages = updatedMessages)
            
            // Auto-complete learn if we have a good message
            if (isGoodLearnMessage(message)) {
                val mapping = createMappingFromLearnedMessage(message, target)
                _learnState.value = MidiLearnState.Completed(mapping)
                
                // Stop the timeout job
                learnJob?.cancel()
                learnJob = null
                
                return MidiLearnResult.LearnCompleted(mapping)
            }
        }
        
        return MidiLearnResult.MessageCaptured(message)
    }
    
    /**
     * Gets the current learn target if learning is active
     */
    fun getCurrentLearnTarget(): MidiLearnTarget? {
        return currentLearnTarget
    }
    
    /**
     * Checks if MIDI learn is currently active
     */
    fun isLearning(): Boolean {
        return _learnState.value is MidiLearnState.Active
    }
    
    /**
     * Gets the remaining time for the current learn session
     */
    fun getRemainingLearnTime(): Long {
        val progress = _learnProgress.value ?: return 0L
        val elapsed = System.currentTimeMillis() - progress.startTime
        return maxOf(0L, progress.timeoutMs - elapsed)
    }
    
    private suspend fun handleLearnTimeout() {
        val currentProgress = _learnProgress.value
        
        if (currentProgress != null && currentProgress.capturedMessages.isNotEmpty()) {
            // Use the first captured message if we have any
            val target = currentProgress.target
            val mapping = createMappingFromLearnedMessage(currentProgress.capturedMessages.first(), target)
            _learnState.value = MidiLearnState.Completed(mapping)
        } else {
            _learnState.value = MidiLearnState.TimedOut
        }
        
        // Reset to inactive after showing the result
        delay(2000)
        if (_learnState.value !is MidiLearnState.Active) {
            _learnState.value = MidiLearnState.Inactive
            _learnProgress.value = null
            currentLearnTarget = null
        }
    }
    
    private fun createMappingFromLearnedMessage(
        message: MidiMessage,
        target: MidiLearnTarget
    ): MidiParameterMapping {
        val midiController = when (message.type) {
            MidiMessageType.CONTROL_CHANGE -> message.data1
            MidiMessageType.NOTE_ON, MidiMessageType.NOTE_OFF -> message.data1
            MidiMessageType.PITCH_BEND -> 0 // Pitch bend doesn't use controller number
            else -> 0
        }
        
        val (minValue, maxValue) = getDefaultRangeForTarget(target.targetType)
        val curve = getDefaultCurveForTarget(target.targetType)
        
        return MidiParameterMapping(
            midiType = message.type,
            midiChannel = message.channel,
            midiController = midiController,
            targetType = target.targetType,
            targetId = target.targetId,
            minValue = minValue,
            maxValue = maxValue,
            curve = curve
        )
    }
    
    private fun isValidLearnMessage(message: MidiMessage): Boolean {
        return when (message.type) {
            MidiMessageType.NOTE_ON -> message.data2 > 0 // Velocity > 0
            MidiMessageType.CONTROL_CHANGE -> message.data1 in 0..127 && message.data2 in 0..127
            MidiMessageType.PITCH_BEND -> true
            else -> false
        }
    }
    
    private fun isGoodLearnMessage(message: MidiMessage): Boolean {
        return when (message.type) {
            MidiMessageType.NOTE_ON -> message.data2 > 20 // Good velocity
            MidiMessageType.CONTROL_CHANGE -> message.data2 > 10 // Significant controller value
            MidiMessageType.PITCH_BEND -> {
                val pitchValue = (message.data2 shl 7) or message.data1
                kotlin.math.abs(pitchValue - 8192) > 1000 // Significant pitch bend
            }
            else -> true
        }
    }
    
    private fun getDefaultRangeForTarget(targetType: MidiTargetType): Pair<Float, Float> {
        return when (targetType) {
            MidiTargetType.PAD_TRIGGER -> 0.0f to 1.0f
            MidiTargetType.PAD_VOLUME -> 0.0f to 1.0f
            MidiTargetType.PAD_PAN -> -1.0f to 1.0f
            MidiTargetType.MASTER_VOLUME -> 0.0f to 1.0f
            MidiTargetType.EFFECT_PARAMETER -> 0.0f to 1.0f
            MidiTargetType.SEQUENCER_TEMPO -> 60.0f to 200.0f
            MidiTargetType.TRANSPORT_CONTROL -> 0.0f to 1.0f
        }
    }
    
    private fun getDefaultCurveForTarget(targetType: MidiTargetType): MidiCurve {
        return when (targetType) {
            MidiTargetType.PAD_VOLUME, MidiTargetType.MASTER_VOLUME -> MidiCurve.EXPONENTIAL
            MidiTargetType.PAD_PAN -> MidiCurve.LINEAR
            MidiTargetType.EFFECT_PARAMETER -> MidiCurve.LINEAR
            MidiTargetType.SEQUENCER_TEMPO -> MidiCurve.LINEAR
            else -> MidiCurve.LINEAR
        }
    }
}

/**
 * Tracks the progress of a MIDI learn session
 */
data class MidiLearnProgress(
    val target: MidiLearnTarget,
    val timeoutMs: Long,
    val startTime: Long,
    val capturedMessages: List<MidiMessage>
) {
    val elapsedTime: Long
        get() = System.currentTimeMillis() - startTime
    
    val remainingTime: Long
        get() = maxOf(0L, timeoutMs - elapsedTime)
    
    val progressPercent: Float
        get() = (elapsedTime.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Result of processing a MIDI message during learn mode
 */
sealed class MidiLearnResult {
    object NotLearning : MidiLearnResult()
    data class MessageTypeNotAllowed(val messageType: MidiMessageType) : MidiLearnResult()
    data class InvalidMessage(val message: MidiMessage) : MidiLearnResult()
    data class MessageCaptured(val message: MidiMessage) : MidiLearnResult()
    data class LearnCompleted(val mapping: MidiParameterMapping) : MidiLearnResult()
}

/**
 * Exception thrown when MIDI learn operations fail
 */
class MidiLearnException(message: String, cause: Throwable? = null) : Exception(message, cause)