package com.high.theone.features.drumtrack.edit

import androidx.compose.runtime.Composable
import com.high.theone.features.drumtrack.model.PadSettings

@Composable
fun DrumProgramEditDialog(
    padSettings: PadSettings,
    onDismiss: () -> Unit,
    onSave: (PadSettings) -> Unit
) {
    // TODO: Implement dialog UI for editing pad settings
    // For now, just call onDismiss immediately
    onDismiss()
}
