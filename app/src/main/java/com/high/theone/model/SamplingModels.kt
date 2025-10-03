package com.high.theone.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.util.UUID

/**
 * Data models for the Basic Sampling & Pad Playback feature (M1).
 * These models support the core MPC-style sampling workflow including
 * pad management, recording operations, and sample editing.
 */

/**
 * Playback modes for pad samples.
 */
enum class PlaybackMode {
    ONE_SHOT,  // Play once from start to finish
    LOOP,      // Continuously repeat until stopped
    GATE,      // Play while pad is held down
    NOTE_ON_OFF // MIDI-style note on/off behavior
}

/**
 * Audio input sources for recording.
 */
enum class AudioInputSource {
    MICROPHONE,
    LINE_IN,
    USB_AUDIO,
    BLUETOOTH
}

/**
 * Audio format types.
 */
enum class AudioFormat {
    WAV,
    MP3,
    FLAC,
    OGG
}

/**
 * Represents the state of a virtual drum pad including sample assignment,
 * playback properties, and visual feedback state.
 * 
 * Requirements: 2.2 (sample assignment), 6.1 (visual feedback)
 */
@Serializable
data class PadState(
    val index: Int,
    val sampleId: String? = null,
    val sampleName: String? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0 (left) to 1.0 (right), 0.0 = center
    val playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    val hasAssignedSample: Boolean = false,
    val isLoading: Boolean = false,
    val lastTriggerVelocity: Float = 0.0f, // 0.0 to 1.0
    val chokeGroup: Int? = null, // For mutually exclusive pads (hi-hat open/close)
    val isEnabled: Boolean = true
) {
    /**
     * Convenience property to check if pad can be triggered
     */
    val canTrigger: Boolean
        get() = hasAssignedSample && !isLoading && isEnabled && sampleId != null
    
    /**
     * Convenience property to get display name for the pad
     */
    val displayName: String
        get() = sampleName ?: "Pad ${index + 1}"
}

/**
 * Represents the current state of the audio recording workflow including
 * recording status, duration tracking, and level monitoring.
 * 
 * Requirements: 1.2 (recording status), 1.3 (level monitoring)
 */
@Serializable
data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L, // Duration in milliseconds
    val peakLevel: Float = 0.0f, // 0.0 to 1.0
    val averageLevel: Float = 0.0f, // 0.0 to 1.0 for smoother UI display
    val isInitialized: Boolean = false,
    val error: String? = null,
    val inputSource: AudioInputSource = AudioInputSource.MICROPHONE,
    val sampleRate: Int = 44100,
    val channels: Int = 1, // 1 = mono, 2 = stereo
    val bitDepth: Int = 16,
    val maxDurationSeconds: Int = 30, // Auto-stop after this duration
    val recordingFilePath: String? = null,
    val isProcessing: Boolean = false // True when finalizing recording
) {
    /**
     * Convenience property to check if recording can be started
     */
    val canStartRecording: Boolean
        get() = isInitialized && !isRecording && !isProcessing && error == null
    
    /**
     * Convenience property to check if recording can be stopped
     */
    val canStopRecording: Boolean
        get() = isRecording && !isProcessing
    
    /**
     * Convenience property to get formatted duration string
     */
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    
    /**
     * Convenience property to check if approaching max duration
     */
    val isNearMaxDuration: Boolean
        get() = (durationMs / 1000) >= (maxDurationSeconds - 5) // Warning at 5 seconds remaining
}

/**
 * Settings for basic sample editing operations including trimming,
 * fade in/out, and basic processing options.
 * 
 * Requirements: 8.3 (sample trimming), 8.4 (fade options)
 */
@Serializable
data class SampleTrimSettings(
    val startTime: Float = 0.0f, // Start trim position (0.0 to 1.0 normalized)
    val endTime: Float = 1.0f,   // End trim position (0.0 to 1.0 normalized)
    val fadeInMs: Float = 0.0f,  // Fade in duration in milliseconds
    val fadeOutMs: Float = 0.0f, // Fade out duration in milliseconds
    val normalize: Boolean = false, // Apply normalization to trimmed sample
    val reverse: Boolean = false,   // Reverse the sample
    val gain: Float = 1.0f,        // Gain adjustment (1.0 = no change)
    val originalDurationMs: Long = 0L, // Original sample duration in milliseconds
    val preserveOriginal: Boolean = true // Keep original file, store trim as metadata
) {
    /**
     * Get the actual start time in milliseconds based on original duration
     */
    val startTimeMs: Long
        get() = (startTime * originalDurationMs).toLong()
    
    /**
     * Get the actual end time in milliseconds based on original duration
     */
    val endTimeMs: Long
        get() = (endTime * originalDurationMs).toLong()
    
    /**
     * Get the trimmed duration in milliseconds
     */
    val trimmedDurationMs: Long
        get() = endTimeMs - startTimeMs
    
    /**
     * Check if any trimming is applied
     */
    val isTrimmed: Boolean
        get() = startTime > 0.0f || endTime < 1.0f
    
    /**
     * Check if any processing is applied
     */
    val hasProcessing: Boolean
        get() = isTrimmed || fadeInMs > 0.0f || fadeOutMs > 0.0f || 
                normalize || reverse || gain != 1.0f
    
    /**
     * Validate trim settings
     */
    fun isValid(): Boolean {
        return startTime >= 0.0f && 
               endTime <= 1.0f && 
               startTime < endTime &&
               fadeInMs >= 0.0f &&
               fadeOutMs >= 0.0f &&
               gain > 0.0f
    }
}

