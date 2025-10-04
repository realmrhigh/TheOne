package com.high.theone.features.sampling

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.high.theone.model.PadState
import com.high.theone.model.RecordingState

/**
 * Accessibility-enhanced PadGrid with keyboard navigation and screen reader support.
 * Provides comprehensive accessibility features for users with disabilities.
 * 
 * Requirements: 6.4 (screen reader support), 6.5 (keyboard navigation)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccessiblePadGrid(
    pads: List<PadState>,
    onPadTap: (Int, Float) -> Unit,
    onPadLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highContrastMode: Boolean = false,
    largeTextMode: Boolean = false
) {
    var focusedPadIndex by remember { mutableStateOf(0) }
    val hapticFeedback = LocalHapticFeedback.current
    
    // Ensure we have exactly 16 pads
    val padList = pads.take(16).let { currentPads ->
        if (currentPads.size < 16) {
            currentPads + List(16 - currentPads.size) { index ->
                PadState(index = currentPads.size + index)
            }
        } else {
            currentPads
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Drum pad grid with 16 pads arranged in 4 rows and 4 columns"
                role = Role.Button
            }
            .onKeyEvent { keyEvent ->
                handlePadGridKeyEvent(
                    keyEvent = keyEvent,
                    focusedPadIndex = focusedPadIndex,
                    onFocusChange = { newIndex -> focusedPadIndex = newIndex },
                    onPadTrigger = { index -> 
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPadTap(index, 0.8f) 
                    },
                    onPadConfigure = { index -> onPadLongPress(index) }
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Create 4 rows of 4 pads each
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 4) {
                    val padIndex = row * 4 + col
                    val padState = padList[padIndex]
                    val isFocused = focusedPadIndex == padIndex
                    
                    AccessiblePad(
                        padState = padState,
                        onTap = { velocity -> onPadTap(padIndex, velocity) },
                        onLongPress = { onPadLongPress(padIndex) },
                        onFocusChange = { focused ->
                            if (focused) {
                                focusedPadIndex = padIndex
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = enabled,
                        isFocused = isFocused,
                        highContrastMode = highContrastMode,
                        largeTextMode = largeTextMode,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Individual accessible pad with comprehensive screen reader support and keyboard navigation.
 */
