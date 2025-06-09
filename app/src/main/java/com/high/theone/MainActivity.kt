package com.high.theone

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.theone.audio.AudioEngineControl
import com.example.theone.features.debug.DebugScreen
import com.example.theone.features.drumtrack.DrumTrackViewModel
import com.example.theone.features.drumtrack.ui.DrumPadScreen
import com.example.theone.features.sequencer.StepSequencerScreen
import com.high.theone.ui.theme.TheOneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioEngine: AudioEngineControl

    private fun copyAssetToCache(context: Context, assetName: String, cacheFileName: String): String? {
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open(assetName)
            val cacheDir = context.cacheDir
            val outFile = File(cacheDir, cacheFileName)
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

        Log.i("MainActivity", "AudioEngine will be initialized by Hilt-injected components as needed.")

        setContent {
            TheOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main_screen") {
                        composable("main_screen") {
                            MainScreen(navController = navController)
                        }
                        composable("step_sequencer_screen") {
                            StepSequencerScreen(navController = navController)
                        }
                        composable("debug_screen") {
                            DebugScreen()
                        }
                        composable("drum_pad_screen") {
                            DrumPadScreen(
                                drumTrackViewModel = hiltViewModel<DrumTrackViewModel>()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "onDestroy called, preparing to shutdown AudioEngine.")
        if (this::audioEngine.isInitialized && audioEngine.isInitialized()) {
            CoroutineScope(Dispatchers.IO).launch {
                audioEngine.shutdown()
                Log.i("MainActivity", "AudioEngine shutdown complete. IsInitialized after shutdown: ${audioEngine.isInitialized()}")
            }
        } else {
            Log.w("MainActivity", "AudioEngine was not initialized or not injected, skip shutdown.")
        }
    }
}

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
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("drum_pad_screen") }) {
            Text("Go to Drum Pads")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("debug_screen") }) {
            Text("Go to Debug Screen")
        }
    }
}