package com.example.theone.domain

import com.example.theone.model.SampleMetadata // Import the unified SampleMetadata

interface ProjectManager {
    suspend fun addSampleToPool(name: String, sourceFileUri: String, copyToProjectDir: Boolean): SampleMetadata?
    suspend fun updateSampleMetadata(sample: SampleMetadata): Boolean // To save changes from M1.3
    suspend fun getSampleById(sampleId: String): SampleMetadata?     // To load a sample for editing
}
