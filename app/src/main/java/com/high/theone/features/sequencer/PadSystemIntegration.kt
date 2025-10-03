package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

/**
 * Integration layer between the sequencer and the existing pad system.
 * Manages pad state synchronization, sample assignments, and seamless switching
 * between live pad mode and sequencer mode.
 * 
 * Requirements: 8.2, 8.3, 8.5, 8.6
 */
@HiltViewModel
class PadSystemIntegration @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val samplingViewModel: SamplingViewModel
) : ViewModel() {

    companion object {
        private const val TAG = "PadSystemIntegration"
    }

    // Current mode state
    private val _currentMode = MutableStateFlow(PlaybackMode.LIVE)
    val currentMode: StateFlow<PlaybackMode> = _currentMode.asStateFlow()

    // Pad configuration synchronization state
    private val _padSyncState = MutableStateFlow(PadSyncState())
    val padSyncState: StateFlow<PadSyncState> = _padSyncState.asStateFlow()

    // Combined pad state for sequencer use
    private val _sequencerPadState = MutableStateFlow<List<SequencerPadInfo>>(emptyList())
    val sequencerPadState: StateFlow<List<SequencerPadInfo>> = _sequencerPadState.asStateFlow()

    init {
        observePadStates()
        initializePadSync()
    }

    /**
     * Observe pad states from the sampling system and synchronize with sequencer
     * Requirements: 8.2 (pad state integration)
     */
    private fun observePadStates() {
        viewModelScope.launch {
            samplingViewModel.uiState.collect { samplingState ->
                val sequencerPads = samplingState.pads.map { padState ->
                    SequencerPadInfo(
                        index = padState.index,
                        sampleId = padState.sampleId,
                        sampleName = padState.sampleName,
                        hasAssignedSample = padState.hasAssignedSample,
                        isEnabled = padState.isEnabled,
                        volume = padState.volume,
                        pan = padState.pan,
                        playbackMode = padState.playbackMode,
                        chokeGroup = padState.chokeGroup,
                        isLoading = padState.isLoading,
                        canTrigger = padState.canTrigger
                    )
                }
                
                _sequencerPadState.value = sequencerPads
                
                // Update sync state
                _padSyncState.update { syncState ->
                    syncState.copy(
                        totalPads = sequencerPads.size,
                        assignedPads = sequencerPads.count { it.hasAssignedSample },
                        enabledPads = sequencerPads.count { it.isEnabled },
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                
                Log.d(TAG, "Synchronized ${sequencerPads.size} pads with sequencer")
            }
        }
    }

    /**
     * Initialize pad synchronization with audio engine
     * Requirements: 8.3 (pad configuration synchronization)
     */
    private fun initializePadSync() {
        viewModelScope.launch {
            try {
                // Ensure audio engine is ready for pad operations
                val isReady = audioEngine.initialize(
                    sampleRate = 44100,
                    bufferSize = 256,
                    enableLowLatency = true
                )
                
                if (!isReady) {
                    Log.e(TAG, "Audio engine not ready for pad synchronization")
                    return@launch
                }

                // Initialize drum engine if not already done
                val drumEngineReady = audioEngine.initializeDrumEngine()
                if (!drumEngineReady) {
                    Log.w(TAG, "Drum engine initialization failed during pad sync")
                }

                _padSyncState.update { it.copy(isAudioEngineReady = isReady && drumEngineReady) }
                
                Log.d(TAG, "Pad synchronization initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing pad synchronization", e)
                _padSyncState.update { 
                    it.copy(
                        isAudioEngineReady = false,
                        lastError = "Initialization failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Switch between live pad mode and sequencer mode
     * Requirements: 8.6 (seamless switching between modes)
     */
    fun switchToMode(mode: PlaybackMode) {
        viewModelScope.launch {
            try {
                val previousMode = _currentMode.value
                
                when (mode) {
                    PlaybackMode.LIVE -> {
                        // Switch to live pad mode
                        Log.d(TAG, "Switching to live pad mode")
                        
                        // Stop any sequencer playback
                        // This would be handled by the sequencer ViewModel
                        
                        // Ensure all pad configurations are synchronized
                        synchronizeAllPadConfigurations()
                        
                        _currentMode.value = PlaybackMode.LIVE
                        
                    }
                    PlaybackMode.SEQUENCER -> {
                        // Switch to sequencer mode
                        Log.d(TAG, "Switching to sequencer mode")
                        
                        // Ensure all pads are properly configured for sequencer use
                        synchronizeAllPadConfigurations()
                        
                        // Enable sequencer optimizations in audio engine
                        if (audioEngine is SequencerAudioEngine) {
                            audioEngine.setSequencerMode(true)
                        }
                        
                        _currentMode.value = PlaybackMode.SEQUENCER
                    }
                    else -> {
                        Log.w(TAG, "Unsupported playback mode: $mode")
                        return@launch
                    }
                }
                
                _padSyncState.update { 
                    it.copy(
                        currentMode = mode,
                        lastModeSwitch = System.currentTimeMillis()
                    )
                }
                
                Log.d(TAG, "Successfully switched from $previousMode to $mode")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error switching playback mode", e)
                _padSyncState.update { 
                    it.copy(lastError = "Mode switch failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Synchronize all pad configurations with the audio engine
     * Requirements: 8.3 (pad configuration synchronization)
     */
    private suspend fun synchronizeAllPadConfigurations() {
        try {
            val pads = _sequencerPadState.value
            var syncedCount = 0
            var errorCount = 0
            
            pads.forEach { pad ->
                try {
                    if (pad.hasAssignedSample && pad.sampleId != null) {
                        // Synchronize pad settings with audio engine
                        audioEngine.setDrumPadVolume(pad.index, pad.volume)
                        audioEngine.setDrumPadPan(pad.index, pad.pan)
                        audioEngine.setDrumPadMode(pad.index, pad.playbackMode.ordinal)
                        
                        syncedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error synchronizing pad ${pad.index}", e)
                    errorCount++
                }
            }
            
            _padSyncState.update { 
                it.copy(
                    syncedPads = syncedCount,
                    syncErrors = errorCount,
                    lastSyncTime = System.currentTimeMillis(),
                    lastError = if (errorCount > 0) "Sync errors: $errorCount" else null
                )
            }
            
            Log.d(TAG, "Synchronized $syncedCount pads, $errorCount errors")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during pad configuration synchronization", e)
            _padSyncState.update { 
                it.copy(lastError = "Sync failed: ${e.message}")
            }
        }
    }

    /**
     * Detect and update pad assignments automatically
     * Requirements: 8.2 (automatic pad assignment detection)
     */
    fun detectPadAssignments() {
        viewModelScope.launch {
            try {
                val currentPads = _sequencerPadState.value
                val detectedAssignments = mutableListOf<PadAssignmentInfo>()
                
                currentPads.forEach { pad ->
                    if (pad.hasAssignedSample && pad.sampleId != null) {
                        detectedAssignments.add(
                            PadAssignmentInfo(
                                padIndex = pad.index,
                                sampleId = pad.sampleId,
                                sampleName = pad.sampleName ?: "Unknown",
                                isActive = pad.isEnabled && pad.canTrigger,
                                lastDetected = System.currentTimeMillis()
                            )
                        )
                    }
                }
                
                _padSyncState.update { 
                    it.copy(
                        detectedAssignments = detectedAssignments,
                        lastAssignmentDetection = System.currentTimeMillis()
                    )
                }
                
                Log.d(TAG, "Detected ${detectedAssignments.size} pad assignments")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting pad assignments", e)
                _padSyncState.update { 
                    it.copy(lastError = "Assignment detection failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Trigger a pad through the sampling system (for live mode)
     * Requirements: 8.6 (seamless switching)
     */
    fun triggerPadLive(padIndex: Int, velocity: Float) {
        if (_currentMode.value == PlaybackMode.LIVE) {
            samplingViewModel.triggerPad(padIndex, velocity)
        } else {
            Log.w(TAG, "Cannot trigger pad in live mode - currently in ${_currentMode.value}")
        }
    }

    /**
     * Get pad information for sequencer use
     * Requirements: 8.2 (pad state integration)
     */
    fun getPadInfo(padIndex: Int): SequencerPadInfo? {
        return _sequencerPadState.value.getOrNull(padIndex)
    }

    /**
     * Get all assigned pads for sequencer pattern creation
     * Requirements: 8.5 (pad assignment integration)
     */
    fun getAssignedPads(): List<SequencerPadInfo> {
        return _sequencerPadState.value.filter { it.hasAssignedSample }
    }

    /**
     * Check if a specific pad can be used in sequencer
     * Requirements: 8.2 (pad state integration)
     */
    fun canUsePadInSequencer(padIndex: Int): Boolean {
        val pad = getPadInfo(padIndex)
        return pad?.canTrigger == true && pad.hasAssignedSample
    }

    /**
     * Get pad usage statistics for sequencer optimization
     * Requirements: 8.3 (performance optimization)
     */
    fun getPadUsageStats(): PadUsageStats {
        val pads = _sequencerPadState.value
        return PadUsageStats(
            totalPads = pads.size,
            loadedPads = pads.count { it.hasAssignedSample },
            playingPads = 0, // This would be tracked during playback
            enabledPads = pads.count { it.isEnabled },
            padsByPlaybackMode = pads.groupBy { it.playbackMode }.mapValues { it.value.size }
        )
    }

    /**
     * Clear any integration errors
     */
    fun clearErrors() {
        _padSyncState.update { it.copy(lastError = null) }
    }

    /**
     * Force resynchronization of all pads
     */
    fun forcePadResync() {
        viewModelScope.launch {
            Log.d(TAG, "Forcing pad resynchronization")
            synchronizeAllPadConfigurations()
            detectPadAssignments()
        }
    }

    /**
     * Get current synchronization status
     */
    fun getSyncStatus(): PadSyncStatus {
        val syncState = _padSyncState.value
        return when {
            !syncState.isAudioEngineReady -> PadSyncStatus.AUDIO_ENGINE_NOT_READY
            syncState.syncErrors > 0 -> PadSyncStatus.SYNC_ERRORS
            syncState.assignedPads == 0 -> PadSyncStatus.NO_ASSIGNMENTS
            syncState.syncedPads < syncState.assignedPads -> PadSyncStatus.PARTIAL_SYNC
            else -> PadSyncStatus.FULLY_SYNCED
        }
    }
}

/**
 * Playback modes for pad system integration
 */
enum class PlaybackMode {
    LIVE,      // Live pad triggering mode
    SEQUENCER  // Sequencer playback mode
}

/**
 * Pad information optimized for sequencer use
 */
data class SequencerPadInfo(
    val index: Int,
    val sampleId: String?,
    val sampleName: String?,
    val hasAssignedSample: Boolean,
    val isEnabled: Boolean,
    val volume: Float,
    val pan: Float,
    val playbackMode: com.high.theone.model.PlaybackMode,
    val chokeGroup: Int?,
    val isLoading: Boolean,
    val canTrigger: Boolean
) {
    /**
     * Get display name for sequencer UI
     */
    val displayName: String
        get() = sampleName ?: "Pad ${index + 1}"
    
    /**
     * Check if pad is ready for sequencer use
     */
    val isSequencerReady: Boolean
        get() = hasAssignedSample && isEnabled && !isLoading && canTrigger
}

/**
 * Pad synchronization state
 */
data class PadSyncState(
    val isAudioEngineReady: Boolean = false,
    val currentMode: PlaybackMode = PlaybackMode.LIVE,
    val totalPads: Int = 0,
    val assignedPads: Int = 0,
    val enabledPads: Int = 0,
    val syncedPads: Int = 0,
    val syncErrors: Int = 0,
    val lastSyncTime: Long = 0L,
    val lastModeSwitch: Long = 0L,
    val lastAssignmentDetection: Long = 0L,
    val lastError: String? = null,
    val detectedAssignments: List<PadAssignmentInfo> = emptyList()
)

/**
 * Information about detected pad assignments
 */
data class PadAssignmentInfo(
    val padIndex: Int,
    val sampleId: String,
    val sampleName: String,
    val isActive: Boolean,
    val lastDetected: Long
)

/**
 * Pad synchronization status
 */
enum class PadSyncStatus {
    AUDIO_ENGINE_NOT_READY,
    NO_ASSIGNMENTS,
    PARTIAL_SYNC,
    SYNC_ERRORS,
    FULLY_SYNCED
}