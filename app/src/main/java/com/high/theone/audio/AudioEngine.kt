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
import com.high.theone.model.SynthModels.LFOSettings
import com.high.theone.model.SynthModels.EnvelopeSettings
import com.high.theone.model.PlaybackMode
import com.high.theone.model.SynthModels.EffectInstance
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
}
