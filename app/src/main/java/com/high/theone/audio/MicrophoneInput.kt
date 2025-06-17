package com.high.theone.audio

interface MicrophoneInput {
    fun startRecording()
    fun stopRecording()
    fun read(): ShortArray? // Or ByteArray, depending on processing needs
    fun getAmplitude(): Float
    fun isRecording(): Boolean
    fun release()
}
