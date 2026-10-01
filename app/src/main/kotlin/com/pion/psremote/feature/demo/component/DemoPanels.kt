package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.core.ui.token.Spacing
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier
import com.pion.psremote.feature.demo.DemoIntent

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

/**
 * Shown on return from the background (D6). Swallows every touch: nothing under it is live until the
 * countdown ends, and a finger resting on a button must not count as a press when it does.
 */
@Composable
fun ResumeCountdownOverlay(secondsLeft: Int, modifier: Modifier = Modifier) {
    ModalScrim(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.resume_title), style = MaterialTheme.typography.titleLarge)
            Text(secondsLeft.toString(), style = MaterialTheme.typography.displayLarge, color = PsColors.Highlight)
            Text(stringResource(R.string.resume_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The end of the video: the run's score, then replay or leave (confirm.md Q13).
 *
 * Scrolls: on a 320 dp-tall phone at 200 % text, title, score, tiers, body and buttons are taller than the screen,
 * and a panel that clips its buttons leaves the user no way out.
 */
@Composable
fun FinishedPanel(score: Score, onIntent: (DemoIntent) -> Unit, modifier: Modifier = Modifier) {
    ModalScrim(modifier) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text(stringResource(R.string.finished_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.finished_score, score.points, score.maxPoints),
                    style = MaterialTheme.typography.headlineMedium,
                    color = PsColors.Highlight,
                )
                Text(
                    stringResource(R.string.finished_tiers, score.count(ScoreTier.PERFECT), score.count(ScoreTier.GOOD)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.finished_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedButton(onClick = { onIntent(DemoIntent.ExitClicked) }) {
                        Text(stringResource(R.string.action_exit))
                    }
                    Button(onClick = { onIntent(DemoIntent.ReplayClicked) }) {
                        Text(stringResource(R.string.action_replay))
                    }
                }
            }
        }
    }
}

/**
 * Why the demo cannot run. For a bad script, every violation at once, so the BA fixes the file in one
 * pass instead of one error per install (confirm.md Q12).
 */
@Composable
fun ErrorPanel(
    title: String,
    messages: List<String>,
    onIntent: (DemoIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(messages) { message ->
                    Text("• $message", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = { onIntent(DemoIntent.ExitClicked) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_exit))
            }
        }
    }
}

/** A full-screen dim that takes every touch, with [content] centred on it. */
@Composable
private fun ModalScrim(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PsColors.Scrim)
            // Hit-tested like any pointer node, so the siblings below — the controller — never are.
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
