package com.high.theone.domain

import com.high.theone.model.Pattern
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for pattern management operations.
 * Handles CRUD operations, JSON-based persistence, and project-scoped organization.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
interface PatternRepository {
    
    /**
     * Save a pattern to the repository.
     * 
     * @param pattern Pattern to save
     * @param projectId Project ID for project-scoped storage
     * @return Result indicating success or failure
     */
    suspend fun savePattern(
        pattern: Pattern,
        projectId: String
    ): Result<Unit, Error>
    
    /**
     * Load a pattern by ID.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped lookup
     * @return Result containing Pattern on success
     */
    suspend fun loadPattern(
        patternId: String,
        projectId: String
    ): Result<Pattern, Error>
    
    /**
     * Load all patterns for a specific project.
     * 
     * @param projectId Project identifier
     * @return Result containing list of patterns
     */
    suspend fun loadAllPatterns(projectId: String): Result<List<Pattern>, Error>
    
    /**
     * Update an existing pattern.
     * 
     * @param pattern Updated pattern
     * @param projectId Project ID for project-scoped storage
     * @return Result indicating success or failure
     */
    suspend fun updatePattern(
        pattern: Pattern,
        projectId: String
    ): Result<Unit, Error>
    
    /**
     * Delete a pattern by ID.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped deletion
     * @return Result indicating success or failure
     */
    suspend fun deletePattern(
        patternId: String,
        projectId: String
    ): Result<Unit, Error>
    
    /**
     * Duplicate a pattern with a new name.
     * 
     * @param patternId Source pattern identifier
     * @param newName New name for the duplicated pattern
     * @param projectId Project ID for project-scoped operation
     * @return Result containing the new Pattern on success
     */
    suspend fun duplicatePattern(
        patternId: String,
        newName: String,
        projectId: String
    ): Result<Pattern, Error>
    
    /**
     * Rename a pattern.
     * 
     * @param patternId Pattern identifier
     * @param newName New name for the pattern
     * @param projectId Project ID for project-scoped operation
     * @return Result indicating success or failure
     */
    suspend fun renamePattern(
        patternId: String,
        newName: String,
        projectId: String
    ): Result<Unit, Error>
    
    /**
     * Check if a pattern exists.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped check
     * @return True if pattern exists, false otherwise
     */
    suspend fun patternExists(
        patternId: String,
        projectId: String
    ): Boolean
    
    /**
     * Get pattern metadata (name, creation date, etc.) without loading full pattern.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped lookup
     * @return Result containing PatternMetadata on success
     */
    suspend fun getPatternMetadata(
        patternId: String,
        projectId: String
    ): Result<PatternMetadata, Error>
    
    /**
     * Get all pattern metadata for a project for fast listing.
     * 
     * @param projectId Project identifier
     * @return Result containing list of pattern metadata
     */
    suspend fun getAllPatternMetadata(projectId: String): Result<List<PatternMetadata>, Error>
    
    /**
     * Search patterns by name.
     * 
     * @param query Search query string
     * @param projectId Project ID for project-scoped search
     * @return Result containing matching patterns
     */
    suspend fun searchPatterns(
        query: String,
        projectId: String
    ): Result<List<Pattern>, Error>
    
    /**
     * Get repository statistics for a project.
     * 
     * @param projectId Project identifier
     * @return Result containing pattern repository statistics
     */
    suspend fun getRepositoryStats(projectId: String): Result<PatternRepositoryStats, Error>
    
    /**
     * Observe changes to patterns in a project.
     * 
     * @param projectId Project identifier
     * @return Flow of pattern change events
     */
    fun observePatternChanges(projectId: String): Flow<PatternChangeEvent>
    
    /**
     * Clean up orphaned pattern files and invalid references.
     * 
     * @param projectId Project identifier
     * @return Result containing cleanup statistics
     */
    suspend fun cleanupRepository(projectId: String): Result<PatternCleanupStats, Error>
    
    /**
     * Validate and repair pattern repository integrity.
     * 
     * @param projectId Project identifier
     * @return Result containing validation statistics
     */
    suspend fun validateAndRepairRepository(projectId: String): Result<PatternValidationStats, Error>
}

/**
 * Lightweight pattern metadata for fast listing and indexing.
 */
data class PatternMetadata(
    val id: String,
    val name: String,
    val length: Int,
    val tempo: Float,
    val swing: Float,
    val createdAt: Long,
    val modifiedAt: Long,
    val stepCount: Int // Total number of active steps across all pads
)

/**
 * Statistics about the pattern repository for a project.
 */
data class PatternRepositoryStats(
    val totalPatterns: Int,
    val totalSteps: Int,
    val averagePatternLength: Float,
    val averageTempo: Float,
    val patternsByLength: Map<Int, Int>,
    val oldestPatternDate: Long,
    val newestPatternDate: Long
)

/**
 * Event representing a change in the pattern repository.
 */
sealed class PatternChangeEvent {
    data class PatternAdded(val pattern: Pattern) : PatternChangeEvent()
    data class PatternUpdated(val pattern: Pattern) : PatternChangeEvent()
    data class PatternDeleted(val patternId: String) : PatternChangeEvent()
    data class PatternRenamed(val patternId: String, val oldName: String, val newName: String) : PatternChangeEvent()
    data class PatternDuplicated(val sourceId: String, val newPattern: Pattern) : PatternChangeEvent()
}

/**
 * Statistics from pattern repository cleanup operation.
 */
data class PatternCleanupStats(
    val orphanedFilesRemoved: Int,
    val invalidReferencesRemoved: Int,
    val spaceFreeBytes: Long
)

/**
 * Statistics from pattern repository validation and repair operation.
 */
data class PatternValidationStats(
    val totalPatterns: Int,
    val validPatterns: Int,
    val repairedPatterns: Int,
    val invalidPatterns: Int,
    val corruptedFiles: Int
)