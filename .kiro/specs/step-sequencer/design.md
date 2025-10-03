# Step Sequencer Design Document

## Overview

The Step Sequencer feature transforms TheOne into a complete MPC-style beat-making machine by adding pattern programming capabilities to the existing sampling system. This design builds upon the established pad grid and audio engine architecture while introducing new components for timing, pattern management, and musical arrangement.

The sequencer follows the classic MPC workflow: users can program rhythmic patterns using a step-based interface, record patterns in real-time, apply swing and dynamics, and chain patterns together to create complete songs. The design prioritizes timing accuracy, intuitive mobile interaction, and seamless integration with the existing sampling infrastructure.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Sequencer UI Layer                       │
├─────────────────────────────────────────────────────────────┤
│  SequencerScreen │ PatternGrid │ TransportControls │ etc.   │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                 Sequencer Business Logic                    │
├─────────────────────────────────────────────────────────────┤
│  SequencerViewModel │ PatternManager │ TimingEngine │ etc.  │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                   Sequencer Domain Layer                    │
├─────────────────────────────────────────────────────────────┤
│  Pattern │ Step │ Song │ SequencerState │ TimingCalculator  │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│              Integration with Existing Systems              │
├─────────────────────────────────────────────────────────────┤
│    Audio Engine    │    Pad System    │   Sample Repository │
└─────────────────────────────────────────────────────────────┘
```

### Core Components Integration

The sequencer integrates with existing systems through well-defined interfaces:

- **Audio Engine Integration**: Uses existing `AudioEngine` for sample triggering with precise timing
- **Pad System Integration**: Leverages `PadState` and pad configuration for sample assignment
- **Sample Management**: Utilizes existing `SampleRepository` for sample loading and metadata
- **Project System**: Extends existing project structure to include sequencer data

## Components and Interfaces

### 1. Sequencer Core Models

#### Pattern Data Model
```kotlin
data class Pattern(
    val id: String,
    val name: String,
    val length: Int = 16, // 8, 16, 24, or 32 steps
    val steps: Map<Int, List<Step>>, // padIndex -> List of steps
    val tempo: Float = 120f,
    val swing: Float = 0f, // 0.0 to 0.75
    val createdAt: Long,
    val modifiedAt: Long
)

data class Step(
    val position: Int, // 0-31 for step position
    val velocity: Int = 100, // 1-127 MIDI velocity
    val isActive: Boolean = true,
    val microTiming: Float = 0f // Fine timing adjustment in milliseconds
)
```

#### Sequencer State Management
```kotlin
data class SequencerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isRecording: Boolean = false,
    val currentStep: Int = 0,
    val currentPattern: String? = null,
    val patterns: List<Pattern> = emptyList(),
    val songMode: SongMode? = null,
    val metronomeEnabled: Boolean = false,
    val quantization: Quantization = Quantization.SIXTEENTH
)

data class SongMode(
    val sequence: List<SongStep>,
    val currentSequencePosition: Int = 0,
    val isActive: Boolean = false
)

data class SongStep(
    val patternId: String,
    val repeatCount: Int = 1
)
```

### 2. Timing Engine

#### High-Precision Timing System
```kotlin
interface TimingEngine {
    fun start(tempo: Float, swing: Float)
    fun stop()
    fun pause()
    fun resume()
    fun setTempo(bpm: Float)
    fun setSwing(amount: Float) // 0.0 to 0.75
    fun getCurrentStep(): Int
    fun getStepProgress(): Float // 0.0 to 1.0 within current step
    fun scheduleStepCallback(callback: (step: Int, microTime: Long) -> Unit)
}

class PrecisionTimingEngine : TimingEngine {
    private val audioThread = HandlerThread("SequencerTiming")
    private val scheduler = ScheduledExecutorService
    private var stepDurationMs: Long = 0
    private var swingDelayMs: Long = 0
    
    // High-precision timing using audio thread priority
    // Calculates exact step timing with swing compensation
    // Provides callbacks for step triggers with microsecond accuracy
}
```

#### Swing and Groove Calculation
```kotlin
class SwingCalculator {
    fun calculateStepTiming(
        stepIndex: Int,
        baseTempo: Float,
        swingAmount: Float
    ): Long {
        val baseStepDuration = (60000f / baseTempo / 4f).toLong() // 16th note duration
        
        return if (stepIndex % 2 == 1) {
            // Odd steps (off-beats) get delayed by swing amount
            baseStepDuration + (baseStepDuration * swingAmount).toLong()
        } else {
            baseStepDuration
        }
    }
    
