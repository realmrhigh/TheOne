package com.high.theone.domain

import com.high.theone.model.SampleMetadata
import com.high.theone.model.Sample
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for sample management operations.
 * Handles CRUD operations, file-based storage, and project-scoped organization.
 * 
 * Requirements: 2.1, 2.2, 7.1
 */
interface SampleRepository {
    
    /**
     * Save a new sample to the repository with metadata.
     * 
     * @param sampleData Raw audio data as ByteArray
     * @param metadata Sample metadata including duration, format info, etc.
     * @param projectId Optional project ID for project-scoped storage
     * @return Result containing the generated sample ID on success
     */
    suspend fun saveSample(
        sampleData: ByteArray, 
        metadata: SampleMetadata,
        projectId: String? = null
    ): Result<String, Error>
    
    /**
     * Save a sample from an existing file URI.
     * 
     * @param sourceUri URI of the source audio file
     * @param metadata Sample metadata
     * @param projectId Optional project ID for project-scoped storage
     * @param copyToProject Whether to copy the file to project directory
     * @return Result containing the generated sample ID on success
     */
    suspend fun saveSampleFromUri(
        sourceUri: Uri,
        metadata: SampleMetadata,
        projectId: String? = null,
        copyToProject: Boolean = true
    ): Result<String, Error>
    
    /**
     * Load sample metadata by ID.
     * 
     * @param sampleId Unique sample identifier
     * @return Result containing SampleMetadata on success
     */
    suspend fun loadSample(sampleId: String): Result<SampleMetadata, Error>
    
    /**
     * Load complete Sample object by ID.
     * 
     * @param sampleId Unique sample identifier
     * @return Result containing Sample object on success
     */
    suspend fun loadSampleComplete(sampleId: String): Result<Sample, Error>
    
    /**
     * Update existing sample metadata.
     * 
     * @param sampleId Sample identifier
     * @param metadata Updated metadata
     * @return Result indicating success or failure
     */
    suspend fun updateSampleMetadata(
        sampleId: String, 
        metadata: SampleMetadata
    ): Result<Unit, Error>
    
    /**
     * Delete a sample and its associated files.
     * 
     * @param sampleId Sample identifier
     * @return Result indicating success or failure
     */
    suspend fun deleteSample(sampleId: String): Result<Unit, Error>
    
    /**
     * Get all samples in the repository.
     * 
     * @return Result containing list of all SampleMetadata
     */
    suspend fun getAllSamples(): Result<List<SampleMetadata>, Error>
    
    /**
     * Get all samples for a specific project.
     * 
     * @param projectId Project identifier
     * @return Result containing list of project-scoped SampleMetadata
     */
    suspend fun getSamplesForProject(projectId: String): Result<List<SampleMetadata>, Error>
    
    /**
     * Get samples matching specific tags.
     * 
     * @param tags List of tags to match
     * @param matchAll If true, sample must have all tags; if false, any tag matches
     * @return Result containing filtered list of SampleMetadata
     */
    suspend fun getSamplesByTags(
        tags: List<String>, 
        matchAll: Boolean = false
    ): Result<List<SampleMetadata>, Error>
    
    /**
     * Search samples by name or metadata.
     * 
     * @param query Search query string
     * @param projectId Optional project ID to limit search scope
     * @return Result containing matching SampleMetadata
     */
    suspend fun searchSamples(
        query: String, 
        projectId: String? = null
    ): Result<List<SampleMetadata>, Error>
    
    /**
     * Get the file path for a sample.
     * 
     * @param sampleId Sample identifier
     * @return Result containing the file path
     */
    suspend fun getSampleFilePath(sampleId: String): Result<String, Error>
    
    /**
     * Check if a sample exists in the repository.
     * 
     * @param sampleId Sample identifier
     * @return True if sample exists, false otherwise
     */
    suspend fun sampleExists(sampleId: String): Boolean
    
    /**
     * Get repository statistics.
     * 
     * @param projectId Optional project ID to get project-specific stats
     * @return Result containing repository statistics
     */
    suspend fun getRepositoryStats(projectId: String? = null): Result<SampleRepositoryStats, Error>
    
    /**
     * Observe changes to the sample repository.
     * 
     * @param projectId Optional project ID to observe project-specific changes
     * @return Flow of sample change events
     */
    fun observeSampleChanges(projectId: String? = null): Flow<SampleChangeEvent>
    
    /**
     * Clean up orphaned files and invalid references.
     * 
     * @return Result containing cleanup statistics
     */
    suspend fun cleanupRepository(): Result<CleanupStats, Error>
    
    /**
     * Validate and repair repository integrity.
     * Checks file paths, validates checksums, and rebuilds indexes.
     * 
     * @return Result containing validation statistics
     */
    suspend fun validateAndRepairRepository(): Result<ValidationStats, Error>
}

/**
 * Statistics about the sample repository.
 */
data class SampleRepositoryStats(
    val totalSamples: Int,
    val totalSizeBytes: Long,
    val samplesByProject: Map<String, Int>,
    val samplesByFormat: Map<String, Int>,
    val averageDurationMs: Long
)

/**
 * Event representing a change in the sample repository.
 */
sealed class SampleChangeEvent {
    data class SampleAdded(val sampleId: String, val metadata: SampleMetadata) : SampleChangeEvent()
    data class SampleUpdated(val sampleId: String, val metadata: SampleMetadata) : SampleChangeEvent()
    data class SampleDeleted(val sampleId: String) : SampleChangeEvent()
    data class ProjectSamplesChanged(val projectId: String) : SampleChangeEvent()
}

/**
 * Statistics from repository cleanup operation.
 */
data class CleanupStats(
    val orphanedFilesRemoved: Int,
    val invalidReferencesRemoved: Int,
    val spaceFreeBytes: Long
)

/**
 * Statistics from repository validation and repair operation.
 */
data class ValidationStats(
    val totalSamples: Int,
    val validSamples: Int,
    val repairedPaths: Int,
    val invalidSamples: Int,
    val corruptedFiles: Int
)