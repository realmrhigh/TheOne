package com.high.theone

import android.app.Application
import com.high.theone.audio.AudioEngineControl // Import the interface
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

@HiltAndroidApp
class TheOneApplication : Application() {

    @Inject
    lateinit var audioEngine: AudioEngineControl // Field inject AudioEngineControl

    // Define a CoroutineScope for application-level tasks if not already available
    // This scope should be managed according to the application lifecycle if tasks are long-running
    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                Log.i("TheOneApplication", "Initializing AudioEngine...")
                // Using default parameters as previously in MainActivity
                val initResult = audioEngine.initialize(
                    sampleRate = 48000,
                    bufferSize = 256,
                    enableLowLatency = true
                )
                if (initResult) {
                    Log.i("TheOneApplication", "AudioEngine initialized successfully. Latency: ${audioEngine.getReportedLatencyMillis()} ms")
                } else {
                    Log.e("TheOneApplication", "AudioEngine initialization failed.")
                }
            } catch (e: Exception) {
                Log.e("TheOneApplication", "Exception during AudioEngine initialization: ${e.message}", e)
            }
        }
        // Optional: Any other application-level initialization can go here
    }
}
