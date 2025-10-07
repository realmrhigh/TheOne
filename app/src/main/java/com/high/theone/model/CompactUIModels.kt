package com.high.theone.model

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Screen configuration data class that detects orientation and size for responsive layout
 */
@Stable
@Serializable
data class ScreenConfiguration(
    @Serializable(with = DpSerializer::class)
    val screenWidth: Dp,
    @Serializable(with = DpSerializer::class)
    val screenHeight: Dp,
    val orientation: Orientation,
    val densityDpi: Int,
    val isTablet: Boolean
) {
    val layoutMode: LayoutMode
        get() = when {
            isTablet -> LayoutMode.TABLET
            orientation == Orientation.LANDSCAPE -> LayoutMode.LANDSCAPE
            screenHeight < 600.dp -> LayoutMode.COMPACT_PORTRAIT
            else -> LayoutMode.STANDARD_PORTRAIT
        }
    
    val aspectRatio: Float
        get() = screenWidth.value / screenHeight.value
    
    val isCompact: Boolean
        get() = layoutMode == LayoutMode.COMPACT_PORTRAIT
    
    val availableWidth: Dp
        get() = screenWidth
    
    val availableHeight: Dp
        get() = screenHeight
}

/**
 * Device orientation enumeration
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Layout mode based on screen configuration
 */
enum class LayoutMode {
    COMPACT_PORTRAIT,    // Small portrait screens
    STANDARD_PORTRAIT,   // Standard portrait screens
    LANDSCAPE,           // Landscape orientation
    TABLET              // Large screens/tablets
}

/**
 * Panel types for the compact UI system
 */
enum class PanelType {
    SAMPLING,
    MIDI,
    MIXER,
    SETTINGS,
    SAMPLE_EDITOR
}

/**
 * Section types that can be collapsed
 */
enum class SectionType {
    DRUM_PADS,
    SEQUENCER,
    TRANSPORT,
    QUICK_ACCESS,
    UTILITY_PANEL
}

/**
 * Panel state for managing visibility and content
 */
@Stable
@Serializable
data class PanelState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val snapPosition: SnapPosition = SnapPosition.HIDDEN,
    val contentType: PanelType? = null
)

/**
 * Snap positions for bottom sheet and panels
 */
enum class SnapPosition {
    HIDDEN,
    PEEK,
    HALF,
    FULL
}

/**
 * Layout state for managing UI configuration
 */
@Stable
@Serializable
data class LayoutState(
    val configuration: ScreenConfiguration,
    val activePanels: Set<PanelType> = emptySet(),
    val panelVisibility: Map<PanelType, Boolean> = emptyMap(),
    val collapsedSections: Set<SectionType> = emptySet(),
    val bottomSheetState: PanelState = PanelState(),
    val sidePanel: PanelState = PanelState()
)

/**
 * Transport control state
 */
@Stable
data class TransportState(
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val bpm: Int = 120,
    val currentPosition: Long = 0,
    val midiSyncEnabled: Boolean = false,
    val midiSyncStatus: MidiSyncStatus = MidiSyncStatus.DISCONNECTED,
    val audioLevels: AudioLevels = AudioLevels()
)

/**
 * MIDI synchronization status
 */
enum class MidiSyncStatus {
    DISCONNECTED,
    CONNECTED,
    SYNCING,
    SYNCED,
    ERROR
}

/**
 * Audio level monitoring data
 */
@Stable
data class AudioLevels(
    val masterLevel: Float = 0f,
    val inputLevel: Float = 0f,
    val peakLevel: Float = 0f,
    val clipIndicator: Boolean = false
)

/**
 * Performance monitoring metrics
 */
