package com.high.theone.features.compactui.accessibility

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.high.theone.model.PadState
import com.high.theone.model.TransportState
import com.high.theone.model.SequencerState

/**
 * Accessibility support utilities for the compact main UI.
 * Implements screen reader compatibility, minimum touch targets, and high contrast mode.
 * 
 * Requirements: 9.1 (screen reader compatibility), 9.2 (minimum touch targets), 
 *              9.3 (high contrast mode), 9.4 (accessibility compliance)
 */

/**
 * Minimum touch target size as per Android accessibility guidelines (44dp)
 */
val MinimumTouchTargetSize = 44.dp

/**
 * Ensures a modifier meets minimum touch target size requirements
 */
fun Modifier.ensureMinimumTouchTarget(
    minSize: Dp = MinimumTouchTargetSize
): Modifier = this.then(
    Modifier.size(minSize)
)

/**
 * Adds comprehensive semantic descriptions for drum pads
 */
fun Modifier.drumPadSemantics(
    padState: PadState,
    padIndex: Int,
    isPressed: Boolean = false,
    velocity: Float = 0f,
    isMidiHighlighted: Boolean = false
): Modifier = this.semantics {
    // Basic identification
    contentDescription = buildString {
        append("Drum pad ${padIndex + 1}")
        
        // Sample information
        when {
            padState.isLoading -> append(", loading sample")
            padState.hasAssignedSample -> {
                append(", ${padState.sampleName ?: "sample"} assigned")
                if (padState.volume < 1f) {
                    append(", volume ${(padState.volume * 100).toInt()}%")
                }
            }
            else -> append(", empty pad")
        }
        
        // Current state
        when {
            padState.isPlaying -> append(", currently playing")
            isPressed -> append(", pressed with ${(velocity * 100).toInt()}% velocity")
            isMidiHighlighted -> append(", MIDI input active")
        }
        
        // Interaction hints
        if (padState.canTrigger) {
            append(". Tap to trigger")
            if (padState.hasAssignedSample) {
                append(", long press for options")
            }
        } else if (!padState.hasAssignedSample) {
            append(". Tap to assign sample")
        }
    }
    
    // Role and state
    role = Role.Button
    
    // Custom actions
    if (padState.canTrigger) {
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Trigger pad",
                action = { true }
            )
        ).let { actions ->
            if (padState.hasAssignedSample) {
                actions + CustomAccessibilityAction(
                    label = "Open pad options",
                    action = { true }
                )
            } else actions
        }
    }
    
    // State descriptions
    stateDescription = when {
        padState.isPlaying -> "Playing"
        padState.isLoading -> "Loading"
        isPressed -> "Pressed"
        isMidiHighlighted -> "MIDI active"
        padState.hasAssignedSample -> "Ready"
        else -> "Empty"
    }
}

/**
 * Adds semantic descriptions for transport controls
 */
fun Modifier.transportControlSemantics(
    transportState: TransportState,
    controlType: TransportControlType
): Modifier = this.semantics {
    contentDescription = when (controlType) {
        TransportControlType.PLAY_PAUSE -> {
            if (transportState.isPlaying) "Pause playback" else "Start playback"
        }
        TransportControlType.STOP -> "Stop playback"
        TransportControlType.RECORD -> {
            if (transportState.isRecording) "Stop recording" else "Start recording"
        }
        TransportControlType.BPM_DECREASE -> "Decrease tempo, current ${transportState.bpm} BPM"
        TransportControlType.BPM_INCREASE -> "Increase tempo, current ${transportState.bpm} BPM"
        TransportControlType.BPM_DISPLAY -> "Tempo: ${transportState.bpm} beats per minute"
    }
    
    role = when (controlType) {
        TransportControlType.BPM_DISPLAY -> Role.Image
        else -> Role.Button
    }
    
    stateDescription = when (controlType) {
        TransportControlType.PLAY_PAUSE -> if (transportState.isPlaying) "Playing" else "Stopped"
        TransportControlType.RECORD -> if (transportState.isRecording) "Recording" else "Ready"
        else -> ""
    }
    
    // Add custom actions for BPM controls
    if (controlType == TransportControlType.BPM_DISPLAY) {
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Decrease tempo",
                action = { true }
            ),
            CustomAccessibilityAction(
                label = "Increase tempo", 
                action = { true }
            )
        )
    }
}

