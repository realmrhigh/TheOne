package com.high.theone.features.sampling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings
import kotlinx.serialization.Serializable

/**
 * Advanced sample processing operations including normalize, gain adjustment,
 * reverse, time-stretching, format conversion, and undo/redo functionality.
 * 
 * Requirements: 8.5 (processing operations), 8.6 (undo/redo)
 */
@Composable
fun SampleProcessingPanel(
    sampleMetadata: SampleMetadata?,
    trimSettings: SampleTrimSettings,
    processingHistory: List<ProcessingOperation>,
    currentHistoryIndex: Int,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetProcessing: () -> Unit,
    onFormatConversion: (AudioFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedSection by remember { mutableStateOf<ProcessingSection?>(null) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Processing toolbar with undo/redo
        ProcessingToolbar(
            processingHistory = processingHistory,
            currentHistoryIndex = currentHistoryIndex,
            hasProcessing = trimSettings.hasProcessing,
            onUndo = onUndo,
            onRedo = onRedo,
            onResetProcessing = onResetProcessing,
            showAdvancedOptions = showAdvancedOptions,
            onToggleAdvanced = { showAdvancedOptions = !showAdvancedOptions }
        )
        
        // Basic processing operations
        BasicProcessingOperations(
            trimSettings = trimSettings,
            onTrimChange = onTrimChange,
            onApplyProcessing = onApplyProcessing,
            expandedSection = expandedSection,
            onExpandSection = { section ->
                expandedSection = if (expandedSection == section) null else section
            }
        )
        
        // Advanced processing operations
        AnimatedVisibility(visible = showAdvancedOptions) {
            AdvancedProcessingOperations(
                sampleMetadata = sampleMetadata,
                trimSettings = trimSettings,
                onTrimChange = onTrimChange,
                onApplyProcessing = onApplyProcessing,
                onFormatConversion = onFormatConversion,
                expandedSection = expandedSection,
                onExpandSection = { section ->
                    expandedSection = if (expandedSection == section) null else section
                }
            )
        }
        
        // Processing history
        ProcessingHistory(
            processingHistory = processingHistory,
            currentHistoryIndex = currentHistoryIndex,
            onJumpToHistory = { index ->
                // Jump to specific point in history
                repeat(currentHistoryIndex - index) { onUndo() }
                repeat(index - currentHistoryIndex) { onRedo() }
            }
        )
        
        // Processing summary
        ProcessingSummary(
            trimSettings = trimSettings,
            processingHistory = processingHistory
        )
    }
}

/**
 * Processing toolbar with undo/redo and reset controls
 */
@Composable
private fun ProcessingToolbar(
    processingHistory: List<ProcessingOperation>,
    currentHistoryIndex: Int,
    hasProcessing: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetProcessing: () -> Unit,
    showAdvancedOptions: Boolean,
    onToggleAdvanced: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo/Redo controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onUndo,
                    enabled = currentHistoryIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (currentHistoryIndex > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
                
                IconButton(
                    onClick = onRedo,
                    enabled = currentHistoryIndex < processingHistory.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (currentHistoryIndex < processingHistory.size - 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
                
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    thickness = 1.dp
                )
                
                // Reset button
                TextButton(
                    onClick = onResetProcessing,
                    enabled = hasProcessing
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset All")
                }
            }
            
            // Advanced options toggle
            TextButton(onClick = onToggleAdvanced) {
                Text(if (showAdvancedOptions) "Basic" else "Advanced")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Basic processing operations (normalize, gain, reverse)
 */
@Composable
private fun BasicProcessingOperations(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit,
    expandedSection: ProcessingSection?,
    onExpandSection: (ProcessingSection) -> Unit
) {
    // Normalize section
    ProcessingSection(
        title = "Normalize",
        icon = Icons.Default.Equalizer,
        isExpanded = expandedSection == ProcessingSection.NORMALIZE,
        onToggleExpanded = { onExpandSection(ProcessingSection.NORMALIZE) },
        isApplied = trimSettings.normalize
    ) {
        NormalizeControls(
            trimSettings = trimSettings,
            onTrimChange = onTrimChange,
            onApplyProcessing = onApplyProcessing
        )
    }
    
    // Gain section
    ProcessingSection(
        title = "Gain Adjustment",
        icon = Icons.Default.VolumeUp,
        isExpanded = expandedSection == ProcessingSection.GAIN,
        onToggleExpanded = { onExpandSection(ProcessingSection.GAIN) },
        isApplied = trimSettings.gain != 1.0f
    ) {
        GainControls(
            trimSettings = trimSettings,
            onTrimChange = onTrimChange,
            onApplyProcessing = onApplyProcessing
        )
    }
    
    // Reverse section
    ProcessingSection(
        title = "Reverse",
        icon = Icons.Default.Flip,
        isExpanded = expandedSection == ProcessingSection.REVERSE,
        onToggleExpanded = { onExpandSection(ProcessingSection.REVERSE) },
        isApplied = trimSettings.reverse
    ) {
        ReverseControls(
            trimSettings = trimSettings,
            onTrimChange = onTrimChange,
            onApplyProcessing = onApplyProcessing
        )
    }
}

/**
 * Advanced processing operations (time-stretch, pitch shift, etc.)
 */
@Composable
private fun AdvancedProcessingOperations(
    sampleMetadata: SampleMetadata?,
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit,
    onFormatConversion: (AudioFormat) -> Unit,
    expandedSection: ProcessingSection?,
    onExpandSection: (ProcessingSection) -> Unit
) {
    // Time stretching section
    ProcessingSection(
        title = "Time Stretching",
        icon = Icons.Default.Timeline,
        isExpanded = expandedSection == ProcessingSection.TIME_STRETCH,
        onToggleExpanded = { onExpandSection(ProcessingSection.TIME_STRETCH) }
    ) {
        TimeStretchControls(
            onApplyProcessing = onApplyProcessing
        )
    }
    
    // Pitch shifting section
    ProcessingSection(
        title = "Pitch Shift",
        icon = Icons.Default.MusicNote,
        isExpanded = expandedSection == ProcessingSection.PITCH_SHIFT,
        onToggleExpanded = { onExpandSection(ProcessingSection.PITCH_SHIFT) }
    ) {
        PitchShiftControls(
            onApplyProcessing = onApplyProcessing
        )
    }
    
    // Format conversion section
    ProcessingSection(
        title = "Format Conversion",
        icon = Icons.Default.Transform,
        isExpanded = expandedSection == ProcessingSection.FORMAT_CONVERSION,
        onToggleExpanded = { onExpandSection(ProcessingSection.FORMAT_CONVERSION) }
    ) {
        FormatConversionControls(
            sampleMetadata = sampleMetadata,
            onFormatConversion = onFormatConversion
        )
    }
}

/**
 * Reusable processing section component
 */
@Composable
private fun ProcessingSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isApplied: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isApplied) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isApplied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Medium,
                        color = if (isApplied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    if (isApplied) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Applied",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }
            
            // Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Normalize controls
 */
@Composable
private fun NormalizeControls(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit
) {
    var targetLevel by remember { mutableStateOf(-3.0f) } // dB
    var normalizeMode by remember { mutableStateOf(NormalizeMode.PEAK) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Normalize audio to maximize volume while preventing clipping.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Normalize mode selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { normalizeMode = NormalizeMode.PEAK },
                label = { Text("Peak") },
                selected = normalizeMode == NormalizeMode.PEAK
            )
            FilterChip(
                onClick = { normalizeMode = NormalizeMode.RMS },
                label = { Text("RMS") },
                selected = normalizeMode == NormalizeMode.RMS
            )
        }
        
        // Target level
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Target:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = targetLevel,
                onValueChange = { targetLevel = it },
                valueRange = -12f..0f,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${String.format("%.1f", targetLevel)}dB",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp)
            )
        }
        
        // Apply button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onTrimChange(trimSettings.copy(normalize = !trimSettings.normalize))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (trimSettings.normalize) "Disable" else "Enable")
            }
            
            Button(
                onClick = {
                    onApplyProcessing(
                        ProcessingOperation.Normalize(
                            mode = normalizeMode,
                            targetLevel = targetLevel
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply")
            }
        }
    }
}

/**
 * Gain controls
 */
@Composable
private fun GainControls(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Adjust the overall volume of the sample.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Gain slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Gain:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = trimSettings.gain,
                onValueChange = { newGain ->
                    onTrimChange(trimSettings.copy(gain = newGain))
                },
                valueRange = 0.1f..3.0f,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${String.format("%.1f", trimSettings.gain)}x",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp)
            )
        }
        
        // Gain in dB
        val gainDb = 20 * kotlin.math.log10(trimSettings.gain)
        Text(
            text = "Gain: ${String.format("%.1f", gainDb)}dB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Quick gain presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onTrimChange(trimSettings.copy(gain = 0.5f)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("-6dB", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { onTrimChange(trimSettings.copy(gain = 1.0f)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("0dB", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { onTrimChange(trimSettings.copy(gain = 2.0f)) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+6dB", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Apply button
        Button(
            onClick = {
                onApplyProcessing(
                    ProcessingOperation.Gain(
                        multiplier = trimSettings.gain
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = trimSettings.gain != 1.0f
        ) {
            Text("Apply Gain")
        }
    }
}

/**
 * Reverse controls
 */
@Composable
private fun ReverseControls(
    trimSettings: SampleTrimSettings,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Reverse the sample playback direction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Reverse toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reverse Sample:",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Switch(
                checked = trimSettings.reverse,
                onCheckedChange = { checked ->
                    onTrimChange(trimSettings.copy(reverse = checked))
                }
            )
        }
        
        if (trimSettings.reverse) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Sample will be played in reverse",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // Apply button
        Button(
            onClick = {
                onApplyProcessing(ProcessingOperation.Reverse)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = trimSettings.reverse
        ) {
            Text("Apply Reverse")
        }
    }
}

