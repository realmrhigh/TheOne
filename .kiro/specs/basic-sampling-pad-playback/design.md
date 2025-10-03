# Design Document

## Overview

The Basic Sampling & Pad Playback module (M1) builds upon the existing audio engine foundation to provide core MPC-style functionality. This module enables users to record samples from the microphone, assign them to virtual drum pads, and trigger them in real-time. Based on analysis of the current codebase, the underlying audio infrastructure is already robust, allowing us to focus on the sampling workflow and pad interface.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Sampling & Pad UI Layer                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Record Button  │  │    Pad Grid     │  │  Sample Editor  │ │
│  │  • Start/Stop   │  │  • 16 Pads      │  │  • Trim/Edit    │ │
│  │  • Level Meter  │  │  • Visual FB    │  │  • Preview      │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Sampling ViewModel                           │
│  • Recording state management                                  │
│  • Pad assignment logic                                        │
│  • Sample metadata handling                                    │
│  • UI state coordination                                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Sample Management Repository                    │
│  • Project sample pool                                         │
│  • File system operations                                      │
│  • Metadata persistence                                        │
│  • Sample organization                                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              Existing Audio Engine (C++)                       │
│  ✅ Sample loading/unloading                                   │
│  ✅ Real-time playback                                         │
│  ✅ Voice management                                            │
│  🔄 Recording system (needs completion)                        │
└─────────────────────────────────────────────────────────────────┘
```

### Component Integration Strategy

Since the audio engine foundation is already solid, M1 focuses on:
1. **UI Layer**: Intuitive sampling and pad interface
2. **Business Logic**: Sample management and pad assignment
3. **Recording Completion**: Finish the existing recording infrastructure
4. **File Management**: Organize samples within projects

## Components and Interfaces

### 1. Sampling UI Components

#### RecordingControls Composable
```kotlin
@Composable
fun RecordingControls(
    isRecording: Boolean,
    recordingDuration: Duration,
    peakLevel: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Record button with visual feedback
    // Duration display
    // Level meter visualization
    // Recording status indicator
}
```

#### PadGrid Composable
```kotlin
@Composable
fun PadGrid(
    pads: List<PadState>,
    onPadTap: (Int) -> Unit,
    onPadLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 4x4 grid of virtual drum pads
    // Visual feedback for pad states
    // Touch handling with velocity sensitivity
    // Sample assignment indicators
}
```

#### SampleEditor Composable
```kotlin
@Composable
fun SampleEditor(
    sample: SampleMetadata?,
    waveformData: FloatArray,
    trimStart: Float,
    trimEnd: Float,
    onTrimChange: (Float, Float) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Waveform visualization
    // Trim handle controls
    // Preview playback
    // Apply/cancel actions
}
```

### 2. Data Models

#### PadState Data Class
```kotlin
data class PadState(
    val index: Int,
    val sampleId: String? = null,
    val sampleName: String? = null,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    val hasAssignedSample: Boolean = false
)
```

#### RecordingState Data Class
```kotlin
data class RecordingState(
    val isRecording: Boolean = false,
    val duration: Duration = Duration.ZERO,
    val peakLevel: Float = 0.0f,
    val isInitialized: Boolean = false,
    val error: String? = null
)
```

#### SampleTrimSettings Data Class
```kotlin
data class SampleTrimSettings(
    val startTime: Float = 0.0f,
    val endTime: Float = 1.0f,
    val fadeInMs: Float = 0.0f,
    val fadeOutMs: Float = 0.0f,
    val normalize: Boolean = false
)
```

### 3. Business Logic Layer

#### SamplingViewModel
```kotlin
class SamplingViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl,
    private val sampleRepository: SampleRepository,
    private val projectManager: ProjectManager
) : ViewModel() {
    
    // Recording management
    suspend fun startRecording()
    suspend fun stopRecording(): SampleMetadata?
    
    // Pad management
    suspend fun assignSampleToPad(padIndex: Int, sampleId: String)
    suspend fun triggerPad(padIndex: Int, velocity: Float)
    suspend fun configurePad(padIndex: Int, settings: PadSettings)
    
    // Sample management
    suspend fun loadExternalSample(uri: Uri): SampleMetadata
    suspend fun trimSample(sampleId: String, settings: SampleTrimSettings)
    suspend fun deleteSample(sampleId: String)
}
```

#### SampleRepository
```kotlin
interface SampleRepository {
    suspend fun saveSample(sampleData: ByteArray, metadata: SampleMetadata): Result<String>
    suspend fun loadSample(sampleId: String): Result<SampleMetadata>
    suspend fun deleteSample(sampleId: String): Result<Unit>
    suspend fun getAllSamples(): Result<List<SampleMetadata>>
    suspend fun getSamplesForProject(projectId: String): Result<List<SampleMetadata>>
}
```

### 4. Audio Engine Enhancements

#### Recording System Completion
The existing audio engine has recording infrastructure that needs completion:

```cpp
// Complete the recording implementation in AudioEngine.cpp
bool AudioEngine::startAudioRecording(JNIEnv* env, jobject context, 
                                     const std::string& filePathUri, 
                                     int sampleRate, int channels) {
    // 1. Initialize input stream with Oboe
    // 2. Set up WAV file writer with dr_wav
    // 3. Start recording callback
    // 4. Monitor input levels
}