    fun getGroovePresets(): Map<String, Float> = mapOf(
        "None" to 0f,
        "Light" to 0.08f,
        "Medium" to 0.15f,
        "Heavy" to 0.25f,
        "Extreme" to 0.4f
    )
}
```

### 3. Pattern Management System

#### Pattern Repository
```kotlin
interface PatternRepository {
    suspend fun savePattern(pattern: Pattern): Result<Unit>
    suspend fun loadPattern(patternId: String): Result<Pattern>
    suspend fun loadAllPatterns(projectId: String): Result<List<Pattern>>
    suspend fun deletePattern(patternId: String): Result<Unit>
    suspend fun duplicatePattern(patternId: String, newName: String): Result<Pattern>
}

class PatternRepositoryImpl : PatternRepository {
    // JSON-based pattern persistence
    // Integration with existing project structure
    // Efficient loading and caching of pattern data
}
```

#### Pattern Operations
```kotlin
class PatternManager {
    fun createEmptyPattern(name: String, length: Int = 16): Pattern
    fun toggleStep(pattern: Pattern, padIndex: Int, stepIndex: Int): Pattern
    fun setStepVelocity(pattern: Pattern, padIndex: Int, stepIndex: Int, velocity: Int): Pattern
    fun clearPattern(pattern: Pattern): Pattern
    fun copyPattern(source: Pattern, newName: String): Pattern
    fun quantizePattern(pattern: Pattern, quantization: Quantization): Pattern
    
    // Real-time recording functions
    fun startRecording(pattern: Pattern): Pattern
    fun recordPadHit(pattern: Pattern, padIndex: Int, timestamp: Long, velocity: Int): Pattern
    fun stopRecording(pattern: Pattern): Pattern
}
```

### 4. User Interface Components

#### Sequencer Screen Layout
```kotlin
@Composable
fun SequencerScreen(
    sequencerState: SequencerState,
    patterns: List<Pattern>,
    pads: List<PadState>,
    onPatternChange: (Pattern) -> Unit,
    onTransportAction: (TransportAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Transport controls (play, stop, tempo, swing)
        TransportControls(
            isPlaying = sequencerState.isPlaying,
            tempo = sequencerState.currentPattern?.tempo ?: 120f,
            swing = sequencerState.currentPattern?.swing ?: 0f,
            onTransportAction = onTransportAction
        )
        
        // Pattern selection and management
        PatternSelector(
            patterns = patterns,
            currentPattern = sequencerState.currentPattern,
            onPatternSelect = { /* Handle pattern selection */ }
        )
        
        // Main step grid interface
        StepGrid(
            pattern = patterns.find { it.id == sequencerState.currentPattern },
            pads = pads,
            currentStep = sequencerState.currentStep,
            onStepToggle = { padIndex, stepIndex -> /* Handle step toggle */ },
            onStepVelocityChange = { padIndex, stepIndex, velocity -> /* Handle velocity */ }
        )
        
        // Pad selection for multi-track view
        PadSelector(
            pads = pads,
            selectedPads = /* Currently visible pads */,
            onPadSelectionChange = { /* Handle pad selection */ }
        )
    }
}
```

#### Step Grid Component
```kotlin
@Composable
fun StepGrid(
    pattern: Pattern?,
    pads: List<PadState>,
    currentStep: Int,
    selectedPadIndex: Int = 0,
    onStepToggle: (padIndex: Int, stepIndex: Int) -> Unit,
    onStepVelocityChange: (padIndex: Int, stepIndex: Int, velocity: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(pads.filter { it.hasAssignedSample }) { pad ->
            StepRow(
                pad = pad,
                steps = pattern?.steps?.get(pad.index) ?: emptyList(),
                patternLength = pattern?.length ?: 16,
                currentStep = currentStep,
                onStepToggle = { stepIndex -> onStepToggle(pad.index, stepIndex) },
                onStepVelocityChange = { stepIndex, velocity -> 
                    onStepVelocityChange(pad.index, stepIndex, velocity) 
                }
            )
        }
    }
}

