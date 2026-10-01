package com.pion.psremote.feature.demo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsRemoteTheme
import com.pion.psremote.domain.input.StepProgress
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.DPAD_LEFT
import com.pion.psremote.domain.model.ControllerButton.DPAD_UP
import com.pion.psremote.domain.model.ControllerButton.L1
import com.pion.psremote.domain.model.ControllerButton.L2
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.model.ControllerButton.R2
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.ScriptViolation
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier
import com.pion.psremote.testing.boundsOf
import com.pion.psremote.testing.centerOf
import com.pion.psremote.testing.hasClickLabel
import com.pion.psremote.testing.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The demo screen's layers, composed for real: what the dim layer lets through, what the modals block, and how
 * the controller survives a right-to-left locale. Runs on the JVM and on a phone (LLM.md §9).
 */
@RunWith(AndroidJUnit4::class)
class DemoScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<DemoIntent>()
    private var state by mutableStateOf(DemoState())

    private fun show(initial: DemoState, direction: LayoutDirection = LayoutDirection.Ltr) {
        state = initial
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                PsRemoteTheme { DemoScreen(state, onIntent = { intents += it }, video = {}) }
            }
        }
    }

    /** F2: under RTL the buttons mirrored and the spotlight holes did not, so the lit spot was a disabled button. */
    @Test
    fun aRightToLeftLocaleLeavesEveryButtonWhereADualSenseHasIt() {
        show(tutorial(InputMode.SEQUENCE, CROSS), LayoutDirection.Rtl)
        val middle = compose.onRoot().fetchSemanticsNode().size.width / 2f

        assertTrue("CROSS mirrored to the left", compose.centerOf(CROSS.name).x > middle)
        assertTrue("DPAD_LEFT mirrored to the right", compose.centerOf(DPAD_LEFT.name).x < middle)
        assertTrue("L2 is not left of R2", compose.centerOf(L2.name).x < compose.centerOf(R2.name).x)

        tap(CROSS)
        assertEquals("the lit button is the live one", DemoIntent.ButtonPressed(CROSS), intents.first())
    }

    @Test
    fun aTapInALitHoleReachesTheButtonUnderTheDimLayer() {
        show(tutorial(InputMode.SEQUENCE, CROSS))

        tap(CROSS)

        assertEquals(listOf(DemoIntent.ButtonPressed(CROSS), DemoIntent.ButtonReleased(CROSS)), intents)
    }

    @Test
    fun aDimmedButtonTakesNoPressWhileATutorialIsUp() {
        show(tutorial(InputMode.SEQUENCE, CROSS))

        tap(L1)

        assertEquals(emptyList<DemoIntent>(), intents)
    }

    /** A SEQUENCE lights the whole combo, but only the next button is live (LLM.md §12). */
    @Test
    fun aSequenceLightsEveryButtonStillToComeAndNumbersThemWithOnlyTheFirstLive() {
        show(tutorial(InputMode.SEQUENCE, DPAD_UP, CROSS, R1))

        listOf("1" to DPAD_UP, "2" to CROSS, "3" to R1).forEach { (badge, button) ->
            val badgeCenter = compose.onNodeWithText(badge).fetchSemanticsNode().boundsInRoot.center
            assertTrue("badge $badge is not on $button", compose.boundsOf(button.name).inflate(1f).contains(badgeCenter))
        }
        tap(CROSS)
        tap(DPAD_UP)
        assertEquals(listOf(DemoIntent.ButtonPressed(DPAD_UP), DemoIntent.ButtonReleased(DPAD_UP)), intents)
    }

    @Test
    fun anAnyOrderButtonOwedTwiceSaysSo() {
        show(tutorial(InputMode.ANY_ORDER, CROSS, CROSS, R1))

        compose.onNodeWithText(string(R.string.badge_presses, 2)).assertIsDisplayed()
    }

    /** D2: a user stuck on a step must always be able to leave. */
    @Test
    fun exitStaysLiveAboveTheDimLayer() {
        show(tutorial(InputMode.SEQUENCE, CROSS))

        compose.onNode(hasClickLabel(string(R.string.action_exit))).performClick()

        assertEquals(listOf(DemoIntent.ExitClicked), intents)
    }

    /** D6: nothing under the countdown is live, so a finger resting on a button cannot count when it ends. */
    @Test
    fun theResumeCountdownSwallowsEveryTouch() {
        show(tutorial(InputMode.SEQUENCE, CROSS).copy(resumeCountdown = 3))

        compose.onNodeWithText("3").assertIsDisplayed()
        tap(CROSS)
        compose.onNode(hasClickLabel(string(R.string.action_exit))).performClick()

        assertEquals(emptyList<DemoIntent>(), intents)
    }

    /** Scoring rules §5: the tier of the last step stays beside the points until the next tutorial takes over. */
    @Test
    fun theScoreShowsTheLastTierUntilTheNextTutorialAppears() {
        show(DemoState(phase = DemoPhase.Playing, score = Score(listOf(ScoreTier.PERFECT), stepCount = 2)))

        compose.onNodeWithText("100").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.score_tier_perfect)).assertIsDisplayed()

        state = tutorial(InputMode.SEQUENCE, CROSS).copy(score = state.score)

        compose.onNodeWithText("100").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.score_label)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.score_tier_perfect)).assertDoesNotExist()
    }

    /**
     * The score takes no touch: no click action, and a tap on it reaches the screen's parent unconsumed. No button lies
     * under it, so an intent count could not tell a score that swallows touches from one that does not.
     */
    @Test
    fun theScoreTakesNoTouch() {
        val consumed = mutableListOf<Boolean>()
        state = tutorial(InputMode.SEQUENCE, CROSS)
        compose.setContent {
            PsRemoteTheme {
                // Main pass, so every node under the tap — the score included — has had each event first. Down and
                // up both: a tap detector consumes at least the up.
                Box(
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                consumed += event.changes.any { it.isConsumed }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                ) {
                    DemoScreen(state, onIntent = { intents += it }, video = {})
                }
            }
        }
        val score = compose.onNodeWithText(string(R.string.score_label))
        score.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        compose.onRoot().performTouchInput { click(score.fetchSemanticsNode().boundsInRoot.center) }

        assertEquals("the tap was consumed under the score", listOf(false, false), consumed)
        assertEquals(emptyList<DemoIntent>(), intents)
    }

    @Test
    fun theFinishedPanelShowsTheScoreOutOfTheMaximumInsteadOfTheCornerDisplay() {
        show(DemoState(phase = DemoPhase.Finished, score = Score(listOf(ScoreTier.PERFECT, ScoreTier.GOOD), stepCount = 3)))

        compose.onNodeWithText(string(R.string.finished_score, 150, 300)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.finished_tiers, 1, 1)).assertIsDisplayed()
        // The corner display would read "GOOD" over "150" here, so all three are checked, not just its "SCORE".
        compose.onNodeWithText("150").assertDoesNotExist()
        compose.onNodeWithText(string(R.string.score_tier_good)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.score_label)).assertDoesNotExist()
    }

    @Test
    fun theFinishedPanelOffersReplayAndExit() {
        show(DemoState(phase = DemoPhase.Finished))

        compose.onNodeWithText(string(R.string.action_replay)).performClick()
        compose.onNodeWithText(string(R.string.action_exit)).performClick()

        assertEquals(listOf(DemoIntent.ReplayClicked, DemoIntent.ExitClicked), intents)
    }

    /** Q12: every violation at once — on a short landscape screen that is a scrolling list, and Exit stays put. */
    @Test
    fun aScriptWithManyViolationsScrollsAndItsExitStaysReachable() {
        val violations = List(VIOLATIONS) { ScriptViolation.DuplicateSequence(it + 1) }
        show(DemoState(phase = DemoPhase.InvalidScript(violations)))

        compose.onNodeWithText(string(R.string.action_exit)).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(VIOLATIONS - 1)
        compose.onNodeWithText(string(R.string.violation_duplicate_sequence, VIOLATIONS), substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText(string(R.string.action_exit)).performClick()

        assertEquals(listOf(DemoIntent.ExitClicked), intents)
    }

    private fun tap(button: ControllerButton) {
        val center = compose.centerOf(button.name)
        compose.onRoot().performTouchInput { click(center) }
    }

    private fun tutorial(mode: InputMode, vararg targets: ControllerButton): DemoState {
        val step = TutorialStep(1, 34_000, targets.toList(), mode, 0.25, 3_000)
        return DemoState(
            phase = DemoPhase.Playing,
            tutorial = ActiveTutorial(step, StepProgress.start(step), isWaiting = true),
        )
    }

    private companion object {
        const val VIOLATIONS = 60
    }
}
