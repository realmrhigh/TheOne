package com.high.theone.features.sequencer

import android.util.Log
import com.high.theone.features.sampling.VoiceManager
import com.high.theone.model.PlaybackMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// Constants
private const val MAX_SEQUENCER_VOICES = 32

/**
 * Specialized voice management system optimized for sequencer playback.
 * Provides intelligent voice allocation, polyphony management, and performance
 * optimization for real-time pattern playback.
 * 
 * Requirements: 10.2, 10.4, 10.5
 */
@Singleton
class SequencerVoiceManager @Inject constructor(
    private val baseVoiceManager: VoiceManager
) {
    companion object {
        private const val TAG = "SequencerVoiceManager"
        private const val VOICE_CLEANUP_INTERVAL_MS = 1000L
        private const val VOICE_TIMEOUT_MS = 30000L
        private const val POLYPHONY_LIMIT_PER_PAD = 4
    }

    // Voice allocation tracking
    private val activeVoices = ConcurrentHashMap<String, SequencerVoice>()
    private val voicesByPad = ConcurrentHashMap<Int, MutableSet<String>>()
    private val voiceIdCounter = AtomicInteger(0)
    
    // Voice management state
    private val _voiceState = MutableStateFlow(VoiceManagementState())
    val voiceState: StateFlow<VoiceManagementState> = _voiceState.asStateFlow()
    
    // Cleanup coroutine
    private var cleanupJob: Job? = null

    /**
     * Initialize the sequencer voice manager
     * Requirements: 10.2
     */
    fun initialize() {
        startVoiceCleanup()
        Log.d(TAG, "Sequencer voice manager initialized")
    }

    /**
     * Allocate voice for sequencer step trigger with optimization
     * Requirements: 10.2, 10.5
     */
    suspend fun allocateSequencerVoice(
        padIndex: Int,
        sampleId: String,
        velocity: Float,
        playbackMode: PlaybackMode,
        stepIndex: Int,
        priority: VoicePriority = VoicePriority.NORMAL
    ): String? {
        return withContext(Dispatchers.Default) {
            try {
                // Check voice limits
                if (!canAllocateVoice(padIndex, priority)) {
                    handleVoiceLimitExceeded(padIndex, priority)
                }
                
                // Generate unique voice ID
                val voiceId = generateVoiceId()
                
                // Allocate voice through base manager
                val baseVoiceId = baseVoiceManager.allocateVoice(
                    padIndex = padIndex,
                    sampleId = sampleId,
                    velocity = velocity,
                    playbackMode = playbackMode,
                    priority = when (priority) {
                        VoicePriority.LOW -> VoiceManager.VoicePriority.LOW
                        VoicePriority.NORMAL -> VoiceManager.VoicePriority.NORMAL
                        VoicePriority.HIGH -> VoiceManager.VoicePriority.HIGH
                        VoicePriority.CRITICAL -> VoiceManager.VoicePriority.HIGH
                    }
                )
                
                if (baseVoiceId != null) {
                    // Track voice in sequencer system
                    val sequencerVoice = SequencerVoice(
                        voiceId = voiceId,
                        baseVoiceId = baseVoiceId,
                        padIndex = padIndex,
                        sampleId = sampleId,
                        velocity = velocity,
                        playbackMode = playbackMode,
                        stepIndex = stepIndex,
                        priority = priority,
                        startTime = System.currentTimeMillis(),
                        isActive = true
                    )
                    
                    activeVoices[voiceId] = sequencerVoice
                    voicesByPad.getOrPut(padIndex) { mutableSetOf() }.add(voiceId)
                    
                    updateVoiceState()
                    
                    Log.d(TAG, "Allocated sequencer voice $voiceId for pad $padIndex, step $stepIndex")
                    voiceId
                } else {
                    Log.w(TAG, "Failed to allocate base voice for pad $padIndex")
                    null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error allocating sequencer voice", e)
                null
            }
        }
    }

    /**
     * Release sequencer voice with cleanup
     * Requirements: 10.2, 10.5
     */
    suspend fun releaseSequencerVoice(voiceId: String) {
        withContext(Dispatchers.Default) {
            try {
                activeVoices.remove(voiceId)?.let { voice ->
                    // Release base voice
                    baseVoiceManager.releaseVoice(voice.baseVoiceId)
                    
                    // Remove from pad tracking
                    voicesByPad[voice.padIndex]?.remove(voiceId)
                    if (voicesByPad[voice.padIndex]?.isEmpty() == true) {
                        voicesByPad.remove(voice.padIndex)
                    }
                    
                    updateVoiceState()
                    
                    Log.d(TAG, "Released sequencer voice $voiceId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing sequencer voice $voiceId", e)
            }
        }
    }

    /**
     * Release all voices for a specific pad
     * Requirements: 10.2, 10.5
     */
    suspend fun releaseAllVoicesForPad(padIndex: Int) {
        withContext(Dispatchers.Default) {
            try {
                val voicesToRelease = voicesByPad[padIndex]?.toList() ?: emptyList()
                
                voicesToRelease.forEach { voiceId ->
                    releaseSequencerVoice(voiceId)
                }
                
                Log.d(TAG, "Released ${voicesToRelease.size} voices for pad $padIndex")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing voices for pad $padIndex", e)
            }
        }
    }

    /**
     * Optimize voice allocation for performance
     * Requirements: 10.4, 10.5
     */
    suspend fun optimizeVoiceAllocation() {
        withContext(Dispatchers.Default) {
            try {
                val currentTime = System.currentTimeMillis()
                val voicesToRelease = mutableListOf<String>()
                
                // Find voices to release based on various criteria
                activeVoices.forEach { (voiceId, voice) ->
                    val shouldRelease = when {
                        // Release old voices
                        (currentTime - voice.startTime) > VOICE_TIMEOUT_MS -> true
                        
                        // Release low priority voices if we're at capacity
                        activeVoices.size > MAX_SEQUENCER_VOICES * 0.8 && 
                        voice.priority == VoicePriority.LOW -> true
                        
                        // Release one-shot voices that have been playing too long
                        voice.playbackMode == PlaybackMode.ONE_SHOT && 
                        (currentTime - voice.startTime) > 5000L -> true
                        
                        else -> false
                    }
                    
                    if (shouldRelease) {
                        voicesToRelease.add(voiceId)
                    }
                }
                
                // Release selected voices
                voicesToRelease.forEach { voiceId ->
                    releaseSequencerVoice(voiceId)
                }
                
                if (voicesToRelease.isNotEmpty()) {
                    Log.d(TAG, "Optimized voice allocation: released ${voicesToRelease.size} voices")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing voice allocation", e)
            }
        }
    }

    /**
     * Prepare voices for pattern playback
     * Requirements: 10.2, 10.4
     */
    suspend fun prepareForPatternPlayback(
        padSampleMap: Map<Int, String>,
        maxPolyphony: Int = POLYPHONY_LIMIT_PER_PAD
    ) {
        withContext(Dispatchers.Default) {
            try {
                // Release voices for pads that no longer have samples
                val activePads = voicesByPad.keys.toSet()
                val assignedPads = padSampleMap.keys.toSet()
                val padsToClean = activePads - assignedPads
                
                padsToClean.forEach { padIndex ->
                    releaseAllVoicesForPad(padIndex)
                }
                
                // Limit polyphony per pad
                voicesByPad.forEach { (padIndex, voices) ->
                    if (voices.size > maxPolyphony) {
                        val voicesToRelease = voices.toList()
                            .sortedBy { activeVoices[it]?.startTime ?: 0L }
                            .take(voices.size - maxPolyphony)
                        
                        voicesToRelease.forEach { voiceId ->
                            releaseSequencerVoice(voiceId)
                        }
                    }
                }
                
                Log.d(TAG, "Prepared voices for pattern playback")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing for pattern playback", e)
            }
        }
    }

    /**
     * Handle voice stealing when limits are exceeded
     * Requirements: 10.5
     */
    private suspend fun handleVoiceLimitExceeded(padIndex: Int, newVoicePriority: VoicePriority) {
        try {
            // First, try to release voices from the same pad
            val padVoices = voicesByPad[padIndex]?.toList() ?: emptyList()
            if (padVoices.isNotEmpty()) {
                // Release oldest voice from same pad
                val oldestVoice = padVoices
                    .mapNotNull { activeVoices[it] }
                    .minByOrNull { it.startTime }
                
                if (oldestVoice != null) {
                    releaseSequencerVoice(oldestVoice.voiceId)
                    return
                }
            }
            
            // If no voices on same pad, steal from other pads based on priority
            val candidatesForStealing = activeVoices.values
                .filter { it.priority.ordinal < newVoicePriority.ordinal }
                .sortedWith(compareBy<SequencerVoice> { it.priority.ordinal }
                    .thenBy { it.startTime })
            
            if (candidatesForStealing.isNotEmpty()) {
                releaseSequencerVoice(candidatesForStealing.first().voiceId)
                Log.d(TAG, "Stole voice for higher priority allocation")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling voice limit exceeded", e)
        }
    }

    /**
     * Check if voice can be allocated
     * Requirements: 10.5
     */
    private fun canAllocateVoice(padIndex: Int, priority: VoicePriority): Boolean {
        // Check global voice limit
        if (activeVoices.size >= MAX_SEQUENCER_VOICES) {
            return priority >= VoicePriority.HIGH
        }
        
        // Check per-pad polyphony limit
        val padVoiceCount = voicesByPad[padIndex]?.size ?: 0
        if (padVoiceCount >= POLYPHONY_LIMIT_PER_PAD) {
            return priority >= VoicePriority.NORMAL
        }
        
        return true
    }

    /**
     * Generate unique voice ID
     */
    private fun generateVoiceId(): String {
        return "seq_voice_${voiceIdCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }

    /**
     * Update voice management state
     * Requirements: 10.1
     */
    private fun updateVoiceState() {
        val currentTime = System.currentTimeMillis()
        
        _voiceState.value = VoiceManagementState(
            totalActiveVoices = activeVoices.size,
            maxVoices = MAX_SEQUENCER_VOICES,
            voicesByPriority = activeVoices.values.groupBy { it.priority }.mapValues { it.value.size },
            voicesByPad = voicesByPad.mapValues { it.value.size },
            averageVoiceAge = if (activeVoices.isNotEmpty()) {
                activeVoices.values.map { currentTime - it.startTime }.average().toLong()
            } else {
                0L
            },
            lastUpdate = currentTime
        )
    }

    /**
     * Start periodic voice cleanup
     * Requirements: 10.5, 10.6
     */
    private fun startVoiceCleanup() {
        cleanupJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    performVoiceCleanup()
                    delay(VOICE_CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in voice cleanup", e)
                    delay(5000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Perform periodic voice cleanup
     * Requirements: 10.5, 10.6
     */
    private suspend fun performVoiceCleanup() {
        try {
            val currentTime = System.currentTimeMillis()
            val voicesToCleanup = mutableListOf<String>()
            
            activeVoices.forEach { (voiceId, voice) ->
                // Clean up old voices
                if ((currentTime - voice.startTime) > VOICE_TIMEOUT_MS) {
                    voicesToCleanup.add(voiceId)
                }
                
                // Clean up one-shot voices that should have finished
                if (voice.playbackMode == PlaybackMode.ONE_SHOT && 
                    (currentTime - voice.startTime) > 10000L) {
                    voicesToCleanup.add(voiceId)
                }
            }
            
            voicesToCleanup.forEach { voiceId ->
                releaseSequencerVoice(voiceId)
            }
            
            if (voicesToCleanup.isNotEmpty()) {
                Log.d(TAG, "Cleaned up ${voicesToCleanup.size} voices")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in voice cleanup", e)
        }
    }

    /**
     * Get voice statistics for monitoring
     * Requirements: 10.1
     */
    fun getVoiceStatistics(): VoiceStatistics {
        val currentTime = System.currentTimeMillis()
        
        return VoiceStatistics(
            totalVoices = activeVoices.size,
            maxVoices = MAX_SEQUENCER_VOICES,
            utilizationPercent = (activeVoices.size.toFloat() / MAX_SEQUENCER_VOICES.toFloat()) * 100f,
            voicesByPriority = activeVoices.values.groupBy { it.priority }.mapValues { it.value.size },
            voicesByPlaybackMode = activeVoices.values.groupBy { it.playbackMode }.mapValues { it.value.size },
            averageVoiceAge = if (activeVoices.isNotEmpty()) {
                activeVoices.values.map { currentTime - it.startTime }.average().toLong()
            } else {
                0L
            },
            oldestVoiceAge = activeVoices.values.maxOfOrNull { currentTime - it.startTime } ?: 0L
        )
    }

    /**
     * Release all voices
     * Requirements: 10.5
     */
    suspend fun releaseAllVoices() {
        withContext(Dispatchers.Default) {
            try {
                val voicesToRelease = activeVoices.keys.toList()
                
                voicesToRelease.forEach { voiceId ->
                    releaseSequencerVoice(voiceId)
                }
                
                Log.d(TAG, "Released all ${voicesToRelease.size} voices")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing all voices", e)
            }
        }
    }

    /**
     * Shutdown the voice manager
     */
    fun shutdown() {
        cleanupJob?.cancel()
        CoroutineScope(Dispatchers.Default).launch {
            releaseAllVoices()
        }
        Log.d(TAG, "Sequencer voice manager shutdown")
    }
}

/**
 * Sequencer-specific voice information
 */
data class SequencerVoice(
    val voiceId: String,
    val baseVoiceId: String,
    val padIndex: Int,
    val sampleId: String,
    val velocity: Float,
    val playbackMode: PlaybackMode,
    val stepIndex: Int,
    val priority: VoicePriority,
    val startTime: Long,
    val isActive: Boolean
)

/**
 * Voice priority levels for sequencer
 */
enum class VoicePriority {
    LOW,      // Background patterns, upcoming steps
    NORMAL,   // Regular pattern playback
    HIGH,     // Current step triggers, important patterns
    CRITICAL  // Real-time user input, critical system sounds
}

/**
 * Voice management state
 */
data class VoiceManagementState(
    val totalActiveVoices: Int = 0,
    val maxVoices: Int = MAX_SEQUENCER_VOICES,
    val voicesByPriority: Map<VoicePriority, Int> = emptyMap(),
    val voicesByPad: Map<Int, Int> = emptyMap(),
    val averageVoiceAge: Long = 0L,
    val lastUpdate: Long = 0L
)

/**
 * Voice statistics for monitoring
 */
data class VoiceStatistics(
    val totalVoices: Int,
    val maxVoices: Int,
    val utilizationPercent: Float,
    val voicesByPriority: Map<VoicePriority, Int>,
    val voicesByPlaybackMode: Map<PlaybackMode, Int>,
    val averageVoiceAge: Long,
    val oldestVoiceAge: Long
)