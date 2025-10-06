package com.high.theone.di

import com.high.theone.ui.performance.PerformanceMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for compact UI components
 */
@Module
@InstallIn(SingletonComponent::class)
object CompactUIModule {
    
    @Provides
    @Singleton
    fun providePerformanceMonitor(): PerformanceMonitor {
        return PerformanceMonitor()
    }
}