# TheOne - Project Structure & Organization

## Architecture Overview

TheOne follows a modular, feature-based architecture that separates concerns between the UI layer (Kotlin/Compose), business logic (Kotlin), and audio processing (C++/JNI). The project is organized to support scalable development and clear separation of responsibilities.

## Package Structure

### Core Application (`com.high.theone`)
```
com.high.theone/
├── MainActivity.kt              # Main entry point and navigation host
├── TheOneApplication.kt         # Application class with Hilt setup
├── audio/                       # Audio engine interfaces and implementations
├── commands/                    # Command pattern for undo/redo
├── di/                         # Dependency injection modules
├── domain/                     # Domain layer interfaces
├── features/                   # Feature-specific modules
├── model/                      # Data models and DTOs
├── project/                    # Project management implementation
└── ui/                         # Shared UI components and themes
```

### Audio Layer (`audio/`)
- **AudioEngine.kt**: Main audio engine interface
- **AudioEngineControl.kt**: Control interface for audio operations
- **AudioEngineImpl.kt**: Concrete implementation with JNI bindings
- **MicrophoneInput.kt**: Audio input interface
- **MicrophoneInputImpl.kt**: Microphone input implementation

### Feature Modules (`features/`)

#### Debug Module (`features/debug/`)
- **DebugScreen.kt**: Comprehensive testing interface
- **DebugViewModel.kt**: Debug screen state management
- Purpose: Audio engine testing, plugin testing, sample loading verification

#### Drum Track Module (`features/drumtrack/`)
- **DrumTrackScreen.kt**: Drum pad interface
- **DrumTrackViewModel.kt**: Drum track state management
- **PadGrid.kt**: Virtual drum pad grid component
- Purpose: Sample triggering, drum pattern creation

#### Sequencer Module (`features/sequencer/`)
- **SequencerScreen.kt**: Step sequencer interface
- **SequencerViewModel.kt**: Sequencer state management
- Purpose: Pattern programming, playback control

#### Sample Editor Module (`features/sampleeditor/`)
- **SampleEditorScreen.kt**: Sample editing interface
- **SampleEditorViewModel.kt**: Sample editing state management
- Purpose: Sample trimming, chopping, effects processing

#### Sampler Module (`features/sampler/`)
- **SamplerScreen.kt**: Sample management interface
- **SamplerViewModel.kt**: Sample library management
- Purpose: Sample loading, organization, metadata management

### Data Models (`model/`)
- **Project.kt**: Main project data structure
- **ProjectModels.kt**: Project-related data classes
- **SampleMetadata.kt**: Sample file information
- **SampleModels.kt**: Sample-related data structures
- **SequenceModels.kt**: Sequencer data structures
- **SharedModels.kt**: Common data types
- **SynthModels.kt**: Synthesizer parameter models
- **LayerModels.kt**: Audio layer management

### Dependency Injection (`di/`)
- **AudioEngineModule.kt**: Audio engine dependencies
- **MicrophoneModule.kt**: Audio input dependencies
- **ProjectManagerModule.kt**: Project management dependencies
- **SequencerModule.kt**: Sequencer dependencies

## Native Layer Structure (`cpp/`)

### Core Audio Engine
```
cpp/
├── AudioEngine.{h,cpp}          # Main audio engine implementation
├── native-lib.cpp               # JNI bridge functions
├── audio_sample.h               # Sample data structures
├── dr_wav.h                     # WAV file loading library
└── CMakeLists.txt               # Build configuration
```

### Audio Processing Components
- **EnvelopeGenerator.{h,cpp}**: ADSR envelope processing
- **LfoGenerator.{h,cpp}**: Low-frequency oscillator
- **StateVariableFilter.{h,cpp}**: Audio filtering
- **SynthEngine.{h,cpp}**: Synthesis engine
- **PadSettings.h**: Drum pad configuration
- **SequenceCpp.h**: Native sequence data

### AVST Plugin System (`avst/`)
- **IAvstPlugin.{h,cpp}**: Base plugin interface
- **AvstParameter.{h,cpp}**: Plugin parameter system
- **AvstParameterContainer.{h,cpp}**: Parameter management
- **AvstAudioIO.h**: Audio I/O configuration
- **SketchingSynth.{h,cpp}**: Example synthesizer plugin

### External Libraries
- **oboe/**: Google Oboe low-latency audio library (git submodule)

## Data Flow Architecture

### UI to Audio Engine
1. **User Interaction** → Compose UI
2. **UI Events** → ViewModel
3. **Business Logic** → Repository/UseCase
4. **Audio Commands** → AudioEngine interface
5. **JNI Bridge** → Native C++ implementation
6. **Audio Processing** → Oboe output

### Audio Engine to UI
1. **Audio Events** → JNI callbacks
2. **State Updates** → Kotlin coroutines
3. **UI State** → ViewModel StateFlow
4. **UI Updates** → Compose recomposition

## Module Dependencies

### Dependency Hierarchy
```
UI Layer (Compose)
    ↓
ViewModel Layer (MVVM)
    ↓
Repository Layer (Data Access)
    ↓
Audio Engine Layer (JNI)
    ↓
Native Audio Layer (C++)
```

### Cross-Module Communication
- **Events**: Use sealed classes for type-safe event handling
- **State**: StateFlow/LiveData for reactive UI updates
- **Commands**: Command pattern for undoable operations
- **Injection**: Hilt for dependency management

## File Organization Conventions

### Naming Conventions
- **Screens**: `*Screen.kt` (e.g., `DrumTrackScreen.kt`)
- **ViewModels**: `*ViewModel.kt` (e.g., `DrumTrackViewModel.kt`)
- **Repositories**: `*Repository.kt` (e.g., `ProjectRepository.kt`)
- **Interfaces**: `*Control.kt` or `I*.kt` (e.g., `AudioEngineControl.kt`)
- **Implementations**: `*Impl.kt` (e.g., `AudioEngineImpl.kt`)

### Package Organization
- **Feature-based**: Group related functionality together
- **Layer separation**: Separate UI, domain, and data concerns
- **Shared components**: Common utilities in shared packages
- **Platform-specific**: Native code isolated in cpp/ directory

## Testing Structure

### Unit Tests (`src/test/`)
```
test/java/com/high/theone/
├── audio/                       # Audio engine unit tests
├── features/                    # Feature-specific tests
├── model/                       # Data model tests
└── project/                     # Project management tests
```

### Integration Tests (`src/androidTest/`)
```
androidTest/java/com/high/theone/
├── audio/                       # Audio integration tests
├── features/                    # UI integration tests
└── end2end/                     # End-to-end test scenarios
```

### Native Tests (`src/test/cpp/`)
- **AudioEngineTests.cpp**: C++ audio engine tests
- **EnvelopeGeneratorTests.cpp**: Envelope processing tests

## Build Artifacts

### Debug Build
- **APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Native Libraries**: `app/build/intermediates/cmake/debug/obj/`
- **Symbols**: `app/build/intermediates/cmake/debug/obj/*/lib*.so`

### Release Build
- **APK**: `app/build/outputs/apk/release/app-release.apk`
- **AAB**: `app/build/outputs/bundle/release/app-release.aab`
- **Mapping**: `app/build/outputs/mapping/release/mapping.txt`

## Development Guidelines

### Adding New Features
1. Create feature package under `features/`
2. Implement Screen, ViewModel, and related components
3. Add navigation route in MainActivity
4. Create corresponding tests
5. Update dependency injection modules if needed

### Audio Engine Extensions
1. Define Kotlin interface in `audio/` package
2. Implement JNI bridge in `native-lib.cpp`
3. Add C++ implementation in appropriate header/source files
4. Update CMakeLists.txt if adding new source files
5. Add comprehensive tests for audio functionality

### Model Changes
1. Update data classes in `model/` package
2. Ensure serialization compatibility
3. Update database migrations if applicable
4. Add validation and business logic
5. Update related ViewModels and repositories