package com.series76.assistant.data.voice

import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.apiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

/** Speech to text: the microphone and the backend behind one door, and no recording left on the device. */
class VoiceRepository(
    private val recorder: AudioRecorder,
    private val api: Series76Api,
    private val json: Json,
) {
    fun startRecording(onLimit: () -> Unit): Boolean = recorder.start(onLimit)

    fun discardRecording() = recorder.discard()

    /** Stops and transcribes. Null means the recording held nothing usable. */
    suspend fun stopAndTranscribe(): ApiResult<String>? {
        val recording = recorder.stop() ?: return null
        return try {
            apiCall(json) { api.transcribe(recording.asRequestBody(M4A)).text }
        } finally {
            recorder.discard()
        }
    }

    private companion object {
        val M4A = "audio/mp4".toMediaType()
    }
}
