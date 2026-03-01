# TheOne - Project Structure & Organization

## Architecture Overview

TheOne follows a modular, feature-based architecture that separates concerns between the UI layer (Kotlin/Compose), business logic (Kotlin ViewModels), and audio processing (C++/JNI). The project uses MVVM with Hilt DI and Kotlin Coroutines throughout.

**Current state**: Core audio, sequencer, sampling, drum pads, and MIDI engine are all implemented. All Quick Access Panels (Sampling, MIDI, Mixer, Settings, SampleEditor) are wired to real ViewModels. Portrait (BottomSheet) and landscape (side-panel) rendering paths are unified to use the same composables.

## Package Structure

### Core Application (`com.high.theone`)
```
com.high.theone/
├── MainActivity.kt              # Main entry point and navigation host
├── TheOneApplication.kt         # Application class with Hilt setup
├── audio/                       # Audio engine interfaces and JNI bridge
├── midi/                        # Full MIDI engine (USB, mapping, I/O, diagnostics)
├── commands/                    # Command pattern for undo/redo
├── di/                          # Dependency injection modules
├── domain/                      # Domain layer interfaces
├── features/                    # Feature-specific modules
├── model/                       # Shared data models and DTOs
├── project/                     # Project management implementation
└── ui/                          # Shared UI components and themes
```

---

### Audio Layer (`audio/`)
- **AudioEngineControl.kt**: Interface for all audio operations, including:
  - Sample loading/unloading, pad playback, note stop/all
  - Recording start/stop, real-time level monitoring (`getRecordingLevel()`)
  - Waveform thumbnail generation (`getWaveformThumbnail()`)
  - Drum pad trim API (`setDrumPadTrim()`)
  - Transport BPM, track volume/pan, effects routing
- **AudioEngineImpl.kt**: JNI implementation delegating to C++ AudioEngine
- **AudioEngine.kt**: Engine lifecycle (init/shutdown)
- **MicrophoneInput.kt / MicrophoneInputImpl.kt**: Mic capture interface + impl

---

### MIDI Engine (`midi/`)
Full Android MIDI API integration for USB devices, event processing, mapping, and output.

```
midi/
├── MidiManager.kt               # Central MIDI coordinator
├── MidiManagerControl.kt        # Interface for MIDI operations
├── MidiMessageParser.kt         # Raw MIDI byte parsing
├── MidiValidator.kt             # MIDI message validation
├── MidiError.kt                 # Error types
├── device/
│   ├── MidiDeviceManager.kt     # USB device connect/disconnect lifecycle
│   └── MidiDeviceScanner.kt     # Available device discovery
├── input/
│   ├── MidiInputProcessor.kt    # Incoming MIDI event handling → pad triggers
│   └── MidiVelocityCurve.kt     # Velocity curve shaping
├── output/
│   ├── MidiOutputGenerator.kt   # Outbound MIDI event generation
│   ├── MidiClockGenerator.kt    # MIDI clock (not yet wired to transport)
│   └── MidiTransportController.kt
├── mapping/
│   ├── MidiMappingEngine.kt     # CC → parameter routing
│   ├── MidiLearnManager.kt      # MIDI learn mode
│   └── MidiParameterMapper.kt   # Parameter value scaling
├── integration/
│   ├── MidiAudioEngineAdapter.kt / Impl  # MIDI → AudioEngine bridge
│   └── MidiSequencerAdapter.kt          # MIDI → Sequencer bridge
├── repository/
│   ├── MidiConfigurationRepository.kt   # Saved device config persistence
│   └── MidiMappingRepository.kt         # Saved mapping persistence
├── service/
│   ├── MidiLifecycleManager.kt  # App lifecycle MIDI management
│   ├── MidiPermissionManager.kt # Runtime permission handling
│   └── MidiSystemInitializer.kt # Startup init
├── diagnostics/
│   └── MidiDiagnosticsManager.kt
├── error/
│   ├── MidiErrorHandler.kt
│   ├── MidiGracefulDegradation.kt
│   └── MidiNotificationManager.kt
├── performance/
│   ├── MidiPerformanceMonitor.kt
│   └── MidiPerformanceOptimizer.kt
└── model/
    ├── MidiModels.kt            # MidiDeviceInfo, MidiEvent, etc.
    ├── MidiState.kt             # MidiConnectionState, MidiUiState
    └── MidiConfiguration.kt    # Persisted configuration
```

---

