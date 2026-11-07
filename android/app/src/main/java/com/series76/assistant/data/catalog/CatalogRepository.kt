package com.series76.assistant.data.catalog

import com.series76.assistant.data.remote.ApiResult
import com.series76.assistant.data.remote.CatalogEntry
import com.series76.assistant.data.remote.Series76Api
import com.series76.assistant.data.remote.apiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** The 68 catalog rows, fetched once per app process: they only change when the data sheet does. */
class CatalogRepository(
    private val api: Series76Api,
    private val json: Json,
) {
    private val lock = Mutex()
    private var cached: List<CatalogEntry>? = null

    suspend fun catalog(): ApiResult<List<CatalogEntry>> =
        lock.withLock {
            cached?.let { return ApiResult.Success(it) }
            apiCall(json) { api.catalog() }.also { if (it is ApiResult.Success) cached = it.value }
        }
}
