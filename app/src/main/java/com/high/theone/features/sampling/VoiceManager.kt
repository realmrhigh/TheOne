package com.high.theone.features.sampling

import android.util.Log
import com.high.theone.audio.AudioEngineControl
import com.high.theone.model.PlaybackMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages voice allocation and optimization for responsive playback.
 * Requirements: 3.3, 4.1, 4.2 (voice management optimization)
 */
@Singleton
class VoiceManager @Inject constructor(
    private val audioEngine: AudioEngineControl
) {
    companion object {
        private const val TAG = "VoiceManager"
        private const val MAX_VOICES = 16 // Maximum concurrent voices
        private const val VOICE_STEAL_THRESHOLD = 0.8f // Steal voices when 80% full
    }

    private val activeVoices = ConcurrentHashMap<String, VoiceInfo>()
    private val voiceMutex = Mutex()
    private var nextVoiceId = 0

    data class VoiceInfo(
        val voiceId: String,
        val padIndex: Int,
        val sampleId: String,
        val startTime: Long,
        val velocity: Float,
        val playbackMode: PlaybackMode,
        val priority: VoicePriority = VoicePriority.NORMAL
    )

    enum class VoicePriority {
        LOW,      // Background sounds, can be stolen easily
        NORMAL,   // Regular pad triggers
        HIGH,     // Important sounds, avoid stealing
        CRITICAL  // Never steal these voices
    }

    /**
     * Allocate a voice for pad triggering with intelligent voice management.
     */
    suspend fun allocateVoice(
        padIndex: Int,
        sampleId: String,
        velocity: Float,
        playbackMode: PlaybackMode,
        priority: VoicePriority = VoicePriority.NORMAL
    ): String? {
        return voiceMutex.withLock {
            try {
                // Check if we need to steal a voice
                if (activeVoices.size >= MAX_VOICES) {
                    if (!stealVoice(priority)) {
                        Log.w(TAG, "Cannot allocate voice - all voices busy and none can be stolen")
                        return@withLock null
                    }
                }

                // Generate unique voice ID
                val voiceId = "voice_${nextVoiceId++}_pad_${padIndex}"
                
                // Handle playback mode specific logic
                when (playbackMode) {
                    PlaybackMode.ONE_SHOT -> {
                        // For one-shot, stop any existing voices on this pad
                        stopVoicesForPad(padIndex)
                    }
                    PlaybackMode.LOOP -> {
                        // For loop mode, stop existing loop on this pad
                        stopVoicesForPad(padIndex)
                    }
                    PlaybackMode.GATE -> {
                        // Gate mode allows multiple triggers
                    }
                    PlaybackMode.NOTE_ON_OFF -> {
                        // MIDI-style, allow multiple notes
                    }
                }

                // Create voice info
                val voiceInfo = VoiceInfo(
                    voiceId = voiceId,
                    padIndex = padIndex,
                    sampleId = sampleId,
                    startTime = System.currentTimeMillis(),
                    velocity = velocity,
                    playbackMode = playbackMode,
                    priority = priority
                )

                activeVoices[voiceId] = voiceInfo
                
                Log.d(TAG, "Allocated voice $voiceId for pad $padIndex (${activeVoices.size}/$MAX_VOICES voices active)")
                voiceId
                
            } catch (e: Exception) {
                Log.e(TAG, "Error allocating voice", e)
                null
            }
        }
    }

    /**
     * Release a specific voice.
     */
    suspend fun releaseVoice(voiceId: String) {
        voiceMutex.withLock {
            try {
                val voiceInfo = activeVoices.remove(voiceId)
                if (voiceInfo != null) {
                    Log.d(TAG, "Released voice $voiceId (${activeVoices.size}/$MAX_VOICES voices active)")
                } else {
                    Log.w(TAG, "Attempted to release unknown voice: $voiceId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing voice $voiceId", e)
            }
        }
    }

    /**
     * Stop all voices for a specific pad.
     */
    suspend fun stopVoicesForPad(padIndex: Int) {
        voiceMutex.withLock {
            try {
                val voicesToStop = activeVoices.values.filter { it.padIndex == padIndex }
                
                voicesToStop.forEach { voiceInfo ->
                    activeVoices.remove(voiceInfo.voiceId)
                    // The actual audio stopping is handled by the audio engine
                }
                
                if (voicesToStop.isNotEmpty()) {
                    Log.d(TAG, "Stopped ${voicesToStop.size} voices for pad $padIndex")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voices for pad $padIndex", e)
            }
        }
    }

    /**
     * Stop all active voices.
     */
    suspend fun stopAllVoices() {
        voiceMutex.withLock {
            try {
                val voiceCount = activeVoices.size
                activeVoices.clear()
                
                // Clear voices in audio engine
                audioEngine.clearDrumVoices()
                
                Log.d(TAG, "Stopped all $voiceCount voices")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping all voices", e)
            }
        }
    }

    /**
     * Get current voice usage statistics.
     */
    fun getVoiceStats(): VoiceStats {
        val voices = activeVoices.values.toList()
        return VoiceStats(
            activeVoices = voices.size,
            maxVoices = MAX_VOICES,
            voiceUtilization = voices.size.toFloat() / MAX_VOICES,
            voicesByPriority = voices.groupBy { it.priority }.mapValues { it.value.size },
            oldestVoiceAge = voices.minOfOrNull { System.currentTimeMillis() - it.startTime } ?: 0L
        )
    }

    /**
     * Check if voice stealing is needed and possible.
     */
    private fun shouldStealVoice(): Boolean {
        return activeVoices.size.toFloat() / MAX_VOICES >= VOICE_STEAL_THRESHOLD
    }

    /**
     * Steal a voice to make room for a new one.
     * Returns true if a voice was successfully stolen.
     */
    private suspend fun stealVoice(newVoicePriority: VoicePriority): Boolean {
        try {
            // Find the best candidate for voice stealing
            val candidate = findVoiceToSteal(newVoicePriority)
            
            if (candidate != null) {
                activeVoices.remove(candidate.voiceId)
                
                // Stop the voice in the audio engine
                audioEngine.releaseDrumPad(candidate.padIndex)
                
                Log.d(TAG, "Stole voice ${candidate.voiceId} (priority: ${candidate.priority}) for new voice (priority: $newVoicePriority)")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error stealing voice", e)
            return false
        }
    }

    /**
     * Find the best voice to steal based on priority and age.
     */
    private fun findVoiceToSteal(newVoicePriority: VoicePriority): VoiceInfo? {
        val voices = activeVoices.values.toList()
        
        // Never steal CRITICAL voices
        val stealableVoices = voices.filter { it.priority != VoicePriority.CRITICAL }
        
        if (stealableVoices.isEmpty()) {
            return null
        }

        // Prioritize stealing by:
        // 1. Lower priority voices first
        // 2. Older voices within the same priority
        // 3. One-shot voices that have been playing longer
        
        return stealableVoices
            .sortedWith(compareBy<VoiceInfo> { it.priority.ordinal }
                .thenBy { it.startTime })
            .firstOrNull { canStealVoice(it, newVoicePriority) }
    }

    /**
     * Check if a voice can be stolen for a new voice of given priority.
     */
    private fun canStealVoice(voice: VoiceInfo, newPriority: VoicePriority): Boolean {
        // Can always steal lower priority voices
        if (voice.priority.ordinal < newPriority.ordinal) {
            return true
        }
        
        // For same priority, steal older voices
        if (voice.priority == newPriority) {
            val voiceAge = System.currentTimeMillis() - voice.startTime
            return voiceAge > 1000L // Only steal voices older than 1 second
        }
        
        // Don't steal higher priority voices
        return false
    }

    /**
     * Optimize voice allocation based on current usage patterns.
     */
    suspend fun optimizeVoiceAllocation() {
        voiceMutex.withLock {
            try {
                val currentTime = System.currentTimeMillis()
                val voicesToCleanup = mutableListOf<String>()
                
                // Find voices that might be finished (one-shot samples)
                activeVoices.values.forEach { voice ->
                    val voiceAge = currentTime - voice.startTime
                    
                    // Clean up one-shot voices that have been playing for a while
                    if (voice.playbackMode == PlaybackMode.ONE_SHOT && voiceAge > 10000L) {
                        voicesToCleanup.add(voice.voiceId)
                    }
                }
                
                // Remove cleanup candidates
                voicesToCleanup.forEach { voiceId ->
                    activeVoices.remove(voiceId)
                }
                
                if (voicesToCleanup.isNotEmpty()) {
                    Log.d(TAG, "Cleaned up ${voicesToCleanup.size} stale voices")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing voice allocation", e)
            }
        }
    }

    /**
     * Get the maximum number of voices supported
     */
    fun getMaxVoices(): Int = MAX_VOICES

    data class VoiceStats(
        val activeVoices: Int,
        val maxVoices: Int,
        val voiceUtilization: Float,
        val voicesByPriority: Map<VoicePriority, Int>,
        val oldestVoiceAge: Long
    )
}