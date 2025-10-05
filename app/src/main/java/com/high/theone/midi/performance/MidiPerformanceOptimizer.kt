package com.high.theone.midi.performance

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatically optimizes MIDI system performance based on monitoring data.
 * Applies performance optimizations and maintains optimal settings.
 * 
 * Requirements: 7.4, 7.5
 */
@Singleton
class MidiPerformanceOptimizer @Inject constructor(
    private val context: Context,
    private val performanceMonitor: MidiPerformanceMonitor
) {
    
    companion object {
        private const val TAG = "MidiPerformanceOptimizer"
        private const val PREFS_NAME = "midi_performance_optimizer"
        private const val KEY_AUTO_OPTIMIZE = "auto_optimize_enabled"
        private const val KEY_OPTIMIZATION_LEVEL = "optimization_level"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_MESSAGE_FILTERING = "message_filtering_enabled"
        private const val KEY_LATENCY_COMPENSATION = "latency_compensation"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Optimization state
    private val _optimizationSettings = MutableStateFlow(loadOptimizationSettings())
    val optimizationSettings: StateFlow<MidiOptimizationSettings> = _optimizationSettings.asStateFlow()
    
    private val _appliedOptimizations = MutableStateFlow<List<AppliedOptimization>>(emptyList())
    val appliedOptimizations: StateFlow<List<AppliedOptimization>> = _appliedOptimizations.asStateFlow()
    
    private val _optimizationHistory = MutableStateFlow<List<OptimizationHistoryEntry>>(emptyList())
    val optimizationHistory: StateFlow<List<OptimizationHistoryEntry>> = _optimizationHistory.asStateFlow()
    
    /**
     * Apply automatic optimizations based on current performance
     */
    suspend fun applyAutomaticOptimizations() {
        if (!_optimizationSettings.value.autoOptimizeEnabled) {
            Log.d(TAG, "Auto-optimization disabled")
            return
        }
        
        val metrics = performanceMonitor.currentMetrics.value
        val recommendations = performanceMonitor.optimizationRecommendations.value
        
        Log.i(TAG, "Applying automatic optimizations based on ${recommendations.size} recommendations")
        
        val appliedOptimizations = mutableListOf<AppliedOptimization>()
        
        for (recommendation in recommendations) {
            if (recommendation.priority == OptimizationPriority.HIGH || 
                recommendation.priority == OptimizationPriority.CRITICAL) {
                
                val optimization = applyOptimization(recommendation, metrics)
                if (optimization != null) {
                    appliedOptimizations.add(optimization)
                }
            }
        }
        
        if (appliedOptimizations.isNotEmpty()) {
            _appliedOptimizations.value = appliedOptimizations
            recordOptimizationHistory(appliedOptimizations)
            Log.i(TAG, "Applied ${appliedOptimizations.size} automatic optimizations")
        }
    }
    
    /**
     * Apply a specific optimization recommendation
     */
    suspend fun applyOptimizationRecommendation(recommendation: MidiOptimizationRecommendation): Boolean {
        Log.i(TAG, "Applying optimization: ${recommendation.title}")
        
        val metrics = performanceMonitor.currentMetrics.value
        val optimization = applyOptimization(recommendation, metrics)
        
        return if (optimization != null) {
            val current = _appliedOptimizations.value.toMutableList()
            current.add(optimization)
            _appliedOptimizations.value = current
            
            recordOptimizationHistory(listOf(optimization))
            true
        } else {
            false
        }
    }
    
    /**
     * Update optimization settings
     */
    fun updateOptimizationSettings(settings: MidiOptimizationSettings) {
        Log.i(TAG, "Updating optimization settings")
        _optimizationSettings.value = settings
        saveOptimizationSettings(settings)
    }
    
    /**
     * Get optimal buffer size based on current performance
     */
    fun getOptimalBufferSize(): Int {
        val metrics = performanceMonitor.currentMetrics.value
        val currentSettings = _optimizationSettings.value
        
        return when {
            metrics.averageLatency > 20f -> {
                // High latency - reduce buffer size
                maxOf(64, currentSettings.bufferSize / 2)
            }
            metrics.droppedMessages > 0 -> {
                // Dropped messages - increase buffer size
                minOf(2048, currentSettings.bufferSize * 2)
            }
            metrics.cpuUsage > 80f -> {
                // High CPU - increase buffer size to reduce processing frequency
                minOf(1024, currentSettings.bufferSize + 128)
            }
            else -> currentSettings.bufferSize
        }
    }
    
    /**
     * Get optimal message filtering settings
     */
    fun getOptimalMessageFiltering(): MidiMessageFilterSettings {
        val metrics = performanceMonitor.currentMetrics.value
        
        return MidiMessageFilterSettings(
            enabled = metrics.throughput > 500 || metrics.cpuUsage > 70f,
            filterAftertouch = metrics.throughput > 800,
            filterContinuousControllers = metrics.throughput > 600,
            filterPitchBend = false, // Keep pitch bend as it's important
            filterSystemExclusive = metrics.throughput > 400,
            maxMessagesPerSecond = when {
                metrics.cpuUsage > 80f -> 300
                metrics.cpuUsage > 60f -> 500
                else -> 1000
            }
        )
    }
    
    /**
     * Get optimal latency compensation settings
     */
    fun getOptimalLatencyCompensation(): Float {
        val metrics = performanceMonitor.currentMetrics.value
        
        return when {
            metrics.averageLatency < 5f -> 0f
            metrics.averageLatency < 10f -> metrics.averageLatency * 0.5f
            metrics.averageLatency < 20f -> metrics.averageLatency * 0.7f
            else -> metrics.averageLatency * 0.8f
        }
    }
    
    /**
     * Revert a specific optimization
     */
    suspend fun revertOptimization(optimizationId: String): Boolean {
        Log.i(TAG, "Reverting optimization: $optimizationId")
        
        val current = _appliedOptimizations.value.toMutableList()
        val optimization = current.find { it.id == optimizationId }
        
        return if (optimization != null) {
            // Apply reverse optimization
            val reverted = revertOptimizationInternal(optimization)
            if (reverted) {
                current.remove(optimization)
                _appliedOptimizations.value = current
                
                recordOptimizationHistory(listOf(
                    AppliedOptimization(
                        id = "revert_${optimization.id}",
                        type = optimization.type,
                        description = "Reverted: ${optimization.description}",
                        timestamp = System.currentTimeMillis(),
                        effectivenessMeasured = false
                    )
                ))
            }
            reverted
        } else {
            false
        }
    }
    
    /**
     * Revert all optimizations
     */
    suspend fun revertAllOptimizations(): Boolean {
        Log.i(TAG, "Reverting all optimizations")
        
        val current = _appliedOptimizations.value
        var allReverted = true
        
        for (optimization in current) {
            if (!revertOptimizationInternal(optimization)) {
                allReverted = false
            }
        }
        
        if (allReverted) {
            _appliedOptimizations.value = emptyList()
        }
        
        return allReverted
    }
    
    /**
     * Get performance improvement suggestions
     */
    fun getPerformanceImprovementSuggestions(): List<PerformanceImprovementSuggestion> {
        val metrics = performanceMonitor.currentMetrics.value
        val suggestions = mutableListOf<PerformanceImprovementSuggestion>()
        
        // Latency improvements
        if (metrics.averageLatency > 10f) {
            suggestions.add(
                PerformanceImprovementSuggestion(
                    category = ImprovementCategory.LATENCY,
                    title = "Reduce Audio Latency",
                    description = "Lower buffer sizes and enable low-latency mode",
                    impact = ImprovementImpact.HIGH,
                    difficulty = ImprovementDifficulty.EASY,
                    steps = listOf(
                        "Go to Audio Settings",
                        "Reduce buffer size to 128 samples or lower",
                        "Enable low-latency audio mode",
                        "Close background apps"
                    )
                )
            )
        }
        
        // CPU optimization
        if (metrics.cpuUsage > 70f) {
            suggestions.add(
                PerformanceImprovementSuggestion(
                    category = ImprovementCategory.CPU,
                    title = "Reduce CPU Usage",
                    description = "Optimize processing to reduce CPU load",
                    impact = ImprovementImpact.MEDIUM,
                    difficulty = ImprovementDifficulty.MEDIUM,
                    steps = listOf(
                        "Enable MIDI message filtering",
                        "Reduce number of active effects",
                        "Lower audio quality if needed",
                        "Enable CPU optimization mode"
                    )
                )
            )
        }
        
        // Memory optimization
        if (metrics.memoryUsage > 80f) {
            suggestions.add(
                PerformanceImprovementSuggestion(
                    category = ImprovementCategory.MEMORY,
                    title = "Free Memory",
                    description = "Reduce memory usage to improve performance",
                    impact = ImprovementImpact.MEDIUM,
                    difficulty = ImprovementDifficulty.EASY,
                    steps = listOf(
                        "Clear sample cache",
                        "Unload unused samples",
                        "Reduce sample quality",
                        "Restart app if needed"
                    )
                )
            )
        }
        
        return suggestions
    }
    
    // Private helper methods
    
    private suspend fun applyOptimization(
        recommendation: MidiOptimizationRecommendation,
        metrics: MidiPerformanceMetrics
    ): AppliedOptimization? {
        
        return when (recommendation.type) {
            OptimizationType.REDUCE_LATENCY -> {
                val newBufferSize = getOptimalBufferSize()
                if (newBufferSize != _optimizationSettings.value.bufferSize) {
                    updateBufferSize(newBufferSize)
                    AppliedOptimization(
                        id = "buffer_size_${System.currentTimeMillis()}",
                        type = recommendation.type,
                        description = "Reduced buffer size to $newBufferSize",
                        timestamp = System.currentTimeMillis()
                    )
                } else null
            }
            
            OptimizationType.REDUCE_THROUGHPUT -> {
                val filterSettings = getOptimalMessageFiltering()
                if (filterSettings.enabled && !_optimizationSettings.value.messageFilteringEnabled) {
                    enableMessageFiltering(filterSettings)
                    AppliedOptimization(
                        id = "message_filtering_${System.currentTimeMillis()}",
                        type = recommendation.type,
                        description = "Enabled MIDI message filtering",
                        timestamp = System.currentTimeMillis()
                    )
                } else null
            }
            
            OptimizationType.PREVENT_DROPS -> {
                val newBufferSize = minOf(2048, _optimizationSettings.value.bufferSize * 2)
                updateBufferSize(newBufferSize)
                AppliedOptimization(
                    id = "prevent_drops_${System.currentTimeMillis()}",
                    type = recommendation.type,
                    description = "Increased buffer size to prevent drops",
                    timestamp = System.currentTimeMillis()
                )
            }
            
            else -> {
                Log.w(TAG, "Optimization type not implemented: ${recommendation.type}")
                null
            }
        }
    }
    
    private fun updateBufferSize(newSize: Int) {
        val current = _optimizationSettings.value
        val updated = current.copy(bufferSize = newSize)
        _optimizationSettings.value = updated
        saveOptimizationSettings(updated)
        Log.i(TAG, "Updated buffer size to $newSize")
    }
    
    private fun enableMessageFiltering(settings: MidiMessageFilterSettings) {
        val current = _optimizationSettings.value
        val updated = current.copy(
            messageFilteringEnabled = true,
            messageFilterSettings = settings
        )
        _optimizationSettings.value = updated
        saveOptimizationSettings(updated)
        Log.i(TAG, "Enabled MIDI message filtering")
    }
    
    private suspend fun revertOptimizationInternal(optimization: AppliedOptimization): Boolean {
        return when (optimization.type) {
            OptimizationType.REDUCE_LATENCY -> {
                // Revert buffer size change
                val defaultBufferSize = 256
                updateBufferSize(defaultBufferSize)
                true
            }
            
            OptimizationType.REDUCE_THROUGHPUT -> {
                // Disable message filtering
                val current = _optimizationSettings.value
                val updated = current.copy(messageFilteringEnabled = false)
                _optimizationSettings.value = updated
                saveOptimizationSettings(updated)
                true
            }
            
            else -> {
                Log.w(TAG, "Revert not implemented for: ${optimization.type}")
                false
            }
        }
    }
    
    private fun recordOptimizationHistory(optimizations: List<AppliedOptimization>) {
        val current = _optimizationHistory.value.toMutableList()
        
        for (optimization in optimizations) {
            current.add(0, OptimizationHistoryEntry(
                optimization = optimization,
                performanceBeforeOptimization = performanceMonitor.currentMetrics.value,
                timestamp = System.currentTimeMillis()
            ))
        }
        
        // Keep only last 50 entries
        if (current.size > 50) {
            current.subList(50, current.size).clear()
        }
        
        _optimizationHistory.value = current
    }
    
    private fun loadOptimizationSettings(): MidiOptimizationSettings {
        return MidiOptimizationSettings(
            autoOptimizeEnabled = preferences.getBoolean(KEY_AUTO_OPTIMIZE, true),
            optimizationLevel = OptimizationLevel.valueOf(
                preferences.getString(KEY_OPTIMIZATION_LEVEL, OptimizationLevel.BALANCED.name) 
                    ?: OptimizationLevel.BALANCED.name
            ),
            bufferSize = preferences.getInt(KEY_BUFFER_SIZE, 256),
            messageFilteringEnabled = preferences.getBoolean(KEY_MESSAGE_FILTERING, false),
            latencyCompensation = preferences.getFloat(KEY_LATENCY_COMPENSATION, 0f)
        )
    }
    
    private fun saveOptimizationSettings(settings: MidiOptimizationSettings) {
        preferences.edit()
            .putBoolean(KEY_AUTO_OPTIMIZE, settings.autoOptimizeEnabled)
            .putString(KEY_OPTIMIZATION_LEVEL, settings.optimizationLevel.name)
            .putInt(KEY_BUFFER_SIZE, settings.bufferSize)
            .putBoolean(KEY_MESSAGE_FILTERING, settings.messageFilteringEnabled)
            .putFloat(KEY_LATENCY_COMPENSATION, settings.latencyCompensation)
            .apply()
    }
}

/**
 * MIDI optimization settings
 */
data class MidiOptimizationSettings(
    val autoOptimizeEnabled: Boolean = true,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.BALANCED,
    val bufferSize: Int = 256,
    val messageFilteringEnabled: Boolean = false,
    val messageFilterSettings: MidiMessageFilterSettings = MidiMessageFilterSettings(),
    val latencyCompensation: Float = 0f
)

/**
 * Optimization levels
 */
enum class OptimizationLevel {
    PERFORMANCE,  // Prioritize performance over quality
    BALANCED,     // Balance performance and quality
    QUALITY       // Prioritize quality over performance
}

/**
 * MIDI message filtering settings
 */
data class MidiMessageFilterSettings(
    val enabled: Boolean = false,
    val filterAftertouch: Boolean = false,
    val filterContinuousControllers: Boolean = false,
    val filterPitchBend: Boolean = false,
    val filterSystemExclusive: Boolean = false,
    val maxMessagesPerSecond: Int = 1000
)

/**
 * Applied optimization record
 */
data class AppliedOptimization(
    val id: String,
    val type: OptimizationType,
    val description: String,
    val timestamp: Long,
    val effectivenessMeasured: Boolean = false
)

/**
 * Optimization history entry
 */
data class OptimizationHistoryEntry(
    val optimization: AppliedOptimization,
    val performanceBeforeOptimization: MidiPerformanceMetrics,
    val timestamp: Long
)

/**
 * Performance improvement suggestion
 */
data class PerformanceImprovementSuggestion(
    val category: ImprovementCategory,
    val title: String,
    val description: String,
    val impact: ImprovementImpact,
    val difficulty: ImprovementDifficulty,
    val steps: List<String>
)

/**
 * Improvement categories
 */
enum class ImprovementCategory {
    LATENCY,
    CPU,
    MEMORY,
    THROUGHPUT,
    STABILITY
}

/**
 * Improvement impact levels
 */
enum class ImprovementImpact {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Improvement difficulty levels
 */
enum class ImprovementDifficulty {
    EASY,
    MEDIUM,
    HARD
}