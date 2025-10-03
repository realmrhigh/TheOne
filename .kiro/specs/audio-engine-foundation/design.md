# Design Document

## Overview

The Audio Engine Foundation provides the core audio infrastructure for TheOne MPC app. Based on analysis of the existing codebase, the audio engine is already partially implemented using Google Oboe for low-latency audio, dr_wav for sample loading, and a comprehensive JNI bridge. This design document outlines the complete architecture and identifies areas that need enhancement or completion.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kotlin UI Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Debug Screen  │  │  Drum Track UI  │  │  Sequencer UI   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 AudioEngineImpl (Kotlin)                       │
│  • Coroutine-based async operations                            │
│  • JNI bridge management                                       │
│  • Error handling and logging                                  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼ JNI
┌─────────────────────────────────────────────────────────────────┐
│                   native-lib.cpp (JNI Bridge)                  │
│  • Type conversion (Java ↔ C++)                               │
│  • Thread-safe native calls                                    │
│  • Resource management                                          │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AudioEngine (C++)                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Sample Manager │  │  Voice Manager  │  │  Effect Engine  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Oboe Stream   │  │   AVST Plugins  │  │   Metronome     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Core Components

#### 1. AudioEngine Class (C++)
**Location**: `app/src/main/cpp/AudioEngine.{h,cpp}`

**Current State**: ✅ Implemented with core functionality
- Oboe stream management with low-latency configuration
- Sample loading using dr_wav library
- Real-time audio processing callback
- AVST plugin system integration
- Voice management for polyphonic playback

**Key Responsibilities**:
- Initialize and manage Oboe audio streams
- Load/unload audio samples from files and assets
- Process real-time audio in `onAudioReady()` callback
- Manage active voices and sample playback
- Host AVST plugins for synthesis and effects

#### 2. JNI Bridge Layer
**Location**: `app/src/main/cpp/native-lib.cpp`

**Current State**: ✅ Partially implemented
- Basic audio engine functions exposed
- Sample loading and playback functions
- Plugin system integration
- Missing some advanced features

**Key Responsibilities**:
- Expose C++ AudioEngine methods to Kotlin
- Handle type conversion between Java/Kotlin and C++
- Manage JNI references and memory
- Provide thread-safe access to native functions

#### 3. AudioEngineImpl (Kotlin)
**Location**: `app/src/main/java/com/high/theone/audio/AudioEngineImpl.kt`

**Current State**: ✅ Well implemented
- Comprehensive coroutine-based async API
- Proper error handling and logging
- Complete plugin system integration
- Asset manager integration

**Key Responsibilities**:
- Provide high-level Kotlin API for audio operations
- Manage async operations using coroutines
- Handle Android-specific concerns (asset loading, permissions)
- Implement AudioEngineControl interface

## Components and Interfaces

### Sample Management System

#### SampleDataCpp Structure
```cpp
struct SampleDataCpp {
    std::string id;
    std::vector<float> samples;
    size_t sampleCount;
    uint32_t sampleRate;
    uint16_t channels;
    
    SampleDataCpp(const std::string& id, std::vector<float>&& samples, 
                  size_t count, uint32_t rate, uint16_t ch);
};
```

#### Sample Loading Pipeline
1. **File Detection**: Determine if loading from file system or Android assets
2. **Format Support**: WAV files via dr_wav library (mono/stereo, various bit depths)
3. **Memory Management**: Efficient loading with configurable offset/length
4. **Thread Safety**: Mutex-protected sample map for concurrent access
5. **Error Handling**: Comprehensive validation and error reporting

### Voice Management System

#### ActiveSound Structure
```cpp
struct ActiveSound {
    std::string sampleKey;
    float currentSampleIndex;
    float playbackSpeed;
    float volume;
    float pan;
    EnvelopeGenerator envelope;
    
    ActiveSound(const std::string& key, float vol, float panValue);
};
```

