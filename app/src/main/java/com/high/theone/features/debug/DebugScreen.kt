package com.high.theone.features.debug

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
    audioEngine: AudioEngineControl
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isEngineInitialized by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf("No tests run yet") }
    var loadedSamples by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    testResults = "Loading file: ${uri.lastPathSegment}..."
                    
                    // Generate a unique sample ID
                    val sampleId = "file_${System.currentTimeMillis()}"
                    val filePath = uri.toString()
                    
                    withContext(Dispatchers.IO) {
                        val success = audioEngine.loadSampleToMemory(sampleId, filePath)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                loadedSamples = loadedSamples + sampleId
                                testResults = "✓ Successfully loaded: ${uri.lastPathSegment}\nSample ID: $sampleId"
                            } else {
                                testResults = "✗ Failed to load: ${uri.lastPathSegment}"
                            }
                        }
                    }
                    Log.i("DebugScreen", "File load result: $testResults")
                } catch (e: Exception) {
                    testResults = "✗ File load error: ${e.message}"
                    Log.e("DebugScreen", "File load failed", e)
                }
            }
        }
    }
    
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
                                        audioEngine.initialize(44100, 256, true)
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
                                        audioEngine.shutdown()
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
                                    val success = audioEngine.createAndTriggerTestSample()
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
                                    val success = audioEngine.loadTestSample()
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
                                        audioEngine.triggerTestPadSample(0)
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
                                        audioEngine.triggerTestPadSample(1)
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
                                    val latency = audioEngine.getOboeReportedLatencyMillis()
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
                }            }
        }
        
        // 🎛️ AVST Plugin Testing Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("🎛️ AVST Plugin System", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Plugin states
                var loadedPlugins by remember { mutableStateOf<List<String>>(emptyList()) }
                var sketchingSynthLoaded by remember { mutableStateOf(false) }
                
                // Load SketchingSynth Plugin
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                testResults = "Loading SketchingSynth plugin..."
                                  withContext(Dispatchers.IO) {                                    val success = audioEngine.loadPlugin("sketchingsynth", "SketchingSynth")
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            sketchingSynthLoaded = true
                                            testResults = "✓ SketchingSynth plugin loaded successfully!\n" +
                                                    "The world's first AVST plugin is ready to rock!"
                                            // Refresh loaded plugins list
                                            scope.launch {
                                                loadedPlugins = audioEngine.getLoadedPlugins()
                                            }
                                        } else {
                                            testResults = "✗ Failed to load SketchingSynth plugin"
                                        }
                                    }
                                }
                                Log.i("DebugScreen", "Plugin load result: $testResults")
                            } catch (e: Exception) {
                                testResults = "✗ Plugin load error: ${e.message}"
                                Log.e("DebugScreen", "Plugin load failed", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sketchingSynthLoaded
                ) {
                    Text("🎹 Load SketchingSynth Plugin")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Piano keys for testing the synth
                if (sketchingSynthLoaded) {
                    Text("🎹 Test Piano", style = MaterialTheme.typography.titleMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72) // C, D, E, F, G, A, B, C
                        val noteNames = listOf("C", "D", "E", "F", "G", "A", "B", "C")
                        
                        notes.forEachIndexed { index, midiNote ->
                            Button(
                                onClick = {                                    scope.launch {
                                        try {
                                            audioEngine.noteOnToPlugin("sketchingsynth", midiNote, 100)
                                            
                                            // Note off after 500ms
                                            kotlinx.coroutines.delay(500)
                                            audioEngine.noteOffToPlugin("sketchingsynth", midiNote, 100)
                                            
                                            testResults = "🎵 Played note ${noteNames[index]} (MIDI $midiNote)"
                                        } catch (e: Exception) {
                                            testResults = "✗ Note trigger error: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(noteNames[index])
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Parameter controls
                    Text("🎛️ Plugin Parameters", style = MaterialTheme.typography.titleMedium)
                    
                    // Volume control
                    var volume by remember { mutableStateOf(0.5f) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volume: ", modifier = Modifier.width(60.dp))
                        Slider(
                            value = volume,                            onValueChange = { newVolume ->
                                volume = newVolume
                                scope.launch {
                                    try {
                                        audioEngine.setPluginParameter("sketchingsynth", "master_volume", newVolume.toDouble())
                                    } catch (e: Exception) {
                                        Log.e("DebugScreen", "Parameter set failed", e)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text("%.2f".format(volume), modifier = Modifier.width(40.dp))
                    }
                      // Cutoff frequency control
                    var cutoff by remember { mutableStateOf(0.5f) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cutoff: ", modifier = Modifier.width(60.dp))
                        Slider(
                            value = cutoff,
                            onValueChange = { newCutoff ->
                                cutoff = newCutoff
                                scope.launch {                                    try {
                                        // Convert 0.0-1.0 slider to 20Hz-8000Hz range (logarithmic)
                                        val cutoffHz = 20.0 + (8000.0 - 20.0) * newCutoff.toDouble()
                                        audioEngine.setPluginParameter("sketchingsynth", "filter_cutoff", cutoffHz)
                                    } catch (e: Exception) {
                                        Log.e("DebugScreen", "Parameter set failed", e)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text("%.0fHz".format(20 + (8000 - 20) * cutoff), modifier = Modifier.width(60.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Unload plugin
                    Button(
                        onClick = {
                            scope.launch {
                                try {                                    val success = audioEngine.unloadPlugin("sketchingsynth")
                                    if (success) {
                                        sketchingSynthLoaded = false
                                        loadedPlugins = emptyList()
                                        testResults = "✓ SketchingSynth plugin unloaded"
                                    } else {
                                        testResults = "✗ Failed to unload plugin"
                                    }
                                } catch (e: Exception) {
                                    testResults = "✗ Plugin unload error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🗑️ Unload Plugin")
                    }
                }
                
                // Display loaded plugins
                if (loadedPlugins.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loaded Plugins:", style = MaterialTheme.typography.titleMedium)
                    loadedPlugins.forEach { pluginId ->
                        Text("• $pluginId", style = MaterialTheme.typography.bodyMedium)
                    }
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
            ) {                Text("Instructions", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
                        🎵 REAL AUDIO FILE TESTING:
                        1. First, initialize the audio engine
                        2. Use 'Pick & Load Audio File' to load real WAV/MP3 files
                        3. Play loaded samples using the ▶️ button
                        4. Unload samples using the 🗑️ button
                        5. Use 'Stop All' to halt all playback
                        
                        🧪 SYNTHETIC SAMPLE TESTING:
                        6. Try 'Create & Play Test Sample' for end-to-end testing
                        7. Use 'Load Test Sample to Memory' to preload samples
                        8. Use 'Trigger Pad' buttons to test sample playback
                        
                        📊 SYSTEM DIAGNOSTICS:
                        9. Check audio latency for performance info
                        
                        The test sample is a synthetic drum sound generated in C++.
                        Real files are loaded using dr_wav with full format support.
                        Listen for audio output when triggering any samples!
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        // File Loading & Management
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Real Audio File Loading", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        filePickerLauncher.launch("audio/*")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁 Pick & Load Audio File")
                }
                
                if (loadedSamples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loaded Samples:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(loadedSamples) { sampleId ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sampleId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        testResults = "Playing sample: $sampleId"
                                                        
                                                        withContext(Dispatchers.IO) {
                                                            audioEngine.triggerSample(sampleId, 1.0f, 0.0f)
                                                            withContext(Dispatchers.Main) {
                                                                testResults = "✓ Triggered sample: $sampleId"
                                                            }
                                                        }
                                                        Log.i("DebugScreen", "Triggered sample: $sampleId")
                                                    } catch (e: Exception) {
                                                        testResults = "✗ Play error: ${e.message}"
                                                        Log.e("DebugScreen", "Sample trigger failed", e)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            audioEngine.unloadSample(sampleId)
                                                            withContext(Dispatchers.Main) {
                                                                loadedSamples = loadedSamples.filter { it != sampleId }
                                                                testResults = "✓ Unloaded sample: $sampleId"
                                                            }
                                                        }
                                                        Log.i("DebugScreen", "Unloaded sample: $sampleId")
                                                    } catch (e: Exception) {
                                                        testResults = "✗ Unload error: ${e.message}"
                                                        Log.e("DebugScreen", "Sample unload failed", e)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Unload")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        audioEngine.stopAllSamples()
                                        withContext(Dispatchers.Main) {
                                            testResults = "✓ Stopped all playing samples"
                                        }
                                    }
                                    Log.i("DebugScreen", "Stopped all samples")
                                } catch (e: Exception) {
                                    testResults = "✗ Stop all error: ${e.message}"
                                    Log.e("DebugScreen", "Stop all failed", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🛑 Stop All Playing Samples")
                    }
                }
            }
        }
        
        // Asset Loading Tests
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Asset Loading Tests", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {                                testResults = "Setting up asset manager..."
                                
                                withContext(Dispatchers.IO) {
                                    // Set the asset manager first
                                    val assetManagerSet = audioEngine.setAssetManager(context.assets)
                                    if (!assetManagerSet) {
                                        withContext(Dispatchers.Main) {
                                            testResults = "✗ Failed to set asset manager"
                                        }
                                        return@withContext
                                    }
                                    
                                    // Test loading an asset from the drum kit
                                    val sampleId = "test_kick_${System.currentTimeMillis()}"
                                    val assetPath = "asset://drum_kits/hip_hop_legends/kick_vinyl.wav"
                                    
                                    val success = audioEngine.loadSampleToMemory(sampleId, assetPath)
                                    
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            loadedSamples = loadedSamples + sampleId
                                            testResults = "✓ Successfully loaded asset: kick_vinyl.wav\nSample ID: $sampleId"
                                        } else {
                                            testResults = "✗ Failed to load asset: kick_vinyl.wav"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                testResults = "✗ Asset load error: ${e.message}"
                                Log.e("DebugScreen", "Asset load failed", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎵 Load Test Asset (Hip Hop Kick)")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {                            testResults = "Loading multiple assets..."
                            
                            withContext(Dispatchers.IO) {
                                // Ensure asset manager is set
                                val assetManagerSet = audioEngine.setAssetManager(context.assets)
                                if (!assetManagerSet) {
                                    withContext(Dispatchers.Main) {
                                        testResults = "✗ Failed to set asset manager"
                                    }
                                    return@withContext
                                }
                                
                                val assetsToLoad = listOf(
                                    "kick.wav",
                                    "snare.wav", 
                                    "hat.wav",
                                    "clap.wav"
                                )
                                
                                val results = mutableListOf<String>()
                                assetsToLoad.forEach { asset ->
                                    val sampleId = "${asset.replace(".wav", "")}_${System.currentTimeMillis()}"
                                    val assetPath = "asset://$asset"
                                    
                                    val success = audioEngine.loadSampleToMemory(sampleId, assetPath)
                                    if (success) {
                                        loadedSamples = loadedSamples + sampleId
                                        results.add("✓ $asset")
                                    } else {
                                        results.add("✗ $asset")
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    testResults = "Asset Loading Results:\n" + results.joinToString("\n")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🥁 Load Basic Drum Samples")
                }
            }
        }
        
        // Sample Playback Tests
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Sample Playback Tests", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (loadedSamples.isNotEmpty()) {
                    Text("Loaded Samples:", style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(
                        modifier = Modifier.height(120.dp)
                    ) {
                        items(loadedSamples) { sampleId ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sampleId,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = {                                            scope.launch {
                                                try {
                                                    testResults = "Playing sample: $sampleId"
                                                    audioEngine.triggerSample(sampleId, 1.0f, 0.0f)
                                                    testResults = "✓ Played sample: $sampleId"
                                                } catch (e: Exception) {
                                                    testResults = "✗ Playback error: ${e.message}"
                                                    Log.e("DebugScreen", "Sample playback failed", e)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    audioEngine.unloadSample(sampleId)
                                                    loadedSamples = loadedSamples - sampleId
                                                    testResults = "✓ Unloaded sample: $sampleId"
                                                } catch (e: Exception) {
                                                    testResults = "✗ Unload error: ${e.message}"
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Unload")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("No samples loaded", style = MaterialTheme.typography.bodyMedium)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                audioEngine.stopAllNotes(null, true)
                                testResults = "✓ Stopped all playing samples"
                            } catch (e: Exception) {
                                testResults = "✗ Stop all error: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🛑 Stop All Samples")
                }
            }
        }
    }
}
