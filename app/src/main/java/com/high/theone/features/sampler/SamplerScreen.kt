package com.high.theone.features.sampler

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.high.theone.model.PlaybackMode
import com.high.theone.model.EnvelopeSettings
import com.high.theone.model.EffectSetting
import com.high.theone.model.LFOSettings
import com.high.theone.model.ModulationRouting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplerScreen(
    modifier: Modifier = Modifier,
    viewModel: SamplerViewModel = viewModel()
) {
    // Example usage of RadioButtonChecked icon in a Row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.RadioButtonChecked,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Radio Button Checked Example")
    }

    // ...existing code...
}
