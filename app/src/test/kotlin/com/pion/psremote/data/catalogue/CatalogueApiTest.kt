package com.pion.psremote.data.catalogue

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The request the app sends and every way the answer can go wrong, without a network: an interceptor stands in
 * for the server. The live catalogue is checked on a phone, by `RemoteDemoRepositoryDeviceTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueApiTest {

    private var sent: Request? = null

    private fun api(answer: (Request) -> Response) = CatalogueApi(
        apiKey = KEY,
        client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            sent = chain.request()
            answer(chain.request())
        }).build(),
    )

    private fun respond(code: Int, body: String) = { request: Request ->
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("-")
            .body(body.toResponseBody()).build()
    }

    @Test
    fun `asks for the PS Remote category with the key`() = runTest {
        api(respond(200, RESPONSE)).items()

        val request = requireNotNull(sent)
        assertEquals(KEY, request.header("X-API-Key"))
        assertEquals(
            "https://api.piontech.site/stores/api/v6.0/public/items/get-all" +
                "?category_id=5f388373-ec63-441e-9054-49fb02e9941f",
            request.url.toString(),
        )
    }

    @Test
    fun `reads every item, with fields it does not know ignored`() = runTest {
        val items = (api(respond(200, RESPONSE)).items() as AppResult.Success).value

        assertEquals(
            listOf(
                CatalogueItem(
                    id = "6ef63378-d525-4633-a244-2ea68bf3ef19",
                    name = "Sample",
                    priority = 1,
                    status = true,
                    customFields = CustomFields(json = "[]", sourceVid = "https://s3.example/sample.mp4"),
                ),
                CatalogueItem(id = "no-fields", name = "Draft", priority = 2, status = false),
            ),
            items,
        )
    }

    /** The server's answer to a request without the key, or with a wrong one. */
    @Test
    fun `an error status is a network failure naming it`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.Network("HTTP 404")),
            api(respond(404, "Cannot GET /stores/api/v6.0/public/items/get-all")).items(),
        )
    }

    @Test
    fun `no connection is a network failure, not a crash`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.Network("UnknownHostException")),
            api { throw UnknownHostException("api.piontech.site") }.items(),
        )
    }

    @Test
    fun `a body that is not the catalogue is a network failure, not a crash`() = runTest {
        val result = api(respond(200, "<html>maintenance</html>")).items()

        assertEquals(AppError.Network::class, ((result as AppResult.Failure).error)::class)
    }

    @Test
    fun `a server error is a network failure naming its status`() = runTest {
        assertEquals(AppResult.Failure(AppError.Network("HTTP 503")), api(respond(503, "")).items())
    }

    @Test
    fun `a call that times out is a network failure, not a crash`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.Network("SocketTimeoutException")),
            api { throw SocketTimeoutException("timeout") }.items(),
        )
    }

    /** MVI §9, "every network wait is bounded": without it a server that never answers keeps Home's spinner up. */
    @Test
    fun `the whole call is bounded, so Home's spinner always comes down`() {
        assertEquals(15_000, CatalogueApi.defaultClient().callTimeoutMillis)
    }

    /** An empty catalogue would tell the BA the category is empty; an empty body says nothing about it. */
    @Test
    fun `an empty answer is a failure, not an empty list`() = runTest {
        val result = api(respond(200, "")).items()

        assertEquals(AppError.Network::class, ((result as AppResult.Failure).error)::class)
    }

    @Test
    fun `a field of the wrong type is a failure, not a crash`() {
        val result = CatalogueApi.parse("""{"data": [{"id": "a", "priority": "first", "status": "yes"}]}""")

        assertEquals(AppError.Network::class, ((result as AppResult.Failure).error)::class)
    }

    @Test
    fun `leaving Home cancels the request in flight`() = runTest {
        val sent = LinkedBlockingQueue<Call>()
        val release = CountDownLatch(1)
        val api = CatalogueApi(KEY, OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            sent.put(chain.call())
            release.await(5, TimeUnit.SECONDS)
            throw IOException("released by the test")
        }).build())

        val fetch = launch { api.items() }
        runCurrent()
        val call = requireNotNull(sent.poll(5, TimeUnit.SECONDS)) { "the request was never sent" }
        fetch.cancel()
        release.countDown()

        assertTrue("the connection was kept until the timeout", call.isCanceled())
    }

    @Test
    fun `an item without an id fails the whole list`() {
        val result = CatalogueApi.parse("""{"data": [{"name": "No id"}]}""")

        assertEquals(AppError.Network::class, ((result as AppResult.Failure).error)::class)
    }

    @Test
    fun `an answer with no data is an empty list`() {
        assertEquals(AppResult.Success(emptyList<CatalogueItem>()), CatalogueApi.parse("""{"message": "success"}"""))
    }

    private companion object {
        const val KEY = "test-key"

        /** The shape the server answered on 2026-10-02, cut to two items. */
        const val RESPONSE = """{"message":"success","status":200,"data":[
            {"id":"6ef63378-d525-4633-a244-2ea68bf3ef19","category_id":"5f388373-ec63-441e-9054-49fb02e9941f",
             "priority":1,"name":"Sample","status":true,"view":0,"like":0,"old_id":9771,
             "custom_fields":{"json":"[]","source_vid":"https://s3.example/sample.mp4"}},
            {"id":"no-fields","name":"Draft","priority":2,"status":false}
        ]}"""
    }
}
