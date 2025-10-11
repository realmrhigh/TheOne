package com.high.theone.features.compactui.accessibility

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/**
 * Keyboard navigation support for compact UI components.
 * Provides focus management, keyboard shortcuts, and navigation helpers.
 * 
 * Requirements: 9.5 (keyboard navigation support), 9.1 (accessibility compliance)
 */

/**
 * Keyboard navigation manager for the compact main UI
 */
class KeyboardNavigationManager {
    
    /**
     * Handle keyboard shortcuts for transport controls
     */
    fun handleTransportKeyEvents(
        keyEvent: KeyEvent,
        onPlay: () -> Unit,
        onStop: () -> Unit,
        onRecord: () -> Unit,
        onBpmIncrease: () -> Unit,
        onBpmDecrease: () -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        return when {
            keyEvent.key == Key.Spacebar -> {
                onPlay()
                true
            }
            keyEvent.key == Key.S && keyEvent.isCtrlPressed -> {
                onStop()
                true
            }
            keyEvent.key == Key.R && keyEvent.isCtrlPressed -> {
                onRecord()
                true
            }
            keyEvent.key == Key.Plus || keyEvent.key == Key.Equals -> {
                onBpmIncrease()
                true
            }
            keyEvent.key == Key.Minus -> {
                onBpmDecrease()
                true
            }
            else -> false
        }
    }
    
    /**
     * Handle keyboard navigation for drum pad grid
     */
    fun handleDrumPadKeyEvents(
        keyEvent: KeyEvent,
        currentPadIndex: Int,
        onPadSelect: (Int) -> Unit,
        onPadTrigger: (Int) -> Unit,
        onPadOptions: (Int) -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        return when (keyEvent.key) {
            Key.DirectionUp -> {
                val newIndex = (currentPadIndex - 4).coerceAtLeast(0)
                onPadSelect(newIndex)
                true
            }
            Key.DirectionDown -> {
                val newIndex = (currentPadIndex + 4).coerceAtMost(15)
                onPadSelect(newIndex)
                true
            }
            Key.DirectionLeft -> {
                val newIndex = if (currentPadIndex % 4 > 0) currentPadIndex - 1 else currentPadIndex
                onPadSelect(newIndex)
                true
            }
            Key.DirectionRight -> {
                val newIndex = if (currentPadIndex % 4 < 3) currentPadIndex + 1 else currentPadIndex
                onPadSelect(newIndex)
                true
            }
            Key.Enter, Key.Spacebar -> {
                onPadTrigger(currentPadIndex)
                true
            }
            Key.F10, Key.Menu -> {
                onPadOptions(currentPadIndex)
                true
            }
            // Number keys 1-9, 0 for pads 1-10
            Key.One -> { onPadSelect(0); true }
            Key.Two -> { onPadSelect(1); true }
            Key.Three -> { onPadSelect(2); true }
            Key.Four -> { onPadSelect(3); true }
            Key.Five -> { onPadSelect(4); true }
            Key.Six -> { onPadSelect(5); true }
            Key.Seven -> { onPadSelect(6); true }
            Key.Eight -> { onPadSelect(7); true }
            Key.Nine -> { onPadSelect(8); true }
            Key.Zero -> { onPadSelect(9); true }
            else -> false
        }
    }
    
    /**
     * Handle keyboard navigation for sequencer steps
     */
    fun handleSequencerKeyEvents(
        keyEvent: KeyEvent,
        currentTrack: Int,
        currentStep: Int,
        maxTracks: Int,
        maxSteps: Int,
        onStepSelect: (track: Int, step: Int) -> Unit,
        onStepToggle: (track: Int, step: Int) -> Unit,
        onTrackSelect: (track: Int) -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        
        return when (keyEvent.key) {
            Key.DirectionUp -> {
                val newTrack = (currentTrack - 1).coerceAtLeast(0)
                onStepSelect(newTrack, currentStep)
                true
            }
            Key.DirectionDown -> {
                val newTrack = (currentTrack + 1).coerceAtMost(maxTracks - 1)
                onStepSelect(newTrack, currentStep)
                true
            }
            Key.DirectionLeft -> {
                val newStep = (currentStep - 1).coerceAtLeast(0)
                onStepSelect(currentTrack, newStep)
                true
            }
            Key.DirectionRight -> {
                val newStep = (currentStep + 1).coerceAtMost(maxSteps - 1)
                onStepSelect(currentTrack, newStep)
                true
            }
            Key.Enter, Key.Spacebar -> {
                onStepToggle(currentTrack, currentStep)
                true
            }
            Key.Tab -> {
                if (keyEvent.isShiftPressed) {
                    val newTrack = (currentTrack - 1).coerceAtLeast(0)
                    onTrackSelect(newTrack)
                } else {
                    val newTrack = (currentTrack + 1).coerceAtMost(maxTracks - 1)
                    onTrackSelect(newTrack)
                }
                true
            }
            else -> false
        }
    }
}

