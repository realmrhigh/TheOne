package com.high.theone.midi.service

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.high.theone.midi.MidiManagerControl
import com.high.theone.midi.model.MidiSystemState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MIDI system lifecycle in coordination with Android app lifecycle.
 * Handles proper initialization, cleanup, and resource management.
 */
@Singleton
class MidiLifecycleManager @Inject constructor(
    private val context: Context,
    private val midiManager: MidiManagerControl
) : DefaultLifecycleObserver {

    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _lifecycleState = MutableStateFlow(MidiLifecycleState.UNINITIALIZED)
    val lifecycleState: StateFlow<MidiLifecycleState> = _lifecycleState.asStateFlow()
    
    private var initializationJob: Job? = null
    private var shutdownJob: Job? = null
    
    init {
        // Register with process lifecycle to handle app-wide events
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    /**
     * Initialize MIDI system with proper lifecycle management
     */
    suspend fun initializeMidiSystem(): Result<Unit> {
        return try {
            _lifecycleState.value = MidiLifecycleState.INITIALIZING
            
            // Cancel any existing initialization
            initializationJob?.cancel()
            
            initializationJob = lifecycleScope.launch {
                val result = midiManager.initialize()
                if (result) {
                    _lifecycleState.value = MidiLifecycleState.ACTIVE
                } else {
                    _lifecycleState.value = MidiLifecycleState.ERROR
                }
            }
            
            initializationJob?.join()
            
            if (_lifecycleState.value == MidiLifecycleState.ACTIVE) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("MIDI system initialization failed"))
            }
        } catch (e: Exception) {
            _lifecycleState.value = MidiLifecycleState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Shutdown MIDI system with proper cleanup
     */
    suspend fun shutdownMidiSystem(): Result<Unit> {
        return try {
            _lifecycleState.value = MidiLifecycleState.SHUTTING_DOWN
            
            // Cancel any existing shutdown
            shutdownJob?.cancel()
            
            shutdownJob = lifecycleScope.launch {
                midiManager.shutdown()
                _lifecycleState.value = MidiLifecycleState.SHUTDOWN
            }
            
            shutdownJob?.join()
            Result.success(Unit)
        } catch (e: Exception) {
            _lifecycleState.value = MidiLifecycleState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Restart MIDI system (shutdown then initialize)
     */
    suspend fun restartMidiSystem(): Result<Unit> {
        shutdownMidiSystem()
        return initializeMidiSystem()
    }
    
    // Lifecycle callbacks
    
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        // MIDI system will be initialized when needed, not automatically on create
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // Resume MIDI system if it was previously active
        if (_lifecycleState.value == MidiLifecycleState.PAUSED) {
            lifecycleScope.launch {
                initializeMidiSystem()
            }
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Pause MIDI system to conserve resources
        if (_lifecycleState.value == MidiLifecycleState.ACTIVE) {
            _lifecycleState.value = MidiLifecycleState.PAUSED
            lifecycleScope.launch {
                // Don't fully shutdown, just pause processing
                // This allows quick resume when app comes back to foreground
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Full shutdown when process is destroyed
        lifecycleScope.launch {
            shutdownMidiSystem()
        }
        lifecycleScope.cancel()
    }
    
    /**
     * Check if MIDI system is ready for use
     */
    fun isMidiSystemReady(): Boolean {
        return _lifecycleState.value == MidiLifecycleState.ACTIVE
    }
    
    /**
     * Get current lifecycle state
     */
    fun getCurrentState(): MidiLifecycleState {
        return _lifecycleState.value
    }
}

/**
 * Represents the lifecycle state of the MIDI system
 */
enum class MidiLifecycleState {
    UNINITIALIZED,
    INITIALIZING,
    ACTIVE,
    PAUSED,
    SHUTTING_DOWN,
    SHUTDOWN,
    ERROR
}