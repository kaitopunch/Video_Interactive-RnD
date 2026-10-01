package com.pion.psremote.feature.demo.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pion.psremote.domain.model.ControllerButton
import kotlin.math.min
import kotlin.math.roundToInt

enum class ButtonShape { Circle, Rounded }

data class ButtonGeometry(val bounds: Rect, val shape: ButtonShape) {
    /** Corner radius of a [ButtonShape.Rounded] button, in px. Both layers must draw it the same. */
    val cornerRadius: Float
        get() = min(bounds.width, bounds.height) * ROUNDED_CORNER_PERCENT / 100f

    /**
     * The point of the outline at 45° towards the top-right corner, where a step badge is centred: on the
     * edge, so it covers neither the symbol nor empty space beyond a round button's corner. A circle is a
     * rounded rectangle whose corner radius is half its side.
     */
    val badgeAnchor: Offset
        get() {
            val radius = if (shape == ButtonShape.Circle) bounds.width / 2f else cornerRadius
            val inset = radius * (1f - SQRT_HALF)
            return Offset(bounds.right - inset, bounds.top + inset)
        }
}

data class ControllerLayout(
    val buttons: Map<ControllerButton, ButtonGeometry>,
    val exit: ButtonGeometry,
)

/** Safe-area insets in px: the display cutout, which in landscape sits on a left or right edge. */
data class EdgeInsets(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Zero = EdgeInsets(0f, 0f, 0f, 0f)
    }
}

/** Percent of the shorter side; passed to `RoundedCornerShape(percent)` so shape and cut-out agree. */
const val ROUNDED_CORNER_PERCENT = 28

/**
 * Where every control sits, as a pure function of the screen size, in px.
 *
 * The button layer, the tutorial's dim layer and its badges all read the same [ControllerLayout], so a
 * highlight cut-out *is* the touch area, pixel for pixel, on every screen size (README §3). Bounds are rounded to
 * whole pixels here, once, so neither layer rounds differently from the other.
 *
 * Positions are in units of 1 % of the usable height, anchored to the left edge, the centre or the right
 * edge. Anchoring to the edges rather than spreading by width keeps both clusters under the thumbs on a
 * 20:9 phone instead of drifting apart. The arrangement follows the DualSense; the Figma file was not
 * readable when this was written (confirm.md Q1), so this table is the one place to change once it is.
 */
object ControllerGeometry {

    fun layout(width: Float, height: Float, insets: EdgeInsets = EdgeInsets.Zero): ControllerLayout {
        val frame = Frame(width, height, insets)
        return ControllerLayout(
            buttons = SPECS.mapValues { (_, spec) -> frame.place(spec) },
            exit = frame.place(EXIT),
        )
    }

    /**
     * The safe area, and the size of one unit in it. A window at least [MIN_WIDTH_UNITS] units wide — every phone
     * in landscape, 16:9 and up — gets 1 % of its height per unit, from its top edge, exactly as it always has.
     * A narrower one (a foldable's inner screen, split screen) gets the same block scaled down to fit the width and
     * centred vertically: scaled by height there, CREATE and OPTIONS would land on L2 and R2, and two overlapping
     * touch areas break "the lit hole is the touch area" (README §3).
     */
    private class Frame(width: Float, height: Float, insets: EdgeInsets) {
        val left = insets.left
        val right = width - insets.right
        val unit: Float
        val top: Float

        init {
            val usableWidth = right - left
            val usableHeight = height - insets.top - insets.bottom
            // Multiplied out, so a window of exactly MIN_WIDTH_UNITS : 100 takes the first branch every time.
            if (usableWidth * 100f >= MIN_WIDTH_UNITS * usableHeight) {
                unit = usableHeight / 100f
                top = insets.top
            } else {
                unit = usableWidth / MIN_WIDTH_UNITS
                top = insets.top + (usableHeight - 100f * unit) / 2f
            }
        }

        private val centerX = (left + right) / 2f

