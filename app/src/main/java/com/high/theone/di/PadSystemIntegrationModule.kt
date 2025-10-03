package com.high.theone.di

import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.features.sampling.SampleCacheManager
import com.high.theone.features.sampling.VoiceManager
import com.high.theone.features.sequencer.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Singleton

/**
 * Dependency injection module for sequencer integration and performance optimization.
 * Provides scoped instances for sequencer-pad system integration and performance components.
 */
@Module
@InstallIn(ViewModelComponent::class)
object PadSystemIntegrationModule {

    @Provides
    @ViewModelScoped
    fun providePadSystemIntegration(
        audioEngine: AudioEngineControl,
        samplingViewModel: SamplingViewModel
    ): PadSystemIntegration {
        return PadSystemIntegration(audioEngine, samplingViewModel)
    }

    @Provides
    @ViewModelScoped
    fun provideSequencerPerformanceOptimizer(
        audioEngine: AudioEngineControl,
        sampleCacheManager: SampleCacheManager,
        voiceManager: VoiceManager
    ): SequencerPerformanceOptimizer {
        return SequencerPerformanceOptimizer(audioEngine, sampleCacheManager, voiceManager)
    }

    @Provides
    @Singleton
    fun provideSequencerSampleCache(
        baseSampleCache: SampleCacheManager
    ): SequencerSampleCache {
        return SequencerSampleCache(baseSampleCache)
    }

    @Provides
    @Singleton
    fun provideSequencerVoiceManager(
        baseVoiceManager: VoiceManager
    ): SequencerVoiceManager {
        return SequencerVoiceManager(baseVoiceManager)
    }

    @Provides
    @ViewModelScoped
    fun provideSequencerErrorHandler(
        audioEngine: AudioEngineControl,
        patternRepository: com.high.theone.domain.PatternRepository
    ): SequencerErrorHandler {
        return SequencerErrorHandler(audioEngine, patternRepository)
    }

    @Provides
    @Singleton
    fun provideSequencerLogger(): SequencerLogger {
        return SequencerLogger()
    }
}