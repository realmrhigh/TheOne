package com.high.theone.midi.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MIDI-related permissions for Android.
 * Handles permission checking, requesting, and status tracking.
 */
@Singleton
class MidiPermissionManager @Inject constructor(
    private val context: Context
) {
    
    private val _permissionState = MutableStateFlow(MidiPermissionState())
    val permissionState: StateFlow<MidiPermissionState> = _permissionState.asStateFlow()
    
    init {
        updatePermissionState()
    }
    
    /**
     * Check if all required MIDI permissions are granted
     */
    fun hasAllMidiPermissions(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ requires POST_NOTIFICATIONS for MIDI device notifications
                hasPermission(Manifest.permission.POST_NOTIFICATIONS) && hasMidiSupport()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6+ has built-in MIDI support, no special permissions needed
                hasMidiSupport()
            }
            else -> {
                // Pre-Android 6 doesn't have native MIDI support
                false
            }
        }
    }
    
    /**
     * Check if device supports MIDI
     */
    fun hasMidiSupport(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)
    }
    
    /**
     * Get list of permissions that need to be requested
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        return permissions
    }
    
    /**
     * Update permission state after permission request result
     */
    fun updatePermissionState() {
        val currentState = _permissionState.value
        val newState = currentState.copy(
            hasMidiSupport = hasMidiSupport(),
            hasAllPermissions = hasAllMidiPermissions(),
            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true // Not required on older versions
            },
            requiredPermissions = getRequiredPermissions(),
            lastChecked = System.currentTimeMillis()
        )
        
        _permissionState.value = newState
    }
    
    /**
     * Get user-friendly explanation for why permissions are needed
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> {
                "Notification permission is required to show MIDI device connection status and alerts."
            }
            else -> {
                "This permission is required for MIDI functionality."
            }
        }
    }
    
    /**
     * Get overall permission status message
     */
    fun getPermissionStatusMessage(): String {
        return when {
            !hasMidiSupport() -> {
                "MIDI is not supported on this device (requires Android 6.0 or higher)."
            }
            hasAllMidiPermissions() -> {
                "All MIDI permissions are granted. MIDI functionality is available."
            }
            else -> {
                val missingCount = getRequiredPermissions().size
                "MIDI requires $missingCount additional permission(s) to function properly."
            }
        }
    }
    
    /**
     * Check if a specific permission is granted
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if we should show rationale for a permission
     */
    fun shouldShowRationale(permission: String): Boolean {
        // This would typically be called from an Activity context
        // For now, return false as we can't check from Application context
        return false
    }
}

/**
 * Represents the current state of MIDI permissions
 */
data class MidiPermissionState(
    val hasMidiSupport: Boolean = false,
    val hasAllPermissions: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val requiredPermissions: List<String> = emptyList(),
    val lastChecked: Long = 0L
) {
    val isReady: Boolean
        get() = hasMidiSupport && hasAllPermissions
}