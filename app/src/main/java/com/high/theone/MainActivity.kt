package com.high.theone

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.debug.DebugScreen
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.drumtrack.ui.DrumPadScreen
import com.high.theone.features.sequencer.SequencerScreen
import com.high.theone.features.sequencer.SequencerHelpScreen
import com.high.theone.features.sequencer.SequencerTutorialScreen
import com.high.theone.ui.theme.TheOneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioEngine: AudioEngineControl

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
                    
                    NavHost(navController = navController, startDestination = "drum_pad_screen") {
                        composable("main_screen") {
                            MainScreen(navController = navController)
                        }
                        composable("step_sequencer_screen") {
                            SequencerScreen(navController = navController)
                        }
                        composable("sequencer_settings") {
                            // Temporarily disabled - settings screen moved
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Settings coming soon")
                            }
                        }
                        composable("sequencer_help") {
                            SequencerHelpScreen(navController = navController)
                        }
                        composable("sequencer_tutorial") {
                            SequencerTutorialScreen(navController = navController)
                        }
                        composable("debug_screen") {
                            DebugScreen(audioEngine = audioEngine)
                        }
                        composable("drum_pad_screen") {
                            DrumPadScreen(
                                drumTrackViewModel = hiltViewModel<DrumTrackViewModel>(),
                                navController = navController
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
        CoroutineScope(Dispatchers.IO).launch {
            audioEngine.shutdown()
            Log.i("MainActivity", "AudioEngine shutdown complete.")
        }
    }
}

@Composable
fun MainScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
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