/**
 * Modifier that adds keyboard navigation support to focusable elements
 */
fun Modifier.keyboardNavigable(
    onKeyEvent: (KeyEvent) -> Boolean = { false }
): Modifier = this
    .focusable()
    .onKeyEvent(onKeyEvent)

/**
 * Modifier that adds drum pad keyboard navigation
 */
@Composable
fun Modifier.drumPadKeyboardNavigation(
    padIndex: Int,
    isSelected: Boolean = false,
    onPadSelect: (Int) -> Unit,
    onPadTrigger: (Int) -> Unit,
    onPadOptions: (Int) -> Unit
): Modifier {
    val navigationManager = remember { KeyboardNavigationManager() }
    
    return this
        .focusable()
        .onKeyEvent { keyEvent ->
            if (isSelected) {
                navigationManager.handleDrumPadKeyEvents(
                    keyEvent = keyEvent,
                    currentPadIndex = padIndex,
                    onPadSelect = onPadSelect,
                    onPadTrigger = onPadTrigger,
                    onPadOptions = onPadOptions
                )
            } else {
                false
            }
        }
        .semantics {
            // Add keyboard navigation hints
            customActions = listOf(
                CustomAccessibilityAction(
                    label = "Use arrow keys to navigate",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press Enter or Space to trigger",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press F10 for options",
                    action = { true }
                )
            )
        }
}

/**
 * Modifier that adds transport control keyboard navigation
 */
@Composable
fun Modifier.transportKeyboardNavigation(
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onBpmIncrease: () -> Unit,
    onBpmDecrease: () -> Unit
): Modifier {
    val navigationManager = remember { KeyboardNavigationManager() }
    
    return this
        .focusable()
        .onKeyEvent { keyEvent ->
            navigationManager.handleTransportKeyEvents(
                keyEvent = keyEvent,
                onPlay = onPlay,
                onStop = onStop,
                onRecord = onRecord,
                onBpmIncrease = onBpmIncrease,
                onBpmDecrease = onBpmDecrease
            )
        }
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = "Press Space to play/pause",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press Ctrl+S to stop",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press Ctrl+R to record",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press +/- to adjust tempo",
                    action = { true }
                )
            )
        }
}

/**
 * Modifier that adds sequencer step keyboard navigation
 */
@Composable
fun Modifier.sequencerStepKeyboardNavigation(
    trackIndex: Int,
    stepIndex: Int,
    isSelected: Boolean = false,
    maxTracks: Int,
    maxSteps: Int,
    onStepSelect: (track: Int, step: Int) -> Unit,
    onStepToggle: (track: Int, step: Int) -> Unit,
    onTrackSelect: (track: Int) -> Unit
): Modifier {
    val navigationManager = remember { KeyboardNavigationManager() }
    
    return this
        .focusable()
        .onKeyEvent { keyEvent ->
            if (isSelected) {
                navigationManager.handleSequencerKeyEvents(
                    keyEvent = keyEvent,
                    currentTrack = trackIndex,
                    currentStep = stepIndex,
                    maxTracks = maxTracks,
                    maxSteps = maxSteps,
                    onStepSelect = onStepSelect,
                    onStepToggle = onStepToggle,
                    onTrackSelect = onTrackSelect
                )
            } else {
                false
            }
        }
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = "Use arrow keys to navigate steps",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press Enter or Space to toggle step",
                    action = { true }
                ),
                CustomAccessibilityAction(
                    label = "Press Tab to switch tracks",
                    action = { true }
                )
            )
        }
}

/**
 * Focus management utilities for complex layouts
 */
class CompactUIFocusManager {
    
    /**
     * Focus order for the compact main UI
     */
    enum class FocusArea {
        TRANSPORT_CONTROLS,
        DRUM_PAD_GRID,
        SEQUENCER_CONTROLS,
        QUICK_ACCESS_PANELS,
        BOTTOM_SHEET
    }
    
    /**
     * Navigate to the next focus area
     */
    fun navigateToNextArea(
        currentArea: FocusArea,
        focusManager: FocusManager
    ): FocusArea {
        val nextArea = when (currentArea) {
            FocusArea.TRANSPORT_CONTROLS -> FocusArea.DRUM_PAD_GRID
            FocusArea.DRUM_PAD_GRID -> FocusArea.SEQUENCER_CONTROLS
            FocusArea.SEQUENCER_CONTROLS -> FocusArea.QUICK_ACCESS_PANELS
            FocusArea.QUICK_ACCESS_PANELS -> FocusArea.BOTTOM_SHEET
            FocusArea.BOTTOM_SHEET -> FocusArea.TRANSPORT_CONTROLS
        }
        
        // In a real implementation, this would move focus to the appropriate component
        focusManager.moveFocus(FocusDirection.Next)
        
        return nextArea
    }
    
