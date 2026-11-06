package com.series76.assistant.data.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

interface AudioRecorder {
    /** Returns false if the microphone could not start. onLimit fires when the maximum duration is reached. */
    fun start(onLimit: () -> Unit): Boolean

    /** Returns the recording, or null if nothing usable was captured. */
    fun stop(): File?

    fun discard()
}

// At 32 kbps a test sample lost a letter of its part number, and at 64 it did not. Two minutes still stay far
// below the backend's 5 MB, so the size never needs its own check.
private const val MAX_DURATION_MS = 120_000
private const val SAMPLE_RATE_HZ = 16_000
private const val BIT_RATE = 64_000

class MediaAudioRecorder(private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private val file = File(context.cacheDir, "recording.m4a")

    override fun start(onLimit: () -> Unit): Boolean {
        discard()
        val created =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioSamplingRate(SAMPLE_RATE_HZ)
            created.setAudioChannels(1)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setMaxDuration(MAX_DURATION_MS)
            created.setOutputFile(file.path)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
            }
            created.prepare()
            created.start()
            // The first reading is always 0 and starts the window that stop() reads.
            created.maxAmplitude
            recorder = created
            true
        } catch (_: IOException) {
            created.release()
            false
        } catch (_: RuntimeException) {
            created.release()
            false
        }
    }

    override fun stop(): File? {
        val active = recorder ?: return null
        recorder = null
        return try {
            // Given pure digital silence, as from a muted or missing microphone, the transcription model invented a
            // part number. A real microphone never reads exactly 0, so that recording is never sent.
            val peak = active.maxAmplitude
            active.stop()
            if (peak > 0) {
                file
            } else {
                file.delete()
                null
            }
        } catch (_: RuntimeException) {
            // MediaRecorder throws here when a recording is too short to hold any data.
            file.delete()
            null
        } finally {
            active.release()
        }
    }

    override fun discard() {
        recorder?.release()
        recorder = null
        file.delete()
    }
}
