package com.series76.assistant.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiResultTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun http(status: Int, body: String) =
        HttpException(Response.error<Any>(status, body.toResponseBody()))

    @Test
    fun `a backend error shows its own message`() = runTest {
        val body = """{"status":409,"message":"Still answering","data":null,"trace_id":"abc"}"""
        assertEquals(ApiResult.Failure("Still answering"), apiCall<Unit>(json) { throw http(409, body) })
    }

    @Test
    fun `an error body that is not ours does not leak to the screen`() = runTest {
        assertEquals(ApiResult.Failure(UNEXPECTED_MESSAGE), apiCall<Unit>(json) { throw http(502, "<html>") })
    }

    @Test
    fun `no connection and a timeout read differently`() = runTest {
        assertEquals(ApiResult.Failure(UNREACHABLE_MESSAGE), apiCall<Unit>(json) { throw IOException() })
        assertEquals(ApiResult.Failure(TIMEOUT_MESSAGE), apiCall<Unit>(json) { throw SocketTimeoutException() })
    }

    @Test
    fun `a response that does not parse is reported, not thrown`() = runTest {
        assertEquals(ApiResult.Failure(UNEXPECTED_MESSAGE), apiCall<Unit>(json) { throw SerializationException() })
    }
}
