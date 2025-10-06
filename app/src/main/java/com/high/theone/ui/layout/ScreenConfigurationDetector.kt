package com.high.theone.ui.layout

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.high.theone.model.ScreenConfiguration

/**
 * Composable that detects and provides screen configuration changes
 */
@Composable
fun rememberScreenConfiguration(): ScreenConfiguration {
    return ResponsiveLayoutUtils.calculateScreenConfiguration()
}

/**
 * Effect that monitors screen configuration changes and notifies callback
 */
@Composable
fun ScreenConfigurationEffect(
    onConfigurationChanged: (ScreenConfiguration) -> Unit
) {
    val configuration = rememberScreenConfiguration()
    
    LaunchedEffect(configuration) {
        onConfigurationChanged(configuration)
    }
}