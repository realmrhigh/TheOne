package com.example.theone.audioengine

interface AudioEngineControl {
    fun getEngineVersion(): String
    fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) // Added
}

class AudioEngineControlImpl : AudioEngineControl {
    external override fun getEngineVersion(): String
    external override fun initialize(sampleRate: Int, bufferSize: Int, enableLowLatency: Boolean): Boolean
    external override fun setMetronomeState(isEnabled: Boolean, bpm: Float, timeSignatureNum: Int, timeSignatureDen: Int, soundPrimaryUri: String, soundSecondaryUri: String?) // Added

    companion object {
        init {
            System.loadLibrary("audioengine")
        }
    }
}
