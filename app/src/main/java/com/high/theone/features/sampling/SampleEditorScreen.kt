package com.high.theone.features.sampling

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.high.theone.model.SampleMetadata
import com.high.theone.model.SampleTrimSettings

/**
 * Complete sample editor screen that integrates all editing functionality
 * including waveform display, trimming, processing, and apply/cancel workflow.
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6 (complete sample editing system)
 */
@Composable
fun SampleEditorScreen(
    sampleMetadata: SampleMetadata?,
    waveformData: FloatArray,
    trimSettings: SampleTrimSettings,
    processingHistory: List<ProcessingOperation>,
    currentHistoryIndex: Int,
    isPlaying: Boolean,
    playbackPosition: Float,
    zoomLevel: Float,
    scrollPosition: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onTrimChange: (SampleTrimSettings) -> Unit,
    onZoomChange: (Float) -> Unit,
    onScrollChange: (Float) -> Unit,
    onApplyProcessing: (ProcessingOperation) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetProcessing: () -> Unit,
    onFormatConversion: (AudioFormat) -> Unit,
    onPreviewTrimmed: () -> Unit,
    onApplyEdits: () -> Unit,
    onCancelEdits: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(EditorTab.WAVEFORM) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    
    // Track if there are unsaved changes
    val hasUnsavedChanges = trimSettings.hasProcessing || processingHistory.isNotEmpty()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top app bar
        SampleEditorTopBar(
            sampleMetadata = sampleMetadata,
            hasUnsavedChanges = hasUnsavedChanges,
            onApplyEdits = onApplyEdits,
            onCancelEdits = {
                if (hasUnsavedChanges) {
                    showUnsavedChangesDialog = true
                } else {
                    onCancelEdits()
                }
            },
            onClose = {
                if (hasUnsavedChanges) {
                    showUnsavedChangesDialog = true
                } else {
                    onClose()
                }
            }
        )
        
        // Tab navigation
        EditorTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
        
        // Tab content
        when (selectedTab) {
            EditorTab.WAVEFORM -> {
                SampleEditor(
                    sampleMetadata = sampleMetadata,
                    waveformData = waveformData,
                    trimSettings = trimSettings,
                    isPlaying = isPlaying,
                    playbackPosition = playbackPosition,
                    zoomLevel = zoomLevel,
                    scrollPosition = scrollPosition,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onSeek = onSeek,
                    onTrimChange = onTrimChange,
                    onZoomChange = onZoomChange,
                    onScrollChange = onScrollChange,
                    onApplyEdits = onApplyEdits,
                    onCancelEdits = onCancelEdits,
                    onResetTrim = {
                        onTrimChange(SampleTrimSettings())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            
            EditorTab.TRIMMING -> {
                AdvancedSampleTrimming(
                    sampleMetadata = sampleMetadata,
                    waveformData = waveformData,
                    trimSettings = trimSettings,
                    isPlaying = isPlaying,
                    playbackPosition = playbackPosition,
                    onTrimChange = onTrimChange,
                    onPreviewTrimmed = onPreviewTrimmed,
                    onSeek = onSeek,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
            
            EditorTab.PROCESSING -> {
                SampleProcessingPanel(
                    sampleMetadata = sampleMetadata,
                    trimSettings = trimSettings,
                    processingHistory = processingHistory,
                    currentHistoryIndex = currentHistoryIndex,
                    onTrimChange = onTrimChange,
                    onApplyProcessing = onApplyProcessing,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onResetProcessing = onResetProcessing,
                    onFormatConversion = onFormatConversion,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
    
    // Unsaved changes dialog
    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onSaveAndExit = {
                onApplyEdits()
                showUnsavedChangesDialog = false
                onClose()
            },
            onDiscardAndExit = {
                onCancelEdits()
                showUnsavedChangesDialog = false
                onClose()
            },
            onCancel = {
                showUnsavedChangesDialog = false
            }
        )
    }
}

/**
 * Top app bar for the sample editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleEditorTopBar(
    sampleMetadata: SampleMetadata?,
    hasUnsavedChanges: Boolean,
    onApplyEdits: () -> Unit,
    onCancelEdits: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = sampleMetadata?.name ?: "Sample Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasUnsavedChanges) {
                    Text(
                        text = "Unsaved changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        },
        actions = {
            // Cancel button
            TextButton(onClick = onCancelEdits) {
                Text("Cancel")
            }
            
            // Apply button
            Button(
                onClick = onApplyEdits,
                enabled = hasUnsavedChanges
            ) {
                Text("Apply")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Tab row for editor sections
 */
@Composable
private fun EditorTabRow(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.displayName,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.displayName,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

/**
 * Unsaved changes confirmation dialog
 */
@Composable
private fun UnsavedChangesDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Unsaved Changes")
        },
        text = {
            Text("You have unsaved changes to this sample. What would you like to do?")
        },
        confirmButton = {
            Button(onClick = onSaveAndExit) {
                Text("Save & Exit")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDiscardAndExit) {
                    Text("Discard")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Editor tab definitions
 */
enum class EditorTab(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    WAVEFORM("Waveform", Icons.Default.GraphicEq),
    TRIMMING("Trimming", Icons.Default.ContentCut),
    PROCESSING("Processing", Icons.Default.Tune)
}

/**
 * Sample editor state for managing the complete editing workflow
 */
@Stable
data class SampleEditorState(
    val sampleMetadata: SampleMetadata? = null,
    val waveformData: FloatArray = FloatArray(0),
    val trimSettings: SampleTrimSettings = SampleTrimSettings(),
    val processingHistory: List<ProcessingOperation> = emptyList(),
    val currentHistoryIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playbackPosition: Float = 0f,
    val zoomLevel: Float = 1f,
    val scrollPosition: Float = 0f,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Check if there are any unsaved changes
     */
    val hasUnsavedChanges: Boolean
        get() = trimSettings.hasProcessing || processingHistory.isNotEmpty()
    
    /**
     * Check if undo is available
     */
    val canUndo: Boolean
        get() = currentHistoryIndex >= 0
    
    /**
     * Check if redo is available
     */
    val canRedo: Boolean
        get() = currentHistoryIndex < processingHistory.size - 1
    
    /**
     * Get the effective sample duration considering trim settings
     */
    val effectiveDurationMs: Long
        get() = sampleMetadata?.let { metadata ->
            if (trimSettings.isTrimmed) {
                trimSettings.trimmedDurationMs
            } else {
                metadata.durationMs
            }
        } ?: 0L
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as SampleEditorState
        
        if (sampleMetadata != other.sampleMetadata) return false
        if (!waveformData.contentEquals(other.waveformData)) return false
        if (trimSettings != other.trimSettings) return false
        if (processingHistory != other.processingHistory) return false
        if (currentHistoryIndex != other.currentHistoryIndex) return false
        if (isPlaying != other.isPlaying) return false
        if (playbackPosition != other.playbackPosition) return false
        if (zoomLevel != other.zoomLevel) return false
        if (scrollPosition != other.scrollPosition) return false
        if (isLoading != other.isLoading) return false
        if (error != other.error) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = sampleMetadata?.hashCode() ?: 0
        result = 31 * result + waveformData.contentHashCode()
        result = 31 * result + trimSettings.hashCode()
        result = 31 * result + processingHistory.hashCode()
        result = 31 * result + currentHistoryIndex
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + playbackPosition.hashCode()
        result = 31 * result + zoomLevel.hashCode()
        result = 31 * result + scrollPosition.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

/**
 * Sample editor actions for handling user interactions
 */
sealed class SampleEditorAction {
    object PlayPause : SampleEditorAction()
    object Stop : SampleEditorAction()
    data class Seek(val position: Float) : SampleEditorAction()
    data class UpdateTrimSettings(val settings: SampleTrimSettings) : SampleEditorAction()
    data class UpdateZoom(val level: Float) : SampleEditorAction()
    data class UpdateScroll(val position: Float) : SampleEditorAction()
    data class ApplyProcessing(val operation: ProcessingOperation) : SampleEditorAction()
    object Undo : SampleEditorAction()
    object Redo : SampleEditorAction()
    object ResetProcessing : SampleEditorAction()
    data class ConvertFormat(val format: AudioFormat) : SampleEditorAction()
    object PreviewTrimmed : SampleEditorAction()
    object ApplyEdits : SampleEditorAction()
    object CancelEdits : SampleEditorAction()
    object Close : SampleEditorAction()
}

/**
 * Sample editor events for communicating results back to the parent
 */
sealed class SampleEditorEvent {
    data class EditsApplied(val sampleMetadata: SampleMetadata, val trimSettings: SampleTrimSettings) : SampleEditorEvent()
    object EditsCancelled : SampleEditorEvent()
    object EditorClosed : SampleEditorEvent()
    data class Error(val message: String) : SampleEditorEvent()
}