### Feature Modules (`features/`)

#### Compact UI Module (`features/compactui/`) — Main Production UI
The primary UI used in production. Handles adaptive portrait/landscape layout, all quick-access panels, pad grid, transport, and recording integration.

```
compactui/
├── CompactMainScreen.kt           # Root composable: pads + sequencer + panels + pad config sheet
│                                  # • selectedPadId state for long-press pad config
│                                  # • LaunchedEffect: sequencerViewModel.sequencerState → viewModel.updateSequencerState()
│                                  # • PadConfigBottomSheet when selectedPadId != null
├── CompactMainViewModel.kt        # Layout state, panel visibility, sequencer state bridge
├── CompactMainScreenEntryPoint.kt # Hilt entry point / navigation wrapper
├── AdaptiveBottomSheet.kt         # Portrait bottom-sheet: BottomSheetContentSwitcher
│                                  # → delegates to same panel composables as QuickAccessPanelContent
├── QuickAccessPanel.kt            # Landscape side-panel container with spring animation + drag gesture
├── QuickAccessPanelContent.kt     # 5 panel types, ALL wired to real ViewModels via hiltViewModel():
│                                  # • SamplingPanel   → SamplingViewModel (record/stop, level, samples)
│                                  # • MidiPanel       → MidiSettingsViewModel (device list, connect btn)
│                                  # • MixerPanel      → DrumTrackViewModel (per-pad vol/pan sliders)
│                                  # • SettingsPanel   → local state (audio buffer, latency, theme)
│                                  # • SampleEditorPanel → local state (waveform trim controls)
├── QuickAccessPanelIntegration.kt # QuickAccessPanelStateHolder helper + WithQuickAccessPanel util
├── CompactDrumPadGrid.kt          # 4x4 pad grid with tap (play) + long-press (config) + velocity
├── InlineSequencer.kt             # Compact 16-step sequencer embedded in main screen
├── TransportControlBar.kt         # BPM, play/stop/record transport buttons
├── TransportControlBarIntegration.kt
├── CompactRecordingPanelIntegration.kt  # Recording panel wired to SamplingViewModel
├── CompactPadMidiIntegration.kt   # MIDI → pad trigger bridge for compact UI
├── ResponsiveMainLayout.kt        # Portrait/landscape layout switching logic
├── ResponsiveRecordingPanel.kt    # Responsive recording panel component
├── PatternManagement.kt           # Pattern select/copy/chain UI
├── QuickPadAssignmentFlow.kt      # Quick sample-to-pad assignment workflow
├── LayoutStateManager.kt / LayoutStateComposables.kt / LayoutPresetManager.kt
├── PreferenceManager.kt           # UI preference persistence
├── CustomizationPanel.kt          # Pad color / layout customization
├── ProjectSettingsScreen.kt       # Project-level settings (BPM, name, etc.)
├── CompactUIPerformanceOptimizer.kt
├── animations/
│   ├── AnimationSystem.kt         # Core animation specs (PanelTransition, PanelDirection)
│   ├── MicroInteractions.kt       # Pad press spring, button tap feedback
│   ├── VisualFeedbackSystem.kt    # Recording level, playback indicators
│   ├── LoadingStates.kt           # Skeleton / loading composables
│   ├── AnimationIntegration.kt
│   └── EnhancedAnimationIntegration.kt / EnhancedRecordingPanel.kt
├── accessibility/
│   ├── AccessibilitySupport.kt    # TalkBack / semantic descriptions
│   ├── AccessibilityTestingUtils.kt
│   ├── HighContrastMode.kt        # High-contrast theme variant
│   └── KeyboardNavigationSupport.kt
├── error/
│   ├── ErrorHandlingSystem.kt     # Error state model & display logic
│   ├── ErrorHandlingIntegration.kt
│   ├── ErrorRecoveryUI.kt         # User-facing error + retry composables
│   ├── AudioEngineRecovery.kt     # Audio engine crash recovery flow
│   ├── PermissionManager.kt       # Runtime permission requests (mic, storage)
│   └── StorageManager.kt          # Storage availability checks
└── performance/
    ├── RecordingPerformanceMonitor.kt  # CPU/memory during recording
    ├── RecordingFrameRateMonitor.kt    # Frame drop detection
    ├── RecordingMemoryManager.kt       # Memory pressure handling
    └── PerformanceWarningUI.kt         # In-app performance warning banner
```

