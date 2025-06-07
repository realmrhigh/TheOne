package com.example.theone.domain

import com.example.theone.model.SampleMetadata // Import the unified SampleMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID // For generating IDs if needed for interface methods

interface ProjectManager {
    // Original interface methods - will be implemented minimally
    suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata?
    suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean // To save changes from M1.3
    suspend fun getSampleById(sampleId: String): SampleMetadata?     // To load a sample for editing

    // New methods from the plan (non-suspend, slightly different signatures/purpose for M1 ViewModels)
    fun addSampleToPool(sampleMetadata: SampleMetadata) // New, from plan
    fun getSamplesFromPool(): List<SampleMetadata>      // New, from plan
    // updateSampleMetadata is tricky: plan has (SampleMetadata) -> Unit, interface has (SampleMetadata) -> Boolean (suspend)
    // For now, the planned one will be implemented as a separate public method in Impl
    // and the interface one will be implemented to call the new one or act as a wrapper.
    // Let's refine this: the planned updateSampleMetadata will be the primary one in Impl.
}

class ProjectManagerImpl : ProjectManager {

    private val _samplePool = MutableStateFlow<List<SampleMetadata>>(emptyList())
    val samplePool: StateFlow<List<SampleMetadata>> = _samplePool.asStateFlow()

    // Implementation of new methods from the plan
    override fun addSampleToPool(sampleMetadata: SampleMetadata) {
        val currentPool = _samplePool.value.toMutableList()
        val existingSampleIndex = currentPool.indexOfFirst { it.uri == sampleMetadata.uri }
        if (existingSampleIndex != -1) {
            currentPool[existingSampleIndex] = sampleMetadata
            println("ProjectManager: Replaced existing sample with URI '${sampleMetadata.uri}' in pool.")
        } else {
            currentPool.add(sampleMetadata)
            println("ProjectManager: Added sample '${sampleMetadata.name ?: sampleMetadata.uri}' to pool.")
        }
        _samplePool.value = currentPool
        println("ProjectManager: Pool size: ${_samplePool.value.size}")
        // TODO: Persist changes to project file (C3 core responsibility)
    }

    override fun getSamplesFromPool(): List<SampleMetadata> {
        // TODO: Load from project file if not already loaded (C3 core responsibility)
        return _samplePool.value
    }

    // This is the primary implementation for the M1.3 SampleEditViewModel
    fun updateSampleMetadataNonSuspend(updatedSampleMetadata: SampleMetadata) {
        val currentPool = _samplePool.value.toMutableList()
        // Assuming URI is the key for now. If SampleMetadata gets a unique ID, use that.
        val index = currentPool.indexOfFirst { it.uri == updatedSampleMetadata.uri }
        if (index != -1) {
            currentPool[index] = updatedSampleMetadata
            _samplePool.value = currentPool
            println("ProjectManager: Updated metadata for sample '${updatedSampleMetadata.name ?: updatedSampleMetadata.uri}'")
        } else {
            println("ProjectManager: Could not update metadata. Sample with URI '${updatedSampleMetadata.uri}' not found.")
            // Optionally add if not found, but spec implies update only.
        }
        // TODO: Persist changes to project file (C3 core responsibility)
    }

    // Minimal implementations for original interface methods
    override suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata? {
        // This interface method is more detailed (copyToProjectDir etc.)
        // For M1, we can make it call the simpler addSampleToPool or simulate.
        println("ProjectManager: Interface addSampleToPool(name,uri,copy) called. Simulating add.")
        // Create a SampleMetadata to add via the other method.
        // The `id` field was in an older version of SampleMetadata, the current one from M1.1 has uri, duration, name, trims
        // The SampleMetadata in M1.1 plan does not have a settable `id` field. URI is the main identifier.
        // The name in SampleMetadata is nullable.
        val newSample = SampleMetadata(
            uri = sourceFileUri, // Using sourceFileUri as URI
            duration = 0L, // Dummy duration, should be determined from file
            name = name
            // trimStartMs and trimEndMs will use defaults
        )
        addSampleToPool(newSample) // Call the other addSampleToPool
        return newSample // Or null if simulation fails
    }

    override suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean {
        println("ProjectManager: Interface updateSampleMetadata(sample) called.")
        updateSampleMetadataNonSuspend(sample) // Call the non-suspend version
        return true // Simulate success
    }

    override suspend fun getSampleById(sampleId: String): SampleMetadata? {
        // Assuming sampleId might be a URI in the context of M1.
        // If SampleMetadata had a dedicated ID field, this would search by that.
        println("ProjectManager: Interface getSampleById called for ID: $sampleId")
        return _samplePool.value.firstOrNull { it.uri == sampleId || it.name == sampleId } // Simple search by URI or name
    }
}
