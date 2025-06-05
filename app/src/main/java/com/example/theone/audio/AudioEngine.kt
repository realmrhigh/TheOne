package com.example.theone.audio

import android.content.Context
import android.net.Uri
// import android.os.ParcelFileDescriptor // Not directly used, but ContentResolver.openFileDescriptor returns it
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID    // For generating unique IDs

class AudioEngine : AudioEngineControl {

    private var initialized = false

    // JNI declarations (Oboe)
    private external fun native_initOboe(): Boolean
    private external fun native_shutdownOboe(): Unit
    private external fun native_isOboeInitialized(): Boolean
    private external fun native_getOboeReportedLatencyMillis(): Float
    external fun native_stringFromJNI(): String

    // JNI declarations for sample loading
    private external fun native_loadSampleToMemory(sampleId: String, fd: Int, offset: Long, length: Long): Boolean
    private external fun native_isSampleLoaded(sampleId: String): Boolean
    private external fun native_unloadSample(sampleId: String): Unit

    // JNI declaration for sample playback

    // JNI declaration for sliced/looped sample playback
    private external fun native_playSampleSlice(sampleId: String, noteInstanceId: String, volume: Float, pan: Float, startFrame: Long, endFrame: Long, loopStartFrame: Long, loopEndFrame: Long, isLooping: Boolean): Boolean
    private external fun native_playPadSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean

    // JNI declarations for metronome
    private external fun native_setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    ): Unit
    private external fun native_setMetronomeVolume(volume: Float): Unit

    // JNI declarations for recording
    private external fun native_startAudioRecording(fd: Int, storagePathForMetadata: String, sampleRate: Int, channels: Int): Boolean
    private external fun native_stopAudioRecording(): Array<Any>? // Returns [String_filePath, Long_totalFrames] or null
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
                val uri = Uri.parse(filePathUri)
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
        return native_playPadSample(sampleId, noteInstanceId, volume, pan)
    }

    suspend fun playSampleSlice(
        sampleId: String,
        noteInstanceId: String,
        volume: Float,
        pan: Float,
        sampleRate: Int,
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
        if (sampleRate <= 0) {
            Log.e("AudioEngine", "Invalid sampleRate: $sampleRate. Cannot calculate frames.")
            return false
        }

        val msToFrames = { ms: Long -> (ms * sampleRate) / 1000L }

        val startFrame = msToFrames(trimStartMs)
        val endFrame = msToFrames(trimEndMs)

        var nativeLoopStartFrame: Long = -1L // -1 indicates no loop point
        var nativeLoopEndFrame: Long = -1L   // -1 indicates no loop point
        val nativeIsLooping = isLooping && loopStartMs != null && loopEndMs != null && loopStartMs < loopEndMs

        if (nativeIsLooping && loopStartMs != null && loopEndMs != null) { // Redundant null check due to nativeIsLooping but good for clarity
            nativeLoopStartFrame = msToFrames(loopStartMs)
            nativeLoopEndFrame = msToFrames(loopEndMs)
            // Ensure loop points are within the playback segment and logical
            if (nativeLoopStartFrame < startFrame || nativeLoopEndFrame > endFrame || nativeLoopStartFrame >= nativeLoopEndFrame) {
                Log.w("AudioEngine", "Invalid loop points ($nativeLoopStartFrame, $nativeLoopEndFrame) for slice ($startFrame, $endFrame). Disabling loop for this playback.")
                // Effectively disable looping if points are bad for this specific call, though isLooping flag might still be true
                // The native side should also validate this rigorously.
                 return native_playPadSample(sampleId, noteInstanceId, volume, pan) // Fallback to full play or play without loop
            }
        }

        Log.d("AudioEngine", "playSampleSlice called: sampleID=$sampleId, instanceID=$noteInstanceId, vol=$volume, pan=$pan, frames: $startFrame-$endFrame, loopFrames: $nativeLoopStartFrame-$nativeLoopEndFrame, isLooping=$nativeIsLooping")
        return native_playSampleSlice(sampleId, noteInstanceId, volume, pan, startFrame, endFrame, nativeLoopStartFrame, nativeLoopEndFrame, nativeIsLooping)
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
                val uri = Uri.parse(filePathUri)
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
                    val name = Uri.parse(filePath).lastPathSegment ?: id

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

    companion object {
        init {
            System.loadLibrary("theone")
        }
    }
}
