package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.core.ui.token.TextSize
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.feature.demo.ButtonBadge
import com.pion.psremote.feature.demo.ButtonHighlight

/**
 * The marks on lit buttons that replaced the tutorial card: a SEQUENCE's order (1, 2, 3) and the presses
 * an ANY_ORDER button still owes (×2). Each is centred on its button's [ButtonGeometry.badgeAnchor].
 *
 * Drawn above the dim layer, so the half of a badge outside its button's hole is not dimmed. No pointer
 * input anywhere in here: a touch on a badge falls through to the button beneath it.
 */
@Composable
fun StepBadges(
    layout: ControllerLayout,
    highlights: Map<ControllerButton, ButtonHighlight>,
    modifier: Modifier = Modifier,
) {
    val radius = with(LocalDensity.current) { BADGE_SIZE.toPx() } / 2f
    Box(modifier.fillMaxSize()) {
        highlights.forEach { (button, highlight) ->
            val badge = highlight.badge ?: return@forEach
            val anchor = layout.buttons[button]?.badgeAnchor ?: return@forEach
            key(button) {
                StepBadge(badge.text(), highlight.isActive, Modifier.placeAt(Rect(anchor, radius)))
            }
        }
    }
}

/**
 * Filled while its button is the one to press; an outline while its turn has not come.
 *
 * The circle is a fixed size — larger, it would cover the button's symbol — so the text shrinks to fit it: at
 * Android 14's 200 % font scale "×2" was wider than the circle (F7). Never larger than labelMedium, so at the
 * default scale nothing changes.
 */
@Composable
private fun StepBadge(text: String, isActive: Boolean, modifier: Modifier) {
    val textColor = if (isActive) MaterialTheme.colorScheme.onPrimary else PsColors.Highlight
    val style = MaterialTheme.typography.labelMedium
    val minFontSize = with(LocalDensity.current) { TextSize.ControllerLabelMin.toSp() }
    Box(
        modifier = modifier
            .background(if (isActive) PsColors.Highlight else PsColors.Surface, CircleShape)
            .border(BADGE_BORDER, PsColors.Highlight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            style = style.copy(color = textColor, textAlign = TextAlign.Center, lineHeight = TextUnit.Unspecified),
            maxLines = 1,
            // One token: wrapped, it would break mid-word onto a line maxLines then drops ("OPTION"), which
            // auto-sizing missed on a 420 dpi phone at 130 % text. Unwrapped, too wide is too wide, and it shrinks.
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
        )
    }
}

@Composable
private fun ButtonBadge.text(): String = when (this) {
    is ButtonBadge.Position -> value.toString()
    is ButtonBadge.Presses -> stringResource(R.string.badge_presses, count)
}

/** Readable at arm's length. A face button is ~47 dp on a 360 dp-tall phone, so this covers its corner, not its symbol. */
private val BADGE_SIZE = 22.dp
private val BADGE_BORDER = 1.5.dp
