TheOne
Project Genesis & Workflow
This is an insane project that I have undertaken solo as a complete programming noob, against my better judgement. I want to see how far I can push the power of AI coding. 
By using this plan to portion out tasks, we prevent information overload to keep the AI focused; this will help prevent getting stuck in an error loop. 
I know music and audio very well, but not the instrumentation, programming, and guts of the synths and effects.

# TheOne MPC App - Development Status & Roadmap

## 🎉 MAJOR ACHIEVEMENTS COMPLETED

**TheOne has evolved from concept to a working MPC-style sampler!** The core functionality is now operational:

### ✅ **WORKING FEATURES**
- **🎵 Full Audio Engine**: Low-latency C++/Oboe implementation with JNI bridge
- **🎤 Sample Recording**: Record from microphone with real-time level monitoring
- **🥁 Drum Pad System**: 4x4 pad grid with velocity sensitivity and visual feedback
- **📝 Step Sequencer**: 16-step patterns with swing, tempo control, and real-time recording
- **✂️ Sample Editor**: Waveform display, trimming, normalize, reverse operations
- **💾 Sample Management**: Load external files, organize samples, assignment system
- **🎛️ Pattern Management**: Create, copy, chain patterns into songs
- **📱 Modern UI**: Material Design 3 with Jetpack Compose, responsive/adaptive layout
- **🔧 Debug Tools**: Comprehensive testing interface for development
- **🎚️ Live Mixer Panel**: Per-pad volume & pan sliders wired to DrumTrackViewModel in real time
- **🎹 MIDI Device Management UI**: Device list, connect/disconnect, MIDI settings screen with live state
- **⚙️ Pad Config Sheet**: Long-press any pad → bottom sheet for volume, pan, playback mode, mute group
- **🔀 Quick Access Panels**: Sampling/MIDI/Mixer/Settings/SampleEditor panels — all wired to real ViewModels
- **↔️ Adaptive Layout**: Portrait bottom-sheet + landscape side-panel rendering paths both fully wired
- **🔄 Sequencer State Sync**: SimpleSequencerViewModel → CompactMainViewModel live state bridge

### 🏗️ **SOLID FOUNDATION**
- **Architecture**: MVVM with Hilt dependency injection, Kotlin coroutines
- **Performance**: Optimized for mobile with efficient memory management
- **Testing**: Unit tests and integration tests for core functionality
- **Build System**: Multi-architecture support (ARM64, ARM32, x86, x86_64)

---

# Comprehensive Development List

-----+

## 🔧 CRITICAL FOUNDATION REPAIRS (Must Fix First)

### C1: Audio Engine Implementation
**Status: ✅ COMPLETED**
- [x] Set up C++ audio engine with Oboe library
- [x] Implement JNI bridge between Kotlin and C++ audio engine
- [x] Create AudioEngineControl interface implementation
- [x] Build low-latency audio rendering pipeline
- [x] Implement sample loading/unloading system
- [x] Add voice management for polyphonic playback
- [x] Create internal audio routing system
- [x] Implement metronome with tempo sync

### C2: MIDI Engine Foundation
**Status: 🔄 PARTIALLY COMPLETED**
- [x] Implement Android MIDI API integration (`midi/` package)
- [x] Create MidiManagerControl interface
- [x] Add USB MIDI device detection and connection (`MidiDeviceManager`, `MidiDeviceScanner`)
- [x] Build MIDI event parsing and generation (`MidiMessageParser`, `MidiInputProcessor`)
- [x] Create MIDI input/output handling (`MidiInputProcessor`, `MidiOutputGenerator`)
- [x] MIDI mapping system (`MidiMappingEngine`, `MidiLearnManager`, `MidiParameterMapper`)
- [x] MIDI diagnostics & error handling (`MidiDiagnosticsManager`, `MidiErrorHandler`)
- [x] Full MIDI settings UI (`MidiSettingsScreen`, `MidiMappingScreen`, `MidiMonitorScreen`)
- [x] MIDI UI wired to live ViewModel state (device list, connect/disconnect buttons)
- [ ] Implement Bluetooth MIDI support
- [ ] Add MIDI clock sync functionality (MidiClockGenerator scaffolded, not yet driving transport)

