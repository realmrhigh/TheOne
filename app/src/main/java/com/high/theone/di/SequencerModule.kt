package com.high.theone.di

import com.high.theone.features.sequencer.*
import dagger.Binds
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
    
    @Provides
    @Singleton
    fun provideTimingCalculator(): TimingCalculator = TimingCalculator()
    
    @Provides
    @Singleton
    fun provideSwingCalculator(): SwingCalculator = SwingCalculator()
    
    @Provides
    @Singleton
    fun provideStepCallbackManager(): StepCallbackManager = StepCallbackManager()
    
    @Provides
    @Singleton
    fun provideTempoSwingController(): TempoSwingController = TempoSwingController()
    
    @Provides
    @Singleton
    fun provideAudioLatencyDetector(): AudioLatencyDetector = AudioLatencyDetector()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SequencerBindingModule {
    
    @Binds
    @Singleton
    abstract fun bindTimingEngine(
        precisionTimingEngine: PrecisionTimingEngine
    ): TimingEngine
}
