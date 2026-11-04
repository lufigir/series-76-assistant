package com.series76.assistant.data.conversation

import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.ConversationRequest
import com.series76.assistant.data.remote.ConversationResponse
import com.series76.assistant.data.remote.MessageDebug
import com.series76.assistant.data.remote.MessageView
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.SessionSummary
import com.series76.assistant.data.remote.apiCall
import kotlinx.serialization.json.Json

class ConversationRepository(
    private val api: Series76Api,
    private val json: Json,
) {
    suspend fun sessions(): ApiResult<List<SessionSummary>> = apiCall(json) { api.sessions() }

    suspend fun messages(sessionId: String): ApiResult<List<MessageView>> =
        apiCall(json) { api.messages(sessionId) }

    suspend fun send(sessionId: String?, query: String): ApiResult<ConversationResponse> =
        apiCall(json) { api.converse(ConversationRequest(sessionId, query)) }

    suspend fun debug(messageId: String): ApiResult<MessageDebug> =
        apiCall(json) { api.debug(messageId) }
}
