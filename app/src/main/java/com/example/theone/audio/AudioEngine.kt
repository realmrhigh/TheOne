package com.example.theone.audio

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.example.theone.features.drumtrack.model.PadSettings
import com.example.theone.model.EnvelopeSettings // Corrected import
import com.example.theone.model.LFOSettings // Corrected import
import com.example.theone.model.PlaybackMode
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class AudioEngine(private val context: Context) : AudioEngineControl {

    private var initialized = false

    // JNI declarations (Oboe)
    private external fun native_initOboe(): Boolean
    private external fun native_shutdownOboe()
    private external fun native_isOboeInitialized(): Boolean
    private external fun native_getOboeReportedLatencyMillis(): Float
    external fun native_stringFromJNI(): String

    // JNI declarations for sample loading
    private external fun native_loadSampleToMemory(sampleId: String, fd: Int, offset: Long, length: Long): Boolean
    private external fun native_isSampleLoaded(sampleId: String): Boolean
    private external fun native_unloadSample(sampleId: String)
    private external fun native_getSampleRate(sampleId: String): Int

    // JNI declaration for sample playback (Pad related)
    private external fun native_playPadSample(
        noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?,
        velocity: Float, coarseTune: Int, fineTune: Int, pan: Float, volume: Float,
        playbackModeOrdinal: Int,
        ampEnvAttackMs: Float, ampEnvDecayMs: Float, ampEnvSustainLevel: Float, ampEnvReleaseMs: Float
    ): Boolean

    // JNI declaration for sample playback (Slice related)
    private external fun native_playSampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, sampleRate: Int, trimStartMs: Long, trimEndMs: Long, loopStartMs: Long, loopEndMs: Long, isLooping: Boolean): Boolean

    // JNI declarations for metronome
    private external fun native_setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    )
    private external fun native_setMetronomeVolume(volume: Float)

    // JNI declarations for recording
    private external fun native_startAudioRecording(fd: Int, storagePathForMetadata: String, sampleRate: Int, channels: Int): Boolean
    private external fun native_stopAudioRecording(): Array<Any>?
    private external fun native_isRecordingActive(): Boolean
    private external fun native_getRecordingLevelPeak(): Float

    // JNI declaration for updating PadSettings
    external fun native_updatePadSettings(trackId: String, padId: String, padSettings: PadSettings)

    // --- New Sequencer JNI Declarations ---
    private external fun native_loadSequenceData(sequence: com.example.theone.model.Sequence)
    private external fun native_playSequence()
    private external fun native_stopSequence()
    private external fun native_setSequencerBpm(bpm: Float)
    private external fun native_getSequencerPlayheadPosition(): Long
    // --- End New Sequencer JNI Declarations ---

    private var mRecordingParams: Pair<Int, Int>? = null

    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        Log.d("AudioEngine", "AudioEngineControl.initialize called with sr: $sampleRate, bs: $bufferSize, lowLatency: $enableLowLatency")
        if (!initialized) {
            Log.d("AudioEngine", "Initializing Oboe via JNI...")
            initialized = native_initOboe()
            Log.d("AudioEngine", "Oboe JNI initialization result: $initialized")
            val testString = native_stringFromJNI()
            Log.d("AudioEngine", "Test JNI string: $testString")
        } else {
            Log.d("AudioEngine", "Already initialized.")
        }
        return initialized
    }

    // Using context provided at construction time for ContentResolver
    suspend fun loadSampleToMemory(contextForContentResolver: Context, sampleId: String, filePathUri: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot load sample.")
            return false
        }
        Log.d("AudioEngine", "loadSampleToMemory (with context) called for ID: $sampleId, URI: $filePathUri")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                // Use the context passed to this specific call if needed, or the class member 'this.context'
                val contentResolver = contextForContentResolver.contentResolver
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fd
                    val statSize = pfd.statSize
                    Log.d("AudioEngine", "Opened FD: $fd, StatSize: $statSize for URI: $filePathUri")
                    if (fd == -1) {
                        Log.e("AudioEngine", "Failed to get valid FileDescriptor for URI: $filePathUri (FD is -1)")
                        return@withContext false
                    }
                    native_loadSampleToMemory(sampleId, fd, 0L, statSize)
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

    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        Log.d("AudioEngine", "loadSampleToMemory (interface version) called for ID: $sampleId, URI: $filePathUri")
        return loadSampleToMemory(this.context, sampleId, filePathUri) // Uses the class member context
    }


    override suspend fun unloadSample(sampleId: String) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot unload sample.")
            return
        }
        Log.d("AudioEngine", "unloadSample called for ID: $sampleId")
        withContext(Dispatchers.IO) {
            native_unloadSample(sampleId)
        }
    }

    override fun isSampleLoaded(sampleId: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot check if sample loaded.")
            return false
        }
        return native_isSampleLoaded(sampleId)
    }

    // This is a simplified playSample, playPadSample is more detailed
    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play sample.")
            return false
        }
        Log.d("AudioEngine", "playSample called: sampleID='$sampleId', instanceID='$noteInstanceId', vol=$volume, pan=$pan")
        // Default values for parameters not present in this simplified call
        return native_playPadSample(
            noteInstanceId = noteInstanceId,
            trackId = "general_track", // Default or derive if possible
            padId = "general_pad",     // Default or derive if possible
            sampleId = sampleId,
            sliceId = null,
            velocity = 1.0f,      // Default velocity
            coarseTune = 0,
            fineTune = 0,
            pan = pan,
            volume = volume,
            playbackModeOrdinal = PlaybackMode.ONE_SHOT.ordinal, // Default playback mode
            ampEnvAttackMs = 5f,    // Default envelope settings
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
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play pad sample.")
            return false
        }
        Log.d("AudioEngine", "playPadSample called: sampleID='$sampleId', padID='$padId', instanceID='$noteInstanceId', mode=${playbackMode.name}")
        return native_playPadSample(
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
            // TODO: Pass filterEnv, pitchEnv, LFOs to native layer if native_playPadSample is extended
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
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play sample slice.")
            return false
        }
        val actualSampleRate = native_getSampleRate(sampleId)
        if (actualSampleRate <= 0) {
            Log.e("AudioEngine", "playSampleSlice: Failed to get valid sample rate for sample ID '$sampleId'. Received: $actualSampleRate.")
            return false
        }
        Log.d("AudioEngine", "playSampleSlice called: ID='$sampleId', SR=$actualSampleRate, trim[$trimStartMs-$trimEndMs], loop[$loopStartMs-$loopEndMs], looping=$isLooping")
        return native_playSampleSlice(
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
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot set metronome state.")
            return
        }
        Log.d("AudioEngine", "setMetronomeState: enabled=$isEnabled, bpm=$bpm")
        withContext(Dispatchers.IO) {
            native_setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, primarySoundSampleId, secondarySoundSampleId ?: "")
        }
    }

    override suspend fun setMetronomeVolume(volume: Float) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot set metronome volume.")
            return
        }
        withContext(Dispatchers.IO) { native_setMetronomeVolume(volume) }
    }

    override suspend fun startAudioRecording(
        context: Context, // This context is for ContentResolver if filePathUri is a content URI
        filePathUri: String,
        sampleRate: Int,
        channels: Int,
        inputDeviceId: String? // Currently unused in native, but kept for interface
    ): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot start recording.")
            return false
        }
        Log.d("AudioEngine", "startAudioRecording called: URI='$filePathUri', SR=$sampleRate, Ch=$channels")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                // Use the passed 'context' for ContentResolver if filePathUri is a content URI
                // If filePathUri is a direct file path, this context might not be strictly necessary for 'w' mode
                // but good practice to have it available.
                this@AudioEngine.context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    val fd = pfd.fd
                    if (fd == -1) {
                        Log.e("AudioEngine", "Failed to get valid FileDescriptor for URI: $filePathUri")
                        return@withContext false
                    }
                    val success = native_startAudioRecording(fd, filePathUri, sampleRate, channels)
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
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot stop recording.")
            return null
        }
        return withContext(Dispatchers.IO) {
            val recordingInfo = native_stopAudioRecording()
            if (recordingInfo != null && recordingInfo.size == 2) {
                val filePath = recordingInfo[0] as? String
                val totalFrames = recordingInfo[1] as? Long
                val (recSampleRate, recChannels) = mRecordingParams ?: Pair(0,0)
                mRecordingParams = null

                if (filePath != null && totalFrames != null && recSampleRate > 0 && recChannels > 0) {
                    val durationMs = if (recSampleRate > 0) (totalFrames * 1000) / recSampleRate else 0L
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
        return native_isRecordingActive()
    }

    override fun getRecordingLevelPeak(): Float {
        if (!initialized) return 0.0f
        return native_getRecordingLevelPeak()
    }

    override suspend fun shutdown() {
        if (initialized) {
            native_shutdownOboe()
            initialized = false
            Log.d("AudioEngine", "Oboe shutdown.")
        }
    }

    override fun isInitialized(): Boolean {
        val nativeState = if (initialized) native_isOboeInitialized() else false
        return initialized && nativeState
    }

    override fun getReportedLatencyMillis(): Float {
        return if (initialized) native_getOboeReportedLatencyMillis() else -1.0f
    }

    // --- New Sequencer Kotlin Methods ---
    override suspend fun loadSequenceData(sequence: com.example.theone.model.Sequence) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot load sequence data.")
            return
        }
        withContext(Dispatchers.IO) {
            native_loadSequenceData(sequence)
        }
    }

    override suspend fun playSequence() {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play sequence.")
            return
        }
        withContext(Dispatchers.IO) {
            native_playSequence()
        }
    }

    override suspend fun stopSequence() {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot stop sequence.")
            return
        }
        withContext(Dispatchers.IO) {
            native_stopSequence()
        }
    }

    override suspend fun setSequencerBpm(bpm: Float) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot set sequencer BPM.")
            return
        }
        withContext(Dispatchers.IO) {
            native_setSequencerBpm(bpm)
        }
    }

    override suspend fun getSequencerPlayheadPosition(): Long {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot get playhead position.")
            return 0L
        }
        return withContext(Dispatchers.IO) {
            native_getSequencerPlayheadPosition()
        }
    }
    // --- End New Sequencer Kotlin Methods ---

    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
}