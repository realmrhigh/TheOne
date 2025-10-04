package com.high.theone.midi

/**
 * MIDI-specific error types for comprehensive error handling
 */
sealed class MidiError : Exception() {
    
    /**
     * Device not found error
     */
    data class DeviceNotFound(val deviceId: String) : MidiError() {
        override val message: String
            get() = "MIDI device not found: $deviceId"
    }
    
    /**
     * Device connection failed error
     */
    data class ConnectionFailed(val deviceId: String, val reason: String) : MidiError() {
        override val message: String
            get() = "Failed to connect to MIDI device $deviceId: $reason"
    }
    
    /**
     * Permission denied error
     */
    data class PermissionDenied(val permission: String) : MidiError() {
        override val message: String
            get() = "MIDI permission denied: $permission"
    }
    
    /**
     * Invalid MIDI message error
     */
    data class InvalidMessage(val messageData: ByteArray) : MidiError() {
        override val message: String
            get() = "Invalid MIDI message: ${messageData.joinToString { "%02X".format(it) }}"
            
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as InvalidMessage
            return messageData.contentEquals(other.messageData)
        }
        
        override fun hashCode(): Int {
            return messageData.contentHashCode()
        }
    }
    
    /**
     * Buffer overflow error
     */
    data class BufferOverflow(val deviceId: String) : MidiError() {
        override val message: String
            get() = "MIDI buffer overflow for device: $deviceId"
    }
    
    /**
     * Clock synchronization lost error
     */
    data class ClockSyncLost(val reason: String) : MidiError() {
        override val message: String
            get() = "MIDI clock synchronization lost: $reason"
    }
    
    /**
     * MIDI not supported on this device
     */
    object MidiNotSupported : MidiError() {
        override val message: String
            get() = "MIDI is not supported on this device"
    }
    
    /**
     * Mapping conflict error
     */
    data class MappingConflict(val conflictingMappings: List<String>) : MidiError() {
        override val message: String
            get() = "MIDI mapping conflict detected: ${conflictingMappings.joinToString()}"
    }
    
    /**
     * Device busy error
     */
    data class DeviceBusy(val deviceId: String) : MidiError() {
        override val message: String
            get() = "MIDI device is busy: $deviceId"
    }
    
    /**
     * Timeout error
     */
    data class Timeout(val operation: String, val timeoutMs: Long) : MidiError() {
        override val message: String
            get() = "MIDI operation timed out: $operation (${timeoutMs}ms)"
    }
}

/**
 * MIDI exception wrapper for standard exceptions
 */
class MidiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * MIDI error recovery strategies
 */
enum class MidiErrorRecoveryStrategy {
    RETRY,
    FALLBACK_TO_INTERNAL,
    NOTIFY_USER,
    IGNORE,
    RESTART_DEVICE
}

/**
 * MIDI error context for detailed error reporting
 */
data class MidiErrorContext(
    val deviceId: String?,
    val operation: String,
    val timestamp: Long,
    val additionalInfo: Map<String, Any> = emptyMap()
) {
    init {
        require(operation.isNotBlank()) { "Operation cannot be blank" }
        require(timestamp > 0) { "Timestamp must be positive" }
    }
}