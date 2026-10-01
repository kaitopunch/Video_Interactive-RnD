package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsColors
import kotlin.math.min

/**
 * The only control besides the controller (confirm.md Q16). Drawn above the tutorial's dim layer: a user
 * stuck on a step can always leave.
 *
 * A back chevron, not an ✕, so it cannot be mistaken for the Cross button a tutorial may be asking for.
 */
@Composable
fun ExitButton(geometry: ButtonGeometry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .placeAt(geometry.bounds)
            .clip(CircleShape)
            .background(PsColors.ButtonFill)
            .border(EXIT_BORDER, PsColors.ButtonBorder, CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.action_exit),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(CHEVRON_FRACTION)) {
            val side = min(size.width, size.height)
            val stroke = side * CHEVRON_STROKE
            val tip = Offset(center.x - side * 0.2f, center.y)
            drawLine(PsColors.Glyph, Offset(center.x + side * 0.15f, center.y - side * 0.35f), tip, stroke, StrokeCap.Round)
            drawLine(PsColors.Glyph, Offset(center.x + side * 0.15f, center.y + side * 0.35f), tip, stroke, StrokeCap.Round)
        }
    }
}

private const val CHEVRON_FRACTION = 0.5f
private const val CHEVRON_STROKE = 0.14f
private val EXIT_BORDER = 1.5.dp
