package com.example.theone.model

import com.example.theone.model.SampleMetadata // Corrected import

data class Project(
    val name: String,
    val samples: List<SampleMetadata>,
    val sequences: List<Sequence>
    // Add other project-related data here
)
