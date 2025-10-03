package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Controller for real-time tempo and swing adjustments
 * Handles smooth transitions and validation for musical parameters
 */
@Singleton
class TempoSwingController @Inject constructor() {
    
    // Current parameter values
    private val _tempo = MutableStateFlow(120f)
    val tempo: StateFlow<Float> = _tempo.asStateFlow()
    
    private val _swing = MutableStateFlow(0f)
    val swing: StateFlow<Float> = _swing.asStateFlow()
    
    private val _isTempoChanging = MutableStateFlow(false)
    val isTempoChanging: StateFlow<Boolean> = _isTempoChanging.asStateFlow()
    
    // Tempo transition parameters
    private var targetTempo: Float = 120f
    private var tempoTransitionRate: Float = 2f // BPM per update
    private var isTransitioning = false
    
    // Validation ranges
    companion object {
        const val MIN_TEMPO = 60f
        const val MAX_TEMPO = 200f
        const val MIN_SWING = 0f
        const val MAX_SWING = 0.75f
        const val TEMPO_TRANSITION_THRESHOLD = 5f // BPM difference to trigger smooth transition
        const val TEMPO_UPDATE_INTERVAL_MS = 50L // Update every 50ms for smooth transitions
    }
    
    /**
     * Set tempo with optional smooth transition
     * @param bpm Target tempo (60-200 BPM)
     * @param smooth Whether to transition smoothly or change immediately
     */
    fun setTempo(bpm: Float, smooth: Boolean = true) {
        val clampedTempo = bpm.coerceIn(MIN_TEMPO, MAX_TEMPO)
        
        if (smooth && abs(clampedTempo - _tempo.value) > TEMPO_TRANSITION_THRESHOLD) {
            startTempoTransition(clampedTempo)
        } else {
            _tempo.value = clampedTempo
            targetTempo = clampedTempo
            stopTempoTransition()
        }
    }
    
    /**
     * Adjust tempo by relative amount
     * @param deltaBpm Change in BPM (can be negative)
     * @param smooth Whether to transition smoothly
     */
    fun adjustTempo(deltaBpm: Float, smooth: Boolean = true) {
        val newTempo = _tempo.value + deltaBpm
        setTempo(newTempo, smooth)
    }
    
    /**
     * Set swing amount
     * @param amount Swing amount (0.0 = no swing, 0.75 = maximum swing)
     */
    fun setSwing(amount: Float) {
        _swing.value = amount.coerceIn(MIN_SWING, MAX_SWING)
    }
    
    /**
     * Adjust swing by relative amount
     * @param deltaAmount Change in swing amount
     */
    fun adjustSwing(deltaAmount: Float) {
        val newSwing = _swing.value + deltaAmount
        setSwing(newSwing)
    }
    
    /**
     * Set swing using MPC-style percentage (50-75%)
     * @param percentage Swing percentage (50 = no swing, 75 = maximum)
     */
    fun setSwingPercentage(percentage: Int) {
        val swingCalculator = SwingCalculator()
        val amount = swingCalculator.swingPercentageToAmount(percentage)
        setSwing(amount)
    }
    
    /**
     * Get current swing as MPC-style percentage
     */
    fun getSwingPercentage(): Int {
        val swingCalculator = SwingCalculator()
        return swingCalculator.swingAmountToPercentage(_swing.value)
    }
    
    /**
     * Apply a groove preset
     * @param presetName Name of the groove preset
     */
    fun applyGroovePreset(presetName: String) {
        val swingAmount = SwingCalculator.GROOVE_PRESETS[presetName] ?: 0f
        setSwing(swingAmount)
    }
    
    /**
     * Apply MPC-style swing preset
     * @param presetName MPC swing preset (e.g., "58%", "62%")
     */
    fun applyMpcSwingPreset(presetName: String) {
        val swingAmount = SwingCalculator.MPC_SWING_PRESETS[presetName] ?: 0f
        setSwing(swingAmount)
    }
    
    /**
     * Get available groove presets
     */
    fun getGroovePresets(): Map<String, Float> = SwingCalculator.GROOVE_PRESETS
    
    /**
     * Get available MPC swing presets
     */
    fun getMpcSwingPresets(): Map<String, Float> = SwingCalculator.MPC_SWING_PRESETS
    
