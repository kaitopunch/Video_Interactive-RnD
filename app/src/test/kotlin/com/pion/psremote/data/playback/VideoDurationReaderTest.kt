package com.pion.psremote.data.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How each outcome of a duration read reaches the error screen: a broken game (the BA fixes `source_vid`) or a
 * connection problem (try again). The retriever itself needs Media3's extractors and a network, so it is
 * `RemoteDemoRepositoryDeviceTest`'s; this suite drives [VideoDurationReader.read] with a lambda in its place.
 * Under Robolectric only because Media3's exceptions carry a `DataSpec`, which holds an `android.net.Uri`.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VideoDurationReaderTest {

    @Test
    fun `a length in microseconds comes back in whole milliseconds`() = runTest {
        assertEquals(AppResult.Success(139_000L), read { 139_000_999L })
    }

    @Test
    fun `a video with no known length is not found, naming its URL`() = runTest {
        assertEquals(AppResult.Failure(AppError.NotFound(URL)), read { C.TIME_UNSET })
    }

    @Test
    fun `a read still going after sixty seconds is a network timeout`() = runTest {
        assertEquals(AppResult.Failure(AppError.Network("timeout")), read { awaitCancellation() })
        assertEquals(60_000L, currentTime)
    }

    @Test
    fun `a slow read that answers before the backstop still succeeds`() = runTest {
        // LLM.md §12: on a dipping Wi-Fi the header took 14 s, and a 15 s limit turned a slow start into an error.
        assertEquals(AppResult.Success(139_000L), read { delay(59_000); 139_000_000L })
    }

    @Test
    fun `a 4xx is the URL's fault, so the game is not found`() = runTest {
        listOf(401, 403, 404, 410).forEach { code ->
            assertEquals("HTTP $code", AppResult.Failure(AppError.NotFound(URL)), read { throw invalidResponse(code) })
        }
    }

    @Test
    fun `a 5xx is the server's trouble, so it is a network failure naming the status`() = runTest {
        listOf(500, 502, 503).forEach { code ->
            assertEquals(AppResult.Failure(AppError.Network("HTTP $code")), read { throw invalidResponse(code) })
        }
    }

    @Test
    fun `no connection is a network failure, not a missing game`() = runTest {
        val unreachable = HttpDataSource.HttpDataSourceException(
            DataSpec(Uri.parse(URL)),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            HttpDataSource.HttpDataSourceException.TYPE_OPEN,
        )

        assertEquals(AppResult.Failure(AppError.Network("HttpDataSourceException")), read { throw unreachable })
    }

    @Test
    fun `a file that is not a video Media3 can read is not found`() = runTest {
        val notAVideo = ParserException.createForMalformedContainer("not an mp4", null)

        assertEquals(AppResult.Failure(AppError.NotFound(URL)), read { throw notAVideo })
    }

    @Test
    fun `leaving the screen cancels the read without reporting a failure`() = runTest {
        var result: AppResult<Long>? = null
        val reading = launch { result = read { awaitCancellation() } }
        runCurrent()

        reading.cancel()
        runCurrent()

        assertTrue(reading.isCancelled)
        assertNull(result)
    }

    private suspend fun read(retrieve: suspend () -> Long) = VideoDurationReader.read(URL, retrieve)

    private fun invalidResponse(code: Int) = HttpDataSource.InvalidResponseCodeException(
        code, "-", null, emptyMap(), DataSpec(Uri.parse(URL)), ByteArray(0),
    )

    private companion object {
        const val URL = "https://s3.example/spiderman.mp4"
    }
}
