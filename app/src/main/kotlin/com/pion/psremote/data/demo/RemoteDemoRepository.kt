package com.pion.psremote.data.demo

import androidx.tracing.traceAsync
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.core.common.map
import com.pion.psremote.data.catalogue.CatalogueApi
import com.pion.psremote.data.catalogue.CatalogueItem
import com.pion.psremote.data.catalogue.toSummaries
import com.pion.psremote.data.playback.VideoDurations
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.domain.repository.DemoRepository

/**
 * The games in the store's catalogue (confirm.md H1): the script is the entry's `custom_fields.json`, the video
 * is streamed from its `custom_fields.source_vid`. Adding or fixing a game is a CMS edit, not a build.
 *
 * [load] opens the entry Home listed, from the list Home read, so the demo is the one the user tapped even if the
 * CMS changed since. After process death nothing is held, and the list is fetched again.
 */
class RemoteDemoRepository(
    private val api: CatalogueApi,
    private val durations: VideoDurations,
) : DemoRepository {

    /** The last list fetched. Replaced whole, never mutated, so `@Volatile` is all a reader on another thread needs. */
    @Volatile
    private var items: List<CatalogueItem> = emptyList()

    override suspend fun list(): AppResult<List<DemoSummary>> = fetch().map { it.toSummaries() }

    /** Traced as `PsRemote.readDemo`, which `OpenDemoBenchmark` reads back (LLM.md §10). Async: it suspends across threads. */
    override suspend fun load(demoId: String): AppResult<DemoSource> = traceAsync(TRACE_READ_DEMO, TRACE_COOKIE) {
        read(demoId)
    }

    private suspend fun read(demoId: String): AppResult<DemoSource> {
        val item = items.byId(demoId) ?: when (val fetched = fetch()) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value.byId(demoId)
        } ?: return AppResult.Failure(AppError.NotFound(demoId))

        // A field the CMS form left blank is as missing as one it left out: a blank URL would reach Media3 and come
        // back as "not found: " with nothing named, and the BA would not know which field to fill.
        val script = item.customFields?.json?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.NotFound(SCRIPT_FIELD))
        val video = item.customFields.sourceVid?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.NotFound(VIDEO_FIELD))
        return durations.durationMs(video).map { durationMs -> DemoSource(video, durationMs, script) }
    }

    private suspend fun fetch(): AppResult<List<CatalogueItem>> =
        api.items().also { if (it is AppResult.Success) items = it.value }

    private fun List<CatalogueItem>.byId(id: String) = firstOrNull { it.id == id }

    private companion object {
        /** The names the BA sees in the CMS, so the error screen points at the field to fill. */
        const val SCRIPT_FIELD = "custom_fields.json"
        const val VIDEO_FIELD = "custom_fields.source_vid"

        const val TRACE_READ_DEMO = "PsRemote.readDemo"

        /** One demo loads at a time: a fixed cookie is enough. */
        const val TRACE_COOKIE = 2
    }
}
