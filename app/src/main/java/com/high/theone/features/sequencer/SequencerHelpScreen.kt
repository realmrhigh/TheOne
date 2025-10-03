package com.high.theone.features.sequencer

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
import androidx.navigation.NavHostController

/**
 * Help and tutorial screen for the sequencer
 * Requirements: 9.7 - help and tutorial integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerHelpScreen(
    navController: NavHostController
) {
    val scrollState = rememberScrollState()
    var selectedSection by remember { mutableStateOf(HelpSection.GETTING_STARTED) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sequencer Help") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Navigation sidebar
            NavigationRail(
                modifier = Modifier.fillMaxHeight()
            ) {
                HelpSection.values().forEach { section ->
                    NavigationRailItem(
                        icon = { Icon(section.icon, contentDescription = section.title) },
                        label = { Text(section.title) },
                        selected = selectedSection == section,
                        onClick = { selectedSection = section }
                    )
                }
            }
            
            // Content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedSection) {
                    HelpSection.GETTING_STARTED -> GettingStartedContent()
                    HelpSection.PATTERN_PROGRAMMING -> PatternProgrammingContent()
                    HelpSection.RECORDING -> RecordingContent()
                    HelpSection.SONG_MODE -> SongModeContent()
                    HelpSection.SETTINGS -> SettingsContent()
                    HelpSection.TROUBLESHOOTING -> TroubleshootingContent()
                }
            }
        }
    }
}

/**
 * Help section definitions
 */
enum class HelpSection(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GETTING_STARTED("Getting Started", Icons.Default.PlayArrow),
    PATTERN_PROGRAMMING("Patterns", Icons.Default.GridOn),
    RECORDING("Recording", Icons.Default.FiberManualRecord),
    SONG_MODE("Song Mode", Icons.Default.QueueMusic),
    SETTINGS("Settings", Icons.Default.Settings),
    TROUBLESHOOTING("Troubleshooting", Icons.Default.Help)
}

/**
 * Getting started content
 */
@Composable
private fun GettingStartedContent() {
    HelpSectionContent(
        title = "Getting Started with the Sequencer",
        content = {
            HelpCard(
                title = "What is a Step Sequencer?",
                content = "A step sequencer lets you program drum patterns by placing beats on a grid. Each row represents a different drum sound (kick, snare, hi-hat, etc.) and each column represents a step in time."
            )
            
            HelpCard(
                title = "Basic Workflow",
                content = """
                    1. Load or record samples into pads
                    2. Create a new pattern
                    3. Tap steps to program your beat
                    4. Press play to hear your pattern
                    5. Adjust tempo and swing to taste
                """.trimIndent()
            )
            
            HelpCard(
                title = "Interface Overview",
                content = """
                    • Transport Controls: Play, pause, stop, record
                    • Pattern Selector: Switch between different patterns
                    • Step Grid: Program your beats here
                    • Pad Selector: Choose which drum sounds to show
                    • Tempo/Swing: Adjust timing and groove
                """.trimIndent()
            )
        }
    )
}

/**
 * Pattern programming content
 */
@Composable
private fun PatternProgrammingContent() {
    HelpSectionContent(
        title = "Programming Patterns",
        content = {
            HelpCard(
                title = "Creating Patterns",
                content = """
                    • Tap the "+" button to create a new pattern
                    • Choose pattern length (8, 16, 24, or 32 steps)
                    • Give your pattern a descriptive name
                    • Start programming by tapping steps
                """.trimIndent()
            )
            
            HelpCard(
                title = "Step Programming",
                content = """
                    • Tap a step to toggle it on/off
                    • Long press a step to adjust velocity
                    • Active steps are highlighted
                    • The playback indicator shows current position
                """.trimIndent()
            )
            
            HelpCard(
                title = "Pattern Management",
                content = """
                    • Duplicate patterns to create variations
                    • Rename patterns for better organization
                    • Delete unused patterns to save space
                    • Copy steps between patterns
                """.trimIndent()
            )
            
            HelpCard(
                title = "Timing and Groove",
                content = """
                    • Adjust tempo from 60-200 BPM
                    • Add swing for a more human feel
                    • Use different pattern lengths for variety
                    • Combine patterns in Song Mode
                """.trimIndent()
            )
        }
    )
}

/**
 * Recording content
 */
