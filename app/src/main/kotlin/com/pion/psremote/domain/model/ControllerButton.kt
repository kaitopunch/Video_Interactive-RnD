package com.pion.psremote.domain.model

/**
 * Every button the on-screen controller draws and a demo script may name (confirm.md Q2).
 *
 * The constant name IS the id used in `targetButtonIds`, case-sensitive: `"CROSS"`, never `"cross"`.
 * Analog sticks are out of scope because a script only describes presses; `L3`/`R3` are the stick
 * clicks, which are presses.
 */
enum class ControllerButton {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    CROSS,
    CIRCLE,
    SQUARE,
    TRIANGLE,
    L1,
    L2,
    R1,
    R2,
    L3,
    R3,
    OPTIONS,
    CREATE,
    PS,
    TOUCHPAD,
    ;

    companion object {
        fun fromId(id: String): ControllerButton? = entries.firstOrNull { it.name == id }
    }
}
