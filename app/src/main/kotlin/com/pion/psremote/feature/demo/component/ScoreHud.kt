package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.core.ui.token.Spacing
import com.pion.psremote.core.ui.token.TextSize
import com.pion.psremote.domain.score.ScoreTier

/**
 * The run's points while the demo plays, in their own slot of the controller layout, so they cover no button on
 * any window shape (`ControllerGeometryTest` checks the slot with the buttons). The caption reads "SCORE" until a
 * step is scored, then the tier that step earned, until the next tutorial appears (scoring rules §5).
 *
 * Drawn under the tutorial's dim layer: on a window under ~390 dp tall, R2's step badge and ring reach past R2 into
 * this slot, and must stay on top. Nothing is lost by the dim: the points change in the same update that removes it.
 * No pointer input anywhere in here: it takes no touch, like the step badges.
 */
@Composable
fun ScoreHud(geometry: ButtonGeometry, points: Int, award: ScoreTier?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ROUNDED_CORNER_PERCENT)
    Column(
        modifier = modifier
            .placeAt(geometry.bounds)
            .background(PsColors.ButtonFill, shape)
            .border(HUD_BORDER, PsColors.ButtonBorder, shape)
            // The narrowest slot, half of a split screen, is ~34 dp across: every dp of padding is taken from the text.
            .padding(Spacing.xxs)
            // Read out as one thing, "PERFECT 150", not as two unrelated texts.
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FittedLine(
            text = award?.label() ?: stringResource(R.string.score_label),
            style = MaterialTheme.typography.labelSmall,
            color = if (award != null) PsColors.Highlight else PsColors.Glyph,
            weight = CAPTION_WEIGHT,
        )
        FittedLine(points.toString(), MaterialTheme.typography.titleLarge, PsColors.Glyph, weight = POINTS_WEIGHT)
    }
}

/**
 * One line shrunk to its share of the slot, which is a fixed size: the slot does not grow with the font scale, so
 * at 200 % "HOÀN HẢO" must shrink rather than clip. The same settings as a controller label, for the same reasons
 * (LLM.md §12, F7): unwrapped, the font's own line height, a floor in dp.
 *
 * No letter spacing: labelSmall's 0.5 sp is a fixed amount that auto-sizing does not shrink and the font scale
 * grows, so in half of a split screen at 130 % "HOÀN HẢO" overflowed its slot by 1.5 px even at the floor size.
 */
@Composable
private fun ColumnScope.FittedLine(text: String, style: TextStyle, color: Color, weight: Float) {
    val minFontSize = with(LocalDensity.current) { TextSize.ControllerLabelMin.toSp() }
    BasicText(
        text = text,
        modifier = Modifier.weight(weight).fillMaxWidth().wrapContentHeight(),
        style = style.copy(
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = TextUnit.Unspecified,
            letterSpacing = TextUnit.Unspecified,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
    )
}

@Composable
private fun ScoreTier.label(): String = stringResource(
    when (this) {
        ScoreTier.PERFECT -> R.string.score_tier_perfect
        ScoreTier.GOOD -> R.string.score_tier_good
    },
)

/** The points get the larger share: they are what changes. */
private const val CAPTION_WEIGHT = 2f
private const val POINTS_WEIGHT = 3f
private val HUD_BORDER = 1.5.dp
