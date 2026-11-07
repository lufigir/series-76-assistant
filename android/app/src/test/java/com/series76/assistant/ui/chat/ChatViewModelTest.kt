package com.series76.assistant.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.series76.assistant.data.catalog.CatalogRepository
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.CatalogEntry
import com.series76.assistant.data.remote.ConversationRequest
import com.series76.assistant.data.remote.ConversationResponse
import com.series76.assistant.data.remote.LoginRequest
import com.series76.assistant.data.remote.MAX_QUERY_LENGTH
import com.series76.assistant.data.remote.MessageDebug
import com.series76.assistant.data.remote.MessageView
import com.series76.assistant.data.remote.Page
import com.series76.assistant.data.remote.Requirements
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.SessionSummary
import com.series76.assistant.data.remote.TokenResponse
import com.series76.assistant.data.remote.TranscriptionResponse
import com.series76.assistant.data.voice.AudioRecorder
import com.series76.assistant.data.voice.VoiceRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// asRequestBody only reads the file when the body is written, which the fake API never does.
private fun recordingFile() = File("recording.m4a")

private class FakeApi : Series76Api {
    val sent = mutableListOf<ConversationRequest>()
    var failSend = false
    var failTranscribe = false
    var history = emptyList<MessageView>()
    var older = emptyList<MessageView>()
    var olderCursor: String? = null

    override suspend fun login(request: LoginRequest) = TokenResponse("token")

    override suspend fun sessions(cursor: String?) = Page(emptyList<SessionSummary>())

    override suspend fun messages(sessionId: String, cursor: String?) =
        if (cursor == null) Page(history, olderCursor) else Page(older)

    override suspend fun converse(request: ConversationRequest): ConversationResponse {
        sent += request
        if (failSend) throw IOException()
        return ConversationResponse(
            sessionId = "session-1",
            messageId = "message-${sent.size}",
            answer = "Answer",
            outcome = "answered",
            products = emptyList(),
            alternatives = emptyList(),
            suggestions = emptyList(),
            requirements = Requirements(enclosure = "weatherproof"),
        )
    }

    override suspend fun debug(messageId: String): MessageDebug = throw NotImplementedError()

    override suspend fun catalog() = listOf(CatalogEntry("763A00-11320000/A", "weatherproof", "220v_single_phase"))

    override suspend fun transcribe(audio: RequestBody): TranscriptionResponse {
        if (failTranscribe) throw IOException()
        return TranscriptionResponse("Torque of 761A00-11300000/A")
    }
}

private class FakeRecorder(private val recording: File?) : AudioRecorder {
    var discarded = 0

    override fun start(onLimit: () -> Unit) = true

    override fun stop() = recording

    override fun discard() {
        discarded++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val api = FakeApi()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = ConversationRepository(api, json)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        session: String? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        recorder: AudioRecorder = FakeRecorder(recordingFile()),
    ) = ChatViewModel(saved, session, repository, VoiceRepository(recorder, api, json), CatalogRepository(api, json))

    private fun ChatViewModel.ask(text: String) {
        updateDraft(text)
        sendDraft()
    }

    @Test
    fun `the session id from the first answer is sent with the next question`() {
        val chat = viewModel()
        chat.ask("First")
        chat.ask("Second")
        assertEquals(listOf(null, "session-1"), api.sent.map { it.sessionId })
    }

    @Test
    fun `a new chat keeps its session id after the process dies`() {
        val saved = SavedStateHandle()
        viewModel(saved = saved).ask("First")
        viewModel(saved = saved).ask("Second")
        assertEquals("session-1", api.sent.last().sessionId)
    }

    @Test
    fun `a draft stops at the length the backend accepts`() {
        val chat = viewModel()
        chat.updateDraft("a".repeat(MAX_QUERY_LENGTH + 50))
        assertEquals(MAX_QUERY_LENGTH, chat.draft.length)
    }

    @Test
    fun `a failure marks the message and is not sent again by itself`() {
        api.failSend = true
        val chat = viewModel()
        chat.ask("Question")
        assertEquals(1, api.sent.size)
        assertTrue(chat.uiState.value.messages.single().error != null)
        assertEquals(false, chat.uiState.value.sending)
    }

    @Test
    fun `retry sends the same question and clears the error`() {
        api.failSend = true
        val chat = viewModel()
        chat.ask("Question")
        api.failSend = false
        chat.retry(chat.uiState.value.messages.single())
        assertEquals(listOf("Question", "Question"), api.sent.map { it.query })
        assertEquals(listOf("Question", "Answer"), chat.uiState.value.messages.map { it.text })
        assertNull(chat.uiState.value.messages.first().error)
    }

    @Test
    fun `a transcription fills the box for review and sends nothing`() {
        val recorder = FakeRecorder(recordingFile())
        val chat = viewModel(recorder = recorder)
        chat.toggleRecording()
        assertEquals(VoiceState.RECORDING, chat.uiState.value.voice)
        chat.toggleRecording()
        assertEquals("Torque of 761A00-11300000/A", chat.draft)
        assertTrue(api.sent.isEmpty())
        assertEquals(1, recorder.discarded)
    }

    @Test
    fun `a failed transcription leaves typing available and still deletes the audio`() {
        api.failTranscribe = true
        val recorder = FakeRecorder(recordingFile())
        val chat = viewModel(recorder = recorder)
        chat.updateDraft("Typed so far")
        chat.toggleRecording()
        chat.toggleRecording()
        assertEquals("Typed so far", chat.draft)
        assertEquals(VoiceState.IDLE, chat.uiState.value.voice)
        assertTrue(chat.uiState.value.voiceError != null)
        assertEquals(1, recorder.discarded)
    }

    @Test
    fun `a recording too short to use says so`() {
        val chat = viewModel(recorder = FakeRecorder(null))
        chat.toggleRecording()
        chat.toggleRecording()
        assertEquals(VoiceState.IDLE, chat.uiState.value.voice)
        assertTrue(chat.uiState.value.voiceError != null)
    }

    @Test
    fun `an opened conversation loads its history and its requirements`() {
        api.history =
            listOf(
                MessageView("1", "user", "Question"),
                MessageView("2", "assistant", "Answer", requirements = Requirements(enclosure = "weatherproof")),
            )
        val chat = viewModel(session = "session-1")
        assertEquals(listOf(true, false), chat.uiState.value.messages.map { it.fromUser })
        assertEquals("weatherproof", chat.uiState.value.requirements?.enclosure)
    }

    @Test
    fun `older messages go on top and the cursor moves back`() {
        api.history = listOf(MessageView("3", "user", "Third"), MessageView("4", "assistant", "Fourth"))
        api.older = listOf(MessageView("1", "user", "First"), MessageView("2", "assistant", "Second"))
        api.olderCursor = "older"
        val chat = viewModel(session = "session-1")
        chat.loadOlder()
        assertEquals(listOf("First", "Second", "Third", "Fourth"), chat.uiState.value.messages.map { it.text })
        assertNull(chat.uiState.value.olderCursor)
    }
}
