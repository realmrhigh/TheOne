package com.high.theone.di

import com.high.theone.domain.SampleRepository
import com.high.theone.project.SampleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for SampleRepository.
 * Provides the SampleRepository implementation for the sampling feature.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SampleRepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindSampleRepository(
        sampleRepositoryImpl: SampleRepositoryImpl
    ): SampleRepository
}