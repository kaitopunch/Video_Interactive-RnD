package com.pion.psremote.data.catalogue

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

/**
 * The request the app sends and every way the answer can go wrong, without a network: an interceptor stands in
 * for the server. The live catalogue is checked on a phone, by `RemoteDemoRepositoryDeviceTest`.
 */
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
