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
    // private val context: Context // If context was passed in constructor

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


    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        Log.d("AudioEngine", "AudioEngineControl.initialize called with sr: $sampleRate, bs: $bufferSize, lowLatency: $enableLowLatency")
        if (!initialized) {
            Log.d("AudioEngine", "Initializing Oboe via JNI...")
            // Parameters sampleRate, bufferSize are not yet passed to native_initOboe. This can be a future enhancement.
            initialized = native_initOboe()
            Log.d("AudioEngine", "Oboe JNI initialization result: $initialized")

            val testString = native_stringFromJNI()
            Log.d("AudioEngine", "Test JNI string: $testString")
        } else {
            Log.d("AudioEngine", "Already initialized.")
        }
        return initialized
    }

    // Public method that requires context for loading sample.
    // This is the one that should be called from activities or viewmodels that have access to context.
    suspend fun loadSampleToMemory(context: Context, sampleId: String, filePathUri: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot load sample.")
            return false
        }
        Log.d("AudioEngine", "loadSampleToMemory (with context) called for ID: $sampleId, URI: $filePathUri")

        return withContext(Dispatchers.IO) { // Perform file operations on IO dispatcher
            try {
                val uri = Uri.parse(filePathUri)
                val contentResolver = context.contentResolver
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fd
                    val statSize = pfd.statSize // This is the total size of the file.
                    // offset will be 0 for a full file. length is statSize.
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

    // Implementation of the interface method.
    // This highlights the context dependency issue. Ideally, AudioEngine is configured with Context at creation.
    override suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean {
        Log.e("AudioEngine", "loadSampleToMemory from interface called for ID: $sampleId. This version of the method needs application Context to be available to AudioEngine instance. Please ensure AudioEngine is initialized with Context or use the overloaded version that accepts Context directly.")
        // To make this work as an interface method without context, AudioEngine would need to store context
        // from its own initialization. For now, this will fail to indicate the design point.
        // Example: throw IllegalStateException("AudioEngine requires context to be initialized/passed for this operation.")
        return false
    }


    override suspend fun unloadSample(sampleId: String) {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot unload sample.")
            return
        }
        Log.d("AudioEngine", "unloadSample called for ID: $sampleId")
        withContext(Dispatchers.IO) { // JNI call itself is likely quick (map erase)
            native_unloadSample(sampleId)
        }
    }

    override fun isSampleLoaded(sampleId: String): Boolean {
        if (!initialized) {
            Log.e("AudioEngine", "AudioEngine not initialized. Cannot check if sample loaded.")
            return false
        }
        // This JNI call is quick, no need for Dispatchers.IO here.
        return native_isSampleLoaded(sampleId)
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
