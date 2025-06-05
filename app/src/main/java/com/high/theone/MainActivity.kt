package com.high.theone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.high.theone.ui.theme.TheOneTheme
import com.example.theone.audio.AudioEngine
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

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.theone.features.sampleeditor.SampleEditScreen
import com.example.theone.features.sampleeditor.SampleEditViewModelFactory
import com.example.theone.model.SampleMetadata
import com.example.theone.domain.ProjectManager
import java.util.UUID
// Mock implementations for demonstration - replace with actual instances
import com.example.theone.features.sampleeditor.MockAudioEngineControl
import com.example.theone.features.sampleeditor.MockProjectManager

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

        audioEngine = AudioEngine()

        Log.i("MainActivity", "Launching coroutine for AudioEngine initialization...")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val initResult = audioEngine.initialize(sampleRate = 48000, bufferSize = 256, enableLowLatency = true)
                android.util.Log.i("MainActivity", "AudioEngine.initialize() returned: $initResult")
                if (initResult) {
                    android.util.Log.i("MainActivity", "AudioEngine isInitialized after init: ${audioEngine.isInitialized()}")
                    android.util.Log.i("MainActivity", "AudioEngine Latency: ${audioEngine.getReportedLatencyMillis()} ms")

                    val primaryClickId = "__METRONOME_PRIMARY__"
                    val secondaryClickId = "__METRONOME_SECONDARY__"
                    val primaryAssetName = "click_primary.wav"
                    val secondaryAssetName = "click_secondary.wav"

                    android.util.Log.i("MainActivity", "Pre-loading primary metronome click sound...")
                    // Reinstate actual call to copyAssetToCache
                    val primaryCachedPath = copyAssetToCache(applicationContext, primaryAssetName, "cached_click_primary.wav")
                    if (primaryCachedPath != null) {
                        val primaryFileUri = "file://$primaryCachedPath"
                        val primaryLoadSuccess = audioEngine.loadSampleToMemory(applicationContext, primaryClickId, primaryFileUri)
                        android.util.Log.i("MainActivity", "Loading $primaryClickId ($primaryAssetName) result: $primaryLoadSuccess. URI: $primaryFileUri")
                        if (!primaryLoadSuccess) android.util.Log.e("MainActivity", "Failed to load $primaryClickId")
                    } else {
                        android.util.Log.e("MainActivity", "Failed to copy $primaryAssetName to cache.")
                    }

                    android.util.Log.i("MainActivity", "Pre-loading secondary metronome click sound...")
                    // Reinstate actual call to copyAssetToCache
                    val secondaryCachedPath = copyAssetToCache(applicationContext, secondaryAssetName, "cached_click_secondary.wav")
                    if (secondaryCachedPath != null) {
                        val secondaryFileUri = "file://$secondaryCachedPath"
                        val secondaryLoadSuccess = audioEngine.loadSampleToMemory(applicationContext, secondaryClickId, secondaryFileUri)
                        android.util.Log.i("MainActivity", "Loading $secondaryClickId ($secondaryAssetName) result: $secondaryLoadSuccess. URI: $secondaryFileUri")
                        if (!secondaryLoadSuccess) android.util.Log.e("MainActivity", "Failed to load $secondaryClickId")
                    } else {
                        android.util.Log.e("MainActivity", "Failed to copy $secondaryAssetName to cache.")
                    }

                    Log.i("MainActivity", "Metronome sounds loading attempted. Proceeding to test metronome.")
                    // ... (rest of the metronome and recording tests as they were, they already use the correct logic) ...
                    audioEngine.setMetronomeVolume(0.8f)
                    Log.i("MainActivity", "Enabling metronome at 120 BPM, 4/4.")
                    audioEngine.setMetronomeState( true, 120.0f, 4, 4, primaryClickId, secondaryClickId )
                    delay(5000)
                    Log.i("MainActivity", "Changing metronome BPM to 180.")
                    audioEngine.setMetronomeState( true, 180.0f, 4, 4, primaryClickId, secondaryClickId )
                    delay(5000)
                    Log.i("MainActivity", "Changing metronome time signature to 3/4, BPM 120.")
                    audioEngine.setMetronomeState( true, 120.0f, 3, 4, primaryClickId, secondaryClickId )
                    delay(5000)
                    Log.i("MainActivity", "Disabling metronome.")
                    audioEngine.setMetronomeState( false, 120.0f, 3, 4, primaryClickId, secondaryClickId )
                    delay(2000)
                    Log.i("MainActivity", "Re-enabling metronome at 60 BPM, 4/4.")
                    audioEngine.setMetronomeState( true, 60.0f, 4, 4, primaryClickId, secondaryClickId )

                    Log.i("MainActivity", "--- Starting Audio Recording Test ---")

                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("MainActivity", "RECORD_AUDIO permission not granted. Skipping recording test.")
                    } else {
                        Log.i("MainActivity", "RECORD_AUDIO permission granted.")
                        val recordFileName = "test_recording.wav"
                        val recordFile = File(cacheDir, recordFileName)
                        val recordFileUriString = "file://${recordFile.absolutePath}"
                        val recordingSampleRate = 48000
                        val recordingChannels = 1

                        Log.i("MainActivity", "Attempting to start recording to: $recordFileUriString")
                        val startSuccess = audioEngine.startAudioRecording( applicationContext, recordFileUriString, recordingSampleRate, recordingChannels )
                        Log.i("MainActivity", "startAudioRecording result: $startSuccess")

                        if (startSuccess) {
                            Log.i("MainActivity", "Recording started. Will record for ~5 seconds.")
                            var peakSum = 0.0f
                            var peakCount = 0
                            for (i in 1..50) { delay(100); val isActive = audioEngine.isRecordingActive(); val peak = audioEngine.getRecordingLevelPeak(); peakSum += peak; peakCount++; Log.d("MainActivity", "Recording active: $isActive, Current Peak: $peak"); if (!isActive && i < 40) { Log.e("MainActivity", "Recording stopped unexpectedly earlier than 5s loop."); break } }
                            if (peakCount > 0 && peakSum == 0.0f && audioEngine.isRecordingActive()) { Log.w("MainActivity", "Warning: Recording seems active but peak levels are consistently 0.0. Check microphone input / emulator settings.") }
                            Log.i("MainActivity", "Stopping recording...")
                            val recordedSampleMetadata = audioEngine.stopAudioRecording()
                            if (recordedSampleMetadata != null) {
                                Log.i("MainActivity", "stopAudioRecording successful. Metadata: $recordedSampleMetadata")
                                Log.i("MainActivity", "Recorded file should be at: ${recordedSampleMetadata.filePathUri}")
                                val actualFile = File(recordFile.absolutePath)
                                if (actualFile.exists() && actualFile.length() > 0) {
                                    Log.i("MainActivity", "Verified: Recording file exists and is not empty. Size: ${actualFile.length()} bytes.")
                                    val playbackId = "playbackOfRecording"
                                    Log.i("MainActivity", "Attempting to load recorded sample: ${recordedSampleMetadata.filePathUri}")
                                    val loadRecordedSuccess = audioEngine.loadSampleToMemory( applicationContext, playbackId, recordedSampleMetadata.filePathUri )
                                    Log.i("MainActivity", "loadSampleToMemory for recorded sample result: $loadRecordedSuccess")
                                    if (loadRecordedSuccess) {
                                        val isPlaybackLoaded = audioEngine.isSampleLoaded(playbackId)
                                        Log.i("MainActivity", "isSampleLoaded($playbackId) after load: $isPlaybackLoaded")
                                        if (isPlaybackLoaded) {
                                            Log.i("MainActivity", "Playing back recorded sample...")
                                            val playRecordedSuccess = audioEngine.playSample(playbackId, "rec_instance_1", 1.0f, 0.0f)
                                            Log.i("MainActivity", "playSample for recorded sample result: $playRecordedSuccess")
                                            delay(recordedSampleMetadata.durationMs + 500)
                                            audioEngine.unloadSample(playbackId)
                                            Log.i("MainActivity", "Unloaded $playbackId")
                                        }
                                    } else { Log.e("MainActivity", "Failed to load the recorded sample for playback.") }
                                } else { Log.e("MainActivity", "Error: Recording file not found or is empty at ${recordFile.absolutePath}. Size: ${actualFile.length()}") }
                            } else { Log.e("MainActivity", "stopAudioRecording failed or returned null metadata.") }
                        } else { Log.e("MainActivity", "Failed to start recording. Check logs for native errors.") }
                    }
                    Log.i("MainActivity", "--- Audio Recording Test Finished ---")

                    android.util.Log.i("MainActivity", "Proceeding to test general sample loading (test.wav).")
                    val sampleId = "testSampleWav"
                    val generalAssetName = "test.wav"

                    // Reinstate actual call to copyAssetToCache
                    val cachedFilePathGeneral = copyAssetToCache(applicationContext, generalAssetName, "cached_test.wav")
                    if (cachedFilePathGeneral != null) {
                        val fileUriString = "file://$cachedFilePathGeneral"
                        android.util.Log.i("MainActivity", "Testing loadSampleToMemory with URI: $fileUriString for $sampleId")
                        val loadSuccess = audioEngine.loadSampleToMemory(applicationContext, sampleId, fileUriString)
                        android.util.Log.i("MainActivity", "loadSampleToMemory($sampleId) result: $loadSuccess")
                        // ... (rest of this test block as it was)
                         if (loadSuccess) {
                            val isLoaded = audioEngine.isSampleLoaded(sampleId)
                            android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after load: $isLoaded")
                            if (isLoaded) {
                                android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_1)...")
                                val play1Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_1", volume = 0.8f, pan = 0.0f)
                                android.util.Log.i("MainActivity", "playSample($sampleId, instance_1) result: $play1Success")
                                delay(500)
                                android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_2) panned left...")
                                val play2Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_2", volume = 0.7f, pan = -0.8f)
                                android.util.Log.i("MainActivity", "playSample($sampleId, instance_2) result: $play2Success")
                                delay(500)
                                android.util.Log.i("MainActivity", "Attempting to play sample $sampleId (instance_3) panned right...")
                                val play3Success = audioEngine.playSample(sampleId = sampleId, noteInstanceId = "instance_3", volume = 0.7f, pan = 0.8f)
                                android.util.Log.i("MainActivity", "playSample($sampleId, instance_3) result: $play3Success")
                            } else { android.util.Log.e("MainActivity", "Sample $sampleId was reported as not loaded after load attempt. Cannot play.") }
                            delay(2000)
                            audioEngine.unloadSample(sampleId)
                            android.util.Log.i("MainActivity", "unloadSample($sampleId) called.")
                            val isLoadedAfterUnload = audioEngine.isSampleLoaded(sampleId)
                            android.util.Log.i("MainActivity", "isSampleLoaded($sampleId) after unload: $isLoadedAfterUnload")
                        } else { android.util.Log.e("MainActivity", "Failed to load sample $sampleId. Cannot test playback.") }
                    } else {
                        // Log specific to generalAssetName
                        android.util.Log.e("MainActivity", "Failed to copy asset $generalAssetName to cache. Cannot test sample loading.")
                    }

                } else {
                    android.util.Log.e("MainActivity", "AudioEngine initialization failed.")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Exception during AudioEngine initialization or tests: ${e.message}", e)
            }
        }

        setContent {
            // --- Temporary integration for SampleEditScreen ---
            var showSampleEditor by remember { mutableStateOf(false) }
            var sampleToEdit by remember { mutableStateOf<SampleMetadata?>(null) }

            // Mock instances - replace with actual singletons or DI
            val mockAudioEngine = remember { MockAudioEngineControl() } // C1
            val mockProjectManager = remember { MockProjectManager() } // C3

            if (showSampleEditor && sampleToEdit != null) {
                val factory = SampleEditViewModelFactory(mockAudioEngine, mockProjectManager, sampleToEdit!!)
                val sampleEditViewModel: SampleEditViewModel = viewModel(factory = factory)
                SampleEditScreen(viewModel = sampleEditViewModel)
            } else {

                    Column(modifier = Modifier.padding(16.dp)) {
                        // Original content of Surface should be here or wrapped in this else
                        // For example, if original was Greeting("Android", ...), it will be below.
                        // The following is ADDED:
                        Text("Main Activity Content (Original content below this button if any)")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            // Create a mock sample to edit
                            sampleToEdit = SampleMetadata(
                                id = UUID.randomUUID().toString(),
                                name = "Test Sample",
                                filePathUri = "/path/to/dummy.wav",
                                durationMs = 2000L,
                                sampleRate = 44100,
                                channels = 1,
                                trimStartMs = 0L,
                                trimEndMs = 2000L
                            )
                            showSampleEditor = true
                        }) {
                            Text("Edit Mock Sample")
                        }
                        // --- End of temporary integration ---
                        // Original content from setContent should follow here:
TheOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android", modifier = Modifier.padding(16.dp))
                }
            }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.i("MainActivity", "onDestroy called, preparing to shutdown AudioEngine.")
        if (::audioEngine.isInitialized) {
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