# Compact Main UI Design Document

## Overview

This design document outlines the architecture and implementation approach for rebuilding TheOne's user interface into a compact, feature-dense main screen. The design transforms the current navigation-based UI into a comprehensive MPC-style interface that maximizes functionality accessibility while maintaining performance and usability.

The solution employs a modular, responsive layout system that adapts to different screen sizes and orientations, using collapsible panels, overlay dialogs, and intelligent space management to pack maximum functionality into the available screen real estate.

## Architecture

### High-Level Architecture

```
CompactMainScreen (Root Container)
├── TopBar (Transport + Status)
├── MainContent (Responsive Layout)
│   ├── PrimaryPanel (Drum Pads + Quick Controls)
│   ├── SecondaryPanel (Sequencer + Mixer)
│   └── UtilityPanel (Collapsible Features)
└── BottomSheet/SidePanel (Advanced Features)
```

### Layout Strategy

The design uses a **responsive grid system** that adapts based on screen size and orientation:

- **Portrait Mode**: Vertical stacking with collapsible sections
- **Landscape Mode**: Horizontal layout with side panels
- **Tablet/Large Screens**: Multi-column layout with persistent panels

### State Management Architecture

```
CompactMainViewModel
├── DrumPadState (from existing DrumTrackViewModel)
├── SequencerState (from existing SequencerViewModel)
├── TransportState (new)
├── LayoutState (new - panel visibility, orientation)
├── MidiState (from existing MIDI components)
└── PerformanceState (new - monitoring UI performance)
```

## Components and Interfaces

### 1. CompactMainScreen (Root Component)

**Purpose**: Main container that orchestrates all UI components and manages responsive layout.

**Key Features**:
- Responsive layout management
- Panel visibility state
- Gesture handling for panel transitions
- Performance monitoring integration

**Interface**:
```kotlin
@Composable
fun CompactMainScreen(
    viewModel: CompactMainViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
)
```

### 2. TransportControlBar

**Purpose**: Always-visible transport controls and system status indicators.

**Key Features**:
- Play/Stop/Record buttons with visual feedback
- BPM display and adjustment
- MIDI sync status indicator
- Audio level meters
- Battery and performance indicators

**Layout**: Horizontal bar at top of screen, ~56dp height

**Interface**:
```kotlin
@Composable
fun TransportControlBar(
    transportState: TransportState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier
)
```

### 3. ResponsiveMainLayout

**Purpose**: Adaptive layout container that manages primary content areas based on screen configuration.

**Layout Modes**:
- **Compact Portrait**: Single column with tabs/accordion
- **Standard Portrait**: Two-section vertical layout
- **Landscape**: Three-column horizontal layout
- **Tablet**: Multi-panel dashboard layout

**Interface**:
```kotlin
@Composable
fun ResponsiveMainLayout(
    configuration: ScreenConfiguration,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
)
```

### 4. CompactDrumPadGrid

**Purpose**: Enhanced version of existing PadGrid optimized for space efficiency.

**Key Features**:
- Smaller pad size with intelligent touch targets
- Sample name/waveform preview overlays
- Velocity-sensitive visual feedback
- Long-press context menus
- MIDI input visual indicators

**Enhancements over existing PadGrid**:
- Adaptive sizing based on available space
- Overlay information display
- Gesture shortcuts for common actions

### 5. InlineSequencer

**Purpose**: Compact sequencer interface integrated into main screen.

**Key Features**:
- Horizontal step display (16 steps visible)
- Track selection via compact tabs or dropdown
- Step editing via tap/long-press
- Pattern switching controls
- Mute/solo indicators per track

**Space Optimization**:
- Collapsible track list
- Overlay step parameter editing
- Gesture-based step manipulation

### 6. QuickAccessPanel

**Purpose**: Sliding panel system for advanced features that don't fit in main layout.

**Panel Types**:
- **Sampling Panel**: Recording controls, sample browser
- **MIDI Panel**: Device settings, mapping controls
- **Mixer Panel**: Track levels, effects, routing
- **Settings Panel**: App preferences, audio settings

**Interaction Patterns**:
- Bottom sheet for portrait mode
- Side panel for landscape mode
- Overlay dialog for complex features

### 7. AdaptiveBottomSheet

**Purpose**: Context-aware bottom sheet that provides access to detailed controls.

**Key Features**:
- Swipe gestures for show/hide
- Multiple snap points (peek, half, full)
- Content switching based on context
- Persistent handle for easy access

**Content Areas**:
- Sample editing and trimming
- Advanced sequencer features
- MIDI mapping interface
- Performance monitoring

## Data Models

### ScreenConfiguration

```kotlin
data class ScreenConfiguration(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val orientation: Orientation,
    val densityDpi: Int,
    val isTablet: Boolean
) {
    val layoutMode: LayoutMode
        get() = when {
            isTablet -> LayoutMode.TABLET
            orientation == Orientation.LANDSCAPE -> LayoutMode.LANDSCAPE
            screenHeight < 600.dp -> LayoutMode.COMPACT_PORTRAIT
            else -> LayoutMode.STANDARD_PORTRAIT
        }
}

enum class LayoutMode {
    COMPACT_PORTRAIT,
    STANDARD_PORTRAIT,
    LANDSCAPE,
    TABLET
}
```

