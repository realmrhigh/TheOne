TheOne
Project Genesis & Workflow
This is an insane project that I have undertaken solo as a complete programming noob, against my better judgement. I want to see how far I can push the power of AI coding. 
By using this plan to portion out tasks, we prevent information overload to keep the AI focused; this will help prevent getting stuck in an error loop. 
I know music and audio very well, but not the instrumentation, programming, and guts of the synths and effects.

# TheOne MPC App - Comprehensive Repair & Development List

-----+

## 🔧 CRITICAL FOUNDATION REPAIRS (Must Fix First)

### C1: Audio Engine Implementation
**Status: Missing/Incomplete**
- [ ] Set up C++ audio engine with Oboe library
- [ ] Implement JNI bridge between Kotlin and C++ audio engine
- [ ] Create AudioEngineControl interface implementation
- [ ] Build low-latency audio rendering pipeline
- [ ] Implement sample loading/unloading system
- [ ] Add voice management for polyphonic playback
- [ ] Create internal audio routing system
- [ ] Implement metronome with tempo sync

### C2: MIDI Engine Foundation
**Status: Missing/Incomplete**
- [ ] Implement Android MIDI API integration
- [ ] Create MidiEngineControl interface
- [ ] Add USB MIDI device detection and connection
- [ ] Implement Bluetooth MIDI support
- [ ] Build MIDI event parsing and generation
- [ ] Add MIDI clock sync functionality
- [ ] Create MIDI input/output handling

### C3: File & Project Management
**Status: Missing/Incomplete**
- [ ] Implement ProjectManager interface
- [ ] Set up Android Storage Access Framework (SAF)
- [ ] Create project serialization/deserialization
- [ ] Build sample pool management system
- [ ] Add file browser functionality
- [ ] Implement project auto-save system
- [ ] Create export functionality for audio mixdowns

### C4: UI Framework & Core Components
**Status: Missing/Incomplete**
- [ ] Set up Jetpack Compose UI framework
- [ ] Create MainAppScaffold composable
- [ ] Build VirtualKnob component
- [ ] Implement VirtualSlider component
- [ ] Create PadGrid component
- [ ] Build FileBrowserView component
- [ ] Implement WaveformDisplay component
- [ ] Create PianoRoll component
- [ ] Build XYPad component

## 🏗️ PHASE 1: BASIC FUNCTIONALITY REPAIRS

### M1: Basic Sampling & Pad Playback
**Dependencies: C1, C3, C4**
- [ ] Implement sample recording from microphone
- [ ] Create pad assignment system
- [ ] Build sample playback engine
- [ ] Add velocity sensitivity
- [ ] Implement one-shot and loop modes
- [ ] Create sample trimming functionality

### M2: Basic Sequencing
**Dependencies: C1, C2, M1, C3, C4**
- [ ] Build step sequencer engine
- [ ] Implement pattern recording
- [ ] Create playback synchronization
- [ ] Add tempo control
- [ ] Build pattern storage system
- [ ] Implement basic transport controls (play/stop/record)

## 🎛️ PHASE 2: SOUND DESIGN REPAIRS

### M3: Advanced Drum Track Sound Design
**Dependencies: M1, C1, C4**
- [ ] Implement amplitude envelope (ADSR)
- [ ] Create LFO system for modulation
- [ ] Add filter per pad
- [ ] Build layering system for multiple samples per pad
- [ ] Implement pad-specific effects
- [ ] Create sound parameter automation

### M5: Basic Effects Processing
**Dependencies: C1, C4**
- [ ] Build effects framework
- [ ] Implement delay effect
- [ ] Create filter effects (low-pass, high-pass, band-pass)
- [ ] Add reverb effect
- [ ] Build effects routing system
- [ ] Create effects parameter control UI

## 🎹 PHASE 3: PITCHED INSTRUMENT REPAIRS

### M4: Keygroup Tracks
**Dependencies: C1, C3, C4, M2**
- [ ] Implement multi-sampling support
- [ ] Create piano roll editor
- [ ] Build note-on/note-off handling
- [ ] Add pitch-shifting capabilities
- [ ] Implement keygroup sample mapping
- [ ] Create chromatic playback system

