package com.high.theone.features.sequencer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.high.theone.model.*

/**
 * Visual feedback components for recording mode
 * Provides clear indication of recording state and activity
 */
@Composable
fun RecordingIndicator(
    isRecording: Boolean,
    recordingMode: RecordingMode,
    modifier: Modifier = Modifier,
    size: RecordingIndicatorSize = RecordingIndicatorSize.MEDIUM
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val recordingColor = when (recordingMode) {
        RecordingMode.REPLACE -> Color.Red
        RecordingMode.OVERDUB -> Color(0xFFFF6B35) // Orange-red
        RecordingMode.PUNCH_IN -> Color(0xFFFF9500) // Orange
    }
    
    Box(
        modifier = modifier.size(size.iconSize),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            // Pulsing background circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(recordingColor.copy(alpha = pulseAlpha * 0.3f))
            )
            
            // Recording icon
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = "Recording",
                tint = recordingColor.copy(alpha = pulseAlpha),
                modifier = Modifier.size(size.iconSize * 0.6f)
            )
        } else {
            // Inactive state
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Not Recording",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(size.iconSize * 0.6f)
            )
        }
    }
}

/**
 * Recording mode badge showing current recording configuration
 */
@Composable
fun RecordingModeBadge(
    recordingState: RecordingState,
    overdubState: OverdubState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = recordingState.isRecording,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (recordingState.mode) {
                    RecordingMode.REPLACE -> Color.Red.copy(alpha = 0.9f)
                    RecordingMode.OVERDUB -> Color(0xFFFF6B35).copy(alpha = 0.9f)
                    RecordingMode.PUNCH_IN -> Color(0xFFFF9500).copy(alpha = 0.9f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recordingState.mode.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                if (recordingState.hasRecordedHits()) {
                    Text(
                        text = "${recordingState.getTotalHitCount()} hits",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                // Show overdub configuration if active
                if (recordingState.mode == RecordingMode.OVERDUB && overdubState.isConfigured) {
                    Text(
                        text = overdubState.layerMode.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Recording activity visualization showing pad hits in real-time
 */
@Composable
fun RecordingActivityDisplay(
    recordingState: RecordingState,
    pads: List<PadState>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = recordingState.isRecording,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Recording Activity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show activity for each pad
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(pads.filter { it.hasAssignedSample }) { pad ->
                        PadRecordingActivity(
                            pad = pad,
                            hitCount = recordingState.getHitCountForPad(pad.index),
                            isSelected = recordingState.selectedPads.isEmpty() || 
                                       recordingState.selectedPads.contains(pad.index)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PadRecordingActivity(
    pad: PadState,
    hitCount: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pad indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${pad.index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    textAlign = TextAlign.Center
                )
            }
            
            // Pad name
            Text(
                text = pad.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }
        
        // Hit count with animation
        AnimatedContent(
            targetState = hitCount,
            transitionSpec = {
                slideInVertically { it } + fadeIn() with
                slideOutVertically { -it } + fadeOut()
            },
            label = "hit_count"
        ) { count ->
            Text(
                text = if (count > 0) "$count hits" else "—",
                style = MaterialTheme.typography.bodySmall,
                color = if (count > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
        }
    }
}

/**
 * Punch-in/punch-out range visualization
 */
@Composable
fun PunchRangeIndicator(
    overdubState: OverdubState,
    patternLength: Int,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    if (!overdubState.hasPunchRange) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Punch Range",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Visual range indicator
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                drawPunchRange(
                    punchIn = overdubState.punchInStep,
                    punchOut = overdubState.punchOutStep,
                    patternLength = patternLength,
                    currentStep = currentStep,
                    canvasSize = size
                )
            }
            
            // Range text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "In: ${overdubState.punchInStep?.let { "${it + 1}" } ?: "Start"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Text(
                    text = "Out: ${overdubState.punchOutStep?.let { "${it + 1}" } ?: "End"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

private fun DrawScope.drawPunchRange(
    punchIn: Int?,
    punchOut: Int?,
    patternLength: Int,
    currentStep: Int,
    canvasSize: androidx.compose.ui.geometry.Size
) {
    val stepWidth = canvasSize.width / patternLength
    val centerY = canvasSize.height / 2
    
    // Draw full pattern background
    drawRect(
        color = Color.Gray.copy(alpha = 0.2f),
        topLeft = Offset(0f, centerY - 4.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(canvasSize.width, 8.dp.toPx())
    )
    
    // Draw punch range
    val startX = (punchIn ?: 0) * stepWidth
    val endX = (punchOut ?: (patternLength - 1)) * stepWidth + stepWidth
    
    drawRect(
        color = Color(0xFFFF6B35).copy(alpha = 0.6f),
        topLeft = Offset(startX, centerY - 4.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(endX - startX, 8.dp.toPx())
    )
    
    // Draw current position
    val currentX = currentStep * stepWidth + stepWidth / 2
    drawCircle(
        color = Color.White,
        radius = 6.dp.toPx(),
        center = Offset(currentX, centerY)
    )
    drawCircle(
        color = Color(0xFF2196F3),
        radius = 4.dp.toPx(),
        center = Offset(currentX, centerY)
    )
}

/**
 * Recording indicator sizes
 */
enum class RecordingIndicatorSize(val iconSize: androidx.compose.ui.unit.Dp) {
    SMALL(16.dp),
    MEDIUM(24.dp),
    LARGE(32.dp)
}