package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.ControllerButton.CREATE
import com.pion.psremote.domain.model.ControllerButton.CROSS
import com.pion.psremote.domain.model.ControllerButton.L1
import com.pion.psremote.domain.model.ControllerButton.L2
import com.pion.psremote.domain.model.ControllerButton.R1
import com.pion.psremote.domain.model.ControllerButton.R2
import com.pion.psremote.domain.model.ControllerButton.TRIANGLE
import com.pion.psremote.feature.demo.DemoIntent
import com.pion.psremote.feature.demo.DemoIntent.ButtonPressed
import com.pion.psremote.feature.demo.DemoIntent.ButtonReleased
import com.pion.psremote.testing.boundsOf
import com.pion.psremote.testing.centerOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a finger on the glass does to the controller — the part of the input path a ViewModel test cannot see
 * (MVI doc §7, "the sixth category"). Multi-touch cannot be driven by `adb shell input`, which is why it lives
 * here (LLM.md §9). Runs on the JVM and on a phone.
 */
@RunWith(AndroidJUnit4::class)
class ControllerOverlayTouchTest {

    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<DemoIntent>()
    private var enabled by mutableStateOf(ControllerButton.entries.toSet())

    @Before
    fun setUp() {
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val layout = remember(constraints) {
                    ControllerGeometry.layout(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                }
                ControllerOverlay(layout, enabled, onIntent = { intents += it })
            }
        }
    }

    @Test
    fun everyButtonIsOnScreenAndAnnouncedAsAButton() {
        ControllerButton.entries.forEach { button ->
            compose.onNodeWithContentDescription(button.name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun aTapIsOnePressAndOneRelease() {
        tap(CROSS)

        assertEquals(listOf(ButtonPressed(CROSS), ButtonReleased(CROSS)), intents)
    }

    @Test
    fun twoFingersHoldTwoButtonsAtTheSameTime() {
        val l1 = compose.centerOf(L1.name)
        val r1 = compose.centerOf(R1.name)

        compose.onRoot().performTouchInput {
            down(0, l1)
            down(1, r1)
            up(0)
            up(1)
        }

        assertEquals(listOf(ButtonPressed(L1), ButtonPressed(R1), ButtonReleased(L1), ButtonReleased(R1)), intents)
    }

    /** The agreed ceiling for a SIMULTANEOUS step (confirm.md Q11): every one of them is down before any lifts. */
    @Test
    fun fiveFingersHoldFiveButtonsAtTheSameTime() {
        val buttons = listOf(L1, R1, L2, R2, CROSS)
        val centers = buttons.map { compose.centerOf(it.name) }

        compose.onRoot().performTouchInput {
            centers.forEachIndexed { pointer, center -> down(pointer, center) }
            buttons.indices.forEach { pointer -> up(pointer) }
        }

        assertEquals(buttons.map(::ButtonPressed) + buttons.map(::ButtonReleased), intents)
    }

    /** F3: the touch area is the circle the spotlight cuts, not the square around it. */
    @Test
    fun aTouchInTheCornerOfARoundButtonsSquareMissesIt() {
        val square = compose.boundsOf(TRIANGLE.name)

        compose.onRoot().performTouchInput { click(square.topLeft + Offset(2f, 2f)) }
        assertEquals(emptyList<DemoIntent>(), intents)

        // Just inside the circle, at the same 45° corner.
        val inside = square.center + Offset(square.width * 0.3f, -square.height * 0.3f)
        compose.onRoot().performTouchInput { click(inside) }
        assertEquals(listOf(ButtonPressed(TRIANGLE), ButtonReleased(TRIANGLE)), intents)
    }

    /**
     * F6: Compose stretches anything under 48 dp to a 48 dp touch target. CREATE is about 27 dp tall even on a
     * 384 dp-tall phone, so a touch just above its pill — outside the hole the spotlight cuts — pressed it.
     */
    @Test
    fun aTouchJustOutsideASmallPillMissesIt() {
        val pill = compose.boundsOf(CREATE.name)

        compose.onRoot().performTouchInput {
            click(Offset(pill.center.x, pill.top - 3f))
            click(Offset(pill.center.x, pill.bottom + 3f))
            click(Offset(pill.left - 3f, pill.center.y))
        }

        assertEquals(emptyList<DemoIntent>(), intents)
    }

    /**
     * requirements.md §7 "bấm sớm": a finger already down when the button goes live counts for nothing until it
     * lifts.
     */
    @Test
    fun aBlockedButtonSwallowsTheTouchEvenWhenItGoesLiveUnderTheFinger() {
        enabled = ControllerButton.entries.toSet() - CROSS
        compose.waitForIdle()
        val cross = compose.centerOf(CROSS.name)

        compose.onRoot().performTouchInput { down(0, cross) }
        enabled = ControllerButton.entries.toSet()
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            moveTo(0, cross + Offset(1f, 1f))
            up(0)
        }
        assertEquals(emptyList<DemoIntent>(), intents)

        tap(CROSS)
        assertEquals(listOf(ButtonPressed(CROSS), ButtonReleased(CROSS)), intents)
    }

    /** D4: a repeated step needs a release and a new press, so two fingers on one button are one press. */
    @Test
    fun aSecondFingerOnTheSameButtonIsTheSamePress() {
        val cross = compose.centerOf(CROSS.name)

        compose.onRoot().performTouchInput {
            down(0, cross)
            down(1, cross + Offset(4f, 4f))
            up(0)
            up(1)
        }

        assertEquals(listOf(ButtonPressed(CROSS), ButtonReleased(CROSS)), intents)
    }

    @Test
    fun aFingerSlidingOffAButtonKeepsItPressedUntilItLifts() {
        val cross = compose.centerOf(CROSS.name)

        compose.onRoot().performTouchInput {
            down(0, cross)
            moveTo(0, Offset(1f, 1f))
            up(0)
        }

        assertEquals(listOf(ButtonPressed(CROSS), ButtonReleased(CROSS)), intents)
    }

    private fun tap(button: ControllerButton) {
        val center = compose.centerOf(button.name)
        compose.onRoot().performTouchInput { click(center) }
    }
}
