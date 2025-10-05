package com.high.theone.midi.error

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.high.theone.MainActivity
import com.high.theone.R
import com.high.theone.midi.MidiError
import com.high.theone.midi.MidiErrorContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user notifications for MIDI errors and system status.
 * Provides user-friendly error messages and recovery guidance.
 * 
 * Requirements: 7.2, 7.6
 */
@Singleton
class MidiNotificationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val CHANNEL_ID_MIDI_ERRORS = "midi_errors"
        private const val CHANNEL_ID_MIDI_STATUS = "midi_status"
        private const val NOTIFICATION_ID_ERROR = 1001
        private const val NOTIFICATION_ID_DEVICE_STATUS = 1002
        private const val NOTIFICATION_ID_SYSTEM_HEALTH = 1003
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Show error notification with recovery guidance
     */
    fun showErrorNotification(
        error: MidiError,
        context: MidiErrorContext,
        result: MidiErrorResult
    ) {
        val title = when (error) {
            is MidiError.DeviceNotFound -> "MIDI Device Not Found"
            is MidiError.ConnectionFailed -> "MIDI Connection Failed"
            is MidiError.PermissionDenied -> "MIDI Permission Required"
            is MidiError.BufferOverflow -> "MIDI Data Overload"
            is MidiError.ClockSyncLost -> "MIDI Clock Sync Lost"
            MidiError.MidiNotSupported -> "MIDI Not Supported"
            else -> "MIDI Error"
        }
        
        val notification = NotificationCompat.Builder(this.context, CHANNEL_ID_MIDI_ERRORS)
            .setSmallIcon(R.drawable.ic_midi_error) // You'll need to add this icon
            .setContentTitle(title)
            .setContentText(result.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityIntent())
            .apply {
                // Add action button if recovery action is available
                result.recoveryAction?.let { action ->
                    addAction(createRecoveryAction(action))
                }
            }
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - handle gracefully
        }
    }
    
    /**
     * Show device reconnection success notification
     */
    fun showDeviceReconnectedNotification(deviceId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MIDI_STATUS)
            .setSmallIcon(R.drawable.ic_midi_connected) // You'll need to add this icon
            .setContentTitle("MIDI Device Reconnected")
            .setContentText("Device $deviceId is now connected")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Auto-dismiss after 5 seconds
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID_DEVICE_STATUS, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - handle gracefully
        }
    }
    
    /**
     * Show device reconnection failure notification
     */
    fun showDeviceReconnectionFailedNotification(deviceId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MIDI_ERRORS)
            .setSmallIcon(R.drawable.ic_midi_error)
            .setContentTitle("MIDI Device Reconnection Failed")
            .setContentText("Unable to reconnect to device $deviceId")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Unable to reconnect to MIDI device $deviceId. Please check the connection and try again manually."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMidiSettingsIntent())
            .addAction(createRetryConnectionAction(deviceId))
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID_DEVICE_STATUS, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - handle gracefully
        }
    }
    
    /**
     * Show system health status notification
     */
    fun showSystemHealthNotification(health: MidiSystemHealth, details: String) {
        val (title, priority) = when (health) {
            MidiSystemHealth.HEALTHY -> "MIDI System Healthy" to NotificationCompat.PRIORITY_LOW
            MidiSystemHealth.DEGRADED -> "MIDI System Issues" to NotificationCompat.PRIORITY_DEFAULT
            MidiSystemHealth.CRITICAL -> "MIDI System Critical" to NotificationCompat.PRIORITY_HIGH
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MIDI_STATUS)
            .setSmallIcon(getHealthIcon(health))
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(createMidiSettingsIntent())
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID_SYSTEM_HEALTH, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - handle gracefully
        }
    }
    
    /**
     * Show permission request notification
     */
    fun showPermissionRequestNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MIDI_ERRORS)
            .setSmallIcon(R.drawable.ic_midi_permission) // You'll need to add this icon
            .setContentTitle("MIDI Permission Required")
            .setContentText("Grant MIDI permission to use external controllers")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("TheOne needs MIDI permission to connect to external controllers and keyboards. Tap to grant permission."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityIntent())
            .addAction(createGrantPermissionAction())
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - handle gracefully
        }
    }
    
    /**
     * Clear all MIDI notifications
     */
    fun clearAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_ERROR)
        notificationManager.cancel(NOTIFICATION_ID_DEVICE_STATUS)
        notificationManager.cancel(NOTIFICATION_ID_SYSTEM_HEALTH)
    }
    
    /**
     * Clear error notifications only
     */
    fun clearErrorNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_ERROR)
    }
    
    // Private helper methods
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val errorChannel = NotificationChannel(
                CHANNEL_ID_MIDI_ERRORS,
                "MIDI Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for MIDI errors and issues"
                enableVibration(true)
            }
            
            val statusChannel = NotificationChannel(
                CHANNEL_ID_MIDI_STATUS,
                "MIDI Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for MIDI device status updates"
                enableVibration(false)
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(errorChannel)
            manager.createNotificationChannel(statusChannel)
        }
    }
    
    private fun createMainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createMidiSettingsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "midi_settings")
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createRecoveryAction(action: MidiRecoveryAction): NotificationCompat.Action {
        val actionText = when (action) {
            MidiRecoveryAction.CHECK_CONNECTION -> "Check Connection"
            MidiRecoveryAction.RETRY_CONNECTION -> "Retry"
            MidiRecoveryAction.GRANT_PERMISSION -> "Grant Permission"
            MidiRecoveryAction.REDUCE_MIDI_LOAD -> "Reduce Load"
            MidiRecoveryAction.CHECK_CLOCK_SOURCE -> "Check Clock"
            MidiRecoveryAction.USE_TOUCH_INPUT -> "Use Touch"
            MidiRecoveryAction.REVIEW_MAPPINGS -> "Review Mappings"
            MidiRecoveryAction.RESTART_DEVICE -> "Restart Device"
            MidiRecoveryAction.RESTART_APP -> "Restart App"
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("midi_recovery_action", action.name)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            action.ordinal + 100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            0, // No icon for now
            actionText,
            pendingIntent
        ).build()
    }
    
    private fun createRetryConnectionAction(deviceId: String): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("midi_retry_device", deviceId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            0, // No icon for now
            "Retry Connection",
            pendingIntent
        ).build()
    }
    
    private fun createGrantPermissionAction(): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("midi_grant_permission", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            300,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            0, // No icon for now
            "Grant Permission",
            pendingIntent
        ).build()
    }
    
    private fun getHealthIcon(health: MidiSystemHealth): Int {
        return when (health) {
            MidiSystemHealth.HEALTHY -> R.drawable.ic_midi_healthy // You'll need to add this icon
            MidiSystemHealth.DEGRADED -> R.drawable.ic_midi_warning // You'll need to add this icon
            MidiSystemHealth.CRITICAL -> R.drawable.ic_midi_error
        }
    }
}