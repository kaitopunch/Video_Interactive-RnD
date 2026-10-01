package com.pion.psremote.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.psremote.core.mvi.CollectEffects
import org.koin.compose.viewmodel.koinViewModel

/** The game picker's stateful entry point. Which screen a tap leads to is the nav host's business (LLM.md §7). */
@Composable
fun HomeRoute(
    onOpenDemo: (demoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.OpenDemo -> onOpenDemo(effect.demoId)
        }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(HomeIntent.ScreenStarted)
        onStopOrDispose { }
    }

    HomeScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
