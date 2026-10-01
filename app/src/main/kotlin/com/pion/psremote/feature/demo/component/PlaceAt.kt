package com.pion.psremote.feature.demo.component

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Sizes and places an element at [bounds], in the px coordinates of a full-screen parent.
 *
 * `offset` first, so the node itself sits at [bounds] and hit-testing happens there — a node that
 * only drew its content at an offset would still take its touches at the origin.
 */
fun Modifier.placeAt(bounds: Rect): Modifier = this
    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
    .layout { measurable, _ ->
        val width = bounds.width.roundToInt()
        val height = bounds.height.roundToInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }
