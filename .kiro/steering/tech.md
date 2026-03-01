# TheOne - Technology Stack & Build System

## Core Technologies

### Primary Languages
- **Kotlin**: Main application language for Android development
- **C++**: High-performance audio engine using C++17 standard
- **JNI**: Bridge layer connecting Kotlin UI with C++ audio engine

### UI Framework
- **Jetpack Compose**: Modern declarative UI framework
- **Material Design 3**: Google's latest design system
- **Compose Navigation**: Type-safe navigation between screens
- **Hilt**: Dependency injection framework

### Audio Technology
- **Google Oboe**: Low-latency audio library for Android
- **dr_wav**: Single-header WAV file loading library
- **AVST Plugin System**: Custom plugin architecture for synthesizers and effects
- **Android MIDI API**: MIDI input/output support

### Architecture Patterns
- **MVVM/MVI**: Model-View-ViewModel with Model-View-Intent patterns
- **Modular Design**: Feature-based module organization
- **Repository Pattern**: Data access abstraction
- **Command Pattern**: Undo/redo functionality

### Build System
- **Gradle**: Build automation with Kotlin DSL
- **CMake**: Native C++ build configuration
- **KSP**: Kotlin Symbol Processing for code generation
- **NDK**: Android Native Development Kit

## Project Structure

```
TheOne/
├── app/
│   ├── src/main/
│   │   ├── java/com/high/theone/
│   │   │   ├── audio/           # AudioEngineControl interface + JNI impl
│   │   │   ├── midi/            # Full MIDI engine (USB, mapping, I/O, diagnostics)
│   │   │   ├── features/
│   │   │   │   ├── compactui/   # Main production UI (adaptive portrait/landscape)
│   │   │   │   │   ├── animations/      # Panel transitions, micro-interactions
│   │   │   │   │   ├── accessibility/   # High-contrast, keyboard nav, TalkBack
│   │   │   │   │   ├── error/           # Error recovery, permissions, storage
│   │   │   │   │   └── performance/     # Frame/memory monitors, perf warnings
│   │   │   │   ├── drumtrack/   # DrumTrackViewModel, PadSettings, edit screens
│   │   │   │   ├── sequencer/   # Step sequencer, song mode, timing, overdub
│   │   │   │   ├── sampling/    # SamplingViewModel, sample browser, recording UI
│   │   │   │   ├── midi/ui/     # MIDI settings, mapping, monitor screens
│   │   │   │   ├── sampleeditor/# Full-screen waveform sample editor
│   │   │   │   ├── sampler/     # Sample library management
│   │   │   │   └── debug/       # Dev/test tools
│   │   │   ├── model/           # Shared data models (SharedModels, CompactUIModels, etc.)
│   │   │   ├── commands/        # Undo/redo command pattern
│   │   │   ├── di/              # Hilt DI modules
│   │   │   ├── domain/          # Domain interfaces
│   │   │   ├── project/         # ProjectManager implementation
│   │   │   └── ui/              # Shared UI components and themes
│   │   ├── cpp/                 # Native C++ audio engine
│   │   │   ├── avst/            # AVST plugin system (IAvstPlugin, SketchingSynth)
│   │   │   └── oboe/            # Oboe audio library (submodule)
│   │   └── assets/              # Audio samples and resources
│   └── build.gradle.kts         # App-level build configuration
├── .kiro/steering/              # Project docs (product.md, tech.md, structure.md)
├── gradle/libs.versions.toml    # Centralized dependency versions
└── build.gradle.kts             # Project-level build configuration
```

## Common Build Commands

### Development
```bash
# Clean build
./gradlew clean

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Testing
```bash
# Run all tests
./gradlew check

# Run specific test class
./gradlew test --tests "com.high.theone.audio.AudioEngineTest"

# Run tests with coverage
./gradlew testDebugUnitTestCoverage
```

### Native Development
```bash
# Clean native builds
./gradlew clean

# Build specific ABI
./gradlew assembleDebug -Pandroid.injected.build.abi=arm64-v8a

# Debug native code (requires Android Studio)
# Use "Debug 'app'" with breakpoints in C++ files
```

## Key Dependencies

### Core Android
- `androidx.core:core-ktx` - Android KTX extensions
- `androidx.lifecycle:lifecycle-runtime-ktx` - Lifecycle management
- `androidx.activity:activity-compose` - Compose integration

### UI & Navigation
- `androidx.compose.bom` - Compose Bill of Materials
- `androidx.compose.material3` - Material Design 3 components
- `androidx.navigation:navigation-compose` - Compose navigation

### Dependency Injection
- `com.google.dagger:hilt-android` - Hilt DI framework
- `androidx.hilt:hilt-navigation-compose` - Hilt Compose integration

### Serialization
- `org.jetbrains.kotlinx:kotlinx-serialization-json` - JSON serialization

### Testing
- `junit:junit` - Unit testing framework
- `org.mockito:mockito-core` - Mocking framework
- `org.robolectric:robolectric` - Android unit testing
- `app.cash.turbine:turbine` - Flow testing utilities

## Build Configuration

### Android Configuration
- **Compile SDK**: 35 (Android 14)
- **Min SDK**: 33 (Android 13)
- **Target SDK**: 35 (Android 14)
- **Java Version**: 17
- **Kotlin JVM Target**: 17

### Native Configuration
- **C++ Standard**: C++17
- **NDK Version**: 25.1.8937393
- **CMake Version**: 3.22.1
- **Supported ABIs**: arm64-v8a, armeabi-v7a, x86, x86_64

### ProGuard/R8
- Minification disabled in debug builds
- Custom ProGuard rules in `proguard-rules.pro`
- R8 optimization enabled for release builds

## Development Workflow

1. **Feature Development**: Create feature branches for new functionality
2. **Testing**: Write unit tests for business logic, integration tests for components
3. **Code Review**: Use pull requests for code review and collaboration
4. **CI/CD**: Automated builds and testing (configure as needed)
5. **Release**: Use semantic versioning for releases

## Performance Considerations

- **Audio Latency**: Target <20ms round-trip latency using Oboe
- **Memory Management**: Efficient sample loading/unloading
- **Threading**: Audio processing on dedicated real-time thread
- **UI Performance**: 60fps target with Compose optimizations