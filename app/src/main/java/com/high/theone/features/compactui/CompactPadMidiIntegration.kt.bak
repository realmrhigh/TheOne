package com.high.theone.features.compactui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.high.theone.features.sampling.MidiPadTriggerEvent
import com.high.theone.features.sampling.MidiPadStopEvent
import com.high.theone.model.PadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MIDI integration manager for CompactDrumPadGrid providing visual feedback
 * for MIDI note events and pad highlighting.
 * 
 * Requirements: 2.3 (MIDI integration), 2.5 (MIDI highlighting), 7.1 (visual feedback)
 */
class CompactPadMidiIntegration {
    
    // State for tracking MIDI-highlighted pads
    private val _highlightedPads = MutableStateFlow<Set<Int>>(emptySet())
    val highlightedPads: StateFlow<Set<Int>> = _highlightedPads.asStateFlow()
    
    // State for tracking sustained notes (for NOTE_ON_OFF mode)
    private val _sustainedNotes = MutableStateFlow<Map<Int, SustainedNoteInfo>>(emptyMap())
    val sustainedNotes: StateFlow<Map<Int, SustainedNoteInfo>> = _sustainedNotes.asStateFlow()
    
    // Configuration for highlight duration
    private var highlightDurationMs: Long = 150L
    private var sustainedHighlightEnabled: Boolean = true
    
    /**
     * Handle MIDI pad trigger event with visual feedback.
     * 
     * Requirements: 2.3 (MIDI input handling), 7.1 (visual feedback)
     */
    suspend fun handleMidiTrigger(
        event: MidiPadTriggerEvent,
        pads: List<PadState>,
        onPadTrigger: (Int, Float) -> Unit
    ) {
        val pad = pads.getOrNull(event.padIndex) ?: return
        
        // Add pad to highlighted set
        _highlightedPads.value = _highlightedPads.value + event.padIndex
        
        // Handle sustained notes for NOTE_ON_OFF mode
        if (pad.playbackMode == com.high.theone.model.PlaybackMode.NOTE_ON_OFF) {
            _sustainedNotes.value = _sustainedNotes.value + (event.padIndex to SustainedNoteInfo(
                padIndex = event.padIndex,
                midiNote = event.midiNote,
                midiChannel = event.midiChannel,
                velocity = event.velocity,
                startTime = event.timestamp
            ))
        }
        
        // Trigger the pad
        onPadTrigger(event.padIndex, event.velocity)
        
        // Remove highlight after duration (for non-sustained notes)
        if (pad.playbackMode != com.high.theone.model.PlaybackMode.NOTE_ON_OFF) {
            delay(highlightDurationMs)
            _highlightedPads.value = _highlightedPads.value - event.padIndex
        }
    }
    
    /**
     * Handle MIDI pad stop event for NOTE_ON_OFF mode.
     * 
     * Requirements: 2.3 (MIDI input handling), 2.5 (sustained note handling)
     */
    suspend fun handleMidiStop(
        event: MidiPadStopEvent,
        onPadStop: ((Int) -> Unit)? = null
    ) {
        // Remove from sustained notes
        val sustainedNote = _sustainedNotes.value[event.padIndex]
        if (sustainedNote != null && sustainedNote.midiNote == event.midiNote) {
            _sustainedNotes.value = _sustainedNotes.value - event.padIndex
            
            // Remove highlight
            _highlightedPads.value = _highlightedPads.value - event.padIndex
            
            // Stop the pad if callback provided
            onPadStop?.invoke(event.padIndex)
        }
    }
    
    /**
     * Manually highlight a pad (for external MIDI events or testing).
     * 
     * Requirements: 7.1 (visual feedback)
     */
    suspend fun highlightPad(padIndex: Int, durationMs: Long = highlightDurationMs) {
        _highlightedPads.value = _highlightedPads.value + padIndex
        delay(durationMs)
        _highlightedPads.value = _highlightedPads.value - padIndex
    }
    
    /**
     * Clear all highlights and sustained notes.
     */
    fun clearAllHighlights() {
        _highlightedPads.value = emptySet()
        _sustainedNotes.value = emptyMap()
    }
    
    /**
     * Configure highlight behavior.
     */
    fun configure(
        highlightDurationMs: Long = 150L,
        sustainedHighlightEnabled: Boolean = true
    ) {
        this.highlightDurationMs = highlightDurationMs
        this.sustainedHighlightEnabled = sustainedHighlightEnabled
    }
    
    /**
     * Get current highlight state for a specific pad.
     */
    fun isPadHighlighted(padIndex: Int): Boolean {
        return _highlightedPads.value.contains(padIndex)
    }
    
    /**
     * Get sustained note info for a specific pad.
     */
    fun getSustainedNote(padIndex: Int): SustainedNoteInfo? {
        return _sustainedNotes.value[padIndex]
    }
}

/**
 * Information about a sustained MIDI note.
 */
data class SustainedNoteInfo(
    val padIndex: Int,
    val midiNote: Int,
    val midiChannel: Int,
    val velocity: Float,
    val startTime: Long
) {
    val durationMs: Long
        get() = System.currentTimeMillis() - startTime
}

