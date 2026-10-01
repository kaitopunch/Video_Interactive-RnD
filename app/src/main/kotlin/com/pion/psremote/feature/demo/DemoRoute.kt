package com.pion.psremote.feature.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.psremote.core.mvi.CollectEffects
import com.pion.psremote.feature.demo.component.VideoSurface
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The demo screen's stateful entry point. Both ViewModels live on this destination's back-stack entry,
 * so leaving the demo clears them and releases the player; opening another demo builds a fresh pair.
 * Everything below this function takes plain values.
 */
@Composable
fun DemoRoute(
    demoId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host: DemoPlaybackHost = koinViewModel()
    // Read once, when the ViewModel is created: the order matches the `params.get()`s in AppModule.
    val viewModel: DemoViewModel = koinViewModel { parametersOf(demoId, host.playback) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Held steady: the subtree below holds the video surface and a canvas per frame (MVI doc §8).
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val video: @Composable () -> Unit = remember(host) { { VideoSurface(host.playback.player) } }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            DemoEffect.Exit -> onExit()
        }
    }
    ReportLifecycle(onIntent)

    DemoScreen(state = state, onIntent = onIntent, video = video, modifier = modifier)
}

/** ON_START / ON_STOP become intents: the ViewModel pauses in the background and counts down on return (D6). */
@Composable
private fun ReportLifecycle(onIntent: (DemoIntent) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onIntent) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onIntent(DemoIntent.ScreenStarted)
                Lifecycle.Event.ON_STOP -> onIntent(DemoIntent.ScreenStopped)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onIntent(DemoIntent.ScreenStopped)
        }
    }
}