#### Drum Track Module (`features/drumtrack/`)
Drum pad state, settings, and advanced pad program editing.

```
drumtrack/
├── DrumTrackViewModel.kt          # padSettingsMap: StateFlow<Map<String, PadSettings>>
│                                  # updatePadSettings(PadSettings) — called by Mixer panel & Pad Config sheet
├── model/
│   ├── PadSettings.kt             # id, name, sampleId, volume, pan, playbackMode, muteGroup,
│   │                              # polyphony, tuning, ampEnvelope, pitchEnvelope, layers, layerTriggerRule
│   └── DrumTrackModels.kt         # DrumTrack, PadTriggerEvent, etc.
├── edit/
│   ├── DrumProgramEditScreen.kt   # Full pad program editor (envelopes, LFO, tuning)
│   ├── DrumProgramEditViewModel.kt
│   ├── DrumProgramEditEvents.kt   # UI events sealed class
│   └── VisualEnvelopeEditor.kt    # Canvas-drawn ADSR envelope visualization
└── ui/
    └── DrumPadScreen.kt           # Standalone drum pad screen (used outside compact UI)
```

#### Sequencer Module (`features/sequencer/`)
Full step sequencer with patterns, song mode, overdub, and timing engine.

```
sequencer/
├── SimpleSequencerViewModel.kt    # sequencerState: StateFlow<SequencerState>
│                                  # Synced → CompactMainViewModel via LaunchedEffect
├── SequencerScreen.kt             # Full-screen sequencer UI
├── SequencerAudioEngineAdapter.kt # AudioEngineControl wrapper for sequencer use
│                                  # Implements all AudioEngineControl methods including
│                                  # getRecordingLevel, setDrumPadTrim, getWaveformThumbnail
├── SequencerAudioEngine.kt        # Sequencer-specific audio engine interface
├── StepGrid.kt / StepButton.kt / StepCallbackManager.kt
├── PatternManager.kt              # Pattern CRUD, active pattern tracking
├── PatternManagementUI.kt / PatternManagementOperations.kt
├── PatternChainEditor.kt          # Song-mode pattern chaining
├── PatternCacheManager.kt / PatternHistoryManager.kt / PatternValidation.kt
├── TimingEngine.kt / PrecisionTimingEngine.kt / TimingCalculator.kt
├── RecordingEngine.kt / OverdubManager.kt / RecordingVisualFeedback.kt
├── SongModeManager.kt / SongArrangementScreen.kt / SongTimeline.kt
├── SongPlaybackEngine.kt / SongNavigationManager.kt / SongExportManager.kt
├── TempoSwingController.kt / TempoSwingControls.kt
├── GrooveTemplates.kt / GrooveTemplateSelector.kt
├── AdvancedStepEditor.kt / AdvancedStepFeatures.kt
├── TransportBar.kt / TransportControls.kt / UndoRedoControls.kt
├── PadSelector.kt / PadSystemIntegration.kt
├── SequencerVoiceManager.kt / SequencerSampleCache.kt
├── SequencerEvents.kt / SequencerLogger.kt
├── SequencerErrorHandler.kt / SequencerErrorHandling.kt
├── SequencerPerformanceMonitor.kt / SequencerPerformanceOptimizer.kt
├── SequencerVisualFeedback.kt
├── SequencerHelpScreen.kt / SequencerTutorialScreen.kt
└── PerformanceMonitorScreen.kt
```

#### Sampling Module (`features/sampling/`)
Audio recording, sample management, browser, and pad grid composables.