@Composable
private fun RecordingContent() {
    HelpSectionContent(
        title = "Real-time Recording",
        content = {
            HelpCard(
                title = "Recording Modes",
                content = """
                    • Replace: Overwrites existing pattern
                    • Overdub: Adds to existing pattern
                    • Punch In: Records only during specific sections
                """.trimIndent()
            )
            
            HelpCard(
                title = "Recording Process",
                content = """
                    1. Press the record button (red circle)
                    2. Press play to start recording
                    3. Play pads in time with the beat
                    4. Press stop when finished
                    5. Your performance is automatically quantized
                """.trimIndent()
            )
            
            HelpCard(
                title = "Recording Tips",
                content = """
                    • Use a metronome for better timing
                    • Start with simple patterns
                    • Record multiple passes for complex beats
                    • Use overdub mode to layer parts
                    • Adjust quantization in settings
                """.trimIndent()
            )
        }
    )
}

/**
 * Song mode content
 */
@Composable
private fun SongModeContent() {
    HelpSectionContent(
        title = "Song Mode and Arrangement",
        content = {
            HelpCard(
                title = "What is Song Mode?",
                content = "Song Mode lets you chain multiple patterns together to create complete song arrangements with verses, choruses, bridges, and more."
            )
            
            HelpCard(
                title = "Creating Songs",
                content = """
                    1. Create multiple patterns (verse, chorus, etc.)
                    2. Switch to Song Mode tab
                    3. Drag patterns into the arrangement
                    4. Set repeat counts for each section
                    5. Press play to hear the full song
                """.trimIndent()
            )
            
            HelpCard(
                title = "Song Features",
                content = """
                    • Seamless pattern transitions
                    • Pattern repeat counts
                    • Song position navigation
                    • Loop entire songs
                    • Export complete arrangements
                """.trimIndent()
            )
        }
    )
}

/**
 * Settings content
 */
@Composable
private fun SettingsContent() {
    HelpSectionContent(
        title = "Settings and Preferences",
        content = {
            HelpCard(
                title = "Default Settings",
                content = """
                    • Default Tempo: Starting tempo for new patterns
                    • Default Swing: Groove amount for new patterns
                    • Quantization: How tightly recordings snap to grid
                    • Recording Mode: Default recording behavior
                """.trimIndent()
            )
            
            HelpCard(
                title = "Performance Settings",
                content = """
                    • Performance Mode: Optimize for battery or performance
                    • Max Pattern Length: Longest allowed patterns
                    • Max Patterns: Maximum patterns per project
                    • Advanced Features: Enable experimental features
                """.trimIndent()
            )
            
            HelpCard(
                title = "Interface Settings",
                content = """
                    • Visual Feedback: Animations and effects
                    • Haptic Feedback: Vibration on interactions
                    • Metronome: Click track during playback
                    • Auto-save: Automatically save changes
                """.trimIndent()
            )
        }
    )
}

/**
 * Troubleshooting content
 */
@Composable
private fun TroubleshootingContent() {
    HelpSectionContent(
        title = "Troubleshooting",
        content = {
            HelpCard(
                title = "Audio Issues",
                content = """
                    • No sound: Check device volume and sample assignments
                    • Crackling audio: Reduce pattern complexity or enable power save mode
                    • Timing issues: Restart the app or check CPU usage
                    • Samples not loading: Check file format and storage space
                """.trimIndent()
            )
            
            HelpCard(
                title = "Performance Issues",
                content = """
                    • Slow response: Enable performance mode in settings
                    • High battery usage: Use power save mode
                    • App crashes: Reduce max patterns and pattern length
                    • Memory warnings: Clear unused patterns and samples
                """.trimIndent()
            )
            
            HelpCard(
                title = "Pattern Issues",
                content = """
                    • Pattern won't play: Check that pads have samples assigned
                    • Steps not triggering: Verify step is active and pad is not muted
                    • Timing feels off: Adjust swing or check tempo settings
                    • Can't hear certain pads: Check mute/solo settings
                """.trimIndent()
            )
            
            HelpCard(
                title = "Getting Help",
                content = """
                    • Check the settings for performance optimizations
                    • Try resetting settings to defaults
                    • Restart the app if issues persist
                    • Contact support with specific error messages
                """.trimIndent()
            )
        }
    )
}

/**
 * Help section content wrapper
 */
@Composable
private fun HelpSectionContent(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        content()
    }
}

/**
 * Help card component
 */
@Composable
private fun HelpCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
            )
        }
    }
}