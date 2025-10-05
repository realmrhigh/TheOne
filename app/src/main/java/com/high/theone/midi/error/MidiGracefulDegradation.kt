package com.high.theone.midi.error

import android.util.Log
import com.high.theone.midi.MidiError
import com.high.theone.midi.model.MidiSystemState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages graceful degradation of MIDI functionality when errors occur.
 * Ensures the app continues to function even when MIDI is unavailable.
 * 
 * Requirements: 7.1, 7.2
 */
@Singleton
class MidiGracefulDegradation @Inject constructor() {
    
    companion object {
        private const val TAG = "MidiGracefulDegradation"
    }
    
    // Degradation state tracking
    private val _degradationMode = MutableStateFlow(MidiDegradationMode.FULL_FUNCTIONALITY)
    val degradationMode: StateFlow<MidiDegradationMode> = _degradationMode.asStateFlow()
    
    private val _disabledFeatures = MutableStateFlow<Set<MidiFeature>>(emptySet())
    val disabledFeatures: StateFlow<Set<MidiFeature>> = _disabledFeatures.asStateFlow()
    
    private val _fallbackMessage = MutableStateFlow<String?>(null)
    val fallbackMessage: StateFlow<String?> = _fallbackMessage.asStateFlow()
    
    /**
     * Apply graceful degradation based on error type
     */
    fun applyDegradation(error: MidiError, systemState: MidiSystemState) {
        Log.i(TAG, "Applying graceful degradation for error: ${error::class.simpleName}")
        
        val (mode, features, message) = determineDegradationStrategy(error, systemState)
        
        _degradationMode.value = mode
        _disabledFeatures.value = features
        _fallbackMessage.value = message
        
        Log.i(TAG, "Degradation applied: mode=$mode, disabled features=${features.size}")
    }
    
    /**
     * Restore functionality when error is resolved
     */
    fun restoreFunctionality(resolvedError: MidiError) {
        Log.i(TAG, "Restoring functionality after resolving: ${resolvedError::class.simpleName}")
        
        val currentFeatures = _disabledFeatures.value.toMutableSet()
        val featuresToRestore = getFeaturesForError(resolvedError)
        
        currentFeatures.removeAll(featuresToRestore)
        _disabledFeatures.value = currentFeatures
        
        // Update degradation mode based on remaining disabled features
        _degradationMode.value = when {
            currentFeatures.isEmpty() -> MidiDegradationMode.FULL_FUNCTIONALITY
            currentFeatures.size <= 2 -> MidiDegradationMode.PARTIAL_FUNCTIONALITY
            else -> MidiDegradationMode.TOUCH_ONLY
        }
        
        // Clear message if fully restored
        if (currentFeatures.isEmpty()) {
            _fallbackMessage.value = null
        }
        
        Log.i(TAG, "Functionality restored: mode=${_degradationMode.value}, remaining disabled=${currentFeatures.size}")
    }
    
    /**
     * Check if a specific MIDI feature is available
     */
    fun isFeatureAvailable(feature: MidiFeature): Boolean {
        return !_disabledFeatures.value.contains(feature)
    }
    
    /**
     * Get user-friendly explanation for current degradation
     */
    fun getDegradationExplanation(): String? {
        return when (_degradationMode.value) {
            MidiDegradationMode.FULL_FUNCTIONALITY -> null
            MidiDegradationMode.PARTIAL_FUNCTIONALITY -> {
                val disabledCount = _disabledFeatures.value.size
                "Some MIDI features are temporarily unavailable ($disabledCount features disabled). Touch controls remain fully functional."
            }
            MidiDegradationMode.TOUCH_ONLY -> {
                "MIDI functionality is currently unavailable. Using touch controls only. " +
                (_fallbackMessage.value ?: "Please check your MIDI connections.")
            }
            MidiDegradationMode.EMERGENCY_MODE -> {
                "Critical MIDI system error. Operating in emergency mode with basic functionality only."
            }
        }
    }
    
    /**
     * Get alternative input methods when MIDI is unavailable
     */
    fun getAlternativeInputMethods(): List<AlternativeInputMethod> {
        val alternatives = mutableListOf<AlternativeInputMethod>()
        
        // Always available
        alternatives.add(
            AlternativeInputMethod(
                type = InputMethodType.TOUCH,
                name = "Touch Controls",
                description = "Use on-screen pads and controls",
                isAvailable = true,
                instructions = "Tap pads to trigger samples, use sliders for parameters"
            )
        )
        
        // Add other alternatives based on what's available
        if (isFeatureAvailable(MidiFeature.VIRTUAL_KEYBOARD)) {
            alternatives.add(
                AlternativeInputMethod(
                    type = InputMethodType.VIRTUAL_KEYBOARD,
                    name = "Virtual Keyboard",
                    description = "On-screen piano keyboard",
                    isAvailable = true,
                    instructions = "Use the virtual keyboard to play notes"
                )
            )
        }
        
        if (isFeatureAvailable(MidiFeature.GESTURE_CONTROL)) {
            alternatives.add(
                AlternativeInputMethod(
                    type = InputMethodType.GESTURE,
                    name = "Gesture Control",
                    description = "Control parameters with gestures",
                    isAvailable = true,
                    instructions = "Swipe and pinch to control effects and parameters"
                )
            )
        }
        
        return alternatives
    }
    
