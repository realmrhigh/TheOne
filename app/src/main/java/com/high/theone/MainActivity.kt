package com.high.theone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button // Added
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment // Added
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp // Added
import androidx.navigation.NavHostController // Added
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.theone.audio.AudioEngine
import com.example.theone.features.sequencer.StepSequencerScreen
import com.example.theone.features.debug.DebugScreen // Added import for DebugScreen
import com.high.theone.ui.theme.TheOneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context // Ensure this is present

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine

    // Define copyAssetToCache here
    private fun copyAssetToCache(context: Context, assetName: String, cacheFileName: String): String? {
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open(assetName)
            val cacheDir = context.cacheDir
            val outFile = File(cacheDir, cacheFileName)
            // Ensure parent directory exists, though cacheDir should exist
            // outFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(outFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            Log.i("MainActivity", "Copied asset '$assetName' to '${outFile.absolutePath}'")
            return outFile.absolutePath
        } catch (e: IOException) {
            Log.e("MainActivity", "Failed to copy asset $assetName to cache: ${e.message}", e)
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioEngine = AudioEngine(this)

        Log.i("MainActivity", "Launching coroutine for AudioEngine initialization...")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val initResult = audioEngine.initialize(sampleRate = 48000, bufferSize = 256, enableLowLatency = true)
                Log.i("MainActivity", "AudioEngine.initialize() returned: $initResult")
                if (initResult) {
                    Log.i("MainActivity", "AudioEngine isInitialized after init: ${audioEngine.isInitialized()}")
                    Log.i("MainActivity", "AudioEngine Latency: ${audioEngine.getReportedLatencyMillis()} ms")
                    // Test logic has been moved to DebugScreen via SequencerViewModel.
                    // The MainActivity onCreate should only handle essential setup.
                    Log.i("MainActivity", "AudioEngine initialized. Test logic can be triggered from DebugScreen.")
                } else {
                    Log.e("MainActivity", "AudioEngine initialization failed.")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception during AudioEngine initialization or tests: ${e.message}", e)
            }
        }

        setContent {
            TheOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // AppNavigation() // Call your new NavHost composable
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main_screen") {
                        composable("main_screen") {
                            MainScreen(navController = navController)
                        }
                        composable("step_sequencer_screen") {
                            StepSequencerScreen(navController = navController) // Pass NavController
                        }
                        composable("debug_screen") {
                            DebugScreen()
                        }
                        composable("drum_pad_screen") { // Added route for DrumPadScreen
                            com.example.theone.features.drumtrack.ui.DrumPadScreen( // Use fully qualified name
                                drumTrackViewModel = androidx.hilt.navigation.compose.hiltViewModel<com.example.theone.features.drumtrack.DrumTrackViewModel>()
                            )
                        }
                        // Add other destinations here if needed
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "onDestroy called, preparing to shutdown AudioEngine.")
        if (::audioEngine.isInitialized) {
             CoroutineScope(Dispatchers.IO).launch {
                audioEngine.shutdown()
                Log.i("MainActivity", "AudioEngine shutdown complete. IsInitialized: ${audioEngine.isInitialized()}")
            }
        }
    }
}

// New Composable for the main screen (can be in the same file or separate)
@Composable
fun MainScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to The One!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { navController.navigate("step_sequencer_screen") }) {
            Text("Go to Step Sequencer")
        }
        Spacer(modifier = Modifier.height(16.dp)) // Added spacer
        Button(onClick = { navController.navigate("debug_screen") }) { // Added button for DebugScreen
            Text("Go to Debug Screen")
        }
        // Add other navigation buttons here later
    }
}

// Remove or comment out Greeting and GreetingPreview
/*
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) { ... }
@Preview(showBackground = true)
@Composable
fun GreetingPreview() { ... }
*/