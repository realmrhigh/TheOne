package com.high.theone.features.debug

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.high.theone.audio.AudioEngineControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DebugScreen(
    audioEngineControl: AudioEngineControl
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isEngineInitialized by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf("No tests run yet") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Audio Engine Debug & Test",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Divider()
        
        // Engine Status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Engine Status", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Initialized: $isEngineInitialized")
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {                                    withContext(Dispatchers.IO) {
                                        audioEngineControl.initialize(44100, 256, true)
                                    }
                                    isEngineInitialized = true
                                    testResults = "Engine initialized successfully"
                                    Log.i("DebugScreen", "Audio engine initialized")
                                } catch (e: Exception) {
                                    testResults = "Engine initialization failed: ${e.message}"
                                    Log.e("DebugScreen", "Failed to initialize engine", e)
                                }
                            }
                        }
                    ) {
                        Text("Initialize Engine")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        audioEngineControl.shutdown()
                                    }
                                    isEngineInitialized = false
                                    testResults = "Engine shutdown successfully"
                                    Log.i("DebugScreen", "Audio engine shutdown")
                                } catch (e: Exception) {
                                    testResults = "Engine shutdown failed: ${e.message}"
                                    Log.e("DebugScreen", "Failed to shutdown engine", e)
                                }
                            }
                        }
                    ) {
                        Text("Shutdown Engine")
                    }
                }
            }
        }
        
        // Sample Testing
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Sample Testing", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                testResults = "Creating and triggering test sample..."
                                
                                withContext(Dispatchers.IO) {
                                    val success = audioEngineControl.createAndTriggerTestSample()
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            testResults = "✓ Test sample created and triggered successfully!\n" +
                                                    "You should hear a synthetic drum sound."
                                        } else {
                                            testResults = "✗ Failed to create/trigger test sample"
                                        }
                                    }
                                }
                                Log.i("DebugScreen", "Test sample result: $testResults")
                            } catch (e: Exception) {
                                testResults = "✗ Test sample error: ${e.message}"
                                Log.e("DebugScreen", "Test sample failed", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔊 Create & Play Test Sample")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                testResults = "Loading test sample to memory..."
                                
                                withContext(Dispatchers.IO) {
                                    val success = audioEngineControl.loadTestSample()
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            testResults = "✓ Test sample loaded to memory successfully!"
                                        } else {
                                            testResults = "✗ Failed to load test sample to memory"
                                        }
                                    }
                                }
                                Log.i("DebugScreen", "Load test sample result: $testResults")
                            } catch (e: Exception) {
                                testResults = "✗ Load test sample error: ${e.message}"
                                Log.e("DebugScreen", "Load test sample failed", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁 Load Test Sample to Memory")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    testResults = "Triggering pad sample 0..."
                                    
                                    withContext(Dispatchers.IO) {
                                        audioEngineControl.triggerTestPadSample(0)
                                        withContext(Dispatchers.Main) {
                                            testResults = "✓ Triggered pad sample 0"
                                        }
                                    }
                                    Log.i("DebugScreen", "Triggered pad sample 0")
                                } catch (e: Exception) {
                                    testResults = "✗ Trigger pad error: ${e.message}"
                                    Log.e("DebugScreen", "Trigger pad failed", e)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🥁 Trigger Pad 0")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    testResults = "Triggering pad sample 1..."
                                    
                                    withContext(Dispatchers.IO) {
                                        audioEngineControl.triggerTestPadSample(1)
                                        withContext(Dispatchers.Main) {
                                            testResults = "✓ Triggered pad sample 1"
                                        }
                                    }
                                    Log.i("DebugScreen", "Triggered pad sample 1")
                                } catch (e: Exception) {
                                    testResults = "✗ Trigger pad error: ${e.message}"
                                    Log.e("DebugScreen", "Trigger pad failed", e)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🥁 Trigger Pad 1")
                    }
                }
            }
        }
        
        // Audio System Info
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Audio System Info", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val latency = audioEngineControl.getOboeReportedLatencyMillis()
                                    withContext(Dispatchers.Main) {
                                        testResults = "Oboe reported latency: ${latency}ms"
                                    }
                                }
                                Log.i("DebugScreen", "Latency check: $testResults")
                            } catch (e: Exception) {
                                testResults = "✗ Latency check error: ${e.message}"
                                Log.e("DebugScreen", "Latency check failed", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📊 Check Audio Latency")
                }
            }
        }
        
        // Test Results
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Test Results", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testResults,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Instructions", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                        1. First, initialize the audio engine
                        2. Try 'Create & Play Test Sample' for end-to-end testing
                        3. Use 'Load Test Sample to Memory' to preload samples
                        4. Use 'Trigger Pad' buttons to test sample playback
                        5. Check audio latency for performance info
                        
                        The test sample is a synthetic drum sound generated in C++.
                        Listen for audio output when triggering samples.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