@Composable
private fun AccessiblePad(
    padState: PadState,
    onTap: (Float) -> Unit,
    onLongPress: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    enabled: Boolean,
    isFocused: Boolean,
    highContrastMode: Boolean,
    largeTextMode: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    
    // Accessibility content description
    val contentDescription = buildString {
        append("Pad ${padState.index + 1}")
        when {
            padState.isLoading -> append(", loading sample")
            padState.hasAssignedSample -> {
                append(", contains sample: ${padState.sampleName ?: "unnamed"}")
                if (padState.isPlaying) append(", currently playing")
                append(", volume ${(padState.volume * 100).toInt()} percent")
                if (padState.pan != 0f) {
                    val panDescription = when {
                        padState.pan > 0.1f -> "panned right"
                        padState.pan < -0.1f -> "panned left"
                        else -> "centered"
                    }
                    append(", $panDescription")
                }
            }
            else -> append(", empty, tap to assign sample")
        }
    }
    
    // Color scheme based on accessibility preferences
    val (backgroundColor, borderColor, contentColor) = getAccessiblePadColors(
        padState = padState,
        enabled = enabled,
        isFocused = isFocused,
        highContrastMode = highContrastMode
    )
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 4.dp else if (padState.isPlaying) 3.dp else 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                
                // Custom actions for screen readers
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = "Trigger pad",
                        action = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTap(0.8f)
                            true
                        }
                    ),
                    CustomAccessibilityAction(
                        label = "Configure pad",
                        action = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                            true
                        }
                    )
                )
                
                // State information for screen readers
                stateDescription = when {
                    padState.isLoading -> "Loading"
                    padState.isPlaying -> "Playing"
                    padState.hasAssignedSample -> "Has sample"
                    else -> "Empty"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            // Pad number with accessibility-friendly sizing
            Text(
                text = "${padState.index + 1}",
                fontSize = if (largeTextMode) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            
            // Sample name or status
            Text(
                text = when {
                    padState.isLoading -> "Loading..."
                    padState.hasAssignedSample -> padState.sampleName ?: "Sample"
                    else -> "Empty"
                },
                fontSize = if (largeTextMode) 14.sp else 10.sp,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
        
        // Focus indicator for keyboard navigation
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
    }
}

/**
 * Accessible recording controls with enhanced screen reader support.
 */
@Composable
fun AccessibleRecordingControls(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
    highContrastMode: Boolean = false,
    largeTextMode: Boolean = false
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Recording controls section"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording status with accessibility
            AccessibleRecordingStatus(
                recordingState = recordingState,
                highContrastMode = highContrastMode,
                largeTextMode = largeTextMode
            )
            
            // Main record button with accessibility
            AccessibleRecordButton(
                recordingState = recordingState,
                onStartRecording = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRecording()
                },
                onStopRecording = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStopRecording()
                },
                highContrastMode = highContrastMode
            )
            
            // Level meter with accessibility
            if (recordingState.isRecording || recordingState.peakLevel > 0.0f) {
                AccessibleLevelMeter(
                    peakLevel = recordingState.peakLevel,
                    averageLevel = recordingState.averageLevel,
                    highContrastMode = highContrastMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Handle keyboard events for pad grid navigation and interaction.
 */
private fun handlePadGridKeyEvent(
    keyEvent: KeyEvent,
    focusedPadIndex: Int,
    onFocusChange: (Int) -> Unit,
    onPadTrigger: (Int) -> Unit,
    onPadConfigure: (Int) -> Unit
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    
    return when (keyEvent.key) {
        Key.DirectionUp -> {
            val newIndex = (focusedPadIndex - 4).coerceAtLeast(0)
            onFocusChange(newIndex)
            true
        }
        Key.DirectionDown -> {
            val newIndex = (focusedPadIndex + 4).coerceAtMost(15)
            onFocusChange(newIndex)
            true
        }
        Key.DirectionLeft -> {
            val newIndex = if (focusedPadIndex % 4 > 0) focusedPadIndex - 1 else focusedPadIndex
            onFocusChange(newIndex)
            true
        }
        Key.DirectionRight -> {
            val newIndex = if (focusedPadIndex % 4 < 3) focusedPadIndex + 1 else focusedPadIndex
            onFocusChange(newIndex)
            true
        }
        Key.Enter, Key.Spacebar -> {
            onPadTrigger(focusedPadIndex)
            true
        }
        Key.Tab -> {
            if (keyEvent.isShiftPressed) {
                val newIndex = (focusedPadIndex - 1).coerceAtLeast(0)
                onFocusChange(newIndex)
            } else {
                val newIndex = (focusedPadIndex + 1).coerceAtMost(15)
                onFocusChange(newIndex)
            }
            true
        }
        Key.F -> { // F for configure
            onPadConfigure(focusedPadIndex)
            true
        }
        else -> false
    }
}

/**
 * Get accessible color scheme based on user preferences.
 */
@Composable
private fun getAccessiblePadColors(
    padState: PadState,
    enabled: Boolean,
    isFocused: Boolean,
    highContrastMode: Boolean
): Triple<Color, Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    
    return if (highContrastMode) {
        // High contrast color scheme
        when {
            !enabled -> Triple(
                Color.Black,
                Color.Gray,
                Color.Gray
            )
            isFocused -> Triple(
                Color.Yellow,
                Color.Black,
                Color.Black
            )
            padState.isPlaying -> Triple(
                Color.Red,
                Color.White,
                Color.White
            )
            padState.hasAssignedSample -> Triple(
                Color.Blue,
                Color.White,
                Color.White
            )
            else -> Triple(
                Color.White,
                Color.Black,
                Color.Black
            )
        }
    } else {
        // Standard color scheme with enhanced contrast
        when {
            !enabled -> Triple(
                colorScheme.surfaceVariant.copy(alpha = 0.5f),
                colorScheme.outline.copy(alpha = 0.5f),
                colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            isFocused -> Triple(
                colorScheme.primaryContainer,
                colorScheme.primary,
                colorScheme.onPrimaryContainer
            )
            padState.isPlaying -> Triple(
                colorScheme.primary,
                colorScheme.primary,
                colorScheme.onPrimary
            )
            padState.hasAssignedSample -> Triple(
                colorScheme.secondaryContainer,
                colorScheme.secondary,
                colorScheme.onSecondaryContainer
            )
            else -> Triple(
                colorScheme.surface,
                colorScheme.outline,
                colorScheme.onSurface
            )
        }
    }
}

/**
 * Accessible recording status display.
 */
@Composable
private fun AccessibleRecordingStatus(
    recordingState: RecordingState,
    highContrastMode: Boolean,
    largeTextMode: Boolean
) {
    val statusText = when {
        recordingState.isProcessing -> "Processing recording"
        recordingState.isRecording -> "Recording in progress"
        recordingState.isPaused -> "Recording paused"
        recordingState.durationMs > 0 -> "Recording ready"
        recordingState.error != null -> "Recording error: ${recordingState.error}"
        !recordingState.isInitialized -> "Initializing recording system"
        else -> "Ready to record"
    }
    
    Text(
        text = statusText,
        style = if (largeTextMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = if (highContrastMode && recordingState.isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics {
            contentDescription = statusText
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/**
 * Accessible record button with enhanced feedback.
 */
@Composable
private fun AccessibleRecordButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    highContrastMode: Boolean
) {
    val buttonText = if (recordingState.isRecording) "Stop Recording" else "Start Recording"
    val buttonColor = if (highContrastMode) {
        if (recordingState.isRecording) Color.Red else Color.Green
    } else {
        if (recordingState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    }
    
    Button(
        onClick = {
            if (recordingState.isRecording) {
                onStopRecording()
            } else if (recordingState.canStartRecording) {
                onStartRecording()
            }
        },
        enabled = recordingState.canStartRecording || recordingState.isRecording,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        modifier = Modifier
            .size(80.dp)
            .semantics {
                contentDescription = buttonText
                role = Role.Button
                stateDescription = if (recordingState.isRecording) "Recording" else "Ready"
            }
    ) {
        Text(
            text = if (recordingState.isRecording) "STOP" else "REC",
            fontWeight = FontWeight.Bold,
            color = if (highContrastMode) Color.White else MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Accessible level meter with text descriptions.
 */
@Composable
private fun AccessibleLevelMeter(
    peakLevel: Float,
    averageLevel: Float,
    highContrastMode: Boolean,
    modifier: Modifier = Modifier
) {
    val peakPercentage = (peakLevel * 100).toInt()
    val avgPercentage = (averageLevel * 100).toInt()
    val levelDescription = "Input level: Peak $peakPercentage percent, Average $avgPercentage percent"
    
    Column(
        modifier = modifier.semantics {
            contentDescription = levelDescription
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Text(
            text = "Input Level",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Visual level meter (same as before but with accessibility)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (highContrastMode) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                )
                .semantics {
                    contentDescription = levelDescription
                }
        )
        
        // Text-based level indicators for screen readers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peak: $peakPercentage%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Avg: $avgPercentage%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}