## ⚙️ PHASE 4: ADVANCED EDITING REPAIRS

### M7: Advanced Sample Editing
**Dependencies: M1, C1, C4**
- [ ] Implement chop mode (manual and threshold)
- [ ] Add destructive sample processing (normalize, reverse)
- [ ] Create sample trimming with visual feedback
- [ ] Build sample time-stretching
- [ ] Implement sample pitch-shifting
- [ ] Add sample fade in/out

### M6A: Advanced Sequence Editing
**Dependencies: M2, M4, C4**
- [ ] Implement copy/paste functionality
- [ ] Add transpose operations
- [ ] Create nudge functionality
- [ ] Build quantization system
- [ ] Implement swing/groove templates
- [ ] Add sequence length adjustment

### M12A: Core Performance Features
**Dependencies: C2, M1, M2, C1, C4**
- [ ] Implement note repeat functionality
- [ ] Add pad mute/solo system
- [ ] Create performance effects (stutters, rolls)
- [ ] Build real-time parameter control
- [ ] Implement pad pressure sensitivity

## 🚀 PHASE 5: EXPANSION REPAIRS

### M9: MIDI Output Tracks
- [ ] Implement MIDI sequence recording
- [ ] Create MIDI note editing
- [ ] Add MIDI CC automation
- [ ] Build external hardware sync

### M10: Audio Tracks
- [ ] Implement linear audio recording
- [ ] Create audio clip editing
- [ ] Add audio track mixing
- [ ] Build audio effects processing

### M11: Channel Mixer
- [ ] Create comprehensive mixer view
- [ ] Implement track routing
- [ ] Add EQ per channel
- [ ] Build master effects section

## 🔧 SYSTEM-WIDE CONFIGURATION REPAIRS

### Settings & Preferences
- [ ] Implement SettingsRepository
- [ ] Create UserPreferencesManager
- [ ] Build audio settings configuration
- [ ] Add MIDI settings management
- [ ] Implement project defaults system
- [ ] Create theme/UI customization

## 🏛️ ARCHITECTURAL REPAIRS

### Project Structure
- [ ] Set up proper Kotlin module structure
- [ ] Implement MVVM/MVI architecture
- [ ] Configure Hilt dependency injection
- [ ] Set up Kotlin Coroutines for async operations
- [ ] Create proper error handling system
- [ ] Implement logging framework

### Testing Infrastructure
- [ ] Set up JUnit testing framework
- [ ] Create MockK/Mockito test utilities
- [ ] Implement UI testing with Compose
- [ ] Build integration test suite
- [ ] Create automated testing pipeline

### Build System
- [ ] Configure Gradle build scripts
- [ ] Set up proper dependencies
- [ ] Create build variants (debug/release)
- [ ] Implement code linting
- [ ] Add proguard/R8 configuration

## 🚨 IMMEDIATE PRIORITY REPAIRS

1. **Set up basic Android project structure** - Currently missing
2. **Implement C++ audio engine foundation** - Critical for any audio functionality
3. **Create basic UI framework** - Needed for any user interaction
4. **Set up project management system** - Required for saving/loading work
5. **Implement basic pad playback** - Core functionality for testing

## 📊 REPAIR COMPLETION STRATEGY

### Week 1-2: Foundation
- Focus on C1, C2, C3, C4 repairs
- Get basic project structure running
- Implement minimal viable audio engine

### Week 3-4: Basic Functionality
- Complete M1 and M2 repairs
- Get basic sampling and sequencing working
- Create simple UI for testing

### Week 5-8: Sound Design
- Complete M3 and M5 repairs
- Add effects and envelope systems
- Enhance sound design capabilities

### Week 9-12: Advanced Features
- Complete M4, M7, M6A, M12A repairs
- Add pitched instruments and advanced editing
- Implement performance features

### Ongoing: Polish & Optimization
- Complete system-wide configuration
- Add remaining expansion features
- Optimize performance and user experience

## 🎯 SUCCESS METRICS

