package com.pion.psremote.feature.demo.component

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import com.pion.psremote.R
import com.pion.psremote.core.ui.theme.PsRemoteTheme
import com.pion.psremote.domain.input.StepProgress
import com.pion.psremote.domain.model.ControllerButton
import com.pion.psremote.domain.model.InputMode
import com.pion.psremote.domain.model.TutorialStep
import com.pion.psremote.domain.score.Score
import com.pion.psremote.domain.score.ScoreTier
import com.pion.psremote.feature.demo.ActiveTutorial
import com.pion.psremote.feature.demo.DemoPhase
import com.pion.psremote.feature.demo.DemoScreen
import com.pion.psremote.feature.demo.DemoState
import com.pion.psremote.testing.boundsOf
import com.pion.psremote.testing.describeFit
import com.pion.psremote.testing.fitsItsBox
import com.pion.psremote.testing.string
import com.pion.psremote.testing.textLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real demo screen at the sizes, densities and font scales phones ship with: every control inside the screen,
 * no two touch areas overlapping, every printed label fitting its button — CREATE and OPTIONS are the shortest
 * pills — a tutorial's badge fitting its circle, the score fitting its corner in both languages, and the end panel's
 * buttons within reach. A user's "largest text" setting (up to 2× since Android 14) is the hardest case.
 *
 * One composition walks the whole matrix: the size is a `requiredSize`, the density and font scale a
 * `LocalDensity`, so it runs unchanged on the JVM and on a phone whose own screen is a different size.
 */
@RunWith(AndroidJUnit4::class)
class ControllerOnPhonesTest {

    @get:Rule
    val compose = createComposeRule()

    private var phone by mutableStateOf(PHONES.first())
    private var fontScale by mutableStateOf(1f)
    private var language by mutableStateOf(DEFAULT)

    @Test
    fun everyPhoneAndFontScaleGetsAWholeControllerWithReadableLabels() {
        onEveryPhoneAndFontScale(TUTORIAL, check = ::assertControllerIsWhole)
    }

    /**
     * The score's slot is a fixed size, so its longest caption and a four-digit score must shrink into it — in both
     * languages: "HOÀN HẢO" is longer than "PERFECT" and stacks two marks on its letters.
     */
    @Test
    fun everyPhoneAndFontScaleFitsTheScoreInItsCorner() {
        onEveryPhoneAndFontScale(SCORED, languages = listOf(DEFAULT, VIETNAMESE)) { where ->
            listOf(text(R.string.score_tier_perfect), SCORED.score.points.toString()).forEach { text ->
                val layout = compose.onNodeWithText(text, useUnmergedTree = true).textLayout()
                assertTrue("$where: \"$text\" does not fit the score's slot: ${layout.describeFit()}", layout.fitsItsBox())
            }
        }
    }

