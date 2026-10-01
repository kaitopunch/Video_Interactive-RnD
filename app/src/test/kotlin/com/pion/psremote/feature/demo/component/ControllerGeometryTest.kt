package com.pion.psremote.feature.demo.component

import androidx.compose.ui.geometry.Rect
import com.pion.psremote.domain.model.ControllerButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs

/**
 * The controller's geometry on every window shape a phone can give the app, in px.
 *
 * Phones in landscape (16:9 and wider) are the supported case (confirm.md D1). The rest are windows a phone still
 * produces: a foldable's inner screen — where Android 16 ignores `sensorLandscape` once the display is 600 dp
 * wide — split-screen, a freeform window, and the odd tablet. On those the layout must stay *correct* (every
 * control inside, apart, round, its badge on its outline), because the spotlight hole is the touch area only as
 * long as no two touch areas overlap (README §3).
 */
@RunWith(Parameterized::class)
class ControllerGeometryTest(private val device: Device) {

    private val layout = ControllerGeometry.layout(device.width, device.height, device.insets)
    private val controls: Map<String, Rect> =
        layout.buttons.mapKeys { it.key.name }.mapValues { it.value.bounds } + ("exit" to layout.exit.bounds)

    private val safeLeft = device.insets.left
    private val safeTop = device.insets.top
    private val safeRight = device.width - device.insets.right
    private val safeBottom = device.height - device.insets.bottom

    @Test
    fun `all eighteen controller buttons are present`() {
        assertEquals(ControllerButton.entries.toSet(), layout.buttons.keys)
    }

    @Test
    fun `controls fit inside the screen outside cutout strips`() {
        controls.forEach { (name, rect) ->
            assertTrue("$name left", rect.left >= safeLeft)
            assertTrue("$name top", rect.top >= safeTop)
            assertTrue("$name right", rect.right <= safeRight)
            assertTrue("$name bottom", rect.bottom <= safeBottom)
            assertTrue("$name has positive size", rect.width > 0 && rect.height > 0)
        }
    }

    @Test
    fun `no controls overlap each other`() {
        val entries = controls.entries.toList()
        entries.forEachIndexed { index, first ->
            entries.drop(index + 1).forEach { second ->
                assertFalse("${first.key} overlaps ${second.key} on $device", first.value.overlaps(second.value))
            }
        }
    }

    @Test
    fun `round buttons stay round`() {
        layout.buttons.filterValues { it.shape == ButtonShape.Circle }.forEach { (button, geometry) ->
            assertEquals("$button is not a circle", geometry.bounds.width, geometry.bounds.height, PIXEL)
        }
        assertEquals("exit is not a circle", layout.exit.bounds.width, layout.exit.bounds.height, PIXEL)
    }

    @Test
    fun `the two clusters mirror each other across the safe area`() {
        MIRRORED.forEach { (left, right) ->
            val l = layout.buttons.getValue(left).bounds
            val r = layout.buttons.getValue(right).bounds
            assertEquals("$left / $right inset from their edges", l.left - safeLeft, safeRight - r.right, PIXEL)
            assertEquals("$left / $right height", l.top, r.top, PIXEL)
            assertEquals("$left / $right size", l.width, r.width, PIXEL)
        }
    }

    @Test
    fun `badge anchor sits on the outline at the top right`() {
        layout.buttons.forEach { (button, geometry) ->
            val anchor = geometry.badgeAnchor
            val bounds = geometry.bounds
            assertTrue("$button badge outside its button", bounds.contains(anchor))
            assertTrue("$button badge not in the top-right quarter", anchor.x > bounds.center.x && anchor.y < bounds.center.y)
            if (geometry.shape == ButtonShape.Circle) {
                assertEquals("$button badge off its circle", bounds.width / 2f, (anchor - bounds.center).getDistance(), 0.5f)
            }
        }
    }

    @Test
    fun `every rectangle edge is a whole pixel`() {
        controls.forEach { (name, rect) ->
            listOf(rect.left, rect.top, rect.right, rect.bottom).forEach { edge ->
                assertEquals("$name edge $edge", edge.toInt().toFloat(), edge, 0f)
            }
        }
    }

    /**
     * Wide enough: the layout is the one every phone has always had — 1 % of the height per unit, from the top of
     * the safe area — so the controller still reaches from the touchpad 3 units down to L3/R3 and the PS button
     * 92.5 units down. Narrower: the same block, scaled down to fit the width, and centred vertically.
     */
    @Test
    fun `the controller fills the height when the window is wide enough, and is centred when it is not`() {
        val usableHeight = safeBottom - safeTop
        val top = controls.values.minOf { it.top }
        val bottom = controls.values.maxOf { it.bottom }
        if (device.isWideEnough) {
            assertEquals("top of the controller", safeTop + usableHeight * TOP_UNITS / 100f, top, PIXEL)
            assertEquals("bottom of the controller", safeTop + usableHeight * BOTTOM_UNITS / 100f, bottom, PIXEL)
        } else {
            val unit = (bottom - top) / (BOTTOM_UNITS - TOP_UNITS)
            val frameTop = top - TOP_UNITS * unit
            val frameBottom = bottom + (100f - BOTTOM_UNITS) * unit
            assertEquals("space above vs below the controller", frameTop - safeTop, safeBottom - frameBottom, 2 * PIXEL)
            assertTrue("the controller is not stretched past the width", (safeRight - safeLeft) / unit >= MIN_WIDTH_UNITS - 1)
        }
    }