@Stable
data class PerformanceMetrics(
    val frameRate: Float = 60f,
    val memoryUsage: Long = 0L,
    val cpuUsage: Float = 0f,
    val audioLatency: Float = 0f,
    val droppedFrames: Int = 0,
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * Drum pad state from DrumTrackViewModel
 */
@Stable
data class DrumPadState(
    val padSettings: Map<String, com.high.theone.features.drumtrack.model.PadSettings> = emptyMap(),
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val activePadId: String? = null
)

/**
 * MIDI system state
 */
@Stable
data class MidiState(
    val isEnabled: Boolean = false,
    val connectedDevices: Int = 0,
    val activeMappings: Int = 0,
    val isMonitoring: Boolean = false,
    val statistics: com.high.theone.midi.model.MidiStatistics = com.high.theone.midi.model.MidiStatistics(
        inputMessageCount = 0L,
        outputMessageCount = 0L,
        averageInputLatency = 0f,
        droppedMessageCount = 0L,
        lastErrorMessage = null
    )
)

/**
 * Complete compact UI state combining all components
 */
@Stable
data class CompactUIState(
    val transportState: TransportState = TransportState(),
    val layoutState: LayoutState,
    val panelStates: Map<PanelType, PanelState> = emptyMap(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val drumPadState: DrumPadState = DrumPadState(),
    val sequencerState: SequencerState = SequencerState(),
    val midiState: MidiState = MidiState(),
    val isInitialized: Boolean = false,
    val errorState: String? = null
)

/**
 * UI customization preferences
 */
@Serializable
data class UICustomizationPreferences(
    val preferredLayoutMode: LayoutMode? = null,
    val hiddenPanels: Set<PanelType> = emptySet(),
    val collapsedSectionsByDefault: Set<SectionType> = emptySet(),
    val enableAnimations: Boolean = true,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED
)

/**
 * Performance mode settings
 */
enum class PerformanceMode {
    HIGH_PERFORMANCE,  // Maximum features, may impact performance
    BALANCED,          // Default balanced mode
    BATTERY_SAVER     // Reduced features for better battery life
}

/**
 * Layout customization settings
 */
@Serializable
data class LayoutCustomization(
    val drumPadSize: ComponentSize = ComponentSize.MEDIUM,
    val sequencerHeight: ComponentSize = ComponentSize.MEDIUM,
    val transportBarHeight: ComponentSize = ComponentSize.MEDIUM,
    val panelSpacing: ComponentSize = ComponentSize.MEDIUM,
    val enableAnimations: Boolean = true,
    val compactMode: Boolean = false,
    val customColors: Map<String, String> = emptyMap()
)

/**
 * Component size options
 */
enum class ComponentSize {
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

/**
 * Feature visibility preferences
 */
@Serializable
data class FeatureVisibilityPreferences(
    val showTransportControls: Boolean = true,
    val showDrumPads: Boolean = true,
    val showSequencer: Boolean = true,
    val showMidiControls: Boolean = true,
    val showMixerControls: Boolean = true,
    val showSamplingControls: Boolean = true,
    val showAdvancedControls: Boolean = false,
    val showPerformanceMetrics: Boolean = false,
    val showVisualEffects: Boolean = true,
    val showAudioLevels: Boolean = true
)

/**
 * Panel position options
 */
enum class PanelPosition {
    BOTTOM_SHEET,
    SIDE_PANEL,
    OVERLAY,
    INLINE,
    HIDDEN
}

/**
 * Layout preset containing all customization settings
 */
@Serializable
data class LayoutPreset(
    val id: String,
    val name: String,
    val layoutCustomization: LayoutCustomization,
    val featureVisibility: FeatureVisibilityPreferences,
    val panelPositions: Map<PanelType, PanelPosition>,
    val createdAt: Long,
    val isDefault: Boolean = false
)

/**
 * Serializer for Compose Dp values
 */
object DpSerializer : KSerializer<Dp> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Dp", PrimitiveKind.FLOAT)
    
    override fun serialize(encoder: Encoder, value: Dp) {
        encoder.encodeFloat(value.value)
    }
    
    override fun deserialize(decoder: Decoder): Dp {
        return decoder.decodeFloat().dp
    }
}