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
import dagger.hilt.android.AndroidEntryPoint // Added Hilt import

@AndroidEntryPoint // Added Hilt annotation
class MainActivity : ComponentActivity() {

    // private lateinit var audioEngine: AudioEngine // Removed manual instantiation and property
    @Inject lateinit var audioEngine: AudioEngineControl // Injected for onDestroy, though direct use in Activity is not ideal

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

        // audioEngine = AudioEngine(this) // Removed manual instantiation

        // AudioEngine initialization should be triggered by a component that needs it,
        // e.g., a ViewModel, or a dedicated startup service/initializer.
        // For now, removing the direct initialization call from MainActivity.onCreate.
        // If no other component initializes it, it won't start.
        // Consider adding an Application-level initialization or ViewModel-triggered init.
        Log.i("MainActivity", "AudioEngine will be initialized by Hilt-injected components as needed.")

        // The CoroutineScope.launch for initialization and tests is removed.
        // Test logic is now in DebugScreen -> SequencerViewModel.

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
        // Check if audioEngine has been injected and then if it's initialized before shutting down
        if (this::audioEngine.isInitialized && audioEngine.isInitialized()) {
             CoroutineScope(Dispatchers.IO).launch { // Still okay to launch a short scope for shutdown
                audioEngine.shutdown()
                Log.i("MainActivity", "AudioEngine shutdown complete. IsInitialized after shutdown: ${audioEngine.isInitialized()}")
            }
        } else {
            Log.w("MainActivity", "AudioEngine was not initialized or not injected, skip shutdown.")
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