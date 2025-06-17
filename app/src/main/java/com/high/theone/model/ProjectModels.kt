package com.high.theone.model

import kotlinx.serialization.Serializable
import com.high.theone.model.SampleMetadata // Import SampleMetadata directly from this package

@Serializable
data class ProjectModel(
    val id: String,
    val name: String,
    val description: String,
    val metadata: SampleMetadata // Use the directly imported SampleMetadata class
)
