package com.high.theone.features.sampling

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Interactive tutorial system for first-time users.
 * Provides step-by-step guidance through the sampling workflow.
 * 
 * Requirements: 6.6 (onboarding and help system)
 */

/**
 * Tutorial step data class containing information for each tutorial step.
 */
data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetComponent: String? = null,
    val action: TutorialAction = TutorialAction.None,
    val highlightArea: HighlightArea? = null
)

/**
 * Tutorial actions that can be performed during tutorial steps.
 */
sealed class TutorialAction {
    object None : TutorialAction()
    object TapRecordButton : TutorialAction()
    object TapPad : TutorialAction()
    object OpenSampleBrowser : TutorialAction()
    object ShowPadSettings : TutorialAction()
}

/**
 * Highlight area for tutorial overlay.
 */
data class HighlightArea(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val shape: HighlightShape = HighlightShape.Rectangle
)

enum class HighlightShape {
    Rectangle, Circle
}

/**
 * Main onboarding screen that guides users through the app features.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(0) }
    val tutorialSteps = remember { createTutorialSteps() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Main content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Welcome header
            if (currentStep == 0) {
                WelcomeHeader()
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Tutorial step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) with slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300)
                    )
                },
                label = "tutorial_step_transition"
            ) { step ->
                if (step < tutorialSteps.size) {
                    TutorialStepContent(
                        step = tutorialSteps[step],
                        stepNumber = step + 1,
                        totalSteps = tutorialSteps.size
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Navigation buttons
            TutorialNavigationButtons(
                currentStep = currentStep,
                totalSteps = tutorialSteps.size,
                onNext = { 
                    if (currentStep < tutorialSteps.size - 1) {
                        currentStep++
                    } else {
                        onComplete()
                    }
                },
                onPrevious = { 
                    if (currentStep > 0) {
                        currentStep--
                    }
                },
                onSkip = onSkip
            )
        }
        
        // Progress indicator
        TutorialProgressIndicator(
            currentStep = currentStep,
            totalSteps = tutorialSteps.size,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )
    }
}

/**
 * Interactive tutorial overlay that highlights specific UI components.
 */
@Composable
fun InteractiveTutorial(
    isVisible: Boolean,
    currentStep: TutorialStep,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = { /* Prevent dismissal */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                // Highlight area
                currentStep.highlightArea?.let { highlight ->
                    HighlightOverlay(
                        highlightArea = highlight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Tutorial tooltip
                TutorialTooltip(
                    step = currentStep,
                    onNext = onNext,
                    onSkip = onSkip,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                )
            }
        }
    }
}

/**
 * Welcome header for the onboarding screen.
 */
@Composable
private fun WelcomeHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App icon or logo placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "TheOne App Icon",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Text(
            text = "Welcome to TheOne",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Your mobile MPC-style sampler and drum machine",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Individual tutorial step content display.
 */
@Composable
private fun TutorialStepContent(
    step: TutorialStep,
    stepNumber: Int,
    totalSteps: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Step title
            Text(
                text = step.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            // Step description
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2
            )
            
            // Step counter
            Text(
                text = "Step $stepNumber of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Navigation buttons for tutorial progression.
 */
@Composable
private fun TutorialNavigationButtons(
    currentStep: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        if (currentStep > 0) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Previous")
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Skip button
        TextButton(onClick = onSkip) {
            Text("Skip Tutorial")
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Next/Finish button
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (currentStep == totalSteps - 1) "Get Started" else "Next")
            if (currentStep < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Progress indicator showing tutorial completion.
 */
@Composable
private fun TutorialProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isCompleted = index <= currentStep
            val isActive = index == currentStep
            
            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

/**
 * Highlight overlay for interactive tutorial.
 */
@Composable
private fun HighlightOverlay(
    highlightArea: HighlightArea,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "highlight_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlight_pulse_alpha"
    )
    
    Box(
        modifier = modifier
    ) {
        // Highlight border
        Box(
            modifier = Modifier
                .offset(
                    x = highlightArea.x.dp,
                    y = highlightArea.y.dp
                )
                .size(
                    width = highlightArea.width.dp,
                    height = highlightArea.height.dp
                )
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                    shape = if (highlightArea.shape == HighlightShape.Circle) {
                        CircleShape
                    } else {
                        RoundedCornerShape(8.dp)
                    }
                )
        )
    }
}

/**
 * Tutorial tooltip with step information and navigation.
 */
@Composable
private fun TutorialTooltip(
    step: TutorialStep,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }
                
                Button(onClick = onNext) {
                    Text("Got it")
                }
            }
        }
    }
}

/**
 * Create the list of tutorial steps.
 */
private fun createTutorialSteps(): List<TutorialStep> {
    return listOf(
        TutorialStep(
            id = "welcome",
            title = "Welcome to TheOne",
            description = "TheOne is your mobile MPC-style sampler and drum machine. Let's learn how to create beats and record samples!",
            icon = Icons.Default.MusicNote
        ),
        TutorialStep(
            id = "recording",
            title = "Recording Samples",
            description = "Tap the record button to start recording audio from your microphone. You can record any sound - drums, vocals, instruments, or environmental sounds.",
            icon = Icons.Default.Mic,
            action = TutorialAction.TapRecordButton
        ),
        TutorialStep(
            id = "pad_grid",
            title = "Drum Pad Grid",
            description = "The 4x4 pad grid is where you'll trigger your samples. Each pad can hold one sample and has its own volume, pan, and playback settings.",
            icon = Icons.Default.GridView,
            action = TutorialAction.TapPad
        ),
        TutorialStep(
            id = "sample_assignment",
            title = "Assigning Samples",
            description = "Long-press any pad to assign a sample to it. You can choose from recorded samples or load audio files from your device.",
            icon = Icons.Default.Assignment,
            action = TutorialAction.OpenSampleBrowser
        ),
        TutorialStep(
            id = "pad_settings",
            title = "Pad Configuration",
            description = "Each pad has individual settings for volume, pan, and playback mode. Access these by long-pressing a pad with an assigned sample.",
            icon = Icons.Default.Settings,
            action = TutorialAction.ShowPadSettings
        ),
        TutorialStep(
            id = "velocity_sensitivity",
            title = "Velocity Sensitivity",
            description = "Pads respond to how hard you tap them. Tap lightly for quiet sounds, or tap harder for louder, more intense playback.",
            icon = Icons.Default.TouchApp
        ),
        TutorialStep(
            id = "sample_editing",
            title = "Sample Editing",
            description = "Edit your samples by trimming unwanted parts, adjusting volume, or applying basic effects. Access the editor from the sample browser.",
            icon = Icons.Default.Edit
        ),
        TutorialStep(
            id = "ready_to_create",
            title = "Ready to Create!",
            description = "You're all set! Start by recording your first sample or loading some audio files. Experiment with different sounds and create your own beats.",
            icon = Icons.Default.PlayArrow
        )
    )
}