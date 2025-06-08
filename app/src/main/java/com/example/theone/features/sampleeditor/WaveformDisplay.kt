package com.example.theone.features.sampleeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.theone.model.Sample
import kotlin.math.abs

@Composable
fun WaveformDisplay(
    sample: Sample?,
    modifier: Modifier = Modifier,
    waveformColor: Color = Color.Green,
    strokeWidth: Float = 2f,
    backgroundColor: Color = Color.Black
) {
    Canvas(modifier = modifier.height(100.dp).fillMaxWidth()) {
        drawRect(color = backgroundColor) // Draw background

        if (sample == null || sample.audioData.isEmpty()) {
            // Optionally draw a "No sample loaded" or "Empty waveform" message
            return@Canvas
        }

        val audioData = sample.audioData
        val numSamples = audioData.size
        val canvasWidth = size.width
        val canvasHeight = size.height
        val middleY = canvasHeight / 2f

        // Number of groups of samples to average for each pixel column.
        // Adjust for performance vs detail.
        val samplesPerPixel = (numSamples / canvasWidth).coerceAtLeast(1f)

        var currentX = 0f
        var segmentIndex = 0

        while (currentX < canvasWidth && (segmentIndex * samplesPerPixel).toInt() < numSamples) {
            val startIndex = (segmentIndex * samplesPerPixel).toInt()
            val endIndex = ((segmentIndex + 1) * samplesPerPixel).toInt().coerceAtMost(numSamples)

            if (startIndex >= endIndex) { // Should not happen if samplesPerPixel >= 1
                currentX += 1f
                segmentIndex++
                continue
            }

            var minVal = 0f // audioData is normalized [-1, 1], so min can be -1
            var maxVal = 0f // max can be 1

            // Find min and max in the current segment
            // Initialize with the first sample in the segment
            if (startIndex < numSamples) {
                minVal = audioData[startIndex]
                maxVal = audioData[startIndex]
            }

            for (i in startIndex until endIndex) {
                if (audioData[i] < minVal) minVal = audioData[i]
                if (audioData[i] > maxVal) maxVal = audioData[i]
            }

            // Normalize to canvas height and draw line
            // Y = 0 is top, Y = canvasHeight is bottom
            // Audio: -1.0 (bottom), 0.0 (middle), 1.0 (top)
            val yMin = middleY - (maxVal * middleY) // maxVal is positive, goes upwards from middle
            val yMax = middleY - (minVal * middleY) // minVal is negative, goes downwards from middle
                                                  // (or positive if signal is all positive)

            drawLine(
                color = waveformColor,
                start = Offset(currentX, yMin.coerceIn(0f, canvasHeight)),
                end = Offset(currentX, yMax.coerceIn(0f, canvasHeight)),
                strokeWidth = strokeWidth
            )
            currentX += 1f // Move to the next pixel column
            segmentIndex++
        }
    }
}
