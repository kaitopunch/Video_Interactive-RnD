package com.pion.psremote.data.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoLoad
import com.pion.psremote.domain.repository.DemoRepository
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

/**
 * The live catalogue, as the phone reads it: the real request with this build's key, and each video's length
 * read over the network through the real cache. This is the check every script used to get at build time
 * (confirm.md H6): run it after a CMS edit. Needs a connection and `CATALOGUE_API_KEY` in `local.properties`.
 *
 * The repository is the app's own, from the graph `PsRemoteApp` started: the cache behind it may exist once per
 * process (`VideoCache`). `runBlocking`, not `runTest`: under virtual time the reader's timeout fires at once.
 */
@RunWith(AndroidJUnit4::class)
class RemoteDemoRepositoryDeviceTest {

    private val repository: DemoRepository = KoinPlatform.getKoin().get()

    @Test
    fun everyCatalogueGameIsListedAndPlayableOnThisPhone() = runBlocking {
        val demos = (repository.list() as AppResult.Success).value
        assertTrue("the catalogue lists no game", demos.isNotEmpty())

        demos.forEach { demo ->
            val source = repository.load(demo.id).orFail(demo.title)
            assertTrue("${demo.title}: no duration read from the video", source.videoDurationMs > 0)
            val load = LoadDemoUseCase(repository)(demo.id).orFail(demo.title)
            assertTrue("${demo.title}: script rejected against the real video: $load", load is DemoLoad.Ready)
        }
    }

    /** Names the game and the error, so a failure says which CMS entry to fix. */
    private fun <T> AppResult<T>.orFail(game: String): T = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw AssertionError("$game: $error")
    }

    @Test
    fun aGameNotInTheCatalogueIsNotFound() = runBlocking {
        assertEquals(
            AppResult.Failure(AppError.NotFound("not-in-the-catalogue")),
            repository.load("not-in-the-catalogue"),
        )
    }
}
