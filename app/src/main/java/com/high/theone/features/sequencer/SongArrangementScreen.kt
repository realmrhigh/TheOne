package com.high.theone.features.sequencer

import androidx.compose.animation.*
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
import com.high.theone.model.*

/**
 * Complete song arrangement screen with pattern chain editor and timeline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongArrangementScreen(
    sequencerState: SequencerState,
    songState: SongPlaybackState,
    navigationState: SongNavigationState,
    patterns: List<Pattern>,
    onCreateSong: (List<SongStep>) -> Unit,
    onUpdateSequence: (List<SongStep>) -> Unit,
    onNavigateToPosition: (Int, Int) -> Unit,
    onNavigateToTimelinePosition: (Float) -> Unit,
    onStartSong: () -> Unit,
    onStopSong: () -> Unit,
    onPauseSong: () -> Unit,
    onResumeSong: () -> Unit,
    onToggleLoop: () -> Unit,
    onActivateSongMode: () -> Unit,
    onDeactivateSongMode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SongArrangementTab.EDITOR) }
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top app bar
        TopAppBar(
            title = {
                Text(
                    text = "Song Arrangement",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                // Song mode toggle
                IconButton(
                    onClick = {
                        if (songState.isActive) {
                            onDeactivateSongMode()
                        } else {
                            onActivateSongMode()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (songState.isActive) {
                            Icons.Default.QueueMusic
                        } else {
                            Icons.Default.MusicNote
                        },
                        contentDescription = if (songState.isActive) {
                            "Exit song mode"
                        } else {
                            "Enter song mode"
                        },
                        tint = if (songState.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        )
        
        // Song mode status
        if (songState.isActive) {
            SongModeStatusBar(
                songState = songState,
                navigationState = navigationState,
                onStartSong = onStartSong,
                onStopSong = onStopSong,
                onPauseSong = onPauseSong,
                onResumeSong = onResumeSong,
                onToggleLoop = onToggleLoop
            )
        }
        
        // Tab selector
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            SongArrangementTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.displayName,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.displayName
                        )
                    }
                )
            }
        }
        
        // Tab content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                SongArrangementTab.EDITOR -> {
                    PatternChainEditorTab(
                        songMode = songState.songMode ?: SongMode(),
                        patterns = patterns,
                        currentSequencePosition = songState.currentSequencePosition,
                        onSequenceUpdate = onUpdateSequence,
                        onPatternSelect = { position ->
                            onNavigateToPosition(position, 0)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
                }
                
                SongArrangementTab.TIMELINE -> {
                    TimelineTab(
                        songMode = songState.songMode ?: SongMode(),
                        patterns = patterns,
                        navigationState = navigationState,
                        songState = songState,
                        onPositionScrub = onNavigateToTimelinePosition,
                        onPatternSelect = { position ->
                            onNavigateToPosition(position, 0)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                SongArrangementTab.OVERVIEW -> {
                    SongOverviewTab(
                        songMode = songState.songMode ?: SongMode(),
                        patterns = patterns,
                        songState = songState,
                        navigationState = navigationState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SongModeStatusBar(
    songState: SongPlaybackState,
    navigationState: SongNavigationState,
    onStartSong: () -> Unit,
    onStopSong: () -> Unit,
    onPauseSong: () -> Unit,
    onResumeSong: () -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (songState.isPlaying) {
                            onPauseSong()
                        } else {
                            onResumeSong()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (songState.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (songState.isPlaying) "Pause" else "Play"
                    )
                }
                
                IconButton(onClick = onStopSong) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop"
                    )
                }
                
                IconButton(onClick = onToggleLoop) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Toggle loop",
                        tint = if (songState.songMode?.loopEnabled == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            
            // Status info
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Song Mode Active",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = buildString {
                        append("Position ${songState.currentSequencePosition + 1}")
                        songState.songMode?.let { song ->
                            if (song.sequence.isNotEmpty()) {
                                append(" of ${song.sequence.size}")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PatternChainEditorTab(
    songMode: SongMode,
    patterns: List<Pattern>,
    currentSequencePosition: Int,
    onSequenceUpdate: (List<SongStep>) -> Unit,
    onPatternSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PatternChainEditor(
        songMode = songMode,
        availablePatterns = patterns,
        currentSequencePosition = currentSequencePosition,
        onSequenceUpdate = onSequenceUpdate,
        onPatternSelect = onPatternSelect,
        modifier = modifier
    )
}

@Composable
private fun TimelineTab(
    songMode: SongMode,
    patterns: List<Pattern>,
    navigationState: SongNavigationState,
    songState: SongPlaybackState,
    onPositionScrub: (Float) -> Unit,
    onPatternSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SongTimeline(
            songMode = songMode,
            availablePatterns = patterns,
            currentPosition = navigationState.currentPosition,
            playbackProgress = navigationState.absoluteStep.toFloat() / navigationState.totalDuration.coerceAtLeast(1),
            isPlaying = songState.isPlaying,
            onPositionScrub = onPositionScrub,
            onPatternSelect = onPatternSelect
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Timeline markers
        TimelineMarkers(
            markers = navigationState.timeline,
            patterns = patterns
        )
    }
}

@Composable
private fun SongOverviewTab(
    songMode: SongMode,
    patterns: List<Pattern>,
    songState: SongPlaybackState,
    navigationState: SongNavigationState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Song statistics
        SongStatistics(
            songMode = songMode,
            patterns = patterns
        )
        
        // Current status
        CurrentPlaybackStatus(
            songState = songState,
            navigationState = navigationState,
            patterns = patterns
        )
        
        // Pattern usage
        PatternUsageOverview(
            songMode = songMode,
            patterns = patterns
        )
    }
}

@Composable
private fun TimelineMarkers(
    markers: List<TimelineMarker>,
    patterns: List<Pattern>,
    modifier: Modifier = Modifier
) {
    if (markers.isNotEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Timeline Markers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                markers.forEach { marker ->
                    val pattern = patterns.find { it.id == marker.patternId }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (marker.type) {
                                    TimelineMarkerType.PATTERN_START -> Icons.Default.PlayArrow
                                    TimelineMarkerType.PATTERN_REPEAT -> Icons.Default.Repeat
                                    TimelineMarkerType.SECTION_BOUNDARY -> Icons.Default.Flag
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = marker.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Text(
                                text = pattern?.name ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Text(
                            text = "${(marker.position * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongStatistics(
    songMode: SongMode,
    patterns: List<Pattern>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Song Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Patterns",
                    value = songMode.sequence.size.toString()
                )
                
                StatisticItem(
                    label = "Total Steps",
                    value = songMode.getTotalSteps().toString()
                )
                
                StatisticItem(
                    label = "Unique Patterns",
                    value = songMode.sequence.map { it.patternId }.distinct().size.toString()
                )
                
                StatisticItem(
                    label = "Loop",
                    value = if (songMode.loopEnabled) "On" else "Off"
                )
            }
        }
    }
}

@Composable
private fun CurrentPlaybackStatus(
    songState: SongPlaybackState,
    navigationState: SongNavigationState,
    patterns: List<Pattern>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Current Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val currentPattern = songState.songMode?.sequence?.getOrNull(songState.currentSequencePosition)
            val pattern = patterns.find { it.id == currentPattern?.patternId }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusRow(
                    label = "Status",
                    value = when {
                        songState.isPlaying -> "Playing"
                        songState.isActive -> "Ready"
                        else -> "Inactive"
                    }
                )
                
                StatusRow(
                    label = "Current Pattern",
                    value = pattern?.name ?: "None"
                )
                
                StatusRow(
                    label = "Position",
                    value = "${songState.currentSequencePosition + 1} / ${songState.songMode?.sequence?.size ?: 0}"
                )
                
                StatusRow(
                    label = "Progress",
                    value = "${(navigationState.absoluteStep.toFloat() / navigationState.totalDuration.coerceAtLeast(1) * 100).toInt()}%"
                )
            }
        }
    }
}

@Composable
private fun PatternUsageOverview(
    songMode: SongMode,
    patterns: List<Pattern>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Pattern Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val patternUsage = songMode.sequence
                .groupBy { it.patternId }
                .mapValues { (_, steps) -> steps.sumOf { it.repeatCount } }
            
            patterns.forEach { pattern ->
                val usage = patternUsage[pattern.id] ?: 0
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pattern.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "${usage}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Tabs for song arrangement screen
 */
private enum class SongArrangementTab(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    EDITOR("Editor", Icons.Default.Edit),
    TIMELINE("Timeline", Icons.Default.Timeline),
    OVERVIEW("Overview", Icons.Default.Info)
}