@Composable
fun StepRow(
    pad: PadState,
    steps: List<Step>,
    patternLength: Int,
    currentStep: Int,
    onStepToggle: (Int) -> Unit,
    onStepVelocityChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Pad info (name, sample)
        PadInfo(
            pad = pad,
            modifier = Modifier.width(80.dp)
        )
        
        // Step buttons
        repeat(patternLength) { stepIndex ->
            val step = steps.find { it.position == stepIndex }
            StepButton(
                isActive = step?.isActive == true,
                velocity = step?.velocity ?: 100,
                isCurrentStep = stepIndex == currentStep,
                onToggle = { onStepToggle(stepIndex) },
                onVelocityChange = { velocity -> onStepVelocityChange(stepIndex, velocity) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

#### Transport Controls
```kotlin
@Composable
fun TransportControls(
    isPlaying: Boolean,
    isPaused: Boolean,
    isRecording: Boolean,
    tempo: Float,
    swing: Float,
    onTransportAction: (TransportAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause/Stop buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = { 
                        onTransportAction(
                            if (isPlaying) TransportAction.Pause else TransportAction.Play
                        ) 
                    },
                    isActive = isPlaying
                )
                
                TransportButton(
                    icon = Icons.Default.Stop,
                    onClick = { onTransportAction(TransportAction.Stop) }
                )
                
                TransportButton(
                    icon = Icons.Default.FiberManualRecord,
                    onClick = { onTransportAction(TransportAction.ToggleRecord) },
                    isActive = isRecording,
                    tint = if (isRecording) Color.Red else LocalContentColor.current
                )
            }
            
            // Tempo control
            TempoControl(
                tempo = tempo,
                onTempoChange = { newTempo -> 
                    onTransportAction(TransportAction.SetTempo(newTempo)) 
                }
            )
            
            // Swing control
            SwingControl(
                swing = swing,
                onSwingChange = { newSwing -> 
                    onTransportAction(TransportAction.SetSwing(newSwing)) 
                }
            )
        }
    }
}
```

### 5. Integration Layer

#### Sequencer ViewModel
```kotlin
class SequencerViewModel @Inject constructor(
    private val audioEngine: AudioEngine,
    private val patternRepository: PatternRepository,
    private val timingEngine: TimingEngine,
    private val patternManager: PatternManager
) : ViewModel() {
    
    private val _sequencerState = MutableStateFlow(SequencerState())
    val sequencerState: StateFlow<SequencerState> = _sequencerState.asStateFlow()
    
    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()
    
    init {
        setupTimingCallbacks()
        loadPatterns()
    }
    
    private fun setupTimingCallbacks() {
        timingEngine.scheduleStepCallback { step, microTime ->
            handleStepTrigger(step, microTime)
        }
    }
    
    private fun handleStepTrigger(step: Int, microTime: Long) {
        val currentPattern = getCurrentPattern() ?: return
        
        // Trigger all active steps for this position
        currentPattern.steps.forEach { (padIndex, steps) ->
            val activeStep = steps.find { it.position == step && it.isActive }
            if (activeStep != null) {
                // Trigger sample through audio engine
                audioEngine.triggerPad(
                    padIndex = padIndex,
                    velocity = activeStep.velocity / 127f,
                    timestamp = microTime + activeStep.microTiming.toLong()
                )
            }
        }
        
        // Update UI state
        _sequencerState.update { it.copy(currentStep = step) }
    }
    
    fun playPattern() {
        val pattern = getCurrentPattern() ?: return
        timingEngine.start(pattern.tempo, pattern.swing)
        _sequencerState.update { it.copy(isPlaying = true, isPaused = false) }
    }
    
    fun stopPattern() {
        timingEngine.stop()
        _sequencerState.update { 
            it.copy(isPlaying = false, isPaused = false, currentStep = 0) 
        }
    }
    
    fun toggleStep(padIndex: Int, stepIndex: Int) {
        val currentPattern = getCurrentPattern() ?: return
        val updatedPattern = patternManager.toggleStep(currentPattern, padIndex, stepIndex)
        updatePattern(updatedPattern)
    }
    
    // Additional methods for pattern management, recording, etc.
}
```

#### Audio Engine Integration
```kotlin
// Extension to existing AudioEngine for sequencer support
interface SequencerAudioEngine {
    fun scheduleStepTrigger(
        padIndex: Int,
        velocity: Float,
        timestamp: Long
    )
    
    fun setSequencerTempo(bpm: Float)
    fun enableMetronome(enabled: Boolean)
    fun getAudioLatency(): Long // For timing compensation
}

class AudioEngineImpl : AudioEngine, SequencerAudioEngine {
    // Existing implementation...
    
    override fun scheduleStepTrigger(padIndex: Int, velocity: Float, timestamp: Long) {
        // Schedule sample trigger at precise timestamp
        // Use existing triggerPad functionality with timing
        val compensatedTimestamp = timestamp - getAudioLatency()
        scheduleCallback(compensatedTimestamp) {
            triggerPad(padIndex, velocity)
        }
    }
}
```

## Data Models

### Pattern Storage Format
```json
{
  "id": "pattern_001",
  "name": "Main Beat",
  "length": 16,
  "tempo": 120.0,
  "swing": 0.15,
  "createdAt": 1640995200000,
  "modifiedAt": 1640995200000,
  "steps": {
    "0": [
      {"position": 0, "velocity": 120, "isActive": true, "microTiming": 0.0},
      {"position": 4, "velocity": 100, "isActive": true, "microTiming": 0.0},
      {"position": 8, "velocity": 115, "isActive": true, "microTiming": 0.0},
      {"position": 12, "velocity": 105, "isActive": true, "microTiming": 0.0}
    ],
    "1": [
      {"position": 4, "velocity": 110, "isActive": true, "microTiming": 0.0},
      {"position": 12, "velocity": 95, "isActive": true, "microTiming": 0.0}
    ]
  }
}
```

### Song Mode Structure
```json
{
  "id": "song_001",
  "name": "My Track",
  "sequence": [
    {"patternId": "pattern_001", "repeatCount": 4},
    {"patternId": "pattern_002", "repeatCount": 2},
    {"patternId": "pattern_001", "repeatCount": 4},
    {"patternId": "pattern_003", "repeatCount": 1}
  ],
  "currentPosition": 0,
  "isActive": true
}
```

## Error Handling

### Timing Error Recovery
- **Clock Drift Detection**: Monitor timing accuracy and compensate for drift
- **Audio Buffer Underruns**: Graceful recovery without stopping playback
- **Pattern Loading Failures**: Fallback to empty pattern with user notification
- **Memory Pressure**: Automatic pattern caching and cleanup

### User Error Prevention
- **Invalid Pattern Operations**: Validate step positions and velocity ranges
- **Concurrent Modification**: Handle simultaneous pattern edits safely
- **Resource Limits**: Prevent excessive pattern complexity that could impact performance

## Testing Strategy

### Unit Testing Focus
- **Timing Accuracy**: Verify step timing calculations and swing implementation
- **Pattern Operations**: Test all pattern manipulation functions
- **State Management**: Validate sequencer state transitions
- **Integration Points**: Mock audio engine interactions

### Integration Testing
- **Audio Engine Integration**: Test sample triggering with precise timing
- **Pattern Persistence**: Verify save/load operations
- **Real-time Performance**: Test under various load conditions

### Performance Testing
- **Timing Jitter**: Measure and validate timing accuracy under load
- **Memory Usage**: Monitor pattern storage and caching efficiency
- **CPU Usage**: Ensure sequencer doesn't impact audio performance

## Performance Considerations

### Timing Optimization
- **Dedicated Audio Thread**: Run timing engine on high-priority thread
- **Pre-calculated Timing**: Cache step timing calculations
- **Minimal Allocations**: Avoid garbage collection during playback
- **Efficient Step Lookup**: Optimize step data structures for fast access

### Memory Management
- **Pattern Caching**: Intelligent caching of frequently used patterns
- **Lazy Loading**: Load pattern data only when needed
- **Memory Pooling**: Reuse objects to minimize allocations
- **Background Cleanup**: Clean up unused patterns automatically

### Mobile Optimization
- **Battery Efficiency**: Minimize CPU usage during playback
- **Background Behavior**: Handle app backgrounding gracefully
- **Resource Scaling**: Adapt complexity based on device capabilities
- **Touch Responsiveness**: Maintain UI responsiveness during audio operations