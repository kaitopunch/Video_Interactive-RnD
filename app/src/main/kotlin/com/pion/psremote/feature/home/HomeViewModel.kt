package com.pion.psremote.feature.home

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppLogger
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.core.mvi.MviViewModel
import com.pion.psremote.domain.repository.DemoRepository
import kotlinx.coroutines.Job

/**
 * The game picker. Lists the bundled demos and opens the one the user taps; what a demo contains is the
 * demo screen's business, so a broken one is listed like any other (confirm.md Q12).
 */
class HomeViewModel(
    private val repository: DemoRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState(), log) {

    private var loadJob: Job? = null

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ScreenStarted -> onScreenStarted()
            is HomeIntent.DemoClicked -> sendEffect(HomeEffect.OpenDemo(intent.demoId))
        }
    }

    /**
     * Read once: the bundled list cannot change while the app runs. A failed read is retried on the next
     * return to the screen, which is the only retry the user has — there is nothing else to press.
     */
    private fun onScreenStarted() {
        if (loadJob == null || currentState.phase is HomePhase.Failed) load()
    }

    private fun load() {
        setState { copy(phase = HomePhase.Loading) }
        loadJob = launchSafely(onError = ::fail) {
            when (val result = repository.list()) {
                is AppResult.Failure -> fail(result.error)
                is AppResult.Success -> setState {
                    copy(phase = if (result.value.isEmpty()) HomePhase.Empty else HomePhase.Ready(result.value))
                }
            }
        }
    }

    private fun fail(error: AppError) = setState { copy(phase = HomePhase.Failed(error)) }
}
