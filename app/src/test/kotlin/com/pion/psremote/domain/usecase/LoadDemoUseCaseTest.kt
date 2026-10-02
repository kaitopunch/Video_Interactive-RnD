package com.pion.psremote.domain.usecase

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.domain.model.DemoLoad
import com.pion.psremote.domain.model.DemoSource
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.feature.demo.FakeDemoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class LoadDemoUseCaseTest {

    private val repository = FakeDemoRepository()

    @Test
    fun `steps come back in step_sequence order whatever the order of the file`() = runTest {
        repository.result = source(
            """[
                {"step_sequence":2,"triggerTimeMs":60000,"targetButtonIds":["CROSS"],"playbackSpeed":0,"slowDurationMs":0},
                {"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["R1"],"playbackSpeed":0.25,"slowDurationMs":3000}
            ]""",
        )

        val load = (LoadDemoUseCase(repository, StandardTestDispatcher(testScheduler))("sample") as AppResult.Success).value

        assertEquals(listOf(1, 2), (load as DemoLoad.Ready).steps.map { it.sequence })
        assertEquals(VIDEO_URI, load.videoUri)
    }

    @Test
    fun `a broken script returns every violation and nothing to play`() = runTest {
        repository.result = source(
            """[
                {"step_sequence":0,"triggerTimeMs":-1,"targetButtonIds":[],"playbackSpeed":2,"slowDurationMs":0},
                {"step_sequence":0,"triggerTimeMs":1000,"targetButtonIds":["cross"],"playbackSpeed":0.5,"slowDurationMs":10}
            ]""",
        )

        val load = (LoadDemoUseCase(repository, StandardTestDispatcher(testScheduler))("sample") as AppResult.Success).value

        assertEquals(
            listOf(
                ScriptViolation.UnknownButton(1, "cross"),
                ScriptViolation.SequenceBelowOne(0),
                ScriptViolation.NegativeTriggerTime(0, -1),
                ScriptViolation.EmptyTargets(0),
                ScriptViolation.SpeedOutOfRange(0, 2.0),
            ),
            (load as DemoLoad.Invalid).violations,
        )
    }

    /** End to end through the parser: a NaN that reached `stopPositionMs` would throw out of here, not be reported. */
    @Test
    fun `a NaN speed comes back as a violation, never as an exception`() = runTest {
        repository.result = source(
            """[{"step_sequence":1,"triggerTimeMs":34000,"targetButtonIds":["CROSS"],"playbackSpeed":NaN,"slowDurationMs":3000}]""",
        )

        val load = (LoadDemoUseCase(repository, StandardTestDispatcher(testScheduler))("sample") as AppResult.Success).value

        assertEquals(listOf(ScriptViolation.SpeedOutOfRange(1, Double.NaN)), (load as DemoLoad.Invalid).violations)
    }

    @Test
    fun `a script that is not JSON at all comes back as a violation, never as an exception`() = runTest {
        repository.result = source("<html>502 Bad Gateway</html>")

        val load = (LoadDemoUseCase(repository, StandardTestDispatcher(testScheduler))("sample") as AppResult.Success).value

        assertTrue((load as DemoLoad.Invalid).violations.single() is ScriptViolation.NotAJsonArray)
    }

    @Test
    fun `a repository failure passes through untouched`() = runTest {
        val failure = AppResult.Failure(AppError.NotFound("demos/sample/video.mp4"))
        repository.result = failure

        assertEquals(failure, LoadDemoUseCase(repository, StandardTestDispatcher(testScheduler))("sample"))
    }

    @Test
    fun `the script is checked on the dispatcher it was given, not the caller's`() = runTest {
        repository.result = source("""[]""")
        val dispatcher = CountingDispatcher(StandardTestDispatcher(testScheduler))

        LoadDemoUseCase(repository, dispatcher)("sample")

        assertTrue("parse and validate never left the caller's thread", dispatcher.dispatches > 0)
    }

    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    private fun source(script: String) = AppResult.Success(DemoSource(VIDEO_URI, 70_000, script))

    private companion object {
        const val VIDEO_URI = "asset:///demos/sample/video.mp4"
    }
}
