package com.series76.assistant.data.user

import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.apiCall
import kotlinx.serialization.json.Json

class UserRepository(
    private val api: Series76Api,
    private val json: Json,
) {
    /** False when the call fails too: the backend refuses the trace to anyone else, so hiding it costs nothing. */
    suspend fun isDeveloper(): Boolean = (apiCall(json) { api.me() } as? ApiResult.Success)?.value?.isDeveloper == true
}