/**
 * Composable hook for managing MIDI integration in CompactDrumPadGrid.
 * 
 * Requirements: 2.3 (MIDI integration), 2.5 (MIDI highlighting)
 */
@Composable
fun rememberCompactPadMidiIntegration(
    midiTriggerEvents: Flow<MidiPadTriggerEvent>? = null,
    midiStopEvents: Flow<MidiPadStopEvent>? = null,
    pads: List<PadState>,
    onPadTrigger: (Int, Float) -> Unit,
    onPadStop: ((Int) -> Unit)? = null
): CompactPadMidiIntegration {
    
    val midiIntegration = remember { CompactPadMidiIntegration() }
    val hapticFeedback = LocalHapticFeedback.current
    
    // Handle MIDI trigger events
    LaunchedEffect(midiTriggerEvents, onPadTrigger) {
        midiTriggerEvents?.collect { event ->
            // Provide haptic feedback for MIDI triggers
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            
            midiIntegration.handleMidiTrigger(
                event = event,
                pads = pads,
                onPadTrigger = onPadTrigger
            )
        }
    }
    
    // Handle MIDI stop events
    LaunchedEffect(midiStopEvents, onPadStop) {
        midiStopEvents?.collect { event ->
            midiIntegration.handleMidiStop(
                event = event,
                onPadStop = onPadStop
            )
        }
    }
    
    return midiIntegration
}

/**
 * Enhanced CompactDrumPadGrid with integrated MIDI support.
 * 
 * Requirements: 2.3 (MIDI integration), 2.5 (MIDI highlighting), 7.1 (visual feedback)
 */
@Composable
fun CompactDrumPadGridWithMidi(
    pads: List<PadState>,
    onPadTap: (Int, Float) -> Unit,
    onPadLongPress: (Int) -> Unit,
    screenConfiguration: com.high.theone.model.ScreenConfiguration,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSampleNames: Boolean = true,
    showWaveformPreviews: Boolean = true,
    midiTriggerEvents: Flow<MidiPadTriggerEvent>? = null,
    midiStopEvents: Flow<MidiPadStopEvent>? = null,
    onPadStop: ((Int) -> Unit)? = null,
    onMidiTrigger: ((MidiPadTriggerEvent) -> Unit)? = null,
    onMidiStop: ((MidiPadStopEvent) -> Unit)? = null
) {
    // Set up MIDI integration
    val midiIntegration = rememberCompactPadMidiIntegration(
        midiTriggerEvents = midiTriggerEvents,
        midiStopEvents = midiStopEvents,
        pads = pads,
        onPadTrigger = onPadTap,
        onPadStop = onPadStop
    )
    
    // Collect highlighted pads state
    val highlightedPads by midiIntegration.highlightedPads.collectAsState()
    
    // Forward MIDI events to external handlers
    LaunchedEffect(midiTriggerEvents) {
        midiTriggerEvents?.collect { event ->
            onMidiTrigger?.invoke(event)
        }
    }
    
    LaunchedEffect(midiStopEvents) {
        midiStopEvents?.collect { event ->
            onMidiStop?.invoke(event)
        }
    }
    
    CompactDrumPadGrid(
        pads = pads,
        onPadTap = onPadTap,
        onPadLongPress = onPadLongPress,
        screenConfiguration = screenConfiguration,
        modifier = modifier,
        enabled = enabled,
        showSampleNames = showSampleNames,
        showWaveformPreviews = showWaveformPreviews,
        midiHighlightedPads = highlightedPads
    )
}

/**
 * Context menu configuration for pad long-press actions.
 * 
 * Requirements: 2.5 (long-press context menus)
 */
data class PadContextMenuConfig(
    val showSampleAssignment: Boolean = true,
    val showMidiMapping: Boolean = true,
    val showPlaybackSettings: Boolean = true,
    val showVolumeControl: Boolean = true,
    val showEffects: Boolean = false, // Advanced feature
    val showCopyPaste: Boolean = true
)

/**
 * Pad context menu actions.
 */
sealed class PadContextMenuAction {
    data class AssignSample(val padIndex: Int) : PadContextMenuAction()
    data class ConfigureMidi(val padIndex: Int) : PadContextMenuAction()
    data class AdjustVolume(val padIndex: Int) : PadContextMenuAction()
    data class SetPlaybackMode(val padIndex: Int) : PadContextMenuAction()
    data class CopyPad(val padIndex: Int) : PadContextMenuAction()
    data class PastePad(val padIndex: Int) : PadContextMenuAction()
    data class ClearPad(val padIndex: Int) : PadContextMenuAction()
}

/**
 * Composable for handling pad context menu.
 * This would typically be implemented as a DropdownMenu or BottomSheet.
 * 
 * Requirements: 2.5 (long-press context menus)
 */
@Composable
fun PadContextMenu(
    padIndex: Int,
    padState: PadState,
    config: PadContextMenuConfig = PadContextMenuConfig(),
    onAction: (PadContextMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Implementation would go here - this is a placeholder for the context menu UI
    // In a real implementation, this would show a dropdown menu or bottom sheet
    // with the available actions based on the config and pad state
}