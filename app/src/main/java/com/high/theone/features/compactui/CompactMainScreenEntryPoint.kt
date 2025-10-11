package com.high.theone.features.compactui

import com.high.theone.features.sampling.PerformanceMonitor
import com.high.theone.features.compactui.error.ErrorHandlingSystem
import com.high.theone.features.compactui.error.PermissionManager
import com.high.theone.features.compactui.error.AudioEngineRecovery
import com.high.theone.features.compactui.error.StorageManager
import com.high.theone.features.compactui.performance.RecordingPerformanceMonitor
import com.high.theone.features.compactui.performance.RecordingMemoryManager
import com.high.theone.features.compactui.performance.RecordingFrameRateMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for CompactMainScreen to access Hilt dependencies
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompactMainScreenEntryPoint {
    fun performanceMonitor(): PerformanceMonitor
    fun preferenceManager(): PreferenceManager
    fun performanceOptimizer(): CompactUIPerformanceOptimizer
    fun errorHandlingSystem(): ErrorHandlingSystem
    fun permissionManager(): PermissionManager
    fun audioEngineRecovery(): AudioEngineRecovery
    fun storageManager(): StorageManager
    fun recordingPerformanceMonitor(): RecordingPerformanceMonitor
    fun recordingMemoryManager(): RecordingMemoryManager
    fun recordingFrameRateMonitor(): RecordingFrameRateMonitor
}