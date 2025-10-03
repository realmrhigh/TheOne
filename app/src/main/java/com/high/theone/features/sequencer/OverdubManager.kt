package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages overdub and layering functionality for pattern recording
 * Provides advanced pattern merging and selective recording capabilities
 */
@Singleton
class OverdubManager @Inject constructor() {
    
    private val _overdubState = MutableStateFlow(OverdubState())
    val overdubState: StateFlow<OverdubState> = _overdubState.asStateFlow()
    
    private val overdubMutex = Mutex()
    
    /**
     * Configure overdub settings for recording session
     */
    suspend fun configureOverdub(
        selectedPads: Set<Int> = emptySet(),
        punchInStep: Int? = null,
        punchOutStep: Int? = null,
        layerMode: LayerMode = LayerMode.ADD,
        velocityMode: VelocityMode = VelocityMode.REPLACE
    ) = overdubMutex.withLock {
        _overdubState.update {
            it.copy(
                selectedPads = selectedPads,
                punchInStep = punchInStep,
                punchOutStep = punchOutStep,
                layerMode = layerMode,
                velocityMode = velocityMode,
                isConfigured = true
            )
        }
    }
    
    /**
     * Clear overdub configuration
     */
    suspend fun clearOverdubConfig() = overdubMutex.withLock {
        _overdubState.update { OverdubState() }
    }
    
    /**
     * Check if a pad hit should be recorded based on overdub settings
     */
    fun shouldRecordHit(
        padIndex: Int,
        currentStep: Int,
        stepProgress: Float
    ): Boolean {
        val state = _overdubState.value
        
        // Check if pad is selected for recording
        if (state.selectedPads.isNotEmpty() && !state.selectedPads.contains(padIndex)) {
            return false
        }
        
        // Check punch-in/punch-out boundaries
        return isWithinPunchRange(currentStep, stepProgress, state)
    }
    
    /**
     * Merge new steps with existing pattern using overdub settings
     */
    suspend fun mergeWithOverdub(
        existingPattern: Pattern,
        newSteps: Map<Int, List<Step>>
    ): Pattern = overdubMutex.withLock {
        val state = _overdubState.value
        
        when (state.layerMode) {
            LayerMode.ADD -> mergeStepsAdditive(existingPattern, newSteps, state)
            LayerMode.REPLACE -> mergeStepsReplace(existingPattern, newSteps, state)
            LayerMode.MULTIPLY -> mergeStepsMultiply(existingPattern, newSteps, state)
        }
    }
    
    /**
     * Check if current position is within punch-in/punch-out range
     */
    private fun isWithinPunchRange(
        currentStep: Int,
        stepProgress: Float,
        state: OverdubState
    ): Boolean {
        val exactPosition = currentStep + stepProgress
        
        val punchIn = state.punchInStep?.toFloat() ?: 0f
        val punchOut = state.punchOutStep?.toFloat() ?: Float.MAX_VALUE
        
        return exactPosition >= punchIn && exactPosition <= punchOut
    }
    