/**
 * Time stretch controls
 */
@Composable
private fun TimeStretchControls(
    onApplyProcessing: (ProcessingOperation) -> Unit
) {
    var stretchRatio by remember { mutableStateOf(1.0f) }
    var preservePitch by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Change the playback speed without affecting pitch (or with pitch change).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Stretch ratio
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Speed:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = stretchRatio,
                onValueChange = { stretchRatio = it },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${String.format("%.1f", stretchRatio)}x",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp)
            )
        }
        
        // Preserve pitch toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Preserve Pitch:",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Switch(
                checked = preservePitch,
                onCheckedChange = { preservePitch = it }
            )
        }
        
        // Quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { stretchRatio = 0.5f },
                modifier = Modifier.weight(1f)
            ) {
                Text("Half", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { stretchRatio = 1.0f },
                modifier = Modifier.weight(1f)
            ) {
                Text("Normal", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { stretchRatio = 2.0f },
                modifier = Modifier.weight(1f)
            ) {
                Text("Double", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Apply button
        Button(
            onClick = {
                onApplyProcessing(
                    ProcessingOperation.TimeStretch(
                        ratio = stretchRatio,
                        preservePitch = preservePitch
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = stretchRatio != 1.0f
        ) {
            Text("Apply Time Stretch")
        }
    }
}

/**
 * Pitch shift controls
 */
@Composable
private fun PitchShiftControls(
    onApplyProcessing: (ProcessingOperation) -> Unit
) {
    var pitchShift by remember { mutableStateOf(0f) } // semitones
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Change the pitch without affecting playback speed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Pitch shift slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Pitch:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = pitchShift,
                onValueChange = { pitchShift = it },
                valueRange = -12f..12f,
                steps = 23, // 24 semitones total
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${if (pitchShift >= 0) "+" else ""}${pitchShift.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp)
            )
        }
        
        // Semitone display
        Text(
            text = "Semitones: ${if (pitchShift >= 0) "+" else ""}${pitchShift.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { pitchShift = -12f },
                modifier = Modifier.weight(1f)
            ) {
                Text("-1 Oct", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { pitchShift = 0f },
                modifier = Modifier.weight(1f)
            ) {
                Text("Original", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { pitchShift = 12f },
                modifier = Modifier.weight(1f)
            ) {
                Text("+1 Oct", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Apply button
        Button(
            onClick = {
                onApplyProcessing(
                    ProcessingOperation.PitchShift(
                        semitones = pitchShift
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pitchShift != 0f
        ) {
            Text("Apply Pitch Shift")
        }
    }
}

/**
 * Format conversion controls
 */
@Composable
private fun FormatConversionControls(
    sampleMetadata: SampleMetadata?,
    onFormatConversion: (AudioFormat) -> Unit
) {
    var targetFormat by remember { mutableStateOf(AudioFormat.WAV) }
    var targetSampleRate by remember { mutableStateOf(44100) }
    var targetBitDepth by remember { mutableStateOf(16) }
    var targetChannels by remember { mutableStateOf(1) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Convert sample to different audio format or quality settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        sampleMetadata?.let { metadata ->
            // Current format info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Current Format",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${metadata.format.uppercase()} • ${metadata.sampleRate}Hz • ${metadata.bitDepth}-bit • ${if (metadata.channels == 1) "Mono" else "Stereo"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Format selection
        Text(
            text = "Target Format:",
            style = MaterialTheme.typography.labelMedium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { targetFormat = AudioFormat.WAV },
                label = { Text("WAV") },
                selected = targetFormat == AudioFormat.WAV
            )
            FilterChip(
                onClick = { targetFormat = AudioFormat.FLAC },
                label = { Text("FLAC") },
                selected = targetFormat == AudioFormat.FLAC
            )
            FilterChip(
                onClick = { targetFormat = AudioFormat.MP3 },
                label = { Text("MP3") },
                selected = targetFormat == AudioFormat.MP3
            )
        }
        
        // Quality settings
        if (targetFormat != AudioFormat.MP3) {
            // Sample rate
            Text(
                text = "Sample Rate:",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { targetSampleRate = 22050 },
                    label = { Text("22kHz") },
                    selected = targetSampleRate == 22050
                )
                FilterChip(
                    onClick = { targetSampleRate = 44100 },
                    label = { Text("44.1kHz") },
                    selected = targetSampleRate == 44100
                )
                FilterChip(
                    onClick = { targetSampleRate = 48000 },
                    label = { Text("48kHz") },
                    selected = targetSampleRate == 48000
                )
            }
            
            // Bit depth
            Text(
                text = "Bit Depth:",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { targetBitDepth = 16 },
                    label = { Text("16-bit") },
                    selected = targetBitDepth == 16
                )
                FilterChip(
                    onClick = { targetBitDepth = 24 },
                    label = { Text("24-bit") },
                    selected = targetBitDepth == 24
                )
                if (targetFormat == AudioFormat.WAV || targetFormat == AudioFormat.FLAC) {
                    FilterChip(
                        onClick = { targetBitDepth = 32 },
                        label = { Text("32-bit") },
                        selected = targetBitDepth == 32
                    )
                }
            }
        }
        
        // Channels
        Text(
            text = "Channels:",
            style = MaterialTheme.typography.labelMedium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { targetChannels = 1 },
                label = { Text("Mono") },
                selected = targetChannels == 1
            )
            FilterChip(
                onClick = { targetChannels = 2 },
                label = { Text("Stereo") },
                selected = targetChannels == 2
            )
        }
        
        // Apply button
        Button(
            onClick = {
                onFormatConversion(
                    AudioFormat(
                        format = targetFormat.format,
                        sampleRate = targetSampleRate,
                        bitDepth = targetBitDepth,
                        channels = targetChannels
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Convert Format")
        }
    }
}

/**
 * Processing history display
 */
@Composable
private fun ProcessingHistory(
    processingHistory: List<ProcessingOperation>,
    currentHistoryIndex: Int,
    onJumpToHistory: (Int) -> Unit
) {
    if (processingHistory.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Processing History",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                
                processingHistory.forEachIndexed { index, operation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${operation.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index <= currentHistoryIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                        )
                        
                        if (index <= currentHistoryIndex) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Applied",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Processing summary
 */
@Composable
private fun ProcessingSummary(
    trimSettings: SampleTrimSettings,
    processingHistory: List<ProcessingOperation>
) {
    if (trimSettings.hasProcessing || processingHistory.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Processing Summary",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                val appliedOperations = mutableListOf<String>()
                
                if (trimSettings.isTrimmed) {
                    appliedOperations.add("Trimmed")
                }
                if (trimSettings.fadeInMs > 0f) {
                    appliedOperations.add("Fade In: ${trimSettings.fadeInMs.toInt()}ms")
                }
                if (trimSettings.fadeOutMs > 0f) {
                    appliedOperations.add("Fade Out: ${trimSettings.fadeOutMs.toInt()}ms")
                }
                if (trimSettings.gain != 1.0f) {
                    appliedOperations.add("Gain: ${String.format("%.1f", trimSettings.gain)}x")
                }
                if (trimSettings.normalize) {
                    appliedOperations.add("Normalized")
                }
                if (trimSettings.reverse) {
                    appliedOperations.add("Reversed")
                }
                
                appliedOperations.forEach { operation ->
                    Text(
                        text = "• $operation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                if (appliedOperations.isEmpty()) {
                    Text(
                        text = "No processing applied",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Processing operation data classes
 */
@Serializable
sealed class ProcessingOperation {
    abstract val displayName: String
    
    @Serializable
    data class Normalize(
        val mode: NormalizeMode,
        val targetLevel: Float
    ) : ProcessingOperation() {
        override val displayName = "Normalize (${mode.name}, ${targetLevel}dB)"
    }
    
    @Serializable
    data class Gain(
        val multiplier: Float
    ) : ProcessingOperation() {
        override val displayName = "Gain (${String.format("%.1f", multiplier)}x)"
    }
    
    @Serializable
    object Reverse : ProcessingOperation() {
        override val displayName = "Reverse"
    }
    
    @Serializable
    data class TimeStretch(
        val ratio: Float,
        val preservePitch: Boolean
    ) : ProcessingOperation() {
        override val displayName = "Time Stretch (${String.format("%.1f", ratio)}x${if (preservePitch) ", preserve pitch" else ""})"
    }
    
    @Serializable
    data class PitchShift(
        val semitones: Float
    ) : ProcessingOperation() {
        override val displayName = "Pitch Shift (${if (semitones >= 0) "+" else ""}${semitones.toInt()} semitones)"
    }
}

/**
 * Processing sections enum
 */
enum class ProcessingSection {
    NORMALIZE,
    GAIN,
    REVERSE,
    TIME_STRETCH,
    PITCH_SHIFT,
    FORMAT_CONVERSION
}

/**
 * Normalize modes
 */
@Serializable
enum class NormalizeMode {
    PEAK,
    RMS
}

/**
 * Audio format data class
 */
@Serializable
data class AudioFormat(
    val format: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int
) {
    companion object {
        val WAV = AudioFormat("wav", 44100, 16, 1)
        val FLAC = AudioFormat("flac", 44100, 16, 1)
        val MP3 = AudioFormat("mp3", 44100, 16, 1)
    }
}