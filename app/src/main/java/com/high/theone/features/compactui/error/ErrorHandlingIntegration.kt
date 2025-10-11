package com.high.theone.features.compactui.error

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.high.theone.features.compactui.CompactMainViewModel
import com.high.theone.model.RecordingRecoveryAction

/**
 * Integration component for error handling in CompactMainScreen
 * This shows how to integrate the error handling UI components with the main screen
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5 (comprehensive error handling integration)
 */

@Composable
fun ErrorHandlingIntegration(
    viewModel: CompactMainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Collect error handling states
    val currentError by viewModel.currentError.collectAsState()
    val isRecovering by viewModel.isRecovering.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val isCleaningUp by viewModel.isCleaningUp.collectAsState()
    val cleanupProgress by viewModel.cleanupProgress.collectAsState()
    val recoveryProgress by viewModel.recoveryProgress.collectAsState()
    
    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val activity = context as? androidx.activity.ComponentActivity
        val shouldShowRationale = activity?.let { 
            ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.RECORD_AUDIO) 
        } ?: false
        
        viewModel.updatePermissionState(granted, shouldShowRationale)
    }
    
    // State for dialog visibility
    var showErrorDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    
    // Show error dialog when there's an error
    LaunchedEffect(currentError) {
        showErrorDialog = currentError != null
    }
    
    // Show permission dialog when permission is needed
    LaunchedEffect(permissionState) {
        showPermissionDialog = permissionState == PermissionState.DENIED
    }
    
    // Show storage dialog when storage is insufficient
    LaunchedEffect(storageInfo) {
        showStorageDialog = !storageInfo.canRecord && storageInfo.availableSpaceMB > 0
    }
    
    Column(modifier = modifier) {
        // Error banner for non-critical errors
        currentError?.let { error ->
            if (!showErrorDialog) {
                ErrorBanner(
                    error = error,
                    onRecoveryAction = { action ->
                        handleRecoveryAction(action, viewModel, permissionLauncher)
                    },
                    onDismiss = { viewModel.clearError() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Error recovery dialog for critical errors
        if (showErrorDialog && currentError != null) {
            ErrorRecoveryDialog(
                error = currentError!!,
                isRecovering = isRecovering,
                recoveryProgress = recoveryProgress,
                onRecoveryAction = { action ->
                    handleRecoveryAction(action, viewModel, permissionLauncher)
                },
                onDismiss = { 
                    showErrorDialog = false
                    viewModel.clearError()
                }
            )
        }
        
        // Permission request dialog
        if (showPermissionDialog) {
            PermissionRequestDialog(
                permissionState = permissionState,
                explanation = viewModel.getPermissionExplanation(),
                instructions = viewModel.getPermissionRecoveryInstructions(),
                onRequestPermission = {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                onOpenSettings = {
                    viewModel.openAppSettings()
                },
                onDismiss = { showPermissionDialog = false }
            )
        }
        
        // Storage management dialog
        if (showStorageDialog) {
            StorageManagementDialog(
                storageInfo = storageInfo,
                recommendations = viewModel.getStorageRecommendations(),
                isCleaningUp = isCleaningUp,
                cleanupProgress = cleanupProgress,
                onCleanupStorage = {
                    viewModel.cleanupStorage()
                },
                onDismiss = { showStorageDialog = false }
            )
        }
    }
}

/**
 * Handle recovery actions with appropriate UI interactions
 */
private fun handleRecoveryAction(
    action: RecordingRecoveryAction,
    viewModel: CompactMainViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    when (action) {
        RecordingRecoveryAction.REQUEST_PERMISSION -> {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        else -> {
            viewModel.executeRecoveryAction(action)
        }
    }
}

/**
 * Composable for showing error handling status in debug/development builds
 */
@Composable
fun ErrorHandlingDebugInfo(
    viewModel: CompactMainViewModel,
    modifier: Modifier = Modifier
) {
    val currentError by viewModel.currentError.collectAsState()
    val retryAttempts by viewModel.retryAttempts.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val recoveryState by viewModel.recoveryState.collectAsState()
    
    Column(modifier = modifier) {
        androidx.compose.material3.Text("Error Handling Debug Info:")
        androidx.compose.material3.Text("Current Error: ${currentError?.type}")
        androidx.compose.material3.Text("Retry Attempts: $retryAttempts")
        androidx.compose.material3.Text("Permission State: $permissionState")
        androidx.compose.material3.Text("Storage Available: ${storageInfo.availableSpaceMB}MB")
        androidx.compose.material3.Text("Recovery State: $recoveryState")
    }
}