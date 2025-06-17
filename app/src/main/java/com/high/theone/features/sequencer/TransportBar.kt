package com.high.theone.features.sequencer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.EffectSetting
import com.high.theone.model.LFOSettings
import com.high.theone.model.ModulationRouting

@Composable
fun TransportBar(
    isPlaying: Boolean,
    bpm: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onBpmChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = onPlayPause) {
            Text(if (isPlaying) "Pause" else "Play")
        }
        Button(onClick = onStop) {
            Text("Stop")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BPM:", modifier = Modifier.padding(end = 4.dp))
            Slider(
                value = bpm,
                onValueChange = onBpmChange,
                valueRange = 60f..200f,
                steps = 140,
                modifier = Modifier.width(120.dp)
            )
            Text(bpm.toInt().toString(), modifier = Modifier.padding(start = 4.dp))
        }
    }
}