### C3: File & Project Management
**Status: 🔄 PARTIALLY COMPLETED**
- [x] Implement basic sample repository and persistence
- [x] Set up Android Storage Access Framework (SAF) for file access
- [x] Create sample metadata management
- [x] Build sample pool management system
- [x] Add file browser functionality
- [ ] Implement ProjectManager interface
- [ ] Create project serialization/deserialization
- [ ] Implement project auto-save system
- [ ] Create export functionality for audio mixdowns

### C4: UI Framework & Core Components
**Status: ✅ COMPLETED**
- [x] Set up Jetpack Compose UI framework
- [x] Create MainAppScaffold composable (MainActivity with navigation)
- [x] Build PadGrid component
- [x] Implement sample browser and file picker
- [x] Implement WaveformDisplay component
- [x] Create step sequencer grid components
- [x] Build transport controls and parameter controls
- [ ] Build VirtualKnob component
- [ ] Implement VirtualSlider component
- [ ] Create PianoRoll component
- [ ] Build XYPad component

## 🏗️ PHASE 1: BASIC FUNCTIONALITY REPAIRS

### M1: Basic Sampling & Pad Playback
**Status: ✅ COMPLETED** *(Dependencies: C1, C3, C4)*
- [x] Implement sample recording from microphone
- [x] Create pad assignment system
- [x] Build sample playback engine
- [x] Add velocity sensitivity
- [x] Implement one-shot and loop modes
- [x] Create sample trimming functionality

### M2: Basic Sequencing
**Status: ✅ COMPLETED** *(Dependencies: C1, M1, C3, C4)*
- [x] Build step sequencer engine
- [x] Implement pattern recording
- [x] Create playback synchronization
- [x] Add tempo control
- [x] Build pattern storage system
- [x] Implement basic transport controls (play/stop/record)

## 🎛️ PHASE 2: SOUND DESIGN REPAIRS

### M3: Advanced Drum Track Sound Design
**Status: 🔄 PARTIALLY COMPLETED** *(Dependencies: M1, C1, C4)*
- [x] Implement basic amplitude envelope (ADSR) + pitch envelope
- [x] Create basic LFO system for modulation
- [x] Per-pad volume and pan — **live UI sliders wired to DrumTrackViewModel** (Mixer panel + Pad Config sheet)
- [x] Per-pad playback mode (ONE_SHOT / NOTE_ON_OFF) — configurable via Pad Config sheet
- [x] Per-pad mute group assignment (0–4) — configurable via Pad Config sheet
- [x] DrumProgramEditScreen with VisualEnvelopeEditor for ADSR visualization
- [ ] Add filter per pad (C++ StateVariableFilter exists, not yet exposed in UI)
- [ ] Build sample layering system (SampleLayer model exists, UI not yet implemented)
- [ ] Implement pad-specific effects routing
- [ ] Create sound parameter automation

### M5: Basic Effects Processing
**Status: ⚠️ NOT STARTED** *(Dependencies: C1, C4)*
- [ ] Build effects framework
- [ ] Implement delay effect
- [ ] Create filter effects (low-pass, high-pass, band-pass)
- [ ] Add reverb effect
- [ ] Build effects routing system
- [ ] Create effects parameter control UI

## 🎹 PHASE 3: PITCHED INSTRUMENT REPAIRS

### M4: Keygroup Tracks
**Status: ⚠️ NOT STARTED** *(Dependencies: C1, C3, C4, M2)*
- [ ] Implement multi-sampling support
- [ ] Create piano roll editor
- [ ] Build note-on/note-off handling
- [ ] Add pitch-shifting capabilities
- [ ] Implement keygroup sample mapping
- [ ] Create chromatic playback system

## ⚙️ PHASE 4: ADVANCED EDITING REPAIRS

