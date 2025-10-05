package com.high.theone.di

import android.content.Context
import com.high.theone.midi.MidiManagerControl
import com.high.theone.midi.service.MidiLifecycleManager
import com.high.theone.midi.service.MidiPermissionManager
import com.high.theone.midi.service.MidiSystemInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing MIDI service-level dependencies.
 * These components manage MIDI system lifecycle, permissions, and initialization.
 */
@Module
@InstallIn(SingletonComponent::class)
object MidiServiceModule {

    /**
     * Provides MIDI lifecycle manager for proper system startup/shutdown
     */
    @Provides
    @Singleton
    fun provideMidiLifecycleManager(
        @ApplicationContext context: Context,
        midiManager: MidiManagerControl
    ): MidiLifecycleManager {
        return MidiLifecycleManager(
            context = context,
            midiManager = midiManager
        )
    }

    /**
     * Provides MIDI permission manager for handling Android MIDI permissions
     */
    @Provides
    @Singleton
    fun provideMidiPermissionManager(
        @ApplicationContext context: Context
    ): MidiPermissionManager {
        return MidiPermissionManager(context)
    }

    /**
     * Provides MIDI system initializer for coordinated startup
     */
    @Provides
    @Singleton
    fun provideMidiSystemInitializer(
        midiManager: MidiManagerControl,
        lifecycleManager: MidiLifecycleManager,
        permissionManager: MidiPermissionManager
    ): MidiSystemInitializer {
        return MidiSystemInitializer(
            midiManager = midiManager,
            lifecycleManager = lifecycleManager,
            permissionManager = permissionManager
        )
    }
}