jobjectArray AudioEngine::stopAudioRecording(JNIEnv* env) {
    // 1. Stop input stream
    // 2. Finalize WAV file
    // 3. Return sample metadata
    // 4. Clean up resources
}
```

#### Enhanced Sample Triggering
```cpp
// Extend existing sample triggering for pad-specific features
bool AudioEngine::triggerPadSample(const std::string& padId, 
                                  float velocity, 
                                  const PadSettings& settings) {
    // 1. Apply velocity sensitivity
    // 2. Handle playback mode (one-shot vs loop)
    // 3. Apply pad-specific volume/pan
    // 4. Manage voice allocation
}
```

## Data Models

### Sample Management Data Flow

#### Sample Creation Pipeline
```
Microphone Input → Oboe Input Stream → Real-time Processing → WAV File → Sample Metadata → Project Database
```

#### Sample Assignment Pipeline
```
Sample Selection → Pad Assignment → Settings Configuration → Audio Engine Update → UI State Update
```

#### Sample Playback Pipeline
```
Pad Trigger → Velocity Processing → Audio Engine Playback → Voice Management → Audio Output
```

### File Organization Strategy

#### Project Structure
```
/storage/emulated/0/Android/data/com.high.theone/files/
├── projects/
│   └── {project-id}/
│       ├── project.json          # Project metadata
│       ├── samples/              # Project-specific samples
│       │   ├── {sample-id}.wav   # Audio files
│       │   └── metadata.json     # Sample metadata
│       └── pads/                 # Pad configurations
│           └── pad-settings.json # Pad assignments and settings
└── shared-samples/               # Global sample library
    ├── {sample-id}.wav
    └── metadata.json
