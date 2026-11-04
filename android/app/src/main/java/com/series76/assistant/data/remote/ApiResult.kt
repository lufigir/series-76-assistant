package com.series76.assistant.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val message: String) : ApiResult<Nothing>
}

const val UNREACHABLE_MESSAGE = "The server can't be reached. Check that the backend is running and try again."
const val TIMEOUT_MESSAGE = "The server took too long to answer. Please try again."
const val UNEXPECTED_MESSAGE = "The server sent an unexpected response. Please try again."

/** Every API call goes through here, so a screen only ever deals with a value or a message it can show. */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (error: HttpException) {
        ApiResult.Failure(errorMessage(json, error.response()?.errorBody()?.string()))
    } catch (_: InterruptedIOException) {
        ApiResult.Failure(TIMEOUT_MESSAGE)
    } catch (_: IOException) {
        ApiResult.Failure(UNREACHABLE_MESSAGE)
    } catch (_: SerializationException) {
        ApiResult.Failure(UNEXPECTED_MESSAGE)
    }

internal fun errorMessage(json: Json, body: String?): String =
    try {
        body?.let { json.decodeFromString<ApiErrorBody>(it).message } ?: UNEXPECTED_MESSAGE
    } catch (_: SerializationException) {
        UNEXPECTED_MESSAGE
    }
