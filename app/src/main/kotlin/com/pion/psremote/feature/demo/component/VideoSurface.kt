package com.pion.psremote.feature.demo.component

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame

/**
 * Draws the player's video filling the screen, cropping what does not fit (confirm.md Q15): a 16:9 video
 * on a 20:9 phone loses a strip at the top and bottom instead of gaining black bars at the sides.
 *
 * The second of the two files that import `androidx.media3.**` (LLM.md §4). Black until the first frame.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        ContentFrame(player = player, contentScale = ContentScale.Crop)
    }
}
