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
import com.example.theone.model.EnvelopeSettings // For ampEnvelope
import com.example.theone.model.SampleLayer // For layers
import com.example.theone.model.LayerTriggerRule // For layerTriggerRule
import java.io.File

class AudioEngine : AudioEngineControl {

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
    private external fun native_playPadSample_DEPRECATED(noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?, velocity: Float, coarseTune: Int, fineTune: Int, pan: Float, volume: Float): Boolean
    private external fun native_playSampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, sampleRate: Int, trimStartMs: Long, trimEndMs: Long, loopStartMs: Long, loopEndMs: Long, isLooping: Boolean): Boolean
    private external fun native_getSampleRate(sampleId: String): Int

    // New JNI function for triggering pads with complex settings
    private external fun triggerPadNative(
        padId: String, // For C++ to identify the pad instance
        velocity: Int,
        layerTriggerRuleOrdinal: Int,
        baseVolume: Float,
        basePan: Float,
        // Pass amp envelope as a FloatArray
        ampEnvelopeParams: FloatArray, // e.g. [type, attack, hold, decay, sustain, release, velToAttack, velToLevel]
        // For layers, pass an array of strings, where each string is "sampleId;velMin;velMax;volOffsetDb;panOffset;tuneCoarseOffset;tuneFineOffset"
        // This is a mock serialization. Real implementation might use JSON or byte buffers.
        layersData: Array<String>,
        placeholderForLfos: String = "" // LFOs are skipped for this JNI definition
    )

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
    // This Array<Any>? needs to be processed to create SampleMetadata for the new startAudioRecording
    private external fun native_isRecordingActive(): Boolean
    private external fun native_getRecordingLevelPeak(): Float

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
        Log.e("AudioEngine", "loadSampleToMemory from interface called for ID: $sampleId. This version of the method needs application Context to be available to AudioEngine instance. Please ensure AudioEngine is initialized with Context or use the overloaded version that accepts Context directly.")
        return false
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

    override suspend fun playPadSample( // This is the old one from AudioEngineControl
        noteInstanceId: String,
        trackId: String,
        padId: String,
        sampleId: String,
        sliceId: String?,
        velocity: Float,
        playbackMode: com.example.theone.model.PlaybackMode, // This is the one from model package
        coarseTune: Int,
        fineTune: Int,
        pan: Float,
        volume: Float,
        ampEnv: EnvelopeSettings, // This is the one from model package
        filterEnv: EnvelopeSettings?,
        pitchEnv: EnvelopeSettings?,
        lfos: List<Any> // This was List<LFOSettings> before, now Any for compatibility with interface
    ): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngineControl.playPadSample called, but AudioEngine not initialized.")
            return false
        }
        Log.w("AudioEngine", "AudioEngineControl.playPadSample (the complex one) called. This is DEPRECATED. " +
                "Consider transitioning to triggerPad with full PadSettings. Simulating a simple playback via JNI for now.")
        // This is a deprecated path. We call the old JNI function.
        // The new triggerPad should be used.
        return native_playPadSample_DEPRECATED(noteInstanceId, trackId, padId, sampleId, sliceId, velocity, coarseTune, fineTune, pan, volume)
    }

    // New triggerPad method for M3
    fun triggerPad(padSettingsId: String, padSettings: PadSettings, velocity: Int) {
        if (!initialized) {
            Log.e("AudioEngine", "Cannot trigger pad $padSettingsId, AudioEngine not initialized.")
            return
        }

        val ampEnv = padSettings.ampEnvelope
        val ampEnvelopeParams = floatArrayOf(
            ampEnv.type.ordinal.toFloat(), ampEnv.attackMs, ampEnv.holdMs ?: 0f, ampEnv.decayMs,
            ampEnv.sustainLevel ?: 0f, ampEnv.releaseMs, ampEnv.velocityToAttack, ampEnv.velocityToLevel
        )

        val layersData = padSettings.layers.filter { it.enabled }.map { layer ->
            // Simple string serialization: "sampleId;velMin;velMax;volOffsetDb;panOffset;tuneCoarseOffset;tuneFineOffset"
            "${layer.sampleId};${layer.velocityRangeMin};${layer.velocityRangeMax};${layer.volumeOffsetDb};${layer.panOffset};${layer.tuningCoarseOffset};${layer.tuningFineOffset}"
        }.toTypedArray()

        if (padSettings.layers.any { it.enabled }) {
            Log.d("AudioEngine", "Triggering pad $padSettingsId with ${layersData.size} enabled layers. Velocity: $velocity. Rule: ${padSettings.layerTriggerRule}")
            triggerPadNative(
                padId = padSettings.padSettingsId, // Use the PadSettings' own unique ID
                velocity = velocity,
                layerTriggerRuleOrdinal = padSettings.layerTriggerRule.ordinal,
                baseVolume = padSettings.volume,
                basePan = padSettings.pan,
                ampEnvelopeParams = ampEnvelopeParams,
                layersData = layersData
                // TODO: Pass filterEnvelope, pitchEnvelope, and LFOs data
            )
        } else {
            println("AudioEngine: No enabled layers to play for pad ${padSettings.padSettingsId}")
        }
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
        // For debugging, you can add:
        // Log.d("AudioEngine", "playSampleSlice: Using actual sample rate $actualSampleRate for sample ID '$sampleId'")

        Log.d("AudioEngine", "playSampleSlice called: sampleID='$sampleId', instanceID='$noteInstanceId', vol=$volume, pan=$pan, SR=$actualSampleRate, trimStart=$trimStartMs, trimEnd=$trimEndMs, loopStart=${loopStartMs ?: 0L}, loopEnd=${loopEndMs ?: 0L}, looping=$isLooping")
        return native_playSampleSlice(
            sampleId,
            noteInstanceId,
            volume,
            pan,
            actualSampleRate, // Pass the fetched actual sample rate
            trimStartMs,
            trimEndMs,
            loopStartMs ?: 0L, // Pass 0 if null
            loopEndMs ?: 0L,   // Pass 0 if null
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
        context: Context, // Added context here for Uri handling
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
                        filePathUri = filePath,
                        durationMs = durationMs,
                        sampleRate = recSampleRate,
                        channels = recChannels
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

    // New methods for SamplerViewModel as per subtask

    fun startAudioRecording(audioInputSource: AudioInputSource, tempFilePath: String): SampleMetadata {
        // TODO: Implement actual audio recording logic using Android APIs (e.g., MediaRecorder or AudioRecord)
        // TODO: Handle different audioInputSource types (MICROPHONE, INTERNAL_LOOPBACK, EXTERNAL_USB)
        // This would involve calling native_startAudioRecording with appropriate parameters (FD from tempFilePath, SR, Ch)
        // and then processing the result from native_stopAudioRecording.
        println("AudioEngine: Starting recording from ${audioInputSource} to $tempFilePath")

        // TODO: Implement 2-minute (120,000 ms) recording limit. If reached, stop recording automatically.
        // For now, simulate a fixed duration, e.g., 2 seconds
        // In a real scenario, native_startAudioRecording would be called here (perhaps with a context for FD)
        // and native_stopAudioRecording would be called after duration or by stopCurrentRecording().
        // The result of native_stopAudioRecording would then be used to create SampleMetadata.

        Thread.sleep(2000) // Simulate recording time
        println("AudioEngine: Recording finished for $tempFilePath")

        val file = File(tempFilePath)
        try {
            file.createNewFile() // Create a dummy file
            // Simulate writing some data if necessary for other parts to read metadata like duration
            // For example, write a dummy WAV header if other tools expect it.
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error creating dummy file $tempFilePath", e)
            // Fallback or rethrow, for now just print stack trace via Log.e
        }

        // TODO: Get actual duration from the recording (e.g. from native_stopAudioRecording's result)
        val durationMs = 2000L // Simulated duration

        // The SampleMetadata should have trimEndMs set to durationMs by default in its constructor
        return SampleMetadata(
            uri = file.toURI().toString(),
            duration = durationMs,
            name = file.nameWithoutExtension, // Or null as per original spec
            trimStartMs = 0,
            trimEndMs = durationMs // Explicitly set, though constructor should handle it
        )
    }

    fun stopCurrentRecording() {
        // TODO: Implement logic to stop the ongoing recording initiated by startAudioRecording.
        // This method would signal the recording process to finalize the file.
        // It should ideally trigger the finalization part of the new startAudioRecording method,
        // possibly by setting a flag that the recording loop checks, or by directly calling native_stopAudioRecording().
        println("AudioEngine: stopCurrentRecording called.")
        // val recordingInfo = native_stopAudioRecording() // This would be called
        // And the result (filePath, duration) would be used by startAudioRecording's logic or a callback.
        // For now, this is a stub. The SamplerViewModel expects startAudioRecording to eventually return metadata.
    }

    fun playSampleSlice(audioUri: String, startMs: Long, endMs: Long) {
        // TODO: Implement actual audio playback logic for a slice of an audio file
        // This might involve using the existing native_playSampleSlice.
        // The existing native_playSampleSlice requires a sampleId. If audioUri is a file path,
        // it might need to be "loaded" temporarily to get a temporary ID, or native code needs to handle direct URI.
        // For now, simulate.
        println("AudioEngine: Playing slice of $audioUri from $startMs ms to $endMs ms")
        if (startMs < endMs) {
            try {
                Thread.sleep(endMs - startMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interrupt status
                Log.w("AudioEngine", "Playback simulation interrupted for $audioUri")
            }
        }
        println("AudioEngine: Finished playing slice of $audioUri")
    }

    // This is the old M1 playPadSample, used by DrumTrackViewModel in M1.
    // It should be removed or marked deprecated once DrumTrackViewModel calls triggerPad.
    // For now, keeping it but renaming the JNI call it uses.
    fun playPadSample(padSettings: PadSettings) { // This is the one from M1 that DrumTrackViewModel was calling
        Log.w("AudioEngine", "Old playPadSample(padSettings: PadSettings) called. This is DEPRECATED. Use triggerPad.")
        if (!initialized) {
            Log.e("AudioEngine", "Cannot play (old) pad sample, AudioEngine not initialized.")
            return
        }
        // This method is too simple for the new PadSettings. It only considers the first layer's sampleId if any.
        // Or, if PadSettings retained a single sampleId at its root (which it doesn't anymore).
        // For now, let's try to play the first enabled layer's sample as a simple fallback.
        val firstEnabledLayer = padSettings.layers.firstOrNull { it.enabled }
        if (firstEnabledLayer != null) {
            Log.d("AudioEngine", "Simulating old playPadSample with first enabled layer: ${firstEnabledLayer.sampleId}")
            // Use a generic note ID for this deprecated path
            val noteInstanceId = "deprecated_pad_note_${System.currentTimeMillis()}"
            // Call the old JNI function with basic parameters from the PadSettings and first layer
            native_playPadSample_DEPRECATED(
                noteInstanceId = noteInstanceId,
                trackId = "deprecated_track",
                padId = padSettings.padSettingsId,
                sampleId = firstEnabledLayer.sampleId,
                sliceId = null,
                velocity = 1.0f, // Default velocity
                coarseTune = padSettings.tuningCoarse + firstEnabledLayer.tuningCoarseOffset,
                fineTune = padSettings.tuningFine + firstEnabledLayer.tuningFineOffset,
                pan = padSettings.pan + firstEnabledLayer.panOffset,
                volume = padSettings.volume // Simplified: doesn't use firstEnabledLayer.volumeOffsetDb directly here
            )
        } else {
            println("AudioEngine: Old playPadSample called for pad ${padSettings.padSettingsId}, but no enabled layers found.")
        }
    }


    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
}