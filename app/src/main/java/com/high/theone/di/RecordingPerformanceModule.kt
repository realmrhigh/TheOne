package com.high.theone.di

import com.high.theone.features.compactui.performance.RecordingPerformanceMonitor
import com.high.theone.features.compactui.performance.RecordingMemoryManager
import com.high.theone.features.compactui.performance.RecordingFrameRateMonitor
import com.high.theone.ui.performance.PerformanceMonitor
import com.high.theone.features.compactui.CompactUIPerformanceOptimizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for recording performance monitoring components
 */
@Module
@InstallIn(SingletonComponent::class)
object RecordingPerformanceModule {

    @Provides
    @Singleton
    fun provideRecordingPerformanceMonitor(
        performanceMonitor: PerformanceMonitor,
        compactUIOptimizer: CompactUIPerformanceOptimizer
    ): RecordingPerformanceMonitor {
        return RecordingPerformanceMonitor(performanceMonitor, compactUIOptimizer)
    }

    @Provides
    @Singleton
    fun provideRecordingMemoryManager(): RecordingMemoryManager {
        return RecordingMemoryManager()
    }

    @Provides
    @Singleton
    fun provideRecordingFrameRateMonitor(): RecordingFrameRateMonitor {
        return RecordingFrameRateMonitor()
    }
}