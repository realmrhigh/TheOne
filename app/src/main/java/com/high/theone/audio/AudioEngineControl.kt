package com.high.theone.audio

import com.high.theone.model.SampleMetadata
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.LFOSettings
import com.high.theone.model.EffectInstance

interface AudioEngineControl {
    // Initialization & Config
    suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    suspend fun shutdown()
    suspend fun setAssetManager(assetManager: android.content.res.AssetManager): Boolean

    // Metronome
    suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)

    // Sample Loading
    suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    suspend fun unloadSample(sampleId: String)

    // Playback Control
    suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String): Boolean
    suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?)
    suspend fun stopAllNotes(trackId: String?, immediate: Boolean)

    // Recording
    suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean
    suspend fun stopAudioRecording(): SampleMetadata?

    // Real-time Controls
    suspend fun setTrackVolume(trackId: String, volume: Float)
    suspend fun setTrackPan(trackId: String, pan: Float)

    // Effects
    suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean
    suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean

    // Effects and Modulation
    suspend fun setSampleEnvelope(sampleId: String, envelope: EnvelopeSettings)
    suspend fun setSampleLFO(sampleId: String, lfo: LFOSettings)
    suspend fun setEffectParameter(effectId: String, parameter: String, value: Float)
    
    // Transport
    suspend fun setTransportBpm(bpm: Float)

    // Latency reporting
    suspend fun getReportedLatencyMillis(): Float

    // Additional methods for testing and debugging
    suspend fun createAndTriggerTestSample(): Boolean
    suspend fun loadTestSample(): Boolean
    suspend fun triggerTestPadSample(padIndex: Int): Boolean
    suspend fun getOboeReportedLatencyMillis(): Float
    
    // Real audio file operations
    suspend fun stopAllSamples()
    
    // Simple sample playback for testing
    suspend fun triggerSample(sampleKey: String, volume: Float = 1.0f, pan: Float = 0.0f)
    
    // Asset and file loading methods
    suspend fun loadSampleFromAsset(sampleId: String, assetPath: String): Boolean
    
    // Drum Engine Methods
    suspend fun initializeDrumEngine(): Boolean
    suspend fun triggerDrumPad(padIndex: Int, velocity: Float)
    suspend fun releaseDrumPad(padIndex: Int)
    suspend fun loadDrumSample(padIndex: Int, samplePath: String): Boolean
    suspend fun setDrumPadVolume(padIndex: Int, volume: Float)
    suspend fun setDrumPadPan(padIndex: Int, pan: Float)
    suspend fun setDrumPadMode(padIndex: Int, playbackMode: Int)
    suspend fun setDrumMasterVolume(volume: Float)
    suspend fun getDrumActiveVoices(): Int
    suspend fun clearDrumVoices()
    
    // Debug functions for drum engine state
    suspend fun debugPrintDrumEngineState()
    suspend fun getDrumEngineLoadedSamples(): Int
    
    // Plugin system methods for DebugScreen
    suspend fun loadPlugin(pluginId: String, pluginName: String): Boolean
    suspend fun unloadPlugin(pluginId: String): Boolean
    suspend fun getLoadedPlugins(): List<String>
    suspend fun setPluginParameter(pluginId: String, paramId: String, value: Double): Boolean
    suspend fun noteOnToPlugin(pluginId: String, note: Int, velocity: Int)
    suspend fun noteOffToPlugin(pluginId: String, note: Int, velocity: Int)
}
