package com.high.theone

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.high.theone.features.midi.ui.MidiSettingsScreen
import com.high.theone.features.midi.ui.MidiMappingScreen
import com.high.theone.features.midi.ui.MidiMonitorScreen
import com.high.theone.features.sequencer.SequencerScreen
import com.high.theone.features.sequencer.SequencerHelpScreen
import com.high.theone.features.sequencer.SequencerTutorialScreen
import com.high.theone.midi.service.MidiPermissionManager
import com.high.theone.ui.theme.TheOneTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioEngine: AudioEngineControl
    
    @Inject
    lateinit var midiPermissionManager: MidiPermissionManager

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        handleMidiPermissionResult(allGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i("MainActivity", "MainActivity created - systems initialized by TheOneApplication")

        setContent {
            TheOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Monitor MIDI permission state and request if needed
                    val permissionState by midiPermissionManager.permissionState.collectAsState()
                    
                    LaunchedEffect(permissionState) {
                        // Request MIDI permissions if needed and supported
                        if (permissionState.hasMidiSupport && 
                            !permissionState.hasAllPermissions && 
                            permissionState.requiredPermissions.isNotEmpty()) {
                            
                            Log.i("MainActivity", "Requesting MIDI permissions: ${permissionState.requiredPermissions}")
                            permissionLauncher.launch(permissionState.requiredPermissions.toTypedArray())
                        }
                    }
                    
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
                        // MIDI screens
                        composable("midi_settings") {
                            MidiSettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("midi_mapping") {
                            MidiMappingScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("midi_monitor") {
                            MidiMonitorScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Handle MIDI permission request results
     */
    private fun handleMidiPermissionResult(allGranted: Boolean) {
        Log.i("MainActivity", "MIDI permission result: granted=$allGranted")
        
        // Update permission state
        midiPermissionManager.updatePermissionState()
        
        // Notify application about permission result
        (application as? TheOneApplication)?.onMidiPermissionsResult(allGranted)
        
        if (allGranted) {
            Log.i("MainActivity", "All MIDI permissions granted - MIDI features available")
        } else {
            Log.w("MainActivity", "Some MIDI permissions denied - MIDI features may be limited")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("MainActivity", "MainActivity destroyed - systems will be shutdown by TheOneApplication")
        // System shutdown is now handled by TheOneApplication lifecycle
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
        
        // Core features
        Button(onClick = { navController.navigate("step_sequencer_screen") }) {
            Text("Go to Step Sequencer")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("drum_pad_screen") }) {
            Text("Go to Drum Pads")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // MIDI features
        Button(onClick = { navController.navigate("midi_settings") }) {
            Text("MIDI Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("midi_mapping") }) {
            Text("MIDI Mapping")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("midi_monitor") }) {
            Text("MIDI Monitor")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // Debug
        Button(onClick = { navController.navigate("debug_screen") }) {
            Text("Go to Debug Screen")
        }
    }
}