### M7: Advanced Sample Editing
**Status: ✅ COMPLETED** *(Dependencies: M1, C1, C4)*
- [x] Implement basic sample trimming with visual feedback
- [x] Add destructive sample processing (normalize, reverse)
- [x] Create sample editor with waveform display
- [x] Add sample fade in/out
- [ ] Implement chop mode (manual and threshold)
- [ ] Build sample time-stretching
- [ ] Implement sample pitch-shifting

### M6A: Advanced Sequence Editing
**Status: ✅ COMPLETED** *(Dependencies: M2, C4)*
- [x] Implement copy/paste functionality
- [x] Create nudge functionality
- [x] Build quantization system
- [x] Implement swing/groove templates
- [x] Add sequence length adjustment (8, 16, 24, 32 steps)
- [ ] Add transpose operations

### M12A: Core Performance Features
**Status: 🔄 PARTIALLY COMPLETED** *(Dependencies: M1, M2, C1, C4)*
- [x] Add pad mute/solo system
- [x] Build real-time parameter control
- [x] Implement velocity sensitivity
- [ ] Implement note repeat functionality
- [ ] Create performance effects (stutters, rolls)
- [ ] Implement pad pressure sensitivity

## 🚀 PHASE 5: EXPANSION REPAIRS

### M9: MIDI Output Tracks
**Status: ⚠️ NOT STARTED** *(Requires C2: MIDI Engine)*
- [ ] Implement MIDI sequence recording
- [ ] Create MIDI note editing
- [ ] Add MIDI CC automation
- [ ] Build external hardware sync

### M10: Audio Tracks
**Status: ⚠️ NOT STARTED**
- [ ] Implement linear audio recording
- [ ] Create audio clip editing
- [ ] Add audio track mixing
- [ ] Build audio effects processing

### M11: Channel Mixer
**Status: ⚠️ NOT STARTED**
- [ ] Create comprehensive mixer view
- [ ] Implement track routing
- [ ] Add EQ per channel
- [ ] Build master effects section

## 🔧 SYSTEM-WIDE CONFIGURATION REPAIRS

### Settings & Preferences
**Status: 🔄 PARTIALLY COMPLETED**
- [x] Create basic settings screens for sequencer
- [x] Build audio settings configuration
- [x] Implement basic project defaults system
- [x] Create theme/UI customization (Material Design 3)
- [x] MIDI settings management UI (`MidiSettingsScreen` wired to `MidiSettingsViewModel`)
- [x] MIDI device configuration dialog (`MidiDeviceConfigDialog`)
- [x] MIDI mapping screen (`MidiMappingScreen`) with live-learn support
- [x] MIDI monitor screen (`MidiMonitorScreen`) for real-time event inspection
- [ ] Implement SettingsRepository
- [ ] Create UserPreferencesManager

## 🏛️ ARCHITECTURAL REPAIRS

### Project Structure
**Status: ✅ COMPLETED**
- [x] Set up proper Kotlin module structure
- [x] Implement MVVM/MVI architecture
- [x] Configure Hilt dependency injection
- [x] Set up Kotlin Coroutines for async operations
- [x] Create proper error handling system
- [x] Implement logging framework

### Testing Infrastructure
**Status: 🔄 PARTIALLY COMPLETED**
- [x] Set up JUnit testing framework
- [x] Create MockK/Mockito test utilities
- [x] Build integration test suite (partial)
- [ ] Implement UI testing with Compose
- [ ] Create automated testing pipeline

### Build System
**Status: ✅ COMPLETED**
- [x] Configure Gradle build scripts
- [x] Set up proper dependencies
- [x] Create build variants (debug/release)
- [x] Add proguard/R8 configuration
- [ ] Implement code linting

## 🚨 CURRENT PRIORITY AREAS

