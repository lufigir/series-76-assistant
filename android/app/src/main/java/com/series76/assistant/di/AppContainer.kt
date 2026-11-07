package com.series76.assistant.di

import android.content.Context
import com.series76.assistant.BuildConfig
import com.series76.assistant.data.auth.AuthRepository
import com.series76.assistant.data.auth.TokenSource
import com.series76.assistant.data.auth.TokenStore
import com.series76.assistant.data.catalog.CatalogRepository
import com.series76.assistant.data.conversation.ConversationRepository
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.voice.MediaAudioRecorder
import com.series76.assistant.data.voice.VoiceRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// The backend gives a turn up to 75 seconds, and OkHttp's default of 10 would cut every slow answer short.
private const val REQUEST_TIMEOUT_SECONDS = 90L

/** Wires the data layer by hand: a few objects and four screens do not justify a DI framework. */
class AppContainer(context: Context) {
    private val tokenStore = TokenStore(context)

    private val json = Json {
        // New response fields must not break an installed app.
        ignoreUnknownKeys = true
    }

    private val client =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // A repeated POST could ask the model twice and hit the conversation's 409.
            .retryOnConnectionFailure(false)
            // With no retries, reusing a socket the server closed after its 5 seconds of keep-alive fails the
            // request with "unexpected end of stream". A new connection costs nothing against a nearby backend.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()

    private val api: Series76Api =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Series76Api::class.java)

    val authRepository = AuthRepository(api, tokenStore, json)
    val conversationRepository = ConversationRepository(api, json)
    val catalogRepository = CatalogRepository(api, json)
    val voiceRepository = VoiceRepository(MediaAudioRecorder(context), api, json)
}

private class AuthInterceptor(private val tokenStore: TokenSource) : Interceptor {
    // OkHttp runs interceptors on its own threads, so blocking here does not touch the main thread.
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.token.first() }
        val request = chain.request()
        val response =
            chain.proceed(
                if (token == null) request
                else request.newBuilder().header("Authorization", "Bearer $token").build()
            )
        // An expired token signs the user out, and the app goes back to login by observing the store.
        if (response.code == 401 && token != null) runBlocking { tokenStore.expire() }
        return response
    }
}
