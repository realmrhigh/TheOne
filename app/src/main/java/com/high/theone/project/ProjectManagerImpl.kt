package com.high.theone.project

import android.net.Uri
import com.high.theone.domain.Error
import com.high.theone.domain.ProjectManager
import com.high.theone.domain.Result
import com.high.theone.model.Project
import com.high.theone.model.Sample
import com.high.theone.model.SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectManagerImpl @Inject constructor() : ProjectManager {

    private val _currentProject = MutableStateFlow<Project?>(null)
    override fun getCurrentProject(): StateFlow<Project?> = _currentProject.asStateFlow()

    // In-memory sample pool keyed by sample ID string
    private val _samplePool = mutableMapOf<String, SampleMetadata>()

    override suspend fun createNewProject(
        name: String,
        templateName: String?
    ): Result<Project, Error> {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled Project" },
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )
        _currentProject.value = project
        return Result.Success(project)
    }

    override suspend fun loadProject(projectUri: Uri): Result<Project, Error> {
        val project = _currentProject.value
            ?: return Result.Failure(Error("No project loaded"))
        return Result.Success(project)
    }

    override suspend fun saveProject(project: Project): Result<Unit, Error> {
        _currentProject.value = project.copy(modifiedAt = System.currentTimeMillis())
        return Result.Success(Unit)
    }

    override suspend fun addSampleToPool(
        name: String,
        sourceFileUri: Uri,
        copyToProjectDir: Boolean
    ): Result<SampleMetadata, Error> {
        val filePath = sourceFileUri.path ?: return Result.Failure(Error("Invalid URI"))
        val file = File(filePath)
        val sampleId = UUID.randomUUID()
        val metadata = SampleMetadata(
            id = sampleId,
            name = name.ifBlank { file.nameWithoutExtension },
            filePath = filePath,
            fileSizeBytes = if (file.exists()) file.length() else 0L,
            createdAt = System.currentTimeMillis()
        )
        _samplePool[sampleId.toString()] = metadata
        // Ensure a project exists
        ensureDefaultProject()
        return Result.Success(metadata)
    }

    override fun getSamplesFromPool(): List<SampleMetadata> = _samplePool.values.toList()

    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        val key = sample.id.toString()
        return if (_samplePool.containsKey(key)) {
            _samplePool[key] = sample
            true
        } else {
            false
        }
    }

    override suspend fun getSampleById(sampleId: String): SampleMetadata? =
        _samplePool[sampleId]

    override suspend fun loadSample(file: File): Sample =
        Sample(
            name = file.nameWithoutExtension,
            filePath = file.absolutePath
        )

    /**
     * Add a recorded SampleMetadata directly (e.g. from a recording result).
     */
    fun addRecordedSample(metadata: SampleMetadata) {
        _samplePool[metadata.id.toString()] = metadata
        ensureDefaultProject()
    }

    private fun ensureDefaultProject() {
        if (_currentProject.value == null) {
            _currentProject.value = Project(
                id = UUID.randomUUID().toString(),
                name = "Default Project",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
        }
    }
}
