package com.pion.psremote.feature.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.psremote.core.ui.theme.PsColors
import com.pion.psremote.core.ui.token.Spacing
import com.pion.psremote.domain.model.DemoSummary
import com.pion.psremote.feature.home.HomeIntent

/** One game: a 16:9 tile, the shape of the video it opens. The whole tile is the button; its title is its label. */
@Composable
fun DemoCard(demo: DemoSummary, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onIntent(HomeIntent.DemoClicked(demo.id)) },
        modifier = modifier.aspectRatio(VIDEO_ASPECT_RATIO),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CARD_BORDER, PsColors.ButtonBorder),
    ) {
        Box(Modifier.padding(Spacing.l)) {
            PlayBadge(Modifier.align(Alignment.Center))
            Text(
                demo.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** Drawn, not an icon font, for the same reason as the controller glyphs (LLM.md §12). */
@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    val triangle = MaterialTheme.colorScheme.onPrimary
    Spacer(
        modifier.size(PLAY_BADGE_SIZE).drawWithCache {
            val path = Path().apply {
                // Nudged right of centre: a centred triangle reads as off-centre, its mass is on the left.
                moveTo(size.width * 0.40f, size.height * 0.30f)
                lineTo(size.width * 0.72f, size.height * 0.50f)
                lineTo(size.width * 0.40f, size.height * 0.70f)
                close()
            }
            onDrawBehind {
                drawCircle(PsColors.Highlight)
                drawPath(path, triangle)
            }
        },
    )
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private val CARD_BORDER = 1.dp
/** Large enough to read as "tap to play" from arm's length, small enough to leave the title its own line. */
private val PLAY_BADGE_SIZE = 48.dp
