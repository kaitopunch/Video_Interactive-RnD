package com.pion.psremote.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Dark only: the app is a full-screen video, and a light surface over gameplay reads as a glitch.
 *
 * Text drawn straight over the video (the countdown, the loading state) sits on no `Surface`, so nothing
 * would set its colour and Material's default is black — invisible on a dark frame. The theme sets it.
 */
@Composable
fun PsRemoteTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = PsColors.Highlight,
        onPrimary = Color.Black,
        surface = PsColors.Surface,
        background = Color.Black,
    )
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}

object PsColors {
    val Surface = Color(0xFF16181D)

    /** The tutorial's rings and step badges. */
    val Highlight = Color(0xFF4FC3F7)

    /** Over everything a tutorial does not point at. */
    val Dim = Color.Black.copy(alpha = 0.65f)

    /** Over a SEQUENCE button whose turn has not come: visible as part of the combo, plainly not the next one. */
    val DimUpcoming = Color.Black.copy(alpha = 0.4f)

    /** Behind modal panels: dark enough to read over, light enough to still see the frame. */
    val Scrim = Color.Black.copy(alpha = 0.75f)

    val ButtonFill = Color.Black.copy(alpha = 0.35f)
    val ButtonFillPressed = Color.White.copy(alpha = 0.35f)
    val ButtonBorder = Color.White.copy(alpha = 0.55f)
    val Glyph = Color.White

    val Cross = Color(0xFF7FA8FF)
    val Circle = Color(0xFFFF6B6B)
    val Square = Color(0xFFF28AD8)
    val Triangle = Color(0xFF3DDBB0)
}
