package com.example.theone.audio

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.EnvelopeSettings
import com.example.theone.model.LFOSettings
import com.example.theone.model.PlaybackMode
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class AudioEngine(private val context: Context) : AudioEngineControl {

    private var initialized = false

    // JNI declarations (Oboe) - Renamed to camelCase
    private external fun nativeInitOboe(): Boolean
    private external fun nativeShutdownOboe()
    private external fun nativeIsOboeInitialized(): Boolean
    private external fun nativeGetOboeReportedLatencyMillis(): Float
    private external fun nativeStringFromJNI(): String

    // JNI declarations for sample loading - Renamed to camelCase
    private external fun nativeLoadSampleToMemory(sampleId: String, fd: Int, offset: Long, length: Long): Boolean
    private external fun nativeIsSampleLoaded(sampleId: String): Boolean
    private external fun nativeUnloadSample(sampleId: String)
    private external fun nativeGetSampleRate(sampleId: String): Int

    // JNI declaration for sample playback (Pad related) - Renamed to camelCase
    private external fun nativePlayPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?,
        velocity: Float, coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        playbackModeOrdinal: Int,
        ampEnvAttackMs: Float, ampEnvDecayMs: Float, ampEnvSustainLevel: Float, ampEnvReleaseMs: Float
    ): Boolean

    // JNI declaration for sample playback (Slice related) - Renamed to camelCase
    private external fun nativePlaySampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, sampleRate: Int, trimStartMs: Long, trimEndMs: Long, loopStartMs: Long, loopEndMs: Long, isLooping: Boolean): Boolean

    // JNI declarations for metronome - Renamed to camelCase
    private external fun nativeSetMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    )
    private external fun nativeSetMetronomeVolume(volume: Float)

    // JNI declarations for recording - Renamed to camelCase
    private external fun nativeStartAudioRecording(fd: Int, storagePathForMetadata: String, sampleRate: Int, channels: Int): Boolean
    private external fun nativeStopAudioRecording(): Array<Any>?
    private external fun nativeIsRecordingActive(): Boolean
    private external fun nativeGetRecordingLevelPeak(): Float

    // JNI declaration for updating PadSettings - Renamed to camelCase
    external fun nativeUpdatePadSettings(trackId: String, padId: String, padSettings: PadSettings)

    // --- New Sequencer JNI Declarations --- Renamed to camelCase and removed 'internal'
    private external fun nativeLoadSequenceData(sequence: com.example.theone.model.Sequence)
    private external fun nativePlaySequence()
    private external fun nativeStopSequence()
    private external fun nativeSetSequencerBpm(bpm: Float)
    private external fun nativeGetSequencerPlayheadPosition(): Long
    // --- End New Sequencer JNI Declarations ---

    private var mRecordingParams: Pair<Int, Int>? = null

    private fun checkInitialized(functionName: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot execute '$functionName'.")
            return false
        }
        return true
    }

    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        Log.d("AudioEngine", "AudioEngineControl.initialize called with sr: $sampleRate, bs: $bufferSize, lowLatency: $enableLowLatency")
        if (!initialized) {
            Log.d("AudioEngine", "Initializing Oboe via JNI...")
            initialized = nativeInitOboe()
            Log.d("AudioEngine", "Oboe JNI initialization result: $initialized")
            val testString = nativeStringFromJNI()
            Log.d("AudioEngine", "Test JNI string: $testString")
        } else {
            Log.d("AudioEngine", "Already initialized.")
        }
        return initialized
    }

    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        if (!checkInitialized("loadSampleToMemory")) return false
        Log.d("AudioEngine", "loadSampleToMemory called for ID: $sampleId, URI: $filePathUri")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fd
                    val statSize = pfd.statSize
                    Log.d("AudioEngine", "Opened FD: $fd, StatSize: $statSize for URI: $filePathUri")
                    if (fd == -1) {
                        Log.e("AudioEngine", "Failed to get valid FileDescriptor for URI: $filePathUri (FD is -1)")
                        return@withContext false
                    }
                    nativeLoadSampleToMemory(sampleId, fd, 0L, statSize)
                } ?: run {
                    Log.e("AudioEngine", "Failed to open ParcelFileDescriptor for URI: $filePathUri")
                    false
                }
            } catch (e: IOException) {
                Log.e("AudioEngine", "IOException during sample loading for $sampleId: ${e.message}", e)
                false
            } catch (e: SecurityException) {
                Log.e("AudioEngine", "SecurityException during sample loading for $sampleId: ${e.message}", e)
                false
            } catch (e: Exception) {
                Log.e("AudioEngine", "Unexpected exception during sample loading for $sampleId: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun unloadSample(sampleId: String) {
        if (!checkInitialized("unloadSample")) return
        Log.d("AudioEngine", "unloadSample called for ID: $sampleId")
        withContext(Dispatchers.IO) {
            nativeUnloadSample(sampleId)
        }
    }

    override fun isSampleLoaded(sampleId: String): Boolean {
        if (!checkInitialized("isSampleLoaded")) return false
        return nativeIsSampleLoaded(sampleId)
    }

    @Deprecated("Use playPadSample for more explicit control.", ReplaceWith("playPadSample(...)"), DeprecationLevel.WARNING)
    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean {
        if (!checkInitialized("playSample")) return false
        Log.d("AudioEngine", "playSample called: sampleID='$sampleId', instanceID='$noteInstanceId', vol=$volume, pan=$pan")
        return nativePlayPadSample(
            noteInstanceId = noteInstanceId,
            trackId = "general_track",
            padId = "general_pad",
            sampleId = sampleId,
            sliceId = null,
            velocity = 1.0f,
            coarseTune = 0,
            fineTune = 0,
            pan = pan,
            volume = volume,
            playbackModeOrdinal = PlaybackMode.ONE_SHOT.ordinal,
            ampEnvAttackMs = 5f,
            ampEnvDecayMs = 100f,
            ampEnvSustainLevel = 1.0f,
            ampEnvReleaseMs = 100f
        )
    }

    override suspend fun playPadSample(
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
    ): Boolean {
        if (!checkInitialized("playPadSample")) return false
        Log.d("AudioEngine", "playPadSample called: sampleID='$sampleId', padID='$padId', instanceID='$noteInstanceId', mode=${playbackMode.name}")
        return nativePlayPadSample(
            noteInstanceId = noteInstanceId,
            trackId = trackId,
            padId = padId,
            sampleId = sampleId,
            sliceId = sliceId,
            velocity = velocity,
            coarseTune = coarseTune,
            fineTune = fineTune,
            pan = pan,
            volume = volume,
            playbackModeOrdinal = playbackMode.ordinal,
            ampEnvAttackMs = ampEnv.attackMs,
            ampEnvDecayMs = ampEnv.decayMs,
            ampEnvSustainLevel = ampEnv.sustainLevel,
            ampEnvReleaseMs = ampEnv.releaseMs
        )
    }

    override suspend fun playSampleSlice(
        sampleId: String,
        noteInstanceId: String,
        volume: Float,
        pan: Float,
        trimStartMs: Long,
        trimEndMs: Long,
        loopStartMs: Long?,
        loopEndMs: Long?,
        isLooping: Boolean
    ): Boolean {
        if (!checkInitialized("playSampleSlice")) return false
        val actualSampleRate = nativeGetSampleRate(sampleId)
        if (actualSampleRate <= 0) {
            Log.e("AudioEngine", "playSampleSlice: Failed to get valid sample rate for sample ID '$sampleId'. Received: $actualSampleRate.")
            return false
        }
        Log.d("AudioEngine", "playSampleSlice called: ID='$sampleId', SR=$actualSampleRate, trim[$trimStartMs-$trimEndMs], loop[$loopStartMs-$loopEndMs], looping=$isLooping")
        return nativePlaySampleSlice(
            sampleId,
            noteInstanceId,
            volume,
            pan,
            actualSampleRate,
            trimStartMs,
            trimEndMs,
            loopStartMs ?: 0L,
            loopEndMs ?: 0L,
            isLooping
        )
    }

    override suspend fun setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    ) {
        if (!checkInitialized("setMetronomeState")) return
        Log.d("AudioEngine", "setMetronomeState: enabled=$isEnabled, bpm=$bpm")
        withContext(Dispatchers.IO) {
            nativeSetMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, primarySoundSampleId, secondarySoundSampleId ?: "")
        }
    }

    override suspend fun setMetronomeVolume(volume: Float) {
        if (!checkInitialized("setMetronomeVolume")) return
        withContext(Dispatchers.IO) { nativeSetMetronomeVolume(volume) }
    }

    override suspend fun startAudioRecording(
        context: Context,
        filePathUri: String,
        sampleRate: Int,
        channels: Int,
        inputDeviceId: String?
    ): Boolean {
        if (!checkInitialized("startAudioRecording")) return false
        Log.d("AudioEngine", "startAudioRecording called: URI='$filePathUri', SR=$sampleRate, Ch=$channels")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                this@AudioEngine.context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    val fd = pfd.fd
                    if (fd == -1) {
                        Log.e("AudioEngine", "Failed to get valid FileDescriptor for URI: $filePathUri")
                        return@withContext false
                    }
                    val success = nativeStartAudioRecording(fd, filePathUri, sampleRate, channels)
                    if (success) {
                        mRecordingParams = Pair(sampleRate, channels)
                    }
                    success
                } ?: run {
                    Log.e("AudioEngine", "Failed to open ParcelFileDescriptor for URI: $filePathUri for writing.")
                    false
                }
            } catch (e: Exception) {
                Log.e("AudioEngine", "Exception during startAudioRecording for $filePathUri: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun stopAudioRecording(): SampleMetadata? {
        if (!checkInitialized("stopAudioRecording")) return null
        return withContext(Dispatchers.IO) {
            val recordingInfo = nativeStopAudioRecording()
            if (recordingInfo != null && recordingInfo.size == 2) {
                val filePath = recordingInfo[0] as? String
                val totalFrames = recordingInfo[1] as? Long
                val (recSampleRate, recChannels) = mRecordingParams ?: Pair(0,0)
                mRecordingParams = null

                if (filePath != null && totalFrames != null && recSampleRate > 0 && recChannels > 0) {
                    val durationMs = (totalFrames * 1000) / recSampleRate
                    val id = UUID.randomUUID().toString()
                    val name = filePath.toUri().lastPathSegment ?: id
                    SampleMetadata(
                        id = id, name = name, uri = filePath, duration = durationMs,
                        sampleRate = recSampleRate, channels = recChannels
                    )
                } else { null }
            } else { null }
        }
    }

    override fun isRecordingActive(): Boolean {
        if (!initialized) return false
        return nativeIsRecordingActive()
    }

    override fun getRecordingLevelPeak(): Float {
        if (!initialized) return 0.0f
        return nativeGetRecordingLevelPeak()
    }

    override suspend fun shutdown() {
        if (initialized) {
            nativeShutdownOboe()
            initialized = false
            Log.d("AudioEngine", "Oboe shutdown.")
        }
    }

    override fun isInitialized(): Boolean {
        if (!initialized) return false
        val nativeState = nativeIsOboeInitialized()
        return initialized && nativeState
    }

    override fun getReportedLatencyMillis(): Float {
        return if (initialized) nativeGetOboeReportedLatencyMillis() else -1.0f
    }

    // --- New Sequencer Kotlin Methods ---
    override suspend fun loadSequenceData(sequence: com.example.theone.model.Sequence) {
        if (!checkInitialized("loadSequenceData")) return
        nativeLoadSequenceData(sequence)
    }

    override suspend fun playSequence() {
        if (!checkInitialized("playSequence")) return
        nativePlaySequence()
    }

    override suspend fun stopSequence() {
        if (!checkInitialized("stopSequence")) return
        nativeStopSequence()
    }

    override suspend fun setSequencerBpm(bpm: Float) {
        if (!checkInitialized("setSequencerBpm")) return
        nativeSetSequencerBpm(bpm)
    }

    override suspend fun getSequencerPlayheadPosition(): Long {
        if (!checkInitialized("getSequencerPlayheadPosition")) return 0L
        return nativeGetSequencerPlayheadPosition()
    }
    // --- End New Sequencer Kotlin Methods ---

    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
}