package com.high.theone.features.compactui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.high.theone.model.*
import kotlinx.coroutines.launch
import com.high.theone.features.compactui.animations.PanelTransition
import com.high.theone.features.compactui.animations.PanelDirection
import com.high.theone.features.compactui.animations.MicroInteractions
import com.high.theone.features.compactui.animations.LoadingStates
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Adaptive bottom sheet component that provides access to detailed controls.
 * Implements swipe gestures for show/hide functionality with multiple snap points.
 * 
 * Requirements addressed:
 * - 5.2: Swipe gestures for show/hide functionality
 * - 5.4: Multiple snap points (peek, half, full) and context-aware content switching
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveBottomSheet(
    state: BottomSheetState,
    onStateChange: (BottomSheetState) -> Unit,
    content: @Composable (PanelType?) -> Unit,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 64.dp,
    handleHeight: Dp = 4.dp,
    cornerRadius: Dp = 16.dp
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val peekHeightPx = with(density) { peekHeight.toPx() }
    val halfHeightPx = screenHeight * 0.5f
    val fullHeightPx = screenHeight * 0.9f
    
    // Calculate snap positions
    val snapPositions = remember(screenHeight) {
        mapOf(
            SnapPosition.HIDDEN to 0f,
            SnapPosition.PEEK to peekHeightPx,
            SnapPosition.HALF to halfHeightPx,
            SnapPosition.FULL to fullHeightPx
        )
    }
    
    // Animated offset for smooth transitions
    val animatedOffset = remember { Animatable(snapPositions[state.snapPosition] ?: 0f) }
    
    // Update animated offset when state changes
    LaunchedEffect(state.snapPosition) {
        val targetOffset = snapPositions[state.snapPosition] ?: 0f
        animatedOffset.animateTo(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    // Calculate current height based on animated offset
    val currentHeight = with(density) { animatedOffset.value.toDp() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        // Bottom sheet container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val currentOffset = animatedOffset.value
                                val newSnapPosition = findNearestSnapPosition(
                                    currentOffset = currentOffset,
                                    snapPositions = snapPositions
                                )
                                
                                onStateChange(
                                    state.copy(
                                        snapPosition = newSnapPosition,
                                        isVisible = newSnapPosition != SnapPosition.HIDDEN
                                    )
                                )
                            }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            val newOffset = (animatedOffset.value + dragAmount.y).coerceIn(
                                minimumValue = snapPositions[SnapPosition.HIDDEN] ?: 0f,
                                maximumValue = snapPositions[SnapPosition.FULL] ?: fullHeightPx
                            )
                            animatedOffset.snapTo(newOffset)
                        }
                    }
                }
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight * 3)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(handleHeight)
                        .clip(RoundedCornerShape(handleHeight / 2))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
            
            // Content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                content(state.contentType)
            }
        }
    }
}

/**
 * Bottom sheet state management
 */
@Stable
data class BottomSheetState(
    val snapPosition: SnapPosition = SnapPosition.HIDDEN,
    val isVisible: Boolean = false,
    val contentType: PanelType? = null,
    val isAnimating: Boolean = false
) {
    val isExpanded: Boolean
        get() = snapPosition == SnapPosition.FULL
    
    val isPeeking: Boolean
        get() = snapPosition == SnapPosition.PEEK
    
    val isHalfOpen: Boolean
        get() = snapPosition == SnapPosition.HALF
}

/**
 * Find the nearest snap position based on current offset
 */
private fun findNearestSnapPosition(
    currentOffset: Float,
    snapPositions: Map<SnapPosition, Float>
): SnapPosition {
    return snapPositions.minByOrNull { (_, position) ->
        abs(currentOffset - position)
    }?.key ?: SnapPosition.HIDDEN
}

/**
 * Bottom sheet controller for programmatic control
 */
class BottomSheetController {
    private var _state by mutableStateOf(BottomSheetState())
    val state: BottomSheetState get() = _state
    
    fun show(contentType: PanelType, snapPosition: SnapPosition = SnapPosition.HALF) {
        _state = _state.copy(
            snapPosition = snapPosition,
            isVisible = true,
            contentType = contentType
        )
    }
    