```

#### Sample Metadata Schema
```json
{
  "id": "sample_001",
  "name": "Kick Drum",
  "filePath": "/path/to/sample.wav",
  "duration": 1.5,
  "sampleRate": 44100,
  "channels": 1,
  "createdAt": "2024-01-01T12:00:00Z",
  "tags": ["drum", "kick"],
  "trimSettings": {
    "startTime": 0.0,
    "endTime": 1.0,
    "fadeIn": 0.0,
    "fadeOut": 0.0
  }
}
```

### State Management Architecture

#### UI State Flow
```kotlin
// Centralized state management for sampling features
data class SamplingUiState(
    val recordingState: RecordingState = RecordingState(),
    val pads: List<PadState> = List(16) { PadState(it) },
    val availableSamples: List<SampleMetadata> = emptyList(),
    val selectedPad: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

#### State Updates
```kotlin
// Reactive state updates using StateFlow
class SamplingViewModel {
    private val _uiState = MutableStateFlow(SamplingUiState())
    val uiState: StateFlow<SamplingUiState> = _uiState.asStateFlow()
    
    private fun updatePadState(padIndex: Int, update: (PadState) -> PadState) {
        _uiState.update { currentState ->
            currentState.copy(
                pads = currentState.pads.mapIndexed { index, pad ->
                    if (index == padIndex) update(pad) else pad
                }
            )
        }
    }
}
```

## Error Handling

### Error Categories and Recovery Strategies

#### 1. Recording Errors
- **Microphone Permission Denied**: Request permissions with clear explanation
- **Storage Space Insufficient**: Check available space before recording
- **Audio Hardware Unavailable**: Fallback to file loading workflow
- **Recording Timeout**: Auto-stop after configurable duration

#### 2. Sample Loading Errors
- **File Format Unsupported**: Display supported formats and conversion options
- **File Corruption**: Validate file integrity and provide error details
- **Memory Limitations**: Implement sample unloading and memory management
- **Network Issues**: Handle cloud storage and sharing failures

#### 3. Playback Errors
- **Sample Not Found**: Handle missing file references gracefully
- **Audio Engine Unavailable**: Provide visual feedback without audio
- **Voice Allocation Failure**: Implement voice stealing and priority management
- **Latency Issues**: Adjust buffer sizes and provide performance warnings

### Error Recovery Mechanisms
```kotlin
// Comprehensive error handling with recovery options
sealed class SamplingError {
    object MicrophonePermissionDenied : SamplingError()
    object InsufficientStorage : SamplingError()
    data class FileLoadError(val message: String) : SamplingError()
    data class AudioEngineError(val message: String) : SamplingError()
}

class ErrorHandler {
    suspend fun handleSamplingError(error: SamplingError): RecoveryAction {
        return when (error) {
            is SamplingError.MicrophonePermissionDenied -> 
                RecoveryAction.RequestPermission
            is SamplingError.InsufficientStorage -> 
                RecoveryAction.ShowStorageManagement
            is SamplingError.FileLoadError -> 
                RecoveryAction.ShowFileSelector
            is SamplingError.AudioEngineError -> 
                RecoveryAction.RestartAudioEngine
        }
    }
}
```

## Testing Strategy

### Unit Testing

#### ViewModel Testing
```kotlin
class SamplingViewModelTest {
    @Test
    fun `recording starts and stops correctly`() = runTest {
        // Test recording state management
        // Verify audio engine interactions
        // Check error handling
    }
    
    @Test
    fun `pad assignment works correctly`() = runTest {
        // Test sample-to-pad assignment
        // Verify state updates
        // Check persistence
    }
}
```

#### Repository Testing
```kotlin
class SampleRepositoryTest {
    @Test
    fun `sample saving and loading works`() = runTest {
        // Test file operations
        // Verify metadata persistence
        // Check error scenarios
    }
}
```

### Integration Testing

#### Audio Engine Integration
```kotlin
class SamplingIntegrationTest {
    @Test
    fun `end-to-end recording workflow`() = runTest {
        // Test complete recording pipeline
        // Verify file creation and metadata
        // Check audio engine integration
    }
    
    @Test
    fun `pad triggering with real samples`() = runTest {
        // Test sample loading and playback
        // Verify voice management
        // Check performance characteristics
    }
}
```

### UI Testing

#### Compose UI Testing
```kotlin
class SamplingScreenTest {
    @Test
    fun `recording controls work correctly`() {
        // Test record button interactions
        // Verify visual feedback
        // Check state synchronization
    }
    
    @Test
    fun `pad grid responds to touches`() {
        // Test pad touch handling
        // Verify velocity sensitivity
        // Check visual feedback
    }
}
```

## Implementation Phases

### Phase 1: Core Recording (Week 1)
1. **Complete Recording System**: Finish microphone input and WAV writing
2. **Basic UI**: Record button and level meter
3. **File Management**: Save recorded samples to project
4. **Error Handling**: Basic recording error scenarios

### Phase 2: Pad System (Week 2)
1. **Pad Grid UI**: 4x4 grid with touch handling
2. **Sample Assignment**: Assign samples to pads
3. **Playback Integration**: Trigger samples via audio engine
4. **Visual Feedback**: Pad states and playing indicators

### Phase 3: Sample Management (Week 3)
1. **File Loading**: Load external audio files
2. **Sample Browser**: UI for selecting and managing samples
3. **Basic Editing**: Sample trimming functionality
4. **Project Integration**: Save/load pad configurations

### Phase 4: Polish & Enhancement (Week 4)
1. **Advanced Controls**: Volume, pan, playback mode per pad
2. **Performance Optimization**: Efficient sample loading and playback
3. **User Experience**: Smooth animations and responsive feedback
4. **Testing & Debugging**: Comprehensive testing and bug fixes

This design builds upon the existing solid audio foundation to deliver core MPC functionality that users can immediately start using to create beats and music.