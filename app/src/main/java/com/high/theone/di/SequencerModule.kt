package com.high.theone.di

import com.high.theone.features.sequencer.SequencerEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SequencerModule {
    @Provides
    @Singleton
    fun provideSequencerEventBus(): SequencerEventBus = SequencerEventBus()
}
