package com.high.theone.model
import kotlinx.serialization.Serializable // Ensure this import is present
import com.high.theone.model.SampleModels.SampleMetadata // Added import

@Serializable
data class ProjectModel(
    val id: String,
    val name: String,
    val description: String,
    val metadata: SampleMetadata // Use the imported SampleMetadata class
)
