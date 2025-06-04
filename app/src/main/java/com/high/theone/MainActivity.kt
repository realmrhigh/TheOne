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

                    // Test sample loading
                    android.util.Log.i("MainActivity", "AudioEngine initialized successfully, proceeding to test sample loading.")
                    val sampleId = "testSampleWav"
                    val assetName = "test.wav"

                    val cachedFilePath = copyAssetToCache(applicationContext, assetName, "cached_test.wav")

                    if (cachedFilePath != null) {
                        val fileUriString = "file://$cachedFilePath"
                        android.util.Log.i("MainActivity", "Testing loadSampleToMemory with URI: $fileUriString")

                        CoroutineScope(Dispatchers.Main).launch { // Or Dispatchers.IO
                            val loadSuccess = audioEngine.loadSampleToMemory(applicationContext, sampleId, fileUriString)
                            android.util.Log.i("MainActivity", "loadSampleToMemory($sampleId) result: $loadSuccess")

                            if (loadSuccess) {
                                val isLoaded = audioEngine.isSampleLoaded(sampleId)
                                android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after load: $isLoaded")

                                audioEngine.unloadSample(sampleId)
                                android.util.Log.i("MainActivity", "unloadSample($sampleId) called.")

                                val isLoadedAfterUnload = audioEngine.isSampleLoaded(sampleId)
                                android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after unload: $isLoadedAfterUnload")
                            } else {
                                android.util.Log.e("MainActivity", "Failed to load sample $sampleId.")
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