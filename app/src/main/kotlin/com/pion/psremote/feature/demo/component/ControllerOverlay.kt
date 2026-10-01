package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.feature.demo.DemoIntent

/**
 * The on-screen controller. Each button takes its own pointer, so any number of fingers can hold any
 * number of buttons at once — what a SIMULTANEOUS step needs (README §3).
 */
@Composable
fun ControllerOverlay(
    layout: ControllerLayout,
    enabledButtons: Set<ControllerButton>,
    onIntent: (DemoIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val exactTouchTargets = remember(viewConfiguration) { ExactTouchTargets(viewConfiguration) }
    CompositionLocalProvider(LocalViewConfiguration provides exactTouchTargets) {
        Box(modifier.fillMaxSize()) {
            layout.buttons.forEach { (button, geometry) ->
                key(button) {
                    ControllerButtonView(button, geometry, enabled = button in enabledButtons, onIntent = onIntent)
                }
            }
        }
    }
}

/**
 * Compose stretches the touch area of anything smaller than 48 dp to 48 dp — an accessibility default that, here,
 * makes a button take touches outside the shape the spotlight cuts (README §3). CREATE, OPTIONS, the shoulder
 * buttons and PS are under 48 dp on a 384 dp-tall phone, and every button is on a smaller one, so a touch in a
 * round button's corner, or just beside a pill, pressed it (F6). The controller's buttons are big by design and
 * their spacing is measured; the Exit button, outside this overlay, keeps the default.
 */
private class ExactTouchTargets(default: ViewConfiguration) : ViewConfiguration by default {
    override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
}

@Composable
private fun ControllerButtonView(
    button: ControllerButton,
    geometry: ButtonGeometry,
    enabled: Boolean,
    onIntent: (DemoIntent) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    // Read at the moment of touch-down, so a change of `enabled` never restarts a gesture in progress.
    val isEnabled by rememberUpdatedState(enabled)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val shape = geometry.shape.toComposeShape()

    Box(
        modifier = Modifier
            .placeAt(geometry.bounds)
            // Before pointerInput: hit-testing honours a clip's outline, so a round button takes touches
            // on its circle only — exactly the hole the spotlight cuts — not in its bounding square's corners.
            // That holds only with no minimum touch target stretching it back out (ExactTouchTargets).
            .clip(shape)
            .semantics {
                role = Role.Button
                contentDescription = button.name
            }
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    // A touch that lands on a blocked button is swallowed whole: no press effect, no
                    // intent — and it stays swallowed even if the button becomes live mid-touch, so a
                    // finger already down when a tutorial appears does not count (README §7 "bấm sớm").
                    if (!isEnabled) {
                        consumeUntilAllUp()
                        return@awaitEachGesture
                    }
                    pressed = true
                    currentOnIntent(DemoIntent.ButtonPressed(button))
                    try {
                        consumeUntilAllUp()
                    } finally {
                        pressed = false
                        currentOnIntent(DemoIntent.ButtonReleased(button))
                    }
                }
            }
            // `pressed` is read in the layer and draw phases only: a press re-draws this button and recomposes
            // nothing. The fill is a plain rect because the clip above already gives it the button's shape.
            .graphicsLayer {
                val scale = if (pressed) PRESSED_SCALE else 1f
                scaleX = scale
                scaleY = scale
            }
            .drawBehind { drawRect(if (pressed) PsColors.ButtonFillPressed else PsColors.ButtonFill) }
            .border(BUTTON_BORDER, PsColors.ButtonBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        ButtonGlyph(button, Modifier.fillMaxSize(GLYPH_FRACTION))
    }
}

/**
 * Consumes every event until the last finger on this button lifts. A second finger on the same button
 * is part of the same press: a repeated step needs a release and a new press (D4).
 */
private suspend fun AwaitPointerEventScope.consumeUntilAllUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

fun ButtonShape.toComposeShape(): Shape = when (this) {
    ButtonShape.Circle -> CircleShape
    ButtonShape.Rounded -> RoundedCornerShape(ROUNDED_CORNER_PERCENT)
}

/** How much of a button its symbol fills. */
private const val GLYPH_FRACTION = 0.6f
private const val PRESSED_SCALE = 0.92f

/** Hairline that separates a button from the video behind it. Measured against 1080p gameplay. */
private val BUTTON_BORDER = 1.5.dp
