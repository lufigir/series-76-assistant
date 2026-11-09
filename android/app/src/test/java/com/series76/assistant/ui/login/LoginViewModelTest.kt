package com.series76.assistant.ui.login

import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.auth.TokenSource
import com.series76.assistant.data.remote.ConversationRequest
import com.series76.assistant.data.remote.CurrentUser
import com.series76.assistant.data.remote.LoginRequest
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeTokens : TokenSource {
    override val token = MutableStateFlow<String?>(null)
    override val expired = MutableStateFlow(false)

    override suspend fun save(token: String) {
        this.token.value = token.ifEmpty { null }
        if (token.isNotEmpty()) expired.value = false
    }

    override suspend fun clear() {
        expired.value = false
        save("")
    }

    override suspend fun expire() {
        expired.value = true
        save("")
    }
}

private class LoginOnlyApi : Series76Api {
    override suspend fun login(request: LoginRequest) = TokenResponse("token")

    override suspend fun me(): CurrentUser = throw NotImplementedError()

    override suspend fun sessions(cursor: String?): Nothing = throw NotImplementedError()

    override suspend fun messages(sessionId: String, cursor: String?): Nothing = throw NotImplementedError()

    override suspend fun converse(request: ConversationRequest) = throw NotImplementedError()

    override suspend fun debug(messageId: String) = throw NotImplementedError()

    override suspend fun catalog(): Nothing = throw NotImplementedError()

    override suspend fun transcribe(audio: RequestBody) = throw NotImplementedError()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val tokens = FakeTokens()
    private val auth = AuthRepository(LoginOnlyApi(), tokens, Json)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an expired session says so on the login screen`() = runTest {
        val login = LoginViewModel(auth)
        tokens.expire()
        assertEquals(SESSION_EXPIRED_MESSAGE, login.uiState.value.error)
    }

    @Test
    fun `signing out by hand shows no message`() = runTest {
        val login = LoginViewModel(auth)
        tokens.clear()
        assertNull(login.uiState.value.error)
    }

    @Test
    fun `the same screen warns again after a later expiry`() = runTest {
        val login = LoginViewModel(auth)
        tokens.expire()
        login.login("ana@series76.test", "secret")
        assertNull(login.uiState.value.error)
        tokens.expire()
        assertEquals(SESSION_EXPIRED_MESSAGE, login.uiState.value.error)
    }
}
