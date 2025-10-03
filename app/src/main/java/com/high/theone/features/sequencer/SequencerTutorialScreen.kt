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
 * Interactive tutorial screen for the sequencer
 * Requirements: 9.7 - tutorial integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerTutorialScreen(
    navController: NavHostController
) {
    var currentStep by remember { mutableStateOf(0) }
    val tutorialSteps = getTutorialSteps()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Sequencer Tutorial (${currentStep + 1}/${tutorialSteps.size})")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            TutorialNavigationBar(
                currentStep = currentStep,
                totalSteps = tutorialSteps.size,
                onPrevious = { if (currentStep > 0) currentStep-- },
                onNext = { if (currentStep < tutorialSteps.size - 1) currentStep++ },
                onFinish = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = (currentStep + 1).toFloat() / tutorialSteps.size,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Current tutorial step
            TutorialStepContent(tutorialSteps[currentStep])
        }
    }
}

/**
 * Tutorial step data class
 */
data class TutorialStep(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val content: String,
    val tips: List<String> = emptyList()
)

/**
 * Get all tutorial steps
 */
private fun getTutorialSteps(): List<TutorialStep> = listOf(
    TutorialStep(
        title = "Welcome to the Sequencer",
        description = "Learn how to create amazing beats with the step sequencer",
        icon = Icons.Default.PlayArrow,
        content = """
            The step sequencer is your gateway to creating professional drum patterns and beats. 
            
            In this tutorial, you'll learn:
            • How to program drum patterns
            • Recording techniques
            • Using tempo and swing
            • Creating song arrangements
            
            Let's get started!
        """.trimIndent()
    ),
    
    TutorialStep(
        title = "Loading Samples",
        description = "First, you need drum sounds to work with",
        icon = Icons.Default.AudioFile,
        content = """
            Before creating patterns, you need to load drum samples into the pads:
            
            1. Go to the drum pad screen
            2. Tap a pad to assign a sample
            3. Choose from built-in samples or record your own
            4. Return to the sequencer when ready
            
            Each pad can hold a different drum sound like kick, snare, hi-hat, etc.
        """.trimIndent(),
        tips = listOf(
            "Start with basic drum sounds: kick, snare, hi-hat",
            "You can record your own samples using the microphone",
            "Organize similar sounds on nearby pads"
        )
    ),
    
    TutorialStep(
        title = "Creating Your First Pattern",
        description = "Learn the basics of pattern programming",
        icon = Icons.Default.GridOn,
        content = """
            Now let's create your first drum pattern:
            
            1. Tap the "+" button to create a new pattern
            2. Choose 16 steps for a standard pattern
            3. Give it a name like "Basic Beat"
            4. You'll see a grid with pads on the left and steps across the top
            
            Each row represents a drum sound, each column represents a beat in time.
        """.trimIndent(),
        tips = listOf(
            "16 steps = 1 bar in 4/4 time",
            "Start with simple patterns before getting complex",
            "You can always change the pattern length later"
        )
    ),
    
    TutorialStep(
        title = "Programming Steps",
        description = "Place beats on the grid to create rhythm",
        icon = Icons.Default.TouchApp,
        content = """
            Time to program your beat:
            
            1. Tap step 1 on the kick drum row (usually the bottom pad)
            2. Tap step 5 on the snare drum row
            3. Tap step 9 on the kick drum row again
            4. Tap step 13 on the snare drum row again
            
            You've just created a basic four-on-the-floor pattern!
        """.trimIndent(),
        tips = listOf(
            "Active steps are highlighted in color",
            "Tap again to turn a step off",
            "Long press a step to adjust its volume (velocity)"
        )
    ),
    
    TutorialStep(
        title = "Playing Your Pattern",
        description = "Hear your creation come to life",
        icon = Icons.Default.PlayArrow,
        content = """
            Let's hear your pattern:
            
            1. Press the play button (triangle icon)
            2. Watch the playback indicator move across the steps
            3. Listen to your drum pattern loop
            4. Press stop (square icon) when you want to stop
            
            The pattern will loop continuously until you stop it.
        """.trimIndent(),
        tips = listOf(
            "You can edit steps while the pattern is playing",
            "The current step is highlighted during playback",
            "Use pause to temporarily stop without losing position"
        )
    ),
    
    TutorialStep(
        title = "Adding Hi-Hats",
        description = "Make your beat more interesting with hi-hats",
        icon = Icons.Default.MusicNote,
        content = """
            Let's add some hi-hats to make the beat more interesting:
            
            1. Find the hi-hat pad row (usually near the top)
            2. Tap steps 1, 3, 5, 7, 9, 11, 13, and 15
            3. This creates eighth-note hi-hats
            4. Play the pattern to hear the difference
            
            Now your beat has kick, snare, and hi-hats!
        """.trimIndent(),
        tips = listOf(
            "Hi-hats on every other step create steady rhythm",
            "Try different hi-hat patterns for variety",
            "You can use different hi-hat sounds (open/closed)"
        )
    ),
    
    TutorialStep(
        title = "Adjusting Tempo and Swing",
        description = "Control the feel and speed of your beat",
        icon = Icons.Default.Speed,
        content = """
            Fine-tune your pattern's feel:
            
            1. Use the tempo slider to speed up or slow down
            2. Try tempos between 80-140 BPM for different styles
            3. Adjust the swing slider to add groove
            4. Small swing amounts (10-20%) add subtle feel
            
            Experiment to find the perfect groove for your beat.
        """.trimIndent(),
        tips = listOf(
            "120 BPM is a good starting tempo",
            "Swing makes beats feel less robotic",
            "Different genres prefer different tempos and swing amounts"
        )
    ),
    
    TutorialStep(
        title = "Recording in Real-Time",
        description = "Capture natural timing by playing live",
        icon = Icons.Default.FiberManualRecord,
        content = """
            Try recording a pattern by playing live:
            
            1. Create a new empty pattern
            2. Press the record button (red circle)
            3. Press play to start recording
            4. Play the pads in time with the beat
            5. Press stop when finished
            
            Your performance will be automatically quantized to the grid.
        """.trimIndent(),
        tips = listOf(
            "Use the metronome for better timing",
            "Don't worry about perfect timing - quantization helps",
            "You can overdub additional parts on top"
        )
    ),
    
    TutorialStep(
        title = "Pattern Variations",
        description = "Create multiple patterns for song sections",
        icon = Icons.Default.ContentCopy,
        content = """
            Create variations of your pattern:
            
            1. Duplicate your current pattern
            2. Rename it (e.g., "Basic Beat - Fill")
            3. Add or remove some steps to create variation
            4. Try adding extra kicks or snare hits
            
            Different patterns can represent verse, chorus, bridge, etc.
        """.trimIndent(),
        tips = listOf(
            "Small changes can make big differences",
            "Save the original before making changes",
            "Create fills by adding extra hits in the last few steps"
        )
    ),
    
    TutorialStep(
        title = "Song Mode",
        description = "Chain patterns together for complete songs",
        icon = Icons.Default.QueueMusic,
        content = """
            Arrange your patterns into a complete song:
            
            1. Switch to the Song Mode tab
            2. Drag your patterns into the arrangement
            3. Set how many times each pattern repeats
            4. Press play to hear the full arrangement
            
            This lets you create verse-chorus-verse structures and more.
        """.trimIndent(),
        tips = listOf(
            "Plan your song structure before arranging",
            "Use different patterns for different song sections",
            "You can loop the entire song arrangement"
        )
    ),
    
    TutorialStep(
        title = "Congratulations!",
        description = "You've completed the sequencer tutorial",
        icon = Icons.Default.CheckCircle,
        content = """
            Great job! You now know the basics of using the step sequencer:
            
            ✓ Loading samples into pads
            ✓ Creating and programming patterns
            ✓ Using tempo and swing
            ✓ Recording in real-time
            ✓ Creating pattern variations
            ✓ Arranging songs
            
            Keep experimenting and have fun making beats!
        """.trimIndent(),
        tips = listOf(
            "Practice makes perfect - keep experimenting",
            "Listen to your favorite songs for pattern ideas",
            "Don't be afraid to try unconventional patterns",
            "Check the help section for more advanced techniques"
        )
    )
)

/**
 * Tutorial step content display
 */
@Composable
private fun TutorialStepContent(step: TutorialStep) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Main content
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = step.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
            )
        }
        
        // Tips section
        if (step.tips.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Tips",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    step.tips.forEach { tip ->
                        Text(
                            text = "• $tip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tutorial navigation bar
 */
@Composable
private fun TutorialNavigationBar(
    currentStep: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button
            OutlinedButton(
                onClick = onPrevious,
                enabled = currentStep > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Previous")
            }
            
            // Step indicator
            Text(
                text = "${currentStep + 1} of $totalSteps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Next/Finish button
            if (currentStep < totalSteps - 1) {
                Button(onClick = onNext) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            } else {
                Button(onClick = onFinish) {
                    Text("Finish")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
            }
        }
    }
}