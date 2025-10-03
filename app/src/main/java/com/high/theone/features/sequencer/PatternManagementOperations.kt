package com.high.theone.features.sequencer

import com.high.theone.domain.PatternRepository
import com.high.theone.domain.Result
import com.high.theone.domain.Error
import com.high.theone.model.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level pattern management operations with enhanced error handling and validation.
 * Provides user-friendly interfaces for pattern CRUD operations.
 * 
 * Requirements: 3.3, 3.4, 3.5, 3.7
 */
@Singleton
class PatternManagementOperations @Inject constructor(
    private val patternRepository: PatternRepository
) {
    
    /**
     * Save a pattern with comprehensive validation and error handling.
     * 
     * @param pattern Pattern to save
     * @param projectId Project ID for project-scoped storage
     * @return Result with detailed error information
     */
    suspend fun savePatternWithValidation(
        pattern: Pattern,
        projectId: String
    ): Result<Unit, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            // Validate project ID
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            // Validate pattern name uniqueness
            val existingPatternsResult = patternRepository.getAllPatternMetadata(projectId)
            if (existingPatternsResult.isSuccess) {
                val existingPatterns = existingPatternsResult.getOrNull()!!
                val nameExists = existingPatterns.any { 
                    it.name.equals(pattern.name, ignoreCase = true) && it.id != pattern.id 
                }
                if (nameExists) {
                    return@withContext Result.Failure(PatternManagementError.DuplicateName(pattern.name))
                }
            }
            
            // Save pattern
            val saveResult = patternRepository.savePattern(pattern, projectId)
            if (saveResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.SaveFailed(saveResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Load a pattern with enhanced error reporting.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped lookup
     * @return Result with pattern or detailed error
     */
    suspend fun loadPatternWithErrorHandling(
        patternId: String,
        projectId: String
    ): Result<Pattern, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            if (patternId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternId)
            }
            
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            val loadResult = patternRepository.loadPattern(patternId, projectId)
            if (loadResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.LoadFailed(patternId, loadResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            Result.Success(loadResult.getOrNull()!!)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Delete a pattern with confirmation and safety checks.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped deletion
     * @param confirmationToken Token to confirm deletion intent
     * @return Result with deletion status
     */
    suspend fun deletePatternWithConfirmation(
        patternId: String,
        projectId: String,
        confirmationToken: String
    ): Result<PatternDeletionResult, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            if (patternId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternId)
            }
            
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            // Validate confirmation token (simple implementation)
            val expectedToken = generateConfirmationToken(patternId)
            if (confirmationToken != expectedToken) {
                return@withContext Result.Failure(PatternManagementError.InvalidConfirmation)
            }
            
            // Check if pattern exists and get metadata for result
            val metadataResult = patternRepository.getPatternMetadata(patternId, projectId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.LoadFailed(patternId, "Pattern not found")
                )
            }
            
            val metadata = metadataResult.getOrNull()!!
            
            // Perform deletion
            val deleteResult = patternRepository.deletePattern(patternId, projectId)
            if (deleteResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.DeleteFailed(patternId, deleteResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            val deletionResult = PatternDeletionResult(
                patternId = patternId,
                patternName = metadata.name,
                deletedAt = System.currentTimeMillis()
            )
            
            Result.Success(deletionResult)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Duplicate a pattern with name validation and conflict resolution.
     * 
     * @param sourcePatternId Source pattern identifier
     * @param newName New name for the duplicated pattern
     * @param projectId Project ID for project-scoped operation
     * @param resolveNameConflict Whether to auto-resolve name conflicts
     * @return Result with new pattern or error
     */
    suspend fun duplicatePatternWithValidation(
        sourcePatternId: String,
        newName: String,
        projectId: String,
        resolveNameConflict: Boolean = true
    ): Result<Pattern, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            if (sourcePatternId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternId)
            }
            
            if (newName.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternName)
            }
            
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            // Resolve name conflicts if requested
            val finalName = if (resolveNameConflict) {
                resolveNameConflict(newName, projectId)
            } else {
                // Check for name conflicts
                val existingPatternsResult = patternRepository.getAllPatternMetadata(projectId)
                if (existingPatternsResult.isSuccess) {
                    val existingPatterns = existingPatternsResult.getOrNull()!!
                    val nameExists = existingPatterns.any { 
                        it.name.equals(newName, ignoreCase = true) 
                    }
                    if (nameExists) {
                        return@withContext Result.Failure(PatternManagementError.DuplicateName(newName))
                    }
                }
                newName
            }
            
            // Perform duplication
            val duplicateResult = patternRepository.duplicatePattern(sourcePatternId, finalName, projectId)
            if (duplicateResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.DuplicationFailed(
                        sourcePatternId, 
                        duplicateResult.errorOrNull()?.message ?: "Unknown error"
                    )
                )
            }
            
            Result.Success(duplicateResult.getOrNull()!!)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Rename a pattern with validation and conflict resolution.
     * 
     * @param patternId Pattern identifier
     * @param newName New name for the pattern
     * @param projectId Project ID for project-scoped operation
     * @return Result with rename status
     */
    suspend fun renamePatternWithValidation(
        patternId: String,
        newName: String,
        projectId: String
    ): Result<PatternRenameResult, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            if (patternId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternId)
            }
            
            if (newName.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternName)
            }
            
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            // Get current pattern metadata
            val metadataResult = patternRepository.getPatternMetadata(patternId, projectId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.LoadFailed(patternId, "Pattern not found")
                )
            }
            
            val metadata = metadataResult.getOrNull()!!
            val oldName = metadata.name
            
            // Check if name is actually changing
            if (oldName.equals(newName, ignoreCase = true)) {
                return@withContext Result.Failure(PatternManagementError.NameUnchanged)
            }
            
            // Check for name conflicts
            val existingPatternsResult = patternRepository.getAllPatternMetadata(projectId)
            if (existingPatternsResult.isSuccess) {
                val existingPatterns = existingPatternsResult.getOrNull()!!
                val nameExists = existingPatterns.any { 
                    it.name.equals(newName, ignoreCase = true) && it.id != patternId 
                }
                if (nameExists) {
                    return@withContext Result.Failure(PatternManagementError.DuplicateName(newName))
                }
            }
            
            // Perform rename
            val renameResult = patternRepository.renamePattern(patternId, newName, projectId)
            if (renameResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.RenameFailed(
                        patternId, 
                        renameResult.errorOrNull()?.message ?: "Unknown error"
                    )
                )
            }
            
            val renameResultData = PatternRenameResult(
                patternId = patternId,
                oldName = oldName,
                newName = newName,
                renamedAt = System.currentTimeMillis()
            )
            
            Result.Success(renameResultData)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Get pattern metadata with enhanced error handling.
     * 
     * @param patternId Pattern identifier
     * @param projectId Project ID for project-scoped lookup
     * @return Result with metadata or error
     */
    suspend fun getPatternMetadataWithErrorHandling(
        patternId: String,
        projectId: String
    ): Result<PatternMetadataResult, PatternManagementError> = withContext(Dispatchers.IO) {
        try {
            if (patternId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidPatternId)
            }
            
            if (projectId.isBlank()) {
                return@withContext Result.Failure(PatternManagementError.InvalidProjectId)
            }
            
            val metadataResult = patternRepository.getPatternMetadata(patternId, projectId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(
                    PatternManagementError.LoadFailed(patternId, metadataResult.errorOrNull()?.message ?: "Unknown error")
                )
            }
            
            val metadata = metadataResult.getOrNull()!!
            val result = PatternMetadataResult(
                metadata = metadata,
                exists = true,
                lastAccessed = System.currentTimeMillis()
            )
            
            Result.Success(result)
        } catch (e: Exception) {
            Result.Failure(PatternManagementError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }
    
    // Private helper methods
    
    private suspend fun resolveNameConflict(baseName: String, projectId: String): String {
        val existingPatternsResult = patternRepository.getAllPatternMetadata(projectId)
        if (existingPatternsResult.isFailure) {
            return baseName
        }
        
        val existingNames = existingPatternsResult.getOrNull()!!.map { it.name.lowercase() }.toSet()
        
        if (!existingNames.contains(baseName.lowercase())) {
            return baseName
        }
        
        // Generate unique name with suffix
        var counter = 1
        var candidateName: String
        do {
            candidateName = "$baseName ($counter)"
            counter++
        } while (existingNames.contains(candidateName.lowercase()) && counter < 1000)
        
        return candidateName
    }
    
    private fun generateConfirmationToken(patternId: String): String {
        // Simple confirmation token - in production, this could be more sophisticated
        return "DELETE_$patternId"
    }
}