```
sampling/
├── SamplingViewModel.kt           # isRecording, peakLevel, formattedDuration, availableSamples
│                                  # startRecording(), stopRecording(), assignSampleToPad()
├── SamplingScreen.kt              # Main sampling/recording UI
├── SampleBrowser.kt               # File browser for loading external samples
├── SampleEditor.kt / SampleEditorScreen.kt  # Inline sample editing
├── SampleTrimming.kt / SampleProcessing.kt  # Trim, normalize, reverse
├── RecordingControls.kt / RecordingWorkflowUI.kt  # Record UI widgets
├── RecordedSamplePreview.kt / SamplePreview.kt / SamplePreviewManager.kt
├── PadGrid.kt                     # Sampling-context pad grid
├── PadConfigurationDialog.kt      # Pad assignment dialog
├── SampleAssignment.kt            # Sample-to-pad assignment logic
├── SampleImport.kt                # SAF file import workflow
├── SampleManagement.kt            # Sample pool CRUD
├── SampleMetadataEditor.kt        # Edit sample name, root note, etc.
├── SampleCacheManager.kt          # In-memory sample cache
├── SampleProjectCreator.kt        # New project from sample workflow
├── SampleUsageTracker.kt          # Track which pads use which samples
├── SampleFilterDialog.kt / SampleReplacementDialog.kt
├── SamplingDebugManager.kt / SamplingDebugPanel.kt
├── VoiceManager.kt                # Polyphony / voice stealing logic
├── WaveformAnalyzer.kt            # Waveform data analysis
├── MidiPadIntegration.kt / MidiSamplingAdapter.kt  # MIDI → sampling integration
├── PadStateProvider.kt / PadVisualFeedback.kt      # Pad state helpers
├── PerformanceMonitor.kt          # Sampling performance tracking
├── HapticFeedbackManager.kt       # Haptic on pad hit
├── AccessibilityFeatures.kt       # A11y for sampling UI
├── HelpSystem.kt / OnboardingSystem.kt
└── AudioEnginePreviewExtensions.kt
```

#### MIDI UI Module (`features/midi/ui/`)
Full MIDI settings, mapping, and monitor screens.

```
midi/ui/
├── MidiSettingsScreen.kt          # Main MIDI settings: connected devices, routing
├── MidiSettingsViewModel.kt       # connectedDevices, availableDevices, connectDevice(id)
├── MidiMappingScreen.kt           # CC → parameter mapping UI
├── MidiMappingViewModel.kt        # Mapping CRUD, MIDI learn mode
├── MidiMonitorScreen.kt           # Real-time MIDI event monitor
├── MidiMonitorViewModel.kt        # Event log StateFlow
├── MidiDeviceConfigDialog.kt      # Per-device channel / filter config
└── MidiPermissionDialog.kt        # Runtime MIDI permission rationale
```

#### Sample Editor Module (`features/sampleeditor/`)
Dedicated full-screen sample editor (waveform view, trim, fade, destructive ops).

```
sampleeditor/
├── SampleEditScreen.kt            # Waveform display, trim handles, fade in/out, normalize, reverse
│                                  # Contains MockAudioEngineControl for preview (implements all AudioEngineControl methods)
├── SampleEditViewModel.kt         # Sample edit state and operations
└── SampleEditViewModelFactory.kt  # Factory for ViewModel with sample ID param
```

#### Sampler Module (`features/sampler/`)
Sample library management (older/simpler complement to `sampling/`).

```
sampler/
├── SamplerScreen.kt               # Sample library browser
├── SamplerViewModel.kt            # Sample library state
└── permissions/
    └── MicrophonePermissionHelper.kt
```

#### Debug Module (`features/debug/`)
```
debug/
└── DebugScreen.kt                 # Audio engine test UI (init, play test samples, latency)
```

---

### Data Models (`model/`)
```
model/
├── SharedModels.kt        # PlaybackMode, LoopMode, SnapPosition, PanelType, PanelState,
│                          # EnvelopeSettings, EnvelopeType, LFOSettings, EffectSetting,
│                          # ModulationRouting, SampleLayer, LayerTriggerRule
├── CompactUIModels.kt     # CompactLayoutState, PanelConfig, DisplayMode
├── SamplingModels.kt      # RecordingState, SampleAssignment, SampleCategory
├── SampleModels.kt        # SampleData, SamplePlaybackState
├── SampleMetadata.kt      # File metadata (id, name, uri, duration, sampleRate, channels)
├── SequenceModels.kt      # SequencerState, StepEvent, Pattern, SequenceStep
├── SynthModels.kt         # Synth/plugin parameter models
├── LayerModels.kt         # Multi-layer audio model
├── Project.kt             # Top-level project container
└── ProjectModels.kt       # Project-related DTOs
```

---

### Dependency Injection (`di/`)
```
di/
├── AudioEngineModule.kt        # Provides AudioEngineControl (singleton AudioEngineImpl)
├── MicrophoneModule.kt         # Provides MicrophoneInput
├── ProjectManagerModule.kt     # Provides ProjectManager
└── SequencerModule.kt          # Provides sequencer dependencies
```

---

## Native Layer Structure (`cpp/`)

