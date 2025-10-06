package com.high.theone.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.high.theone.model.*

/**
 * Utility functions for responsive layout calculations
 */
object ResponsiveLayoutUtils {
    
    /**
     * Calculate screen configuration from current compose context
     */
    @Composable
    fun calculateScreenConfiguration(): ScreenConfiguration {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        
        return remember(configuration.screenWidthDp, configuration.screenHeightDp, configuration.orientation) {
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            val orientation = if (configuration.screenWidthDp > configuration.screenHeightDp) {
                Orientation.LANDSCAPE
            } else {
                Orientation.PORTRAIT
            }
            
            // Determine if device is tablet based on screen size and density
            val isTablet = calculateIsTablet(screenWidth, screenHeight, configuration.densityDpi)
            
            ScreenConfiguration(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                orientation = orientation,
                densityDpi = configuration.densityDpi,
                isTablet = isTablet
            )
        }
    }
    
    /**
     * Calculate if device should be considered a tablet
     */
    private fun calculateIsTablet(width: Dp, height: Dp, densityDpi: Int): Boolean {
        val minDimension = minOf(width.value, height.value)
        val maxDimension = maxOf(width.value, height.value)
        
        // Consider tablet if:
        // 1. Minimum dimension is >= 600dp (Android tablet threshold)
        // 2. Screen diagonal is >= 7 inches
        return minDimension >= 600f || calculateScreenDiagonal(width, height, densityDpi) >= 7f
    }
    
    /**
     * Calculate screen diagonal in inches
     */
    private fun calculateScreenDiagonal(width: Dp, height: Dp, densityDpi: Int): Float {
        val widthInches = width.value / (densityDpi / 160f)
        val heightInches = height.value / (densityDpi / 160f)
        return kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)
    }
    
    /**
     * Calculate optimal grid dimensions for drum pads based on layout mode
     */
    fun calculateDrumPadGridDimensions(
        layoutMode: LayoutMode,
        availableWidth: Dp,
        availableHeight: Dp
    ): GridDimensions {
        return when (layoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> GridDimensions(
                columns = 4,
                rows = 4,
                padSize = minOf(availableWidth / 4.5f, 60.dp),
                spacing = 4.dp
            )
            LayoutMode.STANDARD_PORTRAIT -> GridDimensions(
                columns = 4,
                rows = 4,
                padSize = minOf(availableWidth / 4.2f, 80.dp),
                spacing = 8.dp
            )
            LayoutMode.LANDSCAPE -> GridDimensions(
                columns = 4,
                rows = 4,
                padSize = minOf(availableHeight / 5f, 70.dp),
                spacing = 6.dp
            )
            LayoutMode.TABLET -> GridDimensions(
                columns = 4,
                rows = 4,
                padSize = minOf(availableWidth / 6f, 100.dp),
                spacing = 12.dp
            )
        }
    }
    
    /**
     * Calculate sequencer step dimensions
     */
    fun calculateSequencerDimensions(
        layoutMode: LayoutMode,
        availableWidth: Dp
    ): SequencerDimensions {
        return when (layoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> SequencerDimensions(
                visibleSteps = 16,
                stepWidth = (availableWidth - 32.dp) / 16,
                stepHeight = 32.dp,
                trackHeight = 40.dp
            )
            LayoutMode.STANDARD_PORTRAIT -> SequencerDimensions(
                visibleSteps = 16,
                stepWidth = (availableWidth - 32.dp) / 16,
                stepHeight = 36.dp,
                trackHeight = 48.dp
            )
            LayoutMode.LANDSCAPE -> SequencerDimensions(
                visibleSteps = 32,
                stepWidth = (availableWidth * 0.6f - 32.dp) / 32,
                stepHeight = 32.dp,
                trackHeight = 44.dp
            )
            LayoutMode.TABLET -> SequencerDimensions(
                visibleSteps = 32,
                stepWidth = (availableWidth * 0.5f - 32.dp) / 32,
                stepHeight = 40.dp,
                trackHeight = 56.dp
            )
        }
    }
    
    /**
     * Calculate transport control bar dimensions
     */
    fun calculateTransportBarDimensions(layoutMode: LayoutMode): TransportBarDimensions {
        return when (layoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> TransportBarDimensions(
                height = 48.dp,
                buttonSize = 36.dp,
                spacing = 8.dp,
                showExtendedControls = false
            )
            LayoutMode.STANDARD_PORTRAIT -> TransportBarDimensions(
                height = 56.dp,
                buttonSize = 40.dp,
                spacing = 12.dp,
                showExtendedControls = true
            )
            LayoutMode.LANDSCAPE -> TransportBarDimensions(
                height = 52.dp,
                buttonSize = 38.dp,
                spacing = 16.dp,
                showExtendedControls = true
            )
            LayoutMode.TABLET -> TransportBarDimensions(
                height = 64.dp,
                buttonSize = 48.dp,
                spacing = 20.dp,
                showExtendedControls = true
            )
        }
    }
    
    /**
     * Calculate panel dimensions based on layout mode and panel type
     */
    fun calculatePanelDimensions(
        layoutMode: LayoutMode,
        panelType: PanelType,
        screenWidth: Dp,
        screenHeight: Dp
    ): PanelDimensions {
        return when (layoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> PanelDimensions(
                width = screenWidth,
                maxHeight = screenHeight * 0.6f,
                peekHeight = 56.dp,
                isBottomSheet = true
            )
            LayoutMode.STANDARD_PORTRAIT -> PanelDimensions(
                width = screenWidth,
                maxHeight = screenHeight * 0.7f,
                peekHeight = 64.dp,
                isBottomSheet = true
            )
            LayoutMode.LANDSCAPE -> PanelDimensions(
                width = screenWidth * 0.4f,
                maxHeight = screenHeight,
                peekHeight = 0.dp,
                isBottomSheet = false
            )
            LayoutMode.TABLET -> PanelDimensions(
                width = 400.dp,
                maxHeight = screenHeight,
                peekHeight = 0.dp,
                isBottomSheet = false
            )
        }
    }
}

/**
 * Grid dimensions for drum pad layout
 */
data class GridDimensions(
    val columns: Int,
    val rows: Int,
    val padSize: Dp,
    val spacing: Dp
) {
    val totalWidth: Dp = padSize * columns + spacing * (columns - 1)
    val totalHeight: Dp = padSize * rows + spacing * (rows - 1)
}

/**
 * Sequencer layout dimensions
 */
data class SequencerDimensions(
    val visibleSteps: Int,
    val stepWidth: Dp,
    val stepHeight: Dp,
    val trackHeight: Dp
) {
    val totalWidth: Dp = stepWidth * visibleSteps
}

/**
 * Transport bar layout dimensions
 */
data class TransportBarDimensions(
    val height: Dp,
    val buttonSize: Dp,
    val spacing: Dp,
    val showExtendedControls: Boolean
)

/**
 * Panel layout dimensions
 */
data class PanelDimensions(
    val width: Dp,
    val maxHeight: Dp,
    val peekHeight: Dp,
    val isBottomSheet: Boolean
)