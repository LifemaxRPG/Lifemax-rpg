package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The entry's arrival (07-finishing.css `.journal-entry>*`, keyframes jxIn): each direct child of
 * the card rises 10 dp and fades in over 450 ms, the 2nd to 5th starting 40 ms after one another
 * and every later one at 200 ms. The page redraws the entry on each new selection, so the screen
 * plays it on each new entry. The clock is one value (milliseconds since the entry arrived) that
 * the slot animates; a screen with no clock (a still image, or the page's animations off) shows
 * every child in place at once.
 */

/** Milliseconds since the entry arrived, or null to draw every child at rest. */
internal val LocalEntryClock = compositionLocalOf<(() -> Float)?> { null }

internal const val ARRIVAL_MS = 450
private const val RISE_DP = 10f
private val ease = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/** When the [position]th child (1 is the first) starts, in milliseconds. */
internal fun arrivalDelay(position: Int): Int = (position - 1).coerceIn(0, 5) * 40

/** The time the whole entry takes to arrive: the last child's delay and its own run. */
internal const val ARRIVAL_TOTAL_MS = 200 + ARRIVAL_MS

/** The [position]th child of the entry card (1 is the first), arriving as the page's does. */
@Composable
internal fun Arrive(position: Int, content: @Composable () -> Unit) {
    val clock = LocalEntryClock.current
    if (clock == null) {
        content()
        return
    }
    Box(
        Modifier.graphicsLayer {
            val t = ((clock() - arrivalDelay(position)) / ARRIVAL_MS).coerceIn(0f, 1f)
            val e = ease.transform(t)
            alpha = e
            translationY = (1f - e) * RISE_DP * density
        }
    ) {
        content()
    }
}