#### Voice Allocation Strategy
- **Dynamic Allocation**: Voices created on-demand for each sample trigger
- **Automatic Cleanup**: Voices removed when sample completes or envelope finishes
- **Polyphonic Support**: Multiple simultaneous voices without interference
- **Performance Optimization**: Efficient iteration and memory management

### Real-Time Audio Processing

#### Audio Callback Architecture
```cpp
oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, 
                                     void* audioData, int32_t numFrames) {
    // 1. Clear output buffer
    // 2. Process sample playback
    // 3. Generate test tones (if enabled)
    // 4. Process metronome
    // 5. Process AVST plugins
    // 6. Apply master processing and limiting
}
```

#### Processing Pipeline
1. **Sample Playback**: Mix all active voices with envelope processing
2. **Plugin Processing**: Route audio through loaded AVST plugins
3. **Effects Processing**: Apply real-time effects (reverb, delay, filters)
4. **Master Processing**: Apply master volume and soft limiting
5. **Output Routing**: Handle stereo panning and channel management

### AVST Plugin System Integration

#### Plugin Architecture
- **Base Interface**: `IAvstPlugin` provides standard plugin API
- **Parameter System**: Thread-safe parameter management with automation
- **MIDI Support**: Full note on/off and CC message routing
- **Audio I/O**: Flexible input/output buffer management
- **Plugin Loading**: Dynamic loading/unloading at runtime

#### Current Plugins
- **SketchingSynth**: Example synthesizer with oscillators, filter, and LFO
- **Extensible Framework**: Easy addition of new instruments and effects

## Data Models

### Core Audio Data Structures

#### Global Sample Rate Management
```cpp
class AudioEngine {
private:
    float globalSampleRate = 48000.0f;  // Single source of truth
    std::atomic<uint32_t> audioStreamSampleRate_{0};  // From Oboe negotiation
};
```

#### Pad Settings Configuration
```cpp
struct PadSettingsCpp {
    std::string id;
    std::string sampleId;
    PlaybackMode playbackMode;
    float volume;
    float pan;
    EnvelopeSettingsCpp ampEnvelope;
    std::vector<LfoSettingsCpp> lfos;
};
```

#### Metronome State Management
```cpp
struct MetronomeState {
    std::atomic<bool> enabled{false};
    std::atomic<float> bpm{120.0f};
    std::atomic<int> timeSignatureNum{4};
    std::atomic<int> timeSignatureDen{4};
    std::atomic<float> volume{0.7f};
    uint32_t audioStreamSampleRate = 0;
};
```

### Thread Safety Strategy

#### Mutex Protection Scheme
- **sampleMutex_**: Protects sample loading/unloading operations
- **activeSoundsMutex_**: Protects voice management and playback state
- **padSettingsMutex_**: Protects pad configuration changes
- **pluginsMutex_**: Protects plugin loading/unloading operations
- **metronomeStateMutex_**: Protects metronome configuration

#### Atomic Variables
- **masterVolume_**: Real-time volume control
- **testToneEnabled_**: Debug tone generation
- **oboeInitialized_**: Engine initialization state
- **isRecording_**: Recording state management

## Error Handling

### Error Categories and Strategies

#### 1. Initialization Errors
- **Oboe Stream Failure**: Fallback to higher latency modes
- **Sample Rate Negotiation**: Adapt to system-provided rates
- **Hardware Unavailability**: Graceful degradation with user notification

#### 2. Runtime Audio Errors
- **Buffer Underruns**: Continue processing with silence fill
- **Sample Loading Failures**: Skip problematic samples, continue operation
- **Plugin Crashes**: Isolate plugin failures, continue host operation

#### 3. Resource Management Errors
- **Memory Allocation**: Implement sample unloading and cleanup
- **File Access**: Handle permission and storage issues gracefully
- **Thread Synchronization**: Prevent deadlocks with timeout mechanisms

