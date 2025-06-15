package com.high.theone.features.sampleeditor

import com.high.theone.model.SampleMetadata
import dagger.assisted.AssistedFactory

@AssistedFactory
interface SampleEditViewModelFactory {
    fun create(initialSampleMetadata: SampleMetadata): SampleEditViewModel
}
