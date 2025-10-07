# Compact UI Animation System

This directory contains the comprehensive animation system for TheOne's compact UI. The system provides consistent, performant, and accessible animations across all UI components.

## Architecture Overview

The animation system is built around several key principles:

1. **Consistency**: All animations use the same timing curves and durations
2. **Performance**: Animations are optimized for 60fps performance
3. **Accessibility**: All animations respect accessibility settings
4. **Modularity**: Each animation type is self-contained and reusable

## Core Components

### AnimationSystem.kt
Central configuration for all animations including:
- Standard durations (FAST_ANIMATION, MEDIUM_ANIMATION, SLOW_ANIMATION)
- Easing curves (FastOutSlowIn, FastOutLinearIn, LinearOutSlowIn)
- Spring specifications (BounceSpring, SmoothSpring, QuickSpring)

### VisualFeedbackSystem.kt
Visual feedback components for user interactions:
- `TouchRipple`: Ripple effect for touch interactions
- `SuccessFeedback`: Success confirmation animation
- `ErrorFeedback`: Error indication with shake animation
- `AudioLevelMeter`: Real-time audio level visualization
- `SequencerStepHighlight`: Step sequencer visual feedback
- `MidiActivityIndicator`: MIDI connection status indicator

### MicroInteractions.kt
Enhanced UI components with built-in animations:
- `AnimatedButton`: Button with press animation and haptic feedback
- `AnimatedFAB`: Floating Action Button with enhanced animations
- `AnimatedSwitch`: Switch with smooth transitions
- `AnimatedSlider`: Slider with enhanced visual feedback
- `AnimatedCard`: Card with hover and press animations
- `AnimatedChip`: Chip with selection animation
- `AnimatedIconButton`: Icon button with ripple and scale animation

### LoadingStates.kt
Loading indicators and progress animations:
- `SampleLoadingIndicator`: Sample loading with waveform animation
- `AudioProcessingIndicator`: Audio processing status
- `MidiConnectionIndicator`: MIDI connection status with progress
- `ProjectOperationIndicator`: Project save/load progress
- `SpinningLoadingIndicator`: Generic spinning loader
- `SkeletonLoader`: Skeleton loading for UI components

### AnimationIntegration.kt
Integration layer that coordinates animations:
- `AnimationCoordinator`: Central animation state management
- `AnimationProvider`: Composition local provider for animation context
- Performance monitoring and automatic degradation
- Coordinated animations for complex UI interactions

## Usage Examples

### Basic Button Animation
```kotlin
MicroInteractions.AnimatedButton(
    onClick = { /* handle click */ },
    hapticEnabled = true
) {
    Text("Click Me")
}
```

### Audio Level Meter
```kotlin
VisualFeedbackSystem.AudioLevelMeter(
    level = audioLevel, // 0f to 1f
    modifier = Modifier.size(width = 40.dp, height = 8.dp)
)
```

### Panel Transition
```kotlin
PanelTransition(
    visible = panelVisible,
    direction = PanelDirection.Bottom
) {
    // Panel content
}
```

### Loading Indicator
```kotlin
LoadingStates.SampleLoadingIndicator(
    isLoading = isLoading,
    progress = loadingProgress,
    sampleName = "Sample.wav"
)
```

### Coordinated Animation System
```kotlin
AnimationProvider(
    coordinator = remember { AnimationCoordinator() }
) {
    // Your UI components with coordinated animations
}
```

## Performance Considerations

### Animation Performance Modes
The system supports three performance modes:

1. **HIGH_PERFORMANCE**: Reduced animation durations for better responsiveness
2. **NORMAL**: Standard animation durations and complexity
3. **BATTERY_SAVER**: Longer durations and reduced complexity to save battery

### Automatic Performance Adjustment
The `AnimationCoordinator` automatically adjusts performance based on:
- CPU usage levels
- Frame rate monitoring
- Memory pressure
- Battery level (when available)

### Performance Optimization Guidelines

1. **Use `remember` for expensive calculations**:
```kotlin
val expensiveValue = remember(key) { 
    expensiveCalculation() 
}
```

