package com.example.theone.project

import com.example.theone.model.Project
import com.example.theone.model.SampleMetadata
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri // Required for browseStorage

interface ProjectManager {
    suspend fun createNewProject(name: String, template: String?): Project
    suspend fun saveProject(project: Project, file: File)
    suspend fun loadProject(file: File): Project
    fun addSampleToPool(sampleMetadata: SampleMetadata)
    fun getSamplesFromPool(): List<SampleMetadata>
    suspend fun getSampleById(sampleId: String): SampleMetadata?
    val samplePool: StateFlow<List<SampleMetadata>>
    suspend fun browseStorage(uri: Uri): List<File> // New function
}

class ProjectManagerImpl : ProjectManager {
    private val _samplePool = MutableStateFlow<List<SampleMetadata>>(emptyList())
    override val samplePool: StateFlow<List<SampleMetadata>> = _samplePool.asStateFlow()

    override suspend fun createNewProject(name: String, template: String?): Project {
        val newProject = Project(
            id = UUID.randomUUID().toString(),
            name = name
        )
        return newProject
    }

    override suspend fun saveProject(project: Project, file: File) {
        val jsonString = Json.encodeToString(project)
        file.writeText(jsonString)
    }

    override suspend fun loadProject(file: File): Project {
        val jsonString = file.readText()
        return Json.decodeFromString<Project>(jsonString)
    }

    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        val currentPool = _samplePool.value.toMutableList()
        val existingSampleIndex = currentPool.indexOfFirst { it.uri == sampleMetadata.uri }
        if (existingSampleIndex != -1) {
            currentPool[existingSampleIndex] = sampleMetadata
        } else {
            currentPool.add(sampleMetadata)
        }
        _samplePool.value = currentPool
    }

    override fun getSamplesFromPool(): List<SampleMetadata> {
        return _samplePool.value
    }

    override suspend fun getSampleById(sampleId: String): SampleMetadata? {
        return _samplePool.value.firstOrNull { it.uri == sampleId }
    }

    override suspend fun browseStorage(uri: Uri): List<File> {
        // This is a simplified example. A real implementation would use the Storage Access Framework
        // to let the user pick a directory and then list the files in it.
        // Also, direct file path access from a generic Uri can be problematic and might require
        // content resolvers for content:// Uris. This implementation assumes file:// Uris.
        val directoryPath = uri.path
        if (directoryPath == null) {
            return emptyList()
        }
        val directory = File(directoryPath)
        return directory.listFiles()?.toList() ?: emptyList()
    }
}
