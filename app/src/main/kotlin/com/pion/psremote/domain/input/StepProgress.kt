package com.pion.psremote.domain.input

import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.TutorialStep

/**
 * How far the user has got through one step's input. Immutable: every press returns a new value, so
 * it can sit on screen state as-is.
 *
 * A press is counted on touch-down (D4). Only [activeButtons] can make progress, and the screen lets
 * nothing else be pressed while a tutorial is up (confirm.md Q9, D2) — so a wrong press never needs
 * undoing: it is ignored here as a second line of defence, and progress never goes backwards.
 * Progress is also kept when the video stops at the stop point (requirements.md §6).
 *
 * A fresh value is started when the step's tutorial appears, so a button already held down before
 * that moment counts for nothing until it is released and pressed again (requirements.md §7 "bấm sớm").
 */
data class StepProgress(
    val targets: List<ControllerButton>,
    val mode: InputMode,
    /** Indices of [targets] already satisfied. SEQUENCE and ANY_ORDER only. */
    private val pressedIndices: Set<Int> = emptySet(),
    /** Buttons down right now. SIMULTANEOUS only: there, progress is what is held, not what was pressed. */
    private val held: Set<ControllerButton> = emptySet(),
) {
    fun isDone(index: Int): Boolean = when (mode) {
        InputMode.SIMULTANEOUS -> targets[index] in held
        InputMode.SEQUENCE, InputMode.ANY_ORDER -> index in pressedIndices
    }

    val isComplete: Boolean
        get() = targets.indices.all(::isDone)

    /** The buttons that may be pressed now: lit and touchable. Everything else is blocked. */
    val activeButtons: Set<ControllerButton>
        get() = when (mode) {
            InputMode.SEQUENCE -> nextSequenceIndex()?.let { setOf(targets[it]) } ?: emptySet()
            InputMode.ANY_ORDER -> pendingButtons
            InputMode.SIMULTANEOUS -> targets.toSet()
        }

    /**
     * Every button the step still needs, so the whole of a SEQUENCE is lit from the start and the user
     * sees what comes after the next press. A superset of [activeButtons]: the extra ones are shown but
     * stay blocked until their turn, so a press out of order is still impossible (Q9).
     */
    val pendingButtons: Set<ControllerButton>
        get() = when (mode) {
            InputMode.SIMULTANEOUS -> targets.toSet()
            InputMode.SEQUENCE, InputMode.ANY_ORDER -> targets.filterIndexed { index, _ -> index !in pressedIndices }.toSet()
        }

    /**
     * 1-based place in [targets] of [button]'s first occurrence not yet done, or null when none is left.
     * In `["R2", "CROSS", "R2"]`, R2 is 1 until it is pressed, then 3.
     */
    fun nextPositionOf(button: ControllerButton): Int? =
        targets.indices.firstOrNull { it !in pressedIndices && targets[it] == button }?.plus(1)

    /** Presses [button] still owes: 2 for a fresh `["CROSS", "CROSS"]`. SEQUENCE and ANY_ORDER only. */
    fun remainingPresses(button: ControllerButton): Int =
        targets.indices.count { it !in pressedIndices && targets[it] == button }

    fun press(button: ControllerButton): StepProgress = when (mode) {
        InputMode.SEQUENCE -> nextSequenceIndex()
            ?.takeIf { targets[it] == button }
            ?.let { copy(pressedIndices = pressedIndices + it) }
            ?: this
        // The first unsatisfied slot for this id, so ["CROSS", "CROSS"] needs two separate presses.
        InputMode.ANY_ORDER -> targets.indices
            .firstOrNull { it !in pressedIndices && targets[it] == button }
            ?.let { copy(pressedIndices = pressedIndices + it) }
            ?: this
        InputMode.SIMULTANEOUS -> if (button in targets) copy(held = held + button) else this
    }

    /** Only SIMULTANEOUS cares: letting go of one button breaks the combination. */
    fun release(button: ControllerButton): StepProgress = when (mode) {
        InputMode.SIMULTANEOUS -> if (button in held) copy(held = held - button) else this
        InputMode.SEQUENCE, InputMode.ANY_ORDER -> this
    }

    private fun nextSequenceIndex(): Int? = targets.indices.firstOrNull { it !in pressedIndices }

    companion object {
        fun start(step: TutorialStep): StepProgress = StepProgress(step.targets, step.inputMode)
    }
}
