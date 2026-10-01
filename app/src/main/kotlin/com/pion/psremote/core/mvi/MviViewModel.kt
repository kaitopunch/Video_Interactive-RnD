package com.pion.psremote.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything a screen renders, in one immutable snapshot. */
interface UiState

/** Something the user did. */
interface UiIntent

/** A one-shot instruction to the UI: navigate, show a snackbar, leave the screen. */
interface UiEffect

/**
 * The base class for **every** screen ViewModel (`.claude/CLAUDE.md`, non-negotiable rule 1).
 *
 * [onIntent] is the only public method a screen may call — no escape hatches (rule 2).
 * State, Intent and Effect types live in `XContract.kt`, never inline in the ViewModel file (rule 3).
 *
 * [log] defaults to [AppLogger.NoOp] so a subclass constructs on a bare JVM with no test double.
 * That default is what makes [launchSafely]'s catch branch testable at all (MVI doc §1).
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    protected val log: AppLogger = AppLogger.NoOp,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * `Channel(BUFFERED)`, not `SharedFlow`. `replay = 0` drops an effect raised while the screen is
     * stopped; `replay = 1` re-fires it on every config change. A channel buffers and delivers
     * exactly once (MVI doc §1).
     */
    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    abstract fun onIntent(intent: I)

    protected fun setState(reducer: S.() -> S) = _state.update { it.reducer() }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /**
     * Every coroutine in a ViewModel goes through here.
     *
     * `viewModelScope` carries a `SupervisorJob` but no `CoroutineExceptionHandler`, so an exception
     * escaping a plain `viewModelScope.launch` reaches the platform default handler — on Android that
     * is the process dying. [onError] must lower every flag the call raised (MVI doc §1).
     */
    protected fun launchSafely(
        onError: (AppError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation // NEVER swallow this: it is how a coroutine is told to stop.
        } catch (throwable: Throwable) {
            log.e(throwable) { "Unhandled failure in ${this@MviViewModel::class.simpleName}" }
            onError(AppError.Unexpected(throwable.message))
        }
    }

    /**
     * Observing a Flow goes through here, never `.onEach { }.launchIn(viewModelScope)` — `launchIn`
     * is the same missing handler spelled differently (MVI doc §1).
     */
    protected fun <T> Flow<T>.collectSafely(
        onError: (AppError) -> Unit = {},
        onEach: suspend (T) -> Unit,
    ): Job = launchSafely(onError) { collect { onEach(it) } }
}
