package com.pion.psremote.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * English and Vietnamese ship side by side (Q14), and a device picks one by its locale. A string missing from one
 * file shows that language's users the other's text; a format argument missing from one throws
 * `MissingFormatArgumentException` the moment the message is shown — on that locale's devices only, and only for
 * the BA script error it belongs to, which is exactly where nobody looks before release.
 */
class StringResourcesTest {

    private val english = strings("values")
    private val vietnamese = strings("values-vi")

    @Test
    fun `every string exists in both languages`() {
        assertEquals("only in English", emptySet<String>(), english.keys - vietnamese.keys)
        assertEquals("only in Vietnamese", emptySet<String>(), vietnamese.keys - english.keys)
    }

    @Test
    fun `both languages take the same format arguments`() {
        english.forEach { (name, text) ->
            assertEquals("$name: format arguments differ", arguments(text), arguments(vietnamese.getValue(name)))
        }
    }

    @Test
    fun `every format argument is numbered, so a translation may reorder them`() {
        (english + vietnamese.mapKeys { "vi/${it.key}" }).forEach { (name, text) ->
            assertTrue("$name: unnumbered argument in '$text'", UNNUMBERED.find(text) == null)
        }
    }

    @Test
    fun `no string is empty`() {
        (english + vietnamese.mapKeys { "vi/${it.key}" }).forEach { (name, text) ->
            assertTrue("$name is blank", text.isNotBlank())
        }
    }

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    /** `%1$d` → `1$d`: position and conversion, sorted, so the same arguments in another order still match. */
    private fun arguments(text: String): List<String> = NUMBERED.findAll(text).map { it.value.drop(1) }.sorted().toList()

    private companion object {
        val NUMBERED = Regex("""%\d+\$[a-zA-Z]""")
        val UNNUMBERED = Regex("""%(?!\d+\$)(?!%)[a-zA-Z]""")
    }
}
