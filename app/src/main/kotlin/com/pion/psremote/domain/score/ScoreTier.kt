package com.pion.psremote.domain.score

import com.pion.psremote.domain.model.TutorialStep

/**
 * How well one step was done (docs/button-press-scoring-rules.md R2, R3). A step is scored once, on the press
 * that completes it — never per press: every press a tutorial lets through is a right one (confirm.md Q9), so a
 * count of presses is the same for every user who finishes.
 */
enum class ScoreTier(val points: Int) {
    /** Done while the video was still moving, slowed down. */
    PERFECT(100),

    /** Done after the video had stopped at the step's stop point and waited. */
    GOOD(50),
    ;

    companion object {
        /**
         * Decided by whether the video had stopped when the completing press was handled — not by a clock, which
         * would count time in the background and under the resume countdown against the user (D5, rules R4).
         *
         * A step that stops at once waits from its first frame, so it has no slow phase to beat: PERFECT, or its
         * full points could never be reached (rules R3).
         */
        fun of(step: TutorialStep, completedWhileWaiting: Boolean): ScoreTier =
            if (completedWhileWaiting && !step.stopsImmediately) GOOD else PERFECT
    }
}