### CompactUIState

```kotlin
data class CompactUIState(
    val transportState: TransportState,
    val drumPadState: DrumPadState,
    val sequencerState: SequencerState,
    val layoutState: LayoutState,
    val panelStates: Map<PanelType, PanelState>,
    val performanceMetrics: PerformanceMetrics
)

data class LayoutState(
    val configuration: ScreenConfiguration,
    val activePanels: Set<PanelType>,
    val panelVisibility: Map<PanelType, Boolean>,
    val collapsedSections: Set<SectionType>
)

enum class PanelType {
    SAMPLING, MIDI, MIXER, SETTINGS, SAMPLE_EDITOR
}
```

### TransportState

```kotlin
data class TransportState(
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val bpm: Int = 120,
    val currentPosition: Long = 0,
    val midiSyncEnabled: Boolean = false,
    val midiSyncStatus: MidiSyncStatus = MidiSyncStatus.DISCONNECTED,
    val audioLevels: AudioLevels = AudioLevels()
)

data class AudioLevels(
    val masterLevel: Float = 0f,
    val inputLevel: Float = 0f,
    val peakLevel: Float = 0f
)
```

## Error Handling

### Performance Monitoring

**Strategy**: Continuous monitoring of UI performance with automatic degradation when needed.

**Implementation**:
- Frame rate monitoring using Compose metrics
- Memory usage tracking
- Automatic feature disabling under stress
- User notification of performance issues

**Degradation Levels**:
1. **Normal**: All features enabled
2. **Reduced**: Disable animations, reduce update frequency
3. **Minimal**: Hide non-essential UI elements
4. **Emergency**: Fallback to simple list-based UI

### Layout Failure Handling

**Strategy**: Graceful fallback when responsive layout calculations fail.

**Fallback Sequence**:
1. Retry with simplified layout
2. Fall back to single-column layout
3. Display error message with manual layout selection
4. Provide navigation-based fallback option

### Touch Input Conflicts

**Strategy**: Intelligent touch target management to prevent accidental triggers.

**Implementation**:
- Minimum touch target sizes (44dp)
- Touch exclusion zones during gestures
- Confirmation dialogs for destructive actions
- Undo/redo system for accidental changes

## Testing Strategy

### Unit Testing

**Components to Test**:
- Layout calculation logic
- State management functions
- Performance monitoring utilities
- Touch gesture recognition

**Test Coverage Goals**:
- 90% coverage for layout logic
- 100% coverage for state management
- Performance regression tests

### Integration Testing

**Scenarios**:
- Screen rotation handling
- Panel transition animations
- Multi-touch gesture handling
- MIDI input integration
- Audio engine integration

### Performance Testing

**Metrics**:
- Frame rate during complex interactions
- Memory usage with all panels open
- Touch response latency
- Audio thread impact measurement

**Test Devices**:
- Low-end Android devices (minimum spec)
- High-end devices (performance validation)
- Tablets (layout validation)
- Various screen densities

### Accessibility Testing

**Requirements**:
- Screen reader compatibility
- Minimum touch target sizes
- Color contrast validation
- Keyboard navigation support
- Voice control integration

### User Experience Testing

**Scenarios**:
- First-time user onboarding
- Expert user workflow efficiency
- Error recovery procedures
- Performance under stress

**Metrics**:
- Task completion time
- Error rate reduction
- User satisfaction scores
- Feature discoverability

## Implementation Phases

### Phase 1: Core Layout System
- Responsive layout container
- Basic transport controls
- Screen configuration detection
- Performance monitoring foundation

### Phase 2: Primary Components
- Enhanced drum pad grid
- Inline sequencer
- Basic panel system
- Touch gesture handling

### Phase 3: Advanced Features
- Bottom sheet implementation
- MIDI integration
- Sample editing integration
- Advanced animations

### Phase 4: Optimization & Polish
- Performance optimization
- Accessibility improvements
- User customization options
- Error handling refinement

## Technical Considerations

### Performance Optimization

**Compose Optimization**:
- Use `remember` and `derivedStateOf` for expensive calculations
- Implement `LazyColumn`/`LazyRow` for large lists
- Minimize recomposition scope with stable parameters
- Use `Modifier.drawBehind` for custom drawing

**Memory Management**:
- Lazy loading of panel content
- Image caching for waveform displays
- Proper disposal of audio resources
- Memory leak prevention in ViewModels

### Accessibility Implementation

**Screen Reader Support**:
- Semantic descriptions for all interactive elements
- Logical navigation order
- State announcements for dynamic content
- Custom accessibility actions for complex gestures

**Visual Accessibility**:
- High contrast mode support
- Scalable text and UI elements
- Color-blind friendly indicators
- Reduced motion options

### Platform Integration

**Android-Specific Features**:
- Edge-to-edge display support
- Adaptive icons and shortcuts
- Picture-in-picture mode consideration
- Multi-window support
- Foldable device adaptation

**Hardware Integration**:
- Hardware button mapping
- Stylus input support
- External keyboard shortcuts
- MIDI controller integration
- Audio focus management