    /**
     * Tap tempo functionality - call this method on each tap
     * @param tapTime Current time in milliseconds
     * @return Calculated BPM or null if not enough taps
     */
    fun tapTempo(tapTime: Long): Float? {
        return tapTempoCalculator.addTap(tapTime)
    }
    
    /**
     * Reset tap tempo
     */
    fun resetTapTempo() {
        tapTempoCalculator.reset()
    }
    
    /**
     * Check if tempo is within valid range
     */
    fun isValidTempo(bpm: Float): Boolean = bpm in MIN_TEMPO..MAX_TEMPO
    
    /**
     * Check if swing is within valid range
     */
    fun isValidSwing(amount: Float): Boolean = amount in MIN_SWING..MAX_SWING
    
    /**
     * Get tempo validation error message
     */
    fun getTempoValidationError(bpm: Float): String? {
        return when {
            bpm < MIN_TEMPO -> "Tempo too slow (minimum ${MIN_TEMPO.toInt()} BPM)"
            bpm > MAX_TEMPO -> "Tempo too fast (maximum ${MAX_TEMPO.toInt()} BPM)"
            else -> null
        }
    }
    
    /**
     * Get swing validation error message
     */
    fun getSwingValidationError(amount: Float): String? {
        return when {
            amount < MIN_SWING -> "Swing amount too low (minimum ${MIN_SWING})"
            amount > MAX_SWING -> "Swing amount too high (maximum ${MAX_SWING})"
            else -> null
        }
    }
    
    private fun startTempoTransition(targetBpm: Float) {
        targetTempo = targetBpm
        isTransitioning = true
        _isTempoChanging.value = true
        
        // Start transition loop (would typically use coroutines in real implementation)
        continueTempoTransition()
    }
    
    private fun continueTempoTransition() {
        if (!isTransitioning) return
        
        val currentTempo = _tempo.value
        val difference = targetTempo - currentTempo
        
        if (abs(difference) <= tempoTransitionRate) {
            // Transition complete
            _tempo.value = targetTempo
            stopTempoTransition()
        } else {
            // Continue transition
            val step = if (difference > 0) tempoTransitionRate else -tempoTransitionRate
            _tempo.value = currentTempo + step
            
            // Schedule next update (in real implementation, use coroutines with delay)
            // For now, this is a simplified version
        }
    }
    
    private fun stopTempoTransition() {
        isTransitioning = false
        _isTempoChanging.value = false
    }
    
    // Tap tempo calculator
    private val tapTempoCalculator = TapTempoCalculator()
}

/**
 * Tap tempo calculator for determining BPM from user taps
 */
private class TapTempoCalculator {
    private val tapTimes = mutableListOf<Long>()
    private val maxTaps = 8
    private val maxTapInterval = 3000L // 3 seconds max between taps
    
    fun addTap(tapTime: Long): Float? {
        // Remove old taps
        tapTimes.removeAll { tapTime - it > maxTapInterval }
        
        // Add new tap
        tapTimes.add(tapTime)
        
        // Need at least 2 taps to calculate tempo
        if (tapTimes.size < 2) return null
        
        // Keep only recent taps
        if (tapTimes.size > maxTaps) {
            tapTimes.removeAt(0)
        }
        
        // Calculate average interval
        val intervals = mutableListOf<Long>()
        for (i in 1 until tapTimes.size) {
            intervals.add(tapTimes[i] - tapTimes[i - 1])
        }
        
        val averageInterval = intervals.average()
        
        // Convert to BPM (60000ms per minute / interval in ms)
        val bpm = 60000.0 / averageInterval
        
        return bpm.toFloat().coerceIn(TempoSwingController.MIN_TEMPO, TempoSwingController.MAX_TEMPO)
    }
    
    fun reset() {
        tapTimes.clear()
    }
}

/**
 * Tempo change event for notifying listeners
 */
data class TempoChangeEvent(
    val oldTempo: Float,
    val newTempo: Float,
    val isSmooth: Boolean
)

/**
 * Swing change event for notifying listeners
 */
data class SwingChangeEvent(
    val oldSwing: Float,
    val newSwing: Float
)