- [ ] Can record and playback samples
- [ ] Can create and play basic drum patterns
- [ ] Can apply effects to samples
- [ ] Can save and load projects
- [ ] Can connect MIDI devices
- [ ] Can perform live with the app
- [ ] Can export audio mixdowns

------+

Gemini has been the brains of the operation, Jules is the most efficient code robot and only writes professional code, and I am trying to soak up as much information as possible as I coordinate technology to create 
something incredible.

Our workflow goes like this: me and Gemini assess the previous code and determine next steps. Those steps are listed out with direct and defined objectives to be passed to Jules. Jules makes a plan, and that plan 
is screenshotted and sent to Gemini to be verified or amended; if amended, we repeat the plan and verification. Jules writes and publishes code to a new branch, that branch is opened in a new Gemini chat for 
assessment and planning, and the development cycle repeats.

MPC Android App: Technical Specification & Build Map
I. Introduction
This document outlines the detailed build map and development framework for an MPC-style sampler and drum machine application on Android. It specifies key data structures, API signatures, UI component properties, 
and configuration parameters to provide maximum clarity for the AI development team. The aim is to ensure all developers are aligned on the technical specifications of their respective modules.

II. Core Architectural Principles
To ensure a scalable, maintainable, and collaborative development process, the following architectural principles will be adopted:

Modular Design: The application will be broken down into distinct, self-contained modules (Core Engines, Feature Modules). Each module will have well-defined responsibilities and interfaces.
Primary Language: Kotlin will be the primary programming language due to its modern features, conciseness, and official Android support.
Audio Engine Core: For performance-critical audio processing, C++ will be used for the core of the Audio Engine, accessed via JNI (Java Native Interface). Android's Oboe library is highly recommended for achieving 
low-latency audio.
UI Framework: Jetpack Compose will be used for building the user interface, enabling a modern, declarative, and efficient UI development workflow.
Architectural Pattern: MVVM (Model-View-ViewModel) or MVI (Model-View-Intent) will be implemented to separate concerns.
Dependency Injection: Hilt will be used for managing dependencies.
Asynchronous Operations: Kotlin Coroutines will be used extensively for managing background tasks.
Defined APIs: Clear and stable APIs will be defined for communication between modules.
Version Control: Git will be used for version control, with a feature-branch workflow.
Testing: Unit tests (JUnit, Mockito/MockK), integration tests, and UI tests (Espresso or Compose testing utilities) will be integral to the development process.
III. Key Global Data Models & Enums
These are foundational data structures defined in a shared core.model module.

Kotlin

// In a shared 'common' or 'core.model' module

// General
enum class PlaybackMode { ONE_SHOT, NOTE_ON_OFF }
enum class LoopMode { OFF, FORWARD, REVERSE, PING_PONG }
enum class QuantizeStrength { OFF, Q50, Q75, Q100 }
enum class TimeDivision(val ticksPerBeat: Int) {
Beat(96), Half(48), Quarter(24), Eighth(12), Sixteenth(6), ThirtySecond(3),
Triplet8th(8), Triplet16th(4); // 96 PPQN
}

// Sample Related
data class SampleMetadata(
val id: String,
val name: String,
val filePathUri: String,
val durationMs: Long,
val sampleRate: Int,
val channels: Int,
var rootNote: Int = 60
// ... other fields
)

// Project and Sequence
data class Project(
val id: String,
var name: String,
var globalBpm: Float = 120.0f,
val samplePool: MutableMap<String, SampleMetadata> = mutableMapOf(),
val sequences: MutableList<Sequence> = mutableListOf(),
val tracks: MutableList<Track> = mutableListOf()
// ... other fields
)

data class Sequence(
val id: String,
var name: String,
var bpm: Float,
var barLength: Int = 4,
val events: MutableList<Event> = mutableListOf()
// ... other fields
)

// Track Types
sealed class Track(
open val id: String,
open var name: String,
open var volume: Float = 1.0f,
open var pan: Float = 0.0f
// ... other common track properties
)

