package com.high.theone.di;

import com.example.theone.domain.ProjectManager
import com.example.theone.domain.ProjectManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindProjectManager(
        projectManagerImpl: ProjectManagerImpl
    ): ProjectManager
}
