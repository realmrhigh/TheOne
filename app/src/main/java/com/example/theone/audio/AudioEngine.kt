package com.example.theone.audio

import android.content.Context
import android.net.Uri
// import android.os.ParcelFileDescriptor // Not directly used, but ContentResolver.openFileDescriptor returns it
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AudioEngine : AudioEngineControl {

    private var initialized = false

    // JNI declarations (existing for Oboe)
    private external fun native_initOboe(): Boolean
    private external fun native_shutdownOboe(): Unit
    private external fun native_isOboeInitialized(): Boolean
    private external fun native_getOboeReportedLatencyMillis(): Float
    external fun native_stringFromJNI(): String // Keep the test string

    // JNI declarations for sample loading
    private external fun native_loadSampleToMemory(sampleId: String, fd: Int, offset: Long, length: Long): Boolean
    private external fun native_isSampleLoaded(sampleId: String): Boolean
    private external fun native_unloadSample(sampleId: String): Unit

    // JNI declaration for sample playback
    private external fun native_playPadSample(sampleId: String, noteInstanceId: String, volume: Float, pan: Float): Boolean

    // New JNI declarations for metronome
    private external fun native_setMetronomeState(
        isEnabled: Boolean,
        bpm: Float,
        timeSignatureNum: Int,
        timeSignatureDen: Int,
        primarySoundSampleId: String,
        secondarySoundSampleId: String?
    ): Unit

    private external fun native_setMetronomeVolume(volume: Float): Unit


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
             native_setMetronomeState(isEnabled, bpm, timeSignatureNum, timeSignatureDen, primarySoundSampleId, secondarySoundSampleId ?: "") // Pass empty string if null
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
