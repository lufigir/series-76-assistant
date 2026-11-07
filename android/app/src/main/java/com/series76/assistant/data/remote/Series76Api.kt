package com.series76.assistant.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface Series76Api {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("api/sessions")
    suspend fun sessions(@Query("cursor") cursor: String? = null): Page<SessionSummary>

    @GET("api/sessions/{id}/messages")
    suspend fun messages(@Path("id") sessionId: String, @Query("cursor") cursor: String? = null): Page<MessageView>

    @GET("api/actuators/part-numbers")
    suspend fun catalog(): List<CatalogEntry>

    @POST("api/conversation")
    suspend fun converse(@Body request: ConversationRequest): ConversationResponse

    @GET("api/messages/{id}/debug")
    suspend fun debug(@Path("id") messageId: String): MessageDebug

    @POST("api/transcriptions")
    suspend fun transcribe(@Body audio: RequestBody): TranscriptionResponse
}
