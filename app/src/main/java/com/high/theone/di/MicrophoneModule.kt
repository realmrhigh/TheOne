package com.high.theone.di

import com.high.theone.audio.MicrophoneInput
import com.high.theone.audio.MicrophoneInputImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MicrophoneModule {

    @Binds
    abstract fun bindMicrophoneInput(impl: MicrophoneInputImpl): MicrophoneInput
}