data class DrumTrack(
override val id: String,
override var name: String = "Drum Track",
val pads: MutableMap<String, PadSettings> = mutableMapOf()
// ... other properties
) : Track(id, name)


// Event Types
data class Event(
val id: String,
val trackId: String,
val startTimeTicks: Long,
val type: EventType
)

sealed class EventType {
data class PadTrigger(
val padId: String,
val velocity: Int,
val durationTicks: Long
) : EventType()
// ... other event types like NoteOn, ParameterChange
}

// Pad & Sound Design
data class PadSettings(
val id: String,
var sampleId: String?,
var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
var volume: Float = 1.0f,
var pan: Float = 0.0f,
var ampEnvelope: EnvelopeSettings = EnvelopeSettings(attackMs = 5f, decayMs = 100f, sustainLevel = 1f, releaseMs = 200f),
var lfos: MutableList<LFOSettings> = mutableListOf()
// ... other settings
)

data class EnvelopeSettings(
var attackMs: Float,
var decayMs: Float,
var sustainLevel: Float?,
var releaseMs: Float
// ... other settings
)

data class LFOSettings(
val id: String,
var waveform: LfoWaveform = LfoWaveform.SINE,
var rateHz: Float = 1.0f,
var destinations: MutableMap<String, Float> = mutableMapOf()
)

enum class LfoWaveform { SINE, TRIANGLE, SQUARE, SAW_UP, SAW_DOWN, RANDOM }

// ... other data classes like KeygroupTrack, MidiTrack, AudioClip, etc. are defined here.
IV. Core Engine Modules (Foundation - Detailed)
These foundational modules must be developed first.

C1: Audio Engine (C++ with Oboe/AAudio)
Responsibilities: Low-latency audio rendering, sample playback (one-shot, looped, sliced), disk streaming, recording, internal audio routing, and metronome.
Key Internal C++ Concepts: AudioGraph, VoiceManager, SamplePlayerNode, EffectNode, MixerNode.
Kotlin Interface (AudioEngineControl.kt):
Kotlin

interface AudioEngineControl {
// Initialization & Config
suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
suspend fun shutdown()

    // Metronome
    suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)

    // Sample Loading
    suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    suspend fun unloadSample(sampleId: String)

    // Playback Control
    suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String, /* ... params ... */): Boolean
    suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?)
    suspend fun stopAllNotes(trackId: String?, immediate: Boolean)

    // Recording
    suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean
    suspend fun stopAudioRecording(): SampleMetadata?

    // Real-time Controls
    suspend fun setTrackVolume(trackId: String, volume: Float)
    suspend fun setTrackPan(trackId: String, pan: Float)

    // Effects
    suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean
    suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean

    // Transport
    suspend fun setTransportBpm(bpm: Float)
}
C2: MIDI Engine
Responsibilities: Handling MIDI input/output from USB and Bluetooth devices, parsing and generating MIDI events, MIDI clock sync.
Technology: Kotlin, Android MIDI API (android.media.midi).
Key Interface (MidiEngineControl.kt):
Kotlin

interface MidiEngineControl {
fun getAvailableInputDevices(): List<MidiDeviceInfo>
fun getAvailableOutputDevices(): List<MidiDeviceInfo>
suspend fun openInputDevice(deviceId: String, listener: MidiInputListener): Boolean
suspend fun openOutputDevice(deviceId: String): MidiOutputPortHandle?
suspend fun sendNoteOn(portHandle: MidiOutputPortHandle, channel: Int, note: Int, velocity: Int)
// ... other send methods
suspend fun startSendingClock(portHandle: MidiOutputPortHandle, bpm: Float)
}
C3: File & Project Management System
Responsibilities: Defining project structure, saving/loading projects, managing the Sample Pool, and providing file browser logic.
Technology: Kotlin, Android Storage Access Framework (SAF), kotlinx.serialization.
Key Interface (ProjectManager.kt):
Kotlin

