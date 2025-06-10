package com.example.theone.features.sequencer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.theone.model.Event
import com.example.theone.model.EventType

// Number of steps in the sequencer grid (e.g., 16 for one bar of 16th notes)
const val NUM_STEPS = 16
// Number of pads/rows in the sequencer grid (e.g., 8 common drum machine pads)
const val NUM_PADS_ROWS = 8 // Example, can be configured

@Composable
fun StepSequencerScreen(
    navController: NavController, // Added NavController
    sequencerViewModel: SequencerViewModel = hiltViewModel()
) {
    // Observe events from the ViewModel (will be needed later for display)
    // For now, we'll focus on the layout and clickable cells.
    // val events by sequencerViewModel.getEventsForCurrentSequence().collectAsState() // Assuming getEvents returns a Flow

    // Get the current sequence from the ViewModel (or a default one for now)
    // This would typically be selected by the user.
    // For this initial UI, we assume a sequence is loaded or use placeholders.
    val currentSequence by remember {
        mutableStateOf(sequencerViewModel.getEventsForCurrentSequence()) // This gets a List<Event>
    }


    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Step Sequencer") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(8.dp)
                .fillMaxSize()
        ) {
            // Grid for the step sequencer
            // Rows: Pads (e.g., Kick, Snare, HiHat)
            // Columns: Steps (e.g., 1 to 16)
            LazyVerticalGrid(
                columns = GridCells.Fixed(NUM_STEPS),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(NUM_PADS_ROWS * NUM_STEPS) { index ->
                    val padRow = index / NUM_STEPS // Determines which pad/row (0 to NUM_PADS_ROWS-1)
                    val stepCol = index % NUM_STEPS  // Determines which step (0 to NUM_STEPS-1)

                    // Determine if an event exists at this pad and step
                    // This requires querying `currentSequence.events`
                    val eventExists = currentSequence.any { event ->
                        event.type is EventType.PadTrigger &&
                        (event.type as EventType.PadTrigger).padId == "Pad$padRow" && // Assuming padId like "Pad0", "Pad1"
                        (event.startTimeTicks / (sequencerViewModel.ticksPer16thNote)) == stepCol.toLong() // Assuming ticksPer16thNote is accessible
                    }

                    StepCell(
                        padId = "Pad$padRow", // Example padId
                        step = stepCol,
                        isEventPresent = eventExists,
                        onCellClick = { pad, step ->
                            // Logic to add or remove event
                            // This will call a method in SequencerViewModel
                            println("Cell clicked: Pad $pad, Step $step. Event present: $eventExists")
                            // sequencerViewModel.toggleEventAt(pad, step) // Future method
                            // For now, let's make it call recordPadTrigger as a test if no event,
                            // or a conceptual removeEvent if event exists.
                            if (eventExists) {
                                // sequencerViewModel.removeEventAt(pad, step) // Conceptual
                            } else {
                                // Calculate startTimeTicks for this step
                                val startTimeTicks = step * sequencerViewModel.ticksPer16thNote // Assuming public access or getter
                                // sequencerViewModel.addPadTriggerEvent(pad, 127, startTimeTicks) // Conceptual
                                 sequencerViewModel.recordPadTrigger(padId = pad, velocity = 100) // Using existing for now
                                // Note: recordPadTrigger uses "current time", not step-based time.
                                // This interaction will need refinement.
                            }
                        }
                    )
                }
            }

            // Placeholder for transport controls (Play, Stop, BPM, etc.) - M2.5
            Spacer(Modifier.height(16.dp))
            TransportBar(sequencerViewModel = sequencerViewModel)

            Spacer(Modifier.height(16.dp))
            Button(onClick = { navController.navigate("drum_pad_screen") }) {
                Text("Edit Drum Sounds / Pads")
            }
        }
    }
}

@Composable
fun StepCell(
    padId: String,
    step: Int,
    isEventPresent: Boolean,
    onCellClick: (padId: String, step: Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth() // Fill the grid cell
            .aspectRatio(1f) // Make it square
            .background(if (isEventPresent) MaterialTheme.colors.primaryVariant else MaterialTheme.colors.surface)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f))
            .clickable { onCellClick(padId, step) },
        contentAlignment = Alignment.Center
    ) {
        // Optionally, display something in the cell, like the step number or a dot
        // Text(text = "$step", style = MaterialTheme.typography.caption)
    }
}

// Dummy data for SequencerViewModel.ticksPer16thNote for preview if needed
// In real usage, it's part of the ViewModel
private val previewTicksPer16thNote: Long = 24

// Need to make ticksPer16thNote accessible from SequencerViewModel for the grid calculation.
// Let's assume it's made public or has a getter in SequencerViewModel.
// For the subtask, this detail might be overlooked by the subtask executor if not explicitly stated
// to modify SequencerViewModel.
// For now, the code in StepSequencerScreen will assume `sequencerViewModel.ticksPer16thNote` is accessible.
// If SequencerViewModel.kt needs modification for this, it should be part of this step.

// Add public accessor for ticksPer16thNote in SequencerViewModel.kt
// (This should be part of the subtask instructions for SequencerViewModel.kt)
// Example: `val ticksPer16thNote: Long = 24` (already there but private)
// Change to: `val ticksPer16thNote: Long get() = _ticksPer16thNote` with a private backing field,
// or just make it `internal val ticksPer16thNote: Long = 24` for module-wide access.
// Or simply make it public if no encapsulation is strictly needed for this constant.
// For simplicity in the subtask: "Ensure `ticksPer16thNote` in `SequencerViewModel` is accessible from `StepSequencerScreen.kt`."
