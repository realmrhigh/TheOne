package com.high.theone.features.compactui.error

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permission manager for handling microphone access requests
 * Requirements: 5.3 (permission request flow)
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {
    
    private val _permissionState = MutableStateFlow(PermissionState.UNKNOWN)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    private val _shouldShowRationale = MutableStateFlow(false)
    val shouldShowRationale: StateFlow<Boolean> = _shouldShowRationale.asStateFlow()
    
    private val _permissionDeniedPermanently = MutableStateFlow(false)
    val permissionDeniedPermanently: StateFlow<Boolean> = _permissionDeniedPermanently.asStateFlow()
    
    /**
     * Check current microphone permission status
     */
    fun checkMicrophonePermission(): PermissionState {
        val state = when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> PermissionState.GRANTED
            PackageManager.PERMISSION_DENIED -> PermissionState.DENIED
            else -> PermissionState.UNKNOWN
        }
        _permissionState.value = state
        return state
    }
    
    /**
     * Update permission state after request result
     */
    fun updatePermissionState(granted: Boolean, shouldShowRationale: Boolean) {
        _permissionState.value = if (granted) PermissionState.GRANTED else PermissionState.DENIED
        _shouldShowRationale.value = shouldShowRationale
        _permissionDeniedPermanently.value = !granted && !shouldShowRationale
    }
    
    /**
     * Check if we should show permission rationale
     */
    fun shouldShowPermissionRationale(activity: Activity): Boolean {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }
    
    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Get user-friendly permission explanation
     */
    fun getPermissionExplanation(): String {
        return when (_permissionState.value) {
            PermissionState.DENIED -> {
                if (_permissionDeniedPermanently.value) {
                    "Microphone permission was denied. Please enable it in Settings > Apps > TheOne > Permissions to record audio."
                } else {
                    "TheOne needs microphone access to record audio samples. This permission is required for the recording feature to work."
                }
            }
            PermissionState.GRANTED -> "Microphone permission is granted."
            PermissionState.UNKNOWN -> "Microphone permission status is unknown. Please check app permissions."
        }
    }
    
    /**
     * Get recovery instructions based on permission state
     */
    fun getRecoveryInstructions(): List<String> {
        return when (_permissionState.value) {
            PermissionState.DENIED -> {
                if (_permissionDeniedPermanently.value) {
                    listOf(
                        "1. Tap 'Open Settings' below",
                        "2. Find 'Permissions' section",
                        "3. Enable 'Microphone' permission",
                        "4. Return to the app and try recording again"
                    )
                } else {
                    listOf(
                        "1. Tap 'Grant Permission' below",
                        "2. Select 'Allow' when prompted",
                        "3. Try recording again"
                    )
                }
            }
            PermissionState.GRANTED -> listOf("Permission is already granted. You can start recording.")
            PermissionState.UNKNOWN -> listOf("Check app permissions in device settings.")
        }
    }
    
    /**
     * Reset permission state for fresh check
     */
    fun resetState() {
        _permissionState.value = PermissionState.UNKNOWN
        _shouldShowRationale.value = false
        _permissionDeniedPermanently.value = false
    }
}

/**
 * Permission states for microphone access
 */
enum class PermissionState {
    UNKNOWN,
    GRANTED,
    DENIED
}