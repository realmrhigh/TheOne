package com.high.theone.domain

import com.high.theone.model.Project
import com.high.theone.model.SampleMetadata
import com.high.theone.model.Sample
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.Result
import android.net.Uri

interface ProjectManager {
    fun getCurrentProject(): StateFlow<Project?>
    suspend fun createNewProject(name: String, templateName: String?): Result<Project, Error>
    suspend fun loadProject(projectUri: Uri): Result<Project, Error>
    suspend fun saveProject(project: Project): Result<Unit, Error>
    suspend fun addSampleToPool(name: String, sourceFileUri: Uri, copyToProjectDir: Boolean): Result<SampleMetadata, Error>
    fun getSamplesFromPool(): List<SampleMetadata>
    suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean
    suspend fun getSampleById(sampleId: String): SampleMetadata?
    // Add any other required methods
}
