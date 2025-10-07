package com.high.theone.di

import android.content.Context
import com.high.theone.features.compactui.PreferenceManager
import com.high.theone.features.compactui.CompactUIPerformanceOptimizer
import com.high.theone.ui.performance.PerformanceMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for Compact UI components
 */
@Module
@InstallIn(SingletonComponent::class)
object CompactUIModule {
    
    @Provides
    @Singleton
    fun providePreferenceManager(
        @ApplicationContext context: Context
    ): PreferenceManager {
        return PreferenceManager(context)
    }
    
    @Provides
    @Singleton
    fun provideCompactUIPerformanceOptimizer(
        performanceMonitor: PerformanceMonitor
    ): CompactUIPerformanceOptimizer {
        return CompactUIPerformanceOptimizer(performanceMonitor)
    }
}