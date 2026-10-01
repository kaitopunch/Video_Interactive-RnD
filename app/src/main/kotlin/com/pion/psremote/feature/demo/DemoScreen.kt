package com.pion.psremote.feature.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.pion.psremote.R
import com.pion.psremote.feature.demo.component.ControllerGeometry
import com.pion.psremote.feature.demo.component.ControllerLayout
import com.pion.psremote.feature.demo.component.ControllerOverlay
import com.pion.psremote.feature.demo.component.EdgeInsets
import com.pion.psremote.feature.demo.component.ErrorPanel
import com.pion.psremote.feature.demo.component.ExitButton
import com.pion.psremote.feature.demo.component.FinishedPanel
import com.pion.psremote.feature.demo.component.LoadingIndicator
import com.pion.psremote.feature.demo.component.ResumeCountdownOverlay
import com.pion.psremote.feature.demo.component.ScoreHud
import com.pion.psremote.feature.demo.component.StepBadges
import com.pion.psremote.feature.demo.component.TutorialSpotlight
import com.pion.psremote.feature.demo.component.message

/**
 * Stateless. Layers, bottom to top: video, controller, score, tutorial dim + badges, exit, then whichever modal
 * the phase calls for. Nothing here decides anything: every `if` reads a value [DemoState] computed.
 */
@Composable
internal fun DemoScreen(
    state: DemoState,
    onIntent: (DemoIntent) -> Unit,
    video: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Left-to-right regardless of locale: the controller is physical geometry (a DualSense does not mirror),
    // and under RTL `offset` and `TopStart` would mirror every button while the spotlight's holes, drawn at
    // absolute bounds, would not — lighting a disabled button where the target used to be.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        DemoLayers(state, onIntent, video, modifier)
    }
}

@Composable
private fun DemoLayers(
    state: DemoState,
    onIntent: (DemoIntent) -> Unit,
    video: @Composable () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val layout = rememberControllerLayout(constraints.maxWidth, constraints.maxHeight)

        video()
        if (state.isControllerVisible) {
            ControllerOverlay(layout, state.enabledButtons, onIntent)
        }
        // Under the dim, like everything a tutorial does not point at: R2's ring and badge reach past R2 towards the
        // score on a short window, and must stay on top. The points never change under the dim — they change in the
        // update that removes it.
        if (state.isScoreVisible) {
            ScoreHud(layout.score, state.score.points, state.lastAward)
        }
        if (state.tutorial != null) {
            val highlights = state.highlights
            TutorialSpotlight(layout, highlights)
            StepBadges(layout, highlights)
        }
        if (state.isExitButtonVisible) {
            ExitButton(layout.exit, onClick = { onIntent(DemoIntent.ExitClicked) })
        }
        when (val phase = state.phase) {
            DemoPhase.Loading -> LoadingIndicator()
            DemoPhase.Playing -> Unit
            DemoPhase.Finished -> FinishedPanel(state.score, onIntent)
            is DemoPhase.InvalidScript -> ErrorPanel(
                title = stringResource(R.string.error_script_title),
                messages = phase.violations.map { it.message() },
                onIntent = onIntent,
            )
            is DemoPhase.Failed -> ErrorPanel(
                title = stringResource(R.string.error_failed_title),
                messages = listOf(phase.error.message()),
                onIntent = onIntent,
            )
        }
        state.resumeCountdown?.let { ResumeCountdownOverlay(it) }
    }
}

/**
 * Only the display cutout is avoided, not the system bars: they are hidden (MainActivity), and a
 * transient bar swiped into view must not shift every button under the user's thumbs.
 */
@Composable
private fun rememberControllerLayout(width: Int, height: Int): ControllerLayout {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val insets = EdgeInsets(
        left = cutout.getLeft(density, direction).toFloat(),
        top = cutout.getTop(density).toFloat(),
        right = cutout.getRight(density, direction).toFloat(),
        bottom = cutout.getBottom(density).toFloat(),
    )
    return remember(width, height, insets) {
        ControllerGeometry.layout(width.toFloat(), height.toFloat(), insets)
    }
}
