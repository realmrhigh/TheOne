package com.high.theone

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.high.theone.audio.AudioEngineControl
import com.high.theone.midi.service.MidiSystemInitializer
import com.high.theone.midi.service.MidiPermissionManager
import dagger.hilt.android.HiltAndroidApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class TheOneApplication : Application(), DefaultLifecycleObserver {

    @Inject
    lateinit var audioEngine: AudioEngineControl
    
    @Inject
    lateinit var midiSystemInitializer: MidiSystemInitializer
    
    @Inject
    lateinit var midiPermissionManager: MidiPermissionManager

    // Application-level coroutine scope with proper lifecycle management
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Track initialization state
    private var isAudioEngineInitialized = false
    private var isMidiSystemInitialized = false

    override fun onCreate() {
        super<Application>.onCreate()
        
        // Register for process lifecycle events
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        Log.i("TheOneApplication", "Starting TheOne application initialization...")
        
        // Initialize systems in background
        applicationScope.launch {
            initializeSystems()
        }
    }
    
    /**
     * Initialize all core systems with proper error handling and coordination
     */
    private suspend fun initializeSystems() {
        try {
            // Step 1: Initialize Audio Engine first (MIDI depends on it)
            initializeAudioEngine()
            
            // Step 2: Initialize MIDI system (can work without permissions initially)
            initializeMidiSystem()
            
            Log.i("TheOneApplication", "System initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e("TheOneApplication", "Critical error during system initialization", e)
            // Continue running - some features may be unavailable but app should still work
        }
    }
    
    /**
     * Initialize the audio engine with proper error handling
     */
    private suspend fun initializeAudioEngine() {
        try {
            Log.i("TheOneApplication", "Initializing AudioEngine...")
            
            val initResult = audioEngine.initialize(
                sampleRate = 48000,
                bufferSize = 256,
                enableLowLatency = true
            )
            
            if (initResult) {
                isAudioEngineInitialized = true
                Log.i("TheOneApplication", "AudioEngine initialized successfully. Latency: ${audioEngine.getReportedLatencyMillis()} ms")
            } else {
                Log.e("TheOneApplication", "AudioEngine initialization failed - audio features will be unavailable")
            }
            
        } catch (e: Exception) {
            Log.e("TheOneApplication", "Exception during AudioEngine initialization", e)
        }
    }
    
    /**
     * Initialize the MIDI system with proper coordination
     */
    private suspend fun initializeMidiSystem() {
        try {
            Log.i("TheOneApplication", "Initializing MIDI system...")
            
            // Check MIDI support first
            if (!midiPermissionManager.hasMidiSupport()) {
                Log.i("TheOneApplication", "MIDI not supported on this device - skipping MIDI initialization")
                return
            }
            
            // Initialize MIDI system (will handle permissions internally)
            val midiResult = midiSystemInitializer.initializeSystem()
            
            if (midiResult.isSuccess) {
                isMidiSystemInitialized = true
                Log.i("TheOneApplication", "MIDI system initialized successfully")
            } else {
                val error = midiResult.exceptionOrNull()
                Log.w("TheOneApplication", "MIDI system initialization incomplete: ${error?.message}")
                Log.i("TheOneApplication", "App will continue to function without MIDI support")
            }
            
        } catch (e: Exception) {
            Log.e("TheOneApplication", "Exception during MIDI system initialization", e)
        }
    }
    
    // Process Lifecycle callbacks
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d("TheOneApplication", "App process started")
        
        // Resume systems if needed
        applicationScope.launch {
            if (!isAudioEngineInitialized) {
                initializeAudioEngine()
            }
            if (!isMidiSystemInitialized && midiPermissionManager.hasMidiSupport()) {
                initializeMidiSystem()
            }
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d("TheOneApplication", "App process stopped")
        
        // Systems will pause themselves through their own lifecycle observers
        // No need to explicitly pause here
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.i("TheOneApplication", "App process destroyed - shutting down systems")
        
        // Shutdown systems in reverse order
        applicationScope.launch {
            shutdownSystems()
        }
    }
    
    /**
     * Shutdown all systems in proper order with timeout protection
     */
    private suspend fun shutdownSystems() {
        try {
            // Use timeout to prevent hanging during shutdown
            withTimeout(10000) { // 10 second timeout
                
                // Shutdown MIDI system first
                if (isMidiSystemInitialized) {
                    Log.i("TheOneApplication", "Shutting down MIDI system...")
                    val midiShutdown = midiSystemInitializer.shutdownSystem()
                    if (midiShutdown.isSuccess) {
                        Log.i("TheOneApplication", "MIDI system shutdown complete")
                    } else {
                        Log.w("TheOneApplication", "MIDI system shutdown had issues: ${midiShutdown.exceptionOrNull()?.message}")
                    }
                    isMidiSystemInitialized = false
                }
                
                // Shutdown audio engine last
                if (isAudioEngineInitialized) {
                    Log.i("TheOneApplication", "Shutting down AudioEngine...")
                    audioEngine.shutdown()
                    Log.i("TheOneApplication", "AudioEngine shutdown complete")
                    isAudioEngineInitialized = false
                }
            }
            
        } catch (e: TimeoutCancellationException) {
            Log.w("TheOneApplication", "System shutdown timed out - forcing completion")
        } catch (e: Exception) {
            Log.e("TheOneApplication", "Error during system shutdown", e)
        } finally {
            // Cancel application scope
            applicationScope.cancel()
            Log.i("TheOneApplication", "Application shutdown complete")
        }
    }
    
    /**
     * Get system status for debugging/monitoring
     */
    fun getSystemStatus(): SystemStatus {
        return SystemStatus(
            isAudioEngineInitialized = isAudioEngineInitialized,
            isMidiSystemInitialized = isMidiSystemInitialized,
            midiSystemStatus = if (::midiSystemInitializer.isInitialized) {
                midiSystemInitializer.getSystemStatus()
            } else null
        )
    }
    
    /**
     * Handle permission results from activities
     */
    fun onMidiPermissionsResult(granted: Boolean) {
        if (granted) {
            Log.i("TheOneApplication", "MIDI permissions granted - continuing initialization")
            applicationScope.launch {
                midiSystemInitializer.onPermissionsGranted()
            }
        } else {
            Log.w("TheOneApplication", "MIDI permissions denied - MIDI features will be unavailable")
        }
    }
}

/**
 * System status for monitoring and debugging
 */
data class SystemStatus(
    val isAudioEngineInitialized: Boolean,
    val isMidiSystemInitialized: Boolean,
    val midiSystemStatus: com.high.theone.midi.service.MidiSystemStatus?
)

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
}
