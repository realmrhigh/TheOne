package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.high.theone.model.*
import com.high.theone.ui.layout.ResponsiveLayoutUtils

/**
 * Responsive main layout container that adapts to different screen configurations.
 * Implements layout mode detection and creates adaptive grid system for different screen sizes.
 * 
 * Requirements addressed:
 * - 6.1: Automatic layout adaptation to optimize space usage
 * - 6.2: Horizontal space utilization in landscape mode
 * - 6.3: Efficient vertical space usage in portrait mode with collapsible sections
 * - 6.5: Layout changes preserve user context and current state
 */
@Composable
fun ResponsiveMainLayout(
    layoutState: LayoutState,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLayoutModeChange: (LayoutMode) -> Unit = {}
) {
    // Calculate current screen configuration
    val screenConfiguration = ResponsiveLayoutUtils.calculateScreenConfiguration()
    
    // Detect layout mode changes and notify parent
    LaunchedEffect(screenConfiguration.layoutMode) {
        if (screenConfiguration.layoutMode != layoutState.configuration.layoutMode) {
            onLayoutModeChange(screenConfiguration.layoutMode)
        }
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (screenConfiguration.layoutMode) {
            LayoutMode.COMPACT_PORTRAIT -> CompactPortraitLayout(
                layoutState = layoutState,
                drumPadContent = drumPadContent,
                sequencerContent = sequencerContent,
                utilityContent = utilityContent
            )
            
            LayoutMode.STANDARD_PORTRAIT -> StandardPortraitLayout(
                layoutState = layoutState,
                drumPadContent = drumPadContent,
                sequencerContent = sequencerContent,
                utilityContent = utilityContent
            )
            
            LayoutMode.LANDSCAPE -> LandscapeLayout(
                layoutState = layoutState,
                drumPadContent = drumPadContent,
                sequencerContent = sequencerContent,
                utilityContent = utilityContent
            )
            
            LayoutMode.TABLET -> TabletLayout(
                layoutState = layoutState,
                drumPadContent = drumPadContent,
                sequencerContent = sequencerContent,
                utilityContent = utilityContent
            )
        }
    }
}

/**
 * Compact portrait layout for small screens
 * Uses single column with tabs/accordion for space efficiency
 */
@Composable
private fun CompactPortraitLayout(
    layoutState: LayoutState,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Primary content area - drum pads get priority
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f),
            contentAlignment = Alignment.Center
        ) {
            drumPadContent()
        }
        
        // Secondary content area - sequencer in compact form
        if (!layoutState.collapsedSections.contains(SectionType.SEQUENCER)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
            ) {
                sequencerContent()
            }
        }
        
        // Utility content - collapsible
        if (!layoutState.collapsedSections.contains(SectionType.UTILITY_PANEL)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.1f)
            ) {
                utilityContent()
            }
        }
    }
}

/**
 * Standard portrait layout for regular portrait screens
 * Uses two-section vertical layout with more breathing room
 */
@Composable
private fun StandardPortraitLayout(
    layoutState: LayoutState,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary section - drum pads
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f),
            contentAlignment = Alignment.Center
        ) {
            drumPadContent()
        }
        
        // Secondary section - sequencer and utility in rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!layoutState.collapsedSections.contains(SectionType.SEQUENCER)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f)
                ) {
                    sequencerContent()
                }
            }
            
            if (!layoutState.collapsedSections.contains(SectionType.UTILITY_PANEL)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                ) {
                    utilityContent()
                }
            }
        }
    }
}

/**
 * Landscape layout utilizing horizontal space
 * Three-column layout with side panels
 */
@Composable
private fun LandscapeLayout(
    layoutState: LayoutState,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left panel - drum pads
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            drumPadContent()
        }
        
        // Center panel - sequencer with more horizontal steps
        if (!layoutState.collapsedSections.contains(SectionType.SEQUENCER)) {
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
            ) {
                sequencerContent()
            }
        }
        
        // Right panel - utility content
        if (!layoutState.collapsedSections.contains(SectionType.UTILITY_PANEL)) {
            Box(
                modifier = Modifier
                    .weight(0.1f)
                    .fillMaxHeight()
            ) {
                utilityContent()
            }
        }
    }
}

/**
 * Tablet layout for large screens
 * Multi-panel dashboard layout with persistent panels
 */
@Composable
private fun TabletLayout(
    layoutState: LayoutState,
    drumPadContent: @Composable () -> Unit,
    sequencerContent: @Composable () -> Unit,
    utilityContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left column - drum pads with larger size
        Box(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            drumPadContent()
        }
        
        // Center column - sequencer and utility stacked
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!layoutState.collapsedSections.contains(SectionType.SEQUENCER)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f)
                ) {
                    sequencerContent()
                }
            }
            
            if (!layoutState.collapsedSections.contains(SectionType.UTILITY_PANEL)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                ) {
                    utilityContent()
                }
            }
        }
        
        // Right column - reserved for future panels or extended controls
        Spacer(modifier = Modifier.weight(0.2f))
    }
}

/**
 * Adaptive grid system that calculates optimal layout based on available space
 */
@Composable
fun AdaptiveGrid(
    items: List<@Composable () -> Unit>,
    layoutMode: LayoutMode,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    val gridDimensions = ResponsiveLayoutUtils.calculateDrumPadGridDimensions(
        layoutMode = layoutMode,
        availableWidth = screenWidth,
        availableHeight = screenHeight
    )
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(gridDimensions.spacing)
    ) {
        for (rowIndex in 0 until gridDimensions.rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gridDimensions.spacing)
            ) {
                for (colIndex in 0 until gridDimensions.columns) {
                    val itemIndex = rowIndex * gridDimensions.columns + colIndex
                    if (itemIndex < items.size) {
                        Box(
                            modifier = Modifier.size(gridDimensions.padSize)
                        ) {
                            items[itemIndex]()
                        }
                    } else {
                        Spacer(modifier = Modifier.size(gridDimensions.padSize))
                    }
                }
            }
        }
    }
}