2. **Minimize recomposition scope**:
```kotlin
val animatedValue by animateFloatAsState(
    targetValue = target,
    label = "descriptive_label" // Always provide labels
)
```

3. **Use stable parameters**:
```kotlin
@Stable
data class AnimationConfig(
    val duration: Int,
    val easing: Easing
)
```

## Accessibility Integration

### Screen Reader Support
All animated components include proper semantic descriptions:
```kotlin
Modifier.semantics {
    contentDescription = "Loading sample: $sampleName"
    stateDescription = "Progress: ${(progress * 100).toInt()}%"
}
```

### Reduced Motion Support
The system respects system accessibility settings:
```kotlin
val animationDuration = if (reduceMotionEnabled) 0 else standardDuration
```

### High Contrast Mode
Visual feedback adapts to high contrast requirements:
```kotlin
val feedbackColor = if (highContrastMode) {
    MaterialTheme.colorScheme.onBackground
} else {
    MaterialTheme.colorScheme.primary
}
```

## Testing

### Unit Tests
The animation system includes comprehensive unit tests in `AnimationSystemTest.kt`:
- Animation duration and easing verification
- Component interaction testing
- State change validation
- Performance regression testing

### Integration Tests
Integration tests verify:
- Cross-component animation coordination
- Performance under load
- Accessibility compliance
- Memory leak prevention

### Visual Testing
Use the debug panel to test animations:
```kotlin
if (BuildConfig.DEBUG) {
    AnimationDebugPanel(
        coordinator = animationCoordinator,
        onPerformanceTest = { /* run performance tests */ }
    )
}
```

## Requirements Mapping

This animation system fulfills the following requirements:

### Requirement 7.1 (Visual Feedback)
- All interactive elements provide immediate visual feedback
- Touch ripples, button press animations, and state changes
- Velocity-sensitive feedback for drum pads

### Requirement 7.2 (Audio Activity)
- Real-time audio level meters with smooth animations
- MIDI activity indicators with pulsing animations
- Visual feedback for audio processing states

### Requirement 7.3 (System Status)
- Loading states with progress indicators
- Error states with shake animations
- Success confirmations with bounce animations

## Future Enhancements

### Planned Features
1. **Custom Animation Curves**: User-definable easing curves
2. **Gesture-Based Animations**: Swipe and pinch gesture animations
3. **3D Transformations**: Depth and perspective animations for tablets
4. **Particle Effects**: Advanced visual effects for special interactions
5. **Sound-Reactive Animations**: Animations that respond to audio analysis

### Performance Improvements
1. **GPU Acceleration**: Offload complex animations to GPU
2. **Predictive Loading**: Pre-load animation resources
3. **Adaptive Quality**: Dynamic quality adjustment based on device capabilities
4. **Background Processing**: Non-blocking animation calculations

## Troubleshooting

### Common Issues

1. **Animations Not Showing**:
   - Check if animations are enabled in AnimationCoordinator
   - Verify accessibility settings aren't disabling animations
   - Ensure proper composition local provider setup

2. **Performance Issues**:
   - Monitor CPU usage and adjust performance mode
   - Check for excessive recompositions
   - Verify animation labels are provided for debugging

3. **Accessibility Problems**:
   - Ensure semantic descriptions are provided
   - Test with screen readers enabled
   - Verify high contrast mode compatibility

### Debug Tools

Use the built-in debug tools to diagnose issues:
```kotlin
// Enable animation debugging
AnimationCoordinator.setDebugMode(true)

// Monitor performance
val metrics = AnimationCoordinator.getPerformanceMetrics()

// Test accessibility
AnimationCoordinator.testAccessibility()
```

## Contributing

When adding new animations:

1. Follow the established naming conventions
2. Include proper accessibility support
3. Add comprehensive tests
4. Document performance characteristics
5. Provide usage examples
6. Update this README with new components

### Code Style
- Use descriptive animation labels
- Provide semantic descriptions for accessibility
- Include performance considerations in documentation
- Follow Material Design 3 animation principles