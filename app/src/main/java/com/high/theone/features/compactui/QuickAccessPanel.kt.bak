package com.high.theone.features.compactui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.high.theone.model.PanelState
import com.high.theone.model.PanelType
import com.high.theone.model.SnapPosition
import kotlinx.coroutines.launch
import com.high.theone.features.compactui.animations.PanelTransition
import com.high.theone.features.compactui.animations.PanelDirection
import com.high.theone.features.compactui.animations.MicroInteractions

/**
 * Base quick access panel component with sliding animations and gesture handling
 * Supports different panel types and smooth transitions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessPanel(
    panelState: PanelState,
    panelType: PanelType,
    onPanelStateChange: (PanelState) -> Unit,
    onPanelTypeChange: (PanelType) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PanelType) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Animation for panel visibility
    val panelOffset by animateFloatAsState(
        targetValue = if (panelState.isVisible) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "panel_offset"
    )
    
    // Animation for panel expansion
    val expansionProgress by animateFloatAsState(
        targetValue = if (panelState.isExpanded) 1f else 0.6f,
        animationSpec = tween(durationMillis = 300),
        label = "expansion_progress"
    )
    
    PanelTransition(
        visible = panelState.isVisible,
        direction = PanelDirection.Right,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .width((300.dp * expansionProgress).coerceAtLeast(200.dp))
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .graphicsLayer {
                    translationX = panelOffset * size.width
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Handle panel show/hide based on drag velocity
                            coroutineScope.launch {
                                val shouldHide = panelOffset > 0.3f
                                onPanelStateChange(
                                    panelState.copy(isVisible = !shouldHide)
                                )
                            }
                        }
                    ) { _, dragAmount ->
                        // Handle drag gestures for panel control
                        val newOffset = (panelOffset + dragAmount.x / size.width)
                            .coerceIn(0f, 1f)
                        
                        if (newOffset != panelOffset) {
                            onPanelStateChange(
                                panelState.copy(
                                    isVisible = newOffset < 0.8f
                                )
                            )
                        }
                    }
                }
                .zIndex(10f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Panel header with type selector
                QuickAccessPanelHeader(
                    currentType = panelType,
                    isExpanded = panelState.isExpanded,
                    onTypeChange = onPanelTypeChange,
                    onExpandToggle = {
                        onPanelStateChange(
                            panelState.copy(isExpanded = !panelState.isExpanded)
                        )
                    },
                    onClose = {
                        onPanelStateChange(
                            panelState.copy(isVisible = false)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Panel content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    content(panelType)
                }
            }
        }
    }
}

/**
 * Header component for the quick access panel with type switching controls
 */
@Composable
private fun QuickAccessPanelHeader(
    currentType: PanelType,
    isExpanded: Boolean,
    onTypeChange: (PanelType) -> Unit,
    onExpandToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Panel type selector
        QuickAccessPanelTypeSelector(
            currentType = currentType,
            onTypeChange = onTypeChange,
            modifier = Modifier.weight(1f)
        )
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Expand/collapse button
            IconButton(
                onClick = onExpandToggle
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            // Close button
            IconButton(
                onClick = onClose
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close panel"
                )
            }
        }
    }
}

/**
 * Panel type selector with smooth transitions between types
 */
@Composable
private fun QuickAccessPanelTypeSelector(
    currentType: PanelType,
    onTypeChange: (PanelType) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableTypes = listOf(
        PanelType.SAMPLING,
        PanelType.MIDI,
        PanelType.MIXER,
        PanelType.SETTINGS
    )
    
    ScrollableTabRow(
        selectedTabIndex = availableTypes.indexOf(currentType),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        edgePadding = 0.dp
    ) {
        availableTypes.forEach { type ->
            Tab(
                selected = currentType == type,
                onClick = { onTypeChange(type) },
                text = {
                    Text(
                        text = getPanelTypeDisplayName(type),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}

/**
 * Get display name for panel type
 */
private fun getPanelTypeDisplayName(type: PanelType): String {
    return when (type) {
        PanelType.SAMPLING -> "Sampling"
        PanelType.MIDI -> "MIDI"
        PanelType.MIXER -> "Mixer"
        PanelType.SETTINGS -> "Settings"
        PanelType.SAMPLE_EDITOR -> "Editor"
    }
}

/**
 * Gesture handler for panel show/hide functionality
 */
@Composable
fun QuickAccessPanelGestureHandler(
    onShowPanel: (PanelType) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Detect swipe from right edge to show panel
                        // This would be implemented based on specific gesture requirements
                    }
                ) { _, dragAmount ->
                    // Handle drag gestures for panel activation
                    val swipeThreshold = 50.dp.toPx()
                    
                    if (dragAmount.x < -swipeThreshold) {
                        // Swipe left from right edge - show panel
                        onShowPanel(PanelType.SAMPLING) // Default panel type
                    }
                }
            }
    ) {
        content()
    }
}