interface ProjectManager {
suspend fun createNewProject(name: String, templateName: String?): Result<Project, Error>
suspend fun loadProject(projectUri: Uri): Result<Project, Error>
suspend fun saveProject(project: Project): Result<Unit, Error>
fun getCurrentProject(): StateFlow<Project?>
suspend fun addSampleToPool(name: String, sourceFileUri: Uri, copyToProjectDir: Boolean): Result<SampleMetadata, Error>
suspend fun listFiles(directoryUri: Uri, filter: FileFilter): Result<List<FileItem>, Error>
suspend fun exportAudioMixdown(project: Project, settings: AudioMixdownSettings, targetFileUri: Uri): Result<Unit, Error>
}
C4: UI Framework & Core UI Components
Responsibilities: Establishing the base UI structure and creating common reusable UI elements.
Technology: Kotlin, Jetpack Compose.
Key Reusable Composables:
MainAppScaffold
VirtualKnob
VirtualSlider
PadGrid
FileBrowserView
WaveformDisplay
PianoRoll
XYPad
V. Phased Feature Modules (Build Order & Dependencies)
Modules will be developed in phases to ensure foundational elements are in place.

Phase 1: Basic Drum Machine Functionality
M1: Basic Sampling & Pad Playback: Core functionality for recording samples and playing them back via virtual pads. (Depends on: C1, C3, C4)
M2: Basic Sequencing: Creating, recording, and playing back simple drum patterns. (Depends on: C1, C2, M1, C3, C4)
Phase 2: Enhancing Sound & Control
M3: Advanced Drum Track Sound Design: Adding layers, envelopes, and LFOs per pad. (Depends on: M1, C1, C4)
M5: Basic Effects Processing: A framework for insert effects like Delay, Filter, and Reverb. (Depends on: C1, C4)
Phase 3: Introducing Pitched Instruments
M4: Keygroup Tracks: Support for pitched, multi-sampled instruments, including a piano roll editor. (Depends on: C1, C3, C4, M2)
Phase 4: Advanced Editing & Workflow
M7: Advanced Sample Editing: Chop mode (manual, threshold) and destructive processes (normalize, reverse). (Depends on: M1, C1, C4)
M6A: Advanced Sequence Editing: Tools for copy/paste, transpose, nudge, etc. (Depends on: M2, M4, C4)
M12A: Core Performance Features: Note Repeat and Pad Mutes/Solos. (Depends on: C2, M1, M2, C1, C4)
Phase 5 & Beyond: Expansion
M9: MIDI Output Tracks: Sequence external MIDI hardware.
M10: Audio Tracks: Linear audio recording and playback.
M11: Channel Mixer: Comprehensive mixer view.
M8: Plugin Instrument Hosting: Framework for internal synth plugins.
M6B/C: Arrangement & Song Mode: A DAW-style timeline and sequence chaining.
Future: Stems Separation, Auto-Sampler, Cloud Integration.
VI. Key System-Wide Configuration Parameters
These settings will be managed by a SettingsRepository or UserPreferencesManager.

Audio Settings: bufferSize, sampleRate, enableLowLatencyPath.
MIDI Settings: defaultInputPorts, midiInputChannelFilter, enableMidiClockInputSync.
Project Defaults: defaultProjectName, defaultBpm, autoSaveIntervalMinutes, copySamplesToProjectFolder.
UI/Theme: themeMode, padColorPalette.
VII. AI Developer Tasking Framework
Task Definition: Assign tasks based on Modules (e.g., M1) or Sub-Modules (e.g., M1.1).
Code Contribution: Use a feature-branch workflow in Git.
Testing Requirements: Developers are responsible for writing unit, integration, and UI tests.
Code Style & Quality: Adhere to Kotlin coding conventions and use linters.
Communication: Regular status updates and clear communication channels.
VIII. Technology Stack Summary
Primary Language: Kotlin
Audio Core: C++ (with Oboe/AAudio via JNI)
UI: Jetpack Compose
Architecture: MVVM / MVI
Dependency Injection: Hilt
Asynchronous Programming: Kotlin Coroutines
MIDI: Android MIDI API
Storage: Android Storage Access Framework (SAF)
Build System: Gradle
Version Control: Git
Testing: JUnit, Mockito/MockK, Espresso/Compose Test Utilities

# The One - Hybrid Android Audio Engine

