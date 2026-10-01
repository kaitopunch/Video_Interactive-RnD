package com.pion.psremote.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination, as a type (LLM.md §7). An argument that does not compile cannot be passed, and the
 * back stack — arguments included — survives process death.
 */
sealed interface Route {

    /** The game picker, and the start destination. */
    @Serializable
    data object Home : Route

    /** [demoId] is a folder under `assets/demos/`. */
    @Serializable
    data class Demo(val demoId: String) : Route
}
