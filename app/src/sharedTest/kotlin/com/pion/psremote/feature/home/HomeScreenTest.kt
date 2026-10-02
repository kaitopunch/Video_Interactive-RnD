package com.pion.psremote.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pion.psremote.R
import com.pion.psremote.core.common.AppError
import com.pion.psremote.core.ui.theme.PsRemoteTheme
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.testing.string
import com.pion.psremote.testing.textLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The game picker in each of its phases, composed for real. Runs on the JVM and on a phone (LLM.md §9). */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<HomeIntent>()

    private fun show(phase: HomePhase) {
        compose.setContent { PsRemoteTheme { HomeScreen(HomeState(phase), onIntent = { intents += it }) } }
    }

    @Test
    fun everyListedGameIsACardThatOpensIt() {
        show(HomePhase.Ready(listOf(SAMPLE, SPIDER_MAN)))

        compose.onNodeWithText("Spider Man").performClick()
        compose.onNodeWithText("Sample").performClick()

        assertEquals(listOf(HomeIntent.DemoClicked(SPIDER_MAN.id), HomeIntent.DemoClicked(SAMPLE.id)), intents)
    }

    @Test
    fun loadingIsAnnouncedNotJustDrawn() {
        show(HomePhase.Loading)

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(string(R.string.home_loading))))
            .assertIsDisplayed()
    }

    @Test
    fun anEmptyCatalogueSaysSoAndOffersRetry() {
        show(HomePhase.Empty)

        compose.onNodeWithText(string(R.string.home_empty)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.action_retry)) and hasClickAction()).performClick()

        assertEquals(listOf(HomeIntent.RetryClicked), intents)
    }

    @Test
    fun aFailedFetchSaysSoAndOffersRetry() {
        show(HomePhase.Failed(AppError.Network("UnknownHostException")))

        compose.onNodeWithText(string(R.string.home_failed)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.action_retry)) and hasClickAction()).performClick()

        assertEquals(listOf(HomeIntent.RetryClicked), intents)
    }

    /** A title is whatever the CMS holds, and a long one must not push the card's layout around. */
    @Test
    fun aLongGameTitleStaysOnOneLine() {
        val title = "Marvel's Spider-Man 2 Ultimate Edition Remastered Collection"
        show(HomePhase.Ready(listOf(DemoSummary("long", title))))

        assertEquals(1, compose.onNodeWithText(title).textLayout().lineCount)
    }

    @Test
    fun aLongListScrollsToItsLastGame() {
        val demos = List(GAMES) { DemoSummary("game-${it + 1}", "Game ${it + 1}") }
        show(HomePhase.Ready(demos))

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(GAMES - 1)
        compose.onNodeWithText(demos.last().title).assertIsDisplayed().performClick()

        assertEquals(listOf(HomeIntent.DemoClicked("game-$GAMES")), intents)
    }

    private companion object {
        const val GAMES = 30
        val SAMPLE = DemoSummary("6ef63378-d525-4633-a244-2ea68bf3ef19", "Sample")
        val SPIDER_MAN = DemoSummary("371ed659-b6b5-436c-b9ad-ad41da4ae797", "Spider Man")
    }
}
