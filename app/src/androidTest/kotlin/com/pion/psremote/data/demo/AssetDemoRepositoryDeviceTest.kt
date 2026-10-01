package com.pion.psremote.data.demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoLoad
import com.pion.psremote.domain.usecase.LoadDemoUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundled demos as the phone reads them: `AssetManager` for the folders and the script, and the platform's
 * `MediaMetadataRetriever` for the video's length — which the JVM cannot run, so `DemoScriptValidatorTest`
 * checks every script against a hand-written duration. Here the script is checked against the real one.
 */
@RunWith(AndroidJUnit4::class)
class AssetDemoRepositoryDeviceTest {

    private val repository = AssetDemoRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun everyBundledDemoIsListedAndPlayableOnThisPhone() = runTest {
        val demos = (repository.list() as AppResult.Success).value
        assertTrue("no demo bundled", demos.isNotEmpty())
        assertEquals("not sorted by id", demos.sortedBy { it.id }, demos)

        demos.forEach { demo ->
            val source = (repository.load(demo.id) as AppResult.Success).value
            assertTrue("${demo.id}: no duration read from the video", source.videoDurationMs > 0)
            val load = (LoadDemoUseCase(repository)(demo.id) as AppResult.Success).value
            assertTrue("${demo.id}: script rejected against the real video: $load", load is DemoLoad.Ready)
        }
    }

    @Test
    fun aDemoThatIsNotBundledNamesTheMissingFile() = runTest {
        assertEquals(
            AppResult.Failure(AppError.NotFound("demos/not-bundled/script.json")),
            repository.load("not-bundled"),
        )
    }
}
