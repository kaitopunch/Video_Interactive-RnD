package com.pion.psremote.data.catalogue

import com.pion.psremote.domain.model.DemoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Home shows from the catalogue (confirm.md H4). */
class CatalogueItemTest {

    @Test
    fun `games are listed by priority, lowest first`() {
        val items = listOf(item("b", priority = 2), item("c", priority = 3), item("a", priority = 1))

        assertEquals(listOf("a", "b", "c"), items.toSummaries().map { it.id })
    }

    @Test
    fun `a hidden game is not listed`() {
        val items = listOf(item("shown"), item("hidden", status = false))

        assertEquals(listOf("shown"), items.toSummaries().map { it.id })
    }

    @Test
    fun `equal priorities keep the server's order, and a game with none goes last`() {
        val items = listOf(item("none", priority = null), item("second"), item("third"), item("first", priority = 0))

        assertEquals(listOf("first", "second", "third", "none"), items.toSummaries().map { it.id })
    }

    @Test
    fun `the title is the name as the CMS spells it`() {
        assertEquals(listOf(DemoSummary("id", "Spider Man")), listOf(item("id", name = "Spider Man")).toSummaries())
    }

    @Test
    fun `a blank name falls back to the id, so no card is empty`() {
        assertEquals("id", item("id", name = "  ").title)
    }

    private fun item(id: String, name: String = id, priority: Int? = 1, status: Boolean = true) =
        CatalogueItem(id = id, name = name, priority = priority ?: Int.MAX_VALUE, status = status)
}
