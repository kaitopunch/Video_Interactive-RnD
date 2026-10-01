package com.pion.psremote.feature.demo

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pion.psremote.data.playback.Media3VideoPlayback

/**
 * Owns the demo screen's player for exactly as long as the screen's ViewModelStore.
 *
 * NOT a screen ViewModel, and the one plain `ViewModel` in the app (MVI doc §3, "Where the rule
 * stops"): no state, no intent, no effect. It exists because two readers need the *same* player for the
 * *same* lifetime — [DemoViewModel] drives it through the `VideoPlayback` port, and the video surface
 * draws it — and a ViewModelStore is the one Android-owned place that survives a configuration change
 * and is cleared exactly once.
 *
 * Cost of holding the player in `remember` instead: a configuration change builds a second player at
 * position 0 behind a [DemoViewModel] that still believes the video is stopped at 34 750 ms.
 */
class DemoPlaybackHost(context: Context) : ViewModel() {

    val playback = Media3VideoPlayback(context)

    override fun onCleared() {
        playback.release()
    }
}
