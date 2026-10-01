package com.pion.psremote.feature.demo

import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.mvi.UiEffect
import com.pion.psremote.core.mvi.UiIntent
import com.pion.psremote.core.mvi.UiState
import com.pion.psremote.domain.input.StepProgress
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier

data class DemoState(
    val phase: DemoPhase = DemoPhase.Loading,
    /** The step whose tutorial is on screen. Null between steps. */
    val tutorial: ActiveTutorial? = null,
    /**
     * Non-null from the moment the app goes to the background until the demo continues: the seconds left
     * on the "get ready" countdown shown on return (D6). Set on the way *out*, so the first frame after
     * returning already shows the dialog instead of one frame of live video.
     */
    val resumeCountdown: Int? = null,
    /** This run's score. Reset when a run starts, so Replay starts from 0 (scoring rules R7). */
    val score: Score = Score(),
) : UiState {

    val isControllerVisible: Boolean
        get() = phase == DemoPhase.Playing || phase == DemoPhase.Finished

    /** While playing only: the finished panel shows the final score itself, and an error has none. */
    val isScoreVisible: Boolean
        get() = phase == DemoPhase.Playing

    /**
     * The tier the last step earned, shown beside the score until the next tutorial appears (scoring rules §5).
     * Derived, so it needs no timer to hide it and cannot outlive a Replay.
     */
    val lastAward: ScoreTier?
        get() = if (tutorial == null) score.awards.lastOrNull() else null

    /** The finished and error panels carry their own exit; a second one beside them would be noise. */
    val isExitButtonVisible: Boolean
        get() = phase == DemoPhase.Loading || phase == DemoPhase.Playing

    /**
     * Buttons that respond to touch. During a tutorial only the active highlighted ones do — the dim
     * layer blocks the rest (confirm.md D2, Q9). Outside one, every button shows its press effect and
     * does nothing else (D3). During the countdown, none.
     */
    val enabledButtons: Set<ControllerButton>
        get() = when {
            phase != DemoPhase.Playing || resumeCountdown != null -> emptySet()
            tutorial != null -> tutorial.progress.activeButtons
            else -> ALL_BUTTONS
        }

    /**
     * Every button the tutorial points at, cut out of the dim layer and marked. This is the whole
     * instruction: there is no text card (removed 2026-10-01). Only the [ButtonHighlight.isActive] ones
     * are also in [enabledButtons].
     */
    val highlights: Map<ControllerButton, ButtonHighlight>
        get() {
            val progress = tutorial?.progress ?: return emptyMap()
            val active = progress.activeButtons
            return progress.pendingButtons.associateWith { button ->
                ButtonHighlight(isActive = button in active, badge = progress.badgeFor(button))
            }
        }

    private companion object {
        val ALL_BUTTONS: Set<ControllerButton> = ControllerButton.entries.toSet()

        /**
         * A number only where order matters and there is more than one press: a single button needs no "1".
         * SIMULTANEOUS gets nothing — all its buttons light up at once, which is the whole instruction.
         */
        fun StepProgress.badgeFor(button: ControllerButton): ButtonBadge? = when (mode) {
            InputMode.SEQUENCE -> if (targets.size > 1) nextPositionOf(button)?.let(ButtonBadge::Position) else null
            InputMode.ANY_ORDER -> remainingPresses(button).takeIf { it > 1 }?.let(ButtonBadge::Presses)
            InputMode.SIMULTANEOUS -> null
        }
    }
}

/** How one lit button is drawn while its tutorial is up. */
data class ButtonHighlight(
    /** Pressable now: bright and pulsing. False for a SEQUENCE button whose turn has not come: faint, blocked. */
    val isActive: Boolean,
    val badge: ButtonBadge?,
)

sealed interface ButtonBadge {
    /** Its place in a SEQUENCE: 1, 2, 3. */
    data class Position(val value: Int) : ButtonBadge

    /** Presses an ANY_ORDER button still owes, shown as ×2. */
    data class Presses(val count: Int) : ButtonBadge
}

sealed interface DemoPhase {

    /** Reading the script and preparing the video. */
    data object Loading : DemoPhase

    /** The video is running through its script — including while stopped at a step's stop point. */
    data object Playing : DemoPhase

    /** The video reached its end. Replay or exit (confirm.md Q13). */
    data object Finished : DemoPhase

    /** The script breaks at least one rule; nothing is played (confirm.md Q12). */
    data class InvalidScript(val violations: List<ScriptViolation>) : DemoPhase

    data class Failed(val error: AppError) : DemoPhase
}

data class ActiveTutorial(
    val step: TutorialStep,
    val progress: StepProgress,
    /** True once the video has stopped at the step's stop point and is waiting for the input. */
    val isWaiting: Boolean,
)

sealed interface DemoIntent : UiIntent {
    data object ScreenStarted : DemoIntent
    data object ScreenStopped : DemoIntent
    data class ButtonPressed(val button: ControllerButton) : DemoIntent
    data class ButtonReleased(val button: ControllerButton) : DemoIntent
    data object ReplayClicked : DemoIntent
    data object ExitClicked : DemoIntent
}

sealed interface DemoEffect : UiEffect {
    data object Exit : DemoEffect
}
