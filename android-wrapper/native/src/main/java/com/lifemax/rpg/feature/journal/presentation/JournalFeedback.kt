package com.lifemax.rpg.feature.journal.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import com.lifemax.rpg.core.designsystem.feedback.PageCelebrationLayer
import com.lifemax.rpg.core.designsystem.feedback.PagePoint
import com.lifemax.rpg.feature.journal.domain.JournalCelebration

/** Where the last finger came down on the screen, in its pixels: the celebration starts there. */
internal class TapSpot {
    var at: Offset = Offset.Unspecified
}

/** Notes every finger that comes down, taking nothing from it. */
internal suspend fun PointerInputScope.recordTaps(spot: TapSpot) = awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes
            .firstOrNull { it.changedToDownIgnoreConsumed() }
            ?.let { spot.at = it.position }
    }
}

/**
 * The XP a write paid, flying from where the owner last touched the screen to the page's top bar
 * (core PageCelebration, the same layer Plan and Life Director use), with fewer particles under
 * reduced motion. [onFinished] gets its id once it is over; a screen that is hidden draws none.
 */
@Composable
internal fun JournalCelebrationLayer(
    celebration: JournalCelebration,
    spot: TapSpot,
    target: PagePoint?,
    reduced: Boolean,
    onFinished: (Long) -> Unit,
    startAt: Float = 0f,
) {
    val origin = remember(celebration.id) { spot.at }
    PageCelebrationLayer(
        celebration.id,
        celebration.awards,
        origin,
        target,
        reduced,
        onFinished,
        startAt,
    )
}
