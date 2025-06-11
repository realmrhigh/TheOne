# TheOne
trying this with a plan.
ok, we are having my robot Jules doing our coding, he is a master and knows we are working at production quality so make him a list of things to do and he will implement it perfectly. you(Gemini) are the brains, 
you can do the deep research on the code and present the options to me. my(Stanton) role is to be the conductor of this chaos.

MPC Android App: Build Map & Development FrameworkI. IntroductionThis document outlines the build map and development framework for creating an MPC-style sampler and drum machine application specifically for the 
Android platform. The goal is to provide a clear, phased approach for a team of AI developers, highlighting module dependencies and a common architectural framework to facilitate parallel development and seamless 
integration.II. Core Architectural PrinciplesTo ensure a scalable, maintainable, and collaborative development process, the following architectural principles will be adopted:Modular Design: The application will be
broken down into distinct, self-contained modules (Core Engines, Feature Modules). Each module will have well-defined responsibilities and interfaces.Primary Language: Kotlin will be the primary programming language
due to its modern features, conciseness, and official Android support.Audio Engine Core: For performance-critical audio processing, C++ will be used for the core of the Audio Engine, accessed via JNI (Java Native 
Interface). Android's Oboe library (which wraps AAudio and OpenSL ES) is highly recommended for achieving low-latency audio.UI Framework: Jetpack Compose will be used for building the user interface, enabling a modern,
declarative, and efficient UI development workflow.Architectural Pattern: MVVM (Model-View-ViewModel) or MVI (Model-View-Intent) will be implemented to separate concerns:Model: Contains the data, business logic,
and core engine interactions (e.g., AudioEngine, SequencerLogic).View: Represents the UI (Jetpack Compose Composables).ViewModel/Intent Processor: Acts as the bridge, preparing and managing data for the View, and 
handling user interactions and events.Dependency Injection: Hilt (or Koin) will be used for managing dependencies between components and modules. This promotes loose coupling and testability.Asynchronous Operations: 
Kotlin Coroutines will be used extensively for managing background tasks, I/O operations (file access, MIDI), and audio processing calls to prevent UI freezes.Defined APIs: Clear and stable APIs will be defined for
communication between modules, especially for the Core Engines.Version Control: Git will be used for version control, with a feature-branch workflow.Testing: Unit tests (JUnit, Mockito/MockK), integration tests, 
and UI tests (Espresso or Compose testing utilities) will be integral to the development process.III. Core Engine Modules (Foundation - Highest Priority)These foundational modules must be developed first as most other 
features depend on them. AI developers assigned to these should focus on stability, performance, and clear API definitions.C1: Audio EngineResponsibilities: Low-latency audio playback and recording, sample loading
(from memory and disk streaming via Android's Storage Access Framework), audio routing, basic mixing (gain, pan, summing), metronome implementation.Technology: C++ (core DSP), Oboe/AAudio (Android integration), 
JNI bridge to Kotlin.
Key APIs (Examples):initialize(sampleRate, bufferSize)loadSample(filePath: String, sampleId: String): BooleanplaySample(sampleId: String, trackId: String, velocity: Float, pitch: Float, pan: Float)stopSample(sampleId: String, trackId: String)startRecording(inputDevice: String, filePath: String)stopRecording()setTrackVolume(trackId: String, volume: Float)setTrackPan(trackId: String, pan: Float)getAudioLevels(trackId: String): FloatArraysetMetronomeState(isEnabled: Boolean, bpm: Float, soundPath: String)Dependencies: None initially.
C2:MIDI EngineResponsibilities: Handling MIDI input/output from USB and Bluetooth MIDI devices, parsing and generating MIDI events, MIDI clock send/receive.Technology: Kotlin, Android MIDI API (android.media.midi).
Key APIs (Examples):listMidiInputDevices(): List<MidiDeviceInfo>listMidiOutputDevices(): List<MidiDeviceInfo>openMidiInputDevice(deviceId: String, listener: MidiInputListener)openMidiOutputDevice(deviceId: String): MidiOutputPortsendMidiEvent(outputPort: MidiOutputPort, event: ByteArray)startMidiClockOutput(outputPort: MidiOutputPort, bpm: Float)stopMidiClockOutput(outputPort: MidiOutputPort)setMidiClockListener(listener: MidiClockListener)Dependencies: None initially.
C3: File & Project Management SystemResponsibilities: Defining project structure (app-specific format, likely JSON or similar), saving/loading projects, managing the Sample Pool (metadata, references to audio files), browser logic for navigating internal device storage, SD cards, and USB storage (using Android Storage Access Framework - SAF), file operations (load, delete, info).Technology: Kotlin, Android Storage Access Framework, JSON serialization library (e.g., kotlinx.serialization or Gson).
Key APIs (Examples):createNewProject(name: String, template: String?): ProjectloadProject(uri: Uri): Project?saveProject(project: Project, uri: Uri?): BooleanaddSampleToPool(filePath: Uri): SampleMetadata?getSampleFromPool(sampleId: String): SampleMetadata?listSamplesInPool(projectId: String): List<SampleMetadata>browseStorage(uri: Uri?, filter: FileFilter): List<FileItem>Dependencies: None initially.
C4: UI Framework & Core UI ComponentsResponsibilities: Establishing the base UI structure (main layout, navigation patterns), creating common reusable UI elements (virtual knobs, sliders, buttons, dialogs), developing a responsive Virtual Pad grid component.Technology: Kotlin, Jetpack Compose.
Key Components (Examples):MainAppScaffold(topBar: @Composable () -> Unit, bottomBar: @Composable () -> Unit, content: @Composable () -> Unit)VirtualKnob(value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>)PadGrid(numPads: Int, padStates: List<PadState>, onPadPress: (Int) -> Unit, onPadRelease: (Int) -> Unit)FileBrowserView(currentPath: String, files: List<FileItem>, onFileSelected: (FileItem) -> Unit, onNavigateUp: () -> Unit)Dependencies: None initially.
IV. Phased Feature Modules (Build Order & Dependencies)Modules will be developed in phases to ensure foundational elements are in place before more complex features are added.Phase 1: Basic Drum Machine FunctionalityM1: Basic Sampling & Pad PlaybackDescription: Core functionality for recording samples and playing them back via virtual pads.Dependencies: C1 (Audio Engine), C3 (File & Project Management), C4 (UI Framework).
Sub-Modules:M1.1: Sampler (Recording): UI and logic for selecting input (mic, USB audio if configured), recording audio, saving it (via C3), and adding to Sample Pool. Basic naming.
M1.2: Drum Track Engine (Simple Playback): Logic to assign samples from Pool (C3) to virtual pads. Basic one-shot playback via C1. Polyphony management.
M1.3: Basic Sample Edit UI (Trim/Loop): Simple UI (using C4) to display a waveform (can be a static representation initially), set start/end points for non-destructive playback. Logic interacts with C1 for playback parameters.M2: Basic Sequencing (Drum Track Focus)Description: Creating, recording, and playing back simple drum patterns.Dependencies: C1 (Audio Engine), C2 (MIDI Engine - for pad input timing), M1 (Drum Track Engine), C3 (for saving/loading sequences within projects), C4 (UI Framework).
Sub-Modules:M2.1: Sequence Data Model: Define Kotlin data classes for sequences, tracks (initially just Drum Tracks), events (note, time, velocity), and sequence parameters (BPM, length).
M2.2: Real-time Pad Recording: Logic to capture pad presses (from M1.2) into the active sequence (M2.1), respecting BPM and quantization (basic).
M2.3: Step Sequencer Logic & UI: Grid-based UI (C4) for inputting drum events. Logic to modify sequence data (M2.1).
M2.4: Sequence Playback Engine: Logic to iterate through sequence data (M2.1) and trigger pad playback (M1.2 via C1) at correct timings. Loop functionality.
M2.5: Basic Sequence Editing UI: UI (C4) for erasing events, clearing sequences.Phase 2: Enhancing Drum Machine Sound & ControlM3: Advanced Drum Track Sound DesignDescription: Adding more sound shaping capabilities to drum pads.Dependencies: M1 (Drum Track Engine), C1 (Audio Engine), C4 (UI Framework).
Sub-Modules:M3.1: Drum Program Edit (Layers, Envelopes, LFO): Extend M1.2 to support multiple sample layers per pad (cycle, velocity, random). Implement Amp/Filter/Pitch envelopes and basic LFOs within C1, and provide UI (C4) to control these per pad/layer.
M3.2: Pad Mixer (Basic Volume/Pan): UI (C4) and logic to control volume and pan for individual pads within a drum track, interacting with C1.M5: Basic Effects ProcessingDescription: Introducing a framework for audio effects and a few initial effects.Dependencies: C1 (Audio Engine), C4 (UI Framework).
Sub-Modules:M5.1: Insert Effects Framework: Logic within C1 to allow chaining of audio processing units (effects). Data structures for effect instances and parameters.
M5.2: Initial Effects (e.g., Delay, Filter, Reverb): Implement a few core DSP effects within C1. Provide UI (C4) for selecting effects and editing their parameters.
M5.3: Integration with Drum Tracks/Pads: Allow inserting effects (from M5.2) on individual pads (via M3.2) or entire drum tracks.Phase 3: Introducing Pitched InstrumentsM4: Keygroup TracksDescription: Adding support for pitched, multi-sampled instruments.Dependencies: C1 (Audio Engine), C3 (File & Project Management), C4 (UI Framework), M2 (for sequencer integration).
Sub-Modules:M4.1: Keygroup Engine: Logic in C1 and Kotlin to handle pitched playback of multi-samples across a key range.
M4.2: Keygroup Program Edit: UI (C4) and logic similar to M3.1 but for keygroups (layers, envelopes, LFOs, tuning per keygroup).
M4.3: Piano Roll Editor UI: Develop a piano roll style editor (C4) for inputting and editing notes for Keygroup tracks. This extends concepts from M2.3.
M4.4: Integration into Sequencer (M2): Extend M2.1 (Sequence Data Model) and M2.4 (Playback Engine) to support Keygroup tracks alongside Drum Tracks.Phase 4: Advanced Editing & WorkflowM7: Advanced Sample EditingDescription: More sophisticated sample manipulation tools.Dependencies: M1.3 (Basic Sample Edit), C1 (Audio Engine), C4 (UI Framework).
Sub-Modules:M7.1: Chop Mode (Manual, Threshold): UI (C4) and logic (interacting with C1 for audio analysis if needed for threshold) to slice samples into regions.M7.2: Destructive Processes (e.g., Normalize, Reverse, Fade): Implement these DSP functions in C1 and provide UI (C4) to apply them. (Note: Manage undo carefully or work on copies).M6A: Advanced Sequence EditingDescription: More powerful tools for manipulating sequence data.Dependencies: M2, M4, C4.Sub-Modules: UI (C4) and logic for operations like copy/paste bars/events, transpose MIDI events, nudge, etc.M12A: Core Performance 
FeaturesDescription: Real-time performance tools.Dependencies: C2 (MIDI Engine), M1 (Drum Track), M2 (Sequencer), C1 (Audio Engine), C4.Sub-Modules:M12.1: Note Repeat: Logic and UI for repeating pad hits at various time divisions.M12.2: Pad Mutes/Solos: UI and logic for muting/soloing individual pads within drum tracks.
Phase 5: Expanding Track Types & MixingM9: MIDI Output TracksDescription: Sequencing external MIDI hardware/apps.Dependencies: C2 (MIDI Engine), M2 (Sequencer framework), M4.3 (Piano Roll UI), C4.Sub-Modules: Extend M2 to handle MIDI tracks, sending MIDI data out via C2. UI for MIDI output configuration per track.
M10: Audio Tracks (Linear Recording/Playback)Description: Support for recording and playing back linear audio alongside sequences.Dependencies: C1 (Audio Engine), M2 (for timeline sync), C3 (File Management), C4.Sub-Modules: Logic for recording audio streams into files, and playing them back aligned with the project timeline. UI for audio track management and simple region editing.
M11: Channel MixerDescription: A comprehensive mixer view for all track types.Dependencies: All track types (M1, M4, M9, M10), M5 (Effects), C1 (Audio Engine), C4.Sub-Modules: UI (C4) displaying faders, pan, mute/solo, send levels, and insert effect slots for each track, submix, and master. Logic to control C1 parameters.
Phase 6: Internal Plugins (Stretch Goal / Advanced Phase)M8: Plugin Instrument HostingDescription: Framework for hosting internal synth/instrument plugins.Dependencies: C1 (Audio Engine), C2 (MIDI Engine), C4 (UI Framework), M2 (Sequencer integration).
Sub-Modules:M8.1: Internal Plugin Host Architecture: Define an API/SDK for internal plugins to interact with the audio engine and sequencer.M8.2: Example Internal Synth Plugin: Develop one or two simple internal synth plugins (e.g., basic subtractive synth) adhering to the M8.1 API. UI for plugin parameters.M8.3: Integration into Sequencer (M2): Allow Plugin Tracks in the sequencer.Phase 7: Arrangement & Song StructureM6B: Arrangement View (Linear Timeline)Description: DAW-style linear timeline for arranging sequences and audio clips.Dependencies: M2, M4, M8 (if implemented), M9, M10, 
C4.Sub-Modules: UI (C4) for arranging sequence blocks and audio regions on a timeline. Logic to control playback flow.M6C: Song Mode (Sequence Chaining)Description: Simple mode to chain sequences together for song playback.Dependencies: M2, C4.Sub-Modules: UI (C4) to create a list of sequences with repeat counts. Playback logic.Phase 8: Advanced Performance & ControlM12B: Arpeggiator, LooperDescription: More advanced real-time performance tools.Dependencies: C1, C2, M2, C4.Sub-Modules: Implement arpeggiator logic for MIDI tracks/Keygroups. Implement a real-time audio looper. UI for both.M13: MIDI Learn, Macro ControlsDescription: Customizing control over app parameters.Dependencies: C2, various modules whose parameters will be controlled, C4.Sub-Modules: MIDI Learn UI and logic to map incoming MIDI CCs to app parameters. UI for on-screen macro knobs and assignment.
Future Phases (Post Core Build - Examples)Stems Separation (Requires significant AI/DSP expertise, potentially a cloud service or on-device model).Auto-Sampler.Expanded Effects and Plugin Library.Cloud integration for project backup/sharing or sample downloads (consider for V2).V. AI Developer Tasking FrameworkTo effectively assign tasks to AI developers:Task Definition:Assign tasks based on Modules (e.g., M1) or Sub-Modules (e.g., M1.1).Clearly state the requirements based on the original feature map and this build map.Specify dependencies (e.g., "M1.1 requires C1 and C3 APIs to be stable").Define API contracts the module needs to consume or expose.Provide data models to be used or extended (from C3 or other modules).Give UI guidelines/mockups if applicable, referencing C4 components.Code Contribution:Developers work on feature branches in Git (e.g., feature/M1.1-sampler-recording).Regular pull requests for review and merging into a develop branch.Testing Requirements:Developers are responsible for writing unit tests for their logic (ViewModels, UseCases, engine components).Integration tests for interactions between their module and its dependencies.Contribute to UI tests for their features.Code Style & Quality:Adhere to Kotlin coding conventions and project-specific style guides.Utilize linters (Android Lint, ktlint) and address warnings.Communication:Regular (automated if possible) status updates on progress.Clear channels for asking questions about API usage or dependencies.VI. Technology Stack SummaryPrimary Language: KotlinAudio Core: C++ (with Oboe/AAudio via JNI)UI: Jetpack ComposeArchitecture: MVVM / MVIDependency Injection: Hilt (or Koin)Asynchronous Programming: Kotlin CoroutinesMIDI: Android MIDI APIStorage: Android Storage Access Framework (SAF)Build System: GradleVersion Control: GitTesting: JUnit, Mockito/MockK, Espresso/Compose Test UtilitiesThis build map provides a structured approach to developing your complex MPC application. By breaking it down into manageable modules and phases, and by establishing a clear technical framework, your team of AI developers can work more efficiently and collaboratively towards a successful product.
PC Android App: Detailed Build Map & Development Framework
I. Introduction

This document outlines the detailed build map and development framework for an MPC-style sampler and drum machine application on Android. It expands upon the previous version by specifying key data structures, API signatures, UI component properties, and configuration parameters to provide maximum clarity for the AI development team. The aim is to ensure all developers are aligned on the technical specifications of their respective modules.

II. Core Architectural Principles

(These remain the same as the previous document: Modular Design, Kotlin, C++/Oboe for Audio Core, Jetpack Compose, MVVM/MVI, Dependency Injection (Hilt), Coroutines, Defined APIs, Git, Testing.)

III. Key Global Data Models & Enums

These are foundational data structures that will be used across multiple modules. They should be defined in a shared common or core.model module.

Kotlin

// In a shared 'common' or 'core.model' module

// General
enum class PlaybackMode { ONE_SHOT, NOTE_ON_OFF }
enum class LoopMode { OFF, FORWARD, REVERSE, PING_PONG }
enum class QuantizeStrength { OFF, Q50, Q75, Q100 } // Example strengths
enum class TimeDivision(val ticksPerBeat: Int) {
    Beat(96), Half(48), Quarter(24), Eighth(12), Sixteenth(6), ThirtySecond(3),
    Triplet8th(8), Triplet16th(4); // PPQN (Pulses Per Quarter Note) based, e.g., 96 PPQN
}

data class MidiNote(
    val note: Int, // 0-127
    val velocity: Int, // 0-127
    val channel: Int // 0-15
)

// Sample Related
data class SampleMetadata(
    val id: String, // Unique ID
    val name: String,
    val filePathUri: String, // URI to the actual audio file
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int, // 1 for mono, 2 for stereo
    val detectedBpm: Float?,
    val detectedKey: String?, // e.g., "Am"
    var userBpm: Float?,
    var userKey: String?,
    var rootNote: Int = 60 // MIDI C3
)

data class SampleSlice(
    val id: String, // Unique ID for the slice
    val sampleId: String, // Parent sample
    val startMs: Long,
    val endMs: Long,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val loopMode: LoopMode = LoopMode.OFF
)

// Effect Related
data class EffectParameter(
    val id: String, // e.g., "delay_time_ms"
    val name: String, // "Delay Time"
    val value: Float, // Normalized 0.0f to 1.0f or actual value
    val rangeMin: Float,
    val rangeMax: Float,
    val unit: String // "ms", "%", "dB"
)

data class EffectInstance(
    val id: String, // Unique instance ID
    val effectTypeId: String, // e.g., "reverb_plate", "delay_pingpong"
    val name: String, // User-defined name or default
    var bypass: Boolean = false,
    val parameters: MutableMap<String, EffectParameter>
)

// Project and Sequence
data class Project(
    val id: String,
    var name: String,
    val projectFilePathUri: String?,
    var globalBpm: Float = 120.0f,
    var globalKey: String? = null,
    val samplePool: MutableMap<String, SampleMetadata> = mutableMapOf(),
    val sequences: MutableList<Sequence> = mutableListOf(),
    val tracks: MutableList<Track> = mutableListOf(), // For arrangement mode
    // Other global settings: swing, metronome settings, etc.
    var masterVolume: Float = 1.0f,
    var masterPan: Float = 0.0f,
    val masterEffects: MutableList<EffectInstance> = mutableListOf()
)

data class Sequence(
    val id: String,
    var name: String,
    var bpm: Float, // Can override project BPM
    var barLength: Int = 4, // Number of bars
    var timeSignatureNumerator: Int = 4,
    var timeSignatureDenominator: Int = 4,
    var loop: Boolean = true,
    var loopStartBar: Int = 1,
    var loopEndBar: Int = barLength,
    var transposition: Int = 0, // Semitones
    val events: MutableList<Event> = mutableListOf(), // MIDI events, automation
    var automationData: MutableMap<String, List<AutomationPoint>> = mutableMapOf() // ParamID -> List of points
)

// Track Types
sealed class Track(
    open val id: String,
    open var name: String,
    open var outputRouting: String, // e.g., "Master", "Submix1", "Output3/4"
    open var volume: Float = 1.0f, // 0.0 to 2.0 (double gain)
    open var pan: Float = 0.0f, // -1.0 (L) to 1.0 (R)
    open var solo: Boolean = false,
    open var mute: Boolean = false,
    open var armForRecord: Boolean = false,
    open var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    open var sendLevels: MutableMap<String, Float> = mutableMapOf() // SendBusID -> Level (0.0 to 1.0)
)

data class AutomationPoint(
    val timeTicks: Long, // Absolute time in sequence ticks
    val value: Float // Parameter value (often normalized 0-1)
)

data class Event(
    val id: String,
    val trackId: String,
    val startTimeTicks: Long, // Based on TimeDivision, e.g., 96 PPQN
    val type: EventType
)

sealed class EventType {
    data class NoteOn(
        val note: Int, // MIDI note number
        val velocity: Int,
        val durationTicks: Long,
        val probability: Float = 1.0f, // 0.0 to 1.0
        val ratchetDivisions: Int = 1 // 1 = no ratchet, 2 = double trigger, etc.
    ) : EventType()

    data class PadTrigger( // For Drum Tracks, maps to a specific pad
        val padId: String, // e.g., "Pad1" to "Pad16"
        val velocity: Int,
        val durationTicks: Long, // Note: drum samples often ignore MIDI note off
        val probability: Float = 1.0f,
        val ratchetDivisions: Int = 1
    ) : EventType()

    // Could add CC, PitchBend, Aftertouch, etc.
    data class ParameterChange(
        val parameterId: String, // e.g., "track_volume", "filter_cutoff_pad5"
        val value: Float
    ) : EventType() // For step automation
}

data class PadSettings(
    val id: String, // e.g., "Pad1"
    var sampleId: String?, // From SamplePool
    var sliceId: String?, // If playing a slice
    var playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT,
    var tuningCoarse: Int = 0, // Semitones
    var tuningFine: Int = 0, // Cents
    var volume: Float = 1.0f,
    var pan: Float = 0.0f,
    var muteGroup: Int = 0, // 0 = none
    var polyphony: Int = 16, // Max simultaneous notes for this pad
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings(attackMs = 5f, decayMs = 100f, sustainLevel = 1f, releaseMs = 200f),
    var filterEnvelope: EnvelopeSettings? = null,
    var pitchEnvelope: EnvelopeSettings? = null,
    var lfos: MutableList<LFOSettings> = mutableListOf(),
    var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    var outputRouting: String = "Track" // "Track" means use parent track's output, or direct output
)

data class EnvelopeSettings(
    var type: EnvelopeType = EnvelopeType.ADSR, // AD, AHDS, ADSR
    var attackMs: Float,
    var holdMs: Float? = null, // For AHDS/ADSR
    var decayMs: Float,
    var sustainLevel: Float? = null, // For AHDS/ADSR (0.0 to 1.0)
    var releaseMs: Float,
    var velocityToAttack: Float = 0f, // Mod depth
    var velocityToLevel: Float = 0f
)
enum class EnvelopeType { AD, AHDS, ADSR }

data class LFOSettings(
    val id: String,
    var waveform: LfoWaveform = LfoWaveform.SINE,
    var rateHz: Float = 1.0f,
    var syncToTempo: Boolean = false,
    var tempoDivision: TimeDivision = TimeDivision.Quarter,
    var destinations: MutableMap<String, Float> = mutableMapOf() // ParamID -> ModDepth
)
enum class LfoWaveform { SINE, TRIANGLE, SQUARE, SAW_UP, SAW_DOWN, RANDOM }


data class DrumTrack(
    override val id: String,
    override var name: String = "Drum Track",
    val pads: MutableMap<String, PadSettings> = mutableMapOf(), // Keyed by PadID (e.g., "Pad1" to "Pad16")
    // ... other DrumTrack specific properties
    override var outputRouting: String = "Master",
    override var volume: Float = 1.0f,
    override var pan: Float = 0.0f,
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false,
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    override var sendLevels: MutableMap<String, Float> = mutableMapOf()
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

data class KeygroupSettings(
    val id: String,
    var sampleId: String,
    var sliceId: String? = null, // If entire keygroup is based on one slice
    var keyRangeMin: Int, // MIDI note
    var keyRangeMax: Int,
    var velocityRangeMin: Int = 0,
    var velocityRangeMax: Int = 127,
    var rootNoteForKey: Int, // Original pitch of the sample for this keygroup
    var tuningCoarse: Int = 0,
    var tuningFine: Int = 0,
    var volume: Float = 1.0f,
    var pan: Float = 0.0f,
    var ampEnvelope: EnvelopeSettings = EnvelopeSettings(attackMs = 5f, decayMs = 0f, sustainLevel = 1f, releaseMs = 200f), // Sustain for pitched
    var filterEnvelope: EnvelopeSettings? = null,
    var pitchEnvelope: EnvelopeSettings? = null
)

data class KeygroupTrack(
    override val id: String,
    override var name: String = "Keygroup Track",
    val keygroups: MutableList<KeygroupSettings> = mutableListOf(),
    var polyphony: Int = 16,
    var globalLfos: MutableList<LFOSettings> = mutableListOf(),
    var portamentoTimeMs: Float = 0f,
    // ... other KeygroupTrack specific properties
    override var outputRouting: String = "Master",
    override var volume: Float = 1.0f,
    override var pan: Float = 0.0f,
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false,
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    override var sendLevels: MutableMap<String, Float> = mutableMapOf()
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

data class PluginTrack(
    override val id: String,
    override var name: String = "Plugin Track",
    var pluginId: String, // Identifier for the internal plugin
    var pluginState: ByteArray? = null, // Serialized state of the plugin
    // ... other PluginTrack specific properties
    override var outputRouting: String = "Master",
    override var volume: Float = 1.0f,
    override var pan: Float = 0.0f,
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false,
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    override var sendLevels: MutableMap<String, Float> = mutableMapOf()
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

data class MidiTrack(
    override val id: String,
    override var name: String = "MIDI Track",
    var midiOutputPortId: String?, // Target external MIDI port
    var midiChannel: Int = 0, // 0-15
    // ... other MidiTrack specific properties
    override var outputRouting: String = "Master", // Typically N/A for MIDI, but for consistency
    override var volume: Float = 1.0f, // Can represent velocity scaling
    override var pan: Float = 0.0f, // Can map to a CC
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false,
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(), // MIDI effects (arpeggiator, chord)
    override var sendLevels: MutableMap<String, Float> = mutableMapOf() // N/A
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

data class AudioTrack(
    override val id: String,
    override var name: String = "Audio Track",
    val audioClips: MutableList<AudioClip> = mutableListOf(),
    // ... other AudioTrack specific properties
    override var outputRouting: String = "Master",
    override var volume: Float = 1.0f,
    override var pan: Float = 0.0f,
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false,
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    override var sendLevels: MutableMap<String, Float> = mutableMapOf()
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

data class AudioClip(
    val id: String,
    val filePathUri: String,
    val startTimeTicks: Long, // Position on the track/arrangement timeline
    val offsetInFileMs: Long = 0, // Start playing from this offset within the audio file
    val durationTicks: Long, // How long this clip plays for in the timeline
    var gain: Float = 1.0f,
    var warpEnabled: Boolean = false
)

// Bus Tracks (Submix, Return)
data class BusTrack(
    override val id: String, // e.g., "Submix1", "ReturnA"
    override var name: String,
    // ...
    override var outputRouting: String = "Master",
    override var volume: Float = 1.0f,
    override var pan: Float = 0.0f,
    override var solo: Boolean = false,
    override var mute: Boolean = false,
    override var armForRecord: Boolean = false, // Typically false for busses
    override var insertEffects: MutableList<EffectInstance> = mutableListOf(),
    override var sendLevels: MutableMap<String, Float> = mutableMapOf() // Sends from busses are less common but possible
) : Track(id, name, outputRouting, volume, pan, solo, mute, armForRecord, insertEffects, sendLevels)

IV. Core Engine Modules (Foundation - Detailed)

C1: Audio Engine (C++ with Oboe/AAudio via JNI, Kotlin interface)

Responsibilities: Low-latency audio rendering, sample playback (one-shot, looped, sliced), disk streaming, recording, internal audio routing (tracks, pads, effects), metronome.
Key Internal C++ Concepts (not directly exposed via JNI but inform design):
AudioGraph: Manages nodes (voices, effects, mixers).
VoiceManager: Handles polyphony for sample players, synths.
SamplePlayerNode: Plays samples/slices with pitch, envelopes, LFOs.
EffectNode: Abstract base for DSP effects.
MixerNode: Sums audio streams, applies gain/pan.
AudioBuffer: Data structure for audio blocks.
StreamManager: Handles disk streaming for long samples.
Kotlin Interface (JNI wrapper - AudioEngineControl.kt):
Kotlin

interface AudioEngineControl {
    // Initialization & Config
    suspend fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    suspend fun shutdown()
    fun isInitialized(): Boolean
    fun getReportedLatencyMillis(): Float

    // Metronome
    suspend fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?)
    suspend fun setMetronomeVolume(volume: Float) // 0.0 to 1.0

    // Sample Loading & Management (Handles Caching internally)
    suspend fun loadSampleToMemory(sampleId: String, filePathUri: String): Boolean
    suspend fun loadSampleForStreaming(sampleId: String, filePathUri: String): Boolean
    suspend fun unloadSample(sampleId: String)
    fun isSampleLoaded(sampleId: String): Boolean

    // Playback Control
    // `noteInstanceId` is a unique ID for this specific sounding note, to allow stopping/modifying it later
    suspend fun playPadSample(noteInstanceId: String, trackId: String, padId: String, sampleId: String, sliceId: String?, velocity: Float, playbackMode: PlaybackMode, coarseTune: Int, fineTune: Int, pan: Float, volume: Float, ampEnv: EnvelopeSettings, filterEnv: EnvelopeSettings?, pitchEnv: EnvelopeSettings?, lfos: List<LFOSettings>): Boolean
    suspend fun playKeygroupSample(noteInstanceId: String, trackId: String, keygroupProgramId: String, midiNote: Int, velocity: Float, coarseTune: Int, fineTune: Int, pan: Float, volume: Float, ampEnv: EnvelopeSettings, filterEnv: EnvelopeSettings?, pitchEnv: EnvelopeSettings?, lfos: List<LFOSettings>): Boolean // Keygroup program would define which sample to play based on note/velocity
    suspend fun stopNote(noteInstanceId: String, releaseTimeMs: Float?) // releaseTimeMs can override envelope
    suspend fun stopAllNotes(trackId: String?, immediate: Boolean)

    // Recording
    suspend fun startAudioRecording(filePathUri: String, inputDeviceId: String?): Boolean // null for default mic
    suspend fun stopAudioRecording(): SampleMetadata? // Returns metadata of the recorded sample
    fun getRecordingLevelPeak(): Float // Normalized 0-1
    fun isRecordingActive(): Boolean

    // Track & Pad Level Controls (Real-time parameters)
    suspend fun setTrackVolume(trackId: String, volume: Float)
    suspend fun setTrackPan(trackId: String, pan: Float)
    suspend fun setTrackMute(trackId: String, isMuted: Boolean)
    suspend fun setPadVolume(trackId: String, padId: String, volume: Float) // For drum tracks
    suspend fun setPadPan(trackId: String, padId: String, pan: Float)
    suspend fun setPadMute(trackId: String, padId: String, isMuted: Boolean)

    // Effects Processing (Simplified - actual DSP in C++)
    suspend fun addTrackEffect(trackId: String, effectInstance: EffectInstance): Boolean
    suspend fun removeTrackEffect(trackId: String, effectInstanceId: String): Boolean
    suspend fun updateTrackEffectParameter(trackId: String, effectInstanceId: String, parameterId: String, value: Float): Boolean
    suspend fun bypassTrackEffect(trackId: String, effectInstanceId: String, bypass: Boolean): Boolean
    // Similar methods for Pad effects, Master effects, Send effects

    // Transport Control
    suspend fun setTransportBpm(bpm: Float)
    // (More transport controls might live in SequencerEngine and call setTransportBpm, etc.)

    // Disk Streaming Control
    suspend fun setStreamCacheSize(mb: Int)
}
Key Configurable "Variables": Audio buffer size, sample rate, low-latency path preference, stream cache size.
C2: MIDI Engine (Kotlin, using android.media.midi)

Responsibilities: MIDI device discovery & connection (USB, Bluetooth), routing MIDI events to/from app, MIDI clock sync.
Key Classes & Interfaces:
Kotlin

data class MidiDeviceInfo(val id: String, val name: String, val type: MidiDeviceType)
enum class MidiDeviceType { USB, BLUETOOTH, VIRTUAL }

interface MidiInputListener {
    fun onNoteOn(deviceInfo: MidiDeviceInfo, channel: Int, note: Int, velocity: Int)
    fun onNoteOff(deviceInfo: MidiDeviceInfo, channel: Int, note: Int, velocity: Int)
    fun onControlChange(deviceInfo: MidiDeviceInfo, channel: Int, controller: Int, value: Int)
    // ... other MIDI messages (Pitch Bend, Program Change, Sysex, etc.)
    fun onMidiClockTick(deviceInfo: MidiDeviceInfo)
    fun onMidiClockStart(deviceInfo: MidiDeviceInfo)
    fun onMidiClockStop(deviceInfo: MidiDeviceInfo)
    fun onMidiTimeCode(deviceInfo: MidiDeviceInfo, frame: Int, second: Int, minute: Int, hour: Int, type: MTCFrameRate)
}
enum class MTCFrameRate { FPS24, FPS25, FPS29_97_DROP, FPS30 }


interface MidiEngineControl {
    fun initialize()
    fun getAvailableInputDevices(): List<MidiDeviceInfo>
    fun getAvailableOutputDevices(): List<MidiDeviceInfo>

    suspend fun openInputDevice(deviceId: String, listener: MidiInputListener): Boolean
    suspend fun closeInputDevice(deviceId: String)
    fun getOpenInputDevices(): List<MidiDeviceInfo>

    suspend fun openOutputDevice(deviceId: String): MidiOutputPortHandle? // Handle to send MIDI
    suspend fun closeOutputDevice(deviceId: String)
    fun getOpenOutputDevices(): List<MidiDeviceInfo>

    suspend fun sendNoteOn(portHandle: MidiOutputPortHandle, channel: Int, note: Int, velocity: Int)
    suspend fun sendNoteOff(portHandle: MidiOutputPortHandle, channel: Int, note: Int, velocity: Int)
    suspend fun sendControlChange(portHandle: MidiOutputPortHandle, channel: Int, controller: Int, value: Int)
    // ... other send methods

    // MIDI Clock
    suspend fun startSendingClock(portHandle: MidiOutputPortHandle, bpm: Float)
    suspend fun stopSendingClock(portHandle: MidiOutputPortHandle)
    suspend fun setIncomingClockListener(listener: MidiClockListener?) // For syncing to external clock

    // MIDI Learn
    fun startMidiLearn(callback: (MidiEvent) -> Unit) // MidiEvent is a generic wrapper
    fun stopMidiLearn()
}
typealias MidiOutputPortHandle = String // Internal handle/ID for an open output port
Key Configurable "Variables": MIDI input filters (channel, type), MIDI output routing per track.
C3: File & Project Management System (Kotlin, SAF, JSON serialization)

Responsibilities: CRUD for projects, managing sample pool metadata, Browse storage, file operations. Uses data models from Section III.
Key Interface (ProjectManager.kt):
Kotlin

interface ProjectManager {
    // Project Operations
    suspend fun createNewProject(name: String, templateName: String?): Result<Project, Error>
    suspend fun loadProject(projectUri: Uri): Result<Project, Error>
    suspend fun saveProject(project: Project): Result<Unit, Error> // Saves to existing URI or prompts if new
    suspend fun saveProjectAs(project: Project, newProjectUri: Uri): Result<Unit, Error>
    fun getCurrentProject(): StateFlow<Project?>
    suspend fun closeProject()

    // Sample Pool Management (within current project)
    suspend fun addSampleToPool(name: String, sourceFileUri: Uri, copyToProjectDir: Boolean): Result<SampleMetadata, Error>
    suspend fun removeSampleFromPool(sampleId: String): Boolean
    fun getSampleMetadata(sampleId: String): SampleMetadata?
    fun getAllSamplesInPool(): List<SampleMetadata>
    // updateSampleMetadata(...)

    // Browser Operations
    suspend fun listFiles(directoryUri: Uri, filter: FileFilter): Result<List<FileItem>, Error>
    suspend fun getStorageInfo(storageUri: Uri): Result<StorageInfo, Error>
    // Format External Storage: Generally out of scope for an app. Request user to do it via OS.

    // Exporting (might call other engines)
    suspend fun exportAudioMixdown(project: Project, settings: AudioMixdownSettings, targetFileUri: Uri): Result<Unit, Error>
    suspend fun exportSequenceAsMidi(sequence: Sequence, targetFileUri: Uri): Result<Unit, Error>
}

data class FileItem(val name: String, val uri: Uri, val isDirectory: Boolean, val sizeBytes: Long, val lastModified: Long, val type: FileType)
enum class FileType { PROJECT, SAMPLE, MIDI, AUDIO_CLIP, PLUGIN_PRESET, UNKNOWN }
data class FileFilter(val extensions: List<String>?, val type: FileType?)
data class StorageInfo(val totalSpaceBytes: Long, val freeSpaceBytes: Long)
data class AudioMixdownSettings(val format: AudioFormat, val sampleRate: Int, val bitDepth: Int, val renderSource: RenderSource, val includeAudioTailMs: Int)
enum class AudioFormat { WAV, MP3, FLAC, OGG }
enum class RenderSource { MASTER_OUT, STEMS_ALL_TRACKS, STEMS_SELECTED_TRACKS }
Key Configurable "Variables": Default project location, auto-save interval, rules for copying samples into project folder.
C4: UI Framework & Core UI Components (Jetpack Compose)

Responsibilities: Base UI structure, reusable common UI elements, virtual pad grid.
Key Reusable Composables (with example parameters):
MainAppScaffold(modifier: Modifier, topBar: @Composable () -> Unit, bottomBar: @Composable () -> Unit, drawerContent: (@Composable () -> Unit)?, content: @Composable (PaddingValues) -> Unit)
VirtualKnob(modifier: Modifier, label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)?)
VirtualSlider(modifier: Modifier, label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)?)
PadGrid(modifier: Modifier, padsState: List<PadUiState>, numRows: Int, numCols: Int, onPadInteraction: (padIndex: Int, interaction: PadInteraction) -> Unit)
data class PadUiState(val id: String, val color: Color, val label: String?, val isPlaying: Boolean, val isActive: Boolean, val progress: Float? /* for loop progress */)
sealed class PadInteraction { data class Down(val pressure: Float) : PadInteraction(); object Up : PadInteraction() }
FileBrowserView(modifier: Modifier, currentDirectory: FileItem?, items: List<FileItem>, onFileSelected: (FileItem) -> Unit, onDirectoryChange: (FileItem) -> Unit, onNavigateUp: () -> Unit, onDismiss: () -> Unit)
WaveformDisplay(modifier: Modifier, waveformData: List<Float>, progress: Float?, selectionStart: Float?, selectionEnd: Float?, loopPoints: Pair<Float,Float>?, onSelectionChange: (Float, Float) -> Unit)
PianoRoll(modifier: Modifier, events: List<Event>, sequenceLengthTicks: Long, onNoteEvent: (Event) -> Unit, visibleNoteRange: IntRange, visibleTimeRangeTicks: LongRange)
XYPad(modifier: Modifier, xValue: Float, yValue: Float, onValueChange: (Float, Float) -> Unit, xLabel: String, yLabel: String, latchMode: Boolean = false)
Key Configurable "Variables": Theme (colors, typography), default pad colors, animation durations.
V. Phased Feature Modules (Detailed - Example for Phase 1)

Phase 1: Basic Drum Machine Functionality

M1: Basic Sampling & Pad Playback

Dependencies: C1, C3, C4.
ViewModel(s): SamplerViewModel, DrumPadViewModel
Sub-Modules & Tasks:
M1.1: Sampler (Recording UI & Logic)
UI (Compose Screen):
Input source selection (Mic, USB Audio - if C1 supports named sources).
Live input level meter (visualizing C1.getRecordingLevelPeak()).
Record, Stop, Playback Last Recording buttons.
Threshold recording setting (value input).
Post-recording: "Name Sample" dialog, "Assign to Pad" selector.
Logic (SamplerViewModel):
Manage recording state (Idle, Armed, Recording, Reviewing).
Interface with C1.startAudioRecording(), C1.stopAudioRecording().
Interface with C3.addSampleToPool() to save recorded sample metadata.
Handle sample naming and assignment to a DrumTrack pad (updates Project data).
M1.2: Drum Track Engine & Pad Playback (Model & Basic Control)
Model (Kotlin): Integration with DrumTrack and PadSettings from Section III.
Logic (DrumTrackViewModel or similar):
Load DrumTrack data from current Project (via C3).
For each active pad, when triggered (by UI or Sequencer):
Retrieve PadSettings (sample ID, tuning, volume, pan, etc.).
Call C1.playPadSample() with appropriate parameters.
Handle pad selection for editing PadSettings.
M1.3: Basic Sample Edit UI (Trim/Loop)
UI (Compose Screen/Dialog):
Load selected sample's waveform (via C1 providing data, or C3 path + separate waveform lib).
Display using C4.WaveformDisplay.
Controls (sliders/draggable markers) for Start, End, Loop Start, Loop End points.
Loop mode selector (LoopMode enum).
Audition button (plays selection via C1).
Save changes to SampleMetadata (non-destructive, updates startMs, endMs for slices or specific playback params).
Logic (SampleEditViewModel):
Load SampleMetadata for editing.
Manage temporary edit state of start/end/loop points.
Interface with C1 for auditioning with current settings.
Update SampleMetadata in the Project (via C3 for persistence).
M2: Basic Sequencing (Drum Track Focus)

Dependencies: C1, C2 (for timing/external triggers if any), M1, C3, C4.
ViewModel(s): SequencerViewModel, StepEditorViewModel
Sub-Modules & Tasks:
M2.1: Sequence Data Model Integration:
Ensure Sequence, Event, EventType.PadTrigger are used correctly.
SequencerViewModel manages current Sequence object from Project.
M2.2: Real-time Pad Recording Logic:
In SequencerViewModel, listen to pad triggers (from PadGrid via DrumTrackViewModel or direct).
If sequencer is recording:
Quantize timing based on Sequence.bpm and selected TimeDivision.
Create Event(type = EventType.PadTrigger) and add to current Sequence.events.
Provide visual feedback for recording.
M2.3: Step Sequencer UI & Logic (Drum Grid)
UI (StepEditorScreen):
Grid representing steps (e.g., 16 steps) vs. pads (e.g., 8 visible pads).
Click to add/remove EventType.PadTrigger events.
Velocity control per step (e.g., small bar graph or value).
Navigation for more steps/pads.
Logic (StepEditorViewModel):
Modify Sequence.events based on UI interaction.
Handle different time divisions for display.
M2.4: Sequence Playback Engine (Kotlin side)
Logic (SequencerPlaybackService or within SequencerViewModel):
Internal "playhead" that advances based on Sequence.bpm and C1.setTransportBpm().
Uses a timer (e.g., kotlinx.coroutines.flow.timer) synchronized with audio clock if possible, or driven by C1 callbacks.
Iterates Sequence.events; when an event's startTimeTicks is reached, triggers corresponding action (e.g., C1.playPadSample() for PadTrigger).
Handles sequence looping.
M2.5: Basic Sequence Editing UI (Transport & Selection)
UI (Compose elements):
Play, Stop, Record buttons.
BPM display/editor.
Sequence selector dropdown.
"Clear Sequence" button.
Logic (SequencerViewModel):
Control playback state of M2.4.
Modify Sequence.bpm, clear Sequence.events.
(Continue this level of detail for other Phases and Modules as needed, focusing on data flow, key parameters for functions, and UI component states/interactions. For brevity, I won't detail all phases here but the pattern is established.)

VI. AI Developer Tasking Framework

(Remains the same as the previous document: Task Definition, Code Contribution, Testing Requirements, Code Style, Communication.)

VII. Technology Stack Summary

(Remains the same: Kotlin, Jetpack Compose, C++/Oboe, Android MIDI API, Gradle, MVVM/MVI, Hilt/Koin.)

VIII. Key System-Wide Configuration Parameters

These settings might be managed by a SettingsRepository or UserPreferencesManager and influence various modules.

Audio Settings:
bufferSize: (e.g., 64, 128, 256, 512 frames) - User selectable.
sampleRate: (e.g., 44100, 48000 Hz) - Potentially fixed or user-selectable if hardware supports.
enableLowLatencyPath: Boolean.
globalAudioInputDevice: String? (ID of preferred USB audio input).
globalAudioOutputDevice: String? (ID of preferred USB audio output).
MIDI Settings:
defaultInputPorts: List&lt;String> (IDs of MIDI inputs to open on startup).
defaultOutputPorts: List&lt;String> (IDs of MIDI outputs to open on startup).
midiInputChannelFilter: List&lt;Boolean> (16 booleans for active channels).
enableMidiClockInputSync: Boolean.
enableMidiClockOutput: Boolean.
Project Defaults:
defaultProjectName: String.
defaultBpm: Float.
defaultSequenceLengthBars: Int.
autoSaveIntervalMinutes: Int (0 for off).
copySamplesToProjectFolder: Boolean.
UI/Theme:
themeMode: (Light, Dark, System).
padColorPalette: Map&lt;String, Color> (User customizable pad colors).
