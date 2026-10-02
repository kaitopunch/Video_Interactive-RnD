package com.pion.psremote.data.catalogue

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The store's catalogue: one GET lists every game in the PS Remote category (confirm.md H1). The only file that
 * imports OkHttp (LLM.md §2). Never throws across this boundary (MVI doc §5): every failure is an [AppError.Network].
 *
 * [apiKey] is `BuildConfig.CATALOGUE_API_KEY`, from `local.properties` (confirm.md H5). Without it the server
 * answers 404, not 401, so a "HTTP 404" here usually means a build made without the key.
 */
class CatalogueApi(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun items(): AppResult<List<CatalogueItem>> {
        val request = Request.Builder().url(BASE_URL + ITEMS).header(API_KEY_HEADER, apiKey).build()
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    AppResult.Failure(AppError.Network("HTTP ${response.code}"))
                } else {
                    // Off the main thread: the caller is Home's ViewModel, and each entry carries a whole script.
                    withContext(Dispatchers.IO) { parse(response.body.string()) }
                }
            }
        } catch (unreachable: IOException) {
            AppResult.Failure(AppError.Network(unreachable::class.simpleName))
        }
    }

    companion object {
        private const val BASE_URL = "https://api.piontech.site/stores/"
        private const val ITEMS = "api/v6.0/public/items/get-all?category_id=5f388373-ec63-441e-9054-49fb02e9941f"
        private const val API_KEY_HEADER = "X-API-Key"

        /** The whole call — connect, request, answer — bounds Home's spinner (MVI doc §9, "every network wait is bounded"). */
        private const val CALL_TIMEOUT_S = 15L

        /** A field the app does not read is not an error: the CMS adds fields without telling the app. */
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build()

        internal fun parse(body: String): AppResult<List<CatalogueItem>> = try {
            AppResult.Success(json.decodeFromString<CatalogueResponse>(body).data)
        } catch (malformed: IllegalArgumentException) { // SerializationException is one
            AppResult.Failure(AppError.Network(malformed::class.simpleName))
        }
    }
}

/**
 * [Call.enqueue] as a suspend call that leaving the screen cancels. `execute()` on the IO dispatcher would keep the
 * thread, and the connection, until the timeout.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, unused, _ -> unused.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
