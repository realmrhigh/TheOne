package com.high.theone.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Hilt module for MIDI UI components.
 * ViewModels use @HiltViewModel annotation and don't need manual providers.
 * This module is kept for future UI-specific dependencies.
 */
@Module
@InstallIn(ViewModelComponent::class)
object MidiUiModule {
    // ViewModels are automatically provided by Hilt when annotated with @HiltViewModel
    // This module is reserved for future UI-specific dependencies
}