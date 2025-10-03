package com.high.theone.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.high.theone.model.SampleMetadata
import com.high.theone.model.EffectInstance
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.LFOSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngineImpl @Inject constructor(
    private val context: Context
) : AudioEngineControl {

    companion object {
        init {
            System.loadLibrary("theone")
        }
        private const val TAG = "AudioEngineImpl"
    }

    // Native method declarations
    private external fun native_initialize(): Boolean
    private external fun native_shutdown()
    private external fun native_setAssetManager(assetManager: AssetManager): Boolean
    private external fun native_createAndTriggerTestSample(): Boolean
    private external fun native_loadTestSample(): Boolean
    private external fun native_triggerTestPadSample(padIndex: Int): Boolean
    private external fun native_getOboeReportedLatencyMillis(): Float
    private external fun native_loadSampleToMemory(sampleId: String, filePath: String): Boolean
    private external fun native_unloadSample(sampleId: String)
    private external fun native_triggerSample(sampleKey: String, volume: Float, pan: Float)
    private external fun native_stopAllSamples()
    private external fun native_loadSampleFromAsset(sampleId: String, assetPath: String): Boolean
    private external fun native_initializeDrumEngine(): Boolean
    private external fun native_triggerDrumPad(padIndex: Int, velocity: Float)
    private external fun native_releaseDrumPad(padIndex: Int)
    private external fun native_loadDrumSample(padIndex: Int, samplePath: String): Boolean
    private external fun native_setDrumPadVolume(padIndex: Int, volume: Float)
    private external fun native_setDrumPadPan(padIndex: Int, pan: Float)
    private external fun native_setDrumPadMode(padIndex: Int, playbackMode: Int)
    private external fun native_setDrumMasterVolume(volume: Float)
    private external fun native_getDrumActiveVoices(): Int
    private external fun native_clearDrumVoices()

    // Plugin system native methods
    private external fun native_loadPlugin(pluginId: String, pluginName: String): Boolean
    private external fun native_unloadPlugin(pluginId: String): Boolean
    private external fun native_getLoadedPlugins(): Array<String>
    private external fun native_setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean
    private external fun native_noteOnToPlugin(pluginId: String, note: Int, velocity: Int)
    private external fun native_noteOffToPlugin(pluginId: String, note: Int, velocity: Int)
    private external fun native_debugPrintDrumEngineState()
    private external fun native_getDrumEngineLoadedSamples(): Int
    
    // Recording native methods
    private external fun native_startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean
    private external fun native_stopAudioRecording(): Array<Any>? // Returns [filePath, duration, sampleRate, channels]
    
    // Additional native methods for complete integration
    private external fun native_setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)
    private external fun native_playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean
    private external fun native_stopNote(noteInstanceId: String, releaseTimeMs: Float)
    private external fun native_stopAllNotes(trackId: String?, immediate: Boolean)
    private external fun native_setTrackVolume(trackId: String, volume: Float)
    private external fun native_setTrackPan(trackId: String, pan: Float)
    private external fun native_addTrackEffect(trackId: String, effectInstanceId: String, effectType: String): Boolean
    private external fun native_removeTrackEffect(trackId: String, effectInstanceId: String): Boolean
    
    // Sequencer integration native methods
    private external fun native_scheduleStepTrigger(padIndex: Int, velocity: Float, timestamp: Long): Boolean
    private external fun native_setSequencerTempo(bpm: Float)
    private external fun native_getAudioLatencyMicros(): Long
    private external fun native_setHighPrecisionMode(enabled: Boolean)
    private external fun native_preloadSequencerSamples(padIndices: IntArray): Boolean
    private external fun native_clearScheduledEvents()
    private external fun native_getTimingStatistics(): Map<String, Any>

    private var isInitialized = false

    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing audio engine...")
                val result = native_initialize()
                if (result) {
                    isInitialized = true
                    // Set asset manager for loading samples from assets
                    native_setAssetManager(context.assets)
                    Log.i(TAG, "Audio engine initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize audio engine")
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception during audio engine initialization", e)
                false
            }
        }
    }

    override suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Shutting down audio engine...")
                native_shutdown()
                isInitialized = false
                Log.i(TAG, "Audio engine shutdown complete")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during audio engine shutdown", e)
            }
        }
    }

    override suspend fun setAssetManager(assetManager: AssetManager): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                native_setAssetManager(assetManager)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting asset manager", e)
                false
            }
        }
    }

    override suspend fun createAndTriggerTestSample(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating and triggering test sample...")
                val result = native_createAndTriggerTestSample()
                Log.d(TAG, "Test sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating test sample", e)
                false
            }
        }
    }

    override suspend fun loadTestSample(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading test sample...")
                val result = native_loadTestSample()
                Log.d(TAG, "Load test sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading test sample", e)
                false
            }
        }
    }

    override suspend fun triggerTestPadSample(padIndex: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Triggering test pad sample $padIndex...")
                val result = native_triggerTestPadSample(padIndex)
                Log.d(TAG, "Trigger test pad result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception triggering test pad sample", e)
                false
            }
        }
    }

    override suspend fun getOboeReportedLatencyMillis(): Float {
        return withContext(Dispatchers.IO) {
            try {
                native_getOboeReportedLatencyMillis()
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting latency", e)
                0f
            }
        }
    }

    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading sample to memory: $sampleId from $filePathUri")
                val result = native_loadSampleToMemory(sampleId, filePathUri)
                Log.d(TAG, "Load sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading sample to memory", e)
                false
            }
        }
    }

    override suspend fun unloadSample(sampleId: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Unloading sample: $sampleId")
                native_unloadSample(sampleId)
            } catch (e: Exception) {
                Log.e(TAG, "Exception unloading sample", e)
            }
        }
    }

    override suspend fun triggerSample(sampleKey: String, volume: Float, pan: Float) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Triggering sample: $sampleKey (vol: $volume, pan: $pan)")
                native_triggerSample(sampleKey, volume, pan)
            } catch (e: Exception) {
                Log.e(TAG, "Exception triggering sample", e)
            }
        }
    }

    override suspend fun stopAllSamples() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping all samples")
                native_stopAllSamples()
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping all samples", e)
            }
        }
    }

    override suspend fun loadSampleFromAsset(sampleId: String, assetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading sample from asset: $sampleId from $assetPath")
                val result = native_loadSampleFromAsset(sampleId, assetPath)
                Log.d(TAG, "Load asset sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading sample from asset", e)
                false
            }
        }
    }

    override suspend fun initializeDrumEngine(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing drum engine...")
                val result = native_initializeDrumEngine()
                Log.d(TAG, "Drum engine init result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception initializing drum engine", e)
                false
            }
        }
    }

    override suspend fun triggerDrumPad(padIndex: Int, velocity: Float) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Triggering drum pad $padIndex with velocity $velocity")
                native_triggerDrumPad(padIndex, velocity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception triggering drum pad", e)
            }
        }
    }

    override suspend fun releaseDrumPad(padIndex: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Releasing drum pad $padIndex")
                native_releaseDrumPad(padIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Exception releasing drum pad", e)
            }
        }
    }

    override suspend fun loadDrumSample(padIndex: Int, samplePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading drum sample for pad $padIndex: $samplePath")
                val result = native_loadDrumSample(padIndex, samplePath)
                Log.d(TAG, "Load drum sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading drum sample", e)
                false
            }
        }
    }

    override suspend fun setDrumPadVolume(padIndex: Int, volume: Float) {
        withContext(Dispatchers.IO) {
            try {
                native_setDrumPadVolume(padIndex, volume)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting drum pad volume", e)
            }
        }
    }

    override suspend fun setDrumPadPan(padIndex: Int, pan: Float) {
        withContext(Dispatchers.IO) {
            try {
                native_setDrumPadPan(padIndex, pan)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting drum pad pan", e)
            }
        }
    }

    override suspend fun setDrumPadMode(padIndex: Int, playbackMode: Int) {
        withContext(Dispatchers.IO) {
            try {
                native_setDrumPadMode(padIndex, playbackMode)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting drum pad mode", e)
            }
        }
    }

    override suspend fun setDrumMasterVolume(volume: Float) {
        withContext(Dispatchers.IO) {
            try {
                native_setDrumMasterVolume(volume)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting drum master volume", e)
            }
        }
    }

    override suspend fun getDrumActiveVoices(): Int {
        return withContext(Dispatchers.IO) {
            try {
                native_getDrumActiveVoices()
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting drum active voices", e)
                0
            }
        }
    }

    override suspend fun clearDrumVoices() {
        withContext(Dispatchers.IO) {
            try {
                native_clearDrumVoices()
            } catch (e: Exception) {
                Log.e(TAG, "Exception clearing drum voices", e)
            }
        }
    }

    override suspend fun debugPrintDrumEngineState() {
        withContext(Dispatchers.IO) {
            try {
                native_debugPrintDrumEngineState()
            } catch (e: Exception) {
                Log.e(TAG, "Exception printing drum engine state", e)
            }
        }
    }

    override suspend fun getDrumEngineLoadedSamples(): Int {
        return withContext(Dispatchers.IO) {
            try {
                native_getDrumEngineLoadedSamples()
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting drum engine loaded samples", e)
                0
            }
        }
    }

    // Stub implementations for methods not yet implemented in native code
    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) {
        Log.d(TAG, "setMetronomeState not yet implemented")
    }

    override suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean {
        Log.d(TAG, "playPadSample not yet implemented")
        return false
    }

    override suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?) {
        Log.d(TAG, "stopNote not yet implemented")
    }

    override suspend fun stopAllNotes(trackId: String?, immediate: Boolean) {
        Log.d(TAG, "stopAllNotes not yet implemented")
    }

    override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean {
        Log.d(TAG, "startAudioRecording not yet implemented")
        return false
    }

    override suspend fun stopAudioRecording(): SampleMetadata? {
        Log.d(TAG, "stopAudioRecording not yet implemented")
        return null
    }

    override suspend fun setTrackVolume(trackId: String, volume: Float) {
        Log.d(TAG, "setTrackVolume not yet implemented")
    }

    override suspend fun setTrackPan(trackId: String, pan: Float) {
        Log.d(TAG, "setTrackPan not yet implemented")
    }

    override suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean {
        Log.d(TAG, "addTrackEffect not yet implemented")
        return false
    }

    override suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean {
        Log.d(TAG, "removeTrackEffect not yet implemented")
        return false
    }

    override suspend fun setSampleEnvelope(sampleId: String, envelope: EnvelopeSettings) {
        Log.d(TAG, "setSampleEnvelope not yet implemented")
    }

    override suspend fun setSampleLFO(sampleId: String, lfo: LFOSettings) {
        Log.d(TAG, "setSampleLFO not yet implemented")
    }

    override suspend fun setEffectParameter(effectId: String, parameter: String, value: Float) {
        Log.d(TAG, "setEffectParameter not yet implemented")
    }

    override suspend fun setTransportBpm(bpm: Float) {
        Log.d(TAG, "setTransportBpm not yet implemented")
    }

    override suspend fun getReportedLatencyMillis(): Float {
        return getOboeReportedLatencyMillis()
    }

    // Metronome implementation
    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting metronome state: enabled=$isEnabled, bpm=$bpm")
                native_setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, soundPrimaryUri, soundSecondaryUri)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting metronome state", e)
            }
        }
    }

    // Playback Control implementation
    override suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Playing pad sample: noteId=$noteInstanceId, trackId=$trackId, padId=$padId")
                val result = native_playPadSample(noteInstanceId, trackId, padId)
                Log.d(TAG, "Play pad sample result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception playing pad sample", e)
                false
            }
        }
    }

    override suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping note: $noteInstanceId, releaseTime=${releaseTimeMs ?: 0f}")
                native_stopNote(noteInstanceId, releaseTimeMs ?: 0f)
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping note", e)
            }
        }
    }

    override suspend fun stopAllNotes(trackId: String?, immediate: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping all notes: trackId=$trackId, immediate=$immediate")
                native_stopAllNotes(trackId, immediate)
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping all notes", e)
            }
        }
    }

    // Real-time Controls implementation
    override suspend fun setTrackVolume(trackId: String, volume: Float) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting track volume: $trackId = $volume")
                native_setTrackVolume(trackId, volume)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting track volume", e)
            }
        }
    }

    override suspend fun setTrackPan(trackId: String, pan: Float) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting track pan: $trackId = $pan")
                native_setTrackPan(trackId, pan)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting track pan", e)
            }
        }
    }

    // Effects implementation
    override suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Adding track effect: $trackId, effect=${effectInstance.id}")
                val result = native_addTrackEffect(trackId, effectInstance.id, effectInstance.type)
                Log.d(TAG, "Add track effect result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception adding track effect", e)
                false
            }
        }
    }

    override suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Removing track effect: $trackId, effectId=$effectInstanceId")
                val result = native_removeTrackEffect(trackId, effectInstanceId)
                Log.d(TAG, "Remove track effect result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception removing track effect", e)
                false
            }
        }
    }

    // Recording implementation
    override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting audio recording to: $filePathUri")
                val result = native_startAudioRecording(filePathUri, inputDeviceId)
                Log.d(TAG, "Start recording result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting audio recording", e)
                false
            }
        }
    }

    override suspend fun stopAudioRecording(): SampleMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping audio recording...")
                val result = native_stopAudioRecording()
                
                if (result != null && result.size >= 4) {
                    val filePath = result[0] as? String ?: return@withContext null
                    val durationMs = (result[1] as? Number)?.toLong() ?: 0L
                    val sampleRate = (result[2] as? Number)?.toInt() ?: 44100
                    val channels = (result[3] as? Number)?.toInt() ?: 1
                    
                    val metadata = SampleMetadata(
                        id = java.util.UUID.randomUUID(),
                        name = "Recording_${System.currentTimeMillis()}",
                        filePath = filePath,
                        durationMs = durationMs,
                        sampleRate = sampleRate,
                        channels = channels,
                        format = "wav",
                        fileSizeBytes = 0L, // Will be calculated by repository
                        createdAt = System.currentTimeMillis(),
                        tags = listOf("recording")
                    )
                    
                    Log.d(TAG, "Recording stopped successfully: ${metadata.name}")
                    metadata
                } else {
                    Log.w(TAG, "Invalid recording result from native layer")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping audio recording", e)
                null
            }
        }
    }

    override suspend fun loadPlugin(pluginId: String, pluginName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading plugin: $pluginId ($pluginName)")
                val result = native_loadPlugin(pluginId, pluginName)
                Log.d(TAG, "Load plugin result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading plugin", e)
                false
            }
        }
    }

    override suspend fun unloadPlugin(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Unloading plugin: $pluginId")
                val result = native_unloadPlugin(pluginId)
                Log.d(TAG, "Unload plugin result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception unloading plugin", e)
                false
            }
        }
    }

    override suspend fun getLoadedPlugins(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = native_getLoadedPlugins().toList()
                Log.d(TAG, "Loaded plugins: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting loaded plugins", e)
                emptyList()
            }
        }
    }

    override suspend fun setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting plugin parameter: $pluginId.$paramId = $value")
                val result = native_setPluginParameter(pluginId, paramId, value)
                Log.d(TAG, "Set parameter result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting plugin parameter", e)
                false
            }
        }
    }

    override suspend fun noteOnToPlugin(pluginId: String, note: Int, velocity: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Note on to plugin: $pluginId, note=$note, velocity=$velocity")
                native_noteOnToPlugin(pluginId, note, velocity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending note on to plugin", e)
            }
        }
    }

    override suspend fun noteOffToPlugin(pluginId: String, note: Int, velocity: Int) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Note off to plugin: $pluginId, note=$note, velocity=$velocity")
                native_noteOffToPlugin(pluginId, note, velocity)
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending note off to plugin", e)
            }
        }
    }
    
    // Sequencer Integration Implementation
    
    override suspend fun scheduleStepTrigger(padIndex: Int, velocity: Float, timestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Scheduling step trigger: pad=$padIndex, velocity=$velocity, timestamp=$timestamp")
                val result = native_scheduleStepTrigger(padIndex, velocity, timestamp)
                Log.d(TAG, "Schedule step trigger result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception scheduling step trigger", e)
                false
            }
        }
    }
    
    override suspend fun setSequencerTempo(bpm: Float) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting sequencer tempo: $bpm BPM")
                native_setSequencerTempo(bpm)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting sequencer tempo", e)
            }
        }
    }
    
    override suspend fun getAudioLatencyMicros(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val latency = native_getAudioLatencyMicros()
                Log.d(TAG, "Audio latency: $latency microseconds")
                latency
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting audio latency", e)
                0L
            }
        }
    }
    
    override suspend fun setHighPrecisionMode(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting high precision mode: $enabled")
                native_setHighPrecisionMode(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Exception setting high precision mode", e)
            }
        }
    }
    
    override suspend fun preloadSequencerSamples(padIndices: List<Int>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Preloading sequencer samples for pads: $padIndices")
                val result = native_preloadSequencerSamples(padIndices.toIntArray())
                Log.d(TAG, "Preload samples result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception preloading sequencer samples", e)
                false
            }
        }
    }
    
    override suspend fun clearScheduledEvents() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Clearing scheduled events")
                native_clearScheduledEvents()
            } catch (e: Exception) {
                Log.e(TAG, "Exception clearing scheduled events", e)
            }
        }
    }
    
    override suspend fun getTimingStatistics(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val stats = native_getTimingStatistics()
                Log.d(TAG, "Timing statistics: $stats")
                stats
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting timing statistics", e)
                emptyMap()
            }
        }
    }
}
