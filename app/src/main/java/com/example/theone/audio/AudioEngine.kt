package com.example.theone.audio

import android.util.Log

class AudioEngine : AudioEngineControl {

    private var initialized = false

    // JNI declarations
    private external fun native_initOboe(): Boolean
    private external fun native_shutdownOboe(): Unit // Corrected to Unit
    private external fun native_isOboeInitialized(): Boolean
    private external fun native_getOboeReportedLatencyMillis(): Float
    external fun native_stringFromJNI(): String // Keep the test string

    override suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean {
        Log.d("AudioEngine", "AudioEngineControl.initialize called with sr: $sampleRate, bs: $bufferSize, lowLatency: $enableLowLatency")
        // Parameters sampleRate, bufferSize, enableLowLatency are not yet passed to native_initOboe.
        // This can be a future enhancement.
        if (!initialized) {
            Log.d("AudioEngine", "Initializing Oboe via JNI...")
            initialized = native_initOboe()
            Log.d("AudioEngine", "Oboe JNI initialization result: $initialized")

            // Test call for stringFromJNI
            val testString = native_stringFromJNI()
            Log.d("AudioEngine", "Test JNI string: $testString")
        } else {
            Log.d("AudioEngine", "Already initialized.")
        }
        return initialized
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
        // This could directly call a native method if needed,
        // but for now, relies on the Kotlin side state,
        // which is set by the JNI call result.
        // For more robust status, native_isOboeInitialized can be used.
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
