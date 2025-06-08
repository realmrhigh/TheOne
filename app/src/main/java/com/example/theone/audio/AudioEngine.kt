package com.example.theone.audio

import android.content.Context
//import android.net.Uri
// import android.os.ParcelFileDescriptor // Not directly used, but ContentResolver.openFileDescriptor returns it
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID    // For generating unique IDs
import androidx.core.net.toUri
import com.example.theone.model.SampleMetadata // Added import
import com.example.theone.model.AudioInputSource
import com.example.theone.features.drumtrack.model.PadSettings
import java.io.File
import android.content.Context // Ensure Context is imported

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

    // JNI declaration for sample playback
    private external fun native_playPadSample(noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?, velocity: Float, coarseTune: Int, fineTune: Int, pan: Float, volume: Float): Boolean
    private external fun native_playSampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, sampleRate: Int, trimStartMs: Long, trimEndMs: Long, loopStartMs: Long, loopEndMs: Long, isLooping: Boolean): Boolean
    private external fun native_getSampleRate(sampleId: String): Int

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
    private external fun native_stopAudioRecording(): Array<Any>? // Returns [String_filePath, Long_totalFrames] or null
    private external fun native_isRecordingActive(): Boolean
    private external fun native_getRecordingLevelPeak(): Float

    // --- NEW JNI DECLARATION FOR UPDATING PAD SETTINGS ---
    external fun native_updatePadSettings(
        trackId: String,
        padId: String, // This is the PadSettings.id, used as a key component
        padSettings: PadSettings // The complex PadSettings object from Kotlin
    ): Unit
    // --- END NEW JNI DECLARATION ---


    // Store recording parameters for SampleMetadata creation
    private var mRecordingParams: Pair<Int, Int>? = null // Pair<SampleRate, Channels>


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

    suspend fun loadSampleToMemory(context: Context, sampleId: String, filePathUri: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot load sample.")
            return false
        }
        Log.d("AudioEngine", "loadSampleToMemory (with context) called for ID: $sampleId, URI: $filePathUri")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                val contentResolver = context.contentResolver
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fd
                    val statSize = pfd.statSize
                    Log.d("AudioEngine", "Opened FD: $fd, StatSize: $statSize for URI: $filePathUri")
                    if (fd == -1) {
                        Log.e("AudioEngine", "Failed to get valid FileDescriptor for URI: $filePathUri (FD is -1)")
                        return@withContext false
                    }
                    // Using 0L for Long literals, which is valid Kotlin.
                    native_loadSampleToMemory(sampleId, fd, 0L, statSize)
                } ?: run {
                    Log.e("AudioEngine", "Failed to open ParcelFileDescriptor for URI: $filePathUri")
                    false
                }
            } catch (e: IOException) {
                Log.e("AudioEngine", "IOException during sample loading for $sampleId: ${'$'}{e.message}", e)
                false
            } catch (e: SecurityException) {
                Log.e("AudioEngine", "SecurityException during sample loading for $sampleId: ${'$'}{e.message}", e)
                false
            } catch (e: Exception) {
                Log.e("AudioEngine", "Unexpected exception during sample loading for $sampleId: ${'$'}{e.message}", e)
                false
            }
        }
    }

    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        // Now uses the stored context
        Log.d("AudioEngine", "loadSampleToMemory (interface version) called for ID: $sampleId, URI: $filePathUri")
        return loadSampleToMemory(context, sampleId, filePathUri)
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

    override suspend fun playSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play sample.")
            return false
        }
        Log.d("AudioEngine", "playSample called: sampleID='$sampleId', instanceID='$noteInstanceId', vol=$volume, pan=$pan")
        // Correctly call the more detailed native function with default values.
        return native_playPadSample(
            noteInstanceId = noteInstanceId,
            trackId = "general_playback_track", // Default value
            padId = "general_playback_pad",     // Default value
            sampleId = sampleId,
            sliceId = null,                     // Default value
            velocity = 1.0f,                    // Default value
            coarseTune = 0,                     // Default value
            fineTune = 0,                       // Default value
            pan = pan,
            volume = volume
        )
    }

    override suspend fun playPadSample(
        noteInstanceId: String,
        trackId: String,
        padId: String,
        sampleId: String,
        sliceId: String?,
        velocity: Float,
        playbackMode: com.example.theone.model.PlaybackMode,
        coarseTune: Int,
        fineTune: Int,
        pan: Float,
        volume: Float,
        ampEnv: com.example.theone.model.SynthModels.EnvelopeSettings, // Corrected type
        filterEnv: com.example.theone.model.SynthModels.EnvelopeSettings?, // Corrected type
        pitchEnv: com.example.theone.model.SynthModels.EnvelopeSettings?, // Corrected type
        lfos: List<Any> // This was List<Any> in the original file content
    ): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot play pad sample.")
            return false
        }
        Log.d("AudioEngine", "playPadSample called: sampleID='$sampleId', padID='$padId', instanceID='$noteInstanceId', vol=$volume, pan=$pan")
        // The C++ native_playPadSample now uses trackId and padId to look up the full settings from gPadSettingsMap
        return native_playPadSample(noteInstanceId, trackId, padId, sampleId, sliceId, velocity, coarseTune, fineTune, pan, volume)
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
            Log.e("AudioEngine", "playSampleSlice: Failed to get valid sample rate for sample ID '$sampleId'. Received: $actualSampleRate. Aborting playback.")
            return false
        }

        Log.d("AudioEngine", "playSampleSlice called: sampleID='$sampleId', instanceID='$noteInstanceId', vol=$volume, pan=$pan, SR=$actualSampleRate, trimStart=$trimStartMs, trimEnd=$trimEndMs, loopStart=${loopStartMs ?: 0L}, loopEnd=${loopEndMs ?: 0L}, looping=$isLooping")
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
        Log.d("AudioEngine", "setMetronomeState called: enabled=$isEnabled, bpm=$bpm, timeSig=$timeSignatureNum/$timeSignatureDen, primaryID='$primarySoundSampleId', secondaryID='$secondarySoundSampleId'")
        withContext(Dispatchers.IO) {
            native_setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, primarySoundSampleId, secondarySoundSampleId ?: "")
        }
    }

    override suspend fun setMetronomeVolume(volume: Float) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot set metronome volume.")
            return
        }
        Log.d("AudioEngine", "setMetronomeVolume called: volume=$volume")
        withContext(Dispatchers.IO) {
            native_setMetronomeVolume(volume)
        }
    }

    override suspend fun startAudioRecording(
        context: Context,
        filePathUri: String,
        sampleRate: Int,
        channels: Int,
        inputDeviceId: String?
    ): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot start recording.")
            return false
        }
        Log.d("AudioEngine", "startAudioRecording called: URI='$filePathUri', SR=$sampleRate, Ch=$channels")

        return withContext(Dispatchers.IO) {
            try {
                val uri = filePathUri.toUri()
                context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
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
                Log.e("AudioEngine", "Exception during startAudioRecording for $filePathUri: ${'$'}{e.message}", e)
                false
            }
        }
    }

    override suspend fun stopAudioRecording(): SampleMetadata? {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot stop recording.")
            return null
        }
        Log.d("AudioEngine", "stopAudioRecording called.")

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

                    Log.i("AudioEngine", "Recording stopped. Path: $filePath, Frames: $totalFrames, SR: $recSampleRate, Ch: $recChannels, Duration: $durationMs ms")
                    SampleMetadata(
                        id = id,
                        name = name,
                        uri = filePath, // Changed from filePathUri
                        duration = durationMs, // Changed from durationMs
                        sampleRate = recSampleRate,
                        channels = recChannels
                        // Other fields will use defaults from the consolidated SampleMetadata
                    )
                } else {
                    Log.e("AudioEngine", "Failed to get valid recording info from native layer. Path: $filePath, Frames: $totalFrames, SR: $recSampleRate, Ch: $recChannels")
                    null
                }
            } else {
                Log.e("AudioEngine", "native_stopAudioRecording returned null or invalid array.")
                null
            }
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
        Log.d("AudioEngine", "AudioEngineControl.shutdown called")
        if (initialized) {
            native_shutdownOboe()
            initialized = false
            Log.d("AudioEngine", "Oboe shutdown.")
        }
    }

    override fun isInitialized(): Boolean {
        val nativeState = native_isOboeInitialized()
        Log.d("AudioEngine", "isInitialized called. Kotlin state: $initialized, Native state: $nativeState")
        return initialized && nativeState
    }

    override fun getReportedLatencyMillis(): Float {
        return if (initialized) {
            native_getOboeReportedLatencyMillis()
        } else {
            -1.0f
        }
    }

    // New methods for SamplerViewModel as per subtask (These seem like they belong in a higher level class or ViewModel)
    // For now, keeping them here as per the file content.

    fun startAudioRecording(audioInputSource: AudioInputSource, tempFilePath: String): SampleMetadata {
        println("AudioEngine: Starting recording from ${'$'}{audioInputSource} to ${'$'}tempFilePath")
        Thread.sleep(2000)
        println("AudioEngine: Recording finished for ${'$'}tempFilePath")
        val file = File(tempFilePath)
        try {
            file.createNewFile()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error creating dummy file ${'$'}tempFilePath", e)
        }
        val durationMs = 2000L
        val id = UUID.randomUUID().toString()
        return SampleMetadata(
            id = id, // Added id
            name = file.nameWithoutExtension,
            uri = file.toURI().toString(),
            duration = durationMs,
            // sampleRate, channels, bitDepth, etc., will use default values
            // from the SampleMetadata class definition in SampleModels.kt
            trimStartMs = 0, // Explicitly setting, though defaults might exist
            trimEndMs = durationMs // Explicitly setting
        )
    }

    fun stopCurrentRecording() {
        println("AudioEngine: stopCurrentRecording called.")
    }

    fun playSampleSlice(audioUri: String, startMs: Long, endMs: Long) {
        println("AudioEngine: Playing slice of ${'$'}audioUri from ${'$'}startMs ms to ${'$'}endMs ms")
        if (startMs < endMs) {
            try {
                Thread.sleep(endMs - startMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w("AudioEngine", "Playback simulation interrupted for ${'$'}audioUri")
            }
        }
        println("AudioEngine: Finished playing slice of ${'$'}audioUri")
    }

    fun playPadSample(padSettings: PadSettings) {
        println("AudioEngine: playPadSample called for sampleId ${'$'}{padSettings.sampleId}. Placeholder - full implementation later.")
        if (padSettings.sampleId != null) {
            Log.d("AudioEngine", "Simulating pad sample playback for ${'$'}{padSettings.sampleId}")
            playSampleSlice(padSettings.sampleId!!, 0, 500)
        }
    }

    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
}