    /** The panel is taller than a 320 dp screen at 200 % text: it must scroll, or Replay and Exit are out of reach. */
    @Test
    fun everyPhoneAndFontScaleReachesTheFinishedPanelsButtons() {
        onEveryPhoneAndFontScale(FINISHED) { where ->
            val screen = compose.onNodeWithTag(SCREEN_TAG).fetchSemanticsNode().boundsInRoot
            listOf(R.string.action_replay, R.string.action_exit).forEach { id ->
                val button = compose.onNodeWithText(text(id))
                button.performScrollTo()
                val bounds = button.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "$where: ${text(id)} at $bounds is outside the screen $screen",
                    bounds.left >= screen.left - HALF_PIXEL && bounds.right <= screen.right + HALF_PIXEL &&
                        bounds.top >= screen.top - HALF_PIXEL && bounds.bottom <= screen.bottom + HALF_PIXEL,
                )
            }
        }
    }

    private fun onEveryPhoneAndFontScale(
        state: DemoState,
        languages: List<Context> = listOf(DEFAULT),
        check: (where: String) -> Unit,
    ) {
        compose.setContent {
            // A fresh composition per case, as the Activity recreated by a change of screen or font scale is:
            // auto-sized text measures once, and a density changed under it is not a case a phone produces.
            key(phone, fontScale, language) {
                CompositionLocalProvider(
                    LocalDensity provides Density(phone.density, fontScale),
                    // Both: stringResource reads LocalResources, anything else the context.
                    LocalContext provides language,
                    LocalResources provides language.resources,
                ) {
                    PsRemoteTheme {
                        Box(Modifier.requiredSize(phone.widthDp.dp, phone.heightDp.dp).testTag(SCREEN_TAG)) {
                            DemoScreen(state, onIntent = {}, video = {})
                        }
                    }
                }
            }
        }
        for (each in languages) {
            for (candidate in PHONES) {
                for (scale in FONT_SCALES) {
                    language = each
                    phone = candidate
                    fontScale = scale
                    compose.waitForIdle()
                    check("$candidate at font scale $scale in ${each.resources.configuration.locales[0]}")
                }
            }
        }
    }

    /** [id] in the language the screen is composed in. */
    private fun text(@StringRes id: Int): String = language.getString(id)

    private fun assertControllerIsWhole(where: String) {
        val bounds = ControllerButton.entries.associateWith { compose.boundsOf(it.name) }
        val width = bounds.values.maxOf { it.right } - bounds.values.minOf { it.left }
        val height = bounds.values.maxOf { it.bottom } - bounds.values.minOf { it.top }
        assertTrue("$where: the controller is wider than the screen", width <= phone.widthPx + 1)
        assertTrue("$where: the controller is taller than the screen", height <= phone.heightPx + 1)

        val entries = bounds.entries.toList()
        entries.forEachIndexed { index, first ->
            entries.drop(index + 1).forEach { second ->
                assertFalse("$where: ${first.key} overlaps ${second.key}", first.value.overlaps(second.value))
            }
        }

        val badge = compose.onNodeWithText(string(R.string.badge_presses, 2), useUnmergedTree = true).textLayout()
        assertTrue("$where: the ×2 badge does not fit its circle: ${badge.describeFit()}", badge.fitsItsBox())

        LABELLED.forEach { button ->
            val layout = compose.onNodeWithText(button.name, useUnmergedTree = true).textLayout()
            assertTrue("$where: the $button label does not fit its button: ${layout.describeFit()}", layout.fitsItsBox())
        }
    }

    /** A phone's screen in landscape: dp size and density, as `adb shell wm size` / `wm density` report them. */
    private data class Phone(val name: String, val widthDp: Int, val heightDp: Int, val density: Float) {
        val widthPx get() = widthDp * density
        val heightPx get() = heightDp * density

        override fun toString() = "$name (${widthDp}×$heightDp dp @ ${density}x)"
    }

    private companion object {
        val PHONES = listOf(
            Phone("SM-A165F", 832, 384, 2.8125f),
            Phone("20:9 FHD+ @ 420 dpi", 914, 411, 2.625f),
            Phone("19.5:9 @ 480 dpi", 780, 360, 3f),
            Phone("16:9 HD @ 320 dpi", 640, 360, 2f),
            Phone("18:9 Android Go @ 240 dpi", 640, 320, 1.5f),
            Phone("16:9 WVGA @ 240 dpi", 569, 320, 1.5f),
            Phone("21:9 FHD+ @ 440 dpi", 916, 393, 2.75f),
            Phone("foldable inner screen", 829, 690, 2.625f),
            Phone("split screen, half", 411, 384, 2.8125f),
        )

        /** An ANY_ORDER step owing CROSS twice: its badge, "×2", is the longest a tutorial draws. */
        val TUTORIAL = TutorialStep(1, 34_000, listOf(ControllerButton.CROSS, ControllerButton.CROSS, ControllerButton.R1),
            InputMode.ANY_ORDER, 0.25, 3_000).let { step ->
            DemoState(phase = DemoPhase.Playing, tutorial = ActiveTutorial(step, StepProgress.start(step), isWaiting = true))
        }

        /** Between steps of a long script: the tier caption shown, and a four-digit score, 1 900 of 2 000. */
        val SCORED = DemoState(phase = DemoPhase.Playing, score = Score(List(19) { ScoreTier.PERFECT }, stepCount = 20))

        val FINISHED = SCORED.copy(phase = DemoPhase.Finished)

        /** The language the suite runs in: English on the JVM, the phone's own on a device. */
        val DEFAULT: Context
            get() = ApplicationProvider.getApplicationContext()

        val VIETNAMESE: Context
            get() = DEFAULT.createConfigurationContext(
                Configuration(DEFAULT.resources.configuration).apply { setLocale(Locale.forLanguageTag("vi")) },
            )

        const val SCREEN_TAG = "phone screen"
        const val HALF_PIXEL = 0.5f

        /** Default, the common "large" setting, and Android 14's nonlinear maximum. */
        val FONT_SCALES = listOf(1f, 1.3f, 2f)

        /** Every button with a printed label: the face buttons and the D-pad are drawn shapes (ButtonGlyph). */
        val LABELLED = ControllerButton.entries - setOf(
            ControllerButton.CROSS, ControllerButton.CIRCLE, ControllerButton.SQUARE, ControllerButton.TRIANGLE,
            ControllerButton.DPAD_UP, ControllerButton.DPAD_DOWN, ControllerButton.DPAD_LEFT, ControllerButton.DPAD_RIGHT,
        )
    }
}
