package com.high.theone.di

import android.content.Context
import com.high.theone.audio.AudioEngineControl
import com.high.theone.features.sequencer.PatternManager
import com.high.theone.features.sequencer.RecordingEngine
import com.high.theone.features.sequencer.TimingEngine
import com.high.theone.midi.MidiManager
import com.high.theone.midi.MidiManagerControl
import com.high.theone.midi.device.MidiDeviceManager
import com.high.theone.midi.device.MidiDeviceScanner
import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.input.MidiVelocityCurve
import com.high.theone.midi.integration.MidiAudioEngineControl
import com.high.theone.midi.integration.MidiAudioEngineAdapterImpl
import com.high.theone.midi.integration.MidiSequencerAdapter
import com.high.theone.midi.mapping.MidiLearnManager
import com.high.theone.midi.mapping.MidiMappingEngine
import com.high.theone.midi.mapping.MidiParameterMapper
import com.high.theone.midi.output.MidiClockGenerator
import com.high.theone.midi.output.MidiOutputGenerator
import com.high.theone.midi.output.MidiTransportController
import com.high.theone.midi.repository.MidiConfigurationRepository
import com.high.theone.midi.repository.MidiMappingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing MIDI system dependencies with proper lifecycle management.
 * Configures all MIDI components as singletons for system-wide coordination.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MidiModule {

    companion object {
        
        /**
         * Provides the central MIDI manager as singleton
         */
        @Provides
        @Singleton
        fun provideMidiManager(
            @ApplicationContext context: Context
        ): MidiManager {
            return MidiManager(context)
        }

        /**
         * Provides MIDI device scanner for device discovery
         */
        @Provides
        @Singleton
        fun provideMidiDeviceScanner(
            @ApplicationContext context: Context,
            midiDeviceManager: MidiDeviceManager
        ): MidiDeviceScanner {
            return MidiDeviceScanner(context, midiDeviceManager)
        }



        /**
         * Provides MIDI velocity curve processor
         */
        @Provides
        @Singleton
        fun provideMidiVelocityCurve(): MidiVelocityCurve {
            return MidiVelocityCurve()
        }

        /**
         * Provides MIDI parameter mapper for value transformations
         */
        @Provides
        @Singleton
        fun provideMidiParameterMapper(): MidiParameterMapper {
            return MidiParameterMapper()
        }

        /**
         * Provides MIDI clock generator for output synchronization
         */
        @Provides
        @Singleton
        fun provideMidiClockGenerator(
            outputGenerator: MidiOutputGenerator
        ): MidiClockGenerator {
            return MidiClockGenerator(outputGenerator)
        }

        /**
         * Provides MIDI transport controller for transport messages
         */
        @Provides
        @Singleton
        fun provideMidiTransportController(
            outputGenerator: MidiOutputGenerator,
            clockGenerator: MidiClockGenerator
        ): MidiTransportController {
            return MidiTransportController(outputGenerator, clockGenerator)
        }

        /**
         * Provides MIDI sequencer adapter with sequencer dependencies
         */
        @Provides
        @Singleton
        fun provideMidiSequencerAdapter(
            timingEngine: TimingEngine,
            recordingEngine: RecordingEngine,
            midiClockGenerator: MidiClockGenerator,
            midiTransportController: MidiTransportController,
            patternManager: PatternManager
        ): MidiSequencerAdapter {
            return MidiSequencerAdapter(
                timingEngine = timingEngine,
                recordingEngine = recordingEngine,
                midiClockGenerator = midiClockGenerator,
                midiTransportController = midiTransportController,
                patternManager = patternManager
            )
        }

        /**
         * Provides MIDI audio engine adapter implementation
         */
        @Provides
        @Singleton
        fun provideMidiAudioEngineAdapterImpl(
            audioEngineControl: AudioEngineControl,
            velocityCurve: MidiVelocityCurve
        ): MidiAudioEngineAdapterImpl {
            return MidiAudioEngineAdapterImpl(audioEngineControl, velocityCurve)
        }
    }

    /**
     * Binds MidiManager to MidiManagerControl interface
     */
    @Binds
    abstract fun bindMidiManagerControl(midiManager: MidiManager): MidiManagerControl

    /**
     * Binds MidiAudioEngineAdapterImpl to MidiAudioEngineControl interface
     */
    @Binds
    abstract fun bindMidiAudioEngineControl(
        impl: MidiAudioEngineAdapterImpl
    ): MidiAudioEngineControl
}