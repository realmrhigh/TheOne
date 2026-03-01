package com.high.theone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── MPC ONE+ Dark Color Scheme ────────────────────────────────────────────
private val MpcDarkColorScheme = darkColorScheme(
    primary           = MpcRedBright,
    onPrimary         = MpcTextPrimary,
    primaryContainer  = MpcRed,
    onPrimaryContainer = MpcTextPrimary,

    secondary         = MpcDisplayBlue,
    onSecondary       = MpcBodyDark,
    secondaryContainer = MpcBodyLight,
    onSecondaryContainer = MpcTextPrimary,

    tertiary          = MpcDisplayCyan,
    onTertiary        = MpcBodyDark,

    background        = MpcBodyDark,
    onBackground      = MpcTextPrimary,

    surface           = MpcBodyMid,
    onSurface         = MpcTextPrimary,
    surfaceVariant    = MpcBodyLight,
    onSurfaceVariant  = MpcTextSecondary,

    outline           = MpcButtonMid,
    outlineVariant    = MpcButtonDark,

    error             = MpcRedBright,
    onError           = MpcTextPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun TheOneTheme(
    darkTheme: Boolean = true,           // Always dark – MPC hardware aesthetic
    dynamicColor: Boolean = false,       // Disabled – preserve MPC colour palette
    content: @Composable () -> Unit
) {
    // Always use the MPC dark scheme so the UI matches the physical hardware
    val colorScheme = MpcDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = MpcBodyDark.toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}