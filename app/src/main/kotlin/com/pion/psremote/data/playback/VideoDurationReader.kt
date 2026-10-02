package com.pion.psremote.data.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.inspector.MetadataRetriever
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * A video's length, read before the player exists, so the whole script — "every stop point is before the end of
 * the video" included — is checked before a single frame plays (confirm.md Q12).
 *
 * Media3's retriever rather than the platform's `MediaMetadataRetriever`: it reads through [VideoCache], so the
 * header it fetches is the one the player then reads from disk, and it can be cancelled and bounded. The
 * platform's opens its own connection and blocks with no timeout.
 */
@OptIn(UnstableApi::class)
class VideoDurationReader(context: Context, private val cache: VideoCache) {

    private val context = context.applicationContext

    suspend fun durationMs(url: String): AppResult<Long> {
        val retriever = MetadataRetriever.Builder(context, MediaItem.fromUri(url))
            .setMediaSourceFactory(cache.mediaSourceFactory())
            .build()
        return try {
            val durationUs = withTimeoutOrNull(TIMEOUT_MS) { retriever.retrieveDurationUs().await() }
            when (durationUs) {
                null -> AppResult.Failure(AppError.Network("timeout"))
                C.TIME_UNSET -> AppResult.Failure(AppError.NotFound(url))
                else -> AppResult.Success(durationUs / 1_000)
            }
        } catch (refused: HttpDataSource.InvalidResponseCodeException) {
            // A 4xx is the URL: no file there, or not ours to read — the BA fixes source_vid. A 5xx is the server's
            // trouble and may pass, so it reads as a connection problem, not as a broken game.
            val code = refused.responseCode
            AppResult.Failure(if (code in 400..499) AppError.NotFound(url) else AppError.Network("HTTP $code"))
        } catch (unreachable: HttpDataSource.HttpDataSourceException) {
            AppResult.Failure(AppError.Network(unreachable::class.simpleName))
        } catch (unreadable: IOException) {
            AppResult.Failure(AppError.NotFound(url)) // fetched, but not a video Media3 can read
        } finally {
            retriever.close()
        }
    }

    private companion object {
        /**
         * A backstop, not the bound a user normally meets. A dead connection fails sooner by itself: Media3's HTTP
         * source gives up after 8 s without connecting or without a byte. A slow one keeps going, as the player
         * would: on an SM-A165F whose Wi-Fi dipped (2026-10-02) the 151 kB header took 14 s, and the player itself
         * then took 22 s more — a 15 s limit here turned a slow start into an error screen.
         */
        const val TIMEOUT_MS = 60_000L
    }
}
