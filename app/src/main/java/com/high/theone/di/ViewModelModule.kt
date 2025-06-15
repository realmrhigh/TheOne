package com.high.theone.di

import android.content.Context
import com.high.theone.domain.ProjectManager
import com.high.theone.features.sequencer.SequencerEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ViewModelModule {

    @Provides
    @Singleton
    fun provideSequencerEventBus(): SequencerEventBus {
        return SequencerEventBus() // Assuming it has a default constructor
    }

    @Provides
    @Singleton
    fun provideProjectManager(@ApplicationContext context: Context): ProjectManager {
        // Assuming ProjectManager has a constructor like: ProjectManager(context: Context)
        // This is a guess. The actual constructor needs to be determined from its source code.
        // If it has no constructor dependencies, then ProjectManager() would be used.
        // If it has other dependencies, they need to be passed here and also be Hilt-provided.
        return ProjectManager(context)
    }
}
