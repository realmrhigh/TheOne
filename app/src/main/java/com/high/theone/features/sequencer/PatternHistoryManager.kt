package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.high.theone.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages pattern history for undo/redo functionality
 * Provides memory-efficient storage and operation tracking
 */
@Singleton
class PatternHistoryManager @Inject constructor() {
    
    private val _historyState = MutableStateFlow(PatternHistoryState())
    val historyState: StateFlow<PatternHistoryState> = _historyState.asStateFlow()
    
    private val historyMutex = Mutex()
    private val patternHistories = mutableMapOf<String, PatternHistory>()
    
    companion object {
        private const val MAX_HISTORY_SIZE = 50 // Maximum number of undo states per pattern
        private const val MAX_TOTAL_PATTERNS = 20 // Maximum number of patterns to track
    }
    
    /**
     * Save current pattern state before making changes
     */
    suspend fun saveState(
        pattern: Pattern,
        operation: HistoryOperation,
        description: String = ""
    ) = historyMutex.withLock {
        val patternId = pattern.id
        val history = patternHistories.getOrPut(patternId) { PatternHistory(patternId) }
        
        // Create history entry
        val entry = HistoryEntry(
            pattern = pattern.copy(), // Deep copy to avoid reference issues
            operation = operation,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        
        // Add to history and manage size
        val updatedHistory = history.addEntry(entry)
        patternHistories[patternId] = updatedHistory
        
        // Cleanup old pattern histories if we have too many
        cleanupOldHistories()
        
        updateHistoryState()
    }
    
    /**
     * Undo the last operation for a pattern
     */
    suspend fun undo(patternId: String): Pattern? = historyMutex.withLock {
        val history = patternHistories[patternId] ?: return null
        
        val (updatedHistory, undonePattern) = history.undo()
        if (updatedHistory != null && undonePattern != null) {
            patternHistories[patternId] = updatedHistory
            updateHistoryState()
            return undonePattern
        }
        
        return null
    }
    
    /**
     * Redo the last undone operation for a pattern
     */
    suspend fun redo(patternId: String): Pattern? = historyMutex.withLock {
        val history = patternHistories[patternId] ?: return null
        
        val (updatedHistory, redonePattern) = history.redo()
        if (updatedHistory != null && redonePattern != null) {
            patternHistories[patternId] = updatedHistory
            updateHistoryState()
            return redonePattern
        }
        
        return null
    }
    
    /**
     * Check if undo is available for a pattern
     */
    fun canUndo(patternId: String): Boolean {
        return patternHistories[patternId]?.canUndo() ?: false
    }
    
    /**
     * Check if redo is available for a pattern
     */
    fun canRedo(patternId: String): Boolean {
        return patternHistories[patternId]?.canRedo() ?: false
    }
    
    /**
     * Get the description of the next undo operation
     */
    fun getUndoDescription(patternId: String): String? {
        return patternHistories[patternId]?.getUndoDescription()
    }
    
    /**
     * Get the description of the next redo operation
     */
    fun getRedoDescription(patternId: String): String? {
        return patternHistories[patternId]?.getRedoDescription()
    }
    
    /**
     * Clear history for a specific pattern
     */
    suspend fun clearHistory(patternId: String) = historyMutex.withLock {
        patternHistories.remove(patternId)
        updateHistoryState()
    }
    
    /**
     * Clear all pattern histories
     */
    suspend fun clearAllHistories() = historyMutex.withLock {
        patternHistories.clear()
        updateHistoryState()
    }
    
    /**
     * Get history statistics for a pattern
     */
    fun getHistoryStats(patternId: String): HistoryStats? {
        return patternHistories[patternId]?.getStats()
    }
    
    /**
     * Cleanup old pattern histories to manage memory
     */
    private fun cleanupOldHistories() {
        if (patternHistories.size > MAX_TOTAL_PATTERNS) {
            // Remove oldest accessed histories
            val sortedByAccess = patternHistories.values.sortedBy { it.lastAccessTime }
            val toRemove = sortedByAccess.take(patternHistories.size - MAX_TOTAL_PATTERNS)
            
            toRemove.forEach { history ->
                patternHistories.remove(history.patternId)
            }
        }
    }
    
    /**
     * Update the history state for UI
     */
    private fun updateHistoryState() {
        val currentPatternId = _historyState.value.currentPatternId
        
        _historyState.update { state ->
            state.copy(
                canUndo = currentPatternId?.let { canUndo(it) } ?: false,
                canRedo = currentPatternId?.let { canRedo(it) } ?: false,
                undoDescription = currentPatternId?.let { getUndoDescription(it) },
                redoDescription = currentPatternId?.let { getRedoDescription(it) },
                totalPatterns = patternHistories.size
            )
        }
    }
    
    /**
     * Set the current pattern for history state updates
     */
    fun setCurrentPattern(patternId: String?) {
        _historyState.update { state ->
            val newState = state.copy(currentPatternId = patternId)
            
            if (patternId != null) {
                newState.copy(
                    canUndo = canUndo(patternId),
                    canRedo = canRedo(patternId),
                    undoDescription = getUndoDescription(patternId),
                    redoDescription = getRedoDescription(patternId)
                )
            } else {
                newState.copy(
                    canUndo = false,
                    canRedo = false,
                    undoDescription = null,
                    redoDescription = null
                )
            }
        }
    }
}

/**
 * History for a single pattern
 */
private data class PatternHistory(
    val patternId: String,
    val entries: List<HistoryEntry> = emptyList(),
    val currentIndex: Int = -1, // Index of current state in entries
    val lastAccessTime: Long = System.currentTimeMillis()
) {
    
    companion object {
        private const val MAX_HISTORY_SIZE = 50 // Maximum number of undo states per pattern
    }
    
    /**
     * Add a new history entry
     */
    fun addEntry(entry: HistoryEntry): PatternHistory {
        // Remove any redo entries when adding new entry
        val newEntries = if (currentIndex >= 0) {
            entries.take(currentIndex + 1) + entry
        } else {
            listOf(entry)
        }
        
        // Limit history size
        val limitedEntries = if (newEntries.size > MAX_HISTORY_SIZE) {
            newEntries.takeLast(MAX_HISTORY_SIZE)
        } else {
            newEntries
        }
        
        return copy(
            entries = limitedEntries,
            currentIndex = limitedEntries.size - 1,
            lastAccessTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Undo to previous state
     */
    fun undo(): Pair<PatternHistory?, Pattern?> {
        if (!canUndo()) return Pair(null, null)
        
        val newIndex = currentIndex - 1
        val previousEntry = entries.getOrNull(newIndex)
        
        return if (previousEntry != null) {
            Pair(
                copy(
                    currentIndex = newIndex,
                    lastAccessTime = System.currentTimeMillis()
                ),
                previousEntry.pattern
            )
        } else {
            Pair(null, null)
        }
    }
    
    /**
     * Redo to next state
     */
    fun redo(): Pair<PatternHistory?, Pattern?> {
        if (!canRedo()) return Pair(null, null)
        
        val newIndex = currentIndex + 1
        val nextEntry = entries.getOrNull(newIndex)
        
        return if (nextEntry != null) {
            Pair(
                copy(
                    currentIndex = newIndex,
                    lastAccessTime = System.currentTimeMillis()
                ),
                nextEntry.pattern
            )
        } else {
            Pair(null, null)
        }
    }
    
    /**
     * Check if undo is possible
     */
    fun canUndo(): Boolean = currentIndex > 0
    
    /**
     * Check if redo is possible
     */
    fun canRedo(): Boolean = currentIndex < entries.size - 1
    
    /**
     * Get description of undo operation
     */
    fun getUndoDescription(): String? {
        return if (canUndo()) {
            entries.getOrNull(currentIndex - 1)?.let { entry ->
                "Undo ${entry.operation.displayName}: ${entry.description}"
            }
        } else null
    }
    
    /**
     * Get description of redo operation
     */
    fun getRedoDescription(): String? {
        return if (canRedo()) {
            entries.getOrNull(currentIndex + 1)?.let { entry ->
                "Redo ${entry.operation.displayName}: ${entry.description}"
            }
        } else null
    }
    
    /**
     * Get history statistics
     */
    fun getStats(): HistoryStats {
        return HistoryStats(
            totalEntries = entries.size,
            currentIndex = currentIndex,
            canUndo = canUndo(),
            canRedo = canRedo(),
            memoryUsageBytes = estimateMemoryUsage()
        )
    }
    
    /**
     * Estimate memory usage of this history
     */
    private fun estimateMemoryUsage(): Long {
        // Rough estimate: each pattern entry ~1KB + overhead
        return entries.size * 1024L
    }
}

/**
 * A single entry in pattern history
 */
private data class HistoryEntry(
    val pattern: Pattern,
    val operation: HistoryOperation,
    val description: String,
    val timestamp: Long
)

/**
 * Types of operations that can be undone/redone
 */
enum class HistoryOperation(val displayName: String) {
    STEP_TOGGLE("Step Toggle"),
    STEP_VELOCITY("Velocity Change"),
    PATTERN_CLEAR("Clear Pattern"),
    PATTERN_COPY("Copy Pattern"),
    RECORDING("Recording"),
    OVERDUB("Overdub"),
    TEMPO_CHANGE("Tempo Change"),
    SWING_CHANGE("Swing Change"),
    PATTERN_LENGTH("Length Change"),
    STEP_MICRO_TIMING("Micro Timing"),
    PATTERN_QUANTIZE("Quantize")
}

/**
 * Current state of pattern history system
 */
data class PatternHistoryState(
    val currentPatternId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoDescription: String? = null,
    val redoDescription: String? = null,
    val totalPatterns: Int = 0
)

/**
 * Statistics about pattern history
 */
data class HistoryStats(
    val totalEntries: Int,
    val currentIndex: Int,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val memoryUsageBytes: Long
)