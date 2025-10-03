package com.high.theone.features.sampling

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/**
 * Enhanced haptic feedback manager for sampling features.
 * Provides contextual haptic feedback for different user interactions.
 * 
 * Requirements: 6.5 (haptic feedback for touch interactions)
 */
class HapticFeedbackManager(private val hapticFeedback: HapticFeedback) {
    
    /**
     * Provide haptic feedback for pad interactions based on velocity and context.
     */
    fun padTriggerFeedback(velocity: Float, hasAssignedSample: Boolean) {
        when {
            !hasAssignedSample -> {
                // Light feedback for empty pad taps
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            velocity > 0.8f -> {
                // Strong feedback for hard hits
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            velocity > 0.5f -> {
                // Medium feedback for medium hits
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> {
                // Light feedback for soft hits
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    
    /**
     * Provide haptic feedback for recording state changes.
     */
    fun recordingStateFeedback(isStarting: Boolean) {
        if (isStarting) {
            // Double pulse for recording start
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            // Single pulse for recording stop
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    /**
     * Provide haptic feedback for navigation and focus changes.
     */
    fun navigationFeedback() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    /**
     * Provide haptic feedback for successful operations.
     */
    fun successFeedback() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    /**
     * Provide haptic feedback for errors or warnings.
     */
    fun errorFeedback() {
        // Use a pattern of short pulses for errors
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    
    /**
     * Provide haptic feedback for sample loading progress.
     */
    fun loadingProgressFeedback(progress: Float) {
        // Provide feedback at 25%, 50%, 75%, and 100%
        val milestones = listOf(0.25f, 0.5f, 0.75f, 1.0f)
        if (milestones.any { kotlin.math.abs(progress - it) < 0.01f }) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/**
 * Composable function to provide haptic feedback manager.
 */
@Composable
fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val hapticFeedback = LocalHapticFeedback.current
    return remember { HapticFeedbackManager(hapticFeedback) }
}

/**
 * Enhanced haptic feedback patterns for different interaction types.
 */
object HapticPatterns {
    
    /**
     * Create a custom haptic pattern for velocity-sensitive pad hits.
     */
    suspend fun velocityBasedPattern(
        hapticFeedback: HapticFeedback,
        velocity: Float
    ) {
        when {
            velocity > 0.9f -> {
                // Very hard hit - triple pulse
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(50)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(50)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            velocity > 0.7f -> {
                // Hard hit - double pulse
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(100)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            velocity > 0.3f -> {
                // Medium hit - single strong pulse
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> {
                // Light hit - single light pulse
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    
    /**
     * Create a haptic pattern for recording state transitions.
     */
    suspend fun recordingTransitionPattern(
        hapticFeedback: HapticFeedback,
        isStarting: Boolean
    ) {
        if (isStarting) {
            // Recording start - ascending pattern
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(100)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            // Recording stop - descending pattern
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(100)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    /**
     * Create a haptic pattern for error notifications.
     */
    suspend fun errorNotificationPattern(hapticFeedback: HapticFeedback) {
        // Error pattern - three short pulses
        repeat(3) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(150)
        }
    }
    
    /**
     * Create a haptic pattern for success notifications.
     */
    suspend fun successNotificationPattern(hapticFeedback: HapticFeedback) {
        // Success pattern - two ascending pulses
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        delay(100)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}