package com.pion.psremote.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
    fun everyBundledGameIsACardThatOpensIt() {
        show(HomePhase.Ready(listOf(DemoSummary.fromId("sample"), DemoSummary.fromId("spiderman"))))

        compose.onNodeWithText("Spiderman").performClick()
        compose.onNodeWithText("Sample").performClick()

        assertEquals(listOf(HomeIntent.DemoClicked("spiderman"), HomeIntent.DemoClicked("sample")), intents)
    }

    @Test
    fun loadingIsAnnouncedNotJustDrawn() {
        show(HomePhase.Loading)

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(string(R.string.home_loading))))
            .assertIsDisplayed()
    }

    @Test
    fun noBundledGameSaysHowToAddOne() {
        show(HomePhase.Empty)

        compose.onNodeWithText(string(R.string.home_empty)).assertIsDisplayed()
    }

    @Test
    fun anUnreadableListSaysSo() {
        show(HomePhase.Failed(AppError.NotFound("demos")))

        compose.onNodeWithText(string(R.string.home_failed)).assertIsDisplayed()
    }

    /** A title is a folder name (LLM.md §11 #5), and a long one must not push the card's layout around. */
    @Test
    fun aLongGameTitleStaysOnOneLine() {
        val title = DemoSummary.fromId("marvels-spider-man-2-ultimate-edition-remastered-collection").title
        show(HomePhase.Ready(listOf(DemoSummary("long", title))))

        assertEquals(1, compose.onNodeWithText(title).textLayout().lineCount)
    }

    @Test
    fun aLongListScrollsToItsLastGame() {
        val demos = List(GAMES) { DemoSummary.fromId("game-${it + 1}") }
        show(HomePhase.Ready(demos))

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(GAMES - 1)
        compose.onNodeWithText(demos.last().title).assertIsDisplayed().performClick()

        assertEquals(listOf(HomeIntent.DemoClicked("game-$GAMES")), intents)
    }

    private companion object {
        const val GAMES = 30
    }
}
