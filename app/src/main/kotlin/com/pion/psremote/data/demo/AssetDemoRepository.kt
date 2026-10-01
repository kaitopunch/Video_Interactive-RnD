package com.pion.psremote.data.demo

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.tracing.trace
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.domain.repository.DemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Demos bundled in the APK under `assets/demos/<demoId>/`: `video.mp4` + `script.json`. The home list is
 * the folders found there, so adding a game is adding a folder.
 *
 * Bundled rather than streamed (confirm.md Q3), so a demo plays with no network and the script can
 * never point at a video it was not written for. Switching to a URL is this class and nothing else.
 *
 * The duration is read here, before the player exists, so the whole script — including "every stop
 * point is before the end of the video" — is checked before a single frame plays. `.mp4` is stored
 * uncompressed by AAPT, which is what lets [MediaMetadataRetriever] read it through a file descriptor.
 */
class AssetDemoRepository(context: Context) : DemoRepository {

    private val assets = context.applicationContext.assets

    /**
     * Every non-empty folder under `demos/`. A folder missing one of its two files is still listed: opening
     * it names the missing file, which tells the BA more than a game that silently is not there.
     * `AssetManager.list` returns an empty array for a plain file, so a stray file is not a demo.
     */
    override suspend fun list(): AppResult<List<DemoSummary>> = withContext(Dispatchers.IO) {
        try {
            val ids = assets.list(DEMOS_DIR).orEmpty()
                .filter { id -> assets.list("$DEMOS_DIR/$id").orEmpty().isNotEmpty() }
                .sorted()
            AppResult.Success(ids.map(DemoSummary::fromId))
        } catch (unreadable: IOException) {
            AppResult.Failure(AppError.NotFound(DEMOS_DIR))
        }
    }

    /** Traced as `PsRemote.readDemo`, which `OpenDemoBenchmark` reads back (LLM.md §10). */
    override suspend fun load(demoId: String): AppResult<DemoSource> = withContext(Dispatchers.IO) {
        trace(TRACE_READ_DEMO) { read("$DEMOS_DIR/$demoId") }
    }

    private fun read(dir: String): AppResult<DemoSource> {
        val script = readScript("$dir/$SCRIPT_FILE")
            ?: return AppResult.Failure(AppError.NotFound("$dir/$SCRIPT_FILE"))
        val durationMs = readDurationMs("$dir/$VIDEO_FILE")
            ?: return AppResult.Failure(AppError.NotFound("$dir/$VIDEO_FILE"))
        return AppResult.Success(DemoSource("asset:///$dir/$VIDEO_FILE", durationMs, script))
    }

    private fun readScript(path: String): String? = try {
        assets.open(path).bufferedReader().use { it.readText() }
    } catch (unreadable: IOException) {
        null
    }

    private fun readDurationMs(path: String): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            assets.openFd(path).use { fd -> retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (unreadable: IOException) {
            null
        } catch (corrupt: RuntimeException) { // setDataSource throws IllegalArgumentException/RuntimeException on a bad file
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val DEMOS_DIR = "demos"
        const val SCRIPT_FILE = "script.json"
        const val VIDEO_FILE = "video.mp4"
        const val TRACE_READ_DEMO = "PsRemote.readDemo"
    }
}