/**
 * Adds semantic descriptions for sequencer steps
 */
fun Modifier.sequencerStepSemantics(
    trackId: Int,
    stepIndex: Int,
    hasStep: Boolean,
    velocity: Int = 100,
    isCurrentStep: Boolean = false,
    trackName: String? = null
): Modifier = this.semantics {
    contentDescription = buildString {
        append("Step ${stepIndex + 1}")
        if (trackName != null) {
            append(" for $trackName")
        } else {
            append(" for track ${trackId + 1}")
        }
        
        if (hasStep) {
            append(", active with ${velocity} velocity")
        } else {
            append(", inactive")
        }
        
        if (isCurrentStep) {
            append(", currently playing")
        }
        
        append(". Tap to toggle, long press for options")
    }
    
    role = Role.Switch
    toggleableState = if (hasStep) ToggleableState.On else ToggleableState.Off
    
    stateDescription = when {
        isCurrentStep && hasStep -> "Playing"
        hasStep -> "Active"
        else -> "Inactive"
    }
    
    customActions = listOf(
        CustomAccessibilityAction(
            label = if (hasStep) "Deactivate step" else "Activate step",
            action = { true }
        ),
        CustomAccessibilityAction(
            label = "Edit step parameters",
            action = { true }
        )
    )
}

/**
 * Adds semantic descriptions for quick access panels
 */
fun Modifier.quickAccessPanelSemantics(
    panelType: String,
    isVisible: Boolean,
    hasContent: Boolean = true
): Modifier = this.semantics {
    contentDescription = buildString {
        append("$panelType panel")
        if (isVisible) {
            append(", currently visible")
        } else {
            append(", hidden")
        }
        if (hasContent) {
            append(". Swipe or tap to ${if (isVisible) "hide" else "show"}")
        } else {
            append(", no content available")
        }
    }
    
    role = Role.Tab
    
    stateDescription = if (isVisible) "Expanded" else "Collapsed"
    
    if (hasContent) {
        customActions = listOf(
            CustomAccessibilityAction(
                label = if (isVisible) "Hide panel" else "Show panel",
                action = { true }
            )
        )
    }
}

/**
 * Adds semantic descriptions for audio level meters
 */
fun Modifier.audioLevelSemantics(
    levelType: String,
    level: Float,
    isClipping: Boolean = false
): Modifier = this.semantics {
    val percentage = (level * 100).toInt()
    
    contentDescription = buildString {
        append("$levelType audio level: $percentage percent")
        if (isClipping) {
            append(", clipping detected")
        }
    }
    
    role = Role.Image
    
    stateDescription = when {
        isClipping -> "Clipping"
        level > 0.8f -> "High"
        level > 0.5f -> "Medium"
        level > 0.1f -> "Low"
        else -> "Silent"
    }
}

/**
 * Transport control types for semantic descriptions
 */
enum class TransportControlType {
    PLAY_PAUSE,
    STOP,
    RECORD,
    BPM_DECREASE,
    BPM_INCREASE,
    BPM_DISPLAY
}

/**
 * High contrast color scheme for accessibility
 */
