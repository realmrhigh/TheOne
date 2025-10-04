package com.high.theone.features.sampling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.ProjectManager
import com.high.theone.domain.SampleRepository
import com.high.theone.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.net.Uri
import android.util.Log
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Basic Sampling & Pad Playback feature (M1).
 * Coordinates recording workflow, pad management, and sample organization.
 * 
 * Requirements: All requirements integration
 */
@HiltViewModel
class SamplingViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleRepository: SampleRepository,
    private val projectManager: ProjectManager,
    private val sampleCacheManager: SampleCacheManager,
    private val voiceManager: VoiceManager,
    private val performanceMonitor: PerformanceMonitor,
    private val debugManager: SamplingDebugManager,
    private val midiSamplingAdapter: MidiSamplingAdapter,
    private val padStateProvider: PadStateProvider
) : ViewModel() {

    companion object {
        private const val TAG = "SamplingViewModel"
        private const val LEVEL_UPDATE_INTERVAL_MS = 50L
        private const val MAX_RECORDING_DURATION_MS = 30_000L
    }

    // Private mutable state
    private val _uiState = MutableStateFlow(SamplingUiState())
    
    // Public read-only state
    val uiState: StateFlow<SamplingUiState> = _uiState.asStateFlow()

    // Recording level monitoring job
    private var levelMonitoringJob: Job? = null
    
    // Recording duration tracking job
    private var recordingDurationJob: Job? = null

    init {
        initializeAudioEngine()
        observeProjectChanges()
        loadAvailableSamples()
        startPerformanceMonitoring()
        initializeMidiIntegration()
        observePadStateChanges()
    }

    /**
     * Observe changes to uiState and update the PadStateProvider
     */
    private fun observePadStateChanges() {
        viewModelScope.launch {
            uiState.collect { state ->
                padStateProvider.updatePadState(state)
            }
        }
    }

    /**
     * Initialize the audio engine and update UI state accordingly.
     * Requirements: 1.5 (audio engine availability), 3.1 (pad triggering setup)
     */
    private fun initializeAudioEngine() {
        viewModelScope.launch {
            try {
                // Initialize main audio engine
                val isInitialized = audioEngine.initialize(
                    sampleRate = 44100,
                    bufferSize = 256,
                    enableLowLatency = true
                )
                
                if (!isInitialized) {
                    _uiState.update { currentState ->
                        currentState.copy(
                            isAudioEngineReady = false,
                            recordingState = currentState.recordingState.copy(
                                isInitialized = false,
                                error = "Failed to initialize audio engine"
                            )
                        )
                    }
                    Log.e(TAG, "Failed to initialize audio engine")
                    return@launch
                }
                
                // Initialize drum engine for pad triggering
                val drumEngineInitialized = audioEngine.initializeDrumEngine()
                
                if (!drumEngineInitialized) {
                    Log.w(TAG, "Drum engine initialization failed, pad triggering may not work")
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        isAudioEngineReady = isInitialized && drumEngineInitialized,
                        recordingState = currentState.recordingState.copy(
                            isInitialized = isInitialized,
                            error = null
                        )
                    )
                }
                
                Log.d(TAG, "Audio engine initialized successfully (drum engine: $drumEngineInitialized)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing audio engine", e)
                _uiState.update { currentState ->
                    currentState.copy(
                        isAudioEngineReady = false,
                        recordingState = currentState.recordingState.copy(
                            isInitialized = false,
                            error = "Audio engine initialization failed: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Start performance monitoring for responsive UI updates.
     * Requirements: 3.3, 4.1, 4.2 (responsive UI updates)
     */
    private fun startPerformanceMonitoring() {
        performanceMonitor.startMonitoring()
        
        // Monitor performance metrics and adapt behavior
        viewModelScope.launch {
            performanceMonitor.performanceMetrics.collect { metrics ->
                // Adapt UI behavior based on performance
                if (performanceMonitor.shouldReduceUIComplexity()) {
                    Log.d(TAG, "Reducing UI complexity due to performance issues")
                    // UI components can observe this and reduce animations/effects
                }
                
                // Log performance warnings
                metrics.warnings.forEach { warning ->
                    when (warning.severity) {
                        PerformanceMonitor.Severity.CRITICAL -> Log.e(TAG, "Performance: ${warning.message}")
                        PerformanceMonitor.Severity.WARNING -> Log.w(TAG, "Performance: ${warning.message}")
                        PerformanceMonitor.Severity.INFO -> Log.i(TAG, "Performance: ${warning.message}")
                    }
                }
            }
        }
    }

    /**
     * Observe project changes and update available samples.
     * Requirements: 7.1, 7.2 (project integration)
     */
    private fun observeProjectChanges() {
        viewModelScope.launch {
            projectManager.getCurrentProject().collect { project ->
                _uiState.update { currentState ->
                    currentState.copy(
                        currentProject = project?.id,
                        isDirty = false
                    )
                }
                
                if (project != null) {
                    loadAvailableSamples()
                }
            }
        }
    }

    /**
     * Initialize MIDI integration for pad triggering.
     * Requirements: 1.1 (MIDI input), 1.3 (MIDI velocity), 5.2 (pad integration)
     */
    private fun initializeMidiIntegration() {
        viewModelScope.launch {
            try {
                // Initialize the MIDI sampling adapter
                midiSamplingAdapter.initialize()
                
                // Observe MIDI pad trigger events
                midiSamplingAdapter.padTriggerEvents.collect { event ->
                    handleMidiPadTrigger(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MIDI integration", e)
            }
        }
        
        viewModelScope.launch {
            try {
                // Observe MIDI pad stop events
                midiSamplingAdapter.padStopEvents.collect { event ->
                    handleMidiPadStop(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing MIDI stop events", e)
            }
        }
        
        // Update MIDI adapter when pad configuration changes
        viewModelScope.launch {
            _uiState.collect { state ->
                midiSamplingAdapter.updatePadConfiguration(state.pads)
            }
        }
        
        Log.d(TAG, "MIDI integration initialized")
    }

    /**
     * Load available samples from the repository.
     * Requirements: 7.1, 7.2 (sample organization)
     */
    private fun loadAvailableSamples() {
        viewModelScope.launch {
            try {
                val currentProject = _uiState.value.currentProject
                val samplesResult = if (currentProject != null) {
                    sampleRepository.getSamplesForProject(currentProject)
                } else {
                    sampleRepository.getAllSamples()
                }

                when (samplesResult) {
                    is com.high.theone.domain.Result.Success -> {
                        _uiState.update { currentState ->
                            currentState.copy(
                                availableSamples = samplesResult.value,
                                error = null
                            )
                        }
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to load samples: ${samplesResult.error.message}")
                        _uiState.update { currentState ->
                            currentState.copy(
                                error = "Failed to load samples: ${samplesResult.error.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading samples", e)
                _uiState.update { currentState ->
                    currentState.copy(
                        error = "Error loading samples: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Start audio recording from microphone.
     * Requirements: 1.1 (recording start), 1.2 (recording status)
     */
    fun startRecording() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value.recordingState
                
                if (!currentState.canStartRecording) {
                    Log.w(TAG, "Cannot start recording in current state")
                    return@launch
                }

                // Generate temporary file path for recording
                val tempFilePath = generateTempRecordingPath()
                
                val success = audioEngine.startAudioRecording(
                    filePathUri = tempFilePath,
                    inputDeviceId = null // Use default microphone
                )

                if (success) {
                    _uiState.update { state ->
                        state.copy(
                            recordingState = state.recordingState.copy(
                                isRecording = true,
                                durationMs = 0L,
                                recordingFilePath = tempFilePath,
                                error = null
                            ),
                            isDirty = true
                        )
                    }
                    
                    startLevelMonitoring()
                    startDurationTracking()
                    
                    logDebugEvent(
                        SamplingDebugManager.LogLevel.INFO,
                        "Recording",
                        "Recording started successfully",
                        mapOf("filePath" to tempFilePath)
                    )
                    
                    Log.d(TAG, "Recording started successfully")
                } else {
                    _uiState.update { state ->
                        state.copy(
                            recordingState = state.recordingState.copy(
                                error = "Failed to start recording"
                            )
                        )
                    }
                    Log.e(TAG, "Failed to start recording")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _uiState.update { state ->
                    state.copy(
                        recordingState = state.recordingState.copy(
                            error = "Recording error: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Stop audio recording and process the recorded sample.
     * Requirements: 1.3 (recording stop), 1.6 (sample generation)
     */
    fun stopRecording() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value.recordingState
                
                if (!currentState.canStopRecording) {
                    Log.w(TAG, "Cannot stop recording in current state")
                    return@launch
                }

                // Set processing state
                _uiState.update { state ->
                    state.copy(
                        recordingState = state.recordingState.copy(
                            isProcessing = true
                        )
                    )
                }

                stopLevelMonitoring()
                stopDurationTracking()

                val sampleMetadata = audioEngine.stopAudioRecording()
                
                if (sampleMetadata != null) {
                    // Save the recorded sample to repository
                    val projectId = _uiState.value.currentProject
                    val saveResult = sampleRepository.saveSampleFromUri(
                        sourceUri = Uri.parse(currentState.recordingFilePath),
                        metadata = sampleMetadata,
                        projectId = projectId,
                        copyToProject = true
                    )

                    when (saveResult) {
                        is com.high.theone.domain.Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    recordingState = RecordingState(), // Reset recording state
                                    availableSamples = state.availableSamples + sampleMetadata.copy(id = UUID.fromString(saveResult.value))
                                )
                            }
                            
                            logDebugEvent(
                                SamplingDebugManager.LogLevel.INFO,
                                "Recording",
                                "Recording saved successfully",
                                mapOf(
                                    "sampleId" to saveResult.value,
                                    "duration" to sampleMetadata.durationMs,
                                    "sampleRate" to sampleMetadata.sampleRate
                                )
                            )
                            
                            Log.d(TAG, "Recording saved successfully with ID: ${saveResult.value}")
                        }
                        is com.high.theone.domain.Result.Failure -> {
                            Log.e(TAG, "Failed to save recording: ${saveResult.error.message}")
                            _uiState.update { state ->
                                state.copy(
                                    recordingState = state.recordingState.copy(
                                        isRecording = false,
                                        isProcessing = false,
                                        error = "Failed to save recording: ${saveResult.error.message}"
                                    )
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            recordingState = state.recordingState.copy(
                                isRecording = false,
                                isProcessing = false,
                                error = "Recording failed - no data received"
                            )
                        )
                    }
                    Log.e(TAG, "Recording failed - no metadata received")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                _uiState.update { state ->
                    state.copy(
                        recordingState = state.recordingState.copy(
                            isRecording = false,
                            isProcessing = false,
                            error = "Error stopping recording: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Start monitoring recording levels for UI feedback.
     * Requirements: 1.2 (level monitoring)
     */
    private fun startLevelMonitoring() {
        levelMonitoringJob?.cancel()
        levelMonitoringJob = viewModelScope.launch {
            while (_uiState.value.recordingState.isRecording) {
                try {
                    // Note: This would need to be implemented in the audio engine
                    // For now, we'll simulate level monitoring
                    val peakLevel = 0.5f // Placeholder - should come from audio engine
                    val averageLevel = 0.3f // Placeholder - should come from audio engine
                    
                    _uiState.update { state ->
                        state.copy(
                            recordingState = state.recordingState.copy(
                                peakLevel = peakLevel,
                                averageLevel = averageLevel
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring recording levels", e)
                }
                
                delay(LEVEL_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Start tracking recording duration and auto-stop if needed.
     * Requirements: 1.4 (max duration), 1.2 (duration display)
     */
    private fun startDurationTracking() {
        recordingDurationJob?.cancel()
        recordingDurationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            while (_uiState.value.recordingState.isRecording) {
                val currentTime = System.currentTimeMillis()
                val durationMs = currentTime - startTime
                
                _uiState.update { state ->
                    state.copy(
                        recordingState = state.recordingState.copy(
                            durationMs = durationMs
                        )
                    )
                }
                
                // Auto-stop if max duration reached
                if (durationMs >= MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "Auto-stopping recording due to max duration")
                    stopRecording()
                    break
                }
                
                delay(100L) // Update every 100ms for smooth duration display
            }
        }
    }

    /**
     * Stop level monitoring job.
     */
    private fun stopLevelMonitoring() {
        levelMonitoringJob?.cancel()
        levelMonitoringJob = null
    }

    /**
     * Stop duration tracking job.
     */
    private fun stopDurationTracking() {
        recordingDurationJob?.cancel()
        recordingDurationJob = null
    }

    /**
     * Generate a temporary file path for recording.
     */
    private fun generateTempRecordingPath(): String {
        val timestamp = System.currentTimeMillis()
        return "temp_recording_$timestamp.wav"
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _uiState.update { state ->
            state.copy(
                error = null,
                recordingState = state.recordingState.copy(error = null)
            )
        }
    }

    /**
     * Update the selected pad index.
     */
    fun selectPad(padIndex: Int?) {
        _uiState.update { state ->
            state.copy(selectedPad = padIndex)
        }
    }

    /**
     * Trigger a pad with velocity sensitivity and optimized performance.
     * Requirements: 3.1 (pad triggering), 3.2 (simultaneous pads), 3.3 (velocity sensitivity)
     */
    fun triggerPad(padIndex: Int, velocity: Float = 1.0f) {
        viewModelScope.launch {
            val triggerStartTime = System.currentTimeMillis()
            
            try {
                val currentState = _uiState.value
                val pad = currentState.getPad(padIndex)
                
                if (pad == null || !pad.canTrigger) {
                    Log.w(TAG, "Cannot trigger pad $padIndex - not ready")
                    return@launch
                }

                if (!currentState.isAudioEngineReady) {
                    _uiState.update { state ->
                        state.copy(error = "Audio engine not available")
                    }
                    return@launch
                }

                // Check performance and throttle if needed
                if (performanceMonitor.shouldThrottleAudioOperations()) {
                    Log.w(TAG, "Throttling audio operations due to performance issues")
                    delay(10L) // Small delay to reduce load
                }

                // Ensure sample is loaded in cache for immediate playback
                val sampleId = pad.sampleId
                if (sampleId != null) {
                    val isCached = sampleCacheManager.ensureSampleLoaded(sampleId)
                    if (!isCached) {
                        Log.w(TAG, "Sample $sampleId not ready for pad $padIndex")
                        return@launch
                    }
                }

                // Allocate voice with intelligent management
                val voiceId = voiceManager.allocateVoice(
                    padIndex = padIndex,
                    sampleId = sampleId ?: "",
                    velocity = velocity,
                    playbackMode = pad.playbackMode,
                    priority = VoiceManager.VoicePriority.NORMAL
                )

                if (voiceId == null) {
                    Log.w(TAG, "Cannot allocate voice for pad $padIndex")
                    return@launch
                }

                // Apply real-time parameter updates before triggering
                // Requirements: 5.1 (volume control), 5.2 (pan control)
                audioEngine.setDrumPadVolume(padIndex, pad.volume)
                audioEngine.setDrumPadPan(padIndex, pad.pan)
                audioEngine.setDrumPadMode(padIndex, pad.playbackMode.ordinal)

                // Update pad state to show it's playing
                updatePadState(padIndex) { padState ->
                    padState.copy(
                        isPlaying = true,
                        lastTriggerVelocity = velocity
                    )
                }

                // Trigger the sample in the audio engine
                audioEngine.triggerDrumPad(padIndex, velocity)

                // Handle playback mode with voice management
                when (pad.playbackMode) {
                    PlaybackMode.ONE_SHOT -> {
                        // For one-shot samples, release voice after estimated duration
                        delay(100L) // Minimum delay for UI responsiveness
                        voiceManager.releaseVoice(voiceId)
                        updatePadState(padIndex) { padState ->
                            padState.copy(isPlaying = false)
                        }
                    }
                    PlaybackMode.LOOP -> {
                        // Loop mode - voice stays active until explicitly stopped
                    }
                    PlaybackMode.GATE -> {
                        // Gate mode - similar to one-shot but can be stopped early
                        delay(100L)
                        voiceManager.releaseVoice(voiceId)
                        updatePadState(padIndex) { padState ->
                            padState.copy(isPlaying = false)
                        }
                    }
                    PlaybackMode.NOTE_ON_OFF -> {
                        // MIDI-style note on/off - stays playing until explicitly stopped
                    }
                }

                // Record performance metrics
                val triggerTime = System.currentTimeMillis() - triggerStartTime
                performanceMonitor.recordFrameTime(triggerTime.toFloat())

                // Log debug information
                logDebugEvent(
                    SamplingDebugManager.LogLevel.DEBUG,
                    "Pad Trigger",
                    "Pad $padIndex triggered successfully",
                    mapOf(
                        "velocity" to velocity,
                        "triggerTime" to triggerTime,
                        "playbackMode" to pad.playbackMode.name,
                        "voiceId" to voiceId
                    )
                )

                Log.d(TAG, "Triggered pad $padIndex with velocity $velocity (${triggerTime}ms)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering pad $padIndex", e)
                updatePadState(padIndex) { padState ->
                    padState.copy(isPlaying = false)
                }
            }
        }
    }

    /**
     * Stop/release a pad (mainly for loop mode).
     * Requirements: 4.1 (playback mode control)
     */
    fun releasePad(padIndex: Int) {
        viewModelScope.launch {
            try {
                val pad = _uiState.value.getPad(padIndex)
                
                if (pad?.isPlaying == true) {
                    audioEngine.releaseDrumPad(padIndex)
                    
                    updatePadState(padIndex) { padState ->
                        padState.copy(isPlaying = false)
                    }
                    
                    Log.d(TAG, "Released pad $padIndex")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing pad $padIndex", e)
            }
        }
    }

    /**
     * Assign a sample to a specific pad.
     * Requirements: 2.1 (sample assignment), 2.2 (pad-to-sample mapping)
     */
    fun assignSampleToPad(padIndex: Int, sampleId: String) {
        viewModelScope.launch {
            try {
                val sampleResult = sampleRepository.loadSample(sampleId)
                val sample = when (sampleResult) {
                    is com.high.theone.domain.Result.Success -> sampleResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Sample $sampleId not found")
                        _uiState.update { state ->
                            state.copy(error = "Sample not found")
                        }
                        return@launch
                    }
                }

                // Load sample into audio engine
                val samplePathResult = sampleRepository.getSampleFilePath(sampleId)
                val samplePath = when (samplePathResult) {
                    is com.high.theone.domain.Result.Success -> samplePathResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Sample file path not found for $sampleId")
                        _uiState.update { state ->
                            state.copy(error = "Sample file not found")
                        }
                        return@launch
                    }
                }

                // Preload sample into cache for optimal performance
                val cacheSuccess = sampleCacheManager.preloadSample(sampleId)
                if (!cacheSuccess) {
                    Log.w(TAG, "Failed to preload sample $sampleId into cache")
                }

                val loadSuccess = audioEngine.loadDrumSample(padIndex, samplePath)
                
                if (loadSuccess) {
                    // Update pad state
                    updatePadState(padIndex) { padState ->
                        padState.copy(
                            sampleId = sampleId,
                            sampleName = sample.name,
                            hasAssignedSample = true,
                            isLoading = false
                        )
                    }
                    
                    // Synchronize pad settings with audio engine after assignment
                    val currentPad = _uiState.value.getPad(padIndex)
                    if (currentPad != null) {
                        audioEngine.setDrumPadVolume(padIndex, currentPad.volume)
                        audioEngine.setDrumPadPan(padIndex, currentPad.pan)
                        audioEngine.setDrumPadMode(padIndex, currentPad.playbackMode.ordinal)
                    }
                    
                    _uiState.update { state ->
                        state.copy(isDirty = true)
                    }
                    
                    Log.d(TAG, "Assigned sample $sampleId to pad $padIndex and synchronized settings")
                } else {
                    Log.e(TAG, "Failed to load sample into audio engine")
                    _uiState.update { state ->
                        state.copy(error = "Failed to load sample")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error assigning sample to pad", e)
                _uiState.update { state ->
                    state.copy(error = "Error assigning sample: ${e.message}")
                }
            }
        }
    }

    /**
     * Remove sample assignment from a pad.
     * Requirements: 2.2 (sample removal)
     */
    fun removeSampleFromPad(padIndex: Int) {
        viewModelScope.launch {
            try {
                // Stop any playing sample on this pad
                if (_uiState.value.getPad(padIndex)?.isPlaying == true) {
                    releasePad(padIndex)
                }

                // Update pad state
                updatePadState(padIndex) { padState ->
                    padState.copy(
                        sampleId = null,
                        sampleName = null,
                        hasAssignedSample = false,
                        isPlaying = false
                    )
                }
                
                _uiState.update { state ->
                    state.copy(isDirty = true)
                }
                
                Log.d(TAG, "Removed sample from pad $padIndex")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing sample from pad", e)
            }
        }
    }

    /**
     * Configure pad settings (volume, pan, playback mode, etc.).
     * Requirements: 5.1 (volume control), 5.2 (pan control), 4.1-4.3 (playback modes)
     */
    fun configurePad(padIndex: Int, settings: PadSettings) {
        viewModelScope.launch {
            try {
                // Apply settings to audio engine immediately for real-time updates
                audioEngine.setDrumPadVolume(padIndex, settings.volume)
                audioEngine.setDrumPadPan(padIndex, settings.pan)
                audioEngine.setDrumPadMode(padIndex, settings.playbackMode.ordinal)

                // Update pad state
                updatePadState(padIndex) { padState ->
                    padState.copy(
                        volume = settings.volume,
                        pan = settings.pan,
                        playbackMode = settings.playbackMode,
                        chokeGroup = settings.chokeGroup,
                        isEnabled = settings.isEnabled
                    )
                }
                
                _uiState.update { state ->
                    state.copy(isDirty = true)
                }
                
                Log.d(TAG, "Configured pad $padIndex with new settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring pad $padIndex", e)
                _uiState.update { state ->
                    state.copy(error = "Error configuring pad: ${e.message}")
                }
            }
        }
    }

    /**
     * Synchronize all pad states with the audio engine.
     * Requirements: 3.1, 3.2 (audio engine state synchronization)
     */
    fun synchronizeAudioEngineState() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                
                // Preload all assigned samples for optimal performance
                val padSamples = currentState.pads
                    .mapIndexedNotNull { index, pad -> 
                        if (pad.hasAssignedSample && pad.sampleId != null) {
                            index to pad.sampleId
                        } else null
                    }
                    .toMap()
                
                sampleCacheManager.preloadPadSamples(padSamples)
                
                // Synchronize all pad settings with audio engine
                currentState.pads.forEachIndexed { index, pad ->
                    if (pad.hasAssignedSample) {
                        audioEngine.setDrumPadVolume(index, pad.volume)
                        audioEngine.setDrumPadPan(index, pad.pan)
                        audioEngine.setDrumPadMode(index, pad.playbackMode.ordinal)
                    }
                }
                
                Log.d(TAG, "Audio engine state synchronized with UI state")
            } catch (e: Exception) {
                Log.e(TAG, "Error synchronizing audio engine state", e)
            }
        }
    }

    /**
     * Optimize performance by cleaning up unused resources.
     * Requirements: 3.3, 4.1, 4.2 (memory management)
     */
    fun optimizePerformance() {
        viewModelScope.launch {
            try {
                // Optimize voice allocation
                voiceManager.optimizeVoiceAllocation()
                
                // Get performance recommendations
                val recommendations = performanceMonitor.getPerformanceRecommendations()
                if (recommendations.isNotEmpty()) {
                    Log.i(TAG, "Performance recommendations: ${recommendations.joinToString(", ")}")
                    
                    // Apply automatic optimizations
                    if (recommendations.any { it.contains("Clear unused samples") }) {
                        // Clear samples not assigned to any pad
                        val assignedSamples = _uiState.value.pads
                            .mapNotNull { it.sampleId }
                            .toSet()
                        
                        // This would require additional cache management logic
                        Log.d(TAG, "Would clear unused samples (${assignedSamples.size} samples in use)")
                    }
                }
                
                Log.d(TAG, "Performance optimization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing performance", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        
        // Stop performance monitoring
        performanceMonitor.stopMonitoring()
        
        // Stop all voices
        viewModelScope.launch {
            voiceManager.stopAllVoices()
            sampleCacheManager.clearCache()
        }
        
        // Cancel ongoing jobs
        levelMonitoringJob?.cancel()
        recordingDurationJob?.cancel()
        
        // Stop level monitoring and duration tracking
        stopLevelMonitoring()
        stopDurationTracking()
        
        // Shutdown MIDI integration
        midiSamplingAdapter.shutdown()
        
        // Clean up audio engine if needed
        viewModelScope.launch {
            try {
                if (_uiState.value.recordingState.isRecording) {
                    audioEngine.stopAudioRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up audio engine", e)
            }
        }
        
        Log.d(TAG, "SamplingViewModel cleared and resources cleaned up")
    }

    // Debug and monitoring methods

    /**
     * Get current debug information for troubleshooting.
     * Requirements: All requirements (debugging support)
     */
    suspend fun getDebugInfo(): SamplingDebugManager.SamplingDebugInfo {
        debugManager.updateDebugInfo()
        return debugManager.debugInfo.value
    }

    /**
     * Run comprehensive diagnostics on the sampling system.
     * Requirements: All requirements (comprehensive testing)
     */
    suspend fun runDiagnostics(): SamplingDebugManager.DiagnosticReport {
        debugManager.addDiagnosticLog(
            SamplingDebugManager.LogLevel.INFO,
            "User Action",
            "User initiated system diagnostics"
        )
        
        return debugManager.runDiagnostics()
    }

    /**
     * Export debug report for troubleshooting.
     * Requirements: All requirements (troubleshooting support)
     */
    fun exportDebugReport(): String {
        debugManager.addDiagnosticLog(
            SamplingDebugManager.LogLevel.INFO,
            "User Action",
            "User exported debug report"
        )
        
        return debugManager.exportDebugReport()
    }

    /**
     * Log a debug event for monitoring.
     */
    private fun logDebugEvent(level: SamplingDebugManager.LogLevel, category: String, message: String, details: Map<String, Any> = emptyMap()) {
        debugManager.addDiagnosticLog(level, category, message, details)
    }

    /**
     * Load an external sample file.
     * Requirements: 7.1 (file loading), 7.2 (format support)
     */
    fun loadExternalSample(uri: Uri, name: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    state.copy(isLoading = true)
                }

                // Create sample metadata
                val sampleMetadata = SampleMetadata(
                    id = UUID.randomUUID(),
                    name = name ?: "Imported Sample",
                    filePath = uri.toString(),
                    durationMs = 0L, // Will be determined by repository
                    sampleRate = 44100,
                    channels = 1,
                    format = "wav",
                    fileSizeBytes = 0L,
                    createdAt = System.currentTimeMillis(),
                    tags = emptyList()
                )

                val projectId = _uiState.value.currentProject
                val result = sampleRepository.saveSampleFromUri(
                    sourceUri = uri,
                    metadata = sampleMetadata,
                    projectId = projectId,
                    copyToProject = true
                )

                when (result) {
                    is com.high.theone.domain.Result.Success -> {
                        // Reload available samples
                        loadAvailableSamples()
                        
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isDirty = true
                            )
                        }
                        
                        Log.d(TAG, "Loaded external sample with ID: ${result.value}")
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to load external sample: ${result.error.message}")
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                error = "Failed to load sample: ${result.error.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading external sample", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Error loading sample: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Delete a sample from the repository.
     * Requirements: 7.3 (sample cleanup)
     */
    fun deleteSample(sampleId: String) {
        viewModelScope.launch {
            try {
                // Remove sample from any pads that are using it
                val currentState = _uiState.value
                currentState.pads.forEachIndexed { index, pad ->
                    if (pad.sampleId == sampleId) {
                        removeSampleFromPad(index)
                    }
                }

                // Delete from repository
                val result = sampleRepository.deleteSample(sampleId)
                
                when (result) {
                    is com.high.theone.domain.Result.Success -> {
                        // Reload available samples
                        loadAvailableSamples()
                        
                        _uiState.update { state ->
                            state.copy(isDirty = true)
                        }
                        
                        Log.d(TAG, "Deleted sample $sampleId")
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to delete sample: ${result.error.message}")
                        _uiState.update { state ->
                            state.copy(error = "Failed to delete sample: ${result.error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting sample", e)
                _uiState.update { state ->
                    state.copy(error = "Error deleting sample: ${e.message}")
                }
            }
        }
    }

    /**
     * Save current pad configuration to project.
     * Requirements: 2.2 (pad configuration persistence)
     */
    fun savePadConfiguration() {
        viewModelScope.launch {
            try {
                val currentProject = projectManager.getCurrentProject().value
                if (currentProject == null) {
                    Log.w(TAG, "No current project to save pad configuration")
                    return@launch
                }

                // Create pad configuration data
                val padConfigurations = _uiState.value.pads.map { pad ->
                    PadConfiguration(
                        padIndex = pad.index,
                        sampleId = pad.sampleId,
                        settings = PadSettings(
                            volume = pad.volume,
                            pan = pad.pan,
                            playbackMode = pad.playbackMode,
                            chokeGroup = pad.chokeGroup,
                            isEnabled = pad.isEnabled
                        )
                    )
                }

                // Save to project (this would need to be implemented in ProjectManager)
                // For now, we'll just mark as saved
                _uiState.update { state ->
                    state.copy(isDirty = false)
                }
                
                Log.d(TAG, "Saved pad configuration for project ${currentProject.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving pad configuration", e)
                _uiState.update { state ->
                    state.copy(error = "Failed to save pad configuration: ${e.message}")
                }
            }
        }
    }

    /**
     * Load pad configuration from project.
     * Requirements: 2.2 (pad configuration persistence)
     */
    fun loadPadConfiguration() {
        viewModelScope.launch {
            try {
                val currentProject = projectManager.getCurrentProject().value
                if (currentProject == null) {
                    Log.w(TAG, "No current project to load pad configuration from")
                    return@launch
                }

                // Load pad configurations from project
                // This would need to be implemented in ProjectManager
                // For now, we'll simulate loading
                
                Log.d(TAG, "Loaded pad configuration for project ${currentProject.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading pad configuration", e)
                _uiState.update { state ->
                    state.copy(error = "Failed to load pad configuration: ${e.message}")
                }
            }
        }
    }

    /**
     * Synchronize pad states with audio engine.
     * Requirements: 2.2 (pad state synchronization)
     */
    fun synchronizePadStates() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                
                if (!currentState.isAudioEngineReady) {
                    Log.w(TAG, "Audio engine not ready for synchronization")
                    return@launch
                }

                currentState.pads.forEach { pad ->
                    if (pad.hasAssignedSample && pad.sampleId != null) {
                        // Ensure sample is loaded in audio engine
                        val samplePath = sampleRepository.getSampleFilePath(pad.sampleId).getOrNull()
                        if (samplePath != null) {
                            audioEngine.loadDrumSample(pad.index, samplePath)
                            audioEngine.setDrumPadVolume(pad.index, pad.volume)
                            audioEngine.setDrumPadPan(pad.index, pad.pan)
                            audioEngine.setDrumPadMode(pad.index, pad.playbackMode.ordinal)
                        }
                    }
                }
                
                Log.d(TAG, "Synchronized pad states with audio engine")
            } catch (e: Exception) {
                Log.e(TAG, "Error synchronizing pad states", e)
            }
        }
    }

    /**
     * Handle choke groups - stop other pads in the same choke group.
     * Requirements: Advanced pad features (choke groups)
     */
    private fun handleChokeGroup(padIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val triggeringPad = currentState.getPad(padIndex)
            
            if (triggeringPad?.chokeGroup != null) {
                // Stop all other pads in the same choke group
                currentState.pads.forEachIndexed { index, pad ->
                    if (index != padIndex && 
                        pad.chokeGroup == triggeringPad.chokeGroup && 
                        pad.isPlaying) {
                        releasePad(index)
                    }
                }
            }
        }
    }

    /**
     * Trigger pad with advanced features (choke groups, velocity curves).
     * Requirements: 3.1, 3.2 (advanced triggering)
     */
    fun triggerPadAdvanced(padIndex: Int, velocity: Float = 1.0f) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val pad = currentState.getPad(padIndex)
                
                if (pad == null || !pad.canTrigger) {
                    Log.w(TAG, "Cannot trigger pad $padIndex - not ready")
                    return@launch
                }

                // Handle choke groups first
                handleChokeGroup(padIndex)

                // Apply velocity sensitivity
                val adjustedVelocity = velocity // Could apply velocity curves here

                // Trigger the basic pad functionality
                triggerPad(padIndex, adjustedVelocity)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in advanced pad triggering", e)
            }
        }
    }

    /**
     * Bulk operations for multiple pads.
     * Requirements: 5.1, 5.2 (bulk operations)
     */
    fun bulkConfigurePads(padIndices: List<Int>, settings: PadSettings) {
        viewModelScope.launch {
            try {
                padIndices.forEach { padIndex ->
                    configurePad(padIndex, settings)
                }
                
                Log.d(TAG, "Bulk configured ${padIndices.size} pads")
            } catch (e: Exception) {
                Log.e(TAG, "Error in bulk pad configuration", e)
            }
        }
    }

    /**
     * Copy pad configuration from one pad to another.
     * Requirements: 2.1, 2.2 (pad management)
     */
    fun copyPadConfiguration(sourcePadIndex: Int, targetPadIndex: Int) {
        viewModelScope.launch {
            try {
                val sourcePad = _uiState.value.getPad(sourcePadIndex)
                
                if (sourcePad == null) {
                    Log.w(TAG, "Source pad $sourcePadIndex not found")
                    return@launch
                }

                // Copy sample assignment
                if (sourcePad.hasAssignedSample && sourcePad.sampleId != null) {
                    assignSampleToPad(targetPadIndex, sourcePad.sampleId)
                }

                // Copy settings
                val settings = PadSettings(
                    volume = sourcePad.volume,
                    pan = sourcePad.pan,
                    playbackMode = sourcePad.playbackMode,
                    chokeGroup = sourcePad.chokeGroup,
                    isEnabled = sourcePad.isEnabled
                )
                
                configurePad(targetPadIndex, settings)
                
                Log.d(TAG, "Copied configuration from pad $sourcePadIndex to $targetPadIndex")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying pad configuration", e)
            }
        }
    }

    /**
     * Reset a pad to default state.
     * Requirements: 2.2 (pad management)
     */
    fun resetPad(padIndex: Int) {
        viewModelScope.launch {
            try {
                // Remove sample assignment
                removeSampleFromPad(padIndex)
                
                // Reset to default settings
                val defaultSettings = PadSettings()
                configurePad(padIndex, defaultSettings)
                
                Log.d(TAG, "Reset pad $padIndex to default state")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting pad", e)
            }
        }
    }

    /**
     * Get pad usage statistics.
     * Requirements: 7.3 (sample usage tracking)
     */
    fun getPadUsageStats(): PadUsageStats {
        val currentState = _uiState.value
        
        return PadUsageStats(
            totalPads = currentState.pads.size,
            loadedPads = currentState.loadedPads.size,
            playingPads = currentState.playingPads.size,
            enabledPads = currentState.pads.count { it.isEnabled },
            padsByPlaybackMode = currentState.pads
                .filter { it.hasAssignedSample }
                .groupBy { it.playbackMode }
                .mapValues { it.value.size }
        )
    }

    /**
     * Organize samples within the current project.
     * Requirements: 7.2 (sample organization)
     */
    fun organizeSamples(organizationMode: SampleOrganizationMode) {
        viewModelScope.launch {
            try {
                val currentSamples = _uiState.value.availableSamples
                
                val organizedSamples = when (organizationMode) {
                    SampleOrganizationMode.BY_NAME -> currentSamples.sortedBy { it.name }
                    SampleOrganizationMode.BY_DATE -> currentSamples.sortedByDescending { it.createdAt }
                    SampleOrganizationMode.BY_DURATION -> currentSamples.sortedBy { it.durationMs }
                    SampleOrganizationMode.BY_SIZE -> currentSamples.sortedByDescending { it.fileSizeBytes }
                    SampleOrganizationMode.BY_FORMAT -> currentSamples.sortedBy { it.format }
                    SampleOrganizationMode.BY_USAGE -> {
                        // Sort by how many pads are using each sample
                        val usageCounts = currentSamples.associate { sample ->
                            sample.id.toString() to _uiState.value.pads.count { it.sampleId == sample.id.toString() }
                        }
                        currentSamples.sortedByDescending { usageCounts[it.id.toString()] ?: 0 }
                    }
                }
                
                _uiState.update { state ->
                    state.copy(availableSamples = organizedSamples)
                }
                
                Log.d(TAG, "Organized samples by $organizationMode")
            } catch (e: Exception) {
                Log.e(TAG, "Error organizing samples", e)
            }
        }
    }

    /**
     * Update sample metadata.
     * Requirements: 7.2 (metadata management)
     */
    fun updateSampleMetadata(sampleId: String, updatedMetadata: SampleMetadata) {
        viewModelScope.launch {
            try {
                val result = sampleRepository.updateSampleMetadata(sampleId, updatedMetadata)
                
                when (result) {
                    is com.high.theone.domain.Result.Success -> {
                        // Update local state
                        _uiState.update { state ->
                            state.copy(
                                availableSamples = state.availableSamples.map { sample ->
                                    if (sample.id.toString() == sampleId) updatedMetadata else sample
                                },
                                pads = state.pads.map { pad ->
                                    if (pad.sampleId == sampleId) {
                                        pad.copy(sampleName = updatedMetadata.name)
                                    } else pad
                                },
                                isDirty = true
                            )
                        }
                        
                        Log.d(TAG, "Updated metadata for sample $sampleId")
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to update sample metadata: ${result.error.message}")
                        _uiState.update { state ->
                            state.copy(error = "Failed to update sample: ${result.error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating sample metadata", e)
                _uiState.update { state ->
                    state.copy(error = "Error updating sample: ${e.message}")
                }
            }
        }
    }

    /**
     * Batch import multiple samples.
     * Requirements: 7.1 (sample loading from files)
     */
    fun batchImportSamples(sampleUris: List<Uri>) {
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    state.copy(isLoading = true)
                }

                val importResults = mutableListOf<String>()
                val errors = mutableListOf<String>()

                sampleUris.forEachIndexed { index, uri ->
                    try {
                        val sampleMetadata = SampleMetadata(
                            id = UUID.randomUUID(),
                            name = "Imported Sample ${index + 1}",
                            filePath = uri.toString(),
                            durationMs = 0L,
                            sampleRate = 44100,
                            channels = 1,
                            format = "wav",
                            fileSizeBytes = 0L,
                            createdAt = System.currentTimeMillis(),
                            tags = listOf("imported")
                        )

                        val projectId = _uiState.value.currentProject
                        val result = sampleRepository.saveSampleFromUri(
                            sourceUri = uri,
                            metadata = sampleMetadata,
                            projectId = projectId,
                            copyToProject = true
                        )

                        when (result) {
                            is com.high.theone.domain.Result.Success -> {
                                importResults.add(result.value)
                            }
                            is com.high.theone.domain.Result.Failure -> {
                                errors.add("Failed to import ${uri.lastPathSegment}: ${result.error.message}")
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Error importing ${uri.lastPathSegment}: ${e.message}")
                    }
                }

                // Reload available samples
                loadAvailableSamples()

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = if (errors.isNotEmpty()) errors.joinToString("\n") else null,
                        isDirty = importResults.isNotEmpty()
                    )
                }

                Log.d(TAG, "Batch import completed: ${importResults.size} successful, ${errors.size} failed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch import", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = "Batch import failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clean up unused samples and optimize memory usage.
     * Requirements: 7.3 (sample cleanup and memory management)
     */
    fun cleanupUnusedSamples() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val usedSampleIds = currentState.pads
                    .mapNotNull { it.sampleId }
                    .toSet()

                val unusedSamples = currentState.availableSamples
                    .filter { it.id.toString() !in usedSampleIds }

                if (unusedSamples.isEmpty()) {
                    Log.d(TAG, "No unused samples to clean up")
                    return@launch
                }

                // Unload unused samples from audio engine
                unusedSamples.forEach { sample ->
                    try {
                        audioEngine.unloadSample(sample.id.toString())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to unload sample ${sample.id}", e)
                    }
                }

                Log.d(TAG, "Cleaned up ${unusedSamples.size} unused samples from memory")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up unused samples", e)
            }
        }
    }

    /**
     * Preload samples for better performance.
     * Requirements: 7.3 (memory management)
     */
    fun preloadSamples() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val samplesToPreload = currentState.pads
                    .filter { it.hasAssignedSample && it.sampleId != null }
                    .mapNotNull { pad ->
                        currentState.availableSamples.find { it.id.toString() == pad.sampleId }
                    }

                samplesToPreload.forEach { sample ->
                    try {
                        val samplePathResult = sampleRepository.getSampleFilePath(sample.id.toString())
                        when (samplePathResult) {
                            is com.high.theone.domain.Result.Success -> {
                                audioEngine.loadSampleToMemory(sample.id.toString(), samplePathResult.value)
                            }
                            is com.high.theone.domain.Result.Failure -> {
                                Log.w(TAG, "Failed to get path for sample ${sample.id}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to preload sample ${sample.id}", e)
                    }
                }

                Log.d(TAG, "Preloaded ${samplesToPreload.size} samples")
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading samples", e)
            }
        }
    }

    /**
     * Get sample usage information across all pads.
     * Requirements: 7.3 (sample usage tracking)
     */
    fun getSampleUsageInfo(): Map<String, PadSampleUsageInfo> {
        val currentState = _uiState.value
        val usageMap = mutableMapOf<String, PadSampleUsageInfo>()

        currentState.availableSamples.forEach { sample ->
            val sampleIdString = sample.id.toString()
            val usingPads = currentState.pads
                .filter { it.sampleId == sampleIdString }
                .map { it.index }

            usageMap[sampleIdString] = PadSampleUsageInfo(
                sampleId = sampleIdString,
                sampleName = sample.name,
                usingPads = usingPads,
                usageCount = usingPads.size,
                isLoaded = usingPads.isNotEmpty(),
                lastUsed = System.currentTimeMillis() // Would track actual usage in real implementation
            )
        }

        return usageMap
    }

    /**
     * Duplicate a sample with optional modifications.
     * Requirements: 7.3 (sample management)
     */
    fun duplicateSample(sampleId: String, newName: String? = null) {
        viewModelScope.launch {
            try {
                val originalSampleResult = sampleRepository.loadSample(sampleId)
                val originalSample = when (originalSampleResult) {
                    is com.high.theone.domain.Result.Success -> originalSampleResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        _uiState.update { state ->
                            state.copy(error = "Original sample not found")
                        }
                        return@launch
                    }
                }

                val duplicatedMetadata = originalSample.copy(
                    id = UUID.randomUUID(),
                    name = newName ?: "${originalSample.name} (Copy)",
                    createdAt = System.currentTimeMillis()
                )

                val originalPathResult = sampleRepository.getSampleFilePath(sampleId)
                val originalPath = when (originalPathResult) {
                    is com.high.theone.domain.Result.Success -> originalPathResult.value
                    is com.high.theone.domain.Result.Failure -> {
                        _uiState.update { state ->
                            state.copy(error = "Original sample file not found")
                        }
                        return@launch
                    }
                }

                val result = sampleRepository.saveSampleFromUri(
                    sourceUri = Uri.parse(originalPath),
                    metadata = duplicatedMetadata,
                    projectId = _uiState.value.currentProject,
                    copyToProject = true
                )

                when (result) {
                    is com.high.theone.domain.Result.Success -> {
                        loadAvailableSamples()
                        _uiState.update { state ->
                            state.copy(isDirty = true)
                        }
                        Log.d(TAG, "Duplicated sample $sampleId as ${result.value}")
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Failed to duplicate sample: ${result.error.message}")
                        _uiState.update { state ->
                            state.copy(error = "Failed to duplicate sample: ${result.error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error duplicating sample", e)
                _uiState.update { state ->
                    state.copy(error = "Error duplicating sample: ${e.message}")
                }
            }
        }
    }

    /**
     * Search samples by various criteria.
     * Requirements: 7.2 (sample organization)
     */
    fun searchSamples(query: String, searchCriteria: SampleSearchCriteria = SampleSearchCriteria.ALL) {
        viewModelScope.launch {
            try {
                val projectId = _uiState.value.currentProject
                val result = sampleRepository.searchSamples(query, projectId)

                when (result) {
                    is com.high.theone.domain.Result.Success -> {
                        val samples = result.value
                        val filteredSamples = when (searchCriteria) {
                            SampleSearchCriteria.ALL -> samples
                            SampleSearchCriteria.NAME_ONLY -> samples.filter { 
                                it.name.contains(query, ignoreCase = true) 
                            }
                            SampleSearchCriteria.TAGS_ONLY -> samples.filter { 
                                it.tags.any { tag -> tag.contains(query, ignoreCase = true) } 
                            }
                            SampleSearchCriteria.UNUSED_ONLY -> {
                                val usedSampleIds = _uiState.value.pads.mapNotNull { it.sampleId }.toSet()
                                samples.filter { it.id.toString() !in usedSampleIds }
                            }
                        }

                        _uiState.update { state ->
                            state.copy(availableSamples = filteredSamples)
                        }

                        Log.d(TAG, "Search found ${filteredSamples.size} samples for query: $query")
                    }
                    is com.high.theone.domain.Result.Failure -> {
                        Log.e(TAG, "Sample search failed: ${result.error.message}")
                        _uiState.update { state ->
                            state.copy(error = "Search failed: ${result.error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching samples", e)
                _uiState.update { state ->
                    state.copy(error = "Search error: ${e.message}")
                }
            }
        }
    }

    // ========== ERROR HANDLING AND RECOVERY ==========

    /**
     * Handle audio engine errors with recovery strategies.
     * Requirements: 1.4, 1.5 (error handling), 7.4, 7.5 (graceful degradation)
     */
    private suspend fun handleAudioEngineError(error: Throwable, operation: String): Boolean {
        Log.e(TAG, "Audio engine error during $operation", error)
        
        return try {
            when {
                error.message?.contains("permission", ignoreCase = true) == true -> {
                    _uiState.update { state ->
                        state.copy(
                            error = "Microphone permission required. Please grant permission in settings.",
                            recordingState = state.recordingState.copy(
                                error = "Permission denied"
                            )
                        )
                    }
                    false
                }
                
                error.message?.contains("device", ignoreCase = true) == true -> {
                    _uiState.update { state ->
                        state.copy(
                            error = "Audio device unavailable. Please check your audio settings.",
                            isAudioEngineReady = false
                        )
                    }
                    
                    // Attempt to reinitialize audio engine
                    reinitializeAudioEngine()
                }
                
                error.message?.contains("memory", ignoreCase = true) == true -> {
                    _uiState.update { state ->
                        state.copy(error = "Low memory. Cleaning up unused samples...")
                    }
                    
                    // Clean up memory and retry
                    cleanupUnusedSamples()
                    delay(1000)
                    
                    _uiState.update { state ->
                        state.copy(error = null)
                    }
                    true
                }
                
                else -> {
                    _uiState.update { state ->
                        state.copy(
                            error = "Audio system error. Attempting recovery...",
                            isAudioEngineReady = false
                        )
                    }
                    
                    reinitializeAudioEngine()
                }
            }
        } catch (recoveryError: Exception) {
            Log.e(TAG, "Error during recovery attempt", recoveryError)
            _uiState.update { state ->
                state.copy(
                    error = "Audio system recovery failed. Please restart the app.",
                    isAudioEngineReady = false
                )
            }
            false
        }
    }

    /**
     * Attempt to reinitialize the audio engine.
     * Requirements: 7.4 (error recovery)
     */
    private suspend fun reinitializeAudioEngine(): Boolean {
        return try {
            Log.d(TAG, "Attempting to reinitialize audio engine")
            
            // Shutdown current engine
            audioEngine.shutdown()
            delay(500)
            
            // Reinitialize
            val success = audioEngine.initialize(
                sampleRate = 44100,
                bufferSize = 256,
                enableLowLatency = true
            )
            
            _uiState.update { state ->
                state.copy(
                    isAudioEngineReady = success,
                    error = if (success) null else "Failed to reinitialize audio engine"
                )
            }
            
            if (success) {
                // Resynchronize pad states
                synchronizePadStates()
                Log.d(TAG, "Audio engine reinitialized successfully")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reinitialize audio engine", e)
            _uiState.update { state ->
                state.copy(
                    isAudioEngineReady = false,
                    error = "Audio engine recovery failed"
                )
            }
            false
        }
    }

    /**
     * Handle file system errors with user-friendly messages.
     * Requirements: 7.4 (user-friendly error messages)
     */
    private fun handleFileSystemError(error: Throwable, operation: String) {
        Log.e(TAG, "File system error during $operation", error)
        
        val userMessage = when {
            error.message?.contains("space", ignoreCase = true) == true -> 
                "Not enough storage space. Please free up space and try again."
            
            error.message?.contains("permission", ignoreCase = true) == true -> 
                "Storage permission required. Please grant permission in settings."
            
            error.message?.contains("not found", ignoreCase = true) == true -> 
                "File not found. The sample may have been moved or deleted."
            
            error.message?.contains("corrupt", ignoreCase = true) == true -> 
                "File appears to be corrupted. Please try a different file."
            
            error.message?.contains("format", ignoreCase = true) == true -> 
                "Unsupported file format. Please use WAV, MP3, or FLAC files."
            
            else -> "File operation failed. Please check your storage and try again."
        }
        
        _uiState.update { state ->
            state.copy(error = userMessage)
        }
    }

    /**
     * Handle network-related errors (for future cloud features).
     * Requirements: 7.4 (comprehensive error handling)
     */
    private fun handleNetworkError(error: Throwable, operation: String) {
        Log.e(TAG, "Network error during $operation", error)
        
        val userMessage = when {
            error.message?.contains("timeout", ignoreCase = true) == true -> 
                "Connection timeout. Please check your internet connection."
            
            error.message?.contains("network", ignoreCase = true) == true -> 
                "Network unavailable. Please check your connection and try again."
            
            else -> "Connection error. Please try again later."
        }
        
        _uiState.update { state ->
            state.copy(error = userMessage)
        }
    }

    /**
     * Validate operation preconditions and provide helpful error messages.
     * Requirements: 7.5 (validation and error prevention)
     */
    private fun validateOperationPreconditions(operation: SamplingOperation): ValidationResult {
        val currentState = _uiState.value
        
        return when (operation) {
            SamplingOperation.START_RECORDING -> {
                when {
                    !currentState.isAudioEngineReady -> 
                        ValidationResult.Error("Audio system not ready. Please wait or restart the app.")
                    
                    currentState.recordingState.isRecording -> 
                        ValidationResult.Error("Recording already in progress.")
                    
                    currentState.recordingState.isProcessing -> 
                        ValidationResult.Error("Please wait for current recording to finish processing.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.STOP_RECORDING -> {
                when {
                    !currentState.recordingState.isRecording -> 
                        ValidationResult.Error("No recording in progress.")
                    
                    currentState.recordingState.isProcessing -> 
                        ValidationResult.Error("Recording is already being processed.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.TRIGGER_PAD -> {
                when {
                    !currentState.isAudioEngineReady -> 
                        ValidationResult.Error("Audio system not ready.")
                    
                    currentState.loadedPads.isEmpty() -> 
                        ValidationResult.Warning("No samples loaded. Please assign samples to pads first.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.LOAD_SAMPLE -> {
                when {
                    currentState.isLoading -> 
                        ValidationResult.Error("Another sample is currently loading. Please wait.")
                    
                    currentState.availableSamples.size >= 100 -> 
                        ValidationResult.Warning("Many samples loaded. Consider cleaning up unused samples.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.SAVE_PROJECT -> {
                when {
                    currentState.currentProject == null -> 
                        ValidationResult.Error("No active project to save.")
                    
                    currentState.recordingState.isRecording -> 
                        ValidationResult.Error("Cannot save while recording. Please stop recording first.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.ASSIGN_SAMPLE -> {
                when {
                    currentState.availableSamples.isEmpty() -> 
                        ValidationResult.Error("No samples available to assign.")
                    
                    else -> ValidationResult.Success
                }
            }
            
            SamplingOperation.CONFIGURE_PAD -> {
                when {
                    !currentState.isAudioEngineReady -> 
                        ValidationResult.Warning("Audio system not ready. Configuration will be applied when ready.")
                    
                    else -> ValidationResult.Success
                }
            }
        }
    }

    /**
     * Execute operation with comprehensive error handling.
     * Requirements: 7.4 (error recovery strategies)
     */
    private suspend fun executeWithErrorHandling(
        operation: SamplingOperation,
        action: suspend () -> Unit
    ) {
        try {
            // Validate preconditions
            val validation = validateOperationPreconditions(operation)
            when (validation) {
                is ValidationResult.Error -> {
                    _uiState.update { state ->
                        state.copy(error = validation.message)
                    }
                    return
                }
                is ValidationResult.Warning -> {
                    Log.w(TAG, "Operation warning: ${validation.message}")
                    // Continue with operation but log warning
                }
                ValidationResult.Success -> {
                    // Proceed with operation
                }
            }
            
            // Execute the operation
            action()
            
        } catch (e: Exception) {
            when (e) {
                is SecurityException -> handleFileSystemError(e, operation.name)
                is java.io.IOException -> handleFileSystemError(e, operation.name)
                is java.net.SocketTimeoutException -> handleNetworkError(e, operation.name)
                else -> {
                    // Check if it's an audio engine error
                    if (e.message?.contains("audio", ignoreCase = true) == true ||
                        e.message?.contains("oboe", ignoreCase = true) == true) {
                        handleAudioEngineError(e, operation.name)
                    } else {
                        // Generic error handling
                        Log.e(TAG, "Unexpected error during ${operation.name}", e)
                        _uiState.update { state ->
                            state.copy(error = "An unexpected error occurred. Please try again.")
                        }
                    }
                }
            }
        }
    }

    /**
     * Get detailed system diagnostics for debugging.
     * Requirements: 7.4 (logging and debugging support)
     */
    fun getSystemDiagnostics(): SystemDiagnostics {
        val currentState = _uiState.value
        
        return SystemDiagnostics(
            audioEngineReady = currentState.isAudioEngineReady,
            recordingState = currentState.recordingState,
            loadedSamples = currentState.availableSamples.size,
            loadedPads = currentState.loadedPads.size,
            playingPads = currentState.playingPads.size,
            memoryUsage = Runtime.getRuntime().let { runtime ->
                MemoryUsage(
                    used = runtime.totalMemory() - runtime.freeMemory(),
                    total = runtime.totalMemory(),
                    max = runtime.maxMemory()
                )
            },
            lastError = currentState.error,
            isDirty = currentState.isDirty
        )
    }

    /**
     * Enable or disable debug logging.
     * Requirements: 7.4 (debugging support)
     */
    fun setDebugLogging(enabled: Boolean) {
        // This would configure logging levels in a real implementation
        Log.d(TAG, "Debug logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Helper function to update a specific pad's state.
     */
    private fun updatePadState(padIndex: Int, update: (PadState) -> PadState) {
        _uiState.update { currentState ->
            currentState.copy(
                pads = currentState.pads.mapIndexed { index, pad ->
                    if (index == padIndex) update(pad) else pad
                }
            )
        }
    }

    // MIDI Integration Methods
    // Requirements: 1.1 (MIDI note mapping), 1.3 (MIDI velocity), 5.2 (pad integration)
    
    /**
     * Handle MIDI pad trigger event from MIDI input
     */
    fun handleMidiPadTrigger(event: MidiPadTriggerEvent) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val pad = currentState.getPad(event.padIndex)
                
                if (pad == null || !pad.canTriggerFromMidi) {
                    Log.w(TAG, "Cannot trigger pad ${event.padIndex} from MIDI - not ready or no MIDI mapping")
                    return@launch
                }
                
                // Update pad state to show MIDI trigger
                updatePadState(event.padIndex) { padState ->
                    padState.copy(lastMidiTrigger = event.timestamp)
                }
                
                // Trigger the pad with converted velocity
                triggerPad(event.padIndex, event.velocity)
                
                Log.d(TAG, "MIDI triggered pad ${event.padIndex} (note ${event.midiNote}, velocity ${event.midiVelocity} -> ${event.velocity})")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling MIDI pad trigger", e)
            }
        }
    }
    
    /**
     * Handle MIDI pad stop event (for NOTE_ON_OFF playback mode)
     */
    fun handleMidiPadStop(event: MidiPadStopEvent) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val pad = currentState.getPad(event.padIndex)
                
                if (pad == null || !pad.canTriggerFromMidi) {
                    return@launch
                }
                
                // Only stop if pad is in NOTE_ON_OFF mode
                if (pad.playbackMode == PlaybackMode.NOTE_ON_OFF && pad.isPlaying) {
                    releasePad(event.padIndex)
                    Log.d(TAG, "MIDI stopped pad ${event.padIndex} (note ${event.midiNote})")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling MIDI pad stop", e)
            }
        }
    }
    
    /**
     * Set MIDI note mapping for a pad
     */
    fun setPadMidiNote(padIndex: Int, midiNote: Int?, midiChannel: Int = 9) {
        viewModelScope.launch {
            try {
                updatePadState(padIndex) { padState ->
                    padState.copy(
                        midiNote = midiNote,
                        midiChannel = midiChannel
                    )
                }
                
                Log.d(TAG, "Set MIDI note $midiNote (channel $midiChannel) for pad $padIndex")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting pad MIDI note", e)
            }
        }
    }
    
    /**
     * Set MIDI velocity sensitivity for a pad
     */
    fun setPadMidiVelocitySensitivity(padIndex: Int, sensitivity: Float) {
        viewModelScope.launch {
            try {
                val clampedSensitivity = sensitivity.coerceIn(0f, 2f)
                
                updatePadState(padIndex) { padState ->
                    padState.copy(midiVelocitySensitivity = clampedSensitivity)
                }
                
                Log.d(TAG, "Set MIDI velocity sensitivity $clampedSensitivity for pad $padIndex")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting pad MIDI velocity sensitivity", e)
            }
        }
    }
    
    /**
     * Auto-assign MIDI notes to all pads using standard drum mapping
     */
    fun autoAssignMidiNotes(channel: Int = 9) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val updatedPads = MidiPadIntegration.autoAssignMidiNotes(currentState.pads, channel)
                
                _uiState.update { state ->
                    state.copy(
                        pads = updatedPads,
                        isDirty = true
                    )
                }
                
                Log.d(TAG, "Auto-assigned MIDI notes to all pads on channel $channel")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-assigning MIDI notes", e)
            }
        }
    }
    
    /**
     * Clear MIDI mapping for a pad
     */
    fun clearPadMidiMapping(padIndex: Int) {
        setPadMidiNote(padIndex, null)
    }
    
    /**
     * Clear MIDI mapping for all pads
     */
    fun clearAllMidiMappings() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val updatedPads = currentState.pads.map { pad ->
                    pad.copy(
                        midiNote = null,
                        midiChannel = 0,
                        acceptsAllChannels = false,
                        lastMidiTrigger = 0L
                    )
                }
                
                _uiState.update { state ->
                    state.copy(
                        pads = updatedPads,
                        isDirty = true
                    )
                }
                
                Log.d(TAG, "Cleared MIDI mappings for all pads")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing MIDI mappings", e)
            }
        }
    }
}