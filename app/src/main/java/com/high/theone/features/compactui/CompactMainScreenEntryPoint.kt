package com.high.theone.features.compactui

import com.high.theone.features.sampling.PerformanceMonitor
import com.high.theone.features.compactui.PreferenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompactMainScreenEntryPoint {
    fun performanceMonitor(): PerformanceMonitor
    fun preferenceManager(): PreferenceManager
    fun performanceOptimizer(): CompactUIPerformanceOptimizer
}