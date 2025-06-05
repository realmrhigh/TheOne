package com.example.theone.audio

// Forward declaration for data models that might be needed later, from README
// For now, only core lifecycle methods are defined.
// data class SampleMetadata(...)
// data class EnvelopeSettings(...)
// data class LFOSettings(...)
// enum class PlaybackMode { ONE_SHOT, NOTE_ON_OFF }


interface AudioEngineControl {
    // Initialization & Config
    suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    suspend fun shutdown()
    fun isInitialized(): Boolean
    fun getReportedLatencyMillis(): Float

    // Metronome - Placeholder, actual implementation later
    // suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)
    // suspend fun setMetronomeVolume(volume: Float)

    // Sample Loading & Management
    suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    suspend fun unloadSample(sampleId: String)
    fun isSampleLoaded(sampleId: String): Boolean

    // New simplified playback method
    suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean

    // Method for playing a slice of a sample, with optional looping
    suspend fun playSampleSlice(
        sampleId: String,
        noteInstanceId: String,
        volume: Float,
        pan: Float,
        sampleRate: Int,      // Needed for ms to frame conversion if not done by caller
        trimStartMs: Long,
        trimEndMs: Long,
        loopStartMs: Long?,
        loopEndMs: Long?,
        isLooping: Boolean
    ): Boolean

    // Metronome control methods
    suspend fun setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String? // Nullable if no secondary sound
    ): Unit // JNI function is void

    suspend fun setMetronomeVolume(volume: Float): Unit // JNI function is void

    // Recording methods
    suspend fun startAudioRecording(
        context: Context, // Added context here for Uri handling
        filePathUri: String,
        sampleRate: Int,
        channels: Int,
        inputDeviceId: String? = null // Optional input device ID
    ): Boolean

    suspend fun stopAudioRecording(): SampleMetadata?

    fun isRecordingActive(): Boolean
    fun getRecordingLevelPeak(): Float // Returns peak and resets it

    // Playback Control - Placeholder
    // suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?, velocity: Float, playbackMode: PlaybackMode, coarseTune: Int, fineTune: Int, pan: Float, volume: Float, ampEnv: EnvelopeSettings, filterEnv: EnvelopeSettings?, pitchEnv: EnvelopeSettings?, lfos: List<LFOSettings>): Boolean
    // suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?)
    // suspend fun stopAllNotes(trackId: String?, immediate: Boolean)

    // Recording - Placeholder
    // suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean
    // suspend fun stopAudioRecording(): SampleMetadata? // Returns metadata of the recorded sample
    // fun getRecordingLevelPeak(): Float
    // fun isRecordingActive(): Boolean

    // Transport Control - Placeholder
    // suspend fun setTransportBpm(bpm: Float)
}
