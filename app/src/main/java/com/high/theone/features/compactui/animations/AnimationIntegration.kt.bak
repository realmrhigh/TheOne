package com.high.theone.features.compactui.animations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.high.theone.model.*

/**
 * Integration layer that coordinates animations across the compact UI
 * Provides centralized animation state management and performance optimization
 */
class AnimationCoordinator {
    
    private var _isAnimationEnabled by mutableStateOf(true)
    val isAnimationEnabled: Boolean get() = _isAnimationEnabled
    
    private var _performanceMode by mutableStateOf(PerformanceMode.NORMAL)
    val performanceMode: PerformanceMode get() = _performanceMode
    
    /**
     * Enable or disable animations based on performance requirements
     */
    fun setAnimationEnabled(enabled: Boolean) {
        _isAnimationEnabled = enabled
    }
    
    /**
     * Set performance mode to adjust animation complexity
     */
    fun setPerformanceMode(mode: PerformanceMode) {
        _performanceMode = mode
    }
    
    /**
     * Get animation duration based on performance mode
     */
    fun getAnimationDuration(baseMillis: Int): Int {
        return when (_performanceMode) {
            PerformanceMode.HIGH_PERFORMANCE -> (baseMillis * 0.5f).toInt()
            PerformanceMode.NORMAL -> baseMillis
            PerformanceMode.BATTERY_SAVER -> (baseMillis * 1.5f).toInt()
        }
    }
    
    /**
     * Check if complex animations should be enabled
     */
    fun shouldUseComplexAnimations(): Boolean {
        return _isAnimationEnabled && _performanceMode != PerformanceMode.BATTERY_SAVER
    }
}

enum class PerformanceMode {
    HIGH_PERFORMANCE,
    NORMAL,
    BATTERY_SAVER
}

/**
 * Composable that provides animation coordination context
 */
@Composable
fun AnimationProvider(
    coordinator: AnimationCoordinator = remember { AnimationCoordinator() },
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAnimationCoordinator provides coordinator,
        content = content
    )
}

val LocalAnimationCoordinator = compositionLocalOf<AnimationCoordinator> {
    error("AnimationCoordinator not provided")
}

/**
 * Enhanced transport controls with coordinated animations
 */
@Composable
fun AnimatedTransportSection(
    transportState: TransportState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRecord: () -> Unit,
    onBpmChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalAnimationCoordinator.current
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Transport buttons with staggered animation
        StaggeredAnimation(
            visible = true,
            itemCount = 3,
            staggerDelay = if (coordinator.shouldUseComplexAnimations()) 50 else 0
        ) { index ->
            when (index) {
                0 -> MicroInteractions.AnimatedButton(
                    onClick = onPlayPause,
                    hapticEnabled = coordinator.isAnimationEnabled
                ) {
                    Text(if (transportState.isPlaying) "Pause" else "Play")
                }
                1 -> MicroInteractions.AnimatedButton(
                    onClick = onStop,
                    hapticEnabled = coordinator.isAnimationEnabled
                ) {
                    Text("Stop")
                }
                2 -> MicroInteractions.AnimatedButton(
                    onClick = onRecord,
                    hapticEnabled = coordinator.isAnimationEnabled
                ) {
                    Text("Record")
                }
            }
        }
        
        // BPM with loading indicator
        Column {
            Text("BPM: ${transportState.bpm}")
            LoadingStates.SpinningLoadingIndicator(
                size = 16.dp
            )
        }
        
        // Audio levels with smooth animation
        VisualFeedbackSystem.AudioLevelMeter(
            level = transportState.audioLevels.masterLevel,
            modifier = Modifier.size(width = 40.dp, height = 8.dp)
        )
    }
}

/**
 * Enhanced pad grid with coordinated animations
 */
