package com.high.theone.features.sequencer

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-precision timing engine implementation for step sequencer
 * Uses dedicated audio-priority thread for microsecond-accurate timing
 */
@Singleton
class PrecisionTimingEngine @Inject constructor(
    private val timingCalculator: TimingCalculator,
    private val swingCalculator: SwingCalculator,
    private val callbackManager: StepCallbackManager
) : TimingEngine {
    
    // Thread management
    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private var scheduler: ScheduledExecutorService? = null
    
    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    override val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _currentStep = MutableStateFlow(0)
    override val currentStep: StateFlow<Int> = _currentStep.asStateFlow()
    
    private val _stepProgress = MutableStateFlow(0f)
    override val stepProgress: StateFlow<Float> = _stepProgress.asStateFlow()
    
    // Timing parameters
    private var currentTempo = 120f
    private var currentSwing = 0f
    private var patternLength = 16
    
    // Timing state
    private var stepDurationMicros = AtomicLong(0L)
    private var patternStartTime = AtomicLong(0L)
    private var pausedTime = AtomicLong(0L)
    private var currentStepIndex = AtomicInteger(0)
    
    // Callback management
    private var stepCallback: ((Int, Long) -> Unit)? = null
    private var patternCompleteCallback: (() -> Unit)? = null
    private val callbackId = "main_sequencer_callback"
    
    // Performance monitoring
    private val isRunning = AtomicBoolean(false)
    private val jitterMeasurements = mutableListOf<Long>()
    private var missedCallbacks = AtomicInteger(0)
    private var lastCallbackTime = AtomicLong(0L)
    
    // Timing constants
    private companion object {
        const val MICROSECONDS_PER_MILLISECOND = 1000L
        const val TIMING_THREAD_NAME = "SequencerTiming"
        const val PROGRESS_UPDATE_INTERVAL_MS = 16L // ~60fps UI updates
        const val MAX_JITTER_SAMPLES = 100
        const val CALLBACK_TOLERANCE_MICROS = 5000L // 5ms tolerance
    }
    
    init {
        initializeAudioThread()
    }
    
    private fun initializeAudioThread() {
        audioThread = HandlerThread(TIMING_THREAD_NAME, Process.THREAD_PRIORITY_AUDIO).apply {
            start()
        }
        audioHandler = Handler(audioThread!!.looper)
        
        // Create high-priority scheduler for timing callbacks
        scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "SequencerScheduler").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
            }
        }
    }
    
    override fun start(tempo: Float, swing: Float, patternLength: Int) {
        if (isRunning.get()) {
            stop()
        }
        
        // Validate and set parameters
        currentTempo = TempoUtils.clampTempo(tempo)
        currentSwing = swing.coerceIn(0f, 0.75f)
        this.patternLength = patternLength.coerceIn(8, 32)
        
        // Calculate timing
        val stepDurationMs = timingCalculator.calculateStepDuration(currentTempo)
        stepDurationMicros.set(stepDurationMs * MICROSECONDS_PER_MILLISECOND)
        
        // Reset state
        currentStepIndex.set(0)
        patternStartTime.set(SystemClock.elapsedRealtimeNanos() / 1000L) // Convert to microseconds
        pausedTime.set(0L)
        isRunning.set(true)
        
        // Update state flows
        _isPlaying.value = true
        _isPaused.value = false
        _currentStep.value = 0
        _stepProgress.value = 0f
        
        // Start timing loops
        startTimingLoop()
        startProgressUpdates()
    }
    
    override fun stop() {
        isRunning.set(false)
        scheduler?.shutdown()
        
        // Reset state
        currentStepIndex.set(0)
        patternStartTime.set(0L)
        pausedTime.set(0L)
        
        // Update state flows
        _isPlaying.value = false
        _isPaused.value = false
        _currentStep.value = 0
        _stepProgress.value = 0f
        
        // Reinitialize scheduler for next use
        initializeScheduler()
    }
    
    override fun pause() {
        if (!_isPlaying.value || _isPaused.value) return
        
        pausedTime.set(SystemClock.elapsedRealtimeNanos() / 1000L)
        isRunning.set(false)
        
        _isPaused.value = true
        scheduler?.shutdown()
    }
    
    override fun resume() {
        if (!_isPaused.value) return
        
        // Adjust pattern start time to account for pause duration
        val pauseDuration = (SystemClock.elapsedRealtimeNanos() / 1000L) - pausedTime.get()
        patternStartTime.addAndGet(pauseDuration)
        
        isRunning.set(true)
        _isPaused.value = false
        
        // Restart timing
        initializeScheduler()
        startTimingLoop()
    }
    
    override fun setTempo(bpm: Float) {
        val newTempo = TempoUtils.clampTempo(bpm)
        if (newTempo == currentTempo) return
        
        currentTempo = newTempo
        
        // Recalculate step duration
        val stepDurationMs = timingCalculator.calculateStepDuration(currentTempo)
        stepDurationMicros.set(stepDurationMs * MICROSECONDS_PER_MILLISECOND)
        
        // If playing, adjust timing smoothly
        if (_isPlaying.value && !_isPaused.value) {
            // Recalculate pattern start time to maintain current position
            val currentTime = SystemClock.elapsedRealtimeNanos() / 1000L
            val currentStep = currentStepIndex.get()
            val newPatternStartTime = currentTime - (currentStep * stepDurationMicros.get())
            patternStartTime.set(newPatternStartTime)
        }
    }
    
    override fun setSwing(amount: Float) {
        currentSwing = amount.coerceIn(0f, 0.75f)
        // Swing changes take effect on next pattern loop
    }
    
    override fun getCurrentStep(): Int = currentStepIndex.get()
    
    override fun getStepProgress(): Float = _stepProgress.value
    
    override fun scheduleStepCallback(callback: (step: Int, microTime: Long) -> Unit) {
        stepCallback = callback
        callbackManager.registerCallback(
            id = callbackId,
            callback = callback,
            priority = 100 // High priority for main sequencer
        )
    }
    
    override fun schedulePatternCompleteCallback(callback: () -> Unit) {
        patternCompleteCallback = callback
    }
    
    override fun removeStepCallback() {
        stepCallback = null
        callbackManager.unregisterCallback(callbackId)
    }
    
    override fun removePatternCompleteCallback() {
        patternCompleteCallback = null
    }
    
    override fun getTimingStats(): TimingStats {
        val avgJitter = if (jitterMeasurements.isNotEmpty()) {
            jitterMeasurements.average().toLong()
        } else 0L
        
        val maxJitter = jitterMeasurements.maxOrNull() ?: 0L
        
        return TimingStats(
            averageJitter = avgJitter,
            maxJitter = maxJitter,
            missedCallbacks = missedCallbacks.get(),
            cpuUsage = estimateCpuUsage(),
            isRealTime = audioThread?.isAlive == true
        )
    }
    
    override fun release() {
        stop()
        scheduler?.shutdownNow()
        audioThread?.quitSafely()
        audioThread = null
        audioHandler = null
        stepCallback = null
        callbackManager.unregisterCallback(callbackId)
    }
    
    private fun startTimingLoop() {
        if (!isRunning.get()) return
        
        val stepDuration = stepDurationMicros.get()
        if (stepDuration <= 0) return
        
        // Schedule next step callback
        val currentTime = SystemClock.elapsedRealtimeNanos() / 1000L
        val patternStart = patternStartTime.get()
        val currentStep = currentStepIndex.get()
        
        // Calculate timing for current step with swing
        val stepTiming = calculateStepTimingWithSwing(currentStep)
        val nextStepTime = patternStart + stepTiming
        
        val delayMicros = (nextStepTime - currentTime).coerceAtLeast(0L)
        
        scheduler?.schedule({
            if (isRunning.get()) {
                executeStepCallback(currentStep)
                advanceToNextStep()
                startTimingLoop() // Schedule next step
            }
        }, delayMicros, TimeUnit.MICROSECONDS)
    }
    
    private fun executeStepCallback(step: Int) {
        val callbackTime = SystemClock.elapsedRealtimeNanos() / 1000L
        
        // Measure timing accuracy
        measureJitter(callbackTime, step)
        
        try {
            // Use callback manager for enhanced error handling and latency compensation
            callbackManager.executeStepCallbacks(step, callbackTime)
            lastCallbackTime.set(callbackTime)
        } catch (e: Exception) {
            // Log error but don't stop timing
            missedCallbacks.incrementAndGet()
        }
    }
    
    private fun advanceToNextStep() {
        val currentStep = currentStepIndex.get()
        val nextStep = (currentStepIndex.incrementAndGet()) % patternLength
        
        // Update UI state
        audioHandler?.post {
            _currentStep.value = nextStep
        }
        
        // Reset pattern if we've completed a loop
        if (nextStep == 0) {
            val currentTime = SystemClock.elapsedRealtimeNanos() / 1000L
            patternStartTime.set(currentTime)
            
            // Notify pattern completion
            patternCompleteCallback?.invoke()
        }
    }
    
    private fun startProgressUpdates() {
        if (!isRunning.get()) return
        
        audioHandler?.post {
            updateStepProgress()
            
            if (isRunning.get()) {
                audioHandler?.postDelayed({
                    startProgressUpdates()
                }, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    private fun updateStepProgress() {
        if (!_isPlaying.value || _isPaused.value) return
        
        val currentTime = SystemClock.elapsedRealtimeNanos() / 1000L
        val patternStart = patternStartTime.get()
        val currentStep = currentStepIndex.get()
        
        val stepStartTime = patternStart + calculateStepTimingWithSwing(currentStep)
        val stepDuration = stepDurationMicros.get()
        
        val progress = timingCalculator.calculateStepProgress(
            currentTime / MICROSECONDS_PER_MILLISECOND,
            stepStartTime / MICROSECONDS_PER_MILLISECOND,
            stepDuration / MICROSECONDS_PER_MILLISECOND
        )
        
        _stepProgress.value = progress
    }
    
    private fun calculateStepTimingWithSwing(stepIndex: Int): Long {
        val baseStepDuration = stepDurationMicros.get()
        val stepStartTime = stepIndex * baseStepDuration
        
        // Apply swing to off-beat steps
        return if (stepIndex % 2 == 1) {
            val swingDelay = (baseStepDuration * currentSwing).toLong()
            stepStartTime + swingDelay
        } else {
            stepStartTime
        }
    }
    
    private fun measureJitter(actualTime: Long, step: Int) {
        val expectedTime = patternStartTime.get() + calculateStepTimingWithSwing(step)
        val jitter = kotlin.math.abs(actualTime - expectedTime)
        
        synchronized(jitterMeasurements) {
            jitterMeasurements.add(jitter)
            if (jitterMeasurements.size > MAX_JITTER_SAMPLES) {
                jitterMeasurements.removeAt(0)
            }
        }
        
        // Count as missed if jitter exceeds tolerance
        if (jitter > CALLBACK_TOLERANCE_MICROS) {
            missedCallbacks.incrementAndGet()
        }
    }
    
    private fun estimateCpuUsage(): Float {
        // Simple CPU usage estimation based on timing accuracy
        val avgJitter = if (jitterMeasurements.isNotEmpty()) {
            jitterMeasurements.average()
        } else 0.0
        
        // Higher jitter indicates higher CPU load
        return (avgJitter / CALLBACK_TOLERANCE_MICROS.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    
    private fun initializeScheduler() {
        scheduler?.shutdownNow()
        scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "SequencerScheduler").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
            }
        }
    }
}