    fun hide() {
        _state = _state.copy(
            snapPosition = SnapPosition.HIDDEN,
            isVisible = false
        )
    }
    
    fun expandToFull() {
        if (_state.isVisible) {
            _state = _state.copy(snapPosition = SnapPosition.FULL)
        }
    }
    
    fun collapseToHalf() {
        if (_state.isVisible) {
            _state = _state.copy(snapPosition = SnapPosition.HALF)
        }
    }
    
    fun collapseToPeek() {
        if (_state.isVisible) {
            _state = _state.copy(snapPosition = SnapPosition.PEEK)
        }
    }
    
    fun switchContent(contentType: PanelType) {
        if (_state.isVisible) {
            _state = _state.copy(contentType = contentType)
        }
    }
}

/**
 * Remember bottom sheet controller
 */
@Composable
fun rememberBottomSheetController(): BottomSheetController {
    return remember { BottomSheetController() }
}

/**
 * Context-aware bottom sheet content switcher.
 * Delegates to the unified QuickAccessPanelContent composables so behaviour
 * is identical whether the panel is shown as a bottom-sheet (portrait) or
 * a side panel (landscape/tablet).
 */
@Composable
fun BottomSheetContentSwitcher(
    contentType: PanelType?,
    modifier: Modifier = Modifier,
    onNavigateToMidiSettings: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (contentType) {
            PanelType.SAMPLE_EDITOR -> SampleEditorBottomSheetContent()
            PanelType.SAMPLING      -> SamplingPanel()
            PanelType.MIDI          -> MidiPanel(onOpenMidiSettings = onNavigateToMidiSettings)
            PanelType.MIXER         -> MixerPanel()
            PanelType.SETTINGS      -> SettingsPanel()
            null                    -> DefaultBottomSheetContent()
        }
    }
}

/**
 * Sample editor content for bottom sheet
 */
