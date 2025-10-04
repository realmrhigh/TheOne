package com.high.theone.midi.output

import com.high.theone.midi.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Generates MIDI clock pulses for synchronization with external devices.
 * Provides accurate timing, tempo-based clock division, and jitter reduction.
 */
@Singleton
class MidiClockGenerator @Inject constructor(
    private val outputGenerator: MidiOutputGenerator
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Clock state
    private val isRunning = AtomicBoolean(false)
    private val currentBpm = MutableStateFlow(120.0f)
    private val clockDivision = MutableStateFlow(24) // Standard MIDI clock: 24 pulses per quarter note
    private val pulseCounter = AtomicLong(0)
    
    // Timing precision
    private var clockJob: Job? = null
    private var lastPulseTime = 0L
    private val jitterBuffer = ArrayDeque<Long>(10) // For jitter reduction
    
    // Clock pulse flow for monitoring
    private val _clockPulses = MutableSharedFlow<MidiClockPulse>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val clockPulses: SharedFlow<MidiClockPulse> = _clockPulses.asSharedFlow()
    
    // Clock statistics
    private val _clockStatistics = MutableStateFlow(
        MidiClockStatistics(
            isRunning = false,
            currentBpm = 120.0f,
            totalPulses = 0,
            averageJitter = 0.0f,
            maxJitter = 0.0f
        )
    )
    val clockStatistics: StateFlow<MidiClockStatistics> = _clockStatistics.asStateFlow()
    
    /**
     * Starts MIDI clock generation at the specified BPM
     */
    suspend fun startClock(bpm: Float): Result<Unit> {
        return try {
            if (isRunning.get()) {
                stopClock()
            }
            
            currentBpm.value = bpm
            isRunning.set(true)
            pulseCounter.set(0)
            lastPulseTime = System.nanoTime()
            jitterBuffer.clear()
            
            clockJob = scope.launch {
                generateClockPulses()
            }
            
            updateStatistics()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to start MIDI clock: ${e.message}"))
        }
    }
    
    /**
     * Stops MIDI clock generation
     */
    suspend fun stopClock(): Result<Unit> {
        return try {
            isRunning.set(false)
            clockJob?.cancel()
            clockJob = null
            
            updateStatistics()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to stop MIDI clock: ${e.message}"))
        }
    }
    
    /**
     * Changes the clock tempo while running
     */
    suspend fun setTempo(bpm: Float): Result<Unit> {
        return try {
            require(bpm in 20.0f..300.0f) { "BPM must be between 20 and 300" }
            
            currentBpm.value = bpm
            updateStatistics()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set tempo: ${e.message}"))
        }
    }
    
    /**
     * Sets the clock division (pulses per quarter note)
     */
    suspend fun setClockDivision(division: Int): Result<Unit> {
        return try {
            require(division in 1..96) { "Clock division must be between 1 and 96" }
            
            clockDivision.value = division
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to set clock division: ${e.message}"))
        }
    }
    
    /**
     * Gets the current tempo
     */
    fun getCurrentTempo(): Float = currentBpm.value
    
    /**
     * Gets the current clock division
     */
    fun getClockDivision(): Int = clockDivision.value
    
    /**
     * Checks if the clock is currently running
     */
    fun isClockRunning(): Boolean = isRunning.get()
    
    /**
     * Gets the current pulse count
     */
    fun getPulseCount(): Long = pulseCounter.get()
    
    /**
     * Resets the pulse counter
     */
    fun resetPulseCounter() {
        pulseCounter.set(0)
    }
    
    /**
     * Main clock generation loop
     */
    private suspend fun generateClockPulses() {
        var nextPulseTime = System.nanoTime()
        
        while (isRunning.get() && !currentCoroutineContext().job.isCancelled) {
            try {
                val currentTime = System.nanoTime()
                
                // Calculate timing for next pulse
                val bpm = currentBpm.value
                val division = clockDivision.value
                val nanosPerPulse = calculateNanosPerPulse(bpm, division)
                
                // Wait until it's time for the next pulse
                val delayNanos = nextPulseTime - currentTime
                if (delayNanos > 0) {
                    delay(delayNanos / 1_000_000) // Convert to milliseconds
                }
                
                // Send clock pulse
                val actualPulseTime = System.nanoTime()
                sendClockPulse(actualPulseTime)
                
                // Calculate jitter and update statistics
                updateJitterStatistics(actualPulseTime, nextPulseTime)
                
                // Schedule next pulse
                nextPulseTime += nanosPerPulse
                
                // Prevent drift by adjusting if we're getting behind
                val currentTimeAfterPulse = System.nanoTime()
                if (nextPulseTime < currentTimeAfterPulse) {
                    nextPulseTime = currentTimeAfterPulse + nanosPerPulse
                }
                
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                // Log error but continue
                delay(1) // Small delay to prevent tight error loop
            }
        }
    }
    
    /**
     * Sends a single MIDI clock pulse
     */
    private suspend fun sendClockPulse(timestamp: Long) {
        val pulseNumber = pulseCounter.incrementAndGet()
        
        // Create and send MIDI clock message
        val clockMessage = MidiMessage(
            type = MidiMessageType.CLOCK,
            channel = 0,
            data1 = 0,
            data2 = 0,
            timestamp = timestamp
        )
        
        outputGenerator.sendMessageAtTime(clockMessage, timestamp)
        
        // Emit clock pulse for monitoring
        val clockPulse = MidiClockPulse(
            timestamp = timestamp,
            pulseNumber = pulseNumber.toInt(),
            bpm = currentBpm.value
        )
        _clockPulses.tryEmit(clockPulse)
        
        lastPulseTime = timestamp
    }
    
    /**
     * Calculates nanoseconds per clock pulse based on BPM and division
     */
    private fun calculateNanosPerPulse(bpm: Float, division: Int): Long {
        // 60 seconds per minute, 1e9 nanoseconds per second
        // division pulses per quarter note, 4 quarter notes per whole note
        val nanosPerMinute = 60_000_000_000L
        val pulsesPerMinute = bpm * division
        return (nanosPerMinute / pulsesPerMinute).roundToLong()
    }
    
    /**
     * Updates jitter statistics for timing analysis
     */
    private fun updateJitterStatistics(actualTime: Long, expectedTime: Long) {
        val jitter = kotlin.math.abs(actualTime - expectedTime)
        
        // Add to jitter buffer for averaging
        jitterBuffer.addLast(jitter)
        if (jitterBuffer.size > 10) {
            jitterBuffer.removeFirst()
        }
        
        // Calculate average and max jitter
        val averageJitter = if (jitterBuffer.isNotEmpty()) {
            jitterBuffer.average().toFloat() / 1_000_000 // Convert to milliseconds
        } else {
            0.0f
        }
        
        val maxJitter = if (jitterBuffer.isNotEmpty()) {
            jitterBuffer.maxOrNull()?.toFloat()?.div(1_000_000) ?: 0.0f
        } else {
            0.0f
        }
        
        updateStatistics(averageJitter, maxJitter)
    }
    
    /**
     * Updates clock statistics
     */
    private fun updateStatistics(averageJitter: Float = 0.0f, maxJitter: Float = 0.0f) {
        _clockStatistics.value = MidiClockStatistics(
            isRunning = isRunning.get(),
            currentBpm = currentBpm.value,
            totalPulses = pulseCounter.get(),
            averageJitter = averageJitter,
            maxJitter = maxJitter
        )
    }
    
    /**
     * Cleans up resources
     */
    fun shutdown() {
        scope.launch {
            stopClock()
        }
        scope.cancel()
    }
}

/**
 * Statistics for MIDI clock generation
 */
data class MidiClockStatistics(
    val isRunning: Boolean,
    val currentBpm: Float,
    val totalPulses: Long,
    val averageJitter: Float, // in milliseconds
    val maxJitter: Float // in milliseconds
)