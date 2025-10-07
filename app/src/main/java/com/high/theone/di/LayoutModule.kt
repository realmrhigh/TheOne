package com.high.theone.di

import android.content.Context
import com.high.theone.features.compactui.LayoutStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for layout-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object LayoutModule {
    
    @Provides
    @Singleton
    fun provideLayoutStateManager(
        @ApplicationContext context: Context
    ): LayoutStateManager {
        return LayoutStateManager(context)
    }
}