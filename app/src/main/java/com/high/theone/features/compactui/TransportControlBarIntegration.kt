package com.high.theone.features.compactui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Integration example showing how TransportControlBar connects with CompactMainViewModel
 * This demonstrates the proper wiring between the UI component and state management
 */
@Composable
fun TransportControlBarIntegration(
    viewModel: CompactMainViewModel,
    modifier: Modifier = Modifier
) {
    // Collect transport state from ViewModel
    val transportState by viewModel.transportState.collectAsStateWithLifecycle()
    
    // Render TransportControlBar with ViewModel actions
    TransportControlBar(
        transportState = transportState,
        onPlayPause = viewModel::onPlayPause,
        onStop = viewModel::onStop,
        onRecord = viewModel::onRecord,
        onSettingsClick = { /* Navigate to settings */ },
        modifier = modifier
    )
}

/**
 * Example usage in a larger screen composition
 */
@Composable
fun ExampleScreenWithTransportBar(
    viewModel: CompactMainViewModel,
    modifier: Modifier = Modifier
) {
    // This shows how the transport bar would be used in the actual compact main screen
    
    // In the real implementation, this would be part of CompactMainScreen
    // and positioned at the top of the screen layout
    
    TransportControlBarIntegration(
        viewModel = viewModel,
        modifier = modifier
    )
    
    // Other UI components would follow below:
    // - ResponsiveMainLayout
    // - DrumPadGrid
    // - InlineSequencer
    // - QuickAccessPanels
    // etc.
}