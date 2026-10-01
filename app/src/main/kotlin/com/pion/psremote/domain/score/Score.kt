package com.pion.psremote.domain.score

/**
 * One run's score. Immutable, like `StepProgress`: every award returns a new value, so it sits on screen state as-is.
 *
 * Only [awards] and [stepCount] are stored; everything else is derived from them, so no two fields can disagree
 * (rules R6).
 */
data class Score(
    /** One tier per completed step, in the order the steps were done. */
    val awards: List<ScoreTier> = emptyList(),
    /** Steps in the script: what [maxPoints] is out of. */
    val stepCount: Int = 0,
) {
    val points: Int
        get() = awards.sumOf { it.points }

    val maxPoints: Int
        get() = stepCount * ScoreTier.PERFECT.points

    fun count(tier: ScoreTier): Int = awards.count { it == tier }

    fun award(tier: ScoreTier): Score = copy(awards = awards + tier)
}
