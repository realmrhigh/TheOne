package com.high.theone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A note placed on the piano roll.
 *
 * @param pitch  MIDI pitch value (0–127).
 * @param startStep  Zero-based step index where the note begins.
 * @param durationSteps  How many steps the note lasts (≥ 1).
 * @param velocity  MIDI velocity (0–127).
 */
data class PianoRollNote(
    val pitch: Int,
    val startStep: Int,
    val durationSteps: Int = 1,
    val velocity: Int = 100
)

/**
 * Piano roll editor — shows a scrollable grid of steps × pitches.
 *
 * The left-hand keyboard strip is always visible; the note grid scrolls
 * both horizontally (steps) and vertically (pitches) independently,
 * sharing a single verticalScrollState so they stay in sync.
 *
 * @param notes           Current note list.
 * @param onNoteAdded     Called when the user taps an empty cell.
 * @param onNoteRemoved   Called when the user taps an existing note.
 * @param steps           Total number of step columns.
 * @param lowestPitch     Lowest MIDI pitch shown (bottom of grid).
 * @param highestPitch    Highest MIDI pitch shown (top of grid).
 * @param stepWidth       Width of each step column.
 * @param rowHeight       Height of each pitch row.
 * @param modifier        Standard modifier applied to the outer container.
 * @param gridBackground  Background colour of the grid.
 * @param noteColor       Colour used to fill notes.
 * @param blackKeyColor   Colour of black key rows.
 */
@Composable
fun PianoRoll(
    notes: List<PianoRollNote>,
    onNoteAdded: (pitch: Int, step: Int) -> Unit,
    onNoteRemoved: (pitch: Int, step: Int) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 16,
    lowestPitch: Int = 36,   // C2
    highestPitch: Int = 84,  // C6
    stepWidth: Dp = 32.dp,
    rowHeight: Dp = 14.dp,
    gridBackground: Color = Color(0xFF1A1A2E),
    noteColor: Color = Color(0xFF4CAF50),
    blackKeyColor: Color = Color(0xFF0D0D1A)
) {
    val pitchRange = highestPitch downTo lowestPitch   // top-to-bottom
    val totalRows = pitchRange.count()

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val keyboardWidth = 36.dp
    val totalGridHeight = rowHeight * totalRows
    val totalGridWidth = stepWidth * steps

    // Accent colour from theme for grid lines
    val accentColor = MaterialTheme.colorScheme.primary

    Row(modifier = modifier.height(totalGridHeight)) {

        // ── Left keyboard strip ──────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .width(keyboardWidth)
                .fillMaxHeight()
                .verticalScroll(verticalScrollState)
                .height(totalGridHeight)
        ) {
            val rowH = rowHeight.toPx()
            pitchRange.forEachIndexed { rowIndex, pitch ->
                val top = rowIndex * rowH
                val isBlack = isBlackKey(pitch)
                val keyColor = if (isBlack) Color(0xFF222222) else Color(0xFFDDDDDD)
                drawRect(
                    color = keyColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, rowH)
                )
                // Subtle separator
                drawLine(
                    color = Color.Black.copy(alpha = 0.3f),
                    start = Offset(0f, top + rowH),
                    end = Offset(size.width, top + rowH),
                    strokeWidth = 0.5f
                )
            }
        }

        // ── Scrollable note grid ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
                .verticalScroll(verticalScrollState)
                .width(totalGridWidth)
                .height(totalGridHeight)
                .pointerInput(notes, steps, lowestPitch, highestPitch) {
                    detectTapGestures { offset ->
                        val colIndex = (offset.x / stepWidth.toPx()).toInt()
                            .coerceIn(0, steps - 1)
                        val rowIndex = (offset.y / rowHeight.toPx()).toInt()
                            .coerceIn(0, totalRows - 1)
                        val pitch = pitchRange.elementAt(rowIndex)
                        val step = colIndex

                        val existing = notes.firstOrNull {
                            it.pitch == pitch && step >= it.startStep && step < it.startStep + it.durationSteps
                        }
                        if (existing != null) {
                            onNoteRemoved(pitch, existing.startStep)
                        } else {
                            onNoteAdded(pitch, step)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.width(totalGridWidth).height(totalGridHeight)) {
                val rowH = rowHeight.toPx()
                val colW = stepWidth.toPx()

                // Row backgrounds
                pitchRange.forEachIndexed { rowIndex, pitch ->
                    val top = rowIndex * rowH
                    val bg = if (isBlackKey(pitch)) blackKeyColor else gridBackground
                    drawRect(color = bg, topLeft = Offset(0f, top), size = Size(size.width, rowH))
                }

                // Vertical step lines
                for (col in 0..steps) {
                    val x = col * colW
                    val isBeat = col % 4 == 0
                    drawLine(
                        color = accentColor.copy(alpha = if (isBeat) 0.3f else 0.1f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = if (isBeat) 1.5f else 0.5f
                    )
                }

                // Horizontal pitch lines
                for (row in 0..totalRows) {
                    val y = row * rowH
                    drawLine(
                        color = Color.Black.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5f
                    )
                }

                // Notes
                notes.forEach { note ->
                    val rowIndex = pitchRange.indexOf(note.pitch)
                    if (rowIndex < 0) return@forEach
                    val top = rowIndex * rowH + 1f
                    val left = note.startStep * colW + 1f
                    val noteW = (note.durationSteps * colW - 2f).coerceAtLeast(4f)
                    val noteH = rowH - 2f

                    // Body
                    drawRect(
                        color = noteColor.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = Size(noteW, noteH)
                    )
                    // Velocity indicator — small top highlight
                    val velFraction = note.velocity / 127f
                    drawRect(
                        color = Color.White.copy(alpha = 0.3f * velFraction),
                        topLeft = Offset(left, top),
                        size = Size(noteW, (noteH * 0.25f).coerceAtLeast(1f))
                    )
                }
            }
        }
    }
}

/** Returns true if the given MIDI pitch corresponds to a black key. */
private fun isBlackKey(pitch: Int): Boolean {
    return when (pitch % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }
}