/**
 * Specialized error types for pattern management operations.
 */
sealed class PatternManagementError(val message: String) {
    object InvalidPatternId : PatternManagementError("Invalid pattern ID")
    object InvalidProjectId : PatternManagementError("Invalid project ID")
    object InvalidPatternName : PatternManagementError("Invalid pattern name")
    object InvalidConfirmation : PatternManagementError("Invalid confirmation token")
    object NameUnchanged : PatternManagementError("Pattern name is unchanged")
    
    data class DuplicateName(val name: String) : PatternManagementError("Pattern name '$name' already exists")
    data class SaveFailed(val reason: String) : PatternManagementError("Failed to save pattern: $reason")
    data class LoadFailed(val patternId: String, val reason: String) : PatternManagementError("Failed to load pattern '$patternId': $reason")
    data class DeleteFailed(val patternId: String, val reason: String) : PatternManagementError("Failed to delete pattern '$patternId': $reason")
    data class DuplicationFailed(val sourceId: String, val reason: String) : PatternManagementError("Failed to duplicate pattern '$sourceId': $reason")
    data class RenameFailed(val patternId: String, val reason: String) : PatternManagementError("Failed to rename pattern '$patternId': $reason")
    data class UnexpectedError(val reason: String) : PatternManagementError("Unexpected error: $reason")
}

/**
 * Result data for pattern deletion operations.
 */
data class PatternDeletionResult(
    val patternId: String,
    val patternName: String,
    val deletedAt: Long
)

/**
 * Result data for pattern rename operations.
 */
data class PatternRenameResult(
    val patternId: String,
    val oldName: String,
    val newName: String,
    val renamedAt: Long
)

/**
 * Result data for pattern metadata operations.
 */
data class PatternMetadataResult(
    val metadata: com.high.theone.domain.PatternMetadata,
    val exists: Boolean,
    val lastAccessed: Long
)