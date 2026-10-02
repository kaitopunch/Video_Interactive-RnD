package com.pion.psremote.data.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.data.catalogue.CatalogueApi
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.DemoSummary
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * What opening a game does with the list Home read, and every way a catalogue entry can be incomplete — without a
 * network: an interceptor stands in for the server and a lambda for the video's length. Under Robolectric only
 * because `load` is a `traceAsync` section, which calls `android.os.Trace`. The live catalogue and the real duration
 * read are `RemoteDemoRepositoryDeviceTest`'s, on a phone.
 */
@RunWith(AndroidJUnit4::class)
class RemoteDemoRepositoryTest {

    /** What the server answers next. Read on OkHttp's thread, so `@Volatile`. */
    @Volatile private var catalogue: String = catalogueOf(SPIDER_MAN)
    @Volatile private var unreachable = false
    private val requests = AtomicInteger()

    private var duration: AppResult<Long> = AppResult.Success(DURATION_MS)
    private val durationReads = mutableListOf<String>()

    private val api = CatalogueApi(
        apiKey = "test-key",
        client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.incrementAndGet()
            if (unreachable) throw UnknownHostException("api.piontech.site")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(catalogue.toResponseBody()).build()
        }).build(),
    )

    /** A fresh one holds no list, as after process death. */
    private fun newRepository() = RemoteDemoRepository(api, durations = { url -> durationReads += url; duration })

    private val repository = newRepository()

    @Test
    fun `a game Home listed opens without a second catalogue request`() = runTest {
        repository.list()

        assertEquals(AppResult.Success(DemoSource(VIDEO_URL, DURATION_MS, SCRIPT)), repository.load(SPIDER_MAN_ID))
        assertEquals(1, requests.get())
        assertEquals(listOf(VIDEO_URL), durationReads)
    }

    @Test
    fun `after process death the catalogue is fetched again to open the game`() = runTest {
        assertEquals(AppResult.Success(DemoSource(VIDEO_URL, DURATION_MS, SCRIPT)), repository.load(SPIDER_MAN_ID))
        assertEquals(1, requests.get())
    }

    @Test
    fun `the game the user tapped still opens after the CMS removed it`() = runTest {
        repository.list()
        catalogue = catalogueOf()

        assertTrue(repository.load(SPIDER_MAN_ID) is AppResult.Success)
        assertEquals(1, requests.get())
    }

    @Test
    fun `an id no catalogue lists is not found, naming the id, after one fresh look`() = runTest {
        repository.list()

        assertEquals(AppResult.Failure(AppError.NotFound("deleted-game")), repository.load("deleted-game"))
        assertEquals(2, requests.get())
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun `no connection while opening after process death is a network failure`() = runTest {
        unreachable = true

        assertEquals(AppResult.Failure(AppError.Network("UnknownHostException")), repository.load(SPIDER_MAN_ID))
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun `a failed refresh keeps the games already listed openable`() = runTest {
        repository.list()
        unreachable = true

        assertEquals(AppResult.Failure(AppError.Network("UnknownHostException")), repository.list())
        assertTrue(repository.load(SPIDER_MAN_ID) is AppResult.Success)
    }

    @Test
    fun `the list is Home's order with hidden games left out`() = runTest {
        catalogue = catalogueOf(
            """{"id":"b","name":"Second","priority":2}""",
            """{"id":"hidden","name":"Draft","priority":0,"status":false}""",
            """{"id":"a","name":"First","priority":1}""",
        )

        assertEquals(AppResult.Success(listOf(DemoSummary("a", "First"), DemoSummary("b", "Second"))), repository.list())
    }

    @Test
    fun `an entry with no custom fields names the script field, and no video is read`() = runTest {
        catalogue = catalogueOf("""{"id":"$SPIDER_MAN_ID","name":"Spider Man"}""")

        assertEquals(AppResult.Failure(AppError.NotFound("custom_fields.json")), repository.load(SPIDER_MAN_ID))
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun `an entry with a script but no video names the video field`() = runTest {
        catalogue = catalogueOf(entry(script = SCRIPT, video = null))

        assertEquals(AppResult.Failure(AppError.NotFound("custom_fields.source_vid")), repository.load(SPIDER_MAN_ID))
        assertTrue(durationReads.isEmpty())
    }

    @Test
    fun `a field the CMS left blank is as missing as one it left out`() = runTest {
        catalogue = catalogueOf(entry(script = "", video = VIDEO_URL))
        assertEquals(AppResult.Failure(AppError.NotFound("custom_fields.json")), newRepository().load(SPIDER_MAN_ID))

        catalogue = catalogueOf(entry(script = SCRIPT, video = "   "))
        assertEquals(AppResult.Failure(AppError.NotFound("custom_fields.source_vid")), newRepository().load(SPIDER_MAN_ID))

        assertTrue("a blank URL reached the duration read", durationReads.isEmpty())
    }

    @Test
    fun `a video whose length cannot be read fails the load with that reason`() = runTest {
        duration = AppResult.Failure(AppError.Network("timeout"))

        assertEquals(AppResult.Failure(AppError.Network("timeout")), repository.load(SPIDER_MAN_ID))
        assertEquals(listOf(VIDEO_URL), durationReads)
    }

    @Test
    fun `a broken script is still handed over, for the demo screen to report`() = runTest {
        catalogue = catalogueOf(entry(script = "not json", video = VIDEO_URL))

        assertEquals(AppResult.Success(DemoSource(VIDEO_URL, DURATION_MS, "not json")), repository.load(SPIDER_MAN_ID))
    }

    private companion object {
        const val SPIDER_MAN_ID = "spider-man"
        const val VIDEO_URL = "https://s3.example/spiderman.mp4"
        const val DURATION_MS = 139_000L
        const val SCRIPT = """[{"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["CROSS"],"playbackSpeed":0.25,"slowDurationMs":3000}]"""

        val SPIDER_MAN = entry(SCRIPT, VIDEO_URL)

        /** One entry; [script] is embedded as the JSON string the CMS stores it as. */
        fun entry(script: String?, video: String?): String {
            val fields = listOfNotNull(
                script?.let { "\"json\":${jsonString(it)}" },
                video?.let { "\"source_vid\":${jsonString(it)}" },
            ).joinToString(",")
            return """{"id":"$SPIDER_MAN_ID","name":"Spider Man","priority":1,"custom_fields":{$fields}}"""
        }

        fun catalogueOf(vararg entries: String) =
            """{"message":"success","status":200,"data":[${entries.joinToString(",")}]}"""

        fun jsonString(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