    /**
     * Merge steps using additive layering (combine velocities)
     */
    private fun mergeStepsAdditive(
        existingPattern: Pattern,
        newSteps: Map<Int, List<Step>>,
        state: OverdubState
    ): Pattern {
        val mergedSteps = existingPattern.steps.toMutableMap()
        
        newSteps.forEach { (padIndex, steps) ->
            val existingSteps = mergedSteps[padIndex] ?: emptyList()
            val combinedSteps = combineStepsAdditive(existingSteps, steps, state.velocityMode)
            
            if (combinedSteps.isNotEmpty()) {
                mergedSteps[padIndex] = combinedSteps
            }
        }
        
        return existingPattern.copy(
            steps = mergedSteps,
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Merge steps using replace layering (replace existing steps)
     */
    private fun mergeStepsReplace(
        existingPattern: Pattern,
        newSteps: Map<Int, List<Step>>,
        state: OverdubState
    ): Pattern {
        val mergedSteps = existingPattern.steps.toMutableMap()
        
        newSteps.forEach { (padIndex, steps) ->
            if (state.selectedPads.isEmpty() || state.selectedPads.contains(padIndex)) {
                // Remove existing steps in the punch range
                val existingSteps = mergedSteps[padIndex] ?: emptyList()
                val filteredSteps = filterStepsOutsidePunchRange(existingSteps, state)
                
                // Add new steps
                val combinedSteps = (filteredSteps + steps)
                    .distinctBy { it.position }
                    .sortedBy { it.position }
                
                if (combinedSteps.isNotEmpty()) {
                    mergedSteps[padIndex] = combinedSteps
                } else {
                    mergedSteps.remove(padIndex)
                }
            }
        }
        
        return existingPattern.copy(
            steps = mergedSteps,
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Merge steps using multiply layering (trigger existing and new)
     */
    private fun mergeStepsMultiply(
        existingPattern: Pattern,
        newSteps: Map<Int, List<Step>>,
        state: OverdubState
    ): Pattern {
        val mergedSteps = existingPattern.steps.toMutableMap()
        
        newSteps.forEach { (padIndex, steps) ->
            val existingSteps = mergedSteps[padIndex] ?: emptyList()
            
            // For multiply mode, we keep both existing and new steps
            // but adjust velocities based on velocity mode
            val combinedSteps = combineStepsMultiply(existingSteps, steps, state.velocityMode)
            
            if (combinedSteps.isNotEmpty()) {
                mergedSteps[padIndex] = combinedSteps
            }
        }
        
        return existingPattern.copy(
            steps = mergedSteps,
            modifiedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Combine steps using additive approach
     */
    private fun combineStepsAdditive(
        existingSteps: List<Step>,
        newSteps: List<Step>,
        velocityMode: VelocityMode
    ): List<Step> {
        val stepMap = existingSteps.associateBy { it.position }.toMutableMap()
        
        newSteps.forEach { newStep ->
            val existingStep = stepMap[newStep.position]
            
            if (existingStep != null) {
                // Combine velocities based on velocity mode
                val combinedVelocity = when (velocityMode) {
                    VelocityMode.ADD -> (existingStep.velocity + newStep.velocity).coerceAtMost(127)
                    VelocityMode.AVERAGE -> (existingStep.velocity + newStep.velocity) / 2
                    VelocityMode.MAX -> maxOf(existingStep.velocity, newStep.velocity)
                    VelocityMode.REPLACE -> newStep.velocity
                }
                
                stepMap[newStep.position] = existingStep.copy(
                    velocity = combinedVelocity,
                    microTiming = newStep.microTiming // Use new timing
                )
            } else {
                stepMap[newStep.position] = newStep
            }
        }
        
        return stepMap.values.sortedBy { it.position }
    }
    
    /**
     * Combine steps using multiply approach
     */
    private fun combineStepsMultiply(
        existingSteps: List<Step>,
        newSteps: List<Step>,
        velocityMode: VelocityMode
    ): List<Step> {
        val allSteps = mutableListOf<Step>()
        
        // Add all existing steps
        allSteps.addAll(existingSteps)
        
        // Add new steps, potentially creating multiple triggers at same position
        newSteps.forEach { newStep ->
            val existingStep = existingSteps.find { it.position == newStep.position }
            
            if (existingStep != null) {
                // Modify existing step velocity if needed
                when (velocityMode) {
                    VelocityMode.ADD -> {
                        val index = allSteps.indexOfFirst { it.position == newStep.position }
                        if (index >= 0) {
                            allSteps[index] = allSteps[index].copy(
                                velocity = (allSteps[index].velocity + newStep.velocity).coerceAtMost(127)
                            )
                        }
                    }
                    VelocityMode.AVERAGE -> {
                        val index = allSteps.indexOfFirst { it.position == newStep.position }
                        if (index >= 0) {
                            allSteps[index] = allSteps[index].copy(
                                velocity = (allSteps[index].velocity + newStep.velocity) / 2
                            )
                        }
                    }
                    VelocityMode.MAX -> {
                        val index = allSteps.indexOfFirst { it.position == newStep.position }
                        if (index >= 0) {
                            allSteps[index] = allSteps[index].copy(
                                velocity = maxOf(allSteps[index].velocity, newStep.velocity)
                            )
                        }
                    }
                    VelocityMode.REPLACE -> {
                        val index = allSteps.indexOfFirst { it.position == newStep.position }
                        if (index >= 0) {
                            allSteps[index] = allSteps[index].copy(
                                velocity = newStep.velocity,
                                microTiming = newStep.microTiming
                            )
                        }
                    }
                }
            } else {
                allSteps.add(newStep)
            }
        }
        
        return allSteps.sortedBy { it.position }
    }
    
    /**
     * Filter out steps that are within the punch range
     */
    private fun filterStepsOutsidePunchRange(
        steps: List<Step>,
        state: OverdubState
    ): List<Step> {
        val punchIn = state.punchInStep ?: 0
        val punchOut = state.punchOutStep ?: Int.MAX_VALUE
        
        return steps.filter { step ->
            step.position < punchIn || step.position > punchOut
        }
    }
    
    /**
     * Set punch-in point for recording
     */
    fun setPunchIn(stepPosition: Int?) {
        _overdubState.update { it.copy(punchInStep = stepPosition) }
    }
    
    /**
     * Set punch-out point for recording
     */
    fun setPunchOut(stepPosition: Int?) {
        _overdubState.update { it.copy(punchOutStep = stepPosition) }
    }
    
    /**
     * Toggle pad selection for overdub recording
     */
    fun togglePadSelection(padIndex: Int) {
        _overdubState.update { state ->
            val selectedPads = state.selectedPads.toMutableSet()
            if (selectedPads.contains(padIndex)) {
                selectedPads.remove(padIndex)
            } else {
                selectedPads.add(padIndex)
            }
            state.copy(selectedPads = selectedPads)
        }
    }
    
    /**
     * Set layer mode for overdub recording
     */
    fun setLayerMode(mode: LayerMode) {
        _overdubState.update { it.copy(layerMode = mode) }
    }
    
    /**
     * Set velocity mode for overdub recording
     */
    fun setVelocityMode(mode: VelocityMode) {
        _overdubState.update { it.copy(velocityMode = mode) }
    }
}

/**
 * Current state of overdub configuration
 */
data class OverdubState(
    val selectedPads: Set<Int> = emptySet(),
    val punchInStep: Int? = null,
    val punchOutStep: Int? = null,
    val layerMode: LayerMode = LayerMode.ADD,
    val velocityMode: VelocityMode = VelocityMode.REPLACE,
    val isConfigured: Boolean = false
) {
    /**
     * Returns true if punch-in/punch-out is configured
     */
    val hasPunchRange: Boolean
        get() = punchInStep != null || punchOutStep != null
    
    /**
     * Returns true if selective pad recording is configured
     */
    val hasSelectedPads: Boolean
        get() = selectedPads.isNotEmpty()
}

/**
 * How new steps are layered with existing steps
 */
enum class LayerMode(val displayName: String) {
    ADD("Add"),           // Add new steps to existing
    REPLACE("Replace"),   // Replace existing steps in punch range
    MULTIPLY("Multiply")  // Keep both existing and new steps
}

/**
 * How velocities are combined when layering steps
 */
enum class VelocityMode(val displayName: String) {
    ADD("Add"),           // Add velocities together (clamped to 127)
    AVERAGE("Average"),   // Average the velocities
    MAX("Maximum"),       // Use the higher velocity
    REPLACE("Replace")    // Use the new velocity
}