package com.pion.psremote.domain.model

/**
 * One entry in the home list: a bundled demo the user can open. Nothing about it has been validated —
 * a demo with a broken script is still listed, and opening it shows the BA every violation (confirm.md Q12).
 */
data class DemoSummary(
    /** The folder name under `assets/demos/`, and the argument the demo screen is opened with. */
    val id: String,
    val title: String,
) {
    companion object {

        /**
         * The title is the folder name, so adding a game is dropping a folder — no list to keep in step
         * with it. `god-of-war` reads "God Of War"; `spiderman` reads "Spiderman".
         */
        fun fromId(id: String): DemoSummary {
            val title = id.split('-', '_')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
            return DemoSummary(id, title.ifEmpty { id })
        }
    }
}
