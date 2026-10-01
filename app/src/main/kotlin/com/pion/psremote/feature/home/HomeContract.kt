package com.pion.psremote.feature.home

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.mvi.UiEffect
import com.pion.psremote.core.mvi.UiIntent
import com.pion.psremote.core.mvi.UiState
import com.pion.psremote.domain.model.DemoSummary

data class HomeState(
    val phase: HomePhase = HomePhase.Loading,
) : UiState

sealed interface HomePhase {

    /** Reading the folder list. */
    data object Loading : HomePhase

    /** At least one demo. */
    data class Ready(val demos: List<DemoSummary>) : HomePhase

    /** `assets/demos/` holds no folder: the APK was built without a demo. */
    data object Empty : HomePhase

    data class Failed(val error: AppError) : HomePhase
}

sealed interface HomeIntent : UiIntent {
    data object ScreenStarted : HomeIntent
    data class DemoClicked(val demoId: String) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class OpenDemo(val demoId: String) : HomeEffect
}
