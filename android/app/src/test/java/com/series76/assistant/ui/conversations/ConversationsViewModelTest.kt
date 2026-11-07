package com.series76.assistant.ui.conversations

import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.auth.TokenSource
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.ConversationRequest
import com.series76.assistant.data.remote.ConversationResponse
import com.series76.assistant.data.remote.LoginRequest
import com.series76.assistant.data.remote.MessageDebug
import com.series76.assistant.data.remote.MessageView
import com.series76.assistant.data.remote.Page
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.SessionSummary
import com.series76.assistant.data.remote.TokenResponse
import com.series76.assistant.data.remote.TranscriptionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private fun session(id: String) = SessionSummary(id, "Title $id", "2026-11-04T10:00:00+00:00")

/** Two pages: the first names a cursor, the second is the last. */
private class PagedApi : Series76Api {
    val cursors = mutableListOf<String?>()

    override suspend fun sessions(cursor: String?): Page<SessionSummary> {
        cursors += cursor
        return if (cursor == null) Page(listOf(session("1"), session("2")), "next") else Page(listOf(session("3")))
    }

    override suspend fun login(request: LoginRequest) = TokenResponse("token")

    override suspend fun messages(sessionId: String, cursor: String?) = Page(emptyList<MessageView>())

    override suspend fun converse(request: ConversationRequest): ConversationResponse = throw NotImplementedError()

    override suspend fun debug(messageId: String): MessageDebug = throw NotImplementedError()

    override suspend fun catalog(): Nothing = throw NotImplementedError()

    override suspend fun transcribe(audio: RequestBody): TranscriptionResponse = throw NotImplementedError()
}

private object NoTokens : TokenSource {
    override val token = flowOf<String?>(null)
    override val expired = MutableStateFlow(false)

    override suspend fun save(token: String) = Unit

    override suspend fun clear() = Unit

    override suspend fun expire() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {
    private val api = PagedApi()
    private val viewModel by lazy {
        ConversationsViewModel(ConversationRepository(api, Json), AuthRepository(api, NoTokens, Json))
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the next page is appended after the first`() {
        viewModel.load()
        viewModel.loadMore()
        assertEquals(listOf("1", "2", "3"), viewModel.uiState.value.sessions?.map { it.id })
        assertNull(viewModel.uiState.value.nextCursor)
        assertEquals(listOf(null, "next"), api.cursors)
    }

    @Test
    fun `nothing more is asked for after the last page`() {
        viewModel.load()
        viewModel.loadMore()
        viewModel.loadMore()
        assertEquals(2, api.cursors.size)
    }

    @Test
    fun `a refresh starts again from the first page`() {
        viewModel.load()
        viewModel.loadMore()
        viewModel.load()
        assertEquals(listOf("1", "2"), viewModel.uiState.value.sessions?.map { it.id })
        assertEquals("next", viewModel.uiState.value.nextCursor)
    }
}
