package com.pion.psremote.domain.model

/** The raw parts of one demo, as the repository found them. Nothing here has been validated yet. */
data class DemoSource(
    /** A URI Media3 can open: the catalogue entry's `source_vid`, an `https://` URL. */
    val videoUri: String,
    val videoDurationMs: Long,
    val scriptJson: String,
)

/** A demo after its script was checked: either playable, or the full list of what is wrong with it. */
sealed interface DemoLoad {

    /** [steps] are sorted by `step_sequence`, which is also timeline order once validated. */
    data class Ready(val videoUri: String, val steps: List<TutorialStep>) : DemoLoad

    data class Invalid(val violations: List<ScriptViolation>) : DemoLoad
}
