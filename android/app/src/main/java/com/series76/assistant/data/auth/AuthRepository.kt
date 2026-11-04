package com.series76.assistant.data.auth

import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.LoginRequest
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class AuthRepository(
    private val api: Series76Api,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    val token: Flow<String?> = tokenStore.token

    suspend fun login(email: String, password: String): ApiResult<Unit> =
        apiCall(json) { tokenStore.save(api.login(LoginRequest(email.trim(), password)).token) }

    suspend fun signOut() = tokenStore.clear()
}