        fun place(spec: Spec): ButtonGeometry {
            val cx = when (spec.anchor) {
                Anchor.Left -> left + spec.x * unit
                Anchor.Center -> centerX + spec.x * unit
                Anchor.Right -> right - spec.x * unit
            }
            val cy = top + spec.y * unit
            val halfW = spec.width * unit / 2f
            val halfH = spec.height * unit / 2f
            return ButtonGeometry(rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH), spec.shape)
        }

        private fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(
            left.roundToInt().toFloat(),
            top.roundToInt().toFloat(),
            right.roundToInt().toFloat(),
            bottom.roundToInt().toFloat(),
        )
    }

    /**
     * The narrowest window, in units, that the [SPECS] fit across without overlapping: CREATE's left edge is
     * W/2 − 42 units and L2's right edge 40, so they meet at W = 164. 170 leaves 3 units between them, and
     * 16:9 (177.8) is still above it — no phone in landscape takes the narrow branch.
     */
    private const val MIN_WIDTH_UNITS = 170f

    private enum class Anchor { Left, Center, Right }

    /** Centre ([x] from the anchor, inward for Right; [y] from the top) and size, all in units. */
    private class Spec(
        val anchor: Anchor,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val shape: ButtonShape,
    )

    private val SPECS: Map<ControllerButton, Spec> = mapOf(
        ControllerButton.L2 to Spec(Anchor.Left, 30f, 10f, 20f, 10f, ButtonShape.Rounded),
        ControllerButton.L1 to Spec(Anchor.Left, 30f, 23f, 20f, 10f, ButtonShape.Rounded),
        ControllerButton.DPAD_UP to Spec(Anchor.Left, 28f, 43f, 13f, 13f, ButtonShape.Rounded),
        ControllerButton.DPAD_LEFT to Spec(Anchor.Left, 15f, 56f, 13f, 13f, ButtonShape.Rounded),
        ControllerButton.DPAD_RIGHT to Spec(Anchor.Left, 41f, 56f, 13f, 13f, ButtonShape.Rounded),
        ControllerButton.DPAD_DOWN to Spec(Anchor.Left, 28f, 69f, 13f, 13f, ButtonShape.Rounded),
        ControllerButton.L3 to Spec(Anchor.Left, 54f, 85f, 14f, 14f, ButtonShape.Circle),
        ControllerButton.R2 to Spec(Anchor.Right, 30f, 10f, 20f, 10f, ButtonShape.Rounded),
        ControllerButton.R1 to Spec(Anchor.Right, 30f, 23f, 20f, 10f, ButtonShape.Rounded),
        ControllerButton.TRIANGLE to Spec(Anchor.Right, 28f, 43f, 13f, 13f, ButtonShape.Circle),
        ControllerButton.CIRCLE to Spec(Anchor.Right, 15f, 56f, 13f, 13f, ButtonShape.Circle),
        ControllerButton.SQUARE to Spec(Anchor.Right, 41f, 56f, 13f, 13f, ButtonShape.Circle),
        ControllerButton.CROSS to Spec(Anchor.Right, 28f, 69f, 13f, 13f, ButtonShape.Circle),
        ControllerButton.R3 to Spec(Anchor.Right, 54f, 85f, 14f, 14f, ButtonShape.Circle),
        ControllerButton.TOUCHPAD to Spec(Anchor.Center, 0f, 12f, 44f, 18f, ButtonShape.Rounded),
        ControllerButton.CREATE to Spec(Anchor.Center, -34f, 10f, 16f, 7f, ButtonShape.Rounded),
        ControllerButton.OPTIONS to Spec(Anchor.Center, 34f, 10f, 16f, 7f, ButtonShape.Rounded),
        ControllerButton.PS to Spec(Anchor.Center, 0f, 87f, 11f, 11f, ButtonShape.Circle),
    )

    private val EXIT = Spec(Anchor.Left, 8f, 8f, 9f, 9f, ButtonShape.Circle)
}

/** cos 45°: how far along a corner's radius its 45° point sits. */
private const val SQRT_HALF = 0.70710677f
