package com.high.theone.features.sequencer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.high.theone.audio.AudioEngineControl
import com.high.theone.domain.PatternRepository
import com.high.theone.model.*
import javax.inject.Inject

/**
 * Enhanced ViewModel for the step sequencer with comprehensive state management,
 * reactive state updates, and robust error handling.
 * 
 * Requirements: All requirements - comprehensive state management and coordination
 */
@HiltViewModel
class SequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val patternRepository: PatternRepository,
    private val timingEngine: TimingEngine,
    private val patternManager: PatternManager,
    private val recordingEngine: RecordingEngine,
    private val overdubManager: OverdubManager,
    private val historyManager: PatternHistoryManager,
    private val songModeManager: SongModeManager,
    private val songNavigationManager: SongNavigationManager,
    private val songPlaybackEngine: SongPlaybackEngine,
    private val songExportManager: SongExportManager,
    private val padSystemIntegration: PadSystemIntegration,
    private val performanceOptimizer: SequencerPerformanceOptimizer,
    private val sequencerSampleCache: SequencerSampleCache,
    private val sequencerVoiceManager: SequencerVoiceManager,
    private val errorHandler: SequencerErrorHandler,
    private val logger: SequencerLogger
) : ViewModel() {
    
    companion object {
        private const val TAG = "SequencerViewModel"
    }
    
    private val _sequencerState = MutableStateFlow(SequencerState())
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()
    
    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()
    
    // Pad state from pad system integration
    val pads: StateFlow<List<SequencerPadInfo>> = padSystemIntegration.sequencerPadState
    val padSyncState: StateFlow<PadSyncState> = padSystemIntegration.padSyncState
    val currentPlaybackMode: StateFlow<PlaybackMode> = padSystemIntegration.currentMode
    
    // Performance optimization state
    val performanceMetrics: StateFlow<SequencerPerformanceMetrics> = performanceOptimizer.performanceMetrics
    val optimizationState: StateFlow<OptimizationState> = performanceOptimizer.optimizationState
    val voiceManagementState: StateFlow<VoiceManagementState> = sequencerVoiceManager.voiceState
    
    // Error handling state
    val errorState: StateFlow<SequencerErrorState> = errorHandler.errorState
    
    private val _muteSoloState = MutableStateFlow(TrackMuteSoloState())
    val muteSoloState: StateFlow<TrackMuteSoloState> = _muteSoloState.asStateFlow()
    
    // Enhanced UI state management
    private val _uiState = MutableStateFlow(SequencerUIState())
    val uiState: StateFlow<SequencerUIState> = _uiState.asStateFlow()
    
    // User feedback and notifications
    private val _userFeedback = MutableSharedFlow<UserFeedback>()
    val userFeedback: SharedFlow<UserFeedback> = _userFeedback.asSharedFlow()
    
    // Loading states for async operations
    private val _loadingStates = MutableStateFlow(LoadingStates())
    val loadingStates: StateFlow<LoadingStates> = _loadingStates.asStateFlow()
    
    // Settings and preferences
    private val _sequencerSettings = MutableStateFlow(SequencerSettings())
    val sequencerSettings: StateFlow<SequencerSettings> = _sequencerSettings.asStateFlow()
    
    // Recording state from recording engine
    val recordingState: StateFlow<RecordingState> = recordingEngine.recordingState
    
    // Overdub state from overdub manager
    val overdubState: StateFlow<OverdubState> = overdubManager.overdubState
    
    // History state from history manager
    val historyState: StateFlow<PatternHistoryState> = historyManager.historyState
    
    // Song mode state from song mode manager
    val songState: StateFlow<SongPlaybackState> = songModeManager.songState
    
    // Song navigation state from navigation manager
    val navigationState: StateFlow<SongNavigationState> = songNavigationManager.navigationState
    
    // Song playback engine state
    val playbackEngineState: StateFlow<SongPlaybackEngineState> = songPlaybackEngine.playbackState
    
    // Song export state
    val exportState: StateFlow<SongExportState> = songExportManager.exportState
    
    init {
        initializeErrorHandling()
        setupTimingCallbacks()
        loadPatterns()
        setupPadSystemIntegration()
        setupRecordingCallbacks()
        setupSongModeCallbacks()
        setupPlaybackEngineCallbacks()
        initializePerformanceOptimization()
        setupReactiveStateUpdates()
        loadSequencerSettings()
        initializeUserFeedbackSystem()
    }
    
    /**
     * Setup reactive state updates for comprehensive UI coordination
     * Requirements: All requirements - reactive state management
     */
    private fun setupReactiveStateUpdates() {
        // Combine multiple state flows for comprehensive UI state
        viewModelScope.launch {
            combine(
                sequencerState,
                patterns,
                pads,
                muteSoloState,
                recordingState,
                songState,
                errorState
            ) { sequencer, patterns, pads, muteState, recording, song, errors ->
                SequencerUIState(
                    isInitialized = patterns.isNotEmpty(),
                    hasActivePattern = sequencer.currentPattern != null,
                    canRecord = pads.any { it.sampleId != null },
                    canPlayback = sequencer.currentPattern != null && pads.any { it.sampleId != null },
                    hasErrors = errors.criticalError != null,
                    isPerformanceOptimized = performanceMetrics.value.isOptimal,
                    activeFeatures = buildActiveFeaturesList(sequencer, recording, song),
                    statusMessage = buildStatusMessage(sequencer, recording, song, errors)
                )
            }.collect { newUIState ->
                _uiState.value = newUIState
            }
        }
        
        // Monitor performance and provide user feedback
        viewModelScope.launch {
            performanceMetrics.collect { metrics ->
                if (metrics.cpuUsagePercent > 90f) {
                    emitUserFeedback(UserFeedback.Warning("High CPU usage detected. Consider reducing pattern complexity."))
                } else if (metrics.memoryUsagePercent > 95f) {
                    emitUserFeedback(UserFeedback.Warning("Memory usage is high. Some samples may be unloaded."))
                }
            }
        }
        
        // Monitor error state and provide user notifications
        viewModelScope.launch {
            errorState.collect { errorState ->
                errorState.userMessage?.let { message ->
                    emitUserFeedback(UserFeedback.Error(message))
                }
                
                errorState.criticalError?.let { error ->
                    emitUserFeedback(UserFeedback.Critical("Critical error: $error"))
                }
            }
        }
    }
    
    /**
     * Load sequencer settings and preferences
     * Requirements: 9.7 - settings and preferences
     */
    private fun loadSequencerSettings() {
        viewModelScope.launch {
            try {
                // Load settings from preferences or use defaults
                val settings = SequencerSettings(
                    defaultTempo = 120f,
                    defaultSwing = 0f,
                    defaultQuantization = Quantization.SIXTEENTH,
                    autoSaveEnabled = true,
                    performanceMode = PerformanceMode.BALANCED,
                    visualFeedbackEnabled = true,
                    hapticFeedbackEnabled = true,
                    metronomeEnabled = false,
                    recordingMode = RecordingMode.REPLACE
                )
                
                _sequencerSettings.value = settings
                
                // Apply settings to components
                applySettingsToComponents(settings)
                
                logger.logInfo("SequencerViewModel", "Settings loaded successfully")
            } catch (e: Exception) {
                logger.logError("SequencerViewModel", "Failed to load settings", exception = e)
                emitUserFeedback(UserFeedback.Warning("Failed to load settings. Using defaults."))
            }
        }
    }
    
    /**
     * Initialize user feedback system
     * Requirements: All requirements - user feedback and error handling
     */
    private fun initializeUserFeedbackSystem() {
        logger.logInfo("SequencerViewModel", "User feedback system initialized")
    }
    
    /**
     * Apply settings to various components
     */
    private fun applySettingsToComponents(settings: SequencerSettings) {
        // Apply performance settings
        when (settings.performanceMode) {
            PerformanceMode.POWER_SAVE -> {
                performanceOptimizer.enablePowerSaveMode()
                sequencerSampleCache.setMaxCacheSize(50) // Reduced cache
            }
            PerformanceMode.BALANCED -> {
                performanceOptimizer.enableBalancedMode()
                sequencerSampleCache.setMaxCacheSize(100) // Normal cache
            }
            PerformanceMode.PERFORMANCE -> {
                performanceOptimizer.enablePerformanceMode()
                sequencerSampleCache.setMaxCacheSize(200) // Larger cache
            }
        }
        
        // Apply audio settings
        if (settings.metronomeEnabled) {
            audioEngine.enableMetronome(true)
        }
    }
    
    /**
     * Build list of currently active features for UI state
     */
    private fun buildActiveFeaturesList(
        sequencer: SequencerState,
        recording: RecordingState,
        song: SongPlaybackState
    ): List<ActiveFeature> {
        val features = mutableListOf<ActiveFeature>()
        
        if (sequencer.isPlaying) features.add(ActiveFeature.PLAYBACK)
        if (recording.isRecording) features.add(ActiveFeature.RECORDING)
        if (song.isActive) features.add(ActiveFeature.SONG_MODE)
        if (sequencer.currentPattern != null) features.add(ActiveFeature.PATTERN_LOADED)
        
        return features
    }
    
    /**
     * Build status message for UI display
     */
    private fun buildStatusMessage(
        sequencer: SequencerState,
        recording: RecordingState,
        song: SongPlaybackState,
        errors: SequencerErrorState
    ): String {
        return when {
            errors.criticalError != null -> "Error: ${errors.criticalError}"
            recording.isRecording -> "Recording..."
            song.isActive && sequencer.isPlaying -> "Playing Song - Pattern ${song.currentPatternIndex + 1}"
            sequencer.isPlaying -> "Playing - Step ${sequencer.currentStep + 1}"
            sequencer.isPaused -> "Paused"
            else -> "Ready"
        }
    }
    
    /**
     * Emit user feedback message
     */
    private suspend fun emitUserFeedback(feedback: UserFeedback) {
        _userFeedback.emit(feedback)
        logger.logInfo("SequencerViewModel", "User feedback emitted: ${feedback.javaClass.simpleName}")
    }

    private fun setupTimingCallbacks() {
        // Setup timing engine callbacks for step triggers
        timingEngine.scheduleStepCallback { step, microTime ->
            handleStepTrigger(step, microTime)
        }
        
        // Setup pattern completion callback for song mode
        timingEngine.schedulePatternCompleteCallback {
            onPatternComplete()
        }
    }
    
    private fun setupRecordingCallbacks() {
        // Setup audio engine callback for pad hits during recording
        // This would be implemented when audio engine supports recording callbacks
    }
    
    private fun setupSongModeCallbacks() {
        // Setup song mode manager with pattern transition callback
        songModeManager.initializeSong(SongMode()) { patternId ->
            selectPattern(patternId)
        }
    }
    
    private fun setupPlaybackEngineCallbacks() {
        // Setup song playback engine with callbacks
        songPlaybackEngine.initialize(
            songMode = SongMode(),
            onPatternTransition = { transition ->
                handlePatternTransition(transition)
            },
            onPatternComplete = {
                songPlaybackEngine.advanceToNextPattern()
            }
        )
    }
    
    private fun handlePatternTransition(transition: PatternTransition) {
        // Handle smooth pattern transitions
        selectPattern(transition.toPatternId)
        
        // Update navigation state
        songNavigationManager.updatePosition(
            transition.sequencePosition,
            transition.patternRepeat,
            0
        )
        
        // Apply transition-specific logic
        when (transition.transitionType) {
            TransitionType.START -> {
                // Starting song playback
                playPattern()
            }
            TransitionType.STOP -> {
                // Stopping song playback
                stopPattern()
            }
            TransitionType.NEXT_PATTERN, TransitionType.LOOP -> {
                // Seamless transition to next pattern
                val pattern = getCurrentPattern()
                if (pattern != null) {
                    timingEngine.setTempo(pattern.tempo)
                    timingEngine.setSwing(pattern.swing)
                }
            }
            else -> {
                // Other transitions handled by default
            }
        }
    }
    
    private fun handleStepTrigger(step: Int, microTime: Long) {
        val currentPattern = getCurrentPattern() ?: return
        val state = _sequencerState.value
        
        // Update current step in UI
        _sequencerState.update { it.copy(currentStep = step) }
        
        // Trigger samples for active steps using sequencer audio engine scheduling
        viewModelScope.launch {
            currentPattern.steps.forEach { (padIndex, steps) ->
                val activeStep = steps.find { it.position == step && it.isActive }
                if (activeStep != null && !isMuted(padIndex)) {
                    // Check if pad is available and properly configured
                    val padInfo = padSystemIntegration.getPadInfo(padIndex)
                    if (padInfo?.isSequencerReady == true && padInfo.sampleId != null) {
                        try {
                            // Ensure sample is ready for immediate playback
                            val sampleReady = sequencerSampleCache.ensureSampleReady(padInfo.sampleId)
                            
                            if (sampleReady) {
                                // Allocate voice with performance optimization
                                val voiceId = sequencerVoiceManager.allocateSequencerVoice(
                                    padIndex = padIndex,
                                    sampleId = padInfo.sampleId,
                                    velocity = activeStep.velocity / 127f,
                                    playbackMode = padInfo.playbackMode,
                                    stepIndex = step,
                                    priority = VoicePriority.NORMAL
                                )
                                
                                if (voiceId != null) {
                                    // Track voice allocation for optimization
                                    performanceOptimizer.trackVoiceAllocation(voiceId, padIndex, padInfo.sampleId)
                                    
                                    // Log voice allocation
                                    logger.logVoiceAllocation("allocate", voiceId, padIndex, true, mapOf(
                                        "step" to step,
                                        "velocity" to activeStep.velocity
                                    ))
                                    
                                    try {
                                        // Use the new sequencer integration methods
                                        audioEngine.scheduleStepTrigger(
                                            padIndex = padIndex,
                                            velocity = activeStep.velocity / 127f,
                                            timestamp = microTime + activeStep.microTiming.toLong()
                                        )
                                        
                                        // Log successful audio engine operation
                                        logger.logAudioEngine("scheduleStepTrigger", true, mapOf(
                                            "padIndex" to padIndex,
                                            "step" to step
                                        ))
                                        
                                    } catch (audioException: Exception) {
                                        // Handle audio engine failure
                                        val recoveryResult = errorHandler.handleAudioEngineFailure(
                                            "scheduleStepTrigger",
                                            audioException,
                                            mapOf(
                                                "padIndex" to padIndex,
                                                "step" to step,
                                                "voiceId" to voiceId
                                            )
                                        )
                                        
                                        logger.logAudioEngine("scheduleStepTrigger", false, mapOf(
                                            "padIndex" to padIndex,
                                            "step" to step,
                                            "recoveryResult" to recoveryResult.name
                                        ), audioException)
                                        
                                        // Release voice if audio trigger failed
                                        sequencerVoiceManager.releaseSequencerVoice(voiceId)
                                        performanceOptimizer.releaseTrackedVoice(voiceId)
                                    }
                                    
                                    // Schedule voice release for one-shot samples
                                    if (padInfo.playbackMode == com.high.theone.model.PlaybackMode.ONE_SHOT) {
                                        viewModelScope.launch {
                                            delay(100L) // Minimum delay for UI responsiveness
                                            sequencerVoiceManager.releaseSequencerVoice(voiceId)
                                            performanceOptimizer.releaseTrackedVoice(voiceId)
                                            
                                            logger.logVoiceAllocation("release", voiceId, padIndex, true)
                                        }
                                    }
                                } else {
                                    // Handle voice allocation failure
                                    errorHandler.handleVoiceAllocationError(
                                        padIndex,
                                        padInfo.sampleId,
                                        context = mapOf("step" to step)
                                    )
                                    
                                    logger.logVoiceAllocation("allocate", null, padIndex, false, mapOf(
                                        "step" to step,
                                        "reason" to "allocation_failed"
                                    ))
                                }
                            } else {
                                // Handle sample loading failure
                                errorHandler.handleSampleLoadingError(
                                    padInfo.sampleId,
                                    RuntimeException("Sample not ready for playback"),
                                    mapOf(
                                        "padIndex" to padIndex,
                                        "step" to step
                                    )
                                )
                                
                                logger.logSampleCache("ensureReady", padInfo.sampleId, false, mapOf(
                                    "padIndex" to padIndex,
                                    "step" to step
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in optimized step trigger", e)
                            // Fallback to direct trigger if optimization fails
                            try {
                                audioEngine.triggerDrumPad(padIndex, activeStep.velocity / 127f)
                            } catch (fallbackException: Exception) {
                                Log.e(TAG, "Fallback trigger also failed", fallbackException)
                            }
                        }
                    } else {
                        Log.w(TAG, "Pad $padIndex not ready for sequencer trigger")
                    }
                }
            }
        }
    }
    
    private fun isMuted(padIndex: Int): Boolean {
        val muteState = _muteSoloState.value
        return muteState.mutedTracks.contains(padIndex) || 
               (muteState.soloedTracks.isNotEmpty() && !muteState.soloedTracks.contains(padIndex))
    }
    
    /**
     * Check if a pad can be used in sequencer patterns
     * Requirements: 8.2 (pad state integration)
     */
    fun canUsePadInSequencer(padIndex: Int): Boolean {
        return padSystemIntegration.canUsePadInSequencer(padIndex)
    }
    
    /**
     * Get pad information for sequencer UI
     * Requirements: 8.2 (pad state integration)
     */
    fun getPadInfo(padIndex: Int): SequencerPadInfo? {
        return padSystemIntegration.getPadInfo(padIndex)
    }
    
    /**
     * Get all assigned pads for pattern creation
     * Requirements: 8.5 (pad assignment integration)
     */
    fun getAssignedPads(): List<SequencerPadInfo> {
        return padSystemIntegration.getAssignedPads()
    }
    
    /**
     * Switch between live pad mode and sequencer mode
     * Requirements: 8.6 (seamless switching between modes)
     */
    fun switchPlaybackMode(mode: PlaybackMode) {
        viewModelScope.launch {
            // Stop current playback when switching modes
            if (_sequencerState.value.isPlaying) {
                stopPattern()
            }
            
            padSystemIntegration.switchToMode(mode)
        }
    }
    
    /**
     * Trigger a pad in live mode (when not in sequencer playback)
     * Requirements: 8.6 (seamless switching)
     */
    fun triggerPadLive(padIndex: Int, velocity: Float) {
        if (currentPlaybackMode.value == PlaybackMode.LIVE || !_sequencerState.value.isPlaying) {
            padSystemIntegration.triggerPadLive(padIndex, velocity)
        }
    }
    
    /**
     * Force resynchronization of pad states
     * Requirements: 8.3 (pad configuration synchronization)
     */
    fun resyncPads() {
        padSystemIntegration.forcePadResync()
    }
    
    /**
     * Get pad usage statistics for optimization
     * Requirements: 8.3 (performance optimization)
     */
    fun getPadUsageStats(): PadUsageStats {
        return padSystemIntegration.getPadUsageStats()
    }
    
    /**
     * Get current pad synchronization status
     * Requirements: 8.2 (pad state integration)
     */
    fun getPadSyncStatus(): PadSyncStatus {
        return padSystemIntegration.getSyncStatus()
    }
    
    // Performance Optimization Methods
    
    /**
     * Optimize performance for current playback context
     * Requirements: 10.2, 10.4, 10.5, 10.6
     */
    fun optimizeForPlayback() {
        viewModelScope.launch {
            try {
                val currentPattern = getCurrentPattern()
                if (currentPattern != null) {
                    // Cache current pattern for quick access
                    performanceOptimizer.cachePattern(currentPattern)
                    
                    // Prepare sample cache for pattern
                    val padSampleMap = createPadSampleMap()
                    sequencerSampleCache.preloadPatternSamples(currentPattern, padSampleMap)
                    
                    // Optimize voice allocation
                    sequencerVoiceManager.prepareForPatternPlayback(padSampleMap)
                    
                    // Get upcoming patterns for song mode
                    val upcomingPatterns = getUpcomingPatterns()
                    if (upcomingPatterns.isNotEmpty()) {
                        sequencerSampleCache.optimizeForPlayback(currentPattern, upcomingPatterns, padSampleMap)
                    }
                    
                    Log.d(TAG, "Optimized performance for playback")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing for playback", e)
            }
        }
    }
    
    /**
     * Get performance optimization recommendations
     * Requirements: 10.4, 10.5, 10.6
     */
    fun getPerformanceRecommendations(): List<OptimizationRecommendation> {
        return performanceOptimizer.getOptimizationRecommendations()
    }
    
    /**
     * Apply automatic performance optimizations
     * Requirements: 10.4, 10.5, 10.6
     */
    fun applyPerformanceOptimizations() {
        performanceOptimizer.applyAutomaticOptimizations()
    }
    
    /**
     * Get current performance statistics
     * Requirements: 10.1, 10.2
     */
    fun getPerformanceStatistics(): PerformanceStatistics {
        val cacheStats = sequencerSampleCache.getCacheStatistics()
        val voiceStats = sequencerVoiceManager.getVoiceStatistics()
        val metrics = performanceMetrics.value
        val optimizationState = optimizationState.value
        
        return PerformanceStatistics(
            cacheStatistics = cacheStats,
            voiceStatistics = voiceStats,
            performanceMetrics = metrics,
            optimizationState = optimizationState,
            recommendations = getPerformanceRecommendations()
        )
    }
    
    /**
     * Create pad-to-sample mapping for optimization
     * Requirements: 10.2, 10.6
     */
    private fun createPadSampleMap(): Map<Int, String> {
        return pads.value.associate { pad ->
            pad.index to (pad.sampleId ?: "")
        }.filterValues { it.isNotEmpty() }
    }
    
    /**
     * Get upcoming patterns for song mode optimization
     * Requirements: 10.2
     */
    private fun getUpcomingPatterns(): List<Pattern> {
        val songMode = _sequencerState.value.songMode
        if (songMode?.isActive == true) {
            val currentPosition = songNavigationManager.navigationState.value.currentSequencePosition
            val upcomingSteps = songMode.sequence.drop(currentPosition + 1).take(3)
            
            return upcomingSteps.mapNotNull { songStep ->
                _patterns.value.find { it.id == songStep.patternId }
            }
        }
        return emptyList()
    }
    
    // Error Handling Methods
    
    /**
     * Get current error state for UI display
     * Requirements: 8.7
     */
    fun getCurrentErrorState(): SequencerErrorState {
        return errorHandler.errorState.value
    }
    
    /**
     * Clear user error messages
     * Requirements: 8.7
     */
    fun clearUserMessage() {
        errorHandler.clearUserMessage()
    }
    
    /**
     * Clear critical error state
     * Requirements: 8.7
     */
    fun clearCriticalError() {
        errorHandler.clearCriticalError()
    }
    
    /**
     * Get error statistics for debugging
     * Requirements: 8.7
     */
    fun getErrorStatistics(): ErrorStatistics {
        return errorHandler.getErrorStatistics()
    }
    
    /**
     * Get recent log entries for debugging
     * Requirements: 8.7
     */
    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        return logger.getRecentLogs(count)
    }
    
    /**
     * Clear error history
     * Requirements: 8.7
     */
    fun clearErrorHistory() {
        errorHandler.clearErrorHistory()
        logger.clearLogs()
    }
    
    /**
     * Handle manual error recovery
     * Requirements: 8.7
     */
    fun attemptErrorRecovery() {
        viewModelScope.launch {
            try {
                logger.logInfo("SequencerViewModel", "Manual error recovery initiated")
                
                // Attempt to recover audio engine
                val audioRecovery = errorHandler.handleAudioEngineFailure("manual_recovery")
                
                if (audioRecovery == RecoveryResult.SUCCESS) {
                    // Reinitialize systems after recovery
                    initializePerformanceOptimization()
                    padSystemIntegration.forcePadResync()
                    
                    logger.logInfo("SequencerViewModel", "Manual error recovery successful")
                } else {
                    logger.logWarning("SequencerViewModel", "Manual error recovery failed", mapOf(
                        "result" to audioRecovery.name
                    ))
                }
                
            } catch (e: Exception) {
                logger.logError("SequencerViewModel", "Error during manual recovery", exception = e)
                errorHandler.handleGeneralError("manual_recovery", e, ErrorSeverity.HIGH)
            }
        }
    }
    
    private fun loadPatterns() {
        viewModelScope.launch {
            try {
                // Load patterns from repository
                // For now, create a default pattern
                val defaultPattern = Pattern(
                    name = "New Pattern",
                    length = 16,
                    tempo = 120f,
                    swing = 0f
                )
                _patterns.value = listOf(defaultPattern)
                _sequencerState.update { 
                    it.copy(
                        currentPattern = defaultPattern.id,
                        patterns = listOf(defaultPattern.id)
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Setup pad system integration and monitoring
     * Requirements: 8.2 (pad state integration), 8.6 (seamless switching)
     */
    private fun setupPadSystemIntegration() {
        viewModelScope.launch {
            // Monitor pad synchronization state
            padSystemIntegration.padSyncState.collect { syncState ->
                if (syncState.lastError != null) {
                    Log.w(TAG, "Pad sync error: ${syncState.lastError}")
                }
                
                // Auto-detect pad assignments when they change
                if (syncState.assignedPads > 0) {
                    padSystemIntegration.detectPadAssignments()
                }
            }
        }
        
        // Initialize in sequencer mode
        viewModelScope.launch {
            padSystemIntegration.switchToMode(PlaybackMode.SEQUENCER)
        }
    }
    
    /**
     * Initialize error handling and logging systems
     * Requirements: 8.7, 10.4
     */
    private fun initializeErrorHandling() {
        // Initialize logger
        logger.initialize(LogConfig(
            logLevel = LogLevel.INFO,
            enableFileLogging = false, // Can be enabled for debugging
            enablePerformanceLogging = true
        ))
        
        // Monitor error state
        viewModelScope.launch {
            errorHandler.errorState.collect { errorState ->
                if (errorState.criticalError != null) {
                    logger.logError("SequencerViewModel", "Critical error detected", mapOf(
                        "error" to errorState.criticalError
                    ))
                }
                
                if (errorState.userMessage != null) {
                    logger.logInfo("SequencerViewModel", "User message", mapOf(
                        "message" to errorState.userMessage
                    ))
                }
            }
        }
        
        logger.logInfo("SequencerViewModel", "Error handling system initialized")
    }
    
    /**
     * Initialize performance optimization systems
     * Requirements: 10.2, 10.4, 10.5, 10.6
     */
    private fun initializePerformanceOptimization() {
        // Initialize sample cache
        sequencerSampleCache.initialize()
        
        // Initialize voice manager
        sequencerVoiceManager.initialize()
        
        // Monitor performance metrics and apply optimizations
        viewModelScope.launch {
            performanceOptimizer.optimizationState.collect { state ->
                // Apply automatic optimizations when thresholds are exceeded
                if (state.memoryUsagePercent > 85f || state.cpuUsagePercent > 80f) {
                    performanceOptimizer.applyAutomaticOptimizations()
                }
            }
        }
        
        logger.logInfo("SequencerViewModel", "Performance optimization systems initialized")
    }
    
    fun handleTransportAction(action: TransportAction) {
        when (action) {
            is TransportAction.Play -> playPattern()
            is TransportAction.Pause -> pausePattern()
            is TransportAction.Stop -> stopPattern()
            is TransportAction.ToggleRecord -> toggleRecording()
            is TransportAction.SetTempo -> setTempo(action.tempo)
            is TransportAction.SetSwing -> setSwing(action.swing)
        }
    }
    
    private fun playPattern() {
        val currentPattern = getCurrentPattern() ?: return
        
        viewModelScope.launch {
            // Optimize performance before starting playback
            optimizeForPlayback()
            
            // Start timing engine with pattern settings
            timingEngine.start(currentPattern.tempo, currentPattern.swing)
            
            _sequencerState.update { 
                it.copy(isPlaying = true, isPaused = false) 
            }
            
            Log.d(TAG, "Started pattern playback with optimization")
        }
    }
    
    private fun pausePattern() {
        _sequencerState.update { 
            it.copy(isPlaying = false, isPaused = true) 
        }
    }
    
    private fun stopPattern() {
        viewModelScope.launch {
            // Stop timing engine
            timingEngine.stop()
            
            // Release all active voices for performance
            sequencerVoiceManager.releaseAllVoices()
            
            _sequencerState.update { 
                it.copy(
                    isPlaying = false, 
                    isPaused = false, 
                    currentStep = 0
                ) 
            }
            
            Log.d(TAG, "Stopped pattern playback and released voices")
        }
    }
    
    private fun toggleRecording() {
        viewModelScope.launch {
            val currentState = _sequencerState.value
            if (currentState.isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }
    
    /**
     * Start recording with current pattern and settings
     */
    fun startRecording(
        mode: RecordingMode = RecordingMode.REPLACE,
        quantization: Quantization = Quantization.SIXTEENTH
    ) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            val selectedPads = _sequencerState.value.selectedPads
            
            recordingEngine.startRecording(
                pattern = currentPattern,
                mode = mode,
                quantization = quantization,
                selectedPads = selectedPads
            )
            
            _sequencerState.update { 
                it.copy(
                    isRecording = true,
                    recordingMode = mode
                ) 
            }
        }
    }
    
    /**
     * Stop recording and apply recorded hits to pattern
     */
    fun stopRecording() {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern()
            if (currentPattern != null) {
                // Save state before applying recording
                val recordingState = recordingEngine.recordingState.value
                val operation = when (recordingState.mode) {
                    RecordingMode.REPLACE -> HistoryOperation.RECORDING
                    RecordingMode.OVERDUB -> HistoryOperation.OVERDUB
                    RecordingMode.PUNCH_IN -> HistoryOperation.RECORDING
                }
                
                historyManager.saveState(
                    pattern = currentPattern,
                    operation = operation,
                    description = "${recordingState.mode.displayName} (${recordingState.getTotalHitCount()} hits)"
                )
            }
            
            val updatedPattern = recordingEngine.stopRecording()
            if (updatedPattern != null) {
                updatePattern(updatedPattern)
            }
            
            _sequencerState.update { 
                it.copy(isRecording = false) 
            }
        }
    }
    
    /**
     * Record a pad hit during playback (called by audio engine)
     */
    fun recordPadHit(padIndex: Int, velocity: Int) {
        viewModelScope.launch {
            val state = _sequencerState.value
            val currentPattern = getCurrentPattern()
            
            if (!state.isRecording || currentPattern == null) return@launch
            
            val timestamp = System.nanoTime() / 1000 // Convert to microseconds
            val stepProgress = timingEngine.getStepProgress()
            
            recordingEngine.recordPadHit(
                padIndex = padIndex,
                velocity = velocity,
                timestamp = timestamp,
                currentStep = state.currentStep,
                stepProgress = stepProgress,
                tempo = currentPattern.tempo,
                swing = currentPattern.swing
            )
        }
    }
    
    /**
     * Clear recorded hits without stopping recording
     */
    fun clearRecordedHits() {
        viewModelScope.launch {
            recordingEngine.clearRecordedHits()
        }
    }
    
    /**
     * Set recording mode
     */
    fun setRecordingMode(mode: RecordingMode) {
        _sequencerState.update { 
            it.copy(recordingMode = mode) 
        }
    }
    
    /**
     * Configure overdub settings
     */
    fun configureOverdub(
        selectedPads: Set<Int> = emptySet(),
        punchInStep: Int? = null,
        punchOutStep: Int? = null,
        layerMode: LayerMode = LayerMode.ADD,
        velocityMode: VelocityMode = VelocityMode.REPLACE
    ) {
        viewModelScope.launch {
            overdubManager.configureOverdub(
                selectedPads = selectedPads,
                punchInStep = punchInStep,
                punchOutStep = punchOutStep,
                layerMode = layerMode,
                velocityMode = velocityMode
            )
        }
    }
    
    /**
     * Set punch-in point for overdub recording
     */
    fun setPunchIn(stepPosition: Int?) {
        overdubManager.setPunchIn(stepPosition)
    }
    
    /**
     * Set punch-out point for overdub recording
     */
    fun setPunchOut(stepPosition: Int?) {
        overdubManager.setPunchOut(stepPosition)
    }
    
    /**
     * Toggle pad selection for overdub recording
     */
    fun toggleOverdubPadSelection(padIndex: Int) {
        overdubManager.togglePadSelection(padIndex)
    }
    
    /**
     * Set layer mode for overdub
     */
    fun setLayerMode(mode: LayerMode) {
        overdubManager.setLayerMode(mode)
    }
    
    /**
     * Set velocity mode for overdub
     */
    fun setVelocityMode(mode: VelocityMode) {
        overdubManager.setVelocityMode(mode)
    }
    
    /**
     * Clear overdub configuration
     */
    fun clearOverdubConfig() {
        viewModelScope.launch {
            overdubManager.clearOverdubConfig()
        }
    }
    
    /**
     * Undo the last operation on current pattern
     */
    fun undo() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            val undonePattern = historyManager.undo(currentPatternId)
            
            if (undonePattern != null) {
                // Update pattern without saving to history (to avoid creating new history entry)
                updatePatternWithoutHistory(undonePattern)
            }
        }
    }
    
    /**
     * Redo the last undone operation on current pattern
     */
    fun redo() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            val redonePattern = historyManager.redo(currentPatternId)
            
            if (redonePattern != null) {
                // Update pattern without saving to history (to avoid creating new history entry)
                updatePatternWithoutHistory(redonePattern)
            }
        }
    }
    
    /**
     * Clear pattern (with history)
     */
    fun clearPattern() {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before clearing
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.PATTERN_CLEAR,
                description = "Clear all steps"
            )
            
            val clearedPattern = patternManager.clearPattern(currentPattern)
            updatePattern(clearedPattern)
        }
    }
    
    /**
     * Clear history for current pattern
     */
    fun clearPatternHistory() {
        viewModelScope.launch {
            val currentPatternId = _sequencerState.value.currentPattern ?: return@launch
            historyManager.clearHistory(currentPatternId)
        }
    }
    
    /**
     * Get history statistics for current pattern
     */
    fun getHistoryStats(): HistoryStats? {
        val currentPatternId = _sequencerState.value.currentPattern ?: return null
        return historyManager.getHistoryStats(currentPatternId)
    }
    
    private fun setTempo(tempo: Float) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.TEMPO_CHANGE,
                description = "${currentPattern.tempo} → $tempo BPM"
            )
            
            val updatedPattern = currentPattern.copy(tempo = tempo)
            updatePattern(updatedPattern)
        }
    }
    
    private fun setSwing(swing: Float) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.SWING_CHANGE,
                description = "${(currentPattern.swing * 100).toInt()}% → ${(swing * 100).toInt()}%"
            )
            
            val updatedPattern = currentPattern.copy(swing = swing)
            updatePattern(updatedPattern)
        }
    }
    
    fun toggleStep(padIndex: Int, stepIndex: Int) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.STEP_TOGGLE,
                description = "Pad ${padIndex + 1}, Step ${stepIndex + 1}"
            )
            
            val updatedPattern = patternManager.toggleStep(currentPattern, padIndex, stepIndex)
            updatePattern(updatedPattern)
        }
    }
    
    fun setStepVelocity(padIndex: Int, stepIndex: Int, velocity: Int) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            // Save current state before modification
            historyManager.saveState(
                pattern = currentPattern,
                operation = HistoryOperation.STEP_VELOCITY,
                description = "Pad ${padIndex + 1}, Step ${stepIndex + 1} = $velocity"
            )
            
            val updatedPattern = patternManager.setStepVelocity(currentPattern, padIndex, stepIndex, velocity)
            updatePattern(updatedPattern)
        }
    }
    
    fun togglePadSelection(padIndex: Int) {
        _sequencerState.update { state ->
            val selectedPads = state.selectedPads.toMutableSet()
            if (selectedPads.contains(padIndex)) {
                selectedPads.remove(padIndex)
            } else {
                selectedPads.add(padIndex)
            }
            state.copy(selectedPads = selectedPads)
        }
    }
    
    fun togglePadMute(padIndex: Int) {
        _muteSoloState.update { it.toggleMute(padIndex) }
    }
    
    fun togglePadSolo(padIndex: Int) {
        _muteSoloState.update { it.toggleSolo(padIndex) }
    }
    
    fun selectAllPads() {
        _sequencerState.update { state ->
            state.copy(selectedPads = (0..15).toSet())
        }
    }
    
    fun selectAssignedPads() {
        val assignedPadIndices = _pads.value
            .filter { it.hasAssignedSample }
            .map { it.index }
            .toSet()
        
        _sequencerState.update { state ->
            state.copy(selectedPads = assignedPadIndices)
        }
    }
    
    fun selectPattern(patternId: String) {
        _sequencerState.update { it.copy(currentPattern = patternId) }
        historyManager.setCurrentPattern(patternId)
    }
    
    fun createPattern(name: String, length: Int) {
        viewModelScope.launch {
            try {
                val newPattern = Pattern(
                    name = name,
                    length = length,
                    tempo = 120f,
                    swing = 0f
                )
                
                patternRepository.savePattern(newPattern)
                
                val updatedPatterns = _patterns.value + newPattern
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    state.copy(
                        currentPattern = newPattern.id,
                        patterns = updatedPatterns.map { it.id }
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun duplicatePattern(patternId: String) {
        viewModelScope.launch {
            try {
                val originalPattern = _patterns.value.find { it.id == patternId } ?: return@launch
                val duplicatedPattern = originalPattern.copy(
                    id = generatePatternId(),
                    name = "${originalPattern.name} Copy"
                )
                
                patternRepository.savePattern(duplicatedPattern)
                
                val updatedPatterns = _patterns.value + duplicatedPattern
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    state.copy(patterns = updatedPatterns.map { it.id })
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deletePattern(patternId: String) {
        viewModelScope.launch {
            try {
                patternRepository.deletePattern(patternId)
                
                val updatedPatterns = _patterns.value.filter { it.id != patternId }
                _patterns.value = updatedPatterns
                
                _sequencerState.update { state ->
                    val newCurrentPattern = if (state.currentPattern == patternId) {
                        updatedPatterns.firstOrNull()?.id
                    } else {
                        state.currentPattern
                    }
                    
                    state.copy(
                        currentPattern = newCurrentPattern,
                        patterns = updatedPatterns.map { it.id }
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun renamePattern(patternId: String, newName: String) {
        viewModelScope.launch {
            try {
                val pattern = _patterns.value.find { it.id == patternId } ?: return@launch
                val updatedPattern = pattern.copy(name = newName)
                
                patternRepository.savePattern(updatedPattern)
                updatePattern(updatedPattern)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun generatePatternId(): String {
        return "pattern_${System.currentTimeMillis()}"
    }
    
    private fun getCurrentPattern(): Pattern? {
        val currentPatternId = _sequencerState.value.currentPattern
        return _patterns.value.find { it.id == currentPatternId }
    }
    
    private fun updatePattern(pattern: Pattern) {
        val updatedPatterns = _patterns.value.map { 
            if (it.id == pattern.id) pattern else it 
        }
        _patterns.value = updatedPatterns
        
        // Save to repository
        viewModelScope.launch {
            try {
                patternRepository.savePattern(pattern)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Update pattern without saving to history (used for undo/redo)
     */
    private fun updatePatternWithoutHistory(pattern: Pattern) {
        val updatedPatterns = _patterns.value.map { 
            if (it.id == pattern.id) pattern else it 
        }
        _patterns.value = updatedPatterns
        
        // Save to repository
        viewModelScope.launch {
            try {
                patternRepository.savePattern(pattern)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    // Song Mode Methods
    
    /**
     * Create a new song with pattern sequence
     */
    fun createSong(sequence: List<SongStep>) {
        viewModelScope.launch {
            val songMode = SongMode(
                sequence = sequence,
                isActive = true,
                loopEnabled = true
            )
            
            // Update sequencer state with song mode
            _sequencerState.update { 
                it.copy(songMode = songMode)
            }
            
            // Initialize song mode manager
            songModeManager.initializeSong(songMode) { patternId ->
                selectPattern(patternId)
            }
            
            // Initialize navigation
            songNavigationManager.initializeNavigation(songMode)
        }
    }
    
    /**
     * Start song playback with enhanced engine
     */
    fun startSong(startPosition: Int = 0, startRepeat: Int = 0) {
        viewModelScope.launch {
            songPlaybackEngine.startPlayback(startPosition, startRepeat)
            songModeManager.startSong()
        }
    }
    
    /**
     * Stop song playback with enhanced engine
     */
    fun stopSong() {
        viewModelScope.launch {
            songPlaybackEngine.stopPlayback()
            songModeManager.stopSong()
            stopPattern()
        }
    }
    
    /**
     * Pause song playback with enhanced engine
     */
    fun pauseSong() {
        viewModelScope.launch {
            songPlaybackEngine.pausePlayback()
            songModeManager.pauseSong()
            pausePattern()
        }
    }
    
    /**
     * Resume song playback with enhanced engine
     */
    fun resumeSong() {
        viewModelScope.launch {
            songPlaybackEngine.resumePlayback()
            songModeManager.resumeSong()
        }
    }
    
    /**
     * Navigate to specific position in song
     */
    fun navigateToSongPosition(sequencePosition: Int, patternRepeat: Int = 0) {
        songModeManager.navigateToPosition(sequencePosition, patternRepeat)
        
        // Update navigation manager
        songNavigationManager.updatePosition(sequencePosition, patternRepeat, 0)
    }
    
    /**
     * Navigate to timeline position (0.0 to 1.0)
     */
    fun navigateToTimelinePosition(position: Float) {
        val songPosition = songNavigationManager.navigateToTimelinePosition(position)
        if (songPosition != null) {
            songModeManager.navigateToPosition(
                songPosition.sequencePosition,
                songPosition.patternRepeat
            )
        }
    }
    
    /**
     * Move to next pattern in song
     */
    fun nextSongPattern() {
        songModeManager.nextPattern()
    }
    
    /**
     * Move to previous pattern in song
     */
    fun previousSongPattern() {
        songModeManager.previousPattern()
    }
    
    /**
     * Toggle song loop mode
     */
    fun toggleSongLoop() {
        songModeManager.toggleLoop()
        
        // Update sequencer state
        val updatedSongMode = songModeManager.songState.value.songMode
        if (updatedSongMode != null) {
            _sequencerState.update { 
                it.copy(songMode = updatedSongMode)
            }
        }
    }
    
    /**
     * Update song sequence
     */
    fun updateSongSequence(sequence: List<SongStep>) {
        songModeManager.updateSongSequence(sequence)
        
        // Update sequencer state
        val updatedSongMode = songModeManager.songState.value.songMode
        if (updatedSongMode != null) {
            _sequencerState.update { 
                it.copy(songMode = updatedSongMode)
            }
            
            // Update navigation
            songNavigationManager.initializeNavigation(updatedSongMode)
        }
    }
    
    /**
     * Activate song mode
     */
    fun activateSongMode() {
        songModeManager.activateSongMode()
        
        val updatedSongMode = songModeManager.songState.value.songMode
        if (updatedSongMode != null) {
            _sequencerState.update { 
                it.copy(songMode = updatedSongMode)
            }
        }
    }
    
    /**
     * Deactivate song mode (return to pattern mode)
     */
    fun deactivateSongMode() {
        songModeManager.deactivateSongMode()
        
        _sequencerState.update { 
            it.copy(songMode = null)
        }
    }
    
    /**
     * Get current song progress
     */
    fun getSongProgress(): Float {
        return songNavigationManager.getPlaybackProgress()
    }
    
    /**
     * Get timeline markers for visualization
     */
    fun getTimelineMarkers(): List<TimelineMarker> {
        return songNavigationManager.getTimelineMarkers()
    }
    
    /**
     * Handle pattern completion for song mode
     */
    private fun onPatternComplete() {
        val songState = songModeManager.songState.value
        if (songState.isActive && songState.isPlaying) {
            songModeManager.onPatternComplete()
        }
    }
    
    // Export and Sharing Methods
    
    /**
     * Export current song arrangement
     */
    fun exportSongArrangement(projectName: String = "Untitled Song") {
        viewModelScope.launch {
            val songMode = _sequencerState.value.songMode ?: return@launch
            val patterns = _patterns.value
            
            val result = songExportManager.exportSongArrangement(
                songMode = songMode,
                patterns = patterns,
                projectName = projectName
            )
            
            result.onSuccess { exportResult ->
                // Export successful - UI can show success message
            }.onFailure { error ->
                // Export failed - UI can show error message
            }
        }
    }
    
    /**
     * Export current pattern
     */
    fun exportCurrentPattern(includeMetadata: Boolean = true) {
        viewModelScope.launch {
            val currentPattern = getCurrentPattern() ?: return@launch
            
            val result = songExportManager.exportPattern(
                pattern = currentPattern,
                includeMetadata = includeMetadata
            )
            
            result.onSuccess { exportResult ->
                // Export successful
            }.onFailure { error ->
                // Export failed
            }
        }
    }
    
    /**
     * Export song timeline as text
     */
    fun exportSongTimeline(includePatternDetails: Boolean = true) {
        viewModelScope.launch {
            val songMode = _sequencerState.value.songMode ?: return@launch
            val patterns = _patterns.value
            
            val result = songExportManager.exportSongTimeline(
                songMode = songMode,
                patterns = patterns,
                includePatternDetails = includePatternDetails
            )
            
            result.onSuccess { exportResult ->
                // Export successful
            }.onFailure { error ->
                // Export failed
            }
        }
    }
    
    /**
     * Share exported file
     */
    fun shareExportedFile(exportResult: ExportResult): Intent? {
        return songExportManager.shareExportedFile(exportResult)
    }
    
    /**
     * Import song arrangement from file
     */
    fun importSongArrangement(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = songExportManager.importSongArrangement(uri)
            
            result.onSuccess { importResult ->
                // Apply imported song
                _sequencerState.update { 
                    it.copy(songMode = importResult.songMode)
                }
                
                // Update patterns
                val updatedPatterns = _patterns.value.toMutableList()
                importResult.patterns.forEach { importedPattern ->
                    // Add or update pattern
                    val existingIndex = updatedPatterns.indexOfFirst { it.id == importedPattern.id }
                    if (existingIndex >= 0) {
                        updatedPatterns[existingIndex] = importedPattern
                    } else {
                        updatedPatterns.add(importedPattern)
                    }
                }
                _patterns.value = updatedPatterns
                
                // Initialize song mode with imported data
                songPlaybackEngine.updateSongSequence(importResult.songMode.sequence)
                songNavigationManager.initializeNavigation(importResult.songMode)
                
            }.onFailure { error ->
                // Import failed
            }
        }
    }
    
    /**
     * Get list of exported files
     */
    fun getExportedFiles(): List<ExportedFileInfo> {
        return songExportManager.getExportedFiles()
    }
    
    /**
     * Delete exported file
     */
    fun deleteExportedFile(file: java.io.File): Boolean {
        return songExportManager.deleteExportedFile(file)
    }
    
    /**
     * Clear export state
     */
    fun clearExportState() {
        songExportManager.clearState()
    }
    
    /**
     * Get current song progress for UI
     */
    fun getCurrentSongProgress(): Float {
        return songPlaybackEngine.getSongProgress()
    }
    
    /**
     * Get estimated remaining time in current song
     */
    fun getRemainingTime(): Long {
        val currentPattern = getCurrentPattern()
        val tempo = currentPattern?.tempo ?: 120f
        return songPlaybackEngine.getRemainingTime(tempo)
    }
    
    /**
     * Get current pattern info in song context
     */
    fun getCurrentPatternInfo(): CurrentPatternInfo? {
        return songPlaybackEngine.getCurrentPatternInfo()
    }
    
    /**
     * Check if at end of song
     */
    fun isAtEndOfSong(): Boolean {
        return songPlaybackEngine.isAtEndOfSong()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup timing engine and other resources
    }
}

/**
 * Comprehensive performance statistics for monitoring
 */
data class PerformanceStatistics(
    val cacheStatistics: SequencerCacheStatistics,
    val voiceStatistics: VoiceStatistics,
    val performanceMetrics: SequencerPerformanceMetrics,
    val optimizationState: OptimizationState,
    val recommendations: List<OptimizationRecommendation>
)    //
 Enhanced State Management Methods
    
    /**
     * Update sequencer settings
     * Requirements: 9.7 - settings and preferences
     */
    fun updateSettings(settings: SequencerSettings) {
        viewModelScope.launch {
            try {
                _sequencerSettings.value = settings
                applySettingsToComponents(settings)
                
                // Save settings to preferences
                // TODO: Implement settings persistence
                
                emitUserFeedback(UserFeedback.Success("Settings updated successfully"))
                logger.logInfo("SequencerViewModel", "Settings updated")
            } catch (e: Exception) {
                logger.logError("SequencerViewModel", "Failed to update settings", exception = e)
                emitUserFeedback(UserFeedback.Error("Failed to update settings"))
            }
        }
    }
    
    /**
     * Get current UI state for components
     * Requirements: All requirements - comprehensive state access
     */
    fun getCurrentUIState(): SequencerUIState = _uiState.value
    
    /**
     * Update loading state for specific operations
     */
    fun updateLoadingState(operation: String, isLoading: Boolean) {
        _loadingStates.update { current ->
            val newStates = current.operations.toMutableMap()
            if (isLoading) {
                newStates[operation] = System.currentTimeMillis()
            } else {
                newStates.remove(operation)
            }
            current.copy(operations = newStates)
        }
    }
    
    /**
     * Check if any operation is currently loading
     */
    fun isAnyOperationLoading(): Boolean = _loadingStates.value.operations.isNotEmpty()
    
    /**
     * Get loading state for specific operation
     */
    fun isOperationLoading(operation: String): Boolean = 
        _loadingStates.value.operations.containsKey(operation)
    
    /**
     * Clear all user feedback messages
     */
    fun clearUserFeedback() {
        // User feedback is handled by SharedFlow, no need to clear
        logger.logInfo("SequencerViewModel", "User feedback cleared")
    }
    
    /**
     * Get comprehensive sequencer status for debugging
     */
    fun getSequencerStatus(): SequencerStatus {
        return SequencerStatus(
            sequencerState = _sequencerState.value,
            uiState = _uiState.value,
            loadingStates = _loadingStates.value,
            settings = _sequencerSettings.value,
            performanceMetrics = performanceMetrics.value,
            errorState = errorState.value,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Force refresh of all state components
     */
    fun refreshAllStates() {
        viewModelScope.launch {
            try {
                updateLoadingState("refresh", true)
                
                // Refresh patterns
                loadPatterns()
                
                // Refresh pad system
                padSystemIntegration.forcePadResync()
                
                // Refresh performance optimization
                performanceOptimizer.refreshMetrics()
                
                // Clear any stale errors
                errorHandler.clearStaleErrors()
                
                emitUserFeedback(UserFeedback.Success("All states refreshed"))
                logger.logInfo("SequencerViewModel", "All states refreshed successfully")
                
            } catch (e: Exception) {
                logger.logError("SequencerViewModel", "Failed to refresh states", exception = e)
                emitUserFeedback(UserFeedback.Error("Failed to refresh states"))
            } finally {
                updateLoadingState("refresh", false)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Cleanup resources
        viewModelScope.launch {
            try {
                // Stop any active playback
                if (_sequencerState.value.isPlaying) {
                    stopPattern()
                }
                
                // Release all voices
                sequencerVoiceManager.releaseAllVoices()
                
                // Clear caches
                sequencerSampleCache.clearCache()
                
                // Shutdown components
                performanceOptimizer.shutdown()
                errorHandler.shutdown()
                logger.shutdown()
                
                logger.logInfo("SequencerViewModel", "ViewModel cleanup completed")
            } catch (e: Exception) {
                // Log error but don't throw during cleanup
                android.util.Log.e(TAG, "Error during ViewModel cleanup", e)
            }
        }
    }
}

/**
 * Enhanced UI state for comprehensive state management
 */
data class SequencerUIState(
    val isInitialized: Boolean = false,
    val hasActivePattern: Boolean = false,
    val canRecord: Boolean = false,
    val canPlayback: Boolean = false,
    val hasErrors: Boolean = false,
    val isPerformanceOptimized: Boolean = true,
    val activeFeatures: List<ActiveFeature> = emptyList(),
    val statusMessage: String = "Initializing..."
)

/**
 * Active features in the sequencer
 */
enum class ActiveFeature {
    PLAYBACK,
    RECORDING,
    SONG_MODE,
    PATTERN_LOADED,
    OVERDUB,
    METRONOME
}

/**
 * User feedback types for UI notifications
 */
sealed class UserFeedback {
    data class Success(val message: String) : UserFeedback()
    data class Warning(val message: String) : UserFeedback()
    data class Error(val message: String) : UserFeedback()
    data class Critical(val message: String) : UserFeedback()
    data class Info(val message: String) : UserFeedback()
}

/**
 * Loading states for async operations
 */
data class LoadingStates(
    val operations: Map<String, Long> = emptyMap()
) {
    val isAnyLoading: Boolean get() = operations.isNotEmpty()
    val loadingOperations: List<String> get() = operations.keys.toList()
}

/**
 * Sequencer settings and preferences
 */
data class SequencerSettings(
    val defaultTempo: Float = 120f,
    val defaultSwing: Float = 0f,
    val defaultQuantization: Quantization = Quantization.SIXTEENTH,
    val autoSaveEnabled: Boolean = true,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val visualFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val metronomeEnabled: Boolean = false,
    val recordingMode: RecordingMode = RecordingMode.REPLACE,
    val maxPatternLength: Int = 32,
    val maxPatterns: Int = 64,
    val enableAdvancedFeatures: Boolean = true
)

/**
 * Performance mode settings
 */
enum class PerformanceMode {
    POWER_SAVE,
    BALANCED,
    PERFORMANCE
}

/**
 * Comprehensive sequencer status for debugging
 */
data class SequencerStatus(
    val sequencerState: SequencerState,
    val uiState: SequencerUIState,
    val loadingStates: LoadingStates,
    val settings: SequencerSettings,
    val performanceMetrics: SequencerPerformanceMetrics,
    val errorState: SequencerErrorState,
    val timestamp: Long
)