package com.high.theone.features.sampling

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * Contextual help system with tooltips and feature discovery.
 * Provides just-in-time help and tips for users.
 * 
 * Requirements: 6.6 (contextual help and tooltips, feature discovery)
 */

/**
 * Help content data class for different UI components.
 */
data class HelpContent(
    val title: String,
    val description: String,
    val tips: List<String> = emptyList(),
    val icon: ImageVector? = null
)

/**
 * Feature tip data class for discovery system.
 */
data class FeatureTip(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val priority: TipPriority = TipPriority.Medium,
    val triggerCondition: TipTriggerCondition = TipTriggerCondition.Always
)

enum class TipPriority {
    Low, Medium, High, Critical
}

sealed class TipTriggerCondition {
    object Always : TipTriggerCondition()
    object FirstTime : TipTriggerCondition()
    data class AfterAction(val actionCount: Int) : TipTriggerCondition()
    data class WhenIdle(val idleTimeMs: Long) : TipTriggerCondition()
}

/**
 * Contextual tooltip that appears near UI components.
 */
@Composable
fun ContextualTooltip(
    content: HelpContent,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placement: TooltipPlacement = TooltipPlacement.Bottom
) {
    if (isVisible) {
        Popup(
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(200)) + 
                       scaleIn(animationSpec = tween(200, easing = EaseOutBack)),
                exit = fadeOut(animationSpec = tween(150)) + 
                      scaleOut(animationSpec = tween(150))
            ) {
                TooltipCard(
                    content = content,
                    onDismiss = onDismiss,
                    modifier = modifier
                )
            }
        }
    }
}

/**
 * Help button that shows contextual help when tapped.
 */
@Composable
fun HelpButton(
    content: HelpContent,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Small
) {
    var showTooltip by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(
            onClick = { showTooltip = true },
            modifier = Modifier.size(
                when (size) {
                    ButtonSize.Small -> 24.dp
                    ButtonSize.Medium -> 32.dp
                    ButtonSize.Large -> 40.dp
                }
            )
        ) {
            Icon(
                imageVector = Icons.Default.Help,
                contentDescription = "Help",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(
                    when (size) {
                        ButtonSize.Small -> 16.dp
                        ButtonSize.Medium -> 20.dp
                        ButtonSize.Large -> 24.dp
                    }
                )
            )
        }
        
        ContextualTooltip(
            content = content,
            isVisible = showTooltip,
            onDismiss = { showTooltip = false }
        )
    }
}

enum class ButtonSize {
    Small, Medium, Large
}

enum class TooltipPlacement {
    Top, Bottom, Left, Right
}

/**
 * Feature discovery system that shows tips and hints.
 */
@Composable
fun FeatureDiscovery(
    tips: List<FeatureTip>,
    onTipShown: (String) -> Unit,
    onTipDismissed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTip by remember { mutableStateOf<FeatureTip?>(null) }
    var showTip by remember { mutableStateOf(false) }
    
    // Logic to determine which tip to show
    LaunchedEffect(tips) {
        val tipToShow = tips.firstOrNull { tip ->
            when (tip.triggerCondition) {
                is TipTriggerCondition.Always -> true
                is TipTriggerCondition.FirstTime -> true // TODO: Check if shown before
                is TipTriggerCondition.AfterAction -> true // TODO: Check action count
                is TipTriggerCondition.WhenIdle -> true // TODO: Check idle time
            }
        }
        
        if (tipToShow != null && currentTip?.id != tipToShow.id) {
            delay(1000) // Wait a bit before showing tip
            currentTip = tipToShow
            showTip = true
            onTipShown(tipToShow.id)
        }
    }
    
    currentTip?.let { tip ->
        FeatureTipPopup(
            tip = tip,
            isVisible = showTip,
            onDismiss = {
                showTip = false
                onTipDismissed(tip.id)
                currentTip = null
            },
            modifier = modifier
        )
    }
}

/**
 * Quick help panel that can be toggled on/off.
 */
@Composable
fun QuickHelpPanel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Help",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close help"
                        )
                    }
                }
                
                // Help sections
                QuickHelpSection(
                    title = "Recording",
                    items = listOf(
                        "Tap the record button to start recording",
                        "Watch the level meter to avoid clipping",
                        "Recording stops automatically after 30 seconds"
                    ),
                    icon = Icons.Default.Mic
                )
                
                QuickHelpSection(
                    title = "Drum Pads",
                    items = listOf(
                        "Tap pads to trigger samples",
                        "Tap harder for louder playback",
                        "Long-press to configure pad settings"
                    ),
                    icon = Icons.Default.GridView
                )
                
                QuickHelpSection(
                    title = "Sample Management",
                    items = listOf(
                        "Assign samples by long-pressing empty pads",
                        "Edit samples in the sample browser",
                        "Adjust volume and pan for each pad"
                    ),
                    icon = Icons.Default.LibraryMusic
                )
            }
        }
    }
}

/**
 * Tooltip card component with content and dismiss action.
 */
@Composable
private fun TooltipCard(
    content: HelpContent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.widthIn(max = 300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Description
            Text(
                text = content.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Tips
            if (content.tips.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Tips:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    content.tips.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature tip popup for discovery system.
 */
@Composable
private fun FeatureTipPopup(
    tip: FeatureTip,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (tip.priority) {
                        TipPriority.Critical -> MaterialTheme.colorScheme.errorContainer
                        TipPriority.High -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tip.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Text(
                            text = tip.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Description
                    Text(
                        text = tip.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                    
                    // Dismiss button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Got it")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick help section component.
 */
@Composable
private fun QuickHelpSection(
    title: String,
    items: List<String>,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Section items
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Predefined help content for common UI components.
 */
object HelpContent {
    val recordButton = HelpContent(
        title = "Record Button",
        description = "Tap to start/stop recording audio from your microphone.",
        tips = listOf(
            "Make sure your microphone permissions are enabled",
            "Watch the level meter to avoid clipping",
            "Recording automatically stops after 30 seconds"
        ),
        icon = Icons.Default.Mic
    )
    
    val padGrid = HelpContent(
        title = "Drum Pad Grid",
        description = "Trigger samples by tapping the pads. Each pad can hold one sample.",
        tips = listOf(
            "Tap harder for louder playback (velocity sensitivity)",
            "Long-press to configure pad settings",
            "Empty pads show 'Empty' - long-press to assign samples"
        ),
        icon = Icons.Default.GridView
    )
    
    val levelMeter = HelpContent(
        title = "Level Meter",
        description = "Shows the input level while recording to help avoid clipping.",
        tips = listOf(
            "Keep levels in the green/yellow range",
            "Red indicates clipping - move away from mic or lower input",
            "Peak shows maximum level, Avg shows average level"
        ),
        icon = Icons.Default.BarChart
    )
    
    val sampleBrowser = HelpContent(
        title = "Sample Browser",
        description = "Browse, preview, and manage your audio samples.",
        tips = listOf(
            "Tap samples to preview them",
            "Use search to find specific samples",
            "Import audio files from your device"
        ),
        icon = Icons.Default.LibraryMusic
    )
}