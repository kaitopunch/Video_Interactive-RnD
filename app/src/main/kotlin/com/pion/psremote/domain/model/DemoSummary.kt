package com.pion.psremote.domain.model

/**
 * One entry in the home list: a game in the catalogue the user can open. Nothing about it has been validated —
 * a game with a broken script is still listed, and opening it shows the BA every violation (confirm.md Q12).
 */
data class DemoSummary(
    /** The catalogue entry's id, and the argument the demo screen is opened with. */
    val id: String,
    /** The entry's `name`, as the CMS spells it (confirm.md H4). */
    val title: String,
)
