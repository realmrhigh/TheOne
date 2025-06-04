package com.high.theone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme // Added for Surface
import androidx.compose.material3.Scaffold // Keep existing imports if not conflicting
import androidx.compose.material3.Surface // Added for Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp // Import for dp unit
import com.high.theone.ui.theme.TheOneTheme
import com.example.theone.audio.AudioEngine // Import for AudioEngine
import com.example.theone.audio.AudioEngine // Import for AudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log // Already present but good to ensure
import kotlinx.coroutines.delay // For non-blocking delays
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Keep this if it was there

        audioEngine = AudioEngine()

        Log.i("MainActivity", "Launching coroutine for AudioEngine initialization...")
        // Launch a coroutine for a suspend function
        CoroutineScope(Dispatchers.Main).launch { // Use Dispatchers.Main if UI updates are needed, IO for background
            try {
                // Using Dispatchers.IO for the actual blocking call if needed,
                // but initialize itself is suspend, so it handles its own threading if implemented with withContext(Dispatchers.IO)
                val initResult = audioEngine.initialize(sampleRate = 48000, bufferSize = 256, enableLowLatency = true)
                android.util.Log.i("MainActivity", "AudioEngine.initialize() returned: $initResult")
                if (initResult) {
                    android.util.Log.i("MainActivity", "AudioEngine isInitialized after init: ${audioEngine.isInitialized()}")
                    android.util.Log.i("MainActivity", "AudioEngine Latency: ${audioEngine.getReportedLatencyMillis()} ms")
                    // You can update UI here if needed, e.g., with a LaunchedEffect if in Composable

                    // Define Metronome Sound IDs
                    val primaryClickId = "__METRONOME_PRIMARY__"
                    val secondaryClickId = "__METRONOME_SECONDARY__"
                    val primaryAssetName = "click_primary.wav"
                    val secondaryAssetName = "click_secondary.wav"

                    // Load Primary Click Sound
                    android.util.Log.i("MainActivity", "Pre-loading primary metronome click sound...")
                    val primaryCachedPath = copyAssetToCache(applicationContext, primaryAssetName, "cached_click_primary.wav")
                    if (primaryCachedPath != null) {
                        val primaryFileUri = "file://$primaryCachedPath"
                        val primaryLoadSuccess = audioEngine.loadSampleToMemory(applicationContext, primaryClickId, primaryFileUri)
                        android.util.Log.i("MainActivity", "Loading $primaryClickId ($primaryAssetName) result: $primaryLoadSuccess. URI: $primaryFileUri")
                        if (!primaryLoadSuccess) android.util.Log.e("MainActivity", "Failed to load $primaryClickId")
                    } else {
                        android.util.Log.e("MainActivity", "Failed to copy $primaryAssetName to cache.")
                    }

                    // Load Secondary Click Sound
                    android.util.Log.i("MainActivity", "Pre-loading secondary metronome click sound...")
                    val secondaryCachedPath = copyAssetToCache(applicationContext, secondaryAssetName, "cached_click_secondary.wav")
                    if (secondaryCachedPath != null) {
                        val secondaryFileUri = "file://$secondaryCachedPath"
                        val secondaryLoadSuccess = audioEngine.loadSampleToMemory(applicationContext, secondaryClickId, secondaryFileUri)
                        android.util.Log.i("MainActivity", "Loading $secondaryClickId ($secondaryAssetName) result: $secondaryLoadSuccess. URI: $secondaryFileUri")
                        if (!secondaryLoadSuccess) android.util.Log.e("MainActivity", "Failed to load $secondaryClickId")
                    } else {
                        android.util.Log.e("MainActivity", "Failed to copy $secondaryAssetName to cache.")
                    }

                    // Assuming primaryLoadSuccess and secondaryLoadSuccess would be checked for real sounds.
                    // For dummy sounds, we proceed regardless for testing the metronome logic.
                    Log.i("MainActivity", "Metronome sounds loading attempted. Proceeding to test metronome.")

                    // Test Metronome
                    audioEngine.setMetronomeVolume(0.8f) // Set volume first
                    Log.i("MainActivity", "Enabling metronome at 120 BPM, 4/4.")
                    audioEngine.setMetronomeState(
                        isEnabled = true,
                        bpm = 120.0f,
                        timeSignatureNum = 4,
                        timeSignatureDen = 4,
                        primarySoundSampleId = primaryClickId,
                        secondarySoundSampleId = secondaryClickId
                    )

                    delay(5000) // Let it run for 5 seconds

                    Log.i("MainActivity", "Changing metronome BPM to 180.")
                    audioEngine.setMetronomeState(
                        isEnabled = true, // Keep it enabled
                        bpm = 180.0f,
                        timeSignatureNum = 4,
                        timeSignatureDen = 4,
                        primarySoundSampleId = primaryClickId,
                        secondarySoundSampleId = secondaryClickId
                    )

                    delay(5000) // Let it run for 5 seconds

                    Log.i("MainActivity", "Changing metronome time signature to 3/4, BPM 120.")
                    audioEngine.setMetronomeState(
                        isEnabled = true, // Keep it enabled
                        bpm = 120.0f,
                        timeSignatureNum = 3,
                        timeSignatureDen = 4,
                        primarySoundSampleId = primaryClickId,
                        secondarySoundSampleId = secondaryClickId
                    )

                    delay(5000) // Let it run for 5 seconds

                    Log.i("MainActivity", "Disabling metronome.")
                    audioEngine.setMetronomeState(
                        isEnabled = false,
                        bpm = 120.0f, // These values don't matter much when disabling
                        timeSignatureNum = 3,
                        timeSignatureDen = 4,
                        primarySoundSampleId = primaryClickId,
                        secondarySoundSampleId = secondaryClickId
                    )

                    delay(2000) // Delay 2 seconds to confirm it's off

                    Log.i("MainActivity", "Re-enabling metronome at 60 BPM, 4/4.")
                    audioEngine.setMetronomeState(
                        isEnabled = true,
                        bpm = 60.0f,
                        timeSignatureNum = 4,
                        timeSignatureDen = 4,
                        primarySoundSampleId = primaryClickId,
                        secondarySoundSampleId = secondaryClickId
                    )
                    // Metronome will run until app is closed or engine is shut down.
                    // End of Metronome Test section

                    // Test sample loading (original test.wav) - can proceed after metronome tests or in parallel if desired
                    android.util.Log.i("MainActivity", "Proceeding to test general sample loading (test.wav).")
                    val sampleId = "testSampleWav" // Original test sample
                    val assetName = "test.wav"

                    val cachedFilePath = copyAssetToCache(applicationContext, assetName, "cached_test.wav")

                    if (cachedFilePath != null) {
                        val fileUriString = "file://$cachedFilePath"
                        android.util.Log.i("MainActivity", "Testing loadSampleToMemory with URI: $fileUriString for $sampleId")

                        // This launch block was inside the previous if(initResult), so it's fine.
                        // If it was outside, it would need its own CoroutineScope.
                        // For this subtask, we are placing this inside the same CoroutineScope(Dispatchers.Main).launch
                        // as the initResult check.

                        val loadSuccess = audioEngine.loadSampleToMemory(applicationContext, sampleId, fileUriString)
                        android.util.Log.i("MainActivity", "loadSampleToMemory($sampleId) result: $loadSuccess")

                        if (loadSuccess) {
                                val isLoaded = audioEngine.isSampleLoaded(sampleId)
                                android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after load: $isLoaded")

                                if (isLoaded) {
                                    android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_1)...")
                                    val play1Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_1", volume = 0.8f, pan = 0.0f)
                                    android.util.Log.i("MainActivity", "playSample($sampleId, instance_1) result: $play1Success")

                                    delay(500) // Delay 500 ms

                                    android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_2) panned left...")
                                    val play2Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_2", volume = 0.7f, pan = -0.8f)
                                    android.util.Log.i("MainActivity", "playSample($sampleId, instance_2) result: $play2Success")

                                    delay(500)

                                    android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_3) panned right...")
                                    val play3Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_3", volume = 0.7f, pan = 0.8f)
                                    android.util.Log.i("MainActivity", "playSample($sampleId, instance_3) result: $play3Success")

                                } else {
                                    android.util.Log.e("MainActivity", "Sample $sampleId was reported as not loaded after load attempt. Cannot play.")
                                }

                                // Unload after some delay to let sounds play
                                delay(2000) // Wait 2 seconds
                                audioEngine.unloadSample(sampleId)
                                android.util.Log.i("MainActivity", "unloadSample($sampleId) called.")
                                val isLoadedAfterUnload = audioEngine.isSampleLoaded(sampleId)
                                android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after unload: $isLoadedAfterUnload")

                            } else {
                                android.util.Log.e("MainActivity", "Failed to load sample $sampleId. Cannot test playback.")
                            }
                        }
                    } else {
                        android.util.Log.e("MainActivity", "Failed to copy asset $assetName to cache. Cannot test sample loading.")
                    }

                } else {
                    android.util.Log.e("MainActivity", "AudioEngine initialization failed.")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Exception during AudioEngine initialization: ${e.message}", e)
            }
        }

        setContent {
            TheOneTheme {
                // A surface container using the 'background' color from the theme
                // If Scaffold was used, it can be kept or replaced by Surface as needed.
                // For simplicity, using Surface as in the example.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // The original Greeting call was inside a Scaffold with padding.
                    // Replicating a simple Greeting call here. Adjust as necessary.
                    Greeting("Android", modifier = Modifier.padding(16.dp)) // Example padding
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // android.util.Log.i for clarity, though `Log` is imported
        android.util.Log.i("MainActivity", "onDestroy called, preparing to shutdown AudioEngine.")
        if (::audioEngine.isInitialized) { // Check if audioEngine has been initialized
             CoroutineScope(Dispatchers.IO).launch {
                audioEngine.shutdown()
                android.util.Log.i("MainActivity", "AudioEngine shutdown complete. IsInitialized: ${audioEngine.isInitialized()}")
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TheOneTheme {
        Greeting("Android")
    }
}