1. **Effects Processing System (M5)** - C++ filter/reverb/delay nodes exist; need UI routing & send controls
2. **Complete Project Management (C3)** - Full project save/load (sample pool + sequence + settings)
3. **MIDI Clock Sync (C2 remaining)** - MidiClockGenerator scaffolded; wire to transport play/stop
4. **Bluetooth MIDI (C2 remaining)** - USB done; BT scanning & pairing needed
5. **Advanced Performance Features (M12A)** - Note repeat, performance effects
6. **Pitched Instrument Support (M4)** - Piano roll and keygroup functionality
7. **Sample Layering UI (M3)** - SampleLayer model exists; need layer-stacking UI on pad config

## 📊 CURRENT PROJECT STATUS

### ✅ COMPLETED FOUNDATIONS
- **C1: Audio Engine** - Full C++/Oboe implementation with JNI bridge; waveform thumbnail API added
- **C4: UI Framework** - Jetpack Compose with Material Design 3; adaptive portrait/landscape layouts
- **M1: Basic Sampling** - Recording, pad assignment, playback, trimming
- **M2: Basic Sequencing** - Step sequencer with patterns, transport controls
- **M7: Sample Editing** - Waveform display, trimming, fade in/out, normalize, reverse
- **M6A: Advanced Sequencing** - Copy/paste, nudge, quantize, swing, pattern length, overdub

### 🔄 IN PROGRESS (Current Focus)
- **C2: MIDI Engine** - USB devices, mapping, settings UI all done; BT MIDI + clock sync remaining
- **C3: Project Management** - Sample repository complete; full project serialize/load pending
- **M3: Sound Design** - Per-pad volume/pan/mode/mute-group wired in UI; filters & layering pending
- **M12A: Performance Features** - Basic controls done, need note repeat

### ✅ RECENTLY COMPLETED — UX Wiring Sprint
- **Quick Access Panels** — all 5 panel types (Sampling/MIDI/Mixer/Settings/SampleEditor) wired to live ViewModels
- **Pad Config Bottom Sheet** — long-press pad → volume, pan, playback mode, mute group editor
- **Live Mixer Panel** — per-pad sliders update DrumTrackViewModel in real time
- **MIDI Quick Panel** — device list, connect button, "Open MIDI Settings" nav wired
- **Adaptive layout unification** — portrait (BottomSheet) and landscape (side panel) share same composables
- **Sequencer state bridge** — SimpleSequencerViewModel syncs to CompactMainViewModel via LaunchedEffect

### ⚠️ NEXT PRIORITIES
- **M5: Effects Processing** - Expose C++ filter/reverb/delay nodes to UI; build per-pad effects chain
- **C3: Full Project Save/Load** - Serialize project (sequences + sample pool + pad settings) to disk
- **C2: BT MIDI + Clock Sync** - Complete remaining MIDI engine pieces
- **M4: Pitched Instruments** - Piano roll and keygroup functionality
- **Testing Infrastructure** - Compose UI tests; integration tests for audio pipeline

### 🚀 FUTURE EXPANSION
- **M9-M11: Advanced Features** - MIDI tracks, audio tracks, full channel mixer
- **Sample Layering** - SampleLayer model in place; need layer-stacking UI
- **Performance Optimization** - Memory & CPU profiling pass
- **Export** - Audio mixdown export (C3 tail)

## 🎯 SUCCESS METRICS

- [x] **Can record and playback samples** ✅ WORKING
- [x] **Can create and play basic drum patterns** ✅ WORKING
- [x] **Can assign samples to pads with velocity sensitivity** ✅ WORKING
- [x] **Can edit samples with trimming and basic processing** ✅ WORKING
- [x] **Can create step sequences with swing and tempo control** ✅ WORKING
- [x] **Can manage multiple patterns and song arrangements** ✅ WORKING
- [x] **Can configure per-pad volume, pan, mode and mute group** ✅ WORKING (Pad Config sheet)
- [x] **Can see and connect USB MIDI devices** ✅ WORKING (MidiSettingsScreen + Quick MIDI Panel)
- [x] **Can live-mix pad volumes and pans from quick panel** ✅ WORKING (Mixer Panel)
- [ ] **Can apply effects to samples** ⚠️ PENDING (M5 — C++ nodes exist, no UI)
- [ ] **Can save and load complete projects** ⚠️ PARTIAL (C3)
- [ ] **Can use Bluetooth MIDI** ⚠️ PENDING (C2 — BT scanning not yet implemented)
- [ ] **Can perform live with advanced features** ⚠️ PARTIAL (M12A)
- [ ] **Can export audio mixdowns** ⚠️ PENDING (C3)