### Error Reporting System
```cpp
// Comprehensive logging with different severity levels
__android_log_print(ANDROID_LOG_ERROR, APP_NAME, "Critical error: %s", message);
__android_log_print(ANDROID_LOG_WARN, APP_NAME, "Warning: %s", message);
__android_log_print(ANDROID_LOG_INFO, APP_NAME, "Info: %s", message);
```

## Testing Strategy

### Unit Testing Framework

#### C++ Native Tests
**Location**: `app/src/test/cpp/`
- **AudioEngineTests.cpp**: Core engine functionality
- **EnvelopeGeneratorTests.cpp**: Envelope processing validation
- **Sample Loading Tests**: File format and error handling validation

#### Kotlin Integration Tests
**Location**: `app/src/test/java/`
- **AudioEngineImplTest.kt**: JNI bridge functionality
- **Async Operation Tests**: Coroutine-based operation validation
- **Error Handling Tests**: Exception and failure scenario testing

### Debug and Development Tools

#### Built-in Test Functions
```cpp
// Test sample generation for validation
bool createAndTriggerTestSample(const std::string& sampleKey);
void loadTestSample(const std::string& sampleKey);

// Performance monitoring
float getOboeReportedLatencyMillis() const;
int getActiveSoundsCountForTest() const;
```

#### Debug Screen Integration
- **Engine Status Monitoring**: Real-time latency and performance metrics
- **Sample Testing**: Load and trigger test samples for validation
- **Plugin Testing**: Load plugins and test MIDI note triggering
- **Audio System Diagnostics**: Report hardware capabilities and limitations

### Performance Testing

#### Latency Measurement
- **Round-trip Latency**: Measure input-to-output delay
- **Processing Latency**: Monitor audio callback execution time
- **Plugin Latency**: Measure additional delay from plugin processing

#### Resource Monitoring
- **Memory Usage**: Track sample loading and voice allocation
- **CPU Usage**: Monitor audio thread performance
- **Battery Impact**: Measure power consumption during operation

## Implementation Status

### ✅ Completed Components
1. **Core Oboe Integration**: Low-latency audio stream management
2. **Sample Loading System**: WAV file support with dr_wav
3. **Voice Management**: Polyphonic playback with envelope processing
4. **JNI Bridge**: Comprehensive Kotlin ↔ C++ interface
5. **AVST Plugin System**: Complete plugin architecture with SketchingSynth
6. **Debug Infrastructure**: Testing tools and performance monitoring

### 🔄 Partially Implemented
1. **Recording System**: Basic structure exists, needs completion
2. **Metronome**: Framework present, needs full implementation
3. **Advanced Effects**: Basic framework, needs more effect types
4. **Error Recovery**: Basic handling, needs more robust recovery mechanisms

### ❌ Missing Components
1. **MIDI Input/Output**: Hardware MIDI device support
2. **Advanced Sample Editing**: Trimming, time-stretching, pitch-shifting
3. **Sequence Engine**: Pattern recording and playback
4. **Project Persistence**: Save/load complete project state
5. **Audio Export**: Mixdown and bounce functionality

## Next Development Priorities

### Phase 1: Complete Core Foundation
1. **Finish Recording System**: Complete microphone input and WAV file writing
2. **Enhance Error Handling**: Implement robust recovery mechanisms
3. **Complete Metronome**: Full timing and synchronization implementation
4. **Optimize Performance**: Profile and optimize audio callback performance

### Phase 2: Expand Audio Capabilities
1. **MIDI Integration**: Add hardware MIDI input/output support
2. **Advanced Effects**: Implement reverb, delay, and filter effects
3. **Sample Editing**: Add trimming and basic processing capabilities
4. **Project Management**: Complete save/load functionality

### Phase 3: Advanced Features
1. **Sequence Engine**: Pattern recording and playback system
2. **Audio Export**: Mixdown and file export capabilities
3. **Plugin Expansion**: Additional AVST instruments and effects
4. **Performance Optimization**: Advanced voice stealing and resource management

The audio engine foundation provides a solid base for building the complete MPC-style application, with most core infrastructure already in place and ready for expansion.