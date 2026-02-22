package com.high.theone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * XY performance pad — maps touch position to two independent axes.
 *
 * @param x           Normalised X value in [0, 1]. 0 = left, 1 = right.
 * @param y           Normalised Y value in [0, 1]. 0 = top, 1 = bottom.
 * @param onValueChange Called with (newX, newY) on touch/drag.
 * @param modifier    Standard modifier.
 * @param size        Square size of the pad.
 * @param padColor    Background colour.
 * @param crosshairColor Crosshair and cursor colour.
 */
@Composable
fun XYPad(
    x: Float,
    y: Float,
    onValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    padColor: Color = Color(0xFF1A1A2E),
    crosshairColor: Color = MaterialTheme.colorScheme.primary
) {
    fun Offset.toXY(w: Float, h: Float) = Pair(
        (this.x / w).coerceIn(0f, 1f),
        (this.y / h).coerceIn(0f, 1f)
    )

    Box(
        modifier = modifier
            .size(size)
            .border(1.dp, crosshairColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (nx, ny) = offset.toXY(this.size.width.toFloat(), this.size.height.toFloat())
                    onValueChange(nx, ny)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val (nx, ny) = change.position.toXY(this.size.width.toFloat(), this.size.height.toFloat())
                    onValueChange(nx, ny)
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // Background
            drawRect(color = padColor)

            // Subtle grid lines
            val gridColor = crosshairColor.copy(alpha = 0.08f)
            for (i in 1..3) {
                val xPos = w * i / 4f
                val yPos = h * i / 4f
                drawLine(gridColor, Offset(xPos, 0f), Offset(xPos, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, yPos), Offset(w, yPos), strokeWidth = 1f)
            }

            // Crosshair at cursor position
            val cx = w * x.coerceIn(0f, 1f)
            val cy = h * y.coerceIn(0f, 1f)
            val hairColor = crosshairColor.copy(alpha = 0.35f)
            drawLine(hairColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
            drawLine(hairColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)

            // Cursor dot
            drawCircle(color = crosshairColor, radius = 8f, center = Offset(cx, cy))
            drawCircle(color = crosshairColor.copy(alpha = 0.3f), radius = 18f, center = Offset(cx, cy))
        }
    }
}
