package com.high.theone.di

import android.content.Context
import com.high.theone.audio.AudioEngine // Implementation class
import com.high.theone.audio.AudioEngineControl // Interface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioEngineModule {

    @Provides
    @Singleton
    fun provideAudioEngineControl(@ApplicationContext context: Context): AudioEngineControl {
        return AudioEngine(context)
    }
}
