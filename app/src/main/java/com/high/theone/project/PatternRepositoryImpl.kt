package com.high.theone.project

import com.high.theone.domain.PatternRepository
import com.high.theone.domain.Result
import com.high.theone.domain.Error
import com.high.theone.domain.PatternChangeEvent
import com.high.theone.domain.PatternMetadata
import com.high.theone.domain.PatternRepositoryStats
import com.high.theone.domain.PatternCleanupStats
import com.high.theone.domain.PatternValidationStats
import com.high.theone.model.Pattern
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based implementation of PatternRepository.
 * Manages pattern storage, JSON persistence, and project-scoped organization.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@Singleton
class PatternRepositoryImpl @Inject constructor(
    private val context: Context
) : PatternRepository {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    // Change events flow
    private val _changeEvents = MutableSharedFlow<PatternChangeEvent>()
    
    // Base directories
    private val baseDir: File
        get() = File(context.filesDir, "patterns")
    
    private fun getProjectPatternsDir(projectId: String): File {
        return File(baseDir, "projects/$projectId").also { it.mkdirs() }
    }
    
    private fun getPatternFile(projectId: String, patternId: String): File {
        return File(getProjectPatternsDir(projectId), "$patternId.json")
    }
    
    private fun getPatternIndexFile(projectId: String): File {
        return File(getProjectPatternsDir(projectId), "index.json")
    }
    
    // In-memory cache for performance (project -> pattern cache)
    private val patternCache = mutableMapOf<String, MutableMap<String, Pattern>>()
    private val metadataCache = mutableMapOf<String, MutableMap<String, PatternMetadata>>()
    private val loadedProjects = mutableSetOf<String>()
    
    init {
        // Ensure base directory exists
        baseDir.mkdirs()
    }

    override suspend fun savePattern(
        pattern: Pattern,
        projectId: String
    ): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            val patternFile = getPatternFile(projectId, pattern.id)
            
            // Ensure pattern has updated modification timestamp
            val updatedPattern = pattern.withModification()
            
            // Write pattern to file
            val jsonContent = json.encodeToString(updatedPattern)
            patternFile.writeText(jsonContent)
            
            // Update caches
            getProjectPatternCache(projectId)[pattern.id] = updatedPattern
            getProjectMetadataCache(projectId)[pattern.id] = createPatternMetadata(updatedPattern)
            
            // Update index
            updatePatternIndex(projectId)
            
            // Emit change event
            _changeEvents.emit(PatternChangeEvent.PatternAdded(updatedPattern))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to save pattern: ${e.message}"))
        }
    }

    override suspend fun loadPattern(
        patternId: String,
        projectId: String
    ): Result<Pattern, Error> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cachedPattern = getProjectPatternCache(projectId)[patternId]
            if (cachedPattern != null) {
                return@withContext Result.Success(cachedPattern)
            }
            
            // Load from file
            val patternFile = getPatternFile(projectId, patternId)
            if (!patternFile.exists()) {
                return@withContext Result.Failure(Error.notFound("Pattern with ID $patternId not found"))
            }
            
            val jsonContent = patternFile.readText()
            val pattern: Pattern = json.decodeFromString(jsonContent)
            
            // Cache the pattern
            getProjectPatternCache(projectId)[patternId] = pattern
            getProjectMetadataCache(projectId)[patternId] = createPatternMetadata(pattern)
            
            Result.Success(pattern)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to load pattern: ${e.message}"))
        }
    }

    override suspend fun loadAllPatterns(projectId: String): Result<List<Pattern>, Error> = withContext(Dispatchers.IO) {
        try {
            loadProjectCache(projectId)
            
            val patterns = mutableListOf<Pattern>()
            val projectDir = getProjectPatternsDir(projectId)
            
            if (projectDir.exists()) {
                projectDir.listFiles { file -> file.extension == "json" && file.name != "index.json" }
                    ?.forEach { patternFile ->
                        try {
                            val patternId = patternFile.nameWithoutExtension
                            val cachedPattern = getProjectPatternCache(projectId)[patternId]
                            
                            if (cachedPattern != null) {
                                patterns.add(cachedPattern)
                            } else {
                                val jsonContent = patternFile.readText()
                                val pattern: Pattern = json.decodeFromString(jsonContent)
                                patterns.add(pattern)
                                
                                // Cache the pattern
                                getProjectPatternCache(projectId)[patternId] = pattern
                                getProjectMetadataCache(projectId)[patternId] = createPatternMetadata(pattern)
                            }
                        } catch (e: Exception) {
                            // Skip corrupted pattern files
                        }
                    }
            }
            
            Result.Success(patterns)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to load patterns: ${e.message}"))
        }
    }

    override suspend fun updatePattern(
        pattern: Pattern,
        projectId: String
    ): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            val patternFile = getPatternFile(projectId, pattern.id)
            
            if (!patternFile.exists()) {
                return@withContext Result.Failure(Error.notFound("Pattern with ID ${pattern.id} not found"))
            }
            
            // Ensure pattern has updated modification timestamp
            val updatedPattern = pattern.withModification()
            
            // Write updated pattern to file
            val jsonContent = json.encodeToString(updatedPattern)
            patternFile.writeText(jsonContent)
            
            // Update caches
            getProjectPatternCache(projectId)[pattern.id] = updatedPattern
            getProjectMetadataCache(projectId)[pattern.id] = createPatternMetadata(updatedPattern)
            
            // Update index
            updatePatternIndex(projectId)
            
            // Emit change event
            _changeEvents.emit(PatternChangeEvent.PatternUpdated(updatedPattern))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to update pattern: ${e.message}"))
        }
    }

    override suspend fun deletePattern(
        patternId: String,
        projectId: String
    ): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            val patternFile = getPatternFile(projectId, patternId)
            
            if (!patternFile.exists()) {
                return@withContext Result.Failure(Error.notFound("Pattern with ID $patternId not found"))
            }
            
            // Delete file
            patternFile.delete()
            
            // Remove from caches
            getProjectPatternCache(projectId).remove(patternId)
            getProjectMetadataCache(projectId).remove(patternId)
            
            // Update index
            updatePatternIndex(projectId)
            
            // Emit change event
            _changeEvents.emit(PatternChangeEvent.PatternDeleted(patternId))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to delete pattern: ${e.message}"))
        }
    }

    override suspend fun duplicatePattern(
        patternId: String,
        newName: String,
        projectId: String
    ): Result<Pattern, Error> = withContext(Dispatchers.IO) {
        try {
            val sourcePatternResult = loadPattern(patternId, projectId)
            if (sourcePatternResult.isFailure) {
                return@withContext Result.Failure(sourcePatternResult.errorOrNull()!!)
            }
            
            val sourcePattern = sourcePatternResult.getOrNull()!!
            val newPattern = sourcePattern.copy(
                id = UUID.randomUUID().toString(),
                name = newName,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            
            val saveResult = savePattern(newPattern, projectId)
            if (saveResult.isFailure) {
                return@withContext Result.Failure(saveResult.errorOrNull()!!)
            }
            
            // Emit change event
            _changeEvents.emit(PatternChangeEvent.PatternDuplicated(patternId, newPattern))
            
            Result.Success(newPattern)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to duplicate pattern: ${e.message}"))
        }
    }

    override suspend fun renamePattern(
        patternId: String,
        newName: String,
        projectId: String
    ): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            val patternResult = loadPattern(patternId, projectId)
            if (patternResult.isFailure) {
                return@withContext Result.Failure(patternResult.errorOrNull()!!)
            }
            
            val pattern = patternResult.getOrNull()!!
            val oldName = pattern.name
            val renamedPattern = pattern.copy(
                name = newName,
                modifiedAt = System.currentTimeMillis()
            )
            
            val updateResult = updatePattern(renamedPattern, projectId)
            if (updateResult.isFailure) {
                return@withContext Result.Failure(updateResult.errorOrNull()!!)
            }
            
            // Emit change event
            _changeEvents.emit(PatternChangeEvent.PatternRenamed(patternId, oldName, newName))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to rename pattern: ${e.message}"))
        }
    }

    override suspend fun patternExists(
        patternId: String,
        projectId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            if (getProjectPatternCache(projectId).containsKey(patternId)) {
                return@withContext true
            }
            
            // Check file system
            val patternFile = getPatternFile(projectId, patternId)
            patternFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getPatternMetadata(
        patternId: String,
        projectId: String
    ): Result<PatternMetadata, Error> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cachedMetadata = getProjectMetadataCache(projectId)[patternId]
            if (cachedMetadata != null) {
                return@withContext Result.Success(cachedMetadata)
            }
            
            // Load pattern to get metadata
            val patternResult = loadPattern(patternId, projectId)
            if (patternResult.isFailure) {
                return@withContext Result.Failure(patternResult.errorOrNull()!!)
            }
            
            val pattern = patternResult.getOrNull()!!
            val metadata = createPatternMetadata(pattern)
            
            Result.Success(metadata)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to get pattern metadata: ${e.message}"))
        }
    }

    override suspend fun getAllPatternMetadata(projectId: String): Result<List<PatternMetadata>, Error> = withContext(Dispatchers.IO) {
        try {
            loadProjectCache(projectId)
            
            val metadataList = mutableListOf<PatternMetadata>()
            val projectDir = getProjectPatternsDir(projectId)
            
            if (projectDir.exists()) {
                projectDir.listFiles { file -> file.extension == "json" && file.name != "index.json" }
                    ?.forEach { patternFile ->
                        try {
                            val patternId = patternFile.nameWithoutExtension
                            val cachedMetadata = getProjectMetadataCache(projectId)[patternId]
                            
                            if (cachedMetadata != null) {
                                metadataList.add(cachedMetadata)
                            } else {
                                // Load pattern to create metadata
                                val jsonContent = patternFile.readText()
                                val pattern: Pattern = json.decodeFromString(jsonContent)
                                val metadata = createPatternMetadata(pattern)
                                metadataList.add(metadata)
                                
                                // Cache the metadata
                                getProjectMetadataCache(projectId)[patternId] = metadata
                            }
                        } catch (e: Exception) {
                            // Skip corrupted pattern files
                        }
                    }
            }
            
            Result.Success(metadataList)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to get pattern metadata: ${e.message}"))
        }
    }

    override suspend fun searchPatterns(
        query: String,
        projectId: String
    ): Result<List<Pattern>, Error> = withContext(Dispatchers.IO) {
        try {
            val allPatternsResult = loadAllPatterns(projectId)
            if (allPatternsResult.isFailure) {
                return@withContext Result.Failure(allPatternsResult.errorOrNull()!!)
            }
            
            val allPatterns = allPatternsResult.getOrNull()!!
            val searchQuery = query.lowercase()
            
            val matchingPatterns = allPatterns.filter { pattern ->
                pattern.name.lowercase().contains(searchQuery)
            }
            
            Result.Success(matchingPatterns)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to search patterns: ${e.message}"))
        }
    }

    override suspend fun getRepositoryStats(projectId: String): Result<PatternRepositoryStats, Error> = withContext(Dispatchers.IO) {
        try {
            val metadataResult = getAllPatternMetadata(projectId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(metadataResult.errorOrNull()!!)
            }
            
            val metadata = metadataResult.getOrNull()!!
            
            if (metadata.isEmpty()) {
                val emptyStats = PatternRepositoryStats(
                    totalPatterns = 0,
                    totalSteps = 0,
                    averagePatternLength = 0f,
                    averageTempo = 0f,
                    patternsByLength = emptyMap(),
                    oldestPatternDate = 0L,
                    newestPatternDate = 0L
                )
                return@withContext Result.Success(emptyStats)
            }
            
            val totalSteps = metadata.sumOf { it.stepCount }
            val averagePatternLength = metadata.map { it.length }.average().toFloat()
            val averageTempo = metadata.map { it.tempo }.average().toFloat()
            val patternsByLength = metadata.groupBy { it.length }.mapValues { it.value.size }
            val oldestPatternDate = metadata.minOf { it.createdAt }
            val newestPatternDate = metadata.maxOf { it.createdAt }
            
            val stats = PatternRepositoryStats(
                totalPatterns = metadata.size,
                totalSteps = totalSteps,
                averagePatternLength = averagePatternLength,
                averageTempo = averageTempo,
                patternsByLength = patternsByLength,
                oldestPatternDate = oldestPatternDate,
                newestPatternDate = newestPatternDate
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to get repository stats: ${e.message}"))
        }
    }

    override fun observePatternChanges(projectId: String): Flow<PatternChangeEvent> {
        return _changeEvents.asSharedFlow().filter { event ->
            // For now, we don't filter by project since events don't carry project info
            // In a more sophisticated implementation, we could add project context to events
            true
        }
    }

    override suspend fun cleanupRepository(projectId: String): Result<PatternCleanupStats, Error> = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectPatternsDir(projectId)
            if (!projectDir.exists()) {
                val emptyStats = PatternCleanupStats(
                    orphanedFilesRemoved = 0,
                    invalidReferencesRemoved = 0,
                    spaceFreeBytes = 0L
                )
                return@withContext Result.Success(emptyStats)
            }
            
            var orphanedFilesRemoved = 0
            var invalidReferencesRemoved = 0
            var spaceFreeBytes = 0L
            
            // Load all valid patterns to identify orphaned files
            val validPatternIds = mutableSetOf<String>()
            projectDir.listFiles { file -> file.extension == "json" && file.name != "index.json" }
                ?.forEach { patternFile ->
                    try {
                        val jsonContent = patternFile.readText()
                        val pattern: Pattern = json.decodeFromString(jsonContent)
                        validPatternIds.add(pattern.id)
                    } catch (e: Exception) {
                        // Invalid pattern file - mark for removal
                        spaceFreeBytes += patternFile.length()
                        patternFile.delete()
                        orphanedFilesRemoved++
                    }
                }
            
            // Check for files that don't correspond to valid patterns
            projectDir.listFiles { file -> file.extension == "json" && file.name != "index.json" }
                ?.forEach { patternFile ->
                    val patternId = patternFile.nameWithoutExtension
                    if (!validPatternIds.contains(patternId)) {
                        spaceFreeBytes += patternFile.length()
                        patternFile.delete()
                        orphanedFilesRemoved++
                    }
                }
            
            // Clear caches for this project to force reload
            patternCache.remove(projectId)
            metadataCache.remove(projectId)
            loadedProjects.remove(projectId)
            
            // Rebuild index
            updatePatternIndex(projectId)
            
            val stats = PatternCleanupStats(
                orphanedFilesRemoved = orphanedFilesRemoved,
                invalidReferencesRemoved = invalidReferencesRemoved,
                spaceFreeBytes = spaceFreeBytes
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to cleanup repository: ${e.message}"))
        }
    }

    override suspend fun validateAndRepairRepository(projectId: String): Result<PatternValidationStats, Error> = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectPatternsDir(projectId)
            if (!projectDir.exists()) {
                val emptyStats = PatternValidationStats(
                    totalPatterns = 0,
                    validPatterns = 0,
                    repairedPatterns = 0,
                    invalidPatterns = 0,
                    corruptedFiles = 0
                )
                return@withContext Result.Success(emptyStats)
            }
            
            var totalPatterns = 0
            var validPatterns = 0
            var repairedPatterns = 0
            var invalidPatterns = 0
            var corruptedFiles = 0
            
            val filesToRemove = mutableListOf<File>()
            
            projectDir.listFiles { file -> file.extension == "json" && file.name != "index.json" }
                ?.forEach { patternFile ->
                    totalPatterns++
                    
                    try {
                        val jsonContent = patternFile.readText()
                        val pattern: Pattern = json.decodeFromString(jsonContent)
                        
                        // Validate pattern data
                        if (isValidPattern(pattern)) {
                            validPatterns++
                            
                            // Check if pattern needs repair (e.g., filename doesn't match ID)
                            val expectedFileName = "${pattern.id}.json"
                            if (patternFile.name != expectedFileName) {
                                val correctFile = File(projectDir, expectedFileName)
                                if (!correctFile.exists()) {
                                    patternFile.renameTo(correctFile)
                                    repairedPatterns++
                                } else {
                                    // Duplicate ID - mark for removal
                                    filesToRemove.add(patternFile)
                                    invalidPatterns++
                                }
                            }
                        } else {
                            invalidPatterns++
                            filesToRemove.add(patternFile)
                        }
                    } catch (e: Exception) {
                        corruptedFiles++
                        filesToRemove.add(patternFile)
                    }
                }
            
            // Remove invalid files
            filesToRemove.forEach { file ->
                file.delete()
            }
            
            // Clear caches to force reload
            patternCache.remove(projectId)
            metadataCache.remove(projectId)
            loadedProjects.remove(projectId)
            
            // Rebuild index
            updatePatternIndex(projectId)
            
            val stats = PatternValidationStats(
                totalPatterns = totalPatterns,
                validPatterns = validPatterns,
                repairedPatterns = repairedPatterns,
                invalidPatterns = invalidPatterns,
                corruptedFiles = corruptedFiles
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to validate repository: ${e.message}"))
        }
    }

    // Private helper methods
    
    private fun getProjectPatternCache(projectId: String): MutableMap<String, Pattern> {
        return patternCache.getOrPut(projectId) { mutableMapOf() }
    }
    
    private fun getProjectMetadataCache(projectId: String): MutableMap<String, PatternMetadata> {
        return metadataCache.getOrPut(projectId) { mutableMapOf() }
    }
    
    private suspend fun loadProjectCache(projectId: String) {
        if (loadedProjects.contains(projectId)) return
        
        try {
            // Load index if it exists
            val indexFile = getPatternIndexFile(projectId)
            if (indexFile.exists()) {
                val jsonContent = indexFile.readText()
                val metadataList: List<PatternMetadata> = json.decodeFromString(jsonContent)
                val projectMetadataCache = getProjectMetadataCache(projectId)
                metadataList.forEach { metadata ->
                    projectMetadataCache[metadata.id] = metadata
                }
            }
            
            loadedProjects.add(projectId)
        } catch (e: Exception) {
            // If index is corrupted, we'll rebuild it on next update
            loadedProjects.add(projectId)
        }
    }
    
    private suspend fun updatePatternIndex(projectId: String) {
        try {
            val metadataList = getProjectMetadataCache(projectId).values.toList()
            val jsonContent = json.encodeToString(metadataList)
            val indexFile = getPatternIndexFile(projectId)
            indexFile.writeText(jsonContent)
        } catch (e: Exception) {
            // Log error but don't fail the operation
        }
    }
    
    private fun createPatternMetadata(pattern: Pattern): PatternMetadata {
        val stepCount = pattern.steps.values.sumOf { stepList ->
            stepList.count { it.isActive }
        }
        
        return PatternMetadata(
            id = pattern.id,
            name = pattern.name,
            length = pattern.length,
            tempo = pattern.tempo,
            swing = pattern.swing,
            createdAt = pattern.createdAt,
            modifiedAt = pattern.modifiedAt,
            stepCount = stepCount
        )
    }
    
    private fun isValidPattern(pattern: Pattern): Boolean {
        return try {
            // Basic validation - the Pattern class constructor already validates most constraints
            pattern.name.isNotBlank() &&
            pattern.length in listOf(8, 16, 24, 32) &&
            pattern.tempo in 60f..200f &&
            pattern.swing in 0f..0.75f &&
            pattern.steps.values.flatten().all { step ->
                step.position >= 0 && step.position < pattern.length &&
                step.velocity in 1..127 &&
                step.microTiming in -50f..50f
            }
        } catch (e: Exception) {
            false
        }
    }
}