    /** One window shape, in px, landscape unless named otherwise. */
    data class Device(val name: String, val width: Float, val height: Float, val insets: EdgeInsets = EdgeInsets.Zero) {
        /** Multiplied out rather than divided, so a window of exactly 1.7:1 is not decided by a rounding error. */
        val isWideEnough: Boolean
            get() = (width - insets.left - insets.right) * 100f >= MIN_WIDTH_UNITS * (height - insets.top - insets.bottom)

        override fun toString() = name
    }

    companion object {
        /** Rounding to whole pixels moves an edge by at most this much. */
        private const val PIXEL = 1f

        /** The touchpad's top edge and the PS button's bottom edge, in units of 1 % of the frame's height. */
        private const val TOP_UNITS = 3f
        private const val BOTTOM_UNITS = 92.5f

        /** Mirrors `ControllerGeometry.MIN_WIDTH_UNITS`: kept apart on purpose, so a change there is a decision here. */
        private const val MIN_WIDTH_UNITS = 170f

        private val MIRRORED = listOf(
            ControllerButton.L2 to ControllerButton.R2,
            ControllerButton.L1 to ControllerButton.R1,
            ControllerButton.DPAD_UP to ControllerButton.TRIANGLE,
            ControllerButton.DPAD_LEFT to ControllerButton.CIRCLE,
            ControllerButton.DPAD_RIGHT to ControllerButton.SQUARE,
            ControllerButton.DPAD_DOWN to ControllerButton.CROSS,
            ControllerButton.L3 to ControllerButton.R3,
            ControllerButton.CREATE to ControllerButton.OPTIONS,
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun devices(): List<Device> = listOf(
            // Phones, landscape — the supported case (D1).
            Device("SM-A165F 19.5:9", 2_340f, 1_080f),
            Device("SM-A165F, cutout left (wm: 100 px)", 2_340f, 1_080f, EdgeInsets(100f, 0f, 0f, 0f)),
            Device("SM-A165F, cutout right", 2_340f, 1_080f, EdgeInsets(0f, 0f, 100f, 0f)),
            Device("20:9 FHD+", 2_400f, 1_080f),
            Device("20:9 punch hole", 2_400f, 1_080f, EdgeInsets(110f, 0f, 0f, 0f)),
            Device("16:9 FHD", 1_920f, 1_080f),
            Device("20:9 HD+", 1_600f, 720f),
            Device("18:9 HD+ (Android Go)", 1_440f, 720f),
            Device("18:9 FWVGA+ (Android Go)", 960f, 480f),
            Device("16:9 WVGA", 854f, 480f),
            Device("21:9 FHD+", 2_520f, 1_080f),
            Device("22:9 flip, main screen", 2_640f, 1_080f),
            Device("19.3:9 QHD+", 3_088f, 1_440f),
            Device("cutouts on both short edges", 2_400f, 1_080f, EdgeInsets(100f, 0f, 100f, 0f)),
            Device("cutout on a long edge", 2_400f, 1_080f, EdgeInsets(0f, 80f, 0f, 0f)),
            Device("21:9 foldable cover screen", 2_376f, 968f),
            // Phones in a window that is not the whole landscape screen.
            Device("foldable inner screen", 2_176f, 1_812f),
            Device("foldable inner screen, portrait (Android 16 ignores the lock)", 1_812f, 2_176f),
            Device("Pixel Fold inner screen", 2_208f, 1_840f),
            Device("split screen, half of 19.5:9", 1_160f, 1_080f),
            Device("split screen, a third of 19.5:9", 770f, 1_080f),
            Device("freeform window", 1_200f, 900f),
            Device("exactly 1.7:1", 1_836f, 1_080f),
            // Out of scope (D1) — but a tablet must still get a usable controller, not a broken one.
            Device("tablet 16:10", 2_560f, 1_600f),
            Device("tablet 4:3", 2_048f, 1_536f),
            Device("tablet portrait", 1_600f, 2_560f),
        )
    }
}

/** Pure arithmetic on [ControllerGeometry], one case at a time. */
class ControllerGeometryScalingTest {

    @Test
    fun `a phone wide enough gets exactly the full-height layout`() {
        val reference = ControllerGeometry.layout(2_340f, 1_080f)
        val unit = 1_080f / 100f
        val ps = reference.buttons.getValue(ControllerButton.PS).bounds
        assertEquals(11f * unit, ps.width, 1f)
        assertEquals(87f * unit, ps.center.y, 1f)
    }

    @Test
    fun `a narrower window scales every control by the same factor`() {
        val wide = ControllerGeometry.layout(2_340f, 1_080f)
        val narrow = ControllerGeometry.layout(1_160f, 1_080f)
        val ratios = ControllerButton.entries.map { button ->
            narrow.buttons.getValue(button).bounds.width / wide.buttons.getValue(button).bounds.width
        }
        assertTrue("controls scaled unevenly: $ratios", ratios.all { abs(it - ratios.first()) < 0.05f })
        assertTrue("controls did not shrink: $ratios", ratios.first() < 1f)
    }

    @Test
    fun `the same window always gives the same layout`() {
        assertEquals(ControllerGeometry.layout(1_200f, 900f), ControllerGeometry.layout(1_200f, 900f))
    }
}
