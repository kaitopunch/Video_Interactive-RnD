package com.pion.psremote.testing

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider

/**
 * Helpers for the Compose suites in `src/sharedTest`, which run on the JVM under Robolectric and on a phone from
 * one source (LLM.md §9). Nothing here may depend on either runtime.
 *
 * Test names in `src/sharedTest` and `src/androidTest` are camelCase, not backticked sentences: D8 rejects a
 * space in a method name below DEX 040, and minSdk 28 builds DEX 039.
 */

/** A string resolved the way the screen resolves it, so a suite passes on a device in any locale (MVI doc §7). */
fun string(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

fun ComposeContentTestRule.boundsOf(contentDescription: String): Rect =
    onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot

fun ComposeContentTestRule.centerOf(contentDescription: String): Offset = boundsOf(contentDescription).center

/** The node whose click action carries [label] — a control with no text of its own, such as the Exit chevron. */
fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

/** The laid-out text of a text node: what was actually drawn, at the size auto-sizing settled on. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}

/**
 * Whether the text, at the size it was laid out at, fits the room its parent gave it. Not `hasVisualOverflow`: for
 * a centred `BasicText(String)` with room to spare, the layout read back through semantics is rebuilt at the
 * full width and reports an overflow that is never drawn.
 */
fun TextLayoutResult.fitsItsBox(): Boolean =
    multiParagraph.maxIntrinsicWidth <= layoutInput.constraints.maxWidth + HALF_PIXEL &&
        multiParagraph.height <= layoutInput.constraints.maxHeight + HALF_PIXEL &&
        !multiParagraph.didExceedMaxLines

/** The numbers [fitsItsBox] compares, for a failure message. */
fun TextLayoutResult.describeFit(): String =
    "text ${multiParagraph.maxIntrinsicWidth}×${multiParagraph.height} px in " +
        "${layoutInput.constraints.maxWidth}×${layoutInput.constraints.maxHeight} px, " +
        "lines=${multiParagraph.lineCount}, exceeded=${multiParagraph.didExceedMaxLines}"

private const val HALF_PIXEL = 0.5f