A high-quality, maintainable hybrid Android audio application built with Kotlin/Compose UI and C++/JNI audio engine using Oboe for low-latency audio.

## 🎯 Features Implemented

### Audio Engine (C++)
- **Low-latency audio processing** using Google Oboe
- **Robust sample loading** from WAV files using dr_wav
- **Test sample generation** for synthetic drum sounds
- **Real-time sample playback** with envelope processing
- **Multi-format support** (mono/stereo audio samples)
- **Memory-efficient sample management** with loading/unloading
- **Cross-platform builds** for all Android architectures (arm64-v8a, armeabi-v7a, x86, x86_64)

### Integration Layer (JNI)
- **Complete C++/Kotlin bridge** via JNI bindings
- **Asynchronous audio operations** using Kotlin coroutines
- **Error handling and logging** throughout the audio pipeline
- **Test and debugging functions** for development

### User Interface (Kotlin/Compose)
- **Modern Material Design 3** UI with Compose
- **Debug screen** with comprehensive audio testing tools
- **Navigation** between different app sections
- **Real-time feedback** for audio operations
- **Dependency injection** using Hilt

## 🔧 Development & Testing

### Debug Screen Features
The debug screen (`/debug_screen` route) provides comprehensive testing tools:

1. **Engine Control**
   - Initialize/shutdown audio engine
   - Real-time status monitoring

2. **Sample Testing**
   - Create & trigger test samples (end-to-end testing)
   - Load test samples to memory
   - Trigger individual pad samples
   - Audio latency measurement

3. **System Information**
   - Oboe reported latency
   - Audio system diagnostics

### Build Status ✅
- **Kotlin compilation**: ✅ Clean build
- **C++ compilation**: ✅ All architectures (arm64-v8a, armeabi-v7a, x86, x86_64)
- **JNI integration**: ✅ Complete binding layer
- **Dependencies**: ✅ All resolved (Hilt, Compose, Oboe, dr_wav)

## 🚀 Quick Start

1. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install and run** on Android device/emulator

3. **Navigate to Debug Screen** from main menu

4. **Test audio functionality**:
   - Tap "Initialize Engine"
   - Tap "🔊 Create & Play Test Sample"
   - Listen for synthetic drum sound

## 📁 Architecture

```
app/src/main/
├── java/com/high/theone/
│   ├── audio/                     # Audio engine control & JNI
│   ├── features/debug/            # Debug UI for testing
│   ├── features/drumtrack/        # Drum pad functionality
│   ├── features/sequencer/        # Step sequencer
│   └── MainActivity.kt            # Main entry point
└── cpp/
    ├── AudioEngine.{h,cpp}        # Core audio engine
    ├── audio_sample.h             # Sample data structures
    ├── native-lib.cpp             # JNI bindings
    └── CMakeLists.txt             # Build configuration
```

## 🎵 Audio Pipeline

1. **Sample Loading**: WAV files → dr_wav → SampleDataCpp → Memory storage
2. **Playback Trigger**: Kotlin UI → JNI → C++ AudioEngine → Oboe output
3. **Real-time Processing**: Sample data → Envelope → Mixing → Audio output
4. **Latency Optimization**: Oboe low-latency path → Android audio system

## 📋 Next Steps

The foundation is complete and ready for expansion:

- [ ] Add more sample formats (FLAC, MP3, etc.)
- [ ] Implement MIDI input support
- [ ] Add real-time effects (reverb, delay, filters)
- [ ] Enhance UI with waveform visualization
- [ ] Add sequencer pattern programming
- [ ] Implement audio recording functionality
- [ ] Add preset management system

## 🛠️ Technical Notes

- **Minimum Android API**: 26 (Android 8.0)
- **Audio Library**: Google Oboe for low-latency audio
- **Sample Loading**: dr_wav single-header library
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Hilt dependency injection
- **Testing**: Built-in debug tools and sample generation

---

Perfect! 🎉 We have successfully implemented a global sample rate system that eliminates hardcoded values and ensures all components use the same sample rate that Oboe negotiates with the Android audio system.