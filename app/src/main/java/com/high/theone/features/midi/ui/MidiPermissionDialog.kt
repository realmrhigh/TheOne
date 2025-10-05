package com.high.theone.features.midi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog for requesting MIDI permissions from the user.
 * Provides clear explanation of why MIDI permissions are needed.
 */
@Composable
fun MidiPermissionDialog(
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Required",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "MIDI Permission Required",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "TheOne needs MIDI permission to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BulletPoint("Connect to MIDI keyboards and controllers")
                    BulletPoint("Receive MIDI input for triggering samples")
                    BulletPoint("Send MIDI output for synchronization")
                    BulletPoint("Manage MIDI device connections")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Without this permission, MIDI features will not be available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermission
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}