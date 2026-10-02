package com.pion.psremote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A constant's name IS the id a script uses. Renaming one compiles, passes every other suite, and breaks every
 * script in the CMS that names the old id — on the error screen, after release.
 */
class ControllerButtonTest {

    @Test
    fun `the ids scripts use are exactly these eighteen`() {
        assertEquals(
            setOf(
                "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "CROSS", "CIRCLE", "SQUARE", "TRIANGLE",
                "L1", "L2", "R1", "R2", "L3", "R3", "OPTIONS", "CREATE", "PS", "TOUCHPAD",
            ),
            ControllerButton.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `every id reads back as its button`() {
        ControllerButton.entries.forEach { assertEquals(it, ControllerButton.fromId(it.name)) }
    }

    @Test
    fun `an id is matched exactly, so case, padding and blanks are not ids`() {
        listOf("cross", "Cross", " CROSS", "CROSS ", "", " ", "X", "DPAD-UP", "L 1").forEach { id ->
            assertNull("'$id'", ControllerButton.fromId(id))
        }
    }

    /**
     * The BA writes scripts from that table: an id missing there is a button nobody uses, an extra one an error.
     * Skipped where `docs/` is absent — it is not in git (`.gitignore`), so a clean checkout has no table to check.
     */
    @Test
    fun `the script format doc lists exactly the ids the app reads`() {
        val doc = File("../docs/demo-script-format.md")
        assumeTrue("${doc.path} is not in this checkout", doc.exists())
        val section = doc.readText()
            .substringAfter("## ID các nút")
            .substringBefore("\n## ")

        assertEquals(
            ControllerButton.entries.map { it.name }.toSet(),
            Regex("`([A-Z0-9_]+)`").findAll(section).map { it.groupValues[1] }.toSet(),
        )
    }
}