@Composable
private fun SampleEditorBottomSheetContent() {
    var selectedSample by remember { mutableStateOf<String?>(null) }
    var trimStart by remember { mutableStateOf(0f) }
    var trimEnd by remember { mutableStateOf(1f) }
    var fadeIn by remember { mutableStateOf(0f) }
    var fadeOut by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sample Editor",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Sample selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sample Selection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Sample browser
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(5) { index ->
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(60.dp)
                                .clickable { selectedSample = "sample_$index" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedSample == "sample_$index") 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sample ${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedSample == "sample_$index") 
                                        MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Waveform display and trimming
        if (selectedSample != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Waveform & Trimming",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Playback controls
                        Row {
                            IconButton(
                                onClick = { isPlaying = !isPlaying }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                            }
                            IconButton(onClick = { /* Stop */ }) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }
                        }
                    }
                    
                    // Simplified waveform display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val centerY = height / 2f
                            
                            // Draw simplified waveform
                            for (x in 0 until width.toInt() step 4) {
                                val amplitude = sin(x * 0.02).toFloat() * 0.3f
                                drawLine(
                                    color = Color.Blue,
                                    start = Offset(x.toFloat(), centerY),
                                    end = Offset(x.toFloat(), centerY + amplitude * centerY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            
                            // Draw trim markers
                            val trimStartX = trimStart * width
                            val trimEndX = trimEnd * width
                            
                            drawLine(
                                color = Color.Green,
                                start = Offset(trimStartX, 0f),
                                end = Offset(trimStartX, height),
                                strokeWidth = 3.dp.toPx()
                            )
                            
                            drawLine(
                                color = Color.Green,
                                start = Offset(trimEndX, 0f),
                                end = Offset(trimEndX, height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                    
                    // Trim controls
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start:", modifier = Modifier.width(50.dp))
                            Slider(
                                value = trimStart,
                                onValueChange = { trimStart = it.coerceAtMost(trimEnd - 0.01f) },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(trimStart * 100).toInt()}%",
                                modifier = Modifier.width(40.dp)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End:", modifier = Modifier.width(50.dp))
                            Slider(
                                value = trimEnd,
                                onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 0.01f) },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(trimEnd * 100).toInt()}%",
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    }
                }
            }
            
            // Fade controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Fade Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade In:", modifier = Modifier.width(70.dp))
                        Slider(
                            value = fadeIn,
                            onValueChange = { fadeIn = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(fadeIn * 1000).toInt()}ms",
                            modifier = Modifier.width(50.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fade Out:", modifier = Modifier.width(70.dp))
                        Slider(
                            value = fadeOut,
                            onValueChange = { fadeOut = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(fadeOut * 1000).toInt()}ms",
                            modifier = Modifier.width(50.dp)
                        )
                    }
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Reset */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = { /* Apply */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

/**
 * Sampling content for bottom sheet - includes advanced sequencer features
 */
@Composable
private fun SamplingBottomSheetContent() {
    var isRecording by remember { mutableStateOf(false) }
    var recordingLevel by remember { mutableStateOf(0.7f) }
    var selectedPattern by remember { mutableStateOf(0) }
    var stepProbability by remember { mutableStateOf(1.0f) }
    var stepVelocity by remember { mutableStateOf(100) }
    var humanization by remember { mutableStateOf(0.0f) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Advanced Sequencer & Sampling",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Recording controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recording Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { isRecording = !isRecording },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRecording) "Stop" else "Record")
                    }
                }
                
                if (isRecording) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = "Recording",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording... 00:03",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Input level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Input Level:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = recordingLevel,
                        onValueChange = { recordingLevel = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(recordingLevel * 100).toInt()}%",
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
        
        // Advanced sequencer features
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Advanced Sequencer Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Pattern selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pattern:", modifier = Modifier.width(60.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(8) { index ->
                            FilterChip(
                                onClick = { selectedPattern = index },
                                label = { Text("${index + 1}") },
                                selected = selectedPattern == index,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
                
                // Step probability
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Probability:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = stepProbability,
                        onValueChange = { stepProbability = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(stepProbability * 100).toInt()}%",
                        modifier = Modifier.width(40.dp)
                    )
                }
                
                // Step velocity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Velocity:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = stepVelocity.toFloat(),
                        onValueChange = { stepVelocity = it.toInt() },
                        valueRange = 1f..127f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stepVelocity.toString(),
                        modifier = Modifier.width(40.dp)
                    )
                }
                
                // Humanization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Humanize:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = humanization,
                        onValueChange = { humanization = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(humanization * 100).toInt()}%",
                        modifier = Modifier.width(40.dp)
                    )
                }
                
                // Pattern operations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* Copy pattern */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                    
                    OutlinedButton(
                        onClick = { /* Clear pattern */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                    
                    OutlinedButton(
                        onClick = { /* Randomize pattern */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Random")
                    }
                }
            }
        }
        
        // Groove templates
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Groove Templates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("Straight", "Swing", "Shuffle", "Triplet", "Custom")) { groove ->
                        FilterChip(
                            onClick = { /* Apply groove */ },
                            label = { Text(groove) },
                            selected = false
                        )
                    }
                }
                
                // Swing amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Swing:", modifier = Modifier.width(60.dp))
                    Slider(
                        value = 0.5f,
                        onValueChange = { /* Update swing */ },
                        modifier = Modifier.weight(1f)
                    )
                    Text("50%", modifier = Modifier.width(40.dp))
                }
            }
        }
    }
}

/**
 * MIDI content for bottom sheet
 */
