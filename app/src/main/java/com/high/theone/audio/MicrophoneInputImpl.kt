package com.high.theone.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MicrophoneInputImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MicrophoneInput {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    override fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Consider requesting permission here or ensuring it's granted before calling
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord?.startRecording()
        isRecording = true
    }

    override fun stopRecording() {
        if (isRecording) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
        }
    }

    override fun read(): ShortArray? {
        if (!isRecording || audioRecord == null) return null
        val buffer = ShortArray(bufferSize / 2) // Each short is 2 bytes
        val readResult = audioRecord?.read(buffer, 0, buffer.size)
        return if (readResult != null && readResult > 0) {
            buffer.copyOfRange(0, readResult)
        } else {
            null
        }
    }

    override fun getAmplitude(): Float {
        // This is a simplified amplitude calculation. 
        // For a more accurate RMS or peak amplitude, further processing is needed.
        if (!isRecording || audioRecord == null) return 0f
        val buffer = ShortArray(bufferSize / 2)
        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
        if (readSize > 0) {
            var sum = 0.0
            for (i in 0 until readSize) {
                sum += buffer[i] * buffer[i]
            }
            val rms = Math.sqrt(sum / readSize)
            return (rms / 32767.0).toFloat() // Normalize to 0-1 range
        }
        return 0f
    }

    override fun isRecording(): Boolean = isRecording

    override fun release() {
        stopRecording()
    }
}
