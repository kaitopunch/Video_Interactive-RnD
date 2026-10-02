package com.pion.psremote.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pion.psremote.R
import com.pion.psremote.core.ui.token.Spacing
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.feature.home.component.DemoCard

/** Stateless. A heading over whichever of loading, the game grid, or a message the phase calls for. */
@Composable
internal fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The cutout only, as on the demo screen: the bars are hidden, and a transient one swiped into
        // view must not shift the grid under the user's finger.
        Column(
            modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val phase = state.phase) {
                HomePhase.Loading -> Loading()
                is HomePhase.Ready -> DemoGrid(phase.demos, onIntent)
                HomePhase.Empty -> Message(R.string.home_empty, onIntent)
                is HomePhase.Failed -> Message(R.string.home_failed, onIntent)
            }
        }
    }
}

@Composable
private fun DemoGrid(demos: List<DemoSummary>, onIntent: (HomeIntent) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_MIN_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        items(demos, key = { it.id }) { demo -> DemoCard(demo, onIntent) }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.home_loading)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** Both messages come with Retry (confirm.md H3): with no list there is nothing else on the screen to press. */
@Composable
private fun Message(@StringRes text: Int, onIntent: (HomeIntent) -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = { onIntent(HomeIntent.RetryClicked) }) { Text(stringResource(R.string.action_retry)) }
    }
}

/** Three 16:9 cards across a landscape phone, enough width for a title on one line. */
private val CARD_MIN_WIDTH = 240.dp
