package com.example.theone.features.drumtrack.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.theone.model.SynthModels.EffectSetting
import com.example.theone.model.SynthModels.EnvelopeSettings
import com.example.theone.model.SynthModels.LFOSettings
import com.example.theone.model.SynthModels.ModulationRouting
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Dummy default for previewing if needed
val DefaultEnvelopeSettingsForPreview = EnvelopeSettings(
    attackMs = 100f,
    decayMs = 150f,
    sustainLevel = 0.7f,
    releaseMs = 200f
    // Assuming other fields like type, holdMs have defaults in EnvelopeSettings constructor
)

@Composable
fun VisualEnvelopeEditor(
    modifier: Modifier = Modifier,
    envelopeSettings: EnvelopeSettings,
    onSettingsChange: (EnvelopeSettings) -> Unit, // Will be used later for drag
    lineColor: Color = Color.Blue, // MaterialTheme.colorScheme.primary would be better
    pointColor: Color = Color.Red  // MaterialTheme.colorScheme.secondary
) {
    // TODO: Later, use colors from MaterialTheme.colorScheme (e.g., MaterialTheme.colorScheme.primary)

    var activeDragPointIndex by remember { mutableStateOf<Int?>(null) }
    val pointRadiusDp = 8.dp // Normal radius
    val draggedPointRadiusDp = 12.dp // Radius when dragged

    val hitTestRadiusPx = 15.dp.toPx() // Larger radius for easier touch/drag initiation
    val minPixelSeparationForTime = 10f // Minimum pixel separation between time points (p1.x, p2.x, p3.x, p4.x)


    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp) // Default height, can be overridden
            .pointerInput(envelopeSettings) { // Pass envelopeSettings to re-trigger pointerInput if settings change externally
                detectDragGestures(
                    onDragStart = { startOffset ->
                        // Hit testing for draggable points
                        // Points order for dragging: P1 (Attack), P2 (Decay/Sustain), P3 (Release Start)
                        // P4 is determined by P3 and release time.
                        // For this implementation, P1, P2, P3 are directly draggable for their primary role.
                        // P0 is fixed.

                        // Calculate current point positions based on envelopeSettings for hit testing
                        // This calculation is duplicated from the drawing logic below, refactor if it becomes too unwieldy
                        val tempCanvasWidth = size.width
                        val tempCanvasHeight = size.height
                        val tempAttackSec = max(0.001f, envelopeSettings.attackMs / 1000f)
                        val tempDecaySec = max(0.001f, envelopeSettings.decayMs / 1000f)
                        val tempSustainLevel = envelopeSettings.sustainLevel.coerceIn(0f, 1f)
                        val tempReleaseSec = max(0.001f, envelopeSettings.releaseMs / 1000f)
                        val tempSustainVisualDurationSec = max(0.05f, tempDecaySec * 0.3f + tempAttackSec * 0.2f)
                        val tempTotalEnvelopeTimeSec = tempAttackSec + tempDecaySec + tempSustainVisualDurationSec + tempReleaseSec
                        val tempTimeScale = if (tempTotalEnvelopeTimeSec > 0.01f) tempCanvasWidth / tempTotalEnvelopeTimeSec else tempCanvasWidth


                        val p1 = Offset(tempAttackSec * tempTimeScale, 0f)
                        val p2 = Offset(p1.x + (tempDecaySec * tempTimeScale), tempCanvasHeight * (1f - tempSustainLevel))
                        val p3 = Offset(p2.x + (tempSustainVisualDurationSec * tempTimeScale), p2.y)
                        // p4 is not directly dragged, its position depends on p3 and release time.

                        val points = listOf(p1, p2, p3)
                        var closestPointIndex: Int? = null
                        var minDistance = Float.MAX_VALUE

                        points.forEachIndexed { index, point ->
                            val distance = (startOffset - point).getDistance()
                            if (distance < hitTestRadiusPx && distance < minDistance) {
                                minDistance = distance
                                closestPointIndex = index
                            }
                        }
                        activeDragPointIndex = closestPointIndex
                    },
                    onDrag = { change, dragAmount ->
                        activeDragPointIndex?.let { pointIndex ->
                            var newAttackMs = envelopeSettings.attackMs
                            var newDecayMs = envelopeSettings.decayMs
                            var newSustainLevel = envelopeSettings.sustainLevel
                            var newReleaseMs = envelopeSettings.releaseMs

                            // Current values in seconds for calculations
                            val currentAttackSec = max(0.001f, newAttackMs / 1000f)
                            val currentDecaySec = max(0.001f, newDecayMs / 1000f)
                            val currentSustainVisualSec = max(0.05f, currentDecaySec * 0.3f + currentAttackSec * 0.2f)
                            val currentReleaseSec = max(0.001f, newReleaseMs / 1000f)
                            val currentTotalEnvelopeTimeSec = currentAttackSec + currentDecaySec + currentSustainVisualSec + currentReleaseSec
                            val currentTimeScale = if (currentTotalEnvelopeTimeSec > 0.01f) size.width / currentTotalEnvelopeTimeSec else size.width


                            // Calculate current point positions based on *current* envelopeSettings
                            // These are the reference points before this specific dragAmount is applied
                            val p1_x_current = currentAttackSec * currentTimeScale
                            val p2_x_current = p1_x_current + (currentDecaySec * currentTimeScale)
                            val p2_y_current = size.height * (1f - newSustainLevel)
                            val p3_x_current = p2_x_current + (currentSustainVisualSec * currentTimeScale)


                            when (pointIndex) {
                                0 -> { // Dragging P1 (Attack time)
                                    val draggedX = p1_x_current + change.position.x - change.previousPosition.x
                                    val constrainedX = draggedX.coerceIn(0f, (p2_x_current - minPixelSeparationForTime).coerceAtLeast(0f))
                                    newAttackMs = (constrainedX / currentTimeScale * 1000f).coerceAtLeast(1f) // Min 1ms
                                }
                                1 -> { // Dragging P2 (Decay time and Sustain level)
                                    // X Position for Decay
                                    val draggedX = p2_x_current + change.position.x - change.previousPosition.x
                                    val constrainedX = draggedX.coerceIn((p1_x_current + minPixelSeparationForTime), (p3_x_current - minPixelSeparationForTime).coerceAtLeast(p1_x_current + minPixelSeparationForTime))
                                    newDecayMs = ((constrainedX - p1_x_current) / currentTimeScale * 1000f).coerceAtLeast(1f)

                                    // Y Position for Sustain
                                    val draggedY = p2_y_current + change.position.y - change.previousPosition.y
                                    val constrainedY = draggedY.coerceIn(0f, size.height)
                                    newSustainLevel = (1f - (constrainedY / size.height)).coerceIn(0f, 1f)
                                }
                                2 -> { // Dragging P3 (Release time, by moving its X position)
                                    // This affects the start of the release phase. The duration of sustain visual part changes.
                                    // Or, more intuitively, this drag should change the Release time itself.
                                    // Let's make P3 dragging affect Release time. P3 is end of sustain.
                                    // new_p3_x = p2_x + new_sustain_visual_duration_sec * timeScale
                                    // new_p4_x = new_p3_x + new_release_sec * timeScale
                                    // If we drag P3's X, we are changing the "sustain visual duration" or effectively the "release start time"
                                    // This interpretation is tricky. Let's assume dragging P3 primarily affects release time.
                                    // The release phase starts at p3. Its duration determines p4.x.
                                    // If p3 is dragged horizontally, it means the sustain duration is visually changing.
                                    // Let's refine: P1(AttackX), P2(DecayX, SustainY), P3(ReleaseX relative to P2, not P3 itself)
                                    // For ADSR, P3 is not directly dragged for time. Its X is p2.x + visual_sustain_duration.
                                    // The "release handle" is conceptually the duration from P3.
                                    // Let's try dragging P3's X to control Release Time for now.
                                    // This means p4.x = p3_x_dragged + release_time_pixels.
                                    // release_time_pixels = p4.x_current - p3_x_current
                                    // p3_x_dragged = initial_p3_x + drag.x
                                    // new_release_ms = ( (p4_x_current - (p3_x_current + change.position.x - change.previousPosition.x) ) / timeScale * 1000f) is NOT intuitive.

                                    // Simpler: Dragging P3 means changing the start of release time relative to P2.
                                    // This is effectively changing the "visual sustain duration".
                                    // The release time itself (duration of release phase) is better controlled by a conceptual P4.
                                    // For this subtask, let's make P3 drag primarily affect sustain level (Y, shared with P2)
                                    // and P4 (conceptually) affect release time.
                                    // Since P4 is drawn but not directly made draggable yet, let's focus P3 Y.
                                    // P3 shares Y with P2 for sustain.
                                    val draggedY = p2_y_current + change.position.y - change.previousPosition.y // p3y is same as p2y
                                    val constrainedY = draggedY.coerceIn(0f, size.height)
                                    newSustainLevel = (1f - (constrainedY / size.height)).coerceIn(0f, 1f)

                                    // Let's add dragging for Release Time using P3's X for now, affecting the duration P3-P4
                                    // new_p3_x = p3_x_current + deltaX
                                    // new_p4_x = new_p3_x + new_release_sec * currentTimeScale
                                    // So, new_release_sec = ( (p3_x_current + deltaX + (currentReleaseSec*currentTimeScale) ) - (p3_x_current+deltaX) ) / currentTimeScale = currentReleaseSec
                                    // This means dragging p3.x should adjust the visual sustain time, AND THEN release time starts.
                                    // This is getting complicated. Let's simplify:
                                    // P1.x -> Attack Time
                                    // P2.x -> Decay Time (delta from P1.x)
                                    // P2.y (and P3.y) -> Sustain Level
                                    // P3.x (conceptually, its delta from a fixed point on sustain) -> Release Time.
                                    // The current setup of p3.x depends on p2.x and sustainVisualDurationSec.
                                    // Let's make dragging P3 adjust Release Time.
                                    // The "release handle" is effectively P4, but P3 is where release starts.
                                    // If dragging P3.x, it effectively means changing the total time before release ends.
                                    // (p3_x_current + dragAmount.x) is the new p3_x. Release starts here.
                                    // p4_x = new_p3_x + new_release_sec * timeScale.
                                    // This implies new_release_sec = (p4_x_target - new_p3_x) / timeScale.
                                    // This is not intuitive.
                                    //
                                    // Let's make P3 drag X control the *end* of the release segment (like dragging P4)
                                    // So, new_p4_x = p4_x_current + change.position.x - change.previousPosition.x
                                    // new_release_sec = (new_p4_x - p3_x_current) / timeScale
                                    val p4_x_current = p3_x_current + (currentReleaseSec * currentTimeScale)
                                    val dragged_p4_x = p4_x_current + change.position.x - change.previousPosition.x
                                    val constrained_p4_x = dragged_p4_x.coerceIn(p3_x_current + minPixelSeparationForTime, size.width * 2f) // Allow dragging off-screen
                                    newReleaseMs = ((constrained_p4_x - p3_x_current) / currentTimeScale * 1000f).coerceAtLeast(1f)
                                }
                            }

                            onSettingsChange(
                                envelopeSettings.copy(
                                    attackMs = newAttackMs,
                                    decayMs = newDecayMs,
                                    sustainLevel = newSustainLevel,
                                    releaseMs = newReleaseMs
                                )
                            )
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        activeDragPointIndex = null
                    }
                )
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Normalize settings for drawing (times in seconds, sustain 0-1)
        // Ensure times are not zero to avoid division by zero if scaling based on them, and for visual representation.
        val attackSec = max(0.01f, envelopeSettings.attackMs / 1000f)
        val decaySec = max(0.01f, envelopeSettings.decayMs / 1000f)
        val sustainLevel = envelopeSettings.sustainLevel.coerceIn(0f, 1f)
        val releaseSec = max(0.01f, envelopeSettings.releaseMs / 1000f)
        // Hold time could be added here if the EnvelopeSettings supports it and it needs visualization.
        // For ADSR, hold is typically 0 or handled by keeping peak for a duration.
        // This visualization assumes ADSR without an explicit hold phase visually distinct from attack peak.

        // Determine total time for x-axis scaling.
        // This could be a fixed value (e.g., 5 seconds) or dynamic.
        // For dynamic, sustain phase itself has no "time" for ADSR, it's a level held.
        // Let's make the visual sustain segment proportional to decay or a fixed small time.
        val sustainVisualDurationSec = max(0.05f, decaySec * 0.3f + attackSec * 0.2f) // Arbitrary visual length for sustain part
        val totalEnvelopeTimeSec = attackSec + decaySec + sustainVisualDurationSec + releaseSec

        val timeScale = if (totalEnvelopeTimeSec > 0.01f) canvasWidth / totalEnvelopeTimeSec else canvasWidth

        // Points for ADSR segments (x, y coordinates)
        // Origin (bottom-left)
        val p0 = Offset(0f, canvasHeight)
        // P1: End of Attack (peak)
        val p1_x = attackSec * timeScale
        val p1_y = 0f // Top of canvas for 1.0 amplitude
        val p1 = Offset(p1_x, p1_y)
        // P2: End of Decay (start of sustain)
        val p2_x = p1_x + (decaySec * timeScale)
        val p2_y = canvasHeight * (1f - sustainLevel)
        val p2 = Offset(p2_x, p2_y)
        // P3: Start of Release (end of sustain - visually needs a horizontal segment for sustain)
        val p3_x = p2_x + (sustainVisualDurationSec * timeScale)
        val p3_y = p2_y
        val p3 = Offset(p3_x, p3_y)
        // P4: End of Release
        // Release starts from sustain level (p3)
        val p4_x = p3_x + (releaseSec * timeScale)
        val p4_y = canvasHeight
        val p4 = Offset(p4_x, p4_y)


        // Path for the envelope line
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y) // Line to end of visual sustain segment
            lineTo(p4.x, p4.y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw draggable points (circles) - actual drag logic is for a later step
        val pointRadius = 6.dp.toPx()
        drawCircle(color = pointColor, radius = pointRadius, center = p1) // Attack point (end of attack)
        drawCircle(color = pointColor, radius = pointRadius, center = p2) // Decay point (end of decay / start of sustain)
        // For sustain, the point to drag is typically the level, which influences p2 and p3's y.
        // A separate handle for sustain level might be a horizontal line or a point on p2/p3.
        // The point p3 represents the start of release. Its x position is influenced by sustain visual duration.
        drawCircle(color = pointColor, radius = pointRadius, center = p3) // Sustain end / Release start point
        // Point p4 (end of release) is not typically draggable for ADSR directly; release time is the duration from p3 to p4.
        // However, showing a point there is fine.
        // If p4_x exceeds canvasWidth, it will be drawn off-canvas, which is acceptable.
        if (p4_x <= canvasWidth) { // Only draw if it's (mostly) on screen
             drawCircle(color = pointColor, radius = pointRadius, center = p4) // Release end point
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VisualEnvelopeEditorPreview() {
    VisualEnvelopeEditor(
        envelopeSettings = DefaultEnvelopeSettingsForPreview,
        onSettingsChange = {}
    )
}

@Preview(showBackground = true)
@Composable
fun VisualEnvelopeEditorShortAttackPreview() {
    VisualEnvelopeEditor(
        envelopeSettings = DefaultEnvelopeSettingsForPreview.copy(attackMs = 10f, decayMs = 50f),
        onSettingsChange = {}
    )
}

@Preview(showBackground = true)
@Composable
fun VisualEnvelopeEditorZeroReleasePreview() {
    VisualEnvelopeEditor(
        envelopeSettings = DefaultEnvelopeSettingsForPreview.copy(releaseMs = 0f, sustainLevel = 0.2f),
        onSettingsChange = {}
    )
}

@Preview(showBackground = true)
@Composable
fun VisualEnvelopeEditorHighSustainPreview() {
    VisualEnvelopeEditor(
        envelopeSettings = DefaultEnvelopeSettingsForPreview.copy(sustainLevel = 0.95f, attackMs = 20f, decayMs = 30f, releaseMs = 50f),
        onSettingsChange = {}
    )
}
