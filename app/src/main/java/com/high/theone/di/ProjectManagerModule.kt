package com.high.theone.di

import com.high.theone.domain.ProjectManager
import com.high.theone.project.ProjectManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectManagerModule {
    @Binds
    @Singleton
    abstract fun bindProjectManager(impl: ProjectManagerImpl): ProjectManager
}
