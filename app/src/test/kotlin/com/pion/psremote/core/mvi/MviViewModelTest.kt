package com.pion.psremote.core.mvi

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The crash floor and the effect channel every screen stands on (MVI §1). The screen suites cover them only through
 * the failures their own collaborators can raise; this one drives the base class directly, through a probe whose
 * intents carry the work to run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val log = RecordingLogger()
    private lateinit var vm: ProbeViewModel
    private val state: ProbeState get() = vm.state.value

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        vm = ProbeViewModel(log)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `a throwing coroutine becomes an Unexpected error carrying its message, not a crash`() = runTest(dispatcher) {
        val boom = IllegalStateException("catalogue entry had no id")

        vm.onIntent(ProbeIntent.Run { throw boom })

        assertEquals(listOf(AppError.Unexpected("catalogue entry had no id")), state.errors)
        assertSame(boom, log.errors.single())
    }

    @Test
    fun `even an Error such as an unimplemented branch is contained`() = runTest(dispatcher) {
        vm.onIntent(ProbeIntent.Run { TODO("a branch nobody wrote") })

        assertTrue(state.errors.single() is AppError.Unexpected)
    }

    /** MVI §10: a swallowed cancellation turns every ordinary "the user left" into an error banner. */
    @Test
    fun `a cancellation is not reported as a failure`() = runTest(dispatcher) {
        vm.onIntent(ProbeIntent.Run { throw CancellationException("the screen was left") })

        assertTrue(state.errors.isEmpty())
        assertTrue(log.errors.isEmpty())
    }

    @Test
    fun `clearing the ViewModel stops its coroutines without reporting them`() = runTest(dispatcher) {
        vm.onIntent(ProbeIntent.Run { awaitCancellation() })

        vm.viewModelScope.cancel()

        assertTrue(state.errors.isEmpty())
        assertEquals(0, state.completed)
    }

    @Test
    fun `one failing coroutine does not cancel the others`() = runTest(dispatcher) {
        vm.onIntent(ProbeIntent.Run { delay(1_000) })
        vm.onIntent(ProbeIntent.Run { throw IOException("socket closed") })

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, state.errors.size)
        assertEquals(1, state.completed)
    }

    @Test
    fun `a flow that breaks keeps what it delivered and reports the break`() = runTest(dispatcher) {
        vm.onIntent(ProbeIntent.Collect(flow { emit(1); emit(2); throw IOException("decoder released") }))

        assertEquals(listOf(1, 2), state.collected)
        assertEquals(listOf(AppError.Unexpected("decoder released")), state.errors)
    }

    @Test
    fun `effects raised with no collector wait, then arrive in order, each once`() = runTest(dispatcher) {
        (1..3).forEach { vm.onIntent(ProbeIntent.Send(it)) }

        vm.effects.test {
            assertEquals(listOf(1, 2, 3), List(3) { awaitItem().id })
            expectNoEvents()
        }
        // Not replayed to the next collector, as a SharedFlow with replay would on a configuration change.
        vm.effects.test { expectNoEvents() }
    }

    /** `Channel(BUFFERED)` holds 64; past that `send` suspends, and a suspended send must not lose its effect. */
    @Test
    fun `a burst bigger than the buffer still arrives whole and in order`() = runTest(dispatcher) {
        (1..100).forEach { vm.onIntent(ProbeIntent.Send(it)) }

        vm.effects.test {
            assertEquals((1..100).toList(), List(100) { awaitItem().id })
            expectNoEvents()
        }
    }

    private data class ProbeState(
        val errors: List<AppError> = emptyList(),
        val completed: Int = 0,
        val collected: List<Int> = emptyList(),
    ) : UiState

    private sealed interface ProbeIntent : UiIntent {
        class Run(val block: suspend () -> Unit) : ProbeIntent
        class Collect(val flow: Flow<Int>) : ProbeIntent
        class Send(val id: Int) : ProbeIntent
    }

    private data class ProbeEffect(val id: Int) : UiEffect

    private class ProbeViewModel(log: AppLogger) : MviViewModel<ProbeState, ProbeIntent, ProbeEffect>(ProbeState(), log) {
        override fun onIntent(intent: ProbeIntent) {
            when (intent) {
                is ProbeIntent.Run -> launchSafely(onError = ::record) {
                    intent.block()
                    setState { copy(completed = completed + 1) }
                }
                is ProbeIntent.Collect -> intent.flow.collectSafely(onError = ::record) {
                    setState { copy(collected = collected + it) }
                }
                is ProbeIntent.Send -> sendEffect(ProbeEffect(intent.id))
            }
        }

        private fun record(error: AppError) = setState { copy(errors = errors + error) }
    }

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<Throwable?>()

        override fun d(message: () -> String) = Unit

        override fun e(throwable: Throwable?, message: () -> String) {
            message() // built, as the real logger does, so a message that throws fails here and not in the field
            errors += throwable
        }
    }
}
