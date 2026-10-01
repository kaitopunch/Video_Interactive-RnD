package com.pion.psremote.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a ViewModel's effects, lifecycle-aware. The one copy of this block in the codebase.
 *
 * `collect`, never `collectLatest`: `collectLatest` cancels the previous handler when a second effect
 * arrives, so an effect is silently lost (`.claude/CLAUDE.md`, non-negotiable rule 5).
 */
@Composable
fun <E : UiEffect> CollectEffects(
    effects: Flow<E>,
    onEffect: (E) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}
