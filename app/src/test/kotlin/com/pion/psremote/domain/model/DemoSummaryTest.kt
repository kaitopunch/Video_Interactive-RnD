package com.pion.psremote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSummaryTest {

    @Test
    fun `the folder name becomes the title, one capitalised word per part`() {
        assertEquals("Spiderman", DemoSummary.fromId("spiderman").title)
        assertEquals("God Of War", DemoSummary.fromId("god-of-war").title)
        assertEquals("Gran Turismo 7", DemoSummary.fromId("gran_turismo_7").title)
    }

    @Test
    fun `the id is kept exactly as the folder is named`() {
        assertEquals("god-of-war", DemoSummary.fromId("god-of-war").id)
    }

    @Test
    fun `doubled or edge separators leave no blank words`() {
        assertEquals("Ghost Of Tsushima", DemoSummary.fromId("-ghost--of_tsushima_").title)
    }

    @Test
    fun `a name of separators only falls back to the id`() {
        assertEquals("--", DemoSummary.fromId("--").title)
    }
}
