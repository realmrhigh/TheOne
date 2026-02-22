package com.high.theone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical fader / slider control.
 *
 * @param value         Normalised value in [0, 1]. 0 = bottom, 1 = top.
 * @param onValueChange Called with new value on drag.
 * @param modifier      Standard modifier.
 * @param width         Width of the control.
 * @param height        Height of the control.
 * @param trackColor    Background track colour.
 * @param fillColor     Fill (value) colour.
 * @param thumbColor    Thumb indicator colour.
 */
@Composable
fun VirtualSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 120.dp,
    trackColor: Color = Color.DarkGray,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = Color.White
) {
    var lastY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> lastY = offset.y },
                    onDrag = { change, _ ->
                        val dy = lastY - change.position.y  // dragging up increases value
                        lastY = change.position.y
                        val delta = dy / size.height
                        onValueChange((value + delta).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.width(width).height(height)) {
            val trackW = this.size.width * 0.3f
            val trackX = (this.size.width - trackW) / 2f
            val cornerR = CornerRadius(trackW / 2f)

            // Background track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackX, 0f),
                size = Size(trackW, this.size.height),
                cornerRadius = cornerR
            )

            // Fill (from bottom up to value)
            val fillH = this.size.height * value.coerceIn(0f, 1f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(trackX, this.size.height - fillH),
                size = Size(trackW, fillH),
                cornerRadius = cornerR
            )

            // Thumb
            val thumbW = this.size.width * 0.8f
            val thumbH = this.size.height * 0.07f
            val thumbX = (this.size.width - thumbW) / 2f
            val thumbY = this.size.height * (1f - value.coerceIn(0f, 1f)) - thumbH / 2f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX, thumbY.coerceIn(0f, this.size.height - thumbH)),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbH / 2f)
            )
        }
    }
}
