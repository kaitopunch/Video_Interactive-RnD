package com.pion.psremote.core.ui.token

import androidx.compose.ui.unit.dp

/** Text sizes that are not a Material type scale. No `.sp` literal lives under `feature/` (MVI doc §11). */
object TextSize {

    /**
     * Floor for a controller label auto-sized to its button, and for a step badge's text in its circle.
     * Material's default floor, 12 sp, cut CREATE
     * and OPTIONS to "CRE" and "OPT" on a 1080 px-tall phone: their pills are short, and the label must
     * shrink to fit rather than clip.
     *
     * In dp, not sp, and converted where it is used: a button does not grow with the user's font size, so the
     * smallest its label may shrink to must not either. As 6 sp, the floor doubled at Android 14's 200 % font
     * scale and CREATE overflowed its pill on every phone (F7). The label's largest size still follows the setting.
     *
     * 5, not 6: in half of a split screen OPTIONS fits only from about 5.5 dp. Full screen, the label settles
     * near 10 dp and never reaches the floor.
     */
    val ControllerLabelMin = 5.dp
}