@Composable
private fun MidiBottomSheetContent() {
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var isLearning by remember { mutableStateOf(false) }
    var learningTarget by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MIDI Mapping",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Device selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIDI Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = { /* Scan for devices */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Devices")
                    }
                }
                
                // Device list
                LazyColumn(
                    modifier = Modifier.heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(3) { index ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDevice = "device_$index" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedDevice == "device_$index") 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "MIDI Controller ${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedDevice == "device_$index") 
                                            MaterialTheme.colorScheme.onPrimary 
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selectedDevice == "device_$index") 
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // MIDI Learn section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isLearning) 
                    MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIDI Learn",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { 
                            isLearning = !isLearning
                            if (!isLearning) learningTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLearning) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isLearning) "Stop Learn" else "Start Learn")
                    }
                }
                
                if (isLearning) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Learning Mode Active",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Move a control on your MIDI device to map it to the selected parameter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (learningTarget != null) {
                                Text(
                                    text = "Target: $learningTarget",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // Quick mapping targets
                Text(
                    text = "Quick Map Targets",
                    style = MaterialTheme.typography.labelLarge
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("Master Volume", "BPM", "Pad 1 Volume", "Filter Cutoff", "Reverb")) { target ->
                        FilterChip(
                            onClick = { 
                                learningTarget = target
                                if (!isLearning) isLearning = true
                            },
                            label = { Text(target) },
                            selected = learningTarget == target,
                            leadingIcon = if (learningTarget == target) {
                                { Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
        
        // Current mappings
        if (selectedDevice != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Current Mappings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(4) { index ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CC${index + 1} → Pad ${index + 1} Volume",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Channel 1",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                IconButton(
                                    onClick = { /* Remove mapping */ },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mixer content for bottom sheet
 */
@Composable
private fun MixerBottomSheetContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Advanced Mixer",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Track Levels")
                Text("Effects Chain")
                Text("Routing Matrix")
            }
        }
    }
}

/**
 * Settings content for bottom sheet
 */
@Composable
private fun SettingsBottomSheetContent() {
    var performanceMode by remember { mutableStateOf(PerformanceMode.BALANCED) }
    var enableAnimations by remember { mutableStateOf(true) }
    var showPerformanceMetrics by remember { mutableStateOf(false) }
    var audioLatency by remember { mutableStateOf(128) }
    var sampleRate by remember { mutableStateOf(44100) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Performance & Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Performance monitoring
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Switch(
                        checked = showPerformanceMetrics,
                        onCheckedChange = { showPerformanceMetrics = it }
                    )
                }
                
                if (showPerformanceMetrics) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Performance metrics display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Frame Rate:", style = MaterialTheme.typography.bodyMedium)
                                Text("60.0 fps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Memory Usage:", style = MaterialTheme.typography.bodyMedium)
                                Text("45.2 MB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Audio Latency:", style = MaterialTheme.typography.bodyMedium)
                                Text("12.3 ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dropped Frames:", style = MaterialTheme.typography.bodyMedium)
                                Text("0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Green)
                            }
                        }
                    }
                }
            }
        }
        
        // Performance mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Performance Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerformanceMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { performanceMode = mode }
                                .background(
                                    if (performanceMode == mode) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = performanceMode == mode,
                                onClick = { performanceMode = mode }
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = when (mode) {
                                        PerformanceMode.HIGH_PERFORMANCE -> "High Performance"
                                        PerformanceMode.BALANCED -> "Balanced"
                                        PerformanceMode.BATTERY_SAVER -> "Battery Saver"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (mode) {
                                        PerformanceMode.HIGH_PERFORMANCE -> "Maximum features, may impact battery"
                                        PerformanceMode.BALANCED -> "Good balance of features and performance"
                                        PerformanceMode.BATTERY_SAVER -> "Reduced features for better battery life"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Audio settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Buffer size
                Column {
                    Text(
                        text = "Buffer Size: ${audioLatency} samples",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = audioLatency.toFloat(),
                        onValueChange = { audioLatency = it.toInt() },
                        valueRange = 64f..512f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Lower = less latency, higher CPU usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Sample rate
                Column {
                    Text(
                        text = "Sample Rate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(44100, 48000, 96000).forEach { rate ->
                            FilterChip(
                                onClick = { sampleRate = rate },
                                label = { Text("${rate}Hz") },
                                selected = sampleRate == rate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        
        // UI preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "UI Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enable Animations",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Smooth transitions and visual effects",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = enableAnimations,
                        onCheckedChange = { enableAnimations = it }
                    )
                }
                
                // Reset button
                OutlinedButton(
                    onClick = { /* Reset to defaults */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Defaults")
                }
            }
        }
    }
}

/**
 * Default content when no specific type is selected
 */
@Composable
private fun DefaultBottomSheetContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Advanced Features",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Select a feature to access advanced controls",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}