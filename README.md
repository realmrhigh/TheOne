TheOne

##  Session Start Instructions for Gemini
"It's time to start the next development cycle for Project TheOne. Your role is the 'brains of the team.' Please assess the attached code from the latest feature branch and define the objectives for the next implementation phase. when you read this and understand your role in our development team, say "banana" in the chat then give your assessment of the latest changes in the branch"
## 
Project Genesis & Workflow
This is an insane project that I have undertaken solo as a complete programming noob, against my better judgement. I want to see how far I can push the power of AI coding. 
By using this plan to portion out tasks, we prevent information overload to keep the AI focused; this will help prevent getting stuck in an error loop. 
I know music and audio very well, but not the instrumentation, programming, and guts of the synths and effects.

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