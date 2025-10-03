package com.high.theone.project

import com.high.theone.domain.SampleRepository
import com.high.theone.domain.Result
import com.high.theone.domain.Error
import com.high.theone.domain.SampleChangeEvent
import com.high.theone.domain.SampleRepositoryStats
import com.high.theone.domain.CleanupStats
import com.high.theone.domain.ValidationStats
import com.high.theone.model.SampleMetadata
import com.high.theone.model.Sample
import android.content.Context
import android.net.Uri
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based implementation of SampleRepository.
 * Manages sample storage, metadata persistence, and project-scoped organization.
 * 
 * Requirements: 2.1, 2.2, 7.1
 */
@Singleton
class SampleRepositoryImpl @Inject constructor(
    private val context: Context
) : SampleRepository {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    // Change events flow
    private val _changeEvents = MutableSharedFlow<SampleChangeEvent>()
    
    // Base directories
    private val baseDir: File
        get() = File(context.filesDir, "samples")
    
    private val sharedSamplesDir: File
        get() = File(baseDir, "shared")
    
    private fun getProjectSamplesDir(projectId: String): File {
        return File(baseDir, "projects/$projectId")
    }
    
    private val metadataFile: File
        get() = File(baseDir, "metadata.json")
    
    // In-memory cache for performance
    private val metadataCache = mutableMapOf<String, SampleMetadata>()
    private var cacheLoaded = false
    
    init {
        // Ensure directories exist
        baseDir.mkdirs()
        sharedSamplesDir.mkdirs()
    }    

    override suspend fun saveSample(
        sampleData: ByteArray,
        metadata: SampleMetadata,
        projectId: String?
    ): Result<String, Error> = withContext(Dispatchers.IO) {
        try {
            val sampleId = metadata.id.toString()
            val targetDir = if (projectId != null) {
                getProjectSamplesDir(projectId).also { it.mkdirs() }
            } else {
                sharedSamplesDir
            }
            
            val sampleFile = File(targetDir, "$sampleId.${metadata.format}")
            
            // Write sample data
            FileOutputStream(sampleFile).use { output ->
                output.write(sampleData)
            }
            
            // Calculate checksum
            val checksum = calculateChecksum(sampleData)
            
            // Update metadata with file info
            val updatedMetadata = metadata.copy(
                filePath = sampleFile.absolutePath,
                fileSizeBytes = sampleData.size.toLong(),
                checksum = checksum,
                projectId = projectId,
                modifiedAt = System.currentTimeMillis()
            )
            
            // Save metadata
            saveMetadataToCache(sampleId, updatedMetadata)
            persistMetadata()
            
            // Emit change event
            _changeEvents.emit(SampleChangeEvent.SampleAdded(sampleId, updatedMetadata))
            
            Result.Success(sampleId)
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to save sample: ${e.message}"))
        }
    }    

    override suspend fun saveSampleFromUri(
        sourceUri: Uri,
        metadata: SampleMetadata,
        projectId: String?,
        copyToProject: Boolean
    ): Result<String, Error> = withContext(Dispatchers.IO) {
        try {
            val sampleId = metadata.id.toString()
            
            if (copyToProject) {
                // Copy file to project directory
                val targetDir = if (projectId != null) {
                    getProjectSamplesDir(projectId).also { it.mkdirs() }
                } else {
                    sharedSamplesDir
                }
                
                val sampleFile = File(targetDir, "$sampleId.${metadata.format}")
                
                // Copy file
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(sampleFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Calculate checksum
                val checksum = calculateFileChecksum(sampleFile)
                
                // Update metadata
                val updatedMetadata = metadata.copy(
                    filePath = sampleFile.absolutePath,
                    fileSizeBytes = sampleFile.length(),
                    checksum = checksum,
                    projectId = projectId,
                    originalFilePath = sourceUri.toString(),
                    modifiedAt = System.currentTimeMillis()
                )
                
                saveMetadataToCache(sampleId, updatedMetadata)
                persistMetadata()
                
                _changeEvents.emit(SampleChangeEvent.SampleAdded(sampleId, updatedMetadata))
                
                Result.Success(sampleId)
            } else {
                // Just reference the original file
                val updatedMetadata = metadata.copy(
                    filePath = sourceUri.toString(),
                    projectId = projectId,
                    originalFilePath = sourceUri.toString(),
                    modifiedAt = System.currentTimeMillis()
                )
                
                saveMetadataToCache(sampleId, updatedMetadata)
                persistMetadata()
                
                _changeEvents.emit(SampleChangeEvent.SampleAdded(sampleId, updatedMetadata))
                
                Result.Success(sampleId)
            }
        } catch (e: Exception) {
            Result.Failure(Error.fileSystem("Failed to save sample from URI: ${e.message}"))
        }
    }    

    override suspend fun loadSample(sampleId: String): Result<SampleMetadata, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            val metadata = metadataCache[sampleId]
                ?: return@withContext Result.Failure(Error.notFound("Sample with ID $sampleId"))
            
            Result.Success(metadata)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun loadSampleComplete(sampleId: String): Result<Sample, Error> = withContext(Dispatchers.IO) {
        try {
            val metadataResult = loadSample(sampleId)
            if (metadataResult.isFailure) {
                return@withContext Result.Failure(metadataResult.errorOrNull()!!)
            }
            
            val metadata = metadataResult.getOrNull()!!
            val sample = Sample(
                id = UUID.fromString(sampleId),
                name = metadata.name,
                filePath = metadata.filePath,
                metadata = metadata
            )
            
            Result.Success(sample)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun updateSampleMetadata(
        sampleId: String,
        metadata: SampleMetadata
    ): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            
            if (!metadataCache.containsKey(sampleId)) {
                return@withContext Result.Failure(Error.notFound("Sample with ID $sampleId"))
            }
            
            val updatedMetadata = metadata.copy(
                modifiedAt = System.currentTimeMillis()
            )
            
            saveMetadataToCache(sampleId, updatedMetadata)
            persistMetadata()
            
            _changeEvents.emit(SampleChangeEvent.SampleUpdated(sampleId, updatedMetadata))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun deleteSample(sampleId: String): Result<Unit, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            
            val metadata = metadataCache[sampleId]
                ?: return@withContext Result.Failure(Error.notFound("Sample with ID $sampleId"))
            
            // Delete file if it exists and is in our managed directories
            val file = File(metadata.filePath)
            if (file.exists() && file.absolutePath.startsWith(baseDir.absolutePath)) {
                file.delete()
            }
            
            // Remove from cache and persist
            metadataCache.remove(sampleId)
            persistMetadata()
            
            _changeEvents.emit(SampleChangeEvent.SampleDeleted(sampleId))
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }    

    override suspend fun getAllSamples(): Result<List<SampleMetadata>, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            Result.Success(metadataCache.values.toList())
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun getSamplesForProject(projectId: String): Result<List<SampleMetadata>, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            val projectSamples = metadataCache.values.filter { it.projectId == projectId }
            Result.Success(projectSamples)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun getSamplesByTags(
        tags: List<String>,
        matchAll: Boolean
    ): Result<List<SampleMetadata>, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            val filteredSamples = metadataCache.values.filter { sample ->
                if (matchAll) {
                    tags.all { tag -> sample.tags.contains(tag) }
                } else {
                    tags.any { tag -> sample.tags.contains(tag) }
                }
            }
            Result.Success(filteredSamples)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun searchSamples(
        query: String,
        projectId: String?
    ): Result<List<SampleMetadata>, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            val searchQuery = query.lowercase()
            val filteredSamples = metadataCache.values.filter { sample ->
                val matchesProject = projectId == null || sample.projectId == projectId
                val matchesQuery = sample.name.lowercase().contains(searchQuery) ||
                                sample.tags.any { it.lowercase().contains(searchQuery) }
                matchesProject && matchesQuery
            }
            Result.Success(filteredSamples)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun getSampleFilePath(sampleId: String): Result<String, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            val metadata = metadataCache[sampleId]
                ?: return@withContext Result.Failure(Error.notFound("Sample with ID $sampleId"))
            
            Result.Success(metadata.filePath)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun sampleExists(sampleId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            metadataCache.containsKey(sampleId)
        } catch (e: Exception) {
            false
        }
    }    
  
  override suspend fun getRepositoryStats(projectId: String?): Result<SampleRepositoryStats, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            
            val samples = if (projectId != null) {
                metadataCache.values.filter { it.projectId == projectId }
            } else {
                metadataCache.values
            }
            
            val totalSizeBytes = samples.sumOf { it.fileSizeBytes }
            val samplesByProject = samples.groupBy { it.projectId ?: "shared" }
                .mapValues { it.value.size }
            val samplesByFormat = samples.groupBy { it.format }
                .mapValues { it.value.size }
            val averageDurationMs = if (samples.isNotEmpty()) {
                samples.sumOf { it.durationMs } / samples.size
            } else 0L
            
            val stats = SampleRepositoryStats(
                totalSamples = samples.size,
                totalSizeBytes = totalSizeBytes,
                samplesByProject = samplesByProject,
                samplesByFormat = samplesByFormat,
                averageDurationMs = averageDurationMs
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override fun observeSampleChanges(projectId: String?): Flow<SampleChangeEvent> {
        return if (projectId != null) {
            _changeEvents.asSharedFlow().filter { event ->
                when (event) {
                    is SampleChangeEvent.SampleAdded -> event.metadata.projectId == projectId
                    is SampleChangeEvent.SampleUpdated -> event.metadata.projectId == projectId
                    is SampleChangeEvent.ProjectSamplesChanged -> event.projectId == projectId
                    else -> true
                }
            }
        } else {
            _changeEvents.asSharedFlow()
        }
    }    
 
   override suspend fun cleanupRepository(): Result<CleanupStats, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            
            var orphanedFilesRemoved = 0
            var invalidReferencesRemoved = 0
            var spaceFreeBytes = 0L
            
            // Find orphaned files (files without metadata entries)
            val managedFiles = mutableSetOf<String>()
            metadataCache.values.forEach { metadata ->
                val file = File(metadata.filePath)
                if (file.absolutePath.startsWith(baseDir.absolutePath)) {
                    managedFiles.add(file.absolutePath)
                }
            }
            
            // Scan directories for orphaned files
            scanDirectoryForOrphans(baseDir, managedFiles) { file ->
                spaceFreeBytes += file.length()
                file.delete()
                orphanedFilesRemoved++
            }
            
            // Find invalid references (metadata pointing to non-existent files)
            val invalidSamples = mutableListOf<String>()
            metadataCache.forEach { (sampleId, metadata) ->
                val file = File(metadata.filePath)
                if (!file.exists() && file.absolutePath.startsWith(baseDir.absolutePath)) {
                    invalidSamples.add(sampleId)
                }
            }
            
            // Remove invalid references
            invalidSamples.forEach { sampleId ->
                metadataCache.remove(sampleId)
                invalidReferencesRemoved++
            }
            
            if (invalidReferencesRemoved > 0) {
                persistMetadata()
            }
            
            val stats = CleanupStats(
                orphanedFilesRemoved = orphanedFilesRemoved,
                invalidReferencesRemoved = invalidReferencesRemoved,
                spaceFreeBytes = spaceFreeBytes
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }
    
    override suspend fun validateAndRepairRepository(): Result<ValidationStats, Error> = withContext(Dispatchers.IO) {
        try {
            loadMetadataCache()
            
            var validSamples = 0
            var repairedPaths = 0
            var invalidSamples = 0
            var corruptedFiles = 0
            
            val samplesToRemove = mutableListOf<String>()
            
            metadataCache.forEach { (sampleId, metadata) ->
                val file = File(metadata.filePath)
                
                when {
                    !file.exists() -> {
                        // Try to find the file in expected locations
                        val expectedFile = findSampleFile(sampleId, metadata.format)
                        if (expectedFile != null && expectedFile.exists()) {
                            // Repair the path
                            val repairedMetadata = metadata.copy(
                                filePath = expectedFile.absolutePath,
                                modifiedAt = System.currentTimeMillis()
                            )
                            metadataCache[sampleId] = repairedMetadata
                            repairedPaths++
                            validSamples++
                        } else {
                            samplesToRemove.add(sampleId)
                            invalidSamples++
                        }
                    }
                    
                    metadata.checksum != null -> {
                        // Validate checksum
                        val actualChecksum = calculateFileChecksum(file)
                        if (actualChecksum == metadata.checksum) {
                            validSamples++
                        } else {
                            corruptedFiles++
                            samplesToRemove.add(sampleId)
                        }
                    }
                    
                    else -> {
                        // File exists but no checksum to validate
                        validSamples++
                    }
                }
            }
            
            // Remove invalid samples
            samplesToRemove.forEach { sampleId ->
                metadataCache.remove(sampleId)
            }
            
            if (repairedPaths > 0 || samplesToRemove.isNotEmpty()) {
                persistMetadata()
            }
            
            val stats = ValidationStats(
                totalSamples = metadataCache.size + samplesToRemove.size,
                validSamples = validSamples,
                repairedPaths = repairedPaths,
                invalidSamples = invalidSamples,
                corruptedFiles = corruptedFiles
            )
            
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Failure(Error.fromThrowable(e))
        }
    }    

    // Private helper methods
    
    private suspend fun loadMetadataCache() {
        if (cacheLoaded) return
        
        try {
            if (metadataFile.exists()) {
                val jsonContent = metadataFile.readText()
                val metadataList: List<SampleMetadata> = json.decodeFromString(jsonContent)
                metadataCache.clear()
                metadataList.forEach { metadata ->
                    metadataCache[metadata.id.toString()] = metadata
                }
            }
            cacheLoaded = true
        } catch (e: Exception) {
            // If metadata file is corrupted, start fresh
            metadataCache.clear()
            cacheLoaded = true
        }
    }
    
    private fun saveMetadataToCache(sampleId: String, metadata: SampleMetadata) {
        metadataCache[sampleId] = metadata
    }
    
    private suspend fun persistMetadata() {
        try {
            val metadataList = metadataCache.values.toList()
            val jsonContent = json.encodeToString(metadataList)
            metadataFile.writeText(jsonContent)
        } catch (e: Exception) {
            // Log error but don't fail the operation
        }
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun calculateFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun findSampleFile(sampleId: String, format: String): File? {
        // Check shared samples directory
        val sharedFile = File(sharedSamplesDir, "$sampleId.$format")
        if (sharedFile.exists()) return sharedFile
        
        // Check all project directories
        val projectsDir = File(baseDir, "projects")
        if (projectsDir.exists()) {
            projectsDir.listFiles()?.forEach { projectDir ->
                if (projectDir.isDirectory) {
                    val projectFile = File(projectDir, "$sampleId.$format")
                    if (projectFile.exists()) return projectFile
                }
            }
        }
        
        return null
    }
    
    private fun scanDirectoryForOrphans(
        directory: File,
        managedFiles: Set<String>,
        onOrphanFound: (File) -> Unit
    ) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectoryForOrphans(file, managedFiles, onOrphanFound)
            } else if (file.isFile && file.name != "metadata.json") {
                if (!managedFiles.contains(file.absolutePath)) {
                    onOrphanFound(file)
                }
            }
        }
    }
}