```
cpp/
├── AudioEngine.{h,cpp}          # Main audio engine: Oboe stream, voice manager, mixer
├── native-lib.cpp               # JNI bridge (all AudioEngineControl method bindings)
├── audio_sample.h               # SampleDataCpp structure
├── dr_wav.h                     # WAV file loading (single-header)
├── EnvelopeGenerator.{h,cpp}    # ADSR envelope processing per-voice
├── LfoGenerator.{h,cpp}         # LFO with SINE/TRIANGLE/SQUARE/SAW/RANDOM waveforms
├── StateVariableFilter.{h,cpp}  # LP/HP/BP/notch filter (not yet exposed in UI)
├── SynthEngine.{h,cpp}          # Synthesis engine (AVST plugin host)
├── PadSettings.h                # Native pad configuration struct
├── SequenceCpp.h                # Native sequence data structures
├── avst/                        # AVST plugin framework
│   ├── IAvstPlugin.{h,cpp}      # Base plugin interface
│   ├── AvstParameter.{h,cpp}    # Plugin parameter system
│   ├── AvstParameterContainer.{h,cpp}
│   ├── AvstAudioIO.h            # Audio I/O configuration
│   └── SketchingSynth.{h,cpp}   # Example synthesizer plugin
├── oboe/                        # Google Oboe (git submodule)
└── CMakeLists.txt               # Build config (all architectures: arm64, arm32, x86, x86_64)
```

---

## Data Flow Architecture

### UI → Audio Engine
```
User tap (Compose) → ViewModel → AudioEngineControl interface → AudioEngineImpl → JNI → C++ AudioEngine → Oboe
```

### Audio Engine → UI
```
C++ callbacks → JNI → Kotlin coroutines → ViewModel StateFlow → Compose recomposition
```

### Panel Rendering (Two Paths, Same Composables)
```
Portrait:  CompactMainScreen → AdaptiveBottomSheet → BottomSheetContentSwitcher → [SamplingPanel | MidiPanel | MixerPanel | ...]
Landscape: CompactMainScreen → CompactMainScreenPanels → LazyPanelContent → QuickAccessPanelContent → [same panel composables]
```

### Sequencer State Bridge
```
SimpleSequencerViewModel.sequencerState (StateFlow)
  → LaunchedEffect in CompactMainScreen
  → CompactMainViewModel.updateSequencerState()
  → CompactMainViewModel.compactUiState (StateFlow)
  → UI recomposition
```

---

## Module Dependencies

```
UI Layer (Compose)
    ↓
ViewModel Layer (MVVM + Hilt)
    ↓
Repository / UseCase Layer
    ↓
AudioEngine + MidiManager interfaces
    ↓
Native Audio Layer (C++ / Oboe)
```

### Key ViewModel → ViewModel relationships
- `CompactMainViewModel` consumes `SimpleSequencerViewModel.sequencerState`
- `QuickAccessPanelContent` panels each use `hiltViewModel()` — Hilt scopes ensure single instance per activity
- `DrumTrackViewModel.padSettingsMap` is read by MixerPanel AND PadConfigBottomSheet

---

## File Naming Conventions
- **Screens**: `*Screen.kt` (e.g., `MidiSettingsScreen.kt`)
- **ViewModels**: `*ViewModel.kt` (e.g., `DrumTrackViewModel.kt`)
- **Repositories**: `*Repository.kt`
- **Interfaces**: `*Control.kt` or `I*.kt`
- **Implementations**: `*Impl.kt`
- **Adapters**: `*Adapter.kt` (bridge between two subsystems)
- **Integration**: `*Integration.kt` (wiring composable or integration point)

## Adding New Features

### New panel type
1. Add value to `PanelType` enum in `SharedModels.kt`
2. Add composable in `QuickAccessPanelContent.kt` (wired to `hiltViewModel()`)
3. Add case to `BottomSheetContentSwitcher` in `AdaptiveBottomSheet.kt`
4. Add tab in `QuickAccessPanelTypeSelector` in `QuickAccessPanel.kt`

### New AudioEngineControl method
1. Add `suspend fun` to `AudioEngineControl.kt`
2. Implement in `AudioEngineImpl.kt` (JNI call to C++)
3. Add stub in `MockAudioEngineControl` inside `SampleEditScreen.kt`
4. Add delegation in `SequencerAudioEngineAdapter.kt`

### New feature module
1. Create package under `features/<name>/`
2. Add Screen + ViewModel + model sub-packages
3. Register ViewModel with Hilt (`@HiltViewModel`)
4. Add navigation route in `MainActivity.kt`
5. Add DI module in `di/` if new dependencies needed
