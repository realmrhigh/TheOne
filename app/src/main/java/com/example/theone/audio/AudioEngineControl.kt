package com.example.theone.audio

import android.content.Context
import com.example.theone.model.SampleMetadata
import com.example.theone.model.EnvelopeSettings // Corrected import
import com.example.theone.model.LFOSettings      // Corrected import
import com.example.theone.model.PlaybackMode
import com.example.theone.model.Sequence

interface AudioEngineControl {
    suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    suspend fun unloadSample(sampleId: String)
    fun isSampleLoaded(sampleId: String): Boolean
    suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean

    suspend fun playPadSample(
        noteInstanceId: String,
        trackId: String,
        padId: String,
        sampleId: String,
        sliceId: String?,
        velocity: Float,
        playbackMode: PlaybackMode,
        coarseTune: Int,
        fineTune: Int,
        pan: Float,
        volume: Float,
        ampEnv: EnvelopeSettings,
        filterEnv: EnvelopeSettings?,
        pitchEnv: EnvelopeSettings?,
        lfos: List<LFOSettings>
    ): Boolean

    suspend fun playSampleSlice(
        sampleId: String,
        noteInstanceId: String,
        volume: Float,
        pan: Float,
        trimStartMs: Long,
        trimEndMs: Long,
        loopStartMs: Long?,
        loopEndMs: Long?,
        isLooping: Boolean
    ): Boolean

    suspend fun setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    )
    suspend fun setMetronomeVolume(volume: Float)

    suspend fun startAudioRecording(context: Context, filePathUri: String, sampleRate: Int, channels: Int, inputDeviceId: String? = null): Boolean
    suspend fun stopAudioRecording(): SampleMetadata?
    fun isRecordingActive(): Boolean
    fun getRecordingLevelPeak(): Float

    suspend fun shutdown()
    fun isInitialized(): Boolean
    fun getReportedLatencyMillis(): Float

    // --- New Sequencer Control Methods ---
    suspend fun loadSequenceData(sequence: Sequence)
    suspend fun playSequence()
    suspend fun stopSequence()
    suspend fun setSequencerBpm(bpm: Float)
    suspend fun getSequencerPlayheadPosition(): Long
}