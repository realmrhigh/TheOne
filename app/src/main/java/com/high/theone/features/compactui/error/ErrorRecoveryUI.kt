package com.high.theone.features.compactui.error

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.high.theone.model.RecordingError
import com.high.theone.model.RecordingErrorType
import com.high.theone.model.RecordingRecoveryAction

/**
 * Error recovery UI components with clear messaging and recovery actions
 * Requirements: 5.2 (error recovery UI components with clear messaging)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorRecoveryDialog(
    error: RecordingError,
    isRecovering: Boolean = false,
    recoveryProgress: Float = 0f,
    onRecoveryAction: (RecordingRecoveryAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { if (!isRecovering) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getErrorIcon(error.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = getErrorTitle(error.type),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error message
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Recovery progress if recovering
                if (isRecovering) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Attempting recovery...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = recoveryProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(recoveryProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Recovery instructions
                if (!isRecovering && error.isRecoverable) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How to fix this:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getRecoveryInstructions(error.type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRecovering && error.isRecoverable && error.recoveryAction != null) {
                Button(
                    onClick = { onRecoveryAction(error.recoveryAction) }
                ) {
                    Text(getRecoveryActionText(error.recoveryAction))
                }
            }
        },
        dismissButton = {
            if (!isRecovering) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ErrorBanner(
    error: RecordingError,
    onRecoveryAction: (RecordingRecoveryAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getErrorIcon(error.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = getErrorTitle(error.type),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error.isRecoverable && error.recoveryAction != null) {
                    FilledTonalButton(
                        onClick = { onRecoveryAction(error.recoveryAction) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = getRecoveryActionText(error.recoveryAction),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                IconButton(
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestDialog(
    permissionState: PermissionState,
    explanation: String,
    instructions: List<String>,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Microphone Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Steps to enable:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        instructions.forEach { instruction ->
                            Text(
                                text = instruction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (permissionState) {
                PermissionState.DENIED -> {
                    Button(onClick = onRequestPermission) {
                        Text("Grant Permission")
                    }
                }
                PermissionState.GRANTED -> {
                    Button(onClick = onDismiss) {
                        Text("Continue")
                    }
                }
                PermissionState.UNKNOWN -> {
                    Button(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

@Composable
fun StorageManagementDialog(
    storageInfo: StorageInfo,
    recommendations: List<String>,
    isCleaningUp: Boolean = false,
    cleanupProgress: Float = 0f,
    onCleanupStorage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { if (!isCleaningUp) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (storageInfo.canRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Storage Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Storage info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Storage Status",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Available: ${storageInfo.availableSpaceMB}MB",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Used: ${storageInfo.usedSpaceMB}MB (${storageInfo.usagePercentage.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = storageInfo.usagePercentage / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Cleanup progress
                if (isCleaningUp) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Cleaning up storage...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            LinearProgressIndicator(
                                progress = cleanupProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(cleanupProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Recommendations
                if (!isCleaningUp && recommendations.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (storageInfo.canRecord) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Recommendations:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (storageInfo.canRecord) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                            recommendations.forEach { recommendation ->
                                Text(
                                    text = recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isCleaningUp && !storageInfo.canRecord) {
                Button(onClick = onCleanupStorage) {
                    Text("Clean Up Storage")
                }
            }
        },
        dismissButton = {
            if (!isCleaningUp) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        modifier = modifier
    )
}

private fun getErrorIcon(errorType: RecordingErrorType): ImageVector {
    return when (errorType) {
        RecordingErrorType.PERMISSION_DENIED -> Icons.Default.MicOff
        RecordingErrorType.AUDIO_ENGINE_FAILURE -> Icons.Default.VolumeOff
        RecordingErrorType.STORAGE_FAILURE -> Icons.Default.Storage
        RecordingErrorType.MICROPHONE_UNAVAILABLE -> Icons.Default.MicNone
        RecordingErrorType.SYSTEM_OVERLOAD -> Icons.Default.Warning
    }
}

private fun getErrorTitle(errorType: RecordingErrorType): String {
    return when (errorType) {
        RecordingErrorType.PERMISSION_DENIED -> "Permission Required"
        RecordingErrorType.AUDIO_ENGINE_FAILURE -> "Audio System Error"
        RecordingErrorType.STORAGE_FAILURE -> "Storage Error"
        RecordingErrorType.MICROPHONE_UNAVAILABLE -> "Microphone Unavailable"
        RecordingErrorType.SYSTEM_OVERLOAD -> "System Overloaded"
    }
}

private fun getRecoveryInstructions(errorType: RecordingErrorType): String {
    return when (errorType) {
        RecordingErrorType.PERMISSION_DENIED -> "Grant microphone permission to enable recording functionality."
        RecordingErrorType.AUDIO_ENGINE_FAILURE -> "The audio system will be restarted automatically. This may take a few seconds."
        RecordingErrorType.STORAGE_FAILURE -> "Free up storage space or clean up temporary files to continue recording."
        RecordingErrorType.MICROPHONE_UNAVAILABLE -> "Close other apps that might be using the microphone and try again."
        RecordingErrorType.SYSTEM_OVERLOAD -> "Close other apps or reduce recording quality to improve performance."
    }
}

private fun getRecoveryActionText(recoveryAction: RecordingRecoveryAction): String {
    return when (recoveryAction) {
        RecordingRecoveryAction.REQUEST_PERMISSION -> "Grant Permission"
        RecordingRecoveryAction.RETRY_RECORDING -> "Try Again"
        RecordingRecoveryAction.RESTART_AUDIO_ENGINE -> "Restart Audio"
        RecordingRecoveryAction.FREE_STORAGE_SPACE -> "Manage Storage"
        RecordingRecoveryAction.REDUCE_QUALITY -> "Reduce Quality"
    }
}