@Composable
fun getHighContrastColors(): AccessibilityColorScheme {
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    return if (isSystemInDarkTheme) {
        AccessibilityColorScheme(
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF1A1A1A),
            onSurface = Color.White,
            primary = Color(0xFF00FF00), // Bright green
            onPrimary = Color.Black,
            secondary = Color(0xFF00FFFF), // Bright cyan
            onSecondary = Color.Black,
            error = Color(0xFFFF0000), // Bright red
            onError = Color.White,
            outline = Color.White,
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color.White
        )
    } else {
        AccessibilityColorScheme(
            background = Color.White,
            onBackground = Color.Black,
            surface = Color(0xFFF5F5F5),
            onSurface = Color.Black,
            primary = Color(0xFF0000FF), // Bright blue
            onPrimary = Color.White,
            secondary = Color(0xFF800080), // Purple
            onSecondary = Color.White,
            error = Color(0xFFCC0000), // Dark red
            onError = Color.White,
            outline = Color.Black,
            surfaceVariant = Color(0xFFE0E0E0),
            onSurfaceVariant = Color.Black
        )
    }
}

/**
 * Data class for high contrast color scheme
 */
data class AccessibilityColorScheme(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val error: Color,
    val onError: Color,
    val outline: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color
)

/**
 * Composable that provides high contrast mode support
 */
@Composable
fun AccessibilityHighContrastProvider(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (enabled) {
        val highContrastColors = getHighContrastColors()
        // In a real implementation, this would override the MaterialTheme colors
        // For now, we provide the colors through CompositionLocal
        CompositionLocalProvider(
            LocalAccessibilityColors provides highContrastColors
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * CompositionLocal for accessibility colors
 */
val LocalAccessibilityColors = compositionLocalOf<AccessibilityColorScheme?> { null }

/**
 * Hook to get current accessibility colors
 */
@Composable
fun accessibilityColors(): AccessibilityColorScheme? {
    return LocalAccessibilityColors.current
}

/**
 * Utility to check if minimum touch target size is met
 */
@Composable
fun checkMinimumTouchTargetSize(size: Dp): Boolean {
    return size >= MinimumTouchTargetSize
}

/**
 * Utility to get accessible touch target size
 */
@Composable
fun getAccessibleTouchTargetSize(requestedSize: Dp): Dp {
    return maxOf(requestedSize, MinimumTouchTargetSize)
}

/**
 * Modifier that ensures content meets accessibility contrast requirements
 */
fun Modifier.accessibleContrast(
    backgroundColor: Color,
    contentColor: Color,
    minimumContrast: Float = 4.5f
): Modifier = this.semantics {
    // Calculate contrast ratio (simplified)
    val contrast = calculateContrastRatio(backgroundColor, contentColor)
    if (contrast < minimumContrast) {
        // Add semantic hint about low contrast
        stateDescription = "Low contrast content"
    }
}

/**
 * Calculate contrast ratio between two colors (simplified implementation)
 */
private fun calculateContrastRatio(background: Color, foreground: Color): Float {
    // Simplified contrast calculation
    // In a real implementation, this would use proper luminance calculation
    val bgLuminance = (background.red + background.green + background.blue) / 3f
    val fgLuminance = (foreground.red + foreground.green + foreground.blue) / 3f
    
    val lighter = maxOf(bgLuminance, fgLuminance)
    val darker = minOf(bgLuminance, fgLuminance)
    
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Accessibility preferences data class
 */
data class AccessibilityPreferences(
    val highContrastMode: Boolean = false,
    val largeText: Boolean = false,
    val reducedMotion: Boolean = false,
    val screenReaderEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val audioDescriptionsEnabled: Boolean = false
)

/**
 * Composable to detect system accessibility settings
 */
@Composable
fun rememberAccessibilityPreferences(): AccessibilityPreferences {
    val configuration = LocalConfiguration.current
    
    return remember(configuration) {
        AccessibilityPreferences(
            // In a real implementation, these would read from system settings
            highContrastMode = false, // Would check AccessibilityManager
            largeText = configuration.fontScale > 1.3f,
            reducedMotion = false, // Would check system animation settings
            screenReaderEnabled = false, // Would check if TalkBack is enabled
            hapticFeedbackEnabled = true,
            audioDescriptionsEnabled = false
        )
    }
}