------+

Gemini, Jules, Kiro, Claude, GPT, Augment, Copilot, and Grok all worked on this project, and I am trying to soak up as much information as possible as I coordinate technology to create 
something incredible.

Our workflow started like this: me and a bot assess the previous code and determine next steps. Those steps are listed out with direct and defined objectives to be passed to Jules. Jules makes a plan, and that plan 
is screenshotted and sent to Gemini to be verified or amended; if amended, we repeat the plan and verification. Jules writes and publishes code to a new branch, that branch is opened in a new Gemini chat for 
assessment and planning, and the development cycle repeats.
now: using 

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
│   ├── audio/                         # Audio engine interface & JNI bridge
│   │   ├── AudioEngineControl.kt      # Interface (all audio ops + waveform/trim/level APIs)
│   │   ├── AudioEngineImpl.kt         # JNI implementation
│   │   └── MicrophoneInput*.kt        # Mic capture interface + impl
│   ├── midi/                          # Full MIDI engine
│   │   ├── MidiManager.kt / MidiManagerControl.kt
│   │   ├── device/                    # USB device scanning & management
│   │   ├── input/                     # MIDI input processor, velocity curves
│   │   ├── output/                    # MIDI output, clock generator, transport
│   │   ├── mapping/                   # MIDI learn, mapping engine, parameter mapper
│   │   ├── integration/               # AudioEngine + Sequencer adapters
│   │   ├── repository/                # Config & mapping persistence
│   │   ├── service/                   # Lifecycle, permission, system init
│   │   ├── diagnostics/               # MIDI diagnostics manager
│   │   ├── error/                     # Error handler, graceful degradation
│   │   ├── performance/               # Performance monitor & optimizer
│   │   └── model/                     # MidiModels, MidiState, MidiConfiguration
│   ├── model/                         # Shared data models
│   │   ├── SharedModels.kt            # PlaybackMode, SnapPosition, PanelType, etc.
│   │   ├── CompactUIModels.kt         # PanelState, layout models
│   │   ├── SamplingModels.kt          # Recording state, sample assignment
│   │   ├── SampleModels.kt / SampleMetadata.kt
│   │   ├── SequenceModels.kt
│   │   ├── SynthModels.kt / LayerModels.kt
│   │   └── Project.kt / ProjectModels.kt
│   ├── features/
│   │   ├── compactui/                 # Main production UI (adaptive layout)
│   │   │   ├── CompactMainScreen.kt   # Root screen — pads, transport, panels, pad config sheet
│   │   │   ├── CompactMainViewModel.kt
│   │   │   ├── AdaptiveBottomSheet.kt # Portrait bottom-sheet panel switcher
│   │   │   ├── QuickAccessPanel.kt    # Landscape side-panel container
│   │   │   ├── QuickAccessPanelContent.kt  # All 5 panel types wired to ViewModels
│   │   │   ├── QuickAccessPanelIntegration.kt
│   │   │   ├── CompactDrumPadGrid.kt
│   │   │   ├── InlineSequencer.kt
│   │   │   ├── TransportControlBar*.kt
│   │   │   ├── CompactRecordingPanelIntegration.kt
│   │   │   ├── CompactPadMidiIntegration.kt
│   │   │   ├── ResponsiveMainLayout.kt / ResponsiveRecordingPanel.kt
│   │   │   ├── PatternManagement.kt / QuickPadAssignmentFlow.kt
│   │   │   ├── LayoutState*.kt / LayoutPresetManager.kt / PreferenceManager.kt
│   │   │   ├── CustomizationPanel.kt / ProjectSettingsScreen.kt
│   │   │   ├── CompactUIPerformanceOptimizer.kt
│   │   │   ├── animations/            # Spring/tween panel transitions, micro-interactions
│   │   │   ├── accessibility/         # High-contrast, keyboard nav, screen reader support
│   │   │   ├── error/                 # Audio engine recovery, permission manager, storage
│   │   │   └── performance/           # Frame-rate & memory monitors, perf warning UI
│   │   ├── drumtrack/                 # Drum pad state & settings
│   │   │   ├── DrumTrackViewModel.kt  # padSettingsMap StateFlow, updatePadSettings()
│   │   │   ├── model/                 # PadSettings.kt, DrumTrackModels.kt
│   │   │   ├── edit/                  # DrumProgramEditScreen, VisualEnvelopeEditor
│   │   │   └── ui/                    # DrumPadScreen.kt
│   │   ├── sequencer/                 # Step sequencer
│   │   │   ├── SimpleSequencerViewModel.kt  # sequencerState StateFlow
│   │   │   ├── SequencerScreen.kt / SequencerAudioEngineAdapter.kt
│   │   │   ├── PatternManager.kt / PatternChainEditor.kt / SongModeManager.kt
│   │   │   ├── TimingEngine.kt / PrecisionTimingEngine.kt
│   │   │   ├── RecordingEngine.kt / OverdubManager.kt
│   │   │   └── (+ groove, undo/redo, export, tutorial, perf monitor)
│   │   ├── sampling/                  # Recording & sample management
│   │   │   ├── SamplingViewModel.kt   # isRecording, peakLevel, formattedDuration, availableSamples
│   │   │   ├── SamplingScreen.kt / SampleBrowser.kt
│   │   │   ├── RecordingControls.kt / RecordingWorkflowUI.kt
│   │   │   ├── SampleEditor.kt / SampleTrimming.kt / SampleProcessing.kt
│   │   │   ├── PadGrid.kt / PadConfigurationDialog.kt
│   │   │   └── (+ import, cache, metadata editor, preview, usage tracker)
│   │   ├── midi/ui/                   # MIDI settings screens
│   │   │   ├── MidiSettingsScreen.kt / MidiSettingsViewModel.kt
│   │   │   ├── MidiMappingScreen.kt / MidiMappingViewModel.kt
│   │   │   ├── MidiMonitorScreen.kt / MidiMonitorViewModel.kt
│   │   │   ├── MidiDeviceConfigDialog.kt
│   │   │   └── MidiPermissionDialog.kt
│   │   ├── sampleeditor/              # Deep sample editor (dedicated screen)
│   │   │   ├── SampleEditScreen.kt    # Waveform trim UI, fade, normalize, reverse
│   │   │   ├── SampleEditViewModel.kt
│   │   │   └── SampleEditViewModelFactory.kt
│   │   ├── sampler/                   # Sample library management
│   │   │   ├── SamplerScreen.kt / SamplerViewModel.kt
│   │   │   └── permissions/MicrophonePermissionHelper.kt
│   │   └── debug/                     # Development testing tools
│   │       └── DebugScreen.kt
│   ├── commands/                      # Undo/redo command pattern
│   ├── di/                            # Hilt modules (audio, midi, sequencer, project)
│   ├── domain/                        # Domain interfaces
│   ├── project/                       # ProjectManager implementation
│   └── ui/                            # Shared theme, components
└── cpp/
    ├── AudioEngine.{h,cpp}            # Core audio engine (Oboe)
    ├── native-lib.cpp                 # JNI bindings
    ├── EnvelopeGenerator.{h,cpp}      # ADSR processing
    ├── LfoGenerator.{h,cpp}           # LFO modulation
    ├── StateVariableFilter.{h,cpp}    # Filter (LP/HP/BP)
    ├── SynthEngine.{h,cpp}            # Synthesis engine
    ├── avst/                          # AVST plugin system
    └── CMakeLists.txt
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