package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.high.theone.model.IntegratedRecordingState
import com.high.theone.model.ScreenConfiguration

/**
 * Integration component for the recording panel in the compact main screen
 * Now uses responsive design that adapts to different screen sizes and orientations
 * 
 * Requirements: 3.3 (responsive design), 2.2 (accessibility)
 */
@Composable
fun CompactRecordingPanelIntegration(
    recordingState: IntegratedRecordingState,
    drumPadState: com.high.theone.model.DrumPadState,
    screenConfiguration: ScreenConfiguration,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAssignToPad: (String) -> Unit,
    onDiscardRecording: () -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use the responsive recording panel that adapts to screen configuration
    ResponsiveRecordingPanel(
        recordingState = recordingState,
        drumPadState = drumPadState,
        screenConfiguration = screenConfiguration,
        isVisible = true, // Panel visibility is controlled by parent
        onStartRecording = onStartRecording,
        onStopRecording = onStopRecording,
        onAssignToPad = onAssignToPad,
        onDiscardRecording = onDiscardRecording,
        onHidePanel = onHidePanel,
        modifier = modifier
    )
}