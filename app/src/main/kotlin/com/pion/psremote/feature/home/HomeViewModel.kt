package com.pion.psremote.feature.home

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.common.AppLogger
import com.pion.psremote.core.common.AppResult
import com.pion.psremote.core.mvi.MviViewModel
import com.pion.psremote.domain.repository.DemoRepository
import kotlinx.coroutines.Job

/**
 * The game picker. Lists the catalogue's games and opens the one the user taps; what a game contains is the
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
            HomeIntent.RetryClicked -> onRetryClicked()
            is HomeIntent.DemoClicked -> sendEffect(HomeEffect.OpenDemo(intent.demoId))
        }
    }

    /**
     * Fetched once per Home, not on every return from a demo: a spinner after each game would be the price of
     * seeing a CMS edit a few minutes sooner. A failed fetch is retried on the next return too.
     */
    private fun onScreenStarted() {
        if (loadJob == null || currentState.phase is HomePhase.Failed) load()
    }

    /**
     * Shown under the error and under an empty list (confirm.md H3): an empty catalogue may be a BA mid-edit. Not
     * while a fetch is running or the list is shown — a second tap must not start a second request.
     */
    private fun onRetryClicked() {
        if (currentState.phase is HomePhase.Failed || currentState.phase is HomePhase.Empty) load()
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