/**
 * Configuration settings for individual pads including playback behavior,
 * audio processing, and visual customization.
 */
@Serializable
data class PadSettings(
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    val velocitySensitivity: Float = 1.0f, // How much velocity affects volume
    val chokeGroup: Int? = null,
    val muteGroup: Int? = null, // Pads that should be muted when this pad plays
    val soloGroup: Int? = null, // Solo grouping for exclusive playback
    val pitchShift: Float = 0.0f, // Semitones (-12.0 to +12.0)
    val trimSettings: SampleTrimSettings = SampleTrimSettings(),
    val color: String? = null, // Hex color for visual customization
    val name: String? = null,  // Custom pad name
    val isEnabled: Boolean = true
)

/**
 * Configuration for a pad including sample assignment and settings.
 */
@Serializable
data class PadConfiguration(
    val padIndex: Int,
    val sampleId: String? = null,
    val settings: PadSettings = PadSettings()
)

/**
 * Statistics about pad usage in the current session.
 */
data class PadUsageStats(
    val totalPads: Int,
    val loadedPads: Int,
    val playingPads: Int,
    val enabledPads: Int,
    val padsByPlaybackMode: Map<PlaybackMode, Int>
)

/**
 * Sample organization modes for sorting and filtering.
 */
enum class SampleOrganizationMode {
    BY_NAME,
    BY_DATE,
    BY_DURATION,
    BY_SIZE,
    BY_FORMAT,
    BY_USAGE
}

/**
 * Sample search criteria for filtering search results.
 */
enum class SampleSearchCriteria {
    ALL,
    NAME_ONLY,
    TAGS_ONLY,
    UNUSED_ONLY
}

/**
 * Information about how a sample is being used across pads.
 */
data class PadSampleUsageInfo(
    val sampleId: String,
    val sampleName: String,
    val usingPads: List<Int>,
    val usageCount: Int,
    val isLoaded: Boolean,
    val lastUsed: Long
)

/**
 * Types of sampling operations for validation and error handling.
 */
enum class SamplingOperation {
    START_RECORDING,
    STOP_RECORDING,
    TRIGGER_PAD,
    LOAD_SAMPLE,
    SAVE_PROJECT,
    ASSIGN_SAMPLE,
    CONFIGURE_PAD
}

/**
 * Result of operation validation.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Warning(val message: String) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * System diagnostics information for debugging.
 */
data class SystemDiagnostics(
    val audioEngineReady: Boolean,
    val recordingState: RecordingState,
    val loadedSamples: Int,
    val loadedPads: Int,
    val playingPads: Int,
    val memoryUsage: MemoryUsage,
    val lastError: String?,
    val isDirty: Boolean
)

/**
 * Memory usage information.
 */
data class MemoryUsage(
    val used: Long,
    val total: Long,
    val max: Long
) {
    val usedPercentage: Float
        get() = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f
    
    val availablePercentage: Float
        get() = 100f - usedPercentage
}

/**
 * Comprehensive UI state for the sampling feature combining all related states
 * for reactive UI updates and centralized state management.
 */
@Serializable
data class SamplingUiState(
    val recordingState: RecordingState = RecordingState(),
    val pads: List<PadState> = List(16) { PadState(it) },
    val availableSamples: List<SampleMetadata> = emptyList(),
    val selectedPad: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAudioEngineReady: Boolean = false,
    val currentProject: String? = null,
    val isDirty: Boolean = false // Has unsaved changes
) {
    /**
     * Get a specific pad by index
     */
    fun getPad(index: Int): PadState? {
        return pads.getOrNull(index)
    }
    
    /**
     * Get all pads that are currently playing
     */
    val playingPads: List<PadState>
        get() = pads.filter { it.isPlaying }
    
    /**
     * Get all pads that have samples assigned
     */
    val loadedPads: List<PadState>
        get() = pads.filter { it.hasAssignedSample }
    
    /**
     * Check if any operation is in progress
     */
    val isBusy: Boolean
        get() = isLoading || recordingState.isRecording || recordingState.isProcessing
}