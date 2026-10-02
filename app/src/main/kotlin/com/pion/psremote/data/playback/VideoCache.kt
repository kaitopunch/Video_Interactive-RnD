package com.pion.psremote.data.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

/**
 * The videos streamed so far, on disk (confirm.md H2): a game opened twice is downloaded once, and the duration
 * read before playing ([VideoDurationReader]) fetches the header the player then reads from here.
 *
 * One per process — a Koin `single` (LLM.md §6). A second [SimpleCache] on the same folder throws
 * `IllegalStateException` at construction, so a player that built its own would crash the second demo opened.
 *
 * Keyed by URL. The CMS stores every upload under a new, timestamped path, so a replaced video is a new key; a
 * file overwritten at the same URL would keep playing the old copy until evicted.
 */
@OptIn(UnstableApi::class)
class VideoCache(context: Context) {

    private val cache = SimpleCache(
        File(context.cacheDir, DIRECTORY),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    )

    /**
     * Reads through the cache; on a miss, `DefaultDataSource` fetches the URL over HTTP — or opens a `file://` or
     * `asset://` URI, which the device tests play. A cache error falls back to the network instead of failing.
     */
    private val dataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun mediaSourceFactory(): MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory)

    private companion object {
        const val DIRECTORY = "videos"

        /** About ten games at Spider Man's 22.6 MB. In `cacheDir`, which the system may clear when storage runs low. */
        const val MAX_BYTES = 256L * 1024 * 1024
    }
}
