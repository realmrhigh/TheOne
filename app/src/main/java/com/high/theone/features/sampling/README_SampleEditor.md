# Sample Editing System

This document describes the comprehensive sample editing system implemented for TheOne MPC app, covering all aspects of sample manipulation, trimming, and processing.

## Overview

The sample editing system provides professional-grade audio editing capabilities within the mobile MPC environment. It includes waveform visualization, interactive trimming, audio processing operations, and a complete undo/redo system.

## Architecture

### Core Components

1. **SampleEditor.kt** - Main waveform editor with zoom/scroll functionality
2. **SampleTrimming.kt** - Advanced trimming controls with draggable handles
3. **SampleProcessing.kt** - Audio processing operations (normalize, gain, reverse, etc.)
4. **SampleEditorScreen.kt** - Integration screen with tabbed interface

### Key Features

#### Waveform Display & Navigation
- **Interactive Waveform**: Canvas-based waveform rendering with touch interaction
- **Zoom & Scroll**: 1x to 10x zoom with smooth scrolling navigation
- **Playback Position**: Real-time playback indicator with scrubbing support
- **Visual Feedback**: Trim regions, fade areas, and processing indicators

#### Sample Trimming
- **Draggable Handles**: Interactive trim start/end handles with visual feedback
- **Real-time Preview**: Live preview of trimmed audio during editing
- **Precise Input**: Millisecond-accurate time-based trim input
- **Quick Actions**: Reset, tighten, and expand trim operations
- **Fade Controls**: Configurable fade-in/fade-out with visual representation

#### Audio Processing
- **Normalize**: Peak and RMS normalization with target level control
- **Gain Adjustment**: Linear gain control with dB display and presets
- **Reverse**: Sample reversal with toggle control
- **Time Stretching**: Speed adjustment with pitch preservation option
- **Pitch Shifting**: Semitone-based pitch adjustment (-12 to +12 semitones)
- **Format Conversion**: WAV, FLAC, MP3 with quality settings

#### Undo/Redo System
- **Operation History**: Complete history of all processing operations
- **Visual History**: Timeline view of applied operations
- **Jump to State**: Click any history point to jump to that state
- **Reset All**: One-click reset to original state

## Usage

### Basic Editing Workflow

1. **Open Sample Editor**: Navigate to sample editor from pad configuration or sample browser
2. **Navigate Waveform**: Use zoom/scroll controls to focus on desired region
3. **Set Trim Points**: Drag trim handles or use precise time input
4. **Preview Changes**: Use preview controls to audition trimmed audio
5. **Apply Processing**: Add normalize, gain, or other effects as needed
6. **Apply or Cancel**: Save changes or discard to return to original

### Advanced Features

#### Trim Preview Modes
- **Full Sample**: Shows complete waveform with trim region highlighted
- **Trimmed Only**: Shows only trimmed portion stretched to fill view

#### Processing Operations
- **Normalize**: Automatically adjust levels to prevent clipping
- **Gain**: Manual volume adjustment with visual feedback
- **Reverse**: Flip sample playback direction
- **Time Stretch**: Change speed without affecting pitch
- **Pitch Shift**: Change pitch without affecting speed

#### Format Conversion
- **Quality Settings**: Sample rate, bit depth, channel configuration
- **Format Options**: WAV (uncompressed), FLAC (lossless), MP3 (compressed)
- **Compatibility**: Automatic format validation and conversion

## Technical Implementation

### Waveform Rendering
```kotlin
// Efficient waveform drawing with zoom support
private fun DrawScope.drawWaveformWithZoom(
    waveformData: FloatArray,
    zoomLevel: Float,
    scrollPosition: Float,
    // ... other parameters
) {
    // Calculate visible range based on zoom and scroll
    val visibleStart = scrollPosition
    val visibleEnd = min(1f, scrollPosition + (1f / zoomLevel))
    
    // Render only visible portion for performance
    // Draw waveform, trim regions, playback position
}
```

### Interactive Trim Handles
```kotlin
// Touch gesture handling for trim operations
.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset ->
            // Determine which handle is being dragged
            val normalizedX = offset.x / size.width
            // Check proximity to start/end handles
        },
        onDrag = { _, dragAmount ->
            // Update trim settings based on drag
            val dragDelta = dragAmount.x / size.width
            // Apply constraints and update state
        }
    )
}
```

### Processing Pipeline
```kotlin
// Extensible processing operation system
sealed class ProcessingOperation {
    abstract val displayName: String
    
    data class Normalize(val mode: NormalizeMode, val targetLevel: Float) : ProcessingOperation()
    data class Gain(val multiplier: Float) : ProcessingOperation()
    object Reverse : ProcessingOperation()
    // ... other operations
}
```

## Integration Points

### Audio Engine Integration
- **Waveform Data**: Provided by native audio engine analysis
- **Playback Control**: Direct integration with Oboe audio system
- **Processing Application**: Native processing for optimal performance

### Sample Repository
- **Metadata Updates**: Automatic trim settings persistence
- **File Management**: Original file preservation with metadata overlay
- **Project Integration**: Sample changes reflected in pad assignments

### UI Framework
- **Material Design 3**: Consistent with app-wide design system
- **Compose Integration**: Reactive UI updates with StateFlow
- **Accessibility**: Screen reader support and keyboard navigation

## Performance Considerations

### Waveform Rendering
- **Efficient Drawing**: Only render visible waveform portions
- **Sample Decimation**: Intelligent sample reduction for zoom levels
- **Canvas Optimization**: Minimize draw calls and path operations

### Memory Management
- **Lazy Loading**: Load waveform data on demand
- **Data Compression**: Efficient waveform data representation
- **Cleanup**: Proper resource disposal on editor close

### Audio Processing
- **Native Operations**: CPU-intensive processing in C++ layer
- **Background Threading**: Non-blocking UI during processing
- **Progress Feedback**: Real-time progress updates for long operations

## Error Handling

### File Operations
- **Validation**: Format and integrity checking before processing
- **Backup**: Automatic backup creation before destructive operations
- **Recovery**: Graceful handling of corrupted or missing files

### Processing Errors
- **Operation Validation**: Parameter validation before processing
- **Rollback**: Automatic rollback on processing failures
- **User Feedback**: Clear error messages with recovery suggestions

### Memory Constraints
- **Size Limits**: Automatic handling of large sample files
- **Quality Degradation**: Graceful quality reduction when needed
- **User Notification**: Clear communication of limitations

## Future Enhancements

### Advanced Processing
- **Multi-band EQ**: Frequency-based audio shaping
- **Compression**: Dynamic range control
- **Spectral Editing**: Frequency domain manipulation

### Collaboration Features
- **Version History**: Multiple save states with branching
- **Sharing**: Export processed samples with metadata
- **Templates**: Reusable processing chains

### Performance Optimization
- **GPU Acceleration**: Hardware-accelerated processing
- **Streaming**: Real-time processing for large files
- **Caching**: Intelligent caching of processed audio

## Requirements Fulfilled

This implementation addresses all requirements from the specification:

- **8.1**: Waveform visualization with interactive display ✅
- **8.2**: Zoom and scroll functionality for navigation ✅
- **8.3**: Sample trimming with visual handles and real-time preview ✅
- **8.4**: Fade-in/fade-out options with visual feedback ✅
- **8.5**: Processing operations (normalize, gain, reverse, time-stretch) ✅
- **8.6**: Complete undo/redo functionality with operation history ✅

The system provides a professional-grade sample editing experience that rivals desktop DAW applications while being optimized for mobile touch interaction.