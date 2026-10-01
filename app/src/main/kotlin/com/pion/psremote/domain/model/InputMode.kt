package com.pion.psremote.domain.model

/**
 * How the buttons of one step must be pressed — the `inputMode` field (confirm.md Q8).
 *
 * The README requires three modes but its JSON had no field to tell them apart; this is that field.
 * A step without it is [SEQUENCE], which is what every step in the README's example means.
 */
enum class InputMode {
    /** Press the buttons one at a time, in array order. A repeated id needs a release and a new press. */
    SEQUENCE,

    /** Every button down at the same moment. Completes on the press that makes the set whole; no hold time (Q10). */
    SIMULTANEOUS,

    /** Press every button once, in any order. A repeated id must be pressed that many times. */
    ANY_ORDER,
    ;

    companion object {
        val DEFAULT = SEQUENCE

        fun fromId(id: String): InputMode? = entries.firstOrNull { it.name == id }
    }
}