    /**
     * Navigate to the previous focus area
     */
    fun navigateToPreviousArea(
        currentArea: FocusArea,
        focusManager: FocusManager
    ): FocusArea {
        val previousArea = when (currentArea) {
            FocusArea.TRANSPORT_CONTROLS -> FocusArea.BOTTOM_SHEET
            FocusArea.DRUM_PAD_GRID -> FocusArea.TRANSPORT_CONTROLS
            FocusArea.SEQUENCER_CONTROLS -> FocusArea.DRUM_PAD_GRID
            FocusArea.QUICK_ACCESS_PANELS -> FocusArea.SEQUENCER_CONTROLS
            FocusArea.BOTTOM_SHEET -> FocusArea.QUICK_ACCESS_PANELS
        }
        
        focusManager.moveFocus(FocusDirection.Previous)
        
        return previousArea
    }
}

/**
 * Composable that provides keyboard navigation context
 */
@Composable
fun KeyboardNavigationProvider(
    content: @Composable () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val compactUIFocusManager = remember { CompactUIFocusManager() }
    var currentFocusArea by remember { mutableStateOf(CompactUIFocusManager.FocusArea.TRANSPORT_CONTROLS) }
    
    CompositionLocalProvider(
        LocalKeyboardNavigationManager provides compactUIFocusManager,
        LocalCurrentFocusArea provides currentFocusArea
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when {
                            keyEvent.key == Key.F6 -> {
                                currentFocusArea = compactUIFocusManager.navigateToNextArea(
                                    currentFocusArea,
                                    focusManager
                                )
                                true
                            }
                            keyEvent.key == Key.F6 && keyEvent.isShiftPressed -> {
                                currentFocusArea = compactUIFocusManager.navigateToPreviousArea(
                                    currentFocusArea,
                                    focusManager
                                )
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * CompositionLocals for keyboard navigation
 */
val LocalKeyboardNavigationManager = compositionLocalOf<CompactUIFocusManager> { 
    error("KeyboardNavigationManager not provided") 
}

val LocalCurrentFocusArea = compositionLocalOf<CompactUIFocusManager.FocusArea> { 
    CompactUIFocusManager.FocusArea.TRANSPORT_CONTROLS 
}

/**
 * Hook to get current keyboard navigation manager
 */
@Composable
fun rememberKeyboardNavigationManager(): CompactUIFocusManager {
    return LocalKeyboardNavigationManager.current
}

/**
 * Hook to get current focus area
 */
@Composable
fun currentFocusArea(): CompactUIFocusManager.FocusArea {
    return LocalCurrentFocusArea.current
}

/**
 * Modifier that adds focus indicators for keyboard navigation
 */
@Composable
fun Modifier.keyboardFocusIndicator(
    isFocused: Boolean = false,
    focusColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
): Modifier = this.then(
    if (isFocused) {
        Modifier.padding(2.dp)
            .border(
                width = 2.dp,
                color = focusColor,
                shape = RoundedCornerShape(4.dp)
            )
    } else {
        Modifier
    }
)

/**
 * Accessibility announcements for keyboard navigation
 */
@Composable
fun KeyboardNavigationAnnouncements(
    currentArea: CompactUIFocusManager.FocusArea
) {
    val announcement = when (currentArea) {
        CompactUIFocusManager.FocusArea.TRANSPORT_CONTROLS -> 
            "Transport controls focused. Use Space to play, Ctrl+S to stop, Ctrl+R to record."
        CompactUIFocusManager.FocusArea.DRUM_PAD_GRID -> 
            "Drum pad grid focused. Use arrow keys to navigate, Enter to trigger pads."
        CompactUIFocusManager.FocusArea.SEQUENCER_CONTROLS -> 
            "Sequencer focused. Use arrow keys to navigate steps, Enter to toggle."
        CompactUIFocusManager.FocusArea.QUICK_ACCESS_PANELS -> 
            "Quick access panels focused. Use Tab to navigate between panels."
        CompactUIFocusManager.FocusArea.BOTTOM_SHEET -> 
            "Bottom sheet focused. Use arrow keys to navigate controls."
    }
    
    // In a real implementation, this would use accessibility services to announce
    // For now, we'll use semantics
    Box(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        }
    )
}