    /**
     * Reset degradation to full functionality
     */
    fun reset() {
        Log.i(TAG, "Resetting graceful degradation to full functionality")
        _degradationMode.value = MidiDegradationMode.FULL_FUNCTIONALITY
        _disabledFeatures.value = emptySet()
        _fallbackMessage.value = null
    }
    
    // Private helper methods
    
    private fun determineDegradationStrategy(
        error: MidiError,
        systemState: MidiSystemState
    ): Triple<MidiDegradationMode, Set<MidiFeature>, String?> {
        
        return when (error) {
            is MidiError.DeviceNotFound -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL),
                    "MIDI device not found. Using touch controls."
                )
            }
            
            is MidiError.ConnectionFailed -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL),
                    "MIDI connection failed. Using touch controls."
                )
            }
            
            is MidiError.PermissionDenied -> {
                Triple(
                    MidiDegradationMode.TOUCH_ONLY,
                    setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL, MidiFeature.MIDI_OUTPUT),
                    "MIDI permission required. Grant permission to use external controllers."
                )
            }
            
            is MidiError.BufferOverflow -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.HIGH_SPEED_INPUT, MidiFeature.MIDI_LEARN),
                    "MIDI data overload. Reduced MIDI processing to prevent issues."
                )
            }
            
            is MidiError.ClockSyncLost -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.EXTERNAL_CLOCK, MidiFeature.CLOCK_SYNC),
                    "MIDI clock sync lost. Using internal timing."
                )
            }
            
            MidiError.MidiNotSupported -> {
                Triple(
                    MidiDegradationMode.TOUCH_ONLY,
                    MidiFeature.values().toSet(),
                    "MIDI not supported on this device. Touch controls available."
                )
            }
            
            is MidiError.MappingConflict -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.MIDI_LEARN, MidiFeature.CUSTOM_MAPPING),
                    "MIDI mapping conflict. Using default mappings."
                )
            }
            
            is MidiError.DeviceBusy -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.EXTERNAL_INPUT),
                    "MIDI device busy. Will retry connection automatically."
                )
            }
            
            is MidiError.Timeout -> {
                Triple(
                    MidiDegradationMode.PARTIAL_FUNCTIONALITY,
                    setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL),
                    "MIDI communication timeout. Check device connection."
                )
            }
            
            else -> {
                // For unknown errors, apply conservative degradation
                Triple(
                    MidiDegradationMode.EMERGENCY_MODE,
                    MidiFeature.values().toSet(),
                    "Critical MIDI error. Operating in safe mode."
                )
            }
        }
    }
    
    private fun getFeaturesForError(error: MidiError): Set<MidiFeature> {
        return when (error) {
            is MidiError.DeviceNotFound -> setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL)
            is MidiError.ConnectionFailed -> setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL)
            is MidiError.PermissionDenied -> setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL, MidiFeature.MIDI_OUTPUT)
            is MidiError.BufferOverflow -> setOf(MidiFeature.HIGH_SPEED_INPUT, MidiFeature.MIDI_LEARN)
            is MidiError.ClockSyncLost -> setOf(MidiFeature.EXTERNAL_CLOCK, MidiFeature.CLOCK_SYNC)
            MidiError.MidiNotSupported -> MidiFeature.values().toSet()
            is MidiError.MappingConflict -> setOf(MidiFeature.MIDI_LEARN, MidiFeature.CUSTOM_MAPPING)
            is MidiError.DeviceBusy -> setOf(MidiFeature.EXTERNAL_INPUT)
            is MidiError.Timeout -> setOf(MidiFeature.EXTERNAL_INPUT, MidiFeature.DEVICE_CONTROL)
            else -> emptySet()
        }
    }
}

/**
 * MIDI degradation modes
 */
enum class MidiDegradationMode {
    FULL_FUNCTIONALITY,     // All MIDI features working
    PARTIAL_FUNCTIONALITY,  // Some MIDI features disabled
    TOUCH_ONLY,            // MIDI disabled, touch controls only
    EMERGENCY_MODE         // Critical error, minimal functionality
}

/**
 * MIDI features that can be disabled during degradation
 */
enum class MidiFeature {
    EXTERNAL_INPUT,        // Input from external MIDI devices
    MIDI_OUTPUT,          // Output to external MIDI devices
    DEVICE_CONTROL,       // Device connection and management
    MIDI_LEARN,           // MIDI learn functionality
    CUSTOM_MAPPING,       // Custom MIDI mappings
    EXTERNAL_CLOCK,       // External MIDI clock sync
    CLOCK_SYNC,           // Clock synchronization
    HIGH_SPEED_INPUT,     // High-speed MIDI input processing
    VIRTUAL_KEYBOARD,     // Virtual MIDI keyboard
    GESTURE_CONTROL       // Gesture-based parameter control
}

/**
 * Alternative input methods when MIDI is unavailable
 */
data class AlternativeInputMethod(
    val type: InputMethodType,
    val name: String,
    val description: String,
    val isAvailable: Boolean,
    val instructions: String
)

/**
 * Types of alternative input methods
 */
enum class InputMethodType {
    TOUCH,
    VIRTUAL_KEYBOARD,
    GESTURE,
    VOICE,
    ACCELEROMETER
}