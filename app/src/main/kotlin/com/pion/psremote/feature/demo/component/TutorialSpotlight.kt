package com.pion.psremote.feature.demo.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.feature.demo.ButtonHighlight

/**
 * Dims the whole screen except the [highlights], and rings those: the active ones bright and pulsing,
 * a SEQUENCE button whose turn has not come under a lighter dim with a faint, still ring.
 *
 * Each hole is cut from the same [ButtonGeometry] the button is placed with, so the lit area is the
 * touch area exactly (requirements.md §3). This layer draws only: it has no pointer input, so a touch in a hole
 * reaches the button beneath it. What makes the dimmed buttons unpressable is their `enabled` flag,
 * not this layer (confirm.md D2).
 *
 * Two layers, so the pulse costs one layer property a frame and nothing is redrawn: the dim layer is a
 * full-screen offscreen buffer, and re-rendering it 60 times a second for the pulse made an SM-A165F miss
 * the deadline on every frame of a waiting step — P50 overrun +5.2 ms, +0.7 ms split (F9,
 * plans/261001-1130-quality-perf-device-tests/reports/benchmark-report.md).
 */
@Composable
fun TutorialSpotlight(
    layout: ControllerLayout,
    highlights: Map<ControllerButton, ButtonHighlight>,
    modifier: Modifier = Modifier,
) {
    val lit = remember(layout, highlights) {
        highlights.mapNotNull { (button, highlight) -> layout.buttons[button]?.let { LitButton(it, highlight.isActive) } }
    }
    Box(modifier.fillMaxSize()) {
        DimWithHoles(lit)
        PulsingRings(remember(lit) { lit.filter(LitButton::isActive) })
    }
}

/** Drawn when [lit] changes, and only then. */
@Composable
private fun DimWithHoles(lit: List<LitButton>) {
    Canvas(
        Modifier
            .fillMaxSize()
            // Offscreen, so BlendMode.Clear punches through this layer only, not the video beneath it.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(PsColors.Dim)
        lit.forEach { drawButtonShape(it.geometry, Color.Transparent, blendMode = BlendMode.Clear) }
        lit.filterNot(LitButton::isActive).forEach {
            drawButtonShape(it.geometry, PsColors.DimUpcoming)
            drawRing(it.geometry, PsColors.Highlight.copy(alpha = UPCOMING_RING_ALPHA))
        }
    }
}

/** The pressable buttons' rings, at full colour; the pulse is the layer's alpha. */
@Composable
private fun PulsingRings(active: List<LitButton>) {
    val pulse by rememberInfiniteTransition(label = "spotlight").animateFloat(
        initialValue = RING_ALPHA_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "ring",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read in the layer block, not the draw block: a frame of the pulse updates this property
                // and re-records nothing.
                alpha = pulse
                // No two rings overlap, so the alpha can be applied draw call by draw call — no offscreen
                // buffer for this layer.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        active.forEach { drawRing(it.geometry, PsColors.Highlight) }
    }
}

private class LitButton(val geometry: ButtonGeometry, val isActive: Boolean)

/** A ring just outside the button, so it never covers the touch area it marks. */
private fun DrawScope.drawRing(geometry: ButtonGeometry, color: Color) {
    val ringWidth = RING_WIDTH.toPx()
    drawButtonShape(geometry, color, inflate = RING_GAP.toPx() + ringWidth / 2f, style = Stroke(ringWidth))
}

private fun DrawScope.drawButtonShape(
    geometry: ButtonGeometry,
    color: Color,
    inflate: Float = 0f,
    style: DrawStyle = Fill,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    val rect = geometry.bounds.inflate(inflate)
    when (geometry.shape) {
        ButtonShape.Circle -> drawOval(color, rect.topLeft, rect.size, style = style, blendMode = blendMode)
        ButtonShape.Rounded -> drawRoundRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(geometry.cornerRadius + inflate),
            style = style,
            blendMode = blendMode,
        )
    }
}

private const val RING_ALPHA_MIN = 0.35f

/** Below the pulse's lowest point, so a button still to come never looks as live as the next one. */
private const val UPCOMING_RING_ALPHA = 0.25f
private const val PULSE_MILLIS = 700
private val RING_WIDTH = 3.dp

/** Space between a button's edge and its ring, so the ring never covers the touch area it marks. */
private val RING_GAP = 3.dp
