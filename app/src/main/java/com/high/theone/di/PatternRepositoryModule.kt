package com.high.theone.di

import com.high.theone.domain.PatternRepository
import com.high.theone.project.PatternRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for pattern repository and related components.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PatternRepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindPatternRepository(
        patternRepositoryImpl: PatternRepositoryImpl
    ): PatternRepository
}