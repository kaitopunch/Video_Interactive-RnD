package com.pion.psremote.domain.input

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.ControllerButton.*
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.TutorialStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepProgressTest {
    @Test
    fun `sequence lets only the next button be pressed and completes in order`() {
        val initial = start(InputMode.SEQUENCE, DPAD_UP, CROSS, R1)
        assertEquals(setOf(DPAD_UP), initial.activeButtons)
        assertTrue((0..2).none(initial::isDone))

        val first = initial.press(DPAD_UP)
        assertEquals(setOf(CROSS), first.activeButtons)
        assertTrue(first.isDone(0))
        assertFalse(first.isDone(1))
        assertFalse(first.isComplete)

        val second = first.press(CROSS)
        assertEquals(setOf(R1), second.activeButtons)
        assertTrue(second.isDone(1))
        assertFalse(second.isDone(2))

        val complete = second.press(R1)
        assertTrue(complete.isComplete)
        assertTrue((0..2).all(complete::isDone))
        assertTrue(complete.activeButtons.isEmpty())
        assertTrue((0..2).none(initial::isDone))
    }

    @Test
    fun `sequence keeps every button still to come pending and numbered`() {
        val initial = start(InputMode.SEQUENCE, DPAD_UP, CROSS, R1)
        assertEquals(setOf(DPAD_UP, CROSS, R1), initial.pendingButtons)
        assertEquals(listOf(1, 2, 3), listOf(DPAD_UP, CROSS, R1).map(initial::nextPositionOf))

        val first = initial.press(DPAD_UP)
        assertEquals(setOf(CROSS, R1), first.pendingButtons)
        assertNull(first.nextPositionOf(DPAD_UP))
        assertEquals(2, first.nextPositionOf(CROSS))
        assertTrue(first.press(CROSS).press(R1).pendingButtons.isEmpty())
    }

    @Test
    fun `a repeated sequence button shows its first place not yet done`() {
        val initial = start(InputMode.SEQUENCE, R2, CROSS, R2)
        assertEquals(1, initial.nextPositionOf(R2))
        assertEquals(setOf(R2), initial.activeButtons)

        val first = initial.press(R2)
        assertEquals(3, first.nextPositionOf(R2))
        assertEquals(setOf(R2, CROSS), first.pendingButtons)
        assertEquals(setOf(CROSS), first.activeButtons)
    }

    @Test
    fun `wrong sequence press keeps existing progress`() {
        val progress = start(InputMode.SEQUENCE, DPAD_UP, CROSS, R1).press(DPAD_UP)

        assertEquals(progress, progress.press(R1))
    }

    @Test
    fun `repeated sequence target needs two presses`() {
        val first = start(InputMode.SEQUENCE, CROSS, CROSS).press(CROSS)

        assertFalse(first.isComplete)
        assertTrue(first.isDone(0))
        assertFalse(first.isDone(1))
        assertEquals(2, first.nextPositionOf(CROSS))
        assertEquals(setOf(CROSS), first.activeButtons)
        assertTrue(first.press(CROSS).isComplete)
    }

    @Test
    fun `any order completes in a different order and shrinks active buttons`() {
        val initial = start(InputMode.ANY_ORDER, SQUARE, TRIANGLE, CIRCLE)
        assertEquals(setOf(SQUARE, TRIANGLE, CIRCLE), initial.activeButtons)

        val first = initial.press(CIRCLE)
        assertEquals(setOf(SQUARE, TRIANGLE), first.activeButtons)
        assertEquals(first.activeButtons, first.pendingButtons)
        assertTrue(first.isDone(2))
        assertFalse(first.isDone(0))

        val second = first.press(SQUARE)
        assertEquals(setOf(TRIANGLE), second.activeButtons)
        assertFalse(second.isComplete)
        val complete = second.press(TRIANGLE)
        assertTrue(complete.isComplete)
        assertTrue(complete.activeButtons.isEmpty())
    }

    @Test
    fun `pressing a done any order button changes nothing`() {
        val progress = start(InputMode.ANY_ORDER, SQUARE, TRIANGLE, CIRCLE).press(CIRCLE)

        assertEquals(progress, progress.press(CIRCLE))
    }

    @Test
    fun `repeated any order target needs two presses`() {
        val first = start(InputMode.ANY_ORDER, CROSS, CROSS, R1).press(R1).press(CROSS)

        assertFalse(first.isComplete)
        assertTrue(first.isDone(0))
        assertFalse(first.isDone(1))
        assertEquals(setOf(CROSS), first.activeButtons)
        assertTrue(first.press(CROSS).isComplete)
    }

    @Test
    fun `remaining presses count down for a repeated any order button`() {
        val initial = start(InputMode.ANY_ORDER, CROSS, R1, CROSS)
        assertEquals(2, initial.remainingPresses(CROSS))
        assertEquals(1, initial.remainingPresses(R1))

        val first = initial.press(CROSS)
        assertEquals(1, first.remainingPresses(CROSS))
        assertEquals(0, first.press(CROSS).remainingPresses(CROSS))
    }

    @Test
    fun `simultaneous combination requires overlapping presses`() {
        val initial = start(InputMode.SIMULTANEOUS, L1, R1)
        val left = initial.press(L1)
        val released = left.release(L1)
        val right = released.press(R1)
        val complete = right.press(L1)

        assertTrue(left.isDone(0))
        assertFalse(released.isDone(0))
        assertFalse(right.isComplete)
        assertTrue(complete.isComplete)
        listOf(initial, left, released, right, complete).forEach {
            assertEquals(setOf(L1, R1), it.activeButtons)
            assertEquals(setOf(L1, R1), it.pendingButtons)
        }
    }

    @Test
    fun `releasing a nonheld simultaneous button changes nothing`() {
        val progress = start(InputMode.SIMULTANEOUS, L1, R1).press(L1)

        assertEquals(progress, progress.release(R1))
    }

    @Test
    fun `sequence ignores release`() {
        val progress = start(InputMode.SEQUENCE, CROSS, R1).press(CROSS)

        assertEquals(progress, progress.release(CROSS))
    }

    @Test
    fun `any order ignores release`() {
        val progress = start(InputMode.ANY_ORDER, CROSS, R1).press(CROSS)

        assertEquals(progress, progress.release(CROSS))
    }

    /** The screen hides a finished step at once, but a press already queued behind the completing one still lands. */
    @Test
    fun `a finished step stays finished whatever is pressed after it`() {
        listOf(InputMode.SEQUENCE, InputMode.ANY_ORDER).forEach { mode ->
            val done = start(mode, CROSS, R1).press(CROSS).press(R1)

            assertTrue("$mode", done.isComplete)
            assertEquals("$mode", done, done.press(CROSS).press(R1).press(TRIANGLE))
            assertEquals("$mode", emptySet<ControllerButton>(), done.activeButtons)
            assertEquals("$mode", emptySet<ControllerButton>(), done.pendingButtons)
        }
    }

    @Test
    fun `pressing a held simultaneous button again, or one outside the combination, changes nothing`() {
        val held = start(InputMode.SIMULTANEOUS, L1, R1).press(L1)

        assertEquals(held, held.press(L1))
        assertEquals(held, held.press(CROSS))
        assertFalse(held.press(CROSS).isComplete)
    }

    @Test
    fun `a button the step does not name has no place and owes no press`() {
        val progress = start(InputMode.SEQUENCE, CROSS, R1)

        assertNull(progress.nextPositionOf(TRIANGLE))
        assertEquals(0, progress.remainingPresses(TRIANGLE))
    }

    private fun start(mode: InputMode, vararg targets: ControllerButton) =
        StepProgress.start(TutorialStep(1, 34_000, targets.toList(), mode, 0.25, 3_000))
}