@Composable
fun AnimatedPadGrid(
    pads: List<PadState>,
    onPadTap: (Int, Float) -> Unit,
    onPadLongPress: (Int) -> Unit,
    midiHighlightedPads: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    val coordinator = LocalAnimationCoordinator.current
    
    // Staggered appearance animation for pads
    StaggeredAnimation(
        visible = true,
        itemCount = 16,
        staggerDelay = if (coordinator.shouldUseComplexAnimations()) 25 else 0,
        modifier = modifier
    ) { index ->
        if (index < pads.size) {
            val pad = pads[index]
            val isMidiHighlighted = midiHighlightedPads.contains(index)
            
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padPressAnimation(
                        isPressed = pad.isPlaying,
                        velocity = pad.lastTriggerVelocity
                    )
            ) {
                // Pad content with visual feedback
                Card(
                    onClick = { onPadTap(index, 1f) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // MIDI activity indicator
                        if (isMidiHighlighted) {
                            VisualFeedbackSystem.MidiActivityIndicator(
                                isActive = true,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                            )
                        }
                        
                        // Loading indicator
                        if (pad.isLoading) {
                            LoadingStates.SpinningLoadingIndicator(
                                size = 24.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced sequencer with coordinated step animations
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedSequencerSteps(
    pattern: Pattern,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalAnimationCoordinator.current
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(16) { stepIndex ->
            val hasStep = pattern.steps.values.any { trackSteps ->
                trackSteps.any { it.position == stepIndex && it.isActive }
            }
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .then(
                        if (coordinator.shouldUseComplexAnimations()) {
                            Modifier.animateItemPlacement()
                        } else {
                            Modifier
                        }
                    )
            ) {
                VisualFeedbackSystem.SequencerStepHighlight(
                    isActive = hasStep,
                    isCurrentStep = isPlaying && stepIndex == currentStep,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Enhanced panel system with coordinated transitions
 */
@Composable
fun AnimatedPanelSystem(
    panelStates: Map<PanelType, PanelState>,
    onPanelToggle: (PanelType) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalAnimationCoordinator.current
    
    Column(modifier = modifier) {
        // Panel tabs with animation
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelType.values().forEach { panelType ->
                val isActive = panelStates[panelType]?.isVisible == true
                
                MicroInteractions.AnimatedChip(
                    selected = isActive,
                    onClick = { onPanelToggle(panelType) },
                    label = { Text(panelType.name) },
                    enabled = coordinator.isAnimationEnabled
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Panel content with transitions
        panelStates.forEach { (panelType, panelState) ->
            PanelTransition(
                visible = panelState.isVisible,
                direction = PanelDirection.Bottom
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("${panelType.name} Panel Content")
                        
                        // Loading state for panel content
                        LoadingStates.AudioProcessingIndicator(
                            isProcessing = true,
                            operation = "Loading ${panelType.name}",
                            modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Performance monitoring integration
 */
@Composable
fun AnimationPerformanceMonitor(
    coordinator: AnimationCoordinator,
    performanceMetrics: PerformanceMetrics,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(performanceMetrics) {
        // Adjust animation performance based on metrics
        when {
            performanceMetrics.cpuUsage > 0.8f -> {
                coordinator.setPerformanceMode(PerformanceMode.BATTERY_SAVER)
            }
            performanceMetrics.frameRate < 45f -> {
                coordinator.setPerformanceMode(PerformanceMode.HIGH_PERFORMANCE)
            }
            else -> {
                coordinator.setPerformanceMode(PerformanceMode.NORMAL)
            }
        }
    }
    
    // Visual performance indicator
    Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Perf:",
            style = MaterialTheme.typography.labelSmall
        )
        
        VisualFeedbackSystem.AudioLevelMeter(
            level = performanceMetrics.cpuUsage,
            color = when {
                performanceMetrics.cpuUsage > 0.8f -> MaterialTheme.colorScheme.error
                performanceMetrics.cpuUsage > 0.6f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(width = 30.dp, height = 4.dp)
        )
        
        Text(
            text = "${performanceMetrics.frameRate.toInt()}fps",
            style = MaterialTheme.typography.labelSmall
        )
    }
}