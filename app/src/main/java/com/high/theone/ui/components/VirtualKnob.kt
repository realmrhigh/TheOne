package com.high.theone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Rotary knob control.
 *
 * @param value      Current normalised value in [0, 1].
 * @param onValueChange Called whenever the user drags the knob.
 * @param modifier   Standard modifier.
 * @param size       Overall diameter of the knob.
 * @param label      Optional accessibility description (unused in drawing; add a Text above/below).
 * @param trackColor  Arc background colour.
 * @param fillColor   Arc fill colour (value indicator).
 * @param knobColor   Centre circle colour.
 */
@Composable
fun VirtualKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    label: String = "",
    trackColor: Color = Color.DarkGray,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    knobColor: Color = MaterialTheme.colorScheme.surface
) {
    // The knob sweeps from -135° to +135° (270° range), starting at 7 o'clock.
    val startAngleDeg = 135f   // angle in standard Compose canvas coords (0 = right, clockwise)
    val sweepRangeDeg = 270f

    var dragAccum by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> lastY = offset.y },
                    onDrag = { change, _ ->
                        val dy = lastY - change.position.y  // up = increase
                        lastY = change.position.y
                        dragAccum += dy / (size.toPx() * 2f)
                        val newVal = (value + dragAccum).coerceIn(0f, 1f)
                        dragAccum = 0f
                        onValueChange(newVal)
                    }
                )
            }
    ) {
        val strokeWidthDp = size * 0.1f
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidthDp.toPx(), cap = StrokeCap.Round)
            val padding = strokeWidthDp.toPx() / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - padding * 2,
                this.size.height - padding * 2
            )
            val topLeft = Offset(padding, padding)

            // Background track arc
            drawArc(
                color = trackColor,
                startAngle = startAngleDeg,
                sweepAngle = sweepRangeDeg,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // Value fill arc
            val fillSweep = sweepRangeDeg * value.coerceIn(0f, 1f)
            drawArc(
                color = fillColor,
                startAngle = startAngleDeg,
                sweepAngle = fillSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // Centre knob circle
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val knobRadius = this.size.width * 0.28f
            drawCircle(color = knobColor, radius = knobRadius, center = centre)

            // Indicator line on the knob
            val angleDeg = startAngleDeg + fillSweep
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val lineStart = centre
            val lineEnd = Offset(
                centre.x + (knobRadius * 0.6f * cos(angleRad)).toFloat(),
                centre.y + (knobRadius * 0.6f * sin(angleRad)).toFloat()
            )
            drawLine(
                color = fillColor,
                start = lineStart,
                end = lineEnd,
                strokeWidth = strokeWidthDp.toPx() * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}
