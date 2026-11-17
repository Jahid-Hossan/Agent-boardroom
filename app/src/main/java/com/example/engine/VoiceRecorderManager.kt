package com.example.engine

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording() {
        stopAndDiscard()
        val file = File(context.cacheDir, "voice_prompt_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
    }

    fun stopAndReadBytes(): ByteArray? {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Ignored if stopped immediately
        } finally {
            runCatching { recorder?.release() }
            recorder = null
        }

        return try {
            val bytes = if (file != null && file.exists()) file.readBytes() else null
            file?.delete()
            outputFile = null
            bytes
        } catch (_: Exception) {
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            runCatching { recorder?.release() }
            recorder = null
        }
        outputFile?.delete()
        outputFile = null
    }
}
