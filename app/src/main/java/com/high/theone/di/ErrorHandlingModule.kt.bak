package com.high.theone.di

import android.content.Context
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.compactui.error.AudioEngineRecovery
import com.high.theone.features.compactui.error.ErrorHandlingSystem
import com.high.theone.features.compactui.error.PermissionManager
import com.high.theone.features.compactui.error.StorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for error handling components
 */
@Module
@InstallIn(SingletonComponent::class)
object ErrorHandlingModule {
    
    @Provides
    @Singleton
    fun provideErrorHandlingSystem(
        @ApplicationContext context: Context
    ): ErrorHandlingSystem {
        return ErrorHandlingSystem(context)
    }
    
    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): PermissionManager {
        return PermissionManager(context)
    }
    
    @Provides
    @Singleton
    fun provideAudioEngineRecovery(
        audioEngine: AudioEngineControl
    ): AudioEngineRecovery {
        return AudioEngineRecovery(audioEngine)
    }
    
    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context
    ): StorageManager {
        return StorageManager(context)
    }
}