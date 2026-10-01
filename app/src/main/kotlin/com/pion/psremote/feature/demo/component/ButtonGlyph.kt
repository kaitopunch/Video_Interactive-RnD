package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.core.ui.token.TextSize
import com.pion.psremote.domain.model.ControllerButton
import kotlin.math.min

/**
 * The symbol on a button, drawn to fill [modifier]'s size. Face buttons and the D-pad are drawn as
 * vector shapes rather than text: `△ ○ ✕ □` fall back to whatever symbol font a device has, and look
 * different on every one. Everything else is its printed label, auto-sized to fit.
 */
@Composable
fun ButtonGlyph(button: ControllerButton, modifier: Modifier = Modifier) {
    val label = button.label
    if (label != null) {
        val minFontSize = with(LocalDensity.current) { TextSize.ControllerLabelMin.toSp() }
        BasicText(
            text = label,
            // Its own height, centred in the box: BasicText draws from the top of whatever height it is given.
            modifier = modifier.wrapContentHeight(),
            style = MaterialTheme.typography.labelLarge.copy(
                color = PsColors.Glyph,
                textAlign = TextAlign.Center,
                // The font's own line height, which shrinks with the label. labelLarge's fixed 20 sp line is taller
                // than CREATE's and OPTIONS's label box on a 384 dp-tall phone, so no size ever fit and auto-sizing
                // fell to the floor: both were drawn tiny in pills with room to spare (F7).
                lineHeight = TextUnit.Unspecified,
            ),
            maxLines = 1,
            // One token: wrapped, it would break mid-word onto a line maxLines then drops ("OPTION"), which
            // auto-sizing missed on a 420 dpi phone at 130 % text. Unwrapped, too wide is too wide, and it shrinks.
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = minFontSize),
        )
    } else {
        // Shapes are built once per size, not on every draw: a press re-records the button's layer, glyph included.
        Spacer(modifier.drawWithCache { onDrawBehind(symbol(button)) })
    }
}

/** The printed label, or null for a button whose symbol is drawn. Hardware names: not translated. */
private val ControllerButton.label: String?
    get() = when (this) {
        ControllerButton.CROSS, ControllerButton.CIRCLE, ControllerButton.SQUARE, ControllerButton.TRIANGLE,
        ControllerButton.DPAD_UP, ControllerButton.DPAD_DOWN, ControllerButton.DPAD_LEFT, ControllerButton.DPAD_RIGHT,
        -> null
        else -> name
    }

/** Sizes and shapes [button]'s symbol for the cache's size; the block returned only issues draw calls. */
private fun CacheDrawScope.symbol(button: ControllerButton): DrawScope.() -> Unit {
    val side = min(size.width, size.height)
    val stroke = Stroke(width = side * SYMBOL_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val center = size.center
    fun at(x: Float, y: Float) = Offset(center.x + (x - 0.5f) * side, center.y + (y - 0.5f) * side)

    return when (button) {
        ControllerButton.CROSS -> {
            {
                drawLine(PsColors.Cross, at(0.2f, 0.2f), at(0.8f, 0.8f), stroke.width, StrokeCap.Round)
                drawLine(PsColors.Cross, at(0.8f, 0.2f), at(0.2f, 0.8f), stroke.width, StrokeCap.Round)
            }
        }
        ControllerButton.CIRCLE -> {
            { drawCircle(PsColors.Circle, radius = side * 0.32f, center = center, style = stroke) }
        }
        ControllerButton.SQUARE -> {
            { drawRect(PsColors.Square, topLeft = at(0.2f, 0.2f), size = Size(side * 0.6f, side * 0.6f), style = stroke) }
        }
        ControllerButton.TRIANGLE -> {
            val path = triangle(at(0.5f, 0.16f), at(0.86f, 0.78f), at(0.14f, 0.78f))
            ({ drawPath(path, PsColors.Triangle, style = stroke) })
        }
        ControllerButton.DPAD_UP -> arrow(0f, ::at)
        ControllerButton.DPAD_RIGHT -> arrow(90f, ::at)
        ControllerButton.DPAD_DOWN -> arrow(180f, ::at)
        ControllerButton.DPAD_LEFT -> arrow(270f, ::at)
        else -> {
            {}
        }
    }
}

/** A filled arrow pointing up, turned by [degrees] clockwise. */
private fun arrow(degrees: Float, at: (Float, Float) -> Offset): DrawScope.() -> Unit {
    val path = triangle(at(0.5f, 0.24f), at(0.78f, 0.7f), at(0.22f, 0.7f))
    return { rotate(degrees) { drawPath(path, Color.White) } }
}

private fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

/** Stroke width as a fraction of the glyph's side, so a symbol keeps its weight at every size. */
private const val SYMBOL_STROKE = 0.1f
