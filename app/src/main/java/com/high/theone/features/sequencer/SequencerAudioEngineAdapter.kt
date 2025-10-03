package com.high.theone.features.sequencer

import android.util.Log
import com.high.theone.audio.AudioEngineControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that extends the basic AudioEngineControl with sequencer-specific functionality
 * Provides high-precision timing and scheduling capabilities for step sequencer
 */
@Singleton
class SequencerAudioEngineAdapter @Inject constructor(
    private val audioEngine: AudioEngineControl
) : SequencerAudioEngine {
    
    companion object {
        private const val TAG = "SequencerAudioEngineAdapter"
        private const val DEFAULT_LOOKAHEAD_MICROS = 10000L // 10ms lookahead
    }
    
    private var isSequencerModeEnabled = false
    private var currentTempo = 120f
    
    // Delegate all AudioEngineControl methods to the underlying engine
    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        val result = audioEngine.initialize(sampleRate, bufferSize, enableLowLatency)
        if (result) {
            // Enable high precision mode for sequencer
            audioEngine.setHighPrecisionMode(true)
            Log.d(TAG, "Sequencer audio engine initialized with high precision mode")
        }
        return result
    }
    
    override suspend fun shutdown() = audioEngine.shutdown()
    override suspend fun setAssetManager(assetManager: android.content.res.AssetManager): Boolean = audioEngine.setAssetManager(assetManager)
    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) = audioEngine.setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, soundPrimaryUri, soundSecondaryUri)
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean = audioEngine.loadSampleToMemory(sampleId, filePathUri)
    override suspend fun unloadSample(sampleId: String) = audioEngine.unloadSample(sampleId)
    override suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean = audioEngine.playPadSample(noteInstanceId, trackId, padId)
    override suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?) = audioEngine.stopNote(noteInstanceId, releaseTimeMs)
    override suspend fun stopAllNotes(trackId: String?, immediate: Boolean) = audioEngine.stopAllNotes(trackId, immediate)
    override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean = audioEngine.startAudioRecording(filePathUri, inputDeviceId)
    override suspend fun stopAudioRecording() = audioEngine.stopAudioRecording()
    override suspend fun setTrackVolume(trackId: String, volume: Float) = audioEngine.setTrackVolume(trackId, volume)
    override suspend fun setTrackPan(trackId: String, pan: Float) = audioEngine.setTrackPan(trackId, pan)
    override suspend fun addTrackEffect(trackId: String, effectInstance: com.high.theone.model.EffectInstance): Boolean = audioEngine.addTrackEffect(trackId, effectInstance)
    override suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean = audioEngine.removeTrackEffect(trackId, effectInstanceId)
    override suspend fun setSampleEnvelope(sampleId: String, envelope: com.high.theone.model.EnvelopeSettings) = audioEngine.setSampleEnvelope(sampleId, envelope)
    override suspend fun setSampleLFO(sampleId: String, lfo: com.high.theone.model.LFOSettings) = audioEngine.setSampleLFO(sampleId, lfo)
    override suspend fun setEffectParameter(effectId: String, parameter: String, value: Float) = audioEngine.setEffectParameter(effectId, parameter, value)
    override suspend fun setTransportBpm(bpm: Float) = audioEngine.setTransportBpm(bpm)
    override suspend fun getReportedLatencyMillis(): Float = audioEngine.getReportedLatencyMillis()
    override suspend fun createAndTriggerTestSample(): Boolean = audioEngine.createAndTriggerTestSample()
    override suspend fun loadTestSample(): Boolean = audioEngine.loadTestSample()
    override suspend fun triggerTestPadSample(padIndex: Int): Boolean = audioEngine.triggerTestPadSample(padIndex)
    override suspend fun getOboeReportedLatencyMillis(): Float = audioEngine.getOboeReportedLatencyMillis()
    override suspend fun stopAllSamples() = audioEngine.stopAllSamples()
    override suspend fun triggerSample(sampleKey: String, volume: Float, pan: Float) = audioEngine.triggerSample(sampleKey, volume, pan)
    override suspend fun loadSampleFromAsset(sampleId: String, assetPath: String): Boolean = audioEngine.loadSampleFromAsset(sampleId, assetPath)
    override suspend fun initializeDrumEngine(): Boolean = audioEngine.initializeDrumEngine()
    override suspend fun triggerDrumPad(padIndex: Int, velocity: Float) = audioEngine.triggerDrumPad(padIndex, velocity)
    override suspend fun releaseDrumPad(padIndex: Int) = audioEngine.releaseDrumPad(padIndex)
    override suspend fun loadDrumSample(padIndex: Int, samplePath: String): Boolean = audioEngine.loadDrumSample(padIndex, samplePath)
    override suspend fun setDrumPadVolume(padIndex: Int, volume: Float) = audioEngine.setDrumPadVolume(padIndex, volume)
    override suspend fun setDrumPadPan(padIndex: Int, pan: Float) = audioEngine.setDrumPadPan(padIndex, pan)
    override suspend fun setDrumPadMode(padIndex: Int, playbackMode: Int) = audioEngine.setDrumPadMode(padIndex, playbackMode)
    override suspend fun setDrumMasterVolume(volume: Float) = audioEngine.setDrumMasterVolume(volume)
    override suspend fun getDrumActiveVoices(): Int = audioEngine.getDrumActiveVoices()
    override suspend fun clearDrumVoices() = audioEngine.clearDrumVoices()
    override suspend fun debugPrintDrumEngineState() = audioEngine.debugPrintDrumEngineState()
    override suspend fun getDrumEngineLoadedSamples(): Int = audioEngine.getDrumEngineLoadedSamples()
    override suspend fun loadPlugin(pluginId: String, pluginName: String): Boolean = audioEngine.loadPlugin(pluginId, pluginName)
    override suspend fun unloadPlugin(pluginId: String): Boolean = audioEngine.unloadPlugin(pluginId)
    override suspend fun getLoadedPlugins(): List<String> = audioEngine.getLoadedPlugins()
    override suspend fun setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean = audioEngine.setPluginParameter(pluginId, paramId, value)
    override suspend fun noteOnToPlugin(pluginId: String, note: Int, velocity: Int) = audioEngine.noteOnToPlugin(pluginId, note, velocity)
    override suspend fun noteOffToPlugin(pluginId: String, note: Int, velocity: Int) = audioEngine.noteOffToPlugin(pluginId, note, velocity)
    
    // Sequencer-specific methods from AudioEngineControl
    override suspend fun scheduleStepTrigger(padIndex: Int, velocity: Float, timestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Scheduling step trigger: pad=$padIndex, velocity=$velocity, timestamp=$timestamp")
                audioEngine.scheduleStepTrigger(padIndex, velocity, timestamp)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling step trigger", e)
                false
            }
        }
    }
    
    override suspend fun setSequencerTempo(bpm: Float) {
        withContext(Dispatchers.IO) {
            try {
                currentTempo = bpm
                audioEngine.setSequencerTempo(bpm)
                Log.d(TAG, "Sequencer tempo set to $bpm BPM")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting sequencer tempo", e)
            }
        }
    }
    
    override suspend fun getAudioLatencyMicros(): Long {
        return withContext(Dispatchers.IO) {
            try {
                audioEngine.getAudioLatencyMicros()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting audio latency", e)
                0L
            }
        }
    }
    
    override suspend fun setHighPrecisionMode(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                audioEngine.setHighPrecisionMode(enabled)
                Log.d(TAG, "High precision mode set to $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting high precision mode", e)
            }
        }
    }
    
    override suspend fun preloadSequencerSamples(padIndices: List<Int>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                audioEngine.preloadSequencerSamples(padIndices)
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading sequencer samples", e)
                false
            }
        }
    }
    
    override suspend fun clearScheduledEvents() {
        withContext(Dispatchers.IO) {
            try {
                audioEngine.clearScheduledEvents()
                Log.d(TAG, "Cleared all scheduled events")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing scheduled events", e)
            }
        }
    }
    
    override suspend fun getTimingStatistics(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                audioEngine.getTimingStatistics()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting timing statistics", e)
                emptyMap()
            }
        }
    }
    
    // Extended sequencer-specific methods
    override suspend fun schedulePatternTriggers(triggers: List<StepTrigger>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Scheduling ${triggers.size} pattern triggers")
                
                // Clear existing scheduled events first
                clearScheduledEvents()
                
                // Schedule each trigger
                var successCount = 0
                for (trigger in triggers) {
                    val success = scheduleStepTrigger(
                        trigger.padIndex,
                        trigger.velocity,
                        trigger.timestamp
                    )
                    if (success) successCount++
                }
                
                val allSuccessful = successCount == triggers.size
                Log.d(TAG, "Scheduled $successCount/${triggers.size} triggers successfully")
                allSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling pattern triggers", e)
                false
            }
        }
    }
    
    override suspend fun updateScheduledTriggers(triggers: List<StepTrigger>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating scheduled triggers")
                schedulePatternTriggers(triggers)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scheduled triggers", e)
            }
        }
    }
    
    override suspend fun getCurrentAudioTimestamp(): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Use system time in microseconds as a fallback
                // In a real implementation, this would get the actual audio thread timestamp
                System.nanoTime() / 1000L
            } catch (e: Exception) {
                Log.e(TAG, "Error getting current audio timestamp", e)
                0L
            }
        }
    }
    
    override suspend fun calculateTriggerTime(stepTime: Long, lookahead: Long): Long {
        return withContext(Dispatchers.IO) {
            try {
                val latency = getAudioLatencyMicros()
                val compensatedTime = stepTime - latency + lookahead
                Log.v(TAG, "Calculated trigger time: stepTime=$stepTime, latency=$latency, lookahead=$lookahead, result=$compensatedTime")
                compensatedTime
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating trigger time", e)
                stepTime + lookahead
            }
        }
    }
    
    override suspend fun setSequencerMode(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                isSequencerModeEnabled = enabled
                
                // Enable optimizations for sequencer mode
                if (enabled) {
                    setHighPrecisionMode(true)
                    // Pre-warm the audio engine for low latency
                    Log.d(TAG, "Sequencer mode enabled with optimizations")
                } else {
                    clearScheduledEvents()
                    Log.d(TAG, "Sequencer mode disabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting sequencer mode", e)
            }
        }
    }
    
    override suspend fun getSequencerPerformanceMetrics(): SequencerPerformanceMetrics {
        return withContext(Dispatchers.IO) {
            try {
                val timingStats = getTimingStatistics()
                
                SequencerPerformanceMetrics(
                    averageLatency = (timingStats["averageLatency"] as? Number)?.toLong() ?: 0L,
                    maxLatency = (timingStats["maxLatency"] as? Number)?.toLong() ?: 0L,
                    minLatency = (timingStats["minLatency"] as? Number)?.toLong() ?: 0L,
                    jitter = (timingStats["jitter"] as? Number)?.toLong() ?: 0L,
                    missedTriggers = (timingStats["missedTriggers"] as? Number)?.toInt() ?: 0,
                    scheduledTriggers = (timingStats["scheduledTriggers"] as? Number)?.toInt() ?: 0,
                    cpuUsage = (timingStats["cpuUsage"] as? Number)?.toFloat() ?: 0f,
                    memoryUsage = (timingStats["memoryUsage"] as? Number)?.toLong() ?: 0L,
                    isRealTimeMode = isSequencerModeEnabled,
                    bufferUnderruns = (timingStats["bufferUnderruns"] as? Number)?.toInt() ?: 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting sequencer performance metrics", e)
                SequencerPerformanceMetrics(
                    averageLatency = 0L,
                    maxLatency = 0L,
                    minLatency = 0L,
                    jitter = 0L,
                    missedTriggers = 0,
                    scheduledTriggers = 0,
                    cpuUsage = 0f,
                    memoryUsage = 0L,
                    isRealTimeMode = false,
                    bufferUnderruns = 0
                )
            }
        }
    }
}