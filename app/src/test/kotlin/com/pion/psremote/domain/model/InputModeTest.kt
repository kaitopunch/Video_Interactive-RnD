package com.pion.psremote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Like `ControllerButton`, a mode's name is the `inputMode` value a script carries (confirm.md Q8). */
class InputModeTest {

    @Test
    fun `a step without a mode is a sequence, as every step in the README's example means`() {
        assertEquals(InputMode.SEQUENCE, InputMode.DEFAULT)
    }

    @Test
    fun `the mode ids scripts use are exactly these three`() {
        assertEquals(setOf("SEQUENCE", "SIMULTANEOUS", "ANY_ORDER"), InputMode.entries.map { it.name }.toSet())
        InputMode.entries.forEach { assertEquals(it, InputMode.fromId(it.name)) }
    }

    @Test
    fun `a mode id is matched exactly`() {
        listOf("sequence", "Any_Order", "ANY ORDER", "ANYORDER", "SIMULTANEOUS ", "", "HOLD").forEach { id ->
            assertNull("'$id'", InputMode.fromId(id))
        }
    }
}
