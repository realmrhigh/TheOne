package com.high.theone.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import androidx.core.net.toUri
import com.high.theone.model.SampleMetadata
import com.high.theone.model.AudioInputSource
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.LFOSettings
import com.high.theone.model.PlaybackMode
import com.high.theone.model.EffectInstance
import java.io.File

class AudioEngine(private val context: Context) : AudioEngineControl {
    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        return withContext(Dispatchers.Default) {
            native_initialize(sampleRate, bufferSize, enableLowLatency)
        }
    }
    override suspend fun shutdown() {
        withContext(Dispatchers.Default) {
            native_shutdown()
        }
    }
    override suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) {
        withContext(Dispatchers.Default) {
            native_setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, soundPrimaryUri, soundSecondaryUri)
        }
    }
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_loadSampleToMemory(sampleId, filePathUri)
        }
    }
    override suspend fun unloadSample(sampleId: String) {
        withContext(Dispatchers.Default) {
            native_unloadSample(sampleId)
        }
    }
    override suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_playPadSample(noteInstanceId, trackId, padId)
        }
    }
    override suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?) {
        withContext(Dispatchers.Default) {
            native_stopNote(noteInstanceId, releaseTimeMs)
        }
    }
    override suspend fun stopAllNotes(trackId: String?, immediate: Boolean) {
        withContext(Dispatchers.Default) {
            native_stopAllNotes(trackId, immediate)
        }
    }
    override suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean {
        return withContext(Dispatchers.Default) {
            native_startAudioRecording(filePathUri, inputDeviceId)
        }
    }
    override suspend fun stopAudioRecording(): SampleMetadata? {
        return withContext(Dispatchers.Default) {
            native_stopAudioRecording()
        }
    }
    override suspend fun setTrackVolume(trackId: String, volume: Float) {
        withContext(Dispatchers.Default) {
            native_setTrackVolume(trackId, volume)
        }
    }
    override suspend fun setTrackPan(trackId: String, pan: Float) {
        withContext(Dispatchers.Default) {
            native_setTrackPan(trackId, pan)
        }
    }
    override suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean {
        return withContext(Dispatchers.Default) {
            native_addTrackEffect(trackId, effectInstance)
        }
    }
    override suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_removeTrackEffect(trackId, effectInstanceId)
        }
    }
    override suspend fun setTransportBpm(bpm: Float) {
        withContext(Dispatchers.Default) {
            native_setTransportBpm(bpm)
        }
    }
    /**
     * Set the envelope for a loaded sample.
     * @param sampleId The ID of the sample.
     * @param envelope The envelope settings to apply.
     */
    override suspend fun setSampleEnvelope(sampleId: String, envelope: EnvelopeSettings) {
        withContext(Dispatchers.Default) {
            native_setSampleEnvelope(sampleId, envelope)
        }
    }
    /**
     * Set the LFO for a loaded sample.
     * @param sampleId The ID of the sample.
     * @param lfo The LFO settings to apply.
     */
    override suspend fun setSampleLFO(sampleId: String, lfo: LFOSettings) {
        withContext(Dispatchers.Default) {
            native_setSampleLFO(sampleId, lfo)
        }
    }
    /**
     * Set a parameter on an effect instance.
     * @param effectId The effect instance ID.
     * @param parameter The parameter name.
     * @param value The value to set.
     */
    override suspend fun setEffectParameter(effectId: String, parameter: String, value: Float) {
        withContext(Dispatchers.Default) {
            native_setEffectParameter(effectId, parameter, value)
        }
    }
    /**
     * Get the current audio levels (L/R) for a track.
     * @param trackId The track ID.
     * @return A FloatArray of [left, right] levels.
     */
    suspend fun getAudioLevels(trackId: String): FloatArray = withContext(Dispatchers.Default) {
        native_getAudioLevels(trackId)
    }
    /**
     * Get the reported output latency in milliseconds from the audio engine (Oboe).
     */
    override suspend fun getReportedLatencyMillis(): Float {
        return withContext(Dispatchers.Default) {
            native_getReportedLatencyMillis()
        }
    }    // 🔥 EPIC SAMPLE TRIGGERING FUNCTIONS
    override suspend fun triggerSample(sampleKey: String, volume: Float, pan: Float) {
        withContext(Dispatchers.Default) {
            native_triggerSample(sampleKey, volume, pan)
        }
    }

    override suspend fun stopAllSamples() {
        withContext(Dispatchers.Default) {
            native_stopAllSamples()
        }
    }

    suspend fun loadTestSample(sampleKey: String) {
        withContext(Dispatchers.Default) {
            native_loadTestSample(sampleKey)
        }
    }

    suspend fun setMasterVolume(volume: Float) {
        withContext(Dispatchers.Default) {
            native_setMasterVolume(volume)
        }
    }

    suspend fun getMasterVolume(): Float {
        return withContext(Dispatchers.Default) {
            native_getMasterVolume()
        }
    }

    suspend fun setTestToneEnabled(enabled: Boolean) {
        withContext(Dispatchers.Default) {
            native_setTestToneEnabled(enabled)
        }
    }

    suspend fun isTestToneEnabled(): Boolean {
        return withContext(Dispatchers.Default) {
            native_isTestToneEnabled()
        }
    }    // Additional methods for testing and debugging
    override suspend fun createAndTriggerTestSample(): Boolean {
        return withContext(Dispatchers.Default) {
            native_createAndTriggerTestSample("test_sample", 1.0f, 0.0f)
        }
    }

    override suspend fun loadTestSample(): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                native_loadTestSample("test_sample")
                true
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to load test sample", e)
                false
            }
        }
    }

    override suspend fun triggerTestPadSample(padIndex: Int): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                // Trigger using the existing triggerSample method with a pad-specific key
                native_triggerSample("pad_$padIndex", 1.0f, 0.0f)
                true
            } catch (e: Exception) {
                Log.e("AudioEngine", "Failed to trigger pad sample $padIndex", e)
                false
            }
        }
    }    override suspend fun getOboeReportedLatencyMillis(): Float {
        return getReportedLatencyMillis()
    }

    // 🎛️ ===== AVST PLUGIN SYSTEM ===== 🎛️
    
    suspend fun loadPlugin(pluginId: String, pluginName: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_loadPlugin(pluginId, pluginName)
        }
    }
    
    suspend fun unloadPlugin(pluginId: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_unloadPlugin(pluginId)
        }
    }
    
    suspend fun getLoadedPlugins(): List<String> {
        return withContext(Dispatchers.Default) {
            native_getLoadedPlugins().toList()
        }
    }
    
    suspend fun setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean {
        return withContext(Dispatchers.Default) {
            native_setPluginParameter(pluginId, paramId, value)
        }
    }
    
    suspend fun getPluginParameter(pluginId: String, paramId: String): Double {
        return withContext(Dispatchers.Default) {
            native_getPluginParameter(pluginId, paramId)
        }
    }
    
    suspend fun noteOnToPlugin(pluginId: String, note: Int, velocity: Int) {
        withContext(Dispatchers.Default) {
            native_noteOnToPlugin(pluginId, note, velocity)
        }
    }
    
    suspend fun noteOffToPlugin(pluginId: String, note: Int, velocity: Int) {
        withContext(Dispatchers.Default) {
            native_noteOffToPlugin(pluginId, note, velocity)
        }
    }
    
    suspend fun savePluginPreset(pluginId: String, presetName: String, filePath: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_savePluginPreset(pluginId, presetName, filePath)
        }
    }
    
    suspend fun loadPluginPreset(pluginId: String, filePath: String): Boolean {
        return withContext(Dispatchers.Default) {
            native_loadPluginPreset(pluginId, filePath)
        }
    }

    companion object {
        init {
            System.loadLibrary("theone")
        }
    }

    // Native method declarations
    private external fun native_initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    private external fun native_shutdown()
    private external fun native_setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)
    private external fun native_loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    private external fun native_unloadSample(sampleId: String)
    private external fun native_playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean
    private external fun native_stopNote(noteInstanceId: String, releaseTimeMs: Float?)
    private external fun native_stopAllNotes(trackId: String?, immediate: Boolean)
    private external fun native_startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean
    private external fun native_stopAudioRecording(): SampleMetadata?
    private external fun native_setTrackVolume(trackId: String, volume: Float)
    private external fun native_setTrackPan(trackId: String, pan: Float)
    private external fun native_addTrackEffect(trackId: String, effectInstance: Any): Boolean
    private external fun native_removeTrackEffect(trackId: String, effectInstanceId: String): Boolean
    private external fun native_setTransportBpm(bpm: Float)
    private external fun native_setSampleEnvelope(sampleId: String, envelope: EnvelopeSettings)
    private external fun native_setSampleLFO(sampleId: String, lfo: LFOSettings)
    private external fun native_setEffectParameter(effectId: String, parameter: String, value: Float)
    private external fun native_addInsertEffect(trackId: String, effectType: String, parameters: Map<String, Float>): Boolean
    private external fun native_removeInsertEffect(trackId: String, effectId: String): Boolean
    private external fun native_getAudioLevels(trackId: String): FloatArray
    private external fun native_getReportedLatencyMillis(): Float

    // 🔥 EPIC NATIVE DECLARATIONS
    private external fun native_triggerSample(sampleKey: String, volume: Float, pan: Float)
    private external fun native_stopAllSamples()
    private external fun native_loadTestSample(sampleKey: String)
    private external fun native_setMasterVolume(volume: Float)
    private external fun native_getMasterVolume(): Float    private external fun native_setTestToneEnabled(enabled: Boolean)
    private external fun native_isTestToneEnabled(): Boolean
    private external fun native_createAndTriggerTestSample(sampleKey: String, volume: Float, pan: Float): Boolean
    
    // 🎛️ AVST Plugin System Native Declarations
    private external fun native_loadPlugin(pluginId: String, pluginName: String): Boolean
    private external fun native_unloadPlugin(pluginId: String): Boolean
    private external fun native_getLoadedPlugins(): Array<String>
    private external fun native_setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean
    private external fun native_getPluginParameter(pluginId: String, paramId: String): Double
    private external fun native_noteOnToPlugin(pluginId: String, note: Int, velocity: Int)
    private external fun native_noteOffToPlugin(pluginId: String, note: Int, velocity: Int)
    private external fun native_savePluginPreset(pluginId: String, presetName: String, filePath: String): Boolean
    private external fun native_loadPluginPreset(pluginId: String, filePath: String): Boolean
}
